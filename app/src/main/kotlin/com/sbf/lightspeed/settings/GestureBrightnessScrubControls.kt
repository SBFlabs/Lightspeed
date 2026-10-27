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
 * Dedicated scrub resolution sliders and calibration dialogs for Brightness.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureBrightnessScrubControls(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color
) {
    val lsColors = LightspeedStyle.colors
    var hudEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true))
    }
    var brightRes by remember {
        mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32))
    }
    var showBrightSliderDialog by remember { mutableStateOf(false) }

    DeckSubChip(
        text = if (hudEnabled) "Hud: ON" else "Hud: OFF",
        isActive = hudEnabled,
        snapshot = activeSnapshot,
        onClick = {
            hudEnabled = !hudEnabled
            prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, hudEnabled).apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureBrightnessScrubControls", "BrightnessHudToggle", e) }
        }
    )

    DeckSubChip(
        text = "Res: $brightRes ▾",
        isActive = false,
        snapshot = activeSnapshot,
        onClick = { showBrightSliderDialog = true }
    )

    if (showBrightSliderDialog) {
        var tempRes by remember { mutableFloatStateOf(brightRes.toFloat()) }
        var isBrightFlyoutOpen by remember { mutableStateOf(false) }
        val brightKey = LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION
        var isTapToJumpBright by remember {
            mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$brightKey", false))
        }

        DeckDialog(
            onDismissRequest = { showBrightSliderDialog = false },
            title = {
                Text(
                    text = "Brightness Scrub Resolution",
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
                        text = "Control total tactile graduation notches across the full 0–100% brightness range.",
                        fontSize = 12.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Graduation Steps",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        val curSteps = tempRes.toInt().coerceIn(10, 254)
                        val brightChipShape = RoundedCornerShape((activeSnapshot.cornerRadiusDp * 0.28f).coerceIn(6f, 8f).dp)
                        Box(
                            modifier = Modifier
                                .clip(brightChipShape)
                                .border(
                                    BorderStroke(
                                        (activeSnapshot.borderWidthDp * 0.75f).coerceIn(0.8f, 1.2f).dp,
                                        opticalColors.borderColor.copy(alpha = 0.40f)
                                    ),
                                    brightChipShape
                                )
                                .background(readableTint.copy(alpha = 0.16f))
                                .combinedClickable(
                                    onClick = {
                                        LightspeedHapticEngine.tick(context)
                                        isBrightFlyoutOpen = true
                                    },
                                    onLongClick = {
                                        LightspeedHapticEngine.heavyClick(context)
                                        tempRes = 32f
                                    }
                                )
                        ) {
                            Text(
                                text = "$curSteps Steps (~${String.format(java.util.Locale.US, "%.1f", 100f / curSteps)}%)",
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
                        valueRange = 10f..254f,
                        steps = 243,
                        tapToJump = isTapToJumpBright,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalRes = tempRes.toInt().coerceIn(10, 254)
                        brightRes = finalRes
                        val derivedStep = (255f / finalRes).toInt().coerceIn(1, 32)
                        prefs.edit()
                            .putInt(brightKey, finalRes)
                            .putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, derivedStep)
                            .apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureBrightnessScrubControls", "BrightnessSliderApply", e) }
                        showBrightSliderDialog = false
                    }
                ) {
                    Text("APPLY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBrightSliderDialog = false }) {
                    Text("CANCEL", color = lsColors.textSecondary)
                }
            }
        )

        if (isBrightFlyoutOpen) {
            SliderCalibrationFlyoutDialog(
                title = "Brightness Scrub Resolution",
                sliderKey = brightKey,
                currentValueStr = "${tempRes.roundToInt().coerceIn(10, 254)}",
                defaultValueStr = "32",
                onValueTyped = { typed ->
                    typed.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempRes = it.coerceIn(10, 254).toFloat()
                    }
                },
                onResetToDefault = { tempRes = 32f },
                onSelectProfile = { profileValueStr ->
                    profileValueStr.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempRes = it.coerceIn(10, 254).toFloat()
                    }
                },
                onDismiss = { isBrightFlyoutOpen = false },
                prefs = prefs,
                context = context,
                isTapToJumpEnabled = isTapToJumpBright,
                onTapToJumpChanged = { enabled ->
                    isTapToJumpBright = enabled
                    prefs.edit().putBoolean("pref_slider_tap_to_jump_$brightKey", enabled).apply()
                }
            )
        }
    }
}
