import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarBlueprintWireframe.kt", "r") as f:
    content = f.read()

# Replace the signature
sig_old = """    pinnedSubSections: Map<String, String?> = emptyMap(),
    onMoveSubUp: (String, Int) -> Unit = { _, _ -> },
    onMoveSubDown: (String, Int) -> Unit = { _, _ -> },
    onPinSubSection: (String, String) -> Unit = { _, _ -> }"""
sig_new = """    onMoveSubUp: (String, Int) -> Unit = { _, _ -> },
    onMoveSubDown: (String, Int) -> Unit = { _, _ -> }"""
content = content.replace(sig_old, sig_new)

# Replace the sub-section rendering pin logic
pin_old = """                        val isSubPinned = (subId == pinnedSubSections[secId])"""
pin_new = """                        val isSubPinned = ("${secId}_${subId}" == pinnedSectionId)"""
content = content.replace(pin_old, pin_new)

btn_old = """                                // Pin Sub-Section
                                IconButton(
                                    onClick = { onPinSubSection(secId, subId) },"""
btn_new = """                                // Pin Sub-Section
                                IconButton(
                                    onClick = { onPinSection("${secId}_${subId}") },"""
content = content.replace(btn_old, btn_new)

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarBlueprintWireframe.kt", "w") as f:
    f.write(content)

