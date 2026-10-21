package com.sbf.lightspeed.settings

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.PopupProperties
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Unified 12-theme optical surface renderer for Central Command panels, dialogs, and miniature
 * picker tiles. Uses `Modifier.drawWithCache` so all paths, shaders, and native BlurMaskFilter
 * halo paints are allocated only when size or theme changes (zero per-frame GC churn).
 */
@Composable
fun Modifier.deckGlassSurface(
    snapshot: DeckThemeSnapshot,
    isBlurAvailable: Boolean = true,
    isMiniTile: Boolean = false,
    isFlyout: Boolean = false,
    isCard: Boolean = false,
    withNoise: Boolean = true,
    colorScheme: ColorScheme = MaterialTheme.colorScheme,
    shapeOverride: Shape? = null
): Modifier {
    val density = LocalDensity.current
    val isApi28Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colors = remember(snapshot, colorScheme, isApi31Plus) {
        val isAdaptive = snapshot.styleId == DeckStylePresets.ADAPTIVE.styleId
        val useDynamic = snapshot.usesSystemDynamicColor && isApi31Plus && isAdaptive

        if (useDynamic) {
            ResolvedStyleColors(
                panelColor = colorScheme.surfaceContainerHigh,
                tintColor = colorScheme.primary,
                borderColor = colorScheme.primary.copy(alpha = 0.85f),
                glowColor = colorScheme.tertiary
            )
        } else {
            ResolvedStyleColors(Color(snapshot.panelColorArgb), Color(snapshot.tintArgb), Color(snapshot.borderColorArgb), Color(snapshot.glowColorArgb))
        }
    }

    val noiseBrush = remember(snapshot.noiseStrength, snapshot.styleId, withNoise, isCard, isMiniTile) {
        if (!isCard && !isMiniTile && withNoise && snapshot.styleId == "acrylic" && snapshot.noiseStrength > 0.01f) {
            GlassNoiseTexture.getBrush()
        } else null
    }

    return this.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val safeSize = Size(w, h)
        val crPx = if (shapeOverride is RoundedCornerShape) {
            shapeOverride.topStart.toPx(safeSize, density)
        } else {
            with(density) {
                (if (isMiniTile) snapshot.cornerRadiusDp * 0.42f else snapshot.cornerRadiusDp).dp.toPx()
            }
        }
        val cr = CornerRadius(crPx, crPx)
        val rect = Rect(0f, 0f, w, h)
        val outlinePath = Path().apply { addRoundRect(RoundRect(rect, cr)) }

        val cache = buildUnifiedDeckOpticalCache(
            snapshot = snapshot,
            colors = colors,
            rect = rect,
            cr = cr,
            safeSize = safeSize,
            isBlurAvailable = isBlurAvailable,
            isMiniTile = isMiniTile,
            isFlyout = isFlyout,
            isCard = isCard,
            isApi28Plus = isApi28Plus,
            density = density
        )

        onDrawBehind {
            drawIntoCanvas { canvas ->
                // 1. Stacked 3D rotated depth sheets behind the card ("depth")
                for (sheet in cache.depthSheets) {
                    if (cache.shadowPaint != null) {
                        canvas.nativeCanvas.drawPath(sheet.shadowPath, cache.shadowPaint)
                    }
                    canvas.drawPath(sheet.sheetPath, sheet.fillPaint)
                    canvas.drawPath(sheet.sheetPath, sheet.tintPaint)
                    canvas.drawPath(sheet.sheetPath, sheet.borderPaint)
                }

                // 2. Outer neon / ambient bloom halo (API 28+)
                if (cache.outerHaloPaint != null) {
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, w, h, crPx, crPx, cache.outerHaloPaint
                    )
                }
                if (cache.secondaryHaloPaint != null) {
                    canvas.nativeCanvas.drawRoundRect(
                        0f, 0f, w, h, crPx, crPx, cache.secondaryHaloPaint
                    )
                }
            }

            // 3. Clipped interior optical layers
            clipPath(outlinePath) {
                drawIntoCanvas { canvas ->
                    canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, cache.substratePaint)
                    canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, cache.tintInfusionPaint)
                    cache.lavaRefractionPaint?.let {
                        canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, it)
                    }
                    cache.secondaryPocketPaint?.let {
                        canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, it)
                    }
                }

                if (noiseBrush != null && cache.noiseAlpha > 0.005f) {
                    drawRect(brush = noiseBrush, alpha = cache.noiseAlpha)
                }

                drawIntoCanvas { canvas ->
                    cache.topGlarePaint?.let {
                        canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, it)
                    }
                    cache.bottomCatchPaint?.let {
                        canvas.drawRoundRect(0f, 0f, w, h, crPx, crPx, it)
                    }
                    cache.innerCushionPaint?.let {
                        canvas.drawPath(cache.innerCushionPath, it)
                    }
                    if (cache.specularPath != null && cache.specularPaint != null) {
                        canvas.drawPath(cache.specularPath, cache.specularPaint)
                    }
                }
            }

            // 4. Crisp outer refractive / neon / chamfer border
            drawIntoCanvas { canvas ->
                cache.borderPaint?.let {
                    canvas.drawPath(outlinePath, it)
                }
            }
        }
    }
}

