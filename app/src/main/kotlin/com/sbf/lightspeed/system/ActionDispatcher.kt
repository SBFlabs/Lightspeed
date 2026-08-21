package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log

object ActionDispatcher {
    private const val TAG = "ActionDispatcher"

    fun execute(context: Context, token: String?, onScrollToTop: () -> Unit = {}) {
        Log.i(TAG, "Executing action token: '$token'")
        if (token.isNullOrBlank() || token == "none") return

        val service = context as? AccessibilityService

        when {
            token == "system:close_app" || token == "ACTION_CLOSE_APP" || token == "close_app" -> {
                Log.i(TAG, "Triggering ElevatedTaskCloser.closeTopApp()")
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
            token == "system:back" || token == "ACTION_BACK" || token == "back" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            token == "system:recents" || token == "ACTION_RECENTS" || token == "recents" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
            token == "system:notifications" || token == "ACTION_NOTIFICATIONS" || token == "notifications" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            token == "system:quick_settings" || token == "ACTION_QUICK_SETTINGS" || token == "quick_settings" -> service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
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
                    try {
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                        launcherApps.startShortcut(pkgName, shortcutId, null, null, Process.myUserHandle())
                    } catch (e: Exception) {
                        Log.e(TAG, "LauncherApps shortcut launch failed", e)
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
            else -> Log.w(TAG, "Unhandled action token: $token")
        }
    }
}
