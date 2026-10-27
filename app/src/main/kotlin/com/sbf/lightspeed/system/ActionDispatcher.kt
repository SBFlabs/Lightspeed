package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.settings.LightspeedActionRegistry

object ActionDispatcher {
    private val closeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private const val TAG = "ActionDispatcher"

    fun dispatch(token: String?, context: Context) {
        execute(context, token)
    }

    fun executeDirect(context: Context, token: String?) {
        if (token.isNullOrBlank() || token == "none") return
        val isShortcut = token.startsWith("shortcut:") ||
            token.startsWith("custom:") ||
            token.startsWith("caller;") ||
            token.contains(";b64uri=") ||
            token.contains(";label=") ||
            token.contains("type=")
        if (isShortcut) {
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

        if (ActionDispatcherSonicDisplay.handleSonicOrDisplay(context, token, service)) {
            return
        }

        if (token.startsWith("bot:macro_")) {
            val botId = token.removePrefix("bot:macro_").toIntOrNull() ?: 1
            com.sbf.lightspeed.system.bot.LightspeedBotEngine.playBot(context, botId)
            return
        }

        when {
            token == "system:gearset_nav" -> {
                LightspeedKeyEngine.startHudNav(context)
            }
            token == "system:previous_app" -> {
                ElevatedTaskCloser.switchToPreviousApp(context)
            }
            token == "system:split_screen" -> {
                ElevatedTaskCloser.toggleSplitScreen(context)
            }
            token == "system:freeform" || token == "system:popup_window" -> {
                val style = context.defaultPrefs().getString(
                    LightspeedPreferences.KEY_POPUP_STYLE,
                    LightspeedPreferences.POPUP_STYLE_NATIVE
                ) ?: LightspeedPreferences.POPUP_STYLE_NATIVE
                ElevatedTaskCloser.launchInPopup(context, style)
            }
            token == "system:flashlight" -> {
                ActionDispatcherSystemHelpers.toggleFlashlight(context)
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
            token == "system:power_menu" -> {
                service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            }
            token == "system:perimeter_watchdog" -> {
                val intent = Intent(context, LightspeedPerimeterActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:73", e) }
            }
            token == "system:core_watchdog" -> {
                val intent = Intent(context, LightspeedCoreWatchdogActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:79", e) }
            }
            token == "system:central_command" -> {
                val intent = Intent(context, com.sbf.lightspeed.MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:85", e) }
            }
            token == "system:battery_exemption" -> {
                val intent = Intent(context, LightspeedBatteryExemptionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:91", e) }
            }
            token == "system:close_app" -> {
                closeExecutor.execute {
                    try {
                        ElevatedTaskCloser.closeTopApp(context)
                    } catch (e: Throwable) {
                        logSwallowed(TAG, "close_app", e)
                    }
                }
            }
            token == "system:scroll_to_top" -> {
                LightspeedScrollHelper.scroll(context, toBottom = false)
                onScrollToTop()
            }
            token == "system:scroll_to_bottom" -> {
                LightspeedScrollHelper.scroll(context, toBottom = true)
            }
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
                    try { context.startActivity(homeIntent) } catch (e: Exception) { logSwallowed(TAG, "execute:121", e) }
                }
            }
            token == "system:back" -> {
                val selected = ActionMethods.getSelected(context, "system:back")
                if (ActionMethods.SHIZUKU in selected) {
                    closeExecutor.execute {
                        ShizukuExecHelper.injectBack(context)
                    }
                } else if (ActionMethods.A11Y_ACTION in selected) {
                    service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                } else {
                    if (service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) != true) {
                        closeExecutor.execute {
                            ShizukuExecHelper.injectBack(context)
                        }
                    }
                }
            }
            token == "system:recents" -> {
                ActionDispatcherSystemHelpers.openRecents(context, service)
            }
            token == "system:notifications" -> {
                ActionDispatcherSystemHelpers.expandNotifications(context, service)
            }
            token == "system:quick_settings" -> {
                ActionDispatcherSystemHelpers.expandQuickSettings(context, service)
            }
            token == "system:screen_timeout" -> {
                LightspeedTimeoutEngine.cycleNext(context)
            }
            token == "system:media_play_pause" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.playPause(context)
            }
            token == "system:media_next" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.next(context)
            }
            token == "system:media_prev" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.previous(context)
            }
            token == "system:media_skip_forward" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.skipForward(context)
            }
            token == "system:media_skip_backward" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.skipBackward(context)
            }
            token == "system:media_scrubber" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.showScrubber(context)
            }
            token == "system:media_stop" -> {
                ActionDispatcherSystemHelpers.warnIfNoNotificationAccess(context)
                LightspeedMediaManager.stop(context)
            }
            token == "system:refueling_bay" -> {
                val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                }
                try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:164", e) }
            }
            token == "system:glow_deflectors" -> {
                LightspeedAccessibilityService.instance?.triggerDeflectorsGlow()
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
                } catch (e: Exception) { logSwallowed(TAG, "execute:208", e) }
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
                } catch (e: Exception) { logSwallowed(TAG, "execute:226", e) }
            }
            token == "system:core_cooling" -> {
                LightspeedWatchdogEngine.executeCoreCoolingReboot(context)
            }
            token.startsWith("app:") -> {
                val pkg = token.removePrefix("app:")
                context.packageManager.getLaunchIntentForPackage(pkg)?.let { intent ->
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    if (km?.isKeyguardLocked == true && ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("input keyevent 82")
                    }
                    try { context.startActivity(intent) } catch (e: Exception) { logSwallowed(TAG, "execute:283", e) }
                }
            }
            token.startsWith("shortcut:") ||
            token.startsWith("custom:") ||
            token.startsWith("caller;") ||
            token.contains(";b64uri=") ||
            token.contains(";label=") ||
            token.contains("type=") -> {
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
                    try { context.startActivity(launchIntent) } catch (e: Exception) { logSwallowed(TAG, "execute:301", e) }
                } else {
                    Log.w(TAG, "Unhandled action token: $token")
                }
            }
        }
    }
}
