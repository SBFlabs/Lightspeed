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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

private val SPECULAR_STREAK_STYLES: Set<String> = setOf(
    "prism", "jelly", "glassmorphism", "progressive", "neon", "adaptive", "depth"
)

internal class CardGlassFinish(
    val clipPath: Path,
    val innerGlowPath: Path,
    val innerGlowPaint: Paint,
    val specularPath: Path?,
    val specularPaint: Paint?,
    val borderPath: Path,
    val borderPaint: Paint?
)

internal class GlassFinishSpec(
    val heroFinish: CardGlassFinish,
    val rowFinish: CardGlassFinish,
    val jellyFinish: JellyFinishSpec?
)

/**
 * Builds the cached glass finish (inner glow, specular streak, and gradient border) for styles
 * with `highlightStrength > 0.05f`, or the flat border fallback when `highlightStrength <= 0.05f`
 * (and crisp bright border for `styleId == "neon"`).
 */
internal fun buildGlassFinishCache(
    geom: CardGeometries,
    preset: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    density: Density
): GlassFinishSpec = with(density) {
    val hl = preset.highlightStrength
    val hasGlassFinish = hl > 0.05f
    val hasSpecularStreak = hasGlassFinish && preset.styleId in SPECULAR_STREAK_STYLES
    val borderStrokePx = if (preset.borderWidthDp > 0.01f) preset.borderWidthDp.dp.toPx() else 0f

    val rowOutlinePath = Path().apply {
        addRoundRect(RoundRect(geom.rowRect, geom.rowCr))
    }

    val heroInnerStrokePx = 2.6.dp.toPx()
    val rowInnerStrokePx = 2.2.dp.toPx()

    val heroInnerPath = buildInsetHeroCardPath(
        preset.styleId, geom.iconRect, geom.iconCr, heroInnerStrokePx * 0.5f
    )
    val rowInnerRect = geom.rowRect.deflate(rowInnerStrokePx * 0.5f)
    val rowInnerCr = CornerRadius(
        (geom.rowCr.x - rowInnerStrokePx * 0.5f).coerceAtLeast(1f),
        (geom.rowCr.y - rowInnerStrokePx * 0.5f).coerceAtLeast(1f)
    )
    val rowInnerPath = Path().apply {
        addRoundRect(RoundRect(rowInnerRect, rowInnerCr))
    }

    val innerGlowAlpha = if (hasGlassFinish) (hl * 0.52f).coerceIn(0.04f, 0.48f) else 0f
    val innerTintMix = Color(
        red = (colors.tintColor.red * 0.45f + 0.55f).coerceIn(0f, 1f),
        green = (colors.tintColor.green * 0.45f + 0.55f).coerceIn(0f, 1f),
        blue = (colors.tintColor.blue * 0.45f + 0.55f).coerceIn(0f, 1f),
        alpha = 1f
    )

    fun makeInnerGlowPaint(rect: Rect, strokePx: Float): Paint {
        val brush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = innerGlowAlpha),
            0.50f to innerTintMix.copy(alpha = innerGlowAlpha * 0.65f),
            1.0f to innerTintMix.copy(alpha = innerGlowAlpha * 0.22f),
            start = rect.topLeft,
            end = rect.bottomRight
        )
        return ditheredBrushPaint(brush, geom.canvasSize, style = PaintingStyle.Stroke, strokePx = strokePx)
    }

    val heroSpecularPath = if (hasSpecularStreak) buildHeroSpecularStreakPath(geom.iconRect) else null
    val specAlpha = if (hasSpecularStreak) ((hl * 0.85f).coerceIn(0.06f, 0.78f) * 0.70f) else 0f
    val heroSpecularPaint = if (hasSpecularStreak) {
        val heroSpecBrush = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = specAlpha),
            0.55f to Color.White.copy(alpha = specAlpha * 0.45f),
            1.0f to Color.Transparent,
            start = geom.iconRect.topLeft,
            end = Offset(
                geom.iconRect.left + geom.iconRect.width * 0.72f,
                geom.iconRect.top + geom.iconRect.height * 0.58f
            )
        )
        ditheredBrushPaint(
            heroSpecBrush, geom.canvasSize, style = PaintingStyle.Stroke, strokePx = 1.8.dp.toPx()
        ).apply { strokeCap = StrokeCap.Round }
    } else null

    fun makeBorderPaint(rect: Rect): Paint? {
        if (borderStrokePx <= 0f || colors.borderColor.alpha <= 0.01f) return null
        if (preset.styleId == "neon") {
            return buildNeonCrispBorderPaint(preset, colors, density)
        }
        if (!hasGlassFinish) {
            return ditheredColorPaint(colors.borderColor, style = PaintingStyle.Stroke, strokePx = borderStrokePx)
        }
        val baseA = colors.borderColor.alpha
        val topLeftColor = Color(
            red = (colors.borderColor.red * 0.45f + 0.55f).coerceIn(0f, 1f),
            green = (colors.borderColor.green * 0.45f + 0.55f).coerceIn(0f, 1f),
            blue = (colors.borderColor.blue * 0.45f + 0.55f).coerceIn(0f, 1f),
            alpha = (baseA + hl * 0.35f).coerceIn(0.18f, 0.98f)
        )
        val midColor = colors.borderColor.copy(alpha = (baseA * (0.65f + hl * 0.25f)).coerceIn(0.12f, 0.88f))
        val botRightColor = colors.borderColor.copy(alpha = (baseA * 0.22f).coerceIn(0.04f, 0.42f))
        val grad = Brush.linearGradient(
            0.0f to topLeftColor,
            0.48f to midColor,
            1.0f to botRightColor,
            start = rect.topLeft,
            end = rect.bottomRight
        )
        return ditheredBrushPaint(grad, geom.canvasSize, style = PaintingStyle.Stroke, strokePx = borderStrokePx)
    }

    val heroFinish = CardGlassFinish(
        clipPath = geom.heroPath,
        innerGlowPath = heroInnerPath,
        innerGlowPaint = makeInnerGlowPaint(geom.iconRect, heroInnerStrokePx),
        specularPath = heroSpecularPath,
        specularPaint = heroSpecularPaint,
        borderPath = geom.heroPath,
        borderPaint = makeBorderPaint(geom.iconRect)
    )

    val rowFinish = CardGlassFinish(
        clipPath = rowOutlinePath,
        innerGlowPath = rowInnerPath,
        innerGlowPaint = makeInnerGlowPaint(geom.rowRect, rowInnerStrokePx),
        specularPath = null,
        specularPaint = null,
        borderPath = rowOutlinePath,
        borderPaint = makeBorderPaint(geom.rowRect)
    )

    val jellyFinish = buildJellyFinishCache(
        preset.styleId, geom.iconRect, geom.iconCr, geom.canvasSize, colors, density
    )

    GlassFinishSpec(
        heroFinish = heroFinish,
        rowFinish = rowFinish,
        jellyFinish = jellyFinish
    )
}

