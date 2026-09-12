#!/bin/bash
# 1. Edge Gesture Sovereignty
sed -i 's/icon = Icons.Default.VerticalDistribute,/context = context,\
            prefs = prefs,\
            sliderKey = "sys_override_edge_gesture",\
            defaultValue = 1.0f,\
            icon = Icons.Default.VerticalDistribute,/' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

# 2. Animation Speeds
sed -i 's/icon = Icons.Default.Speed,/context = context,\
            prefs = prefs,\
            sliderKey = "sys_override_animation_speed",\
            defaultValue = 1.0f,\
            icon = Icons.Default.Speed,/' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

# 3. Display Metrics
sed -i 's/icon = Icons.Default.ScreenshotMonitor,/context = context,\
            prefs = prefs,\
            sliderKey = "sys_override_display_dpi",\
            defaultValue = context.resources.configuration.densityDpi.toFloat(),\
            icon = Icons.Default.ScreenshotMonitor,/' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

# 4. Font Scale
sed -i 's/icon = Icons.Default.FontDownload,/context = context,\
            prefs = prefs,\
            sliderKey = "sys_override_font_scale",\
            defaultValue = 1.0f,\
            icon = Icons.Default.FontDownload,/' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
