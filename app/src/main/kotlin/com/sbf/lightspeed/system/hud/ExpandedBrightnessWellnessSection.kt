package com.sbf.lightspeed.system.hud

import android.accessibilityservice.AccessibilityService
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.settings.DeckSubChip
import com.sbf.lightspeed.settings.CollapsibleSubSection
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VistaBrightnessSection(
    service: AccessibilityService,
    onOpenMediaOverridePicker: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    val prefs = service.defaultPrefs()

    var systemBrightness by remember {
        val cur = LightspeedDisplayWellnessResetEngine.getSystemBrightness(service)
        mutableFloatStateOf(cur / 255f)
    }

    var isVideoOverrideActive by remember {
        mutableStateOf(LightspeedDisplayWellnessResetEngine.getActiveVideoOverride() != null)
    }

    var autoResetEnabled by remember {
        val legacyMode = prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF)
        val defaultEnabled = legacyMode != null && legacyMode != LightspeedPreferences.AUTO_RESET_OFF
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_ENABLED, defaultEnabled))
    }

    var selectedTriggers by remember {
        val legacyMode = prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF)
        val initialSet = prefs.getStringSet(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_TRIGGERS, null)
            ?: if (legacyMode != null && legacyMode != LightspeedPreferences.AUTO_RESET_OFF) setOf(legacyMode)
               else setOf(LightspeedPreferences.AUTO_RESET_SCREEN_OFF)
        mutableStateOf(initialSet.toSet())
    }

    var preserveAutoMode by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_BRIGHTNESS_PRESERVE_AUTO_MODE, false))
    }

    var sensorOffsetPct by remember {
        mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SENSOR_OFFSET_PCT, 0))
    }

    var scrubAccelEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_ACCELERATION_ENABLED, true))
    }

    var scrubAccelRate by remember {
        mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_ACCELERATION_RATE, 15))
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- CARD 1: Brightness & Kinetic Controls ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(lsColors.dialogSurface.copy(alpha = 0.25f))
                .border(0.6.dp, lsColors.accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Brightness Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Display Brightness",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isVideoOverrideActive) lsColors.accentWarn.copy(alpha = 0.2f) else lsColors.accent.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isVideoOverrideActive) "${(systemBrightness * 100).toInt()}% (Video Override)" else "${(systemBrightness * 100).toInt()}%",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVideoOverrideActive) lsColors.accentWarn else lsColors.accent
                        )
                    }
                }

                DeckSlider(
                    value = systemBrightness,
                    valueRange = 0.01f..1.00f,
                    onValueChange = { newPct ->
                        systemBrightness = newPct
                        LightspeedDisplayWellnessResetEngine.setSystemBrightness(service, kotlin.math.round(newPct * 255).toInt())
                        if (isVideoOverrideActive) {
                            LightspeedDisplayWellnessResetEngine.setVideoOverride(service, newPct)
                        }
                    },
                    modifier = Modifier.height(26.dp)
                )

                // Kinetic Scrub Acceleration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Acceleration", fontSize = 9.5.sp, color = lsColors.textPrimary)
                        Text(
                            if (scrubAccelEnabled) "Velocity curve: ${String.format(java.util.Locale.US, "%.1fx", scrubAccelRate / 10f)} multiplier"
                            else "Linear 1:1 scrubbing (no velocity ramp)",
                            fontSize = 8.5.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = scrubAccelEnabled,
                        onCheckedChange = {
                            scrubAccelEnabled = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_ACCELERATION_ENABLED, it).apply()
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                if (scrubAccelEnabled) {
                    DeckSlider(
                        value = scrubAccelRate.toFloat(),
                        valueRange = 5f..40f,
                        onValueChange = {
                            val intVal = it.roundToInt().coerceIn(5, 40)
                            scrubAccelRate = intVal
                            prefs.edit().putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_ACCELERATION_RATE, intVal).apply()
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                // Preserve Auto-Brightness toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preserve Auto-Brightness on Scrub", fontSize = 9.5.sp, color = lsColors.textPrimary)
                    DeckSwitch(
                        checked = preserveAutoMode,
                        onCheckedChange = {
                            preserveAutoMode = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_BRIGHTNESS_PRESERVE_AUTO_MODE, it).apply()
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                var showOffsetExplanation by remember { mutableStateOf(false) }

                // Protective Screen / Case Sensor Offset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { showOffsetExplanation = !showOffsetExplanation }
                    ) {
                        Text("Protective Screen / Sensor Offset", fontSize = 9.5.sp, color = lsColors.textPrimary)
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Why this value?",
                            tint = if (showOffsetExplanation) lsColors.accent else lsColors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(if (sensorOffsetPct > 0) "+$sensorOffsetPct%" else if (sensorOffsetPct < 0) "$sensorOffsetPct%" else "0% (Neutral)", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accent)
                }

                if (showOffsetExplanation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(lsColors.dialogSurface.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text(
                            "Thick glass screen protectors or heavy-duty cases often shade or reflect light away from the ambient lux sensor, causing Android auto-brightness to think the room is darker than it is and underexpose your screen. This offset shifts the baseline brightness up (+1%..+20%) or down to keep auto-curves perfectly legible without disabling adaptive mode.",
                            fontSize = 8.sp,
                            color = lsColors.textSecondary,
                            lineHeight = 11.sp
                        )
                    }
                }

                DeckSlider(
                    value = sensorOffsetPct.toFloat(),
                    valueRange = -10f..20f,
                    onValueChange = {
                        val intVal = it.roundToInt()
                        sensorOffsetPct = intVal
                        prefs.edit().putInt(LightspeedPreferences.KEY_BRIGHTNESS_SENSOR_OFFSET_PCT, intVal).apply()
                    },
                    modifier = Modifier.height(22.dp)
                )
            }
        }

        // --- CARD 2: Brightness Override & Automation ---
        var showOverrideExplanation by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(lsColors.dialogSurface.copy(alpha = 0.25f))
                .border(
                    0.6.dp,
                    if (autoResetEnabled) lsColors.accent.copy(alpha = 0.35f) else lsColors.textSecondary.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Master Brightness Override Toggle with Info Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.clickable { showOverrideExplanation = !showOverrideExplanation }
                        ) {
                            Text(
                                "BRIGHTNESS OVERRIDE",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (autoResetEnabled) lsColors.accent else lsColors.textSecondary
                            )
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "What is Brightness Override?",
                                tint = if (showOverrideExplanation) lsColors.accent else lsColors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            if (autoResetEnabled) "Automated release via triggers below"
                            else "Whole automation off (Manually released only)",
                            fontSize = 8.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = autoResetEnabled,
                        onCheckedChange = { active ->
                            autoResetEnabled = active
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_ENABLED, active).apply()
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                if (showOverrideExplanation) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(lsColors.dialogSurface.copy(alpha = 0.5f))
                            .padding(8.dp)
                    ) {
                        Text(
                            "Brightness Override locks screen luminance using a top-level overlay so media players (VLC, MX Player, YouTube) or system ambient sensors cannot hijack or dim your display. Trigger it anytime by scrubbing upward past 100%. When automation is turned on, the system automatically disengages the override and restores your adaptive curve on the triggers selected below.",
                            fontSize = 8.sp,
                            color = lsColors.textSecondary,
                            lineHeight = 11.sp
                        )
                    }
                }

                // Auto-Brightness Reset Multi-Trigger Chips
                Text(
                    "AUTO-RELEASE TRIGGERS (SELECT MULTIPLE)",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (autoResetEnabled) lsColors.textPrimary else lsColors.textSecondary.copy(alpha = 0.4f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    val triggerChips = listOf(
                        Triple(LightspeedPreferences.AUTO_RESET_OFF, "Manual", ""),
                        Triple(LightspeedPreferences.AUTO_RESET_APP_SWITCH, "App Switch", ""),
                        Triple(LightspeedPreferences.AUTO_RESET_SCREEN_OFF, "Screen Off", ""),
                        Triple(LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE, "Recents Close", "HOLD ↗")
                    )

                    triggerChips.forEach { (triggerKey, title, subText) ->
                        val isManualOnly = triggerKey == LightspeedPreferences.AUTO_RESET_OFF
                        val isRecents = triggerKey == LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE
                        val isSelected = if (isManualOnly) {
                            selectedTriggers.isEmpty()
                        } else {
                            selectedTriggers.contains(triggerKey)
                        }
                        val chipActive = autoResetEnabled && isSelected

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        !autoResetEnabled -> lsColors.dialogSurface.copy(alpha = 0.15f)
                                        isSelected -> lsColors.accent.copy(alpha = 0.35f)
                                        else -> lsColors.dialogSurface.copy(alpha = 0.3f)
                                    }
                                )
                                .border(
                                    0.6.dp,
                                    when {
                                        !autoResetEnabled -> Color.Transparent
                                        isSelected -> lsColors.accent.copy(alpha = 0.5f)
                                        else -> lsColors.textSecondary.copy(alpha = 0.15f)
                                    }
                                )
                                .combinedClickable(
                                    enabled = autoResetEnabled,
                                    onClick = {
                                        val nextSet = if (isManualOnly) {
                                            emptySet()
                                        } else {
                                            if (isSelected) selectedTriggers - triggerKey
                                            else selectedTriggers + triggerKey
                                        }
                                        selectedTriggers = nextSet
                                        prefs.edit().putStringSet(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_TRIGGERS, nextSet).apply()
                                    },
                                    onLongClick = {
                                        if (isRecents) {
                                            onOpenMediaOverridePicker()
                                        }
                                    }
                                )
                                .padding(vertical = 6.dp, horizontal = 2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    title,
                                    fontSize = 7.5.sp,
                                    fontWeight = if (chipActive) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        !autoResetEnabled -> lsColors.textSecondary.copy(alpha = 0.35f)
                                        isSelected -> lsColors.accent
                                        else -> lsColors.textSecondary
                                    },
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 9.sp
                                )
                                if (subText.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        subText,
                                        fontSize = 6.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            !autoResetEnabled -> lsColors.textSecondary.copy(alpha = 0.25f)
                                            isSelected -> lsColors.accent.copy(alpha = 0.85f)
                                            else -> lsColors.textSecondary.copy(alpha = 0.5f)
                                        },
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
}

