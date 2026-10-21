package com.sbf.lightspeed.settings

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
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class ResolvedStyleColors(
    val panelColor: Color,
    val tintColor: Color,
    val borderColor: Color,
    val glowColor: Color
)

internal fun ditheredBrushPaint(
    brush: Brush,
    size: Size,
    style: PaintingStyle = PaintingStyle.Fill,
    strokePx: Float = 0f
): Paint = Paint().apply {
    isAntiAlias = true
    asFrameworkPaint().isDither = true
    this.style = style
    if (strokePx > 0f) strokeWidth = strokePx
    brush.applyTo(size, this, 1f)
}

internal fun ditheredColorPaint(
    color: Color = Color.Unspecified,
    style: PaintingStyle = PaintingStyle.Fill,
    strokePx: Float = 0f
): Paint = Paint().apply {
    isAntiAlias = true
    asFrameworkPaint().isDither = true
    this.style = style
    if (strokePx > 0f) strokeWidth = strokePx
    if (color != Color.Unspecified) this.color = color
}

internal class CardGeometries(
    val canvasSize: Size,
    val iconRect: Rect,
    val iconCr: CornerRadius,
    val rowRect: Rect,
    val rowCr: CornerRadius,
    val layerStepY: Float,
    val layerInsetX: Float,
    val heroPath: Path,
    val heroShadowPath: android.graphics.Path,
    val heroOffsetLayerPath: Path,
    val heroOffsetShadowPath: android.graphics.Path,
    val clipPath: Path,
    val fillPaint: Paint,
    val strokePaint: Paint
)

internal fun computeCardGeometries(
    size: Size,
    density: Density,
    iconCornerDp: Dp,
    rowCornerDp: Dp,
    iconSizeDp: Dp = 84.dp,
    iconTopDp: Dp = 12.dp,
    rowTopDp: Dp = 110.dp,
    rowHeightDp: Dp = 52.dp,
    rowHPadDp: Dp = 8.dp,
    styleId: String = "prism"
): CardGeometries = with(density) {
    val iconPx = iconSizeDp.toPx()
    val iconLeft = (size.width - iconPx) * 0.5f
    val iconTop = iconTopDp.toPx()
    val iconRect = Rect(iconLeft, iconTop, iconLeft + iconPx, iconTop + iconPx)
    val iconCr = CornerRadius(iconCornerDp.toPx())

    val rowLeft = rowHPadDp.toPx()
    val rowTop = rowTopDp.toPx()
    val rowW = (size.width - rowLeft * 2f).coerceAtLeast(1f)
    val rowH = rowHeightDp.toPx()
    val rowRect = Rect(rowLeft, rowTop, rowLeft + rowW, rowTop + rowH)
    val rowCr = CornerRadius(rowCornerDp.toPx())

    val stepY = 4.5.dp.toPx()
    val insetX = 4.dp.toPx()
    val heroPath = buildHeroCardPath(styleId, iconRect, iconCr)
    val heroShadowPath = createOffsetAndroidPath(heroPath, 0f, stepY * 0.45f)

    val offsetIconRect = Rect(iconRect.left + insetX, iconRect.top + stepY, iconRect.right - insetX, iconRect.bottom + stepY)
    val heroOffsetLayerPath = buildHeroCardPath(styleId, offsetIconRect, iconCr)
    val heroOffsetShadowPath = createOffsetAndroidPath(heroOffsetLayerPath, 0f, stepY * 0.4f)

    val clipPath = Path().apply {
        addPath(heroPath)
        addRoundRect(RoundRect(rowRect, rowCr))
    }

    CardGeometries(
        Size(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f)),
        iconRect, iconCr, rowRect, rowCr, stepY, insetX,
        heroPath, heroShadowPath, heroOffsetLayerPath, heroOffsetShadowPath, clipPath,
        ditheredColorPaint(),
        ditheredColorPaint(style = PaintingStyle.Stroke, strokePx = 1.dp.toPx())
    )
}

internal fun DrawScope.drawShadowsAndGlow(
    geom: CardGeometries,
    preset: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    shadowPaint: android.graphics.Paint?,
    glowPaint: android.graphics.Paint?,
    depthSpec: DepthSheetsSpec? = null
) {
    drawShadowsAndGlowLayers(geom, preset, colors, shadowPaint, glowPaint, depthSpec)
}

internal class SingleCardBrushes(
    val rect: Rect,
    val cr: CornerRadius,
    val isHero: Boolean,
    val fillPaint: Paint,
    val tintPaint: Paint,
    val glarePaint: Paint?
)

internal class CardSurfaceSpec(
    val fallbackScrimPaint: Paint?,
    val cards: List<SingleCardBrushes>,
    val glassFinish: GlassFinishSpec,
    val noiseAlpha: Float
)

