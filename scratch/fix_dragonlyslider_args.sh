#!/bin/bash
sed -i '/activeTrackColor =/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '/inactiveTrackColor =/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
# Now there might be two `)` lines.
sed -i 's/modifier = Modifier.fillMaxWidth().height(36.dp),/modifier = Modifier.fillMaxWidth().height(36.dp)/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
