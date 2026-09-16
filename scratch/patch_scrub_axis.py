import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_delta = """                            val dx = rawX - lastTouchRawX
                            val dy = rawY - lastTouchRawY
                            // Upward sweep (-dy > 0) or rightward sweep (dx > 0) increases level.
                            // Downward sweep (-dy < 0) or leftward sweep (dx < 0) decreases level.
                            val pixelDelta = if (abs(dx) >= abs(dy)) dx else -dy
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)"""

new_delta = """                            val dx = rawX - lastTouchRawX
                            val dy = rawY - lastTouchRawY
                            // Axis Lock: Side deflectors ALWAYS use Y-axis. Top/Bottom bars ALWAYS use X-axis.
                            val pixelDelta = if (currentActiveZone == TouchZone.TOP_EDGE || currentActiveZone == TouchZone.BOTTOM_EDGE) {
                                dx
                            } else {
                                -dy
                            }
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)"""

content = content.replace(old_delta, new_delta)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
