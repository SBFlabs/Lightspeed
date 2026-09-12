import re

file_path = "app/src/main/kotlin/com/sbf/lightspeed/settings/DeflectorComponents.kt"
with open(file_path, "r") as f:
    content = f.read()

# Fix the definitions
content = content.replace("LightspeedPreferences.getDeflectorPillStyle(context)", "LightspeedPreferences.getDeflectorPillStyle(context, isLeft)")
content = content.replace("LightspeedPreferences.setDeflectorPillStyle(context, id)", "LightspeedPreferences.setDeflectorPillStyle(context, isLeft, id)")

with open(file_path, "w") as f:
    f.write(content)
print("Updated DeflectorComponents.kt")
