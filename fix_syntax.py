import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt"
with open(file_path, 'r') as f:
    content = f.read()

# Fix left deflector
bad_left = 'Flank Vector Zones (Upper "Left Deflector — Flank Vector Zones (Upper & Lower)" Lower)'
good_left = 'Flank Vector Zones (Upper & Lower)'
content = content.replace(bad_left, good_left)

# Fix right deflector
bad_right = 'Flank Vector Zones (Upper "Right Deflector — Flank Vector Zones (Upper & Lower)" Lower)'
good_right = 'Flank Vector Zones (Upper & Lower)'
content = content.replace(bad_right, good_right)

with open(file_path, 'w') as f:
    f.write(content)

print("Syntax fixed.")
