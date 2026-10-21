package com.sbf.lightspeed.settings

import android.graphics.BlurMaskFilter
import android.graphics.Matrix
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal class RotatedDepthSheet(
    val sheetPath: Path,
    val shadowPath: android.graphics.Path,
    val fillPaint: Paint,
    val tintPaint: Paint,
    val borderPaint: Paint
)

internal class RowOffsetSheet(
    val rect: Rect,
    val cr: CornerRadius,
    val shadowOffsetY: Float,
    val fillPaint: Paint,
    val tintPaint: Paint,
    val borderPaint: Paint
)

internal class DepthSheetsSpec(
    val heroSheets: List<RotatedDepthSheet>,
    val rowSheet: RowOffsetSheet
)

private data class SheetTransformRecipe(
    val angleDeg: Float,
    val dxDp: Float,
    val dyDp: Float,
    val scale: Float,
    val fillAlpha: Float,
    val borderAlpha: Float
)

private val HERO_DEPTH_RECIPES = listOf(
    // Back to front: -9 deg, +6 deg, -3 deg
    SheetTransformRecipe(angleDeg = -9.0f, dxDp = -6.5f, dyDp = 6.5f, scale = 0.94f, fillAlpha = 0.24f, borderAlpha = 0.36f),
    SheetTransformRecipe(angleDeg = 6.0f, dxDp = 5.5f, dyDp = 4.5f, scale = 0.96f, fillAlpha = 0.32f, borderAlpha = 0.46f),
    SheetTransformRecipe(angleDeg = -3.0f, dxDp = -2.5f, dyDp = 2.5f, scale = 0.98f, fillAlpha = 0.40f, borderAlpha = 0.56f)
)

/**
 * Precomputes the 3 tilted stacked sheets behind the hero card (rotated ONCE in cache via Matrix)
 * and 1 subtle offset sheet behind the row card for `styleId == "depth"`.
 */
internal fun buildDepthSheetsCache(
    styleId: String,
    iconRect: Rect,
    iconCr: CornerRadius,
    rowRect: Rect,
    rowCr: CornerRadius,
    colors: ResolvedStyleColors,
    density: Density
): DepthSheetsSpec? = with(density) {
    if (styleId != "depth") return null

    val strokePx = 1.1.dp.toPx()
    val shadowDy = 2.5.dp.toPx()
    val cx = iconRect.center.x
    val cy = iconRect.center.y
    val matrix = Matrix()

    val heroSheets = HERO_DEPTH_RECIPES.map { recipe ->
        val w = iconRect.width * recipe.scale
        val h = iconRect.height * recipe.scale
        val left = cx - w * 0.5f + recipe.dxDp.dp.toPx()
        val top = cy - h * 0.5f + recipe.dyDp.dp.toPx()
        val scx = left + w * 0.5f
        val scy = top + h * 0.5f
        val crPx = (iconCr.x * recipe.scale).coerceAtLeast(2f)

        val rawAndroidPath = android.graphics.Path().apply {
            addRoundRect(
                left, top, left + w, top + h,
                crPx, crPx,
                android.graphics.Path.Direction.CW
            )
        }
        matrix.reset()
        matrix.setRotate(recipe.angleDeg, scx, scy)
        rawAndroidPath.transform(matrix)

        val shadowAndroidPath = android.graphics.Path(rawAndroidPath).apply {
            offset(0f, shadowDy)
        }

        val composePath = Path().apply {
            asAndroidPath().set(rawAndroidPath)
        }

        RotatedDepthSheet(
            sheetPath = composePath,
            shadowPath = shadowAndroidPath,
            fillPaint = ditheredColorPaint(colors.panelColor.copy(alpha = recipe.fillAlpha)),
            tintPaint = ditheredColorPaint(colors.tintColor.copy(alpha = recipe.fillAlpha * 0.65f)),
            borderPaint = ditheredColorPaint(
                colors.borderColor.copy(alpha = recipe.borderAlpha),
                style = PaintingStyle.Stroke,
                strokePx = strokePx
            )
        )
    }

    val rowInsetX = 4.dp.toPx()
    val rowOffsetY = 4.5.dp.toPx()
    val rRect = Rect(
        rowRect.left + rowInsetX,
        rowRect.top + rowOffsetY,
        rowRect.right - rowInsetX,
        rowRect.bottom + rowOffsetY
    )
    val rowSheet = RowOffsetSheet(
        rect = rRect,
        cr = rowCr,
        shadowOffsetY = 2.dp.toPx(),
        fillPaint = ditheredColorPaint(colors.panelColor.copy(alpha = 0.34f)),
        tintPaint = ditheredColorPaint(colors.tintColor.copy(alpha = 0.20f)),
        borderPaint = ditheredColorPaint(
            colors.borderColor.copy(alpha = 0.45f),
            style = PaintingStyle.Stroke,
            strokePx = strokePx
        )
    )

    DepthSheetsSpec(heroSheets = heroSheets, rowSheet = rowSheet)
}

