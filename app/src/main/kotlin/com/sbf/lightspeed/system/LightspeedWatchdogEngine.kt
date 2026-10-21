package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.content.ContextCompat
import com.sbf.lightspeed.LightspeedAccessibilityService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Subspace Watchdog & Sentinel Engine.
 * Provides elevated process termination (Emergency Shizuku Jettison)
 * and auto-revival of killed Accessibility Services via Shizuku IPC.
 */
object LightspeedWatchdogEngine {
    private const val TAG = "LightspeedWatchdog"
    @Volatile
    private var watchdogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var sentinelJob: Job? = null

    @Volatile
    private var lastInternalWriteTime = 0L

    @Volatile
    private var lastPerimeterPulseTime = 0L

    private val missingPulseCounts = ConcurrentHashMap<String, Int>()
    private val previouslyEnabledServices = ConcurrentHashMap<String, Boolean>()
    private val presumedToggleOffLogged = ConcurrentHashMap<String, Boolean>()

    private var a11yObserver: ContentObserver? = null
    private var fastTriggerReceiver: BroadcastReceiver? = null

    fun recordInternalWrite() {
        lastInternalWriteTime = SystemClock.uptimeMillis()
    }

    fun isRecentInternalWrite(): Boolean {
        return SystemClock.uptimeMillis() - lastInternalWriteTime < 3000L
    }

