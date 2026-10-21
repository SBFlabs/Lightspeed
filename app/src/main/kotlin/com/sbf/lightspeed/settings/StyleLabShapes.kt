package com.sbf.lightspeed.settings

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val JELLY_SUPERELLIPSE_EXPONENT = 3.35f
private const val JELLY_STEPS = 72

/**
 * Builds the hero card outline Path for the given styleId inside [iconRect].
 * Default styles use a rounded rectangle; "jelly" uses a soft superellipse blob
 * (exponent ~3.35, slightly wider than tall, with a gentle upper-cheek bulge).
 */
internal fun buildHeroCardPath(
    styleId: String,
    iconRect: Rect,
    iconCr: CornerRadius
): Path {
    if (styleId != "jelly") {
        return Path().apply {
            addRoundRect(RoundRect(iconRect, iconCr))
        }
    }
    return buildJellyBlobPath(iconRect, insetPx = 0f)
}

/**
 * Builds an inset hero card outline Path for inner glow strokes.
 */
internal fun buildInsetHeroCardPath(
    styleId: String,
    iconRect: Rect,
    iconCr: CornerRadius,
    insetPx: Float
): Path {
    if (styleId != "jelly") {
        val r = iconRect.deflate(insetPx)
        val cr = CornerRadius(
            (iconCr.x - insetPx).coerceAtLeast(1f),
            (iconCr.y - insetPx).coerceAtLeast(1f)
        )
        return Path().apply {
            addRoundRect(RoundRect(r, cr))
        }
    }
    return buildJellyBlobPath(iconRect, insetPx = insetPx)
}

private fun buildJellyBlobPath(iconRect: Rect, insetPx: Float): Path {
    val cx = iconRect.center.x
    val cy = iconRect.center.y
    val rx = (iconRect.width * 0.525f - insetPx).coerceAtLeast(2f)
    val ry = (iconRect.height * 0.490f - insetPx).coerceAtLeast(2f)
    val exp = 2.0f / JELLY_SUPERELLIPSE_EXPONENT

    return Path().apply {
        for (i in 0 until JELLY_STEPS) {
            val theta = (2.0 * PI * i) / JELLY_STEPS
            val ct = cos(theta).toFloat()
            val st = sin(theta).toFloat()
            val sx = signPow(ct, exp)
            val sy = signPow(st, exp)
            // Subtle upper-half jelly bulge & soft bottom cushion
            val bulgeX = 1.0f + 0.045f * (-st).coerceAtLeast(0f) + 0.020f * cos(2.0 * theta).toFloat()
            val bulgeY = 1.0f + 0.025f * sin(theta).toFloat()
            val x = cx + rx * sx * bulgeX
            val y = cy + ry * sy * bulgeY
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

private fun signPow(value: Float, exp: Float): Float {
    val a = abs(value)
    if (a < 1e-6f) return 0f
    val p = a.pow(exp)
    return if (value >= 0f) p else -p
}

/**
 * Builds the glossy curved highlight cap on the upper half of the Jelly hero card.
 */
internal fun buildJellyUpperCapPath(iconRect: Rect): Path {
    val cx = iconRect.center.x
    val top = iconRect.top + iconRect.height * 0.07f
    val midY = iconRect.top + iconRect.height * 0.44f
    val halfW = iconRect.width * 0.39f
    return Path().apply {
        moveTo(cx - halfW, midY * 0.96f)
        cubicTo(
            cx - halfW * 0.88f, top,
            cx + halfW * 0.88f, top,
            cx + halfW, midY * 0.96f
        )
        cubicTo(
            cx + halfW * 0.52f, midY * 0.78f,
            cx - halfW * 0.52f, midY * 0.78f,
            cx - halfW, midY * 0.96f
        )
        close()
    }
}

internal class JellyFinishSpec(
    val capPath: Path,
    val capPaint: Paint,
    val innerGlowPath: Path,
    val innerGlowPaint: Paint
)

internal fun buildJellyFinishCache(
    styleId: String,
    iconRect: Rect,
    iconCr: CornerRadius,
    canvasSize: Size,
    colors: ResolvedStyleColors,
    density: Density
): JellyFinishSpec? = with(density) {
    if (styleId != "jelly") return null

    val capPath = buildJellyUpperCapPath(iconRect)
    val capBrush = Brush.verticalGradient(
        0.0f to Color.White.copy(alpha = 0.36f),
        0.55f to colors.tintColor.copy(alpha = 0.18f),
        1.0f to Color.Transparent,
        startY = iconRect.top + iconRect.height * 0.06f,
        endY = iconRect.top + iconRect.height * 0.46f
    )
    val capPaint = ditheredBrushPaint(capBrush, canvasSize)

    val strokePx = 3.2.dp.toPx()
    val innerGlowPath = buildInsetHeroCardPath(styleId, iconRect, iconCr, strokePx * 0.55f)
    val glowColor = Color(
        red = (colors.tintColor.red * 0.72f + 0.28f).coerceIn(0f, 1f),
        green = (colors.tintColor.green * 0.65f + 0.22f).coerceIn(0f, 1f),
        blue = (colors.tintColor.blue * 0.78f + 0.22f).coerceIn(0f, 1f),
        alpha = 0.62f
    )
    val innerBrush = Brush.verticalGradient(
        colors = listOf(glowColor, colors.borderColor.copy(alpha = 0.42f)),
        startY = iconRect.top,
        endY = iconRect.bottom
    )
    val innerGlowPaint = ditheredBrushPaint(
        innerBrush,
        canvasSize,
        style = PaintingStyle.Stroke,
        strokePx = strokePx
    )

    JellyFinishSpec(
        capPath = capPath,
        capPaint = capPaint,
        innerGlowPath = innerGlowPath,
        innerGlowPaint = innerGlowPaint
    )
}

/**
 * Custom Shape clipping to both the style-specific hero card Path and the rounded row card Path.
 */
internal class StyleDualPreviewCardsShape(
    private val styleId: String,
    private val iconSize: Dp,
    private val iconTop: Dp,
    private val rowTop: Dp,
    private val rowHeight: Dp,
    private val rowHPad: Dp,
    private val iconCornerDp: Dp,
    private val rowCornerDp: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline = with(density) {
        val iconPx = iconSize.toPx()
        val iconLeft = (size.width - iconPx) * 0.5f
        val iconTopPx = iconTop.toPx()
        val iconRect = Rect(iconLeft, iconTopPx, iconLeft + iconPx, iconTopPx + iconPx)
        val iconCr = CornerRadius(iconCornerDp.toPx())

        val rowLeft = rowHPad.toPx()
        val rowTopPx = rowTop.toPx()
        val rowW = (size.width - rowLeft * 2f).coerceAtLeast(1f)
        val rowH = rowHeight.toPx()
        val rowRect = Rect(rowLeft, rowTopPx, rowLeft + rowW, rowTopPx + rowH)
        val rowCr = CornerRadius(rowCornerDp.toPx())

        val combined = Path().apply {
            addPath(buildHeroCardPath(styleId, iconRect, iconCr))
            addRoundRect(RoundRect(rowRect, rowCr))
        }
        Outline.Generic(combined)
    }
}

/**
 * Offsets a cached Compose Path into a new Android Path for shadow drawing without per-frame allocation.
 */
internal fun createOffsetAndroidPath(source: Path, dx: Float, dy: Float): android.graphics.Path {
    return android.graphics.Path(source.asAndroidPath()).apply {
        offset(dx, dy)
    }
}
