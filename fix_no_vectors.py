import re

files = [
    "app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt",
    "app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt",
    "app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt"
]

for file in files:
    with open(file, "r") as f:
        content = f.read()

    content = content.replace("Unified Deflectors (Flank Vectors)", "Unified Deflectors")
    content = content.replace("Upper Vector Zone", "Upper Deflector Zone")
    content = content.replace("Lower Vector Zone", "Lower Deflector Zone")

    with open(file, "w") as f:
        f.write(content)

print("Names updated without vectors")
