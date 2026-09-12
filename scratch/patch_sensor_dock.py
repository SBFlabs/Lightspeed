import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedSensorDeckTouchOverlay.kt', 'r') as f:
    content = f.read()

# Find handleScrubMotion call and add Deep Pull
old_scrub = """                if (isScrubbing) {
                    val rawDx = event.rawX - lastScrubRawX
                    val rawDy = event.rawY - lastScrubRawY
                    handleScrubMotion(rawDx, rawDy)
                    lastScrubRawX = event.rawX
                    lastScrubRawY = event.rawY
                    return true
                }"""

new_scrub = """                if (isScrubbing) {
                    val rawDx = event.rawX - lastScrubRawX
                    val rawDy = event.rawY - lastScrubRawY
                    
                    val isVolume = activeScrubType == "system:volume"
                    val isBrightness = activeScrubType == "system:brightness"
                    val verticalPull = kotlin.math.abs(event.rawY - startY)
                    
                    if (verticalPull > 80f * density && (isVolume || isBrightness)) {
                        isScrubbing = false
                        activeScrubType = "none"
                        activeScrubActionKey = null
                        com.sbf.lightspeed.LightspeedStatusBarOverlay.dismissActionHud(0L)
                        if (isVolume) {
                            com.sbf.lightspeed.system.OmniscientAudioDockManager.show(service)
                        } else {
                            val intent = android.content.Intent("com.sbf.lightspeed.OMNISCIENT_DISPLAY").apply {
                                setPackage(context.packageName)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                            }
                            try { context.startActivity(intent) } catch (e: Exception) {}
                        }
                        return true
                    }

                    handleScrubMotion(rawDx, rawDy)
                    lastScrubRawX = event.rawX
                    lastScrubRawY = event.rawY
                    return true
                }"""
content = content.replace(old_scrub, new_scrub)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedSensorDeckTouchOverlay.kt', 'w') as f:
    f.write(content)

