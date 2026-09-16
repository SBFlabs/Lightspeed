package com.sbf.lightspeed.settings

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.overrideZeroTransition
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class CentralCommandActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isActive: Boolean = false
            private set
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true
        enableEdgeToEdge()

        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        setContent {
            val backdrop = rememberDeckBackdropVisuals(this)
            SideEffect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = window.attributes
                    lp.blurBehindRadius = backdrop.blurBehindRadius
                    window.attributes = lp
                }
                window.setDimAmount(backdrop.dimAmount)
            }

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
        overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
    }
}
