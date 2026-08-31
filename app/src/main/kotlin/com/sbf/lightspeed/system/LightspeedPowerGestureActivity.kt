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

        try {
            val boundAction = LightspeedKeyEngine.getBoundPowerAction(this, PowerTriggerSlot.POWER_DOUBLE_PRESS)

            if (!boundAction.isNullOrBlank() && boundAction != "none") {
                LightspeedHapticEngine.click(this)
                ActionDispatcher.dispatch(boundAction, this)
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
