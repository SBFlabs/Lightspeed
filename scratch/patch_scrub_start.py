import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'r') as f:
    content = f.read()

old_block = """            if (actionValue == "system:volume" || actionValue == "system:brightness" || actionValue == "system:screen_timeout" || actionValue == "scrub:volume" || actionValue == "scrub:brightness") {
                currentDetectedGesture = MacroGesture.SCRUBBING
                activeHoldScrubAction = actionValue
                activeHoldScrubActionKey = actionKey
                aggregateScrubAccumulator = 0f
                isScrubEntranceHapticFired = true"""
new_block = """            if (actionValue == "system:volume" || actionValue == "system:brightness" || actionValue == "system:screen_timeout" || actionValue == "scrub:volume" || actionValue == "scrub:brightness") {
                currentDetectedGesture = MacroGesture.SCRUBBING
                activeHoldScrubAction = actionValue
                activeHoldScrubActionKey = actionKey
                aggregateScrubAccumulator = 0f
                scrubStartX = lastTouchRawX
                isScrubEntranceHapticFired = true"""
content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'w') as f:
    f.write(content)
