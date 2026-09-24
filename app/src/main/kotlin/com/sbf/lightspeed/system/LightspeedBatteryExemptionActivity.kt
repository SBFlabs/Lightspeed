package com.sbf.lightspeed.system

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.sbf.lightspeed.settings.BatteryExemptionDeckDialog
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class LightspeedBatteryExemptionActivity : ComponentActivity() {
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
                    BatteryExemptionDeckDialog(
                        context = this@LightspeedBatteryExemptionActivity,
                        onDismiss = {
                            finish()
                        },
                        onExemptionChanged = {}
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
