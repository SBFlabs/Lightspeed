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

    fun dispatch(token: String?, context: Context) {
        execute(context, token)
    }

    fun executeDirect(context: Context, token: String?) {
        if (token.isNullOrBlank() || token == "none") return
        if (token.startsWith("shortcut:")) {
            LightspeedShortcutManager.launch(context, token)
        } else {
            execute(context, token)
        }
    }

    private fun isDeepActivity(token: String): Boolean {
        if (token.startsWith("shortcut:")) {
            val parsed = LightspeedShortcutManager.parseToken(token)
            return (parsed.type == "activity" || parsed.type == "app_shortcut") &&
                    parsed.packageName.isNotBlank() && parsed.activityName.isNotBlank()
        }
        return false
    }

    fun execute(context: Context, token: String?, onScrollToTop: () -> Unit = {}) {
        if (token.isNullOrBlank() || token == "none") return
        Log.i(TAG, "Executing action token: '$token'")

        if (isDeepActivity(token)) {
            val prefs = context.defaultPrefs()
            val suppressWarning = prefs.getBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, false)
            if (!suppressWarning) {
                val intent = Intent(context, com.sbf.lightspeed.CockpitDialogActivity::class.java).apply {
                    action = com.sbf.lightspeed.CockpitDialogActivity.ACTION_CONFIRM_DEEP_ACTIVITY
                    putExtra(com.sbf.lightspeed.CockpitDialogActivity.EXTRA_TOKEN, token)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try {
                    context.startActivity(intent)
                } catch (_: Exception) {}
                return
            }
        }

        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance

        when {
            token == "action_enter_gearset_nav" || token == "system:gearset_nav" || token == "ACTION_ENTER_GEARSET_NAV" -> {
                LightspeedKeyEngine.startHudNav(context)
            }
            token == "system:previous_app" || token == "ACTION_PREVIOUS_APP" || token == "previous_app" -> {
                ElevatedTaskCloser.switchToPreviousApp(context)
            }
            token == "system:split_screen" || token == "ACTION_SPLIT_SCREEN" || token == "split_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                }
            }
            token == "system:popup_window" || token == "system:freeform" || token == "popup_window" -> {
                ElevatedTaskCloser.launchInFreeform(context)
            }
            token == "system:flashlight" || token == "system:torch" || token == "ACTION_FLASHLIGHT" || token == "flashlight" -> {
                toggleFlashlight(context)
            }
            token == "system:screenshot" || token == "ACTION_SCREENSHOT" || token == "screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    ElevatedTaskCloser.execShizuku("input keyevent KEYCODE_SYSRQ")
                }
            }
            token == "system:lock_screen" || token == "ACTION_LOCK_SCREEN" || token == "lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    ElevatedTaskCloser.execShizuku("input keyevent KEYCODE_POWER")
                }
            }
            token == "system:close_app" || token == "ACTION_CLOSE_APP" || token == "close_app" -> {
                ElevatedTaskCloser.closeTopApp(context)
            }
            token == "system:scroll_to_top" || token == "ACTION_SCROLL_TO_TOP" || token == "scroll_to_top" -> onScrollToTop()
            token == LightspeedOrientationManager.ACTION_TOGGLE_ROTATION || token == "system:orientation_toggle" || token == "orientation_toggle" -> {
                LightspeedOrientationManager.toggleRotation(context)
            }
            token == LightspeedOrientationManager.ACTION_FORCE_PORTRAIT || token == "system:orientation_portrait" || token == "orientation_portrait" -> {
                LightspeedOrientationManager.forcePortrait(context)
            }
            token == LightspeedOrientationManager.ACTION_FORCE_SENSOR_360 || token == "system:orientation_sensor_360" || token == "orientation_sensor_360" -> {
                LightspeedOrientationManager.forceSensor360(context)
            }
            token == LightspeedOrientationManager.ACTION_SENSOR_PORTRAIT || token == "system:orientation_sensor_portrait" || token == "orientation_sensor_portrait" -> {
                LightspeedOrientationManager.setSensorPortrait(context)
            }
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
            token == "system:media_play_pause" || token == "ACTION_MEDIA_PLAY_PAUSE" || token == "media_play_pause" -> {
                LightspeedMediaManager.playPause(context)
            }
            token == "system:media_next" || token == "ACTION_MEDIA_NEXT" || token == "media_next" -> {
                LightspeedMediaManager.next(context)
            }
            token == "system:media_prev" || token == "ACTION_MEDIA_PREV" || token == "media_prev" -> {
                LightspeedMediaManager.previous(context)
            }
            token == "system:media_skip_forward" || token == "ACTION_MEDIA_SKIP_FORWARD" || token == "media_skip_forward" -> {
                LightspeedMediaManager.skipForward(context)
            }
            token == "system:media_skip_backward" || token == "ACTION_MEDIA_SKIP_BACKWARD" || token == "media_skip_backward" -> {
                LightspeedMediaManager.skipBackward(context)
            }
            token == "system:media_scrubber" || token == "ACTION_MEDIA_SCRUBBER" || token == "media_scrubber" -> {
                LightspeedMediaManager.showScrubber(context)
            }
            token == "system:media_stop" || token == "ACTION_MEDIA_STOP" || token == "media_stop" -> {
                LightspeedMediaManager.stop(context)
            }
            token == "system:refueling_bay" || token == "ACTION_REFUELING_BAY" || token == "refueling_bay" -> {
                val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:tactical_flyout" || token == "ACTION_TACTICAL_FLYOUT" || token == "action_quick_flyout" || token == "system:quick_flyout" -> {
                TacticalFlyoutLauncher.launch(context)
            }
            token == "system:tactical_audio" || token == "ACTION_TACTICAL_AUDIO" || token == "tactical_audio" -> {
                TacticalAudioEngine.toggle(context)
            }
            token == "system:lens" || token == "ACTION_LENS" || token == "google_lens" -> {
                TacticalFlyoutLauncher.launchLens(context)
            }
            token == "system:qr_scanner" || token == "ACTION_QR_SCANNER" || token == "qr_scanner" -> {
                TacticalFlyoutLauncher.launchQrScanner(context)
            }
            token == "system:chatgpt" || token == "ACTION_CHATGPT" || token == "chatgpt" -> {
                TacticalFlyoutLauncher.launchChatGPT(context)
            }
            token == "system:claude" || token == "ACTION_CLAUDE" || token == "claude" -> {
                TacticalFlyoutLauncher.launchClaude(context)
            }
            token == "system:gemini" || token == "ACTION_GEMINI" || token == "gemini" -> {
                TacticalFlyoutLauncher.launchGemini(context)
            }
            token == "system:folax" || token == "ACTION_FOLAX" || token == "folax" -> {
                val folaxIntent = context.packageManager.getLaunchIntentForPackage("com.transsion.folax")
                    ?: context.packageManager.getLaunchIntentForPackage("com.transsion.folaxclient")
                if (folaxIntent != null) {
                    folaxIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(folaxIntent) } catch (_: Exception) {}
                }
            }
            token == "system:camera_photo" || token == "ACTION_CAMERA_PHOTO" || token == "camera_photo" || token == "system:camera" -> {
                try {
                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            token == "system:camera_video" || token == "ACTION_CAMERA_VIDEO" || token == "camera_video" -> {
                try {
                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            token == "system:core_cooling" || token == "ACTION_CORE_COOLING" || token == "core_cooling" -> {
                LightspeedWatchdogEngine.executeCoreCoolingReboot(context)
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
                ElevatedTaskCloser.execShizuku("input keyevent 187")?.waitFor()
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

    private var isTorchOn = false

    private fun toggleFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
        }
    }
}
