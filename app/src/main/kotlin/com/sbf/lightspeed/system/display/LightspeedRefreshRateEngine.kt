package com.sbf.lightspeed.system.display

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.ShizukuExecHelper
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Hardware-adaptive display refresh rate discovery, override, and kinetic boost engine.
 *
 * Dynamically inspects physical panel modes (60Hz fixed tablets, 90Hz, 120Hz, 144Hz, 165Hz, 185Hz+)
 * and coordinates Settings.System refresh rate votes alongside WindowManager preferredDisplayModeId.
 */
object LightspeedRefreshRateEngine {

    private const val TAG = "RefreshRateEngine"
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var cachedNativeModes: List<Display.Mode>? = null

    @Volatile
    private var isCacheWarming = false

    @Volatile
    private var listenerRegistered = false

    private fun ensureDisplayListener(context: Context) {
        if (listenerRegistered) return
        synchronized(this) {
            if (listenerRegistered) return
            try {
                val dm = context.applicationContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
                dm.registerDisplayListener(object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = warmCacheAsync(context.applicationContext)
                    override fun onDisplayRemoved(displayId: Int) = Unit
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
                            warmCacheAsync(context.applicationContext)
                        }
                    }
                }, null)
                listenerRegistered = true
            } catch (e: Exception) {
                logSwallowed(TAG, "ensureDisplayListener", e)
            }
        }
    }

    fun warmCacheAsync(context: Context) {
        if (isCacheWarming) return
        isCacheWarming = true
        val appCtx = context.applicationContext
        ioScope.launch {
            try {
                cachedNativeModes = queryNativeModes(appCtx)
            } finally {
                isCacheWarming = false
            }
        }
    }

    private fun queryNativeModes(context: Context): List<Display.Mode> {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.getDisplay(Display.DEFAULT_DISPLAY) ?: return emptyList()
            val modes = display.supportedModes ?: return emptyList()
            val activeMode = display.mode
            modes.filter {
                activeMode == null || (it.physicalWidth == activeMode.physicalWidth && it.physicalHeight == activeMode.physicalHeight)
            }.ifEmpty { modes.toList() }
        } catch (e: Exception) {
            logSwallowed(TAG, "queryNativeModes", e)
            emptyList()
        }
    }

    private fun getNativeModesCached(context: Context): List<Display.Mode> {
        ensureDisplayListener(context)
        cachedNativeModes?.let { return it }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            warmCacheAsync(context)
            return emptyList()
        }
        return queryNativeModes(context).also { cachedNativeModes = it }
    }

    /**
     * Returns sorted distinct integer refresh rates supported by the primary display at native resolution.
     * Preserves any low-refresh hardware modes (>= 10Hz) exposed by the display panel.
     * Always returns at least [60] even on fixed-rate 32-bit/legacy panels.
     */
    fun getSupportedRefreshRates(context: Context): List<Int> {
        val nativeModes = getNativeModesCached(context)
        if (nativeModes.isEmpty()) return listOf(60)
        val rates = nativeModes
            .map { it.refreshRate.roundToInt() }
            .filter { it >= 10 }
            .distinct()
            .sorted()
        return rates.ifEmpty { listOf(60) }
    }

    /**
     * Returns the lowest hardware-reported refresh rate (>= 10Hz) at native resolution,
     * falling back to 60Hz if native modes are not yet cached.
     */
    fun getLowestHardwareRefreshRate(context: Context): Int {
        val nativeModes = getNativeModesCached(context)
        return nativeModes
            .map { it.refreshRate.roundToInt() }
            .filter { it >= 10 }
            .minOrNull() ?: 60
    }

    /**
     * Returns the highest refresh rate Display.Mode at native resolution for automatic overlay boosting.
     */
    fun getPeakDisplayMode(context: Context): Display.Mode? {
        return getNativeModesCached(context).maxByOrNull { it.refreshRate }
    }

    /**
     * Finds the hardware Display.Mode matching [targetHz] at native resolution.
     */
    fun findDisplayModeForHz(context: Context, targetHz: Int): Display.Mode? {
        return getNativeModesCached(context).minByOrNull { abs(it.refreshRate.roundToInt() - targetHz) }
    }

    /**
     * Applies or clears a peak hardware refresh rate vote on a WindowManager.LayoutParams instance.
     * Used by LightspeedCruiseOverlay when expanded so gesture rendering always runs at peak Hz.
     */
    fun applyOverlayPeakBoost(context: Context, params: WindowManager.LayoutParams, enableBoost: Boolean) {
        try {
            if (enableBoost) {
                val peakMode = getPeakDisplayMode(context)
                if (peakMode != null) {
                    params.preferredDisplayModeId = peakMode.modeId
                    params.preferredRefreshRate = peakMode.refreshRate
                }
            } else {
                params.preferredDisplayModeId = 0
                params.preferredRefreshRate = 0f
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "applyOverlayPeakBoost", e)
        }
    }

    /**
     * Reads the active target refresh rate in Hz from preferences or system settings.
     */
    fun getActiveTargetHz(context: Context): Int {
        val supported = getSupportedRefreshRates(context)
        if (supported.size <= 1) return supported.firstOrNull() ?: 60

        val saved = context.defaultPrefs().getInt(LightspeedPreferences.KEY_OVERDRIVE_TARGET_HZ, 0)
        if (saved in supported) return saved

        val cr = context.contentResolver
        val sysPeak = try {
            Settings.System.getFloat(cr, "peak_refresh_rate", 0f).roundToInt()
        } catch (_: Exception) { 0 }
        if (sysPeak in supported) return sysPeak

        val tranMode = try {
            Settings.System.getInt(cr, "tran_refresh_mode", 0)
        } catch (_: Exception) { 0 }
        if (tranMode in supported) return tranMode

        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val cur = dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60
        return supported.minByOrNull { abs(it - cur) } ?: supported.last()
    }

    /**
     * Reads the live instantaneous refresh rate reported by DisplayManager.
     */
    fun getLiveDisplayHz(context: Context): Int {
        return try {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.roundToInt() ?: 60
        } catch (_: Exception) {
            60
        }
    }

    /**
     * Applies [targetHz] and [floorMode] ("adaptive" vs "locked") to system refresh rate settings.
     * In adaptive mode, deletes `min_refresh_rate` (or sets 0.0f fallback) so DisplayModeDirector
     * and SurfaceFlinger frame-pacing can step down freely to the panel's lowest physical refresh rate.
     */
    fun applyRefreshRate(context: Context, targetHz: Int, floorMode: String) {
        val prefs = context.defaultPrefs()
        prefs.edit()
            .putInt(LightspeedPreferences.KEY_OVERDRIVE_TARGET_HZ, targetHz)
            .putString(LightspeedPreferences.KEY_OVERDRIVE_FLOOR_MODE, floorMode)
            .apply()

        val isLocked = floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED
        val minHz = if (isLocked) targetHz.toFloat() else 0.0f
        val peakHz = targetHz.toFloat()

        if (Settings.System.canWrite(context)) {
            val cr = context.contentResolver
            try { Settings.System.putFloat(cr, "peak_refresh_rate", peakHz) } catch (e: Exception) { logSwallowed(TAG, "put:peak_refresh_rate", e) }
            try { Settings.System.putFloat(cr, "user_refresh_rate", peakHz) } catch (e: Exception) { logSwallowed(TAG, "put:user_refresh_rate", e) }
            if (Build.MANUFACTURER.equals("INFINIX", ignoreCase = true) ||
                Build.MANUFACTURER.equals("TECNO", ignoreCase = true) ||
                Build.MANUFACTURER.equals("ITEL", ignoreCase = true)
            ) {
                val tranModeVal = if (!isLocked && targetHz >= 120) 10000 else targetHz
                try { Settings.System.putInt(cr, "tran_refresh_mode", tranModeVal) } catch (e: Exception) { logSwallowed(TAG, "put:tran_refresh_mode", e) }
            }
            if (isLocked) {
                try { Settings.System.putFloat(cr, "min_refresh_rate", minHz) } catch (e: Exception) { logSwallowed(TAG, "put:min_refresh_rate", e) }
            } else {
                // Deleting min_refresh_rate prevents Transsion/MTK DisplayModeDirector from clamping 0.0f to 60.0Hz
                val deleted = try {
                    cr.delete(Settings.System.getUriFor("min_refresh_rate"), null, null) > 0
                } catch (_: Exception) { false }
                if (!deleted) {
                    try { Settings.System.putFloat(cr, "min_refresh_rate", 0.0f) } catch (e: Exception) { logSwallowed(TAG, "put:min_refresh_rate_zero", e) }
                }
            }
        }

        if (ElevatedTaskCloser.isShizukuActive) {
            ioScope.launch {
                try {
                    if (targetHz > 120) {
                        val uncap = (targetHz + 1).toString()
                        ShizukuExecHelper.execShizukuArgv(
                            "device_config", "put", "display_manager", "peak_refresh_rate_default", uncap
                        )?.waitFor()
                    }
                    ShizukuExecHelper.execShizukuArgv(
                        "settings", "put", "global", "display_refresh_rate", peakHz.toString()
                    )?.waitFor()
                    ShizukuExecHelper.execShizukuArgv(
                        "settings", "put", "system", "peak_refresh_rate", peakHz.toString()
                    )?.waitFor()
                    if (isLocked) {
                        ShizukuExecHelper.execShizukuArgv(
                            "settings", "put", "system", "min_refresh_rate", minHz.toString()
                        )?.waitFor()
                    } else {
                        ShizukuExecHelper.execShizukuArgv(
                            "settings", "delete", "system", "min_refresh_rate"
                        )?.waitFor()
                    }
                } catch (e: Exception) {
                    logSwallowed(TAG, "applyRefreshRate:shizuku", e)
                }
            }
        }

        LightspeedTelemetryHudOverlay.refreshDisplayModeVote(context)
    }
}
