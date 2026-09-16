package com.sbf.lightspeed

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.Surface
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

internal fun LightspeedAccessibilityService.handleDisplayOrientationChange() {
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

internal fun LightspeedAccessibilityService.registerSystemStateReceiver() {
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
                    com.sbf.lightspeed.system.LightspeedOrientationManager.onScreenOff(this@registerSystemStateReceiver)
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

                    com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@registerSystemStateReceiver, isLocked, null)

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

                    com.sbf.lightspeed.system.LightspeedOrientationManager.onDeviceUnlocked(this@registerSystemStateReceiver)

                    val activePkg = rootInActiveWindow?.packageName?.toString()
                        ?: com.sbf.lightspeed.system.LightspeedOrientationEngine.getDefaultLauncherPackage(this@registerSystemStateReceiver)
                    com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@registerSystemStateReceiver, isLocked, activePkg)

                    // Re-evaluate shortly after unlock to ensure keyguard dismiss animation settled and top window is captured
                    handler.postDelayed({
                        val currentKm = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        val lockedNow = currentKm?.isKeyguardLocked == true
                        val postPkg = rootInActiveWindow?.packageName?.toString()
                            ?: com.sbf.lightspeed.system.LightspeedOrientationEngine.getDefaultLauncherPackage(this@registerSystemStateReceiver)
                        com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this@registerSystemStateReceiver, lockedNow, postPkg)
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

internal fun LightspeedAccessibilityService.teardown() {
    LightspeedKeyEngine.reset()
    com.sbf.lightspeed.system.LightspeedBackTapEngine.destroy()
    com.sbf.lightspeed.system.LightspeedOrientationManager.stopActiveSensorPortraitDriver()
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
        try { windowManager?.removeView(it) } catch (_: Exception) {}
        overlayView = null
    }
    leftWingOverlayView?.let {
        try { windowManager?.removeView(it) } catch (_: Exception) {}
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
