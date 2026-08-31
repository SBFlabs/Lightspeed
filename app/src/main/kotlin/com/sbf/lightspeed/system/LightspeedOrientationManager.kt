package com.sbf.lightspeed.system

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface

/**
 * Synthetic Gravity Engine Manager.
 *
 * Strict Resolution Hierarchy:
 * - Priority 1: Runtime Manual Gesture Override (Instant user veto; overrides all guards and app rules).
 * - Priority 2: Protected Context Guardrails (Keyguard, Launcher, and Phone/VoIP calls lock to portrait when no manual gesture override is active).
 * - Priority 3: Per-App Launch Baseline (Enforced on foreground switch; resets transient override on new app entry).
 * - Priority 4: Master Auto-Rotate baseline (Configurable default: Auto-Rotate Off [0° Locked] vs Auto-Rotate On [360° Gyro]).
 *
 * Transient Override Lifetime Boundaries:
 * - Yields/Clears: On app/task switch, screen turn-off / device lock (ACTION_SCREEN_OFF), or incoming/outgoing phone call.
 * - Preserved: During incoming heads-up notifications and when opening system overlays (Central Command, Notification Shade).
 */
object LightspeedOrientationManager {
    private const val TAG = "SyntheticGravityEngine"

    const val ACTION_GRAVITY_RESET = "system:gravity_reset"
    const val ACTION_GRAVITY_TOGGLE_MASTER = "system:gravity_toggle_master"
    const val ACTION_GRAVITY_OVERRIDE_360 = "system:gravity_override_360"
    const val ACTION_GRAVITY_OVERRIDE_LANDSCAPE = "system:gravity_override_landscape"
    const val ACTION_GRAVITY_OVERRIDE_PORTRAIT = "system:gravity_override_portrait"

    // Backward-compatibility aliases
    const val ACTION_TOGGLE_ROTATION = "system:orientation_toggle"
    const val ACTION_FORCE_PORTRAIT = "system:orientation_portrait"
    const val ACTION_FORCE_SENSOR_360 = "system:orientation_sensor_360"
    const val ACTION_SENSOR_PORTRAIT = "system:orientation_sensor_portrait"

    enum class GravityOverrideMode {
        FORCE_PORTRAIT,
        FORCE_LANDSCAPE,
        FORCE_360
    }

    @Volatile
    var manualGestureOverride: GravityOverrideMode? = null
        private set

    @Volatile
    private var lastForegroundPackage: String? = null

    fun canWriteSettings(context: Context): Boolean = LightspeedOrientationEngine.canWriteSettings(context)
    fun getAccelerometerRotation(context: Context): Int = if (LightspeedOrientationEngine.isAutoRotateEnabled(context)) 1 else 0
    fun getUserRotation(context: Context): Int = LightspeedOrientationEngine.getUserRotation(context)

    fun resetGravity(context: Context) {
        Log.i(TAG, "Restoring Default Gravity baseline")
        manualGestureOverride = null
        evaluateGravityCascade(context)
    }

    fun toggleMasterAutoRotate(context: Context) {
        Log.i(TAG, "Toggling Master Auto-Rotate")
        LightspeedOrientationEngine.toggleAutoRotate(context)
        manualGestureOverride = null
        evaluateGravityCascade(context)
    }

    fun overrideTransient360(context: Context) {
        Log.i(TAG, "Engaging transient 360° Gyro override")
        manualGestureOverride = GravityOverrideMode.FORCE_360
        LightspeedOrientationEngine.forceSensor360(context)
    }

    fun overrideTransientLandscape(context: Context) {
        Log.i(TAG, "Engaging transient Landscape override")
        manualGestureOverride = GravityOverrideMode.FORCE_LANDSCAPE
        LightspeedOrientationEngine.forceLandscape(context)
    }

    fun overrideTransientPortrait(context: Context) {
        Log.i(TAG, "Engaging transient Portrait override")
        manualGestureOverride = GravityOverrideMode.FORCE_PORTRAIT
        LightspeedOrientationEngine.forcePortrait(context)
    }

