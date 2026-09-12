#!/bin/bash
sed -i 's/onValueChange: (Float) -> Unit,/onValueChange: (Float) -> Unit,\
    onValueChangeFinished: (() -> Unit)? = null,/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i '/onTap = { offset ->/,/onValueChange(steppedValue)/ {
  /onValueChange(steppedValue)/!b
  a\
                        onValueChangeFinished?.invoke()
}' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i '/onDragEnd = {/a\
                        onValueChangeFinished?.invoke()' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

sed -i '/onDragCancel = {/a\
                        onValueChangeFinished?.invoke()' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsSliderComponents.kt