private fun buildHeroSpecularStreakPath(rect: Rect): Path {
    val w = rect.width
    val h = rect.height
    val l = rect.left
    val t = rect.top
    return Path().apply {
        moveTo(l + w * 0.11f, t + h * 0.42f)
        cubicTo(
            l + w * 0.10f, t + h * 0.14f,
            l + w * 0.24f, t + h * 0.09f,
            l + w * 0.64f, t + h * 0.10f
        )
    }
}

internal fun DrawScope.drawGlassFinishes(
    spec: GlassFinishSpec,
    preset: DeckThemeSnapshot
) {
    val hasGlassFinish = preset.highlightStrength > 0.05f

    if (hasGlassFinish) {
        clipPath(spec.heroFinish.clipPath) {
            drawIntoCanvas { canvas ->
                canvas.drawPath(spec.heroFinish.innerGlowPath, spec.heroFinish.innerGlowPaint)
                if (spec.jellyFinish != null) {
                    canvas.drawPath(spec.jellyFinish.innerGlowPath, spec.jellyFinish.innerGlowPaint)
                    canvas.drawPath(spec.jellyFinish.capPath, spec.jellyFinish.capPaint)
                }
                val specPath = spec.heroFinish.specularPath
                val specPaint = spec.heroFinish.specularPaint
                if (specPath != null && specPaint != null) {
                    canvas.drawPath(specPath, specPaint)
                }
            }
        }
        clipPath(spec.rowFinish.clipPath) {
            drawIntoCanvas { canvas ->
                canvas.drawPath(spec.rowFinish.innerGlowPath, spec.rowFinish.innerGlowPaint)
            }
        }
    }

    drawIntoCanvas { canvas ->
        spec.heroFinish.borderPaint?.let { paint ->
            canvas.drawPath(spec.heroFinish.borderPath, paint)
        }
        spec.rowFinish.borderPaint?.let { paint ->
            canvas.drawPath(spec.rowFinish.borderPath, paint)
        }
    }
}
