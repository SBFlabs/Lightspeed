import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "r") as f:
    content = f.read()

# Remove pinnedSubTop etc from memory and blueprint call
# Since I added them via patch earlier, I can just replace the whole blueprint call.

blueprint_call_regex = re.compile(r'subSections = mapOf\([^)]+\),\n\s*subSectionTitles = mapOf\([^)]+\),\n\s*pinnedSubSections = mapOf\([^)]+\),\n\s*onMoveSubUp = .*?onPinSubSection = .*?\}\n\s*\)', re.DOTALL)
# Actually, it's easier to just do string replacements.

call_old = """                            pinnedSubSections = mapOf(
                                "top" to pinnedSubTop,
                                "bottom" to pinnedSubBottom,
                                "unified" to pinnedSubUnified
                            ),"""
content = content.replace(call_old, "")

pin_logic_old = """                            onPinSubSection = { secId, subId ->
                                val key = when (secId) {
                                    "top" -> "pref_sub_pinned_top_2"
                                    "bottom" -> "pref_sub_pinned_bottom_2"
                                    "unified" -> "pref_sub_pinned_unified_2"
                                    else -> return@BlueprintWireframeView
                                }
                                when (secId) {
                                    "top" -> pinnedSubTop = subId
                                    "bottom" -> pinnedSubBottom = subId
                                    "unified" -> pinnedSubUnified = subId
                                }
                                prefs.edit().putString(key, subId).apply()
                            }"""
content = content.replace(pin_logic_old, "")

# Remove the state variables pinnedSubTop etc
content = content.replace('var pinnedSubTop by remember { mutableStateOf(prefs.getString("pref_sub_pinned_top_2", "geo")) }', '')
content = content.replace('var pinnedSubBottom by remember { mutableStateOf(prefs.getString("pref_sub_pinned_bottom_2", "geo")) }', '')
content = content.replace('var pinnedSubUnified by remember { mutableStateOf(prefs.getString("pref_sub_pinned_unified_2", "geo")) }', '')

# Ensure commas are fixed. 
# `onMoveSubDown = { ... }` is the last one now.
content = content.replace("prefs.edit().putString(key, mutable.joinToString(\",\")).apply()\n                                },", "prefs.edit().putString(key, mutable.joinToString(\",\")).apply()\n                                }")


with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "w") as f:
    f.write(content)

