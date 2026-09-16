with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    lines = f.readlines()

depth = 0
for i, line in enumerate(lines):
    for char in line:
        if char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
    if depth < 0:
        print(f"Negative depth at line {i+1}: {line}")
        break

print(f"Final depth: {depth}")
