import re

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

# Remove system writes from Priority 2 (Keyguard)
old_p2 = """        // Priority 2: Keyguard Lock Screen (Explicit assignment if user configured keyguard:lockscreen)
        if (locked) {
            val lockscreenToken = "keyguard:lockscreen"
            when {
                strictPortraitApps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    LightspeedOrientationEngine.forcePortrait(context)
                    return
                }
                sensorPortraitApps.contains(lockscreenToken) -> {
                    startActiveSensorPortraitDriver(context)
                    return
                }
                sensorLandscapeApps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    LightspeedOrientationEngine.forceLandscape(context)
                    return
                }
                sensor360Apps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                    LightspeedOrientationEngine.forceSensor360(context)
                    return
                }
            }
        }"""
new_p2 = """        // Priority 2: Keyguard Lock Screen (Explicit assignment if user configured keyguard:lockscreen)
        if (locked) {
            val lockscreenToken = "keyguard:lockscreen"
            when {
                strictPortraitApps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    return
                }
                sensorPortraitApps.contains(lockscreenToken) -> {
                    startActiveSensorPortraitDriver(context)
                    return
                }
                sensorLandscapeApps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    return
                }
                sensor360Apps.contains(lockscreenToken) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                    return
                }
            }
        }"""

# Remove system writes from Priority 3 (Per-App)
old_p3 = """        // Priority 3: Per-App Launch Rules (Enforced on foreground switch)
        val resolvedPackage = if (!locked && (targetPackage.isNullOrBlank() || targetPackage == "com.android.systemui" || targetPackage == "android")) {
            LightspeedOrientationEngine.getDefaultLauncherPackage(context)
        } else {
            targetPackage
        }
        if (!resolvedPackage.isNullOrBlank()) {
            when {
                strictPortraitApps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    LightspeedOrientationEngine.forcePortrait(context)
                    return
                }
                sensorPortraitApps.contains(resolvedPackage) -> {
                    startActiveSensorPortraitDriver(context)
                    return
                }
                sensorLandscapeApps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    LightspeedOrientationEngine.forceLandscape(context)
                    return
                }
                sensor360Apps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                    LightspeedOrientationEngine.forceSensor360(context)
                    return
                }
            }
        }"""

new_p3 = """        // Priority 3: Per-App Launch Rules (Enforced on foreground switch)
        val resolvedPackage = if (!locked && (targetPackage.isNullOrBlank() || targetPackage == "com.android.systemui" || targetPackage == "android")) {
            LightspeedOrientationEngine.getDefaultLauncherPackage(context)
        } else {
            targetPackage
        }
        if (!resolvedPackage.isNullOrBlank()) {
            when {
                strictPortraitApps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                    return
                }
                sensorPortraitApps.contains(resolvedPackage) -> {
                    startActiveSensorPortraitDriver(context)
                    return
                }
                sensorLandscapeApps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                    return
                }
                sensor360Apps.contains(resolvedPackage) -> {
                    stopActiveSensorPortraitDriver()
                    LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
                    return
                }
            }
        }"""

# Remove redundant system writes from Priority 4
old_p4 = """        // Priority 4: User's Master Auto-Rotate (Fallback for unassigned apps / native)
        stopActiveSensorPortraitDriver()
        LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
        val masterAutoRotate = LightspeedOrientationEngine.getMasterAutoRotateBaseline(context)
        if (masterAutoRotate) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
        }"""

new_p4 = """        // Priority 4: User's Master Auto-Rotate (Fallback for unassigned apps / native)
        stopActiveSensorPortraitDriver()
        LightspeedAccessibilityService.instance?.updateForcedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)"""

if old_p2 in content:
    content = content.replace(old_p2, new_p2)
else:
    print("Failed to replace p2")

if old_p3 in content:
    content = content.replace(old_p3, new_p3)
else:
    print("Failed to replace p3")

if old_p4 in content:
    content = content.replace(old_p4, new_p4)
else:
    print("Failed to replace p4")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
