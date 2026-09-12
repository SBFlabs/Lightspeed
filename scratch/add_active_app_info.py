import re
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

data_class = """
data class ActiveAppInfo(val pkg: String, val name: String, val icon: android.graphics.drawable.Drawable?)
"""
if 'data class ActiveAppInfo' not in content:
    content += data_class

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
