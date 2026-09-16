import re

files = [
    "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedIconManager.kt",
    "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt"
]

for file in files:
    with open(file, 'r') as f:
        content = f.read()
    
    # Check cache at beginning
    content = re.sub(r'drawableCache\.get\(([^)]+)\)\?\.let\s*\{', r'if (useCache) drawableCache.get(\1)?.let {', content)
    content = re.sub(r'bitmapCache\.get\(([^)]+)\)\?\.let\s*\{\s*return it\s*\}', r'if (useCache) bitmapCache.get(\1)?.let { return it }', content)
    
    # Putting to cache
    content = re.sub(r'drawableCache\.put\(([^,]+),\s*([^)]+)\)', r'if (useCache) drawableCache.put(\1, \2)', content)
    content = re.sub(r'bitmapCache\.put\(([^,]+),\s*([^)]+)\)', r'if (useCache) bitmapCache.put(\1, \2)', content)
    
    with open(file, 'w') as f:
        f.write(content)
