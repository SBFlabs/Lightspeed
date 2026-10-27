package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.bot.LightspeedBotContract
import com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Cybernetic Trackpad Main Accordion for Deflector Tabs (< 100 lines).
 * Houses master enable toggle and 2 modular sub-accordions:
 * 1. Geometry & Calibration (with automatic live trigger zone preview)
 * 2. Cursor Physics & Timing
 */
@Composable
fun CyberneticTrackpadAccordion(
    isLeft: Boolean,
    context: Context,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val flankKey = if (isLeft) "left" else "right"
    val enabledKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED else LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED

    var isExpanded by remember(prefs.getBoolean("pref_accordion_trackpad_$flankKey", false), flankKey) {
        mutableStateOf(prefs.getBoolean("pref_accordion_trackpad_$flankKey", false))
    }
    var isEnabled by remember(prefs.getBoolean(enabledKey, false), enabledKey) {
        mutableStateOf(prefs.getBoolean(enabledKey, false))
    }

    CompactAccordionSection(
        title = "Cybernetic Trackpad",
        isExpanded = isExpanded,
        onToggle = {
            isExpanded = !isExpanded
            prefs.edit().putBoolean("pref_accordion_trackpad_$flankKey", isExpanded).apply()
        },
        icon = {
            Icon(
                imageVector = Icons.Default.ControlCamera,
                contentDescription = null,
                tint = lsColors.accentInfo,
                modifier = Modifier.size(20.dp)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Master Flank Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enable Cybernetic Trackpad", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Text("Half-circle cursor trackpad with 3 programmable macro segments (Tap = Play, Hold = Record, Tap+Hold = Edit)", fontSize = 10.sp, color = lsColors.textSecondary)
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = {
                        isEnabled = it
                        prefs.edit().putBoolean(enabledKey, it).apply()
                        LightspeedBotTrackpadOverlay.reload(context)
                        onRefreshNeeded()
                    }
                )
            }

            if (isEnabled) {
                HorizontalDivider(color = lsColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.8.dp)

                // Sub-Accordion 1: Geometry & Calibration (auto-enables Live Trigger Zone Preview when expanded)
                TrackpadGeometrySubSection(isLeft = isLeft, context = context, prefs = prefs, isParentExpanded = isExpanded)

                // Sub-Accordion 2: Cursor Physics & Timing
                TrackpadPhysicsSubSection(isLeft = isLeft, prefs = prefs)
            }
        }
    }
}
