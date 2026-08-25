package com.sbf.lightspeed.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class SidebarSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LightspeedTheme {
                MainSettingsScreen()
            }
        }
    }
}
