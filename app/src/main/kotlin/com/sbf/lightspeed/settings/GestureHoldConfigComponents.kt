package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
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
 * Dedicated hold-delay calibration chip, dialog, and precision flyout.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GestureHoldDurationControl(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    activeSnapshot: DeckThemeSnapshot,
    opticalColors: OpticalSurfaceColors,
    readableTint: Color
) {
    val lsColors = LightspeedStyle.colors
    val perGestureHoldKey = "pref_gesture_hold_duration_$key"
    var currentHoldMs by remember(key, perGestureHoldKey) {
        mutableIntStateOf(
            prefs.getInt(
                perGestureHoldKey,
                prefs.getInt(LightspeedPreferences.KEY_GESTURE_HOLD_DURATION_MS, 300)
            )
        )
    }
    var showHoldDialog by remember { mutableStateOf(false) }

    DeckSubChip(
        text = "${currentHoldMs}ms ▾",
        icon = Icons.Default.Timer,
        isActive = true,
        snapshot = activeSnapshot,
        onClick = { showHoldDialog = true }
    )

    if (showHoldDialog) {
        var tempHoldVal by remember { mutableFloatStateOf(currentHoldMs.toFloat()) }
        var applyToAll by remember { mutableStateOf(false) }
        var isHoldFlyoutOpen by remember { mutableStateOf(false) }
        var isTapToJumpHold by remember {
            mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$perGestureHoldKey", false))
        }

        DeckDialog(
            onDismissRequest = { showHoldDialog = false },
            title = {
                Text(
                    text = "Hold Trigger Delay",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Duration to pause and hold before the hold modifier action activates.",
                        fontSize = 12.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.75f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trigger Delay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        val sliderChipShape = RoundedCornerShape((activeSnapshot.cornerRadiusDp * 0.28f).coerceIn(6f, 8f).dp)
                        Box(
                            modifier = Modifier
                                .clip(sliderChipShape)
                                .border(
                                    BorderStroke(
                                        (activeSnapshot.borderWidthDp * 0.75f).coerceIn(0.8f, 1.2f).dp,
                                        opticalColors.borderColor.copy(alpha = 0.40f)
                                    ),
                                    sliderChipShape
                                )
                                .background(readableTint.copy(alpha = 0.16f))
                                .combinedClickable(
                                    onClick = {
                                        LightspeedHapticEngine.tick(context)
                                        isHoldFlyoutOpen = true
                                    },
                                    onLongClick = {
                                        LightspeedHapticEngine.heavyClick(context)
                                        tempHoldVal = 300f
                                    }
                                )
                        ) {
                            Text(
                                text = "${tempHoldVal.roundToInt()} ms",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                fontFamily = FontFamily.Monospace,
                                color = readableTint,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    DragOnlySlider(
                        value = tempHoldVal,
                        onValueChange = {
                            val near = (it.roundToInt() / 25) * 25
                            tempHoldVal = near.coerceIn(150, 800).toFloat()
                        },
                        valueRange = 150f..800f,
                        steps = 25,
                        tapToJump = isTapToJumpHold,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { applyToAll = !applyToAll }
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Apply as global default to all hold gestures",
                            fontSize = 11.sp,
                            color = lsColors.textSecondary
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalVal = tempHoldVal.toInt()
                        currentHoldMs = finalVal
                        val editor = prefs.edit()
                        editor.putInt(perGestureHoldKey, finalVal)
                        if (applyToAll) {
                            editor.putInt(LightspeedPreferences.KEY_GESTURE_HOLD_DURATION_MS, finalVal)
                        }
                        editor.apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHoldConfigComponents", "GestureHoldDurationControl", e) }
                        showHoldDialog = false
                    }
                ) {
                    Text("APPLY", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showHoldDialog = false }) {
                    Text("CANCEL", color = lsColors.textSecondary)
                }
            }
        )

        if (isHoldFlyoutOpen) {
            SliderCalibrationFlyoutDialog(
                title = "Hold Trigger Delay",
                sliderKey = perGestureHoldKey,
                currentValueStr = "${tempHoldVal.roundToInt()}",
                defaultValueStr = "300",
                onValueTyped = { typed ->
                    typed.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempHoldVal = it.coerceIn(150, 800).toFloat()
                    }
                },
                onResetToDefault = { tempHoldVal = 300f },
                onSelectProfile = { profileValueStr ->
                    profileValueStr.split(" ").firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()?.let {
                        tempHoldVal = it.coerceIn(150, 800).toFloat()
                    }
                },
                onDismiss = { isHoldFlyoutOpen = false },
                prefs = prefs,
                context = context,
                isTapToJumpEnabled = isTapToJumpHold,
                onTapToJumpChanged = { enabled ->
                    isTapToJumpHold = enabled
                    prefs.edit().putBoolean("pref_slider_tap_to_jump_$perGestureHoldKey", enabled).apply()
                }
            )
        }
    }
}
