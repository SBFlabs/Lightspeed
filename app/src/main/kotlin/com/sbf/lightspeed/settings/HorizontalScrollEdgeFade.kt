package com.sbf.lightspeed.settings

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Horizontal edge feathering modifier for single-row scrollable chip rails.
 * Mirrors [verticalEdgeFade] with cached left/right [BlendMode.DstOut] alpha ramps.
 */
fun Modifier.horizontalEdgeFade(
    scrollableState: ScrollableState,
    startFeatherDp: Dp = 18.dp,
    endFeatherDp: Dp = 20.dp,
    isRtlLayout: Boolean = false
): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (scrollableState.canScrollBackward || scrollableState.canScrollForward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithCache {
        val leftFeatherPx = minOf((if (isRtlLayout) endFeatherDp else startFeatherDp).toPx(), size.width * 0.30f)
        val rightFeatherPx = minOf((if (isRtlLayout) startFeatherDp else endFeatherDp).toPx(), size.width * 0.30f)
        val rampPx = 18.dp.toPx().coerceAtLeast(1f)
        val leftBrush = Brush.horizontalGradient(
            0.0f to Color.Black,
            0.52f to Color.Black.copy(alpha = 0.52f),
            1.0f to Color.Transparent,
            startX = 0f,
            endX = leftFeatherPx.coerceAtLeast(1f)
        )
        val rightStartX = (size.width - rightFeatherPx).coerceAtLeast(0f)
        val rightBrush = Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.48f to Color.Black.copy(alpha = 0.52f),
            1.0f to Color.Black,
            startX = rightStartX,
            endX = size.width.coerceAtLeast(rightStartX + 1f)
        )
        onDrawWithContent {
            drawContent()
            val backwardAlpha = if (scrollableState.canScrollBackward && scrollableState is ScrollState) {
                (scrollableState.value.toFloat() / rampPx).coerceIn(0f, 1f)
            } else if (scrollableState.canScrollBackward) {
                1f
            } else {
                0f
            }
            val forwardAlpha = if (scrollableState.canScrollForward && scrollableState is ScrollState) {
                val max = scrollableState.maxValue
                if (max == Int.MAX_VALUE) 1f else ((max - scrollableState.value).toFloat() / rampPx).coerceIn(0f, 1f)
            } else if (scrollableState.canScrollForward) {
                1f
            } else {
                0f
            }
            val leftAlpha = if (isRtlLayout) forwardAlpha else backwardAlpha
            val rightAlpha = if (isRtlLayout) backwardAlpha else forwardAlpha

            if (leftFeatherPx > 2f && leftAlpha > 0.004f) {
                drawRect(
                    brush = leftBrush,
                    topLeft = Offset.Zero,
                    size = Size(leftFeatherPx, size.height),
                    alpha = leftAlpha,
                    blendMode = BlendMode.DstOut
                )
            }
            if (rightFeatherPx > 2f && rightAlpha > 0.004f) {
                drawRect(
                    brush = rightBrush,
                    topLeft = Offset(rightStartX, 0f),
                    size = Size(rightFeatherPx, size.height),
                    alpha = rightAlpha,
                    blendMode = BlendMode.DstOut
                )
            }
        }
    }

/**
 * Single-row horizontal scroll with smooth left/right edge feathering.
 * When content overflows horizontally (`state.maxValue > 0`), consumes leftover horizontal
 * drag deltas so scrolling a chip rail never triggers the parent tab pager accidentally.
 */
@Composable
fun Modifier.horizontalScrollWithEdgeFade(
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    startFeatherDp: Dp = 18.dp,
    endFeatherDp: Dp = 20.dp
): Modifier {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pagerIsolation = remember(state) {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (state.maxValue > 0 && source == NestedScrollSource.UserInput) {
                    Offset(x = available.x, y = 0f)
                } else {
                    Offset.Zero
                }
            }
        }
    }
    return this
        .nestedScroll(pagerIsolation)
        .horizontalEdgeFade(
            scrollableState = state,
            startFeatherDp = startFeatherDp,
            endFeatherDp = endFeatherDp,
            isRtlLayout = isRtl
        )
        .horizontalScroll(state = state, enabled = enabled)
}