internal fun buildCardSurfaceCache(
    geom: CardGeometries,
    preset: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    isApi31Plus: Boolean,
    density: Density,
    noiseEnabled: Boolean = false
): CardSurfaceSpec = with(density) {
    val fallbackScrimPaint = if (!isApi31Plus) {
        ditheredColorPaint(Color.Black.copy(alpha = (preset.dimAmount * 0.72f).coerceIn(0.40f, 0.78f)))
    } else null

    val baseAlpha = if (preset.styleId == "jelly") {
        0.92f
    } else if (isApi31Plus) {
        ((preset.panelAlpha - 0.32f) * 1.1f).coerceIn(0.20f, 0.76f)
    } else {
        preset.panelAlpha.coerceIn(0.60f, 0.92f)
    }

    val topAlpha = if (preset.isProgressive) (baseAlpha * 0.55f).coerceAtLeast(0.15f) else baseAlpha * 0.88f
    val botAlpha = if (preset.isProgressive) (baseAlpha * 1.15f).coerceAtMost(0.88f) else baseAlpha
    val tintAlpha = if (preset.styleId == "jelly") 0.48f else if (preset.styleId == "adaptive") 0.36f else 0.22f
    val hl = preset.highlightStrength

    fun buildForRect(rect: Rect, cr: CornerRadius, isHero: Boolean): SingleCardBrushes {
        val fill = Brush.verticalGradient(
            colors = listOf(colors.panelColor.copy(alpha = topAlpha), colors.panelColor.copy(alpha = botAlpha)),
            startY = rect.top,
            endY = rect.bottom
        )
        val tint = when (preset.styleId) {
            "prism" -> Brush.linearGradient(
                0.0f to Color(0xFFFF8A50).copy(alpha = 0.32f),
                0.48f to colors.tintColor.copy(alpha = 0.26f),
                1.0f to Color(0xFFE040FB).copy(alpha = 0.24f),
                start = rect.topLeft,
                end = rect.bottomRight
            )
            "jelly" -> Brush.linearGradient(
                0.0f to Color(0xFF4FC3FF).copy(alpha = 0.40f),
                0.55f to colors.tintColor.copy(alpha = 0.30f),
                1.0f to Color(0xFF9C5BFF).copy(alpha = 0.55f),
                start = rect.topLeft,
                end = rect.bottomRight
            )
            else -> Brush.linearGradient(
                colors = listOf(colors.tintColor.copy(alpha = tintAlpha), colors.tintColor.copy(alpha = tintAlpha * 0.28f)),
                start = rect.topLeft,
                end = rect.bottomRight
            )
        }
        val glare = if (hl > 0.01f) {
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = (0.42f * hl).coerceAtMost(0.55f)),
                0.35f to colors.tintColor.copy(alpha = (0.20f * hl).coerceAtMost(0.35f)),
                0.55f to Color.White.copy(alpha = (0.14f * hl).coerceAtMost(0.25f)),
                1.0f to Color.Transparent,
                start = rect.topLeft,
                end = rect.bottomRight
            )
        } else null
        return SingleCardBrushes(
            rect = rect,
            cr = cr,
            isHero = isHero,
            fillPaint = ditheredBrushPaint(fill, geom.canvasSize),
            tintPaint = ditheredBrushPaint(tint, geom.canvasSize),
            glarePaint = glare?.let { ditheredBrushPaint(it, geom.canvasSize) }
        )
    }

    CardSurfaceSpec(
        fallbackScrimPaint = fallbackScrimPaint,
        cards = listOf(buildForRect(geom.iconRect, geom.iconCr, true), buildForRect(geom.rowRect, geom.rowCr, false)),
        glassFinish = buildGlassFinishCache(geom, preset, colors, density),
        noiseAlpha = if (noiseEnabled) (preset.noiseStrength * 1.65f).coerceIn(0f, 0.35f) else 0f
    )
}

internal fun DrawScope.drawCardSurfaces(
    geom: CardGeometries,
    spec: CardSurfaceSpec,
    preset: DeckThemeSnapshot,
    noiseBrush: ShaderBrush?
) {
    drawIntoCanvas { canvas ->
        for (card in spec.cards) {
            if (card.isHero) {
                if (spec.fallbackScrimPaint != null) {
                    canvas.drawPath(geom.heroPath, spec.fallbackScrimPaint)
                }
                canvas.drawPath(geom.heroPath, card.fillPaint)
                canvas.drawPath(geom.heroPath, card.tintPaint)
            } else {
                val r = card.rect
                val cr = card.cr
                if (spec.fallbackScrimPaint != null) {
                    canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, spec.fallbackScrimPaint)
                }
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, card.fillPaint)
                canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, card.tintPaint)
            }
        }
    }

    if (noiseBrush != null && spec.noiseAlpha > 0.005f) {
        clipPath(geom.clipPath) {
            drawRect(brush = noiseBrush, alpha = spec.noiseAlpha)
        }
    }

    drawIntoCanvas { canvas ->
        for (card in spec.cards) {
            if (card.glarePaint != null) {
                if (card.isHero) {
                    canvas.drawPath(geom.heroPath, card.glarePaint)
                } else {
                    val r = card.rect
                    val cr = card.cr
                    canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, card.glarePaint)
                }
            }
        }
    }

    drawGlassFinishes(spec.glassFinish, preset)
}

