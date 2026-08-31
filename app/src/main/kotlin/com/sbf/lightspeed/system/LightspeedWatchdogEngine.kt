package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService
import kotlinx.coroutines.*

/**
 * Subspace Watchdog & Sentinel Engine.
 * Provides elevated process termination (Emergency Shizuku Jettison)
 * and auto-revival of killed Accessibility Services via Shizuku IPC.
 */
object LightspeedWatchdogEngine {
    private const val TAG = "LightspeedWatchdog"
    private val watchdogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sentinelJob: Job? = null

    /**
     * Emergency Shizuku Jettison: Force-terminates a given package name via Shizuku shell.
     */
    fun jettisonPackage(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        Log.i(TAG, "Executing Emergency Jettison for package: $packageName")

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("am force-stop $packageName")
                proc?.waitFor()
                Log.i(TAG, "Successfully jettisoned $packageName via Shizuku")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error jettisoning package via Shizuku", e)
            }
        }

        if (ElevatedTaskCloser.isRootActive) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
                proc.waitFor()
                Log.i(TAG, "Successfully jettisoned $packageName via Root")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Error jettisoning package via Root", e)
            }
        }

        return false
    }

    /**
     * Checks whether LightspeedAccessibilityService is actively enabled in system secure settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (LightspeedAccessibilityService.instance != null) return true

        val expectedComponent = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponent, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Revives LightspeedAccessibilityService using Shizuku privileged shell.
     */
    fun reviveAccessibilityService(context: Context): Boolean {
        if (!ElevatedTaskCloser.isShizukuActive && !ElevatedTaskCloser.isRootActive) {
            Log.w(TAG, "Cannot revive accessibility service: Shizuku / Root not available")
            return false
        }

        val serviceComponent = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToString()
        Log.i(TAG, "Reviving Accessibility Service: $serviceComponent")

        val cmd = """
            current_services=$(settings get secure enabled_accessibility_services)
            if [ -z "${'$'}current_services" ] || [ "${'$'}current_services" = "null" ]; then
                new_services="$serviceComponent"
            else
                case ":${'$'}current_services:" in
                    *":$serviceComponent:"*) new_services="${'$'}current_services" ;;
                    *) new_services="${'$'}current_services:$serviceComponent" ;;
                esac
            fi
            settings put secure enabled_accessibility_services "${'$'}new_services"
            settings put secure accessibility_enabled 1
        """.trimIndent()

        val success = if (ElevatedTaskCloser.isShizukuActive) {
            val p = ElevatedTaskCloser.execShizuku(cmd)
            p?.waitFor() == 0
        } else {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        }

        Log.i(TAG, "Revival command result: $success")
        return success
    }

    /**
     * Starts background watchdog sentinel polling.
     */
    fun initSentinel(context: Context) {
        val prefs = context.defaultPrefs()
        val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false)
        if (!isEnabled) {
            stopSentinel()
            return
        }

        if (sentinelJob != null && sentinelJob?.isActive == true) return

        sentinelJob = watchdogScope.launch {
            while (isActive) {
                delay(20_000L) // Poll every 20 seconds
                try {
                    val sentinelActive = context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false)
                    if (!sentinelActive) break

                    if (LightspeedAccessibilityService.instance == null) {
                        Log.w(TAG, "Sentinel alert: AccessibilityService instance is null! Attempting revival...")
                        reviveAccessibilityService(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sentinel poll cycle error", e)
                }
            }
        }
    }

    fun isSentinelRunning(): Boolean = sentinelJob?.isActive == true

    /**
     * Checks if scheduled Core Cooling reminder is currently due.
     * STRICT RULE: Zero automatic reboots. Only triggers silent UI telemetry indicator.
     */
    fun isCoreCoolingDue(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, false)
        if (!isEnabled) return false

        val targetDay = prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, java.util.Calendar.SUNDAY)
        val targetHour = prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, 3)
        val lastTrigger = prefs.getLong(LightspeedPreferences.KEY_CORE_COOLING_LAST_TRIGGER, 0L)
        val now = System.currentTimeMillis()

        val cal = java.util.Calendar.getInstance()
        val currentDay = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)

        // Prevent multiple triggers on the same day (minimum 24h cooldown)
        val minIntervalMs = 24 * 60 * 60 * 1000L
        if (lastTrigger != 0L && (now - lastTrigger) < minIntervalMs) {
            return false
        }

        if (currentDay == targetDay && currentHour >= targetHour) {
            return true
        }

        return false
    }

    /**
     * Dismisses active Core Cooling reminder until the next cycle.
     */
    fun dismissCoreCoolingReminder(context: Context) {
        val prefs = context.defaultPrefs()
        prefs.edit().putLong(LightspeedPreferences.KEY_CORE_COOLING_LAST_TRIGGER, System.currentTimeMillis()).apply()
    }

    /**
     * Executes Core Cooling Hardware Reboot via Shizuku shell or Root privilege.
     * Invoked strictly after Triple-Lock safety confirmation.
     */
    fun executeCoreCoolingReboot(context: Context): Boolean {
        Log.i(TAG, "Executing Core Cooling Reboot via elevated interface...")
        dismissCoreCoolingReminder(context)

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val proc = ElevatedTaskCloser.execShizuku("svc power reboot")
                proc?.waitFor()
                Log.i(TAG, "Reboot dispatched via Shizuku svc power reboot")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku reboot error", e)
            }
        }

        if (ElevatedTaskCloser.isRootActive) {
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", "svc power reboot"))
                proc.waitFor()
                Log.i(TAG, "Reboot dispatched via Root svc power reboot")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Root reboot error", e)
            }
        }

        Log.w(TAG, "Core Cooling reboot failed: Neither Shizuku nor Root is active")
        return false
    }

    fun stopSentinel() {
        sentinelJob?.cancel()
        sentinelJob = null
    }
}
