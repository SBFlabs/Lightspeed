#!/bin/bash
sed -i '/onValueTyped = { typed ->/,/},/c\
            onValueTyped = { typed ->\
                typed.toFloatOrNull()?.let { f ->\
                    onValueChange(f.coerceIn(valueRange))\
                }\
            },' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt

sed -i '/onDismiss = { isFlyoutOpen = false }/c\
            onDismiss = {\
                isFlyoutOpen = false\
                onValueChangeFinished()\
            },' app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt
