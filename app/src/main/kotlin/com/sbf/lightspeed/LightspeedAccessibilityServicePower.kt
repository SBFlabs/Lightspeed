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

    internal fun LightspeedAccessibilityService.isDeviceCharging(): Boolean {
        return try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            false
        }
    }

    internal fun LightspeedAccessibilityService.checkPowerConnectedRefuelingTrigger(prefs: SharedPreferences) {
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

    internal fun LightspeedAccessibilityService.checkScreenOffRefuelingTrigger(prefs: SharedPreferences) {
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

    internal fun LightspeedAccessibilityService.launchRefuelingActivity() {
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

