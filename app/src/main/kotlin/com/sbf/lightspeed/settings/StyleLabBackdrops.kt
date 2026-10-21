package com.sbf.lightspeed.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

internal class SampleStarPx(
    val center: Offset, val radiusPx: Float, val color: Color,
    val sparkleColor: Color, val armPx: Float, val strokePx: Float, val isSparkle: Boolean
)

internal class SampleBlobPx(val paint: Paint, val center: Offset, val radius: Float)
internal class SamplePathLayerPx(val path: Path, val paint: Paint)

internal class SampleBackdropCache(
    val bgPaint: Paint, val blobs: List<SampleBlobPx>, val pathLayers: List<SamplePathLayerPx>,
    val stars: List<SampleStarPx>, val fillPaint: Paint, val linePaint: Paint
)

private data class BlobRecipe(
    val cxF: Float, val cyF: Float, val rF: Float,
    val innerColor: Color, val midColor: Color, val bokehStop: Float = 0f
)

private data class StarRecipe(val xF: Float, val yF: Float, val rDp: Float, val alpha: Float, val isSparkle: Boolean = false)

private enum class BackdropPathKind { NONE, PRISM_STREAKS, PROGRESSIVE_DUSK, ADAPTIVE_WAVES, DARK_SWEEP }

private data class StyleBackdropRecipe(
    val bgColors: List<Color>,
    val verticalBg: Boolean = false,
    val blobs: List<BlobRecipe> = emptyList(),
    val pathKind: BackdropPathKind = BackdropPathKind.NONE,
    val stars: List<StarRecipe> = emptyList()
)

private val DEFAULT_STARS_FEW = listOf(
    StarRecipe(0.14f, 0.14f, 1.3f, 0.85f, isSparkle = true),
    StarRecipe(0.84f, 0.16f, 1.2f, 0.80f),
    StarRecipe(0.20f, 0.56f, 1.0f, 0.65f),
    StarRecipe(0.80f, 0.52f, 1.3f, 0.82f, isSparkle = true)
)

