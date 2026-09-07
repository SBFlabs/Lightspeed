#!/bin/bash
set -e

DIR="app/src/main/kotlin/com/sbf/lightspeed/settings"

# Rename files
mv $DIR/SidebarMatrixConfig.kt $DIR/CentralCommandConfig.kt
mv $DIR/SidebarSettingsActivity.kt $DIR/CentralCommandActivity.kt
mv $DIR/SidebarSettingsViewModel.kt $DIR/CentralCommandViewModel.kt

# Find and replace in all Kotlin and XML files
find app/src/main -type f \( -name "*.kt" -o -name "*.xml" \) -print0 | xargs -0 sed -i \
  -e 's/SidebarMatrixConfigurationFields/CentralCommandMatrixFields/g' \
  -e 's/SidebarMatrixConfig/CentralCommandConfig/g' \
  -e 's/SidebarSettingsActivity/CentralCommandActivity/g' \
  -e 's/SidebarSettingsViewModel/CentralCommandViewModel/g'

echo "Central Command Refactor Complete"
