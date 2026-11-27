package com.sbf.lightspeed.settings

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

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
        LiquidRefractionLayer(
            bitmap = bitmap,
            snapshot = snapshot,
            modifier = modifier,
            downscaleRatio = downscale
        )
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
