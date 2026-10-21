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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
import kotlin.math.roundToInt

/**
 * Fallback backdrop blur engine for devices whose OEM firmware disables SurfaceFlinger
 * cross-window blur (`ro.surface_flinger.supports_background_blur` unset, such as Transsion XOS).
 *
 * When `windowManager.isCrossWindowBlurEnabled` is false on Android 12+ (API 31+), captures a
 * single 1/4-scale display snapshot via [LightspeedAccessibilityService.takeScreenshot] before
 * the Central Command panel animates in, and renders it through a hardware GPU [RenderEffect]
 * Gaussian blur layer reactive to [DeckThemeSnapshot.windowBlurRadiusPx].
 */
object CentralCommandBackdropBlur {
    private const val TAG = "CentralCommandBackdropBlur"
    private const val DOWNSCALE_FACTOR = 0.25f
    private const val PRISM_CAPTURE_SCALE = 0.6f
    private const val CAPTURE_COOLDOWN_MS = 1200L
    private const val CAPTURE_TIMEOUT_MS = 140L

    private val captureExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    var backdropBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    var isCapturing by mutableStateOf(false)
        private set

    @Volatile
    private var lastCaptureTimeMs: Long = 0L

    private val timeoutRunnable = Runnable {
        isCapturing = false
    }

    /**
     * Checks if native cross-window blur is active and inspects the user's BackdropBlurMode;
     * initiates an Accessibility screenshot capture if screenshot blur is required.
     */
    fun prepareForCentralCommand(windowManager: WindowManager?, forceRecapture: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            isCapturing = false
            return
        }
        val service = LightspeedAccessibilityService.instance
        val mode = if (service != null) BackdropBlurModeManager.getMode(service) else BackdropBlurModeManager.modeFlow.value
        val shouldScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, mode)

        if (!shouldScreenshot) {
            backdropBitmap = null
            isCapturing = false
            return
        }

        if (service == null) {
            isCapturing = false
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (!forceRecapture && backdropBitmap != null && (now - lastCaptureTimeMs) < CAPTURE_COOLDOWN_MS) {
            isCapturing = false
            return
        }

        isCapturing = true
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
                                val captureScale = if (DeckThemeManager.styleIdFlow.value == DeckStylePresets.PRISM.styleId) PRISM_CAPTURE_SCALE else DOWNSCALE_FACTOR
                                val targetW = (hwBitmap.width * captureScale).toInt().coerceAtLeast(1)
                                val targetH = (hwBitmap.height * captureScale).toInt().coerceAtLeast(1)
                                val swBitmap = hwBitmap.copy(Bitmap.Config.ARGB_8888, false)
                                hwBitmap.recycle()
                                val scaled = if (swBitmap != null) {
                                    val s = Bitmap.createScaledBitmap(swBitmap, targetW, targetH, true)
                                    if (s !== swBitmap) swBitmap.recycle()
                                    s
                                } else null

                                if (scaled != null) {
                                    val imgBitmap = scaled.asImageBitmap()
                                    lastCaptureTimeMs = android.os.SystemClock.uptimeMillis()
                                    mainHandler.post {
                                        mainHandler.removeCallbacks(timeoutRunnable)
                                        backdropBitmap = imgBitmap
                                        isCapturing = false
                                    }
                                    FlightLog.log(
                                        "central_command",
                                        true,
                                        "OEM cross-window blur disabled; Accessibility GPU blur fallback active (${targetW}x${targetH})"
                                    )
                                    return
                                }
                            }
                        } catch (e: Exception) {
                            logSwallowed(TAG, "onSuccess", e)
                        }
                        mainHandler.post {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            isCapturing = false
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        mainHandler.post {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            isCapturing = false
                        }
                    }
                }
            )
        } catch (e: Exception) {
            logSwallowed(TAG, "takeScreenshot", e)
            mainHandler.removeCallbacks(timeoutRunnable)
            isCapturing = false
        }
    }

    /**
     * Clears any cached backdrop bitmap immediately.
     */
    fun clearBitmap() {
        mainHandler.removeCallbacks(timeoutRunnable)
        backdropBitmap = null
        lastCaptureTimeMs = 0L
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
            lastCaptureTimeMs = 0L
        }
    }
}

