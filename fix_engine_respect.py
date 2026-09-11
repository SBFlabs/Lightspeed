with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

target = """    fun evaluateGravityCascade(context: Context, isLocked: Boolean? = null, foregroundPackage: String? = null) {
        val targetPackage = foregroundPackage ?: lastForegroundPackage

        // Priority 1: Runtime Manual Gesture Override (Instant user veto if enabled)"""

new_guard = """    fun evaluateGravityCascade(context: Context, isLocked: Boolean? = null, foregroundPackage: String? = null) {
        val prefs = context.defaultPrefs()
        if (!prefs.getBoolean("pref_synthetic_gravity_enabled", true)) {
            stopActiveSensorPortraitDriver()
            LightspeedAccessibilityService.instance?.updateForcedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
            return
        }

        val targetPackage = foregroundPackage ?: lastForegroundPackage

        // Priority 1: Runtime Manual Gesture Override (Instant user veto if enabled)"""

if target in content:
    content = content.replace(target, new_guard)
else:
    print("Could not find target in manager")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
