package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    val focusAccent = categoryAccentOverride() ?: (if (themeBorder != 0) themeBorder else themePanel)

    val bgAlpha = (snapshot.panelAlpha * 210f).toInt().coerceIn(160, 230)
    canvas.drawColor(Color.argb(bgAlpha, Color.red(themePanel), Color.green(themePanel), Color.blue(themePanel)))
    drawCosmicStarfield(canvas, w, h, density, 0.7f)

    // Ambient radial spotlight centered on currently focused app icon's (X, Y) coordinate
    val catId = cachedCategories.getOrNull(activeCatIndex)?.id ?: "comm"
    val gTop = getCategoryGridTop(catId)
    val gBot = h * 0.98f
    val gScope = gBot - gTop
    val scroll = if (totalGridContentHeight <= gScope) 0f else viewportScrollOffset

    val focusedPlaced = placedAppsList.find { it.app == activeItem }
    if (focusedPlaced != null) {
        val spotX = focusedPlaced.bounds.centerX()
        val spotY = focusedPlaced.bounds.centerY() - scroll
        drawGalacticNebula(canvas, spotX, spotY, 150f * density, focusAccent, 0.75f)
    }

    val hasInspector = activeCategoryHudItems.any { it.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT }
    val hideGridLabels = hasInspector && currentLayer == CruiseLayer.GRID && !isStickyPinned

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 11f * density
    val gridLayerCount = canvas.saveLayer(0f, gTop, w, gBot, null)
    canvas.clipRect(0f, gTop, w, gBot)
    canvas.translate(0f, -scroll)
    for (item in placedAppsList) {
        val b = item.bounds; val isSel = activeItem == item.app
        if (isSel) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(160, Color.red(themePanel), Color.green(themePanel), Color.blue(themePanel))
            canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)

            // Crisp bright accent ring/glow around currently focused app icon
            val accentColor = focusAccent
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 3.5f * density
            highlightPaint.color = accentColor
            canvas.drawRoundRect(b.left + 3f, b.top + 3f, b.right - 3f, b.bottom - 3f, 18f, 18f, highlightPaint)

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
            if (!hideGridLabels) {
                textPaint.textSize = 11f * density
                canvas.drawText(if (item.app.label.length > 16) item.app.label.take(14) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
            }
        } else {
            val icon = applicationIconCache[item.app.packageName]
            if (icon != null) {
                val sz = minOf(b.width() * 0.48f, b.height() * 0.48f)
                val iconTopY = if (hideGridLabels) (b.centerY() - sz * 0.5f).toInt() else (b.centerY() - sz * 0.62f).toInt()
                icon.setBounds((b.centerX() - sz / 2f).toInt(), iconTopY, (b.centerX() + sz / 2f).toInt(), iconTopY + sz.toInt())
                icon.draw(canvas)
                if (!hideGridLabels) {
                    textPaint.textSize = 11f * density
                    canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
                }
            } else {
                val sz = minOf(b.width() * 0.44f, b.height() * 0.44f)
                val iconY = if (hideGridLabels) b.centerY() else (b.centerY() - sz * 0.62f + sz / 2f)
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

                if (!hideGridLabels) {
                    textPaint.textSize = 11f * density
                    textPaint.typeface = android.graphics.Typeface.DEFAULT
                    canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
                }
            }
        }
    }
    canvas.translate(0f, scroll)
    val gridMaxScroll = kotlin.math.max(0f, totalGridContentHeight - (gBot - gTop))
    LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
        canvas = canvas,
        left = 0f,
        top = gTop,
        right = w,
        bottom = gBot,
        featherTopPx = if (scroll > 0.5f) 18f * density else 0f,
        featherBottomPx = if (scroll < gridMaxScroll - 0.5f) 22f * density else 0f,
        density = density,
        seed = 4242
    )
    canvas.restoreToCount(gridLayerCount)

    drawCategoryTopHudStrip(canvas, w, density, 1f)
}

/**
 * Computes the screen-space bounds of the Pinned-view Edge Category Switcher strip.
 * Anchored independently of per-category HUD widgets and docked flush to the screen edge and bottom.
 */
internal fun LightspeedCruiseOverlay.computePinnedEdgeSwitcherRect(outRect: RectF): Boolean {
    if ((!isStickyPinned && currentLayer != CruiseLayer.STICKY_PIN) || isEditModeOpen || isHudEditMode || cachedCategories.isEmpty()) {
        outRect.setEmpty()
        return false
    }
    val density = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()
    if (w <= 0f || h <= 0f) {
        outRect.setEmpty()
        return false
    }

    val stripW = 30f * density
    val stripLeft = if (isOpenedFromLeftFlank) 0f else (w - stripW)
    val safeMinTop = getSafeHudTopOffset() + 8f * density
    val fixedTop = if (isFarHalfSplitMode() || w > h) {
        safeMinTop
    } else {
        (h * 0.42f).coerceAtLeast(safeMinTop)
    }
    val stripBot = h
    val stripTop = fixedTop.coerceAtMost(stripBot - 72f * density).coerceAtLeast(safeMinTop)
    outRect.set(stripLeft, stripTop, stripLeft + stripW, stripBot)
    return !outRect.isEmpty
}

