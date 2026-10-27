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
import kotlinx.coroutines.*

/**
 * 3-Tier Eye Care & Extra Dim Suite
 *
 * Tier 1 (Native): WRITE_SECURE_SETTINGS / Shizuku settings put secure
 *   - reduce_bright_colors_activated (0/1) + reduce_bright_colors_level (0-100)
 *   - night_display_activated (0/1) + night_display_color_temperature (1000-6000K)
 * Tier 2 (SurfaceFlinger 1015): Direct hardware compositor 4x4 color matrix via Shizuku
 *   - Works at hardware HAL level across 100% of devices, debounced at 80ms
 * Tier 3 (Accessibility Overlay): Full-display edge-to-edge overlay covering status bar and cutouts
 */
object LightspeedEyeCareEngine {

    private const val TAG = "LightspeedEyeCare"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Internal State
    private var isExtraDimEnabled: Boolean = false
    private var extraDimLevel: Int = 40
    private var isEyeCareEnabled: Boolean = false
    private var eyeCareKelvin: Int = 3200

    // Debounced SurfaceFlinger Dispatcher
    private var sfDebounceJob: Job? = null

    // Overlay Window State (Tier 3)
    private var overlayView: View? = null

    fun init(context: Context) {
        val prefs = context.defaultPrefs()
        isExtraDimEnabled = prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)
        extraDimLevel = prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
        isEyeCareEnabled = prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)
        eyeCareKelvin = prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3200)

        if (isExtraDimEnabled) {
            applyExtraDim(context, true, extraDimLevel)
        }
        if (isEyeCareEnabled) {
            applyEyeCare(context, true, eyeCareKelvin)
        }
        LightspeedEyeRestMonitor.init(context)
    }

    // --- Extra Dim ---

    fun applyExtraDim(context: Context, enabled: Boolean, level: Int) {
        val clampedLevel = level.coerceIn(0, 100)
        isExtraDimEnabled = enabled
        extraDimLevel = clampedLevel

        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, enabled)
            .putInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, clampedLevel)
            .apply()

        val mode = context.defaultPrefs().getString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native") ?: "native"
        dispatchSync(context, mode, immediate = !enabled)
    }

    // --- Eye Care (Blue Light Filter) ---

    fun applyEyeCare(context: Context, enabled: Boolean, kelvin: Int) {
        val clampedKelvin = kelvin.coerceIn(1000, 6000)
        isEyeCareEnabled = enabled
        eyeCareKelvin = clampedKelvin

        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, enabled)
            .putInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, clampedKelvin)
            .apply()

        val mode = context.defaultPrefs().getString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native") ?: "native"
        dispatchSync(context, mode, immediate = !enabled)
    }

    // --- Unified Dispatcher: Hardware SurfaceFlinger 1015 + Settings.Secure + Overlay ---

    private fun dispatchSync(context: Context, mode: String, immediate: Boolean) {
        scope.launch {
            val isShizuku = ElevatedTaskCloser.isShizukuActive
            val useHardware = (mode == "native" || mode == "surfaceflinger") && isShizuku

            if (useHardware) {
                // Tier 2: Real-time SurfaceFlinger 1015 hardware matrix (HAL level)
                dispatchSurfaceFlingerUpdate(immediate)

                // Also update native Settings.Secure
                try {
                    val cr = context.contentResolver
                    if (isEyeCareEnabled) {
                        Settings.Secure.putInt(cr, "night_display_activated", 1)
                        Settings.Secure.putInt(cr, "night_display_color_temperature", eyeCareKelvin)
                    } else {
                        Settings.Secure.putInt(cr, "night_display_activated", 0)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Settings.Secure.putInt(cr, "reduce_bright_colors_activated", if (isExtraDimEnabled) 1 else 0)
                        Settings.Secure.putInt(cr, "reduce_bright_colors_level", extraDimLevel)
                    }
                } catch (_: Exception) {
                    ElevatedTaskCloser.execShizuku("settings put secure night_display_activated ${if (isEyeCareEnabled) 1 else 0}")
                    if (isEyeCareEnabled) {
                        ElevatedTaskCloser.execShizuku("settings put secure night_display_color_temperature $eyeCareKelvin")
                    }
                    ElevatedTaskCloser.execShizuku("settings put secure reduce_bright_colors_activated ${if (isExtraDimEnabled) 1 else 0}")
                    ElevatedTaskCloser.execShizuku("settings put secure reduce_bright_colors_level $extraDimLevel")
                }

                // Shizuku hardware HAL active: remove accessibility overlay to avoid double dim/tint
                mainHandler.post {
                    updateOverlay(context, 0f, 0)
                }
            } else {
                // When Shizuku is NOT active, try Settings.Secure anyway
                try {
                    val cr = context.contentResolver
                    Settings.Secure.putInt(cr, "night_display_activated", if (isEyeCareEnabled) 1 else 0)
                    if (isEyeCareEnabled) {
                        Settings.Secure.putInt(cr, "night_display_color_temperature", eyeCareKelvin)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Settings.Secure.putInt(cr, "reduce_bright_colors_activated", if (isExtraDimEnabled) 1 else 0)
                        Settings.Secure.putInt(cr, "reduce_bright_colors_level", extraDimLevel)
                    }
                } catch (_: Exception) {}

                // Mode Tier 3: ALWAYS apply full-display TYPE_ACCESSIBILITY_OVERLAY filter covering status bar
                mainHandler.post {
                    val dimAlpha = if (isExtraDimEnabled) (extraDimLevel / 100f) * 0.75f else 0f
                    val kelvin = if (isEyeCareEnabled) eyeCareKelvin else 0
                    updateOverlay(context, dimAlpha, kelvin)
                }
            }
        }
    }

    private fun dispatchSurfaceFlingerUpdate(immediate: Boolean) {
        sfDebounceJob?.cancel()
        sfDebounceJob = scope.launch(Dispatchers.IO) {
            if (!immediate) {
                delay(80L)
            }
            if (!isEyeCareEnabled && !isExtraDimEnabled) {
                ElevatedTaskCloser.execShizuku("service call SurfaceFlinger 1015 i32 0")
            } else {
                val kelvin = if (isEyeCareEnabled) eyeCareKelvin.coerceIn(1000, 6000) else 6000
                val kelvinFactor = ((kelvin - 1000f) / 5000f).coerceIn(0f, 1f)
                val baseR = 1.0f
                val baseG = if (isEyeCareEnabled) (kelvinFactor * 0.4f + 0.6f).coerceIn(0.15f, 1.0f) else 1.0f
                val baseB = if (isEyeCareEnabled) (kelvinFactor * 0.7f + 0.3f).coerceIn(0.15f, 1.0f) else 1.0f

                val dimScale = if (isExtraDimEnabled) (1.0f - (extraDimLevel / 100f * 0.75f)).coerceIn(0.10f, 1.0f) else 1.0f

                val r = (baseR * dimScale).coerceIn(0.05f, 1.0f)
                val g = (baseG * dimScale).coerceIn(0.05f, 1.0f)
                val b = (baseB * dimScale).coerceIn(0.05f, 1.0f)

                val cmd = "service call SurfaceFlinger 1015 i32 1 f $r f 0 f 0 f 0 f 0 f $g f 0 f 0 f 0 f 0 f $b f 0 f 0 f 0 f 0 f 1.0"
                ElevatedTaskCloser.execShizuku(cmd)
            }
        }
    }

    // --- Tier 3: Edge-to-Edge Accessibility Window Overlay ---

    private fun updateOverlay(context: Context, dimAlpha: Float, kelvin: Int) {
        val service = LightspeedAccessibilityService.instance ?: return
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        if (dimAlpha <= 0.001f && kelvin <= 0) {
            overlayView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
                overlayView = null
            }
            return
        }

        if (overlayView == null) {
            val v = View(service).apply {
                isClickable = false
                isFocusable = false
            }
            val params = createFullDisplayOverlayParams(service)
            try {
                wm.addView(v, params)
                overlayView = v
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach eye care overlay", e)
                return
            }
        }

        // Composite Color: Amber Warm Tint + Black Dim Alpha
        val warmAlpha = if (kelvin > 0) {
            val factor = 1f - ((kelvin - 1000f) / 5000f).coerceIn(0f, 1f)
            (factor * 0.38f).coerceIn(0f, 0.48f)
        } else 0f

        val finalColor = when {
            dimAlpha > 0f && kelvin > 0 -> {
                val combinedAlpha = (dimAlpha + warmAlpha * 0.5f).coerceIn(0f, 0.88f)
                val r = (255 * (1f - dimAlpha * 0.4f)).toInt().coerceIn(0, 255)
                val g = (160 * (1f - dimAlpha * 0.5f)).toInt().coerceIn(0, 255)
                android.graphics.Color.argb((combinedAlpha * 255).toInt(), r, g, 0)
            }
            dimAlpha > 0f -> {
                android.graphics.Color.argb((dimAlpha * 255).toInt(), 0, 0, 0)
            }
            kelvin > 0 -> {
                android.graphics.Color.argb((warmAlpha * 255).toInt(), 255, 170, 0)
            }
            else -> android.graphics.Color.TRANSPARENT
        }

        overlayView?.setBackgroundColor(finalColor)
    }

    // --- Eye Rest Reminder Delegation ---

    fun onScreenStateChanged(context: Context, isScreenOn: Boolean) =
        LightspeedEyeRestMonitor.onScreenStateChanged(context, isScreenOn)

    fun triggerReminder(context: Context) =
        LightspeedEyeRestMonitor.triggerReminder(context)
}
