package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.os.Process
import android.provider.MediaStore
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.overrideZeroTransition
import com.sbf.lightspeed.system.PowerTriggerSlot

/**
 * Intercepts the native Android double-click power hardware trigger.
 * Operates over the lockscreen with zero latency and executes the user's remapped power action.
 */
class LightspeedPowerGestureActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isAuthorizedCaller()) {
            finish()
            return
        }

        val wasScreenOn = LightspeedKeyEngine.wasScreenInteractiveAtDown

        try {
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            LightspeedKeyEngine.onPowerGestureHandled()
            val boundAction = LightspeedKeyEngine.getBoundPowerAction(this, PowerTriggerSlot.POWER_DOUBLE_PRESS)

            if (!boundAction.isNullOrBlank() && boundAction != "none") {
                LightspeedHapticEngine.click(this)
                ActionDispatcher.dispatch(boundAction, this)

                if (km?.isKeyguardLocked == true && ElevatedTaskCloser.isShizukuActive) {
                    ElevatedTaskCloser.execShizuku("input keyevent 82")
                }

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
            val normalIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val secureMatch = pm.queryIntentActivities(secureIntent, 0)
                .firstOrNull { it.activityInfo.packageName != packageName }
            if (secureMatch != null) {
                secureIntent.component = ComponentName(
                    secureMatch.activityInfo.packageName,
                    secureMatch.activityInfo.name
                )
                startActivity(secureIntent)
                return
            }

            val normalMatch = pm.queryIntentActivities(normalIntent, 0)
                .firstOrNull { it.activityInfo.packageName != packageName }
            if (normalMatch != null) {
                normalIntent.component = ComponentName(
                    normalMatch.activityInfo.packageName,
                    normalMatch.activityInfo.name
                )
                startActivity(normalIntent)
                return
            }
        } catch (e: Exception) { logSwallowed("LightspeedPowerGestureActivity", "launchDefaultCamera", e) }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }

    private fun isAuthorizedCaller(): Boolean {
        intent.removeExtra(Intent.EXTRA_REFERRER)
        intent.removeExtra(Intent.EXTRA_REFERRER_NAME)
        val callerPackage = callingPackage ?: referrer?.host ?: referrer?.authority ?: return true
        if (callerPackage == packageName) return true
        if (callerPackage == "android" || callerPackage == "com.android.systemui") return true
        return try {
            val info = packageManager.getApplicationInfo(callerPackage, 0)
            (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
            (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
            info.uid == Process.SYSTEM_UID
        } catch (_: Exception) {
            false
        }
    }
}
