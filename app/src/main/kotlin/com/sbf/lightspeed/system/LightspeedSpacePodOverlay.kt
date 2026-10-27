package com.sbf.lightspeed.system

import android.content.Context
import android.util.Log

/**
 * Tactical Space Pods State & Service Registry
 *
 * Space Pods function as multi-gesture Quick Settings Tiles, Home Screen Shortcuts,
 * 1x1 Widget Pods, Stackable Widgets, and Resizable Home Screen Folders.
 * The legacy floating bubble overlay has been replaced with system-level Pod components.
 */
object LightspeedSpacePodOverlay {

    private const val TAG = "SpacePods"

    @Volatile
    var isAutoClickerActive = false
        private set

    fun init(context: Context) {
        // Safe no-op retained for backwards compatibility with accessibility service init
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.defaultPrefs().edit().putBoolean(LightspeedPreferences.KEY_SPACE_PODS_ENABLED, enabled).apply()
        Log.i(TAG, "Space Pods active status set to: $enabled")
    }

    fun show(context: Context) {
        // Safe no-op: Space Pods are hosted natively as QS Tiles and Widgets
    }

    fun hide() {
        stopAutoClicker()
    }

    fun toggleAutoClicker(context: Context) {
        if (isAutoClickerActive) {
            stopAutoClicker()
        } else {
            startAutoClicker(context)
        }
    }

    fun startAutoClicker(context: Context) {
        isAutoClickerActive = true
        Log.i(TAG, "Auto-clicker enabled")
    }

    fun stopAutoClicker() {
        isAutoClickerActive = false
    }
}
