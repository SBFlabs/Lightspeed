package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedMediaScrubberOverlay
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

class LightspeedAccessibilityService : AccessibilityService() {

    companion object {
        var instance: LightspeedAccessibilityService? = null
            private set
    }

    private var windowManager: WindowManager? = null
    private var displayManager: DisplayManager? = null
    private val handler = Handler(Looper.getMainLooper())

    // Edge Sidebar Overlay
    private var overlayView: LightspeedCruiseOverlay? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private val edgeWidthPx = 45

    // Status Bar Overlay
    private var statusBarOverlayView: LightspeedStatusBarOverlay? = null
    private lateinit var statusBarWindowParams: WindowManager.LayoutParams

    // Dedicated Notch Pill Overlay
    private var notchOverlayView: LightspeedNotchOverlay? = null
    private lateinit var notchWindowParams: WindowManager.LayoutParams

    // Left Deflector Wing Overlay
    private var leftWingOverlayView: LightspeedLeftWingOverlay? = null
    private lateinit var leftWingWindowParams: WindowManager.LayoutParams

    // Floating Media Scrubber Overlay
    private var mediaScrubberOverlayView: LightspeedMediaScrubberOverlay? = null

    private var systemStateReceiver: BroadcastReceiver? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handleDisplayOrientationChange()
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_section_statusbar") || key.startsWith("pref_macro_action_STATUSBAR"))) {
            updateStatusBarOverlayFromPrefs(prefs)
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
        instance = this

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        serviceInfo = info

        com.sbf.lightspeed.system.LightspeedShortcutManager.purgeCorruptedIcons(this)
        com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        displayManager?.registerDisplayListener(displayListener, handler)

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

        overlayView = LightspeedCruiseOverlay(this)
        windowManager?.addView(overlayView, windowParams)
        overlayView?.post { overlayView?.updateMetricsDimensions() }

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

        leftWingOverlayView = LightspeedLeftWingOverlay(this, this)
        windowManager?.addView(leftWingOverlayView, leftWingWindowParams)
        leftWingOverlayView?.post { leftWingOverlayView?.updateMetricsDimensions() }

        // 3. Initialize Status Bar Overlay Window
        val prefs = defaultPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        setupStatusBarOverlay(prefs)
        setupNotchOverlay()

        // 4. Initialize Back Tap Engine
        com.sbf.lightspeed.system.LightspeedBackTapEngine.init(this)

        // 5. Register System State & Dock Receivers
        registerSystemStateReceiver()
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
        if (!enabled) return

