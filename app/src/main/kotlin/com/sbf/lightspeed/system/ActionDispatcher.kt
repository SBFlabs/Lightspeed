package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
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
                openRecents(context, service)
            }
            token == "system:notifications" || token == "ACTION_NOTIFICATIONS" || token == "notifications" -> {
                expandNotifications(context, service)
            }
            token == "system:quick_settings" || token == "ACTION_QUICK_SETTINGS" || token == "quick_settings" -> {
                expandQuickSettings(context, service)
            }
            token == "system:screen_timeout" -> {
                LightspeedTimeoutEngine.cycleNext(context)
            }
            token == "system:volume" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.adjustSuggestedStreamVolume(
                    AudioManager.ADJUST_SAME,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    AudioManager.FLAG_SHOW_UI
                )
            }
            token.startsWith("app:") -> {
                val pkg = token.removePrefix("app:")
                context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
            token.startsWith("shortcut:") -> {
                LightspeedShortcutManager.launch(context, token)
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

    private fun openRecents(context: Context, service: AccessibilityService?) {
        // 1. Primary: AccessibilityService GLOBAL_ACTION_RECENTS
        val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true
        if (success) {
            Log.i(TAG, "Recents overview triggered via AccessibilityService")
            return
        }

        // 2. Secondary: StatusBarManager.toggleRecentApps() reflection
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("toggleRecentApps")
            method.invoke(statusBarService)
            Log.i(TAG, "Recents overview triggered via StatusBarManager.toggleRecentApps")
            return
        } catch (e: Exception) {
            Log.d(TAG, "StatusBarManager fallback skipped: ${e.message}")
        }

        // 3. Tertiary: Shizuku Shell KEYCODE_APP_SWITCH (187)
        try {
            if (ElevatedTaskCloser.isShizukuActive) {
                rikka.shizuku.Shizuku.newProcess(arrayOf("input", "keyevent", "187"), null, null).waitFor()
                Log.i(TAG, "Recents overview triggered via Shizuku KEYCODE_APP_SWITCH")
                return
            }
        } catch (_: Exception) {}

        // 4. Quaternary: Asynchronous retry on Main Looper (in case WindowManager focus was transitioning)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val retryService = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            retryService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }, 30L)
    }

    private fun expandNotifications(context: Context, service: AccessibilityService?) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
            return
        } catch (_: Exception) {}
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    private fun expandQuickSettings(context: Context, service: AccessibilityService?) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandSettingsPanel")
            method.invoke(statusBarService)
            return
        } catch (_: Exception) {}
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }
}
