package com.sbf.lightspeed.system
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import com.sbf.lightspeed.LightspeedAccessibilityService
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

    @Volatile
    private var lastInternalWriteTime = 0L
    private var lastPermissionPromptTime = 0L

    fun isRecentInternalWrite(): Boolean {
        return android.os.SystemClock.uptimeMillis() - lastInternalWriteTime < 1000L
    }

    fun recordInternalWrite() {
        lastInternalWriteTime = android.os.SystemClock.uptimeMillis()
    }

    fun canWriteSettings(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun hasPermission(context: Context): Boolean {
        return canWriteSettings(context) || ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive
    }

    fun requestWriteSettingsPermission(context: Context) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastPermissionPromptTime < 3000L) return
        lastPermissionPromptTime = now
        try {
            val intent = android.content.Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            android.widget.Toast.makeText(
                context,
                "Lightspeed requires 'Modify system settings' permission to control screen rotation",
                android.widget.Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch ACTION_MANAGE_WRITE_SETTINGS", e)
        }
    }

    fun getMasterAutoRotateBaseline(context: Context): Boolean {
        val prefs = context.defaultPrefs()
        if (!prefs.contains(LightspeedPreferences.KEY_SAVED_ACCEL_ROTATION)) {
            val systemDefault = isAutoRotateEnabled(context)
            prefs.edit().putBoolean(LightspeedPreferences.KEY_SAVED_ACCEL_ROTATION, systemDefault).apply()
            return systemDefault
        }
        return prefs.getBoolean(LightspeedPreferences.KEY_SAVED_ACCEL_ROTATION, true)
    }

    fun setMasterAutoRotateBaseline(context: Context, enabled: Boolean) {
        val prefs = context.defaultPrefs()
        prefs.edit().putBoolean(LightspeedPreferences.KEY_SAVED_ACCEL_ROTATION, enabled).apply()
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
        setMasterAutoRotateBaseline(context, enabled)
        return writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, target)
    }

    fun toggleAutoRotate(context: Context): Boolean {
        val current = getMasterAutoRotateBaseline(context)
        return setAutoRotateEnabled(context, !current)
    }

    fun isFaceRotateSupported(context: Context): Boolean {
        // Supported on Android 12 (API 31)+ if camera_autorotate exists or is queryable
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            Settings.Secure.getInt(context.contentResolver, "camera_autorotate")
            true
        } catch (_: Settings.SettingNotFoundException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun isFaceRotateEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "camera_autorotate", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun canWriteSecureSettings(context: Context): Boolean {
        return context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    fun setFaceRotateEnabled(context: Context, enabled: Boolean): Boolean {
        val target = if (enabled) 1 else 0
        Log.i(TAG, "Setting Face-Oriented Auto-Rotate (camera_autorotate): $target")

        if (canWriteSecureSettings(context)) {
            try {
                Settings.Secure.putInt(context.contentResolver, "camera_autorotate", target)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing camera_autorotate via Settings.Secure with permission: ${e.message}")
            }
        }

        if (ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                if (ElevatedTaskCloser.isShizukuActive) {
                    try {
                        ElevatedTaskCloser.execShizuku("settings put secure camera_autorotate $target")
                        return@launch
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing camera_autorotate via Shizuku: ${e.message}")
                    }
                }
                if (ElevatedTaskCloser.isRootActive) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure camera_autorotate $target")).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing camera_autorotate via Root: ${e.message}")
                    }
                }
            }
            return true
        }
        try {
            Settings.Secure.putInt(context.contentResolver, "camera_autorotate", target)
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing camera_autorotate via Settings.Secure: ${e.message}")
        }
        return false
    }

    fun openAutoRotateSettings(context: Context) {
        try {
            val intent = Intent("android.settings.AUTO_ROTATE_SETTINGS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    fun getUserRotation(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        } catch (_: Exception) {
            Surface.ROTATION_0
        }
    }

    internal fun applyOrientation(context: Context, orientation: Int, sysRotation: Int, accelRotation: Int) {
        val mode = LightspeedPreferences.getGravityEnforcementEngine(context)
        val useSystemSettings = mode == LightspeedPreferences.GRAVITY_ENGINE_SYSTEM_SETTINGS || mode == LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID
        val useWindowAnchor = mode == LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR || mode == LightspeedPreferences.GRAVITY_ENGINE_DUAL_HYBRID

        if (useSystemSettings) {
            writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, accelRotation)
            if (accelRotation == 0) {
                writeSystemSetting(context, Settings.System.USER_ROTATION, sysRotation)
            }
        }
        if (useWindowAnchor) {
            LightspeedAccessibilityService.instance?.updateForcedOrientation(orientation)
        } else {
            LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        }
    }

    fun forcePortrait(context: Context) {
        LightspeedOrientationManager.stopActiveSensorPortraitDriver()
        Log.i(TAG, "Forcing strict portrait orientation (0°)")
        applyOrientation(context, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Surface.ROTATION_0, 0)
    }

    fun setSensorPortrait(context: Context) {
        Log.i(TAG, "Applying active sensor portrait driver (0° & 180° inverted, blocking landscape)")
        val mode = LightspeedPreferences.getGravityEnforcementEngine(context)
        if (mode != LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR) {
            writeSystemSetting(context, Settings.System.ACCELEROMETER_ROTATION, 0)
            writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        }
        LightspeedOrientationManager.startActiveSensorPortraitDriver(context)
    }

    fun forceLandscape(context: Context) {
        LightspeedOrientationManager.stopActiveSensorPortraitDriver()
        Log.i(TAG, "Forcing sensor landscape orientation (90° / 270°)")
        applyOrientation(context, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, Surface.ROTATION_90, 0)
    }

    fun forceSensor360(context: Context) {
        LightspeedOrientationManager.stopActiveSensorPortraitDriver()
        Log.i(TAG, "Forcing full 360° sensor auto-rotation")
        applyOrientation(context, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR, Surface.ROTATION_0, 1)
    }

    fun releaseForcedOrientation(context: Context) {
        LightspeedOrientationManager.stopActiveSensorPortraitDriver()
        Log.i(TAG, "Releasing forced orientation to native baseline")
        LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        val mode = LightspeedPreferences.getGravityEnforcementEngine(context)
        if (mode != LightspeedPreferences.GRAVITY_ENGINE_WINDOW_ANCHOR) {
            val baseline = getMasterAutoRotateBaseline(context)
            setAutoRotateEnabled(context, baseline)
            if (!baseline) {
                writeSystemSetting(context, Settings.System.USER_ROTATION, Surface.ROTATION_0)
            }
        }
    }

    internal fun writeSystemSetting(context: Context, name: String, value: Int): Boolean {
        recordInternalWrite()
        if (canWriteSettings(context)) {
            try {
                Settings.System.putInt(context.contentResolver, name, value)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Settings.System: ${e.message}")
            }
        }
        if (ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                if (ElevatedTaskCloser.isShizukuActive) {
                    try {
                        ElevatedTaskCloser.execShizuku("settings put system $name $value")
                        return@launch
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing setting $name=$value via Shizuku: ${e.message}")
                    }
                }
                if (ElevatedTaskCloser.isRootActive) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $name $value")).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing setting $name=$value via Root: ${e.message}")
                    }
                }
            }
            return true
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



    val KNOWN_TRANSSION_LAUNCHERS = setOf(
        "com.transsion.XOSLauncher",
        "com.transsion.hilauncher",
        "com.transsion.itel.launcher",
        "com.sh.smart.launcher"
    )

    private var cachedLauncherPackages: Set<String>? = null

    fun getLauncherPackages(context: Context): Set<String> {
        cachedLauncherPackages?.let { return it }
        val pkgs = mutableSetOf<String>()
        try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val list = context.packageManager.queryIntentActivities(intent, 0)
            for (ri in list) {
                val p = ri.activityInfo?.packageName
                if (!p.isNullOrBlank() && p != "android") {
                    pkgs.add(p)
                }
            }
        } catch (_: Exception) {}

        // Guarantee detection for known OEM launchers (e.g. Infinix / Transsion XOS / HiOS)
        for (oemPkg in KNOWN_TRANSSION_LAUNCHERS) {
            try {
                context.packageManager.getPackageInfo(oemPkg, 0)
                pkgs.add(oemPkg)
            } catch (_: Exception) {}
        }

        if (pkgs.isNotEmpty()) {
            cachedLauncherPackages = pkgs
        }
        return pkgs
    }

    fun isLauncherPackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (KNOWN_TRANSSION_LAUNCHERS.contains(pkg)) return true
        if (pkg.contains("com.transsion.") && (pkg.contains("launcher", ignoreCase = true) || pkg.contains("quickstep", ignoreCase = true))) {
            return true
        }
        val defaultLauncher = getDefaultLauncherPackage(context)
        if (pkg == defaultLauncher) return true
        return getLauncherPackages(context).contains(pkg) || isDefaultLauncherPackage(context, pkg)
    }

    fun isDefaultLauncherPackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val resolvedPkg = resolveInfo?.activityInfo?.packageName
            if (resolvedPkg == pkg) return true
            if (resolvedPkg == null || resolvedPkg == "android") {
                // Secondary check for resolvers or unconfirmed defaults
                val altInfo = context.packageManager.resolveActivity(intent, 0)
                altInfo?.activityInfo?.packageName == pkg
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    fun getDefaultLauncherPackage(context: Context): String? {
        return try {
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
                addCategory(android.content.Intent.CATEGORY_HOME)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            val pkg = resolveInfo?.activityInfo?.packageName
            if (!pkg.isNullOrBlank() && pkg != "android") {
                pkg
            } else {
                // Fallback: check query list or known OEM installed launcher
                val list = context.packageManager.queryIntentActivities(intent, 0)
                val candidate = list.firstOrNull { it.activityInfo?.packageName != null && it.activityInfo.packageName != "android" }?.activityInfo?.packageName
                candidate ?: KNOWN_TRANSSION_LAUNCHERS.firstOrNull { oemPkg ->
                    try {
                        context.packageManager.getPackageInfo(oemPkg, 0)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        } catch (_: Exception) {
            null
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
