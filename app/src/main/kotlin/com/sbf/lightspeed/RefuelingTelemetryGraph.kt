package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

internal data class RefuelingTelemetrySample(
    val timestampMs: Long,
    val wattage: Float,
    val tempC: Float
)

internal data class RefuelingHistorySnapshot(
    val samples: List<RefuelingTelemetrySample> = emptyList(),
    val activeChargeStartMs: Long = 0L,
    val lastChargeStartMs: Long = 0L,
    val lastChargeEndMs: Long = 0L
)

internal data class RefuelingGraphSliceResult(
    val samples: List<RefuelingTelemetrySample>,
    val spanBadge: String
)

private data class DiskHistoryLoadResult(
    val samples: List<RefuelingTelemetrySample>,
    val activeChargeStartMs: Long,
    val lastChargeStartMs: Long,
    val lastChargeEndMs: Long
)

internal object RefuelingTelemetryHistoryBuffer {
    private const val FILE_NAME = "refueling_graph_history.log"
    private const val SESSION_PREFIX = "#session|"
    private const val MAX_SAMPLES = 2400
    private const val MAX_AGE_MS = 6L * 3_600_000L
    private const val MAX_CHARGE_RETAIN_MS = 12L * 3_600_000L
    private const val FLUSH_INTERVAL_MS = 15_000L
    private const val COMPACT_RECENT_WINDOW_MS = 15L * 60_000L
    private const val COMPACT_MID_WINDOW_MS = 3_600_000L
    private const val COMPACT_MID_STEP_MS = 10_000L
    private const val COMPACT_FAR_STEP_MS = 30_000L

    private val lock = Any()
    private val backingSamples = ArrayList<RefuelingTelemetrySample>()
    private var activeChargeStartMs: Long = 0L
    private var lastChargeStartMs: Long = 0L
    private var lastChargeEndMs: Long = 0L
    private var samplesSinceCompaction: Int = 0

    private val _historyFlow = MutableStateFlow(RefuelingHistorySnapshot())
    val historyFlow: StateFlow<RefuelingHistorySnapshot> = _historyFlow.asStateFlow()

    @Volatile private var loaded = false
    @Volatile private var loadingStarted = false
    @Volatile private var lastFlushMs = 0L

    suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        val appCtx = context.applicationContext ?: context
        val diskResult = withContext(Dispatchers.IO) { readDiskHistory(appCtx, System.currentTimeMillis()) }
        mergeLoadedData(diskResult)
    }

    private fun readDiskHistory(appCtx: Context, nowMs: Long): DiskHistoryLoadResult {
        val file = File(appCtx.filesDir, FILE_NAME)
        var parsedActive = 0L
        var parsedLastStart = 0L
        var parsedLastEnd = 0L
        val rawSamples = ArrayList<RefuelingTelemetrySample>()
        if (file.exists()) {
            try {
                file.forEachLine { line ->
                    if (line.startsWith(SESSION_PREFIX)) {
                        val parts = line.removePrefix(SESSION_PREFIX).split('|')
                        if (parts.size >= 3) {
                            parsedActive = parts[0].toLongOrNull()?.takeIf { it in 1L..nowMs + 60_000L } ?: 0L
                            parsedLastStart = parts[1].toLongOrNull()?.takeIf { it in 1L..nowMs + 60_000L } ?: 0L
                            parsedLastEnd = parts[2].toLongOrNull()?.takeIf { it in 1L..nowMs + 60_000L } ?: 0L
                        }
                        return@forEachLine
                    }
                    val parts = line.split('|')
                    if (parts.size >= 3) {
                        val ts = parts[0].toLongOrNull() ?: return@forEachLine
                        val w = parts[1].toFloatOrNull() ?: return@forEachLine
                        val t = parts[2].toFloatOrNull() ?: return@forEachLine
                        if (ts in (nowMs - MAX_CHARGE_RETAIN_MS)..(nowMs + 60_000L)) {
                            rawSamples.add(RefuelingTelemetrySample(ts, w.coerceAtLeast(0f), t))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        val samples = if (rawSamples.isNotEmpty()) {
            rawSamples
        } else {
            LightspeedVitalsHistoryStore.getSamplesInWindow(appCtx, MAX_AGE_MS)
                .takeLast(120)
                .map { RefuelingTelemetrySample(it.timestampMs, 0f, it.tempC.toFloat()) }
        }

        if (parsedActive == 0L && parsedLastStart == 0L && samples.isNotEmpty()) {
            val recovered = recoverLastChargeRun(samples)
            if (recovered != null) {
                if (nowMs - recovered.second <= 120_000L && samples.last().wattage > 0.2f) {
                    parsedActive = recovered.first
                } else {
                    parsedLastStart = recovered.first
                    parsedLastEnd = recovered.second
                }
            }
        }

        return DiskHistoryLoadResult(
            samples = samples,
            activeChargeStartMs = parsedActive,
            lastChargeStartMs = parsedLastStart,
            lastChargeEndMs = parsedLastEnd
        )
    }

    private fun recoverLastChargeRun(samples: List<RefuelingTelemetrySample>): Pair<Long, Long>? {
        var idx = samples.lastIndex
        while (idx >= 0 && samples[idx].wattage <= 0.25f) idx--
        if (idx < 0) return null
        val endTs = samples[idx].timestampMs
        var startIdx = idx
        while (startIdx > 0 && (samples[startIdx - 1].wattage > 0.2f || samples[startIdx].timestampMs - samples[startIdx - 1].timestampMs <= 35_000L && startIdx > 1 && samples[startIdx - 2].wattage > 0.2f)) {
            startIdx--
        }
        val startTs = samples[startIdx].timestampMs
        return if (endTs >= startTs) startTs to endTs else null
    }

    private fun mergeLoadedData(diskResult: DiskHistoryLoadResult) {
        synchronized(lock) {
            if (loaded) return
            val earliestMemoryTs = backingSamples.firstOrNull()?.timestampMs ?: Long.MAX_VALUE
            val prepended = diskResult.samples.filter { it.timestampMs < earliestMemoryTs }
            if (prepended.isNotEmpty()) {
                val combined = ArrayList<RefuelingTelemetrySample>(prepended.size + backingSamples.size)
                combined.addAll(prepended)
                combined.addAll(backingSamples)
                backingSamples.clear()
                backingSamples.addAll(combined)
            }
            if (activeChargeStartMs == 0L && lastChargeStartMs == 0L) {
                if (diskResult.activeChargeStartMs > 0L) {
                    val firstUnpluggedAfterLoad = backingSamples.firstOrNull {
                        it.timestampMs >= earliestMemoryTs && it.wattage <= 0.05f
                    }
                    if (firstUnpluggedAfterLoad != null && earliestMemoryTs < Long.MAX_VALUE) {
                        lastChargeStartMs = diskResult.activeChargeStartMs
                        lastChargeEndMs = firstUnpluggedAfterLoad.timestampMs
                    } else {
                        activeChargeStartMs = diskResult.activeChargeStartMs
                    }
                } else {
                    lastChargeStartMs = diskResult.lastChargeStartMs
                    lastChargeEndMs = diskResult.lastChargeEndMs
                }
            }
            compactAndPruneLocked(System.currentTimeMillis())
            loaded = true
            publishSnapshotLocked()
        }
    }

    private fun ensureLoadedBlockingOnIo(appCtx: Context) {
        if (loaded) return
        val diskResult = readDiskHistory(appCtx, System.currentTimeMillis())
        mergeLoadedData(diskResult)
    }

    private fun updateSessionLocked(isCharging: Boolean, nowMs: Long): Boolean {
        if (isCharging) {
            if (activeChargeStartMs == 0L) {
                activeChargeStartMs = nowMs
                return true
            }
            return false
        } else {
            if (activeChargeStartMs > 0L) {
                lastChargeStartMs = activeChargeStartMs
                lastChargeEndMs = maxOf(activeChargeStartMs, nowMs)
                activeChargeStartMs = 0L
                return true
            }
            return false
        }
    }

    private fun compactAndPruneLocked(nowMs: Long) {
        if (backingSamples.isEmpty()) return
        val retainedFloorMs = resolveRetainedFloorMsLocked(nowMs)
        val midStartMs = nowMs - COMPACT_MID_WINDOW_MS
        val recentStartMs = nowMs - COMPACT_RECENT_WINDOW_MS

        val compacted = ArrayList<RefuelingTelemetrySample>(minOf(backingSamples.size, MAX_SAMPLES))
        var lastKeptTs = Long.MIN_VALUE
        for (i in backingSamples.indices) {
            val s = backingSamples[i]
            if (s.timestampMs < retainedFloorMs) continue
            val isBoundary = s.timestampMs == activeChargeStartMs ||
                s.timestampMs == lastChargeStartMs ||
                s.timestampMs == lastChargeEndMs ||
                i == backingSamples.lastIndex
            if (isBoundary || s.timestampMs >= recentStartMs) {
                compacted.add(s)
                lastKeptTs = s.timestampMs
            } else {
                val minStep = if (s.timestampMs >= midStartMs) COMPACT_MID_STEP_MS else COMPACT_FAR_STEP_MS
                if (lastKeptTs == Long.MIN_VALUE || s.timestampMs - lastKeptTs >= minStep) {
                    compacted.add(s)
                    lastKeptTs = s.timestampMs
                }
            }
        }
        backingSamples.clear()
        val startOffset = (compacted.size - MAX_SAMPLES).coerceAtLeast(0)
        for (idx in startOffset until compacted.size) {
            backingSamples.add(compacted[idx])
        }
        samplesSinceCompaction = 0
    }

    private fun resolveRetainedFloorMsLocked(nowMs: Long): Long {
        val defaultFloor = nowMs - MAX_AGE_MS
        val maxRetainFloor = nowMs - MAX_CHARGE_RETAIN_MS
        val protectedAnchor = when {
            activeChargeStartMs > 0L -> activeChargeStartMs - 5_000L
            lastChargeStartMs > 0L -> lastChargeStartMs - 5_000L
            else -> defaultFloor
        }
        return maxOf(maxRetainFloor, minOf(defaultFloor, protectedAnchor))
    }

    private fun publishSnapshotLocked(): RefuelingHistorySnapshot {
        val snapshot = RefuelingHistorySnapshot(
            samples = backingSamples.toList(),
            activeChargeStartMs = activeChargeStartMs,
            lastChargeStartMs = lastChargeStartMs,
            lastChargeEndMs = lastChargeEndMs
        )
        _historyFlow.value = snapshot
        return snapshot
    }

    fun record(
        wattage: Float,
        tempC: Float,
        isCharging: Boolean = wattage > 0.2f,
        context: Context? = null,
        nowMs: Long = System.currentTimeMillis(),
        minIntervalMs: Long = 1500L,
        forceFlush: Boolean = false
    ) {
        val appCtx = context?.applicationContext ?: context
        if (!loaded && appCtx != null && !loadingStarted) {
            loadingStarted = true
            LightspeedCruiseHudPods.scope.launch(Dispatchers.IO) {
                ensureLoadedBlockingOnIo(appCtx)
            }
        }

        var shouldFlush = false
        val snapshotToWrite: RefuelingHistorySnapshot?
        synchronized(lock) {
            val sessionChanged = updateSessionLocked(isCharging = isCharging, nowMs = nowMs)
            val last = backingSamples.lastOrNull()
            val withinInterval = last != null && nowMs - last.timestampMs < minIntervalMs
            if (withinInterval && !sessionChanged && !forceFlush) return

            if (!withinInterval || sessionChanged) {
                backingSamples.add(
                    RefuelingTelemetrySample(
                        timestampMs = nowMs,
                        wattage = wattage.coerceAtLeast(0f),
                        tempC = tempC
                    )
                )
                samplesSinceCompaction++
            }

            if (samplesSinceCompaction >= 30 || backingSamples.size > MAX_SAMPLES) {
                compactAndPruneLocked(nowMs)
            } else {
                val floorMs = resolveRetainedFloorMsLocked(nowMs)
                while (backingSamples.size > 2 && backingSamples[0].timestampMs < floorMs) {
                    backingSamples.removeAt(0)
                }
            }

            val published = publishSnapshotLocked()
            val flushDue = loaded && appCtx != null && (forceFlush || sessionChanged || nowMs - lastFlushMs >= FLUSH_INTERVAL_MS)
            if (flushDue) {
                lastFlushMs = nowMs
                shouldFlush = true
                snapshotToWrite = published
            } else {
                snapshotToWrite = null
            }
        }
        if (shouldFlush && appCtx != null && snapshotToWrite != null) {
            flushSnapshotAsync(appCtx, snapshotToWrite)
        }
    }

    fun recordFromBackground(
        context: Context,
        wattage: Float,
        tempC: Float,
        isCharging: Boolean = wattage > 0.2f
    ) {
        record(
            wattage = wattage,
            tempC = tempC,
            isCharging = isCharging,
            context = context,
            minIntervalMs = 5_000L
        )
    }

    fun sampleBatteryNow(
        context: Context,
        forceChargingState: Boolean? = null,
        forceFlush: Boolean = false
    ) {
        val appCtx = context.applicationContext ?: context
        val nowMs = System.currentTimeMillis()
        LightspeedCruiseHudPods.scope.launch(Dispatchers.IO) {
            try {
                ensureLoadedBlockingOnIo(appCtx)
                val batIntent = appCtx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return@launch
                val status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val detectedCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val isCharging = forceChargingState ?: detectedCharging
                val tempC = (batIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 300) / 10f)
                val watts = if (isCharging) {
                    val voltageMv = batIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                    val bm = appCtx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                    val rawCur = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) ?: 0
                    val curUa = if (abs(rawCur) < 10_000) abs(rawCur) * 1000 else abs(rawCur)
                    ((voltageMv / 1000f) * (curUa / 1_000_000f)).coerceIn(0f, 240f)
                } else {
                    0f
                }
                record(
                    wattage = watts,
                    tempC = tempC,
                    isCharging = isCharging,
                    context = appCtx,
                    nowMs = nowMs,
                    minIntervalMs = 5_000L,
                    forceFlush = forceFlush
                )
            } catch (_: Exception) {}
        }
    }

    private fun flushSnapshotAsync(appCtx: Context, snapshot: RefuelingHistorySnapshot) {
        LightspeedCruiseHudPods.scope.launch(Dispatchers.IO) {
            try {
                val file = File(appCtx.filesDir, FILE_NAME)
                val nowMs = System.currentTimeMillis()
                val floorMs = maxOf(
                    nowMs - MAX_CHARGE_RETAIN_MS,
                    minOf(
                        nowMs - MAX_AGE_MS,
                        when {
                            snapshot.activeChargeStartMs > 0L -> snapshot.activeChargeStartMs - 5_000L
                            snapshot.lastChargeStartMs > 0L -> snapshot.lastChargeStartMs - 5_000L
                            else -> nowMs - MAX_AGE_MS
                        }
                    )
                )
                val text = buildString {
                    append(SESSION_PREFIX)
                        .append(snapshot.activeChargeStartMs).append('|')
                        .append(snapshot.lastChargeStartMs).append('|')
                        .append(snapshot.lastChargeEndMs).append('\n')
                    for (s in snapshot.samples) {
                        if (s.timestampMs >= floorMs) {
                            append(s.timestampMs).append('|')
                                .append(String.format(Locale.US, "%.2f", s.wattage)).append('|')
                                .append(String.format(Locale.US, "%.1f", s.tempC)).append('\n')
                        }
                    }
                }
                file.writeText(text)
            } catch (_: Exception) {}
        }
    }

    fun sliceForSpan(
        snapshot: RefuelingHistorySnapshot,
        spanKey: String,
        isCharging: Boolean,
        unpluggedMode: String,
        nowMs: Long = System.currentTimeMillis()
    ): RefuelingGraphSliceResult {
        val allSamples = snapshot.samples
        if (allSamples.isEmpty()) {
            return RefuelingGraphSliceResult(emptyList(), fallbackBadge(spanKey, isCharging, unpluggedMode))
        }

        if (spanKey == "charge") {
            if (isCharging) {
                val startMs = when {
                    snapshot.activeChargeStartMs > 0L -> snapshot.activeChargeStartMs
                    else -> recoverLastChargeRun(allSamples)?.first ?: (nowMs - 900_000L)
                }
                val firstIdx = allSamples.indexOfFirst { it.timestampMs >= startMs - 5_000L }
                val slice = if (firstIdx <= 0) allSamples else allSamples.subList(firstIdx, allSamples.size)
                return RefuelingGraphSliceResult(resampleTimeProportional(slice), "CHARGE")
            }
            if (unpluggedMode == "last_charge" &&
                snapshot.lastChargeStartMs > 0L &&
                snapshot.lastChargeEndMs > snapshot.lastChargeStartMs
            ) {
                val lastSlice = allSamples.filter {
                    it.timestampMs in (snapshot.lastChargeStartMs - 5_000L)..(snapshot.lastChargeEndMs + 5_000L)
                }
                if (lastSlice.size >= 2 || lastSlice.any { it.wattage > 0.2f }) {
                    return RefuelingGraphSliceResult(resampleTimeProportional(lastSlice), "LAST CHG")
                }
            }
            val live15mSlice = sliceWindow(allSamples, nowMs - 900_000L)
            return RefuelingGraphSliceResult(resampleTimeProportional(live15mSlice), "15M")
        }

        val windowMs = when (spanKey) {
            "1m" -> 60_000L
            "5m" -> 300_000L
            "15m" -> 900_000L
            "1h" -> 3_600_000L
            "3h" -> 10_800_000L
            else -> 900_000L
        }
        val badge = REFUELING_GRAPH_SPAN_OPTIONS.firstOrNull { it.first == spanKey }?.second ?: "CHARGE"
        val slice = sliceWindow(allSamples, nowMs - windowMs)
        return RefuelingGraphSliceResult(resampleTimeProportional(slice), badge)
    }

    private fun fallbackBadge(spanKey: String, isCharging: Boolean, unpluggedMode: String): String {
        if (spanKey != "charge") {
            return REFUELING_GRAPH_SPAN_OPTIONS.firstOrNull { it.first == spanKey }?.second ?: "CHARGE"
        }
        return when {
            isCharging -> "CHARGE"
            unpluggedMode == "last_charge" -> "LAST CHG"
            else -> "15M"
        }
    }

    private fun sliceWindow(
        allSamples: List<RefuelingTelemetrySample>,
        cutoffMs: Long
    ): List<RefuelingTelemetrySample> {
        val firstIdx = allSamples.indexOfFirst { it.timestampMs >= cutoffMs }
        return if (firstIdx <= 0) allSamples else allSamples.subList(firstIdx, allSamples.size)
    }

    private fun resampleTimeProportional(slice: List<RefuelingTelemetrySample>): List<RefuelingTelemetrySample> {
        if (slice.size <= 2) return slice
        val startMs = slice.first().timestampMs
        val endMs = slice.last().timestampMs
        val totalMs = endMs - startMs
        if (totalMs <= 0L) return slice.takeLast(64)
        if (slice.size <= 48 && totalMs < 120_000L) return slice

        val targetCount = 60
        var ptr = 0
        return List(targetCount) { idx ->
            val targetTs = startMs + ((totalMs.toDouble() * idx) / (targetCount - 1)).toLong()
            while (ptr + 1 < slice.size &&
                abs(slice[ptr + 1].timestampMs - targetTs) <= abs(slice[ptr].timestampMs - targetTs)
            ) {
                ptr++
            }
            slice[ptr]
        }
    }
}

internal fun refuelingGraphModeOptions(): List<Pair<String, String>> = listOf(
    "strip" to lsString(R.string.ref_graph_mode_strip),
    "backdrop" to lsString(R.string.ref_graph_mode_backdrop),
    "gauge" to lsString(R.string.ref_graph_mode_gauge),
    "off" to lsString(R.string.ref_graph_mode_off)
)

internal val REFUELING_GRAPH_SPAN_OPTIONS: List<Pair<String, String>> = listOf(
    "1m" to "1M",
    "5m" to "5M",
    "15m" to "15M",
    "1h" to "1H",
    "3h" to "3H",
    "charge" to "CHARGE"
)

internal fun refuelingGraphSpanOptions(): List<Pair<String, String>> = listOf(
    "1m" to lsString(R.string.ref_graph_span_1m),
    "5m" to lsString(R.string.ref_graph_span_5m),
    "15m" to lsString(R.string.ref_graph_span_15m),
    "1h" to lsString(R.string.ref_graph_span_1h),
    "3h" to lsString(R.string.ref_graph_span_3h),
    "charge" to lsString(R.string.ref_graph_span_charge)
)

internal fun refuelingGraphUnpluggedOptions(): List<Pair<String, String>> = listOf(
    "live_15m" to lsString(R.string.ref_graph_unplug_live),
    "last_charge" to lsString(R.string.ref_graph_unplug_last)
)

internal fun resolveRefuelingSpanBadge(badge: String): String = when (badge) {
    "1M" -> lsString(R.string.ref_graph_span_1m)
    "5M" -> lsString(R.string.ref_graph_span_5m)
    "15M" -> lsString(R.string.ref_graph_span_15m)
    "1H" -> lsString(R.string.ref_graph_span_1h)
    "3H" -> lsString(R.string.ref_graph_span_3h)
    "LAST CHG" -> lsString(R.string.ref_graph_span_last_chg)
    "CHARGE" -> lsString(R.string.ref_graph_span_charge)
    else -> badge
}

internal fun nextRefuelingGraphSpan(current: String): String {
    val keys = REFUELING_GRAPH_SPAN_OPTIONS.map { it.first }
    val idx = keys.indexOf(current)
    return if (idx < 0) "charge" else keys[(idx + 1) % keys.size]
}

/**
 * Draws the shared charging power (W) & thermal (°C/°F) live curve surface inside Refueling Bay.
 * Follows the active 12-Theme tokens while separating Power (solid filled curve) from
 * Temperature (dashed unshaded curve in upper-middle lane), with optional bottom subtext & Clean Mode.
 */
@Composable
fun RefuelingTelemetryGraphSurface(
    wattage: Float,
    batteryTempC: Float,
    isCharging: Boolean,
    graphSpan: String,
    graphUnpluggedMode: String,
    showLabels: Boolean,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    onCycleSpan: () -> Unit,
    modifier: Modifier = Modifier,
    insideGaugeOnly: Boolean = false,
    enableSamplerLoop: Boolean = false
) {
    val context = LocalContext.current
    val lsColors = LightspeedStyle.colors
    val wattFillPath = remember { Path() }
    val wattStrokePath = remember { Path() }
    val tempStrokePath = remember { Path() }
    val latestWattage by rememberUpdatedState(wattage)
    val latestTempC by rememberUpdatedState(batteryTempC)
    val latestIsCharging by rememberUpdatedState(isCharging)

    LaunchedEffect(enableSamplerLoop) {
        RefuelingTelemetryHistoryBuffer.ensureLoaded(context)
        if (enableSamplerLoop) {
            RefuelingTelemetryHistoryBuffer.record(
                wattage = latestWattage,
                tempC = latestTempC,
                isCharging = latestIsCharging,
                context = context
            )
            while (true) {
                delay(2000L)
                RefuelingTelemetryHistoryBuffer.record(
                    wattage = latestWattage,
                    tempC = latestTempC,
                    isCharging = latestIsCharging,
                    context = context
                )
            }
        }
    }

    val historySnapshot by RefuelingTelemetryHistoryBuffer.historyFlow.collectAsState()
    val sliceResult = remember(historySnapshot, graphSpan, isCharging, graphUnpluggedMode) {
        RefuelingTelemetryHistoryBuffer.sliceForSpan(
            snapshot = historySnapshot,
            spanKey = graphSpan,
            isCharging = isCharging,
            unpluggedMode = graphUnpluggedMode
        )
    }
    val visibleSamples = sliceResult.samples

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawTelemetryBackgroundGraph(
                    samples = visibleSamples,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    dynamicArcColor = dynamicArcColor,
                    thermalBadgeColor = thermalBadgeColor,
                    guideColor = lsColors.textPrimary,
                    wattFillPath = wattFillPath,
                    wattStrokePath = wattStrokePath,
                    tempStrokePath = tempStrokePath,
                    compactMode = insideGaugeOnly
                )
            }
        }

        if (showLabels && !insideGaugeOnly) {
            RefuelingGraphBottomLegend(
                context = context,
                samples = visibleSamples,
                currentWattage = wattage,
                currentTempC = batteryTempC,
                spanBadge = sliceResult.spanBadge,
                dynamicArcColor = dynamicArcColor,
                thermalBadgeColor = thermalBadgeColor,
                onCycleSpan = onCycleSpan
            )
        }
    }
}

