package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

internal fun LightspeedAccessibilityService.setupNotchOverlay() {
    val d = resources.displayMetrics.density
    notchWindowParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        (80 * d).toInt(),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    notchOverlayView = LightspeedNotchOverlay(this)
    try {
        windowManager?.addView(notchOverlayView, notchWindowParams)
    } catch (_: Exception) {}
}

internal fun LightspeedAccessibilityService.setupStatusBarOverlay(prefs: SharedPreferences) {
    val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
    val dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line") ?: "top_line"
    val mediaRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
    val isRailRoutingActive = dlRouting == "top_line" || dlRouting == "both" || mediaRouting == "top_line" || mediaRouting == "both"
    val isRailPreview = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)

    if (!enabled && !isRailRoutingActive && !isRailPreview) return

    val screenWidthPx = resources.displayMetrics.widthPixels
    val density = resources.displayMetrics.density
    val sensorThicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
    val railThicknessDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6)
    val maxRails = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3)
    val isRailText = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
    val railOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0)
    val textOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0)
    val textPos = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"

    val baseRailHeightDp = railOffsetY + (railThicknessDp * maxRails) + 8
    val textHeightDp = if (isRailText) {
        if (textPos == "below_statusbar") {
            sensorThicknessDp + textOffsetY + 24
        } else {
            baseRailHeightDp + textOffsetY + 24
        }
    } else baseRailHeightDp

    val effectiveHeightDp = if (enabled) maxOf(sensorThicknessDp, textHeightDp) else textHeightDp
    val heightPx = (effectiveHeightDp * density).toInt()

    @Suppress("DEPRECATION")
    val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    statusBarWindowParams = WindowManager.LayoutParams(
        screenWidthPx,
        heightPx,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    statusBarOverlayView = LightspeedStatusBarOverlay(this, this)
    try {
        windowManager?.addView(statusBarOverlayView, statusBarWindowParams)
    } catch (_: Exception) {}

    setupSensorTouchOverlay(prefs)
}

internal fun LightspeedAccessibilityService.setupSensorTouchOverlay(prefs: SharedPreferences) {
    val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
    if (!enabled) return

    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val density = resources.displayMetrics.density
    val spanPref = prefs.getInt("pref_statusbar_span", 1080)
    val spanPx = if (spanPref >= 1000) screenW else (spanPref * density).coerceIn(50f * density, screenW)
    val sensorOffsetX = prefs.getInt("pref_statusbar_offset_x", 0) * density
    val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
    val sensorHeight = thicknessDp * density
    val sensorOffsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).coerceAtLeast(0f)

    val sensorLeft = (((screenW - spanPx) / 2f) + sensorOffsetX).coerceIn(0f, screenW - spanPx)
    val sensorTop = sensorOffsetY

    val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED

    sensorTouchWindowParams = WindowManager.LayoutParams(
        spanPx.toInt().coerceAtLeast(1),
        sensorHeight.toInt().coerceAtLeast(1),
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = sensorLeft.toInt()
        y = sensorTop.toInt()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    sensorTouchOverlayView = LightspeedSensorDeckTouchOverlay(this, this)
    try {
        windowManager?.addView(sensorTouchOverlayView, sensorTouchWindowParams)
    } catch (_: Exception) {}
}

