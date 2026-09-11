with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationEngine.kt", "r") as f:
    lines = f.readlines()

new_lines = []
imports = []
for line in lines:
    if line.startswith("import kotlinx.coroutines."):
        imports.append(line)
    else:
        new_lines.append(line)

package_idx = -1
for i, line in enumerate(new_lines):
    if line.startswith("package "):
        package_idx = i
        break

if package_idx != -1:
    for imp in reversed(imports):
        new_lines.insert(package_idx + 1, imp)

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationEngine.kt", "w") as f:
    f.writelines(new_lines)
