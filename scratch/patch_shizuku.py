import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

# Fix for pinned shortcuts
old_quaternary = """                            if (targetIntent != null) {
                                val uriStr = targetIntent.toUri(Intent.URI_INTENT_SCHEME).replace("'", "'\\''")
                                ElevatedTaskCloser.execShizuku("am start '$uriStr'")
                                launched = true
                            }"""

new_quaternary = """                            if (targetIntent != null) {
                                ElevatedTaskCloser.execShizuku(intentToAmStartCommand(targetIntent))
                                launched = true
                            }"""

content = content.replace(old_quaternary, new_quaternary)

# Fix for intent tokens
old_intent_shizuku = """                        // 4. Elevated Shizuku fallback
                        if (ElevatedTaskCloser.isShizukuActive) {
                            try {
                                ElevatedTaskCloser.execShizuku("am start '${uri}' || am broadcast '${uri}'")
                                return true
                            } catch (_: Exception) {}
                        }"""

new_intent_shizuku = """                        // 4. Elevated Shizuku fallback
                        if (ElevatedTaskCloser.isShizukuActive) {
                            try {
                                ElevatedTaskCloser.execShizuku(intentToAmStartCommand(launchIntent))
                                return true
                            } catch (_: Exception) {}
                        }"""

content = content.replace(old_intent_shizuku, new_intent_shizuku)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
