import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

old_secondary = """                            if (!launched) {
                                val directIntent = shortcut.intent
                                if (directIntent != null) {
                                    val launchIntent = Intent(directIntent).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try {
                                        context.startActivity(launchIntent)
                                        launched = true
                                    } catch (_: Exception) {
                                        try {
                                            context.sendBroadcast(launchIntent)
                                            launched = true
                                        } catch (_: Exception) {}
                                    }
                                }
                            }"""

new_secondary = """                            // Fallback moved to end of pipeline"""

content = content.replace(old_secondary, new_secondary)

old_quaternary_end = """                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Shizuku shortcut launch failed", e)
                    }
                }"""

new_quaternary_end = """                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Shizuku shortcut launch failed", e)
                    }
                }

                // 5. Quinary: Fallback to single/main intent (Last Resort)
                if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        val query = LauncherApps.ShortcutQuery().apply {
                            setPackage(parsed.packageName)
                            setShortcutIds(listOf(parsed.id))
                            setQueryFlags(
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                            )
                        }
                        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                        val shortcut = shortcuts?.firstOrNull()
                        val directIntent = shortcut?.intent
                        if (directIntent != null) {
                            val launchIntent = Intent(directIntent).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                context.startActivity(launchIntent)
                                launched = true
                            } catch (_: Exception) {
                                try {
                                    context.sendBroadcast(launchIntent)
                                    launched = true
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {}
                }"""

content = content.replace(old_quaternary_end, new_quaternary_end)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
