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
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.lsString

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
        "off" to lsString(R.string.hap_prof_off),
        "jumps_only" to lsString(R.string.hap_prof_trans_only),
        "soft_low_freq" to lsString(R.string.hap_prof_soft_full),
        "tactical" to lsString(R.string.hap_prof_crisp_full),
        "heavy" to lsString(R.string.hap_prof_heavy_full)
    )

    val currentLabel = when (selectedProfile) {
        "off" -> lsString(R.string.hap_prof_off)
        "jumps_only" -> lsString(R.string.hap_prof_trans)
        "soft_low_freq" -> lsString(R.string.hap_prof_soft)
        "heavy" -> lsString(R.string.hap_prof_heavy)
        else -> lsString(R.string.hap_prof_crisp)
    }

    Box {
        DeckSubChip(
            text = lsString(R.string.hap_chip_label, currentLabel),
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
