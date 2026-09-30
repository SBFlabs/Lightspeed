package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

internal data class HorizonRailLayout(
    val railLeft: Float,
    val railRight: Float,
    val railSpanPxActual: Float,
    val lineYs: FloatArray,
    val thicknesses: FloatArray,
    val progressXs: FloatArray,
    val activeStreams: List<HorizonStream>,
    val primaryColor: Int,
    val sensorHeight: Float,
    val gapDp: Float
)

internal fun LightspeedStatusBarOverlay.computeRailLayout(
    activeStreams: List<HorizonStream>,
    d: Float,
    screenW: Float,
    isLandscape: Boolean,
    m3Primary: Int
): HorizonRailLayout {
    val screenWidthDp = (screenW / d).toInt()
    val portraitDimDp = (minOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels) / d).toInt()
    val railSpanPref = renderCache.railSpanPref
    val isFullWidth = railSpanPref >= 999 || railSpanPref >= (screenWidthDp - 15) || railSpanPref >= (portraitDimDp - 15)

    val safePaddingLeftDp = renderCache.safePaddingLeftDp
    val safePaddingRightDp = renderCache.safePaddingRightDp
    val safeInsetLeft = safePaddingLeftDp * d
    val safeInsetRight = safePaddingRightDp * d

    val (railLeft, railRight) = if (isFullWidth) {
        Pair(safeInsetLeft, screenW - safeInsetRight)
    } else {
        val railSpanPx = (railSpanPref * d).coerceIn(50f * d, screenW)
        val railAlign = renderCache.railAlign
        val railOffsetX = renderCache.railOffsetX * d
        val availableScreenLeft = safeInsetLeft
        val availableScreenRight = (screenW - safeInsetRight).coerceAtLeast(availableScreenLeft + 20f * d)

        val baseRailLeft = when (railAlign) {
            "left" -> (availableScreenLeft + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            "right" -> (availableScreenRight - railSpanPx + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
            else -> (((screenW - railSpanPx) / 2f) + railOffsetX).coerceIn(availableScreenLeft, (availableScreenRight - railSpanPx).coerceAtLeast(availableScreenLeft))
        }
        val left = baseRailLeft.coerceIn(availableScreenLeft, (availableScreenRight - 20f * d).coerceAtLeast(availableScreenLeft))
        val right = (left + railSpanPx).coerceAtMost(availableScreenRight)
        Pair(left, right)
    }
    val railSpanPxActual = (railRight - railLeft).coerceAtLeast(20f * d)

    val railOffsetY = renderCache.railOffsetY.toFloat() * d
    val railThicknessDp = renderCache.railThicknessDp
    val stackSpacingDp = renderCache.stackSpacingDp
    val gapDp = stackSpacingDp

    val thicknesses = FloatArray(activeStreams.size) { i ->
        if (i == 0) railThicknessDp.toFloat() else (railThicknessDp * (1f - i * 0.15f)).coerceAtLeast(1.5f)
    }
    val lineYs = FloatArray(activeStreams.size)

    lineYs[0] = railOffsetY + ((thicknesses[0] * d) / 2f)
    for (i in 1 until activeStreams.size) {
        lineYs[i] = lineYs[i - 1] + ((thicknesses[i - 1] * d) / 2f) + (gapDp * d) + ((thicknesses[i] * d) / 2f)
    }

    val progressXs = FloatArray(activeStreams.size) { i ->
        railLeft + (railSpanPxActual * activeStreams[i].progressFraction)
    }

    val colorMode = renderCache.colorMode
    val primaryColor = if (activeStreams.isNotEmpty()) resolveStreamColor(0, activeStreams[0], colorMode, m3Primary) else m3Primary

    val thicknessDp = renderCache.thicknessDp
    val sensorHeight = thicknessDp * d

    return HorizonRailLayout(
        railLeft = railLeft,
        railRight = railRight,
        railSpanPxActual = railSpanPxActual,
        lineYs = lineYs,
        thicknesses = thicknesses,
        progressXs = progressXs,
        activeStreams = activeStreams,
        primaryColor = primaryColor,
        sensorHeight = sensorHeight,
        gapDp = gapDp
    )
}

internal fun LightspeedStatusBarOverlay.drawHorizonRail(
    canvas: Canvas,
    layout: HorizonRailLayout,
    d: Float,
    m3Primary: Int
) {
    val isDropShadow = renderCache.isDropShadow
    val railTrackOpacityPct = renderCache.railTrackOpacityPct
    val railGlowPct = renderCache.railGlowPct
    val colorMode = renderCache.colorMode
    val isTerminalCaps = renderCache.isTerminalCaps
    val activeStreams = layout.activeStreams

    // Ambient Drop Shadow & Contrast Trench
    if (isDropShadow) {
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        activeStreams.forEachIndexed { index, stream ->
            val thickness = layout.thicknesses[index]
            val lineY = layout.lineYs[index]
            val progX = layout.progressXs[index]

            if (railTrackOpacityPct > 0) {
                shadowPaint.strokeWidth = (thickness + 2.2f) * d
                shadowPaint.color = Color.argb(75, 0, 0, 0)
                canvas.drawLine(layout.railLeft, lineY + (1.0f * d), layout.railRight, lineY + (1.0f * d), shadowPaint)
            }

            if (stream.progressFraction > 0f) {
                shadowPaint.strokeWidth = (thickness + 3.0f) * d
                shadowPaint.color = Color.argb(135, 4, 6, 10)
                canvas.drawLine(layout.railLeft, lineY + (1.3f * d), progX, lineY + (1.3f * d), shadowPaint)
            }
        }
    }

    // Tracks, Glow, and Progress
    activeStreams.forEachIndexed { index, stream ->
        val col = resolveStreamColor(index, stream, colorMode, m3Primary)
        val thickness = layout.thicknesses[index]
        val lineY = layout.lineYs[index]
        val progressX = layout.progressXs[index]

        if (railTrackOpacityPct > 0) {
            val trackAlpha = (railTrackOpacityPct * 2.55f).toInt().coerceIn(10, 255)
            telemetryGlowPaint.strokeWidth = thickness * d
            telemetryGlowPaint.color = Color.argb(trackAlpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawLine(layout.railLeft, lineY, layout.railRight, lineY, telemetryGlowPaint)
        }

        if (railGlowPct > 0) {
            val glowAlpha = ((railGlowPct * 1.5f) / (1f + index * 0.3f)).toInt().coerceIn(10, 200)
            telemetryGlowPaint.strokeWidth = (thickness + 2.5f - index * 0.5f) * d
            telemetryGlowPaint.color = Color.argb(glowAlpha, Color.red(col), Color.green(col), Color.blue(col))
            canvas.drawLine(layout.railLeft, lineY, progressX, lineY, telemetryGlowPaint)
        }

        telemetryGlowPaint.strokeWidth = thickness * d
        telemetryGlowPaint.color = col
        canvas.drawLine(layout.railLeft, lineY, progressX, lineY, telemetryGlowPaint)
    }

    // Inter-Rail Contact Micro-Grooves
    if (layout.gapDp <= 1f && activeStreams.size > 1) {
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeWidth = (0.75f * d).coerceAtLeast(1f)
            color = Color.argb(165, 4, 6, 10)
        }
        for (i in 0 until activeStreams.size - 1) {
            val topBottom = layout.lineYs[i] + (layout.thicknesses[i] * d / 2f)
            val bottomTop = layout.lineYs[i + 1] - (layout.thicknesses[i + 1] * d / 2f)
            val seamY = (topBottom + bottomTop) / 2f
            val maxActiveX = maxOf(layout.progressXs[i], layout.progressXs[i + 1])
            if (maxActiveX > layout.railLeft) {
                canvas.drawLine(layout.railLeft, seamY, maxActiveX, seamY, seamPaint)
            }
        }
    }

    // Tactical Terminal Progress Head Caps
    if (isTerminalCaps) {
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        activeStreams.forEachIndexed { index, stream ->
            val progressX = layout.progressXs[index]
            val thickness = layout.thicknesses[index]
            val lineY = layout.lineYs[index]

            if (stream.progressFraction in 0.01f..0.99f) {
                val halfCapH = ((thickness * d) / 2f) + (1.2f * d)
                capPaint.strokeWidth = (1.8f * d).coerceAtLeast(2f)
                capPaint.color = Color.argb(220, 6, 8, 14)
                canvas.drawLine(progressX, lineY - halfCapH, progressX, lineY + halfCapH, capPaint)

                capPaint.strokeWidth = (1.0f * d).coerceAtLeast(1f)
                capPaint.color = Color.argb(240, 255, 255, 255)
                canvas.drawLine(progressX - 0.75f * d, lineY - halfCapH * 0.7f, progressX - 0.75f * d, lineY + halfCapH * 0.7f, capPaint)
            }
        }
    }
}
