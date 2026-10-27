package com.sbf.lightspeed.system

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LightspeedWatchdog"

internal fun executePerimeterPulse(
    context: Context,
    missingPulseCounts: ConcurrentHashMap<String, Int>,
    previouslyEnabledServices: ConcurrentHashMap<String, Boolean>,
    presumedToggleOffLogged: ConcurrentHashMap<String, Boolean>
): Int {
    val hasElevated = ElevatedTaskCloser.isShizukuActive
    val hasDirectWritePermission = context.checkCallingOrSelfPermission(
        android.Manifest.permission.WRITE_SECURE_SETTINGS
    ) == PackageManager.PERMISSION_GRANTED

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
    var crashedPackagesCache: Set<String>? = null
    fun getCrashedSet(): Set<String> {
        if (crashedPackagesCache == null) {
            crashedPackagesCache = GriffinRecovery.getCrashedAccessibilityPackages()
        }
        return crashedPackagesCache!!
    }

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
                LightspeedWatchdogEngine.toggleAccessibilityService(context, pCnLong, enable = false)
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
                LightspeedWatchdogEngine.recordInternalWrite()
                var revived = GriffinRecovery.plainReAdd(context, pCnLong, reason = "policy ALWAYS_REVIVE")
                if (revived) {
                    if (getCrashedSet().contains(pCn.packageName.lowercase())) {
                        Log.w(TAG, "pulsePerimeterServices: $pCnLong plain re-add failed to stick (crashed in dumpsys). Executing Griffin recovery...")
                        LightspeedWatchdogEngine.recordInternalWrite()
                        revived = GriffinRecovery.recover(context, pCn.packageName, pCnLong, skipDebounce = true)
                    }
                } else if (!GriffinRecovery.isDebounced(pCnLong)) {
                    Log.w(TAG, "pulsePerimeterServices: $pCnLong plain re-add failed. Executing Griffin recovery...")
                    LightspeedWatchdogEngine.recordInternalWrite()
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
            val wasEnabled = previouslyEnabledServices[pCnLong] ?: false
            if (!wasEnabled) {
                if (presumedToggleOffLogged.putIfAbsent(pCnLong, true) == null) {
                    Log.i(TAG, "pulsePerimeterServices: $pCnLong missing. Treating as user toggle-off.")
                    FlightLog.log("watchdog", false, "Revive skip: $pCnLong (presumed user toggle-off)")
                }
                continue
            }

            val count = (missingPulseCounts[pCnLong] ?: 0) + 1
            missingPulseCounts[pCnLong] = count

            if (count > 2) {
                previouslyEnabledServices[pCnLong] = false
                if (presumedToggleOffLogged.putIfAbsent(pCnLong, true) == null) {
                    Log.i(TAG, "pulsePerimeterServices: $pCnLong missing (count=$count). Presumed user toggle-off.")
                    FlightLog.log("watchdog", false, "Revive skip: $pCnLong (presumed user toggle-off)")
                }
                continue
            }

            if (getCrashedSet().contains(pCn.packageName.lowercase())) {
                Log.w(TAG, "Perimeter Sentinel alert: Protected service ${pCn.packageName} crashed! Executing Griffin recovery...")
                LightspeedWatchdogEngine.recordInternalWrite()
                val success = GriffinRecovery.recover(context, pCn.packageName, pCnLong)
                if (success) {
                    recoveredCount++
                    previouslyEnabledServices[pCnLong] = true
                    missingPulseCounts[pCnLong] = 0
                    presumedToggleOffLogged.remove(pCnLong)
                }
            } else if (count == 2) {
                Log.i(TAG, "pulsePerimeterServices: $pCnLong missing on 2 consecutive pulses. Attempting plain re-add...")
                LightspeedWatchdogEngine.recordInternalWrite()
                val success = GriffinRecovery.plainReAdd(context, pCnLong)
                if (success) {
                    recoveredCount++
                    previouslyEnabledServices[pCnLong] = true
                    missingPulseCounts[pCnLong] = 0
                    presumedToggleOffLogged.remove(pCnLong)
                }
            } else if (count == 1) {
                Log.i(TAG, "pulsePerimeterServices: $pCnLong missing on 1st pulse. Waiting for 2 consecutive pulses.")
                FlightLog.log("watchdog", false, "Revive skip: $pCnLong (missing 1 pulse, awaiting confirmation)")
            }
        }
    }
    return LightspeedWatchdogEngine.getEnabledAccessibilityServices(context).size
}
