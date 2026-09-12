#!/bin/bash
sed -i '/keyboardOptions = KeyboardOptions(/i\
                                keyboardActions = KeyboardActions(onDone = { onDismiss() }),' app/src/main/kotlin/com/sbf/lightspeed/settings/SettingsDialogComponents.kt
