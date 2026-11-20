package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.roundToInt

private data class NumericTier(
    val label: String,
    val minInclusive: Float
)

private val RAM_TIERS_MB = listOf(
    NumericTier("1G+", 1024f),
    NumericTier("500M", 500f),
    NumericTier("250M", 250f),
    NumericTier("100M", 100f),
    NumericTier("50M", 50f),
    NumericTier("25M", 25f),
    NumericTier("10M", 10f),
    NumericTier("<10M", 0f)
)

private val CPU_PCT_TIERS = listOf(
    NumericTier("20%+", 20f),
    NumericTier("10%+", 10f),
    NumericTier("5%+", 5f),
    NumericTier("2%+", 2f),
    NumericTier("1%+", 1f),
    NumericTier(">0%", 0.05f),
    NumericTier("0%", 0f)
)

private val MINUTES_TIERS = listOf(
    NumericTier("1h+", 60f),
    NumericTier("30m+", 30f),
    NumericTier("10m+", 10f),
    NumericTier("5m+", 5f),
    NumericTier("1m+", 1f),
    NumericTier("<1m", 0f)
)

internal fun sortVitalsTaskEntries(
    entries: List<AppMemoryEntry>,
    tabKey: String,
    ramBySwap: Boolean,
    cpuSortMode: String,
    wakeSortMode: String
): List<AppMemoryEntry> {
    return when (tabKey) {
        "cpu" -> when (cpuSortMode) {
            "time" -> entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.cpuTimeSec }
                    .thenByDescending { it.cpuPct }
            )
            "hog" -> entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.isHighRisk }
                    .thenByDescending { it.ramMb }
            )
            else -> entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.cpuPct }
                    .thenByDescending { it.cpuTimeSec }
                    .thenByDescending { it.isHighRisk }
            )
        }
        "wakelocks" -> when (wakeSortMode) {
            "awake" -> entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.heldMinutes }
                    .thenByDescending { it.activeLocks }
            )
            else -> entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.activeLocks }
                    .thenByDescending { it.heldMinutes }
            )
        }
        else -> if (ramBySwap) {
            entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.swapMb }
                    .thenByDescending { it.ramMb }
            )
        } else {
            entries.sortedWith(
                compareByDescending<AppMemoryEntry> { it.ramMb }
                    .thenByDescending { it.swapMb }
            )
        }
    }
}

internal fun buildVitalsTaskJumpMarkers(
    entries: List<AppMemoryEntry>,
    tabKey: String,
    ramBySwap: Boolean,
    cpuSortMode: String,
    wakeSortMode: String
): List<StorageJumpMarker> {
    if (entries.isEmpty()) return emptyList()
    return when (tabKey) {
        "cpu" -> when (cpuSortMode) {
            "time" -> buildTierMarkersFromValues(entries, MINUTES_TIERS) { it.cpuTimeSec / 60f }
            "hog" -> {
                val out = mutableListOf<StorageJumpMarker>()
                val hogIdx = entries.indexOfFirst { it.isHighRisk }
                if (hogIdx >= 0) out.add(StorageJumpMarker("HOG", hogIdx))
                for (tier in CPU_PCT_TIERS) {
                    val idx = entries.indexOfFirst { !it.isHighRisk && it.cpuPct >= tier.minInclusive }
                    if (idx >= 0 && out.none { it.rowIndex == idx }) {
                        out.add(StorageJumpMarker(tier.label, idx))
                    }
                }
                out
            }
            else -> buildTierMarkersFromValues(entries, CPU_PCT_TIERS) { it.cpuPct }
        }
        "wakelocks" -> if (wakeSortMode == "awake") {
            buildTierMarkersFromValues(entries, MINUTES_TIERS) { it.heldMinutes }
        } else {
            val out = mutableListOf<StorageJumpMarker>()
            val lockBuckets = listOf(3 to "3L+", 2 to "2L", 1 to "1L")
            for ((minLocks, label) in lockBuckets) {
                val idx = entries.indexOfFirst {
                    it.activeLocks >= minLocks && (minLocks == 3 || it.activeLocks == minLocks)
                }
                if (idx >= 0 && out.none { it.rowIndex == idx }) {
                    out.add(StorageJumpMarker(label, idx))
                }
            }
            for (tier in MINUTES_TIERS) {
                val idx = entries.indexOfFirst { it.activeLocks == 0 && it.heldMinutes >= tier.minInclusive }
                if (idx >= 0 && out.none { it.rowIndex == idx }) {
                    out.add(StorageJumpMarker(tier.label, idx))
                }
            }
            out
        }
        else -> buildTierMarkersFromValues(entries, RAM_TIERS_MB) { if (ramBySwap) it.swapMb else it.ramMb }
    }
}

private inline fun buildTierMarkersFromValues(
    entries: List<AppMemoryEntry>,
    tiers: List<NumericTier>,
    valueOf: (AppMemoryEntry) -> Float
): List<StorageJumpMarker> {
    val out = mutableListOf<StorageJumpMarker>()
    var upperBound = Float.MAX_VALUE
    for (tier in tiers) {
        val idx = entries.indexOfFirst { e ->
            val v = valueOf(e)
            v >= tier.minInclusive && v < upperBound
        }
        if (idx >= 0 && out.none { it.rowIndex == idx }) {
            out.add(StorageJumpMarker(label = tier.label, rowIndex = idx))
        }
        upperBound = tier.minInclusive
    }
    return out
}

internal fun formatVitalsTaskPrimaryMetric(
    entry: AppMemoryEntry,
    tabKey: String,
    ramBySwap: Boolean,
    cpuSortMode: String
): String {
    return when (tabKey) {
        "cpu" -> when (cpuSortMode) {
            "time" -> formatCpuTimeCompact(entry.cpuTimeSec)
            else -> String.format(Locale.US, "%.1f%%", entry.cpuPct)
        }
        "wakelocks" -> if (entry.activeLocks > 0) {
            "${entry.activeLocks} LCK"
        } else {
            formatMinutesCompact(entry.heldMinutes)
        }
        else -> formatMbCompact(if (ramBySwap) entry.swapMb else entry.ramMb)
    }
}

internal fun formatVitalsTaskLoupeMetric(
    entry: AppMemoryEntry,
    tabKey: String,
    ramBySwap: Boolean,
    cpuSortMode: String
): String {
    return when (tabKey) {
        "cpu" -> {
            val pctStr = String.format(Locale.US, "%.1f%%", entry.cpuPct)
            val timeStr = formatCpuTimeCompact(entry.cpuTimeSec)
            if (cpuSortMode == "time") "$timeStr · $pctStr"
            else if (entry.isHighRisk) "HOG · $pctStr"
            else "$pctStr · $timeStr"
        }
        "wakelocks" -> if (entry.activeLocks > 0) {
            val dur = if (entry.heldMinutes > 0f) " · ${formatMinutesCompact(entry.heldMinutes)}" else ""
            "${entry.activeLocks} LOCK${if (entry.activeLocks > 1) "S" else ""}$dur"
        } else {
            "${formatMinutesCompact(entry.heldMinutes)} AWAKE"
        }
        else -> {
            val mb = if (ramBySwap) entry.swapMb else entry.ramMb
            val tag = if (ramBySwap) "SWAP" else "RAM"
            "${formatMbCompact(mb)} $tag"
        }
    }
}

private fun formatMbCompact(mb: Float): String {
    val safe = mb.coerceAtLeast(0f)
    return if (safe >= 1024f) {
        String.format(Locale.US, "%.2fG", safe / 1024f)
    } else {
        "${safe.roundToInt()}M"
    }
}

