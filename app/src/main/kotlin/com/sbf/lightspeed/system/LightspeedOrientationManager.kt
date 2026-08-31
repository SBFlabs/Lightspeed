package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Surface

object LightspeedOrientationManager {
    private const val TAG = "LightspeedOrientation"

    const val ACTION_TOGGLE_ROTATION = "system:orientation_toggle"
    const val ACTION_FORCE_PORTRAIT = "system:orientation_portrait"
    const val ACTION_FORCE_SENSOR_360 = "system:orientation_sensor_360"
    const val ACTION_SENSOR_PORTRAIT = "system:orientation_sensor_portrait"

    private var isContextGuarded = false
    private var preGuardAccelRotation: Int = 1
    private var preGuardUserRotation: Int = Surface.ROTATION_0

    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    private fun writeSystemSetting(context: Context, name: String, value: Int): Boolean {
        if (canWriteSettings(context)) {
            try {
                Settings.System.putInt(context.contentResolver, name, value)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Settings.System: ${e.message}")
            }
        }
        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                ElevatedTaskCloser.execShizuku("settings put system $name $value")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Shizuku: ${e.message}")
            }
        }
        return false
    }

    fun getAccelerometerRotation(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1)
        } catch (_: Exception) {
            1
        }
    }

    fun getUserRotation(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    fun toggleRotation(context: Context) {
        val current = getAccelerometerRotation(context)
        val target = if (current == 1) 0 else 1
        Log.i(TAG, "Toggling auto-rotation: current=$current -> target=$target")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, target)
    }

    fun forcePortrait(context: Context) {
        Log.i(TAG, "Forcing strict portrait orientation (0°)")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
        writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
    }

    fun forceSensor360(context: Context) {
        Log.i(TAG, "Forcing full 360° sensor auto-rotation")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 1)
    }

    fun setSensorPortrait(context: Context) {
        Log.i(TAG, "Applying sensor portrait orientation (blocking landscape)")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
        writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
    }

    /**
     * Contextual Guardrails: Checks if current state requires strict portrait or launcher suppression,
     * and restores user orientation when leaving protected contexts.
     */
    fun evaluateContextGuardrails(context: Context, isLocked: Boolean, currentPackage: String?) {
        val prefs = context.defaultPrefs()
        val guardEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ORIENTATION_CONTEXT_GUARD_ENABLED, true)
        if (!guardEnabled) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inCall = audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

        val isLauncher = isDefaultLauncherPackage(context, currentPackage)

        val shouldGuard = inCall || isLocked || isLauncher

        if (shouldGuard) {
            if (!isContextGuarded) {
                // Record previous settings before applying guardrail
                preGuardAccelRotation = getAccelerometerRotation(context)
                preGuardUserRotation = getUserRotation(context)
                isContextGuarded = true
                Log.d(TAG, "Engaging orientation guardrail (inCall=$inCall, isLocked=$isLocked, isLauncher=$isLauncher). Saved state: accel=$preGuardAccelRotation, user=$preGuardUserRotation")
            }
            // In Call: strict portrait
            if (inCall) {
                forcePortrait(context)
            } else if (isLocked || isLauncher) {
                // Suppress landscape glitching: force auto-rotate off in portrait
                if (getAccelerometerRotation(context) != 0 || getUserRotation(context) != Surface.ROTATION_0) {
                    setSensorPortrait(context)
                }
            }
        } else {
            if (isContextGuarded) {
                Log.d(TAG, "Leaving protected context. Reverting to saved orientation: accel=$preGuardAccelRotation, user=$preGuardUserRotation")
                writeSystemSetting(context, Settings.System.USER_ROTATION, preGuardUserRotation)
                writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, preGuardAccelRotation)
                isContextGuarded = false
            }
        }
    }

    private fun isDefaultLauncherPackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName == pkg
        } catch (_: Exception) {
            false
        }
    }
}
