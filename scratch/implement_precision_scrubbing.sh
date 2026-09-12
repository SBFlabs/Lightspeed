#!/bin/bash
sed -i 's/detectHorizontalDragGestures/androidx.compose.foundation.gestures.detectDragGestures/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i 's/onHorizontalDrag = { change, dragAmount ->/onDrag = { change, dragAmount ->/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

