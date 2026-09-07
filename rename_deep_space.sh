#!/bin/bash
set -e

DIR="app/src/main/kotlin/com/sbf/lightspeed"

# 1. Rename the file
mv $DIR/CockpitHangarRenderer.kt $DIR/DeepSpaceRenderer.kt

# 2. Replace occurrences in all kotlin files
find app/src/main -type f \( -name "*.kt" \) -print0 | xargs -0 sed -i \
  -e 's/CockpitHangarRenderer/DeepSpaceRenderer/g' \
  -e 's/hangarRenderer/deepSpaceRenderer/g' \
  -e 's/drawHangar/drawDeepSpace/g'

echo "Deep Space Rename completed."
