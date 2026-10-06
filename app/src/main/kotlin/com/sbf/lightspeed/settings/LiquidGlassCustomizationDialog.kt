package com.sbf.lightspeed.settings

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedDeckStyleManager.LiquidGlassConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Liquid Glass & Progressive Blur Calibration Dialog.
 * Opens on long-press of the "Liquid Glass" theme chip in Central Command.
 * Offers live interactive parameter tuning for blur depth, opacity, frost diffusion,
 * specular glare, and prismatic Material edge dispersion with immediate preview.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidGlassCustomizationDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val scrollState = rememberScrollState()

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    var showCustomHexDialog by remember { mutableStateOf(false) }
    var pulseHighlight by remember { mutableStateOf(false) }

    LaunchedEffect(pulseHighlight) {
        if (pulseHighlight) {
            kotlinx.coroutines.delay(1800)
            pulseHighlight = false
        }
    }

    // Live reactive config
    val configFlow by LightspeedPreferences.liquidGlassConfigFlow.collectAsState()
    var currentConfig by remember {
        mutableStateOf(configFlow ?: LightspeedPreferences.getLiquidGlassConfig(context))
    }

    // Keep currentConfig synchronized if updated externally
    LaunchedEffect(configFlow) {
        configFlow?.let { currentConfig = it }
    }

    val updateConfig: (LiquidGlassConfig) -> Unit = { newConfig ->
        currentConfig = newConfig
        LightspeedPreferences.setLiquidGlassConfig(context, newConfig)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window

        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dialogWindow?.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = dialogWindow?.attributes
                if (lp != null) {
                    lp.blurBehindRadius = currentConfig.blurRadius.coerceIn(0, 150)
                    dialogWindow.attributes = lp
                }
            }
            dialogWindow?.setDimAmount(0.25f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.98f)
                    .fillMaxHeight(0.92f)
                    .offset { IntOffset(0, dragOffsetY.value.roundToInt()) }
                    .clip(RoundedCornerShape(24.dp))
                    .background(colorScheme.surface.copy(alpha = 0.85f))
                    .border(
                        1.2.dp,
                        Brush.verticalGradient(
                            listOf(
                                colorScheme.outlineVariant.copy(alpha = 0.7f),
                                colorScheme.outlineVariant.copy(alpha = 0.2f)
                            )
                        ),
                        RoundedCornerShape(24.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.85f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // ── Header / Drag Handle ──────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragStart = { hasCrossedThreshold = false },
                                    onDragEnd = {
                                        if (dragOffsetY.value >= thresholdPx) {
                                            onDismiss()
                                        } else {
                                            coroutineScope.launch {
                                                dragOffsetY.animateTo(
                                                    0f,
                                                    animationSpec = spring(
                                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            dragOffsetY.animateTo(0f)
                                        }
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        val current = dragOffsetY.value + dragAmount
                                        val newOffset = if (current <= 0f) 0f
                                        else if (current <= thresholdPx) current
                                        else thresholdPx + (current - thresholdPx) * 0.35f

                                        if (!hasCrossedThreshold && newOffset >= thresholdPx) {
                                            hasCrossedThreshold = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            LightspeedHapticEngine.tick(context)
                                        } else if (hasCrossedThreshold && newOffset < thresholdPx) {
                                            hasCrossedThreshold = false
                                        }

                                        coroutineScope.launch {
                                            dragOffsetY.snapTo(newOffset)
                                        }
                                    }
                                )
                            }
                    ) {
                        // Drag Handle Pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 4.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.35f))
                            )
                        }

                        // Title Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "GLASS OPTICAL CALIBRATION",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Material glassmorphism, progressive blur & AGSL optics",
                                        fontSize = 10.sp,
                                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            IconButton(
                                onClick = { onDismiss() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 8.dp))

                    // ── FIXED STICKY LIVE PREVIEW HEADER ────────────────────────
                    // Pinned permanently at top so it remains visible while scrolling sliders
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        colorScheme.primaryContainer.copy(alpha = 0.45f),
                                        colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                        colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                                    )
                                )
                            )
                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    ) {
                        // High-contrast background elements underneath glass
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "⚡ GLASS OPTICS",
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary
                                )
                                Text(
                                    if (LiquidGlassAgsl.isAvailable && currentConfig.agslEnabled && currentConfig.agslIntensity > 0.01f) "AGSL ON" else "AGSL OFF",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.tertiary
                                )
                            }
                            val activeTint = if (currentConfig.useCustomColor) Color(currentConfig.customColor) else colorScheme.primary
                            val isM3Mode = !currentConfig.useCustomColor

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isM3Mode) {
                                                Brush.sweepGradient(
                                                    listOf(
                                                        colorScheme.primary,
                                                        colorScheme.secondary,
                                                        colorScheme.tertiary,
                                                        colorScheme.primary
                                                    )
                                                )
                                            } else {
                                                Brush.radialGradient(
                                                    listOf(
                                                        activeTint,
                                                        activeTint.copy(alpha = 0.80f)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = 1.6.dp,
                                            color = Color.White.copy(alpha = 0.85f),
                                            shape = CircleShape
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                LightspeedHapticEngine.tick(context)
                                                val newUseCustom = !currentConfig.useCustomColor
                                                updateConfig(currentConfig.copy(useCustomColor = newUseCustom))
                                            },
                                            onLongClick = {
                                                LightspeedHapticEngine.heavyClick(context)
                                                if (!currentConfig.useCustomColor) {
                                                    updateConfig(currentConfig.copy(useCustomColor = true))
                                                }
                                                pulseHighlight = true
                                                coroutineScope.launch {
                                                    scrollState.animateScrollTo(280)
                                                }
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isM3Mode) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = "Material 3 Dynamic Mode (Tap to toggle, hold to customize)",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    } else {
                                        val isLight = (activeTint.red * 0.299f + activeTint.green * 0.587f + activeTint.blue * 0.114f) > 0.6f
                                        Icon(
                                            imageVector = Icons.Default.Palette,
                                            contentDescription = "Custom Tint Mode (Tap to toggle, hold to customize)",
                                            tint = if (isLight) Color.Black else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            LightspeedHapticEngine.tick(context)
                                            val newUseCustom = !currentConfig.useCustomColor
                                            updateConfig(currentConfig.copy(useCustomColor = newUseCustom))
                                        }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            if (isM3Mode) "Dynamic Material 3 Optics" else "Custom Chromatic Tint",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = (if (isM3Mode) colorScheme.primary else activeTint).copy(alpha = 0.25f),
                                            border = BorderStroke(
                                                0.6.dp,
                                                (if (isM3Mode) colorScheme.primary else activeTint).copy(alpha = 0.6f)
                                            )
                                        ) {
                                            Text(
                                                if (isM3Mode) "M3 MONET" else "#%06X".format(currentConfig.customColor.toInt() and 0xFFFFFF),
                                                fontSize = 8.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isM3Mode) colorScheme.primary else activeTint,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        if (isM3Mode) "Tap circle to toggle custom • Hold to customize"
                                        else "Tap circle to revert to M3 • Hold to pick color",
                                        fontSize = 9.sp,
                                        color = Color.White.copy(alpha = 0.85f)
                                    )
                                }

                                Switch(
                                    checked = isM3Mode,
                                    onCheckedChange = { checked ->
                                        LightspeedHapticEngine.tick(context)
                                        updateConfig(currentConfig.copy(useCustomColor = !checked))
                                    },
                                    modifier = Modifier.scale(0.85f),
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = colorScheme.primary,
                                        uncheckedThumbColor = Color.White,
                                        uncheckedTrackColor = activeTint
                                    )
                                )
                            }
                        }

                        // Active Liquid Glass surface overlaid on top
                        LiquidGlassPanel(
                            cornerRadius = 18.dp,
                            config = currentConfig,
                            colorScheme = colorScheme,
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))

                    // ── Scrollable Body ────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Section 2: Preset Quick-Pill Selectors ───────────
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "OPTICAL PRESETS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    "Default" to LiquidGlassConfig.PRESET_DEFAULT,
                                    "Crystal" to LiquidGlassConfig.PRESET_CRYSTAL,
                                    "Frosted" to LiquidGlassConfig.PRESET_FROSTED,
                                    "Obsidian" to LiquidGlassConfig.PRESET_OBSIDIAN
                                ).forEach { (label, preset) ->
                                    val isMatch = currentConfig.blurRadius == preset.blurRadius &&
                                            (kotlin.math.abs(currentConfig.opacity - preset.opacity) < 0.05f) &&
                                            (kotlin.math.abs(currentConfig.frostNoise - preset.frostNoise) < 0.02f)

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                LightspeedHapticEngine.tick(context)
                                                updateConfig(preset)
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isMatch) colorScheme.primary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isMatch) colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.12f)
                                        )
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isMatch) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isMatch) colorScheme.primary else Color.White.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // ── Section 2.5: Chromatic Engine & Optical Tint ───────
                        val activeTint = if (currentConfig.useCustomColor) Color(currentConfig.customColor) else colorScheme.primary
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (pulseHighlight) activeTint.copy(alpha = 0.14f)
                                    else Color.White.copy(alpha = 0.03f)
                                )
                                .border(
                                    1.dp,
                                    if (pulseHighlight) activeTint.copy(alpha = 0.75f)
                                    else Color.White.copy(alpha = 0.08f),
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "CHROMATIC ENGINE & OPTICAL TINT",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentConfig.useCustomColor) activeTint else colorScheme.primary,
                                    letterSpacing = 0.8.sp
                                )
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = (if (currentConfig.useCustomColor) activeTint else colorScheme.primary).copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        if (currentConfig.useCustomColor) "CUSTOM TINT" else "DYNAMIC M3",
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentConfig.useCustomColor) activeTint else colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Two-Pill Segmented Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val isM3Selected = !currentConfig.useCustomColor
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            LightspeedHapticEngine.tick(context)
                                            updateConfig(currentConfig.copy(useCustomColor = false))
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isM3Selected) colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isM3Selected) colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = if (isM3Selected) colorScheme.primary else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            "Dynamic Material 3",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isM3Selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isM3Selected) colorScheme.primary else Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }

                                val isCustomSelected = currentConfig.useCustomColor
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            LightspeedHapticEngine.tick(context)
                                            updateConfig(currentConfig.copy(useCustomColor = true))
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isCustomSelected) activeTint.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isCustomSelected) activeTint.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.10f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(Icons.Default.Palette, contentDescription = null, tint = if (isCustomSelected) activeTint else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            "Custom Palette",
                                            fontSize = 10.5.sp,
                                            fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCustomSelected) activeTint else Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            }

                            // Tint Infusion Depth Slider
                            CalibrationSliderRow(
                                title = "Tint Infusion Depth",
                                subtitle = "Volume of color infused into glass substrate & apex glow",
                                displayValue = "${(currentConfig.tintIntensity * 100).roundToInt()}%",
                                value = currentConfig.tintIntensity,
                                valueRange = 0.05f..1.00f,
                                steps = 18,
                                onValueChange = { newTint ->
                                    updateConfig(currentConfig.copy(tintIntensity = newTint))
                                }
                            )

                            // Palette Presets Swatches
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "CUSTOM PALETTE PRESETS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.LightGray.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        "#%06X".format(currentConfig.customColor.toInt() and 0xFFFFFF),
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (currentConfig.useCustomColor) activeTint else Color.Gray
                                    )
                                }

                                val palettePresets = listOf(
                                    0xFF00E5FFL to "Cyan",
                                    0xFF00E676L to "Emerald",
                                    0xFFFFD600L to "Amber",
                                    0xFFFF6D00L to "Orange",
                                    0xFFFF1744L to "Crimson",
                                    0xFFFF007FL to "Pink",
                                    0xFFD500F9L to "Purple",
                                    0xFF607D8BL to "Obsidian",
                                    0xFFFFFFFFL to "White"
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    palettePresets.forEach { (colorVal, _) ->
                                        val col = Color(colorVal)
                                        val isSelected = currentConfig.useCustomColor && (currentConfig.customColor == colorVal)
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(col)
                                                .border(
                                                    width = if (isSelected) 2.2.dp else 0.8.dp,
                                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    LightspeedHapticEngine.tick(context)
                                                    updateConfig(currentConfig.copy(useCustomColor = true, customColor = colorVal))
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isSelected) {
                                                val isLight = (col.red * 0.299f + col.green * 0.587f + col.blue * 0.114f) > 0.6f
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = if (isLight) Color.Black else Color.White,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Custom Hex Button
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .border(
                                                width = 1.dp,
                                                color = Color.White.copy(alpha = 0.35f),
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                LightspeedHapticEngine.tick(context)
                                                showCustomHexDialog = true
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Custom Hex Code",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // ── Slider 1: Backdrop Blur Depth (Window Blur) ─────
                        CalibrationSliderRow(
                            title = "Window Backdrop Blur",
                            subtitle = "System blur radius behind the command deck",
                            displayValue = "${currentConfig.blurRadius} px",
                            value = currentConfig.blurRadius.toFloat(),
                            valueRange = 0f..120f,
                            steps = 23, // 5px increments
                            onValueChange = { newBlur ->
                                updateConfig(currentConfig.copy(blurRadius = newBlur.roundToInt()))
                            }
                        )

                        // ── Slider 2: Glass Substrate Opacity ───────────────
                        CalibrationSliderRow(
                            title = "Glass Body Opacity",
                            subtitle = "Substrate density and optical transmission",
                            displayValue = "${(currentConfig.opacity * 100).roundToInt()}%",
                            value = currentConfig.opacity,
                            valueRange = 0.20f..0.98f,
                            steps = 15,
                            onValueChange = { newOp ->
                                updateConfig(currentConfig.copy(opacity = newOp))
                            }
                        )

                        // ── Slider 3: Surface Frost Diffusion (Noise) ───────
                        CalibrationSliderRow(
                            title = "Frost Diffusion (Micro-Grain)",
                            subtitle = "Matte surface light scattering and etched texture",
                            displayValue = "${(currentConfig.frostNoise * 100).roundToInt()}%",
                            value = currentConfig.frostNoise,
                            valueRange = 0.00f..0.20f,
                            steps = 19,
                            onValueChange = { newFrost ->
                                updateConfig(currentConfig.copy(frostNoise = newFrost))
                            }
                        )

                        // ── Slider 4: Specular Glare Intensity ──────────────
                        CalibrationSliderRow(
                            title = "Specular Glare & Apex Crest",
                            subtitle = "Overhead reflection and bright meniscus highlight",
                            displayValue = "${(currentConfig.glareIntensity * 100).roundToInt()}%",
                            value = currentConfig.glareIntensity,
                            valueRange = 0.00f..1.00f,
                            steps = 19,
                            onValueChange = { newGlare ->
                                updateConfig(currentConfig.copy(glareIntensity = newGlare))
                            }
                        )

                        // ── Slider 5: Prismatic Edge Rim Dispersion ─────────
                        CalibrationSliderRow(
                            title = "Prismatic Material Edge Rim",
                            subtitle = "Fresnel border dispersion using dynamic Material palette",
                            displayValue = "${(currentConfig.rimIntensity * 100).roundToInt()}%",
                            value = currentConfig.rimIntensity,
                            valueRange = 0.00f..1.00f,
                            steps = 19,
                            onValueChange = { newRim ->
                                updateConfig(currentConfig.copy(rimIntensity = newRim))
                            }
                        )

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // ── Toggle 1: Progressive Depth Ramp ────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(progressiveDepth = !currentConfig.progressiveDepth))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "Progressive Depth Gradient",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Ramps from clear meniscus top to frosted resting base",
                                    fontSize = 10.5.sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Switch(
                                checked = currentConfig.progressiveDepth,
                                onCheckedChange = { checked ->
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(progressiveDepth = checked))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colorScheme.primary
                                )
                            )
                        }

                        // ── Toggle 2: Light Shimmer (Canvas highlight drift + rim flares) ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(causticShimmer = !currentConfig.causticShimmer))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "Light Shimmer",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Drifting highlight and rim flares (works on every Android version)",
                                    fontSize = 10.5.sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Switch(
                                checked = currentConfig.causticShimmer,
                                onCheckedChange = { checked ->
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(causticShimmer = checked))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        // ── AGSL Caustic Light (real GPU shader) ────────────
                        val agslAvailable = LiquidGlassAgsl.isAvailable
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = agslAvailable) {
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(agslEnabled = !currentConfig.agslEnabled))
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    "AGSL Caustic Light (GPU)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    if (agslAvailable)
                                        "Animated light-web caustics rendered by a GPU shader behind the glass"
                                    else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU)
                                        "Unavailable: needs Android 13+ (API 33)"
                                    else
                                        "Unavailable: this GPU driver rejected the shader",
                                    fontSize = 10.5.sp,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                )
                            }
                            Switch(
                                checked = currentConfig.agslEnabled && agslAvailable,
                                enabled = agslAvailable,
                                onCheckedChange = { checked ->
                                    LightspeedHapticEngine.tick(context)
                                    updateConfig(currentConfig.copy(agslEnabled = checked))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = colorScheme.primary
                                )
                            )
                        }

                        if (agslAvailable && currentConfig.agslEnabled) {
                            CalibrationSliderRow(
                                title = "AGSL Intensity",
                                subtitle = "Brightness and visibility of the caustic light web",
                                displayValue = "${(currentConfig.agslIntensity * 100).roundToInt()}%",
                                value = currentConfig.agslIntensity,
                                valueRange = 0.05f..1.00f,
                                steps = 18,
                                onValueChange = { v ->
                                    updateConfig(currentConfig.copy(agslIntensity = v))
                                }
                            )
                            CalibrationSliderRow(
                                title = "AGSL Speed",
                                subtitle = "How fast the caustic light flows",
                                displayValue = "${"%.2f".format(currentConfig.agslSpeed)}x",
                                value = currentConfig.agslSpeed,
                                valueRange = 0.25f..3.00f,
                                steps = 10,
                                onValueChange = { v ->
                                    updateConfig(currentConfig.copy(agslSpeed = v))
                                }
                            )
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))

                    // ── Bottom Action Row ──────────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                LightspeedHapticEngine.tick(context)
                                updateConfig(LiquidGlassConfig.PRESET_DEFAULT)
                            }
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Defaults", fontSize = 11.5.sp)
                        }

                        Button(
                            onClick = {
                                LightspeedHapticEngine.click(context)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (showCustomHexDialog) {
            var hexInput by remember {
                mutableStateOf("%06X".format(currentConfig.customColor.toInt() and 0xFFFFFF))
            }
            var parseError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showCustomHexDialog = false },
                title = {
                    Text("CUSTOM CHROMATIC TINT", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Enter a 6-digit hex color code for glass refraction & caustics:",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { raw ->
                                val cleaned = raw.trimStart('#').take(6).uppercase()
                                hexInput = cleaned
                                parseError = false
                            },
                            prefix = { Text("#", fontWeight = FontWeight.Bold) },
                            singleLine = true,
                            isError = parseError,
                            supportingText = if (parseError) {
                                { Text("Invalid hex color (use 6 digits)", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val parsedColor = try {
                            if (hexInput.length == 6) {
                                Color(android.graphics.Color.parseColor("#$hexInput"))
                            } else null
                        } catch (_: Exception) { null }

                        if (parsedColor != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                                )
                                Text("Preview Swatch", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            try {
                                val parsed = android.graphics.Color.parseColor("#$hexInput")
                                val longColor = (parsed.toLong() and 0xFFFFFFFFL)
                                LightspeedHapticEngine.click(context)
                                updateConfig(currentConfig.copy(useCustomColor = true, customColor = longColor))
                                showCustomHexDialog = false
                            } catch (_: Exception) {
                                parseError = true
                                LightspeedHapticEngine.triggerWarning(context)
                            }
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomHexDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun CalibrationSliderRow(
    title: String,
    subtitle: String,
    displayValue: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Text(
                    text = displayValue,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }

        DragOnlySlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
