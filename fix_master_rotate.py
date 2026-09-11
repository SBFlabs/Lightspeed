with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "r") as f:
    content = f.read()

old1 = """        if (newBaseline) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
            LightspeedOrientationEngine.forcePortrait(context)
        }"""

new1 = """        if (newBaseline) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
        }"""

old2 = """        val masterAutoRotate = LightspeedOrientationEngine.getMasterAutoRotateBaseline(context)
        if (masterAutoRotate) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
            LightspeedOrientationEngine.forcePortrait(context)
        }"""

new2 = """        val masterAutoRotate = LightspeedOrientationEngine.getMasterAutoRotateBaseline(context)
        if (masterAutoRotate) {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, true)
        } else {
            LightspeedOrientationEngine.setAutoRotateEnabled(context, false)
        }"""

content = content.replace(old1, new1)
content = content.replace(old2, new2)

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationManager.kt", "w") as f:
    f.write(content)
