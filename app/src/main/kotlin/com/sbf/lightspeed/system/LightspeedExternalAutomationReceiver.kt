package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opt-in external BroadcastReceiver for external automation tools (Tasker, MacroDroid, ADB).
 * Rejects all incoming intents unless explicitly enabled by the user in settings.
 */
class LightspeedExternalAutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isAllowed = context.defaultPrefs().getBoolean(
            LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION,
            false
        )
        if (!isAllowed) return

        LightspeedAutomationReceiver().onReceive(context, intent)
    }
}
