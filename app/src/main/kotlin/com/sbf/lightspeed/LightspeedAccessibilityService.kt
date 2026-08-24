package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class LightspeedAccessibilityService : AccessibilityService() {

    companion object {
        var instance: LightspeedAccessibilityService? = null
            private set
    }

    private var windowManager: WindowManager? = null

    // Edge Sidebar Overlay
    private var overlayView: LightspeedCruiseOverlay? = null
    private lateinit var windowParams: WindowManager.LayoutParams
    private val edgeWidthPx = 45

    // Status Bar Overlay
    private var statusBarOverlayView: LightspeedStatusBarOverlay? = null
    private lateinit var statusBarWindowParams: WindowManager.LayoutParams

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_macro_action_STATUSBAR"))) {
            updateStatusBarOverlayFromPrefs(prefs)
        }
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_"))) {
            updateSidebarOverlayFromPrefs(prefs)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

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

        // 2. Initialize Status Bar Overlay Window
        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        setupStatusBarOverlay(prefs)
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
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 80)
        val heightPx = (thicknessDp * density).toInt().coerceIn((20 * density).toInt(), (300 * density).toInt())
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
        val thicknessDp = prefs.getInt("pref_statusbar_thickness", 80)
        val heightPx = (thicknessDp * density).toInt().coerceIn((20 * density).toInt(), (300 * density).toInt())
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
        val isPreview = prefs.getBoolean("pref_sidebar_preview", false)
        val density = resources.displayMetrics.density
        val maxTouchWidthDp = maxOf(
            prefs.getInt("pref_sidebar_center_touch_width", 40),
            prefs.getInt("pref_sidebar_top_touch_width", 40),
            prefs.getInt("pref_sidebar_bottom_touch_width", 40)
        )
        val targetWidth = if (isPreview) {
            (maxTouchWidthDp * density * 2.5f).toInt().coerceAtLeast((150 * density).toInt())
        } else {
            edgeWidthPx
        }
        windowParams.width = targetWidth
        overlayView?.updateMetricsDimensions()
        overlayView?.invalidate()
        windowManager?.updateViewLayout(overlayView, windowParams)
    }

    fun reloadPreferences() {
        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
        updateStatusBarOverlayFromPrefs(prefs)
        updateSidebarOverlayFromPrefs(prefs)
    }

    fun updateWindowLayout(expand: Boolean) {
        if (windowManager == null || overlayView == null) return
        val isPreview = getSharedPreferences("default", Context.MODE_PRIVATE).getBoolean("pref_sidebar_preview", false)
        val density = resources.displayMetrics.density
        val restingWidth = if (isPreview) (150 * density).toInt() else edgeWidthPx
        windowParams.width = if (expand) WindowManager.LayoutParams.MATCH_PARENT else restingWidth
        windowManager?.updateViewLayout(overlayView, windowParams)
    }

    fun reopenCockpitHangar(setIndex: Int = -1) {
        overlayView?.post {
            overlayView?.openHangarDirectly(setIndex)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { teardown() }
    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        teardown()
    }

    private fun teardown() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        statusBarOverlayView?.let {
            windowManager?.removeView(it)
            statusBarOverlayView = null
        }
    }
}
