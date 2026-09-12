#!/bin/bash
sed -i '57i \    var showLanguageHud by remember { mutableStateOf(false) }\n    var activeLanguageIndex by remember { mutableIntStateOf(-1) }\n' app/src/main/kotlin/com/sbf/lightspeed/settings/MainSettingsScreen.kt
