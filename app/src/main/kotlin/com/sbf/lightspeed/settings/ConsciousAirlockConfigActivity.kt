package com.sbf.lightspeed.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/**
 * Dedicated Dialog Activity for Conscious Airlock Gated Applications.
 * Cleanly callable from both Settings Activities and Accessibility Overlay Windows (e.g. Vista Deck)
 * with zero WindowManager BadTokenException risks.
 */
class ConsciousAirlockConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LightspeedTheme(forceDark = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .widthIn(max = 380.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        DeckGlassCardSurface(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ConsciousAirlockAppPickerContent(
                                context = this@ConsciousAirlockConfigActivity,
                                prefs = defaultPrefs(),
                                onDismissRequest = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }
}
