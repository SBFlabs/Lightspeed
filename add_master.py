with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "r") as f:
    content = f.read()

target = """                                                    // 1. Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE)"""

new_toggle = """                                                    var isEngineEnabled by remember { mutableStateOf(prefs.getBoolean("pref_synthetic_gravity_enabled", true)) }
                                                    PrefToggleRow(
                                                        title = "Enable Synthetic Gravity Engine",
                                                        subtitle = "Master switch to enable or disable all custom per-app rotation rules and bucket logic.",
                                                        isChecked = isEngineEnabled,
                                                        onCheckedChange = { checked ->
                                                            isEngineEnabled = checked
                                                            prefs.edit().putBoolean("pref_synthetic_gravity_enabled", checked).apply()
                                                            com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateGravityCascade(context)
                                                        }
                                                    )

                                                    // 1. Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE)"""

if target in content:
    content = content.replace(target, new_toggle)
else:
    print("Could not find target")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", "w") as f:
    f.write(content)
