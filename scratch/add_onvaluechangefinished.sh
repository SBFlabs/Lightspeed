#!/bin/bash
sed -i 's/onValueChange: (Float) -> Unit,/onValueChange: (Float) -> Unit,\
    onValueChangeFinished: (() -> Unit)? = null,/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i '/onValueChange(steppedValue)/a\
                        onValueChangeFinished?.invoke()' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

