with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "r") as f:
    content = f.read()

old = """                    when (tabId) {
                        0 -> {
                            tabMode0 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, newMode).apply()
                        }
                        1 -> {
                            tabMode1 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, newMode).apply()
                        }
                        2 -> {
                            tabMode2 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, newMode).apply()
                        }
                    }
                    popoverTabTarget = null
                    onRefreshNeeded()
                },"""

new = """                    when (tabId) {
                        0 -> {
                            tabMode0 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, newMode).apply()
                        }
                        1 -> {
                            tabMode1 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, newMode).apply()
                        }
                        2 -> {
                            tabMode2 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, newMode).apply()
                        }
                    }
                    if (newMode == "custom_pinned" && (currentPinned == null || currentPinned == "none" || currentPinned.isEmpty())) {
                        blueprintTabTarget = tabId
                    }
                    popoverTabTarget = null
                    onRefreshNeeded()
                },"""

if old in content:
    content = content.replace(old, new)
else:
    print("Could not find old text for auto blueprint")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "w") as f:
    f.write(content)
