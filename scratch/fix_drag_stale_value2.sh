#!/bin/bash
sed -i 's|dragProgress = if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f|dragProgress = if (range > 0f) ((currentVal - valueRange.start) / range).coerceIn(0f, 1f) else 0f|g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt
