package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService

object ActionDispatcherSystemHelpers {
    private const val TAG = "ActionDispatcherHelpers"

    private var isTorchOn = false
    private var torchCallbackRegistered = false
    private var lastNoAccessToastMs: Long = 0L

    fun openRecents(context: Context, service: AccessibilityService?) {
        val success = service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true
        if (success) {
            Log.i(TAG, "Recents overview triggered via AccessibilityService")
            return
        }

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

        try {
            if (ElevatedTaskCloser.isShizukuActive) {
                ElevatedTaskCloser.execShizuku("input keyevent 187").waitForOrKill()
                Log.i(TAG, "Recents overview triggered via Shizuku KEYCODE_APP_SWITCH")
                return
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "openRecents:336", e)
        }

        Handler(Looper.getMainLooper()).postDelayed({
            val retryService = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            retryService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }, 30L)
    }

    fun expandNotifications(context: Context, service: AccessibilityService?) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandNotificationsPanel")
            method.invoke(statusBarService)
            return
        } catch (e: Exception) {
            logSwallowed(TAG, "expandNotifications:352", e)
        }
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    fun expandQuickSettings(context: Context, service: AccessibilityService?) {
        try {
            val statusBarService = context.getSystemService("statusbar")
            val statusBarManagerClass = Class.forName("android.app.StatusBarManager")
            val method = statusBarManagerClass.getMethod("expandSettingsPanel")
            method.invoke(statusBarService)
            return
        } catch (e: Exception) {
            logSwallowed(TAG, "expandQuickSettings:363", e)
        }
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    private fun ensureTorchCallback(cameraManager: CameraManager) {
        if (!torchCallbackRegistered) {
            try {
                cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        super.onTorchModeChanged(cameraId, enabled)
                        isTorchOn = enabled
                    }
                }, null)
                torchCallbackRegistered = true
            } catch (e: Exception) {
                logSwallowed(TAG, "onTorchModeChanged:380", e)
            }
        }
    }

    fun toggleFlashlight(context: Context) {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            ensureTorchCallback(cameraManager)
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
            isTorchOn = !isTorchOn
            cameraManager.setTorchMode(cameraId, isTorchOn)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle flashlight", e)
        }
    }

    fun warnIfNoNotificationAccess(context: Context) {
        try {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            if (flat?.contains(context.packageName) == true || LightspeedNotificationListener.instance != null) {
                return
            }
            val now = SystemClock.elapsedRealtime()
            if (now - lastNoAccessToastMs >= 15000) {
                lastNoAccessToastMs = now
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        context,
                        "Media control needs Notification access. Allow it for this app in Android settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (_: Exception) {
        }
    }
}
