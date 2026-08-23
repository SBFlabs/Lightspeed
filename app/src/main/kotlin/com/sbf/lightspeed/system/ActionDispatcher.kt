package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService

object ActionDispatcher {
    private const val TAG = "ActionDispatcher"

    fun execute(context: Context, token: String?, onScrollToTop: () -> Unit = {}) {
        if (token.isNullOrBlank() || token == "none") return
        Log.i(TAG, "Executing action token: '$token'")

        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance

        when {
            token == "system:close_app" || token == "ACTION_CLOSE_APP" || token == "close_app" -> {
                ElevatedTaskCloser.closeTopApp(context)
            }
            token == "system:scroll_to_top" || token == "ACTION_SCROLL_TO_TOP" || token == "scroll_to_top" -> onScrollToTop()
            token == "system:home" || token == "ACTION_HOME" || token == "home" -> {
                if (service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) != true) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(homeIntent) } catch (_: Exception) {}
                }
            }
            token == "system:back" || token == "ACTION_BACK" || token == "back" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            token == "system:recents" || token == "ACTION_RECENTS" || token == "recents" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            }
            token == "system:notifications" || token == "ACTION_NOTIFICATIONS" || token == "notifications" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            }
            token == "system:quick_settings" || token == "ACTION_QUICK_SETTINGS" || token == "quick_settings" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            }
            token.startsWith("app:") -> {
                val pkg = token.removePrefix("app:")
                context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
            token.startsWith("shortcut:") -> {
                val uriString = token.removePrefix("shortcut:")
                if (uriString.contains(";id=") && uriString.contains(";pkg=")) {
                    val shortcutId = uriString.substringAfter(";id=").substringBefore(";")
                    val pkgName = uriString.substringAfter(";pkg=").substringBefore(";")
                    var launched = false
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(pkgName, shortcutId, null, null, Process.myUserHandle())
                        launched = true
                    } catch (e: Exception) {
                        Log.w(TAG, "LauncherApps shortcut launch failed, falling back to Shizuku/Intent", e)
                    }

                    if (!launched && ElevatedTaskCloser.isShizukuActive) {
                        try {
                            ElevatedTaskCloser.execShizuku("cmd shortcut start-shortcut --user 0 -p $pkgName -i $shortcutId || am start-shortcut -p $pkgName -i $shortcutId")
                            launched = true
                        } catch (e: Exception) {
                            Log.e(TAG, "Shizuku shortcut launch failed", e)
                        }
                    }

                    if (!launched) {
                        context.packageManager.getLaunchIntentForPackage(pkgName)?.let {
                            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try { context.startActivity(it) } catch (_: Exception) {}
                        }
                    }
                } else if (uriString.contains(";activity=") && uriString.contains(";pkg=")) {
                    val pkg = uriString.substringAfter(";pkg=").substringBefore(";")
                    val act = uriString.substringAfter(";activity=").substringBefore(";")
                    val intent = if (uriString.contains(";type=app_shortcut;")) {
                        Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                            setClassName(pkg, act)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    } else {
                        Intent().apply {
                            setClassName(pkg, act)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.w(TAG, "Standard activity launch failed, attempting elevated launch", e)
                        if (ElevatedTaskCloser.isShizukuActive) {
                            ElevatedTaskCloser.execShizuku("am start -n $pkg/$act")
                        }
                    }
                } else {
                    try {
                        val pureUri = if (uriString.contains("intent:#Intent;")) {
                            "intent:#Intent;" + uriString.substringAfter("intent:#Intent;").substringBefore(";pkg=").substringBefore(";custom_label=").substringBefore(";label=")
                        } else uriString
                        val launchIntent = Intent.parseUri(pureUri, Intent.URI_INTENT_SCHEME).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(launchIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Shortcut parse/launch failed", e)
                    }
                }
            }
            else -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(token)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(launchIntent) } catch (_: Exception) {}
                } else {
                    Log.w(TAG, "Unhandled action token: $token")
                }
            }
        }
    }
}
