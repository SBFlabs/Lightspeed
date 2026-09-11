with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

old = """    fun stopActiveSensorPortraitDriver() {
        if (!isSensorPortraitDriverActive) return
        isSensorPortraitDriverActive = false
        try {
            sensorPortraitListener?.disable()
            Log.i(TAG, "Disabled Active Sensor Portrait Driver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable OrientationEventListener", e)
        }
    }"""

new = """    fun stopActiveSensorPortraitDriver() {
        if (!isSensorPortraitDriverActive) return
        isSensorPortraitDriverActive = false
        currentSensorPortraitOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        try {
            sensorPortraitListener?.disable()
            Log.i(TAG, "Disabled Active Sensor Portrait Driver")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable OrientationEventListener", e)
        }
    }"""

if old in content:
    content = content.replace(old, new)
else:
    print("Could not find stopActiveSensorPortraitDriver")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