@Composable
fun VistaOpticsSection(
    service: AccessibilityService
) {
    val lsColors = LightspeedStyle.colors
    val prefs = service.defaultPrefs()

    var bottomExtreme by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_BOTTOM_EXTREME, LightspeedPreferences.SCRUB_EXTREME_NONE) ?: LightspeedPreferences.SCRUB_EXTREME_NONE)
    }

    var extraDimEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)) }
    var extraDimLevel by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40).toFloat()) }

    var smartPixelsEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) }
    var smartPixelsPattern by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)) }
    var smartPixelsBlockSize by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, 1)) }
    var smartPixelsDisableOnCast by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, true)) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Below 0% Scrub Target
        Text("BELOW 0% SCRUB TARGET", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val options = listOf(
                Pair(LightspeedPreferences.SCRUB_EXTREME_EYE_CARE, "Eye Care"),
                Pair(LightspeedPreferences.SCRUB_EXTREME_EXTRA_DIM, "Extra Dim"),
                Pair(LightspeedPreferences.SCRUB_EXTREME_SMART_PIXELS, "Smart Pixels")
            )
            options.forEach { (mode, label) ->
                val isSelected = bottomExtreme == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) lsColors.accent.copy(alpha = 0.3f) else lsColors.dialogSurface.copy(alpha = 0.3f))
                        .border(
                            0.6.dp,
                            if (isSelected) lsColors.accent.copy(alpha = 0.5f) else lsColors.textSecondary.copy(alpha = 0.15f),
                            RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            val nextMode = if (isSelected) LightspeedPreferences.SCRUB_EXTREME_NONE else mode
                            bottomExtreme = nextMode
                            prefs.edit().putString(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_BOTTOM_EXTREME, nextMode).apply()
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 8.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) lsColors.accent else lsColors.textSecondary
                    )
                }
            }
        }

        // Extra Dim
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Extra Dim: ${extraDimLevel.toInt()}%", fontSize = 9.5.sp, color = lsColors.textPrimary)
            DeckSwitch(
                checked = extraDimEnabled,
                onCheckedChange = {
                    extraDimEnabled = it
                    LightspeedEyeCareEngine.applyExtraDim(service, it, extraDimLevel.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (extraDimEnabled) {
            DeckSlider(
                value = extraDimLevel,
                valueRange = 10f..90f,
                onValueChange = {
                    extraDimLevel = it
                    LightspeedEyeCareEngine.applyExtraDim(service, true, it.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }

        // OLED Smart Pixels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Smart Pixels Density: $smartPixelsPattern%", fontSize = 9.5.sp, color = lsColors.textPrimary)
            DeckSwitch(
                checked = smartPixelsEnabled,
                onCheckedChange = {
                    smartPixelsEnabled = it
                    LightspeedSmartPixelsOverlay.setEnabled(service, it, smartPixelsPattern)
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (smartPixelsEnabled) {
            DeckSlider(
                value = smartPixelsPattern.toFloat(),
                valueRange = 5f..85f,
                onValueChange = {
                    val intVal = it.roundToInt().coerceIn(5, 85)
                    smartPixelsPattern = intVal
                    LightspeedSmartPixelsOverlay.setEnabled(service, true, intVal)
                },
                modifier = Modifier.height(22.dp)
            )

            // Block Size / Pixel Pattern Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Block Size: ${smartPixelsBlockSize}px", fontSize = 9.sp, color = lsColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 4).forEach { size ->
                        val isSel = smartPixelsBlockSize == size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSel) lsColors.accent.copy(alpha = 0.35f) else lsColors.dialogSurface.copy(alpha = 0.3f))
                                .clickable {
                                    smartPixelsBlockSize = size
                                    prefs.edit().putInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, size).apply()
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("${size}x${size}", fontSize = 8.sp, color = if (isSel) lsColors.accent else lsColors.textSecondary)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Bypass on Screen Record / Cast", fontSize = 8.5.sp, color = lsColors.textSecondary)
                DeckSwitch(
                    checked = smartPixelsDisableOnCast,
                    onCheckedChange = {
                        smartPixelsDisableOnCast = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, it).apply()
                        LightspeedSmartPixelsOverlay.refreshCaptureBypass(service)
                    },
                    modifier = Modifier.height(20.dp)
                )
            }
        }

        // Eye Care & Blue Light Filter
        var eyeCareEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)) }
        var eyeCareTemp by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3400).coerceIn(2200, 5500).toFloat()) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Eye Care & Blue Light Filter", fontSize = 9.5.sp, color = lsColors.textPrimary)
                Text(
                    if (eyeCareEnabled) "${eyeCareTemp.toInt()}K — " + when {
                        eyeCareTemp < 2600f -> "Deep Candlelight"
                        eyeCareTemp < 3400f -> "Warm Amber"
                        else -> "Soft Evening"
                    } else "Filter disabled",
                    fontSize = 8.5.sp,
                    color = if (eyeCareEnabled) lsColors.accentWarn else lsColors.textSecondary
                )
            }
            DeckSwitch(
                checked = eyeCareEnabled,
                onCheckedChange = {
                    eyeCareEnabled = it
                    LightspeedEyeCareEngine.applyEyeCare(service, it, eyeCareTemp.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }

        if (eyeCareEnabled) {
            DeckSlider(
                value = eyeCareTemp,
                valueRange = 2200f..5500f,
                onValueChange = {
                    eyeCareTemp = it
                    LightspeedEyeCareEngine.applyEyeCare(service, true, it.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }
    }
}

@Composable
fun VistaEyeCareSection(
    service: AccessibilityService
) {
    VistaOpticsSection(service = service)
}

@Composable
fun VistaAirlockSection(
    service: AccessibilityService,
    onOpenAirlockPicker: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    val prefs = service.defaultPrefs()

    var airlockEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false))
    }
    var airlockApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet()) ?: emptySet())
    }

    var eyeRestEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false))
    }
    var eyeRestInterval by remember {
        mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20).toFloat())
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Astrogation Airlock
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Astrogation Airlock Delay", fontSize = 9.5.sp, color = lsColors.textPrimary)
                Text("${airlockApps.size} gated apps with 5s mindful pause", fontSize = 8.5.sp, color = lsColors.textSecondary)
            }
            DeckSwitch(
                checked = airlockEnabled,
                onCheckedChange = {
                    airlockEnabled = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, it).apply()
                },
                modifier = Modifier.height(22.dp)
            )
        }

        if (airlockEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DeckSubChip(
                    text = "Gated Apps (${airlockApps.size})",
                    icon = Icons.Default.LockClock,
                    isActive = airlockApps.isNotEmpty(),
                    onClick = onOpenAirlockPicker
                )
            }
        }

        // 20-20-20 Eye Rest
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("20-20-20 Eye Rest Timer", fontSize = 9.5.sp, color = lsColors.textPrimary)
                Text(if (eyeRestEnabled) "Every ${eyeRestInterval.toInt()} min of continuous screen time" else "Periodic 20s eye rest break", fontSize = 8.5.sp, color = lsColors.textSecondary)
            }
            DeckSwitch(
                checked = eyeRestEnabled,
                onCheckedChange = {
                    eyeRestEnabled = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, it).apply()
                    LightspeedEyeCareEngine.onScreenStateChanged(service, it)
                },
                modifier = Modifier.height(22.dp)
            )
        }

        if (eyeRestEnabled) {
            DeckSlider(
                value = eyeRestInterval,
                valueRange = 10f..60f,
                onValueChange = {
                    val intVal = it.roundToInt()
                    eyeRestInterval = intVal.toFloat()
                    prefs.edit().putInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, intVal).apply()
                },
                modifier = Modifier.height(22.dp)
            )
        }
    }
}
