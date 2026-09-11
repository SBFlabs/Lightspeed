import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "r") as f:
    content = f.read()

state_code = """
                    val defaultSubTop = listOf("geo", "scrub", "gestures")
                    val subTopStr = prefs.getString("pref_sub_order_top_2", "geo,scrub,gestures")!!
                    var currentSubTop by remember { mutableStateOf(subTopStr.split(",").filter { it in defaultSubTop }.let { it + (defaultSubTop - it.toSet()) }) }
                    var pinnedSubTop by remember { mutableStateOf(prefs.getString("pref_sub_pinned_top_2", "geo")) }

                    val defaultSubBottom = listOf("geo", "scrub", "gestures")
                    val subBottomStr = prefs.getString("pref_sub_order_bottom_2", "geo,scrub,gestures")!!
                    var currentSubBottom by remember { mutableStateOf(subBottomStr.split(",").filter { it in defaultSubBottom }.let { it + (defaultSubBottom - it.toSet()) }) }
                    var pinnedSubBottom by remember { mutableStateOf(prefs.getString("pref_sub_pinned_bottom_2", "geo")) }

                    val defaultSubUnified = listOf("geo", "scrub", "gestures")
                    val subUnifiedStr = prefs.getString("pref_sub_order_unified_2", "geo,scrub,gestures")!!
                    var currentSubUnified by remember { mutableStateOf(subUnifiedStr.split(",").filter { it in defaultSubUnified }.let { it + (defaultSubUnified - it.toSet()) }) }
                    var pinnedSubUnified by remember { mutableStateOf(prefs.getString("pref_sub_pinned_unified_2", "geo")) }
"""

# Insert state_code before `if (blueprintTabTarget == 2) {`
content = content.replace("                    if (blueprintTabTarget == 2) {", state_code + "\n                    if (blueprintTabTarget == 2) {")

# Update BlueprintWireframeView call
blueprint_call_old = """                            onExitBlueprint = {
                                blueprintTabTarget = null
                            }
                        )"""
blueprint_call_new = """                            onExitBlueprint = {
                                blueprintTabTarget = null
                            },
                            subSections = mapOf(
                                "top" to currentSubTop,
                                "bottom" to currentSubBottom,
                                "unified" to currentSubUnified
                            ),
                            subSectionTitles = mapOf(
                                "geo" to "Sensor Geometry",
                                "scrub" to "Inward Scrubbing Control",
                                "gestures" to "Gesture Actions & Macro Mappings"
                            ),
                            pinnedSubSections = mapOf(
                                "top" to pinnedSubTop,
                                "bottom" to pinnedSubBottom,
                                "unified" to pinnedSubUnified
                            ),
                            onMoveSubUp = { secId, idx ->
                                if (idx > 0) {
                                    val (list, key) = when (secId) {
                                        "top" -> currentSubTop to "pref_sub_order_top_2"
                                        "bottom" -> currentSubBottom to "pref_sub_order_bottom_2"
                                        "unified" -> currentSubUnified to "pref_sub_order_unified_2"
                                        else -> return@BlueprintWireframeView
                                    }
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    when (secId) {
                                        "top" -> currentSubTop = mutable
                                        "bottom" -> currentSubBottom = mutable
                                        "unified" -> currentSubUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },
                            onMoveSubDown = { secId, idx ->
                                val (list, key) = when (secId) {
                                    "top" -> currentSubTop to "pref_sub_order_top_2"
                                    "bottom" -> currentSubBottom to "pref_sub_order_bottom_2"
                                    "unified" -> currentSubUnified to "pref_sub_order_unified_2"
                                    else -> return@BlueprintWireframeView
                                }
                                if (idx < list.size - 1) {
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    when (secId) {
                                        "top" -> currentSubTop = mutable
                                        "bottom" -> currentSubBottom = mutable
                                        "unified" -> currentSubUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },
                            onPinSubSection = { secId, subId ->
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
                            }
                        )"""

content = content.replace(blueprint_call_old, blueprint_call_new)

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "w") as f:
    f.write(content)

