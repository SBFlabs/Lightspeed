package com.sbf.lightspeed.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class SidebarSettingsActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isActive: Boolean = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true
        enableEdgeToEdge()

        setContent {
            LightspeedTheme {
                MainSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        // Clear all preview highlights whenever settings leaves the screen — covers
        // every exit path: X button, background tap, back gesture, or an external
        // action (gesture launching another app) that kills the activity unexpectedly.
        defaultPrefs().edit()
            .putBoolean("pref_statusbar_preview", false)
            .putBoolean("pref_sidebar_preview", false)
            .putBoolean("pref_sidebar_left_preview", false)
            .apply()
        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
    }
}
