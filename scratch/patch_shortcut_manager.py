import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

# Replace block 1 (Primary / Secondary)
old_primary = """                // 1. Primary: Extract authentic direct Intent from ShortcutInfo via LauncherApps (Android 7.1+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
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
                        if (shortcut != null) {
                            val directIntent = shortcut.intent ?: shortcut.intents?.firstOrNull()
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
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct shortcut intent resolution failed: ${e.message}")
                    }
                }

                // 2. Secondary: LauncherApps.startShortcut (works if Lightspeed is home launcher or privileged)
                if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(parsed.packageName, parsed.id, null, null, Process.myUserHandle())
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "LauncherApps startShortcut rejected: ${e.message}")
                    }
                }"""

new_primary = """                // 1. Primary: LauncherApps.startShortcut (works if Lightspeed is home launcher or privileged)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(parsed.packageName, parsed.id, null, null, Process.myUserHandle())
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "LauncherApps startShortcut rejected: ${e.message}")
                    }
                }

                // 2. Secondary: Extract authentic direct Intents backstack from ShortcutInfo
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
                        if (shortcut != null) {
                            val directIntents = shortcut.intents
                            if (directIntents != null && directIntents.isNotEmpty()) {
                                val intentsToStart = directIntents.map { Intent(it) }.toTypedArray()
                                intentsToStart[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try {
                                    context.startActivities(intentsToStart)
                                    launched = true
                                } catch (_: Exception) {}
                            }
                            
                            if (!launched) {
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
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Direct shortcut intent resolution failed: ${e.message}")
                    }
                }"""

# Replace block 2 (Quaternary)
old_quaternary = """                // 4. Quaternary: Elevated Shizuku Shell Execution
                if (!launched && ElevatedTaskCloser.isShizukuActive) {
                    try {
                        if (parsed.packageName == "com.arlosoft.macrodroid") {
                            ElevatedTaskCloser.execShizuku("am start -n com.arlosoft.macrodroid/.ShortcutDispatchActivity -a android.intent.action.MAIN --es com.arlosoft.macrodroid.MACRO_NAME '${parsed.label}'")
                            launched = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Shizuku shortcut launch failed", e)
                    }
                }"""

new_quaternary = """                // 4. Quaternary: Elevated Shizuku Shell Execution
                if (!launched && ElevatedTaskCloser.isShizukuActive) {
                    try {
                        if (parsed.packageName == "com.arlosoft.macrodroid") {
                            ElevatedTaskCloser.execShizuku("am start -n com.arlosoft.macrodroid/.ShortcutDispatchActivity -a android.intent.action.MAIN --es com.arlosoft.macrodroid.MACRO_NAME '${parsed.label}'")
                            launched = true
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
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
                            val targetIntent = shortcut?.intents?.lastOrNull() ?: shortcut?.intent
                            if (targetIntent != null) {
                                val uriStr = targetIntent.toUri(Intent.URI_INTENT_SCHEME).replace("'", "'\\''")
                                ElevatedTaskCloser.execShizuku("am start '$uriStr'")
                                launched = true
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Shizuku shortcut launch failed", e)
                    }
                }"""

content = content.replace(old_primary, new_primary)
content = content.replace(old_quaternary, new_quaternary)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
