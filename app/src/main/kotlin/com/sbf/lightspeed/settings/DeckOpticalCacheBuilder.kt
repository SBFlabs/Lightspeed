package com.sbf.lightspeed.settings

import android.graphics.BlurMaskFilter
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal class UnifiedDeckOpticalCache(
    val depthSheets: List<RotatedDepthSheet>,
    val shadowPaint: android.graphics.Paint?,
    val outerHaloPaint: android.graphics.Paint?,
    val secondaryHaloPaint: android.graphics.Paint?,
    val substratePaint: Paint,
    val tintInfusionPaint: Paint,
    val lavaRefractionPaint: Paint?,
    val secondaryPocketPaint: Paint?,
    val topGlarePaint: Paint?,
    val bottomCatchPaint: Paint?,
    val innerCushionPath: Path,
    val innerCushionPaint: Paint?,
    val specularPath: Path?,
    val specularPaint: Paint?,
    val borderPaint: Paint?,
    val noiseAlpha: Float
)

internal fun buildUnifiedDeckOpticalCache(
    snapshot: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    rect: Rect,
    cr: CornerRadius,
    safeSize: Size,
    isBlurAvailable: Boolean,
    isMiniTile: Boolean,
    isFlyout: Boolean = false,
    isCard: Boolean = false,
    isApi28Plus: Boolean,
    density: Density
): UnifiedDeckOpticalCache = with(density) {
    val w = safeSize.width
    val h = safeSize.height
    val styleId = snapshot.styleId

    val baseAlpha = if (isCard) {
        (snapshot.panelAlpha * 0.35f).coerceIn(0.08f, 0.35f)
    } else if (isFlyout) {
        when (styleId) {
            "prism" -> 0.72f
            "jelly" -> 0.76f
            "glassmorphism" -> 0.74f
            "frosted" -> 0.78f
            "acrylic" -> 0.80f
            "gaussian" -> 0.78f
            "progressive" -> 0.78f
            "neon" -> 0.82f
            "dark" -> 0.88f
            "depth" -> 0.82f
            else -> snapshot.panelAlpha.coerceIn(0.72f, 0.88f)
        }
    } else if (isBlurAvailable) {
        when (styleId) {
            "glassmorphism" -> 0.42f
            "jelly" -> 0.48f
            "frosted" -> 0.52f
            "gaussian" -> 0.56f
            "acrylic" -> 0.62f
            "progressive" -> 0.64f
            "prism" -> 0.66f
            "adaptive" -> 0.68f
            "neon" -> 0.76f
            "depth" -> 0.76f
            "blur" -> 0.84f
            "dark" -> 0.86f
            else -> snapshot.panelAlpha.coerceIn(0.45f, 0.88f)
        }
    } else {
        snapshot.panelAlpha.coerceIn(0.84f, 0.96f)
    }

    val topAlpha = if (snapshot.isProgressive) (baseAlpha * 0.62f).coerceAtLeast(0.24f) else (baseAlpha * 0.90f)
    val botAlpha = if (snapshot.isProgressive) (baseAlpha * 1.15f).coerceAtMost(0.92f) else baseAlpha

    val substrateBrush = Brush.verticalGradient(
        colors = listOf(colors.panelColor.copy(alpha = topAlpha), colors.panelColor.copy(alpha = botAlpha)),
        startY = 0f,
        endY = h
    )

    val tintIntensity = if (isCard) 0.08f else when (styleId) {
        "jelly" -> 0.46f
        "prism" -> 0.06f
        "adaptive" -> 0.32f
        "gaussian" -> 0.26f
        "frosted" -> 0.24f
        else -> 0.18f
    }
    val tintBrush = Brush.linearGradient(
        colors = listOf(
            colors.tintColor.copy(alpha = tintIntensity),
            colors.tintColor.copy(alpha = tintIntensity * 0.30f)
        ),
        start = Offset.Zero,
        end = Offset(w, h)
    )

    val lavaPaint = if (isCard || isMiniTile) null else when (styleId) {
        "jelly" -> ditheredBrushPaint(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF4081).copy(alpha = 0.30f),
                    Color(0xFF7C4DFF).copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = Offset(w * 0.30f, h * 0.16f),
                radius = w * 0.85f
            ),
            safeSize
        )
        else -> null
    }

    val secondaryPocketPaint = if (isCard || isMiniTile) null else when (styleId) {
        "neon", "adaptive" -> ditheredBrushPaint(
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFF00E5FF).copy(alpha = if (styleId == "neon") 0.25f else 0.15f),
                    Color.Transparent
                ),
                center = Offset(w * 0.82f, h * 0.85f),
                radius = w * 0.65f
            ),
            safeSize
        )
        else -> null
    }

    val hl = snapshot.highlightStrength
    val topGlarePaint = if (!isCard && !isMiniTile && hl > 0.04f) {
        ditheredBrushPaint(
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = (0.34f * hl).coerceAtMost(0.45f)),
                0.28f to colors.tintColor.copy(alpha = (0.16f * hl).coerceAtMost(0.28f)),
                0.55f to Color.Transparent,
                start = Offset.Zero,
                end = Offset(w * 0.85f, h * 0.65f)
            ),
            safeSize
        )
    } else null

    val bottomCatchPaint = if (!isCard && !isMiniTile && styleId in setOf("prism", "dark", "neon", "glassmorphism", "progressive")) {
        val catchColor = if (styleId == "dark") Color(0xFF00E5FF) else if (styleId == "prism") Color.White else colors.tintColor
        val catchAlpha = if (styleId == "dark") 0.22f else (0.18f * hl.coerceAtLeast(0.35f))
        ditheredBrushPaint(
            Brush.radialGradient(
                colors = listOf(catchColor.copy(alpha = catchAlpha), Color.Transparent),
                center = Offset(w * 0.88f, h * 0.94f),
                radius = w * 0.55f
            ),
            safeSize
        )
    } else null

    val insetPx = (if (styleId == "jelly") 3.2.dp else 1.8.dp).toPx()
    val innerRect = rect.deflate(insetPx * 0.5f)
    val innerCr = CornerRadius((cr.x - insetPx * 0.5f).coerceAtLeast(1f))
    val innerCushionPath = Path().apply { addRoundRect(RoundRect(innerRect, innerCr)) }
    val innerCushionPaint = if (!isCard && !isMiniTile && styleId in setOf("jelly", "frosted")) {
        val rimAlpha = when (styleId) {
            "jelly" -> 0.44f
            "frosted" -> 0.36f
            else -> (hl * 0.44f).coerceIn(0.10f, 0.42f)
        }
        val rimBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = rimAlpha),
            0.50f to colors.tintColor.copy(alpha = rimAlpha * 0.65f),
            1.0f to colors.borderColor.copy(alpha = rimAlpha * 0.25f),
            start = Offset.Zero,
            end = Offset(w, h)
        )
        ditheredBrushPaint(rimBrush, safeSize, style = PaintingStyle.Stroke, strokePx = insetPx)
    } else null

    val hasSpecular = !isCard && !isMiniTile && hl > 0.20f && styleId in setOf("prism", "jelly", "glassmorphism", "progressive", "neon")
    val specularPath = if (hasSpecular) {
        Path().apply {
            moveTo(w * 0.06f, h * 0.18f)
            cubicTo(
                w * 0.18f, h * 0.04f,
                w * 0.45f, h * 0.03f,
                w * 0.62f, h * 0.09f
            )
        }
    } else null
    val specularPaint = if (hasSpecular) {
        val sAlpha = (0.55f * hl).coerceAtMost(0.65f)
        val sBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = 0f),
            0.35f to Color.White.copy(alpha = sAlpha),
            0.70f to colors.tintColor.copy(alpha = sAlpha * 0.8f),
            1.0f to Color.Transparent,
            start = Offset(w * 0.06f, h * 0.18f),
            end = Offset(w * 0.62f, h * 0.09f)
        )
        ditheredBrushPaint(sBrush, safeSize, style = PaintingStyle.Stroke, strokePx = (if (isMiniTile) 1.5.dp else 2.8.dp).toPx()).apply {
            strokeCap = StrokeCap.Round
        }
    } else null

    val borderStrokePx = if (isCard) {
        0.8.dp.toPx()
    } else if (snapshot.borderWidthDp > 0.01f) {
        (if (isMiniTile) (snapshot.borderWidthDp * 0.85f).coerceAtLeast(0.9f) else snapshot.borderWidthDp).dp.toPx()
    } else 0f

    val borderPaint = if (borderStrokePx > 0f) {
        if (isCard) {
            ditheredBrushPaint(
                Brush.linearGradient(
                    0.0f to colors.borderColor.copy(alpha = 0.25f),
                    1.0f to colors.borderColor.copy(alpha = 0.08f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                safeSize,
                style = PaintingStyle.Stroke,
                strokePx = borderStrokePx
            )
        } else if (colors.borderColor.alpha > 0.01f) {
            val brush = when (styleId) {
                "neon" -> Brush.linearGradient(
                    listOf(Color(0xFF00F0FF), Color(0xFFDF00FF), Color(0xFF00F0FF)),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                "jelly" -> Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.70f),
                        colors.borderColor.copy(alpha = 0.55f),
                        Color(0xFFDF00FF).copy(alpha = 0.35f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                "prism" -> Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color.White.copy(alpha = 0.45f),
                        Color.White.copy(alpha = 0.15f)
                    ),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                "glassmorphism", "frosted" -> Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = (0.55f + hl * 0.35f).coerceAtMost(0.92f)),
                    0.35f to Color.White.copy(alpha = 0.22f),
                    1.0f to Color.White.copy(alpha = 0.06f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                "dark" -> Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.28f),
                    0.65f to Color.White.copy(alpha = 0.08f),
                    1.0f to Color(0xFF00E5FF).copy(alpha = 0.62f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
                else -> Brush.linearGradient(
                    0.0f to Color(
                        red = (colors.borderColor.red * 0.5f + 0.5f).coerceIn(0f, 1f),
                        green = (colors.borderColor.green * 0.5f + 0.5f).coerceIn(0f, 1f),
                        blue = (colors.borderColor.blue * 0.5f + 0.5f).coerceIn(0f, 1f),
                        alpha = (colors.borderColor.alpha + hl * 0.3f).coerceIn(0.25f, 0.96f)
                    ),
                    0.55f to colors.borderColor.copy(alpha = (colors.borderColor.alpha * 0.75f).coerceIn(0.15f, 0.85f)),
                    1.0f to colors.borderColor.copy(alpha = (colors.borderColor.alpha * 0.30f).coerceIn(0.08f, 0.50f)),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            }
            ditheredBrushPaint(brush, safeSize, style = PaintingStyle.Stroke, strokePx = borderStrokePx)
        } else null
    } else null

    val outerHaloPaint = if (!isCard && !isMiniTile && isApi28Plus && snapshot.glowStrength > 0.05f) {
        val haloBlur = 14.dp.toPx()
        val haloStroke = 7.dp.toPx()
        val haloColor = if (styleId == "neon") Color(0xFF00E5FF) else if (styleId == "prism") Color.White else colors.glowColor
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = haloStroke
            color = haloColor.copy(alpha = (snapshot.glowStrength * 0.65f).coerceIn(0.15f, 0.80f)).toArgb()
            maskFilter = BlurMaskFilter(haloBlur, BlurMaskFilter.Blur.NORMAL)
        }
    } else null

    val secondaryHaloPaint = if (!isCard && !isMiniTile && isApi28Plus && styleId == "neon") {
        val haloBlur = 18.dp.toPx()
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5.dp.toPx()
            color = Color(0xFFE040FB).copy(alpha = 0.55f).toArgb()
            maskFilter = BlurMaskFilter(haloBlur, BlurMaskFilter.Blur.NORMAL)
        }
    } else null

    val depthSheets = if (!isCard && !isMiniTile && styleId == "depth") {
        buildUnifiedDepthSheets(rect, cr, colors, isMiniTile, density)
    } else emptyList()

    val shadowPaint = if (!isCard && !isMiniTile && isApi28Plus && depthSheets.isNotEmpty()) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.FILL
            color = android.graphics.Color.argb(120, 0, 0, 0)
            maskFilter = BlurMaskFilter(6.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
        }
    } else null

    UnifiedDeckOpticalCache(
        depthSheets = depthSheets,
        shadowPaint = shadowPaint,
        outerHaloPaint = outerHaloPaint,
        secondaryHaloPaint = secondaryHaloPaint,
        substratePaint = ditheredBrushPaint(substrateBrush, safeSize),
        tintInfusionPaint = ditheredBrushPaint(tintBrush, safeSize),
        lavaRefractionPaint = lavaPaint,
        secondaryPocketPaint = secondaryPocketPaint,
        topGlarePaint = topGlarePaint,
        bottomCatchPaint = bottomCatchPaint,
        innerCushionPath = innerCushionPath,
        innerCushionPaint = innerCushionPaint,
        specularPath = specularPath,
        specularPaint = specularPaint,
        borderPaint = borderPaint,
        noiseAlpha = if (isCard || isMiniTile || snapshot.styleId != "acrylic" || snapshot.noiseStrength <= 0.01f) {
            0f
        } else {
            (snapshot.noiseStrength * 0.85f).coerceIn(0f, 0.12f)
        }
    )
}
