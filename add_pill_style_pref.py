import re

file_path = "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedPreferences.kt"
with open(file_path, "r") as f:
    content = f.read()

pref_decl = 'const val KEY_DEFLECTOR_GLOW_STYLE = "pref_deflector_glow_style" // "progressive_frost", "material_shade", "crimson_reactor", "cyber_plasma"\n    const val KEY_DEFLECTOR_PILL_STYLE = "pref_deflector_pill_style" // "anchored_glow", "floating_smart_pill", "neon_core", "razor_edge", "kinetic_elastic", "hollow_ghost"'
content = content.replace('const val KEY_DEFLECTOR_GLOW_STYLE = "pref_deflector_glow_style" // "progressive_frost", "material_shade", "crimson_reactor", "cyber_plasma"', pref_decl)

pref_func = '''    fun getDeflectorGlowStyle(context: Context): String =
        getPrefs(context).getString(KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"

    fun getDeflectorPillStyle(context: Context): String =
        getPrefs(context).getString(KEY_DEFLECTOR_PILL_STYLE, "anchored_glow") ?: "anchored_glow"

    fun setDeflectorPillStyle(context: Context, style: String) =
        getPrefs(context).edit().putString(KEY_DEFLECTOR_PILL_STYLE, style).apply()'''

content = content.replace('    fun getDeflectorGlowStyle(context: Context): String =\n        getPrefs(context).getString(KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"', pref_func)

with open(file_path, "w") as f:
    f.write(content)
print("Updated LightspeedPreferences.kt")
