package com.sbf.lightspeed.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sbf.lightspeed.overrideZeroTransition
import com.sbf.lightspeed.settings.CentralCommandDeckDialog
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/**
 * Lightweight, zero-transition floating activity for the Core Watchdog action.
 * Directly launches the Flight Control Deck scrolled to System Immunity & Watchdogs.
 */
class LightspeedCoreWatchdogActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prepareForCentralCommand(windowManager) {
            com.sbf.lightspeed.settings.CentralCommandBackdropBlur.applyStandaloneActivityBlur(this)
        }
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.applyStandaloneActivityBlur(this)

        setContent {
            LightspeedTheme(forceDark = true) {
                DeckTheme {
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
    }

    override fun onResume() {
        super.onResume()
        if (com.sbf.lightspeed.settings.CentralCommandBackdropBlur.backdropBitmap == null) {
            com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prepareForCentralCommand(windowManager) {
                com.sbf.lightspeed.settings.CentralCommandBackdropBlur.applyStandaloneActivityBlur(this)
            }
        }
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.applyStandaloneActivityBlur(this)
    }

    override fun onStop() {
        super.onStop()
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }
}
