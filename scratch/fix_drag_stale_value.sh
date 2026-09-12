#!/bin/bash
sed -i '/var lastHapticSteppedVal/a\
    val currentVal by androidx.compose.runtime.rememberUpdatedState(value)' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i 's/dragProgress = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f/dragProgress = if (range > 0f) ((currentVal - valueRange.start) \/ range).coerceIn(0f, 1f) else 0f/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i 's/lastHapticSteppedVal = value/lastHapticSteppedVal = currentVal/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

