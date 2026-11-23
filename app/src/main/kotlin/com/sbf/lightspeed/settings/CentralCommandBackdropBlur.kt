package com.sbf.lightspeed.settings

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.FlightLog
import com.sbf.lightspeed.system.logSwallowed
import java.util.concurrent.Executors

/**
 * CompositionLocal providing a live lambda for the card's vertical drag offset [dragOffsetY.value].
 * Read directly inside [Modifier.drawWithContent] to achieve synchronous 120Hz frame-0 drag alignment
 * with zero recomposition and zero layout lag.
 */
val LocalDeckCardDragOffsetY = staticCompositionLocalOf<() -> Float> { { 0f } }

/**
 * Fallback backdrop blur engine for devices whose OEM firmware disables SurfaceFlinger
 * cross-window blur (`ro.surface_flinger.supports_background_blur` unset, such as Transsion XOS).
 *
 * When `windowManager.isCrossWindowBlurEnabled` is false on Android 12+ (API 31+), captures a
 * single native display snapshot via [LightspeedAccessibilityService.takeScreenshot] before
 * the Central Command panel animates in, and renders it through a hardware GPU [RenderEffect]
 * Gaussian blur layer reactive to [DeckThemeSnapshot.windowBlurRadiusPx].
 */
object CentralCommandBackdropBlur {
    private const val TAG = "CentralCommandBackdropBlur"
    private const val CAPTURE_COOLDOWN_MS = 1200L
    private const val CAPTURE_TIMEOUT_MS = 450L

    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var backdropBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var backdropBlurBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    @Volatile
    var blurDownscaleRatio: Float = 1f
        private set

    @Volatile
    private var quarterBackdropBitmap: Bitmap? = null
    @Volatile
    private var cachedCanvasBlurBitmap: Bitmap? = null
    @Volatile
    private var cachedCanvasBlurRadiusBucket: Int = -1
    @Volatile
    private var hudQuarterBackdropBitmap: Bitmap? = null
    @Volatile
    private var hudCachedCanvasBlurBitmap: Bitmap? = null
    @Volatile
    private var hudCachedCanvasBlurRadiusBucket: Int = -1
    @Volatile
    var isHudCapturing: Boolean = false
        private set
    @Volatile
    private var lastHudCaptureTimeMs: Long = 0L
    @Volatile
    private var cachedDenseHudBlurBitmap: Bitmap? = null
    @Volatile
    var capturedDisplayWidthPx: Int = 0
        private set
    @Volatile
    var capturedDisplayHeightPx: Int = 0
        private set

    var isCapturing by mutableStateOf(false)
        private set

    @Volatile
    private var lastCaptureTimeMs: Long = 0L

    private val readyCallbacks = mutableListOf<() -> Unit>()
    private val registeredHostWindows = java.util.WeakHashMap<android.view.Window, Boolean>()
    private val hiddenHostWindows = mutableMapOf<android.view.Window, Float>()

    fun registerHostWindow(window: android.view.Window?) {
        if (window == null) return
        synchronized(registeredHostWindows) {
            registeredHostWindows[window] = true
        }
    }

    fun unregisterHostWindow(window: android.view.Window?) {
        if (window == null) return
        synchronized(registeredHostWindows) {
            registeredHostWindows.remove(window)
        }
        hiddenHostWindows.remove(window)
    }

    fun hasRegisteredHostWindows(): Boolean {
        return synchronized(registeredHostWindows) {
            registeredHostWindows.isNotEmpty()
        }
    }

    private fun hideRegisteredHostWindowsForCapture() {
        val windows = synchronized(registeredHostWindows) { registeredHostWindows.keys.toList() }
        for (w in windows) {
            try {
                val decor = w.decorView
                if (decor.visibility == android.view.View.VISIBLE) {
                    val prevDim = w.attributes?.dimAmount ?: 0f
                    hiddenHostWindows[w] = prevDim
                    decor.visibility = android.view.View.INVISIBLE
                    w.setDimAmount(0f)
                }
            } catch (_: Exception) {}
        }
    }

