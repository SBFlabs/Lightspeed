package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.ShaderBrush



object GlassNoiseTexture {
    private var cachedBrush: ShaderBrush? = null

    fun getBrush(): ShaderBrush {
        cachedBrush?.let { return it }
        val size = 96
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val random = java.util.Random(42L)
        for (i in pixels.indices) {
            val v = (random.nextFloat() * 255).toInt()
            val a = (random.nextFloat() * 160).toInt()
            pixels[i] = android.graphics.Color.argb(a, v, v, v)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
        val brush = ShaderBrush(shader)
        cachedBrush = brush
        return brush
    }
}

data class DeckGlassVisuals(
    val styleKey: String,
    val backgroundBrush: Brush,
    val borderBrush: Brush,
    val borderWidth: androidx.compose.ui.unit.Dp,
    val showTopGlare: Boolean,
    val topGlareAlpha: Float,
    val innerChamferAlpha: Float,
    val showNoiseGrain: Boolean = false,
    val noiseAlpha: Float = 0f,
    val showRefractiveRim: Boolean = false,
    val showBottomCaustic: Boolean = false,
    val shapeCornerRadius: androidx.compose.ui.unit.Dp = 28.dp,
    // ── True Liquid Glass system ──────────────────────────────────────────
    // When true: LiquidGlassPanel + liquidGlassLayer() are activated.
    // All other showXxx flags are ignored — LiquidGlassPanel owns rendering.
    val showLiquidGlass: Boolean = false,
    val liquidGlassBlurRadius: Float = 22f
)

data class DeckBackdropVisuals(
    val styleKey: String,
    val title: String,
    val subtitle: String,
    val dimAmount: Float,
    val scrimAlpha: Float,
    val blurBehindRadius: Int
)

object DeckBackdropTheme {
    val STYLES = listOf(
        DeckBackdropVisuals(
            styleKey = "cosmic",
            title = "Cosmic",
            subtitle = "Tactical spaceship contrast",
            dimAmount = 0.65f,
            scrimAlpha = 0.45f,
            blurBehindRadius = 60
        ),
        DeckBackdropVisuals(
            styleKey = "void",
            title = "Void",
            subtitle = "Deep stealth OLED blackout",
            dimAmount = 0.78f,
            scrimAlpha = 0.60f,
            blurBehindRadius = 50
        ),
        DeckBackdropVisuals(
            styleKey = "frost_veil",
            title = "Frosted",
            subtitle = "Luminous ambient diffusion",
            dimAmount = 0.45f,
            scrimAlpha = 0.30f,
            blurBehindRadius = 90
        ),
        DeckBackdropVisuals(
            styleKey = "clear",
            title = "Clarity",
            subtitle = "High context visibility",
            dimAmount = 0.40f,
            scrimAlpha = 0.25f,
            blurBehindRadius = 40
        )
    )

    fun resolve(styleKey: String): DeckBackdropVisuals {
        return STYLES.find { it.styleKey == styleKey } ?: STYLES[0]
    }
}

@Composable
fun rememberDeckBackdropVisuals(context: Context): DeckBackdropVisuals {
    val styleFlow by LightspeedPreferences.deckBackdropStyleFlow.collectAsState()
    val activeStyle = styleFlow ?: remember { LightspeedPreferences.getDeckBackdropStyle(context) }
    return DeckBackdropTheme.resolve(activeStyle)
}

object DeckGlassTheme {
    @Composable
    fun resolve(style: String): DeckGlassVisuals {
        val colorScheme = MaterialTheme.colorScheme
        return when (style) {
            "frost" -> DeckGlassVisuals(
                styleKey = "frost",
                backgroundBrush = Brush.radialGradient(
                    colors = listOf(
                        colorScheme.onSurface.copy(alpha = 0.08f),
                        colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        colorScheme.surface.copy(alpha = 0.65f)
                    ),
                    center = Offset(0.5f, 0.15f),
                    radius = 1100f
                ),
                borderBrush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.outlineVariant.copy(alpha = 0.75f),
                        colorScheme.outlineVariant.copy(alpha = 0.30f),
                        colorScheme.outlineVariant.copy(alpha = 0.10f)
                    )
                ),
                borderWidth = 1.4.dp,
                showTopGlare = false,
                topGlareAlpha = 0f,
                innerChamferAlpha = 0.20f,
                showNoiseGrain = true,
                noiseAlpha = 0.08f,
                showRefractiveRim = false,
                showBottomCaustic = false,
                shapeCornerRadius = 28.dp
            )
            "obsidian" -> DeckGlassVisuals(
                styleKey = "obsidian",
                backgroundBrush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF090D14).copy(alpha = 0.94f),
                        Color(0xFF040608).copy(alpha = 0.98f)
                    ),
                    radius = 1200f
                ),
                borderBrush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = 0.85f),
                        colorScheme.primary.copy(alpha = 0.35f),
                        Color.White.copy(alpha = 0.12f),
                        Color.Transparent
                    )
                ),
                borderWidth = 1.2.dp,
                showTopGlare = false,
                topGlareAlpha = 0f,
                innerChamferAlpha = 0f,
                showNoiseGrain = false,
                noiseAlpha = 0f,
                showRefractiveRim = false,
                showBottomCaustic = false,
                shapeCornerRadius = 28.dp
            )
            else -> {
                val glassConfig = rememberLiquidGlassConfig()
                val baseOpacity = (0.55f + glassConfig.opacity * 0.40f).coerceIn(0.65f, 0.98f)
                DeckGlassVisuals( // "liquid" — Dynamic liquid glass (RenderEffect + AGSL + Canvas layers)
                    styleKey = "liquid",
                    backgroundBrush = Brush.radialGradient(
                        colors = listOf(
                            colorScheme.surfaceVariant.copy(alpha = (baseOpacity * 0.88f).coerceIn(0.55f, 0.96f)),
                            colorScheme.surface.copy(alpha = baseOpacity)
                        ),
                        center = Offset(0.5f, 0.1f),
                        radius = 1200f
                    ),
                    borderBrush = Brush.linearGradient(
                        colors = listOf(
                            colorScheme.primary.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.30f),
                            colorScheme.secondary.copy(alpha = 0.40f),
                            Color.Transparent
                        )
                    ),
                    borderWidth = 1.2.dp,
                    showTopGlare = true,
                    topGlareAlpha = 0.12f,
                    innerChamferAlpha = 0.18f,
                    showNoiseGrain = false,
                    showRefractiveRim = true,
                    showBottomCaustic = false,
                    shapeCornerRadius = 28.dp,
                    showLiquidGlass = true,
                    liquidGlassBlurRadius = 22f
                )
            }
        }
    }
}

@Composable
fun rememberDeckGlassVisuals(context: Context): DeckGlassVisuals {
    val styleFlow by LightspeedPreferences.deckGlassStyleFlow.collectAsState()
    val activeStyle = styleFlow ?: remember { LightspeedPreferences.getDeckGlassStyle(context) }
    return DeckGlassTheme.resolve(activeStyle)
}

