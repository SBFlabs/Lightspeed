package com.sbf.lightspeed.system.display

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import kotlin.math.roundToInt

/**
 * Display Wellness & Video Override Controller.
 *
 * Handles:
 * 1. Safe Hardware System Brightness Control: writes standard Settings.System brightness
 *    and screen_brightness_float without overlay window hijacking, ensuring VLC/MX Player
 *    in-app gestures and the Android quick settings slider retain full sovereignty.
 * 2. Video Brightness Takeover: dynamically setting `windowLayoutParams.screenBrightness`
 *    strictly in the optional boost zone (> 100%), immediately clearing when in standard range (1..255).
 * 3. Auto-Brightness Reset: automatically resetting system brightness to Auto
 *    (Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) and clearing video override
 *    based on configured trigger mode ("off", "screen_off", "app_switch", "recents_close").
 */
object LightspeedDisplayWellnessResetEngine {
    private const val TAG = "DisplayWellnessReset"

    @Volatile
    private var activeVideoOverridePct: Float? = null

    private var videoOverrideView: View? = null
    private var videoOverrideParams: WindowManager.LayoutParams? = null

    fun getActiveVideoOverride(): Float? = activeVideoOverridePct

    /**
     * Reads current hardware screen brightness accurately across all Android OEMs (0..255).
     * On Android 9+, Samsung One UI, and modern Pixels, checks `screen_brightness_float`
     * (0.0..1.0) first, falling back to legacy `Settings.System.SCREEN_BRIGHTNESS` (0..255).
     */
    fun getSystemBrightness(context: Context): Int {
        val cr = context.contentResolver
        try {
            val floatVal = try {
                Settings.System.getFloat(cr, "screen_brightness_float", -1f)
            } catch (_: Exception) { -1f }

            if (floatVal in 0.0f..1.0f) {
                return (floatVal * 255f).roundToInt().coerceIn(1, 255)
            }

            return Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128).coerceIn(1, 255)
        } catch (_: Exception) {
            return 128
        }
    }

    /**
     * Sets system brightness cleanly across all Android OEMs without interfering with window hierarchies.
     * Updates SCREEN_BRIGHTNESS (int 1..255) and screen_brightness_float (float 0.0..1.0).
     * If Shizuku is active, runs `cmd display set-brightness <float>` (never integer > 1.0).
     */
    fun setSystemBrightness(context: Context, brightnessInt: Int) {
        val cr = context.contentResolver
        val clampedInt = brightnessInt.coerceIn(1, 255)
        val clampedFloat = (clampedInt / 255f).coerceIn(0.004f, 1.0f)

        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, clampedInt)
                try {
                    Settings.System.putFloat(cr, "screen_brightness_float", clampedFloat)
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "setSystemBrightness:writeSettings", e)
        }

        if (ElevatedTaskCloser.isShizukuActive) {
            val formattedFloat = String.format(java.util.Locale.US, "%.3f", clampedFloat)
            ElevatedTaskCloser.execShizuku("cmd display set-brightness $formattedFloat 2>/dev/null || settings put system screen_brightness $clampedInt")
        }
    }

    fun setVideoOverride(context: Context, pct: Float?) {
        activeVideoOverridePct = pct
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        if (pct == null || pct <= 0f) {
            clearVideoOverride(context)
            return
        }

        try {
            val brightness = pct.coerceIn(0.01f, 1.0f)
            if (videoOverrideView == null) {
                val v = View(context).apply {
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
                val lp = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    screenBrightness = brightness
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
                wm.addView(v, lp)
                videoOverrideView = v
                videoOverrideParams = lp
            } else {
                videoOverrideParams?.let { lp ->
                    lp.screenBrightness = brightness
                    wm.updateViewLayout(videoOverrideView, lp)
                }
            }

            // Always synchronize hardware system brightness in lockstep so brightness never resets on app close or update
            val intVal = (brightness * 255).roundToInt().coerceIn(1, 255)
            setSystemBrightness(context, intVal)
        } catch (e: Exception) {
            logSwallowed(TAG, "setVideoOverride", e)
        }
    }

    fun clearVideoOverride(context: Context) {
        activeVideoOverridePct = null
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        videoOverrideView?.let { v ->
            try {
                wm.removeView(v)
            } catch (e: Exception) {
                logSwallowed(TAG, "clearVideoOverride", e)
            }
            videoOverrideView = null
            videoOverrideParams = null
        }
    }

    fun resetAutoBrightness(context: Context) {
        clearVideoOverride(context)
        try {
            if (Settings.System.canWrite(context)) {
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                )
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "resetAutoBrightness", e)
        }
        if (ElevatedTaskCloser.isShizukuActive) {
            ElevatedTaskCloser.execShizuku("cmd display reset-brightness-configuration 2>/dev/null; settings put system screen_brightness_mode 1")
        }
    }

    fun onScreenOff(context: Context) {
        val prefs = context.defaultPrefs()
        val mode = prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF)
        if (mode == LightspeedPreferences.AUTO_RESET_SCREEN_OFF ||
            mode == LightspeedPreferences.AUTO_RESET_APP_SWITCH ||
            mode == LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE) {
            resetAutoBrightness(context)
        }
    }

    fun onAppSwitch(context: Context) {
        val prefs = context.defaultPrefs()
        val mode = prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF)
        if (mode == LightspeedPreferences.AUTO_RESET_APP_SWITCH ||
            mode == LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE) {
            resetAutoBrightness(context)
        }
    }

    fun onAppClosedFromRecents(context: Context) {
        val prefs = context.defaultPrefs()
        val mode = prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF)
        if (mode == LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE) {
            resetAutoBrightness(context)
        }
    }
}
