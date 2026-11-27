package com.sbf.lightspeed.settings

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.ui.graphics.ShaderBrush
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.reflect.Method

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
            else -> AUTOMATIC
        }
    }
}

/**
 * Global singleton state and controller for Backdrop Blur Mode selection.
 * Persisted under [LightspeedPreferences.KEY_BACKDROP_BLUR_MODE] across all 12 themes.
 */
object BackdropBlurModeManager {
    private const val PROP_SUPPORTS_BACKGROUND_BLUR = "ro.surface_flinger.supports_background_blur"
    private const val PROP_DISABLE_BLURS = "persist.sys.sf.disable_blurs"

    private enum class PropertyCapability { UNSET, SUPPORTED, UNSUPPORTED, UNAVAILABLE }

    private val _modeFlow = MutableStateFlow(BackdropBlurMode.AUTOMATIC)
    val modeFlow: StateFlow<BackdropBlurMode> = _modeFlow

    private var initialized = false

    @Volatile
    private var cachedSurfaceFlingerBlur: PropertyCapability = PropertyCapability.UNSET

    @Volatile
    private var sysPropGetMethod: Method? = null
    @Volatile
    private var sysPropReflectionFailed: Boolean = false

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
        CentralCommandActivity.updateCrossWindowBlurState(shouldUseNativeBlur(wm, mode))
        DeckThemeManager.syncLiveBackdropOnThemeTransition(context = context, forceRecapture = true)
    }

    fun init(context: Context) {
        val stored = context.defaultPrefs().getString(LightspeedPreferences.KEY_BACKDROP_BLUR_MODE, null)
        _modeFlow.value = BackdropBlurMode.fromKey(stored)
        initialized = true
    }

    /**
     * Pre-warms SurfaceFlinger property capability off the UI thread so fallback [getprop]
     * probing can execute safely even if hidden SystemProperties reflection is restricted.
     */
    fun warmCapabilities(context: Context) {
        init(context)
        probeSurfaceFlingerSupportsBlur()
        val wm = context.getSystemService(WindowManager::class.java)
        isNativeBlurSupported(wm)
    }

    private fun readSystemPropertyViaReflection(key: String): String? {
        if (sysPropReflectionFailed) return null
        return try {
            val method = sysPropGetMethod ?: Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java, String::class.java)
                .also { sysPropGetMethod = it }
            (method.invoke(null, key, "") as? String)?.trim()
        } catch (_: Throwable) {
            sysPropReflectionFailed = true
            null
        }
    }

    private fun readSystemPropertyViaGetprop(key: String): String? {
        if (Looper.myLooper() == Looper.getMainLooper()) return null
        return try {
            val process = ProcessBuilder("/system/bin/getprop", key)
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().use { it.readLine() }
            process.waitFor()
            out?.trim() ?: ""
        } catch (_: Exception) {
            null
        }
    }

    private fun isTruthyProp(value: String): Boolean {
        return when (value.trim().lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }
    }

    private fun probeSurfaceFlingerSupportsBlur(): PropertyCapability {
        val existing = cachedSurfaceFlingerBlur
        if (existing != PropertyCapability.UNSET && existing != PropertyCapability.UNAVAILABLE) {
            return existing
        }
        val rawValue = readSystemPropertyViaReflection(PROP_SUPPORTS_BACKGROUND_BLUR)
            ?: readSystemPropertyViaGetprop(PROP_SUPPORTS_BACKGROUND_BLUR)
        val resolved = when {
            rawValue == null -> PropertyCapability.UNAVAILABLE
            isTruthyProp(rawValue) -> PropertyCapability.SUPPORTED
            else -> PropertyCapability.UNSUPPORTED
        }
        if (resolved != PropertyCapability.UNAVAILABLE || existing == PropertyCapability.UNSET) {
            cachedSurfaceFlingerBlur = resolved
        }
        return resolved
    }

    private fun isSurfaceFlingerBlurExplicitlyDisabled(): Boolean {
        val raw = readSystemPropertyViaReflection(PROP_DISABLE_BLURS) ?: return false
        return isTruthyProp(raw)
    }

    /**
     * Inspects whether genuine hardware/firmware native SurfaceFlinger cross-window blur is
     * actually supported and active. Combines Android's [WindowManager.isCrossWindowBlurEnabled]
     * with SurfaceFlinger's `ro.surface_flinger.supports_background_blur` capability gate so
     * firmware that flips WindowManager's flag without enabling SurfaceFlinger blur (e.g. TCL)
     * falls back cleanly instead of forcing slow GPU client composition.
     */
    fun isNativeBlurSupported(windowManager: WindowManager?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val wmEnabled = try {
            windowManager?.isCrossWindowBlurEnabled == true
        } catch (_: Exception) {
            false
        }
        if (!wmEnabled) return false
        if (isSurfaceFlingerBlurExplicitlyDisabled()) return false
        return when (probeSurfaceFlingerSupportsBlur()) {
            PropertyCapability.UNSUPPORTED -> false
            PropertyCapability.SUPPORTED,
            PropertyCapability.UNAVAILABLE,
            PropertyCapability.UNSET -> true
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

    /**
     * Synchronizes [WindowManager.LayoutParams.FLAG_DIM_BEHIND] alongside [Window.setDimAmount]
     * so translucent activities (whose theme sets `android:backgroundDimEnabled=false`) and
     * OEM compositors like Samsung One UI honor live backdrop opacity updates reliably.
     */
    fun applyWindowDimAmount(window: Window?, dimAmount: Float) {
        if (window == null) return
        val clamped = dimAmount.coerceIn(0f, 0.95f)
        val hasDimFlag = ((window.attributes?.flags ?: 0) and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
        if (clamped > 0.001f) {
            if (!hasDimFlag) window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        } else {
            if (hasDimFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        window.setDimAmount(clamped)
    }

    /**
     * Shared window flag synchronization for modal dialogs, dropdown menus, and standalone deck
     * surfaces. Prevents unapproved raw `FLAG_BLUR_BEHIND` requests from forcing slow GPU
     * composition when native SurfaceFlinger blur is unsupported or disabled by [mode].
     */
    fun applyWindowBlurFlags(
        view: View,
        blurRadiusPx: Int,
        mode: BackdropBlurMode,
        window: Window? = null,
        clampMinRadius: Int = 20,
        clampMaxRadius: Int = 150
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        val useNative = shouldUseNativeBlur(wm, mode) && blurRadiusPx > 0
        val targetRadius = if (useNative) blurRadiusPx.coerceIn(clampMinRadius, clampMaxRadius) else 0
        if (window != null) {
            val lp = window.attributes
            val hasFlag = ((lp?.flags ?: 0) and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            if (useNative) {
                if (!hasFlag) window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (lp != null && lp.blurBehindRadius != targetRadius) {
                    lp.blurBehindRadius = targetRadius
                    window.attributes = lp
                }
            } else {
                if (hasFlag) window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                if (lp != null && lp.blurBehindRadius != 0) {
                    lp.blurBehindRadius = 0
                    window.attributes = lp
                }
            }
            return
        }
        val rootView = view.rootView
        val lp = rootView.layoutParams as? WindowManager.LayoutParams ?: return
        val hasFlag = (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
        if (useNative && (!hasFlag || lp.blurBehindRadius != targetRadius)) {
            lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            lp.blurBehindRadius = targetRadius
            try { wm?.updateViewLayout(rootView, lp) } catch (e: Exception) { logSwallowed("BackdropBlurModeManager", "applyWindowBlurFlags", e) }
        } else if (!useNative && (hasFlag || lp.blurBehindRadius != 0)) {
            lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND.inv()
            lp.blurBehindRadius = 0
            try { wm?.updateViewLayout(rootView, lp) } catch (e: Exception) { logSwallowed("BackdropBlurModeManager", "applyWindowBlurFlags", e) }
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
