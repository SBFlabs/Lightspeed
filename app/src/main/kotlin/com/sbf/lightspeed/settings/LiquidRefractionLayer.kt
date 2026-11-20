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
import androidx.compose.runtime.mutableIntStateOf
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
 * Light bending (lens refraction) and chromatic dispersion are strictly confined
 * to the outer rim bevel (perimeter chamfer). In the center of the card, the glass
 * is parallel and flat: light passes straight through 1:1 without geometric warping,
 * maintaining crystal-clear UI legibility over the frosted backdrop blur.
 */
private const val LIQUID_REFRACTION_AGSL = """
uniform shader content;
uniform float2 size;
uniform float radius;
uniform float bevel;
uniform float strength;
uniform float scale;
uniform float gleam;

float sdRoundRect(float2 p, float2 hsz, float r) {
    float2 q = abs(p) - (hsz - float2(r));
    return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

half4 main(float2 fragCoord) {
    float2 c = size * 0.5;
    float2 p = fragCoord - c;
    float d = sdRoundRect(p, c, radius);
    float depth = clamp(-d / bevel, 0.0, 1.0);
    float edge = smoothstep(1.0, 0.0, depth);

    float2 mx = size - float2(1.0);
    half3 col;

    if (edge > 0.0001) {
        // Border Bevel Zone: light is refracted inward strictly along the outer rim
        float e = 1.5;
        float2 grad = float2(
            sdRoundRect(p + float2(e, 0.0), c, radius) - sdRoundRect(p - float2(e, 0.0), c, radius),
            sdRoundRect(p + float2(0.0, e), c, radius) - sdRoundRect(p - float2(0.0, e), c, radius)
        );
        float2 g = grad / max(length(grad), 0.0001);
        float2 offs = -g * edge * strength;
        float2 base = fragCoord + offs;

        // Chromatic dispersion: subtle prismatic split along the chamfer
        half r = content.eval(clamp(base + offs * 0.12, float2(0.0), mx)).r;
        half gCol = content.eval(clamp(base, float2(0.0), mx)).g;
        half b = content.eval(clamp(base - offs * 0.12, float2(0.0), mx)).b;
        col = half3(r, gCol, b);
        col *= mix(0.92, 1.0, edge);
    } else {
        // Center Zone: Flat optical transmission with zero bending or distortion
        col = content.eval(clamp(fragCoord, float2(0.0), mx)).rgb;
    }

    // Anti-aliased outer edge mask: strictly clip any pixel outside the rounded boundary
    float mask = clamp(0.5 - d, 0.0, 1.0);
    if (mask <= 0.0) {
        return half4(0.0);
    }

    // Rim lighting: subtle, even perimeter rim catch strictly at outer chamfer (no fake top-left corner spike)
    float rimW = 2.0 * scale;
    float a = (d <= 0.0) ? (1.0 - clamp(-d / (rimW * 1.5), 0.0, 1.0)) : 0.0;
    if (a > 0.001 && gleam > 0.001) {
        a = a * a;
        col += half3(0.28 * gleam) * a;
    }

    return half4(clamp(col, 0.0, 1.0) * mask, mask);
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
                shader.setFloatUniform("gleam", 0.5f)
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
    var basePosX by remember { mutableIntStateOf(Int.MIN_VALUE) }
    var basePosY by remember { mutableIntStateOf(Int.MIN_VALUE) }
    val dragOffsetYProvider = LocalDeckCardDragOffsetY.current
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    val cornerPx = with(density) { snapshot.cornerRadiusDp.dp.toPx() }
    val bevelPx = with(density) { 16.dp.toPx() }
    val strengthPx = with(density) { 14.dp.toPx() }
    val scalePx = density.density

    val blurRadiusPx = snapshot.windowBlurRadiusPx
    val gleamStrength = snapshot.highlightStrength.coerceIn(0f, 1f)
    val effect = remember(boxSize, cornerPx, bevelPx, strengthPx, scalePx, blurRadiusPx, gleamStrength) {
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
                shader.setFloatUniform("gleam", gleamStrength)
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
            .onSizeChanged {
                boxSize = it
                basePosX = Int.MIN_VALUE
                basePosY = Int.MIN_VALUE
            }
            .onGloballyPositioned { coords ->
                view.getLocationOnScreen(screenLocation)
                val posInWindow = coords.positionInWindow()
                val currentDrag = dragOffsetYProvider().roundToInt()
                val newX = screenLocation[0] + posInWindow.x.roundToInt()
                val newY = screenLocation[1] + posInWindow.y.roundToInt() - currentDrag
                if (basePosX != newX) basePosX = newX
                if (basePosY != newY) basePosY = newY
            }
    ) {
        if (boxSize.width > 0 && boxSize.height > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = effect
                        clip = true
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(snapshot.cornerRadiusDp.dp)
                    }
                    .drawWithContent {
                        if (basePosX == Int.MIN_VALUE || basePosY == Int.MIN_VALUE) return@drawWithContent
                        val currentDrag = dragOffsetYProvider().roundToInt()
                        val screenX = basePosX
                        val screenY = basePosY + currentDrag

                        // 1:1 Native Resolution Mapping
                        val srcX = screenX.coerceIn(0, (bitmap.width - 1).coerceAtLeast(0))
                        val srcY = screenY.coerceIn(0, (bitmap.height - 1).coerceAtLeast(0))
                        val srcW = boxSize.width.coerceIn(1, (bitmap.width - srcX).coerceAtLeast(1))
                        val srcH = boxSize.height.coerceIn(1, (bitmap.height - srcY).coerceAtLeast(1))

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