private fun formatCpuTimeCompact(totalSec: Long): String {
    if (totalSec <= 0L) return "0s"
    val mins = totalSec / 60L
    val remSec = totalSec % 60L
    return when {
        mins >= 60L -> "${mins / 60L}h ${mins % 60L}m"
        mins > 0L -> "${mins}m"
        else -> "${remSec}s"
    }
}

private fun formatMinutesCompact(mins: Float): String {
    val safe = mins.coerceAtLeast(0f)
    return when {
        safe >= 60f -> String.format(Locale.US, "%.1fh", safe / 60f)
        safe >= 10f -> "${safe.roundToInt()}m"
        else -> String.format(Locale.US, "%.1fm", safe)
    }
}

internal fun mapVitalsRailYToScroll(
    touchY: Float,
    railTop: Float,
    railBot: Float,
    markers: List<StorageJumpMarker>,
    rowH: Float,
    maxScroll: Float
): Float {
    val y = touchY.coerceIn(railTop, railBot)
    if (markers.isEmpty() || rowH <= 1f || maxScroll <= 0f) {
        val frac = ((y - railTop) / (railBot - railTop).coerceAtLeast(1f)).coerceIn(0f, 1f)
        return frac * maxScroll
    }
    val first = markers.first()
    if (y <= first.yCenter) {
        val span = (first.yCenter - railTop).coerceAtLeast(1f)
        val firstScroll = (first.rowIndex * rowH).coerceIn(0f, maxScroll)
        return ((y - railTop) / span) * firstScroll
    }
    for (i in 0 until markers.lastIndex) {
        val a = markers[i]
        val b = markers[i + 1]
        if (y <= b.yCenter) {
            val span = (b.yCenter - a.yCenter).coerceAtLeast(1f)
            val sA = (a.rowIndex * rowH).coerceIn(0f, maxScroll)
            val sB = (b.rowIndex * rowH).coerceIn(0f, maxScroll)
            return sA + ((y - a.yCenter) / span) * (sB - sA)
        }
    }
    val last = markers.last()
    val lastScroll = (last.rowIndex * rowH).coerceIn(0f, maxScroll)
    val span = (railBot - last.yCenter).coerceAtLeast(1f)
    return lastScroll + ((y - last.yCenter) / span) * (maxScroll - lastScroll)
}

internal fun mapVitalsScrollToRailY(
    scroll: Float,
    railTop: Float,
    railBot: Float,
    markers: List<StorageJumpMarker>,
    rowH: Float,
    maxScroll: Float
): Float {
    if (markers.isEmpty() || maxScroll <= 0f || rowH <= 1f) {
        val frac = if (maxScroll > 0f) (scroll / maxScroll).coerceIn(0f, 1f) else 0f
        return railTop + frac * (railBot - railTop)
    }
    val s = scroll.coerceIn(0f, maxScroll)
    val first = markers.first()
    val firstScroll = (first.rowIndex * rowH).coerceIn(0f, maxScroll)
    if (s <= firstScroll) {
        val frac = if (firstScroll > 0f) s / firstScroll else 1f
        return railTop + frac * (first.yCenter - railTop)
    }
    for (i in 0 until markers.lastIndex) {
        val a = markers[i]
        val b = markers[i + 1]
        val sA = (a.rowIndex * rowH).coerceIn(0f, maxScroll)
        val sB = (b.rowIndex * rowH).coerceIn(0f, maxScroll)
        if (s <= sB) {
            val frac = if (sB > sA) (s - sA) / (sB - sA) else 0f
            return a.yCenter + frac * (b.yCenter - a.yCenter)
        }
    }
    val last = markers.last()
    val lastScroll = (last.rowIndex * rowH).coerceIn(0f, maxScroll)
    val span = (maxScroll - lastScroll).coerceAtLeast(1f)
    return last.yCenter + ((s - lastScroll) / span) * (railBot - last.yCenter)
}

