package com.sbf.lightspeed

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.sbf.lightspeed.system.ShizukuGate

@Composable
fun ShizukuChoiceDialog() {
    var show by remember { mutableStateOf(ShizukuGate.needsChoice()) }
    if (!show) return
    AlertDialog(
        onDismissRequest = {},
        title = { Text("How should Lightspeed work?") },
        text = { Text("Full features uses Shizuku for extras like closing apps, split screen and shortcuts. Accessibility Service only works without Shizuku and never asks for it. You can change this later in settings.") },
        confirmButton = {
            TextButton(onClick = { ShizukuGate.chooseFull(); show = false }) {
                Text("Full features (Shizuku)")
            }
        },
        dismissButton = {
            TextButton(onClick = { ShizukuGate.chooseAccessibilityOnly(); show = false }) {
                Text("Accessibility Service only")
            }
        }
    )
}