        val screenWidthPx = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density
        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) {
            screenWidthPx
        } else {
            (spanPref * density).toInt().coerceIn((50 * density).toInt(), screenWidthPx)
        }
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val heightPx = (thicknessDp * density).toInt()
        val offsetX = (prefs.getInt("pref_statusbar_offset_x", 0) * density).toInt()
        val offsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).toInt()

        statusBarWindowParams = WindowManager.LayoutParams(
            spanPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offsetX
            y = offsetY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        statusBarOverlayView = LightspeedStatusBarOverlay(this, this)
        windowManager?.addView(statusBarOverlayView, statusBarWindowParams)
    }

    private fun updateStatusBarOverlayFromPrefs(prefs: SharedPreferences) {
        val enabled = prefs.getBoolean("pref_statusbar_enabled", true)
        val screenWidthPx = resources.displayMetrics.widthPixels
        val density = resources.displayMetrics.density

        if (!enabled) {
            statusBarOverlayView?.let {
                windowManager?.removeView(it)
                statusBarOverlayView = null
            }
            return
        }

        val spanPref = prefs.getInt("pref_statusbar_span", 1080)
        val spanPx = if (spanPref >= 1000) {
            screenWidthPx
        } else {
            (spanPref * density).toInt().coerceIn((50 * density).toInt(), screenWidthPx)
        }
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 48).coerceIn(20, 52)
        val heightPx = (thicknessDp * density).toInt()
        val offsetX = (prefs.getInt("pref_statusbar_offset_x", 0) * density).toInt()
        val offsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).toInt()

        if (statusBarOverlayView == null) {
            setupStatusBarOverlay(prefs)
        } else {
            statusBarWindowParams.width = spanPx
            statusBarWindowParams.height = heightPx
            statusBarWindowParams.x = offsetX
            statusBarWindowParams.y = offsetY
            statusBarOverlayView?.invalidate()
            windowManager?.updateViewLayout(statusBarOverlayView, statusBarWindowParams)
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

    fun reloadPreferences() {
        val prefs = defaultPrefs()
        updateStatusBarOverlayFromPrefs(prefs)
        notchOverlayView?.postInvalidate()
        updateSidebarOverlayFromPrefs(prefs)
        updateLeftWingOverlayFromPrefs(prefs)
        com.sbf.lightspeed.system.LightspeedBackTapEngine.reloadPreferences()
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
        return LightspeedKeyEngine.onKeyEvent(this, event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { teardown() }
    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        val prefs = defaultPrefs()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        teardown()
    }

    private fun handleDisplayOrientationChange() {
        val prefs = defaultPrefs()
        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager?.defaultDisplay
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        if (policy == "portrait_only" && isLandscape) {
            setOverlaysVisible(false)
        } else {
            setOverlaysVisible(true)
            resyncOverlayMetrics()
        }
    }

    private fun isSuppressedByOrientation(): Boolean {
        val prefs = defaultPrefs()
        val policy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager?.defaultDisplay
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270
        return policy == "portrait_only" && isLandscape
    }

    private fun setOverlaysVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        overlayView?.visibility = v
        leftWingOverlayView?.visibility = v
        statusBarOverlayView?.visibility = v
        notchOverlayView?.visibility = v
    }

    fun scheduleGeometryResync() {
        handler.postDelayed({ resyncOverlayMetrics() }, 100L)
        handler.postDelayed({ resyncOverlayMetrics() }, 300L)
    }

    private fun resyncOverlayMetrics() {
        if (windowManager == null) return
        overlayView?.updateMetricsDimensions()
        leftWingOverlayView?.updateMetricsDimensions()
        updateStatusBarOverlayFromPrefs(defaultPrefs())
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
                    Intent.ACTION_DREAMING_STARTED, Intent.ACTION_SCREEN_OFF -> {
                        if (hideOnLockAndDock && !LightspeedRefuelingActivity.isActive) {
                            notchOverlayView?.visibility = View.GONE
                            statusBarOverlayView?.visibility = View.GONE
                        }
                    }
                    Intent.ACTION_DREAMING_STOPPED, Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                        if (!isSuppressedByOrientation()) {
                            notchOverlayView?.visibility = View.VISIBLE
                            statusBarOverlayView?.visibility = View.VISIBLE
                            resyncOverlayMetrics()
                        }
                    }
                    Intent.ACTION_POWER_CONNECTED -> {
                        checkPowerConnectedRefuelingTrigger(prefs)
                    }
                }
            }
        }
        try {
            registerReceiver(systemStateReceiver, filter)
        } catch (_: Exception) {}
    }

    private fun checkPowerConnectedRefuelingTrigger(prefs: SharedPreferences) {
        val trigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
        if (trigger == "disabled") return

        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager?.defaultDisplay
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val isLandscape = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270

        if (trigger == "always_charging" || (trigger == "landscape_charging" && isLandscape)) {
            val intent = Intent(this, LightspeedRefuelingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            try { startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun updateNotchWindowBounds(isExpanded: Boolean, targetX: Int, targetY: Int, targetWidth: Int, targetHeight: Int) {
        if (windowManager == null || notchOverlayView == null) return
        notchWindowParams.width = targetWidth
        notchWindowParams.height = targetHeight
        notchWindowParams.x = targetX
        notchWindowParams.y = targetY
        notchWindowParams.gravity = Gravity.TOP or Gravity.START
        try {
            windowManager?.updateViewLayout(notchOverlayView, notchWindowParams)
        } catch (_: Exception) {}
    }

    private fun teardown() {
        LightspeedKeyEngine.reset()
        com.sbf.lightspeed.system.LightspeedBackTapEngine.destroy()
        displayManager?.unregisterDisplayListener(displayListener)
        systemStateReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
            systemStateReceiver = null
        }
        mediaScrubberOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            mediaScrubberOverlayView = null
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
            windowManager?.removeView(it)
            statusBarOverlayView = null
        }
        notchOverlayView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
            notchOverlayView = null
        }
    }
}
