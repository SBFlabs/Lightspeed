package com.sbf.lightspeed

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Persists real Shizuku `dumpsys batterystats` unplugged app drain across `LAST CHG`, `TODAY`, and `7-DAY` spans.
 * Day-1 seeding populates `TODAY` and `7-DAY` from the live batterystats cycle immediately so `VIEW: APPS`
 * never sits empty while Shizuku is active, with zero UsageStats/screen-time fallback.
 */
internal object LightspeedShizukuBatteryDrainStore {

    private const val FILE_NAME = "shizuku_battery_drain_v1.json"
    private const val MAX_DAYS = 7
    private const val FULL_7D_MS = (7L * 24L - 4L) * 3_600_000L

    private data class DailyDrainBucket(
        var totalMah: Float = 0f,
        val pkgMah: MutableMap<String, Float> = HashMap()
    )

    private data class Snapshot(
        val bySpan: Map<String, List<LightspeedCruiseHudPods.AppBatteryDrainEntry>> = emptyMap(),
        val firstSampleMs: Long = 0L,
        val loaded: Boolean = false
    )

    private val lock = Any()
    private val pkgLabelCache = HashMap<String, String>()
    private val dailyBuckets = LinkedHashMap<String, DailyDrainBucket>()
    private val lastRawPkgMah = HashMap<String, Float>()
    private var firstSampleMs: Long = 0L
    private var lastRawTotalMah: Float = 0f

    @Volatile
    private var snapshot: Snapshot = Snapshot()
    private val prewarmInFlight = AtomicBoolean(false)

    fun isLoaded(): Boolean = snapshot.loaded

    fun prewarmAsync(context: Context) {
        if (snapshot.loaded) return
        if (!prewarmInFlight.compareAndSet(false, true)) return
        val appCtx = context.applicationContext ?: context
        LightspeedCruiseHudPods.scope.launch {
            try {
                ensureLoadedOnIo(appCtx)
                LightspeedCruiseHudPods.onSnapshotUpdated?.invoke()
            } finally {
                prewarmInFlight.set(false)
            }
        }
    }

