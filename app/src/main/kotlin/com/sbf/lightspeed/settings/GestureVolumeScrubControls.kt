package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlin.math.roundToInt

/**
 * Dedicated scrub resolution sliders and calibration dialogs for Volume.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureVolumeScrubControls(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color
) {
    val lsColors = LightspeedStyle.colors
    var hudEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true))
    }
    var nativeSliderEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false))
    }
    var volResolution by remember {
        mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100))
    }
    var showVolSliderDialog by remember { mutableStateOf(false) }

    DeckSubChip(
        text = if (hudEnabled) "Hud: ON" else "Hud: OFF",
        isActive = hudEnabled,
        snapshot = activeSnapshot,
        onClick = {
            hudEnabled = !hudEnabled
            prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, hudEnabled).apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureVolumeScrubControls", "VolumeHudToggle", e) }
        }
    )

    DeckSubChip(
        text = if (nativeSliderEnabled) "Native: ON" else "Native: OFF",
        isActive = nativeSliderEnabled,
        snapshot = activeSnapshot,
        onClick = {
            nativeSliderEnabled = !nativeSliderEnabled
            prefs.edit().putBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, nativeSliderEnabled).apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureVolumeScrubControls", "VolumeNativeSliderToggle", e) }
        }
    )

    DeckSubChip(
        text = "Res: ${volResolution} ▾",
        isActive = false,
        snapshot = activeSnapshot,
        onClick = { showVolSliderDialog = true }
    )

    if (showVolSliderDialog) {
        var tempRes by remember { mutableFloatStateOf(volResolution.toFloat()) }
        var isVolFlyoutOpen by remember { mutableStateOf(false) }
        val volKey = LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION
        var isTapToJumpVol by remember {
            mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$volKey", false))
        }

        DeckDialog(
            onDismissRequest = { showVolSliderDialog = false },
            title = {
                Text(
                    text = "Volume Scrub Resolution",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Graduation steps mapped across the audio volume range (5 to 100 steps / 100th precision).",
                        fontSize = 12.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Scrub Steps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        val curRes = tempRes.toInt().coerceIn(5, 100)
                        val pct = String.format(java.util.Locale.US, "%.1f", 100f / curRes.coerceAtLeast(1))
                        val volChipShape = RoundedCornerShape((activeSnapshot.cornerRadiusDp * 0.28f).coerceIn(6f, 8f).dp)
                        Box(
                            modifier = Modifier
                                .clip(volChipShape)
                                .border(
                                    BorderStroke(
                                        (activeSnapshot.borderWidthDp * 0.75f).coerceIn(0.8f, 1.2f).dp,
                                        opticalColors.borderColor.copy(alpha = 0.40f)
                                    ),
                                    volChipShape
                                )
                                .background(readableTint.copy(alpha = 0.16f))
                                .combinedClickable(
                                    onClick = {
                                        LightspeedHapticEngine.tick(context)
                                        isVolFlyoutOpen = true
                                    },
                                    onLongClick = {
                                        LightspeedHapticEngine.heavyClick(context)
                                        tempRes = 100f
                                    }
                                )
                        ) {
                            Text(
                                text = "$curRes Steps (~$pct%)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = readableTint,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    DragOnlySlider(
                        value = tempRes,
                        onValueChange = { tempRes = it },
                        valueRange = 5f..100f,
                        steps = 94,
                        tapToJump = isTapToJumpVol,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalRes = tempRes.toInt().coerceIn(5, 100)
                        val derivedStep = (100f / finalRes).roundToInt().coerceIn(1, 20)
                        volResolution = finalRes
                        prefs.edit()
                            .putInt(volKey, finalRes)
                            .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, derivedStep)
                            .apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureVolumeScrubControls", "VolumeSliderApply", e) }
                        showVolSliderDialog = false
                    }
                ) {
                    Text("APPLY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVolSliderDialog = false }) {
                    Text("CANCEL", color = lsColors.textSecondary)
                }
            }
        )

        if (isVolFlyoutOpen) {
            SliderCalibrationFlyoutDialog(
                title = "Volume Scrub Resolution",
                sliderKey = volKey,
                currentValueStr = "${tempRes.roundToInt().coerceIn(5, 100)}",
                defaultValueStr = "100",
                onValueTyped = { typed ->
                    typed.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempRes = it.coerceIn(5, 100).toFloat()
                    }
                },
                onResetToDefault = { tempRes = 100f },
                onSelectProfile = { profileValueStr ->
                    profileValueStr.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempRes = it.coerceIn(5, 100).toFloat()
                    }
                },
                onDismiss = { isVolFlyoutOpen = false },
                prefs = prefs,
                context = context,
                isTapToJumpEnabled = isTapToJumpVol,
                onTapToJumpChanged = { enabled ->
                    isTapToJumpVol = enabled
                    prefs.edit().putBoolean("pref_slider_tap_to_jump_$volKey", enabled).apply()
                }
            )
        }
    }
}
