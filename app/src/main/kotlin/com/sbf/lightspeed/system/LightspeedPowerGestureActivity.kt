package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.PowerTriggerSlot

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

                if (ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("input keyevent 82")
                }

                val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                if (km?.isKeyguardLocked == true) {
                    km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            super.onDismissSucceeded()
                            finish()
                        }

                        override fun onDismissError() {
                            super.onDismissError()
                            finish()
                        }

                        override fun onDismissCancelled() {
                            super.onDismissCancelled()
                            finish()
                        }
                    })
                    window.decorView.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            finish()
                        }
                    }, 350)
                } else {
                    finish()
                }
            } else {
                launchDefaultCamera()
                finish()
            }
        } catch (_: Exception) {
            launchDefaultCamera()
            finish()
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

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }
}
