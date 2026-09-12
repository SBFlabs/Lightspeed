#!/bin/bash
sed -i '/\/\/ 1. Primary: Extract authentic direct Intent/,/\/\/ 3. Tertiary: MacroDroid Explicit Direct Runner Pipelines/c\
                // 1. Primary: LauncherApps.startShortcut (works if Lightspeed is home launcher or privileged)\
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {\
                    try {\
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps\
                        launcherApps.startShortcut(parsed.packageName, parsed.id, null, null, Process.myUserHandle())\
                        launched = true\
                    } catch (e: Exception) {\
                        Log.w(TAG, "LauncherApps startShortcut rejected: ${e.message}")\
                    }\
                }\
\
                // 2. Secondary: Extract authentic direct Intents backstack from ShortcutInfo\
                if (!launched && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {\
                    try {\
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps\
                        val query = LauncherApps.ShortcutQuery().apply {\
                            setPackage(parsed.packageName)\
                            setShortcutIds(listOf(parsed.id))\
                            setQueryFlags(\
                                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or\
                                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or\
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST\
                            )\
                        }\
                        val shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())\
                        val shortcut = shortcuts?.firstOrNull()\
                        if (shortcut != null) {\
                            val directIntents = shortcut.intents\
                            if (directIntents != null && directIntents.isNotEmpty()) {\
                                val intentsToStart = directIntents.map { Intent(it) }.toTypedArray()\
                                intentsToStart[0].addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)\
                                try {\
                                    context.startActivities(intentsToStart)\
                                    launched = true\
                                } catch (_: Exception) {}\
                            }\
                            \
                            if (!launched) {\
                                val directIntent = shortcut.intent\
                                if (directIntent != null) {\
                                    val launchIntent = Intent(directIntent).apply {\
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)\
                                    }\
                                    try {\
                                        context.startActivity(launchIntent)\
                                        launched = true\
                                    } catch (_: Exception) {\
                                        try {\
                                            context.sendBroadcast(launchIntent)\
                                            launched = true\
                                        } catch (_: Exception) {}\
                                    }\
                                }\
                            }\
                        }\
                    } catch (e: Exception) {\
                        Log.d(TAG, "Direct shortcut intent resolution failed: ${e.message}")\
                    }\
                }\
\
                // 3. Tertiary: MacroDroid Explicit Direct Runner Pipelines' app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt
