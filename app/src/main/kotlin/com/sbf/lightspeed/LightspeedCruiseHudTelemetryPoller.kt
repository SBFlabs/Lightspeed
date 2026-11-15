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
        val spansToFetch = mutableListOf<String>()
        if (needUsage && LightspeedUsageStats.hasUsageAccess(appCtx)) {
            val candidateSpans = mutableSetOf("1d")
            if (needBattery) {
                candidateSpans.add("7d")
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

        val shouldFetchBattery = needBattery &&
            !LightspeedCruiseHudPods.isRefreshingBattery &&
            (force || !LightspeedCruiseHudPods.hasBatterySnapshot || (now - LightspeedCruiseHudPods.lastBatteryRefreshMs) >= 5_000L)

        if (spansToFetch.isEmpty() && !shouldFetchBattery) return

        LightspeedCruiseHudPods.inFlightUsageSpans.addAll(spansToFetch)
        if (shouldFetchBattery) {
            LightspeedCruiseHudPods.isRefreshingBattery = true
        }
        LightspeedCruiseHudPods.isRefreshingTelemetry =
            LightspeedCruiseHudPods.isRefreshingBattery || LightspeedCruiseHudPods.inFlightUsageSpans.isNotEmpty()
        LightspeedCruiseHudPods.lastRefreshMs = now

        LightspeedCruiseHudPods.scope.launch {
            try {
                // Poll Battery/System Vitals first (~15ms) so Vitals/RAM/Storage never wait behind long UsageStats scans.
                if (shouldFetchBattery) {
                    try {
                        updateSystemVitalsSnapshot(appCtx)
                        LightspeedCruiseHudPods.hasBatterySnapshot = true
                        LightspeedCruiseHudPods.lastBatteryRefreshMs = System.currentTimeMillis()
                    } finally {
                        LightspeedCruiseHudPods.isRefreshingBattery = false
                    }
                    if (spansToFetch.isNotEmpty()) {
                        LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
                    }
                }
                for (span in spansToFetch) {
                    try {
                        val days = when (span) {
                            "1d" -> 1
                            "7d" -> 7
                            "30d" -> 30
                            "365d" -> 365
                            "all" -> 3650
                            else -> 1
                        }
                        LightspeedCruiseHudPods.usageCacheBySpan[span] =
                            LightspeedUsageStats.usageMillisByPackage(appCtx, days)
                        LightspeedCruiseHudPods.lastUsageRefreshMsBySpan[span] = System.currentTimeMillis()
                    } catch (_: Exception) {
                    } finally {
                        LightspeedCruiseHudPods.inFlightUsageSpans.remove(span)
                    }
                }
            } catch (_: Exception) {
            } finally {
                LightspeedCruiseHudPods.isRefreshingTelemetry =
                    LightspeedCruiseHudPods.isRefreshingBattery || LightspeedCruiseHudPods.inFlightUsageSpans.isNotEmpty()
                LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
            }
        }
    }

    private fun updateSystemVitalsSnapshot(context: Context) {
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
            batIntent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
        } else {
            -1
        }
        val cycleCount = if (cycleExtra >= 0) cycleExtra else readSysfsBatteryCycleCount()

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
        val netSupported = mobileRx != android.net.TrafficStats.UNSUPPORTED.toLong() &&
            totalRx != android.net.TrafficStats.UNSUPPORTED.toLong()
        val mobileBytes = if (netSupported) mobileRx + mobileTx else -1L
        val wifiBytes = if (netSupported) ((totalRx + totalTx) - (mobileRx + mobileTx)).coerceAtLeast(0L) else -1L
        val wifiRxBytes = if (netSupported) (totalRx - mobileRx).coerceAtLeast(0L) else -1L
        val wifiTxBytes = if (netSupported) (totalTx - mobileTx).coerceAtLeast(0L) else -1L

        val sevenDayMap = LightspeedCruiseHudPods.usageCacheBySpan["7d"] ?: emptyMap()
        val sevenDayDailyAvgHrs = (sevenDayMap.values.sum() / 3_600_000.0) / 7.0
        val todayMap = LightspeedCruiseHudPods.usageCacheBySpan["1d"] ?: emptyMap()
        val sinceChargeScrn = LightspeedCruiseHudPods.formatDurationCompact(todayMap.values.sum())

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
            mobileRxBytes = if (netSupported) mobileRx.coerceAtLeast(0L) else -1L,
            mobileTxBytes = if (netSupported) mobileTx.coerceAtLeast(0L) else -1L,
            wifiRxBytes = wifiRxBytes,
            wifiTxBytes = wifiTxBytes,
            screenTodayMs = todayMap.values.sum(),
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

    private fun readSysfsBatteryCycleCount(): Int {
        val sysfsPaths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/battery/battery_cycle"
        )
        for (path in sysfsPaths) {
            try {
                val raw = File(path).readText().trim().toIntOrNull() ?: continue
                if (raw >= 0) return raw
            } catch (_: Exception) {}
        }
        return -1
    }
}
