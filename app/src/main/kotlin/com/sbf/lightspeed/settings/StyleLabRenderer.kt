package com.sbf.lightspeed.settings

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

private val HERO_SIZE_DP = 76.dp
private val HERO_TOP_DP = 10.dp
private val ROW_TOP_DP = 96.dp
private val ROW_HEIGHT_DP = 68.dp
private val ROW_HPAD_DP = 8.dp
private val PREVIEW_HEIGHT_DP = 176.dp

/**
 * Slimmed Style Lab tile preview driven directly by the live 12-Theme optical surface engine,
 * reusable [DeckCard], and reusable [DeckButton] over real blurred sample backdrops.
 */
@Composable
fun StyleLabCardPreview(
    preset: DeckThemeSnapshot,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isApi33Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val primaryBlurEffect = remember(preset.styleId, preset.windowBlurRadiusPx, preset.progressiveStartBlurPx, isApi31Plus, isApi33Plus) {
        if (isApi31Plus && preset.windowBlurRadiusPx > 0) {
            val radiusPx = when {
                preset.styleId == "jelly" -> (preset.windowBlurRadiusPx * 0.14f).coerceIn(2f, 20f)
                isApi33Plus && preset.isProgressive -> (preset.progressiveStartBlurPx * 0.55f).coerceAtLeast(3f)
                else -> (preset.windowBlurRadiusPx * 0.45f).coerceIn(3f, 36f)
            }
            RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }

    val progressiveEndBlurEffect = remember(preset.styleId, preset.windowBlurRadiusPx, preset.progressiveEndBlurPx, isApi33Plus) {
        if (isApi33Plus && preset.isProgressive && preset.windowBlurRadiusPx > 0) {
            val endPx = (maxOf(preset.progressiveEndBlurPx, preset.windowBlurRadiusPx) * 0.75f).coerceAtLeast(16f)
            RenderEffect.createBlurEffect(endPx, endPx, Shader.TileMode.CLAMP).asComposeRenderEffect()
        } else null
    }

    val heroCornerDp = (preset.cornerRadiusDp * 0.72f).coerceIn(10f, 24f).dp
    val rowCornerDp = (preset.cornerRadiusDp * 0.58f).coerceIn(10f, 20f).dp
    val heroShape = RoundedCornerShape(heroCornerDp)
    val rowShape = RoundedCornerShape(rowCornerDp)
    val blurAvailable = isApi31Plus && preset.windowBlurRadiusPx > 0
    val cardFg = if (preset.styleId == DeckStylePresets.MATERIAL_3.styleId) MaterialTheme.colorScheme.onSurface else Color.White
    val resolvedOptical = preset.resolveOpticalColors()

    CompositionLocalProvider(LocalDeckTheme provides preset) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(PREVIEW_HEIGHT_DP)
                .clip(RoundedCornerShape(14.dp))
        ) {
            // 1. Sample backdrop with preset dim amount
            val backdropDimAlpha = (preset.dimAmount * 0.42f).coerceIn(0f, 0.72f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                        onDrawBehind {
                            drawSampleBackdrop(backdropSpec)
                            if (backdropDimAlpha > 0.01f) {
                                drawRect(Color.Black.copy(alpha = backdropDimAlpha))
                            }
                        }
                    }
            )

            // 2. Hero optical showcase tile (live Modifier.deckGlassSurface)
            Box(
                modifier = Modifier
                    .size(HERO_SIZE_DP)
                    .align(Alignment.TopCenter)
                    .offset(y = HERO_TOP_DP),
                contentAlignment = Alignment.Center
            ) {
                if (primaryBlurEffect != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(heroShape)
                            .graphicsLayer { renderEffect = primaryBlurEffect }
                            .drawWithCache {
                                val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                                onDrawBehind { drawSampleBackdrop(backdropSpec) }
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .deckGlassSurface(
                            snapshot = preset,
                            isBlurAvailable = blurAvailable,
                            isCard = false,
                            shapeOverride = heroShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = resolvedOptical.readableTintColor,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // 3. Lower live DeckCard + DeckButton preview row
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ROW_HPAD_DP)
                    .height(ROW_HEIGHT_DP)
                    .offset(y = ROW_TOP_DP)
            ) {
                if (primaryBlurEffect != null) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(rowShape)
                            .graphicsLayer { renderEffect = primaryBlurEffect }
                            .drawWithCache {
                                val backdropSpec = buildSampleBackdropCache(preset.styleId, size, density)
                                onDrawBehind { drawSampleBackdrop(backdropSpec) }
                            }
                    )
                    if (progressiveEndBlurEffect != null) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(rowShape)
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithCache {
                                    val maskPaint = ditheredBrushPaint(
                                        Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black),
                                        size
                                    ).apply { blendMode = BlendMode.DstIn }
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

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .deckGlassSurface(
                            snapshot = preset,
                            isBlurAvailable = blurAvailable,
                            isCard = false,
                            shapeOverride = rowShape
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    DeckCard(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape((preset.cornerRadiusDp * 0.42f).coerceIn(8f, 16f).dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = resolvedOptical.readableTintColor,
                                modifier = Modifier.size(15.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CENTRAL_COMMAND),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = cardFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 10.5.sp
                                )
                                Text(
                                    text = preset.localizedName(),
                                    fontSize = 7.5.sp,
                                    color = cardFg.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 9.sp
                                )
                            }
                            DeckButton(
                                onClick = {},
                                isActive = true,
                                snapshot = preset,
                                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = lsString(R.string.slab_active),
                                    fontSize = 7.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
