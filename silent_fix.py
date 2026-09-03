import re
with open('/home/Sherif/Lightspeed/app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarComponents.kt', 'r') as f:
    comp_text = f.read()
comp_text = comp_text.replace('@Composable\n\n@Composable', '@Composable')
with open('/home/Sherif/Lightspeed/app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarComponents.kt', 'w') as f:
    f.write(comp_text)

with open('/home/Sherif/Lightspeed/app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarMatrixConfig.kt', 'r') as f:
    conf_text = f.read()

if 'import androidx.lifecycle.viewmodel.compose.viewModel' not in conf_text:
    conf_text = conf_text.replace('import androidx.compose.runtime.Composable', 'import androidx.compose.runtime.Composable\nimport androidx.lifecycle.viewmodel.compose.viewModel\nimport androidx.compose.runtime.collectAsState')

with open('/home/Sherif/Lightspeed/app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarMatrixConfig.kt', 'w') as f:
    f.write(conf_text)
