package com.sbf.lightspeed.settings

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
@Composable
fun LiquidGlassCustomizationDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    val thresholdPx = with(density) { 90.dp.toPx() }
    val dragOffsetY = remember { Animatable(0f) }
    var hasCrossedThreshold by remember { mutableStateOf(false) }

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
        val backdropVisuals = rememberDeckBackdropVisuals(context)
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
            dialogWindow?.setDimAmount(backdropVisuals.dimAmount)
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
                                        "LIQUID GLASS CALIBRATION",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        "Material glassmorphism, progressive blur & optics",
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

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 10.dp))

                    // ── Scrollable Body ────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // ── Section 1: Live Interactive Glass Preview Card ──
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF10141D))
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Simulated background texture behind the glass
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
                                            "LIGHTSPEED AVIONICS",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "ORBITAL DECK // 120Hz",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = colorScheme.tertiary.copy(alpha = 0.7f)
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(colorScheme.primary.copy(alpha = 0.35f))
                                        )
                                        Column {
                                            Text("Cruising at Mach 9.4", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("Dynamic Material 3 Color Optics", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.8f))
                                        }
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
                        }

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

                        // ── Toggle 2: Dynamic Caustic Shimmer ───────────────
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
                                    "Fluid Caustic Shimmer",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "Subtle moving light pool and catch-light flares across glass",
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
