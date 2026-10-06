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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.runtime.withFrameNanos
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
// AGSL caustic light shader — a GENERATIVE backdrop layer (does not sample or
// distort the content above it, so text/buttons stay crisp).
// Draws animated caustic light-web lines (domain-crossed fbm fields) tinted by
// the Material 3 dynamic primary color. Premultiplied alpha output.
// All literals are strict floats (MediaTek Mali / ARM drivers reject int->float).
// Requires API 33+ (RuntimeShader).
// ---------------------------------------------------------------------------
private const val LIQUID_CAUSTIC_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float uIntensity;
uniform float3 uTint;

float hash(float2 p) {
    float3 p3 = fract(float3(p.x, p.y, p.x) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(float2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p = p * 2.03 + float2(17.0, 9.0);
        a *= 0.5;
    }
    return v;
}

half4 main(float2 coord) {
    float scale = max(uResolution.x, 1.0);
    float2 uv = coord / scale;
    float2 p = uv * 5.0;
    float t = uTime;

    float n1 = fbm(p + float2(t * 0.30, t * 0.18));
    float n2 = fbm(p * 1.15 + float2(-t * 0.22, t * 0.27) + float2(7.3, 2.1));

    // Bright web where the two moving fields cross.
    float d = abs(n1 - n2);
    float web = pow(1.0 - clamp(d * 5.0, 0.0, 1.0), 3.0);
    float pool = smoothstep(0.35, 0.75, n1) * 0.35;

    float a = clamp((web * 0.85 + pool) * uIntensity * 0.55, 0.0, 0.85);
    float3 lightColor = mix(float3(1.0, 1.0, 1.0), uTint, 0.40);
    return half4(half3(lightColor * a), half(a));
}
"""

/** Process-wide check: does this device's driver compile the caustic shader? (cached) */
internal object LiquidGlassAgsl {
    val isAvailable: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            false
        } else {
            try {
                android.graphics.RuntimeShader(LIQUID_CAUSTIC_AGSL)
                true
            } catch (e: Exception) {
                android.util.Log.e("LightspeedShader", "Caustic AGSL failed to compile on this GPU driver", e)
                false
            }
        }
    }
}

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

    val activeTint = if (config.useCustomColor) Color(config.customColor) else colorScheme.primary

    // Harmonized chromatic palette for custom tint or dynamic Material 3 Monet
    val effectiveColorScheme = remember(config.useCustomColor, config.customColor, colorScheme) {
        if (config.useCustomColor) {
            val hsv = FloatArray(3)
            val argb = (config.customColor.toLong() and 0xFFFFFFFFL).toInt()
            android.graphics.Color.colorToHSV(argb, hsv)
            val secHsv = floatArrayOf((hsv[0] + 30f) % 360f, (hsv[1] * 0.70f).coerceIn(0f, 1f), hsv[2])
            val terHsv = floatArrayOf((hsv[0] + 60f) % 360f, (hsv[1] * 0.85f).coerceIn(0f, 1f), hsv[2])
            val secColor = Color(android.graphics.Color.HSVToColor(secHsv))
            val terColor = Color(android.graphics.Color.HSVToColor(terHsv))
            colorScheme.copy(
                primary = activeTint,
                surfaceTint = activeTint,
                secondary = secColor,
                tertiary = terColor
            )
        } else {
            colorScheme
        }
    }

    // ── AGSL caustic setup (API 33+, driver-verified, user-toggleable) ──
    val agslActive = config.agslEnabled && config.agslIntensity > 0.01f &&
        LiquidGlassAgsl.isAvailable
    val agslShader: android.graphics.RuntimeShader? = if (agslActive) {
        remember { android.graphics.RuntimeShader(LIQUID_CAUSTIC_AGSL) }
    } else null
    val agslBrush: ShaderBrush? = remember(agslShader) { agslShader?.let { ShaderBrush(it) } }
    val currentSpeed by rememberUpdatedState(config.agslSpeed)
    var agslPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(agslActive) {
        if (agslActive) {
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) agslPhase += (now - last) / 1_000_000_000f * currentSpeed
                    last = now
                }
            }
        }
    }
    val tintR = effectiveColorScheme.primary.red
    val tintG = effectiveColorScheme.primary.green
    val tintB = effectiveColorScheme.primary.blue

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cr = cornerRadius.toPx()

        // ── Layer A: Progressive Substrate Body (Material Surface Tint) ──
        if (config.progressiveDepth) {
            // Progressive blur ramp: luminous translucent meniscus at apex -> deep resting base
            drawRoundRect(
                brush = Brush.verticalGradient(
                    0.0f to effectiveColorScheme.surfaceVariant.copy(alpha = (config.opacity * 0.70f).coerceIn(0.20f, 0.96f)),
                    0.28f to effectiveColorScheme.surface.copy(alpha = (config.opacity * 0.85f).coerceIn(0.25f, 0.97f)),
                    1.0f to effectiveColorScheme.surface.copy(alpha = (config.opacity * 0.98f).coerceIn(0.30f, 0.99f))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        } else {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        effectiveColorScheme.surfaceVariant.copy(alpha = (config.opacity * 0.80f).coerceIn(0.20f, 0.96f)),
                        effectiveColorScheme.surface.copy(alpha = config.opacity.coerceIn(0.25f, 0.98f))
                    ),
                    center = Offset(w * 0.4f, h * 0.15f),
                    radius = w * 1.1f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // Chromatic volumetric optical core & tint infusion pool (Material 3 or Custom)
        val coreAlpha = (config.opacity * config.tintIntensity * 0.45f).coerceIn(0f, 0.65f)
        if (coreAlpha > 0.01f) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        effectiveColorScheme.primary.copy(alpha = coreAlpha),
                        effectiveColorScheme.primary.copy(alpha = coreAlpha * 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(w * 0.35f, h * 0.12f),
                    radius = w * 0.95f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // ── Layer A2: AGSL Caustic Light (GPU, API 33+) ─────────────────
        if (agslShader != null) {
            agslShader.setFloatUniform("uResolution", w.coerceAtLeast(1f), h.coerceAtLeast(1f))
            agslShader.setFloatUniform("uTime", agslPhase)
            agslShader.setFloatUniform("uIntensity", config.agslIntensity)
            agslShader.setFloatUniform("uTint", tintR, tintG, tintB)
            drawRoundRect(
                brush = agslBrush!!,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr),
                size = size
            )
        }

        // ── Layer B: Meniscus Crest & Overhead Diffuse Glare ──────────────
        if (config.glareIntensity > 0.01f) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        effectiveColorScheme.onSurface.copy(alpha = 0.20f * config.glareIntensity),
                        effectiveColorScheme.onSurface.copy(alpha = 0.06f * config.glareIntensity),
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
                        effectiveColorScheme.primary.copy(alpha = 0.16f * config.glareIntensity),
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
                        effectiveColorScheme.primary.copy(alpha = 0.04f * config.rimIntensity),
                        effectiveColorScheme.surfaceTint.copy(alpha = 0.08f * config.rimIntensity),
                        effectiveColorScheme.onSurface.copy(alpha = 0.07f * config.rimIntensity)
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
                        effectiveColorScheme.primary.copy(alpha = 0.70f * config.rimIntensity),
                        Color.White.copy(alpha = 0.40f * config.rimIntensity),
                        effectiveColorScheme.tertiary.copy(alpha = 0.65f * config.rimIntensity),
                        effectiveColorScheme.secondary.copy(alpha = 0.45f * config.rimIntensity),
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
                        effectiveColorScheme.onSurface.copy(alpha = 0.35f * config.rimIntensity),
                        effectiveColorScheme.onSurface.copy(alpha = 0.06f * config.rimIntensity),
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
                        effectiveColorScheme.primary.copy(alpha = 0.35f * config.rimIntensity),
                        Color.Transparent,
                        effectiveColorScheme.tertiary.copy(alpha = 0.25f * config.rimIntensity)
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
                        effectiveColorScheme.primary.copy(alpha = flareAlpha * 0.5f),
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
                        effectiveColorScheme.tertiary.copy(alpha = flareAlpha * 0.45f),
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
                (effectiveColorScheme.primary.red * 255).toInt(),
                (effectiveColorScheme.primary.green * 255).toInt(),
                (effectiveColorScheme.primary.blue * 255).toInt()
            )
            val onSurfaceArgb = android.graphics.Color.argb(
                (0.18f * config.rimIntensity * 255).toInt(),
                (effectiveColorScheme.onSurface.red * 255).toInt(),
                (effectiveColorScheme.onSurface.green * 255).toInt(),
                (effectiveColorScheme.onSurface.blue * 255).toInt()
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
