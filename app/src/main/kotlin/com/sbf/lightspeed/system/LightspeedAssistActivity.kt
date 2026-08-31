package com.sbf.lightspeed.system

import android.app.Activity
import android.os.Bundle
import android.util.Log

class LightspeedAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = defaultPrefs()
        val action = prefs.getString(LightspeedPreferences.KEY_POWER_LONG_PRESS_ACTION, "none")
        Log.i("LightspeedAssist", "Assist activity triggered. Dispatching action: $action")
        if (!action.isNullOrBlank() && action != "none") {
            ActionDispatcher.dispatch(this, action)
        }
        finish()
    }
}
