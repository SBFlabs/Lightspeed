package com.sbf.lightspeed.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sbf.lightspeed.settings.CentralCommandDeckDialog
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/**
 * Lightweight, zero-transition floating activity for the Core Watchdog action.
 * Directly launches the Flight Control Deck scrolled to System Immunity & Watchdogs.
 */
class LightspeedCoreWatchdogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDimAmount(0.65f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        setContent {
            LightspeedTheme(forceDark = true) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CentralCommandDeckDialog(
                        context = this@LightspeedCoreWatchdogActivity,
                        prefs = defaultPrefs(),
                        onDismiss = {
                            finish()
                        },
                        onRefreshNeeded = {},
                        initialScrollToWatchdog = true
                    )
                }
            }
        }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }
}
