package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.app.KeyguardManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedMediaScrubberOverlay
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

class LightspeedAccessibilityService : AccessibilityService() {

    companion object {
        private var instanceRef: java.lang.ref.WeakReference<LightspeedAccessibilityService>? = null
        val instance: LightspeedAccessibilityService?
            get() = instanceRef?.get()
    }

    private var windowManager: WindowManager? = null
    private var displayManager: DisplayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    // Edge Sidebar Overlay
    private var overlayView: LightspeedCruiseOverlay? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private val edgeWidthPx = 45

    // Status Bar Overlay (Full-Width 100% Pass-Through Visual Canvas for Horizon Rails & Guides)
    private var statusBarOverlayView: LightspeedStatusBarOverlay? = null
    private lateinit var statusBarWindowParams: WindowManager.LayoutParams

    // Sensor Deck Touch Overlay (Isolated Touch Target strictly sized to Sensor Area Geometry)
    private var sensorTouchOverlayView: LightspeedSensorDeckTouchOverlay? = null
    private lateinit var sensorTouchWindowParams: WindowManager.LayoutParams

    // Dedicated Notch Pill Overlay
    private var notchOverlayView: LightspeedNotchOverlay? = null
    private lateinit var notchWindowParams: WindowManager.LayoutParams

    // Left Deflector Wing Overlay
    private var leftWingOverlayView: LightspeedLeftWingOverlay? = null
    private lateinit var leftWingWindowParams: WindowManager.LayoutParams

    // Floating Media Scrubber Overlay
    private var mediaScrubberOverlayView: LightspeedMediaScrubberOverlay? = null

    // Hardware Orientation Anchor (1x1 Transparent Window enforcing dynamic ScreenOrientation)
    private var orientationAnchorView: View? = null
    private var orientationAnchorParams: WindowManager.LayoutParams? = null

