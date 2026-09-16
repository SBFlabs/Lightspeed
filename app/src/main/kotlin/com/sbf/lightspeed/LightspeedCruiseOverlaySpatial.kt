package com.sbf.lightspeed

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import android.graphics.RenderEffect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.hypot

internal fun LightspeedCruiseOverlay.updateRenderCache() {
    val p = prefs()
    renderCacheRightFlankUnified    = p.getBoolean("pref_sidebar_right_link_flank_actions", false)
    renderCacheRightUnifiedExpanded = p.getBoolean("pref_section_right_unified_expanded", false)
    renderCacheTopExpanded          = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_top_expanded", false)
    renderCacheCenterExpanded       = p.getBoolean("pref_section_center_expanded", false)
    renderCacheBottomExpanded       = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_bottom_expanded", false)
    renderCacheSidebarPreview       = p.getBoolean("pref_sidebar_preview", false)
    renderCacheLinkEdges            = p.getBoolean("pref_sidebar_link_edges", false)
    renderCacheCenterTransparency   = p.getInt("pref_sidebar_center_transparency", 0)
    renderCacheLeftCenterTransparency = p.getInt("pref_sidebar_left_center_transparency", 0)
    renderCacheTopTransparency      = p.getInt("pref_sidebar_top_transparency", 0)
    renderCacheBottomTransparency   = if (renderCacheLinkEdges) renderCacheTopTransparency else p.getInt("pref_sidebar_bottom_transparency", 0)
    renderCacheGlowStyle            = p.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"
    renderCacheReticleStyle         = p.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
    renderCacheGlowEnabled          = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))
    renderCacheLeftGlowEnabled      = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))
    renderCacheUseM3Color           = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, true)
}

