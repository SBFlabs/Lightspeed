package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import com.sbf.lightspeed.system.LightspeedHudRenderer

internal fun LightspeedStatusBarOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
    superCall()
    val d = resources.displayMetrics.density
    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val h = height.toFloat()

    val orientation = resources.configuration.orientation
    val isLandscape = orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.resources.getColor(android.R.color.system_accent1_300, context.theme)
    } else {
        Color.parseColor("#90CAF9")
    }

    // 1. Sensor Debug Area & Transparency Guard
    drawSensorDebugArea(canvas, d, screenW, m3Primary)

    // 2. Horizon Rail Telemetry Line & Micro-Text Renderer
    val streams = collectHorizonStreams(isLandscape)
    if (streams.isNotEmpty()) {
        val layout = computeRailLayout(streams, d, screenW, isLandscape, m3Primary)
        drawHorizonRail(canvas, layout, d, m3Primary)
        drawTelemetryTicker(canvas, layout, d, screenW, h, isLandscape)
    }

    val activeMedia = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry
    if (activeMedia?.isPlaying == true) {
        postInvalidateDelayed(1000)
    }

    // 3. Hardware Gear Set HUD Navigation & Transient Action HUD Renderer
    drawScrubHud(canvas, d, screenW, m3Primary)
}

// -------------------------------------------------------------------------------------------------
// 1. Sensor Area Sub-Renderer
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawSensorDebugArea(
    canvas: Canvas,
    d: Float,
    screenW: Float,
    m3Primary: Int
) {
    val isSensorEnabled = renderCache.isSensorEnabled
    val isExpanded = renderCache.isExpanded
    val isPreview  = renderCache.isPreview
    val isReview = isSensorEnabled && isPreview
    val transparencyPct = renderCache.transparencyPct

    val spanPref = renderCache.spanPref
    val spanPx = if (spanPref >= 1000) screenW else (spanPref * d).coerceIn(50f * d, screenW)
    val sensorOffsetX = renderCache.sensorOffsetX * d
    val thicknessDp = renderCache.thicknessDp
    val sensorHeight = thicknessDp * d
    val sensorOffsetY = (renderCache.sensorOffsetY * d).coerceAtLeast(0f)

    val sensorLeft = ((screenW - spanPx) / 2f) + sensorOffsetX
    val sensorRight = sensorLeft + spanPx
    val sensorTop = sensorOffsetY
    val sensorBottom = sensorTop + sensorHeight

    if (isReview) {
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorBottom), 8f * d, 8f * d, debugPaint)

        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 2f * d
        debugPaint.color = Color.WHITE
        canvas.drawRoundRect(RectF(sensorLeft + 1f * d, sensorTop + 1f * d, sensorRight - 1f * d, sensorBottom - 1f * d), 8f * d, 8f * d, debugPaint)

        hudTextPaint.textSize = 10f * d
        hudTextPaint.color = Color.WHITE
        canvas.drawText("✦ SENSOR AREA (TOP EDGE)", sensorLeft + (spanPx / 2f), sensorTop + (sensorHeight / 2f) + 3.5f * d, hudTextPaint)
    } else if (transparencyPct > 0) {
        val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 4f * d), 2f * d, 2f * d, debugPaint)

        debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(RectF(sensorLeft, sensorTop, sensorRight, sensorTop + 2.5f * d), 1.5f * d, 1.5f * d, debugPaint)
    }
}

// -------------------------------------------------------------------------------------------------
// 4. Scrub / Navigation HUD Sub-Renderer
// -------------------------------------------------------------------------------------------------
internal fun LightspeedStatusBarOverlay.drawScrubHud(
    canvas: Canvas,
    d: Float,
    screenW: Float,
    m3Primary: Int
) {
    val navState = com.sbf.lightspeed.system.LightspeedKeyEngine.currentNavState
    if (navState != null && navState.isActive) {
        val topY = (renderCache.thicknessDp * d) + (8f * d)
        val hudCenterY = topY + (72f * d)
        LightspeedHudRenderer.renderHud(
            canvas = canvas,
            style = "canopy_droppod",
            title = "GEAR SET NAV: ${navState.setName}",
            value = navState.currentLabel,
            stepIndex = navState.currentIndex,
            totalSteps = navState.totalCount,
            centerX = screenW / 2f,
            centerY = hudCenterY,
            topY = topY,
            primaryColor = m3Primary,
            density = d
        )
    } else {
        val transientHud = currentTransientHudState
        if (transientHud != null) {
            val topY = (renderCache.thicknessDp * d) + (8f * d)
            val hudCenterY = topY + (72f * d)
            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = transientHud.style,
                title = transientHud.title,
                value = transientHud.value,
                stepIndex = transientHud.stepIndex,
                totalSteps = transientHud.totalSteps,
                centerX = screenW / 2f,
                centerY = hudCenterY,
                topY = topY,
                primaryColor = m3Primary,
                density = d
            )
        }
    }
}
