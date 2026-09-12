#!/bin/bash
sed -i '/\/\/ 2. Animation Speeds/,/onClick = { \/\* TODO \*\/ }/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '/\/\/ 3. Display Metrics/,/onClick = { \/\* TODO \*\/ }/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '/\/\/ 4. Font Scale/,/onClick = { \/\* TODO \*\/ }/d' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

# The file now has a bunch of empty `OverrideSettingRow(` calls removed, but `)` might be left dangling. Let's fix that safely.
