import re

files = [
    "app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt",
    "app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt",
    "app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt"
]

deflector_vars = [
    "com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)",
    "com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)"
]

for file in files:
    with open(file, "r") as f:
        content = f.read()

    for var_str in deflector_vars:
        content = content.replace(f'${{{var_str}}} — Central Pill (Core Zone)', 'Central Pill (Core Astrogation)')
        content = content.replace(f'${{{var_str}}} — Flank Vector Zones (Upper & Lower)', 'Unified Deflectors (Flank Vectors)')
        content = content.replace(f'${{{var_str}}} — Upper Vector Zone', 'Upper Vector Zone')
        content = content.replace(f'${{{var_str}}} — Lower Vector Zone', 'Lower Vector Zone')

    with open(file, "w") as f:
        f.write(content)

print("Names updated")
