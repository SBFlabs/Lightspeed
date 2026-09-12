#!/bin/bash
sed -i 's/import androidx.compose.foundation.gestures.androidx.compose.foundation.gestures.detectDragGestures/import androidx.compose.foundation.gestures.detectDragGestures/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i 's/androidx.compose.foundation.gestures.detectDragGestures(/detectDragGestures(/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt
