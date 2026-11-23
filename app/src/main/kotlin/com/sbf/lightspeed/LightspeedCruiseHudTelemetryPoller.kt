package com.sbf.lightspeed

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Background telemetry poller for Category Cruise HUD pods (UsageStats + battery/memory/storage/network snapshots).
 * Extracted from LightspeedCruiseHudPods.kt.
 */
internal object LightspeedCruiseHudTelemetryPoller {

    @Volatile
    private var attemptedShizukuUnplugSeed: Boolean = false

    /**
     * Seeds the last-unplug timestamp on Day 1 from `dumpsys batterystats` when Shizuku is active
     * so `LAST CHG` immediately reflects real on-battery duration instead of waiting for the next unplug.
     */
    private fun seedLastUnplugFromBatteryStats(nowMs: Long): Long {
        return try {
            val proc = com.sbf.lightspeed.system.ElevatedTaskCloser.execShizuku(
                "out=$(dumpsys batterystats --charged 2>/dev/null | grep -m 3 -E 'Time on battery:|Battery realtime:'); " +
                    "if [ -n \"\$out\" ]; then printf '%s\\n' \"\$out\"; " +
                    "else dumpsys batterystats 2>/dev/null | grep -m 3 -E 'Time on battery:|Battery realtime:'; fi"
            ) ?: return 0L
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            try { proc.destroy() } catch (_: Exception) {}
            val line = output.lineSequence().firstOrNull {
                it.contains("Time on battery:", ignoreCase = true) ||
                    it.contains("Battery realtime:", ignoreCase = true)
            } ?: return 0L
            val afterColon = line.substringAfter(':', "")
            val days = Regex("(\\d+)d").find(afterColon)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val hrs = Regex("(\\d+)h").find(afterColon)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val mins = Regex("(\\d+)m(?!s)").find(afterColon)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val secs = Regex("(\\d+)s").find(afterColon)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val elapsedMs = (days * 86_400_000L) + (hrs * 3_600_000L) + (mins * 60_000L) + (secs * 1_000L)
            if (elapsedMs in 60_000L..(30L * 86_400_000L)) {
                (nowMs - elapsedMs).coerceAtLeast(0L)
            } else 0L
        } catch (_: Throwable) {
            0L
        }
    }

    private fun ensureUnplugTimestampSeeded(context: Context, nowMs: Long) {
        if (LightspeedCruiseHudPods.lastUnplugTimestampMs > 0L) return
        val prefs = context.defaultPrefs()
        val savedUnplug = prefs.getLong(LightspeedPreferences.KEY_VITALS_LAST_UNPLUG_MS, 0L)
        if (savedUnplug > 0L && savedUnplug <= nowMs) {
            LightspeedCruiseHudPods.lastUnplugTimestampMs = savedUnplug
            LightspeedCruiseHudPods.hasObservedRealUnplug = true
            return
        }
        if (!attemptedShizukuUnplugSeed && com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive) {
            attemptedShizukuUnplugSeed = true
            val seededUnplug = seedLastUnplugFromBatteryStats(nowMs)
            if (seededUnplug > 0L && seededUnplug <= nowMs) {
                LightspeedCruiseHudPods.lastUnplugTimestampMs = seededUnplug
                LightspeedCruiseHudPods.hasObservedRealUnplug = true
                prefs.edit().putLong(LightspeedPreferences.KEY_VITALS_LAST_UNPLUG_MS, seededUnplug).apply()
            }
        }
    }

