#!/bin/bash
sed -i '/fun OverrideSliderRow(/i\
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

sed -i 's/fun OverrideSliderRow(/fun OverrideSliderRow(\
    context: Context,\
    prefs: SharedPreferences,\
    sliderKey: String,\
    defaultValue: Float,/' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
