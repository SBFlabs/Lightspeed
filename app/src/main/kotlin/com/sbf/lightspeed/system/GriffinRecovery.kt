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
     * Executes Griffin macro recovery recipe for a crashed package/component:
     * 1. `killall -9 <pkg>`
     * 2. `am force-stop <pkg>`
     * 3. Remove ONLY that component from secure enabled_accessibility_services (OFF)
     * 4. Wait 500 ms
     * 5. Add it back (ON)
     *
     * Enforces a 60-second debounce per package.
     */
    fun recover(context: Context, packageName: String, componentString: String): Boolean {
        if (packageName.isBlank() || componentString.isBlank()) return false

        val now = System.currentTimeMillis()
        val lastRecovery = lastRecoveryMap[packageName] ?: 0L
        if (now - lastRecovery < DEBOUNCE_MS) {
            Log.i(TAG, "Debounced recovery for $packageName (last run ${now - lastRecovery}ms ago)")
            return false
        }
        lastRecoveryMap[packageName] = now

        Log.i(TAG, "Executing Griffin recovery recipe for pkg=$packageName component=$componentString")

        val targetCn = ComponentName.unflattenFromString(componentString)
        val svcLong = targetCn?.flattenToString() ?: componentString
        val svcShort = targetCn?.flattenToShortString() ?: componentString

        // Step 1 & 2: killall -9 <pkg> and am force-stop <pkg>
        if (ElevatedTaskCloser.isShizukuActive) {
            val killCmd = "killall -9 $packageName 2>/dev/null; am force-stop $packageName 2>/dev/null"
            ElevatedTaskCloser.execShizuku(killCmd)?.waitForOrKill()
        }

        // Step 3: Remove ONLY that component from secure enabled_accessibility_services (OFF)
        toggleComponentInSettings(context, svcLong, svcShort, packageName, enable = false)

        // Step 4: Wait 500 ms
        try {
            Thread.sleep(500L)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }

        // Step 5: Add it back (ON)
        val success = toggleComponentInSettings(context, svcLong, svcShort, packageName, enable = true)

        Log.i(TAG, "Griffin recovery complete for $packageName: success=$success")
        return success
    }

    private fun shellSingleQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun toggleComponentInSettings(
        context: Context,
        svcLong: String,
        svcShort: String,
        packageName: String,
        enable: Boolean
    ): Boolean {
        val targetCn = ComponentName.unflattenFromString(svcLong)

        val currentRaw = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val entries = currentRaw.split(":").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()

        entries.removeAll { entry ->
            entry.equals(svcLong, ignoreCase = true) ||
            entry.equals(svcShort, ignoreCase = true) ||
            (targetCn != null && ComponentName.unflattenFromString(entry) == targetCn)
        }

        if (enable) {
            entries.add(svcLong)
        }

        val newServices = entries.joinToString(":")
        val a11yEnabled = if (entries.isNotEmpty()) 1 else 0

        val canWrite = context.checkCallingOrSelfPermission(
            android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (canWrite) {
            try {
                Settings.Secure.putString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, newServices)
                Settings.Secure.putInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, a11yEnabled)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Direct Settings write failed in toggleComponentInSettings", e)
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
