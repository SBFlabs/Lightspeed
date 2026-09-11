with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

old_driver = """    fun startActiveSensorPortraitDriver(context: Context) {
        LightspeedOrientationEngine.setSensorPortrait(context)
        if (isSensorPortraitDriverActive) {"""

new_driver = """    fun startActiveSensorPortraitDriver(context: Context) {
        if (isSensorPortraitDriverActive) {"""

if old_driver in content:
    content = content.replace(old_driver, new_driver)
else:
    print("Failed to replace driver")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
