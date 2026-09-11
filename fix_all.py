import re
import os

# Fix LightspeedPreferences.kt
file_path = "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedPreferences.kt"
with open(file_path, "r") as f:
    content = f.read()

pref_func = """    fun getDeflectorGlowStyle(context: Context): String =
        context.defaultPrefs().getString(KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"

    fun getDeflectorPillStyle(context: Context): String =
        context.defaultPrefs().getString(KEY_DEFLECTOR_PILL_STYLE, "anchored_glow") ?: "anchored_glow"

    fun setDeflectorPillStyle(context: Context, style: String) {
        context.defaultPrefs().edit().putString(KEY_DEFLECTOR_PILL_STYLE, style).apply()
    }"""

content = content.replace('    fun getDeflectorGlowStyle(context: Context): String =\n        context.defaultPrefs().getString(KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"', pref_func)
with open(file_path, "w") as f:
    f.write(content)

# Fix imports in LightspeedCruiseOverlayDraw.kt and LightspeedLeftWingOverlay.kt
def fix_import(filepath):
    with open(filepath, "r") as f:
        lines = f.readlines()
    if lines and lines[0].startswith("import com.sbf.lightspeed.system.LightspeedPreferences"):
        lines.pop(0) # remove it from line 1
        # find where to insert (after package decl)
        for i, line in enumerate(lines):
            if line.startswith("package "):
                lines.insert(i + 1, "import com.sbf.lightspeed.system.LightspeedPreferences\n")
                break
    with open(filepath, "w") as f:
        f.writelines(lines)

fix_import("app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt")
fix_import("app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt")
fix_import("app/src/main/kotlin/com/sbf/lightspeed/settings/DeflectorComponents.kt") # just in case I messed this up? No, DeflectorComponents wasn't modified with sed.

print("Fixes applied")
