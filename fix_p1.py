with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

# Priority 1 Portrait
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)\n                        LightspeedOrientationEngine.forcePortrait(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)")

# Priority 1 Landscape
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)\n                        LightspeedOrientationEngine.forceLandscape(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)")

# Priority 1 360
content = content.replace("LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)\n                        LightspeedOrientationEngine.forceSensor360(context)", "LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
