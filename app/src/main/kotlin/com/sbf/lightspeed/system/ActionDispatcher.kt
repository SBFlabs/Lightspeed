package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.settings.LightspeedActionRegistry

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

    fun execute(context: Context, rawToken: String?, onScrollToTop: () -> Unit = {}) {
        if (rawToken.isNullOrBlank() || rawToken == "none") return
        val token = LightspeedActionRegistry.canonicalToken(rawToken)
        Log.i(TAG, "Executing action token: '$token' (raw: '$rawToken')")

        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance

        when {
            token == "system:gearset_nav" -> {
                LightspeedKeyEngine.startHudNav(context)
            }
            token == "system:previous_app" -> {
                ElevatedTaskCloser.switchToPreviousApp(context)
            }
            token == "system:split_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                }
            }
            token == "system:freeform" -> {
                ElevatedTaskCloser.launchInFreeform(context)
            }
            token == "system:flashlight" -> {
                toggleFlashlight(context)
            }
            token == "system:screenshot" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    ElevatedTaskCloser.execShizuku("input keyevent KEYCODE_SYSRQ")
                }
            }
            token == "system:lock_screen" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    ElevatedTaskCloser.execShizuku("input keyevent KEYCODE_POWER")
                }
            }
            token == "system:perimeter_watchdog" -> {
                val intent = Intent(context, LightspeedPerimeterActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:core_watchdog" -> {
                val intent = Intent(context, LightspeedCoreWatchdogActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:central_command" -> {
                val intent = Intent(context, com.sbf.lightspeed.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:battery_exemption" -> {
                val intent = Intent(context, LightspeedBatteryExemptionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:close_app" -> {
                ElevatedTaskCloser.closeTopApp(context)
            }
            token == "system:scroll_to_top" -> onScrollToTop()
            token == "system:gravity_reset" -> {
                LightspeedOrientationManager.resetGravity(context)
            }
            token == "system:auto_rotate_toggle" -> {
                LightspeedOrientationManager.toggleNativeAutoRotate(context)
            }
            token == "system:gravity_override_360" -> {
                LightspeedOrientationManager.overrideTransient360(context)
            }
            token == "system:gravity_override_landscape" -> {
                LightspeedOrientationManager.overrideTransientLandscape(context)
            }
            token == "system:gravity_override_portrait" -> {
                LightspeedOrientationManager.overrideTransientPortrait(context)
            }
            token == "system:gravity_override_sensor_portrait" || token == "system:orientation_sensor_portrait" -> {
                LightspeedOrientationManager.overrideTransientSensorPortrait(context)
            }
            token == "system:home" -> {
                if (service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) != true) {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    try { context.startActivity(homeIntent) } catch (_: Exception) {}
                }
            }
            token == "system:back" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            token == "system:recents" -> {
                openRecents(context, service)
            }
            token == "system:notifications" -> {
                expandNotifications(context, service)
            }
            token == "system:quick_settings" -> {
                expandQuickSettings(context, service)
            }
            token == "system:screen_timeout" -> {
                LightspeedTimeoutEngine.cycleNext(context)
            }
            token == "system:media_play_pause" -> {
                LightspeedMediaManager.playPause(context)
            }
            token == "system:media_next" -> {
                LightspeedMediaManager.next(context)
            }
            token == "system:media_prev" -> {
                LightspeedMediaManager.previous(context)
            }
            token == "system:media_skip_forward" -> {
                LightspeedMediaManager.skipForward(context)
            }
            token == "system:media_skip_backward" -> {
                LightspeedMediaManager.skipBackward(context)
            }
            token == "system:media_scrubber" -> {
                LightspeedMediaManager.showScrubber(context)
            }
            token == "system:media_stop" -> {
                LightspeedMediaManager.stop(context)
            }
            token == "system:refueling_bay" -> {
                val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                try { context.startActivity(intent) } catch (_: Exception) {}
            }
            token == "system:glow_deflectors" -> {
                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.triggerDeflectorsGlow()
            }
            token == "system:omniscient_audio" -> {
                val svc = service ?: com.sbf.lightspeed.LightspeedAccessibilityService.instance
                svc?.let { OmniscientAudioDockManager.show(it) }
            }
            token == "system:tactical_flyout" -> {
                TacticalFlyoutLauncher.launch(context)
            }

            token == "system:lens" -> {
                TacticalFlyoutLauncher.launchLens(context)
            }
            token == "system:qr_scanner" -> {
                TacticalFlyoutLauncher.launchQrScanner(context)
            }
            token == "system:chatgpt" -> {
                TacticalFlyoutLauncher.launchChatGPT(context)
            }
            token == "system:claude" -> {
                TacticalFlyoutLauncher.launchClaude(context)
            }
            token == "system:gemini" -> {
                TacticalFlyoutLauncher.launchGemini(context)
            }
            token == "system:camera_photo" -> {
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
            token == "system:camera_video" -> {
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
            token == "system:core_cooling" -> {
                LightspeedWatchdogEngine.executeCoreCoolingReboot(context)
            }
            token == "system:volume" -> {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                val showNative = LightspeedPreferences.isVolumeShowNativeSlider(context)
                val flags = if (showNative) AudioManager.FLAG_SHOW_UI else 0
                audioManager?.adjustSuggestedStreamVolume(
                    AudioManager.ADJUST_SAME,
                    AudioManager.USE_DEFAULT_STREAM_TYPE,
                    flags
                )
                if (LightspeedPreferences.isHudVolumeEnabled(context)) {
                    val cur = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
                    val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                    val pct = kotlin.math.round(cur * 100f / max.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                    val volResolution = LightspeedPreferences.getVolumeScrubResolution(context)
                    com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                        title = "MEDIA VOLUME",
                        value = "$pct%",
                        stepIndex = (pct * volResolution / 100).coerceIn(0, volResolution),
                        totalSteps = volResolution
                    )
                }
            }
            token == "system:brightness" -> {
                if (android.provider.Settings.System.canWrite(context)) {
                    val current = try {
                        android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { 128 }
                    val steps = listOf(0, 51, 102, 153, 204, 255)
                    val next = steps.firstOrNull { it > current + 10 } ?: steps.first()
                    try {
                        android.provider.Settings.System.putInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS, next)
                        if (LightspeedPreferences.isHudBrightnessEnabled(context)) {
                            com.sbf.lightspeed.LightspeedStatusBarOverlay.showActionHud(
                                title = "BRIGHTNESS",
                                value = "${(next * 100 / 255)}%",
                                stepIndex = (next * 10 / 255),
                                totalSteps = 10
                            )
                        }
                    } catch (_: Exception) {}
                } else {
                    LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
                }
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