internal class RocketIconCache(
    val flamePath: Path,
    val flamePaint: Paint,
    val finsPath: Path,
    val finsPaint: Paint,
    val bodyPath: Path,
    val bodyPaint: Paint,
    val highlightPath: Path,
    val highlightPaint: Paint,
    val portholeCenter: Offset,
    val portholeRimRadius: Float,
    val portholeRimPaint: Paint,
    val portholeRadius: Float,
    val portholePaint: Paint
)

internal fun buildRocketIconCache(size: Size): RocketIconCache {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val safeSize = Size(w, h)

    val flamePath = Path().apply {
        moveTo(w * 0.40f, h * 0.72f)
        quadraticTo(w * 0.35f, h * 0.85f, w * 0.50f, h * 0.96f)
        quadraticTo(w * 0.65f, h * 0.85f, w * 0.60f, h * 0.72f)
        close()
    }
    val flameBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFE082), Color(0xFFFF9100), Color(0xFFFF3D00)),
        startY = h * 0.72f,
        endY = h * 0.96f
    )

    val finsPath = Path().apply {
        moveTo(w * 0.35f, h * 0.50f)
        quadraticTo(w * 0.16f, h * 0.60f, w * 0.18f, h * 0.76f)
        quadraticTo(w * 0.28f, h * 0.71f, w * 0.38f, h * 0.71f)
        close()
        moveTo(w * 0.65f, h * 0.50f)
        quadraticTo(w * 0.84f, h * 0.60f, w * 0.82f, h * 0.76f)
        quadraticTo(w * 0.72f, h * 0.71f, w * 0.62f, h * 0.71f)
        close()
    }
    val finsBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF2563EB), Color(0xFF1E3A8A)),
        startY = h * 0.50f,
        endY = h * 0.76f
    )

    val bodyPath = Path().apply {
        moveTo(w * 0.50f, h * 0.06f)
        cubicTo(w * 0.68f, h * 0.20f, w * 0.69f, h * 0.48f, w * 0.61f, h * 0.73f)
        lineTo(w * 0.39f, h * 0.73f)
        cubicTo(w * 0.31f, h * 0.48f, w * 0.32f, h * 0.20f, w * 0.50f, h * 0.06f)
        close()
    }
    val bodyBrush = Brush.linearGradient(
        colors = listOf(Color(0xFF60A5FA), Color(0xFF3B82F6), Color(0xFF1D4ED8)),
        start = Offset(w * 0.32f, h * 0.08f),
        end = Offset(w * 0.68f, h * 0.73f)
    )

    val highlightPath = Path().apply {
        moveTo(w * 0.50f, h * 0.09f)
        cubicTo(w * 0.36f, h * 0.22f, w * 0.35f, h * 0.46f, w * 0.41f, h * 0.70f)
        lineTo(w * 0.46f, h * 0.70f)
        cubicTo(w * 0.42f, h * 0.46f, w * 0.43f, h * 0.24f, w * 0.50f, h * 0.09f)
        close()
    }
    val highlightBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFBAE6FD).copy(alpha = 0.72f), Color(0xFF93C5FD).copy(alpha = 0.12f)),
        startY = h * 0.09f,
        endY = h * 0.70f
    )

    val portholeCenter = Offset(w * 0.50f, h * 0.39f)
    val portholeRimRadius = w * 0.125f
    val portholeRadius = w * 0.096f
    val portholeBrush = Brush.radialGradient(
        colors = listOf(Color(0xFFFFE0B2), Color(0xFFFFAB91), Color(0xFFFF8A65)),
        center = Offset(w * 0.48f, h * 0.37f),
        radius = portholeRadius * 1.2f
    )

    return RocketIconCache(
        flamePath = flamePath,
        flamePaint = ditheredBrushPaint(flameBrush, safeSize),
        finsPath = finsPath,
        finsPaint = ditheredBrushPaint(finsBrush, safeSize),
        bodyPath = bodyPath,
        bodyPaint = ditheredBrushPaint(bodyBrush, safeSize),
        highlightPath = highlightPath,
        highlightPaint = ditheredBrushPaint(highlightBrush, safeSize),
        portholeCenter = portholeCenter,
        portholeRimRadius = portholeRimRadius,
        portholeRimPaint = ditheredColorPaint(Color(0xFFE0F2FE).copy(alpha = 0.90f)),
        portholeRadius = portholeRadius,
        portholePaint = ditheredBrushPaint(portholeBrush, safeSize)
    )
}

internal fun DrawScope.drawRocketIcon(cache: RocketIconCache) {
    drawIntoCanvas { canvas ->
        canvas.drawPath(cache.flamePath, cache.flamePaint)
        canvas.drawPath(cache.finsPath, cache.finsPaint)
        canvas.drawPath(cache.bodyPath, cache.bodyPaint)
        canvas.drawPath(cache.highlightPath, cache.highlightPaint)
        canvas.drawCircle(cache.portholeCenter, cache.portholeRimRadius, cache.portholeRimPaint)
        canvas.drawCircle(cache.portholeCenter, cache.portholeRadius, cache.portholePaint)
    }
}
