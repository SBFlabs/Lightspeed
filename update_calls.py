import re
import os

def update_file(filepath):
    with open(filepath, "r") as f:
        content = f.read()

    # Look for the useM3Color parameter to inject pillStyle right after
    content = re.sub(
        r'(useM3Color\s*=\s*[^,]+),',
        r'\1,\n            pillStyle = LightspeedPreferences.getDeflectorPillStyle(context),',
        content
    )
    # If the context is `context` or `this`, handled.
    # In LightspeedLeftWingOverlay, context is `this` 
    
    with open(filepath, "w") as f:
        f.write(content)

update_file("app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt")
update_file("app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt")
print("Calls updated")
