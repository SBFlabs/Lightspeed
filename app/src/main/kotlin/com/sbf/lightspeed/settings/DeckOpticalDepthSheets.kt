package com.sbf.lightspeed.settings

import android.graphics.Matrix
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal fun buildUnifiedDepthSheets(
    rect: Rect,
    cr: CornerRadius,
    colors: ResolvedStyleColors,
    isMiniTile: Boolean,
    density: Density
): List<RotatedDepthSheet> = with(density) {
    val scaleFactor = if (isMiniTile) 0.55f else 1.0f
    val recipes = listOf(
        Triple(-4.5f * scaleFactor, -4f * scaleFactor, 5f * scaleFactor),
        Triple(3.2f * scaleFactor, 3.5f * scaleFactor, 3.2f * scaleFactor)
    )
    val matrix = Matrix()
    val strokePx = 1.dp.toPx()
    recipes.mapIndexed { idx, (angle, dxDp, dyDp) ->
        val scale = if (idx == 0) 0.95f else 0.975f
        val w = rect.width * scale
        val h = rect.height * scale
        val left = rect.center.x - w * 0.5f + dxDp.dp.toPx()
        val top = rect.center.y - h * 0.5f + dyDp.dp.toPx()
        val rawPath = android.graphics.Path().apply {
            addRoundRect(left, top, left + w, top + h, cr.x * scale, cr.y * scale, android.graphics.Path.Direction.CW)
        }
        matrix.reset()
        matrix.setRotate(angle, left + w * 0.5f, top + h * 0.5f)
        rawPath.transform(matrix)
        val shadowPath = android.graphics.Path(rawPath).apply { offset(0f, 2.dp.toPx()) }
        val composePath = Path().apply { asAndroidPath().set(rawPath) }
        val fillA = if (idx == 0) 0.28f else 0.38f
        RotatedDepthSheet(
            sheetPath = composePath,
            shadowPath = shadowPath,
            fillPaint = ditheredColorPaint(colors.panelColor.copy(alpha = fillA)),
            tintPaint = ditheredColorPaint(colors.tintColor.copy(alpha = fillA * 0.65f)),
            borderPaint = ditheredColorPaint(colors.borderColor.copy(alpha = fillA + 0.18f), PaintingStyle.Stroke, strokePx)
        )
    }
}
