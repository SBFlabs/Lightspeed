package com.sbf.lightspeed

import android.content.Context
import android.graphics.RenderEffect
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.sbf.lightspeed.system.LightspeedPreferences

internal fun LightspeedCruiseOverlay.updateRenderCache() {
    val p = prefs()
    renderCacheRightFlankUnified    = p.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, true)
    renderCacheRightUnifiedExpanded = p.getBoolean("pref_section_right_unified_expanded", true)
    renderCacheTopExpanded          = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_top_expanded", false)
    renderCacheCenterExpanded       = p.getBoolean("pref_section_center_expanded", false)
    renderCacheBottomExpanded       = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_bottom_expanded", false)
    renderCacheSidebarPreview       = p.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
    renderCacheLinkEdges            = p.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LINK_EDGES, false)
    renderCacheCenterTransparency   = p.getInt("pref_sidebar_center_transparency", 35)
    renderCacheLeftCenterTransparency = p.getInt("pref_sidebar_left_center_transparency", 35)
    renderCacheTopTransparency      = p.getInt("pref_sidebar_top_transparency", 0)
    renderCacheBottomTransparency   = if (renderCacheLinkEdges) renderCacheTopTransparency else p.getInt("pref_sidebar_bottom_transparency", 0)
    renderCacheGlowStyle            = p.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"
    val rawReticle                  = p.getString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "tactical") ?: "tactical"
    val isInfUnlocked               = com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
    renderCacheReticleStyle         = if (!isInfUnlocked && (rawReticle == "cross" || rawReticle == "diamond")) "tactical" else rawReticle
    renderCacheReticleOrientation   = p.getString(LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180) ?: LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
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
    val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val landscapeMode = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
    val isCustomLandscape = isLandscape && landscapeMode == LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
    val landscapeScale = if (!isLandscape) {
        1.0f
    } else when (landscapeMode) {
        LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> 1.0f
        else -> 0.45f
    }
    val landscapeYScale = if (!isLandscape) {
        1.0f
    } else when (landscapeMode) {
        LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_FIXED -> 1.0f
        else -> 0.5f
    }

    linkEdges = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LINK_EDGES, false)
    val edgeOffsetPx = (prefs.getInt("pref_sidebar_center_x_offset", 0) * density).toInt()

    if (isCustomLandscape) {
        val defCenterH = (prefs.getInt("pref_sidebar_center_height", 70) * 0.45f).toInt().coerceAtLeast(30)
        val defCenterY = 0
        val defTopH = (prefs.getInt("pref_sidebar_top_height", 220) * 0.45f).toInt().coerceAtLeast(30)
        val defBottomH = (prefs.getInt("pref_sidebar_bottom_height", 250) * 0.45f).toInt().coerceAtLeast(30)

        centerHeightPx = prefs.getInt("pref_sidebar_center_height_landscape", defCenterH).toFloat() * density
        centerYOffsetPx = prefs.getInt("pref_sidebar_center_y_offset_landscape", defCenterY).toFloat() * density
        topHeightPx = prefs.getInt("pref_sidebar_top_height_landscape", defTopH).toFloat() * density
        bottomHeightPx = if (linkEdges) topHeightPx else prefs.getInt("pref_sidebar_bottom_height_landscape", defBottomH).toFloat() * density
    } else {
        centerHeightPx = prefs.getInt("pref_sidebar_center_height", 70).toFloat() * density * landscapeScale
        centerYOffsetPx = prefs.getInt("pref_sidebar_center_y_offset", 0).toFloat() * density * landscapeYScale
        topHeightPx = prefs.getInt("pref_sidebar_top_height", 220).toFloat() * density * landscapeScale
        bottomHeightPx = if (linkEdges) topHeightPx else prefs.getInt("pref_sidebar_bottom_height", 250).toFloat() * density * landscapeScale
    }

    centerVisualWidthPx = prefs.getInt("pref_sidebar_center_visual_width", 0).toFloat() * density
    centerTouchWidthPx = prefs.getInt("pref_sidebar_center_touch_width", 10).toFloat() * density

    topVisualWidthPx = prefs.getInt("pref_sidebar_top_visual_width", 0).toFloat() * density
    topTouchWidthPx = prefs.getInt("pref_sidebar_top_touch_width", 10).toFloat() * density

    bottomVisualWidthPx = if (linkEdges) topVisualWidthPx else prefs.getInt("pref_sidebar_bottom_visual_width", 0).toFloat() * density
    bottomTouchWidthPx = if (linkEdges) topTouchWidthPx else prefs.getInt("pref_sidebar_bottom_touch_width", 10).toFloat() * density

    if (currentLayer == CruiseLayer.HIDDEN) {
        val centerY = (screenH / 2f) + centerYOffsetPx
        val centerTop = centerY - (centerHeightPx / 2f)
        val centerBottom = centerY + (centerHeightPx / 2f)

        if (isLockscreenPillOnly) {
            val winHeight = centerHeightPx.toInt().coerceAtLeast(1)
            val winWidth = centerTouchWidthPx.toInt().coerceAtLeast(1)
            val winY = centerTop.toInt()
            val w = winWidth.toFloat()

            topTouchBounds.setEmpty()
            bottomTouchBounds.setEmpty()
            centerTouchBounds.set(w - centerTouchWidthPx, 0f, w, centerHeightPx)

            topVisualBounds.setEmpty()
            bottomVisualBounds.setEmpty()
            centerVisualBounds.set(w - centerVisualWidthPx, 0f, w, centerHeightPx)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != winY || lp.x != edgeOffsetPx || lp.gravity != (Gravity.TOP or Gravity.END)) {
                lp.gravity = Gravity.TOP or Gravity.END
                lp.x = edgeOffsetPx; lp.y = winY
                lp.width = winWidth; lp.height = winHeight
                wm.updateViewLayout(this, lp)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                systemGestureExclusionRects = listOf(android.graphics.Rect(0, 0, winWidth, winHeight))
            }
            refreshActiveRenderEffect()
            invalidate()
            return
        }

        val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
        val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

        val maxTouchW = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx)
        val winHeight = (bottomLimit - topLimit).toInt().coerceAtLeast(100)
        val winWidth = maxTouchW.toInt().coerceAtLeast(1)
        val winY = topLimit.toInt()
        val w = winWidth.toFloat()

        topTouchBounds.set(w - topTouchWidthPx, 0f, w, topHeightPx)
        centerTouchBounds.set(w - centerTouchWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
        bottomTouchBounds.set(w - bottomTouchWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

        topVisualBounds.set(w - topVisualWidthPx, 0f, w, topHeightPx)
        centerVisualBounds.set(w - centerVisualWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
        bottomVisualBounds.set(w - bottomVisualWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

        launchpadPillBounds.set(centerVisualBounds)

        if (lp.height != winHeight || lp.width != winWidth || lp.y != winY || lp.x != edgeOffsetPx || lp.gravity != (Gravity.TOP or Gravity.END)) {
            lp.gravity = Gravity.TOP or Gravity.END
            lp.x = edgeOffsetPx; lp.y = winY
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
            cachedRenderEffect = RenderEffect.createRuntimeShaderEffect(LightspeedCruiseOverlay.getProgressiveShader(context), "inputTexture")
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
