package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Opt-in external BroadcastReceiver for external automation tools (Tasker, MacroDroid, ADB).
 * Rejects all incoming intents unless explicitly enabled by the user in settings (KEY_ALLOW_EXTERNAL_AUTOMATION)
 * and verifies that any configured security token matches before dispatching to internal execution.
 */
class LightspeedExternalAutomationReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ExternalAutomation"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.defaultPrefs()
        val isAllowed = prefs.getBoolean(
            LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION,
            false
        )
        if (!isAllowed) {
            Log.w(TAG, "External automation broadcast rejected: KEY_ALLOW_EXTERNAL_AUTOMATION is disabled")
            return
        }

        // Verify security token if one is configured for stealth cloak / external automation
        val savedToken = prefs.getString(LightspeedPreferences.KEY_STEALTH_CLOAK_TOKEN, "").orEmpty().trim()
        if (savedToken.isNotEmpty()) {
            val providedToken = intent.getStringExtra(LightspeedAutomationReceiver.EXTRA_TOKEN).orEmpty().trim()
            if (savedToken != providedToken) {
                Log.w(TAG, "External automation broadcast rejected: Token mismatch or missing token")
                return
            }
        }

        LightspeedAutomationReceiver().onReceive(context, intent)
    }
}

