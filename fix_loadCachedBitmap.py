import re

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt", "r") as f:
    content = f.read()

def replacer(match):
    return match.group(0).replace("if (useCache) bitmapCache.put(token, bmp)", "bitmapCache.put(token, bmp)")

content = re.sub(r'fun loadCachedBitmap.*?return null\n    \}', replacer, content, flags=re.DOTALL)

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt", "w") as f:
    f.write(content)
