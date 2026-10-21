package com.sbf.lightspeed.settings

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.graphics.ShaderBrush
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Backdrop Blur Mode options for Central Command and Flight Control Deck themes.
 * - AUTOMATIC: Uses native window blur if genuine hardware/firmware support exists; otherwise uses screenshot blur workaround.
 * - NATIVE: Uses native OS window blur only when supported; never silently falls back to screenshot blur.
 * - SCREENSHOT: Always uses Lightspeed's screenshot-based clipped backdrop blur workaround (ideal for OEM ROMs with broken native blur).
 */
enum class BackdropBlurMode(val key: String, val label: String) {
    AUTOMATIC(LightspeedPreferences.BLUR_MODE_AUTOMATIC, "Automatic"),
    NATIVE(LightspeedPreferences.BLUR_MODE_NATIVE, "Native"),
    SCREENSHOT(LightspeedPreferences.BLUR_MODE_SCREENSHOT, "Screenshot");

    companion object {
        fun fromKey(key: String?): BackdropBlurMode = when (key) {
            LightspeedPreferences.BLUR_MODE_NATIVE -> NATIVE
            LightspeedPreferences.BLUR_MODE_AUTOMATIC -> AUTOMATIC
            LightspeedPreferences.BLUR_MODE_SCREENSHOT -> SCREENSHOT
            else -> SCREENSHOT
        }
    }
}

/**
 * Global singleton state and controller for Backdrop Blur Mode selection.
 * Persisted under [LightspeedPreferences.KEY_BACKDROP_BLUR_MODE] across all 12 themes.
 */
object BackdropBlurModeManager {
    private val _modeFlow = MutableStateFlow(BackdropBlurMode.SCREENSHOT)
    val modeFlow: StateFlow<BackdropBlurMode> = _modeFlow

    private var initialized = false

    fun getMode(context: Context): BackdropBlurMode {
        if (!initialized) init(context)
        return _modeFlow.value
    }

    fun setMode(context: Context, mode: BackdropBlurMode) {
        context.defaultPrefs().edit()
            .putString(LightspeedPreferences.KEY_BACKDROP_BLUR_MODE, mode.key)
            .apply()
        _modeFlow.value = mode
        initialized = true

        val wm = context.getSystemService(WindowManager::class.java)
        if (shouldUseScreenshotBlur(wm, mode)) {
            CentralCommandBackdropBlur.prepareForCentralCommand(wm, forceRecapture = true)
        } else {
            CentralCommandBackdropBlur.clearBitmap()
        }
        CentralCommandActivity.activeInstance?.updateLiveWindowBlur()
    }

    fun init(context: Context) {
        val stored = context.defaultPrefs().getString(LightspeedPreferences.KEY_BACKDROP_BLUR_MODE, null)
        _modeFlow.value = BackdropBlurMode.fromKey(stored)
        initialized = true
    }

    /**
     * Inspects whether the hardware/firmware genuine native cross-window blur is supported and active.
     */
    fun isNativeBlurSupported(windowManager: WindowManager?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            windowManager?.isCrossWindowBlurEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Determines whether native OS blur should be applied to the Window, given the selected mode
     * and actual runtime capabilities.
     */
    fun shouldUseNativeBlur(windowManager: WindowManager?, mode: BackdropBlurMode): Boolean {
        val nativeSupported = isNativeBlurSupported(windowManager)
        return when (mode) {
            BackdropBlurMode.NATIVE -> nativeSupported
            BackdropBlurMode.SCREENSHOT -> false
            BackdropBlurMode.AUTOMATIC -> nativeSupported
        }
    }

    /**
     * Determines whether the screenshot-based clipped blur capture & layer should run.
     */
    fun shouldUseScreenshotBlur(windowManager: WindowManager?, mode: BackdropBlurMode): Boolean {
        val nativeSupported = isNativeBlurSupported(windowManager)
        return when (mode) {
            BackdropBlurMode.SCREENSHOT -> true
            BackdropBlurMode.NATIVE -> false
            BackdropBlurMode.AUTOMATIC -> !nativeSupported
        }
    }
}

object GlassNoiseTexture {
    private var cachedBrush: ShaderBrush? = null

    fun getBrush(): ShaderBrush {
        cachedBrush?.let { return it }
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = java.util.Random(42L)
        for (i in pixels.indices) {
            val v = (random.nextFloat() * 255).toInt()
            val a = (random.nextFloat() * 160).toInt()
            pixels[i] = android.graphics.Color.argb(a, v, v, v)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        val brush = ShaderBrush(shader)
        cachedBrush = brush
        return brush
    }
}
