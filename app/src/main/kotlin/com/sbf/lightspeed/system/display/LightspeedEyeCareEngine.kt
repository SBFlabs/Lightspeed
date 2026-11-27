package com.sbf.lightspeed.system.display

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.roundToInt
import kotlinx.coroutines.*

/**
 * Native OS Eye Care & Extra Dim Engine
 *
 * - Eye Care: Pure OS built-in Night Display (`night_display_activated` / `night_display_color_temperature`)
 *   on AOSP/Infinix, and native Eye Comfort Shield (`blue_light_filter` / `blue_light_filter_opacity`) on Samsung.
 * - Extra Dim: Native `reduce_bright_colors_activated` / `reduce_bright_colors_level` paired with a
 *   click-through pure-black edge-to-edge dim overlay when enabled.
 */
object LightspeedEyeCareEngine {

    private const val TAG = "LightspeedEyeCare"
    const val MIN_KELVIN = 2200
    const val MAX_KELVIN = 5000
    const val DEFAULT_KELVIN = 3400

    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Internal State
    private var isExtraDimEnabled: Boolean = false
    private var extraDimLevel: Int = 40
    private var isEyeCareEnabled: Boolean = false
    private var eyeCareKelvin: Int = DEFAULT_KELVIN

    // Extra Dim Overlay Window State
    private var overlayView: View? = null

    private fun getNativeNightDisplayBounds(): Pair<Int, Int> {
        val res = android.content.res.Resources.getSystem()
        val minId = res.getIdentifier("config_nightDisplayColorTemperatureMin", "integer", "android")
        val maxId = res.getIdentifier("config_nightDisplayColorTemperatureMax", "integer", "android")
        val rawMin = if (minId != 0) try { res.getInteger(minId) } catch (_: Exception) { 2596 } else 2596
        val rawMax = if (maxId != 0) try { res.getInteger(maxId) } catch (_: Exception) { 4082 } else 4082
        val minVal = rawMin.coerceIn(1000, 5000)
        val maxVal = rawMax.coerceAtLeast(minVal + 200).coerceAtMost(6500)
        return minVal to maxVal
    }

    private fun toNativeNightDisplayTemp(uiKelvin: Int, minNative: Int, maxNative: Int): Int {
        val clamped = uiKelvin.coerceIn(MIN_KELVIN, MAX_KELVIN)
        val warmth = ((MAX_KELVIN - clamped).toFloat() / (MAX_KELVIN - MIN_KELVIN).toFloat()).coerceIn(0f, 1f)
        return (maxNative - warmth * (maxNative - minNative)).roundToInt().coerceIn(minNative, maxNative)
    }

    private fun fromNativeNightDisplayTemp(nativeTemp: Int, minNative: Int, maxNative: Int): Int {
        val warmth = ((maxNative - nativeTemp).toFloat() / (maxNative - minNative).toFloat()).coerceIn(0f, 1f)
        return (MAX_KELVIN - warmth * (MAX_KELVIN - MIN_KELVIN)).roundToInt().coerceIn(MIN_KELVIN, MAX_KELVIN)
    }

    fun init(context: Context) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()

        // Purge legacy software overlay mode so Eye Care always uses pure OS built-in hardware filter
        if (prefs.getString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native") != "native") {
            prefs.edit().putString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native").apply()
        }

