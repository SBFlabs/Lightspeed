import re

# Fix LightspeedCruiseOverlayTouch.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_axis = """                            // Axis Lock: Side deflectors ALWAYS use Y-axis. Top/Bottom bars ALWAYS use X-axis.
                            val pixelDelta = if (currentActiveZone == TouchZone.TOP_EDGE || currentActiveZone == TouchZone.BOTTOM_EDGE) {
                                dx
                            } else {
                                -dy
                            }"""
new_axis = """                            // Axis Lock: Lightspeed side deflectors ALWAYS use Y-axis.
                            val pixelDelta = -dy"""
content = content.replace(old_axis, new_axis)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)

# Fix LightspeedSensorDeckTouchOverlay.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedSensorDeckTouchOverlay.kt', 'r') as f:
    content2 = f.read()

old_sensor_axis = """    private fun handleScrubMotion(rawDx: Float, rawDy: Float) {
        // Allow scrubbing both sideways (rawDx: right is +) and up/down (-rawDy: up is +) based on dominant movement
        val delta = if (abs(rawDx) >= abs(rawDy)) rawDx else -rawDy"""
new_sensor_axis = """    private fun handleScrubMotion(rawDx: Float, rawDy: Float) {
        // Axis Lock: Status Bar Sensor Deck ALWAYS uses X-axis.
        val delta = rawDx"""
content2 = content2.replace(old_sensor_axis, new_sensor_axis)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedSensorDeckTouchOverlay.kt', 'w') as f:
    f.write(content2)

