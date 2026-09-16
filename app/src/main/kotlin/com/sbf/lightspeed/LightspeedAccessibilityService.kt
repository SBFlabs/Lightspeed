package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.app.KeyguardManager
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
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedMediaScrubberOverlay
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

class LightspeedAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "LightspeedService"
        internal var instanceRef: java.lang.ref.WeakReference<LightspeedAccessibilityService>? = null

        val instance: LightspeedAccessibilityService?
            get() = instanceRef?.get()

        /** True when the service is bound and its WeakReference is still live. */
        val isAlive: Boolean
            get() = instanceRef?.get() != null

        /**
         * Executes [block] on the live service instance. If the instance has been
         * GC'd or was never set, logs a diagnostic warning instead of silently
         * dropping the action. Use this at call sites where a null service is
         * unexpected (gestures, overlay lifecycle, automation commands).
         */
        inline fun withService(block: LightspeedAccessibilityService.() -> Unit) {
            val svc = instance
            if (svc != null) {
                svc.block()
            } else {
                Log.w(TAG, "withService: instance unavailable — action dropped (WeakRef GC'd or service not started)")
            }
        }
    }

    internal var windowManager: WindowManager? = null
    internal var displayManager: DisplayManager? = null
    internal val handler = Handler(Looper.getMainLooper())

    // Edge Sidebar Overlay
    internal var overlayView: LightspeedCruiseOverlay? = null
    internal lateinit var windowParams: WindowManager.LayoutParams
    internal val edgeWidthPx = 45

    // Status Bar Overlay (Full-Width 100% Pass-Through Visual Canvas for Horizon Rails & Guides)
    internal var statusBarOverlayView: LightspeedStatusBarOverlay? = null
    internal lateinit var statusBarWindowParams: WindowManager.LayoutParams

    // Sensor Deck Touch Overlay (Isolated Touch Target strictly sized to Sensor Area Geometry)
    internal var sensorTouchOverlayView: LightspeedSensorDeckTouchOverlay? = null
    internal lateinit var sensorTouchWindowParams: WindowManager.LayoutParams

    // Dedicated Notch Pill Overlay
    internal var notchOverlayView: LightspeedNotchOverlay? = null
    internal lateinit var notchWindowParams: WindowManager.LayoutParams

    // Left Deflector Wing Overlay
    internal var leftWingOverlayView: LightspeedLeftWingOverlay? = null
    internal lateinit var leftWingWindowParams: WindowManager.LayoutParams

    // Floating Media Scrubber Overlay
    internal var mediaScrubberOverlayView: LightspeedMediaScrubberOverlay? = null

    // Hardware Orientation Anchor (1x1 Transparent Window enforcing dynamic ScreenOrientation)
    internal var orientationAnchorView: View? = null
    internal var orientationAnchorParams: WindowManager.LayoutParams? = null

    internal var systemStateReceiver: BroadcastReceiver? = null
    internal var rotationContentObserver: ContentObserver? = null

    internal val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handleDisplayOrientationChange()
        }
    }

    internal val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
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
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 45
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
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 45
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
        com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateGravityCascade(this)
        updateOverlaysVisibility()
        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(this)
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

    fun triggerDeflectorsGlow(durationMs: Long = -1L) = triggerDeflectorsGlowInternal(durationMs)
    fun triggerDeflectorGlow(isLeft: Boolean, durationMs: Long = -1L) = triggerDeflectorGlowInternal(isLeft, durationMs)
    fun updateOverlaysVisibility(isLocked: Boolean? = null, currentPkg: String? = null) = updateOverlaysVisibilityInternal(isLocked, currentPkg)
    fun scheduleGeometryResync() = scheduleGeometryResyncInternal()
    fun updateForcedOrientation(orientation: Int) = updateForcedOrientationInternal(orientation)
    fun updateWindowLayout(expand: Boolean) = updateWindowLayoutInternal(expand)
    fun updateNotchWindowBounds(isExpanded: Boolean, targetX: Int, targetY: Int, targetWidth: Int, targetHeight: Int, isVisible: Boolean = true) = updateNotchWindowBoundsInternal(isExpanded, targetX, targetY, targetWidth, targetHeight, isVisible)

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (!LightspeedPreferences.isMasterFlightArmed(this)) return false
        return LightspeedKeyEngine.onKeyEvent(this, event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPkg = event.packageName?.toString()
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = keyguardManager?.isKeyguardLocked == true

            updateOverlaysVisibility(isLocked, currentPkg)

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
        try {
            val prefs = defaultPrefs()
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            com.sbf.lightspeed.system.LightspeedKeyEngine.stopShizukuPowerMonitor()
            com.sbf.lightspeed.system.LightspeedWatchdogEngine.stopSentinel()
            teardown()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during service onDestroy: ${e.message}", e)
        }
    }
}
