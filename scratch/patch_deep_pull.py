import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_block = """                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            val dx = rawX - lastTouchRawX
                            val dy = rawY - lastTouchRawY
                            // Upward sweep (-dy > 0) or rightward sweep (dx > 0) increases level.
                            // Downward sweep (-dy < 0) or leftward sweep (dx < 0) decreases level.
                            val pixelDelta = if (abs(dx) >= abs(dy)) dx else -dy
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)
                        }"""

new_block = """                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            
                            val isVolume = activeHoldScrubAction == "scrub:volume" || activeHoldScrubAction == "system:volume"
                            val isBrightness = activeHoldScrubAction == "scrub:brightness" || activeHoldScrubAction == "system:brightness"
                            
                            val perpendicularPull = if (currentActiveZone == TouchZone.LEFT_EDGE) {
                                rawX - touchStartX
                            } else if (currentActiveZone == TouchZone.RIGHT_EDGE) {
                                touchStartX - rawX
                            } else {
                                touchStartY - rawY // TOP/BOTTOM EDGE
                            }
                            
                            if (perpendicularPull > 140f * density && (isVolume || isBrightness)) {
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
                            }
                            
                            val dx = rawX - lastTouchRawX
                            val dy = rawY - lastTouchRawY
                            // Upward sweep (-dy > 0) or rightward sweep (dx > 0) increases level.
                            // Downward sweep (-dy < 0) or leftward sweep (dx < 0) decreases level.
                            val pixelDelta = if (abs(dx) >= abs(dy)) dx else -dy
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)
                        }"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
