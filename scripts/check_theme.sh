#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
KOTLIN_SRC="$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed"
SETTINGS_DIR="$KOTLIN_SRC/settings"
DECK_FILES=(
    "$KOTLIN_SRC/system/SonicDeckManager.kt"
    "$KOTLIN_SRC/system/SonicDeckSections.kt"
    "$KOTLIN_SRC/system/SonicAppMixerSection.kt"
    "$KOTLIN_SRC/system/VistaDeckManager.kt"
    "$KOTLIN_SRC/system/hud/ExpandedBrightnessWellnessSection.kt"
)

echo "=== Lightspeed Theme Drift Guard ==="

# Check 1: Zero raw AlertDialog( or ExposedDropdownMenuBox( anywhere across entire codebase
UNAPPROVED_DIALOGS=$(grep -rnE "AlertDialog\(|ExposedDropdownMenuBox\(" "$KOTLIN_SRC" || true)
if [ -n "$UNAPPROVED_DIALOGS" ]; then
    echo "❌ ERROR: Found unapproved raw AlertDialog(/ExposedDropdownMenuBox( (must use DeckDialog / DeckDropdownMenu):"
    echo "$UNAPPROVED_DIALOGS"
    exit 1
fi
echo "✓ PASS: Zero raw AlertDialog( or ExposedDropdownMenuBox( across entire codebase"

# Check 2: Raw DropdownMenu( only inside DeckSurfaceModifier.kt; ModalBottomSheet( confined to allowlisted sheets
UNAPPROVED_DROPDOWNS=$(grep -rn "[^a-zA-Z]DropdownMenu(" "$KOTLIN_SRC" | grep -v "DeckSurfaceModifier.kt" || true)
if [ -n "$UNAPPROVED_DROPDOWNS" ]; then
    echo "❌ ERROR: Found unapproved raw DropdownMenu( outside DeckSurfaceModifier.kt (must use DeckDropdownMenu):"
    echo "$UNAPPROVED_DROPDOWNS"
    exit 1
fi
ALLOWLIST_SHEETS="GuidebookBottomSheet.kt|SidebarAttitudeSheet.kt"
UNAPPROVED_SHEETS=$(grep -rn "ModalBottomSheet(" "$KOTLIN_SRC" | grep -vE "$ALLOWLIST_SHEETS" || true)
if [ -n "$UNAPPROVED_SHEETS" ]; then
    echo "❌ ERROR: Found unapproved raw ModalBottomSheet( outside documented glass sheets:"
    echo "$UNAPPROVED_SHEETS"
    exit 1
fi
echo "✓ PASS: All dropdowns use DeckDropdownMenu and bottom sheets are strictly confined to glass sheets"

# Check 3: Raw Card( app-wide confined only to legacy settings coordinator containers & StyleLab
ALLOWLIST_CARDS="settings/(StyleLabPreviewDialog|SettingsContainerComponents|SettingsDialogComponents|GuidebookBottomSheet|WatchdogComponents|FlightControlDeckComponents)\.kt"
UNAPPROVED_CARDS=$(grep -rn "[^a-zA-Z]Card(" "$KOTLIN_SRC" | grep -vE "$ALLOWLIST_CARDS" || true)
if [ -n "$UNAPPROVED_CARDS" ]; then
    echo "❌ ERROR: Found unapproved raw Card( (Cockpit, Refueling, Flyouts, Widgets, Sonic/Vista Decks must use 12-Theme glass/Box):"
    echo "$UNAPPROVED_CARDS"
    exit 1
fi
echo "✓ PASS: Zero unapproved raw Card( across Cockpit, Refueling, Flyouts, Widgets, and 12-Theme Decks"

# Check 4: Sonic Deck, Vista Deck & Non-Settings UI Packages must use DeckSwitch / DeckSlider (zero raw Switch/Slider)
NON_SETTINGS_FILES=()
while IFS= read -r f; do
    NON_SETTINGS_FILES+=("$f")
done < <(find "$KOTLIN_SRC" -name "*.kt" ! -path "$SETTINGS_DIR/*" | sort)

TARGET_CONTROL_FILES=("${DECK_FILES[@]}" "${NON_SETTINGS_FILES[@]}")
UNAPPROVED_SWITCHES=$(grep -rn "[^a-zA-Z.]Switch(" "${TARGET_CONTROL_FILES[@]}" | grep -v "Compat(" || true)
if [ -n "$UNAPPROVED_SWITCHES" ]; then
    echo "❌ ERROR: Found unapproved raw Switch( in Decks or non-settings UI (must use DeckSwitch):"
    echo "$UNAPPROVED_SWITCHES"
    exit 1
fi
echo "✓ PASS: Zero raw Switch( in Sonic/Vista Decks, Cockpit, Refueling, Flyouts, or Widgets"

UNAPPROVED_SLIDERS=$(grep -rn "[^a-zA-Z.]Slider(" "${TARGET_CONTROL_FILES[@]}" || true)
if [ -n "$UNAPPROVED_SLIDERS" ]; then
    echo "❌ ERROR: Found unapproved raw Slider( in Decks or non-settings UI (must use DeckSlider):"
    echo "$UNAPPROVED_SLIDERS"
    exit 1
fi
echo "✓ PASS: Zero raw Slider( in Sonic/Vista Decks, Cockpit, Refueling, Flyouts, or Widgets"

echo "=== All theme drift guard checks passed successfully! ==="
exit 0