    // Aliases for backward compatibility
    fun toggleRotation(context: Context) = toggleMasterAutoRotate(context)
    fun forcePortrait(context: Context) = overrideTransientPortrait(context)
    fun forceSensor360(context: Context) = overrideTransient360(context)
    fun setSensorPortrait(context: Context) = overrideTransientPortrait(context)

    fun onForegroundPackageChanged(context: Context, newPackage: String?, isLocked: Boolean) {
        if (newPackage.isNullOrBlank()) return
        val isSelf = newPackage == context.packageName || newPackage.contains("com.sbf.lightspeed")
        val isSystemUI = newPackage == "com.android.systemui" || newPackage == "android"

        if (isSelf || isSystemUI) {
            // Transient override is preserved during overlays & heads-up notifications
            return
        }

        if (newPackage != lastForegroundPackage) {
            lastForegroundPackage = newPackage
            // Yields/Clears transient override on app/task switch
            manualGestureOverride = null
        }
        evaluateGravityCascade(context, isLocked = isLocked, foregroundPackage = newPackage)
    }

    fun onScreenOff(context: Context) {
        // Yields/Clears on screen turn-off / device lock
        manualGestureOverride = null
    }

    fun onCallStateChanged(context: Context) {
        // Yields/Clears on phone call state changes
        manualGestureOverride = null
        evaluateGravityCascade(context)
    }

    fun evaluateContextGuardrails(context: Context, isLocked: Boolean, currentPackage: String?) {
        onForegroundPackageChanged(context, currentPackage, isLocked)
    }

    fun evaluateGravityCascade(context: Context, isLocked: Boolean? = null, foregroundPackage: String? = null) {
        val targetPackage = foregroundPackage ?: lastForegroundPackage

        // Priority 1: Runtime Manual Gesture Override (Instant user veto; overrides all guards and app rules)
        manualGestureOverride?.let { override ->
            when (override) {
                GravityOverrideMode.FORCE_PORTRAIT -> LightspeedOrientationEngine.forcePortrait(context)
                GravityOverrideMode.FORCE_LANDSCAPE -> LightspeedOrientationEngine.forceLandscape(context)
                GravityOverrideMode.FORCE_360 -> LightspeedOrientationEngine.forceSensor360(context)
            }
            return
        }

        val prefs = context.defaultPrefs()
        val guardEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ORIENTATION_CONTEXT_GUARD_ENABLED, true)

        // Priority 2: Protected Context Guardrails (Keyguard, Launcher, and Phone/VoIP calls lock to portrait)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isCallActive = audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = isLocked ?: (keyguardManager?.isKeyguardLocked == true)
        val isLauncher = LightspeedOrientationEngine.isDefaultLauncherPackage(context, targetPackage)

        if (guardEnabled && (locked || isLauncher || isCallActive)) {
            LightspeedOrientationEngine.forcePortrait(context)
            return
        }

        // Priority 3: Per-App Launch Baseline (Enforced on foreground switch)
        if (!targetPackage.isNullOrBlank()) {
            val strictPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT)
            val sensorPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT)
            val sensorLandscapeApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE)
            val sensor360Apps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_360)

            when {
                strictPortraitApps.contains(targetPackage) -> {
                    LightspeedOrientationEngine.forcePortrait(context)
                    return
                }
                sensorPortraitApps.contains(targetPackage) -> {
                    LightspeedOrientationEngine.setSensorPortrait(context)
                    return
                }
                sensorLandscapeApps.contains(targetPackage) -> {
                    LightspeedOrientationEngine.forceLandscape(context)
                    return
                }
                sensor360Apps.contains(targetPackage) -> {
                    LightspeedOrientationEngine.forceSensor360(context)
                    return
                }
            }
        }

        // Priority 4: Master Auto-Rotate baseline (Auto-Rotate Off [0° Locked] vs Auto-Rotate On [360° Gyro])
        if (LightspeedOrientationEngine.isAutoRotateEnabled(context)) {
            LightspeedOrientationEngine.forceSensor360(context)
        } else {
            LightspeedOrientationEngine.forcePortrait(context)
        }
    }
}
