with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarTabAccordion.kt", "r") as f:
    content = f.read()

old = """                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                    if (modeKey == "custom_pinned" && pinnedSectionId != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "Anchor: ${sectionTitles[pinnedSectionId] ?: pinnedSectionId}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.75f), lineHeight = 13.sp)
                            }"""

new = """                            Column {
                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.75f), lineHeight = 13.sp)
                                if (modeKey == "custom_pinned" && pinnedSectionId != null && pinnedSectionId != "none" && pinnedSectionId.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = "Anchor: ${sectionTitles[pinnedSectionId] ?: pinnedSectionId}",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }"""

if old in content:
    content = content.replace(old, new)
else:
    print("Could not find old text in SidebarTabAccordion.kt")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/SidebarTabAccordion.kt", "w") as f:
    f.write(content)
