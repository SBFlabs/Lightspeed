package com.sbf.lightspeed

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.system.ShizukuGate

@Composable
fun ShizukuChoiceDialog() {
    var show by remember { mutableStateOf(ShizukuGate.needsChoice()) }
    if (!show) return
    DeckDialog(
        onDismissRequest = {},
        title = { Text("How should Lightspeed work?") },
        text = { Text("Full features uses Shizuku for extras like closing apps, split screen and shortcuts. Accessibility Service only works without Shizuku and never asks for it. You can change this later in settings.") },
        confirmButton = {
            DeckButton(
                text = "Full features (Shizuku)",
                onClick = { ShizukuGate.chooseFull(); show = false },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = "Accessibility only",
                onClick = { ShizukuGate.chooseAccessibilityOnly(); show = false }
            )
        }
    )
}
