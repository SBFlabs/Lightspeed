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
    density: Density,
    bottomCr: CornerRadius = cr,
    useLightweightHudBlur: Boolean = false
): UnifiedDeckOpticalCache = with(density) {
    val w = safeSize.width
    val h = safeSize.height
    val styleId = snapshot.styleId
    val isMaterial3 = styleId == DeckStylePresets.MATERIAL_3.styleId
    val suppressOrnaments = isCard || isMiniTile || useLightweightHudBlur || isMaterial3
    val allowGlassHighlights = !suppressOrnaments && (isBlurAvailable || isFlyout)

    val baseAlpha = if (isMaterial3 && !useLightweightHudBlur) {
        snapshot.panelAlpha.coerceIn(0.0f, 1.0f)
    } else if (useLightweightHudBlur) {
        if (isCard) {
            maxOf(snapshot.panelAlpha * 0.38f, 0.28f).coerceIn(0.28f, 0.42f)
        } else if (isBlurAvailable) {
            snapshot.panelAlpha.coerceIn(0.0f, 0.78f)
        } else {
            maxOf(snapshot.panelAlpha, 0.92f).coerceIn(0.92f, 0.98f)
        }
    } else if (isCard) {
        maxOf(snapshot.panelAlpha * 0.38f, 0.28f).coerceIn(0.28f, 0.42f)
    } else if (isFlyout) {
        (snapshot.panelAlpha * 1.06f).coerceIn(0.15f, 0.98f)
    } else if (isBlurAvailable) {
        val styleAlphaFactor = when (styleId) {
            "glassmorphism" -> 0.42f / 0.55f
            "jelly" -> 0.48f / 0.80f
            "frosted" -> 0.52f / 0.65f
            "gaussian" -> 0.56f / 0.70f
            "acrylic" -> 0.62f / 0.76f
            "progressive" -> 0.64f / 0.78f
            "prism" -> 0.66f / 0.85f
            "adaptive" -> 0.68f / 0.82f
            "neon" -> 0.76f / 0.84f
            "depth" -> 0.76f / 0.86f
            "blur" -> 0.84f / 0.90f
            "dark" -> 0.86f / 0.92f
            else -> 0.80f
        }
        (snapshot.panelAlpha * styleAlphaFactor).coerceIn(0.0f, 0.98f)
    } else {
        // When backdrop blur is off (0px) or not yet captured, still respect user's Surface Opacity slider
        snapshot.panelAlpha.coerceIn(0.0f, 0.98f)
    }

    val topAlpha = if (isMaterial3 && !useLightweightHudBlur) {
        baseAlpha
    } else if (!isBlurAvailable && !isFlyout && !isCard) {
        (baseAlpha * 0.96f).coerceIn(0.0f, 0.98f)
    } else if (snapshot.isProgressive && !useLightweightHudBlur) {
        (baseAlpha * 0.62f).coerceIn(0.0f, 0.96f)
    } else if (useLightweightHudBlur) {
        baseAlpha
    } else {
        (baseAlpha * 0.90f)
    }
    val botAlpha = if (isMaterial3 && !useLightweightHudBlur) {
        baseAlpha
    } else if (snapshot.isProgressive && !useLightweightHudBlur) {
        (baseAlpha * 1.15f).coerceIn(0.0f, 0.98f)
    } else {
        baseAlpha
    }

    val substrateBrush = Brush.verticalGradient(
        colors = listOf(colors.panelColor.copy(alpha = topAlpha), colors.panelColor.copy(alpha = botAlpha)),
        startY = 0f,
        endY = h
    )

    val tintIntensity = if (isMaterial3 && !useLightweightHudBlur) {
        0f
    } else if (useLightweightHudBlur) {
        if (isCard) 0.08f else 0.14f
    } else if (isCard) 0.08f else when (styleId) {
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

    val lavaPaint = if (suppressOrnaments) null else when (styleId) {
        "jelly" -> ditheredBrushPaint(
            Brush.radialGradient(
                colors = listOf(
                    colors.tintColor.copy(alpha = 0.32f),
                    colors.glowColor.copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = Offset(w * 0.30f, h * 0.16f),
                radius = w * 0.85f
            ),
            safeSize
        )
        else -> null
    }

    val secondaryPocketPaint = if (suppressOrnaments) null else when (styleId) {
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
    val topGlarePaint = if (allowGlassHighlights && hl > 0.04f) {
        ditheredBrushPaint(
            Brush.verticalGradient(
                0.0f to Color.White.copy(alpha = (0.12f * hl).coerceAtMost(0.16f)),
                0.45f to colors.tintColor.copy(alpha = (0.06f * hl).coerceAtMost(0.10f)),
                1.0f to Color.Transparent,
                startY = 0f,
                endY = h * 0.32f
            ),
            safeSize
        )
    } else null

    val bottomCatchPaint = if (!suppressOrnaments && bottomCr.x > 1f && styleId in setOf("prism", "dark", "neon", "glassmorphism", "progressive")) {
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
    val innerTopCr = CornerRadius((cr.x - insetPx * 0.5f).coerceAtLeast(0f))
    val innerBottomCr = CornerRadius((bottomCr.x - insetPx * 0.5f).coerceAtLeast(0f))
    val innerCushionPath = Path().apply {
        addRoundRect(
            RoundRect(
                rect = innerRect,
                topLeft = innerTopCr,
                topRight = innerTopCr,
                bottomRight = innerBottomCr,
                bottomLeft = innerBottomCr
            )
        )
    }
    val innerCushionPaint = if (allowGlassHighlights && styleId in setOf("jelly", "frosted")) {
        val rimAlpha = when (styleId) {
            "jelly" -> (0.18f + hl * 0.26f).coerceIn(0.10f, 0.44f)
            "frosted" -> (0.14f + hl * 0.22f).coerceIn(0.08f, 0.36f)
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

    val specularPath: Path? = null
    val specularPaint: Paint? = null

    val borderStrokePx = if (isCard || isMaterial3 || useLightweightHudBlur) {
        if (isCard) 0.8.dp.toPx() else 1.0.dp.toPx()
    } else if (snapshot.borderWidthDp > 0.01f) {
        (if (isMiniTile) (snapshot.borderWidthDp * 0.85f).coerceAtLeast(0.9f) else snapshot.borderWidthDp).dp.toPx()
    } else 0f

    val borderPaint = if (borderStrokePx > 0f) {
        if (isCard) {
            ditheredBrushPaint(
                Brush.linearGradient(
                    0.0f to colors.borderColor.copy(alpha = if (isMaterial3) 0.60f else 0.25f),
                    1.0f to colors.borderColor.copy(alpha = if (isMaterial3) 0.45f else 0.08f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                safeSize,
                style = PaintingStyle.Stroke,
                strokePx = borderStrokePx
            )
        } else if (isMaterial3 && !useLightweightHudBlur) {
            ditheredBrushPaint(
                Brush.linearGradient(
                    listOf(colors.borderColor.copy(alpha = 0.80f), colors.borderColor.copy(alpha = 0.65f)),
                    start = Offset.Zero,
                    end = Offset(w, h)
                ),
                safeSize,
                style = PaintingStyle.Stroke,
                strokePx = borderStrokePx
            )
        } else if (useLightweightHudBlur) {
            ditheredBrushPaint(
                Brush.linearGradient(
                    listOf(colors.borderColor.copy(alpha = 0.45f), colors.tintColor.copy(alpha = 0.28f)),
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
                        colors.tintColor.copy(alpha = 0.35f)
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

    val outerHaloPaint = if (!suppressOrnaments && isApi28Plus && snapshot.glowStrength > 0.05f) {
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

    val secondaryHaloPaint = if (!suppressOrnaments && isApi28Plus && styleId == "neon") {
        val haloBlur = 18.dp.toPx()
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5.dp.toPx()
            color = Color(0xFFE040FB).copy(alpha = 0.55f).toArgb()
            maskFilter = BlurMaskFilter(haloBlur, BlurMaskFilter.Blur.NORMAL)
        }
    } else null

    val depthSheets = if (!suppressOrnaments && styleId == "depth") {
        buildUnifiedDepthSheets(rect, cr, colors, isMiniTile, density)
    } else emptyList()

    val shadowPaint = if (!suppressOrnaments && isApi28Plus && depthSheets.isNotEmpty()) {
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
        noiseAlpha = if (suppressOrnaments || snapshot.styleId != "acrylic" || snapshot.noiseStrength <= 0.01f) {
            0f
        } else {
            (snapshot.noiseStrength * 0.85f).coerceIn(0f, 0.12f)
        }
    )
}
