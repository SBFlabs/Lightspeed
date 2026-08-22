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

        val density = resources.displayMetrics.density
        val spanPx = (prefs.getInt("pref_statusbar_span", 1080) * density).toInt()
        val sensitivityPx = (prefs.getInt("pref_statusbar_sensitivity", 40) * density).toInt()
        val offsetX = (prefs.getInt("pref_statusbar_offset_x", 0) * density).toInt()
        val offsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).toInt()

        statusBarWindowParams = WindowManager.LayoutParams(
            spanPx,
            sensitivityPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
        val density = resources.displayMetrics.density

        if (!enabled) {
            statusBarOverlayView?.let {
                windowManager?.removeView(it)
                statusBarOverlayView = null
            }
            return
        }

        val spanPx = (prefs.getInt("pref_statusbar_span", 1080) * density).toInt()
        val sensitivityPx = (prefs.getInt("pref_statusbar_sensitivity", 40) * density).toInt()
        val offsetX = (prefs.getInt("pref_statusbar_offset_x", 0) * density).toInt()
        val offsetY = (prefs.getInt("pref_statusbar_offset_y", 0) * density).toInt()

        if (statusBarOverlayView == null) {
            setupStatusBarOverlay(prefs)
        } else {
            statusBarWindowParams.width = spanPx
            statusBarWindowParams.height = sensitivityPx
            statusBarWindowParams.x = offsetX
            statusBarWindowParams.y = offsetY
            statusBarOverlayView?.invalidate()
            windowManager?.updateViewLayout(statusBarOverlayView, statusBarWindowParams)
        }
    }

    fun updateWindowLayout(expand: Boolean) {
        if (windowManager == null || overlayView == null) return
        windowParams.width = if (expand) WindowManager.LayoutParams.MATCH_PARENT else edgeWidthPx
        windowManager?.updateViewLayout(overlayView, windowParams)
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
