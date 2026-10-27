package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences

internal fun LightspeedCruiseOverlay.drawCruiseCentralPill(canvas: Canvas, m3Primary: Int) {
    val isLeft = isOpenedFromLeftFlank
    val isGlowEnabled = if (isLeft) renderCacheLeftGlowEnabled else renderCacheGlowEnabled
    if (!isGlowEnabled) {
        return
    }

    val d = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()

    val visualWidth = if (isLeft) {
        val geomMode = prefs().getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        val isMirroringRight = geomMode == "right"
        val prefix = if (isMirroringRight) "pref_sidebar_center" else "pref_sidebar_left_center"
        prefs().getInt("${prefix}_visual_width", 6).toFloat() * d
    } else {
        centerVisualWidthPx
    }

    val offsetKey = if (isLeft) {
        val geomMode = prefs().getString("pref_symmetry_geometry_mode", "independent") ?: "independent"
        if (geomMode == "right") "pref_sidebar_center_x_offset" else "pref_sidebar_left_center_x_offset"
    } else "pref_sidebar_center_x_offset"
    val edgeShiftPx = prefs().getInt(offsetKey, 0).toFloat() * d
    val glowDx = if (isLeft) edgeShiftPx else -edgeShiftPx

    canvas.save()
    canvas.translate(glowDx, 0f)
    com.sbf.lightspeed.system.LightspeedDeflectorRenderer.drawDeflectorWing(
        canvas = canvas,
        isLeft = isLeft,
        density = d,
        w = w,
        h = h,
        topTouchBounds = topTouchBounds,
        centerTouchBounds = centerTouchBounds,
        bottomTouchBounds = bottomTouchBounds,
        isCurrentlyTouched = isCurrentlyTouched,
        activeZoneIsCenter = true,
        activeZoneIsTop = false,
        activeZoneIsBottom = false,
        glowFraction = glowFraction,
        centerTransparency = if (isLeft) renderCacheLeftCenterTransparency else renderCacheCenterTransparency,
        topTransparency = 0,
        bottomTransparency = 0,
        isReview = false,
        m3Primary = m3Primary,
        glowStyle = renderCacheGlowStyle,
        isGlowEnabled = true,
        useM3Color = renderCacheUseM3Color,
        pillStyle = LightspeedPreferences.getDeflectorPillStyle(context, isLeft),
        touchY = null,
        visualWidthPx = visualWidth
    )
    canvas.restore()
}

internal fun LightspeedCruiseOverlay.drawSpaceshipGimbalRing(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    trackRadius: Float,
    trackWidth: Float,
    teethCount: Int,
    toothDepth: Float,
    rotationDeg: Float,
    isActive: Boolean,
    m3Primary: Int,
    gearShape: String = LightspeedPreferences.GEAR_SHAPE_CIRCLE,
    segmentCount: Int = (teethCount / 2).coerceAtLeast(6)
) = deepSpaceRenderer.drawSpaceshipGimbalRing(canvas, cx, cy, trackRadius, trackWidth, teethCount, toothDepth, rotationDeg, isActive, m3Primary, gearShape, segmentCount)

internal fun LightspeedCruiseOverlay.drawFlightLockReticle(
    canvas: Canvas,
    targetCX: Float,
    targetCY: Float,
    bracketSize: Float,
    m3Primary: Int,
    m3Secondary: Int,
    appName: String,
    density: Float,
    reticleStyle: String = "tactical"
) = deepSpaceRenderer.drawFlightLockReticle(canvas, targetCX, targetCY, bracketSize, m3Primary, m3Secondary, appName, density, reticleStyle)

internal fun LightspeedCruiseOverlay.drawHolographicReactorCore(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    coreRadius: Float,
    isActive: Boolean,
    m3Primary: Int,
    density: Float
) = deepSpaceRenderer.drawHolographicReactorCore(canvas, cx, cy, coreRadius, isActive, m3Primary, density)

internal fun LightspeedCruiseOverlay.drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) =
    deepSpaceRenderer.drawCosmicStarfield(canvas, w, h, density, alphaFactor)