/**
 * Returns true if (x, y) falls within the expanded touch target of the Pinned-view Edge Category Switcher.
 */
internal fun LightspeedCruiseOverlay.isTouchOnPinnedEdgeSwitcher(x: Float, y: Float): Boolean {
    if (pinnedEdgeSwitcherRect.isEmpty && !computePinnedEdgeSwitcherRect(pinnedEdgeSwitcherRect)) {
        return false
    }
    val dp = resources.displayMetrics.density
    val hitLeft = (pinnedEdgeSwitcherRect.left - 10f * dp).coerceAtLeast(0f)
    val hitTop = (pinnedEdgeSwitcherRect.top - 6f * dp).coerceAtLeast(0f)
    val hitRight = (pinnedEdgeSwitcherRect.right + 10f * dp).coerceAtMost(width.toFloat())
    val hitBottom = pinnedEdgeSwitcherRect.bottom
    return x in hitLeft..hitRight && y in hitTop..hitBottom
}

/**
 * Draws the Pinned-view Edge Category Switcher strip flush against the triggering edge
 * and bottom of the screen during [LightspeedCruiseOverlay.dispatchDraw].
 */
internal fun LightspeedCruiseOverlay.drawPinnedEdgeSwitcher(canvas: Canvas) {
    if (!computePinnedEdgeSwitcherRect(pinnedEdgeSwitcherRect)) {
        return
    }
    val density = resources.displayMetrics.density
    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val focusAccent = ensureHighContrastColor(tintColor)

    val stripW = pinnedEdgeSwitcherRect.width()
    val stripTop = pinnedEdgeSwitcherRect.top
    val stripBot = pinnedEdgeSwitcherRect.bottom
    val innerCornerR = stripW * 0.5f
    val radii = if (isOpenedFromLeftFlank) {
        floatArrayOf(0f, 0f, innerCornerR, innerCornerR, 0f, 0f, 0f, 0f)
    } else {
        floatArrayOf(innerCornerR, innerCornerR, 0f, 0f, 0f, 0f, 0f, 0f)
    }
    val dockPath = Path().apply {
        addRoundRect(pinnedEdgeSwitcherRect, radii, Path.Direction.CW)
    }

    elementPaint.style = Paint.Style.FILL
    elementPaint.color = Color.argb(185, 10, 14, 24)
    canvas.drawPath(dockPath, elementPaint)
    elementPaint.style = Paint.Style.STROKE
    elementPaint.strokeWidth = 1.1f * density
    elementPaint.color = Color.argb(145, Color.red(focusAccent), Color.green(focusAccent), Color.blue(focusAccent))
    canvas.drawPath(dockPath, elementPaint)

    val count = cachedCategories.size
    val innerTop = stripTop + 6f * density
    val innerBot = (stripBot - 10f * density).coerceAtLeast(innerTop + count * 12f * density)
    val slotH = (innerBot - innerTop) / count
    val iconR = (slotH * 0.30f).coerceIn(5.0f * density, 8.0f * density)
    for (i in 0 until count) {
        val cat = cachedCategories[i]
        val cy = innerTop + slotH * (i + 0.5f)
        val cx = pinnedEdgeSwitcherRect.centerX()
        val isSel = (i == activeCatIndex)
        if (isSel) {
            elementPaint.style = Paint.Style.FILL
            elementPaint.color = Color.argb(230, Color.red(focusAccent), Color.green(focusAccent), Color.blue(focusAccent))
            canvas.drawCircle(cx, cy, (iconR + 3.5f * density), elementPaint)
        }
        LightspeedHudVectorIcons.drawCategoryVectorIcon(
            canvas = canvas,
            categoryId = cat.id,
            cx = cx,
            cy = cy,
            r = iconR,
            color = if (isSel) Color.BLACK else Color.WHITE,
            alpha = if (isSel) 255 else 215,
            strokeWidth = (1.4f * density)
        )
    }
}

/**
 * Draws all cruise popups, context menus, info bubbles, and modals on top of child Views
 * (such as [android.appwidget.AppWidgetHostView]) during [LightspeedCruiseOverlay.dispatchDraw].
 */
internal fun LightspeedCruiseOverlay.drawCruiseModalsOverlay(canvas: Canvas) {
    val density = resources.displayMetrics.density
    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val focusAccent = ensureHighContrastColor(tintColor)

    if (isPinnedAppMenuOpen()) drawPinnedAppMenu(canvas, elementPaint)
    if (isEditModeOpen) drawEditModePanel(canvas, elementPaint)
    if (isCountdownModalOpen) drawCountdownModal(canvas, elementPaint)
    if (isScreenTimeInfoBubbleOpen) drawScreenTimeInfoBubble(canvas)
    if (isPrivacyScoreInfoBubbleOpen) drawPrivacyScoreInfoBubble(canvas)
    if (isAlertsHistoryModalOpen) drawAlertsHistoryModal(canvas, density, focusAccent)
    if (isFirewallModalOpen) drawFirewallManagerModal(canvas, density, focusAccent)
    if (isVitalsModalOpen) drawVitalsTaskManagerModal(canvas, density, focusAccent)
}

