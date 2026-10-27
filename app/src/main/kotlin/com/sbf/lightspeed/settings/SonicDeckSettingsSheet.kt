package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun SonicDeckSettingsSheet(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors

    var customHudEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CUSTOM_VOLUME_HUD_ENABLED, true)) }
    var vocalBoost by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.vocalBoostLevel) }
    var bgMuffle by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.backgroundMuffleLevel) }
    var voiceIsolationEnabled by remember { mutableStateOf(SonicVoiceIsolationEngine.isEnabled) }

    val customApps = remember {
        val map = SonicAppVolumeEngine.getAllCustomAppVolumes(context).toMutableMap()
        val topFg = try {
            com.sbf.lightspeed.system.TaskInspector.getTopForegroundTaskInfo(context).packageName
        } catch (_: Exception) { null }
        if (!topFg.isNullOrBlank() && topFg != context.packageName && topFg != "com.android.systemui") {
            if (!map.containsKey(topFg)) {
                map[topFg] = SonicAppVolumeEngine.getAppVolume(context, topFg)
            }
        }
        for (pkg in SonicAppVolumeEngine.getActivePlayingPackages()) {
            if (!map.containsKey(pkg) && pkg != context.packageName && pkg != "com.android.systemui") {
                map[pkg] = SonicAppVolumeEngine.getAppVolume(context, pkg)
            }
        }
        map
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1. Custom Volume HUD Replacement Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("SONIC VOLUME HUD", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("Replaces stock OEM volume with theme-aware tactical HUD", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = customHudEnabled,
                    onCheckedChange = {
                        customHudEnabled = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_CUSTOM_VOLUME_HUD_ENABLED, it).apply()
                        onStateChanged()
                    }
                )
            }
        }

        // 2. GTCRN Neural Voice Isolation Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = lsColors.accentOk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("NEURAL CINEMA ISOLATION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("ONNX-powered GTCRN speech stem isolation", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = voiceIsolationEnabled,
                    onCheckedChange = {
                        voiceIsolationEnabled = it
                        SonicVoiceIsolationEngine.setEnabled(context, it)
                        onStateChanged()
                    }
                )
            }
            if (voiceIsolationEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Vocal Boost: ${(vocalBoost * 100).toInt()}%", fontSize = 11.sp, color = lsColors.accentOk)
                }
                Slider(
                    value = vocalBoost,
                    onValueChange = {
                        vocalBoost = it
                        SonicVoiceIsolationEngine.setVocalBoost(context, it)
                    }
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Background Muffle: ${(bgMuffle * 100).toInt()}%", fontSize = 11.sp, color = lsColors.accentWarn)
                }
                Slider(
                    value = bgMuffle,
                    onValueChange = {
                        bgMuffle = it
                        SonicVoiceIsolationEngine.setBackgroundMuffle(context, it)
                    }
                )
            }
        }

        // 3. Multi-Channel SplitAudio & Per-App Mixer Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Headphones, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("MULTI-CHANNEL ROUTING & MIXER", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Text("Speaker vs. Bluetooth routing & per-app gain up to 200%", fontSize = 10.sp, color = lsColors.textSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))

            if (customApps.isEmpty()) {
                Text("No custom app volume overrides active. Use the Sonic Volume HUD to customize active apps on the fly.", fontSize = 10.sp, color = lsColors.textSecondary)
            } else {
                for ((pkg, vol) in customApps.entries.take(5)) {
                    var appVol by remember(pkg) { mutableFloatStateOf(vol) }
                    var appRoute by remember(pkg) { mutableStateOf(SonicChannelRouter.getAppRoute(context, pkg)) }
                    var isPinned by remember(pkg) { mutableStateOf(SonicChannelRouter.isAlwaysOnAudioFocus(context, pkg)) }

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(pkg.substringAfterLast('.'), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                DeckButton(
                                    text = if (appRoute == SonicChannelRouter.ROUTE_SPEAKER) "SPK" else if (appRoute == SonicChannelRouter.ROUTE_BLUETOOTH) "BT" else "DEF",
                                    onClick = {
                                        val next = when (appRoute) {
                                            SonicChannelRouter.ROUTE_DEFAULT -> SonicChannelRouter.ROUTE_SPEAKER
                                            SonicChannelRouter.ROUTE_SPEAKER -> SonicChannelRouter.ROUTE_BLUETOOTH
                                            else -> SonicChannelRouter.ROUTE_DEFAULT
                                        }
                                        appRoute = next
                                        SonicChannelRouter.setAppRoute(context, pkg, next)
                                    }
                                )
                                DeckButton(
                                    text = if (isPinned) "PINNED" else "PIN",
                                    isActive = isPinned,
                                    onClick = {
                                        isPinned = !isPinned
                                        SonicChannelRouter.setAlwaysOnAudioFocus(context, pkg, isPinned)
                                    }
                                )
                            }
                        }
                        Slider(
                            value = appVol,
                            valueRange = 0.0f..2.0f,
                            onValueChange = {
                                appVol = it
                                SonicAppVolumeEngine.setAppVolume(context, pkg, it)
                            }
                        )
                    }
                }
            }
        }
    }
}
