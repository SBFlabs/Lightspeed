with open("app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt", "r") as f:
    content = f.read()

# find:
#         isCurrentlyTouched = true,
#         activeZoneIsCenter = true,
#         activeZoneIsTop = false,
#         activeZoneIsBottom = false,
#         glowFraction = 1f,

# and replace it with dynamic state
old = """        isCurrentlyTouched = true,
        activeZoneIsCenter = true,
        activeZoneIsTop = false,
        activeZoneIsBottom = false,
        glowFraction = 1f,"""

new = """        isCurrentlyTouched = isCurrentlyTouched,
        activeZoneIsCenter = true,
        activeZoneIsTop = false,
        activeZoneIsBottom = false,
        glowFraction = glowFraction,"""

if old in content:
    content = content.replace(old, new)
else:
    print("Could not find old text in LightspeedCruiseOverlayDraw.kt")

with open("app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayDraw.kt", "w") as f:
    f.write(content)
