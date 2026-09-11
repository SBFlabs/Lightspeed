import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", "r") as f:
    content = f.read()

call_old = """                            pinnedSubSections = mapOf(
                                "left_top" to pinnedSubLeftTop,
                                "left_bottom" to pinnedSubLeftBottom,
                                "left_unified" to pinnedSubLeftUnified
                            ),"""
content = content.replace(call_old, "")

pin_logic_old = """                            onPinSubSection = { secId, subId ->
                                val key = when (secId) {
                                    "left_top" -> "pref_sub_pinned_left_top_2"
                                    "left_bottom" -> "pref_sub_pinned_left_bottom_2"
                                    "left_unified" -> "pref_sub_pinned_left_unified_2"
                                    else -> return@BlueprintWireframeView
                                }
                                when (secId) {
                                    "left_top" -> pinnedSubLeftTop = subId
                                    "left_bottom" -> pinnedSubLeftBottom = subId
                                    "left_unified" -> pinnedSubLeftUnified = subId
                                }
                                prefs.edit().putString(key, subId).apply()
                            }"""
content = content.replace(pin_logic_old, "")

# Remove the state variables pinnedSubTop etc
content = content.replace('var pinnedSubLeftTop by remember { mutableStateOf(prefs.getString("pref_sub_pinned_left_top_2", "geo")) }', '')
content = content.replace('var pinnedSubLeftBottom by remember { mutableStateOf(prefs.getString("pref_sub_pinned_left_bottom_2", "geo")) }', '')
content = content.replace('var pinnedSubLeftUnified by remember { mutableStateOf(prefs.getString("pref_sub_pinned_left_unified_2", "geo")) }', '')

# Ensure commas are fixed. 
# `onMoveSubDown = { ... }` is the last one now.
content = content.replace("prefs.edit().putString(key, mutable.joinToString(\",\")).apply()\n                                },", "prefs.edit().putString(key, mutable.joinToString(\",\")).apply()\n                                }")


with open("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", "w") as f:
    f.write(content)

