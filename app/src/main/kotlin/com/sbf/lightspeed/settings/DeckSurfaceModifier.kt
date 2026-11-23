package com.sbf.lightspeed.settings

import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
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
    shapeOverride: Shape? = null,
    useLightweightHudBlur: Boolean = false
): Modifier {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isApi28Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    val isApi31Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colors = remember(snapshot, colorScheme, isApi31Plus, isCard, isFlyout, useLightweightHudBlur) {
        val isMaterial3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
        val isAdaptive = snapshot.styleId == DeckStylePresets.ADAPTIVE.styleId
        val useDynamic = snapshot.usesSystemDynamicColor && (isMaterial3 || (isApi31Plus && isAdaptive))

        if (isMaterial3 && !useLightweightHudBlur) {
            val container = when {
                isCard -> colorScheme.surfaceContainerHigh
                isFlyout -> colorScheme.surfaceContainer
                else -> colorScheme.surfaceContainerLow
            }
            ResolvedStyleColors(
                panelColor = container,
                tintColor = colorScheme.primary,
                borderColor = colorScheme.outlineVariant,
                glowColor = Color.Transparent
            )
        } else if (useDynamic) {
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
        val (topLeftCr, topRightCr, bottomRightCr, bottomLeftCr) = if (shapeOverride is RoundedCornerShape) {
            val ts = shapeOverride.topStart.toPx(safeSize, density)
            val te = shapeOverride.topEnd.toPx(safeSize, density)
            val be = shapeOverride.bottomEnd.toPx(safeSize, density)
            val bs = shapeOverride.bottomStart.toPx(safeSize, density)
            listOf(CornerRadius(ts, ts), CornerRadius(te, te), CornerRadius(be, be), CornerRadius(bs, bs))
        } else {
            val defCr = with(density) {
                (if (isMiniTile) snapshot.cornerRadiusDp * 0.42f else snapshot.cornerRadiusDp).dp.toPx()
            }
            listOf(CornerRadius(defCr, defCr), CornerRadius(defCr, defCr), CornerRadius(defCr, defCr), CornerRadius(defCr, defCr))
        }

        val cr = topLeftCr
        val rect = Rect(0f, 0f, w, h)
        val customOutline = shapeOverride?.takeIf { it !is RoundedCornerShape }
            ?.createOutline(safeSize, layoutDirection, density)
        val isGenericShape = customOutline is Outline.Generic
        val outlinePath = when (customOutline) {
            is Outline.Generic -> customOutline.path
            is Outline.Rounded -> Path().apply { addRoundRect(customOutline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(customOutline.rect) }
            else -> Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = rect,
                        topLeft = topLeftCr,
                        topRight = topRightCr,
                        bottomRight = bottomRightCr,
                        bottomLeft = bottomLeftCr
                    )
                )
            }
        }

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
            density = density,
            bottomCr = bottomRightCr,
            useLightweightHudBlur = useLightweightHudBlur
        )

        onDrawBehind {
            drawIntoCanvas { canvas ->
                // 1. Stacked 3D rotated depth sheets behind the card ("depth")
                if (!isGenericShape) {
                    for (sheet in cache.depthSheets) {
                        if (cache.shadowPaint != null) {
                            canvas.nativeCanvas.drawPath(sheet.shadowPath, cache.shadowPaint)
                        }
                        canvas.drawPath(sheet.sheetPath, sheet.fillPaint)
                        canvas.drawPath(sheet.sheetPath, sheet.tintPaint)
                        canvas.drawPath(sheet.sheetPath, sheet.borderPaint)
                    }
                }

                // 2. Outer neon / ambient bloom halo (API 28+)
                if (cache.outerHaloPaint != null) {
                    canvas.nativeCanvas.drawPath(outlinePath.asAndroidPath(), cache.outerHaloPaint)
                }
                if (cache.secondaryHaloPaint != null) {
                    canvas.nativeCanvas.drawPath(outlinePath.asAndroidPath(), cache.secondaryHaloPaint)
                }
            }

            // 3. Clipped interior optical layers
            clipPath(outlinePath) {
                drawIntoCanvas { canvas ->
                    canvas.drawPath(outlinePath, cache.substratePaint)
                    canvas.drawPath(outlinePath, cache.tintInfusionPaint)
                    cache.lavaRefractionPaint?.let {
                        canvas.drawPath(outlinePath, it)
                    }
                    cache.secondaryPocketPaint?.let {
                        canvas.drawPath(outlinePath, it)
                    }
                }

                if (noiseBrush != null && cache.noiseAlpha > 0.005f) {
                    drawRect(brush = noiseBrush, alpha = cache.noiseAlpha)
                }

                drawIntoCanvas { canvas ->
                    cache.topGlarePaint?.let {
                        canvas.drawPath(outlinePath, it)
                    }
                    cache.bottomCatchPaint?.let {
                        canvas.drawPath(outlinePath, it)
                    }
                    cache.innerCushionPaint?.let {
                        canvas.drawPath(
                            if (isGenericShape) outlinePath else cache.innerCushionPath,
                            it
                        )
                    }
                    if (!isGenericShape && cache.specularPath != null && cache.specularPaint != null) {
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
    shapeOverride: Shape? = null,
    useLightweightHudBlur: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val activeSnapshot by DeckThemeManager.snapshotFlow.collectAsState()
    val snapshot = snapshotOverride ?: activeSnapshot
    val view = LocalView.current
    val blurMode by BackdropBlurModeManager.modeFlow.collectAsState()
    val isBlurAvailable = (useLightweightHudBlur && CentralCommandBackdropBlur.backdropBitmap != null) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (CentralCommandActivity.isCrossWindowBlurActive || CentralCommandBackdropBlur.backdropBitmap != null))
    val shape = shapeOverride ?: RoundedCornerShape(snapshot.cornerRadiusDp.dp)
    val dialogWindow = (view.parent as? DialogWindowProvider)?.window

    DisposableEffect(dialogWindow) {
        if (dialogWindow != null) {
            CentralCommandBackdropBlur.registerHostWindow(dialogWindow)
        }
        onDispose {
            if (dialogWindow != null) {
                CentralCommandBackdropBlur.unregisterHostWindow(dialogWindow)
            }
        }
    }

    // Hardware window blur for modal dialog windows & standalone overlay decks
    SideEffect {
        val currentDialogWindow = (view.parent as? DialogWindowProvider)?.window
        currentDialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        BackdropBlurModeManager.applyWindowDimAmount(currentDialogWindow, snapshot.dimAmount)
        val effectiveWindowBlurPx = if (useLightweightHudBlur) {
            maxOf(snapshot.windowBlurRadiusPx, 72)
        } else {
            snapshot.windowBlurRadiusPx
        }
        BackdropBlurModeManager.applyWindowBlurFlags(
            view = view,
            blurRadiusPx = effectiveWindowBlurPx,
            mode = blurMode,
            window = currentDialogWindow,
            clampMinRadius = 20,
            clampMaxRadius = 150
        )
    }

    Box(modifier = modifier) {
        // Layer 1: Clipped fallback hardware blur strictly inside the card's rounded bounds
        // Hardware RenderNode clip keeps Layer 1 isolated from foreground scroll invalidations.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    this.shape = shape
                    this.clip = true
                }
        ) {
            ClippedFallbackBlurLayer(
                snapshot = snapshot,
                useLightweightHudBlur = useLightweightHudBlur
            )
        }

        // Layer 2: Unified 12-theme optical surface (draws outer glow/depth sheets + clipped glass finish)
        Box(
            modifier = Modifier
                .matchParentSize()
                .deckGlassSurface(
                    snapshot = snapshot,
                    isBlurAvailable = isBlurAvailable,
                    isFlyout = isFlyout,
                    shapeOverride = shape,
                    useLightweightHudBlur = useLightweightHudBlur
                )
        )

        // Layer 3: Foreground UI content clipped to the card's rounded shape
        Box(
            modifier = Modifier
                .graphicsLayer {
                    this.shape = shape
                    this.clip = true
                },
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
    val isMaterial3 = styleId == DeckStylePresets.MATERIAL_3.styleId
    val isAdaptive = styleId == DeckStylePresets.ADAPTIVE.styleId
    val useDynamic = usesSystemDynamicColor && (isMaterial3 || (isApi31Plus && isAdaptive))

    val safeTintArgb = if (tintArgb == 0x00FFFFFFL) 0xFFFFFFFFL else tintArgb
    val rawTint = if (useDynamic) colorScheme.primary else Color(safeTintArgb)
    val panel = if (isMaterial3) {
        colorScheme.surfaceContainerLow
    } else if (useDynamic) {
        colorScheme.surfaceContainerHigh
    } else {
        Color(panelColorArgb)
    }
    val rawBorder = if (isMaterial3) {
        colorScheme.outlineVariant
    } else if (useDynamic) {
        colorScheme.primary.copy(alpha = 0.80f)
    } else {
        Color(borderColorArgb)
    }

    val readableTint = if (panel.luminance() > 0.5f) {
        rawTint.copy(alpha = rawTint.alpha.coerceAtLeast(0.88f))
    } else {
        ensureReadableContrast(rawTint)
    }
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
    val blurMode by BackdropBlurModeManager.modeFlow.collectAsState()
    val cornerRadius = snapshot.cornerRadiusDp.dp
    val shape = RoundedCornerShape(cornerRadius)
    val activeLayoutDirection = LocalLayoutDirection.current
    val activeDensity = LocalDensity.current
    val activeTextStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
    val targetAndroidDir = if (activeLayoutDirection == LayoutDirection.Rtl) {
        android.view.View.LAYOUT_DIRECTION_RTL
    } else {
        android.view.View.LAYOUT_DIRECTION_LTR
    }

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
            CompositionLocalProvider(
                LocalLayoutDirection provides activeLayoutDirection,
                LocalDensity provides activeDensity,
                LocalTextStyle provides activeTextStyle
            ) {
                val view = LocalView.current
                SideEffect {
                    if (view.layoutDirection != targetAndroidDir) {
                        view.layoutDirection = targetAndroidDir
                    }
                    view.rootView?.let { root ->
                        if (root.layoutDirection != targetAndroidDir) {
                            root.layoutDirection = targetAndroidDir
                        }
                    }
                    val dialogWindow = (view.parent as? DialogWindowProvider)?.window
                    dialogWindow?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    BackdropBlurModeManager.applyWindowBlurFlags(
                        view = view,
                        blurRadiusPx = snapshot.windowBlurRadiusPx,
                        mode = blurMode,
                        window = dialogWindow,
                        clampMinRadius = 20,
                        clampMaxRadius = 120
                    )
                }
                val isJelly = snapshot.styleId == "jelly"
                val scaleAnim = remember(expanded) { Animatable(if (isJelly) 0.88f else 1.0f) }
                if (isJelly) {
                    LaunchedEffect(expanded) {
                        if (expanded) {
                            scaleAnim.animateTo(1.0f, spring(dampingRatio = 0.65f, stiffness = 450f))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(shape)
                        .graphicsLayer {
                            if (isJelly) {
                                scaleX = scaleAnim.value
                                scaleY = scaleAnim.value
                            }
                        }
                ) {
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
        }
    )
}

private fun computeTopEdgeFeatherAlpha(
    scrollableState: ScrollableState,
    rampPx: Float
): Float {
    if (!scrollableState.canScrollBackward) return 0f
    val distPx = when (scrollableState) {
        is ScrollState -> scrollableState.value.toFloat()
        is LazyListState -> if (scrollableState.firstVisibleItemIndex == 0) {
            scrollableState.firstVisibleItemScrollOffset.toFloat()
        } else {
            rampPx
        }
        is LazyGridState -> if (scrollableState.firstVisibleItemIndex == 0) {
            scrollableState.firstVisibleItemScrollOffset.toFloat()
        } else {
            rampPx
        }
        else -> rampPx
    }
    return (distPx / rampPx).coerceIn(0f, 1f)
}

private fun computeBottomEdgeFeatherAlpha(
    scrollableState: ScrollableState,
    rampPx: Float
): Float {
    if (!scrollableState.canScrollForward) return 0f
    val distPx = when (scrollableState) {
        is ScrollState -> {
            val max = scrollableState.maxValue
            if (max == Int.MAX_VALUE) rampPx else (max - scrollableState.value).toFloat()
        }
        is LazyListState -> {
            val info = scrollableState.layoutInfo
            val lastVis = info.visibleItemsInfo.lastOrNull()
            if (lastVis != null && info.totalItemsCount > 0 && lastVis.index == info.totalItemsCount - 1) {
                ((lastVis.offset + lastVis.size + info.afterContentPadding) - info.viewportEndOffset).toFloat()
            } else {
                rampPx
            }
        }
        is LazyGridState -> {
            val info = scrollableState.layoutInfo
            val vis = info.visibleItemsInfo
            val lastIdx = info.totalItemsCount - 1
            if (lastIdx >= 0 && vis.any { it.index == lastIdx }) {
                var maxEnd = Int.MIN_VALUE
                for (i in vis.indices) {
                    val end = vis[i].offset.y + vis[i].size.height
                    if (end > maxEnd) maxEnd = end
                }
                ((maxEnd + info.afterContentPadding) - info.viewportEndOffset).toFloat()
            } else {
                rampPx
            }
        }
        else -> rampPx
    }
    return (distPx / rampPx).coerceIn(0f, 1f)
}

/**
 * Universal vertical edge feathering modifier for Compose scrollable containers (`LazyColumn`,
 * `LazyVerticalGrid`, `ScrollState`). Uses a single container-level offscreen compositing layer
 * with cached top/bottom [BlendMode.DstOut] alpha gradient masks that ramp smoothly over the first
 * and last ~20dp of scroll travel.
 */
fun Modifier.verticalEdgeFade(
    scrollableState: ScrollableState,
    topFeatherDp: Dp = 20.dp,
    bottomFeatherDp: Dp = 22.dp
): Modifier = this
    .graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
    }
    .drawWithCache {
        val topPx = minOf(topFeatherDp.toPx(), size.height * 0.35f)
        val botPx = minOf(bottomFeatherDp.toPx(), size.height * 0.35f)
        val rampPx = 20.dp.toPx().coerceAtLeast(1f)
        val topBrush = Brush.verticalGradient(
            0.0f to Color.Black,
            0.52f to Color.Black.copy(alpha = 0.52f),
            1.0f to Color.Transparent,
            startY = 0f,
            endY = topPx.coerceAtLeast(1f)
        )
        val botStartY = (size.height - botPx).coerceAtLeast(0f)
        val bottomBrush = Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.48f to Color.Black.copy(alpha = 0.52f),
            1.0f to Color.Black,
            startY = botStartY,
            endY = size.height.coerceAtLeast(botStartY + 1f)
        )
        onDrawWithContent {
            drawContent()
            val topAlpha = computeTopEdgeFeatherAlpha(scrollableState, rampPx)
            if (topPx > 2f && topAlpha > 0.004f) {
                drawRect(
                    brush = topBrush,
                    topLeft = Offset.Zero,
                    size = Size(size.width, topPx),
                    alpha = topAlpha,
                    blendMode = BlendMode.DstOut
                )
            }
            val botAlpha = computeBottomEdgeFeatherAlpha(scrollableState, rampPx)
            if (botPx > 2f && botAlpha > 0.004f) {
                drawRect(
                    brush = bottomBrush,
                    topLeft = Offset(0f, botStartY),
                    size = Size(size.width, botPx),
                    alpha = botAlpha,
                    blendMode = BlendMode.DstOut
                )
            }
        }
    }

@Composable
fun Modifier.verticalScrollWithEdgeFade(
    state: ScrollState = rememberScrollState(),
    enabled: Boolean = true,
    topFeatherDp: Dp = 20.dp,
    bottomFeatherDp: Dp = 22.dp
): Modifier = this
    .verticalEdgeFade(
        scrollableState = state,
        topFeatherDp = topFeatherDp,
        bottomFeatherDp = bottomFeatherDp
    )
    .verticalScroll(state = state, enabled = enabled)