private val BACKDROP_RECIPES: Map<String, StyleBackdropRecipe> = mapOf(
    "prism" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF060B1E), Color(0xFF0E2154), Color(0xFF1D4ED8)),
        blobs = listOf(
            BlobRecipe(0.20f, 0.78f, 0.48f, Color(0xFFFF7A18).copy(alpha = 0.78f), Color(0xFFE11D48).copy(alpha = 0.36f)),
            BlobRecipe(0.78f, 0.24f, 0.44f, Color(0xFF00E5FF).copy(alpha = 0.65f), Color(0xFF6366F1).copy(alpha = 0.32f))
        ),
        pathKind = BackdropPathKind.PRISM_STREAKS,
        stars = listOf(
            StarRecipe(0.16f, 0.16f, 1.3f, 0.85f, isSparkle = true),
            StarRecipe(0.84f, 0.18f, 1.1f, 0.75f),
            StarRecipe(0.76f, 0.58f, 1.2f, 0.78f)
        )
    ),
    "jelly" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF060B26), Color(0xFF0C1650), Color(0xFF1A1058)),
        blobs = listOf(
            BlobRecipe(0.80f, 0.20f, 0.50f, Color(0xFF7C4DFF).copy(alpha = 0.60f), Color(0xFF3B82F6).copy(alpha = 0.30f)),
            BlobRecipe(0.24f, 0.56f, 0.44f, Color(0xFF8B5CF6).copy(alpha = 0.65f), Color(0xFF3B82F6).copy(alpha = 0.34f)),
            BlobRecipe(0.68f, 0.78f, 0.38f, Color(0xFFC084FC).copy(alpha = 0.58f), Color(0xFF38BDF8).copy(alpha = 0.28f))
        ),
        stars = listOf(
            StarRecipe(0.18f, 0.18f, 1.2f, 0.75f),
            StarRecipe(0.82f, 0.54f, 1.3f, 0.82f, isSparkle = true),
            StarRecipe(0.22f, 0.84f, 1.0f, 0.65f)
        )
    ),
    "frosted" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF2B3A50), Color(0xFF3D526E), Color(0xFF24344B)),
        blobs = listOf(
            BlobRecipe(0.22f, 0.22f, 0.17f, Color(0xFFE0F2FE).copy(alpha = 0.75f), Color(0xFF7DD3FC).copy(alpha = 0.42f), bokehStop = 0.58f),
            BlobRecipe(0.76f, 0.20f, 0.21f, Color(0xFFFFFFFF).copy(alpha = 0.68f), Color(0xFF93C5FD).copy(alpha = 0.38f), bokehStop = 0.55f),
            BlobRecipe(0.48f, 0.40f, 0.15f, Color(0xFFE0F2FE).copy(alpha = 0.72f), Color(0xFF38BDF8).copy(alpha = 0.40f), bokehStop = 0.60f),
            BlobRecipe(0.20f, 0.62f, 0.19f, Color(0xFFFFFFFF).copy(alpha = 0.65f), Color(0xFFBAE6FD).copy(alpha = 0.36f), bokehStop = 0.56f),
            BlobRecipe(0.74f, 0.60f, 0.22f, Color(0xFFBAE6FD).copy(alpha = 0.70f), Color(0xFF60A5FA).copy(alpha = 0.38f), bokehStop = 0.54f),
            BlobRecipe(0.44f, 0.80f, 0.16f, Color(0xFFE0F2FE).copy(alpha = 0.68f), Color(0xFF7DD3FC).copy(alpha = 0.35f), bokehStop = 0.58f)
        )
    ),
    "blur" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF070C18), Color(0xFF0F1B33), Color(0xFF091224)),
        blobs = listOf(
            BlobRecipe(0.26f, 0.28f, 0.48f, Color(0xFF1E3A5F).copy(alpha = 0.72f), Color(0xFF0F2442).copy(alpha = 0.38f)),
            BlobRecipe(0.76f, 0.44f, 0.46f, Color(0xFF1E2952).copy(alpha = 0.68f), Color(0xFF111936).copy(alpha = 0.35f)),
            BlobRecipe(0.36f, 0.76f, 0.50f, Color(0xFF16324F).copy(alpha = 0.70f), Color(0xFF0C1D33).copy(alpha = 0.36f)),
            BlobRecipe(0.62f, 0.22f, 0.28f, Color(0xFF38BDF8).copy(alpha = 0.28f), Color(0xFF1D4ED8).copy(alpha = 0.12f))
        ),
        stars = listOf(
            StarRecipe(0.18f, 0.16f, 1.0f, 0.55f),
            StarRecipe(0.82f, 0.24f, 1.1f, 0.60f)
        )
    ),
    "progressive" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF0E2244), Color(0xFF284B7A), Color(0xFFE07A3C), Color(0xFF141324)),
        verticalBg = true,
        blobs = listOf(
            BlobRecipe(0.50f, 0.50f, 0.52f, Color(0xFFFF9E44).copy(alpha = 0.62f), Color(0xFFF43F5E).copy(alpha = 0.24f))
        ),
        pathKind = BackdropPathKind.PROGRESSIVE_DUSK,
        stars = listOf(
            StarRecipe(0.16f, 0.12f, 1.1f, 0.75f),
            StarRecipe(0.52f, 0.10f, 1.2f, 0.80f, isSparkle = true),
            StarRecipe(0.84f, 0.15f, 1.0f, 0.70f)
        )
    ),
    "glassmorphism" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF0A0F29), Color(0xFF1D1442), Color(0xFF122244)),
        blobs = listOf(
            BlobRecipe(0.26f, 0.24f, 0.46f, Color(0xFFA855F7).copy(alpha = 0.74f), Color(0xFF4F46E5).copy(alpha = 0.38f)),
            BlobRecipe(0.76f, 0.32f, 0.44f, Color(0xFF38BDF8).copy(alpha = 0.72f), Color(0xFF2563EB).copy(alpha = 0.36f)),
            BlobRecipe(0.28f, 0.74f, 0.48f, Color(0xFFD946EF).copy(alpha = 0.68f), Color(0xFF6366F1).copy(alpha = 0.34f)),
            BlobRecipe(0.74f, 0.76f, 0.42f, Color(0xFF60A5FA).copy(alpha = 0.66f), Color(0xFF312E81).copy(alpha = 0.32f))
        ),
        stars = DEFAULT_STARS_FEW
    ),
    "neon" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF04060F), Color(0xFF080C1C), Color(0xFF04050D)),
        blobs = listOf(
            BlobRecipe(0.24f, 0.28f, 0.48f, Color(0xFFE040FB).copy(alpha = 0.80f), Color(0xFF9333EA).copy(alpha = 0.34f)),
            BlobRecipe(0.76f, 0.68f, 0.48f, Color(0xFF00E5FF).copy(alpha = 0.80f), Color(0xFF0284C7).copy(alpha = 0.34f))
        ),
        stars = listOf(
            StarRecipe(0.80f, 0.18f, 1.2f, 0.80f, isSparkle = true),
            StarRecipe(0.18f, 0.76f, 1.1f, 0.75f)
        )
    ),
    "adaptive" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF07132B), Color(0xFF0E244D), Color(0xFF091835)),
        verticalBg = true,
        blobs = listOf(
            BlobRecipe(0.50f, 0.20f, 0.48f, Color(0xFF38BDF8).copy(alpha = 0.32f), Color(0xFF1D4ED8).copy(alpha = 0.14f))
        ),
        pathKind = BackdropPathKind.ADAPTIVE_WAVES
    ),
    "acrylic" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF090D24), Color(0xFF1A1133), Color(0xFF0D132A)),
        blobs = listOf(
            BlobRecipe(0.24f, 0.26f, 0.46f, Color(0xFFFF7A18).copy(alpha = 0.76f), Color(0xFFF43F5E).copy(alpha = 0.38f)),
            BlobRecipe(0.74f, 0.36f, 0.44f, Color(0xFFEC4899).copy(alpha = 0.72f), Color(0xFFA855F7).copy(alpha = 0.36f)),
            BlobRecipe(0.32f, 0.74f, 0.48f, Color(0xFFA855F7).copy(alpha = 0.68f), Color(0xFF6366F1).copy(alpha = 0.34f)),
            BlobRecipe(0.78f, 0.76f, 0.40f, Color(0xFFFB923C).copy(alpha = 0.64f), Color(0xFFDB2777).copy(alpha = 0.30f))
        ),
        stars = DEFAULT_STARS_FEW
    ),
    "gaussian" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF151834), Color(0xFF222750), Color(0xFF192040)),
        blobs = listOf(
            BlobRecipe(0.28f, 0.28f, 0.52f, Color(0xFF818CF8).copy(alpha = 0.46f), Color(0xFF6366F1).copy(alpha = 0.22f)),
            BlobRecipe(0.74f, 0.34f, 0.50f, Color(0xFFA78BFA).copy(alpha = 0.44f), Color(0xFF7C3AED).copy(alpha = 0.20f)),
            BlobRecipe(0.30f, 0.72f, 0.52f, Color(0xFF60A5FA).copy(alpha = 0.40f), Color(0xFF4F46E5).copy(alpha = 0.18f)),
            BlobRecipe(0.72f, 0.74f, 0.46f, Color(0xFFC084FC).copy(alpha = 0.38f), Color(0xFF6366F1).copy(alpha = 0.18f))
        ),
        stars = listOf(
            StarRecipe(0.20f, 0.18f, 1.0f, 0.55f),
            StarRecipe(0.80f, 0.22f, 1.0f, 0.55f)
        )
    ),
    "depth" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF060A18), Color(0xFF0E162E), Color(0xFF050915)),
        blobs = listOf(
            BlobRecipe(0.50f, 0.44f, 0.50f, Color(0xFF4F46E5).copy(alpha = 0.36f), Color(0xFF1E3A8A).copy(alpha = 0.14f)),
            BlobRecipe(0.50f, 0.40f, 0.28f, Color(0xFF60A5FA).copy(alpha = 0.26f), Color(0xFF3B82F6).copy(alpha = 0.08f))
        ),
        stars = listOf(
            StarRecipe(0.14f, 0.14f, 1.3f, 0.85f, isSparkle = true),
            StarRecipe(0.84f, 0.16f, 1.2f, 0.80f),
            StarRecipe(0.22f, 0.42f, 1.0f, 0.65f),
            StarRecipe(0.78f, 0.46f, 1.3f, 0.82f, isSparkle = true),
            StarRecipe(0.18f, 0.78f, 1.1f, 0.70f),
            StarRecipe(0.82f, 0.80f, 1.1f, 0.72f)
        )
    ),
    "dark" to StyleBackdropRecipe(
        bgColors = listOf(Color(0xFF03050B), Color(0xFF070B16), Color(0xFF030409)),
        blobs = listOf(
            BlobRecipe(0.52f, 0.48f, 0.42f, Color(0xFF1D4ED8).copy(alpha = 0.22f), Color(0xFF0F172A).copy(alpha = 0.08f))
        ),
        pathKind = BackdropPathKind.DARK_SWEEP
    )
)

