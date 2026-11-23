package com.sbf.lightspeed

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import com.sbf.lightspeed.system.LightspeedUsageStats
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * 100% offline Network Usage Analyzer backed by Android [NetworkStatsManager].
 * Uses ONLY `PACKAGE_USAGE_STATS` (already granted for Screen Time) with zero `INTERNET`,
 * `ACCESS_NETWORK_STATE`, or `READ_PHONE_STATE` permissions.
 */
internal object LightspeedNetworkUsageAnalyzer {

    private const val CACHE_TTL_MS = 20_000L

    val SPAN_OPTIONS = listOf(
        "1H" to "1H",
        "3H" to "3H",
        "TODAY" to "TODAY",
        "3D" to "3D",
        "7D" to "7D",
        "30D" to "30D"
    )

    val TRANSPORT_OPTIONS = listOf(
        "ALL" to "ALL",
        "WIFI" to "WI-FI",
        "CELL" to "CELL"
    )

    val WIFI_ONLY_TRANSPORT_OPTIONS = listOf(
        "ALL" to "ALL",
        "WIFI" to "WI-FI"
    )

    @Volatile
    private var cachedHasCellular: Boolean? = null

    fun hasCellularCapability(context: Context): Boolean {
        cachedHasCellular?.let { return it }
        val pm = context.applicationContext?.packageManager ?: context.packageManager
        val supported = try {
            pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY) ||
                pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_GSM) ||
                pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_CDMA) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    (pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_DATA) ||
                        pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)))
        } catch (_: Throwable) {
            true
        }
        cachedHasCellular = supported
        return supported
    }

    fun transportOptionsFor(hasCellular: Boolean): List<Pair<String, String>> =
        if (hasCellular) TRANSPORT_OPTIONS else WIFI_ONLY_TRANSPORT_OPTIONS

    data class DeviceNetworkTotals(
        val spanKey: String,
        val wifiRxBytes: Long,
        val wifiTxBytes: Long,
        val cellRxBytes: Long,
        val cellTxBytes: Long,
        val hasUsageAccess: Boolean,
        val queriedAtMs: Long
    ) {
        val wifiTotalBytes: Long get() = wifiRxBytes + wifiTxBytes
        val cellTotalBytes: Long get() = cellRxBytes + cellTxBytes
        val hasRealData: Boolean get() = hasUsageAccess && queriedAtMs > 0L
    }

    data class AppNetworkUsageEntry(
        val uid: Int,
        val packageName: String,
        val label: String,
        val wifiRxBytes: Long,
        val wifiTxBytes: Long,
        val cellRxBytes: Long,
        val cellTxBytes: Long,
        val isSystemSpecial: Boolean = false
    ) {
        val wifiTotalBytes: Long get() = wifiRxBytes + wifiTxBytes
        val cellTotalBytes: Long get() = cellRxBytes + cellTxBytes
        val totalBytes: Long get() = wifiTotalBytes + cellTotalBytes

        fun totalForTransport(filter: String): Long = when (filter) {
            "WIFI" -> wifiTotalBytes
            "CELL" -> cellTotalBytes
            else -> totalBytes
        }

        fun rxForTransport(filter: String): Long = when (filter) {
            "WIFI" -> wifiRxBytes
            "CELL" -> cellRxBytes
            else -> wifiRxBytes + cellRxBytes
        }

        fun txForTransport(filter: String): Long = when (filter) {
            "WIFI" -> wifiTxBytes
            "CELL" -> cellTxBytes
            else -> wifiTxBytes + cellTxBytes
        }
    }

    data class NetworkUsageSnapshot(
        val spanKey: String,
        val hasUsageAccess: Boolean,
        val deviceWifiRx: Long,
        val deviceWifiTx: Long,
        val deviceCellRx: Long,
        val deviceCellTx: Long,
        val entries: List<AppNetworkUsageEntry>,
        val queriedAtMs: Long
    )

    private val fullSnapshotCache = ConcurrentHashMap<String, NetworkUsageSnapshot>()
    private val deviceTotalsCache = ConcurrentHashMap<String, DeviceNetworkTotals>()

    fun mapSystemHudSpanToAnalyzerSpan(hudSpan: String): String = when (hudSpan) {
        "1h" -> "1H"
        "3h" -> "3H"
        "today" -> "TODAY"
        "3d" -> "3D"
        "7d" -> "7D"
        "30d" -> "30D"
        else -> "TODAY"
    }

    fun getCachedDeviceTotals(hudSpan: String): DeviceNetworkTotals? {
        val key = mapSystemHudSpanToAnalyzerSpan(hudSpan)
        return deviceTotalsCache[key]
    }

    fun clearCache() {
        fullSnapshotCache.clear()
        deviceTotalsCache.clear()
    }

    fun resolveWindow(spanKey: String, nowMs: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val start = when (spanKey) {
            "1H" -> (nowMs - 3_600_000L).coerceAtLeast(0L)
            "3H" -> (nowMs - 3L * 3_600_000L).coerceAtLeast(0L)
            "TODAY" -> {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
            }
            "3D" -> (nowMs - 3L * 24L * 3_600_000L).coerceAtLeast(0L)
            "30D" -> (nowMs - 30L * 24L * 3_600_000L).coerceAtLeast(0L)
            else -> (nowMs - 7L * 24L * 3_600_000L).coerceAtLeast(0L)
        }
        return start to nowMs
    }

    /**
     * Refreshes compact device-level Wi-Fi & Cellular totals for the active System Vitals span.
     */
    fun refreshDeviceTotalsIfNeeded(context: Context, hudSpan: String, force: Boolean = false): DeviceNetworkTotals? {
        val spanKey = mapSystemHudSpanToAnalyzerSpan(hudSpan)
        val now = System.currentTimeMillis()
        val cached = deviceTotalsCache[spanKey]
        if (!force && cached != null && (now - cached.queriedAtMs) < CACHE_TTL_MS) {
            return cached
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !LightspeedUsageStats.hasUsageAccess(context)) {
            val empty = DeviceNetworkTotals(spanKey, 0L, 0L, 0L, 0L, hasUsageAccess = false, queriedAtMs = now)
            deviceTotalsCache[spanKey] = empty
            return empty
        }
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return null
        val (startMs, endMs) = resolveWindow(spanKey, now)
        val hasCellular = hasCellularCapability(context)

        val (wRx, wTx) = queryDeviceTransportTotals(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_WIFI, startMs, endMs)
        val (cRx, cTx) = if (hasCellular) {
            queryDeviceTransportTotals(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_MOBILE, startMs, endMs)
        } else {
            0L to 0L
        }

        val totals = DeviceNetworkTotals(
            spanKey = spanKey,
            wifiRxBytes = wRx,
            wifiTxBytes = wTx,
            cellRxBytes = cRx,
            cellTxBytes = cTx,
            hasUsageAccess = true,
            queriedAtMs = now
        )
        deviceTotalsCache[spanKey] = totals
        return totals
    }

    private fun queryDeviceTransportTotals(
        nsm: NetworkStatsManager,
        networkType: Int,
        startMs: Long,
        endMs: Long
    ): Pair<Long, Long> {
        // Primary: querySummaryForDevice; Fallback: sum UID buckets if OEM blocks device summary.
        try {
            val bucket = nsm.querySummaryForDevice(networkType, null, startMs, endMs)
            if (bucket != null) {
                val rx = bucket.rxBytes.coerceAtLeast(0L)
                val tx = bucket.txBytes.coerceAtLeast(0L)
                if (rx > 0L || tx > 0L) return rx to tx
            }
        } catch (_: SecurityException) {
            return 0L to 0L
        } catch (_: Throwable) {}

        var rxSum = 0L
        var txSum = 0L
        var stats: NetworkStats? = null
        try {
            stats = nsm.querySummary(networkType, null, startMs, endMs)
            val b = NetworkStats.Bucket()
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(b)
                rxSum += b.rxBytes.coerceAtLeast(0L)
                txSum += b.txBytes.coerceAtLeast(0L)
            }
        } catch (_: Throwable) {
        } finally {
            try { stats?.close() } catch (_: Throwable) {}
        }
        return rxSum to txSum
    }

    fun getCachedSnapshotIfFresh(spanKey: String): NetworkUsageSnapshot? {
        val snap = fullSnapshotCache[spanKey] ?: return null
        return if ((System.currentTimeMillis() - snap.queriedAtMs) < CACHE_TTL_MS) snap else null
    }

    fun queryAppNetworkUsage(
        context: Context,
        spanKey: String,
        knownLabels: Map<String, String> = emptyMap(),
        forceRefresh: Boolean = false
    ): NetworkUsageSnapshot {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            getCachedSnapshotIfFresh(spanKey)?.let { return it }
        }
        val hasAccess = LightspeedUsageStats.hasUsageAccess(context)
        if (!hasAccess || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return NetworkUsageSnapshot(spanKey, hasUsageAccess = false, 0L, 0L, 0L, 0L, emptyList(), now)
        }
        val nsm = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return NetworkUsageSnapshot(spanKey, hasUsageAccess = false, 0L, 0L, 0L, 0L, emptyList(), now)

        val (startMs, endMs) = resolveWindow(spanKey, now)
        val hasCellular = hasCellularCapability(context)
        val uidWifi = HashMap<Int, LongArray>()
        val uidCell = HashMap<Int, LongArray>()

        val (sumWRx, sumWTx) = collectUidBuckets(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_WIFI, startMs, endMs, uidWifi)
        val (sumCRx, sumCTx) = if (hasCellular) {
            collectUidBuckets(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_MOBILE, startMs, endMs, uidCell)
        } else {
            0L to 0L
        }

        val (devWRx, devWTx) = queryDeviceTransportTotals(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_WIFI, startMs, endMs)
        val (devCRx, devCTx) = if (hasCellular) {
            queryDeviceTransportTotals(nsm, @Suppress("DEPRECATION") ConnectivityManager.TYPE_MOBILE, startMs, endMs)
        } else {
            0L to 0L
        }

        val finalDevWRx = maxOf(devWRx, sumWRx)
        val finalDevWTx = maxOf(devWTx, sumWTx)
        val finalDevCRx = maxOf(devCRx, sumCRx)
        val finalDevCTx = maxOf(devCTx, sumCTx)

        deviceTotalsCache[spanKey] = DeviceNetworkTotals(
            spanKey = spanKey,
            wifiRxBytes = finalDevWRx,
            wifiTxBytes = finalDevWTx,
            cellRxBytes = finalDevCRx,
            cellTxBytes = finalDevCTx,
            hasUsageAccess = true,
            queriedAtMs = now
        )

        val allUids = HashSet<Int>(uidWifi.size + uidCell.size).apply {
            addAll(uidWifi.keys)
            addAll(uidCell.keys)
        }
        val pm = context.packageManager
        val mergedByKey = LinkedHashMap<String, AppNetworkUsageEntry>()

        for (uid in allUids) {
            val w = uidWifi[uid]
            val c = uidCell[uid]
            val wRx = w?.get(0) ?: 0L
            val wTx = w?.get(1) ?: 0L
            val cRx = c?.get(0) ?: 0L
            val cTx = c?.get(1) ?: 0L
            if (wRx + wTx + cRx + cTx <= 0L) continue

            val (pkg, label, isSpecial) = resolveUidIdentity(pm, uid, knownLabels)
            val existing = mergedByKey[pkg]
            if (existing == null) {
                mergedByKey[pkg] = AppNetworkUsageEntry(
                    uid = uid,
                    packageName = pkg,
                    label = label,
                    wifiRxBytes = wRx,
                    wifiTxBytes = wTx,
                    cellRxBytes = cRx,
                    cellTxBytes = cTx,
                    isSystemSpecial = isSpecial
                )
            } else {
                mergedByKey[pkg] = existing.copy(
                    wifiRxBytes = existing.wifiRxBytes + wRx,
                    wifiTxBytes = existing.wifiTxBytes + wTx,
                    cellRxBytes = existing.cellRxBytes + cRx,
                    cellTxBytes = existing.cellTxBytes + cTx
                )
            }
        }

        val sorted = mergedByKey.values.sortedByDescending { it.totalBytes }
        return NetworkUsageSnapshot(
            spanKey = spanKey,
            hasUsageAccess = true,
            deviceWifiRx = finalDevWRx,
            deviceWifiTx = finalDevWTx,
            deviceCellRx = finalDevCRx,
            deviceCellTx = finalDevCTx,
            entries = sorted,
            queriedAtMs = now
        ).also {
            fullSnapshotCache[spanKey] = it
        }
    }

    private fun collectUidBuckets(
        nsm: NetworkStatsManager,
        networkType: Int,
        startMs: Long,
        endMs: Long,
        out: MutableMap<Int, LongArray>
    ): Pair<Long, Long> {
        var totalRx = 0L
        var totalTx = 0L
        var stats: NetworkStats? = null
        try {
            stats = nsm.querySummary(networkType, null, startMs, endMs)
            val bucket = NetworkStats.Bucket()
            while (stats != null && stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val rx = bucket.rxBytes.coerceAtLeast(0L)
                val tx = bucket.txBytes.coerceAtLeast(0L)
                if (rx == 0L && tx == 0L) continue
                totalRx += rx
                totalTx += tx
                val uid = bucket.uid
                val arr = out.getOrPut(uid) { LongArray(2) }
                arr[0] += rx
                arr[1] += tx
            }
        } catch (_: Throwable) {
        } finally {
            try { stats?.close() } catch (_: Throwable) {}
        }
        return totalRx to totalTx
    }

    private fun resolveUidIdentity(
        pm: PackageManager,
        uid: Int,
        knownLabels: Map<String, String>
    ): Triple<String, String, Boolean> {
        when (uid) {
            NetworkStats.Bucket.UID_REMOVED -> return Triple("uid:removed", "Removed Apps", true)
            NetworkStats.Bucket.UID_TETHERING -> return Triple("uid:tethering", "Hotspot & Tethering", true)
            Process.SYSTEM_UID -> return Triple("android", "Android OS", true)
            0 -> return Triple("uid:root", "Android Kernel / OS", true)
        }
        val pkgs = try { pm.getPackagesForUid(uid) } catch (_: Exception) { null }
        if (pkgs.isNullOrEmpty()) {
            return Triple("uid:$uid", "System Service ($uid)", true)
        }
        val primaryPkg = pkgs.firstOrNull { knownLabels.containsKey(it) }
            ?: pkgs.firstOrNull {
                try { pm.getLaunchIntentForPackage(it) != null } catch (_: Exception) { false }
            }
            ?: pkgs[0]
        val friendly = knownLabels[primaryPkg] ?: try {
            val ai = pm.getApplicationInfo(primaryPkg, 0)
            pm.getApplicationLabel(ai).toString().ifBlank { primaryPkg }
        } catch (_: Exception) {
            primaryPkg
        }
        val extraCount = pkgs.size - 1
        val label = if (extraCount > 0 && !knownLabels.containsKey(primaryPkg) && uid == Process.SYSTEM_UID) {
            "Android System"
        } else {
            friendly
        }
        return Triple(primaryPkg, label, false)
    }

    fun filterAndSort(
        entries: List<AppNetworkUsageEntry>,
        transportFilter: String,
        reversed: Boolean
    ): List<AppNetworkUsageEntry> {
        val filtered = entries.filter { it.totalForTransport(transportFilter) > 0L }
        val sorted = filtered.sortedWith(
            compareByDescending<AppNetworkUsageEntry> { it.totalForTransport(transportFilter) }
                .thenBy { it.label.lowercase() }
        )
        return if (reversed) sorted.reversed() else sorted
    }
}
