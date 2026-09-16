import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

# Let's see the transition from SWIPE to SCRUBBING
match = re.search(r'if \((currentDetectedGesture == MacroGesture\.SWIPE_LEFT.*?)\).*?\{.*?currentDetectedGesture = MacroGesture\.SCRUBBING.*?', content, re.DOTALL)
if match:
    print("Found SCRUB transition")
else:
    print("Not found SCRUB transition")
