import re
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# 1. Add heightIn(max = 700.dp) to the Box
old_box = ".fillMaxWidth(0.9f)"
new_box = ".fillMaxWidth(0.9f).heightIn(max = 750.dp)"
content = content.replace(old_box, new_box)

# 2. Add verticalScroll to the Column
old_col = """                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {"""
new_col = """                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp)
                    ) {"""
content = content.replace(old_col, new_col)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
