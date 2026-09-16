import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# Fix named argument error
old_row_call = """                                    AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, isPinned = app.pkg in pinnedSet, dynamicPrimary) { isPinned ->"""
new_row_call = """                                    AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, app.pkg in pinnedSet, dynamicPrimary) { isPinned ->"""
content = content.replace(old_row_call, new_row_call)

# Fix withContext error
old_io = """        LaunchedEffect(Unit) {
            kotlinx.coroutines.Dispatchers.IO.invoke {"""
new_io = """        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {"""
content = content.replace(old_io, new_io)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)