fun LightspeedCruiseOverlay.updateMetricsDimensions() {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val lp = layoutParams as? WindowManager.LayoutParams ?: return

    val displayMetrics = resources.displayMetrics
    val screenW = displayMetrics.widthPixels.toFloat()
    val screenH = displayMetrics.heightPixels.toFloat()
    val density = displayMetrics.density

    val prefs = prefs()
    
    centerHeightPx = prefs.getInt("pref_sidebar_center_height", 280).toFloat() * density
    centerYOffsetPx = prefs.getInt("pref_sidebar_center_y_offset", 0).toFloat() * density
    centerVisualWidthPx = prefs.getInt("pref_sidebar_center_visual_width", 4).toFloat() * density
    centerTouchWidthPx = prefs.getInt("pref_sidebar_center_touch_width", 24).toFloat() * density

    linkEdges = prefs.getBoolean("pref_sidebar_link_edges", false)

    topHeightPx = prefs.getInt("pref_sidebar_top_height", 300).toFloat() * density
    topVisualWidthPx = prefs.getInt("pref_sidebar_top_visual_width", 2).toFloat() * density
    topTouchWidthPx = prefs.getInt("pref_sidebar_top_touch_width", 32).toFloat() * density

    bottomHeightPx = if (linkEdges) topHeightPx else prefs.getInt("pref_sidebar_bottom_height", 300).toFloat() * density
    bottomVisualWidthPx = if (linkEdges) topVisualWidthPx else prefs.getInt("pref_sidebar_bottom_visual_width", 2).toFloat() * density
    bottomTouchWidthPx = if (linkEdges) topTouchWidthPx else prefs.getInt("pref_sidebar_bottom_touch_width", 32).toFloat() * density

    if (currentLayer == CruiseLayer.HIDDEN) {
        val centerY = (screenH / 2f) + centerYOffsetPx
        val centerTop = centerY - (centerHeightPx / 2f)
        val centerBottom = centerY + (centerHeightPx / 2f)

        val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
        val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

        val maxTouchW = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx)
        val winHeight = (bottomLimit - topLimit).toInt().coerceAtLeast(100)
        val winWidth = maxTouchW.toInt().coerceAtLeast((10f * density).toInt())
        val winY = topLimit.toInt()
        val w = winWidth.toFloat()

        topTouchBounds.set(w - topTouchWidthPx, 0f, w, topHeightPx)
        centerTouchBounds.set(w - centerTouchWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
        bottomTouchBounds.set(w - bottomTouchWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

        topVisualBounds.set(w - topVisualWidthPx, 0f, w, topHeightPx)
        centerVisualBounds.set(w - centerVisualWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
        bottomVisualBounds.set(w - bottomVisualWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

        launchpadPillBounds.set(centerVisualBounds)

        if (lp.height != winHeight || lp.width != winWidth || lp.y != winY || lp.gravity != (Gravity.TOP or Gravity.END)) {
            lp.gravity = Gravity.TOP or Gravity.END
            lp.x = 0; lp.y = winY
            lp.width = winWidth; lp.height = winHeight
            wm.updateViewLayout(this, lp)
        }
    } else {
        val winWidth = WindowManager.LayoutParams.MATCH_PARENT
        val winHeight = WindowManager.LayoutParams.MATCH_PARENT
        val w = screenW; val h = screenH

        val centerY = (h / 2f) + centerYOffsetPx
        val centerTop = centerY - (centerHeightPx / 2f)
        val centerBottom = centerY + (centerHeightPx / 2f)

        val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
        val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(h)

        if (isOpenedFromLeftFlank) {
            topTouchBounds.set(0f, topLimit, topTouchWidthPx, centerTop)
            centerTouchBounds.set(0f, centerTop, centerTouchWidthPx, centerBottom)
            bottomTouchBounds.set(0f, centerBottom, bottomTouchWidthPx, bottomLimit)

            topVisualBounds.set(0f, topLimit, topVisualWidthPx, centerTop)
            centerVisualBounds.set(0f, centerTop, centerVisualWidthPx, centerBottom)
            bottomVisualBounds.set(0f, centerBottom, bottomVisualWidthPx, bottomLimit)
        } else {
            topTouchBounds.set(w - topTouchWidthPx, topLimit, w, centerTop)
            centerTouchBounds.set(w - centerTouchWidthPx, centerTop, w, centerBottom)
            bottomTouchBounds.set(w - bottomTouchWidthPx, centerBottom, w, bottomLimit)

            topVisualBounds.set(w - topVisualWidthPx, topLimit, w, centerTop)
            centerVisualBounds.set(w - centerVisualWidthPx, centerTop, w, centerBottom)
            bottomVisualBounds.set(w - bottomVisualWidthPx, centerBottom, w, bottomLimit)
        }

        launchpadPillBounds.set(centerVisualBounds)

        if (lp.height != winHeight || lp.width != winWidth || lp.y != 0) {
            lp.gravity = Gravity.FILL
            lp.x = 0; lp.y = 0
            lp.width = winWidth; lp.height = winHeight
            wm.updateViewLayout(this, lp)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            updatePillShaderUniforms(active = false)
            cachedRenderEffect = RenderEffect.createRuntimeShaderEffect(LightspeedCruiseOverlay.progressiveShader, "inputTexture")
        } catch (e: Exception) {
            Log.e("LightspeedBlur", "AGSL fault", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                cachedRenderEffect = RenderEffect.createBlurEffect(25f, 25f, android.graphics.Shader.TileMode.CLAMP)
            }
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cachedRenderEffect = RenderEffect.createBlurEffect(25f, 25f, android.graphics.Shader.TileMode.CLAMP)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val systemExclusions = mutableListOf<android.graphics.Rect>()
        systemExclusions.add(android.graphics.Rect(topTouchBounds.left.toInt(), topTouchBounds.top.toInt(), topTouchBounds.right.toInt(), topTouchBounds.bottom.toInt()))
        systemExclusions.add(android.graphics.Rect(centerTouchBounds.left.toInt(), centerTouchBounds.top.toInt(), centerTouchBounds.right.toInt(), centerTouchBounds.bottom.toInt()))
        systemExclusions.add(android.graphics.Rect(bottomTouchBounds.left.toInt(), bottomTouchBounds.top.toInt(), bottomTouchBounds.right.toInt(), bottomTouchBounds.bottom.toInt()))
        this.systemGestureExclusionRects = systemExclusions
    }

    refreshActiveRenderEffect()
    invalidate()
}

internal fun LightspeedCruiseOverlay.refreshActiveRenderEffect() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val isPillEnabled = if (isOpenedFromLeftFlank) renderCacheLeftGlowEnabled else renderCacheGlowEnabled
        val isExpanded = (currentLayer != CruiseLayer.HIDDEN && currentLayer != CruiseLayer.NEUTRAL)
        val shouldApplyBlur = isExpanded && (isPillEnabled || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)
        setRenderEffect(if (shouldApplyBlur) cachedRenderEffect else null)
    }
}

internal fun LightspeedCruiseOverlay.updatePillShaderUniforms(
    left: Float = 0f,
    top: Float = 0f,
    right: Float = 0f,
    bottom: Float = 0f,
    cornerR: Float = 0f,
    isLeft: Boolean = false,
    active: Boolean = false
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        try {
            val screenW = width.toFloat().coerceAtLeast(1f)
            val screenH = height.toFloat().coerceAtLeast(1f)
            val density = resources.displayMetrics.density
            LightspeedCruiseOverlay.progressiveShader.apply {
                setFloatUniform("viewSize", screenW, screenH)
                setFloatUniform("topBlurHeight", 130.0f * density)
                setFloatUniform("bottomBlurHeight", 160.0f * density)
                setFloatUniform("headerBlurActive", if (currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) 1f else 0f)
            }
        } catch (e: Exception) {
            Log.e("LightspeedBlur", "Failed to update AGSL uniforms", e)
        }
    }
}

internal fun LightspeedCruiseOverlay.evaluateSpatialMetrics(rawX: Float, rawY: Float, localX: Float, localY: Float) {
    val wF = width.toFloat(); val hF = height.toFloat()
    if (wF <= 0f || hF <= 0f) return
    val density = resources.displayMetrics.density
    depthPercentage = if (isOpenedFromLeftFlank) (localX / wF).coerceIn(0f, 1f) else ((wF - localX) / wF).coerceIn(0f, 1f)
    val verticalComfortScope = hF * 0.40f
    val verticalStartAnchor = hF * 0.30f
    val normalizedVerticalProgress = ((localY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

    if (currentLayer == CruiseLayer.CATEGORY) {
        activeItem = null; viewportScrollOffset = 0f
        val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
        if (categoryScrubbingEngaged && horizontalPull > (28f * density)) {
            currentLayer = CruiseLayer.GRID; loadActiveCategoryGrid(); invalidate(); return
        }
        if (cachedCategories.isNotEmpty()) {
            val startYArea = hF * 0.22f; val endYArea = hF * 0.78f
            val catLineH = (endYArea - startYArea) / cachedCategories.size

            val isAtBottom = (activeCatIndex == cachedCategories.size - 1)
            val isDraggingDown = (rawY > lastTouchRawY)

            if (isAtBottom && isDraggingDown) {
                val stepDistance = abs(rawY - lastTouchRawY)
                if (stepDistance > 2f) {
                    overScrollBoundaryAccumulator += stepDistance * 0.45f
                }
                if (overScrollBoundaryAccumulator > 120f && !settingsCategoryAppended) {
                    settingsCategoryAppended = true
                    triggerHardwareHaptic(35, 160)
                    val mutableCats = cachedCategories.toMutableList()
                    val hasSettings = mutableCats.any { it.id == "launcher_settings_virtual_id" }
                    if (!hasSettings) {
                        mutableCats.add(LightspeedDataBridge.CategoryNode("launcher_settings_virtual_id", "⚙ Settings"))
                        cachedCategories = mutableCats
                    }
                }
            } else if (normalizedVerticalProgress < 1.0f) {
                overScrollBoundaryAccumulator = (overScrollBoundaryAccumulator - 12f).coerceAtLeast(0f)
            }

            val resistanceFactor = if (overScrollBoundaryAccumulator > 0f) (1.0f / (1.0f + (overScrollBoundaryAccumulator * 0.008f))) else 1.0f
            val adjustedProgress = if (isAtBottom && isDraggingDown) {
                normalizedVerticalProgress + (overScrollBoundaryAccumulator * 0.0005f * resistanceFactor)
            } else {
                normalizedVerticalProgress
            }

            categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (adjustedProgress * ((cachedCategories.size - 1) * catLineH)))

            var closestIndex = activeCatIndex; var minDistance = Float.MAX_VALUE
            for (index in cachedCategories.indices) {
                val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                if (d < minDistance) { minDistance = d; closestIndex = index }
            }
            if (closestIndex != activeCatIndex) activeCatIndex = closestIndex
        }
    } else {
        val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
        // In GRID mode, allow navigating all the way to the rightmost column without prematurely kicking back to CATEGORY.
        // Only exit back to CATEGORY if the thumb slides all the way back to the physical screen edge (< 6dp pull).
        val nextLayerState = when {
            horizontalPull <= (6f * density) -> CruiseLayer.CATEGORY
            (if (isOpenedFromLeftFlank) localX else (wF - localX)) <= (wF * 0.42f) -> CruiseLayer.GRID
            else -> CruiseLayer.STICKY_PIN
        }
        if (currentLayer == CruiseLayer.GRID && nextLayerState == CruiseLayer.CATEGORY) {
            currentLayer = CruiseLayer.CATEGORY
            touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
            placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
            if (cachedCategories.isNotEmpty()) {
                val startYArea = hF * 0.22f; val endYArea = hF * 0.78f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                val maxScroll = totalGridContentHeight - (hF * 0.79f)
                val ratio = if (maxScroll > 0f) (viewportScrollOffset / maxScroll).coerceIn(0f, 1f) else normalizedVerticalProgress
                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (ratio * ((cachedCategories.size - 1) * catLineH)))
                var closestIndex = 0; var minDistance = Float.MAX_VALUE
                for (index in cachedCategories.indices) {
                    val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                    if (d < minDistance) { minDistance = d; closestIndex = index }
                }
                activeCatIndex = closestIndex
            }
            invalidate(); return
        }
        currentLayer = nextLayerState
        if (currentLayer == CruiseLayer.CATEGORY) {
            touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
            placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
            invalidate(); return
        }

        // Map cursor progress so that the outermost column is reached cleanly with zero edge collision
        val pullDistance = if (isOpenedFromLeftFlank) localX.coerceAtLeast(0f) else (wF - localX).coerceAtLeast(0f)
        val cursorProgress = ((pullDistance - (10f * density)) / (wF * 0.28f)).coerceIn(0f, 1f)
        virtualCursorX = if (isOpenedFromLeftFlank) ((wF * 0.06f) + (cursorProgress * (wF * 0.88f))) else ((wF * 0.94f) - (cursorProgress * (wF * 0.88f)))

        val gTop = hF * 0.16f; val gBot = hF * 0.94f; val gScope = gBot - gTop
        virtualCursorY = gTop + (normalizedVerticalProgress * gScope)
        if (totalGridContentHeight <= gScope) { viewportScrollOffset = 0f } 
        else { viewportScrollOffset = normalizedVerticalProgress * (totalGridContentHeight - gScope) }

        var closestMatchItem: LightspeedDataBridge.LaunchTarget? = null; var minD = Float.MAX_VALUE
        for (item in placedAppsList) {
            val d = hypot((virtualCursorX - item.bounds.centerX()).toDouble(), ((virtualCursorY + viewportScrollOffset) - item.bounds.centerY()).toDouble()).toFloat()
            if (d < minD) { minD = d; closestMatchItem = item.app }
        }
        activeItem = if (closestMatchItem != null && placedAppsList.isNotEmpty() && currentLayer != CruiseLayer.STICKY_PIN) closestMatchItem else null
    }
    invalidate()
}

internal fun LightspeedCruiseOverlay.buildPackedGridLayout() {
    val wF = width.toFloat(); val hF = height.toFloat()
    if (wF <= 0f) return
    val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
    val cellW = (wF * 0.92f) / columns; val cellH = cellW * 1.1f
    placedAppsList.clear()
    val occupied = HashSet<Pair<Int, Int>>()
    for (app in cachedApps.distinctBy { "${it.packageName}_${it.activityName}_${it.instanceId}" }) {
        var sW = 1; var sH = 1
        if (app.isWidget) { sW = if (app.spanX > 0) app.spanX.coerceIn(1, columns) else columns / 2; sH = if (app.spanY > 0) app.spanY.coerceIn(1, 12) else 2 }
        var row = 0; var found = false
        while (!found) {
            for (col in 0..columns - sW) {
                var clear = true
                for (dr in 0 until sH) { for (dc in 0 until sW) { if (occupied.contains(Pair(row + dr, col + dc))) { clear = false; break } }; if (!clear) break }
                if (clear) {
                    placedAppsList.add(PlacedItem(app, RectF((wF * 0.04f) + (col * cellW), (hF * 0.16f) + (row * cellH), (wF * 0.04f) + ((col + sW) * cellW), (hF * 0.16f) + ((row + sH) * cellH))))
                    for (dr in 0 until sH) { for (dc in 0 until sW) { occupied.add(Pair(row + dr, col + dc)) } }
                    found = true; break
                }
            }
            if (!found) row++
        }
    }
    totalGridContentHeight = if (occupied.isEmpty()) 0f else ((occupied.maxOf { it.first }) + 1) * cellH
}

internal fun LightspeedCruiseOverlay.querySystemColumnPreference(isLandscape: Boolean): Int {
    val prefs = prefs()
    return try { val v = prefs.all[if (isLandscape) "pref_numcolsland" else "pref_numcolspor"]; if (v is Int) v else v?.toString()?.toInt() ?: 4 } catch (e: Exception) { 4 }
}