/**
 * Renders the hardware-blurred screen snapshot strictly inside a floating card or dialog's
 * clipped bounds, aligned to window coordinates so the card acts as a physical optical lens
 * over the underlying screen while keeping the outer screen backdrop unblurred.
 */
@Composable
fun ClippedFallbackBlurLayer(
    snapshot: DeckThemeSnapshot,
    modifier: Modifier = Modifier
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val context = LocalView.current.context
    val mode = BackdropBlurModeManager.getMode(context)
    if (mode == BackdropBlurMode.NATIVE) return
    if (CentralCommandActivity.isCrossWindowBlurActive && mode != BackdropBlurMode.SCREENSHOT) return
    val bitmap = CentralCommandBackdropBlur.backdropBitmap ?: return
    if (snapshot.styleId == DeckStylePresets.PRISM.styleId && LiquidRefractionProbe.isSupported) {
        LiquidRefractionLayer(bitmap = bitmap, snapshot = snapshot, modifier = modifier)
        return
    }
    val blurRadiusPx = snapshot.windowBlurRadiusPx
    if (blurRadiusPx <= 0) return

    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val view = LocalView.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx().roundToInt() }.coerceAtLeast(1)
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx().roundToInt() }.coerceAtLeast(1)

    val screenLocation = remember { IntArray(2) }
    var screenOffset by remember { mutableStateOf(IntOffset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val effectiveRadius = (blurRadiusPx * 0.35f).coerceIn(2f, 60f)
    val primaryBlurEffect = remember(effectiveRadius) {
        RenderEffect.createBlurEffect(
            effectiveRadius,
            effectiveRadius,
            Shader.TileMode.MIRROR
        ).asComposeRenderEffect()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { coords ->
                view.getLocationOnScreen(screenLocation)
                val posInWindow = coords.positionInWindow()
                val screenX = screenLocation[0] + posInWindow.x.roundToInt()
                val screenY = screenLocation[1] + posInWindow.y.roundToInt()
                screenOffset = IntOffset(screenX, screenY)
            }
    ) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val metrics = view.context.resources.displayMetrics
            val screenW = metrics.widthPixels.coerceAtLeast(1)
            val screenH = metrics.heightPixels.coerceAtLeast(1)
            val scaleX = bitmap.width.toFloat() / screenW
            val scaleY = bitmap.height.toFloat() / screenH

            val srcX = (screenOffset.x * scaleX).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
            val srcY = (screenOffset.y * scaleY).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
            val srcW = (boxSize.width * scaleX).roundToInt().coerceIn(1, bitmap.width - srcX)
            val srcH = (boxSize.height * scaleY).roundToInt().coerceIn(1, bitmap.height - srcY)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        this.renderEffect = primaryBlurEffect
                    }
                    .drawWithContent {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(boxSize.width, boxSize.height)
                        )
                    }
            )

            // Progressive depth second pass (top clearer -> bottom deeper blur) for Glass Lava & Progressive
            if (snapshot.isProgressive) {
                val endRadius = (snapshot.progressiveEndBlurPx * 0.42f).coerceIn(effectiveRadius + 4f, 68f)
                val endBlurEffect = remember(endRadius) {
                    RenderEffect.createBlurEffect(
                        endRadius,
                        endRadius,
                        Shader.TileMode.MIRROR
                    ).asComposeRenderEffect()
                }
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
                                drawImage(
                                    image = bitmap,
                                    srcOffset = IntOffset(srcX, srcY),
                                    srcSize = IntSize(srcW, srcH),
                                    dstOffset = IntOffset.Zero,
                                    dstSize = IntSize(boxSize.width, boxSize.height)
                                )
                            }
                    )
                }
            }
        }
    }
}