internal fun DrawScope.drawDepthSheets(
    spec: DepthSheetsSpec,
    shadowPaint: android.graphics.Paint?
) {
    drawIntoCanvas { canvas ->
        for (sheet in spec.heroSheets) {
            if (shadowPaint != null) {
                canvas.nativeCanvas.drawPath(sheet.shadowPath, shadowPaint)
            }
            canvas.drawPath(sheet.sheetPath, sheet.fillPaint)
            canvas.drawPath(sheet.sheetPath, sheet.tintPaint)
            canvas.drawPath(sheet.sheetPath, sheet.borderPaint)
        }

        val rs = spec.rowSheet
        val r = rs.rect
        val cr = rs.cr
        if (shadowPaint != null) {
            canvas.nativeCanvas.drawRoundRect(
                r.left, r.top + rs.shadowOffsetY, r.right, r.bottom + rs.shadowOffsetY,
                cr.x, cr.y, shadowPaint
            )
        }
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, rs.fillPaint)
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, rs.tintPaint)
        canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cr.x, cr.y, rs.borderPaint)
    }
}

/**
 * Builds the cached outer glow Paint.
 * For `styleId == "neon"`, produces a wide soft halo (BlurMaskFilter ~16dp, alpha scaled by glowStrength
 * in `preset.glowColorArgb`) paired with the crisp bright border on the card surface.
 * Skips the halo below API 28 while preserving the border.
 */
internal fun rememberOuterGlowPaint(
    preset: DeckThemeSnapshot,
    glowColor: Color,
    density: Density,
    isApi28Plus: Boolean
): android.graphics.Paint? = with(density) {
    if (!isApi28Plus || preset.glowStrength <= 0.01f) return null

    if (preset.styleId == "neon") {
        val neonHaloColor = Color(preset.glowColorArgb)
        val haloAlpha = (preset.glowStrength * 0.92f).coerceIn(0.25f, 0.95f)
        val blurRadiusPx = 16.dp.toPx()
        val strokeWidthPx = (8f + preset.glowStrength * 6f).dp.toPx()
        return android.graphics.Paint(
            android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG
        ).apply {
            isDither = true
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = neonHaloColor.copy(alpha = haloAlpha).toArgb()
            maskFilter = BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL)
        }
    }

    if (glowColor.alpha <= 0.01f) return null
    android.graphics.Paint(
        android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG
    ).apply {
        isDither = true
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = (2.5f + preset.glowStrength * 5.5f).dp.toPx()
        color = glowColor
            .copy(alpha = (preset.glowStrength * 0.80f).coerceIn(0.15f, 0.92f))
            .toArgb()
        maskFilter = BlurMaskFilter(
            (4f + preset.glowStrength * 9f).dp.toPx(),
            BlurMaskFilter.Blur.NORMAL
        )
    }
}

/**
 * Builds the crisp bright neon border Paint for `styleId == "neon"`.
 */
internal fun buildNeonCrispBorderPaint(
    preset: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    density: Density
): Paint = with(density) {
    val strokePx = preset.borderWidthDp.coerceAtLeast(1.6f).dp.toPx()
    val brightBorder = Color(
        red = (colors.borderColor.red * 0.82f + 0.18f).coerceIn(0f, 1f),
        green = (colors.borderColor.green * 0.82f + 0.18f).coerceIn(0f, 1f),
        blue = (colors.borderColor.blue * 0.82f + 0.18f).coerceIn(0f, 1f),
        alpha = 0.98f
    )
    ditheredColorPaint(brightBorder, style = PaintingStyle.Stroke, strokePx = strokePx)
}