internal fun buildSampleBackdropCache(styleId: String, size: Size, density: Density): SampleBackdropCache = with(density) {
    val w = size.width.coerceAtLeast(1f)
    val h = size.height.coerceAtLeast(1f)
    val safeSize = Size(w, h)
    val recipe = BACKDROP_RECIPES[styleId] ?: BACKDROP_RECIPES.getValue("prism")

    val bgBrush = if (recipe.verticalBg) {
        Brush.verticalGradient(colors = recipe.bgColors, startY = 0f, endY = h)
    } else {
        Brush.linearGradient(colors = recipe.bgColors, start = Offset(0f, 0f), end = Offset(w, h))
    }
    val bgPaint = ditheredBrushPaint(bgBrush, safeSize)

    val blobs = recipe.blobs.map { b ->
        val center = Offset(w * b.cxF, h * b.cyF)
        val radius = (w * b.rF).coerceAtLeast(1f)
        val brush = if (b.bokehStop > 0f) {
            Brush.radialGradient(
                0.0f to b.innerColor,
                b.bokehStop to b.midColor,
                1.0f to Color.Transparent,
                center = center,
                radius = radius
            )
        } else {
            Brush.radialGradient(listOf(b.innerColor, b.midColor, Color.Transparent), center = center, radius = radius)
        }
        SampleBlobPx(ditheredBrushPaint(brush, safeSize), center, radius)
    }

    val pathLayers = buildBackdropPaths(recipe.pathKind, w, h, safeSize, density)

    val stars = recipe.stars.map { st ->
        val rPx = st.rDp.dp.toPx()
        val c = Color.White.copy(alpha = st.alpha)
        SampleStarPx(
            center = Offset(w * st.xF, h * st.yF),
            radiusPx = rPx,
            color = c,
            sparkleColor = c.copy(alpha = st.alpha * 0.65f),
            armPx = rPx * 2.4f,
            strokePx = rPx * 0.65f,
            isSparkle = st.isSparkle
        )
    }

    val fillPaint = ditheredColorPaint()
    val linePaint = ditheredColorPaint(style = PaintingStyle.Stroke).apply { strokeCap = StrokeCap.Butt }

    SampleBackdropCache(bgPaint, blobs, pathLayers, stars, fillPaint, linePaint)
}

