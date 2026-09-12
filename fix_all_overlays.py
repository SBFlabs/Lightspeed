import re

# LeftWingOverlay
f1 = "app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt"
with open(f1, "r") as f:
    c = f.read()
c = c.replace("getDeflectorPillStyle(context)", "getDeflectorPillStyle(context, true)")
with open(f1, "w") as f:
    f.write(c)

# CruiseOverlayDraw
f2 = "app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt"
with open(f2, "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "getDeflectorPillStyle(context)" in line:
        if i < 300: # First one around line 119
            lines[i] = line.replace("getDeflectorPillStyle(context)", "getDeflectorPillStyle(context, false)")
        else: # Second one around line 549
            lines[i] = line.replace("getDeflectorPillStyle(context)", "getDeflectorPillStyle(context, isLeft)")

with open(f2, "w") as f:
    f.writelines(lines)
    
print("Updated overlays")
