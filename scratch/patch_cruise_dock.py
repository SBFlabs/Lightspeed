import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_call = """                                val actionStr = if (isVolume) "com.sbf.lightspeed.OMNISCIENT_AUDIO" else "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                val intent = android.content.Intent(actionStr).apply {
                                    setPackage(context.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}"""

new_call = """                                if (isVolume) {
                                    service?.let { com.sbf.lightspeed.system.OmniscientAudioDockManager.show(it) }
                                } else {
                                    val actionStr = "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                    val intent = android.content.Intent(actionStr).apply {
                                        setPackage(context.packageName)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }"""
content = content.replace(old_call, new_call)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)

