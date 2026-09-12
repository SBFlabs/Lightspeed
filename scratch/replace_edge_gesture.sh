#!/bin/bash
sed -i 's/var edgeGestureEnabled by remember { mutableStateOf(false) }/var edgeGestureScale by remember { mutableFloatStateOf(1.0f) }/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i 's/edgeGestureEnabled = (left == 0.0f)/edgeGestureScale = left/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i 's/edgeGestureEnabled = false/edgeGestureScale = 1.0f/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