    private val uidPowerRegex = Regex(
        """^\s*uid\s+(?:u(\d+)a(\d+)|(\d+)):\s*([0-9]+(?:\.[0-9]+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val computedDrainRegex = Regex("""Computed\s+drain:\s*([0-9]+(?:\.[0-9]+)?)""", RegexOption.IGNORE_CASE)

    // Filter down to the Estimated power use UID summary (~9-25 KB) so Binder UserService never hits 1 MB IPC caps.
    private const val SHIZUKU_BATTERY_USAGE_CMD =
        "out=$(dumpsys batterystats --usage 2>/dev/null | grep -iE 'Estimated power use|Computed drain|actual drain|^[[:space:]]*uid[[:space:]]'); " +
            "if printf '%s\\n' \"\$out\" | grep -qiE '^[[:space:]]*uid[[:space:]]'; then " +
            "  printf '%s\\n' \"\$out\"; " +
            "else " +
            "  dumpsys batterystats --charged 2>/dev/null | grep -iE 'Estimated power use|Computed drain|actual drain|^[[:space:]]*uid[[:space:]]'; " +
            "fi"

    /**
     * Reads real unplugged per-app power estimates (in mAh & % drain) from `dumpsys batterystats` via Shizuku,
     * and seeds/accumulates [LightspeedShizukuBatteryDrainStore] across LAST CHG, TODAY, and 7-DAY spans.
     */
    private fun pollShizukuUnpluggedBatteryDrain(context: Context): List<LightspeedCruiseHudPods.AppBatteryDrainEntry> {
        if (!com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive) return emptyList()
        var proc: Process? = null
        return try {
            proc = com.sbf.lightspeed.system.ElevatedTaskCloser.execShizuku(SHIZUKU_BATTERY_USAGE_CMD)
                ?: return emptyList()
            val uidMahMap = HashMap<Int, Float>()
            var computedDrainMah = 0f
            var totalSectionMah = 0f
            var inEstimatedSection = false
            var sawAnyHeader = false

            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    if (line.contains("Estimated power use", ignoreCase = true)) {
                        sawAnyHeader = true
                        if (inEstimatedSection && (uidMahMap.isNotEmpty() || computedDrainMah > 0f)) {
                            break
                        }
                        inEstimatedSection = true
                        continue
                    }

                    if (!inEstimatedSection) {
                        if (computedDrainRegex.containsMatchIn(line) || uidPowerRegex.containsMatchIn(line)) {
                            inEstimatedSection = true
                        } else {
                            continue
                        }
                    }

                    computedDrainRegex.find(line)?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let { parsedComputed ->
                        if (parsedComputed > computedDrainMah) computedDrainMah = parsedComputed
                    }

                    val uidMatch = uidPowerRegex.find(line)
                    if (uidMatch != null) {
                        sawAnyHeader = true
                        val userStr = uidMatch.groupValues[1]
                        val appStr = uidMatch.groupValues[2]
                        val rawUidStr = uidMatch.groupValues[3]
                        val mah = uidMatch.groupValues[4].toFloatOrNull() ?: 0f
                        val uid = if (userStr.isNotEmpty() && appStr.isNotEmpty()) {
                            (userStr.toIntOrNull() ?: 0) * 100_000 + 10_000 + (appStr.toIntOrNull() ?: 0)
                        } else {
                            rawUidStr.toIntOrNull() ?: -1
                        }
                        if (mah > 0f && uid >= 0) {
                            totalSectionMah += mah
                            val appId = uid % 100_000
                            if (appId >= 10_000) {
                                uidMahMap[uid] = (uidMahMap[uid] ?: 0f) + mah
                            }
                        }
                    }
                }
            }

            if (uidMahMap.isEmpty()) {
                if (sawAnyHeader) LightspeedShizukuBatteryDrainStore.markLiveLastChargeEmpty()
                return emptyList()
            }
            val pm = context.packageManager
            val pkgMahMap = HashMap<String, Float>()
            val pkgLabelMap = HashMap<String, String>()

            for ((uid, mah) in uidMahMap) {
                val pkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null } ?: continue
                if (pkgs.isEmpty()) continue
                val primaryPkg = pkgs.firstOrNull { pkg ->
                    try { pm.getLaunchIntentForPackage(pkg) != null } catch (_: Exception) { false }
                } ?: pkgs[0]
                pkgMahMap[primaryPkg] = (pkgMahMap[primaryPkg] ?: 0f) + mah
                if (!pkgLabelMap.containsKey(primaryPkg)) {
                    val label = try {
                        pm.getApplicationLabel(pm.getApplicationInfo(primaryPkg, 0)).toString()
                    } catch (_: Exception) {
                        primaryPkg
                    }
                    pkgLabelMap[primaryPkg] = label
                }
            }

            if (pkgMahMap.isEmpty()) {
                LightspeedShizukuBatteryDrainStore.markLiveLastChargeEmpty()
                return emptyList()
            }
            val uidSumMah = pkgMahMap.values.sum()
            val denom = maxOf(computedDrainMah, totalSectionMah, uidSumMah).coerceAtLeast(0.1f)
            val observedCycleStartMs = if (LightspeedCruiseHudPods.hasObservedRealUnplug) {
                LightspeedCruiseHudPods.lastUnplugTimestampMs
            } else {
                0L
            }
            LightspeedShizukuBatteryDrainStore.ingestLiveSnapshot(
                context = context,
                pkgMahMap = pkgMahMap,
                pkgLabelMap = pkgLabelMap,
                totalDrainMah = denom,
                observedCycleStartMs = observedCycleStartMs
            )
        } catch (_: Throwable) {
            emptyList()
        } finally {
            try { proc?.destroy() } catch (_: Exception) {}
        }
    }

    fun refreshTelemetryAsync(
        context: Context,
        force: Boolean = false,
        activeCategoryId: String? = null,
        activeModes: Set<String>? = null
    ) {
        val needUsage = activeModes == null || activeModes.any {
            it == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME ||
                it == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM ||
                it == LightspeedCruiseHudPods.MODE_APP_AND_CAT
        }
        val needBattery = activeModes == null ||
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM in activeModes
        if (!needUsage && !needBattery) return

        val appCtx = context.applicationContext ?: context
        val now = System.currentTimeMillis()
        val activeSystemSpans = linkedSetOf("today", "7d")
        if (needBattery) {
            val batSlots = if (!activeCategoryId.isNullOrBlank()) {
                CategoryHudManager.getHudItems(appCtx, activeCategoryId)
                    .filter { it.type == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM }
                    .map { it.slotIndex }
                    .ifEmpty { listOf(1, 2, 3) }
            } else {
                listOf(1, 2, 3)
            }
            for (slot in batSlots) {
                val bMode = LightspeedCruiseHudPods.getBatteryMode(appCtx, activeCategoryId, slot)
                if (bMode == "system") {
                    val bSpan = LightspeedCruiseHudPods.getBatterySpan(appCtx, activeCategoryId, slot)
                    if (bSpan in setOf("1h", "3h", "today", "3d", "7d", "30d")) {
                        activeSystemSpans.add(bSpan)
                    }
                }
            }
        }
        val spansToFetch = mutableListOf<String>()
        if (needUsage && LightspeedUsageStats.hasUsageAccess(appCtx)) {
            val candidateSpans = mutableSetOf("1d")
            if (needBattery) {
                candidateSpans.add("7d")
                candidateSpans.add("last_charge")
                if ("1h" in activeSystemSpans) candidateSpans.add("1h")
                if ("3h" in activeSystemSpans) candidateSpans.add("3h")
                if ("3d" in activeSystemSpans) candidateSpans.add("3d")
                if ("30d" in activeSystemSpans) candidateSpans.add("30d")
            }
            if (activeModes == null || LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME in activeModes) {
                val screenTimeSlots = if (!activeCategoryId.isNullOrBlank()) {
                    CategoryHudManager.getHudItems(appCtx, activeCategoryId)
                        .filter { it.type == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME }
                        .map { it.slotIndex }
                        .ifEmpty { listOf(1, 2) }
                } else {
                    listOf(1, 2)
                }
                for (slot in screenTimeSlots) {
                    candidateSpans.add(LightspeedCruiseHudPods.getTimeSpan(appCtx, activeCategoryId, slot))
                }
            }
            for (span in candidateSpans) {
                if (span in LightspeedCruiseHudPods.inFlightUsageSpans) continue
                val lastSpanMs = LightspeedCruiseHudPods.lastUsageRefreshMsBySpan[span] ?: 0L
                val missingSpan = !LightspeedCruiseHudPods.usageCacheBySpan.containsKey(span)
                if (force || missingSpan || (now - lastSpanMs) >= 10_000L) {
                    spansToFetch.add(span)
                }
            }
        }

        val shizukuActive = com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive
        val drainCooldownMs = if (LightspeedCruiseHudPods.shizukuBatteryDrainApps.isNotEmpty()) 30_000L else 5_000L
        val shouldFetchShizukuDrain = needBattery &&
            shizukuActive &&
            !LightspeedCruiseHudPods.isRefreshingShizukuBatteryDrain &&
            (force || !LightspeedCruiseHudPods.hasShizukuBatteryDrainSnapshot || (now - LightspeedCruiseHudPods.lastShizukuBatteryDrainRefreshMs) >= drainCooldownMs)

        val shouldFetchBattery = needBattery &&
            !LightspeedCruiseHudPods.isRefreshingBattery &&
            (force || !LightspeedCruiseHudPods.hasBatterySnapshot || (now - LightspeedCruiseHudPods.lastBatteryRefreshMs) >= 5_000L)

        if (spansToFetch.isEmpty() && !shouldFetchBattery && !shouldFetchShizukuDrain) return

        LightspeedCruiseHudPods.inFlightUsageSpans.addAll(spansToFetch)
        if (shouldFetchBattery) {
            LightspeedCruiseHudPods.isRefreshingBattery = true
        }
        if (shouldFetchShizukuDrain) {
            LightspeedCruiseHudPods.isRefreshingShizukuBatteryDrain = true
        }
        LightspeedCruiseHudPods.isRefreshingTelemetry =
            LightspeedCruiseHudPods.isRefreshingBattery ||
                LightspeedCruiseHudPods.isRefreshingShizukuBatteryDrain ||
                LightspeedCruiseHudPods.inFlightUsageSpans.isNotEmpty()
        LightspeedCruiseHudPods.lastRefreshMs = now

        LightspeedCruiseHudPods.scope.launch {
            try {
                // Poll Battery/System Vitals first (~15ms) so Vitals/RAM/Storage never wait behind long UsageStats scans.
                if (shouldFetchBattery) {
                    try {
                        updateSystemVitalsSnapshot(appCtx, activeSystemSpans, force)
                        LightspeedCruiseHudPods.hasBatterySnapshot = true
                        LightspeedCruiseHudPods.lastBatteryRefreshMs = System.currentTimeMillis()
                    } finally {
                        LightspeedCruiseHudPods.isRefreshingBattery = false
                    }
                    if (spansToFetch.isNotEmpty() || shouldFetchShizukuDrain) {
                        LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
                    }
                }
                if (shouldFetchShizukuDrain) {
                    try {
                        ensureUnplugTimestampSeeded(appCtx, System.currentTimeMillis())
                        val polled = pollShizukuUnpluggedBatteryDrain(appCtx)
                        if (polled.isNotEmpty() || !LightspeedCruiseHudPods.hasShizukuBatteryDrainSnapshot) {
                            LightspeedCruiseHudPods.shizukuBatteryDrainApps = polled
                        }
                        LightspeedCruiseHudPods.hasShizukuBatteryDrainSnapshot = true
                        LightspeedCruiseHudPods.lastShizukuBatteryDrainRefreshMs = System.currentTimeMillis()
                    } finally {
                        LightspeedCruiseHudPods.isRefreshingShizukuBatteryDrain = false
                    }
                    LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
                }
                for (span in spansToFetch) {
                    try {
                        val fetchNow = System.currentTimeMillis()
                        if (span == "last_charge") {
                            ensureUnplugTimestampSeeded(appCtx, fetchNow)
                            val bootStart = (fetchNow - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                            val startMs = if (LightspeedCruiseHudPods.hasObservedRealUnplug && LightspeedCruiseHudPods.lastUnplugTimestampMs > 0L) {
                                LightspeedCruiseHudPods.lastUnplugTimestampMs
                            } else {
                                bootStart
                            }
                            LightspeedCruiseHudPods.usageCacheBySpan[span] =
                                LightspeedUsageStats.usageMillisSince(appCtx, startMs)
                            LightspeedCruiseHudPods.usageAvailableSpanMsBySpan[span] =
                                (fetchNow - startMs).coerceAtLeast(0L)
                        } else if (span == "1h") {
                            val fetchedMap = LightspeedUsageStats.usageMillisSince(appCtx, (fetchNow - 3_600_000L).coerceAtLeast(0L))
                            LightspeedCruiseHudPods.usageCacheBySpan[span] = fetchedMap
                            LightspeedCruiseHudPods.latestBatterySnapshot =
                                LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenOneHourMs = fetchedMap.values.sum())
                        } else if (span == "3h") {
                            val fetchedMap = LightspeedUsageStats.usageMillisSince(appCtx, (fetchNow - 3L * 3_600_000L).coerceAtLeast(0L))
                            LightspeedCruiseHudPods.usageCacheBySpan[span] = fetchedMap
                            LightspeedCruiseHudPods.latestBatterySnapshot =
                                LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenThreeHourMs = fetchedMap.values.sum())
                        } else {
                            val days = when (span) {
                                "1d" -> 1
                                "3d" -> 3
                                "7d" -> 7
                                "30d" -> 30
                                "365d" -> 365
                                "all" -> 3650
                                else -> 1
                            }
                            val fetchedMap = LightspeedUsageStats.usageMillisByPackage(appCtx, days)
                            LightspeedCruiseHudPods.usageCacheBySpan[span] = fetchedMap
                            if (span == "1d") {
                                LightspeedCruiseHudPods.latestBatterySnapshot =
                                    LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenTodayMs = fetchedMap.values.sum())
                            } else if (span == "3d") {
                                LightspeedCruiseHudPods.latestBatterySnapshot =
                                    LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenThreeDayMs = fetchedMap.values.sum())
                            } else if (span == "7d") {
                                LightspeedCruiseHudPods.latestBatterySnapshot =
                                    LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenSevenDayMs = fetchedMap.values.sum())
                            } else if (span == "30d") {
                                LightspeedCruiseHudPods.latestBatterySnapshot =
                                    LightspeedCruiseHudPods.latestBatterySnapshot.copy(screenThirtyDayMs = fetchedMap.values.sum())
                            }
                            if (days > 1 && days < 3650) {
                                LightspeedCruiseHudPods.usageAvailableSpanMsBySpan[span] =
                                    LightspeedUsageStats.getAvailableUsageSpanMs(appCtx, days)
                            }
                        }
                        LightspeedCruiseHudPods.lastUsageRefreshMsBySpan[span] = System.currentTimeMillis()
                    } catch (_: Exception) {
                    } finally {
                        LightspeedCruiseHudPods.inFlightUsageSpans.remove(span)
                    }
                }
            } catch (_: Exception) {
            } finally {
                LightspeedCruiseHudPods.isRefreshingTelemetry =
                    LightspeedCruiseHudPods.isRefreshingBattery ||
                        LightspeedCruiseHudPods.isRefreshingShizukuBatteryDrain ||
                        LightspeedCruiseHudPods.inFlightUsageSpans.isNotEmpty()
                LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
            }
        }
    }

    private fun updateSystemVitalsSnapshot(
        context: Context,
        activeSystemSpans: Set<String> = setOf("today"),
        forceNetworkRefresh: Boolean = false
    ) {
        val prefs = context.defaultPrefs()
        if (LightspeedCruiseHudPods.sessionPeakChargeWatts < 0f) {
            LightspeedCruiseHudPods.sessionPeakChargeWatts =
                prefs.getFloat(LightspeedPreferences.KEY_VITALS_PEAK_CHARGE_WATTS, -1f)
        }
        if (LightspeedCruiseHudPods.sessionMinChargeWatts < 0f) {
            LightspeedCruiseHudPods.sessionMinChargeWatts =
                prefs.getFloat(LightspeedPreferences.KEY_VITALS_MIN_CHARGE_WATTS, -1f)
        }

        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batIntent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) { null }

        val level = batIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = (batIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val batPct = if (level >= 0) ((level * 100f) / scale).roundToInt().coerceIn(0, 100) else 0
        val status = batIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val nowMs = System.currentTimeMillis()
        if (LightspeedCruiseHudPods.lastUnplugTimestampMs <= 0L) {
            val savedUnplug = prefs.getLong(LightspeedPreferences.KEY_VITALS_LAST_UNPLUG_MS, 0L)
            if (savedUnplug > 0L && savedUnplug <= nowMs) {
                LightspeedCruiseHudPods.lastUnplugTimestampMs = savedUnplug
                LightspeedCruiseHudPods.hasObservedRealUnplug = true
            }
        }
        val prevCharging = LightspeedCruiseHudPods.wasLastSnapshotCharging
        if (prevCharging == true && !isCharging) {
            LightspeedCruiseHudPods.lastUnplugTimestampMs = nowMs
            LightspeedCruiseHudPods.hasObservedRealUnplug = true
            prefs.edit().putLong(LightspeedPreferences.KEY_VITALS_LAST_UNPLUG_MS, nowMs).apply()
        }
        LightspeedCruiseHudPods.wasLastSnapshotCharging = isCharging
        val tempTenths = batIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) ?: 300
        val rawTempC = tempTenths / 10f
        val tempC = rawTempC.roundToInt()
        val voltageMv = (batIntent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 3850) ?: 3850).let {
            if (it in 1..20) it * 1000 else if (it <= 0) 3850 else it
        }
        val rawCurrent = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
        val amps = if (abs(rawCurrent) > 15_000) abs(rawCurrent) / 1_000_000f else abs(rawCurrent) / 1_000f
        val watts = (amps * (voltageMv / 1000f)).coerceIn(0f, 240f)
        val wattStr = if (watts > 0.1f) String.format(Locale.US, "%s%.1fW", if (isCharging) "+" else "-", watts) else "IDLE"

        LightspeedCruiseHudPods.wasChargingSession = isCharging
        if (isCharging && watts > 0.2f) {
            val newPeak = if (LightspeedCruiseHudPods.sessionPeakChargeWatts < 0f) watts else maxOf(LightspeedCruiseHudPods.sessionPeakChargeWatts, watts)
            val newMin = if (LightspeedCruiseHudPods.sessionMinChargeWatts < 0f) watts else minOf(LightspeedCruiseHudPods.sessionMinChargeWatts, watts)
            if (newPeak != LightspeedCruiseHudPods.sessionPeakChargeWatts || newMin != LightspeedCruiseHudPods.sessionMinChargeWatts) {
                LightspeedCruiseHudPods.sessionPeakChargeWatts = newPeak
                LightspeedCruiseHudPods.sessionMinChargeWatts = newMin
                prefs.edit()
                    .putFloat(LightspeedPreferences.KEY_VITALS_PEAK_CHARGE_WATTS, LightspeedCruiseHudPods.sessionPeakChargeWatts)
                    .putFloat(LightspeedPreferences.KEY_VITALS_MIN_CHARGE_WATTS, LightspeedCruiseHudPods.sessionMinChargeWatts)
                    .apply()
            }
        } else if (!isCharging && watts > 0.1f) {
            LightspeedCruiseHudPods.sessionPeakDischargeWatts =
                if (LightspeedCruiseHudPods.sessionPeakDischargeWatts < 0f) watts else maxOf(LightspeedCruiseHudPods.sessionPeakDischargeWatts, watts)
        }

        val chgPeakMinStr = when {
            LightspeedCruiseHudPods.sessionPeakChargeWatts > 0f -> {
                val pStr = String.format(Locale.US, "+%.1fW", LightspeedCruiseHudPods.sessionPeakChargeWatts)
                val mStr = String.format(Locale.US, "+%.1fW", LightspeedCruiseHudPods.sessionMinChargeWatts.coerceAtLeast(0.1f))
                "$pStr / $mStr"
            }
            LightspeedCruiseHudPods.sessionPeakDischargeWatts > 0f || watts > 0.1f -> {
                val pDis = maxOf(LightspeedCruiseHudPods.sessionPeakDischargeWatts.coerceAtLeast(0.1f), watts.coerceAtLeast(0.1f))
                val cDis = watts.coerceAtLeast(0.1f)
                String.format(Locale.US, "-%.1fW / -%.1fW", pDis, cDis)
            }
            else -> "IDLE"
        }

        // Current charge (mAh) via BATTERY_PROPERTY_CHARGE_COUNTER (µAh -> mAh)
        val rawChargeCounter = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER) ?: Int.MIN_VALUE
        val chargeMah = if (rawChargeCounter > 0 && rawChargeCounter != Int.MIN_VALUE) {
            (rawChargeCounter / 1000f).roundToInt()
        } else {
            -1
        }
        val designCapMah = resolveDesignBatteryCapacityMah(context)

        // Health status + Cycle count
        val healthCode = batIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
            ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        val healthLabel = when (healthCode) {
            BatteryManager.BATTERY_HEALTH_GOOD -> if (tempC >= 38) "WARM" else "GOOD"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "OVERHEAT"
            BatteryManager.BATTERY_HEALTH_DEAD -> "DEAD"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "OVER VOLT"
            BatteryManager.BATTERY_HEALTH_COLD -> "COLD"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "FAILURE"
            else -> "UNKNOWN"
        }
        val cycleExtra = if (Build.VERSION.SDK_INT >= 34) {
            sanitizeCycleCount(batIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1)
        } else {
            -1
        }
        val cycleCount = if (cycleExtra > 0) cycleExtra else readSysfsBatteryCycleCount()

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)
        val usedGb = (memInfo.totalMem - memInfo.availMem).toDouble() / (1024.0 * 1024.0 * 1024.0)
        val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
        val ramPct = if (memInfo.totalMem > 0) (((memInfo.totalMem - memInfo.availMem) * 100L) / memInfo.totalMem).toInt() else 0

        val dataStat = StatFs(Environment.getDataDirectory().path)
        val totalIntBytes = dataStat.totalBytes
        val freeIntBytes = dataStat.availableBytes
        val usedIntBytes = (totalIntBytes - freeIntBytes).coerceAtLeast(0L)
        val freeIntGb = (freeIntBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
        val totalIntGb = (totalIntBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
        val usedIntGb = (usedIntBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()

        var extStorageInfo: LightspeedCruiseHudPods.ExternalStorageInfo? = null

        // 1. Check StorageManager for mounted physical SD Card or USB OTG Hard Drive / Flash Drive
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null) {
                for (vol in sm.storageVolumes) {
                    if (vol.isPrimary) continue
                    val state = vol.state
                    if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) continue

                    val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        vol.directory
                    } else {
                        try {
                            val getPath = vol.javaClass.getMethod("getPath")
                            (getPath.invoke(vol) as? String)?.let { File(it) }
                        } catch (_: Exception) { null }
                    }

                    if (dir != null && dir.exists()) {
                        try {
                            val stat = StatFs(dir.path)
                            val tBytes = stat.totalBytes
                            val fBytes = stat.availableBytes
                            if (tBytes > 0L) {
                                val uBytes = (tBytes - fBytes).coerceAtLeast(0L)
                                val desc = vol.getDescription(context)?.trim() ?: ""
                                val isUsb = desc.contains("USB", ignoreCase = true) ||
                                    desc.contains("OTG", ignoreCase = true) ||
                                    dir.path.contains("usb", ignoreCase = true) ||
                                    dir.path.contains("otg", ignoreCase = true)
                                val label = when {
                                    isUsb -> "USB OTG"
                                    desc.contains("SD", ignoreCase = true) || dir.path.contains("sdcard", ignoreCase = true) -> "SD CARD"
                                    desc.isNotBlank() -> desc.uppercase()
                                    vol.isRemovable -> "SD / OTG"
                                    else -> "EXT DRIVE"
                                }
                                val tGb = (tBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                val fGb = (fBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                val uGb = (uBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                extStorageInfo = LightspeedCruiseHudPods.ExternalStorageInfo(
                                    label = label,
                                    totalBytes = tBytes,
                                    usedBytes = uBytes,
                                    freeBytes = fBytes,
                                    totalGb = tGb,
                                    usedGb = uGb,
                                    freeGb = fGb
                                )
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback via getExternalFilesDirs if StorageManager scan returned nothing
        if (extStorageInfo == null) {
            try {
                val extDirs = context.getExternalFilesDirs(null)
                if (extDirs != null && extDirs.size > 1) {
                    for (i in 1 until extDirs.size) {
                        val extDir = extDirs[i] ?: continue
                        val state = Environment.getExternalStorageState(extDir)
                        if (state != Environment.MEDIA_MOUNTED && state != Environment.MEDIA_MOUNTED_READ_ONLY) continue
                        try {
                            val stat = StatFs(extDir.path)
                            val tBytes = stat.totalBytes
                            val fBytes = stat.availableBytes
                            if (tBytes > 0L) {
                                val uBytes = (tBytes - fBytes).coerceAtLeast(0L)
                                val isUsb = extDir.path.contains("usb", ignoreCase = true) || extDir.path.contains("otg", ignoreCase = true)
                                val label = if (isUsb) "USB OTG" else "SD CARD"
                                val tGb = (tBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                val fGb = (fBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                val uGb = (uBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)).roundToInt()
                                extStorageInfo = LightspeedCruiseHudPods.ExternalStorageInfo(
                                    label = label,
                                    totalBytes = tBytes,
                                    usedBytes = uBytes,
                                    freeBytes = fBytes,
                                    totalGb = tGb,
                                    usedGb = uGb,
                                    freeGb = fGb
                                )
                                break
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }

        val freeSdGb = extStorageInfo?.freeGb

        // Real counters only: /proc/meminfo swap, uptime vs elapsed realtime, TrafficStats since boot.
        var swapTotalBytes = 0L
        var swapFreeBytes = 0L
        try {
            File("/proc/meminfo").forEachLine { line ->
                when {
                    line.startsWith("SwapTotal:") -> swapTotalBytes = parseMeminfoKb(line) * 1024L
                    line.startsWith("SwapFree:") -> swapFreeBytes = parseMeminfoKb(line) * 1024L
                }
            }
        } catch (_: Exception) {}
        val elapsed = android.os.SystemClock.elapsedRealtime().coerceAtLeast(1L)
        val awakePct = ((android.os.SystemClock.uptimeMillis() * 100L) / elapsed).toInt().coerceIn(0, 100)
        val mobileRx = android.net.TrafficStats.getMobileRxBytes()
        val mobileTx = android.net.TrafficStats.getMobileTxBytes()
        val totalRx = android.net.TrafficStats.getTotalRxBytes()
        val totalTx = android.net.TrafficStats.getTotalTxBytes()
        val mobileSupported = mobileRx >= 0L && mobileTx >= 0L
        val totalSupported = totalRx >= 0L && totalTx >= 0L
        val safeMobRx = if (mobileSupported) mobileRx else 0L
        val safeMobTx = if (mobileSupported) mobileTx else 0L
        val mobileBytes = if (mobileSupported) safeMobRx + safeMobTx else -1L
        val wifiRxBytes = if (totalSupported) (totalRx - safeMobRx).coerceAtLeast(0L) else -1L
        val wifiTxBytes = if (totalSupported) (totalTx - safeMobTx).coerceAtLeast(0L) else -1L
        val wifiBytes = if (totalSupported) wifiRxBytes + wifiTxBytes else -1L

        val oneHourMap = LightspeedCruiseHudPods.usageCacheBySpan["1h"] ?: emptyMap()
        val threeHourMap = LightspeedCruiseHudPods.usageCacheBySpan["3h"] ?: emptyMap()
        val sevenDayMap = LightspeedCruiseHudPods.usageCacheBySpan["7d"] ?: emptyMap()
        val sevenDayDailyAvgHrs = (sevenDayMap.values.sum() / 3_600_000.0) / 7.0
        val todayMap = LightspeedCruiseHudPods.usageCacheBySpan["1d"] ?: emptyMap()
        val threeDayMap = LightspeedCruiseHudPods.usageCacheBySpan["3d"] ?: emptyMap()
        val thirtyDayMap = LightspeedCruiseHudPods.usageCacheBySpan["30d"] ?: emptyMap()
        val sinceChargeScrn = LightspeedCruiseHudPods.formatDurationCompact(todayMap.values.sum())

        for (spanKey in activeSystemSpans) {
            LightspeedNetworkUsageAnalyzer.refreshDeviceTotalsIfNeeded(context, spanKey, forceNetworkRefresh)
        }

        val capMahForEst = if (designCapMah > 0) designCapMah else 5000
        val currentMahForEst = if (chargeMah > 0) chargeMah.toFloat() else (capMahForEst * (batPct / 100f))
        val currentMaForEst = amps * 1000f
        val estRemainingStr = if (isCharging) {
            val sysChargeRemMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                try { bm?.computeChargeTimeRemaining() ?: -1L } catch (_: Exception) { -1L }
            } else -1L
            if (sysChargeRemMs > 60_000L) {
                "FULL ~${LightspeedCruiseHudPods.formatDurationCompact(sysChargeRemMs)}"
            } else if (batPct >= 100) {
                "CHARGED 100%"
            } else {
                val missingMah = (capMahForEst - currentMahForEst).coerceAtLeast(50f)
                val effChargeMa = if (currentMaForEst > 150f) currentMaForEst else 1800f
                val hrs = (missingMah / effChargeMa).coerceIn(0.05f, 12f)
                "FULL ~${LightspeedCruiseHudPods.formatDurationCompact((hrs * 3_600_000f).toLong())}"
            }
        } else {
            val effDrawMa = if (currentMaForEst in 90f..4500f) currentMaForEst else 360f
            val hrs = (currentMahForEst / effDrawMa).coerceIn(0.1f, 72f)
            "~${LightspeedCruiseHudPods.formatDurationCompact((hrs * 3_600_000f).toLong())}"
        }

        LightspeedCruiseHudPods.latestBatterySnapshot = LightspeedCruiseHudPods.BatterySnapshot(
            levelPct = batPct,
            isCharging = isCharging,
            wattageStr = wattStr,
            tempC = tempC,
            voltageMv = voltageMv,
            usedRamGb = usedGb,
            totalRamGb = totalGb,
            ramPct = ramPct,
            freeIntGb = freeIntGb,
            totalIntGb = totalIntGb,
            usedIntGb = usedIntGb,
            totalIntBytes = totalIntBytes,
            usedIntBytes = usedIntBytes,
            freeIntBytes = freeIntBytes,
            freeSdGb = freeSdGb,
            externalStorage = extStorageInfo,
            sinceChargeScrn = sinceChargeScrn,
            sevenDayDailyAvgHrs = sevenDayDailyAvgHrs,
            swapUsedBytes = (swapTotalBytes - swapFreeBytes).coerceAtLeast(0L),
            swapTotalBytes = swapTotalBytes,
            awakePct = awakePct,
            mobileBytes = mobileBytes,
            wifiBytes = wifiBytes,
            mobileRxBytes = if (mobileSupported) safeMobRx else -1L,
            mobileTxBytes = if (mobileSupported) safeMobTx else -1L,
            wifiRxBytes = wifiRxBytes,
            wifiTxBytes = wifiTxBytes,
            screenOneHourMs = oneHourMap.values.sum(),
            screenThreeHourMs = threeHourMap.values.sum(),
            screenTodayMs = todayMap.values.sum(),
            screenThreeDayMs = threeDayMap.values.sum(),
            screenSevenDayMs = sevenDayMap.values.sum(),
            screenThirtyDayMs = thirtyDayMap.values.sum(),
            chargeMah = chargeMah,
            designCapacityMah = designCapMah,
            healthLabel = healthLabel,
            cycleCount = cycleCount,
            peakChargeWatts = LightspeedCruiseHudPods.sessionPeakChargeWatts,
            minChargeWatts = LightspeedCruiseHudPods.sessionMinChargeWatts,
            chgPeakMinStr = chgPeakMinStr,
            estRemainingStr = estRemainingStr
        )
        LightspeedVitalsHistoryStore.recordSampleIfDue(
            context = context,
            tempC = tempC,
            ramUsedMb = (usedGb * 1024.0).toInt(),
            swapUsedMb = (LightspeedCruiseHudPods.latestBatterySnapshot.swapUsedBytes / (1024L * 1024L)).toInt()
        )
        RefuelingTelemetryHistoryBuffer.recordFromBackground(
            context = context,
            wattage = if (isCharging) watts else 0f,
            tempC = rawTempC,
            isCharging = isCharging
        )
        LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
    }

    private fun parseMeminfoKb(line: String): Long =
        line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L

    internal fun resolveDesignBatteryCapacityMah(context: Context): Int {
        if (LightspeedCruiseHudPods.cachedDesignCapacityMah > 0) return LightspeedCruiseHudPods.cachedDesignCapacityMah
        // 1. PowerProfile via reflection
        try {
            val cls = Class.forName("com.android.internal.os.PowerProfile")
            val ctor = cls.getConstructor(Context::class.java)
            val profile = ctor.newInstance(context)
            val cap = (cls.getMethod("getBatteryCapacity").invoke(profile) as? Double)?.roundToInt() ?: 0
            if (cap in 500..25_000) {
                LightspeedCruiseHudPods.cachedDesignCapacityMah = cap
                return cap
            }
        } catch (_: Exception) {}
        // 2. Sysfs fallback (µAh -> mAh)
        val sysfsPaths = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/bms/charge_full_design"
        )
        for (path in sysfsPaths) {
            try {
                val raw = File(path).readText().trim().toLongOrNull() ?: continue
                val mah = if (raw > 100_000L) (raw / 1000L).toInt() else raw.toInt()
                if (mah in 500..25_000) {
                    LightspeedCruiseHudPods.cachedDesignCapacityMah = mah
                    return mah
                }
            } catch (_: Exception) {}
        }
        return -1
    }

    /**
     * Rejects uncalibrated/sub-unit kernel cycle counters (e.g., 0 or >2,500) so impossible values
     * fall back cleanly to plain health status instead of misleading cycle readouts.
     */
    private fun sanitizeCycleCount(raw: Int): Int = if (raw in 1..2500) raw else -1

    private fun readSysfsBatteryCycleCount(): Int {
        val sysfsPaths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle"
        )
        for (path in sysfsPaths) {
            try {
                val raw = File(path).readText().trim().toIntOrNull() ?: continue
                val clean = sanitizeCycleCount(raw)
                if (clean > 0) return clean
            } catch (_: Exception) {}
        }
        return -1
    }
}
