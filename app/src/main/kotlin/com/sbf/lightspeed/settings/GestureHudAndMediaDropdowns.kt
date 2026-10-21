package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Dropdown menus for HUD Style selection, Media Skip interval, and Quick Media Action presets.
 */
@Composable
fun GestureHudStyleDropdown(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    var hudStyle by remember(currentRawValue, key) {
        mutableStateOf(LightspeedPreferences.resolveHudStyle(prefs, key, currentRawValue))
    }
    var showHudMenu by remember { mutableStateOf(false) }

    Box {
        val hudName = when (hudStyle) {
            "canopy_droppod" -> "Drop-Pod"
            "cockpit_reticle" -> "Reticle"
            "edge_blade" -> "Blade"
            "quantum_horizon" -> "Horizon"
            "tachyon_dial" -> "Radar"
            else -> "Drop-Pod"
        }
        DeckSubChip(
            text = "$hudName ▾",
            icon = Icons.Default.Palette,
            isActive = true,
            snapshot = activeSnapshot,
            onClick = {
                hudStyle = LightspeedPreferences.resolveHudStyle(prefs, key, currentRawValue)
                showHudMenu = true
            }
        )
        DeckThemedDropdownMenu(
            expanded = showHudMenu,
            onDismissRequest = { showHudMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            listOf(
                "canopy_droppod" to "Tactical Canopy Drop-Pod",
                "cockpit_reticle" to "Holographic Cockpit Reticle",
                "edge_blade" to "Dynamic Edge Blade",
                "quantum_horizon" to "Quantum Synthetic Horizon",
                "tachyon_dial" to "Tachyon Orbital Radar"
            ).forEach { (styleKey, styleTitle) ->
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = if (styleKey == hudStyle) "✓ $styleTitle" else styleTitle,
                            fontWeight = if (styleKey == hudStyle) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        hudStyle = styleKey
                        LightspeedPreferences.saveHudStyle(prefs, key, currentRawValue, styleKey)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHudAndMediaDropdowns", "SaveHudStyle", e) }
                        showHudMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun GestureMediaSkipDropdown(
    context: Context,
    prefs: SharedPreferences,
    currentRawValue: String,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    val isForward = currentRawValue == "system:media_skip_forward"
    var currentSkipSec by remember(currentRawValue) {
        mutableStateOf(LightspeedPreferences.getMediaSkipSeconds(prefs, forward = isForward))
    }
    var showSkipMenu by remember { mutableStateOf(false) }

    Box {
        DeckSubChip(
            text = "Skip: ${currentSkipSec}s ▾",
            isActive = false,
            snapshot = activeSnapshot,
            onClick = { showSkipMenu = true }
        )
        DeckThemedDropdownMenu(
            expanded = showSkipMenu,
            onDismissRequest = { showSkipMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            listOf(5, 10, 15, 30, 60).forEach { sec ->
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = if (sec == currentSkipSec) "✓ ${sec}s Interval" else "${sec}s Interval",
                            fontWeight = if (sec == currentSkipSec) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        currentSkipSec = sec
                        LightspeedPreferences.setMediaSkipSeconds(prefs, forward = isForward, sec = sec)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHudAndMediaDropdowns", "SetMediaSkipSeconds", e) }
                        showSkipMenu = false
                    }
                )
            }
        }
    }
}

@Composable
fun GestureMediaQuickAccessControl(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    onActionSelected: (String) -> Unit,
    activeSnapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    var showMediaQuickMenu by remember { mutableStateOf(false) }

    Box {
        DeckSubChip(
            text = "Media ▾",
            icon = Icons.Default.PlayArrow,
            isActive = currentRawValue.startsWith("system:media_"),
            snapshot = activeSnapshot,
            onClick = { showMediaQuickMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showMediaQuickMenu,
            onDismissRequest = { showMediaQuickMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            val skipForwardSec = LightspeedPreferences.getMediaSkipSeconds(prefs, forward = true)
            val skipBackwardSec = LightspeedPreferences.getMediaSkipSeconds(prefs, forward = false)
            val mediaItems = listOf(
                "system:media_play_pause" to "Play / Pause",
                "system:media_next" to "Next Track",
                "system:media_prev" to "Previous Track",
                "system:media_skip_forward" to "Skip Forward (${skipForwardSec}s)",
                "system:media_skip_backward" to "Skip Backward (${skipBackwardSec}s)",
                "system:media_scrubber" to "Media Timeline Scrubber (HUD)",
                "system:media_stop" to "Stop Playback"
            )

            mediaItems.forEach { (token, title) ->
                val isSelected = currentRawValue == token
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = if (isSelected) "✓ $title" else title,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    onClick = {
                        onActionSelected(token)
                        prefs.edit().putString(key, token).apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHudAndMediaDropdowns", "GestureMediaQuickAccessControl", e) }
                        showMediaQuickMenu = false
                    }
                )
            }
        }
    }
}
