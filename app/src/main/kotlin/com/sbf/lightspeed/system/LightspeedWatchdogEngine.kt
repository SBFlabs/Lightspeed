package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
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
     * Gets all currently enabled accessibility services as parsed ComponentNames.
     */
    fun getEnabledAccessibilityServices(context: Context): Set<ComponentName> {
        val raw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return emptySet()

        val set = mutableSetOf<ComponentName>()
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(raw)
        while (colonSplitter.hasNext()) {
            val str = colonSplitter.next().trim()
            if (str.isNotEmpty()) {
                val cn = ComponentName.unflattenFromString(str)
                if (cn != null) set.add(cn)
            }
        }
        return set
    }

    /**
     * Checks whether a specific component (e.g. "pkg/cls") is currently enabled.
     */
    fun isComponentEnabled(context: Context, componentId: String): Boolean {
        val target = ComponentName.unflattenFromString(componentId) ?: return false
        val enabledSet = getEnabledAccessibilityServices(context)
        return enabledSet.contains(target)
    }

    /**
     * Instant 1-tap toggling of ANY accessibility service via Shizuku shell or Root.
     * Bypasses Android Settings menus and Android 13+ restricted settings dialogs.
     */
    fun toggleAccessibilityService(context: Context, componentId: String, enable: Boolean): Boolean {
        val targetCn = ComponentName.unflattenFromString(componentId) ?: return false

        if (!ElevatedTaskCloser.isShizukuActive && !ElevatedTaskCloser.isRootActive) {
            // Graceful fallback to system accessibility settings
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            return false
        }

        val currentRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""

        val currentEntries = currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }
        val remainingEntries = mutableListOf<String>()

        for (entry in currentEntries) {
            val cn = ComponentName.unflattenFromString(entry)
            if (cn != null && cn == targetCn) {
                // Skip matched target
                continue
            }
            remainingEntries.add(entry)
        }

        if (enable) {
            remainingEntries.add(targetCn.flattenToString())
        }

        val newServices = remainingEntries.joinToString(":")
        val accessibilityEnabled = if (remainingEntries.isNotEmpty()) 1 else 0
        val targetPkg = targetCn.packageName

        // If enabling, also bypass Android 13+ Restricted Settings via appops
        val restrictedCmd = if (enable) "appops set $targetPkg ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; " else ""
        val cmd = "${restrictedCmd}settings put secure enabled_accessibility_services \"$newServices\" && settings put secure accessibility_enabled $accessibilityEnabled"

        val success = if (ElevatedTaskCloser.isShizukuActive) {
            val p = ElevatedTaskCloser.execShizuku(cmd)
            p?.waitFor() == 0
        } else {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        }

        Log.i(TAG, "toggleAccessibilityService: $componentId -> enable=$enable, success=$success")
        return success
    }

    /**
     * 1-Tap battery whitelist via Shizuku/Root shell:
     * Immediately excludes the package from Doze & OEM aggressive task killers.
     */
    fun whitelistBattery(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!ElevatedTaskCloser.isShizukuActive && !ElevatedTaskCloser.isRootActive) {
            requestIgnoreBatteryOptimization(context)
            return false
        }

        val cmd = "cmd deviceidle whitelist +$packageName"
        val success = if (ElevatedTaskCloser.isShizukuActive) {
            val p = ElevatedTaskCloser.execShizuku(cmd)
            p?.waitFor() == 0
        } else {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            p.waitFor() == 0
        }
        Log.i(TAG, "whitelistBattery: $packageName -> success=$success")
        return success
    }

    /**
     * Checks if a package is whitelisted from battery optimization.
     */
    fun isPackageBatteryWhitelisted(context: Context, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            return pm?.isIgnoringBatteryOptimizations(packageName) == true
        }
        return true
    }

    /**
     * Pulses and ensures all protected third-party accessibility sentinels are running.
     * Re-injects any missing services and triggers Android AccessibilityManagerService re-binding.
     */
    fun pulsePerimeterServices(context: Context): Int {
        if (!ElevatedTaskCloser.isShizukuActive && !ElevatedTaskCloser.isRootActive) {
            return 0
        }

        val protectedStrings = LightspeedPreferences.getPerimeterProtectedServices(context)
        val protectedCns = protectedStrings.mapNotNull { ComponentName.unflattenFromString(it) }

        val currentRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val entries = currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

        var revivedCount = 0
        for (pCn in protectedCns) {
            val alreadyIn = entries.any { ComponentName.unflattenFromString(it) == pCn }
            if (!alreadyIn) {
                entries.add(pCn.flattenToString())
                revivedCount++
            }
        }

        val newServices = entries.joinToString(":")
        val accessibilityEnabled = if (entries.isNotEmpty()) 1 else 0

        val cmd = "settings put secure enabled_accessibility_services \"$newServices\" && settings put secure accessibility_enabled $accessibilityEnabled"

        if (ElevatedTaskCloser.isShizukuActive) {
            ElevatedTaskCloser.execShizuku(cmd)?.waitFor()
        } else if (ElevatedTaskCloser.isRootActive) {
            Runtime.getRuntime().exec(arrayOf("su", "-c", cmd)).waitFor()
        }

        Log.i(TAG, "pulsePerimeterServices: revived $revivedCount missing services. Total active: ${entries.size}")
        return entries.size
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

                    // 1. Core Watchdog: Lightspeed's own service
                    if (LightspeedAccessibilityService.instance == null) {
                        Log.w(TAG, "Sentinel alert: AccessibilityService instance is null! Attempting revival...")
                        reviveAccessibilityService(context)
                    }

                    // 2. Perimeter Watchdog: External protected accessibility services
                    val protectedStrings = LightspeedPreferences.getPerimeterProtectedServices(context)
                    if (protectedStrings.isNotEmpty()) {
                        val enabledSet = getEnabledAccessibilityServices(context)
                        val hasMissing = protectedStrings.any {
                            val cn = ComponentName.unflattenFromString(it)
                            cn != null && !enabledSet.contains(cn)
                        }
                        if (hasMissing) {
                            Log.w(TAG, "Perimeter Sentinel alert: Protected services missing! Reviving...")
                            pulsePerimeterServices(context)
                        }
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

    /**
     * Checks whether the app is whitelisted from battery optimizations.
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } else {
            true
        }
    }

    /**
     * Dispatches system request or settings intent to exclude app from aggressive battery optimizations.
     */
    fun requestIgnoreBatteryOptimization(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Clears persisted crash blackbox telemetry.
     */
    fun clearCrashTelemetry(context: Context) {
        context.defaultPrefs().edit()
            .remove("key_last_crash_timestamp")
            .remove("key_last_crash_message")
            .remove("key_last_crash_stack")
            .remove("key_has_unreported_crash")
            .apply()
    }
}
