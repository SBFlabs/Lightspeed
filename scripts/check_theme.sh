#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS_DIR="$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/settings"
DECK_FILES=(
    "$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/system/SonicDeckManager.kt"
    "$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/system/SonicDeckSections.kt"
    "$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/system/SonicAppMixerSection.kt"
    "$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/system/VistaDeckManager.kt"
    "$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/system/hud/ExpandedBrightnessWellnessSection.kt"
)

echo "=== Lightspeed Theme Drift Guard ==="

# Check 1: Raw Color(0x...) in settings/ outside allow-list (StyleLab*, DeckOpticalCacheBuilder)
RAW_COLORS=$(grep -rn "Color(0x" "$SETTINGS_DIR" | grep -v "StyleLab\|DeckOpticalCacheBuilder" || true)

if [ -n "$RAW_COLORS" ]; then
    echo "❌ ERROR: Found raw Color(0x...) in settings/ outside allow-list:"
    echo "$RAW_COLORS"
    exit 1
fi
echo "✓ PASS: Zero raw Color(0x...) in settings/ outside StyleLab / DeckOpticalCacheBuilder"

# Check 2: Raw AlertDialog( in settings/ outside DeckControls.kt and LiquidGlass theme editor
ALLOWLIST_DIALOGS="DeckControls.kt|LiquidGlass"
UNAPPROVED_DIALOGS=$(grep -rn "AlertDialog(" "$SETTINGS_DIR" | grep -vE "$ALLOWLIST_DIALOGS" || true)

if [ -n "$UNAPPROVED_DIALOGS" ]; then
    echo "❌ ERROR: Found unapproved raw AlertDialog( in settings/:"
    echo "$UNAPPROVED_DIALOGS"
    exit 1
fi
echo "✓ PASS: All AlertDialog instances in settings/ are strictly confined to DeckControls.kt or LiquidGlass theme editor"

# Check 3: Raw Card( in settings/ outside DeckControls.kt, StyleLab, and documented pending coordinator containers
ALLOWLIST_CARDS="DeckControls.kt|StyleLab|SettingsContainerComponents.kt|SettingsDialogComponents.kt|GuidebookBottomSheet.kt|WatchdogComponents.kt|FlightControlDeckComponents.kt"
UNAPPROVED_CARDS=$(grep -rn "[^a-zA-Z]Card(" "$SETTINGS_DIR" | grep -vE "$ALLOWLIST_CARDS" || true)

if [ -n "$UNAPPROVED_CARDS" ]; then
    echo "❌ ERROR: Found unapproved raw Card( in settings/:"
    echo "$UNAPPROVED_CARDS"
    exit 1
fi
echo "✓ PASS: All Card instances in settings/ are strictly confined to DeckControls.kt, StyleLab, or documented coordinator containers"

# Check 4: Sonic Deck, Vista Deck & ExpandedBrightnessWellnessSection 12-Theme Compliance
UNAPPROVED_DECK_CARDS=$(grep -rn "[^a-zA-Z]Card(" "${DECK_FILES[@]}" || true)
if [ -n "$UNAPPROVED_DECK_CARDS" ]; then
    echo "❌ ERROR: Found unapproved raw Card( in Sonic/Vista Decks:"
    echo "$UNAPPROVED_DECK_CARDS"
    exit 1
fi
echo "✓ PASS: Sonic & Vista Decks strictly use 12-Theme optical glass containers (zero raw Card)"

UNAPPROVED_DECK_SWITCHES=$(grep -rn "[^a-zA-Z]Switch(" "${DECK_FILES[@]}" || true)
if [ -n "$UNAPPROVED_DECK_SWITCHES" ]; then
    echo "❌ ERROR: Found unapproved raw Switch( in Sonic/Vista Decks (must use DeckSwitch):"
    echo "$UNAPPROVED_DECK_SWITCHES"
    exit 1
fi
echo "✓ PASS: Sonic & Vista Decks strictly use DeckSwitch for active theme consistency"

UNAPPROVED_DECK_SLIDERS=$(grep -rn "[^a-zA-Z]Slider(" "${DECK_FILES[@]}" || true)
if [ -n "$UNAPPROVED_DECK_SLIDERS" ]; then
    echo "❌ ERROR: Found unapproved raw Slider( in Sonic/Vista Decks (must use DeckSlider):"
    echo "$UNAPPROVED_DECK_SLIDERS"
    exit 1
fi
echo "✓ PASS: Sonic & Vista Decks strictly use DeckSlider for active theme consistency"

echo "=== All theme drift guard checks passed successfully! ==="
exit 0