    private fun ensureLoadedOnIo(context: Context) {
        if (snapshot.loaded) return
        synchronized(lock) {
            if (snapshot.loaded) return
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (file.exists()) {
                    val root = JSONObject(file.readText())
                    firstSampleMs = root.optLong("firstSampleMs", 0L).coerceAtLeast(0L)
                    lastRawTotalMah = root.optDouble("lastRawTotalMah", 0.0).toFloat().coerceAtLeast(0f)

                    val rawObj = root.optJSONObject("lastRawPkgMah")
                    if (rawObj != null) {
                        val keys = rawObj.keys()
                        while (keys.hasNext()) {
                            val pkg = keys.next()
                            val mah = rawObj.optDouble(pkg, 0.0).toFloat()
                            if (mah > 0f) lastRawPkgMah[pkg] = mah
                        }
                    }

                    val daysObj = root.optJSONObject("dailyBuckets")
                    if (daysObj != null) {
                        val dayKeys = daysObj.keys()
                        while (dayKeys.hasNext()) {
                            val dayKey = dayKeys.next()
                            val bObj = daysObj.optJSONObject(dayKey) ?: continue
                            val bucket = DailyDrainBucket(
                                totalMah = bObj.optDouble("totalMah", 0.0).toFloat().coerceAtLeast(0f)
                            )
                            val pkgsObj = bObj.optJSONObject("pkgs")
                            if (pkgsObj != null) {
                                val pKeys = pkgsObj.keys()
                                while (pKeys.hasNext()) {
                                    val pkg = pKeys.next()
                                    val mah = pkgsObj.optDouble(pkg, 0.0).toFloat()
                                    if (mah > 0f) bucket.pkgMah[pkg] = mah
                                }
                            }
                            if (bucket.pkgMah.isNotEmpty() || bucket.totalMah > 0f) {
                                dailyBuckets[dayKey] = bucket
                            }
                        }
                    }
                    pruneOldBucketsLocked(System.currentTimeMillis())
                }
            } catch (_: Exception) {
            }
            rebuildCachesLocked(context)
        }
    }

    fun ingestLiveSnapshot(
        context: Context,
        pkgMahMap: Map<String, Float>,
        pkgLabelMap: Map<String, String>,
        totalDrainMah: Float,
        observedCycleStartMs: Long = 0L
    ): List<LightspeedCruiseHudPods.AppBatteryDrainEntry> {
        ensureLoadedOnIo(context)
        val nowMs = System.currentTimeMillis()
        val safeTotal = maxOf(totalDrainMah, pkgMahMap.values.sum()).coerceAtLeast(0.1f)
        synchronized(lock) {
            for ((pkg, label) in pkgLabelMap) {
                if (label.isNotBlank()) pkgLabelCache[pkg] = label
            }

            val todayKey = formatDayKey(nowMs)
            val isInitialSeed = dailyBuckets.isEmpty()
            var addedDelta = 0f

            if (isInitialSeed) {
                val validCycleStart = observedCycleStartMs in (nowMs - MAX_DAYS * 86_400_000L)..nowMs
                firstSampleMs = if (validCycleStart) observedCycleStartMs else nowMs
                val bucket = dailyBuckets.getOrPut(todayKey) { DailyDrainBucket() }
                bucket.totalMah = safeTotal
                for ((pkg, mah) in pkgMahMap) {
                    if (mah > 0f) {
                        bucket.pkgMah[pkg] = mah
                        addedDelta += mah
                    }
                }
            } else {
                if (firstSampleMs <= 0L) {
                    firstSampleMs = nowMs
                } else if (observedCycleStartMs in (nowMs - MAX_DAYS * 86_400_000L) until firstSampleMs) {
                    firstSampleMs = observedCycleStartMs
                }
                val isCounterReset = lastRawTotalMah > 10f && safeTotal + 5f < lastRawTotalMah * 0.7f
                val bucket = dailyBuckets.getOrPut(todayKey) { DailyDrainBucket() }
                var sumPkgDelta = 0f
                for ((pkg, currMah) in pkgMahMap) {
                    val prevMah = if (isCounterReset) 0f else (lastRawPkgMah[pkg] ?: 0f)
                    val delta = (currMah - prevMah).coerceAtLeast(0f)
                    if (delta > 0f) {
                        bucket.pkgMah[pkg] = (bucket.pkgMah[pkg] ?: 0f) + delta
                        sumPkgDelta += delta
                    }
                }
                val rawTotalDelta = if (isCounterReset) safeTotal else (safeTotal - lastRawTotalMah).coerceAtLeast(0f)
                val effectiveTotalDelta = maxOf(rawTotalDelta, sumPkgDelta)
                if (effectiveTotalDelta > 0f) {
                    bucket.totalMah += effectiveTotalDelta
                }
                addedDelta = effectiveTotalDelta
            }

            lastRawPkgMah.clear()
            for ((pkg, mah) in pkgMahMap) {
                if (mah > 0f) lastRawPkgMah[pkg] = mah
            }
            lastRawTotalMah = safeTotal

            pruneOldBucketsLocked(nowMs)
            rebuildCachesLocked(context)
            if (isInitialSeed || addedDelta >= 0.05f) {
                saveToDiskLocked(context)
            }
            return snapshot.bySpan["last_charge"] ?: emptyList()
        }
    }

    fun markLiveLastChargeEmpty() {
        synchronized(lock) {
            val cur = snapshot
            snapshot = cur.copy(bySpan = cur.bySpan + ("last_charge" to emptyList()))
        }
    }

    fun getEntriesForSpan(context: Context, span: String): List<LightspeedCruiseHudPods.AppBatteryDrainEntry> {
        val snap = snapshot
        if (!snap.loaded) {
            prewarmAsync(context)
        }
        val key = when (span) {
            "today" -> "today"
            "7d" -> "7d"
            else -> "last_charge"
        }
        return snap.bySpan[key] ?: emptyList()
    }

    fun clearMemoryCache() {
        synchronized(lock) {
            snapshot = Snapshot()
        }
    }

    fun getAvailableSpanMs(context: Context): Long {
        val snap = snapshot
        if (!snap.loaded) {
            prewarmAsync(context)
        }
        val first = snap.firstSampleMs
        if (first <= 0L) return 0L
        return (System.currentTimeMillis() - first).coerceAtLeast(0L)
    }

    fun isSpanPartial(context: Context, span: String): Boolean {
        if (span != "7d") return false
        val avail = getAvailableSpanMs(context)
        return avail > 0L && avail < FULL_7D_MS
    }

    private fun rebuildCachesLocked(context: Context) {
        val pm = context.packageManager
        val lastChargeList = buildSortedEntries(pm, lastRawPkgMah, lastRawTotalMah)

        val todayKey = formatDayKey(System.currentTimeMillis())
        val todayBucket = dailyBuckets[todayKey]
        val todayList = if (todayBucket != null) {
            buildSortedEntries(pm, todayBucket.pkgMah, todayBucket.totalMah)
        } else {
            emptyList()
        }

        val combinedPkgs = HashMap<String, Float>()
        var combinedTotal = 0f
        for (bucket in dailyBuckets.values) {
            combinedTotal += bucket.totalMah
            for ((pkg, mah) in bucket.pkgMah) {
                combinedPkgs[pkg] = (combinedPkgs[pkg] ?: 0f) + mah
            }
        }
        val sevenDayList = buildSortedEntries(pm, combinedPkgs, combinedTotal)

        snapshot = Snapshot(
            bySpan = mapOf(
                "last_charge" to lastChargeList,
                "today" to todayList,
                "7d" to sevenDayList
            ),
            firstSampleMs = firstSampleMs,
            loaded = true
        )
    }

    private fun buildSortedEntries(
        pm: PackageManager,
        pkgMahMap: Map<String, Float>,
        totalDrainMah: Float
    ): List<LightspeedCruiseHudPods.AppBatteryDrainEntry> {
        if (pkgMahMap.isEmpty()) return emptyList()
        val denom = maxOf(totalDrainMah, pkgMahMap.values.sum()).coerceAtLeast(0.1f)
        return pkgMahMap.entries
            .filter { it.value >= 0.05f }
            .map { (pkg, mah) ->
                val label = pkgLabelCache[pkg] ?: try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }.also { pkgLabelCache[pkg] = it }
                LightspeedCruiseHudPods.AppBatteryDrainEntry(
                    packageName = pkg,
                    label = label,
                    drainPct = ((mah / denom) * 100f).coerceIn(0.1f, 100f),
                    drainMah = mah
                )
            }
            .sortedByDescending { it.drainMah }
    }

    private fun pruneOldBucketsLocked(nowMs: Long) {
        val validKeys = HashSet<String>(MAX_DAYS)
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        for (i in 0 until MAX_DAYS) {
            validKeys.add(formatDayKey(cal.timeInMillis))
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        val it = dailyBuckets.entries.iterator()
        while (it.hasNext()) {
            if (it.next().key !in validKeys) {
                it.remove()
            }
        }
    }

    private fun saveToDiskLocked(context: Context) {
        try {
            val root = JSONObject().apply {
                put("firstSampleMs", firstSampleMs)
                put("lastRawTotalMah", lastRawTotalMah.toDouble())
                val rawObj = JSONObject()
                for ((pkg, mah) in lastRawPkgMah) {
                    rawObj.put(pkg, mah.toDouble())
                }
                put("lastRawPkgMah", rawObj)
                val daysObj = JSONObject()
                for ((dayKey, bucket) in dailyBuckets) {
                    val bObj = JSONObject().apply {
                        put("totalMah", bucket.totalMah.toDouble())
                        val pkgsObj = JSONObject()
                        for ((pkg, mah) in bucket.pkgMah) {
                            pkgsObj.put(pkg, mah.toDouble())
                        }
                        put("pkgs", pkgsObj)
                    }
                    daysObj.put(dayKey, bObj)
                }
                put("dailyBuckets", daysObj)
            }
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        } catch (_: Exception) {
        }
    }

    private fun formatDayKey(timeMs: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timeMs))
}
