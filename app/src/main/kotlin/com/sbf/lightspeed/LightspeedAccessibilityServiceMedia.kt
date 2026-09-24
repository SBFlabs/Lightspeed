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

    fun LightspeedAccessibilityService.showMediaScrubber() {
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

    fun LightspeedAccessibilityService.hideMediaScrubber() {
        handler.post {
            mediaScrubberOverlayView?.let {
                try {
                    windowManager?.removeView(it)
                } catch (_: Exception) {}
                mediaScrubberOverlayView = null
            }
        }
    }

    fun LightspeedAccessibilityService.updateMediaScrubberProgress() {
        handler.post {
            if (mediaScrubberOverlayView != null) {
                mediaScrubberOverlayView?.updateTrackInfo()
            } else {
                showMediaScrubber()
            }
        }
    }

