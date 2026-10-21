package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.logSwallowed
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.normalizeRefuelingTimeout
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun HudRefuelingBaySection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onShowAmoledWarning: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val (refuelingTitle, refuelingSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.REFUELING_BAY, currentLanguageMode)

    CompactAccordionSection(
        title = refuelingTitle,
        subtitle = refuelingSub,
        icon = {
            Icon(
                imageVector = Icons.Outlined.BatteryChargingFull,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Operational Recommendation: Manual Trigger Preferred
            DeckCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Lightbulb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp).padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Operational Note: Manual Trigger Recommended",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Automatic charging trigger is disabled by default to avoid screen collisions with OEM Always-On Displays. Keep it off and trigger Refueling Bay manually on demand (via gesture, shortcut, or the launch button below).",
                            fontSize = 11.sp,
                            color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
                            lineHeight = 14.5.sp
                        )
                    }
                }
            }

            val isInfinixOrTranssion = remember {
                val m = Build.MANUFACTURER.lowercase()
                val b = Build.BRAND.lowercase()
                m.contains("infinix") || m.contains("transsion") || m.contains("tecno") || m.contains("itel") ||
                b.contains("infinix") || b.contains("transsion") || b.contains("tecno") || b.contains("itel")
            }
            var isWarningDemoted by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_INFINIX_STANDBY_WARNING_DEMOTED, false)) }

            if (isInfinixOrTranssion && !isWarningDemoted) {
                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "OEM Ambient Display / Dock Conflicts",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LightspeedStyle.colors.textPrimary
                                )
                                Text(
                                    text = "OEM standby display style may overlap with Refueling Bay. Disable it in system settings to prevent screen collisions.",
                                    fontSize = 11.sp,
                                    color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.85f),
                                    lineHeight = 14.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    isWarningDemoted = true
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_INFINIX_STANDBY_WARNING_DEMOTED, true).apply()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerticalAlignBottom,
                                    contentDescription = "Demote to Footnote",
                                    tint = LightspeedStyle.colors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (e: Exception) { logSwallowed("HudHardwareRefuelingComponents", "HudRefuelingBaySection:666", e) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open OEM Standby Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            val currentTrigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
            var isTriggerDropdownOpen by remember { mutableStateOf(false) }
            val triggerOptions = listOf(
                "disabled" to "Disabled (Manual Launch Only)",
                "charging_screen_off" to "Screen-Off While Charging / Plugged While Locked",
                "charging_dock_landscape" to "Charging in Landscape Dock Orientation",
                "screen_timeout" to "Screen-Off / Standby (Even When Not Charging)",
                "screensaver_only" to "Android Screensaver (DreamService Only)"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("AUTO-LAUNCH CHARGING TRIGGER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isTriggerDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                triggerOptions.firstOrNull {
                                    it.first == currentTrigger ||
                                    (it.first == "charging_dock_landscape" && currentTrigger == "landscape_charging") ||
                                    (it.first == "charging_screen_off" && currentTrigger == "always_charging")
                                }?.second ?: "Disabled",
                                color = LightspeedStyle.colors.textPrimary,
                                fontSize = 12.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeckThemedDropdownMenu(
                        expanded = isTriggerDropdownOpen,
                        onDismissRequest = { isTriggerDropdownOpen = false }
                    ) {
                        triggerOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isTriggerDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, key).apply()
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            val currentDismissBehavior = prefs.getString(LightspeedPreferences.KEY_REFUELING_DISMISS_BEHAVIOR, "session") ?: "session"
            var isDismissDropdownOpen by remember { mutableStateOf(false) }
            val dismissOptions = listOf(
                "session" to "Until unplugged (default)",
                "screen_off" to "Until the screen turns off"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("WHEN DISMISSED", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isDismissDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                dismissOptions.firstOrNull { it.first == currentDismissBehavior }?.second ?: "Until unplugged (default)",
                                color = LightspeedStyle.colors.textPrimary,
                                fontSize = 12.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeckThemedDropdownMenu(
                        expanded = isDismissDropdownOpen,
                        onDismissRequest = { isDismissDropdownOpen = false }
                    ) {
                        dismissOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isDismissDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_DISMISS_BEHAVIOR, key).apply()
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            var isSleepBlackoutEnabled by remember {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_SLEEP_BLACKOUT, true))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        text = "Cover overlays while the Bay sleeps",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Draws black over Lightspeed's side deflectors and other apps' overlays during Bay sleep, to protect OLED screens",
                        fontSize = 11.sp,
                        color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = isSleepBlackoutEnabled,
                    onCheckedChange = { checked ->
                        isSleepBlackoutEnabled = checked
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_SLEEP_BLACKOUT, checked).apply()
                        onRefreshNeeded()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }


            val currentTimeout = normalizeRefuelingTimeout(prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s")
            var isTimeoutDropdownOpen by remember { mutableStateOf(false) }
            val timeoutOptions = listOf(
                "5s" to "5 Seconds (Rapid Sleep Test)",
                "15s" to "15 Seconds",
                "30s" to "30 Seconds",
                "60s" to "60 Seconds (Recommended)",
                "120s" to "2 Minutes",
                "300s" to "5 Minutes",
                "never" to "Never (⚠️ AMOLED Risk)"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("INACTIVITY SLEEP TIMEOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isTimeoutDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(timeoutOptions.firstOrNull { it.first == currentTimeout }?.second ?: "60 Seconds", color = LightspeedStyle.colors.textPrimary, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeckThemedDropdownMenu(
                        expanded = isTimeoutDropdownOpen,
                        onDismissRequest = { isTimeoutDropdownOpen = false }
                    ) {
                        timeoutOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isTimeoutDropdownOpen = false
                                    if (key == "never") {
                                        onShowAmoledWarning()
                                    } else {
                                        prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, key).apply()
                                        onRefreshNeeded()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE,
                defaultVal = false,
                title = "Remember Smart Stack Active Page",
                subtitle = "Resume the last active widget in Smart Stack instead of resetting to the first widget on launch.",
                onChanged = { onRefreshNeeded() }
            )

            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_REFUELING_PIXEL_SHIFT,
                defaultVal = true,
                title = "Enable Pixel Shift Burn-In Shield",
                subtitle = "Subtly shifts text and indicators by 2-4px every 2 minutes to protect OLED panels.",
                onChanged = { onRefreshNeeded() }
            )

            Button(
                onClick = {
                    val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Launch Refueling Bay Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts (Demoted)
            if (isWarningDemoted && isInfinixOrTranssion) {
                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .border(1.dp, LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                Text("OEM standby display style may overlap with Refueling Bay. Tap to manage settings.", fontSize = 9.5.sp, color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.65f), lineHeight = 13.sp)
                            }
                            IconButton(
                                onClick = {
                                    isWarningDemoted = false
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_INFINIX_STANDBY_WARNING_DEMOTED, false).apply()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Move Upward", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                            }
                        }
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        })
                                    } catch (e: Exception) { logSwallowed("HudHardwareRefuelingComponents", "HudRefuelingBaySection:881", e) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
