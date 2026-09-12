#!/bin/bash
sed -i '/var edgeGestureScale by remember/a\
    var animationScale by remember { mutableFloatStateOf(1.0f) }\
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }\
    var fontScale by remember { mutableFloatStateOf(1.0f) }' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

sed -i '/edgeGestureScale = left/a\
                val anim = Settings.Global.getFloat(context.contentResolver, "window_animation_scale", 1.0f)\
                animationScale = anim\
                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)\
                fontScale = fScale' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
