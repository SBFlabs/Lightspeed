package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import com.sbf.lightspeed.system.LightspeedHudRenderer

private val tempSensorRect = RectF()
private val overlayScreenLoc = IntArray(2)

internal fun LightspeedStatusBarOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
    superCall()
    val d = resources.displayMetrics.density
    val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager
    val activeBounds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && wm != null) {
        wm.currentWindowMetrics.bounds
    } else null
    val metricsW = activeBounds?.width()?.toFloat() ?: resources.displayMetrics.widthPixels.toFloat()
    val metricsH = activeBounds?.height()?.toFloat() ?: resources.displayMetrics.heightPixels.toFloat()
    val screenW = if (width > 0) width.toFloat() else metricsW
    val screenH = metricsH
    val h = height.toFloat()

    getLocationOnScreen(overlayScreenLoc)
    LightspeedHudRenderer.updateOverlayScreenCoordinates(
        screenX = overlayScreenLoc[0],
        screenY = overlayScreenLoc[1],
        displayWidthPx = screenW.toInt(),
        displayHeightPx = screenH.toInt()
    )

    val orientation = resources.configuration.orientation
    val isLandscape = (screenW > screenH) || (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)

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

    if (renderCache.mediaRouting != "none") {
        val activeMedia = com.sbf.lightspeed.system.LightspeedNotificationListener.activeMediaTelemetry
        if (activeMedia?.isPlaying == true) {
            postInvalidateDelayed(1000)
        }
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
    val isReview = isPreview
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
        tempSensorRect.set(sensorLeft, sensorTop, sensorRight, sensorBottom)
        canvas.drawRoundRect(tempSensorRect, 8f * d, 8f * d, debugPaint)

        debugPaint.style = Paint.Style.STROKE
        debugPaint.strokeWidth = 2f * d
        debugPaint.color = Color.WHITE
        tempSensorRect.set(sensorLeft + 1f * d, sensorTop + 1f * d, sensorRight - 1f * d, sensorBottom - 1f * d)
        canvas.drawRoundRect(tempSensorRect, 8f * d, 8f * d, debugPaint)

        hudTextPaint.textSize = 10f * d
        hudTextPaint.color = Color.WHITE
        canvas.drawText("✦ SENSOR AREA (TOP EDGE)", sensorLeft + (spanPx / 2f), sensorTop + (sensorHeight / 2f) + 3.5f * d, hudTextPaint)
    } else if (isSensorEnabled && transparencyPct > 0) {
        val alpha = (transparencyPct * 2.55f).toInt().coerceIn(10, 255)
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb((alpha * 0.4f).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        tempSensorRect.set(sensorLeft, sensorTop, sensorRight, sensorTop + 4f * d)
        canvas.drawRoundRect(tempSensorRect, 2f * d, 2f * d, debugPaint)

        debugPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        tempSensorRect.set(sensorLeft, sensorTop, sensorRight, sensorTop + 2.5f * d)
        canvas.drawRoundRect(tempSensorRect, 1.5f * d, 1.5f * d, debugPaint)
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
    val transientHud = currentTransientHudState
    val isNavActive = navState != null && navState.isActive
    val hasActiveHud = isNavActive || transientHud != null
    if (!hasActiveHud) return

    val activeStyle = if (isNavActive) {
        prefs.getString(
            com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_STYLE_DEFAULT,
            com.sbf.lightspeed.system.LightspeedPreferences.DEFAULT_HUD_STYLE
        )
    } else {
        transientHud?.style
    }
    val isFramelessShade = com.sbf.lightspeed.system.LightspeedHudRenderer.isFramelessShadeStyle(activeStyle)

    // Gate Frame-0 rendering while takeScreenshot() is actively capturing (~15-25ms)
    // for screenshot-blurred card HUDs so they never flash unblurred or capture themselves.
    if (!isFramelessShade &&
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.isHudCapturing &&
        !com.sbf.lightspeed.settings.CentralCommandBackdropBlur.hasHudBackdrop()
    ) {
        return
    }

    val baseAlpha = if (isNavActive && hudAlpha <= 0f) 1f else if (!isFramelessShade && hudAlpha <= 0f) 1f else hudAlpha
    val cProgress = scrubContinuumProgress.coerceIn(0f, 1f)
    val morphScale = 1f + 0.055f * cProgress
    val morphFade = 1f - 0.45f * ((cProgress - 0.65f).coerceAtLeast(0f) / 0.35f)
    val effectiveAlpha = (baseAlpha * morphFade).coerceIn(0f, 1f)
    if (effectiveAlpha <= 0.01f) return

    val topY = (renderCache.thicknessDp * d) + (8f * d)
    val hudCenterY = topY + (72f * d)
    val layerAlphaInt = (effectiveAlpha * 255f).toInt().coerceIn(0, 255)
    val saveCount = if (layerAlphaInt < 254) {
        canvas.saveLayerAlpha(0f, 0f, screenW, height.toFloat().coerceAtLeast(260f * d), layerAlphaInt)
    } else {
        canvas.save()
    }
    if (morphScale > 1.001f) {
        canvas.scale(morphScale, morphScale, screenW / 2f, hudCenterY)
    }

    if (isNavActive && navState != null) {
        val hudColorMode = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudColorMode(prefs, actionToken = "system:gear_nav")
        val useCentralTheme = hudColorMode != com.sbf.lightspeed.system.LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
        val gearHudStyle = prefs.getString(
            com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_STYLE_DEFAULT,
            com.sbf.lightspeed.system.LightspeedPreferences.DEFAULT_HUD_STYLE
        ) ?: "canopy_droppod"
        LightspeedHudRenderer.renderHud(
            canvas = canvas,
            style = gearHudStyle,
            title = "GEAR SET NAV: ${navState.setName}",
            value = navState.currentLabel,
            stepIndex = navState.currentIndex,
            totalSteps = navState.totalCount,
            centerX = screenW / 2f,
            centerY = hudCenterY,
            topY = topY,
            primaryColor = m3Primary,
            density = d,
            useCentralTheme = useCentralTheme,
            colorMode = hudColorMode
        )
    } else if (transientHud != null) {
        val hudColorMode = transientHud.colorMode
            ?: com.sbf.lightspeed.system.LightspeedPreferences.resolveHudColorMode(prefs)
        val useCentralTheme = hudColorMode != com.sbf.lightspeed.system.LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
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
            density = d,
            useCentralTheme = useCentralTheme,
            colorMode = hudColorMode
        )
    }

    val targetDeck = scrubContinuumTargetDeck
    if (cProgress > 0.12f && targetDeck != null) {
        val barW = 148f * d
        val barH = 4f * d
        val barLeft = (screenW - barW) * 0.5f
        val barTop = topY + (108f * d)
        tempSensorRect.set(barLeft, barTop, barLeft + barW, barTop + barH)
        hudFillPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(tempSensorRect, barH * 0.5f, barH * 0.5f, hudFillPaint)

        tempSensorRect.set(barLeft, barTop, barLeft + (barW * cProgress), barTop + barH)
        debugPaint.style = Paint.Style.FILL
        debugPaint.color = Color.argb(235, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(tempSensorRect, barH * 0.5f, barH * 0.5f, debugPaint)

        hudTextPaint.textSize = 9.5f * d
        if (isFramelessShade) {
            hudTextPaint.style = Paint.Style.STROKE
            hudTextPaint.strokeWidth = 2.8f * d
            hudTextPaint.color = Color.argb((230f * cProgress).toInt().coerceIn(80, 235), 0, 0, 0)
            canvas.drawText("PULL :: $targetDeck DECK", screenW * 0.5f, barTop + (16f * d), hudTextPaint)
            hudTextPaint.style = Paint.Style.FILL
        }
        hudTextPaint.color = Color.argb((210f * cProgress).toInt().coerceIn(80, 235), 255, 255, 255)
        canvas.drawText("PULL :: $targetDeck DECK", screenW * 0.5f, barTop + (16f * d), hudTextPaint)
    }
    canvas.restoreToCount(saveCount)
}
