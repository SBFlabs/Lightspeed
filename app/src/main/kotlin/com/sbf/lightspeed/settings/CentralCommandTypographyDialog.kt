package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.sbf.lightspeed.LightspeedCruiseOverlay
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 12-Theme optical glass Typography & Scale flyout panel.
 * Adheres to Central Command's sub-deck flyout sizing rule (80% width, max 350.dp),
 * morphs continuously as a single liquid drop from the FormatSize header button,
 * and loads device font previews purely on demand with a live calligraphy writing animation.
 */
@Composable
fun CentralCommandTypographyMenu(
    context: Context,
    expanded: Boolean,
    iconCenterInContainer: Offset,
    iconSizePx: Float,
    onDismiss: () -> Unit
) {
    val morphProgress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "typography_liquid_morph"
    )

    if (!expanded && morphProgress <= 0.02f) return

    BackHandler(enabled = expanded) { onDismiss() }

    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val deckTheme = LocalDeckTheme.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val isLandscape = screenWidthDp > configuration.screenHeightDp
    val prefs = remember { context.defaultPrefs() }
    val savedScale by LightspeedPreferences.centralCommandFontScaleFlow.collectAsState()
    val fontScale = savedScale ?: remember { LightspeedPreferences.getCentralCommandFontScale(context) }
    val scalePct = (fontScale * 100f).roundToInt().coerceIn(80, 135)
    var draftScalePct by remember(scalePct) { mutableIntStateOf(scalePct) }
    val savedCruiseScale by LightspeedPreferences.categoryCruiseFontScaleFlow.collectAsState()
    val cruiseFontScale = savedCruiseScale ?: remember { LightspeedPreferences.getCategoryCruiseFontScale(context) }
    val cruiseScalePct = (cruiseFontScale * 100f).roundToInt().coerceIn(80, 135)
    var draftCruiseScalePct by remember(cruiseScalePct) { mutableIntStateOf(cruiseScalePct) }
    val savedHangarScale by LightspeedPreferences.cockpitHangarFontScaleFlow.collectAsState()
    val hangarFontScale = savedHangarScale ?: remember { LightspeedPreferences.getCockpitHangarFontScale(context) }
    val hangarScalePct = (hangarFontScale * 100f).roundToInt().coerceIn(80, 135)
    var draftHangarScalePct by remember(hangarScalePct) { mutableIntStateOf(hangarScalePct) }
    val savedFamily by LightspeedPreferences.centralCommandFontFamilyFlow.collectAsState()
    val fontFamilyKey = savedFamily ?: remember { LightspeedPreferences.getCentralCommandFontFamily(context) }
    val savedApplyGlobally by LightspeedPreferences.applyFontGloballyFlow.collectAsState()
    val applyFontGlobally = savedApplyGlobally ?: remember { LightspeedPreferences.getApplyFontGlobally(context) }
    val selectedFontLabel = remember(fontFamilyKey) { DeviceFontScanner.getFontLabel(fontFamilyKey) }
    val selectedFontFamily = remember(fontFamilyKey) { DeviceFontScanner.resolveComposeFontFamily(fontFamilyKey) }

    var isFontListExpanded by remember(expanded) { mutableStateOf(false) }
    var loadedFonts by remember(expanded) { mutableStateOf<List<Triple<String, String, FontFamily?>>?>(null) }

    // Load installed fonts purely on demand on IO when dropdown expands; release when collapsed
    LaunchedEffect(isFontListExpanded, expanded) {
        if (expanded && isFontListExpanded) {
            loadedFonts = null
            val startMs = android.os.SystemClock.uptimeMillis()
            val scanned = DeviceFontScanner.loadCleanResolvedFontsOnDemand()
            val elapsed = android.os.SystemClock.uptimeMillis() - startMs
            if (elapsed < 380L) {
                delay(380L - elapsed)
            }
            loadedFonts = scanned
        } else {
            loadedFonts = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(deckTheme.cornerRadiusDp.dp))
    ) {
        val rootWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val rootHeightDp = with(density) { constraints.maxHeight.toDp() }
        val screenWidthPx = with(density) { screenWidthDp.dp.toPx() }
        val minPadPx = with(density) { 12.dp.toPx() }
        val maxFlyoutDp = if (isLandscape && screenWidthDp >= 600) 420.dp else 350.dp
        val maxFlyoutWidthPx = with(density) { maxFlyoutDp.toPx() }
        val targetPanelWidthPx = minOf(
            screenWidthPx * 0.80f,
            rootWidthPx - 2f * minPadPx,
            maxFlyoutWidthPx
        ).coerceAtLeast(with(density) { 120.dp.toPx() })
        val targetPanelWidthDp = with(density) { targetPanelWidthPx.toDp() }

        val fallbackBulbPx = with(density) { 40.dp.toPx() }
        val effectiveIconSizePx = if (iconSizePx > 0f) iconSizePx else fallbackBulbPx
        val bulbRadiusPx = effectiveIconSizePx / 2f
        val neckGapPx = with(density) { 14.dp.toPx() }
        val topPaddingForPanelPx = effectiveIconSizePx + neckGapPx
        val topPaddingForPanelDp = with(density) { topPaddingForPanelPx.toDp() }
        val cornerRadiusPx = with(density) { deckTheme.cornerRadiusDp.dp.toPx() }
        val flareHalfPx = with(density) { 38.dp.toPx() }

        val anchorCenter = if (iconCenterInContainer != Offset.Zero) {
            iconCenterInContainer
        } else {
            Offset(rootWidthPx * 0.72f, with(density) { 36.dp.toPx() })
        }

        val maxPanelLeftPx = (rootWidthPx - targetPanelWidthPx - minPadPx).coerceAtLeast(minPadPx)
        val centeredLeftPx = (rootWidthPx - targetPanelWidthPx) / 2f
        val needAnchorShift =
            anchorCenter.x + bulbRadiusPx + minPadPx > centeredLeftPx + targetPanelWidthPx ||
                anchorCenter.x - bulbRadiusPx - minPadPx < centeredLeftPx
        val panelLeftInRootPx = (if (needAnchorShift) {
            anchorCenter.x - targetPanelWidthPx * 0.72f
        } else {
            centeredLeftPx
        }).coerceIn(minPadPx, maxPanelLeftPx)

        val boxTopOffsetPx = (anchorCenter.y - bulbRadiusPx).coerceAtLeast(0f).roundToInt()
        val localBulbCenterX = (anchorCenter.x - panelLeftInRootPx)
            .coerceIn(
                bulbRadiusPx + with(density) { 6.dp.toPx() },
                targetPanelWidthPx - bulbRadiusPx - with(density) { 6.dp.toPx() }
            )

        // Dim scrim inside Central Command bounds
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.scrim.copy(alpha = 0.28f * morphProgress.coerceIn(0f, 1f)))
                .clickable(
                    enabled = expanded,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )

        val p = morphProgress.coerceIn(0f, 1f)
        val liquidShape = remember(p, localBulbCenterX, bulbRadiusPx, topPaddingForPanelPx, cornerRadiusPx, flareHalfPx) {
            GenericShape { size, _ ->
                addLiquidDropPanelPath(
                    path = this,
                    w = size.width,
                    h = size.height,
                    cx = localBulbCenterX.coerceIn(bulbRadiusPx + 4f, (size.width - bulbRadiusPx - 4f).coerceAtLeast(bulbRadiusPx + 4f)),
                    bulbR = bulbRadiusPx,
                    panelTopY = topPaddingForPanelPx,
                    cornerR = cornerRadiusPx,
                    flareHalfW = flareHalfPx,
                    progress = p
                )
            }
        }

        val contentAlpha = ((p - 0.28f) / 0.52f).coerceIn(0f, 1f)
        val iconOffsetXDp = with(density) { (localBulbCenterX - bulbRadiusPx).toDp() }
        val bulbSizeDp = with(density) { effectiveIconSizePx.toDp() }

        val boxTopDp = with(density) { boxTopOffsetPx.toDp() }
        val maxPanelHeightDp = (rootHeightDp - boxTopDp - 10.dp).coerceAtLeast(180.dp)

        Box(
            modifier = Modifier
                .offset { IntOffset(panelLeftInRootPx.roundToInt(), boxTopOffsetPx) }
                .width(targetPanelWidthDp)
                .widthIn(max = maxFlyoutDp)
                .heightIn(max = maxPanelHeightDp)
        ) {
            DeckGlassCardSurface(
                modifier = Modifier.fillMaxWidth(),
                isFlyout = true,
                shapeOverride = liquidShape
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Origin droplet hub hugging the exact FormatSize button
                    Box(
                        modifier = Modifier
                            .offset(x = iconOffsetXDp, y = 0.dp)
                            .size(bulbSizeDp)
                            .clip(CircleShape)
                            .clickable(enabled = expanded, onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FormatSize,
                            contentDescription = "Close Typography & Scale",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expanding panel content beneath the fused liquid neck (scrollable on landscape screens)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = topPaddingForPanelDp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                            .verticalScrollWithEdgeFade()
                            .padding(
                                horizontal = 14.dp,
                                vertical = if (isLandscape) 10.dp else 14.dp
                            )
                            .graphicsLayer { alpha = contentAlpha },
                        verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lsString(R.string.typo_title),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.textPrimary
                                )
                                Text(
                                    text = lsString(R.string.typo_hint),
                                    fontSize = 10.sp,
                                    color = lsColors.textSecondary
                                )
                            }
                        }

                        PrefDottedSliderRow(
                            context = context,
                            prefs = prefs,
                            keyResName = LightspeedPreferences.KEY_CENTRAL_COMMAND_FONT_SCALE,
                            titleResName = "",
                            defaultTitle = if (applyFontGlobally) lsString(R.string.typo_all) else lsString(R.string.typo_cc),
                            minVal = 80,
                            maxVal = 135,
                            step = 5,
                            defaultVal = 100,
                            valueSuffix = "%",
                            externalValue = draftScalePct,
                            onValueChanged = { pct ->
                                draftScalePct = pct
                            },
                            onValueChangeFinished = { pct ->
                                LightspeedPreferences.setCentralCommandFontScale(context, pct / 100f)
                                LightspeedCruiseOverlay.instance?.invalidate()
                            }
                        )

                        AnimatedVisibility(
                            visible = !applyFontGlobally,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(if (isLandscape) 6.dp else 8.dp)
                            ) {
                                PrefDottedSliderRow(
                                    context = context,
                                    prefs = prefs,
                                    keyResName = LightspeedPreferences.KEY_CATEGORY_CRUISE_FONT_SCALE,
                                    titleResName = "",
                                    defaultTitle = lsString(R.string.typo_cruise),
                                    minVal = 80,
                                    maxVal = 135,
                                    step = 5,
                                    defaultVal = 100,
                                    valueSuffix = "%",
                                    externalValue = draftCruiseScalePct,
                                    onValueChanged = { pct ->
                                        draftCruiseScalePct = pct
                                    },
                                    onValueChangeFinished = { pct ->
                                        LightspeedPreferences.setCategoryCruiseFontScale(context, pct / 100f)
                                        LightspeedCruiseOverlay.instance?.invalidate()
                                    }
                                )
                                PrefDottedSliderRow(
                                    context = context,
                                    prefs = prefs,
                                    keyResName = LightspeedPreferences.KEY_COCKPIT_HANGAR_FONT_SCALE,
                                    titleResName = "",
                                    defaultTitle = lsString(R.string.typo_cockpit),
                                    minVal = 80,
                                    maxVal = 135,
                                    step = 5,
                                    defaultVal = 100,
                                    valueSuffix = "%",
                                    externalValue = draftHangarScalePct,
                                    onValueChanged = { pct ->
                                        draftHangarScalePct = pct
                                    },
                                    onValueChangeFinished = { pct ->
                                        LightspeedPreferences.setCockpitHangarFontScale(context, pct / 100f)
                                        LightspeedCruiseOverlay.instance?.invalidate()
                                    }
                                )
                            }
                        }

                        // Instant-open on-demand Device Font Family dropdown
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(lsColors.textPrimary.copy(alpha = 0.05f))
                                .border(0.8.dp, lsColors.borderSubtle, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isFontListExpanded = !isFontListExpanded }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = lsString(R.string.typo_font_family),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.7.sp,
                                        color = colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = selectedFontLabel,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = selectedFontFamily,
                                        color = lsColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = if (isFontListExpanded) {
                                        Icons.Default.KeyboardArrowUp
                                    } else {
                                        Icons.Default.KeyboardArrowDown
                                    },
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(
                                visible = isFontListExpanded,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                val currentFonts = loadedFonts
                                if (currentFonts == null) {
                                    FontWritingLoadingAnimation(
                                        accentColor = colorScheme.primary,
                                        textColor = lsColors.textPrimary,
                                        subColor = lsColors.textSecondary
                                    )
                                } else {
                                    val availableListDp = rootHeightDp - boxTopDp - topPaddingForPanelDp - 195.dp
                                    val fontListMaxHeightDp = availableListDp.coerceIn(
                                        110.dp,
                                        if (isLandscape) 260.dp else 380.dp
                                    )
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = fontListMaxHeightDp)
                                            .verticalScrollWithEdgeFade()
                                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        currentFonts.forEach { (key, label, itemFont) ->
                                            val isSelected = key == fontFamilyKey
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(
                                                        if (isSelected) colorScheme.primary.copy(alpha = 0.18f)
                                                        else Color.Transparent
                                                    )
                                                    .clickable {
                                                        LightspeedPreferences.setCentralCommandFontFamily(context, key)
                                                        if (applyFontGlobally) {
                                                            LightspeedCruiseOverlay.instance?.invalidate()
                                                        }
                                                        isFontListExpanded = false
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 12.sp,
                                                    fontFamily = itemFont,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) colorScheme.primary else lsColors.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = colorScheme.primary,
                                                        modifier = Modifier.size(15.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Toggle: extend Central Command font family to Cockpit, Category Cruise, menus & HUD widgets
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(lsColors.textPrimary.copy(alpha = 0.05f))
                                .border(0.8.dp, lsColors.borderSubtle, RoundedCornerShape(12.dp))
                                .clickable {
                                    val next = !applyFontGlobally
                                    LightspeedPreferences.setApplyFontGlobally(context, next)
                                    LightspeedCruiseOverlay.instance?.invalidate()
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = lsString(R.string.typo_apply_overlays),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = lsColors.textPrimary
                                )
                                Text(
                                    text = lsString(R.string.typo_apply_overlays_sub),
                                    fontSize = 9.5.sp,
                                    color = lsColors.textSecondary
                                )
                            }
                            DeckSwitch(
                                checked = applyFontGlobally,
                                onCheckedChange = { checked ->
                                    LightspeedPreferences.setApplyFontGlobally(context, checked)
                                    LightspeedCruiseOverlay.instance?.invalidate()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Self-writing calligraphy / typewriter loading indicator for on-demand device font loading.
 * Cycles sample phrases across Sans, Serif, Monospace, and Cursive styles with an animated pencil tip.
 */
@Composable
fun FontWritingLoadingAnimation(
    accentColor: Color,
    textColor: Color,
    subColor: Color
) {
    val specimens = remember {
        listOf(
            "Lightspeed · Sans" to FontFamily.SansSerif,
            "Editorial · Serif" to FontFamily.Serif,
            "Terminal · Mono" to FontFamily.Monospace,
            "Calligraphy · Script" to FontFamily.Cursive
        )
    }
    var specimenIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(850L)
            specimenIndex = (specimenIndex + 1) % specimens.size
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "font_writing")
    val strokeProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "font_write_progress"
    )
    val nibPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "font_nib_bob"
    )

    val (phrase, family) = specimens[specimenIndex]
    val charCount = ((strokeProgress * (phrase.length + 2)).toInt()).coerceIn(1, phrase.length)
    val visibleText = phrase.take(charCount)
    val traceFraction = (strokeProgress * 1.1f).coerceIn(0.08f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = visibleText,
                fontSize = 14.sp,
                fontFamily = family,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier
                    .size(15.dp)
                    .graphicsLayer {
                        rotationZ = -12f + 10f * sin(nibPhase)
                        translationY = -2f * kotlin.math.abs(sin(nibPhase))
                    }
            )
        }

        // Animated ink underline trace
        Box(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accentColor.copy(alpha = 0.16f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(traceFraction)
                    .height(2.dp)
                    .background(accentColor.copy(alpha = 0.85f))
            )
        }

        Text(
            text = lsString(R.string.typo_reading_fonts),
            fontSize = 10.sp,
            color = subColor
        )
    }
}

