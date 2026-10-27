package com.sbf.lightspeed.settings

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * AGSL "liquid glass" refraction for the Glass Lava (prism) theme (API 33+).
 *
 * Takes the same screen snapshot as the blur workaround ([CentralCommandBackdropBlur.backdropBitmap])
 * but keeps it SHARP: pixels near the card rim are pulled in from deeper inside the card (lens bevel),
 * split slightly per colour channel (chromatic dispersion), lightly saturated, and finished with a
 * bright rim and soft inner glow. No blur is applied.
 */
private const val LIQUID_REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float scale;

float sdRoundRect(float2 p, float2 hsz, float r) {
    float2 q = abs(p) - (hsz - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half3 sampleDisp(float2 p, float2 offs) {
    float2 mx = size - float2(1.0);
    half r = content.eval(clamp(p + offs * 0.14, float2(1.0), mx)).r;
    half g = content.eval(clamp(p, float2(1.0), mx)).g;
    half b = content.eval(clamp(p - offs * 0.14, float2(1.0), mx)).b;
    return half3(r, g, b);
}

half4 main(float2 fragCoord) {
    float2 c = size * 0.5;
    float2 p = fragCoord - c;
    float d = sdRoundRect(p, c, radius);
    float depth = clamp(-d / bevel, 0.0, 1.0);
    float edge = pow(1.0 - depth, 1.7);

    float e = 1.5;
    float2 grad = float2(
        sdRoundRect(p + float2(e, 0.0), c, radius) - sdRoundRect(p - float2(e, 0.0), c, radius),
        sdRoundRect(p + float2(0.0, e), c, radius) - sdRoundRect(p - float2(0.0, e), c, radius)
    );
    float2 g = grad / max(length(grad), 0.0001);

    // Lens: gentle pull of the background inward near the rim
    float2 offs = -g * edge * strength;
    float2 base = fragCoord + offs;

    // Soft uniform blurring across interior, fading to razor sharp at the refractive bevel edge
    float br = mix(2.0 * scale, 0.4 * scale, edge);
    half3 acc = sampleDisp(base, offs);
    for (int i = 0; i < 8; i++) {
        float ang = float(i) * 0.785398;
        acc += sampleDisp(base + float2(cos(ang), sin(ang)) * br, offs);
    }
    half3 col = acc / 9.0;

    // Neutral glass transparency: slight luma bump, completely neutral tone (no forced blue tint)
    float luma = dot(col, half3(0.299, 0.587, 0.114));
    col = mix(half3(luma), col, 1.08);
    col *= mix(0.85, 1.0, edge);

    // Rim lighting: pure white specular gleam on top-left, soft rim catch
    float rimW = 2.0 * scale;
    float a = 1.0 - clamp(-d / (rimW * 1.5), 0.0, 1.0);
    a = a * a;
    float facing = dot(g, normalize(float2(-0.6, -0.8)));
    float spec = pow(max(facing, 0.0), 2.2) * a;
    float back = pow(max(-facing, 0.0), 1.8) * a;
    col += half3(spec * 0.85); // Pure neutral white gleam
    col += half3(0.18) * back; // Soft neutral rim
    col += half3(0.15) * a;

    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

/** One-time compile probe: if the AGSL fails on this GPU driver, callers fall back to the blur layer. */
internal object LiquidRefractionProbe {
    val isSupported: Boolean by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            false
        } else {
            try {
                val shader = RuntimeShader(LIQUID_REFRACTION_AGSL)
                shader.setFloatUniform("size", 100f, 100f)
                shader.setFloatUniform("radius", 20f)
                shader.setFloatUniform("bevel", 20f)
                shader.setFloatUniform("strength", 20f)
                shader.setFloatUniform("scale", 1f)
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                val blurEffect = RenderEffect.createBlurEffect(10f, 10f, Shader.TileMode.CLAMP)
                RenderEffect.createChainEffect(shaderEffect, blurEffect)
                true
            } catch (e: Throwable) {
                android.util.Log.e("LightspeedShader", "Liquid refraction AGSL unsupported, using blur fallback", e)
                false
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
internal fun LiquidRefractionLayer(
    bitmap: ImageBitmap,
    snapshot: DeckThemeSnapshot,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val config = LocalConfiguration.current
    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx().roundToInt() }.coerceAtLeast(1)
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx().roundToInt() }.coerceAtLeast(1)

    val view = LocalView.current
    val screenLocation = remember { IntArray(2) }
    var screenOffset by remember { mutableStateOf(IntOffset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val cornerPx = with(density) { snapshot.cornerRadiusDp.dp.toPx() }
    val bevelPx = with(density) { 22.dp.toPx() }
    val strengthPx = with(density) { 24.dp.toPx() }
    val scalePx = density.density

    val blurRadiusPx = snapshot.windowBlurRadiusPx
    val effect = remember(boxSize, cornerPx, bevelPx, strengthPx, scalePx, blurRadiusPx) {
        if (boxSize.width <= 0 || boxSize.height <= 0) {
            null
        } else {
            try {
                val shader = RuntimeShader(LIQUID_REFRACTION_AGSL)
                shader.setFloatUniform("size", boxSize.width.toFloat(), boxSize.height.toFloat())
                shader.setFloatUniform("radius", cornerPx)
                shader.setFloatUniform("bevel", bevelPx)
                shader.setFloatUniform("strength", strengthPx)
                shader.setFloatUniform("scale", scalePx)
                val shaderEffect = RenderEffect.createRuntimeShaderEffect(shader, "content")
                val blurPx = (blurRadiusPx * 0.35f).coerceIn(0f, 60f)
                if (blurPx >= 1f) {
                    val blurEffect = RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    RenderEffect.createChainEffect(shaderEffect, blurEffect).asComposeRenderEffect()
                } else {
                    shaderEffect.asComposeRenderEffect()
                }
            } catch (e: Exception) {
                android.util.Log.e("LightspeedShader", "Liquid refraction AGSL failed", e)
                null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { coords ->
                view.getLocationOnScreen(screenLocation)
                val posInWindow = coords.positionInWindow()
                val screenX = screenLocation[0] + posInWindow.x.roundToInt()
                val screenY = screenLocation[1] + posInWindow.y.roundToInt()
                screenOffset = IntOffset(screenX, screenY)
            }
    ) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            val metrics = view.context.resources.displayMetrics
            val screenW = metrics.widthPixels.coerceAtLeast(1)
            val screenH = metrics.heightPixels.coerceAtLeast(1)
            val scaleX = bitmap.width.toFloat() / screenW
            val scaleY = bitmap.height.toFloat() / screenH

            val srcX = (screenOffset.x * scaleX).roundToInt().coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
            val srcY = (screenOffset.y * scaleY).roundToInt().coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
            val srcW = (boxSize.width * scaleX).roundToInt().coerceIn(1, bitmap.width - srcX)
            val srcH = (boxSize.height * scaleY).roundToInt().coerceIn(1, bitmap.height - srcY)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { renderEffect = effect }
                    .drawWithContent {
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset(srcX, srcY),
                            srcSize = IntSize(srcW, srcH),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(boxSize.width, boxSize.height)
                        )
                    }
            )
        }
    }
}
