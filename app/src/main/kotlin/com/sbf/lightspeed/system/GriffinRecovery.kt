package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * Griffin Recovery Engine.
 * Provides autonomous macro recovery recipes for crashed accessibility services
 * using Shizuku privileged shell execution and atomic secure settings updates.
 */
object GriffinRecovery {
    private const val TAG = "GriffinRecovery"
    private const val DEBOUNCE_MS = 60_000L

    private val lastRecoveryMap = ConcurrentHashMap<String, Long>()

    /**
     * Inspects `dumpsys accessibility` and returns true ONLY if [packageName] appears
     * under the "Crashed services" section.
     */
    fun isCrashed(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        if (!ElevatedTaskCloser.isShizukuActive) return false

        try {
            val process = ElevatedTaskCloser.execShizuku("dumpsys accessibility 2>&1") ?: return false
            val reader = process.inputStream.bufferedReader()
            var inCrashedSection = false
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                val l = line!!
                if (l.contains("Crashed services:", ignoreCase = true)) {
                    val content = l.substringAfter("Crashed services:", "").trim()
                    if (content.isNotEmpty() && content != "{}") {
                        if (containsPackage(content, packageName)) {
                            process.waitForOrKill()
                            return true
                        }
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
                    if (containsPackage(l, packageName)) {
                        process.waitForOrKill()
                        return true
                    }
                }
            }
            process.waitForOrKill()
        } catch (e: Exception) {
            Log.w(TAG, "isCrashed check failed for $packageName", e)
        }
        return false
    }

