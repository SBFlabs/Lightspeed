#!/bin/bash
# First delete the body of OverrideSliderRow
sed -i '/fun OverrideSliderRow/,/^}/c\
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)\
@Composable\
fun OverrideSliderRow(\
    context: Context,\
    prefs: SharedPreferences,\
    sliderKey: String,\
    defaultValue: Float,\
    icon: androidx.compose.ui.graphics.vector.ImageVector,\
    title: String,\
    subtitle: String,\
    value: Float,\
    valueRange: ClosedFloatingPointRange<Float>,\
    steps: Int = 0,\
    isEnabled: Boolean,\
    valueFormatter: (Float) -> String = { String.format("%.2fx", it) },\
    onValueChange: (Float) -> Unit,\
    onValueChangeFinished: () -> Unit\
) {\
    var isFlyoutOpen by remember { mutableStateOf(false) }\
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$sliderKey", false)) }\
\
    Column(\
        modifier = Modifier\
            .fillMaxWidth()\
            .clip(RoundedCornerShape(12.dp))\
            .padding(vertical = 8.dp, horizontal = 4.dp)\
    ) {\
        Row(\
            modifier = Modifier.fillMaxWidth(),\
            verticalAlignment = Alignment.CenterVertically\
        ) {\
            Icon(\
                imageVector = icon,\
                contentDescription = null,\
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),\
                modifier = Modifier.size(20.dp)\
            )\
            Spacer(modifier = Modifier.width(12.dp))\
            Column(modifier = Modifier.weight(1f)) {\
                Text(\
                    text = title,\
                    fontSize = 13.sp,\
                    fontWeight = FontWeight.Bold,\
                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f)\
                )\
                Text(\
                    text = subtitle,\
                    fontSize = 11.sp,\
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f)\
                )\
            }\
            Surface(\
                shape = RoundedCornerShape(8.dp),\
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),\
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),\
                modifier = Modifier\
                    .clip(RoundedCornerShape(8.dp))\
                    .androidx.compose.foundation.combinedClickable(\
                        enabled = isEnabled,\
                        onClick = {\
                            LightspeedHapticEngine.tick(context)\
                            onValueChange(defaultValue)\
                            onValueChangeFinished()\
                        },\
                        onLongClick = {\
                            LightspeedHapticEngine.heavyClick(context)\
                            isFlyoutOpen = true\
                        }\
                    )\
            ) {\
                Text(\
                    text = valueFormatter(value),\
                    fontSize = 13.sp,\
                    fontWeight = FontWeight.Black,\
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),\
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)\
                )\
            }\
        }\
        Spacer(modifier = Modifier.height(4.dp))\
        Slider(\
            value = value,\
            onValueChange = onValueChange,\
            onValueChangeFinished = onValueChangeFinished,\
            valueRange = valueRange,\
            steps = steps,\
            enabled = isEnabled,\
            modifier = Modifier.fillMaxWidth().height(36.dp),\
            colors = SliderDefaults.colors(\
                thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),\
                activeTrackColor = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f),\
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)\
            )\
        )\
    }\
\
    if (isFlyoutOpen) {\
        SliderCalibrationFlyoutDialog(\
            title = title,\
            sliderKey = sliderKey,\
            currentValueStr = value.toString(),\
            defaultValueStr = defaultValue.toString(),\
            onValueTyped = { typed ->\
                typed.toFloatOrNull()?.let { f ->\
                    onValueChange(f.coerceIn(valueRange))\
                    onValueChangeFinished()\
                }\
            },\
            onResetToDefault = {\
                onValueChange(defaultValue)\
                onValueChangeFinished()\
            },\
            onSelectProfile = { profileValueStr ->\
                profileValueStr.toFloatOrNull()?.let { f ->\
                    onValueChange(f.coerceIn(valueRange))\
                    onValueChangeFinished()\
                }\
            },\
            onDismiss = { isFlyoutOpen = false },\
            prefs = prefs,\
            context = context,\
            isTapToJumpEnabled = isTapToJumpEnabled,\
            onTapToJumpChanged = {\
                isTapToJumpEnabled = it\
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$sliderKey", it).apply()\
            }\
        )\
    }\
}' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
