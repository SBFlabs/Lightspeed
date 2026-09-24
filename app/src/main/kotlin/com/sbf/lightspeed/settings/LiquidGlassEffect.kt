package com.sbf.lightspeed.settings

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedDeckStyleManager.LiquidGlassConfig

// ---------------------------------------------------------------------------
// AGSL shader for fluid caustic distortion — executed on GPU.
// Modulates luminance with dynamic fluid fractal Brownian motion (fbm).
// 100% neutral luminance (no hardcoded hue) to respect Material dynamic colors.
// Requires API 33+ (RuntimeShader). Gracefully absent on older devices.
// ---------------------------------------------------------------------------
private val LIQUID_CAUSTIC_AGSL = """
uniform float uTime;
uniform float2 uResolution;
uniform shader uContents;

float hash(float2 p) {
    p = fract(p * float2(234.34, 435.345));
    p += dot(p, p + 34.23);
    return fract(p.x * p.y);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i + float2(0,0)), hash(i + float2(1,0)), f.x),
        mix(hash(i + float2(0,1)), hash(i + float2(1,1)), f.x),
        f.y
    );
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    float2 shift = float2(100.0);
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p  = p * 2.0 + shift;
        a *= 0.5;
    }
    return v;
}

half4 main(float2 coord) {
    float2 uv = coord / uResolution;
    float t = uTime * 0.35;
    float2 q = float2(
        fbm(uv + float2(0.0, 0.0)),
        fbm(uv + float2(5.2, 1.3))
    );
    float2 r = float2(
        fbm(uv + 4.0 * q + float2(1.7 + t * 0.15, 9.2)),
        fbm(uv + 4.0 * q + float2(8.3 + t * 0.126, 2.8))
    );

    float displacement = 0.005;
    float2 warpedUV = uv + displacement * (r - 0.5);
    half4 col = uContents.eval(warpedUV * uResolution);

    // Pure neutral luminance caustic shimmer
    float caustic = fbm(uv * 3.5 + float2(t * 0.4, t * 0.2));
    caustic = pow(caustic, 2.2) * 0.05;

    col.rgb += half3(caustic);
    col.a = min(col.a, 1.0);
    return col;
}
""".trimIndent()

const val LIQUID_GLASS_BLUR_RADIUS = 75

/**
 * Returns the active [LiquidGlassConfig] from [LightspeedPreferences], updating reactively.
 */
@Composable
fun rememberLiquidGlassConfig(context: Context = LocalContext.current): LiquidGlassConfig {
    val configFlow by LightspeedPreferences.liquidGlassConfigFlow.collectAsState()
    return configFlow ?: remember { LightspeedPreferences.getLiquidGlassConfig(context) }
}

/**
 * Modifier: applies a true backdrop blur + AGSL caustic warp to decorative background layers.
 * Falls back gracefully on API < 31 (blur) and API < 33 (AGSL).
 */
@Composable
fun Modifier.liquidGlassLayer(
    blurRadius: Float = 22f,
    cornerRadius: Dp = 28.dp
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "liquid_time"
    )

    val runtimeShaderPair = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember {
            try {
                android.graphics.RuntimeShader(LIQUID_CAUSTIC_AGSL) to true
            } catch (_: Exception) {
                null to false
            }
        }
    } else {
        remember { null to false }
    }

    return this then Modifier.graphicsLayer {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blur = RenderEffect.createBlurEffect(
                blurRadius, blurRadius,
                Shader.TileMode.CLAMP
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                runtimeShaderPair.second && runtimeShaderPair.first != null
            ) {
                val shader = runtimeShaderPair.first!!
                shader.setFloatUniform("uTime", time)
                val warp = RenderEffect.createRuntimeShaderEffect(shader, "uContents")
                renderEffect = RenderEffect.createChainEffect(warp, blur).asComposeRenderEffect()
            } else {
                renderEffect = blur.asComposeRenderEffect()
            }
        }
        clip = true
        shape = RoundedCornerShape(cornerRadius)
    }
}

/**
 * LiquidGlassPanel — 100% Material 3 dynamic color compliant liquid glass renderer.
 * Draws physical glass phenomena: progressive depth diffusion, meniscus crest, specular
 * apex glare, prismatic Material edge dispersion, and tactile micro-frosted texture.
 */
