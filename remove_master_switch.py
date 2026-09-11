with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "r") as f:
    content = f.read()

target = """                                                    // Master Auto-Rotate Toggle
                                                    PrefToggleRow(
                                                        title = "Auto-Rotate Master Switch",
                                                        subtitle = "Global Android display rotation controller",
                                                        isChecked = isAutoRotateActive,
                                                        onCheckedChange = { checked ->
                                                            val ok = LightspeedOrientationEngine.setAutoRotateEnabled(context, checked)
                                                            if (ok) {
                                                                isAutoRotateActive = checked
                                                            } else {
                                                                android.widget.Toast.makeText(context, "Elevated permission needed. Opening system settings...", android.widget.Toast.LENGTH_SHORT).show()
                                                                LightspeedOrientationEngine.openAutoRotateSettings(context)
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    )"""

if target in content:
    content = content.replace(target, "")
else:
    print("Could not find block")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "w") as f:
    f.write(content)
