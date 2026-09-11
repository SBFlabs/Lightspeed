import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarBlueprintWireframe.kt", "r") as f:
    content = f.read()

# Add parameters
content = content.replace(
    "onExitBlueprint: () -> Unit",
    """onExitBlueprint: () -> Unit,
    subSections: Map<String, List<String>> = emptyMap(),
    subSectionTitles: Map<String, String> = emptyMap(),
    pinnedSubSections: Map<String, String?> = emptyMap(),
    onMoveSubUp: (String, Int) -> Unit = { _, _ -> },
    onMoveSubDown: (String, Int) -> Unit = { _, _ -> },
    onPinSubSection: (String, String) -> Unit = { _, _ -> }"""
)

# Insert the nested rendering
nested_render = """
            // Render sub-sections if they exist
            val subs = subSections[secId] ?: emptyList()
            if (subs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subs.forEachIndexed { subIndex, subId ->
                        val isSubPinned = (subId == pinnedSubSections[secId])
                        val subTitle = subSectionTitles[subId] ?: subId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSubPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF1B1E2B).copy(alpha = 0.8f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSubPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "↳ $subTitle",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }

                                // Pin Sub-Section
                                IconButton(
                                    onClick = { onPinSubSection(secId, subId) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pin Sub",
                                        tint = if (isSubPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Move Sub Up
                                IconButton(
                                    onClick = { onMoveSubUp(secId, subIndex) },
                                    enabled = subIndex > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Up",
                                        tint = if (subIndex > 0) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Move Sub Down
                                IconButton(
                                    onClick = { onMoveSubDown(secId, subIndex) },
                                    enabled = subIndex < subs.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Down",
                                        tint = if (subIndex < subs.size - 1) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
"""

# Find where to insert it. We want it right after the main card is closed.
# The main card ends right before the `}` of the `forEachIndexed`.
# We need to be careful. Let's find: `                    }` inside `sectionIds.forEachIndexed` that closes the Card.
# Let's just use string replacement carefully.
# The end of the main Card rendering:
card_end = """                    }
                }
            }"""

if card_end in content:
    content = content.replace(card_end, card_end + "\n" + nested_render)

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarBlueprintWireframe.kt", "w") as f:
    f.write(content)

