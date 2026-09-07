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

    fun execute(context: Context, token: String?, onScrollToTop: () -> Unit = {}) {
        if (token.isNullOrBlank() || token == "none") return
        Log.i(TAG, "Executing action token: '$token'")

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
            token == LightspeedOrientationManager.ACTION_GRAVITY_RESET || token == "system:gravity_reset" || token == "ACTION_GRAVITY_RESET" -> {
                LightspeedOrientationManager.resetGravity(context)
            }
            token == LightspeedOrientationManager.ACTION_AUTO_ROTATE_TOGGLE || token == "system:auto_rotate_toggle" || token == "system:toggle_auto_rotate" || token == "auto_rotate_toggle" || token == LightspeedOrientationManager.ACTION_GRAVITY_TOGGLE_MASTER || token == "system:gravity_toggle_master" || token == LightspeedOrientationManager.ACTION_TOGGLE_ROTATION || token == "system:orientation_toggle" || token == "orientation_toggle" -> {
                LightspeedOrientationManager.toggleNativeAutoRotate(context)
            }
            token == LightspeedOrientationManager.ACTION_GRAVITY_OVERRIDE_360 || token == "system:gravity_override_360" || token == LightspeedOrientationManager.ACTION_FORCE_SENSOR_360 || token == "system:orientation_sensor_360" || token == "orientation_sensor_360" -> {
                LightspeedOrientationManager.overrideTransient360(context)
            }
            token == LightspeedOrientationManager.ACTION_GRAVITY_OVERRIDE_LANDSCAPE || token == "system:gravity_override_landscape" -> {
                LightspeedOrientationManager.overrideTransientLandscape(context)
            }
            token == LightspeedOrientationManager.ACTION_GRAVITY_OVERRIDE_PORTRAIT || token == "system:gravity_override_portrait" || token == LightspeedOrientationManager.ACTION_FORCE_PORTRAIT || token == "system:orientation_portrait" || token == "orientation_portrait" || token == LightspeedOrientationManager.ACTION_SENSOR_PORTRAIT || token == "system:orientation_sensor_portrait" || token == "orientation_sensor_portrait" -> {
                LightspeedOrientationManager.overrideTransientPortrait(context)
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
            token == "system:screen_timeout" || token == "ACTION_SCREEN_TIMEOUT" || token == "screen_timeout" -> {
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
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:glow_deflectors" || token == "ACTION_GLOW_DEFLECTORS" || token == "glow_deflectors" -> {
                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.triggerDeflectorsGlow()
            }
            token == "system:tactical_flyout" || token == "ACTION_TACTICAL_FLYOUT" || token == "action_quick_flyout" || token == "system:quick_flyout" -> {
                TacticalFlyoutLauncher.launch(context)
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
            token == "system:camera_photo" || token == "ACTION_CAMERA_PHOTO" || token == "camera_photo" || token == "system:camera" -> {
                try {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    val isLocked = keyguardManager?.isKeyguardLocked == true
                    val pm = context.packageManager
                    val secureIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val targetIntent = if (isLocked && secureIntent.resolveActivity(pm) != null) {
                        secureIntent
                    } else {
                        Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    }
                    context.startActivity(targetIntent)
                } catch (_: Exception) {}
            }
            token == "system:camera_video" || token == "ACTION_CAMERA_VIDEO" || token == "camera_video" -> {
                try {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    val isLocked = keyguardManager?.isKeyguardLocked == true
                    val pm = context.packageManager
                    val secureIntent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    val targetIntent = if (isLocked && secureIntent.resolveActivity(pm) != null) {
                        secureIntent
                    } else {
                        Intent(android.provider.MediaStore.INTENT_ACTION_VIDEO_CAMERA).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    }
                    context.startActivity(targetIntent)
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
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    if (km?.isKeyguardLocked == true && ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("input keyevent 82")
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
            token.startsWith("shortcut:") -> {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (km?.isKeyguardLocked == true && ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("input keyevent 82")
                }
                LightspeedShortcutManager.launch(context, token)
            }
            else -> {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(token)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    if (km?.isKeyguardLocked == true && ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("input keyevent 82")
                    }
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
    private var torchCallbackRegistered = false

    private fun ensureTorchCallback(cameraManager: android.hardware.camera2.CameraManager) {
        if (!torchCallbackRegistered) {
            try {
                cameraManager.registerTorchCallback(object : android.hardware.camera2.CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        isTorchOn = enabled
                    }
                }, null)
                torchCallbackRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun toggleFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager ?: return
            ensureTorchCallback(cameraManager)
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
        }
    }
}
