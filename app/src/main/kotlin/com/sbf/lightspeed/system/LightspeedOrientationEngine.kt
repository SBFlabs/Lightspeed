package com.sbf.lightspeed.system

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Surface

/**
 * System Attitude & Orientation Engine.
 * Manages Master Auto-Rotate, Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE),
 * 2x2 Attitude Mode Bucket assignments, and Context Guardrails.
 */
object LightspeedOrientationEngine {
    private const val TAG = "LightspeedOrientation"

    enum class AttitudeBucket(
        val key: String,
        val title: String,
        val subtitle: String,
        val prefKey: String
    ) {
        STRICT_PORTRAIT(
            "strict_portrait",
            "Strict Portrait",
            "0° Fixed Lock (No Inversion)",
            LightspeedPreferences.KEY_ATTITUDE_BUCKET_STRICT_PORTRAIT
        ),
        SENSOR_PORTRAIT(
            "sensor_portrait",
            "Sensor Portrait",
            "0° & 180° Inverted (Blocks Landscape)",
            LightspeedPreferences.KEY_ATTITUDE_BUCKET_SENSOR_PORTRAIT
        ),
        SENSOR_LANDSCAPE(
            "sensor_landscape",
            "Sensor Landscape",
            "90° & 270° (Emulators / Games)",
            LightspeedPreferences.KEY_ATTITUDE_BUCKET_SENSOR_LANDSCAPE
        ),
        SENSOR_360(
            "sensor_360",
            "Sensor 360°",
            "Unconstrained 4-Way Gyroscope",
            LightspeedPreferences.KEY_ATTITUDE_BUCKET_SENSOR_360
        )
    }

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

    fun isAutoRotateEnabled(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    fun setAutoRotateEnabled(context: Context, enabled: Boolean): Boolean {
        val target = if (enabled) 1 else 0
        Log.i(TAG, "Setting Accelerometer Rotation: $target")
        return writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, target)
    }

    fun toggleAutoRotate(context: Context): Boolean {
        val current = isAutoRotateEnabled(context)
        return setAutoRotateEnabled(context, !current)
    }

    fun isFaceRotateSupported(context: Context): Boolean {
        // Supported on Android 12 (API 31)+ if camera_autorotate exists or is queryable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            Settings.Secure.getInt(context.contentResolver, "camera_autorotate")
            true
        } catch (_: Exception) {
            true
        }
    }

    fun isFaceRotateEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "camera_autorotate", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setFaceRotateEnabled(context: Context, enabled: Boolean): Boolean {
        val target = if (enabled) 1 else 0
        Log.i(TAG, "Setting Face-Oriented Auto-Rotate (camera_autorotate): $target")

        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                ElevatedTaskCloser.execShizuku("settings put secure camera_autorotate $target")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing camera_autorotate via Shizuku: ${e.message}")
            }
        }
        if (ElevatedTaskCloser.isRootActive) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure camera_autorotate $target")).waitFor()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing camera_autorotate via Root: ${e.message}")
            }
        }
        try {
            Settings.Secure.putInt(context.contentResolver, "camera_autorotate", target)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing camera_autorotate via Settings.Secure: ${e.message}")
        }
        return false
    }

    fun getUserRotation(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    fun forcePortrait(context: Context) {
        Log.i(TAG, "Forcing strict portrait orientation (0°)")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
        writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
    }

    fun setSensorPortrait(context: Context) {
        Log.i(TAG, "Applying sensor portrait orientation (blocking landscape)")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
        writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
    }

    fun forceLandscape(context: Context) {
        Log.i(TAG, "Forcing landscape orientation (90°)")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
        writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_90)
    }

    fun forceSensor360(context: Context) {
        Log.i(TAG, "Forcing full 360° sensor auto-rotation")
        writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 1)
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
        if (ElevatedTaskCloser.isRootActive) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $name $value")).waitFor()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Root: ${e.message}")
            }
        }
        return false
    }

    fun getAssignedPackages(context: Context, bucket: AttitudeBucket): Set<String> {
        val prefs = context.defaultPrefs()
        return prefs.getStringSet(bucket.prefKey, emptySet()) ?: emptySet()
    }

    fun setAssignedPackages(context: Context, bucket: AttitudeBucket, packages: Set<String>) {
        val prefs = context.defaultPrefs()
        prefs.edit().putStringSet(bucket.prefKey, packages).apply()
    }

    /**
     * Contextual Guardrails & App Rules Evaluation.
     */
    fun evaluateContextAndAppRules(context: Context, isLocked: Boolean, currentPackage: String?) {
        val prefs = context.defaultPrefs()
        val guardEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ORIENTATION_CONTEXT_GUARD_ENABLED, true)
        if (!guardEnabled) return

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val inCall = audioManager?.mode == AudioManager.MODE_IN_CALL || audioManager?.mode == AudioManager.MODE_IN_COMMUNICATION

        val isLauncher = isDefaultLauncherPackage(context, currentPackage)

        // Check assigned buckets
        val strictPortraitApps = getAssignedPackages(context, AttitudeBucket.STRICT_PORTRAIT)
        val sensorPortraitApps = getAssignedPackages(context, AttitudeBucket.SENSOR_PORTRAIT)
        val sensorLandscapeApps = getAssignedPackages(context, AttitudeBucket.SENSOR_LANDSCAPE)
        val sensor360Apps = getAssignedPackages(context, AttitudeBucket.SENSOR_360)

        val isStrictApp = currentPackage != null && strictPortraitApps.contains(currentPackage)
        val isSensorPortApp = currentPackage != null && sensorPortraitApps.contains(currentPackage)
        val isLandscapeApp = currentPackage != null && sensorLandscapeApps.contains(currentPackage)
        val is360App = currentPackage != null && sensor360Apps.contains(currentPackage)

        val shouldGuard = inCall || isLocked || isLauncher || isStrictApp || isSensorPortApp || isLandscapeApp || is360App

        if (shouldGuard) {
            if (!isContextGuarded) {
                preGuardAccelRotation = if (isAutoRotateEnabled(context)) 1 else 0
                preGuardUserRotation = getUserRotation(context)
                isContextGuarded = true
                Log.d(TAG, "Engaged orientation guardrail. Saved: accel=$preGuardAccelRotation, user=$preGuardUserRotation")
            }

            when {
                inCall || isStrictApp -> forcePortrait(context)
                isLocked || isLauncher || isSensorPortApp -> setSensorPortrait(context)
                isLandscapeApp -> forceLandscape(context)
                is360App -> forceSensor360(context)
            }
        } else {
            if (isContextGuarded) {
                Log.d(TAG, "Leaving guarded context. Restoring: accel=$preGuardAccelRotation, user=$preGuardUserRotation")
                writeSystemSetting(context, Settings.System.USER_ROTATION, preGuardUserRotation)
                writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, preGuardAccelRotation)
                isContextGuarded = false
            }
        }
    }

    fun isDefaultLauncherPackage(context: Context, pkg: String?): Boolean {
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

    fun registerObserver(
        context: Context,
        onAutoRotateChanged: (Boolean) -> Unit,
        onFaceRotateChanged: (Boolean) -> Unit
    ): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                onAutoRotateChanged(isAutoRotateEnabled(context))
                onFaceRotateChanged(isFaceRotateEnabled(context))
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
                false,
                observer
            )
            context.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("camera_autorotate"),
                false,
                observer
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed registering ContentObserver", e)
        }
        return observer
    }
}
