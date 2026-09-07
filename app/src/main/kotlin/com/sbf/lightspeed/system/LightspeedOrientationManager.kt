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

    const val ACTION_AUTO_ROTATE_TOGGLE = "system:auto_rotate_toggle"
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
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY RESTORED",
            value = "DEFAULT BASELINE",
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "Gravity Restored", android.widget.Toast.LENGTH_SHORT).show()
        }
        evaluateGravityCascade(context)
    }

    fun toggleNativeAutoRotate(context: Context) {
        if (!LightspeedOrientationEngine.hasPermission(context)) {
            LightspeedOrientationEngine.requestWriteSettingsPermission(context)
            return
        }

        val currentBaseline = LightspeedOrientationEngine.getMasterAutoRotateBaseline(context)
        val newBaseline = !currentBaseline
        Log.i(TAG, "Toggling native auto-rotate baseline: $currentBaseline -> $newBaseline")
        LightspeedOrientationEngine.setMasterAutoRotateBaseline(context, newBaseline)

        // Clear transient manual override
        manualGestureOverride = null

        // Apply immediately to system (Pure Native Android Auto-Rotate)
        if (newBaseline) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
            LightspeedOrientationEngine.forcePortrait(context)
        }

        // Avionics HUD feedback
        val label = if (newBaseline) "AUTO-ROTATE ON" else "AUTO-ROTATE OFF (LOCKED)"
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "AUTO-ROTATE",
            value = label,
            stepIndex = if (newBaseline) 1 else 0,
            totalSteps = 2,
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "Auto-Rotate: $label", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleMasterAutoRotate(context: Context) {
        toggleNativeAutoRotate(context)
    }

    fun isActionOverrideAllowed(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, "until_app_switch") ?: "until_app_switch"
        return expiration != "disabled"
    }

    fun overrideTransient360(context: Context) {
        if (!isActionOverrideAllowed(context)) {
            com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "ACTIONS DISABLED (BUCKETS ONLY)",
                durationMs = 1500L
            )
            return
        }
        Log.i(TAG, "Engaging transient 360° Gyro override")
        manualGestureOverride = GravityOverrideMode.FORCE_360
        LightspeedOrientationEngine.forceSensor360(context)
        com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED 360° GYRO",
            durationMs = 1800L
        )
    }

    fun overrideTransientLandscape(context: Context) {
        if (!isActionOverrideAllowed(context)) {
            com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "ACTIONS DISABLED (BUCKETS ONLY)",
                durationMs = 1500L
            )
            return
        }
        Log.i(TAG, "Engaging transient Landscape override")
        manualGestureOverride = GravityOverrideMode.FORCE_LANDSCAPE
        LightspeedOrientationEngine.forceLandscape(context)
        com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED LANDSCAPE (90°)",
            durationMs = 1800L
        )
    }

    fun overrideTransientPortrait(context: Context) {
        if (!isActionOverrideAllowed(context)) {
            com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "ACTIONS DISABLED (BUCKETS ONLY)",
                durationMs = 1500L
            )
            return
        }
        Log.i(TAG, "Engaging transient Portrait override")
        manualGestureOverride = GravityOverrideMode.FORCE_PORTRAIT
        LightspeedOrientationEngine.forcePortrait(context)
        com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED PORTRAIT (0°)",
            durationMs = 1800L
        )
    }

    // Aliases for backward compatibility
    fun toggleRotation(context: Context) = toggleNativeAutoRotate(context)
    fun forcePortrait(context: Context) = overrideTransientPortrait(context)
    fun forceSensor360(context: Context) = overrideTransient360(context)
    fun setSensorPortrait(context: Context) = overrideTransientPortrait(context)

    fun onExternalAutoRotateChanged(context: Context, isEnabled: Boolean) {
        Log.i(TAG, "External Auto-Rotate change synced: $isEnabled")
        LightspeedOrientationEngine.setMasterAutoRotateBaseline(context, isEnabled)
    }

    fun onForegroundPackageChanged(context: Context, newPackage: String?, isLocked: Boolean) {
        val targetPkg = newPackage ?: lastForegroundPackage
        val isSelf = targetPkg != null && (targetPkg == context.packageName || targetPkg.contains("com.sbf.lightspeed"))
        val isSystemUI = targetPkg == "com.android.systemui" || targetPkg == "android"

        if (isSelf) return
        if (isSystemUI && !isLocked) return

        if (!targetPkg.isNullOrBlank() && targetPkg != lastForegroundPackage) {
            lastForegroundPackage = targetPkg
            val prefs = context.defaultPrefs()
            val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, "until_app_switch") ?: "until_app_switch"
            if (expiration == "until_app_switch") {
                manualGestureOverride = null
            }
        }
        evaluateGravityCascade(context, isLocked = isLocked, foregroundPackage = targetPkg)
    }

    fun onScreenOff(context: Context) {
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, "until_app_switch") ?: "until_app_switch"
        if (expiration != "persistent") {
            manualGestureOverride = null
        }
    }

    fun onCallStateChanged(context: Context) {
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, "until_app_switch") ?: "until_app_switch"
        if (expiration == "until_app_switch") {
            manualGestureOverride = null
        }
        evaluateGravityCascade(context)
    }

    fun evaluateContextGuardrails(context: Context, isLocked: Boolean, currentPackage: String?) {
        onForegroundPackageChanged(context, currentPackage, isLocked)
    }

    fun evaluateGravityCascade(context: Context, isLocked: Boolean? = null, foregroundPackage: String? = null) {
        val targetPackage = foregroundPackage ?: lastForegroundPackage

        // Priority 1: Runtime Manual Gesture Override (Instant user veto if enabled)
        if (isActionOverrideAllowed(context)) {
            manualGestureOverride?.let { override ->
                when (override) {
                    GravityOverrideMode.FORCE_PORTRAIT -> LightspeedOrientationEngine.forcePortrait(context)
                    GravityOverrideMode.FORCE_LANDSCAPE -> LightspeedOrientationEngine.forceLandscape(context)
                    GravityOverrideMode.FORCE_360 -> LightspeedOrientationEngine.forceSensor360(context)
                }
                return
            }
        }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = isLocked ?: (keyguardManager?.isKeyguardLocked == true)

        val strictPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT)
        val sensorPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT)
        val sensorLandscapeApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE)

        // Priority 2: Keyguard Lock Screen (Explicit assignment if user configured keyguard:lockscreen)
        if (locked) {
            val lockscreenToken = "keyguard:lockscreen"
            when {
                strictPortraitApps.contains(lockscreenToken) -> {
                    LightspeedOrientationEngine.forcePortrait(context)
                    return
                }
                sensorPortraitApps.contains(lockscreenToken) -> {
                    LightspeedOrientationEngine.setSensorPortrait(context)
                    return
                }
                sensorLandscapeApps.contains(lockscreenToken) -> {
                    LightspeedOrientationEngine.forceLandscape(context)
                    return
                }
            }
        }

        // Priority 3: Per-App Launch Rules (Enforced on foreground switch)
        if (!targetPackage.isNullOrBlank()) {
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
            }
        }

        // Priority 4: User's Master Auto-Rotate (Fallback for unassigned apps)
        val masterAutoRotate = LightspeedOrientationEngine.getMasterAutoRotateBaseline(context)
        if (masterAutoRotate) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.forcePortrait(context)
        }
    }
}
