import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

old_colors = """        val dynamicPrimary = Color(0xFF6366F1)
        val dynamicSecondary = Color(0xFFEAB308)"""

new_colors = """        val dynamicPrimary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(service).primary else Color(0xFF6366F1)
        val dynamicSecondary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(service).secondary else Color(0xFFEAB308)"""

content = content.replace(old_colors, new_colors)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)