@Composable
fun LiquidGlassPanel(
    cornerRadius: Dp = 28.dp,
    config: LiquidGlassConfig = rememberLiquidGlassConfig(),
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glass_surface")

    val shimmerAngle by infiniteTransition.animateFloat(
        initialValue = -25f,
        targetValue = 25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5_200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_angle"
    )

    val flarePulse by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "flare_pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cr = cornerRadius.toPx()

        // ── Layer A: Progressive Substrate Body (Material Surface Tint) ──
        if (config.progressiveDepth) {
            // Progressive blur ramp: luminous translucent meniscus at apex -> deep resting base
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to colorScheme.surfaceVariant.copy(alpha = (config.opacity * 0.70f).coerceIn(0.20f, 0.96f)),
                    0.28f to colorScheme.surface.copy(alpha = (config.opacity * 0.85f).coerceIn(0.25f, 0.97f)),
                    1.0f to colorScheme.surface.copy(alpha = (config.opacity * 0.98f).coerceIn(0.30f, 0.99f))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        } else {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorScheme.surfaceVariant.copy(alpha = (config.opacity * 0.80f).coerceIn(0.20f, 0.96f)),
                        colorScheme.surface.copy(alpha = config.opacity.coerceIn(0.25f, 0.98f))
                    ),
                    center = Offset(w * 0.4f, h * 0.15f),
                    radius = w * 1.1f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // Volumetric optical core (subtle Material surfaceTint pool)
        val coreAlpha = (config.opacity * 0.22f).coerceAtMost(0.35f)
        if (coreAlpha > 0.01f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colorScheme.surfaceTint.copy(alpha = coreAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.35f, h * 0.08f),
                    radius = w * 0.85f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // ── Layer B: Meniscus Crest & Overhead Diffuse Glare ──────────────
        if (config.glareIntensity > 0.01f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.onSurface.copy(alpha = 0.20f * config.glareIntensity),
                        colorScheme.onSurface.copy(alpha = 0.06f * config.glareIntensity),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.18f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // ── Layer C: Animated Specular Hot-Spot (Fluid Shimmer) ───────────
        if (config.glareIntensity > 0.01f) {
            val angleOffset = if (config.causticShimmer) shimmerAngle * 0.0035f else 0f
            val hotspotX = w * (0.36f + angleOffset)
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.36f * config.glareIntensity),
                        colorScheme.primary.copy(alpha = 0.16f * config.glareIntensity),
                        Color.Transparent
                    ),
                    center = Offset(hotspotX, h * 0.02f),
                    radius = w * 0.32f
                ),
                topLeft = Offset(hotspotX - w * 0.32f, -h * 0.04f),
                size = Size(w * 0.64f, h * 0.20f)
            )
        }

        // ── Layer D: Bottom Ambient Refraction Shelf ──────────────────────
        if (config.rimIntensity > 0.01f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        colorScheme.primary.copy(alpha = 0.04f * config.rimIntensity),
                        colorScheme.surfaceTint.copy(alpha = 0.08f * config.rimIntensity),
                        colorScheme.onSurface.copy(alpha = 0.07f * config.rimIntensity)
                    ),
                    startY = h * 0.80f,
                    endY = h
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // ── Layer E: Prismatic Material Refractive Rim (Outer Border) ─────
        // Dynamically disperses light using the user's Material 3 palette
        if (config.rimIntensity > 0.01f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.85f * config.rimIntensity),
                        colorScheme.primary.copy(alpha = 0.70f * config.rimIntensity),
                        Color.White.copy(alpha = 0.40f * config.rimIntensity),
                        colorScheme.tertiary.copy(alpha = 0.65f * config.rimIntensity),
                        colorScheme.secondary.copy(alpha = 0.45f * config.rimIntensity),
                        Color.White.copy(alpha = 0.80f * config.rimIntensity)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size,
                style = Stroke(width = 1.5.dp.toPx())
            )
        }

        // ── Layer F: Fresnel Inner Chamfer Shelf ──────────────────────────
        if (config.rimIntensity > 0.01f) {
            val chamferInset = 2.2.dp.toPx()
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.onSurface.copy(alpha = 0.35f * config.rimIntensity),
                        colorScheme.onSurface.copy(alpha = 0.06f * config.rimIntensity),
                        Color.Transparent
                    ),
                    startY = 0f,
                    endY = h * 0.45f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius((cr - chamferInset).coerceAtLeast(0f)),
                topLeft = Offset(chamferInset, chamferInset),
                size = Size((w - chamferInset * 2).coerceAtLeast(0f), (h - chamferInset * 2).coerceAtLeast(0f)),
                style = Stroke(width = 0.8.dp.toPx())
            )
        }

        // ── Layer G: Prismatic Lateral Micro-Dispersion ───────────────────
        if (config.rimIntensity > 0.01f) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = 0.35f * config.rimIntensity),
                        Color.Transparent,
                        colorScheme.tertiary.copy(alpha = 0.25f * config.rimIntensity)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(w, h)
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                topLeft = Offset(1f, 0f),
                size = Size(w - 1f, h),
                style = Stroke(width = 0.5.dp.toPx())
            )
        }

        // ── Layer H: Specular Micro Catch-Light Flares ────────────────────
        if (config.rimIntensity > 0.05f && config.causticShimmer) {
            val flareAlpha = (0.50f + flarePulse * 0.35f) * config.rimIntensity
            val flareRadius1 = (2.8f + flarePulse * 1.8f).dp.toPx()
            val flareRadius2 = (2.0f + (1f - flarePulse) * 1.4f).dp.toPx()

            // Top-left primary catch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = flareAlpha),
                        colorScheme.primary.copy(alpha = flareAlpha * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.16f, cr * 0.65f),
                    radius = flareRadius1 * 2.8f
                ),
                radius = flareRadius1,
                center = Offset(w * 0.16f, cr * 0.65f)
            )

            // Bottom-right tertiary catch
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = flareAlpha * 0.85f),
                        colorScheme.tertiary.copy(alpha = flareAlpha * 0.45f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.84f, h - cr * 0.70f),
                    radius = flareRadius2 * 2.8f
                ),
                radius = flareRadius2,
                center = Offset(w * 0.84f, h - cr * 0.70f)
            )
        }

        // ── Layer I: Sub-Surface Light Refraction Streaks ─────────────────
        if (config.rimIntensity > 0.05f) {
            val streakPaint = Paint().apply {
                asFrameworkPaint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 0.6.dp.toPx()
                }
            }
            val primaryArgb = android.graphics.Color.argb(
                (0.20f * config.rimIntensity * 255).toInt(),
                (colorScheme.primary.red * 255).toInt(),
                (colorScheme.primary.green * 255).toInt(),
                (colorScheme.primary.blue * 255).toInt()
            )
            val onSurfaceArgb = android.graphics.Color.argb(
                (0.18f * config.rimIntensity * 255).toInt(),
                (colorScheme.onSurface.red * 255).toInt(),
                (colorScheme.onSurface.green * 255).toInt(),
                (colorScheme.onSurface.blue * 255).toInt()
            )

            drawIntoCanvas { canvas ->
                val streaks = listOf(
                    Triple(Offset(w * 0.06f, h * 0.04f), Offset(w * 0.38f, 0f), 1f),
                    Triple(Offset(w * 0.58f, 0f), Offset(w * 0.88f, h * 0.05f), 0.8f),
                    Triple(Offset(w * 0.65f, h * 0.96f), Offset(w * 0.94f, h), 0.7f)
                )
                streaks.forEach { (from, to, factor) ->
                    streakPaint.asFrameworkPaint().shader = android.graphics.LinearGradient(
                        from.x, from.y, to.x, to.y,
                        intArrayOf(onSurfaceArgb, primaryArgb, android.graphics.Color.TRANSPARENT),
                        floatArrayOf(0f, 0.5f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    canvas.drawLine(from, to, streakPaint)
                }
            }
        }

        // ── Layer J: Frosted Micro-Texture Noise ──────────────────────────
        if (config.frostNoise > 0.001f) {
            drawRect(
                brush = GlassNoiseTexture.getBrush(),
                alpha = config.frostNoise
            )
        }
    }
}

/**
 * LiquidGlassTopGlare — a thin animated shimmer bar for the top header.
 * 100% Material 3 dynamic color compliant.
 */
@Composable
fun LiquidGlassTopGlare(
    cornerRadius: Dp = 28.dp,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "top_glare")
    val sweep by infiniteTransition.animateFloat(
        initialValue = 0.0f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "top_glare_sweep"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius))
            .height(160.dp)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colorScheme.onSurface.copy(alpha = 0.25f + sweep * 0.08f),
                        colorScheme.onSurface.copy(alpha = 0.08f + sweep * 0.03f),
                        colorScheme.surfaceTint.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = Offset(x = 320f + sweep * 80f, y = 0f),
                    radius = 650f
                )
            )
    )
}
