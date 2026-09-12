import re

with open('app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt', 'r') as f:
    content = f.read()

# 1. Update Shizuku state tracking
old_shizuku_state = "    val isShizukuActive = ElevatedTaskCloser.isShizukuActive"
new_shizuku_state = """    var isShizukuActive by remember { mutableStateOf(ElevatedTaskCloser.isShizukuActive) }
    
    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnBinderReceivedListener {
            isShizukuActive = ElevatedTaskCloser.isShizukuActive
        }
        val deadListener = rikka.shizuku.Shizuku.OnBinderDeadListener {
            isShizukuActive = false
        }
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(listener)
            rikka.shizuku.Shizuku.addBinderDeadListener(deadListener)
        } catch (_: Exception) {}
        
        onDispose {
            try {
                rikka.shizuku.Shizuku.removeBinderReceivedListener(listener)
                rikka.shizuku.Shizuku.removeBinderDeadListener(deadListener)
            } catch (_: Exception) {}
        }
    }"""
content = content.replace(old_shizuku_state, new_shizuku_state)

# 2. Remove Lock-Screen Shortcuts accordion
old_lock_screen = """        OverrideSettingRow(
            icon = Icons.Default.Lock,
            title = "Lock-Screen Shortcuts",
            subtitle = "Remapping left/right lockscreen shortcuts directly via secure settings",
            isChecked = false,
            isEnabled = isShizukuActive,
            onCheckedChange = {
                // Not yet implemented
            }
        )"""
content = content.replace(old_lock_screen, "")

with open('app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt', 'w') as f:
    f.write(content)
