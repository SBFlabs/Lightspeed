package com.sbf.lightspeed.system

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot

/**
 * Intercepts the native Android double-click power hardware trigger.
 * Operates over the lockscreen with zero latency and executes the user's remapped power action.
 */
class LightspeedPowerGestureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wasScreenOn = LightspeedKeyEngine.wasScreenInteractiveAtDown

        try {
            LightspeedKeyEngine.onPowerGestureHandled()
            val boundAction = LightspeedKeyEngine.getBoundPowerAction(this, PowerTriggerSlot.POWER_DOUBLE_PRESS)

            if (!boundAction.isNullOrBlank() && boundAction != "none") {
                LightspeedHapticEngine.click(this)
                ActionDispatcher.dispatch(boundAction, this)

                val isBackgroundAction = boundAction.startsWith("system:torch") ||
                        boundAction.startsWith("system:media") ||
                        boundAction.startsWith("system:volume") ||
                        boundAction.startsWith("system:mute") ||
                        boundAction.startsWith("system:brightness")

                if (wasScreenOn) {
                    val km = getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    km?.requestDismissKeyguard(this, null)
                    if (ElevatedTaskCloser.isShizukuActive) {
                        ElevatedTaskCloser.execShizuku("input keyevent 82")
                    }
                } else {
                    if (isBackgroundAction) {
                        LightspeedAccessibilityService.instance?.performGlobalAction(
                            android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
                        )
                    }
                }
            } else {
                launchDefaultCamera()
            }
        } catch (_: Exception) {
            launchDefaultCamera()
        } finally {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun launchDefaultCamera() {
        try {
            val pm = packageManager
            val secureIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val target = if (secureIntent.resolveActivity(pm) != null) secureIntent else intent
            startActivity(target)
        } catch (_: Exception) {}
    }
}