internal fun drawVitalsNumberedJumpRail(
    canvas: Canvas,
    railRect: RectF,
    rawMarkers: List<StorageJumpMarker>,
    scrollOffset: Float,
    maxScroll: Float,
    rowH: Float,
    itemCount: Int,
    accent: Int,
    dp: Float,
    highlightPaint: Paint,
    textPaint: Paint
): List<StorageJumpMarker> {
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(32, 255, 255, 255)
    canvas.drawRoundRect(railRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.7f * dp
    highlightPaint.color = Color.argb(55, 255, 255, 255)
    canvas.drawRoundRect(railRect, 7f * dp, 7f * dp, highlightPaint)

    if (maxScroll <= 0f || itemCount <= 0) return emptyList()

    val railTop = railRect.top + 10f * dp
    val railBot = railRect.bottom - 10f * dp
    val usableH = (railBot - railTop).coerceAtLeast(24f * dp)
    val safeRowH = rowH.coerceAtLeast(1f)
    val safeMaxScroll = maxScroll.coerceAtLeast(1f)

    if (rawMarkers.isEmpty()) {
        val thumbH = (railRect.height() * (railRect.height() / (itemCount * safeRowH))).coerceIn(22f * dp, railRect.height() * 0.6f)
        val thumbFrac = (scrollOffset / safeMaxScroll).coerceIn(0f, 1f)
        val thumbTop = railRect.top + thumbFrac * (railRect.height() - thumbH)
        val thumbRect = RectF(railRect.left + 2f * dp, thumbTop, railRect.right - 2f * dp, thumbTop + thumbH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = accent
        canvas.drawRoundRect(thumbRect, 4f * dp, 4f * dp, highlightPaint)
        return emptyList()
    }

    val minGap = 15.5f * dp
    val maxFitting = ((usableH / minGap).toInt() + 1).coerceAtLeast(2)
    val thinned = if (rawMarkers.size > maxFitting) {
        val picked = mutableListOf<StorageJumpMarker>()
        for (i in 0 until maxFitting) {
            val srcIdx = ((i.toFloat() / (maxFitting - 1)) * rawMarkers.lastIndex).toInt().coerceIn(0, rawMarkers.lastIndex)
            val m = rawMarkers[srcIdx]
            if (picked.none { it.rowIndex == m.rowIndex }) picked.add(m)
        }
        picked
    } else {
        rawMarkers
    }

    val laidOut = mutableListOf<StorageJumpMarker>()
    for ((i, m) in thinned.withIndex()) {
        val natY = railTop + ((m.rowIndex * safeRowH).coerceIn(0f, safeMaxScroll) / safeMaxScroll) * usableH
        val minY = if (laidOut.isEmpty()) railTop else laidOut.last().yCenter + minGap
        val remAfter = (thinned.lastIndex - i)
        val maxY = railBot - remAfter * minGap
        val finalY = natY.coerceAtLeast(minY).coerceAtMost(maxY.coerceAtLeast(minY))
        if (finalY <= railBot + 1f) {
            laidOut.add(m.copy(yCenter = finalY))
        }
    }

    val topVisibleIdx = ((scrollOffset + safeRowH * 0.3f) / safeRowH).toInt().coerceIn(0, (itemCount - 1).coerceAtLeast(0))
    val activeMarkerIdx = laidOut.indexOfLast { it.rowIndex <= topVisibleIdx }.coerceAtLeast(0)

    val thumbY = mapVitalsScrollToRailY(scrollOffset, railRect.top + 4f * dp, railRect.bottom - 4f * dp, laidOut, safeRowH, safeMaxScroll)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, Color.red(accent), Color.green(accent), Color.blue(accent))
    val thumbPill = RectF(
        railRect.left + 2f * dp,
        (thumbY - 7.5f * dp).coerceAtLeast(railRect.top + 1f * dp),
        railRect.right - 2f * dp,
        (thumbY + 7.5f * dp).coerceAtMost(railRect.bottom - 1f * dp)
    )
    canvas.drawRoundRect(thumbPill, 4f * dp, 4f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.CENTER
    val fm = textPaint.fontMetrics
    for ((idx, marker) in laidOut.withIndex()) {
        val isAct = (idx == activeMarkerIdx)
        if (isAct) {
            val pillRect = RectF(railRect.left + 1.5f * dp, marker.yCenter - 6.5f * dp, railRect.right - 1.5f * dp, marker.yCenter + 6.5f * dp)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(220, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(pillRect, 4f * dp, 4f * dp, highlightPaint)
        }
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = if (isAct) 8.2f * dp else 7.5f * dp
        textPaint.color = if (isAct) Color.BLACK else Color.argb(205, 215, 232, 250)
        canvas.drawText(marker.label, railRect.centerX(), marker.yCenter - ((fm.descent + fm.ascent) / 2f), textPaint)
    }
    return laidOut
}

internal fun LightspeedCruiseOverlay.drawVitalsScrubLoupeBubble(
    canvas: Canvas,
    listRight: Float,
    railRect: RectF,
    dragY: Float,
    primaryText: String,
    secondaryLabel: String,
    accent: Int,
    dp: Float
) {
    val bubbleW = 152f * dp
    val bubbleH = 38f * dp
    val cy = (if (dragY >= 0f) dragY else railRect.centerY())
        .coerceIn(railRect.top + bubbleH * 0.5f, railRect.bottom - bubbleH * 0.5f)
    val bRect = RectF(listRight - bubbleW - 4f * dp, cy - bubbleH * 0.5f, listRight - 4f * dp, cy + bubbleH * 0.5f)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(242, 10, 14, 24)
    canvas.drawRoundRect(bRect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * dp
    highlightPaint.color = accent
    canvas.drawRoundRect(bRect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = 12.6f * dp
    textPaint.color = accent
    canvas.drawText(clipTextToWidth(primaryText, bubbleW - 12f * dp), bRect.centerX(), bRect.top + 16.5f * dp, textPaint)

    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.8f * dp
    textPaint.color = Color.WHITE
    canvas.drawText(clipTextToWidth(secondaryLabel, bubbleW - 14f * dp), bRect.centerX(), bRect.top + 31f * dp, textPaint)
}

internal fun LightspeedCruiseOverlay.drawVitalsTaskSortBar(
    canvas: Canvas,
    left: Float,
    right: Float,
    top: Float,
    height: Float,
    itemCount: Int,
    accent: Int,
    dp: Float
) {
    vitalsTaskSortPillRects.clear()
    val ar = Color.red(accent)
    val ag = Color.green(accent)
    val ab = Color.blue(accent)

    val (options, selectedKey) = when (vitalsActiveTabKey) {
        "cpu" -> listOf("cpu" to "CPU %", "time" to "TIME", "hog" to "BG HOG") to vitalsCpuSortMode
        "wakelocks" -> listOf("locks" to "LOCKS", "awake" to "AWAKE") to vitalsWakeSortMode
        else -> listOf("ram" to "RAM", "swap" to "SWAP") to (if (vitalsRamSortBySwap) "swap" else "ram")
    }

    val pillGap = 4f * dp
    val pillW = if (options.size >= 3) 48f * dp else 54f * dp
    val groupW = options.size * pillW + (options.size - 1) * pillGap
    val groupLeft = right - groupW

    val countLabel = if (vitalsActiveTabKey == "ram") "$itemCount PROCS" else "$itemCount APPS"
    val maxSummaryW = (groupLeft - left - 6f * dp).coerceAtLeast(40f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    textPaint.textSize = 9.4f * dp
    textPaint.color = Color.rgb(34, 211, 238)
    val fm = textPaint.fontMetrics
    canvas.drawText(
        clipTextToWidth("$countLabel · SORT:", maxSummaryW),
        left,
        top + height * 0.5f - ((fm.descent + fm.ascent) / 2f),
        textPaint
    )

    for ((idx, opt) in options.withIndex()) {
        val (sKey, sLabel) = opt
        val isSel = (selectedKey == sKey)
        val pLeft = groupLeft + idx * (pillW + pillGap)
        val pRect = RectF(pLeft, top, pLeft + pillW, top + height)
        vitalsTaskSortPillRects[sKey] = pRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(215, ar, ag, ab) else Color.argb(36, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = if (isSel) accent else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.4f * dp
        textPaint.color = if (isSel) Color.BLACK else Color.WHITE
        val pFm = textPaint.fontMetrics
        canvas.drawText(sLabel, pRect.centerX(), pRect.centerY() - ((pFm.descent + pFm.ascent) / 2f), textPaint)
    }
}

/**
 * Draws an animated tactical radar sweep inside the Vitals / Storage loading canvas
 * and schedules the next VSync frame while background sampling is active.
 */
internal fun LightspeedCruiseOverlay.drawVitalsLoadingRadar(
    canvas: Canvas,
    centerX: Float,
    centerY: Float,
    accent: Int,
    scanMsg: String,
    textPaint: Paint,
    highlightPaint: Paint
) {
    if (vitalsModalScroller.computeScrollOffset()) {
        vitalsModalScrollOffset = vitalsModalScroller.currY.toFloat().coerceAtLeast(0f)
        postInvalidateOnAnimation()
    }

    val dp = resources.displayMetrics.density
    val r = 24f * dp
    val nowMs = android.os.SystemClock.uptimeMillis()
    val sweepDeg = ((nowMs % 1500L) / 1500f) * 360f
    val ar = Color.red(accent)
    val ag = Color.green(accent)
    val ab = Color.blue(accent)

    highlightPaint.shader = null
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * dp
    highlightPaint.color = Color.argb(105, ar, ag, ab)
    canvas.drawCircle(centerX, centerY, r, highlightPaint)
    highlightPaint.strokeWidth = 0.8f * dp
    highlightPaint.color = Color.argb(65, ar, ag, ab)
    canvas.drawCircle(centerX, centerY, r * 0.62f, highlightPaint)
    canvas.drawLine(centerX - r, centerY, centerX + r, centerY, highlightPaint)
    canvas.drawLine(centerX, centerY - r, centerX, centerY + r, highlightPaint)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(56, ar, ag, ab)
    canvas.drawArc(
        centerX - r,
        centerY - r,
        centerX + r,
        centerY + r,
        sweepDeg - 48f,
        48f,
        true,
        highlightPaint
    )

    val rad = Math.toRadians(sweepDeg.toDouble())
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.7f * dp
    highlightPaint.color = Color.argb(235, ar, ag, ab)
    canvas.drawLine(
        centerX,
        centerY,
        centerX + (kotlin.math.cos(rad) * r).toFloat(),
        centerY + (kotlin.math.sin(rad) * r).toFloat(),
        highlightPaint
    )

    val blips = arrayOf(42f to 0.56f, 168f to 0.78f, 284f to 0.44f)
    highlightPaint.style = Paint.Style.FILL
    for ((deg, dist) in blips) {
        val bRad = Math.toRadians(deg.toDouble())
        val diff = ((sweepDeg - deg) % 360f + 360f) % 360f
        val pulse = if (diff < 110f) (1f - diff / 110f) else 0.14f
        highlightPaint.color = Color.argb((60 + pulse * 195).toInt().coerceIn(0, 255), ar, ag, ab)
        canvas.drawCircle(
            centerX + (kotlin.math.cos(bRad) * r * dist).toFloat(),
            centerY + (kotlin.math.sin(bRad) * r * dist).toFloat(),
            2.2f * dp * (0.85f + pulse * 0.4f),
            highlightPaint
        )
    }

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.4f * dp
    textPaint.color = Color.argb(210, ar, ag, ab)
    canvas.drawText(scanMsg, centerX, centerY + r + 18f * dp, textPaint)
    postInvalidateOnAnimation()
}