        isExtraDimEnabled = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
        val rawDimLevel = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
        extraDimLevel = if (rawDimLevel <= 0) 40 else rawDimLevel.coerceIn(10, 90)
        if (extraDimLevel != rawDimLevel) {
            prefs.edit().putInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, extraDimLevel).apply()
        }

        // Read-only sync from OS built-in Eye Care; never overwrite user's OS setting on startup
        syncFromSystem(appCtx)

        if (isExtraDimEnabled) {
            applyExtraDim(appCtx, true, extraDimLevel)
        } else {
            mainHandler.post { refreshOverlay(appCtx) }
        }
        LightspeedEyeRestMonitor.init(appCtx)
    }

    fun syncFromSystem(context: Context): Pair<Boolean, Int> {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        val storedEnabled = prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)
        val rawStoredTemp = prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, DEFAULT_KELVIN)
        var nextEnabled = storedEnabled
        var nextKelvin = rawStoredTemp.coerceIn(MIN_KELVIN, MAX_KELVIN)

        val cr = appCtx.contentResolver
        val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
        if (isSamsung) {
            val activated = try { Settings.System.getInt(cr, "blue_light_filter", -1) } catch (_: Exception) { -1 }
            if (activated >= 0) {
                nextEnabled = (activated == 1)
                val opacity = try { Settings.System.getInt(cr, "blue_light_filter_opacity", -1) } catch (_: Exception) { -1 }
                if (opacity in 0..10) {
                    val expectedFactor = ((MAX_KELVIN - nextKelvin).toFloat() / (MAX_KELVIN - MIN_KELVIN).toFloat()).coerceIn(0f, 1f)
                    val expectedOpacity = (expectedFactor * 10f).roundToInt().coerceIn(0, 10)
                    if (opacity != expectedOpacity) {
                        val factor = (opacity / 10f).coerceIn(0f, 1f)
                        nextKelvin = (MAX_KELVIN - factor * (MAX_KELVIN - MIN_KELVIN)).roundToInt().coerceIn(MIN_KELVIN, MAX_KELVIN)
                    }
                }
            }
        } else {
            val activated = try { Settings.Secure.getInt(cr, "night_display_activated", -1) } catch (_: Exception) { -1 }
            val nativeTemp = try { Settings.Secure.getInt(cr, "night_display_color_temperature", -1) } catch (_: Exception) { -1 }
            val (minNative, maxNative) = getNativeNightDisplayBounds()
            if (activated >= 0) {
                nextEnabled = (activated == 1)
            }
            if (nativeTemp > 0) {
                nextKelvin = fromNativeNightDisplayTemp(nativeTemp, minNative, maxNative)
            }
        }

        isEyeCareEnabled = nextEnabled
        eyeCareKelvin = nextKelvin
        if (nextEnabled != storedEnabled || nextKelvin != rawStoredTemp) {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, nextEnabled)
                .putInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, nextKelvin)
                .apply()
        }
        mainHandler.post { refreshOverlay(appCtx) }
        return nextEnabled to nextKelvin
    }

    // --- Extra Dim ---

    fun applyExtraDim(context: Context, enabled: Boolean, level: Int) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        val savedLevel = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40).let { if (it in 10..90) it else 40 }
        val effectiveLevel = if (enabled) {
            (if (level <= 0) savedLevel else level).coerceIn(10, 90)
        } else {
            if (level in 10..90) level else savedLevel
        }
        isExtraDimEnabled = enabled
        extraDimLevel = effectiveLevel

        prefs.edit()
            .putBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, enabled)
            .putInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, effectiveLevel)
            .apply()

        scope.launch {
            val cr = appCtx.contentResolver
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Settings.Secure.putInt(cr, "reduce_bright_colors_activated", if (isExtraDimEnabled) 1 else 0)
                    if (isExtraDimEnabled) {
                        Settings.Secure.putInt(cr, "reduce_bright_colors_level", extraDimLevel)
                    }
                }
            } catch (_: Exception) {
                ElevatedTaskCloser.execShizuku("settings put secure reduce_bright_colors_activated ${if (isExtraDimEnabled) 1 else 0}")
                if (isExtraDimEnabled) {
                    ElevatedTaskCloser.execShizuku("settings put secure reduce_bright_colors_level $extraDimLevel")
                }
            }
            mainHandler.post { refreshOverlay(appCtx) }
        }
    }

    fun disableExtraDimIfActive(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (!isExtraDimEnabled && !appCtx.defaultPrefs().getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)) {
            return
        }
        applyExtraDim(appCtx, false, extraDimLevel)
    }

    // --- Eye Care (Blue Light Filter) ---

    fun applyEyeCare(
        context: Context,
        enabled: Boolean,
        kelvin: Int = eyeCareKelvin,
        updateTemperature: Boolean = true
    ) {
        val appCtx = context.applicationContext ?: context
        val clampedKelvin = kelvin.coerceIn(MIN_KELVIN, MAX_KELVIN)
        isEyeCareEnabled = enabled
        if (updateTemperature) {
            eyeCareKelvin = clampedKelvin
        }

        val prefs = appCtx.defaultPrefs()
        prefs.edit()
            .putBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, enabled)
            .apply {
                if (updateTemperature) {
                    putInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, clampedKelvin)
                }
            }
            .apply()

        scope.launch {
            val isSamsung = Build.MANUFACTURER.contains("samsung", ignoreCase = true)
            if (isSamsung) {
                // Samsung One UI: Use native Eye Comfort Shield (Settings.System)
                // NEVER write AOSP night_display_* to prevent the 0.00 SurfaceFlinger black screen collapse
                val cr = appCtx.contentResolver
                val opacity = if (isEyeCareEnabled && updateTemperature) {
                    val factor = ((MAX_KELVIN - eyeCareKelvin).toFloat() / (MAX_KELVIN - MIN_KELVIN).toFloat()).coerceIn(0f, 1f)
                    (factor * 10f).roundToInt().coerceIn(0, 10)
                } else null

                try {
                    Settings.System.putInt(cr, "blue_light_filter", if (isEyeCareEnabled) 1 else 0)
                    if (opacity != null) {
                        Settings.System.putInt(cr, "blue_light_filter_opacity", opacity)
                    }
                } catch (_: Exception) {
                    ElevatedTaskCloser.execShizuku("settings put system blue_light_filter ${if (isEyeCareEnabled) 1 else 0}")
                    if (opacity != null) {
                        ElevatedTaskCloser.execShizuku("settings put system blue_light_filter_opacity $opacity")
                    }
                }
            } else {
                val nativeTemp = if (isEyeCareEnabled && updateTemperature) {
                    val (minNative, maxNative) = getNativeNightDisplayBounds()
                    toNativeNightDisplayTemp(eyeCareKelvin, minNative, maxNative)
                } else null
                try {
                    val cr = appCtx.contentResolver
                    Settings.Secure.putInt(cr, "night_display_activated", if (isEyeCareEnabled) 1 else 0)
                    if (nativeTemp != null) {
                        Settings.Secure.putInt(cr, "night_display_color_temperature", nativeTemp)
                    }
                } catch (_: Exception) {
                    ElevatedTaskCloser.execShizuku("settings put secure night_display_activated ${if (isEyeCareEnabled) 1 else 0}")
                    if (nativeTemp != null) {
                        ElevatedTaskCloser.execShizuku("settings put secure night_display_color_temperature $nativeTemp")
                    }
                }
            }

            mainHandler.post { refreshOverlay(appCtx) }
        }
    }

    private fun refreshOverlay(context: Context) {
        val dimAlpha = if (isExtraDimEnabled) ((extraDimLevel / 100f) * 0.70f).coerceIn(0f, 0.70f) else 0f
        updateOverlay(context, dimAlpha)
    }

    // --- Pure Black Extra Dim Accessibility Window Overlay ---

    private fun updateOverlay(context: Context, dimAlpha: Float) {
        if (dimAlpha <= 0.001f) {
            overlayView?.let { v ->
                val wm = v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                try { wm?.removeView(v) } catch (_: Exception) {}
                overlayView = null
            }
            return
        }

        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        overlayView?.let { existing ->
            if (existing.context !== service) {
                val oldWm = existing.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                try { oldWm?.removeView(existing) } catch (_: Exception) {}
                overlayView = null
            }
        }

        if (overlayView == null) {
            val v = View(service).apply {
                isClickable = false
                isFocusable = false
                // Prevent hardware compositor blackouts on Samsung A24 / Mali GPUs by using software rendering layer
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            val params = createFullDisplayOverlayParams(service).apply {
                format = android.graphics.PixelFormat.TRANSLUCENT
                // Strip FLAG_HARDWARE_ACCELERATED since this overlay uses LAYER_TYPE_SOFTWARE; prevents idle GPU swapchain allocation
                flags = flags and WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED.inv()
            }
            try {
                wm.addView(v, params)
                overlayView = v
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach extra dim overlay", e)
                return
            }
        }

        val safeDim = dimAlpha.coerceIn(0f, 0.70f)
        overlayView?.setBackgroundColor(android.graphics.Color.argb((safeDim * 255).toInt(), 0, 0, 0))
    }

    fun onServiceDestroyed(service: Context?) {
        overlayView?.let { v ->
            try {
                val wm = (service ?: v.context).getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                wm?.removeView(v)
            } catch (_: Exception) {}
        }
        overlayView = null
    }

    // --- Eye Rest Reminder Delegation ---

    fun onScreenStateChanged(context: Context, isScreenOn: Boolean) =
        LightspeedEyeRestMonitor.onScreenStateChanged(context, isScreenOn)

    fun triggerReminder(context: Context) =
        LightspeedEyeRestMonitor.triggerReminder(context)
}
