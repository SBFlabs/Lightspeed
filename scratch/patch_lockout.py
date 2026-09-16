import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_block = """                            if (horizontalPull > 140f * density && (isVolume || isBrightness)) {
                                currentDetectedGesture = MacroGesture.NONE
                                isCruising = false
                                LightspeedStatusBarOverlay.dismissActionHud(0L)
                                val actionStr = if (isVolume) "com.sbf.lightspeed.OMNISCIENT_AUDIO" else "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                val intent = android.content.Intent(actionStr).apply {
                                    setPackage(context.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                return true
                            }"""

new_block = """                            if (horizontalPull > 140f * density && (isVolume || isBrightness)) {
                                currentDetectedGesture = MacroGesture.NONE
                                macroTrackingActive = false // Portal Line Lock: Prevent re-triggering until finger lifts
                                isCruising = false
                                LightspeedStatusBarOverlay.dismissActionHud(0L)
                                val actionStr = if (isVolume) "com.sbf.lightspeed.OMNISCIENT_AUDIO" else "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                val intent = android.content.Intent(actionStr).apply {
                                    setPackage(context.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                return true
                            }"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
