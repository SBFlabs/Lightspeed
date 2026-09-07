import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

imports_to_add = """import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi"""

content = content.replace("import androidx.compose.foundation.layout.*", imports_to_add)

with open(file_path, 'w') as f:
    f.write(content)

print("Imports fixed.")