private fun buildBackdropPaths(
    kind: BackdropPathKind,
    w: Float,
    h: Float,
    safeSize: Size,
    density: Density
): List<SamplePathLayerPx> = with(density) {
    when (kind) {
        BackdropPathKind.NONE -> emptyList()
        BackdropPathKind.PRISM_STREAKS -> {
            val streak1 = Path().apply {
                moveTo(-w * 0.08f, h * 0.72f)
                cubicTo(w * 0.28f, h * 0.56f, w * 0.56f, h * 0.26f, w * 1.08f, h * 0.12f)
                lineTo(w * 1.08f, h * 0.24f)
                cubicTo(w * 0.60f, h * 0.38f, w * 0.32f, h * 0.68f, -w * 0.08f, h * 0.84f)
                close()
            }
            val streak2 = Path().apply {
                moveTo(w * 0.05f, h * 0.94f)
                cubicTo(w * 0.42f, h * 0.72f, w * 0.66f, h * 0.48f, w * 1.06f, h * 0.34f)
                lineTo(w * 1.06f, h * 0.43f)
                cubicTo(w * 0.70f, h * 0.56f, w * 0.46f, h * 0.80f, w * 0.12f, h * 0.99f)
                close()
            }
            val brush1 = Brush.linearGradient(
                listOf(Color(0xFF38BDF8).copy(alpha = 0.46f), Color(0xFFA855F7).copy(alpha = 0.52f), Color(0xFF60A5FA).copy(alpha = 0.40f)),
                start = Offset(0f, h * 0.8f), end = Offset(w, h * 0.15f)
            )
            val brush2 = Brush.linearGradient(
                listOf(Color(0xFF818CF8).copy(alpha = 0.38f), Color(0xFF00E5FF).copy(alpha = 0.48f)),
                start = Offset(0f, h * 0.9f), end = Offset(w, h * 0.35f)
            )
            listOf(
                SamplePathLayerPx(streak1, ditheredBrushPaint(brush1, safeSize)),
                SamplePathLayerPx(streak2, ditheredBrushPaint(brush2, safeSize))
            )
        }
        BackdropPathKind.PROGRESSIVE_DUSK -> {
            val farMountains = Path().apply {
                moveTo(0f, h * 0.58f)
                lineTo(w * 0.22f, h * 0.44f)
                lineTo(w * 0.44f, h * 0.54f)
                lineTo(w * 0.70f, h * 0.42f)
                lineTo(w * 0.90f, h * 0.52f)
                lineTo(w, h * 0.47f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            val nearSilhouette = Path().apply {
                moveTo(0f, h * 0.68f)
                lineTo(w * 0.12f, h * 0.62f)
                lineTo(w * 0.18f, h * 0.48f)
                lineTo(w * 0.24f, h * 0.63f)
                lineTo(w * 0.35f, h * 0.58f)
                lineTo(w * 0.42f, h * 0.46f)
                lineTo(w * 0.49f, h * 0.61f)
                lineTo(w * 0.64f, h * 0.56f)
                lineTo(w * 0.74f, h * 0.45f)
                lineTo(w * 0.82f, h * 0.60f)
                lineTo(w * 0.91f, h * 0.52f)
                lineTo(w, h * 0.62f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            listOf(
                SamplePathLayerPx(farMountains, ditheredColorPaint(Color(0xFF1E223D))),
                SamplePathLayerPx(nearSilhouette, ditheredColorPaint(Color(0xFF0B0E1C)))
            )
        }
        BackdropPathKind.ADAPTIVE_WAVES -> {
            fun wavePath(yStart: Float, c1y: Float, c2y: Float, yEnd: Float): Path = Path().apply {
                moveTo(0f, h * yStart)
                cubicTo(w * 0.32f, h * c1y, w * 0.68f, h * c2y, w, h * yEnd)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            listOf(
                SamplePathLayerPx(wavePath(0.38f, 0.28f, 0.46f, 0.34f), ditheredColorPaint(Color(0xFF2563EB).copy(alpha = 0.34f))),
                SamplePathLayerPx(wavePath(0.56f, 0.64f, 0.46f, 0.54f), ditheredColorPaint(Color(0xFF38BDF8).copy(alpha = 0.32f))),
                SamplePathLayerPx(wavePath(0.74f, 0.66f, 0.82f, 0.72f), ditheredColorPaint(Color(0xFF7DD3FC).copy(alpha = 0.30f)))
            )
        }
        BackdropPathKind.DARK_SWEEP -> {
            val sweepRibbon = Path().apply {
                moveTo(-w * 0.05f, h * 0.80f)
                cubicTo(w * 0.32f, h * 0.62f, w * 0.64f, h * 0.36f, w * 1.05f, h * 0.18f)
                lineTo(w * 1.05f, h * 0.24f)
                cubicTo(w * 0.66f, h * 0.42f, w * 0.34f, h * 0.68f, -w * 0.05f, h * 0.86f)
                close()
            }
            val sweepCore = Path().apply {
                moveTo(-w * 0.04f, h * 0.83f)
                cubicTo(w * 0.33f, h * 0.65f, w * 0.65f, h * 0.39f, w * 1.04f, h * 0.21f)
            }
            val ribbonBrush = Brush.linearGradient(
                listOf(Color.Transparent, Color(0xFF2563EB).copy(alpha = 0.48f), Color.Transparent),
                start = Offset(0f, h * 0.83f), end = Offset(w, h * 0.21f)
            )
            val coreBrush = Brush.linearGradient(
                listOf(Color.Transparent, Color(0xFF60A5FA).copy(alpha = 0.78f), Color.Transparent),
                start = Offset(0f, h * 0.83f), end = Offset(w, h * 0.21f)
            )
            listOf(
                SamplePathLayerPx(sweepRibbon, ditheredBrushPaint(ribbonBrush, safeSize)),
                SamplePathLayerPx(sweepCore, ditheredBrushPaint(coreBrush, safeSize, PaintingStyle.Stroke, 1.6.dp.toPx()))
            )
        }
    }
}

internal fun DrawScope.drawSampleBackdrop(cache: SampleBackdropCache) {
    drawIntoCanvas { canvas ->
        canvas.drawRect(0f, 0f, size.width, size.height, cache.bgPaint)
        for (blob in cache.blobs) {
            canvas.drawCircle(blob.center, blob.radius, blob.paint)
        }
        for (layer in cache.pathLayers) {
            canvas.drawPath(layer.path, layer.paint)
        }
        for (star in cache.stars) {
            cache.fillPaint.color = star.color
            canvas.drawCircle(star.center, star.radiusPx, cache.fillPaint)
            if (star.isSparkle) {
                cache.linePaint.color = star.sparkleColor
                cache.linePaint.strokeWidth = star.strokePx
                val cx = star.center.x
                val cy = star.center.y
                val arm = star.armPx
                canvas.drawLine(Offset(cx - arm, cy), Offset(cx + arm, cy), cache.linePaint)
                canvas.drawLine(Offset(cx, cy - arm), Offset(cx, cy + arm), cache.linePaint)
            }
        }
    }
}