    private fun restoreHiddenHostWindows() {
        if (hiddenHostWindows.isEmpty()) return
        val snapshotHidden = hiddenHostWindows.entries.toList()
        hiddenHostWindows.clear()
        for ((w, prevDim) in snapshotHidden) {
            try {
                w.decorView.visibility = android.view.View.VISIBLE
                w.setDimAmount(prevDim)
            } catch (_: Exception) {}
        }
        CentralCommandActivity.activeInstance?.updateLiveWindowBlur()
    }

    private val timeoutRunnable = Runnable {
        isCapturing = false
        isHudCapturing = false
        restoreHiddenHostWindows()
        flushReadyCallbacks()
    }

    private fun flushReadyCallbacks() {
        restoreHiddenHostWindows()
        val list = synchronized(readyCallbacks) {
            if (readyCallbacks.isEmpty()) emptyList()
            else readyCallbacks.toList().also { readyCallbacks.clear() }
        }
        list.forEach { it.invoke() }
    }

    /**
     * Proactively pre-captures the screen backdrop for Canvas Scrubber HUDs when a user's finger
     * first touches the Deflector edge or Status Bar Sensor strip (`ACTION_DOWN`), isolated from
     * Central Command's window `backdropBitmap` so Central Command's own blur never flickers.
     */
    fun prewarmForGesture(context: android.content.Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (backdropBitmap != null && quarterBackdropBitmap != null) return
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? WindowManager ?: return
        val mode = BackdropBlurModeManager.getMode(context)
        if (!BackdropBlurModeManager.shouldUseScreenshotBlur(wm, mode)) return
        val now = android.os.SystemClock.uptimeMillis()
        if (hudQuarterBackdropBitmap != null && (now - lastHudCaptureTimeMs) < CAPTURE_COOLDOWN_MS) return
        if (isHudCapturing || isCapturing) return
        prepareForHudOnly(wm)
    }