internal fun LightspeedAccessibilityService.updateSensorTouchOverlayFromPrefs(prefs: SharedPreferences) {
    val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
    if (!enabled) {
        sensorTouchOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            sensorTouchOverlayView = null
        }
        return
    }

    val screenW = resources.displayMetrics.widthPixels.toFloat()
    val density = resources.displayMetrics.density
    val spanPref = prefs.getInt("pref_statusbar_span", 1080)
    val spanPx = if (spanPref >= 1000) screenW else (spanPref * density).coerceIn(50f * density, screenW)
    val sensorOffsetX = prefs.getInt("pref_statusbar_offset_x", 0) * density
    val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
    val sensorHeight = thicknessDp * density
    val sensorOffsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).coerceAtLeast(0f)

    val sensorLeft = (((screenW - spanPx) / 2f) + sensorOffsetX).coerceIn(0f, screenW - spanPx)
    val sensorTop = sensorOffsetY

    if (sensorTouchOverlayView == null) {
        setupSensorTouchOverlay(prefs)
    } else {
        sensorTouchWindowParams.width = spanPx.toInt().coerceAtLeast(1)
        sensorTouchWindowParams.height = sensorHeight.toInt().coerceAtLeast(1)
        sensorTouchWindowParams.x = sensorLeft.toInt()
        sensorTouchWindowParams.y = sensorTop.toInt()
        try {
            windowManager?.updateViewLayout(sensorTouchOverlayView, sensorTouchWindowParams)
        } catch (_: Exception) {}
    }
}

internal fun LightspeedAccessibilityService.updateStatusBarOverlayFromPrefs(prefs: SharedPreferences) {
    val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
    val dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
    val mediaRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
    val isRailRoutingActive = dlRouting == "top_line" || dlRouting == "both" || mediaRouting == "top_line" || mediaRouting == "both"
    val isRailPreview = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)

    if (!enabled && !isRailRoutingActive && !isRailPreview) {
        statusBarOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            statusBarOverlayView = null
        }
        return
    }

    val screenWidthPx = resources.displayMetrics.widthPixels
    val density = resources.displayMetrics.density
    val sensorThicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
    val railThicknessDp = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, 2).coerceIn(1, 6)
    val maxRails = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, 2).coerceIn(1, 3)
    val isRailText = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ENABLED, true)
    val railOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, 0)
    val textOffsetY = prefs.getInt(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, 0)
    val textPos = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_POSITION, "below") ?: "below"

    val baseRailHeightDp = railOffsetY + (railThicknessDp * maxRails) + 8
    val textHeightDp = if (isRailText) {
        if (textPos == "below_statusbar") {
            sensorThicknessDp + textOffsetY + 24
        } else {
            baseRailHeightDp + textOffsetY + 24
        }
    } else baseRailHeightDp

    val effectiveHeightDp = if (enabled) maxOf(sensorThicknessDp, textHeightDp) else textHeightDp
    val heightPx = (effectiveHeightDp * density).toInt()

    if (statusBarOverlayView == null) {
        setupStatusBarOverlay(prefs)
    } else {
        val isHudActive = statusBarOverlayView?.isHudActive() == true
        statusBarWindowParams.flags = statusBarWindowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        statusBarWindowParams.width = screenWidthPx
        if (!isHudActive) {
            statusBarWindowParams.height = heightPx
        } else {
            statusBarWindowParams.height = (260 * density).toInt()
        }
        statusBarWindowParams.x = 0
        statusBarWindowParams.y = 0
        statusBarOverlayView?.invalidate()
        try {
            windowManager?.updateViewLayout(statusBarOverlayView, statusBarWindowParams)
        } catch (_: Exception) {}
    }
}

internal fun LightspeedAccessibilityService.updateSidebarOverlayFromPrefs(prefs: SharedPreferences) {
    if (windowManager == null || overlayView == null) return
    overlayView?.updateRenderCache()
    overlayView?.updateMetricsDimensions()
    overlayView?.postInvalidate()
}

internal fun LightspeedAccessibilityService.updateLeftWingOverlayFromPrefs(prefs: SharedPreferences) {
    if (windowManager == null || leftWingOverlayView == null) return
    leftWingOverlayView?.updateMetricsDimensions()
    leftWingOverlayView?.postInvalidate()
}

internal fun LightspeedAccessibilityService.triggerDeflectorsGlowInternal(durationMs: Long = -1L) {
    handler.post {
        overlayView?.triggerGlow(durationMs)
        leftWingOverlayView?.triggerGlow(durationMs)
    }
}