@Composable
private fun RefuelingGraphBottomLegend(
    context: Context,
    samples: List<RefuelingTelemetrySample>,
    currentWattage: Float,
    currentTempC: Float,
    spanBadge: String,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    onCycleSpan: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val isFahrenheit = remember(context) { LightspeedCruiseHudPods.isTempFahrenheit(context) }
    val maxWatt = samples.maxOfOrNull { it.wattage }?.coerceAtLeast(currentWattage) ?: currentWattage
    val minActiveWatt = samples.asSequence()
        .filter { it.wattage >= 0.5f }
        .minOfOrNull { it.wattage }
        ?.let { if (currentWattage >= 0.5f) minOf(it, currentWattage) else it }
        ?: if (currentWattage >= 0.5f) currentWattage else null
    val minTemp = samples.minOfOrNull { it.tempC }?.coerceAtMost(currentTempC) ?: currentTempC
    val maxTemp = samples.maxOfOrNull { it.tempC }?.coerceAtLeast(currentTempC) ?: currentTempC

    fun formatTemp(c: Float): String {
        val v = if (isFahrenheit) c * 9f / 5f + 32f else c
        val unit = if (isFahrenheit) "F" else "C"
        return String.format(Locale.US, "%.1f°%s", v, unit)
    }

    val pwrTag = lsString(R.string.ref_graph_pwr)
    val pkTag = lsString(R.string.ref_graph_pk)
    val tempTag = lsString(R.string.ref_graph_temp)
    val powerLabel = when {
        currentWattage > 0.1f && minActiveWatt != null && maxWatt >= 0.5f && (maxWatt - minActiveWatt) >= 1.5f ->
            String.format(Locale.US, "%s %.1fW (%.0f–%.0f)", pwrTag, currentWattage, minActiveWatt, maxWatt)
        currentWattage > 0.1f && maxWatt >= currentWattage + 0.8f && maxWatt >= 0.5f ->
            String.format(Locale.US, "%s %.1fW (%s %.0f)", pwrTag, currentWattage, pkTag, maxWatt)
        currentWattage > 0.1f -> String.format(Locale.US, "%s %.1fW", pwrTag, currentWattage)
        maxWatt >= 0.5f && minActiveWatt != null && (maxWatt - minActiveWatt) >= 1.5f ->
            String.format(Locale.US, "%s 0W (%.0f–%.0f)", pwrTag, minActiveWatt, maxWatt)
        maxWatt >= 0.5f -> String.format(Locale.US, "%s 0W (%s %.0f)", pwrTag, pkTag, maxWatt)
        else -> "$pwrTag 0.0W"
    }

    val tempLabel = if (abs(maxTemp - minTemp) >= 0.6f) {
        val tMinStr = String.format(Locale.US, "%.0f", if (isFahrenheit) minTemp * 9f / 5f + 32f else minTemp)
        val tMaxStr = String.format(Locale.US, "%.0f", if (isFahrenheit) maxTemp * 9f / 5f + 32f else maxTemp)
        "$tempTag ${formatTemp(currentTempC)} ($tMinStr–$tMaxStr)"
    } else {
        "$tempTag ${formatTemp(currentTempC)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Solid bar matching Solid Power curve
                Box(
                    modifier = Modifier
                        .size(width = 12.dp, height = 2.8.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(dynamicArcColor)
                )
                Text(
                    text = powerLabel,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = lsColors.textPrimary.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // Dashed swatch matching Dashed Temperature curve
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 2.4.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(thermalBadgeColor)
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 5.dp, height = 2.4.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(thermalBadgeColor)
                    )
                }
                Text(
                    text = tempLabel,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = lsColors.textPrimary.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(dynamicArcColor.copy(alpha = 0.14f))
                .border(0.6.dp, dynamicArcColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .clickable(onClick = onCycleSpan)
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = resolveRefuelingSpanBadge(spanBadge),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = dynamicArcColor
            )
        }
    }
}

