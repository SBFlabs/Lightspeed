package com.sbf.lightspeed.settings

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Random

private const val STYLE_LAB_NOISE_ENABLED = true

/**
 * Cached small procedural noise tile (64x64) generated once and reused across all Style Lab tiles.
 */
internal object StyleLabNoiseTile {
    private var cachedBrush: ShaderBrush? = null

    @Synchronized
    fun getBrush(): ShaderBrush? {
        if (!STYLE_LAB_NOISE_ENABLED) return null
        cachedBrush?.let { return it }
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(size * size)
        val rng = Random(1337L)
        for (i in pixels.indices) {
            val lum = if (rng.nextBoolean()) 245 else 20
            val alpha = 70 + rng.nextInt(150)
            pixels[i] = android.graphics.Color.argb(alpha, lum, lum, lum)
        }
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return ShaderBrush(shader).also { cachedBrush = it }
    }
}

private val ICON_SIZE_DP = 84.dp
private val ICON_TOP_DP = 12.dp
private val ROW_TOP_DP = 110.dp
private val ROW_HEIGHT_DP = 52.dp
private val ROW_HPAD_DP = 8.dp
private val PREVIEW_HEIGHT_DP = 176.dp
private val ROCKET_ICON_SIZE_DP = 54.dp

@Composable
private fun rememberResolvedStyleColors(preset: DeckThemeSnapshot): ResolvedStyleColors {
    val scheme = MaterialTheme.colorScheme
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return remember(preset.styleId, scheme, isApi31Plus) {
        val basePanel = Color(preset.panelColorArgb)
        val baseTint = Color(preset.tintArgb)
        val baseBorder = Color(preset.borderColorArgb)
        val baseGlow = Color(preset.glowColorArgb)

        if (preset.usesSystemDynamicColor && isApi31Plus && preset.styleId == "adaptive") {
            ResolvedStyleColors(
                panelColor = scheme.surfaceContainerHigh,
                tintColor = scheme.primary,
                borderColor = scheme.primary.copy(alpha = 0.80f),
                glowColor = scheme.tertiary
            )
        } else {
            ResolvedStyleColors(basePanel, baseTint, baseBorder, baseGlow)
        }
    }
}

