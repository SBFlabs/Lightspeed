package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Tactical slider row with numeric badge, tap-to-calibrate flyout, and long-press default reset.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OverrideSliderRow(
    context: Context,
    prefs: SharedPreferences,
    sliderKey: String,
    defaultValue: Float,
    icon: ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    isEnabled: Boolean,
    valueFormatter: (Float) -> String = { String.format(java.util.Locale.US, "%.2fx", it) },
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$sliderKey", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else lsColors.textPrimary.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) lsColors.textPrimary else lsColors.textPrimary.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else lsColors.textPrimary.copy(alpha = 0.2f)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = isEnabled,
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            isFlyoutOpen = true
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            onValueChange(defaultValue)
                            onValueChangeFinished()
                        }
                    )
            ) {
                Text(
                    text = valueFormatter(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        DragOnlySlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = isEnabled,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = sliderKey,
            currentValueStr = value.toString(),
            defaultValueStr = defaultValue.toString(),
            onValueTyped = { typed ->
                typed.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(valueRange))
                }
            },
            onResetToDefault = {
                onValueChange(defaultValue)
                onValueChangeFinished()
            },
            onSelectProfile = { profileValueStr ->
                profileValueStr.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(valueRange))
                    onValueChangeFinished()
                }
            },
            onDismiss = {
                isFlyoutOpen = false
                onValueChangeFinished()
            },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = {
                isTapToJumpEnabled = it
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$sliderKey", it).apply()
            }
        )
    }
}