internal fun LightspeedAccessibilityService.triggerDeflectorGlowInternal(isLeft: Boolean, durationMs: Long = -1L) {
    handler.post {
        if (isLeft) {
            leftWingOverlayView?.triggerGlow(durationMs)
        } else {
            overlayView?.triggerGlow(durationMs)
        }
    }
}

internal fun LightspeedAccessibilityService.updateWindowLayoutInternal(expand: Boolean) {
    if (windowManager == null || overlayView == null) return
    if (expand) {
        windowParams.gravity = Gravity.TOP or Gravity.START
        windowParams.x = 0; windowParams.y = 0
        windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            windowParams.blurBehindRadius = 75
        }
        windowManager?.updateViewLayout(overlayView, windowParams)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowParams.flags = windowParams.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            windowParams.blurBehindRadius = 45
        }
        overlayView?.updateMetricsDimensions()
    }
}

internal fun LightspeedAccessibilityService.getScreenRotation(): Int {
    val dm = displayManager ?: (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
    val disp = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
    return disp?.rotation ?: Surface.ROTATION_0
}

internal fun LightspeedAccessibilityService.isSuppressedByOrientation(): Boolean {
    val prefs = defaultPrefs()
    val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
    val rotation = getScreenRotation()
    val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
    return policy == "portrait_only" && isLandscape
}

internal fun LightspeedAccessibilityService.setOverlaysVisible(visible: Boolean) {
    val v = if (visible) View.VISIBLE else View.GONE
    overlayView?.visibility = v
    leftWingOverlayView?.visibility = v
    statusBarOverlayView?.visibility = v
    sensorTouchOverlayView?.visibility = v
    notchOverlayView?.visibility = v
}

internal fun LightspeedAccessibilityService.updateOverlaysVisibilityInternal(isLocked: Boolean? = null, currentPkg: String? = null) {
    val prefs = defaultPrefs()

    // 0. Master Flight Deck Standby Guard: If disarmed, dismantle all overlays instantly
    if (!LightspeedPreferences.isMasterFlightArmed(this)) {
        statusBarOverlayView?.visibility = View.GONE
        notchOverlayView?.visibility = View.GONE
        sensorTouchOverlayView?.visibility = View.GONE
        overlayView?.visibility = View.GONE
        leftWingOverlayView?.visibility = View.GONE
        mediaScrubberOverlayView?.visibility = View.GONE
        return
    }

    val hideOnLockAndDock = prefs.getBoolean(LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK, true)
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
    val effectiveLocked = isLocked ?: (keyguardManager?.isKeyguardLocked == true)
    val isRefueling = LightspeedRefuelingActivity.isActive
    val isInfinixStandby = currentPkg != null && (currentPkg.contains("standby") || currentPkg.contains("aod") || currentPkg == "com.transsion.aod" || currentPkg == "com.infinix.aod" || currentPkg == "com.transsion.aod.app")
    val isDoze = currentPkg == "com.android.systemui" && !effectiveLocked && (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == false

    // Top HUD Overlays (Status Bar Rail, Notch Orbital Capsule, and Sensor Deck Touch Target)
    val shouldHideTopHud = (hideOnLockAndDock && effectiveLocked) || isRefueling || isInfinixStandby || isDoze || isSuppressedByOrientation()
    val topHudVisibility = if (shouldHideTopHud) View.GONE else View.VISIBLE

    statusBarOverlayView?.visibility = topHudVisibility
    notchOverlayView?.visibility = topHudVisibility
    sensorTouchOverlayView?.visibility = topHudVisibility

    // Kinetic Deflectors (Left & Right Flank Wings / Edge Gesture Controls)
    val isLeftDeflectorEnabled = LightspeedPreferences.isLeftDeflectorEnabled(this)
    val isRightDeflectorEnabled = LightspeedPreferences.isRightDeflectorEnabled(this)
    val isOrientationSuppressed = isSuppressedByOrientation()

    val leftVisibility = if (isLeftDeflectorEnabled && !isOrientationSuppressed) View.VISIBLE else View.GONE
    val rightVisibility = if (isRightDeflectorEnabled && !isOrientationSuppressed) View.VISIBLE else View.GONE

    overlayView?.visibility = rightVisibility
    leftWingOverlayView?.visibility = leftVisibility

    if (rightVisibility == View.VISIBLE) {
        overlayView?.postInvalidate()
    }
    if (leftVisibility == View.VISIBLE) {
        leftWingOverlayView?.postInvalidate()
    }
}

internal fun LightspeedAccessibilityService.scheduleGeometryResyncInternal() {
    handler.postDelayed({ resyncOverlayMetrics() }, 100L)
    handler.postDelayed({ resyncOverlayMetrics() }, 300L)
}

internal fun LightspeedAccessibilityService.resyncOverlayMetrics() {
    if (windowManager == null) return
    overlayView?.updateMetricsDimensions()
    leftWingOverlayView?.updateMetricsDimensions()
    val prefs = defaultPrefs()
    updateStatusBarOverlayFromPrefs(prefs)
    updateSensorTouchOverlayFromPrefs(prefs)
    notchOverlayView?.updateNotchMetrics()
    overlayView?.postInvalidate()
    leftWingOverlayView?.postInvalidate()
    statusBarOverlayView?.postInvalidate()
    notchOverlayView?.postInvalidate()
}

internal fun LightspeedAccessibilityService.updateNotchWindowBoundsInternal(isExpanded: Boolean, targetX: Int, targetY: Int, targetWidth: Int, targetHeight: Int, isVisible: Boolean = true) {
    if (windowManager == null || notchOverlayView == null) return
    notchWindowParams.width = targetWidth.coerceAtLeast(1)
    notchWindowParams.height = targetHeight.coerceAtLeast(1)
    notchWindowParams.x = targetX
    notchWindowParams.y = targetY
    notchWindowParams.gravity = Gravity.TOP or Gravity.START
    if (!isVisible) {
        notchWindowParams.flags = notchWindowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        notchOverlayView?.visibility = View.GONE
    } else {
        notchWindowParams.flags = notchWindowParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        notchOverlayView?.visibility = View.VISIBLE
    }
    try {
        windowManager?.updateViewLayout(notchOverlayView, notchWindowParams)
    } catch (_: Exception) {}
}

internal fun LightspeedAccessibilityService.setupOrientationAnchor() {
    if (orientationAnchorView != null) return
    val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }
    @Suppress("DEPRECATION")
    orientationAnchorParams = WindowManager.LayoutParams(
        1, 1,
        windowType,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        alpha = 0.8f
        screenOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    try {
        val view = View(this).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        orientationAnchorView = view
        windowManager?.addView(view, orientationAnchorParams)
        Log.i("LightspeedAccessibility", "Initialized hardware orientation anchor window (TYPE_APPLICATION_OVERLAY)")
    } catch (e: Exception) {
        Log.w("LightspeedAccessibility", "Failed adding orientation anchor", e)
    }
}

internal fun LightspeedAccessibilityService.updateForcedOrientationInternal(orientation: Int) {
    handler.post {
        val anchor = orientationAnchorView
        val params = orientationAnchorParams
        if (anchor == null || params == null) {
            setupOrientationAnchor()
        }
        orientationAnchorParams?.let { p ->
            if (p.screenOrientation != orientation) {
                p.screenOrientation = orientation
                try {
                    windowManager?.updateViewLayout(orientationAnchorView, p)
                    Log.i("LightspeedAccessibility", "Updated hardware orientation anchor: $orientation")
                } catch (e: Exception) {
                    Log.w("LightspeedAccessibility", "Failed updating hardware orientation anchor", e)
                }
            }
        }
    }
}
