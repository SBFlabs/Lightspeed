package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import com.sbf.lightspeed.system.LightspeedDeflectorRenderer
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Extracted draw logic for LightspeedLeftWingOverlay.
 */
internal fun LightspeedLeftWingOverlay.handleDraw(canvas: Canvas, superCall: () -> Unit) {
    superCall()
    val d = resources.displayMetrics.density

    val isFlankUnified = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK_ACTIONS, true)
    val isUnifiedExpanded = prefs.getBoolean(LightspeedPreferences.KEY_SECTION_LEFT_UNIFIED_EXPANDED, true)
    val isTopExpanded = if (isFlankUnified) isUnifiedExpanded else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_LEFT_TOP_EXPANDED, false)
    val isCenterExpanded = prefs.getBoolean(LightspeedPreferences.KEY_SECTION_LEFT_CENTER_EXPANDED, false)
    val isBottomExpanded = if (isFlankUnified) isUnifiedExpanded else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_LEFT_BOTTOM_EXPANDED, false)
    val isPreview = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)

    val geomMode = prefs.getString(LightspeedPreferences.KEY_SYMMETRY_GEOMETRY_MODE, "independent") ?: "independent"
    val isMirroringRight = geomMode == "right"

    val centerTransparency = if (isMirroringRight) prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_CENTER_TRANSPARENCY, 0) else prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_LEFT_CENTER_TRANSPARENCY, 0)
    val topTransparency = if (isMirroringRight) prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_TOP_TRANSPARENCY, 0) else prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_LEFT_TOP_TRANSPARENCY, 0)
    val bottomTransparency = if (isMirroringRight) prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_BOTTOM_TRANSPARENCY, 0) else prefs.getInt(LightspeedPreferences.KEY_SIDEBAR_LEFT_BOTTOM_TRANSPARENCY, 0)

    val glowStyle = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"
    val isGlowEnabled = prefs.getBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, prefs.getBoolean(LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))
    val useM3Color = prefs.getBoolean(LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, true)

    val m3Primary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.resources.getColor(android.R.color.system_accent1_600, context.theme)
    } else {
        Color.parseColor("#6750A4")
    }

    LightspeedDeflectorRenderer.drawDeflectorWing(
        canvas = canvas,
        isLeft = true,
        density = d,
        w = width.toFloat(),
        h = height.toFloat(),
        topTouchBounds = topTouchBounds,
        centerTouchBounds = centerTouchBounds,
        bottomTouchBounds = bottomTouchBounds,
        isCurrentlyTouched = isCurrentlyTouched,
        activeZoneIsCenter = activeZoneKey == "LEFT_CENTER",
        activeZoneIsTop = activeZoneKey == "LEFT_TOP",
        activeZoneIsBottom = activeZoneKey == "LEFT_BOTTOM",
        glowFraction = glowFraction,
        centerTransparency = centerTransparency,
        topTransparency = topTransparency,
        bottomTransparency = bottomTransparency,
        isReview = isPreview && (isTopExpanded || isCenterExpanded || isBottomExpanded),
        m3Primary = m3Primary,
        glowStyle = glowStyle,
        isGlowEnabled = isGlowEnabled,
        useM3Color = useM3Color,
        pillStyle = LightspeedPreferences.getDeflectorPillStyle(context, true),
        touchY = null,
        visualWidthPx = centerVisualWidthPx
    )
}
