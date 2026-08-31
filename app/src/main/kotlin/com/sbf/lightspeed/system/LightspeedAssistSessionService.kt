package com.sbf.lightspeed.system

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Intercepts digital assistant triggers (long-press Power button) and dispatches mapped cockpit actions.
 */
class LightspeedAssistSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return LightspeedAssistSession(this)
    }

    private class LightspeedAssistSession(context: Context) : VoiceInteractionSession(context) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            try {
                hide()
            } catch (_: Exception) {}

            val prefs = context.defaultPrefs()
            val action = prefs.getString(LightspeedPreferences.KEY_POWER_LONG_PRESS_ACTION, "none")
            Log.i("LightspeedAssist", "Assist session triggered via Power long-press. Dispatching action: $action")
            if (!action.isNullOrBlank() && action != "none") {
                ActionDispatcher.dispatch(context, action)
            }
        }
    }
}