    /**
     * Executes one Perimeter pulse debounced to at most once per 3 s (3000 ms).
     * Ignores changes triggered by internal writes (within timestamp guard window).
     */
    fun triggerDebouncedPerimeterPulse(context: Context, isContentObserver: Boolean = false) {
        if (isContentObserver && isRecentInternalWrite()) {
            Log.d(TAG, "Skipping perimeter pulse: recent internal write detected")
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastPerimeterPulseTime < 3000L) {
            Log.d(TAG, "Debounced perimeter pulse (last pulse ${now - lastPerimeterPulseTime}ms ago)")
            return
        }
        lastPerimeterPulseTime = now

        watchdogScope.launch {
            try {
                pulsePerimeterServices(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error in debounced perimeter pulse", e)
            }
        }
    }

    /**
     * Registers fast triggers for perimeter checks:
     * 1. ContentObserver on Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
     * 2. BroadcastReceiver for ACTION_SCREEN_ON and ACTION_USER_PRESENT
     */
    fun registerFastTriggers(context: Context) {
        unregisterFastTriggers(context)

        try {
            val uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    triggerDebouncedPerimeterPulse(context, isContentObserver = true)
                }
            }
            context.contentResolver.registerContentObserver(uri, false, observer)
            a11yObserver = observer
        } catch (e: Exception) {
            logSwallowed(TAG, "registerFastTriggers:observer", e)
        }

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context?, intent: Intent?) {
                    val action = intent?.action ?: return
                    if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
                        triggerDebouncedPerimeterPulse(context, isContentObserver = false)
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            fastTriggerReceiver = receiver
        } catch (e: Exception) {
            logSwallowed(TAG, "registerFastTriggers:receiver", e)
        }
    }

    /**
     * Unregisters fast trigger observer and receiver.
     */
    fun unregisterFastTriggers(context: Context) {
        a11yObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (e: Exception) {
                logSwallowed(TAG, "unregisterFastTriggers:observer", e)
            }
            a11yObserver = null
        }

        fastTriggerReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                logSwallowed(TAG, "unregisterFastTriggers:receiver", e)
            }
            fastTriggerReceiver = null
        }
    }

    /**
     * Checks whether LightspeedAccessibilityService is actively enabled in system secure settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        if (LightspeedAccessibilityService.instance != null) return true

        val a11yEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
        } catch (_: Exception) { false }
        if (!a11yEnabled) return false

        val expectedLong = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToString()
        val expectedShort = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToShortString()
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next().trim()
            if (componentName.equals(expectedLong, ignoreCase = true) ||
                componentName.equals(expectedShort, ignoreCase = true)) {
                return true
            }
            val parsed = ComponentName.unflattenFromString(componentName)
            if (parsed != null && parsed.packageName == context.packageName &&
                parsed.className == LightspeedAccessibilityService::class.java.name) {
                return true
            }
        }
        return false
    }

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    /**
     * Revives LightspeedAccessibilityService using WRITE_SECURE_SETTINGS or Shizuku privileged shell.
     */
    fun reviveAccessibilityService(context: Context): Boolean {
        if (context.defaultPrefs().getBoolean("pref_service_intentionally_stopped", false)) {
            Log.i(TAG, "reviveAccessibilityService: pref_service_intentionally_stopped is true. Skipping revive.")
            FlightLog.log("watchdog", false, "Revive skip: Lightspeed (intentionally stopped)")
            return false
        }

        val serviceLong = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToString()
        val serviceShort = ComponentName(context, LightspeedAccessibilityService::class.java).flattenToShortString()
        val targetPkg = context.packageName
        Log.i(TAG, "Reviving Accessibility Service: $serviceLong")

        // Core Watchdog: Check if Lightspeed itself is listed as crashed in dumpsys
        if (GriffinRecovery.isCrashed(targetPkg)) {
            Log.w(TAG, "Core Sentinel alert: Lightspeed is listed as crashed in dumpsys! Executing Griffin recovery...")
            recordInternalWrite()
            return GriffinRecovery.recover(context, targetPkg, serviceLong)
        }

        // 1. Direct ContentResolver write if WRITE_SECURE_SETTINGS is granted
        if (canWriteSecureSettings(context)) {
            try {
                recordInternalWrite()
                val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                val newServices = if (current.isEmpty() || current == "null") {
                    serviceLong
                } else {
                    val list = current.split(":").filter { it.isNotBlank() }.toMutableList()
                    val containsService = list.any { entry ->
                        entry.equals(serviceLong, ignoreCase = true) ||
                        entry.equals(serviceShort, ignoreCase = true) ||
                        ComponentName.unflattenFromString(entry)?.let { cn ->
                            cn.packageName == targetPkg && cn.className == LightspeedAccessibilityService::class.java.name
                        } == true
                    }
                    if (!containsService) list.add(serviceLong)
                    list.joinToString(":")
                }
                FlightLog.log("watchdog", true, "Revive attempt: $serviceLong (direct Settings write)")
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                Log.i(TAG, "Revived via direct Settings.Secure: $newServices")
                FlightLog.log("watchdog", true, "Revive result: $serviceLong (direct Settings write succeeded)")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing secure accessibility settings: ${e.message}")
                FlightLog.log("watchdog", false, "Revive result: $serviceLong (direct Settings write failed: ${e.message})")
            }
        }

        // 2. Shizuku privileged shell
        if (ElevatedTaskCloser.isShizukuActive) {
            recordInternalWrite()
            val svcLongQuote = shellSingleQuote(serviceLong)
            val svcShortQuote = shellSingleQuote(serviceShort)
            val cmd = """
                svc_long=$svcLongQuote
                svc_short=$svcShortQuote
                appops set ${shellSingleQuote(targetPkg)} ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null
                pm grant ${shellSingleQuote(targetPkg)} android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
                current_services=$(settings get secure enabled_accessibility_services)
                if [ -z "${'$'}current_services" ] || [ "${'$'}current_services" = "null" ]; then
                    new_services="${'$'}svc_long"
                else
                    case ":${'$'}current_services:" in
                        *":${'$'}svc_long}:"*|*":${'$'}svc_short}:"*) new_services="${'$'}current_services" ;;
                        *) new_services="${'$'}current_services:${'$'}svc_long" ;;
                    esac
                fi
                settings put secure enabled_accessibility_services "${'$'}new_services"
                settings put secure accessibility_enabled 1
            """.trimIndent()

            FlightLog.log("watchdog", true, "Revive attempt: $serviceLong (Shizuku privileged shell)")
            val success = execPrivileged(cmd)
            Log.i(TAG, "Revival command result: $success")
            FlightLog.log("watchdog", success, "Revive result: $serviceLong (Shizuku shell ${if (success) "succeeded" else "failed"})")
            if (success) return true
        }

        Log.w(TAG, "Cannot revive accessibility service: Elevated permissions / Shizuku not available")
        FlightLog.log("watchdog", false, "Revive skip: $serviceLong (elevated permissions unavailable)")
        return false
    }

    fun openAccessibilitySettings(context: Context) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                android.widget.Toast.makeText(context, com.sbf.lightspeed.R.string.restricted_settings_hint, android.widget.Toast.LENGTH_LONG).show()
            }
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) { logSwallowed(TAG, "openAccessibilitySettings:127", e) }
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
     * Instant 1-tap toggling of ANY accessibility service via Shizuku shell or Root.
     * Bypasses Android Settings menus and Android 13+ restricted settings dialogs.
     */
    fun toggleAccessibilityService(context: Context, componentId: String, enable: Boolean): Boolean {
        val targetCn = ComponentName.unflattenFromString(componentId) ?: return false

        if (!ElevatedTaskCloser.isShizukuActive) {
            // Graceful fallback to system accessibility settings
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e: Exception) { logSwallowed(TAG, "toggleAccessibilityService:166", e) }
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
        val restrictedCmd = if (enable) "appops set ${shellSingleQuote(targetPkg)} ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; " else ""
        val cmd = "${restrictedCmd}settings put secure enabled_accessibility_services ${shellSingleQuote(newServices)} && settings put secure accessibility_enabled $accessibilityEnabled"

        recordInternalWrite()
        val success = execPrivileged(cmd)

        Log.i(TAG, "toggleAccessibilityService: $componentId -> enable=$enable, success=$success")
        return success
    }

    private fun execPrivileged(cmd: String): Boolean {
        return if (ElevatedTaskCloser.isShizukuActive) {
            val p = ElevatedTaskCloser.execShizuku(cmd)
            p.waitForOrKill() == 0
        } else false
    }

    /**
     * 1-Tap battery whitelist via Shizuku/Root shell:
     * Immediately excludes the package from Doze & OEM aggressive task killers.
     */
    fun whitelistBattery(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!ElevatedTaskCloser.isShizukuActive) {
            requestIgnoreBatteryOptimization(context)
            return false
        }

        val success = execPrivileged("cmd deviceidle whitelist +$packageName")
        Log.i(TAG, "whitelistBattery: $packageName -> success=$success")
        return success
    }

    /**
     * Removes battery whitelist exemption via Shizuku/Root shell:
     * Restores standard Doze / system battery optimization for the package.
     */
    fun removeBatteryWhitelist(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!ElevatedTaskCloser.isShizukuActive) {
            return false
        }

        val success = execPrivileged("cmd deviceidle whitelist -$packageName")
        Log.i(TAG, "removeBatteryWhitelist: $packageName -> success=$success")
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

    private fun getCrashedAccessibilityPackages(): Set<String> {
        if (!ElevatedTaskCloser.isShizukuActive) return emptySet()
        val crashed = mutableSetOf<String>()
        try {
            val process = ElevatedTaskCloser.execShizuku("dumpsys accessibility 2>&1") ?: return emptySet()
            val reader = process.inputStream.bufferedReader()
            var inCrashedSection = false
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val l = line!!
                if (l.contains("Crashed services:", ignoreCase = true)) {
                    val content = l.substringAfter("Crashed services:", "").trim()
                    if (content.isNotEmpty() && content != "{}") {
                        parsePackagesFromText(content, crashed)
                    }
                    inCrashedSection = true
                    continue
                }
                if (inCrashedSection) {
                    val trimmed = l.trim()
                    if (trimmed.startsWith("Client list") || trimmed.startsWith("Bound services") ||
                        trimmed.startsWith("Enabled services") || trimmed.startsWith("Binding services") ||
                        trimmed.startsWith("User state") || trimmed.startsWith("Global state") ||
                        trimmed.startsWith("Accessibility Display Listener")) {
                        inCrashedSection = false
                        continue
                    }
                    parsePackagesFromText(l, crashed)
                }
            }
            process.waitForOrKill()
        } catch (e: Exception) {
            Log.w(TAG, "getCrashedAccessibilityPackages error", e)
        }
        return crashed
    }

    private fun parsePackagesFromText(text: String, outSet: MutableSet<String>) {
        val regex = Regex("""[a-zA-Z0-9_.]+(?:/[a-zA-Z0-9_.]+)?""")
        for (match in regex.findAll(text)) {
            val str = match.value
            val pkg = if (str.contains('/')) str.substringBefore('/') else str
            if (pkg.contains('.') && pkg.length > 3) {
                outSet.add(pkg)
            }
        }
    }

    /**
     * Pulses and ensures all protected third-party accessibility sentinels are running.
     * Re-injects any missing services and triggers Android AccessibilityManagerService re-binding.
     * Uses GriffinRecovery recipe for services verified crashed via dumpsys, and plain re-add
     * for services missing across 2 consecutive pulses.
     */
    fun pulsePerimeterServices(context: Context): Int {
        val hasElevated = ElevatedTaskCloser.isShizukuActive
        val hasDirectWritePermission = context.checkCallingOrSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasElevated && !hasDirectWritePermission) {
            Log.w(TAG, "pulsePerimeterServices: no elevated access (Shizuku or WRITE_SECURE_SETTINGS)")
            FlightLog.log("watchdog", false, "Perimeter pulse skipped: no elevated access (Shizuku or WRITE_SECURE_SETTINGS)")
            return 0
        }

        val protectedStrings = LightspeedPreferences.getPerimeterProtectedServices(context)
        val protectedCns = protectedStrings.mapNotNull { ComponentName.unflattenFromString(it) }
        if (protectedCns.isEmpty()) return 0

        val activeProtectedKeys = protectedCns.map { it.flattenToString() }.toSet()
        missingPulseCounts.keys.retainAll(activeProtectedKeys)
        previouslyEnabledServices.keys.retainAll(activeProtectedKeys)
        presumedToggleOffLogged.keys.retainAll(activeProtectedKeys)

        var recoveredCount = 0
        val policies = LightspeedPreferences.getPerimeterServicePolicies(context)

        for (pCn in protectedCns) {
            val pCnLong = pCn.flattenToString()
            val pCnShort = pCn.flattenToShortString()

            // Skip uninstalled packages silently (no FlightLog, no revive, keep saved list)
            val isInstalled = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(pCn.packageName, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pCn.packageName, 0)
                }
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: Exception) {
                false
            }
            if (!isInstalled) {
                continue
            }

            // Fresh read for each protected component (no stale snapshot)
            val currentRaw = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val entries = currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }

            val alreadyIn = entries.any { entry ->
                val cn = ComponentName.unflattenFromString(entry)
                cn == pCn || entry.equals(pCnLong, ignoreCase = true) || entry.equals(pCnShort, ignoreCase = true)
            }

            val policy = policies[pCnLong] ?: PerimeterPolicy.NORMAL

            if (policy == PerimeterPolicy.KEEP_OFF) {
                if (alreadyIn) {
                    Log.i(TAG, "pulsePerimeterServices: $pCnLong policy is KEEP_OFF and enabled. Toggling off...")
                    toggleAccessibilityService(context, pCnLong, enable = false)
                    FlightLog.log("watchdog", true, "Perimeter policy KEEP_OFF: disabled $pCnLong")
                }
                previouslyEnabledServices[pCnLong] = false
                missingPulseCounts[pCnLong] = 0
                presumedToggleOffLogged.remove(pCnLong)
                continue
            }

            if (policy == PerimeterPolicy.ALWAYS_REVIVE) {
                if (alreadyIn) {
                    previouslyEnabledServices[pCnLong] = true
                    missingPulseCounts[pCnLong] = 0
                    presumedToggleOffLogged.remove(pCnLong)
                } else {
                    Log.i(TAG, "pulsePerimeterServices: $pCnLong policy is ALWAYS_REVIVE and missing. Attempting plain re-add...")
                    recordInternalWrite()
                    var revived = GriffinRecovery.plainReAdd(context, pCnLong, reason = "policy ALWAYS_REVIVE")
                    if (revived) {
                        if (GriffinRecovery.isCrashed(pCn.packageName)) {
                            Log.w(TAG, "pulsePerimeterServices: $pCnLong plain re-add failed to stick (crashed in dumpsys). Executing Griffin recovery...")
                            recordInternalWrite()
                            revived = GriffinRecovery.recover(context, pCn.packageName, pCnLong, skipDebounce = true)
                        }
                    } else if (!GriffinRecovery.isDebounced(pCnLong)) {
                        Log.w(TAG, "pulsePerimeterServices: $pCnLong plain re-add failed. Executing Griffin recovery...")
                        recordInternalWrite()
                        revived = GriffinRecovery.recover(context, pCn.packageName, pCnLong, skipDebounce = true)
                    }
                    if (revived) {
                        recoveredCount++
                        previouslyEnabledServices[pCnLong] = true
                        missingPulseCounts[pCnLong] = 0
                        presumedToggleOffLogged.remove(pCnLong)
                    }
                }
                continue
            }

            if (alreadyIn) {
                previouslyEnabledServices[pCnLong] = true
                missingPulseCounts[pCnLong] = 0
                presumedToggleOffLogged.remove(pCnLong)
            } else {
                if (GriffinRecovery.isCrashed(pCn.packageName)) {
                    Log.w(TAG, "Perimeter Sentinel alert: Protected service ${pCn.packageName} crashed! Executing Griffin recovery...")
                    recordInternalWrite()
                    val success = GriffinRecovery.recover(context, pCn.packageName, pCnLong)
                    if (success) {
                        recoveredCount++
                        previouslyEnabledServices[pCnLong] = true
                        missingPulseCounts[pCnLong] = 0
                        presumedToggleOffLogged.remove(pCnLong)
                    }
                } else {
                    val wasEnabled = previouslyEnabledServices[pCnLong] ?: false
                    val count = ((missingPulseCounts[pCnLong] ?: 0) + 1).coerceAtMost(3)
                    missingPulseCounts[pCnLong] = count

                    if (wasEnabled && count == 2) {
                        Log.i(TAG, "pulsePerimeterServices: $pCnLong missing on 2 consecutive pulses. Attempting plain re-add...")
                        recordInternalWrite()
                        val success = GriffinRecovery.plainReAdd(context, pCnLong)
                        if (success) {
                            recoveredCount++
                            previouslyEnabledServices[pCnLong] = true
                            missingPulseCounts[pCnLong] = 0
                            presumedToggleOffLogged.remove(pCnLong)
                        }
                    } else if (wasEnabled && count == 1) {
                        Log.i(TAG, "pulsePerimeterServices: $pCnLong missing on 1st pulse. Waiting for 2 consecutive pulses.")
                        FlightLog.log("watchdog", false, "Revive skip: $pCnLong (missing 1 pulse, awaiting confirmation)")
                    } else {
                        if (presumedToggleOffLogged.put(pCnLong, true) != true) {
                            Log.i(TAG, "pulsePerimeterServices: $pCnLong missing (count=$count, wasEnabled=$wasEnabled). Treating as user toggle-off.")
                            FlightLog.log("watchdog", false, "Revive skip: $pCnLong (presumed user toggle-off, count=$count)")
                        }
                    }
                }
            }
        }
        return getEnabledAccessibilityServices(context).size
    }

    /**
     * Starts background watchdog sentinel polling.
     * Skips cycles when the screen is off to avoid unnecessary CPU wakeups
     * and allow Android to enter deep sleep uninterrupted.
     */
    fun initSentinel(context: Context) {
        val prefs = context.defaultPrefs()
        val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false)
        if (!isEnabled) {
            stopSentinel()
            return
        }

        if (sentinelJob != null && sentinelJob?.isActive == true) {
            LightspeedGuardHelper.start(context)
            return
        }

        LightspeedGuardHelper.start(context)
        val isStopped = prefs.getBoolean("pref_service_intentionally_stopped", false)
        LightspeedGuardHelper.setPaused(context, isStopped)

        sentinelJob = watchdogScope.launch {
            while (isActive) {
                delay(30_000L) // Poll every 30 seconds
                try {
                    val sentinelActive = context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false)
                    if (!sentinelActive) break

                    // Skip entire cycle when screen is off — let Android enter deep sleep
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (pm?.isInteractive == false) continue

                    // 1. Core Watchdog: Lightspeed's own service (uses isCrashed and recover)
                    if (LightspeedAccessibilityService.instance == null) {
                        val isIntentionallyStopped = context.defaultPrefs().getBoolean("pref_service_intentionally_stopped", false)
                        if (!isIntentionallyStopped) {
                            if (GriffinRecovery.isCrashed(context.packageName)) {
                                Log.w(TAG, "Sentinel alert: Core service instance is null and crashed! Executing Griffin recovery...")
                                reviveAccessibilityService(context)
                            } else {
                                Log.i(TAG, "Sentinel poll: Core service instance is null but not crashed in dumpsys.")
                            }
                        }
                    }

                    // 2. Perimeter Watchdog: External protected accessibility services
                    val protectedStrings = LightspeedPreferences.getPerimeterProtectedServices(context)
                    if (protectedStrings.isNotEmpty()) {
                        pulsePerimeterServices(context)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sentinel poll cycle error", e)
                }
            }
        }
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
                proc.waitForOrKill()
                Log.i(TAG, "Reboot dispatched via Shizuku svc power reboot")
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku reboot error", e)
            }
        } else {
            ElevatedTaskCloser.toastShizukuNeeded(context, "Reboot")
        }

        Log.w(TAG, "Core Cooling reboot failed: Shizuku is not active")
        return false
    }

    fun stopSentinel() {
        sentinelJob?.cancel()
        sentinelJob = null
        missingPulseCounts.clear()
        previouslyEnabledServices.clear()
        presumedToggleOffLogged.clear()
        watchdogScope.cancel()
        watchdogScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
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
                } catch (e: Exception) { logSwallowed(TAG, "requestIgnoreBatteryOptimization:446", e) }
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
