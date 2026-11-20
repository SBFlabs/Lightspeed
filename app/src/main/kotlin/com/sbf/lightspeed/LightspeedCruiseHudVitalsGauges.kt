package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.roundToInt

internal val cockpitMonoBold: Typeface by lazy { Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
private val cockpitScratchRect = RectF()
private val cockpitScratchPath = Path()

/** Emerald Green (<75%), Amber (75..89%), Crimson Red (>=90%). */
internal fun vitalsPctColor(pct: Int, alpha: Float): Int {
    val a = (255 * alpha).toInt().coerceIn(0, 255)
    return when {
        pct >= 90 -> Color.argb(a, 239, 68, 68)
        pct >= 75 -> Color.argb(a, 245, 158, 11)
        else -> Color.argb(a, 34, 197, 94)
    }
}

internal fun vitalsTempStateLabel(tempC: Int): String = when {
    tempC >= 44 -> "HOT"
    tempC >= 38 -> "WARM"
    tempC >= 33 -> "NORMAL"
    else -> "COOL"
}

/** Emerald Green (<38°C), Amber (38..43°C), Crimson Red (>=44°C). */
internal fun vitalsTempColor(tempC: Int, alpha: Float): Int {
    val a = (255 * alpha).toInt().coerceIn(0, 255)
    return when {
        tempC >= 44 -> Color.argb(a, 239, 68, 68)
        tempC >= 38 -> Color.argb(a, 245, 158, 11)
        else -> Color.argb(a, 34, 197, 94)
    }
}

internal fun LightspeedCruiseOverlay.drawVitalsThermometerGauge(
    canvas: Canvas,
    cx: Float,
    top: Float,
    height: Float,
    fraction: Float,
    color: Int,
    valueText: String,
    stateLabel: String,
    density: Float
) {
    val barW = 10f * density
    val barLeft = cx - 22f * density
    val barTop = top + 2f * density
    val barBot = top + height - 2f * density
    val segCount = 6
    val segGap = 2f * density
    val segH = ((barBot - barTop) - (segCount - 1) * segGap) / segCount
    val activeSegs = (fraction * segCount).roundToInt().coerceIn(1, segCount)

    highlightPaint.style = Paint.Style.FILL
    for (i in 0 until segCount) {
        val segIdxFromBot = segCount - 1 - i
        val sTop = barTop + i * (segH + segGap)
        cockpitScratchRect.set(barLeft, sTop, barLeft + barW, sTop + segH)
        highlightPaint.color = if (segIdxFromBot < activeSegs) color else Color.argb(38, 255, 255, 255)
        canvas.drawRoundRect(cockpitScratchRect, 2f * density, 2f * density, highlightPaint)
    }

    val textX = barLeft + barW + 6f * density
    val barCenterY = top + height * 0.5f
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 12.2f * density
    textPaint.color = Color.WHITE
    val valFm = textPaint.fontMetrics
    canvas.drawText(valueText, textX, barCenterY - 2f * density - valFm.descent * 0.25f, textPaint)

    textPaint.textSize = 8.8f * density
    textPaint.color = color
    val stFm = textPaint.fontMetrics
    canvas.drawText(stateLabel, textX, barCenterY + 2f * density - stFm.ascent * 0.85f, textPaint)
}

internal fun LightspeedCruiseOverlay.drawVitalsArcGauge(
    canvas: Canvas,
    cx: Float,
    top: Float,
    diameter: Float,
    fraction: Float,
    color: Int,
    valueText: String,
    ringTitle: String,
    subLabel: String,
    density: Float
) {
    cockpitScratchRect.set(cx - diameter / 2f, top, cx + diameter / 2f, top + diameter)
    val strokeW = 4.2f * density

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeCap = Paint.Cap.ROUND
    highlightPaint.strokeWidth = strokeW
    highlightPaint.color = Color.argb(45, 255, 255, 255)
    canvas.drawArc(cockpitScratchRect, 150f, 240f, false, highlightPaint)

    if (fraction > 0f) {
        highlightPaint.color = color
        canvas.drawArc(cockpitScratchRect, 150f, 240f * fraction.coerceIn(0.02f, 1f), false, highlightPaint)
    }
    highlightPaint.strokeCap = Paint.Cap.BUTT

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = (diameter * 0.27f).coerceAtLeast(9.5f * density)
    textPaint.color = Color.WHITE
    val fm = textPaint.fontMetrics
    canvas.drawText(valueText, cx, cockpitScratchRect.centerY() - ((fm.descent + fm.ascent) / 2f) - 1f * density, textPaint)

    // Ring title ("RAM" / "SWAP") inside the bottom opening of the 240° arc
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = (diameter * 0.19f).coerceAtLeast(8.2f * density)
    textPaint.color = color
    val rFm = textPaint.fontMetrics
    canvas.drawText(ringTitle, cx, cockpitScratchRect.bottom - 1.5f * density - rFm.descent * 0.35f, textPaint)

    // Used / Total numbers cleanly underneath the dial
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 9.0f * density
    textPaint.color = Color.argb(230, 215, 230, 250)
    canvas.drawText(subLabel, cx, cockpitScratchRect.bottom + 10.5f * density, textPaint)
}

internal fun LightspeedCruiseOverlay.drawStorageProgressBar(
    canvas: Canvas,
    left: Float,
    right: Float,
    labelY: Float,
    label: String,
    pct: Int,
    barColor: Int,
    density: Float
) {
    textPaint.textAlign = Paint.Align.RIGHT
    textPaint.typeface = cockpitMonoBold
    textPaint.textSize = 10.0f * density
    textPaint.color = barColor
    val pctStr = "$pct%"
    val pctW = textPaint.measureText(pctStr)
    canvas.drawText(pctStr, right, labelY, textPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true, fallback = cockpitMonoBold)
    textPaint.textSize = 9.8f * density
    textPaint.color = Color.WHITE
    canvas.drawText(clipTextToWidth(label, (right - pctW - left - 6f * density).coerceAtLeast(40f * density)), left, labelY, textPaint)

    val barTop = labelY + 3f * density
    drawCockpitSegmentedBar(
        canvas = canvas,
        left = left,
        top = barTop,
        right = right,
        bottom = barTop + 5.8f * density,
        ratio = pct / 100f,
        segments = 16,
        fillColor = barColor,
        bgColor = Color.argb(42, 255, 255, 255),
        density = density
    )
}

internal fun LightspeedCruiseOverlay.drawCockpitSegmentedBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    ratio: Float,
    segments: Int,
    fillColor: Int,
    bgColor: Int,
    density: Float
) {
    val totalW = (right - left).coerceAtLeast(20f * density)
    val gap = 2f * density
    val segCount = segments.coerceAtLeast(4)
    val segW = ((totalW - gap * (segCount - 1)) / segCount).coerceAtLeast(2f * density)
    val filledSegs = if (ratio <= 0f) 0 else (ratio.coerceIn(0f, 1f) * segCount).roundToInt().coerceIn(1, segCount)
    highlightPaint.style = Paint.Style.FILL
    for (i in 0 until segCount) {
        val sx = left + i * (segW + gap)
        highlightPaint.color = if (i < filledSegs) fillColor else bgColor
        cockpitScratchRect.set(sx, top, sx + segW, bottom)
        canvas.drawRoundRect(cockpitScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
    }
}

internal fun LightspeedCruiseOverlay.drawCockpitDualSegmentBar(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    firstRatio: Float,
    secondRatio: Float,
    firstColor: Int,
    secondColor: Int,
    density: Float
) {
    val totalW = (right - left).coerceAtLeast(20f * density)
    val segments = 16
    val gap = 2f * density
    val segW = ((totalW - gap * (segments - 1)) / segments).coerceAtLeast(2f * density)
    val sumRatio = (firstRatio + secondRatio).coerceAtLeast(0f)
    val firstSegs = if (sumRatio <= 0f) 0 else ((firstRatio / sumRatio) * segments).roundToInt().coerceIn(0, segments)
    highlightPaint.style = Paint.Style.FILL
    for (i in 0 until segments) {
        val sx = left + i * (segW + gap)
        highlightPaint.color = when {
            sumRatio <= 0f -> Color.argb(38, 255, 255, 255)
            i < firstSegs -> firstColor
            else -> secondColor
        }
        cockpitScratchRect.set(sx, top, sx + segW, bottom)
        canvas.drawRoundRect(cockpitScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
    }
}

internal fun LightspeedCruiseOverlay.drawCockpitVectorIcon(
    canvas: Canvas,
    kind: String,
    cx: Float,
    cy: Float,
    size: Float,
    color: Int,
    density: Float
) {
    val r = size * 0.48f
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.25f * density
    highlightPaint.color = color
    when (kind) {
        "BATTERY" -> {
            cockpitScratchRect.set(cx - r * 0.85f, cy - r * 0.52f, cx + r * 0.72f, cy + r * 0.52f)
            canvas.drawRoundRect(cockpitScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
            highlightPaint.style = Paint.Style.FILL
            canvas.drawRect(cx + r * 0.72f, cy - r * 0.24f, cx + r, cy + r * 0.24f, highlightPaint)
            canvas.drawRect(cx - r * 0.55f, cy - r * 0.25f, cx + r * 0.35f, cy + r * 0.25f, highlightPaint)
        }
        "BOLT" -> {
            cockpitScratchPath.reset()
            cockpitScratchPath.moveTo(cx + r * 0.15f, cy - r)
            cockpitScratchPath.lineTo(cx - r * 0.55f, cy + r * 0.08f)
            cockpitScratchPath.lineTo(cx + r * 0.05f, cy + r * 0.08f)
            cockpitScratchPath.lineTo(cx - r * 0.15f, cy + r)
            cockpitScratchPath.lineTo(cx + r * 0.55f, cy - r * 0.08f)
            cockpitScratchPath.lineTo(cx - r * 0.05f, cy - r * 0.08f)
            cockpitScratchPath.close()
            highlightPaint.style = Paint.Style.FILL
            canvas.drawPath(cockpitScratchPath, highlightPaint)
        }
        "HEART" -> {
            cockpitScratchPath.reset()
            cockpitScratchPath.moveTo(cx, cy + r * 0.85f)
            cockpitScratchPath.lineTo(cx - r * 0.85f, cy - r * 0.05f)
            cockpitScratchPath.lineTo(cx - r * 0.45f, cy - r * 0.65f)
            cockpitScratchPath.lineTo(cx, cy - r * 0.25f)
            cockpitScratchPath.lineTo(cx + r * 0.45f, cy - r * 0.65f)
            cockpitScratchPath.lineTo(cx + r * 0.85f, cy - r * 0.05f)
            cockpitScratchPath.close()
            canvas.drawPath(cockpitScratchPath, highlightPaint)
        }
        "CPU" -> {
            canvas.drawRect(cx - r * 0.55f, cy - r * 0.55f, cx + r * 0.55f, cy + r * 0.55f, highlightPaint)
            canvas.drawLine(cx - r, cy - r * 0.25f, cx - r * 0.55f, cy - r * 0.25f, highlightPaint)
            canvas.drawLine(cx - r, cy + r * 0.25f, cx - r * 0.55f, cy + r * 0.25f, highlightPaint)
            canvas.drawLine(cx + r * 0.55f, cy - r * 0.25f, cx + r, cy - r * 0.25f, highlightPaint)
            canvas.drawLine(cx + r * 0.55f, cy + r * 0.25f, cx + r, cy + r * 0.25f, highlightPaint)
        }
        "SCREEN" -> {
            cockpitScratchRect.set(cx - r * 0.85f, cy - r * 0.62f, cx + r * 0.85f, cy + r * 0.42f)
            canvas.drawRoundRect(cockpitScratchRect, 1.5f * density, 1.5f * density, highlightPaint)
            canvas.drawLine(cx - r * 0.35f, cy + r * 0.78f, cx + r * 0.35f, cy + r * 0.78f, highlightPaint)
        }
        "CELL" -> {
            highlightPaint.style = Paint.Style.FILL
            canvas.drawRect(cx - r * 0.75f, cy + r * 0.2f, cx - r * 0.45f, cy + r * 0.8f, highlightPaint)
            canvas.drawRect(cx - r * 0.15f, cy - r * 0.2f, cx + r * 0.15f, cy + r * 0.8f, highlightPaint)
            canvas.drawRect(cx + r * 0.45f, cy - r * 0.7f, cx + r * 0.75f, cy + r * 0.8f, highlightPaint)
        }
        "WIFI" -> {
            cockpitScratchRect.set(cx - r, cy - r * 0.6f, cx + r, cy + r * 1.4f)
            canvas.drawArc(cockpitScratchRect, 225f, 90f, false, highlightPaint)
            cockpitScratchRect.set(cx - r * 0.6f, cy - r * 0.2f, cx + r * 0.6f, cy + r)
            canvas.drawArc(cockpitScratchRect, 225f, 90f, false, highlightPaint)
            highlightPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + r * 0.65f, 1.3f * density, highlightPaint)
        }
        else -> {
            val sq = r * 0.38f
            canvas.drawRect(cx - r * 0.75f, cy - r * 0.75f, cx - r * 0.75f + sq * 1.3f, cy - r * 0.75f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx + r * 0.15f, cy - r * 0.75f, cx + r * 0.15f + sq * 1.3f, cy - r * 0.75f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx - r * 0.75f, cy + r * 0.15f, cx - r * 0.75f + sq * 1.3f, cy + r * 0.15f + sq * 1.3f, highlightPaint)
            canvas.drawRect(cx + r * 0.15f, cy + r * 0.15f, cx + r * 0.15f + sq * 1.3f, cy + r * 0.15f + sq * 1.3f, highlightPaint)
        }
    }
}