internal fun LightspeedCruiseOverlay.drawGalacticNebula(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    m3Primary: Int,
    alphaFactor: Float
) = deepSpaceRenderer.drawGalacticNebula(canvas, cx, cy, radius, m3Primary, alphaFactor)

internal fun LightspeedCruiseOverlay.drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) =
    deepSpaceRenderer.drawHyperdriveWarpSurge(canvas, m3Primary, density)

internal fun LightspeedCruiseOverlay.drawCruiseGridLayer(canvas: Canvas, w: Float, h: Float) {
    val density = resources.displayMetrics.density
    val snapshot = DeckThemeManager.getSnapshot(context)
    val themePanel = snapshot.panelColorArgb.toInt()
    val themeBorder = snapshot.borderColorArgb.toInt()

    val bgAlpha = (snapshot.panelAlpha * 210f).toInt().coerceIn(160, 230)
    canvas.drawColor(Color.argb(bgAlpha, Color.red(themePanel), Color.green(themePanel), Color.blue(themePanel)))
    drawCosmicStarfield(canvas, w, h, density, 0.7f)

    textPaint.textAlign = Paint.Align.CENTER
    canvas.save(); canvas.clipRect(0f, 0f, w, h)
    canvas.translate(0f, -(if (totalGridContentHeight <= (h * 0.79f)) 0f else viewportScrollOffset))
    for (item in placedAppsList) {
        val b = item.bounds; val isSel = activeItem == item.app
        if (isSel) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(160, Color.red(themePanel), Color.green(themePanel), Color.blue(themePanel))
            canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 2.2f * density
            highlightPaint.color = themeBorder
            canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)

            textPaint.color = Color.WHITE
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        } else {
            elementPaint.style = Paint.Style.FILL
            val tileAlpha = if (item.app.isWidget) 45 else 25
            elementPaint.color = Color.argb(tileAlpha, Color.red(themePanel), Color.green(themePanel), Color.blue(themePanel))
            canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)

            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = 1f * density
            elementPaint.color = Color.argb(40, Color.red(themeBorder), Color.green(themeBorder), Color.blue(themeBorder))
            canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)

            textPaint.color = Color.parseColor("#E0E5F0")
            textPaint.typeface = android.graphics.Typeface.DEFAULT
        }
        if (item.app.isWidget) {
            canvas.drawText(if (item.app.label.length > 16) item.app.label.take(14) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
        } else {
            val icon = applicationIconCache[item.app.packageName]
            if (icon != null) {
                val sz = minOf(b.width() * 0.48f, b.height() * 0.48f)
                icon.setBounds((b.centerX() - sz / 2f).toInt(), (b.centerY() - sz * 0.62f).toInt(), (b.centerX() + sz / 2f).toInt(), (b.centerY() - sz * 0.62f + sz).toInt())
                icon.draw(canvas)
                canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
            } else {
                val sz = minOf(b.width() * 0.44f, b.height() * 0.44f)
                val iconY = b.centerY() - sz * 0.62f + sz / 2f
                elementPaint.style = Paint.Style.FILL
                elementPaint.color = Color.argb(22, 200, 220, 255)
                canvas.drawCircle(b.centerX(), iconY, sz * 0.44f, elementPaint)
                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = 1f * density
                elementPaint.color = Color.argb(45, 200, 220, 255)
                canvas.drawCircle(b.centerX(), iconY, sz * 0.44f, elementPaint)

                val initial = item.app.label.firstOrNull()?.uppercase() ?: "?"
                textPaint.textSize = sz * 0.46f
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                canvas.drawText(initial, b.centerX(), iconY + (sz * 0.16f), textPaint)

                textPaint.textSize = 10f * density
                textPaint.typeface = android.graphics.Typeface.DEFAULT
                canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
            }
        }
    }
    canvas.restore()
    if (isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) drawSettingsButton(canvas, elementPaint)
    if (isPinnedAppMenuOpen()) drawPinnedAppMenu(canvas, elementPaint)
    if (isEditModeOpen) drawEditModePanel(canvas, elementPaint)
}

