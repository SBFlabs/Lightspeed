package com.sbf.lightspeed.system

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

@Composable
internal fun SonicDialogueSection(
    context: Context,
    snapshot: DeckThemeSnapshot
) {
    var isEnabled by remember { mutableStateOf(SonicVoiceIsolationEngine.isEnabled) }
    var activePreset by remember { mutableStateOf(SonicVoiceIsolationEngine.activePreset) }
    var vocalBoost by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.vocalBoostLevel) }
    var bgMuffle by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.backgroundMuffleLevel) }
    val tint = Color(snapshot.tintArgb.toInt())

    val presets = remember {
        listOf(
            SonicVoiceIsolationEngine.PRESET_CINEMA to "Cinema",
            SonicVoiceIsolationEngine.PRESET_PODCAST to "Podcast",
            SonicVoiceIsolationEngine.PRESET_GAMING to "Gaming",
            SonicVoiceIsolationEngine.PRESET_MAX to "Max",
            SonicVoiceIsolationEngine.PRESET_CUSTOM to "Custom"
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text("CINEMA DIALOGUE & AI ISOLATION", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Switch(
                checked = isEnabled,
                onCheckedChange = {
                    isEnabled = it
                    SonicVoiceIsolationEngine.setEnabled(context, it)
                    vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                    bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                    activePreset = SonicVoiceIsolationEngine.activePreset
                },
                modifier = Modifier.height(24.dp)
            )
        }

        // 5 Acoustic Presets Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for ((presetId, label) in presets) {
                val isSelected = activePreset == presetId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                        .border(
                            width = if (isSelected) 1.dp else 0.6.dp,
                            color = if (isSelected) tint else Color.White.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable {
                            activePreset = presetId
                            SonicVoiceIsolationEngine.applyPreset(context, presetId)
                            vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                            bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                        }
                        .padding(vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 9.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Vocal Boost: +${(vocalBoost * 8.0f).toInt()}dB", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
            Text("${(vocalBoost * 100).toInt()}%", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = vocalBoost,
            onValueChange = {
                vocalBoost = it
                activePreset = SonicVoiceIsolationEngine.PRESET_CUSTOM
                SonicVoiceIsolationEngine.setVocalBoost(context, it)
            },
            modifier = Modifier.height(24.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Film Background Muffle", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
            Text("${(bgMuffle * 100).toInt()}%", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = bgMuffle,
            onValueChange = {
                bgMuffle = it
                activePreset = SonicVoiceIsolationEngine.PRESET_CUSTOM
                SonicVoiceIsolationEngine.setBackgroundMuffle(context, it)
            },
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
internal fun SonicMasterStreamsSection(
    context: Context,
    audioManager: AudioManager,
    snapshot: DeckThemeSnapshot
) {
    val tint = Color(snapshot.tintArgb.toInt())

    fun readStreamRatio(stream: Int, fallbackMax: Float): Float {
        val max = try { audioManager.getStreamMaxVolume(stream).toFloat().coerceAtLeast(1f) } catch (_: Exception) { fallbackMax }
        val cur = try { audioManager.getStreamVolume(stream).toFloat() } catch (_: Exception) { fallbackMax * 0.7f }
        return (cur / max).coerceIn(0f, 1f)
    }

    var mediaVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_MUSIC, 15f)) }
    var ringVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_RING, 7f)) }
    var notifVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_NOTIFICATION, 7f)) }
    var alarmVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_ALARM, 7f)) }
    var callVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_VOICE_CALL, 7f)) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text("MASTER & SYSTEM STREAMS", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        StreamSliderRow("Media", mediaVol, tint) {
            mediaVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow("Ringtone", ringVol, tint) {
            ringVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow("Notifications", notifVol, tint) {
            notifVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow("Alarms", alarmVol, tint) {
            alarmVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow("Voice Call & Assistant", callVol, tint) {
            callVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Stream Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = {
                    try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (_: Exception) {}
                    try { audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (_: Exception) {}
                    try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) } catch (_: Exception) {}
                    mediaVol = 0f
                    ringVol = 0f
                    notifVol = 0f
                },
                modifier = Modifier.weight(1f).height(30.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x35FF4B4B))
            ) {
                Text("MUTE ALL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (nm != null && nm.isNotificationPolicyAccessGranted) {
                        val newFilter = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                            NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        } else {
                            NotificationManager.INTERRUPTION_FILTER_ALL
                        }
                        nm.setInterruptionFilter(newFilter)
                    } else if (ElevatedTaskCloser.isShizukuActive) {
                        val curZen = ElevatedTaskCloser.execShizuku("settings get global zen_mode")?.readTextOrKill()?.trim() ?: "0"
                        val newZen = if (curZen == "0") "1" else "0"
                        ElevatedTaskCloser.execShizuku("settings put global zen_mode $newZen")
                    }
                },
                modifier = Modifier.weight(1f).height(30.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x356A88FF))
            ) {
                Text("DND TOGGLE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Button(
                onClick = {
                    SonicAppVolumeEngine.resetAllAppVolumesToUnity()
                    SonicChannelRouter.resetAllRouting(context)
                },
                modifier = Modifier.weight(1f).height(30.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0x354AE082))
            ) {
                Text("RESET APPS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun StreamSliderRow(
    label: String,
    value: Float,
    tint: Color,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 10.sp,
            modifier = Modifier.width(105.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(22.dp)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp)
        )
    }
}
