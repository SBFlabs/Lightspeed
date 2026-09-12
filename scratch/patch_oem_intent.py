import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

old_block = """                        // Unpack nested ShortcutMaker intent if present
                        val extraIntent = launchIntent.getStringExtra("extra_intent")"""

new_block = """                        // 0. Unpack OEM Dialer / Hidden Intents
                        if (launchIntent.hasExtra("shortcut_action")) {
                            launchIntent.action = launchIntent.getStringExtra("shortcut_action")
                            launchIntent.component = null
                        }

                        // Unpack nested ShortcutMaker intent if present
                        val extraIntent = launchIntent.getStringExtra("extra_intent")"""

content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
