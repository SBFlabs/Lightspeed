#!/bin/bash
set -e

DIR="app/src/main/kotlin/com/sbf/lightspeed"
SYS_DIR="$DIR/system"

# 1. Rename files
mv $DIR/GearPickerActivity.kt $DIR/CockpitGearPickerActivity.kt
mv $DIR/GearPickerDialogs.kt $DIR/CockpitGearPickerDialogs.kt
mv $DIR/GearPickerModels.kt $DIR/CockpitGearPickerModels.kt
mv $DIR/GearPickerRows.kt $DIR/CockpitGearPickerRows.kt
mv $DIR/CruiseHangarRenderer.kt $DIR/CockpitHangarRenderer.kt
mv $SYS_DIR/GearSetRepository.kt $SYS_DIR/CockpitGearRepository.kt

# 2. Replace occurrences in all kotlin and xml files
find app/src/main -type f \( -name "*.kt" -o -name "*.xml" \) -print0 | xargs -0 sed -i \
  -e 's/GearPickerActivity/CockpitGearPickerActivity/g' \
  -e 's/GearPickerDialogs/CockpitGearPickerDialogs/g' \
  -e 's/GearPickerModels/CockpitGearPickerModels/g' \
  -e 's/GearPickerRows/CockpitGearPickerRows/g' \
  -e 's/CruiseHangarRenderer/CockpitHangarRenderer/g' \
  -e 's/GearSetRepository/CockpitGearRepository/g'

echo "Refactor completed."
