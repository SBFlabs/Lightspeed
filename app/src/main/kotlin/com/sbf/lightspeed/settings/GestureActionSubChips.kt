package com.sbf.lightspeed.settings

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.SonicDeckManager
import com.sbf.lightspeed.system.logSwallowed

/**
 * Dedicated sub-chips for Volume (Sonic Deck) and Brightness (Wellness & Video Takeover).
 */
@Composable
fun GestureVolumeSubChips(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot
) {
    // Open Sonic Deck Configuration Modal
    DeckSubChip(
        text = "Sonic Deck ⚙",
        icon = Icons.Default.GraphicEq,
        isActive = false,
        snapshot = activeSnapshot,
        onClick = {
            val a11y = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            if (a11y != null) {
                SonicDeckManager.show(a11y)
            }
        }
    )
}

@Composable
fun GestureBrightnessSubChips(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot
) {
    var autoResetMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF) ?: LightspeedPreferences.AUTO_RESET_OFF)
    }
    var showResetMenu by remember { mutableStateOf(false) }
    var showVideoTakeoverInfoDialog by remember { mutableStateOf(false) }

    // Info chip: explains when Lightspeed's invisible overlay overrides video-player brightness gestures
    DeckSubChip(
        text = "Video Override ⓘ",
        icon = Icons.Default.Videocam,
        isActive = false,
        snapshot = activeSnapshot,
        onClick = { showVideoTakeoverInfoDialog = true }
    )

    if (showVideoTakeoverInfoDialog) {
        val colorScheme = MaterialTheme.colorScheme
        DeckDialog(
            onDismissRequest = { showVideoTakeoverInfoDialog = false },
            title = {
                Text(
                    text = "Video Override",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Above 100% and below 1% (Extra Dim), Lightspeed's invisible overlay takes precedence, so apps with their own video brightness gestures are overridden. Between 1% and 100%, native Android brightness is left untouched.",
                    fontSize = 12.sp,
                    color = colorScheme.onSurface.copy(alpha = 0.82f)
                )
            },
            confirmButton = {
                TextButton(onClick = { showVideoTakeoverInfoDialog = false }) {
                    Text("GOT IT", fontWeight = FontWeight.Bold, color = colorScheme.primary)
                }
            }
        )
    }

    // Open Vista Deck Configuration Modal
    DeckSubChip(
        text = "Vista Deck ⚙",
        icon = Icons.Default.Brightness4,
        isActive = false,
        snapshot = activeSnapshot,
        onClick = {
            val a11y = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            if (a11y != null) {
                com.sbf.lightspeed.system.VistaDeckManager.show(a11y)
            }
        }
    )

    // Dropdown: Auto-Brightness Reset Mode
    Box {
        val resetLabel = when (autoResetMode) {
            LightspeedPreferences.AUTO_RESET_SCREEN_OFF -> "Auto-Reset: Screen Off ▾"
            LightspeedPreferences.AUTO_RESET_APP_SWITCH -> "Auto-Reset: App Switch ▾"
            LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE -> "Auto-Reset: Recents ▾"
            else -> "Auto-Reset: Off ▾"
        }

        DeckSubChip(
            text = resetLabel,
            icon = Icons.Default.RestartAlt,
            isActive = autoResetMode != LightspeedPreferences.AUTO_RESET_OFF,
            snapshot = activeSnapshot,
            onClick = { showResetMenu = true }
        )

        DeckThemedDropdownMenu(
            expanded = showResetMenu,
            onDismissRequest = { showResetMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            val modes: List<Pair<String, String>> = listOf(
                Pair(LightspeedPreferences.AUTO_RESET_OFF, "Off (Manual Only)"),
                Pair(LightspeedPreferences.AUTO_RESET_SCREEN_OFF, "Screen Off"),
                Pair(LightspeedPreferences.AUTO_RESET_APP_SWITCH, "App Switch"),
                Pair(LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE, "App Closed from Recents")
            )

            modes.forEach { (modeKey, modeTitle) ->
                val isSelected = autoResetMode == modeKey
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Text(
                            text = if (isSelected) "✓ $modeTitle" else modeTitle,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    onClick = {
                        autoResetMode = modeKey
                        prefs.edit().putString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, modeKey).apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureActionSubChips", "ResetModeSelect", e) }
                        showResetMenu = false
                    }
                )
            }
        }
    }
}
