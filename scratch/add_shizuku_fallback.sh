#!/bin/bash
sed -i '/if (parsed.packageName == "com.arlosoft.macrodroid") {/,/launched = true\n                        }/c\
                        if (parsed.packageName == "com.arlosoft.macrodroid") {\
                            ElevatedTaskCloser.execShizuku("am start -n com.arlosoft.macrodroid/.ShortcutDispatchActivity -a android.intent.action.MAIN --es com.arlosoft.macrodroid.MACRO_NAME '"'"'${parsed.label}'"'"'")\
                            launched = true\
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {\
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
                            val targetIntent = shortcuts?.firstOrNull()?.let { it.intents?.lastOrNull() ?: it.intent }\
                            if (targetIntent != null) {\
                                val uriStr = targetIntent.toUri(Intent.URI_INTENT_SCHEME).replace("'"'"'", "'"'"'\\'"'"''"'"'")\
                                ElevatedTaskCloser.execShizuku("am start '"'"'$uriStr'"'"'")\
                                launched = true\
                            }\
                        }' app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt
