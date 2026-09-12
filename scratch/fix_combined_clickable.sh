#!/bin/bash
sed -i 's/\.androidx\.compose\.foundation\.combinedClickable/.combinedClickable/g' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
sed -i '4a\
import androidx.compose.foundation.combinedClickable' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
