package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Compact 28.dp sub-chip with themed dropdown for gesture and scrub tactile feedback.
 */
@Composable
fun GestureScrubHapticsSelector(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot,
    readableTint: Color
) {
    var selectedProfile by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS, "tactical") ?: "tactical")
    }
    var showMenu by remember { mutableStateOf(false) }

    val profiles = listOf(
        "off" to "Off",
        "jumps_only" to "Transitions Only",
        "soft_low_freq" to "Soft (Low-Freq)",
        "tactical" to "Crisp (Standard)",
        "heavy" to "Heavy (Mechanical)"
    )

    val currentLabel = when (selectedProfile) {
        "off" -> "Off"
        "jumps_only" -> "Transitions"
        "soft_low_freq" -> "Soft"
        "heavy" -> "Heavy"
        else -> "Crisp"
    }

    Box {
        DeckSubChip(
            text = "Haptics: $currentLabel ▾",
            icon = Icons.Default.Vibration,
            isActive = selectedProfile != "off",
            snapshot = activeSnapshot,
            onClick = { showMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            profiles.forEach { (key, label) ->
                val isSelected = selectedProfile == key
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 44.dp),
                    text = {
                        Text(
                            text = if (isSelected) "✓ $label" else label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    onClick = {
                        selectedProfile = key
                        prefs.edit().putString(LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS, key).apply()
                        when (key) {
                            "off" -> {}
                            "jumps_only" -> LightspeedHapticEngine.click(context)
                            "soft_low_freq" -> LightspeedHapticEngine.tick(context)
                            "heavy" -> LightspeedHapticEngine.heavyClick(context)
                            else -> LightspeedHapticEngine.tick(context)
                        }
                        showMenu = false
                    }
                )
            }
        }
    }
}