/**
 * Reusable floating container wrapper that combines:
 * 1. [ClippedFallbackBlurLayer] clipped strictly to the theme's rounded corners (for OEMs without SurfaceFlinger window blur),
 * 2. [Modifier.deckGlassSurface] rendering the full 12-theme optical stack (including outer neon bloom & 3D rotated depth sheets),
 * 3. Foreground content clipped to the card's rounded geometry.
 */
@Composable
fun DeckGlassCardSurface(
    modifier: Modifier = Modifier,
    snapshotOverride: DeckThemeSnapshot? = null,
    isFlyout: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val activeSnapshot by DeckThemeManager.snapshotFlow.collectAsState()
    val snapshot = snapshotOverride ?: activeSnapshot
    val isBlurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (CentralCommandActivity.isCrossWindowBlurActive || CentralCommandBackdropBlur.backdropBitmap != null)
    val shape = RoundedCornerShape(snapshot.cornerRadiusDp.dp)

    // Hardware window blur for modal dialog windows & clear rectangular Android window substrate
    val view = LocalView.current
    SideEffect {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        dialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dialogWindow != null) {
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = dialogWindow.attributes
                if (lp != null) {
                    lp.blurBehindRadius = snapshot.windowBlurRadiusPx.coerceIn(20, 150)
                    dialogWindow.attributes = lp
                }
            } else {
                val rootView = view.rootView
                val lp = rootView.layoutParams as? WindowManager.LayoutParams
                if (lp != null && (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) {
                    lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                    lp.blurBehindRadius = snapshot.windowBlurRadiusPx.coerceIn(20, 150)
                    val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    try {
                        wm?.updateViewLayout(rootView, lp)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    Box(modifier = modifier) {
        // Layer 1: Clipped fallback hardware blur strictly inside the card's rounded bounds
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
        ) {
            ClippedFallbackBlurLayer(snapshot = snapshot)
        }

        // Layer 2: Unified 12-theme optical surface (draws outer glow/depth sheets + clipped glass finish)
        Box(
            modifier = Modifier
                .matchParentSize()
                .deckGlassSurface(
                    snapshot = snapshot,
                    isBlurAvailable = isBlurAvailable,
                    isFlyout = isFlyout
                )
        )

        // Layer 3: Foreground UI content clipped to the card's rounded shape
        Box(
            modifier = Modifier.clip(shape),
            content = content
        )
    }
}

/**
 * Verified readable color set derived from [DeckThemeSnapshot] for optical surfaces,
 * subchips, cards, and floating/sliding panels.
 */
data class OpticalSurfaceColors(
    val panelColor: Color,
    val tintColor: Color,
    val readableTintColor: Color,
    val borderColor: Color
)

/**
 * Ensures a color has sufficient relative luminance against dark UI surfaces.
 * Preserves chromatic hue while lightening towards readable white/pastel if needed.
 */
fun ensureReadableContrast(color: Color, minLuminance: Float = 0.38f): Color {
    val targetAlpha = color.alpha.coerceAtLeast(0.88f)
    val lum = color.luminance()
    if (lum >= minLuminance) return color.copy(alpha = targetAlpha)
    val factor = ((minLuminance - lum) / (1f - lum)).coerceIn(0f, 0.88f)
    return Color(
        red = (color.red + (1f - color.red) * factor).coerceIn(0f, 1f),
        green = (color.green + (1f - color.green) * factor).coerceIn(0f, 1f),
        blue = (color.blue + (1f - color.blue) * factor).coerceIn(0f, 1f),
        alpha = targetAlpha
    )
}

/**
 * Resolves theme colours with guaranteed text/icon legibility and dynamic colour support.
 */
@Composable
fun DeckThemeSnapshot.resolveOpticalColors(): OpticalSurfaceColors {
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = MaterialTheme.colorScheme
    val isAdaptive = styleId == DeckStylePresets.ADAPTIVE.styleId
    val useDynamic = usesSystemDynamicColor && isApi31Plus && isAdaptive

    val safeTintArgb = if (tintArgb == 0x00FFFFFFL) 0xFFFFFFFFL else tintArgb
    val rawTint = if (useDynamic) colorScheme.primary else Color(safeTintArgb)
    val panel = if (useDynamic) colorScheme.surfaceContainerHigh else Color(panelColorArgb)
    val rawBorder = if (useDynamic) colorScheme.primary.copy(alpha = 0.80f) else Color(borderColorArgb)

    val readableTint = ensureReadableContrast(rawTint)
    val effectiveBorder = if (rawBorder.alpha < 0.15f) readableTint.copy(alpha = 0.40f) else rawBorder

    return OpticalSurfaceColors(
        panelColor = panel,
        tintColor = rawTint,
        readableTintColor = readableTint,
        borderColor = effectiveBorder
    )
}

/**
 * 12-Theme Optical Dropdown Menu.
 * Wraps [DropdownMenu] in the authentic glass optical stack of the active theme
 * (Glass Lava, Liquid Jelly, Cyber Neon, Frosted, etc.) with themed borders,
 * specular highlight caps, and ambient glow.
 */
@Composable
fun DeckThemedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    anchorWidth: androidx.compose.ui.unit.Dp? = null,
    snapshotOverride: DeckThemeSnapshot? = null,
    properties: PopupProperties = PopupProperties(focusable = true),
    content: @Composable ColumnScope.() -> Unit
) {
    val activeSnapshot = LocalDeckTheme.current
    val snapshot = snapshotOverride ?: activeSnapshot
    val cornerRadius = snapshot.cornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)

    val menuModifier = if (anchorWidth != null && anchorWidth > 0.dp) {
        Modifier.width(anchorWidth).then(modifier).clip(shape)
    } else {
        Modifier.widthIn(min = 180.dp, max = 260.dp).then(modifier).clip(shape)
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = menuModifier,
        offset = offset,
        properties = properties,
        shape = shape,
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = null,
        content = {
            val view = LocalView.current
            SideEffect {
                val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                dialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val rootView = view.rootView
                    val lp = rootView.layoutParams as? WindowManager.LayoutParams
                    if (lp != null && (lp.flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) == 0) {
                        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                        lp.blurBehindRadius = snapshot.windowBlurRadiusPx.coerceIn(20, 120)
                        val wm = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                        try {
                            wm?.updateViewLayout(rootView, lp)
                        } catch (_: Exception) {}
                    }
                }
            }
            Box(modifier = Modifier.clip(shape)) {
                // Layer 1: Clipped fallback physical hardware blur directly inside dropdown
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(shape)
                ) {
                    ClippedFallbackBlurLayer(snapshot = snapshot)
                }

                // Layer 2: Unified 12-theme optical surface
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .deckGlassSurface(
                            snapshot = snapshot,
                            isBlurAvailable = true,
                            isMiniTile = false,
                            isFlyout = true
                        )
                )
                Column(
                    modifier = Modifier
                        .clip(shape)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    content()
                }
            }
        }
    )
}
