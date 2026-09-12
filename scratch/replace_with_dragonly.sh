#!/bin/bash
sed -i 's/Slider(/DragOnlySlider(/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '/tapToJump = isTapToJumpEnabled,/! s/modifier = Modifier.fillMaxWidth().height(36.dp),/tapToJump = isTapToJumpEnabled,\
            modifier = Modifier.fillMaxWidth().height(36.dp),/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '/colors = SliderDefaults.colors(/,/)/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
