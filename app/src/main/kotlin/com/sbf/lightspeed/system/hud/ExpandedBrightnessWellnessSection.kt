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
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.settings.CollapsibleSubSection
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.lsString
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
                            lsString(R.string.vdeck_display_bright),
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
                                text = if (isVideoOverrideActive) {
                                    lsString(R.string.vdeck_bright_override_fmt, (systemBrightness * 100).toInt())
                                } else {
                                    "${(systemBrightness * 100).toInt()}%"
                                },
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
                        Text(lsString(R.string.vdeck_accel_title), fontSize = 9.5.sp, color = lsColors.textPrimary)
                        Text(
                            if (scrubAccelEnabled) lsString(R.string.vdeck_accel_vel_fmt, scrubAccelRate / 10f)
                            else lsString(R.string.vdeck_accel_linear),
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
                    Text(lsString(R.string.vdeck_preserve_auto), fontSize = 9.5.sp, color = lsColors.textPrimary)
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
                        Text(lsString(R.string.vdeck_sensor_offset), fontSize = 9.5.sp, color = lsColors.textPrimary)
                        Icon(
                            Icons.Default.Info,
                            contentDescription = lsString(R.string.vdeck_offset_cd),
                            tint = if (showOffsetExplanation) lsColors.accent else lsColors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(if (sensorOffsetPct > 0) "+$sensorOffsetPct%" else if (sensorOffsetPct < 0) "$sensorOffsetPct%" else lsString(R.string.vdeck_offset_neutral), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accent)
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
                            lsString(R.string.vdeck_offset_desc),
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
                                lsString(R.string.vdeck_override_hdr),
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (autoResetEnabled) lsColors.accent else lsColors.textSecondary
                            )
                            Icon(
                                Icons.Default.Info,
                                contentDescription = lsString(R.string.vdeck_override_cd),
                                tint = if (showOverrideExplanation) lsColors.accent else lsColors.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            if (autoResetEnabled) lsString(R.string.vdeck_override_sub_on)
                            else lsString(R.string.vdeck_override_sub_off),
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
                            lsString(R.string.vdeck_override_desc),
                            fontSize = 8.sp,
                            color = lsColors.textSecondary,
                            lineHeight = 11.sp
                        )
                    }
                }

                // Auto-Brightness Reset Multi-Trigger Chips
                Text(
                    lsString(R.string.vdeck_triggers_hdr),
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
                        Triple(LightspeedPreferences.AUTO_RESET_OFF, lsString(R.string.vdeck_trig_manual), ""),
                        Triple(LightspeedPreferences.AUTO_RESET_APP_SWITCH, lsString(R.string.vdeck_trig_app_switch), ""),
                        Triple(LightspeedPreferences.AUTO_RESET_SCREEN_OFF, lsString(R.string.vdeck_trig_screen_off), ""),
                        Triple(LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE, lsString(R.string.vdeck_trig_recents), lsString(R.string.sdeck_hold_tag))
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
    var extraDimLevel by remember {
        mutableFloatStateOf(
            prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
                .let { if (it in 10..90) it else 40 }
                .toFloat()
        )
    }

    var smartPixelsEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) }
    var smartPixelsPattern by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)) }
    var smartPixelsBlockSize by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, 1)) }
    var smartPixelsDisableOnCast by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, true)) }
    var eyeCareEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)) }
    var eyeCareTemp by remember {
        mutableFloatStateOf(
            prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, LightspeedEyeCareEngine.DEFAULT_KELVIN)
                .coerceIn(LightspeedEyeCareEngine.MIN_KELVIN, LightspeedEyeCareEngine.MAX_KELVIN)
                .toFloat()
        )
    }

    LaunchedEffect(Unit) {
        val (syncedEnabled, syncedKelvin) = LightspeedEyeCareEngine.syncFromSystem(service)
        eyeCareEnabled = syncedEnabled
        eyeCareTemp = syncedKelvin.toFloat()
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                LightspeedPreferences.KEY_EXTRA_DIM_ENABLED -> extraDimEnabled = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_EXTRA_DIM_LEVEL -> extraDimLevel = prefs.getInt(key, 40).let { if (it in 10..90) it else 40 }.toFloat()
                LightspeedPreferences.KEY_SMART_PIXELS_ENABLED -> smartPixelsEnabled = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_SMART_PIXELS_PATTERN -> smartPixelsPattern = prefs.getInt(key, 25)
                LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE -> smartPixelsBlockSize = prefs.getInt(key, 1)
                LightspeedPreferences.KEY_EYE_CARE_ENABLED -> eyeCareEnabled = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_EYE_CARE_TEMP -> eyeCareTemp = prefs.getInt(key, LightspeedEyeCareEngine.DEFAULT_KELVIN)
                    .coerceIn(LightspeedEyeCareEngine.MIN_KELVIN, LightspeedEyeCareEngine.MAX_KELVIN)
                    .toFloat()
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Below 0% Scrub Target
        Text(lsString(R.string.vdeck_below_zero_hdr), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accent)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val options = listOf(
                Pair(LightspeedPreferences.SCRUB_EXTREME_EYE_CARE, lsString(R.string.vdeck_pill_eye_care)),
                Pair(LightspeedPreferences.SCRUB_EXTREME_EXTRA_DIM, lsString(R.string.vdeck_pill_extra_dim)),
                Pair(LightspeedPreferences.SCRUB_EXTREME_SMART_PIXELS, lsString(R.string.vdeck_pill_smart_pixels))
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
            Text(lsString(R.string.vdeck_extra_dim_fmt, extraDimLevel.toInt()), fontSize = 9.5.sp, color = lsColors.textPrimary)
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
            Text(lsString(R.string.vdeck_sp_density_fmt, smartPixelsPattern), fontSize = 9.5.sp, color = lsColors.textPrimary)
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
                Text(lsString(R.string.vdeck_sp_block_fmt, smartPixelsBlockSize), fontSize = 9.sp, color = lsColors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1, 2, 4).forEach { size ->
                        val isSel = smartPixelsBlockSize == size
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isSel) lsColors.accent.copy(alpha = 0.35f) else lsColors.dialogSurface.copy(alpha = 0.3f))
                                .clickable {
                                    smartPixelsBlockSize = size
                                    LightspeedSmartPixelsOverlay.setBlockSize(service, size)
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
                Text(lsString(R.string.vdeck_sp_cast_bypass), fontSize = 8.5.sp, color = lsColors.textSecondary)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(lsString(R.string.vdeck_eye_care_title), fontSize = 9.5.sp, color = lsColors.textPrimary)
                Text(
                    if (eyeCareEnabled) {
                        val tempK = eyeCareTemp.toInt()
                        when {
                            eyeCareTemp < 2600f -> lsString(R.string.vdeck_eye_care_candle, tempK)
                            eyeCareTemp < 3400f -> lsString(R.string.vdeck_eye_care_amber, tempK)
                            else -> lsString(R.string.vdeck_eye_care_evening, tempK)
                        }
                    } else {
                        lsString(R.string.vdeck_eye_care_disabled)
                    },
                    fontSize = 8.5.sp,
                    color = if (eyeCareEnabled) lsColors.accentWarn else lsColors.textSecondary
                )
            }
            DeckSwitch(
                checked = eyeCareEnabled,
                onCheckedChange = {
                    eyeCareEnabled = it
                    LightspeedEyeCareEngine.applyEyeCare(service, it, eyeCareTemp.toInt(), updateTemperature = false)
                },
                modifier = Modifier.height(22.dp)
            )
        }

        if (eyeCareEnabled) {
            val minK = LightspeedEyeCareEngine.MIN_KELVIN.toFloat()
            val maxK = LightspeedEyeCareEngine.MAX_KELVIN.toFloat()
            val warmthFraction = ((maxK - eyeCareTemp) / (maxK - minK)).coerceIn(0f, 1f)
            DeckSlider(
                value = warmthFraction,
                valueRange = 0f..1f,
                onValueChange = { frac ->
                    val newKelvin = (maxK - frac.coerceIn(0f, 1f) * (maxK - minK))
                        .roundToInt()
                        .coerceIn(LightspeedEyeCareEngine.MIN_KELVIN, LightspeedEyeCareEngine.MAX_KELVIN)
                    eyeCareTemp = newKelvin.toFloat()
                    LightspeedEyeCareEngine.applyEyeCare(service, true, newKelvin)
                },
                modifier = Modifier.height(22.dp)
            )
        }
    }
}
