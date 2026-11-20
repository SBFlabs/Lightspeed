package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.hypot

internal data class VitalsCachedChartData(
    val spanKey: String,
    val sampleCount: Int,
    val lastSampleTs: Long,
    val gLeft: Float,
    val gRight: Float,
    val gTop: Float,
    val gBot: Float,
    val availableSpanMs: Long,
    val peakTempC: Int,
    val avgRamMb: Int,
    val topFgApps: String,
    val tempPath: Path?,
    val ramPath: Path?
)

internal var vitalsChartCache: VitalsCachedChartData? = null
private val vitalsChartRect = RectF()

internal val VITALS_SPAN_OPTIONS = listOf(
    "1H" to 1L * 3_600_000L,
    "2H" to 2L * 3_600_000L,
    "3H" to 3L * 3_600_000L,
    "24H" to 24L * 3_600_000L,
    "7D" to 7L * 24L * 3_600_000L,
    "30D" to 30L * 24L * 3_600_000L
)

internal fun LightspeedCruiseOverlay.drawVitalsTelemetryHistorySection(
    canvas: Canvas,
    left: Float,
    padX: Float,
    modalW: Float,
    tabTop: Float,
    tabH: Float,
    accent: Int,
    dp: Float
): Float {
    val windowMs = VITALS_SPAN_OPTIONS.firstOrNull { it.first == vitalsModalSpanKey }?.second ?: (3L * 3_600_000L)
    val chartTop = tabTop + tabH + 7f * dp + 24f * dp + 8f * dp
    val chartH = 78f * dp
    vitalsChartRect.set(left + padX, chartTop, vitalsModalRect.right - padX, chartTop + chartH)
    val gLeft = vitalsChartRect.left + 8f * dp
    val gRight = vitalsChartRect.right - 8f * dp
    val gTop = vitalsChartRect.top + 18f * dp
    val gBot = vitalsChartRect.bottom - 16f * dp

    val history = LightspeedVitalsHistoryStore.getSamplesInWindow(context, windowMs)
    val lastTs = history.lastOrNull()?.timestampMs ?: 0L
    val cachedChart = vitalsChartCache?.takeIf {
        it.spanKey == vitalsModalSpanKey &&
            it.sampleCount == history.size &&
            it.lastSampleTs == lastTs &&
            it.gLeft == gLeft &&
            it.gRight == gRight &&
            it.gTop == gTop &&
            it.gBot == gBot
    } ?: run {
        val availableSpanMsNow = LightspeedVitalsHistoryStore.getAvailableHistorySpanMs(context)
        val snapFallback = LightspeedCruiseHudPods.latestBatterySnapshot
        val peakTempCNow = history.maxOfOrNull { it.tempC } ?: snapFallback.tempC
        val avgRamMbNow = if (history.isNotEmpty()) history.map { it.ramUsedMb }.average().toInt()
        else (snapFallback.usedRamGb * 1024).toInt()
        val topFgNow = history.map { it.foregroundPkg }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
            .joinToString(", ") { it.key.substringAfterLast('.').uppercase() }
            .ifBlank { "CURRENT SESSION" }

        var tPath: Path? = null
        var rPath: Path? = null
        if (history.size >= 2) {
            val minT = (history.minOf { it.tempC } - 2).toFloat()
            val maxT = (history.maxOf { it.tempC } + 2).toFloat().coerceAtLeast(minT + 4f)
            val totalRamMb = (snapFallback.totalRamGb * 1024.0).toFloat().coerceAtLeast(1024f)
            val tempP = Path()
            val ramP = Path()
            for (i in history.indices) {
                val s = history[i]
                val px = gLeft + (i.toFloat() / (history.size - 1)) * (gRight - gLeft)
                val ty = gBot - (((s.tempC - minT) / (maxT - minT)).coerceIn(0f, 1f)) * (gBot - gTop)
                val ry = gBot - ((s.ramUsedMb / totalRamMb).coerceIn(0f, 1f)) * (gBot - gTop)
                if (i == 0) {
                    tempP.moveTo(px, ty)
                    ramP.moveTo(px, ry)
                } else {
                    tempP.lineTo(px, ty)
                    ramP.lineTo(px, ry)
                }
            }
            tPath = tempP
            rPath = ramP
        }
        VitalsCachedChartData(
            spanKey = vitalsModalSpanKey,
            sampleCount = history.size,
            lastSampleTs = lastTs,
            gLeft = gLeft,
            gRight = gRight,
            gTop = gTop,
            gBot = gBot,
            availableSpanMs = availableSpanMsNow,
            peakTempC = peakTempCNow,
            avgRamMb = avgRamMbNow,
            topFgApps = topFgNow,
            tempPath = tPath,
            ramPath = rPath
        ).also { vitalsChartCache = it }
    }

    val availableSpanMs = cachedChart.availableSpanMs
    val spanTop = tabTop + tabH + 7f * dp
    val spanH = 24f * dp
    val spanGap = 4f * dp
    val spanW = (modalW - padX * 2f - spanGap * (VITALS_SPAN_OPTIONS.size - 1)) / VITALS_SPAN_OPTIONS.size
    for ((idx, opt) in VITALS_SPAN_OPTIONS.withIndex()) {
        val (key, optWindowMs) = opt
        val isPartial = optWindowMs > (availableSpanMs + 15L * 60_000L)
        val isSel = (vitalsModalSpanKey == key)
        val pLeft = left + padX + idx * (spanW + spanGap)
        val pRect = vitalsSpanPillRects.getOrPut(key) { RectF() }
        pRect.set(pLeft, spanTop, pLeft + spanW, spanTop + spanH)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) Color.argb(195, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = if (isSel) accent else if (isPartial) Color.argb(130, 245, 158, 11) else Color.argb(65, 255, 255, 255)
        canvas.drawRoundRect(pRect, 5f * dp, 5f * dp, highlightPaint)

        val pillText = if (isPartial) "$key !" else key
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.4f * dp
        textPaint.color = if (isSel) Color.BLACK else if (isPartial) Color.rgb(245, 158, 11) else Color.WHITE
        val pFm = textPaint.fontMetrics
        canvas.drawText(pillText, pRect.centerX(), pRect.centerY() - ((pFm.descent + pFm.ascent) / 2f), textPaint)
    }

    val isSelectedSpanPartial = windowMs > (availableSpanMs + 15L * 60_000L)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(26, 255, 255, 255)
    canvas.drawRoundRect(vitalsChartRect, 8f * dp, 8f * dp, highlightPaint)

    val peakTempC = cachedChart.peakTempC
    val avgRamMb = cachedChart.avgRamMb
    val topFgApps = cachedChart.topFgApps

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.0f * dp
    textPaint.color = Color.rgb(245, 158, 11)
    canvas.drawText("PEAK TEMP: ${LightspeedCruiseHudPods.formatTemp(context, peakTempC)}", vitalsChartRect.left + 8f * dp, vitalsChartRect.top + 13.5f * dp, textPaint)

    textPaint.textAlign = Paint.Align.RIGHT
    if (isSelectedSpanPartial) {
        val availStr = LightspeedCruiseHudPods.formatDurationCompact(availableSpanMs.coerceAtLeast(60_000L))
        textPaint.color = Color.rgb(245, 158, 11)
        canvas.drawText("! $availStr OF $vitalsModalSpanKey · ${avgRamMb}MB", vitalsChartRect.right - 8f * dp, vitalsChartRect.top + 13.5f * dp, textPaint)
    } else {
        textPaint.color = Color.rgb(34, 211, 238)
        canvas.drawText("AVG RAM: ${avgRamMb}MB · ${cachedChart.sampleCount} PTS", vitalsChartRect.right - 8f * dp, vitalsChartRect.top + 13.5f * dp, textPaint)
    }

    if (cachedChart.tempPath != null && cachedChart.ramPath != null) {
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.5f * dp
        highlightPaint.color = Color.rgb(245, 158, 11)
        canvas.drawPath(cachedChart.tempPath, highlightPaint)
        highlightPaint.color = Color.rgb(34, 211, 238)
        canvas.drawPath(cachedChart.ramPath, highlightPaint)
    } else {
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT
        textPaint.textSize = 9.8f * dp
        textPaint.color = Color.argb(165, 190, 205, 225)
        canvas.drawText("COLLECTING ROLLING SAMPLES...", vitalsChartRect.centerX(), (gTop + gBot) * 0.5f + 3f * dp, textPaint)
    }

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.6f * dp
    textPaint.color = Color.argb(215, 200, 220, 245)
    canvas.drawText(clipTextToWidth("ACTIVE: $topFgApps", vitalsChartRect.width() - 16f * dp), vitalsChartRect.left + 8f * dp, vitalsChartRect.bottom - 5f * dp, textPaint)

    return vitalsChartRect.bottom
}

