import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_block = """                            val perpendicularPull = if (currentActiveZone == TouchZone.LEFT_EDGE) {
                                rawX - touchStartX
                            } else if (currentActiveZone == TouchZone.RIGHT_EDGE) {
                                touchStartX - rawX
                            } else {
                                touchStartY - rawY // TOP/BOTTOM EDGE
                            }
                            
                            if (perpendicularPull > 140f * density && (isVolume || isBrightness)) {"""

new_block = """                            val horizontalPull = kotlin.math.abs(rawX - gestureStartX)
                            if (horizontalPull > 140f * density && (isVolume || isBrightness)) {"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