    private fun containsPackage(text: String, packageName: String): Boolean {
        val regex = Regex("""[a-zA-Z0-9_.]+(?:/[a-zA-Z0-9_.]+)?""")
        for (match in regex.findAll(text)) {
            val str = match.value
            val pkg = if (str.contains('/')) str.substringBefore('/') else str
            if (pkg.equals(packageName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if recovery for [componentString] is currently debounced (< 60s since last run).
     */
    fun isDebounced(componentString: String): Boolean {
        val targetCn = ComponentName.unflattenFromString(componentString)
        val svcLong = targetCn?.flattenToString() ?: componentString
        val now = System.currentTimeMillis()
        val lastRecovery = lastRecoveryMap[svcLong] ?: 0L
        return now - lastRecovery < DEBOUNCE_MS
    }

    /**
     * Executes Griffin macro recovery recipe for a crashed package/component:
     * 1. Records all currently enabled accessibility components of that package before force-stop
     * 2. `killall -9 <pkg>` and `am force-stop <pkg>`
     * 3. Removes recorded components from secure enabled_accessibility_services (OFF)
     * 4. Waits 500 ms
     * 5. Adds ALL of them back in one write (ON)
     *
     * Enforces a 60-second debounce per component.
     */
    fun recover(context: Context, packageName: String, componentString: String, skipDebounce: Boolean = false): Boolean {
        if (packageName.isBlank() || componentString.isBlank()) return false

        val targetCn = ComponentName.unflattenFromString(componentString)
        val svcLong = targetCn?.flattenToString() ?: componentString

        val now = System.currentTimeMillis()
        val lastRecovery = lastRecoveryMap[svcLong] ?: 0L
        if (!skipDebounce && now - lastRecovery < DEBOUNCE_MS) {
            val skipMsg = "Debounced recovery for $svcLong (last run ${now - lastRecovery}ms ago)"
            Log.i(TAG, skipMsg)
            FlightLog.log("watchdog", false, "Revive skip: $svcLong (debounced, ${now - lastRecovery}ms < 60s)")
            return false
        }
        lastRecoveryMap[svcLong] = now

        // Step 0: Record every enabled accessibility component of this package before force-stop
        val currentRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val packageComponents = LinkedHashSet<String>()
        for (entry in currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }) {
            val cn = ComponentName.unflattenFromString(entry)
            val entryPkg = cn?.packageName ?: if (entry.contains('/')) entry.substringBefore('/') else entry
            if (entryPkg.equals(packageName, ignoreCase = true)) {
                packageComponents.add(cn?.flattenToString() ?: entry)
            }
        }
        packageComponents.add(svcLong)

        Log.i(TAG, "Executing Griffin recovery recipe for pkg=$packageName component=$svcLong (all components: $packageComponents)")
        FlightLog.log("watchdog", true, "Revive attempt: $svcLong (crashed in dumpsys)")

        // Step 1 & 2: killall -9 <pkg> and am force-stop <pkg>
        if (ElevatedTaskCloser.isShizukuActive) {
            val killCmd = "killall -9 $packageName 2>/dev/null; am force-stop $packageName 2>/dev/null"
            ElevatedTaskCloser.execShizuku(killCmd)?.waitForOrKill()
        }

        // Step 3: Remove recorded components of this package from secure enabled_accessibility_services (OFF)
        toggleComponentsInSettings(context, packageComponents, packageName, enable = false)

        // Step 4: Wait 500 ms
        try {
            Thread.sleep(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // Step 5: Add ALL of them back (ON) in one write
        val success = toggleComponentsInSettings(context, packageComponents, packageName, enable = true)

        Log.i(TAG, "Griffin recovery complete for $packageName: success=$success")
        FlightLog.log("watchdog", success, "Revive result: $svcLong (crashed recovery ${if (success) "succeeded" else "failed"})")
        return success
    }

    /**
     * Executes plain re-add of a missing accessibility component without force-stopping the host package.
     * Merges the component into secure enabled_accessibility_services in one atomic write.
     * Enforces a 60-second debounce per component.
     */
    fun plainReAdd(context: Context, componentString: String, reason: String = "missing 2 consecutive pulses", skipDebounce: Boolean = false): Boolean {
        if (componentString.isBlank()) return false

        val targetCn = ComponentName.unflattenFromString(componentString)
        val svcLong = targetCn?.flattenToString() ?: componentString
        val packageName = targetCn?.packageName ?: if (componentString.contains('/')) componentString.substringBefore('/') else componentString

        val now = System.currentTimeMillis()
        val lastRecovery = lastRecoveryMap[svcLong] ?: 0L
        if (!skipDebounce && now - lastRecovery < DEBOUNCE_MS) {
            val skipMsg = "Debounced plain re-add for $svcLong (last run ${now - lastRecovery}ms ago)"
            Log.i(TAG, skipMsg)
            FlightLog.log("watchdog", false, "Revive skip: $svcLong (debounced, ${now - lastRecovery}ms < 60s)")
            return false
        }
        lastRecoveryMap[svcLong] = now

        Log.i(TAG, "Executing plain re-add for component=$svcLong pkg=$packageName")
        FlightLog.log("watchdog", true, "Revive attempt: $svcLong ($reason)")

        val success = toggleComponentsInSettings(context, setOf(svcLong), packageName, enable = true)

        Log.i(TAG, "Plain re-add complete for $svcLong: success=$success")
        FlightLog.log("watchdog", success, "Revive result: $svcLong (plain re-add ${if (success) "succeeded" else "failed"})")
        return success
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun toggleComponentsInSettings(
        context: Context,
        components: Set<String>,
        packageName: String,
        enable: Boolean
    ): Boolean {
        val targetCns = components.mapNotNull { ComponentName.unflattenFromString(it) }

        val currentRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val entries = currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

        entries.removeAll { entry ->
            val entryCn = ComponentName.unflattenFromString(entry)
            components.any { comp ->
                entry.equals(comp, ignoreCase = true) ||
                (entryCn != null && targetCns.contains(entryCn))
            }
        }

        if (enable) {
            for (comp in components) {
                val compCn = ComponentName.unflattenFromString(comp)
                val canonical = compCn?.flattenToString() ?: comp
                if (!entries.any { it.equals(canonical, ignoreCase = true) || (compCn != null && ComponentName.unflattenFromString(it) == compCn) }) {
                    entries.add(canonical)
                }
            }
        }

        val newServices = entries.joinToString(":")
        val a11yEnabled = if (entries.isNotEmpty()) 1 else 0

        LightspeedWatchdogEngine.recordInternalWrite()

        val canWrite = context.checkCallingOrSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (canWrite) {
            try {
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, a11yEnabled)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct Settings write failed in toggleComponentsInSettings", e)
            }
        }

        if (ElevatedTaskCloser.isShizukuActive) {
            val restrictedCmd = if (enable) "appops set $packageName ACCESS_RESTRICTED_SETTINGS allow 2>/dev/null; " else ""
            val cmd = "${restrictedCmd}settings put secure enabled_accessibility_services ${shellSingleQuote(newServices)} && settings put secure accessibility_enabled $a11yEnabled"
            val p = ElevatedTaskCloser.execShizuku(cmd)
            return p?.waitForOrKill() == 0
        }

        return false
    }
}
