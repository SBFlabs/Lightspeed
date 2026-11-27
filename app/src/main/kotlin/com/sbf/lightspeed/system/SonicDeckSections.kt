package com.sbf.lightspeed.system

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckSubChip
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SonicDialogueSection(
    context: Context,
    snapshot: DeckThemeSnapshot,
    voiceOn: Boolean,
    onVoiceOnChanged: (Boolean) -> Unit,
    showSaveDialog: Boolean,
    onShowSaveDialogChange: (Boolean) -> Unit
) {
    var activePreset by remember { mutableStateOf(SonicVoiceIsolationEngine.activePreset) }
    var vocalBoost by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.vocalBoostLevel) }
    var bgMuffle by remember { mutableFloatStateOf(SonicVoiceIsolationEngine.backgroundMuffleLevel) }
    var customProfiles by remember { mutableStateOf(SonicVoiceIsolationEngine.getCustomProfiles(context)) }
    var profileNameInput by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val tint = Color(snapshot.effectiveTintArgb.toInt())

    LaunchedEffect(voiceOn) {
        vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
        bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
        activePreset = SonicVoiceIsolationEngine.activePreset
    }

    LaunchedEffect(showSaveDialog) {
        if (showSaveDialog) {
            kotlinx.coroutines.delay(100)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                Text(lsString(R.string.sdeck_voice_hdr), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            DeckSwitch(
                checked = voiceOn,
                onCheckedChange = {
                    onVoiceOnChanged(it)
                    SonicVoiceIsolationEngine.setEnabled(context, it)
                    vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                    bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                    activePreset = SonicVoiceIsolationEngine.activePreset
                },
                modifier = Modifier.height(24.dp)
            )
        }

        // Horizontal Scrollable Acoustic Presets Row (MAX, Custom, + User Saved Profiles)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. MAX Preset
            val isMaxSelected = activePreset == SonicVoiceIsolationEngine.PRESET_MAX
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isMaxSelected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                    .border(
                        width = if (isMaxSelected) 1.dp else 0.6.dp,
                        color = if (isMaxSelected) tint else Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        activePreset = SonicVoiceIsolationEngine.PRESET_MAX
                        SonicVoiceIsolationEngine.applyPreset(context, SonicVoiceIsolationEngine.PRESET_MAX)
                        vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                        bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = lsString(R.string.sdeck_preset_max),
                    fontSize = 9.sp,
                    fontWeight = if (isMaxSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isMaxSelected) Color.White else Color.White.copy(alpha = 0.75f)
                )
            }

            // 2. Custom Preset with HOLD ↗ to save
            val isCustomSelected = activePreset == SonicVoiceIsolationEngine.PRESET_CUSTOM
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCustomSelected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                    .border(
                        width = if (isCustomSelected) 1.dp else 0.6.dp,
                        color = if (isCustomSelected) tint else Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            activePreset = SonicVoiceIsolationEngine.PRESET_CUSTOM
                            SonicVoiceIsolationEngine.applyPreset(context, SonicVoiceIsolationEngine.PRESET_CUSTOM)
                            vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                            bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                        },
                        onLongClick = {
                            profileNameInput = ""
                            onShowSaveDialogChange(true)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = lsString(R.string.sdeck_preset_custom),
                        fontSize = 9.sp,
                        fontWeight = if (isCustomSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCustomSelected) Color.White else Color.White.copy(alpha = 0.75f)
                    )
                    Text(
                        text = lsString(R.string.sdeck_hold_tag),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCustomSelected) tint else Color.White.copy(alpha = 0.45f)
                    )
                }
            }

            // 3. Dynamic User Saved Custom Profiles
            for ((profileName, values) in customProfiles) {
                val isProfSelected = activePreset == profileName
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isProfSelected) tint.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
                    .border(
                        width = if (isProfSelected) 1.dp else 0.6.dp,
                        color = if (isProfSelected) tint else Color.White.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .combinedClickable(
                        onClick = {
                            activePreset = profileName
                            SonicVoiceIsolationEngine.applyPreset(context, profileName)
                            vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                            bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                        },
                        onLongClick = {
                            // Long press on saved profile deletes it
                            SonicVoiceIsolationEngine.deleteCustomProfile(context, profileName)
                            customProfiles = SonicVoiceIsolationEngine.getCustomProfiles(context)
                            activePreset = SonicVoiceIsolationEngine.activePreset
                            vocalBoost = SonicVoiceIsolationEngine.vocalBoostLevel
                            bgMuffle = SonicVoiceIsolationEngine.backgroundMuffleLevel
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = profileName,
                    fontSize = 9.sp,
                    fontWeight = if (isProfSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isProfSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

        // Inline Save Custom Profile Dialog
        if (showSaveDialog) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(0.6.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        lsString(R.string.sdeck_save_prof_fmt, (vocalBoost * 100).toInt(), (bgMuffle * 100).toInt()),
                        color = tint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { if (it.length <= 16) profileNameInput = it },
                        placeholder = { Text(lsString(R.string.sdeck_prof_hint), fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f)) },
                        singleLine = true,
                        supportingText = {
                            Text(
                                "${profileNameInput.length}/16",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.End,
                                fontSize = 7.5.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = tint,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = tint
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { onShowSaveDialogChange(false) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(lsString(R.string.cc_dlg_cancel), color = Color.White.copy(alpha = 0.7f), fontSize = 9.sp)
                        }
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = {
                                val cleanName = profileNameInput.trim()
                                if (cleanName.isNotBlank() &&
                                    !cleanName.equals(SonicVoiceIsolationEngine.PRESET_MAX, ignoreCase = true) &&
                                    !cleanName.equals(SonicVoiceIsolationEngine.PRESET_CUSTOM, ignoreCase = true)
                                ) {
                                    SonicVoiceIsolationEngine.saveCustomProfile(context, cleanName, vocalBoost, bgMuffle)
                                    customProfiles = SonicVoiceIsolationEngine.getCustomProfiles(context)
                                    activePreset = cleanName
                                    SonicVoiceIsolationEngine.applyPreset(context, cleanName)
                                    onShowSaveDialogChange(false)
                                }
                            },
                            enabled = profileNameInput.trim().isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = tint)
                        ) {
                            Text(lsString(R.string.cc_common_save), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lsString(R.string.sdeck_vocal_boost_db, (vocalBoost * 8.0f).toInt()), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
            Text("${(vocalBoost * 100).toInt()}%", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        DeckSlider(
            value = vocalBoost,
            onValueChange = {
                vocalBoost = it
                activePreset = SonicVoiceIsolationEngine.PRESET_CUSTOM
                SonicVoiceIsolationEngine.setVocalBoost(context, it)
            },
            modifier = Modifier.height(24.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lsString(R.string.sdeck_bg_muffle), color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
            Text("${(bgMuffle * 100).toInt()}%", color = tint, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        DeckSlider(
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
    snapshot: DeckThemeSnapshot,
    mediaVol: Float,
    onMediaVolChange: (Float) -> Unit,
    onAllMuted: () -> Unit,
    onAppsReset: () -> Unit
) {
    val tint = Color(snapshot.effectiveTintArgb.toInt())
    val coroutineScope = rememberCoroutineScope()

    fun readStreamRatio(stream: Int, fallbackMax: Float): Float {
        val max = try { audioManager.getStreamMaxVolume(stream).toFloat().coerceAtLeast(1f) } catch (_: Exception) { fallbackMax }
        val cur = try { audioManager.getStreamVolume(stream).toFloat() } catch (_: Exception) { fallbackMax * 0.7f }
        return (cur / max).coerceIn(0f, 1f)
    }

    var ringVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_RING, 7f)) }
    var notifVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_NOTIFICATION, 7f)) }
    var alarmVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_ALARM, 7f)) }
    var callVol by remember { mutableFloatStateOf(readStreamRatio(AudioManager.STREAM_VOICE_CALL, 7f)) }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(lsString(R.string.sdeck_streams_hdr), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        StreamSliderRow(lsString(R.string.sdeck_stream_media), mediaVol, tint) {
            onMediaVolChange(it)
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow(lsString(R.string.sdeck_stream_ring), ringVol, tint) {
            ringVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                audioManager.setStreamVolume(AudioManager.STREAM_RING, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow(lsString(R.string.sdeck_stream_notif), notifVol, tint) {
            notifVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION)
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow(lsString(R.string.sdeck_stream_alarm), alarmVol, tint) {
            alarmVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        StreamSliderRow(lsString(R.string.sdeck_stream_call), callVol, tint) {
            callVol = it
            try {
                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (it * max).toInt(), 0)
            } catch (_: Exception) {}
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Stream Control Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DeckSubChip(
                text = lsString(R.string.sdeck_btn_mute_all),
                isActive = false,
                snapshot = snapshot,
                modifier = Modifier.weight(1f),
                onClick = {
                    onAllMuted()
                    try { audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, 0) } catch (_: Exception) {}
                    try { audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0) } catch (_: Exception) {}
                    ringVol = 0f
                    notifVol = 0f
                }
            )

            DeckSubChip(
                text = lsString(R.string.sdeck_btn_dnd),
                isActive = false,
                snapshot = snapshot,
                modifier = Modifier.weight(1f),
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
                        coroutineScope.launch(Dispatchers.IO) {
                            val curZen = ElevatedTaskCloser.execShizuku("settings get global zen_mode")?.readTextOrKill()?.trim() ?: "0"
                            val newZen = if (curZen == "0") "1" else "0"
                            ElevatedTaskCloser.execShizuku("settings put global zen_mode $newZen")
                        }
                    }
                }
            )

            DeckSubChip(
                text = lsString(R.string.sdeck_btn_reset_apps),
                isActive = false,
                snapshot = snapshot,
                modifier = Modifier.weight(1f),
                onClick = {
                    SonicAppVolumeEngine.clearAllCustomAppVolumes(context)
                    SonicChannelRouter.resetAllRouting(context)
                    onAppsReset()
                }
            )
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
    val lsColors = com.sbf.lightspeed.ui.theme.LightspeedStyle.colors
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
        DeckSlider(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(22.dp)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            color = if (tint != Color.Unspecified) tint else lsColors.textPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(32.dp)
        )
    }
}
