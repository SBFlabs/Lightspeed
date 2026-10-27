package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.database.ContentObserver
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

internal fun LightspeedAccessibilityService.initializeServiceSession() {
    val info = serviceInfo ?: AccessibilityServiceInfo()
    info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
    serviceInfo = info

    LightspeedShortcutManager.purgeCorruptedIcons(this)
    LightspeedIconManager.clearCache()
    CockpitGearRepository.prewarmGearCache(this)
    LightspeedIconManager.prewarmIcons(this)
    LightspeedKeyEngine.startShizukuPowerMonitor(this)
    PowerLongPressTakeover.sync(this)
    LightspeedWatchdogEngine.initSentinel(this)
    LightspeedWatchdogEngine.registerFastTriggers(this)
    LightspeedOrientationManager.killConflictingTools(this)
    LightspeedGuardHelper.start(this)

    val initPrefs = defaultPrefs()
    initPrefs.edit().putBoolean("pref_service_intentionally_stopped", false).apply()
    LightspeedGuardHelper.setPaused(this, false)
    if (initPrefs.getBoolean("key_has_unreported_crash", false)) {
        initPrefs.edit().putBoolean("key_has_unreported_crash", false).apply()
        val crashMsg = initPrefs.getString("key_last_crash_message", "Core anomaly") ?: "Core anomaly"
        handler.postDelayed({
            LightspeedHapticEngine.tick(this)
            android.widget.Toast.makeText(this, "// SENTINEL RECOVERY // Online after: $crashMsg", android.widget.Toast.LENGTH_LONG).show()
        }, 1200L)
    }

    if (!initPrefs.getBoolean("pref_service_first_connect_edge_notified", false)) {
        initPrefs.edit().putBoolean("pref_service_first_connect_edge_notified", true).apply()
        handler.postDelayed({
            try {
                val leftInset = Settings.Secure.getFloat(contentResolver, "back_gesture_inset_scale_left", 1.0f)
                val rightInset = Settings.Secure.getFloat(contentResolver, "back_gesture_inset_scale_right", 1.0f)
                if (leftInset > 0.05f || rightInset > 0.05f) {
                    android.widget.Toast.makeText(this, "💡 Tip: Android's native back gestures can be neutralized in System Overrides.", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { logSwallowed("LightspeedAccessibilityService", "firstConnectEdge", e) }
        }, 3500L)
    }

    windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    displayManager?.registerDisplayListener(displayListener, handler)

    rotationContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            if (LightspeedOrientationEngine.isRecentInternalWrite()) {
                return
            }
            val isEnabled = LightspeedOrientationEngine.isAutoRotateEnabled(this@initializeServiceSession)
            LightspeedOrientationManager.onExternalAutoRotateChanged(this@initializeServiceSession, isEnabled)
        }
    }
    try {
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            rotationContentObserver!!
        )
    } catch (e: Exception) {
        Log.w("AccessibilityService", "Failed registering rotationContentObserver", e)
    }

    // 1. Initialize Right Sidebar Overlay Window
    @Suppress("DEPRECATION")
    val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS

    windowParams = WindowManager.LayoutParams(
        edgeWidthPx,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        overlayFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.END
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurBehindRadius = 0
        }
    }

    try {
        overlayView = LightspeedCruiseOverlay(this)
        windowManager?.addView(overlayView, windowParams)
        overlayView?.post { overlayView?.updateMetricsDimensions() }
    } catch (e: Exception) { logSwallowed("LightspeedAccessibilityService", "addOverlayView", e) }

    // 2. Initialize Left Deflector Wing Overlay Window
    leftWingWindowParams = WindowManager.LayoutParams(
        edgeWidthPx,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        overlayFlags,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurBehindRadius = 0
        }
    }

    try {
        leftWingOverlayView = LightspeedLeftWingOverlay(this, this)
        windowManager?.addView(leftWingOverlayView, leftWingWindowParams)
        leftWingOverlayView?.post { leftWingOverlayView?.updateMetricsDimensions() }
    } catch (e: Exception) { logSwallowed("LightspeedAccessibilityService", "addLeftWingOverlay", e) }

    // 3. Initialize Status Bar Overlay Window
    val prefs = defaultPrefs()
    prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
    setupStatusBarOverlay(prefs)
    setupNotchOverlay()

    // 4. Initialize Back Tap Engine
    LightspeedBackTapEngine.init(this)

    // 5. Register System State & Dock Receivers
    registerSystemStateReceiver()
    CallStateTracker.register(this)
    val isSonicEnabled = prefs.getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false)
    if (isSonicEnabled) {
        SonicAppVolumeEngine.startMonitoring(this)
        SonicVoiceIsolationEngine.initialize(this)
        SonicChannelRouter.applyRoutingRules(this)
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky {
                SonicVoiceIsolationEngine.ensureBound(this)
            }
        } catch (_: Exception) {}
    }
    LightspeedEyeCareEngine.init(this)
    LightspeedSmartPixelsOverlay.init(this)
    LightspeedSpacePodOverlay.init(this)
    com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.init(this)

    // 6. Check Deflector Startup Default State & Flight Notification
    val deflectorStartup = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, "always_armed")
    if (deflectorStartup == "standby_by_default") {
        prefs.edit()
            .putBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED, false)
            .putBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED, false)
            .apply()
    }
    LightspeedFlightNotificationManager.update(this)
    updateOverlaysVisibility()
}
