import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

old_state = """    var pinned by remember { mutableStateOf(isPinned) }
    var isCurrentlyMuted by remember { mutableStateOf(initialVol == 0f) }"""
new_state = """    var pinned by remember { mutableStateOf(isPinned) }
    var isCurrentlyMuted by remember { mutableStateOf(initialVol == 0f) }
    var focusDenied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(pkg) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            focusDenied = com.sbf.lightspeed.system.LightspeedAppSovereigntyEngine.isAudioFocusDenied(pkg)
        }
    }"""
content = content.replace(old_state, new_state)

old_buttons = """        IconButton(
            onClick = { 
                pinned = !pinned 
                onPinToggled(pinned)
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }"""
new_buttons = """        IconButton(
            onClick = { 
                val newDenied = !focusDenied
                focusDenied = newDenied
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    com.sbf.lightspeed.system.LightspeedAppSovereigntyEngine.setAudioFocusDenied(pkg, newDenied)
                }
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (focusDenied) Color(0xFFEF4444).copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.HearingDisabled, contentDescription = "Strip Audio Focus", tint = if (focusDenied) Color(0xFFEF4444) else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { 
                pinned = !pinned 
                onPinToggled(pinned)
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.PushPin, contentDescription = "Pin to Dock", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }"""
content = content.replace(old_buttons, new_buttons)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