    /**
     * Captures a 1/4-res blurred software bitmap exclusively for Canvas Scrubber HUDs without
     * mutating [backdropBitmap] or [isCapturing] (preserving Central Command's active window blur).
     */
    fun prepareForHudOnly(
        windowManager: WindowManager?,
        onReady: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            isHudCapturing = false
            onReady?.invoke()
            return
        }
        if (backdropBitmap != null && quarterBackdropBitmap != null) {
            isHudCapturing = false
            onReady?.invoke()
            return
        }
        val service = LightspeedAccessibilityService.instance
        val mode = if (service != null) BackdropBlurModeManager.getMode(service) else BackdropBlurModeManager.modeFlow.value
        val shouldScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, mode)
        if (!shouldScreenshot || service == null) {
            isHudCapturing = false
            onReady?.invoke()
            return
        }
        val now = android.os.SystemClock.uptimeMillis()
        if (hudQuarterBackdropBitmap != null && (now - lastHudCaptureTimeMs) < CAPTURE_COOLDOWN_MS) {
            isHudCapturing = false
            onReady?.invoke()
            return
        }
        if (onReady != null) {
            synchronized(readyCallbacks) { readyCallbacks.add(onReady) }
        }
        if (isHudCapturing) return

        isHudCapturing = true
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS)

        try {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                captureExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        try {
                            val hwBuffer = screenshot.hardwareBuffer
                            val colorSpace = screenshot.colorSpace
                            val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                            hwBuffer.close()
                            if (hwBitmap != null) {
                                val fullW = hwBitmap.width
                                val fullH = hwBitmap.height
                                val qW = (fullW / 4).coerceAtLeast(1)
                                val qH = (fullH / 4).coerceAtLeast(1)
                                val swSmall = try {
                                    val scaledHw = Bitmap.createScaledBitmap(hwBitmap, qW, qH, true)
                                    val sw = scaledHw.copy(Bitmap.Config.ARGB_8888, false)
                                    if (scaledHw !== hwBitmap && scaledHw !== sw) scaledHw.recycle()
                                    sw
                                } catch (_: Exception) {
                                    try {
                                        val sw = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                        val scaled = Bitmap.createScaledBitmap(sw, qW, qH, true)
                                        if (scaled !== sw) sw.recycle()
                                        scaled
                                    } catch (_: Exception) { null }
                                }
                                hwBitmap.recycle()
                                val defaultRadiusPx = DeckThemeManager.snapshotFlow.value.windowBlurRadiusPx.toFloat()
                                val preBucket = (defaultRadiusPx / 8f).roundToInt().coerceIn(1, 20)
                                val preBlurred = if (swSmall != null) {
                                    try {
                                        val p1 = fastBoxBlur(swSmall, (preBucket * 2).coerceIn(2, 24))
                                        fastBoxBlur(p1, preBucket.coerceIn(2, 16))
                                    } catch (_: Exception) { null }
                                } else null
                                lastHudCaptureTimeMs = android.os.SystemClock.uptimeMillis()
                                mainHandler.post {
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    capturedDisplayWidthPx = fullW
                                    capturedDisplayHeightPx = fullH
                                    hudQuarterBackdropBitmap = swSmall
                                    hudCachedCanvasBlurBitmap = preBlurred
                                    hudCachedCanvasBlurRadiusBucket = if (preBlurred != null) preBucket else -1
                                    isHudCapturing = false
                                    flushReadyCallbacks()
                                }
                                return
                            }
                        } catch (e: Exception) {
                            logSwallowed(TAG, "prepareForHudOnly", e)
                        }
                        mainHandler.post {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            isHudCapturing = false
                            flushReadyCallbacks()
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        mainHandler.post {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            isHudCapturing = false
                            flushReadyCallbacks()
                        }
                    }
                }
            )
        } catch (e: Exception) {
            logSwallowed(TAG, "prepareForHudOnly", e)
            mainHandler.removeCallbacks(timeoutRunnable)
            isHudCapturing = false
            flushReadyCallbacks()
        }
    }

    fun onHudHidden() {
        isHudCapturing = false
        hudQuarterBackdropBitmap = null
        hudCachedCanvasBlurBitmap = null
        hudCachedCanvasBlurRadiusBucket = -1
        lastHudCaptureTimeMs = 0L
    }

    /**
     * Checks if native cross-window blur is active and inspects the user's BackdropBlurMode;
     * initiates an Accessibility screenshot capture if screenshot blur is required.
     */
    fun prepareForCentralCommand(
        windowManager: WindowManager?,
        forceRecapture: Boolean = false,
        settleDelayMs: Long = 20L,
        hideActiveHosts: Boolean = false,
        ignoreZeroThemeBlur: Boolean = false,
        onReady: (() -> Unit)? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            isCapturing = false
            onReady?.invoke()
            return
        }
        val service = LightspeedAccessibilityService.instance
        val mode = if (service != null) BackdropBlurModeManager.getMode(service) else BackdropBlurModeManager.modeFlow.value
        val activeThemeBlur = DeckThemeManager.snapshotFlow.value.windowBlurRadiusPx
        val shouldScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, mode) &&
            (ignoreZeroThemeBlur || activeThemeBlur > 0)

        if (!shouldScreenshot) {
            backdropBitmap = null
            backdropBlurBitmap = null
            blurDownscaleRatio = 1f
            quarterBackdropBitmap = null
            cachedCanvasBlurBitmap = null
            cachedCanvasBlurRadiusBucket = -1
            cachedDenseHudBlurBitmap = null
            isCapturing = false
            restoreHiddenHostWindows()
            onReady?.invoke()
            return
        }

        if (service == null) {
            isCapturing = false
            restoreHiddenHostWindows()
            onReady?.invoke()
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (!forceRecapture && backdropBitmap != null && (now - lastCaptureTimeMs) < CAPTURE_COOLDOWN_MS) {
            isCapturing = false
            onReady?.invoke()
            return
        }

        if (onReady != null) {
            synchronized(readyCallbacks) { readyCallbacks.add(onReady) }
        }
        if (isCapturing && !forceRecapture) return

        isCapturing = true
        if (hideActiveHosts) {
            hideRegisteredHostWindowsForCapture()
        }
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, CAPTURE_TIMEOUT_MS + if (hideActiveHosts) settleDelayMs else 0L)

        val captureTask = Runnable {
            try {
                val currentService = LightspeedAccessibilityService.instance ?: run {
                    isCapturing = false
                    flushReadyCallbacks()
                    return@Runnable
                }
                currentService.takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    captureExecutor,
                    object : AccessibilityService.TakeScreenshotCallback {
                        override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                            try {
                                val hwBuffer = screenshot.hardwareBuffer
                                val colorSpace = screenshot.colorSpace
                                val hwBitmap = Bitmap.wrapHardwareBuffer(hwBuffer, colorSpace)
                                hwBuffer.close()
                                if (hwBitmap != null) {
                                    val fullW = hwBitmap.width
                                    val fullH = hwBitmap.height
                                    val imgBitmap = hwBitmap.asImageBitmap()
                                    val blurW = (fullW / 3).coerceAtLeast(1)
                                    val blurH = (fullH / 3).coerceAtLeast(1)
                                    val imgBlurScaled = try {
                                        val scaledForBlur = Bitmap.createScaledBitmap(hwBitmap, blurW, blurH, true)
                                        scaledForBlur.asImageBitmap() to (blurW.toFloat() / fullW.coerceAtLeast(1).toFloat())
                                    } catch (_: Exception) {
                                        null
                                    }
                                    val qW = (fullW / 4).coerceAtLeast(1)
                                    val qH = (fullH / 4).coerceAtLeast(1)
                                    val swSmall = try {
                                        val scaledHw = Bitmap.createScaledBitmap(hwBitmap, qW, qH, true)
                                        val sw = scaledHw.copy(Bitmap.Config.ARGB_8888, false)
                                        if (scaledHw !== hwBitmap && scaledHw !== sw) scaledHw.recycle()
                                        sw
                                    } catch (_: Exception) {
                                        try {
                                            val sw = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                            val scaled = Bitmap.createScaledBitmap(sw, qW, qH, true)
                                            if (scaled !== sw) sw.recycle()
                                            scaled
                                        } catch (_: Exception) { null }
                                    }
                                    val finalBlurImg = imgBlurScaled?.first ?: swSmall?.asImageBitmap()
                                    val finalBlurScale = imgBlurScaled?.second
                                        ?: if (swSmall != null) qW.toFloat() / fullW.coerceAtLeast(1).toFloat() else 1f
                                    val defaultRadiusPx = DeckThemeManager.snapshotFlow.value.windowBlurRadiusPx.toFloat()
                                    val preBucket = (defaultRadiusPx / 8f).roundToInt().coerceIn(1, 20)
                                    val preBlurred = if (swSmall != null) {
                                        try {
                                            val p1 = fastBoxBlur(swSmall, (preBucket * 2).coerceIn(2, 24))
                                            fastBoxBlur(p1, preBucket.coerceIn(2, 16))
                                        } catch (_: Exception) { null }
                                    } else null
                                    lastCaptureTimeMs = android.os.SystemClock.uptimeMillis()
                                    mainHandler.post {
                                        mainHandler.removeCallbacks(timeoutRunnable)
                                        capturedDisplayWidthPx = fullW
                                        capturedDisplayHeightPx = fullH
                                        quarterBackdropBitmap = swSmall
                                        cachedCanvasBlurBitmap = preBlurred
                                        cachedCanvasBlurRadiusBucket = if (preBlurred != null) preBucket else -1
                                        cachedDenseHudBlurBitmap = null
                                        backdropBlurBitmap = finalBlurImg
                                        blurDownscaleRatio = finalBlurScale
                                        backdropBitmap = imgBitmap
                                        isCapturing = false
                                        flushReadyCallbacks()
                                    }
                                    FlightLog.log(
                                        "central_command",
                                        true,
                                        "OEM cross-window blur fallback active: native 1:1 hardware buffer (${hwBitmap.width}x${hwBitmap.height})"
                                    )
                                    return
                                }
                            } catch (e: Exception) {
                                logSwallowed(TAG, "onSuccess", e)
                            }
                            mainHandler.post {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                isCapturing = false
                                flushReadyCallbacks()
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            mainHandler.post {
                                mainHandler.removeCallbacks(timeoutRunnable)
                                isCapturing = false
                                flushReadyCallbacks()
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                logSwallowed(TAG, "takeScreenshot", e)
                mainHandler.removeCallbacks(timeoutRunnable)
                isCapturing = false
                flushReadyCallbacks()
            }
        }

        // Settle delay (when hiding active host windows wait 85ms so compositor draws clean behind-app pixels)
        val effectiveDelay = if (hideActiveHosts) maxOf(settleDelayMs, 85L) else settleDelayMs
        if (effectiveDelay <= 0L) {
            captureTask.run()
        } else {
            mainHandler.postDelayed(captureTask, effectiveDelay)
        }
    }

    /**
     * Clears any cached backdrop bitmap immediately.
     */
    fun clearBitmap() {
        mainHandler.removeCallbacks(timeoutRunnable)
        backdropBitmap = null
        backdropBlurBitmap = null
        blurDownscaleRatio = 1f
        quarterBackdropBitmap = null
        cachedCanvasBlurBitmap = null
        cachedCanvasBlurRadiusBucket = -1
        cachedDenseHudBlurBitmap = null
        lastCaptureTimeMs = 0L
        flushReadyCallbacks()
    }

    /**
     * Releases the cached snapshot when Central Command closes so the next launch
     * captures the newly visible foreground app or wallpaper.
     */
    fun onWindowHidden(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations) {
            mainHandler.removeCallbacks(timeoutRunnable)
            isCapturing = false
            backdropBitmap = null
            backdropBlurBitmap = null
            blurDownscaleRatio = 1f
            quarterBackdropBitmap = null
            cachedCanvasBlurBitmap = null
            cachedCanvasBlurRadiusBucket = -1
            cachedDenseHudBlurBitmap = null
            lastCaptureTimeMs = 0L
            flushReadyCallbacks()
        }
    }

    /**
     * Returns an ultra-dense (~1/10th screen resolution, dual-pass blurred) ambient color cloud
     * [Bitmap] for Deflector Scrubber HUDs (Option A: frosted control center style).
     * Dissolves sharp video details into pure ambient color clouds so blending at ~45% opacity
     * lets live video motion underneath breathe through without looking frozen in time.
     */
    fun getDenseHudBackdropBitmap(): Bitmap? {
        cachedDenseHudBlurBitmap?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        val src = quarterBackdropBitmap ?: return null
        return try {
            val tinyW = (src.width * 2 / 5).coerceAtLeast(16)
            val tinyH = (src.height * 2 / 5).coerceAtLeast(16)
            val tiny = Bitmap.createScaledBitmap(src, tinyW, tinyH, true)
            val pass1 = fastBoxBlur(tiny, 12)
            val pass2 = fastBoxBlur(pass1, 10)
            cachedDenseHudBlurBitmap = pass2
            pass2
        } catch (_: Exception) {
            null
        }
    }

    fun hasHudBackdrop(): Boolean = hudQuarterBackdropBitmap != null || quarterBackdropBitmap != null

    /**
     * Returns a cached 1/4-resolution blurred software [Bitmap] for Canvas-based Scrubber HUDs.
     * Uses fast separable box blur on the already-downscaled snapshot for 120Hz zero-allocation draw.
     */
    fun getBlurredCanvasBitmap(radiusPx: Float): Bitmap? {
        val bucket = (radiusPx / 8f).roundToInt().coerceIn(1, 20)
        val hudSrc = hudQuarterBackdropBitmap
        if (hudSrc != null && !hudSrc.isRecycled) {
            hudCachedCanvasBlurBitmap?.let { cached ->
                if (!cached.isRecycled && hudCachedCanvasBlurRadiusBucket == bucket) return cached
            }
            val p1 = fastBoxBlur(hudSrc, (bucket * 2).coerceIn(2, 24))
            val blurred = fastBoxBlur(p1, bucket.coerceIn(2, 16))
            hudCachedCanvasBlurBitmap = blurred
            hudCachedCanvasBlurRadiusBucket = bucket
            return blurred
        }
        val src = quarterBackdropBitmap ?: return null
        cachedCanvasBlurBitmap?.let { cached ->
            if (!cached.isRecycled && cachedCanvasBlurRadiusBucket == bucket) return cached
        }
        val p1 = fastBoxBlur(src, (bucket * 2).coerceIn(2, 24))
        val blurred = fastBoxBlur(p1, bucket.coerceIn(2, 16))
        cachedCanvasBlurBitmap = blurred
        cachedCanvasBlurRadiusBucket = bucket
        return blurred
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val pix = IntArray(w * h)
        out.getPixels(pix, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val div = radius * 2 + 1
        // Horizontal pass
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0
            val rowOff = y * w
            for (k in -radius..radius) {
                val c = pix[rowOff + k.coerceIn(0, w - 1)]
                rSum += (c shr 16) and 0xff
                gSum += (c shr 8) and 0xff
                bSum += c and 0xff
            }
            for (x in 0 until w) {
                tmp[rowOff + x] = (0xff shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val addIdx = (x + radius + 1).coerceAtMost(w - 1)
                val subIdx = (x - radius).coerceAtLeast(0)
                val cAdd = pix[rowOff + addIdx]
                val cSub = pix[rowOff + subIdx]
                rSum += ((cAdd shr 16) and 0xff) - ((cSub shr 16) and 0xff)
                gSum += ((cAdd shr 8) and 0xff) - ((cSub shr 8) and 0xff)
                bSum += (cAdd and 0xff) - (cSub and 0xff)
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (k in -radius..radius) {
                val c = tmp[k.coerceIn(0, h - 1) * w + x]
                rSum += (c shr 16) and 0xff
                gSum += (c shr 8) and 0xff
                bSum += c and 0xff
            }
            for (y in 0 until h) {
                pix[y * w + x] = (0xff shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val addIdx = (y + radius + 1).coerceAtMost(h - 1)
                val subIdx = (y - radius).coerceAtLeast(0)
                val cAdd = tmp[addIdx * w + x]
                val cSub = tmp[subIdx * w + x]
                rSum += ((cAdd shr 16) and 0xff) - ((cSub shr 16) and 0xff)
                gSum += ((cAdd shr 8) and 0xff) - ((cSub shr 8) and 0xff)
                bSum += (cAdd and 0xff) - (cSub and 0xff)
            }
        }
        out.setPixels(pix, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Applies 12-theme Backdrop Blur Engine (Native, Screenshot, Shader, or Automatic)
     * to standalone floating activities such as Watchdog & Perimeter decks.
     */
    fun applyStandaloneActivityBlur(activity: android.app.Activity) {
        val window = activity.window ?: return
        val wm = activity.windowManager ?: return
        val snapshot = DeckThemeManager.getSnapshot(activity)
        val blurMode = BackdropBlurModeManager.getMode(activity)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nativeSupported = BackdropBlurModeManager.isNativeBlurSupported(wm)
            val useNative = BackdropBlurModeManager.shouldUseNativeBlur(wm, blurMode)
            val useScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(wm, blurMode)
            CentralCommandActivity.updateCrossWindowBlurState(nativeSupported && blurMode != BackdropBlurMode.SCREENSHOT)
            if (useNative) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            val lp = window.attributes
            lp.blurBehindRadius = if (useNative) snapshot.windowBlurRadiusPx.coerceIn(0, 150) else 0
            window.attributes = lp
            BackdropBlurModeManager.applyWindowDimAmount(window, if (useScreenshot && backdropBitmap != null) 0f else snapshot.dimAmount)
        } else {
            CentralCommandActivity.updateCrossWindowBlurState(false)
            BackdropBlurModeManager.applyWindowDimAmount(window, snapshot.dimAmount)
        }
    }
}

/**
 * Renders the [BackdropBlurMode.SCREENSHOT] / [BackdropBlurMode.AUTOMATIC] fallback:
 * Hardware-blurred screen snapshot strictly inside a floating card or dialog's clipped bounds,
 * aligned to window coordinates.
 */
@Composable
@android.annotation.SuppressLint("NewApi") // LiquidRefractionProbe.isSupported is false below API 33
fun ClippedFallbackBlurLayer(
    snapshot: DeckThemeSnapshot,
    modifier: Modifier = Modifier,
    useLightweightHudBlur: Boolean = false
) {
    val context = LocalView.current.context
    val mode = BackdropBlurModeManager.getMode(context)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    if (mode == BackdropBlurMode.NATIVE) return
    if (CentralCommandActivity.isCrossWindowBlurActive && mode != BackdropBlurMode.SCREENSHOT) return
    if (!useLightweightHudBlur && snapshot.windowBlurRadiusPx <= 0) return

    val view = LocalView.current
    val densityPx = LocalDensity.current.density
    val dragOffsetYProvider = LocalDeckCardDragOffsetY.current

    if (useLightweightHudBlur) {
        val floorPx = maxOf(snapshot.windowBlurRadiusPx.toFloat(), 28f * densityPx)
        val lightBmp = remember(CentralCommandBackdropBlur.backdropBitmap, floorPx) {
            CentralCommandBackdropBlur.getBlurredCanvasBitmap(floorPx)?.asImageBitmap()
        } ?: CentralCommandBackdropBlur.backdropBlurBitmap
            ?: CentralCommandBackdropBlur.backdropBitmap
            ?: return
        val fullW = CentralCommandBackdropBlur.capturedDisplayWidthPx.takeIf { it > 0 }?.toFloat()
            ?: lightBmp.width.toFloat().coerceAtLeast(1f)
        val fullH = CentralCommandBackdropBlur.capturedDisplayHeightPx.takeIf { it > 0 }?.toFloat()
            ?: lightBmp.height.toFloat().coerceAtLeast(1f)
        val lightScaleX = lightBmp.width.toFloat() / fullW
        val lightScaleY = lightBmp.height.toFloat() / fullH
        val screenLocation = remember { IntArray(2) }
        var basePosX by remember { mutableIntStateOf(Int.MIN_VALUE) }
        var basePosY by remember { mutableIntStateOf(Int.MIN_VALUE) }
        Box(
            modifier = modifier
                .fillMaxSize()
                .onSizeChanged {
                    basePosX = Int.MIN_VALUE
                    basePosY = Int.MIN_VALUE
                }
                .onGloballyPositioned { coords ->
                    view.getLocationOnScreen(screenLocation)
                    val posInWindow = coords.positionInWindow()
                    val currentDrag = dragOffsetYProvider().roundToInt()
                    val newX = screenLocation[0] + posInWindow.x.roundToInt()
                    val newY = screenLocation[1] + posInWindow.y.roundToInt() - currentDrag
                    if (basePosX != newX) basePosX = newX
                    if (basePosY != newY) basePosY = newY
                }
                .drawWithContent {
                    if (basePosX == Int.MIN_VALUE || basePosY == Int.MIN_VALUE) return@drawWithContent
                    val currentDrag = dragOffsetYProvider().roundToInt()
                    val screenX = basePosX
                    val screenY = basePosY + currentDrag
                    val srcX = (screenX * lightScaleX).roundToInt().coerceIn(0, (lightBmp.width - 1).coerceAtLeast(0))
                    val srcY = (screenY * lightScaleY).roundToInt().coerceIn(0, (lightBmp.height - 1).coerceAtLeast(0))
                    val srcW = (size.width * lightScaleX).roundToInt().coerceIn(1, (lightBmp.width - srcX).coerceAtLeast(1))
                    val srcH = (size.height * lightScaleY).roundToInt().coerceIn(1, (lightBmp.height - srcY).coerceAtLeast(1))
                    drawImage(
                        image = lightBmp,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium
                    )
                }
        )
        return
    }

    val bitmap = CentralCommandBackdropBlur.backdropBlurBitmap
        ?: CentralCommandBackdropBlur.backdropBitmap
        ?: return
    val downscale = if (bitmap === CentralCommandBackdropBlur.backdropBlurBitmap) {
        CentralCommandBackdropBlur.blurDownscaleRatio
    } else 1f
    if (snapshot.styleId == DeckStylePresets.PRISM.styleId && LiquidRefractionProbe.isSupported) {
        val fullBitmap = CentralCommandBackdropBlur.backdropBitmap ?: bitmap
        LiquidRefractionLayer(bitmap = fullBitmap, snapshot = snapshot, modifier = modifier)
        return
    }
    val blurRadiusPx = snapshot.windowBlurRadiusPx
    if (blurRadiusPx <= 0) return

    val screenLocation = remember { IntArray(2) }
    var basePosX by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var basePosY by remember { mutableIntStateOf(Int.MIN_VALUE) }

    val effectiveRadius = (blurRadiusPx * 0.70f * downscale).coerceIn(3f, 32f)
    val primaryBlurEffect = remember(effectiveRadius) {
        RenderEffect.createBlurEffect(
            effectiveRadius,
            effectiveRadius,
            Shader.TileMode.CLAMP
        ).asComposeRenderEffect()
    }

    val endBlurEffect = if (snapshot.isProgressive) {
        val endRadius = (snapshot.progressiveEndBlurPx * 0.80f * downscale).coerceIn(effectiveRadius + 2f, 40f)
        remember(endRadius) {
            RenderEffect.createBlurEffect(
                endRadius,
                endRadius,
                Shader.TileMode.CLAMP
            ).asComposeRenderEffect()
        }
    } else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged {
                basePosX = Int.MIN_VALUE
                basePosY = Int.MIN_VALUE
            }
            .onGloballyPositioned { coords ->
                view.getLocationOnScreen(screenLocation)
                val posInWindow = coords.positionInWindow()
                val currentDrag = dragOffsetYProvider().roundToInt()
                val newX = screenLocation[0] + posInWindow.x.roundToInt()
                val newY = screenLocation[1] + posInWindow.y.roundToInt() - currentDrag
                if (basePosX != newX) basePosX = newX
                if (basePosY != newY) basePosY = newY
            }
    ) {
        // Primary Frosted Blur Pass: synchronous draw-time frame-0 drag alignment on scaled texture
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    this.renderEffect = primaryBlurEffect
                }
                .drawWithContent {
                    if (basePosX == Int.MIN_VALUE || basePosY == Int.MIN_VALUE) return@drawWithContent
                    val currentDrag = dragOffsetYProvider().roundToInt()
                    val screenX = basePosX
                    val screenY = basePosY + currentDrag

                    val srcX = (screenX * downscale).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                    val srcY = (screenY * downscale).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                    val srcW = (size.width * downscale).roundToInt().coerceIn(1, (bitmap.width - srcX).coerceAtLeast(1))
                    val srcH = (size.height * downscale).roundToInt().coerceIn(1, (bitmap.height - srcY).coerceAtLeast(1))

                    drawImage(
                        image = bitmap,
                        srcOffset = IntOffset(srcX, srcY),
                        srcSize = IntSize(srcW, srcH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.Medium
                    )
                }
        )

        // Progressive depth second pass (top clearer -> bottom deeper blur) for Glass Lava & Progressive
        if (snapshot.isProgressive && endBlurEffect != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.35f to Color.Black.copy(alpha = 0.45f),
                                1.0f to Color.Black
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            this.renderEffect = endBlurEffect
                        }
                        .drawWithContent {
                            if (basePosX == Int.MIN_VALUE || basePosY == Int.MIN_VALUE) return@drawWithContent
                            val currentDrag = dragOffsetYProvider().roundToInt()
                            val screenX = basePosX
                            val screenY = basePosY + currentDrag

                            val srcX = (screenX * downscale).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                            val srcY = (screenY * downscale).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                            val srcW = (size.width * downscale).roundToInt().coerceIn(1, (bitmap.width - srcX).coerceAtLeast(1))
                            val srcH = (size.height * downscale).roundToInt().coerceIn(1, (bitmap.height - srcY).coerceAtLeast(1))

                            drawImage(
                                image = bitmap,
                                srcOffset = IntOffset(srcX, srcY),
                                srcSize = IntSize(srcW, srcH),
                                dstOffset = IntOffset.Zero,
                                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                                filterQuality = FilterQuality.Medium
                            )
                        }
                )
            }
        }
    }
}

