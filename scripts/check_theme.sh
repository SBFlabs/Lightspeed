#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SETTINGS_DIR="$ROOT_DIR/app/src/main/kotlin/com/sbf/lightspeed/settings"

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

echo "=== All theme drift guard checks passed successfully! ==="
exit 0
