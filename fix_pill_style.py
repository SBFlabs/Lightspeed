import re

file_path = "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedPreferences.kt"
with open(file_path, "r") as f:
    content = f.read()

# Add keys
content = content.replace(
    'const val KEY_DEFLECTOR_PILL_STYLE = "pref_deflector_pill_style"',
    'const val KEY_DEFLECTOR_LEFT_PILL_STYLE = "pref_deflector_left_pill_style"\n    const val KEY_DEFLECTOR_RIGHT_PILL_STYLE = "pref_deflector_right_pill_style"\n    const val KEY_DEFLECTOR_PILL_STYLE = "pref_deflector_pill_style"'
)

func_replacement = """    fun getDeflectorPillStyle(context: Context, isLeft: Boolean): String {
        val key = if (isLeft) KEY_DEFLECTOR_LEFT_PILL_STYLE else KEY_DEFLECTOR_RIGHT_PILL_STYLE
        val fallback = context.defaultPrefs().getString(KEY_DEFLECTOR_PILL_STYLE, "anchored_glow") ?: "anchored_glow"
        return context.defaultPrefs().getString(key, fallback) ?: fallback
    }

    fun setDeflectorPillStyle(context: Context, isLeft: Boolean, style: String) {
        val key = if (isLeft) KEY_DEFLECTOR_LEFT_PILL_STYLE else KEY_DEFLECTOR_RIGHT_PILL_STYLE
        context.defaultPrefs().edit().putString(key, style).apply()
    }"""

# Replace the single arg functions
content = re.sub(
    r'fun getDeflectorPillStyle\(context: Context\): String =.*?apply\(\)\n    }',
    func_replacement,
    content,
    flags=re.DOTALL
)

with open(file_path, "w") as f:
    f.write(content)
print("Updated LightspeedPreferences.kt")
