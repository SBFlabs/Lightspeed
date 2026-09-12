package com.sbf.lightspeed.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import com.sbf.lightspeed.settings.PerimeterServicesDeckDialog
import com.google.accompanist.systemuicontroller.rememberSystemUiController

class LightspeedPerimeterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val systemUiController = rememberSystemUiController()
            SideEffect {
                systemUiController.setSystemBarsColor(
                    color = Color.Transparent,
                    darkIcons = false
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PerimeterServicesDeckDialog(
                    context = this@LightspeedPerimeterActivity,
                    prefs = defaultPrefs(),
                    onDismiss = { finish() },
                    onRefreshNeeded = {}
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }
}