/**
 * Builds one continuous closed path uniting the FormatSize circular hub (`center = (cx, bulbR)`)
 * with the centered card body (`y in [panelTopY..h]`) via G1 tangent-continuous cubic curves.
 * At `progress = 0f`, collapses cleanly into the exact `bulbR` circle at `(cx, bulbR)`.
 */
private fun addLiquidDropPanelPath(
    path: Path,
    w: Float,
    h: Float,
    cx: Float,
    bulbR: Float,
    panelTopY: Float,
    cornerR: Float,
    flareHalfW: Float,
    progress: Float
) {
    val cy = bulbR
    val p = progress.coerceIn(0f, 1f)
    val dropRaw = (p / 0.42f).coerceIn(0f, 1f)
    val dropW = dropRaw * dropRaw * (3f - 2f * dropRaw)
    val spreadRaw = ((p - 0.10f) / 0.90f).coerceIn(0f, 1f)
    val spreadW = spreadRaw * spreadRaw * (3f - 2f * spreadRaw)
    val bottomRaw = p
    val bottomW = 1f - (1f - bottomRaw) * (1f - bottomRaw) * (1f - bottomRaw)

    val topFlatY = lerp(cy, panelTopY.coerceAtMost((h - 4f).coerceAtLeast(cy + 2f)), dropW)
    val bottomY = lerp(cy + bulbR, h, bottomW).coerceAtLeast(topFlatY + 4f)
    val leftX = lerp(cx - bulbR, 0f, spreadW)
    val rightX = lerp(cx + bulbR, w, spreadW)

    val maxPossibleCr = min((rightX - leftX) * 0.48f, (bottomY - topFlatY) * 0.48f).coerceAtLeast(0f)
    val crTop = lerp(0f, cornerR, spreadW).coerceAtMost(maxPossibleCr)
    val crBottom = lerp(bulbR, cornerR, spreadW).coerceAtMost(maxPossibleCr)

    val maxNeckJoinRight = (rightX - crTop).coerceAtLeast(cx)
    val minNeckJoinLeft = (leftX + crTop).coerceAtMost(cx)
    val flareOut = lerp(bulbR, flareHalfW, spreadW)
    val rightJoinX = (cx + flareOut).coerceIn(cx, maxNeckJoinRight)
    val leftJoinX = (cx - flareOut).coerceIn(minNeckJoinLeft, cx)

    val waistPinch = bulbR * (1f - 0.22f * dropW)
    val neckMidY = cy + (topFlatY - cy) * 0.48f

    // 1. Top semicircle over the FormatSize icon (180 deg -> 0 deg clockwise)
    path.arcTo(
        rect = Rect(cx - bulbR, cy - bulbR, cx + bulbR, cy + bulbR),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = true
    )

    // 2. Right viscous metaball neck from bulb equator down to topFlatY
    if (topFlatY > cy + 0.5f || rightJoinX > cx + bulbR + 0.5f) {
        path.cubicTo(
            cx + waistPinch, neckMidY,
            lerp(rightJoinX, cx + waistPinch, 0.35f), topFlatY,
            rightJoinX, topFlatY
        )
    }

    // 3. Top-right edge & top-right corner
    path.lineTo((rightX - crTop).coerceAtLeast(rightJoinX), topFlatY)
    if (crTop > 0.5f) {
        path.arcTo(
            rect = Rect(rightX - 2f * crTop, topFlatY, rightX, topFlatY + 2f * crTop),
            startAngleDegrees = 270f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }

    // 4. Right edge & bottom-right corner
    path.lineTo(rightX, (bottomY - crBottom).coerceAtLeast(topFlatY + crTop))
    if (crBottom > 0.5f) {
        path.arcTo(
            rect = Rect(rightX - 2f * crBottom, bottomY - 2f * crBottom, rightX, bottomY),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }

    // 5. Bottom edge & bottom-left corner
    path.lineTo((leftX + crBottom).coerceAtMost(rightX - crBottom), bottomY)
    if (crBottom > 0.5f) {
        path.arcTo(
            rect = Rect(leftX, bottomY - 2f * crBottom, leftX + 2f * crBottom, bottomY),
            startAngleDegrees = 90f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }

    // 6. Left edge & top-left corner
    path.lineTo(leftX, (topFlatY + crTop).coerceAtMost(bottomY - crBottom))
    if (crTop > 0.5f) {
        path.arcTo(
            rect = Rect(leftX, topFlatY, leftX + 2f * crTop, topFlatY + 2f * crTop),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 90f,
            forceMoveTo = false
        )
    }

    // 7. Top-left edge back to neck & left viscous metaball neck up to bulb equator
    path.lineTo(leftJoinX.coerceAtLeast(leftX + crTop), topFlatY)
    if (topFlatY > cy + 0.5f || leftJoinX < cx - bulbR - 0.5f) {
        path.cubicTo(
            lerp(leftJoinX, cx - waistPinch, 0.35f), topFlatY,
            cx - waistPinch, neckMidY,
            cx - bulbR, cy
        )
    }
    path.close()
}
