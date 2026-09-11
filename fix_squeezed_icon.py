with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "r") as f:
    content = f.read()

target = """                                                                        Text("Face-Oriented Auto-Rotate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .size(18.dp)
                                                                                .clip(CircleShape)
                                                                                .background(cautionAmber.copy(alpha = 0.15f))
                                                                                .border(0.8.dp, cautionAmber.copy(alpha = 0.4f), CircleShape)
                                                                                .clickable { showFaceRotatePrivacyDialog = true },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Info,
                                                                                contentDescription = "Privacy Architecture",
                                                                                tint = cautionAmber,
                                                                                modifier = Modifier.size(12.dp)
                                                                            )"""

new_target = """                                                                        Text("Face-Oriented Auto-Rotate", modifier = Modifier.weight(1f, fill = false), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Box(
                                                                            modifier = Modifier
                                                                                .requiredSize(22.dp)
                                                                                .clip(CircleShape)
                                                                                .background(cautionAmber.copy(alpha = 0.15f))
                                                                                .border(0.8.dp, cautionAmber.copy(alpha = 0.4f), CircleShape)
                                                                                .clickable { showFaceRotatePrivacyDialog = true },
                                                                            contentAlignment = Alignment.Center
                                                                        ) {
                                                                            Icon(
                                                                                Icons.Default.Info,
                                                                                contentDescription = "Privacy Architecture",
                                                                                tint = cautionAmber,
                                                                                modifier = Modifier.requiredSize(14.dp)
                                                                            )"""

if target in content:
    content = content.replace(target, new_target)
else:
    print("Could not find target block")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "w") as f:
    f.write(content)