internal fun LightspeedCruiseOverlay.handleVitalsTaskManagerModalTouch(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y

    fun scrollFromRailY(touchY: Float) {
        if (vitalsModalMaxScroll <= 0f || vitalsFastRailRect.height() <= 1f) return
        if (vitalsActiveTabKey == "storage") {
            scrollStorageFromRailY(touchY)
            return
        }
        vitalsStorageDragY = touchY.coerceIn(vitalsFastRailRect.top, vitalsFastRailRect.bottom)
        vitalsModalScrollOffset = mapVitalsRailYToScroll(
            touchY = touchY,
            railTop = vitalsFastRailRect.top,
            railBot = vitalsFastRailRect.bottom,
            markers = vitalsStorageJumpMarkers,
            rowH = vitalsStorageRowH,
            maxScroll = vitalsModalMaxScroll
        ).coerceIn(0f, vitalsModalMaxScroll)
        invalidate()
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            vitalsModalScroller.forceFinished(true)
            vitalsVelocityTracker?.recycle()
            vitalsVelocityTracker = VelocityTracker.obtain().apply { addMovement(event) }
            touchDownX = x; touchDownY = y; lastTouchY = y
            if (vitalsCloseRect.contains(x, y) || !vitalsModalRect.contains(x, y)) {
                triggerHardwareHaptic(18, 80)
                closeVitalsTaskManagerModal()
                return true
            }
            val railSlop = 5f * resources.displayMetrics.density
            if (!vitalsInfoOpen && !vitalsFastRailRect.isEmpty &&
                x >= vitalsFastRailRect.left - railSlop &&
                x <= vitalsFastRailRect.right + railSlop &&
                y >= vitalsFastRailRect.top &&
                y <= vitalsFastRailRect.bottom
            ) {
                vitalsModalDraggingRail = true
                triggerHardwareHaptic(14, 70)
                scrollFromRailY(y)
                return true
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            vitalsVelocityTracker?.addMovement(event)
            if (vitalsModalDraggingRail) {
                scrollFromRailY(y)
                return true
            }
            val dy = y - lastTouchY
            lastTouchY = y
            if (vitalsModalMaxScroll > 0f) {
                vitalsModalScrollOffset = (vitalsModalScrollOffset - dy).coerceIn(0f, vitalsModalMaxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            vitalsVelocityTracker?.addMovement(event)
            if (vitalsModalDraggingRail) {
                vitalsModalDraggingRail = false
                vitalsStorageDragY = -1f
                vitalsVelocityTracker?.recycle()
                vitalsVelocityTracker = null
                invalidate()
                return true
            }
            val isTap = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f
            if (!isTap && event.actionMasked == MotionEvent.ACTION_UP && vitalsModalMaxScroll > 0f) {
                vitalsVelocityTracker?.let { vt ->
                    vt.computeCurrentVelocity(1000)
                    val vy = vt.yVelocity
                    val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
                    if (abs(vy) > minFling) {
                        vitalsModalScroller.fling(
                            0,
                            vitalsModalScrollOffset.toInt(),
                            0,
                            (-vy).toInt(),
                            0,
                            0,
                            0,
                            vitalsModalMaxScroll.toInt()
                        )
                        postInvalidateOnAnimation()
                    }
                }
            }
            vitalsVelocityTracker?.recycle()
            vitalsVelocityTracker = null
            if (isTap) {
                if (!vitalsInfoButtonRect.isEmpty && vitalsInfoButtonRect.contains(x, y)) {
                    triggerHardwareHaptic(16, 80)
                    vitalsInfoOpen = !vitalsInfoOpen
                    invalidate()
                    return true
                }
                if (vitalsInfoOpen && vitalsInfoCardRect.contains(x, y)) {
                    vitalsInfoOpen = false
                    invalidate()
                    return true
                }
                if (vitalsTempUnitToggleRect.contains(x, y)) {
                    triggerHardwareHaptic(16, 80)
                    LightspeedCruiseHudPods.toggleTempUnit(context)
                    invalidate()
                    return true
                }
                for ((key, rect) in vitalsSpanPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(16, 80)
                        vitalsModalSpanKey = key
                        invalidate()
                        return true
                    }
                }
                for ((tKey, rect) in vitalsTabPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(18, 90)
                        switchVitalsTaskTab(tKey)
                        return true
                    }
                }
                if (vitalsActiveTabKey == "storage" && handleVitalsStorageTabTouch(x, y)) {
                    return true
                }
                for ((sKey, rect) in vitalsTaskSortPillRects) {
                    if (rect.contains(x, y)) {
                        triggerHardwareHaptic(16, 80)
                        when (vitalsActiveTabKey) {
                            "cpu" -> vitalsCpuSortMode = sKey
                            "wakelocks" -> vitalsWakeSortMode = sKey
                            else -> vitalsRamSortBySwap = (sKey == "swap")
                        }
                        vitalsCachedMemApps = sortVitalsTaskEntries(
                            entries = vitalsCachedMemApps,
                            tabKey = vitalsActiveTabKey,
                            ramBySwap = vitalsRamSortBySwap,
                            cpuSortMode = vitalsCpuSortMode,
                            wakeSortMode = vitalsWakeSortMode
                        )
                        vitalsModalScroller.forceFinished(true)
                        vitalsModalScrollOffset = 0f
                        invalidate()
                        return true
                    }
                }
                for ((pkg, rect) in vitalsForceStopRects) {
                    if (rect.contains(x, y)) {
                        val label = vitalsCachedMemApps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                        triggerHardwareHaptic(24, 120)
                        thread(name = "VitalsForceStop") {
                            val stopped = LightspeedVitalsHistoryStore.forceStopApp(
                                context = context,
                                packageName = pkg,
                                appLabel = label,
                                onBeforeStartSettings = {
                                    closeVitalsTaskManagerModal()
                                    dismissOverlay()
                                }
                            )
                            val updated = if (stopped) {
                                vitalsCachedMemApps.filterNot { it.packageName == pkg }
                            } else {
                                vitalsCachedMemApps
                            }
                            post {
                                vitalsCachedMemApps = updated
                                invalidate()
                            }
                        }
                        return true
                    }
                }
            }
            return true
        }
    }
    return true
}
