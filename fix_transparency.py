with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedDeflectorRenderer.kt", "r") as f:
    content = f.read()

old = """            val restingAlpha = if (centerTransparency > 0) {
                (centerTransparency * 2.55f).toInt().coerceIn(15, 255)
            } else {
                75 // Clean resting baseline presence
            }"""

new = """            val restingAlpha = (centerTransparency * 2.55f).toInt().coerceIn(0, 255)"""

content = content.replace(old, new)

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedDeflectorRenderer.kt", "w") as f:
    f.write(content)