@Composable
fun StyleLabCardPreview(
    preset: DeckThemeSnapshot,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val resolvedColors = rememberResolvedStyleColors(preset)
    val isApi28Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isApi33Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val iconCornerDp = remember(preset.cornerRadiusDp) { (preset.cornerRadiusDp * 0.78f).dp }
    val rowCornerDp = remember(preset.cornerRadiusDp) { (preset.cornerRadiusDp * 0.58f).dp }

    val cardsClipShape = remember(preset.styleId, iconCornerDp, rowCornerDp) {
        StyleDualPreviewCardsShape(
            styleId = preset.styleId,
            iconSize = ICON_SIZE_DP,
            iconTop = ICON_TOP_DP,
            rowTop = ROW_TOP_DP,
            rowHeight = ROW_HEIGHT_DP,
            rowHPad = ROW_HPAD_DP,
            iconCornerDp = iconCornerDp,
            rowCornerDp = rowCornerDp
        )
    }

    // Remembered native Paints for outer glow & shadow layers (API 28+, zero per-frame allocations)
    val glowPaint = remember(preset.styleId, resolvedColors.glowColor, density, isApi28Plus) {
        rememberOuterGlowPaint(preset, resolvedColors.glowColor, density, isApi28Plus)
    }

    val shadowPaint = remember(preset.styleId, density, isApi28Plus) {
        if (isApi28Plus && preset.shadowLayers >= 1) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.DITHER_FLAG).apply {
                isDither = true
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.argb(135, 0, 0, 0)
                maskFilter = BlurMaskFilter(with(density) { 5.dp.toPx() }, BlurMaskFilter.Blur.NORMAL)
            }
        } else null
    }

    // Remembered RenderEffects for API 31+ fixed blur and API 33+ progressive blur
    val primaryBlurEffect = remember(preset.styleId, density, isApi31Plus, isApi33Plus) {
        if (isApi31Plus) {
            val radiusPx = if (preset.styleId == "jelly") {
                3f
            } else if (isApi33Plus && preset.isProgressive) {
                (preset.progressiveStartBlurPx * 0.55f).coerceAtLeast(3f)
            } else {
                with(density) { (preset.inAppBlurRadiusDp * 0.75f).dp.toPx().coerceAtLeast(4f) }
            }
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }

    val progressiveEndBlurEffect = remember(preset.styleId, isApi33Plus) {
        if (isApi33Plus && preset.isProgressive) {
            val endPx = (preset.progressiveEndBlurPx * 0.75f).coerceAtLeast(16f)
            RenderEffect.createBlurEffect(endPx, endPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }

    val noiseBrush = remember { if (STYLE_LAB_NOISE_ENABLED) StyleLabNoiseTile.getBrush() else null }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PREVIEW_HEIGHT_DP)
            .clip(RoundedCornerShape(14.dp))
    ) {
        // 1. Unblurred sample backdrop per style (gradients, soft blobs, paths & stars)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                    onDrawBehind { drawSampleBackdrop(backdropSpec) }
                }
        )

        // 2. Stacked depth layers (shadowLayers) and outer glow (API 28+) behind cards
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val geom = computeCardGeometries(
                        size, density, iconCornerDp, rowCornerDp,
                        ICON_SIZE_DP, ICON_TOP_DP, ROW_TOP_DP, ROW_HEIGHT_DP, ROW_HPAD_DP, preset.styleId
                    )
                    val depthSpec = buildDepthSheetsCache(
                        preset.styleId, geom.iconRect, geom.iconCr, geom.rowRect, geom.rowCr, resolvedColors, density
                    )
                    onDrawBehind {
                        drawShadowsAndGlow(geom, preset, resolvedColors, shadowPaint, glowPaint, depthSpec)
                    }
                }
        )

        // 3. Real in-app blurred copy of the sample background clipped to the card shapes (API 31+)
        if (isApi31Plus && primaryBlurEffect != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(cardsClipShape)
                    .graphicsLayer { renderEffect = primaryBlurEffect }
                    .drawWithCache {
                        val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                        onDrawBehind { drawSampleBackdrop(backdropSpec) }
                    }
            )

            // Progressive blur second pass with vertical gradient mask on API 33+
            if (isApi33Plus && preset.isProgressive && progressiveEndBlurEffect != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(cardsClipShape)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithCache {
                            val h = size.height.coerceAtLeast(1f)
                            val iconTopF = (ICON_TOP_DP.toPx() / h).coerceIn(0f, 1f)
                            val iconBotF = ((ICON_TOP_DP + ICON_SIZE_DP).toPx() / h).coerceIn(0f, 1f)
                            val rowTopF = (ROW_TOP_DP.toPx() / h).coerceIn(0f, 1f)
                            val rowBotF = ((ROW_TOP_DP + ROW_HEIGHT_DP).toPx() / h).coerceIn(0f, 1f)
                            val maskBrush = Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                iconTopF to Color.Transparent,
                                iconBotF to Color.Black,
                                ((iconBotF + rowTopF) * 0.5f) to Color.Transparent,
                                rowTopF to Color.Transparent,
                                rowBotF to Color.Black,
                                1.0f to Color.Black
                            )
                            val maskPaint = ditheredBrushPaint(maskBrush, size).apply { blendMode = BlendMode.DstIn }
                            onDrawWithContent {
                                drawContent()
                                drawIntoCanvas { canvas ->
                                    canvas.drawRect(0f, 0f, size.width, size.height, maskPaint)
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer { renderEffect = progressiveEndBlurEffect }
                            .drawWithCache {
                                val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                                onDrawBehind { drawSampleBackdrop(backdropSpec) }
                            }
                    )
                }
            }
        }

        // 4. Card surface fills, tint, noise, glare, inner glow, specular streak & border (plus <31 scrim fallback)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val geom = computeCardGeometries(
                        size, density, iconCornerDp, rowCornerDp,
                        ICON_SIZE_DP, ICON_TOP_DP, ROW_TOP_DP, ROW_HEIGHT_DP, ROW_HPAD_DP, preset.styleId
                    )
                    val surfaceSpec = buildCardSurfaceCache(
                        geom, preset, resolvedColors, isApi31Plus, density, STYLE_LAB_NOISE_ENABLED
                    )
                    onDrawBehind { drawCardSurfaces(geom, surfaceSpec, preset, noiseBrush) }
                }
        )

        // 5. Crisp foreground content inside the icon tile and wide row card
        Box(
            modifier = Modifier
                .size(ICON_SIZE_DP)
                .align(Alignment.TopCenter)
                .offset(y = ICON_TOP_DP),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(ROCKET_ICON_SIZE_DP)
                    .drawWithCache {
                        val rocketCache = buildRocketIconCache(size)
                        onDrawBehind { drawRocketIcon(rocketCache) }
                    }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_HPAD_DP)
                .height(ROW_HEIGHT_DP)
                .offset(y = ROW_TOP_DP)
                .padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .drawWithCache {
                        val circlePaint = ditheredColorPaint(resolvedColors.tintColor.copy(alpha = 0.28f))
                        onDrawBehind {
                            drawIntoCanvas { canvas -> canvas.drawCircle(center, size.minDimension * 0.5f, circlePaint) }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
            Spacer(modifier = Modifier.width(7.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    text = "Control Center",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 11.sp
                )
                Text(
                    text = "Quick access to your tools",
                    fontSize = 8.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 9.5.sp
                )
            }
            if (preset.styleId == "jelly") {
                Box(
                    modifier = Modifier
                        .size(width = 30.dp, height = 18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4FC3FF)),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.80f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