    private var systemStateReceiver: BroadcastReceiver? = null
    private var rotationContentObserver: ContentObserver? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handleDisplayOrientationChange()
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_statusbar") || key.startsWith("pref_macro_action_STATUSBAR"))) {
            updateStatusBarOverlayFromPrefs(prefs)
            updateSensorTouchOverlayFromPrefs(prefs)
            statusBarOverlayView?.postInvalidate()
        }
        if (key != null && (key.startsWith("pref_notch_") || key.startsWith("pref_telemetry_"))) {
            notchOverlayView?.postInvalidate()
        }
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_top") || key.startsWith("pref_section_center") || key.startsWith("pref_section_bottom"))) {
            updateSidebarOverlayFromPrefs(prefs)
        }
        if (key != null && (key.startsWith("pref_sidebar_left_") || key.startsWith("pref_section_left_") || key.startsWith("pref_macro_action_LEFT_"))) {
            updateLeftWingOverlayFromPrefs(prefs)
        }
        if (key == LightspeedPreferences.KEY_MASTER_FLIGHT_ARMED ||
            key == LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED ||
            key == LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED) {
            updateOverlaysVisibility()
            com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(this)
        }
        if (key == LightspeedPreferences.KEY_FLIGHT_NOTIFICATION_ENABLED) {
            com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(this)
        }
        if (key != null && key.startsWith("pref_back_tap_")) {
            com.sbf.lightspeed.system.LightspeedBackTapEngine.reloadPreferences()
        }
        if (key == LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY) {
            handleDisplayOrientationChange()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = java.lang.ref.WeakReference(this)

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        com.sbf.lightspeed.system.LightspeedShortcutManager.purgeCorruptedIcons(this)
        com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
        com.sbf.lightspeed.system.LightspeedKeyEngine.startShizukuPowerMonitor(this)
        com.sbf.lightspeed.system.LightspeedWatchdogEngine.initSentinel(this)
        com.sbf.lightspeed.system.LightspeedOrientationManager.killConflictingTools(this)

        val initPrefs = defaultPrefs()
        initPrefs.edit().putBoolean("pref_service_intentionally_stopped", false).apply()
        if (initPrefs.getBoolean("key_has_unreported_crash", false)) {
            initPrefs.edit().putBoolean("key_has_unreported_crash", false).apply()
            val crashMsg = initPrefs.getString("key_last_crash_message", "Core anomaly") ?: "Core anomaly"
            handler.postDelayed({
                com.sbf.lightspeed.system.LightspeedHapticEngine.tick(this@LightspeedAccessibilityService)
                android.widget.Toast.makeText(
                    this@LightspeedAccessibilityService,
                    "// SENTINEL RECOVERY // Online after: $crashMsg",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }, 1200L)
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)

        rotationContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                if (!com.sbf.lightspeed.system.LightspeedOrientationEngine.isRecentInternalWrite()) {
                    val isEnabled = com.sbf.lightspeed.system.LightspeedOrientationEngine.isAutoRotateEnabled(this@LightspeedAccessibilityService)
                    com.sbf.lightspeed.system.LightspeedOrientationManager.onExternalAutoRotateChanged(this@LightspeedAccessibilityService, isEnabled)
                }
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
        windowParams = WindowManager.LayoutParams(
            edgeWidthPx,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            overlayView = LightspeedCruiseOverlay(this)
            windowManager?.addView(overlayView, windowParams)
            overlayView?.post { overlayView?.updateMetricsDimensions() }
        } catch (_: Exception) {}

        // 2. Initialize Left Deflector Wing Overlay Window
        leftWingWindowParams = WindowManager.LayoutParams(
            edgeWidthPx,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            leftWingOverlayView = LightspeedLeftWingOverlay(this, this)
            windowManager?.addView(leftWingOverlayView, leftWingWindowParams)
            leftWingOverlayView?.post { leftWingOverlayView?.updateMetricsDimensions() }
        } catch (_: Exception) {}

        // 3. Initialize Status Bar Overlay Window
        val prefs = defaultPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        setupStatusBarOverlay(prefs)
        setupNotchOverlay()
        setupOrientationAnchor()

        // 4. Initialize Back Tap Engine
        com.sbf.lightspeed.system.LightspeedBackTapEngine.init(this)

        // 5. Register System State & Dock Receivers
        registerSystemStateReceiver()

        // 6. Check Deflector Startup Default State & Flight Notification
        val deflectorStartup = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, "always_armed")
        if (deflectorStartup == "standby_by_default") {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED, false)
                .putBoolean(LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED, false)
                .apply()
        }
        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(this)
        updateOverlaysVisibility()
    }

    private fun setupNotchOverlay() {
        val d = resources.displayMetrics.density
        notchWindowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (80 * d).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
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

    private fun setupStatusBarOverlay(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
        val dlRouting = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
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

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

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

    private fun setupSensorTouchOverlay(prefs: SharedPreferences) {
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
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

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

    private fun updateSensorTouchOverlayFromPrefs(prefs: SharedPreferences) {
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

    private fun updateStatusBarOverlayFromPrefs(prefs: SharedPreferences) {
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
            statusBarWindowParams.flags = statusBarWindowParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            statusBarWindowParams.width = screenWidthPx
            statusBarWindowParams.height = heightPx
            statusBarWindowParams.x = 0
            statusBarWindowParams.y = 0
            statusBarOverlayView?.invalidate()
            try {
                windowManager?.updateViewLayout(statusBarOverlayView, statusBarWindowParams)
            } catch (_: Exception) {}
        }
    }

    private fun updateSidebarOverlayFromPrefs(prefs: SharedPreferences) {
        if (windowManager == null || overlayView == null) return
        overlayView?.updateMetricsDimensions()
        overlayView?.postInvalidate()
    }

    private fun updateLeftWingOverlayFromPrefs(prefs: SharedPreferences) {
        if (windowManager == null || leftWingOverlayView == null) return
        leftWingOverlayView?.updateMetricsDimensions()
        leftWingOverlayView?.postInvalidate()
    }

    fun triggerDeflectorsGlow(durationMs: Long = -1L) {
        handler.post {
            overlayView?.triggerGlow(durationMs)
            leftWingOverlayView?.triggerGlow(durationMs)
        }
    }

    fun reloadPreferences() {
        val prefs = defaultPrefs()
        updateStatusBarOverlayFromPrefs(prefs)
        updateSensorTouchOverlayFromPrefs(prefs)
        notchOverlayView?.postInvalidate()
        updateSidebarOverlayFromPrefs(prefs)
        updateLeftWingOverlayFromPrefs(prefs)
        com.sbf.lightspeed.system.LightspeedBackTapEngine.reloadPreferences()
        com.sbf.lightspeed.system.LightspeedKeyEngine.startShizukuPowerMonitor(this)
        com.sbf.lightspeed.system.LightspeedWatchdogEngine.initSentinel(this)
        updateOverlaysVisibility()
        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(this)
    }

    fun updateWindowLayout(expand: Boolean) {
        if (windowManager == null || overlayView == null) return
        if (expand) {
            windowParams.gravity = Gravity.TOP or Gravity.START
            windowParams.x = 0; windowParams.y = 0
            windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
            windowParams.height = WindowManager.LayoutParams.MATCH_PARENT
            windowManager?.updateViewLayout(overlayView, windowParams)
        } else {
            overlayView?.updateMetricsDimensions()
        }
    }

    fun reopenCockpitHangar(setIndex: Int = -1) {
        overlayView?.post {
            overlayView?.openHangarDirectly(setIndex)
        }
    }

    fun startCruiseFromLeft(rawX: Float, rawY: Float) {
        overlayView?.startCruiseFromFlank(isLeft = true, startRawX = rawX, startRawY = rawY)
    }

    fun forwardTouchEventToCruise(isLeft: Boolean, event: MotionEvent): Boolean {
        return overlayView?.handleFlankTouchEvent(isLeft = isLeft, event = event) ?: false
    }

    fun openCockpitFromLeft() {
        overlayView?.post {
            overlayView?.openHangarFromFlank(isLeftFlank = true)
        }
    }

    fun openCockpitFromRight() {
        overlayView?.post {
            overlayView?.openHangarFromFlank(isLeftFlank = false)
        }
    }

    fun showMediaScrubber() {
        handler.post {
            if (mediaScrubberOverlayView != null) {
                mediaScrubberOverlayView?.updateTrackInfo()
                return@post
            }

            val d = resources.displayMetrics.density
            val widthPx = (350 * d).toInt().coerceAtMost(resources.displayMetrics.widthPixels)
            val heightPx = (116 * d).toInt()

            val params = WindowManager.LayoutParams(
                widthPx,
                heightPx,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (60 * d).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val overlay = LightspeedMediaScrubberOverlay(this) {
                hideMediaScrubber()
            }
            mediaScrubberOverlayView = overlay
            try {
                windowManager?.addView(overlay, params)
            } catch (_: Exception) {}
        }
    }

    fun hideMediaScrubber() {
        handler.post {
            mediaScrubberOverlayView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (_: Exception) {}
                mediaScrubberOverlayView = null
            }
        }
    }

    fun updateMediaScrubberProgress() {
        handler.post {
            if (mediaScrubberOverlayView != null) {
                mediaScrubberOverlayView?.updateTrackInfo()
            } else {
                showMediaScrubber()
            }
        }
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (!LightspeedPreferences.isMasterFlightArmed(this)) return false
        return LightspeedKeyEngine.onKeyEvent(this, event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentPkg = event.packageName?.toString()
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        
        updateOverlaysVisibility(isLocked, currentPkg)
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isFullScreen = event.isFullScreen
            val className = event.className?.toString()
            val isLikelyActivity = isFullScreen || (className != null && (className.endsWith("Activity") || className.endsWith("Launcher")))
            if (isLikelyActivity) {
                com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this, isLocked, currentPkg)
            }
        }
    }
    override fun onInterrupt() { teardown() }
    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() === this) {
            instanceRef?.clear()
            instanceRef = null
        }
        val prefs = defaultPrefs()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        com.sbf.lightspeed.system.LightspeedKeyEngine.stopShizukuPowerMonitor()
        com.sbf.lightspeed.system.LightspeedWatchdogEngine.stopSentinel()
        teardown()
    }

    private fun getScreenRotation(): Int {
        val dm = displayManager ?: (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
        val disp = dm?.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        return disp?.rotation ?: Surface.ROTATION_0
    }

    private fun handleDisplayOrientationChange() {
        val prefs = defaultPrefs()
        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val rotation = getScreenRotation()
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        if (policy == "portrait_only" && isLandscape) {
            setOverlaysVisible(false)
        } else {
            updateOverlaysVisibility()
            resyncOverlayMetrics()
        }

        // Check if landscape dock charging trigger activates upon rotating to landscape
        val trigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
        if ((trigger == "charging_dock_landscape" || trigger == "landscape_charging") && isLandscape && isDeviceCharging() && !LightspeedRefuelingActivity.isActive) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isScreenOff = powerManager?.isInteractive == false
            val isLocked = keyguardManager?.isKeyguardLocked == true
            if (isScreenOff || isLocked) {
                launchRefuelingActivity()
            }
        }
    }

    private fun isSuppressedByOrientation(): Boolean {
        val prefs = defaultPrefs()
        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val rotation = getScreenRotation()
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        return policy == "portrait_only" && isLandscape
    }

    private fun setOverlaysVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        overlayView?.visibility = v
        leftWingOverlayView?.visibility = v
        statusBarOverlayView?.visibility = v
        sensorTouchOverlayView?.visibility = v
        notchOverlayView?.visibility = v
    }

    fun updateOverlaysVisibility(isLocked: Boolean? = null, currentPkg: String? = null) {
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
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val effectiveLocked = isLocked ?: (keyguardManager?.isKeyguardLocked == true)
        val isRefueling = LightspeedRefuelingActivity.isActive
        val isInfinixStandby = currentPkg != null && (currentPkg.contains("standby") || currentPkg.contains("aod") || currentPkg == "com.transsion.aod" || currentPkg == "com.infinix.aod" || currentPkg == "com.transsion.aod.app")
        val isDoze = currentPkg == "com.android.systemui" && !effectiveLocked && (getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == false

        // Top HUD Overlays (Status Bar Rail, Notch Orbital Capsule, and Sensor Deck Touch Target)
        // Suppressed when device is locked/docked (if configured), during Refueling Bay, or during OEM AOD/Doze.
        val shouldHideTopHud = (hideOnLockAndDock && effectiveLocked) || isRefueling || isInfinixStandby || isDoze || isSuppressedByOrientation()
        val topHudVisibility = if (shouldHideTopHud) View.GONE else View.VISIBLE

        statusBarOverlayView?.visibility = topHudVisibility
        notchOverlayView?.visibility = topHudVisibility
        sensorTouchOverlayView?.visibility = topHudVisibility

        // Kinetic Deflectors (Left & Right Flank Wings / Edge Gesture Controls)
        // Deflectors respect their dedicated master toggles and orientation suppression policy.
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

    fun scheduleGeometryResync() {
        handler.postDelayed({ resyncOverlayMetrics() }, 100L)
        handler.postDelayed({ resyncOverlayMetrics() }, 300L)
    }

    private fun resyncOverlayMetrics() {
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

    private fun registerSystemStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DREAMING_STARTED)
            addAction(Intent.ACTION_DREAMING_STOPPED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }

        systemStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null) return
                val action = intent.action ?: return
                val prefs = defaultPrefs()
                val hideOnLockAndDock = prefs.getBoolean(LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK, true)

                when (action) {
                    Intent.ACTION_DREAMING_STARTED -> {
                        if (hideOnLockAndDock && !LightspeedRefuelingActivity.isActive) {
                            updateOverlaysVisibility(isLocked = true, currentPkg = null)
                        }
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        com.sbf.lightspeed.system.LightspeedOrientationManager.onScreenOff(this@LightspeedAccessibilityService)
                        if (hideOnLockAndDock && !LightspeedRefuelingActivity.isActive) {
                            updateOverlaysVisibility(isLocked = true, currentPkg = null)
                        }
                        checkScreenOffRefuelingTrigger(prefs)
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val isLocked = keyguardManager?.isKeyguardLocked == true
                        
                        updateOverlaysVisibility(isLocked, null)
                        resyncOverlayMetrics()
                        
                        com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@LightspeedAccessibilityService, isLocked, null)

                        val asLockscreen = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_AS_LOCKSCREEN, false)
                        if (asLockscreen && isLocked && !LightspeedRefuelingActivity.isActive) {
                            launchRefuelingActivity()
                        }
                    }
                    Intent.ACTION_DREAMING_STOPPED, Intent.ACTION_USER_PRESENT -> {
                        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val isLocked = keyguardManager?.isKeyguardLocked == true
                        
                        updateOverlaysVisibility(isLocked, null)
                        resyncOverlayMetrics()
                        
                        val activePkg = rootInActiveWindow?.packageName?.toString() 
                            ?: com.sbf.lightspeed.system.LightspeedOrientationEngine.getDefaultLauncherPackage(this@LightspeedAccessibilityService)
                        com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@LightspeedAccessibilityService, isLocked, activePkg)

                        // Re-evaluate shortly after unlock to ensure keyguard dismiss animation settled and top window is captured
                        handler.postDelayed({
                            val currentKm = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                            val lockedNow = currentKm?.isKeyguardLocked == true
                            val postPkg = rootInActiveWindow?.packageName?.toString() 
                                ?: com.sbf.lightspeed.system.LightspeedOrientationEngine.getDefaultLauncherPackage(this@LightspeedAccessibilityService)
                            com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@LightspeedAccessibilityService, lockedNow, postPkg)
                        }, 250L)
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        checkPowerConnectedRefuelingTrigger(prefs)
                    }
                    Intent.ACTION_POWER_DISCONNECTED -> {
                        LightspeedRefuelingActivity.isChargingSessionDismissed = false
                        LightspeedRefuelingActivity.isSessionDismissed = false
                    }
                }
            }
        }
        try {
            registerReceiver(systemStateReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun isDeviceCharging(): Boolean {
        return try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            false
        }
    }

    private fun checkPowerConnectedRefuelingTrigger(prefs: SharedPreferences) {
        val trigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
        if (trigger == "disabled" || trigger == "screensaver_only") return

        val rotation = getScreenRotation()
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isScreenOff = powerManager?.isInteractive == false
        val isLocked = keyguardManager?.isKeyguardLocked == true

        when (trigger) {
            "charging_screen_off", "always_charging" -> {
                // If cable plugged in while locked or screen off -> wake & launch Refueling Bay
                if (isScreenOff || isLocked) {
                    launchRefuelingActivity()
                }
            }
            "charging_dock_landscape", "landscape_charging" -> {
                if (isLandscape) {
                    launchRefuelingActivity()
                }
            }
        }
    }

    private fun checkScreenOffRefuelingTrigger(prefs: SharedPreferences) {
        val trigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
        if (trigger == "disabled" || trigger == "screensaver_only") return

        val rotation = getScreenRotation()
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        if (trigger == "screen_timeout" || trigger == "screen_off_always") {
            launchRefuelingActivity()
            return
        }

        if (isDeviceCharging()) {
            when (trigger) {
                "charging_screen_off", "always_charging" -> {
                    // Screen went off while plugged in -> launch over lockscreen
                    launchRefuelingActivity()
                }
                "charging_dock_landscape", "landscape_charging" -> {
                    if (isLandscape) {
                        launchRefuelingActivity()
                    }
                }
            }
        }
    }

    private fun launchRefuelingActivity() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wl = pm?.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "lightspeed:refueling_wake"
            )
            wl?.acquire(3000L)
        } catch (_: Exception) {}

        val intent = Intent(this, LightspeedRefuelingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
    }

    fun updateNotchWindowBounds(isExpanded: Boolean, targetX: Int, targetY: Int, targetWidth: Int, targetHeight: Int, isVisible: Boolean = true) {
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

    private fun setupOrientationAnchor() {
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

    fun updateForcedOrientation(orientation: Int) {
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

    private fun teardown() {
        LightspeedKeyEngine.reset()
        com.sbf.lightspeed.system.LightspeedBackTapEngine.destroy()
        displayManager?.unregisterDisplayListener(displayListener)
        rotationContentObserver?.let {
            try { contentResolver.unregisterContentObserver(it) } catch (_: Exception) {}
            rotationContentObserver = null
        }
        systemStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            systemStateReceiver = null
        }
        mediaScrubberOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            mediaScrubberOverlayView = null
        }
        orientationAnchorView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            orientationAnchorView = null
        }
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        leftWingOverlayView?.let {
            windowManager?.removeView(it)
            leftWingOverlayView = null
        }
        statusBarOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            statusBarOverlayView = null
        }
        sensorTouchOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            sensorTouchOverlayView = null
        }
        notchOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            notchOverlayView = null
        }
    }
}
