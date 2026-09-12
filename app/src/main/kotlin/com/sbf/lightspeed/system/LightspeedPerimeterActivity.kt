package com.sbf.lightspeed.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sbf.lightspeed.settings.PerimeterServicesDeckDialog
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class LightspeedPerimeterActivity : ComponentActivity() {
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
                    PerimeterServicesDeckDialog(
                        context = this@LightspeedPerimeterActivity,
                        prefs = defaultPrefs(),
                        onDismiss = {
                            finishAndRemoveTask()
                            overridePendingTransition(0, 0)
                        },
                        onRefreshNeeded = {}
                    )
                }
            }
        }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overridePendingTransition(0, 0)
    }
}
