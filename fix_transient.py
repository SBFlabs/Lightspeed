import re

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

# Transient 360
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)\n        LightspeedOrientationEngine.forceSensor360(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)")

# Transient Landscape
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)\n        LightspeedOrientationEngine.forceLandscape(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)")

# Transient Portrait
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)\n        LightspeedOrientationEngine.forcePortrait(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
