import re

def fix(filepath):
    with open(filepath, "r") as f:
        content = f.read()

    # Revert the bad injection
    bad_str = ",\n            pillStyle = LightspeedPreferences.getDeflectorPillStyle(context),"
    bad_str2 = ",\n            pillStyle = LightspeedPreferences.getDeflectorPillStyle(this),"
    content = content.replace(bad_str, ",")
    content = content.replace(bad_str2, ",")
    
    # Now find the ACTUAL function call.
    # It looks like:
    # useM3Color = useM3Color,
    # Or
    # useM3Color = true,
    # But specifically inside drawDeflectorWing
    # Let's just do a manual string replace for the exact line in drawDeflectorWing.
    
    content = content.replace(
        "            useM3Color = useM3Color,\n",
        "            useM3Color = useM3Color,\n            pillStyle = com.sbf.lightspeed.system.LightspeedPreferences.getDeflectorPillStyle(context),\n"
    )
    # in LightspeedLeftWingOverlay it might use 'this' instead of 'context'
    if "LightspeedLeftWingOverlay" in filepath:
        content = content.replace("getDeflectorPillStyle(context)", "getDeflectorPillStyle(this)")

    with open(filepath, "w") as f:
        f.write(content)

fix("app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt")
fix("app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt")
print("Fixed calls")