internal fun DrawScope.drawShadowsAndGlowLayers(
    geom: CardGeometries,
    preset: DeckThemeSnapshot,
    colors: ResolvedStyleColors,
    shadowPaint: android.graphics.Paint?,
    glowPaint: android.graphics.Paint?,
    depthSpec: DepthSheetsSpec?
) {
    if (depthSpec != null) {
        drawDepthSheets(depthSpec, shadowPaint)
    } else {
        val extraLayers = (preset.shadowLayers - 1).coerceAtLeast(0)
        drawIntoCanvas { canvas ->
            for (layerIdx in extraLayers downTo 1) {
                val dy = geom.layerStepY * layerIdx
                val dxInset = geom.layerInsetX * layerIdx
                val layerFillAlpha = (0.38f - layerIdx * 0.08f).coerceAtLeast(0.16f)
                val layerBorderAlpha = (0.55f - layerIdx * 0.12f).coerceAtLeast(0.20f)

                if (shadowPaint != null) {
                    canvas.nativeCanvas.drawPath(geom.heroOffsetShadowPath, shadowPaint)
                }
                geom.fillPaint.color = colors.panelColor.copy(alpha = layerFillAlpha)
                canvas.drawPath(geom.heroOffsetLayerPath, geom.fillPaint)
                geom.fillPaint.color = colors.tintColor.copy(alpha = layerFillAlpha * 0.55f)
                canvas.drawPath(geom.heroOffsetLayerPath, geom.fillPaint)
                geom.strokePaint.color = colors.borderColor.copy(alpha = layerBorderAlpha)
                canvas.drawPath(geom.heroOffsetLayerPath, geom.strokePaint)

                val lRow = Rect(geom.rowRect.left + dxInset, geom.rowRect.top + dy, geom.rowRect.right - dxInset, geom.rowRect.bottom + dy)
                val rCr = geom.rowCr
                if (shadowPaint != null) {
                    val sOff = geom.layerStepY * 0.4f
                    canvas.nativeCanvas.drawRoundRect(
                        lRow.left, lRow.top + sOff, lRow.right, lRow.bottom + sOff, rCr.x, rCr.y, shadowPaint
                    )
                }
                geom.fillPaint.color = colors.panelColor.copy(alpha = layerFillAlpha)
                canvas.drawRoundRect(lRow.left, lRow.top, lRow.right, lRow.bottom, rCr.x, rCr.y, geom.fillPaint)
                geom.fillPaint.color = colors.tintColor.copy(alpha = layerFillAlpha * 0.55f)
                canvas.drawRoundRect(lRow.left, lRow.top, lRow.right, lRow.bottom, rCr.x, rCr.y, geom.fillPaint)
                geom.strokePaint.color = colors.borderColor.copy(alpha = layerBorderAlpha)
                canvas.drawRoundRect(lRow.left, lRow.top, lRow.right, lRow.bottom, rCr.x, rCr.y, geom.strokePaint)
            }
        }
    }

    drawIntoCanvas { canvas ->
        if (shadowPaint != null) {
            canvas.nativeCanvas.drawPath(geom.heroShadowPath, shadowPaint)
            val sOff = geom.layerStepY * 0.45f
            val r = geom.rowRect
            canvas.nativeCanvas.drawRoundRect(
                r.left, r.top + sOff, r.right, r.bottom + sOff, geom.rowCr.x, geom.rowCr.y, shadowPaint
            )
        }
        if (glowPaint != null) {
            canvas.nativeCanvas.drawPath(geom.heroPath.asAndroidPath(), glowPaint)
            val r = geom.rowRect
            canvas.nativeCanvas.drawRoundRect(
                r.left, r.top, r.right, r.bottom, geom.rowCr.x, geom.rowCr.y, glowPaint
            )
        }
    }
}
