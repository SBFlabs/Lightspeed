package com.sbf.lightspeed.system

import android.app.KeyguardManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface
import android.content.pm.ActivityInfo
import android.hardware.SensorManager
import android.view.OrientationEventListener
import com.sbf.lightspeed.LightspeedAccessibilityService

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
    const val ACTION_GRAVITY_OVERRIDE_SENSOR_PORTRAIT = "system:gravity_override_sensor_portrait"

    // Backward-compatibility aliases
    const val ACTION_TOGGLE_ROTATION = "system:orientation_toggle"
    const val ACTION_FORCE_PORTRAIT = "system:orientation_portrait"
    const val ACTION_FORCE_SENSOR_360 = "system:orientation_sensor_360"
    const val ACTION_SENSOR_PORTRAIT = "system:orientation_sensor_portrait"

    enum class GravityOverrideMode {
        FORCE_PORTRAIT,
        FORCE_LANDSCAPE,
        FORCE_360,
        FORCE_SENSOR_PORTRAIT
    }

    @Volatile
    var manualGestureOverride: GravityOverrideMode? = null
        private set

    @Volatile
    private var lastForegroundPackage: String? = null

    @Volatile
    private var isSensorPortraitDriverActive = false
    private var sensorPortraitListener: OrientationEventListener? = null
    @Volatile
    private var currentSensorPortraitOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

    fun startActiveSensorPortraitDriver(context: Context) {
        if (isSensorPortraitDriverActive) return
        isSensorPortraitDriverActive = true

        val appContext = context.applicationContext
        if (sensorPortraitListener == null) {
            sensorPortraitListener = object : OrientationEventListener(appContext, SensorManager.SENSOR_DELAY_NORMAL) {
                override fun onOrientationChanged(orientation: Int) {
                    if (orientation == ORIENTATION_UNKNOWN || !isSensorPortraitDriverActive) return

                    // Dynamic Gyro Inversion:
                    // Upright 0° cone: 315°..360° or 0°..45°
                    // Inverted 180° cone: 135°..225°
                    // Landscape angles (45°..135° and 225°..315°): ignored to strictly enforce portrait
                    val target = when {
                        orientation >= 315 || orientation <= 45 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        orientation in 135..225 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                        else -> null
                    }

                    if (target != null && target != currentSensorPortraitOrientation) {
                        currentSensorPortraitOrientation = target
                        Log.i(TAG, "Active Sensor Portrait dynamic flip: $target (angle=$orientation°)")
                        val mode = LightspeedPreferences.getGravityEnforcementEngine(appContext)
                        if (mode != LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR) {
                            val sysRot = if (target == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Surface.ROTATION_0 else Surface.ROTATION_180
                            LightspeedOrientationEngine.writeSystemSetting(appContext, Settings.System.USER_ROTATION, sysRot)
                        }
                        if (mode != LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS) {
                            LightspeedAccessibilityService.instance?.updateForcedOrientation(target)
                        }
                    }
                }
            }
        }
        try {
            sensorPortraitListener?.enable()
            val mode = LightspeedPreferences.getGravityEnforcementEngine(appContext)
            if (mode != LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR) {
                val sysRot = if (currentSensorPortraitOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) Surface.ROTATION_0 else Surface.ROTATION_180
                LightspeedOrientationEngine.writeSystemSetting(appContext, Settings.System.USER_ROTATION, sysRot)
            }
            if (mode != LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS) {
                LightspeedAccessibilityService.instance?.updateForcedOrientation(currentSensorPortraitOrientation)
            }
            Log.i(TAG, "Enabled Active Sensor Portrait Driver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable OrientationEventListener", e)
        }
    }

    fun stopActiveSensorPortraitDriver() {
        if (!isSensorPortraitDriverActive) return
        isSensorPortraitDriverActive = false
        try {
            sensorPortraitListener?.disable()
            Log.i(TAG, "Disabled Active Sensor Portrait Driver")
        } catch (_: Exception) {}
    }

    fun canWriteSettings(context: Context): Boolean = LightspeedOrientationEngine.canWriteSettings(context)
    fun getAccelerometerRotation(context: Context): Int = if (LightspeedOrientationEngine.isAutoRotateEnabled(context)) 1 else 0
    fun getUserRotation(context: Context): Int = LightspeedOrientationEngine.getUserRotation(context)

    fun resetGravity(context: Context) {
        Log.i(TAG, "Restoring Default Gravity baseline")
        manualGestureOverride = null
        LightspeedOrientationEngine.releaseForcedOrientation(context)
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
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
        return expiration != LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_DISABLED
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
        if (manualGestureOverride == GravityOverrideMode.FORCE_360) {
            Log.i(TAG, "Disengaging transient 360° Gyro override")
            manualGestureOverride = null
            evaluateGravityCascade(context)
            val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "360° GYRO DISENGAGED",
                durationMs = 1500L
            )
            if (!shown) {
                android.widget.Toast.makeText(context, "360° Gyro Disengaged", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        Log.i(TAG, "Engaging transient 360° Gyro override")
        manualGestureOverride = GravityOverrideMode.FORCE_360
        LightspeedOrientationEngine.forceSensor360(context)
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED 360° GYRO",
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "360° Gyro Forced", android.widget.Toast.LENGTH_SHORT).show()
        }
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
        if (manualGestureOverride == GravityOverrideMode.FORCE_LANDSCAPE) {
            Log.i(TAG, "Disengaging transient Landscape override")
            manualGestureOverride = null
            evaluateGravityCascade(context)
            val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "LANDSCAPE DISENGAGED",
                durationMs = 1500L
            )
            if (!shown) {
                android.widget.Toast.makeText(context, "Landscape Disengaged", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        Log.i(TAG, "Engaging transient Landscape override")
        manualGestureOverride = GravityOverrideMode.FORCE_LANDSCAPE
        LightspeedOrientationEngine.forceLandscape(context)
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED LANDSCAPE (90°)",
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "Landscape Forced", android.widget.Toast.LENGTH_SHORT).show()
        }
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
        if (manualGestureOverride == GravityOverrideMode.FORCE_PORTRAIT) {
            Log.i(TAG, "Disengaging transient Portrait override")
            manualGestureOverride = null
            evaluateGravityCascade(context)
            val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "PORTRAIT DISENGAGED",
                durationMs = 1500L
            )
            if (!shown) {
                android.widget.Toast.makeText(context, "Portrait Disengaged", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        Log.i(TAG, "Engaging transient Portrait override")
        manualGestureOverride = GravityOverrideMode.FORCE_PORTRAIT
        LightspeedOrientationEngine.forcePortrait(context)
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "FORCED PORTRAIT (0°)",
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "Portrait Forced", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun overrideTransientSensorPortrait(context: Context) {
        if (!isActionOverrideAllowed(context)) {
            com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "ACTIONS DISABLED (BUCKETS ONLY)",
                durationMs = 1500L
            )
            return
        }
        if (manualGestureOverride == GravityOverrideMode.FORCE_SENSOR_PORTRAIT) {
            Log.i(TAG, "Disengaging transient Sensor Portrait override")
            manualGestureOverride = null
            stopActiveSensorPortraitDriver()
            evaluateGravityCascade(context)
            val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                title = "GRAVITY OVERRIDE",
                value = "SENSOR PORTRAIT DISENGAGED",
                durationMs = 1500L
            )
            if (!shown) {
                android.widget.Toast.makeText(context, "Sensor Portrait Disengaged", android.widget.Toast.LENGTH_SHORT).show()
            }
            return
        }
        Log.i(TAG, "Engaging transient Sensor Portrait override")
        manualGestureOverride = GravityOverrideMode.FORCE_SENSOR_PORTRAIT
        startActiveSensorPortraitDriver(context)
        val shown = com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
            title = "GRAVITY OVERRIDE",
            value = "SENSOR PORTRAIT (0°/180°)",
            durationMs = 1800L
        )
        if (!shown) {
            android.widget.Toast.makeText(context, "Sensor Portrait Forced", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Aliases for backward compatibility
    fun toggleRotation(context: Context) = toggleNativeAutoRotate(context)
    fun forcePortrait(context: Context) = overrideTransientPortrait(context)
    fun forceSensor360(context: Context) = overrideTransient360(context)
    fun setSensorPortrait(context: Context) = overrideTransientSensorPortrait(context)

    fun onExternalAutoRotateChanged(context: Context, isEnabled: Boolean) {
        Log.i(TAG, "External Auto-Rotate change synced: $isEnabled")
        LightspeedOrientationEngine.setMasterAutoRotateBaseline(context, isEnabled)
    }

    fun onForegroundPackageChanged(context: Context, newPackage: String?, isLocked: Boolean) {
        var targetPkg = newPackage
        val isLauncher = LightspeedOrientationEngine.isLauncherPackage(context, targetPkg)
        if (!isLauncher && (targetPkg == null || (targetPkg == "com.android.systemui" && !isLocked))) {
            targetPkg = if (!lastForegroundPackage.isNullOrBlank() && lastForegroundPackage != "com.android.systemui" && lastForegroundPackage != "android") {
                lastForegroundPackage
            } else {
                LightspeedOrientationEngine.getDefaultLauncherPackage(context)
            }
        }
        val isSelf = targetPkg != null && (targetPkg == context.packageName || targetPkg.contains("com.sbf.lightspeed"))

        if (isSelf) return

        if (!targetPkg.isNullOrBlank() && targetPkg != lastForegroundPackage) {
            lastForegroundPackage = targetPkg
            val prefs = context.defaultPrefs()
            val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
            if (expiration == LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_APP_SWITCH) {
                manualGestureOverride = null
            }
        }
        evaluateGravityCascade(context, isLocked = isLocked, foregroundPackage = targetPkg)
    }

    fun onScreenOff(context: Context) {
        LightspeedOrientationEngine.releaseForcedOrientation(context)
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
        if (expiration != LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT) {
            manualGestureOverride = null
        }
    }

    fun onDeviceUnlocked(context: Context) {
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
        if (expiration != LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT) {
            Log.i(TAG, "Device unlocked - clearing transient lockscreen manual overrides")
            manualGestureOverride = null
        }
    }

    fun onCallStateChanged(context: Context) {
        val prefs = context.defaultPrefs()
        val expiration = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
        if (expiration == LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_APP_SWITCH) {
            manualGestureOverride = null
        }
        evaluateGravityCascade(context)
    }

    fun evaluateContextGuardrails(context: Context, isLocked: Boolean, currentPackage: String?) {
        onForegroundPackageChanged(context, currentPackage, isLocked)
    }

    fun killConflictingTools(context: Context) {
        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                ElevatedTaskCloser.execShizuku("am force-stop com.arlosoft.macrodroid")
            } catch (_: Exception) {}
        }
        if (ElevatedTaskCloser.isRootActive) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop com.arlosoft.macrodroid")).waitFor()
            } catch (_: Exception) {}
        }
    }

    fun evaluateGravityCascade(context: Context, isLocked: Boolean? = null, foregroundPackage: String? = null) {
        val prefs = context.defaultPrefs()

        // Priority 1: Runtime Manual Gesture Override (Instant user veto; ALWAYS active & independent of automation toggle)
        if (isActionOverrideAllowed(context)) {
            manualGestureOverride?.let { override ->
                when (override) {
                    GravityOverrideMode.FORCE_PORTRAIT -> {
                        LightspeedOrientationEngine.forcePortrait(context)
                    }
                    GravityOverrideMode.FORCE_LANDSCAPE -> {
                        LightspeedOrientationEngine.forceLandscape(context)
                    }
                    GravityOverrideMode.FORCE_360 -> {
                        LightspeedOrientationEngine.forceSensor360(context)
                    }
                    GravityOverrideMode.FORCE_SENSOR_PORTRAIT -> {
                        LightspeedOrientationEngine.setSensorPortrait(context)
                    }
                }
                return
            }
        }

        // Master Automation Interlock: If Synthetic Gravity Engine (per-app buckets) is disabled,
        // bypass per-app evaluation and restore the user's Master Auto-Rotate baseline.
        if (!prefs.getBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, false)) {
            LightspeedOrientationEngine.releaseForcedOrientation(context)
            return
        }

        val targetPackage = foregroundPackage ?: lastForegroundPackage
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val locked = isLocked ?: (keyguardManager?.isKeyguardLocked == true)

        val strictPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT)
        val sensorPortraitApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT)
        val sensorLandscapeApps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE)
        val sensor360Apps = LightspeedOrientationEngine.getAssignedPackages(context, LightspeedOrientationEngine.AttitudeBucket.SENSOR_360)

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
                sensor360Apps.contains(lockscreenToken) -> {
                    LightspeedOrientationEngine.forceSensor360(context)
                    return
                }
            }
        }

        // Priority 3: Per-App Launch Rules (Enforced on foreground switch)
        val isTargetLauncher = LightspeedOrientationEngine.isLauncherPackage(context, targetPackage)
        val resolvedPackage = if (!locked && !isTargetLauncher && (targetPackage.isNullOrBlank() || targetPackage == "com.android.systemui" || targetPackage == "android")) {
            LightspeedOrientationEngine.getDefaultLauncherPackage(context)
        } else {
            targetPackage
        }
        val isResolvedLauncher = isTargetLauncher || LightspeedOrientationEngine.isLauncherPackage(context, resolvedPackage)

        fun inBucket(bucketSet: Set<String>): Boolean {
            if (resolvedPackage != null && bucketSet.contains(resolvedPackage)) return true
            if (targetPackage != null && bucketSet.contains(targetPackage)) return true
            if (isResolvedLauncher) {
                val allLaunchers = LightspeedOrientationEngine.getLauncherPackages(context)
                if (bucketSet.any { it in allLaunchers || it in LightspeedOrientationEngine.KNOWN_TRANSSION_LAUNCHERS }) return true
                if (bucketSet.any { it.contains("launcher", ignoreCase = true) || it.contains("Launcher") }) return true
            }
            return false
        }

        if (!resolvedPackage.isNullOrBlank()) {
            when {
                inBucket(strictPortraitApps) -> {
                    LightspeedOrientationEngine.forcePortrait(context)
                    return
                }
                inBucket(sensorPortraitApps) -> {
                    LightspeedOrientationEngine.setSensorPortrait(context)
                    return
                }
                inBucket(sensorLandscapeApps) -> {
                    LightspeedOrientationEngine.forceLandscape(context)
                    return
                }
                inBucket(sensor360Apps) -> {
                    LightspeedOrientationEngine.forceSensor360(context)
                    return
                }
            }
        }

        // Priority 4: User's Master Auto-Rotate (Fallback for unassigned apps / native)
        LightspeedOrientationEngine.releaseForcedOrientation(context)
    }
}