internal fun DrawScope.drawTelemetryBackgroundGraph(
    samples: List<RefuelingTelemetrySample>,
    canvasWidth: Float,
    canvasHeight: Float,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    guideColor: Color,
    wattFillPath: Path,
    wattStrokePath: Path,
    tempStrokePath: Path,
    compactMode: Boolean = false
) {
    if (samples.isEmpty() || canvasWidth <= 2f || canvasHeight <= 2f) return

    val topY = canvasHeight * if (compactMode) 0.12f else 0.10f
    val bottomY = canvasHeight * if (compactMode) 0.88f else 0.90f
    val graphHeight = (bottomY - topY).coerceAtLeast(16f)

    if (!compactMode) {
        val horizontalGridBrush = Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                guideColor.copy(alpha = 0.08f),
                guideColor.copy(alpha = 0.08f),
                Color.Transparent
            )
        )
        for (fraction in listOf(0.20f, 0.50f, 0.80f)) {
            val gy = topY + graphHeight * fraction
            drawLine(
                brush = horizontalGridBrush,
                start = Offset(0f, gy),
                end = Offset(canvasWidth, gy),
                strokeWidth = 0.8.dp.toPx()
            )
        }
    }

    val effectiveSamples = if (samples.size == 1) listOf(samples[0], samples[0]) else samples
    val hasChargingPower = effectiveSamples.any { it.wattage > 0.2f }
    val peakSampleW = effectiveSamples.maxOfOrNull { it.wattage } ?: 0f
    val maxWattage = (peakSampleW * 1.15f).coerceAtLeast(10f)

    // Give Temperature its own upper-middle lane (38%..88%) so it stays visually separated from low power or floor
    val minSampleTemp = effectiveSamples.minOfOrNull { it.tempC } ?: 28f
    val maxSampleTemp = effectiveSamples.maxOfOrNull { it.tempC } ?: 32f
    val tempDelta = (maxSampleTemp - minSampleTemp).coerceAtLeast(0f)
    val halfPad = maxOf(0.8f, tempDelta * 0.45f)
    val tLow = minSampleTemp - halfPad
    val tSpan = ((maxSampleTemp + halfPad) - tLow).coerceAtLeast(1.6f)

    fun mapTempY(tempC: Float): Float {
        val norm = ((tempC - tLow) / tSpan).coerceIn(0f, 1f)
        val elevatedFrac = 0.36f + norm * 0.50f
        return bottomY - graphHeight * elevatedFrac
    }

    fun mapWattY(wattage: Float): Float {
        val norm = (wattage / maxWattage).coerceIn(0f, 1f)
        return bottomY - graphHeight * (norm * 0.90f)
    }

    val count = effectiveSamples.size
    val stepX = canvasWidth / (count - 1).coerceAtLeast(1)

    wattStrokePath.reset()
    wattFillPath.reset()
    tempStrokePath.reset()

    var prevWX = 0f
    var prevWY = mapWattY(effectiveSamples[0].wattage)
    var prevTX = 0f
    var prevTY = mapTempY(effectiveSamples[0].tempC)

    wattStrokePath.moveTo(prevWX, prevWY)
    wattFillPath.moveTo(prevWX, bottomY)
    wattFillPath.lineTo(prevWX, prevWY)
    tempStrokePath.moveTo(prevTX, prevTY)

    for (i in 1 until count) {
        val x = i * stepX
        val yW = mapWattY(effectiveSamples[i].wattage)
        val yT = mapTempY(effectiveSamples[i].tempC)

        val midX = (prevWX + x) * 0.5f
        wattStrokePath.cubicTo(midX, prevWY, midX, yW, x, yW)
        wattFillPath.cubicTo(midX, prevWY, midX, yW, x, yW)
        tempStrokePath.cubicTo(midX, prevTY, midX, yT, x, yT)

        prevWX = x
        prevWY = yW
        prevTX = x
        prevTY = yT
    }

    wattFillPath.lineTo(prevWX, bottomY)
    wattFillPath.close()

    val strokeBoost = if (compactMode) 0.78f else 1f
    val dashEffect = PathEffect.dashPathEffect(
        intervals = floatArrayOf(6.dp.toPx() * strokeBoost, 4.dp.toPx() * strokeBoost),
        phase = 0f
    )

    // 1. Dashed Temperature curve (no bottom fill so it never muddies solid Power wave)
    drawPath(
        path = tempStrokePath,
        brush = Brush.horizontalGradient(
            colors = listOf(
                thermalBadgeColor.copy(alpha = 0.25f),
                thermalBadgeColor.copy(alpha = 0.72f),
                thermalBadgeColor.copy(alpha = 0.85f)
            )
        ),
        style = Stroke(
            width = 1.9.dp.toPx() * strokeBoost,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = dashEffect
        )
    )
    drawCircle(
        color = thermalBadgeColor.copy(alpha = 0.90f),
        radius = 3.0.dp.toPx() * strokeBoost,
        center = Offset((prevTX - 3.dp.toPx()).coerceAtLeast(0f), prevTY),
        style = Stroke(width = 1.5.dp.toPx() * strokeBoost)
    )

    // 2. Solid Power curve + gradient fill (or subtle 0W idle baseline when unplugged)
    if (hasChargingPower) {
        drawPath(
            path = wattFillPath,
            brush = Brush.verticalGradient(
                colors = listOf(dynamicArcColor.copy(alpha = 0.22f), Color.Transparent),
                startY = topY,
                endY = bottomY
            )
        )
        drawPath(
            path = wattStrokePath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    dynamicArcColor.copy(alpha = 0.35f),
                    dynamicArcColor.copy(alpha = 0.82f),
                    dynamicArcColor
                )
            ),
            style = Stroke(width = 2.6.dp.toPx() * strokeBoost, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(
            color = dynamicArcColor,
            radius = 3.2.dp.toPx() * strokeBoost,
            center = Offset((prevWX - 3.dp.toPx()).coerceAtLeast(0f), prevWY)
        )
    } else if (!compactMode) {
        drawLine(
            color = dynamicArcColor.copy(alpha = 0.24f),
            start = Offset(0f, bottomY),
            end = Offset(canvasWidth, bottomY),
            strokeWidth = 1.2.dp.toPx()
        )
    }
}
