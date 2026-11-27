package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckStylePresets
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
internal fun VistaQuickOpticsStrip(
    service: AccessibilityService,
    prefs: SharedPreferences,
    snapshot: DeckThemeSnapshot
) {
    var extraDimOn by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)) }
    var extraDimLevel by remember {
        mutableIntStateOf(
            prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40)
                .let { if (it in 10..90) it else 40 }
        )
    }
    var smartPixelsOn by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) }
    var smartPixelsPct by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)) }
    var eyeCareOn by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)) }
    var eyeCareTemp by remember {
        mutableIntStateOf(
            prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, LightspeedEyeCareEngine.DEFAULT_KELVIN)
                .coerceIn(LightspeedEyeCareEngine.MIN_KELVIN, LightspeedEyeCareEngine.MAX_KELVIN)
        )
    }

    LaunchedEffect(Unit) {
        val (syncedOn, syncedTemp) = LightspeedEyeCareEngine.syncFromSystem(service)
        eyeCareOn = syncedOn
        eyeCareTemp = syncedTemp
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                LightspeedPreferences.KEY_EXTRA_DIM_ENABLED -> extraDimOn = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_EXTRA_DIM_LEVEL -> extraDimLevel = prefs.getInt(key, 40).let { if (it in 10..90) it else 40 }
                LightspeedPreferences.KEY_SMART_PIXELS_ENABLED -> smartPixelsOn = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_SMART_PIXELS_PATTERN -> smartPixelsPct = prefs.getInt(key, 25)
                LightspeedPreferences.KEY_EYE_CARE_ENABLED -> eyeCareOn = prefs.getBoolean(key, false)
                LightspeedPreferences.KEY_EYE_CARE_TEMP -> eyeCareTemp = prefs.getInt(key, LightspeedEyeCareEngine.DEFAULT_KELVIN)
                    .coerceIn(LightspeedEyeCareEngine.MIN_KELVIN, LightspeedEyeCareEngine.MAX_KELVIN)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val stateOff = lsString(R.string.sdeck_state_off)
        SystemQuickTogglePill(
            label = lsString(R.string.vdeck_pill_extra_dim),
            status = if (extraDimOn) "$extraDimLevel%" else stateOff,
            icon = Icons.Default.BrightnessLow,
            isActive = extraDimOn,
            snapshot = snapshot,
            modifier = Modifier.weight(1f)
        ) {
            val next = !extraDimOn
            extraDimOn = next
            LightspeedHapticEngine.tick(service)
            LightspeedEyeCareEngine.applyExtraDim(service, next, extraDimLevel)
        }
        SystemQuickTogglePill(
            label = lsString(R.string.vdeck_pill_smart_pixels),
            status = if (smartPixelsOn) "$smartPixelsPct%" else stateOff,
            icon = Icons.Default.GridOn,
            isActive = smartPixelsOn,
            snapshot = snapshot,
            modifier = Modifier.weight(1f)
        ) {
            val next = !smartPixelsOn
            smartPixelsOn = next
            LightspeedHapticEngine.tick(service)
            LightspeedSmartPixelsOverlay.setEnabled(service, next, smartPixelsPct)
        }
        SystemQuickTogglePill(
            label = lsString(R.string.vdeck_pill_eye_care),
            status = if (eyeCareOn) "${eyeCareTemp}K" else stateOff,
            icon = Icons.Default.NightlightRound,
            isActive = eyeCareOn,
            snapshot = snapshot,
            modifier = Modifier.weight(1f)
        ) {
            val next = !eyeCareOn
            eyeCareOn = next
            LightspeedHapticEngine.tick(service)
            LightspeedEyeCareEngine.applyEyeCare(service, next, eyeCareTemp, updateTemperature = false)
        }
    }
}

@Composable
internal fun SonicQuickControlsStrip(
    context: Context,
    snapshot: DeckThemeSnapshot,
    voiceOn: Boolean,
    isMuted: Boolean,
    isSilent: Boolean,
    onToggleVoice: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSilent: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SystemQuickTogglePill(
            label = lsString(R.string.sdeck_pill_voice_iso),
            status = if (voiceOn) lsString(R.string.sdeck_state_on) else lsString(R.string.sdeck_state_off),
            icon = Icons.Default.GraphicEq,
            isActive = voiceOn,
            snapshot = snapshot,
            modifier = Modifier.weight(1f),
            onClick = {
                LightspeedHapticEngine.tick(context)
                onToggleVoice()
            }
        )
        SystemQuickTogglePill(
            label = lsString(R.string.sdeck_pill_mute_media),
            status = if (isMuted) lsString(R.string.sdeck_state_muted) else lsString(R.string.sdeck_state_live),
            icon = Icons.AutoMirrored.Filled.VolumeOff,
            isActive = isMuted,
            snapshot = snapshot,
            modifier = Modifier.weight(1f),
            onClick = {
                LightspeedHapticEngine.tick(context)
                onToggleMute()
            }
        )
        SystemQuickTogglePill(
            label = lsString(R.string.sdeck_pill_silent),
            status = if (isSilent) lsString(R.string.sdeck_state_on) else lsString(R.string.sdeck_state_std),
            icon = Icons.Default.DoNotDisturbOn,
            isActive = isSilent,
            snapshot = snapshot,
            modifier = Modifier.weight(1f),
            onClick = {
                LightspeedHapticEngine.tick(context)
                onToggleSilent()
            }
        )
    }
}

@Composable
private fun SystemQuickTogglePill(
    label: String,
    status: String,
    icon: ImageVector,
    isActive: Boolean,
    snapshot: DeckThemeSnapshot,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val optical = snapshot.resolveOpticalColors()
    val accent = optical.readableTintColor
    val shape = RoundedCornerShape(14.dp)

    val bg = when {
        isM3 && isActive -> colorScheme.secondaryContainer
        isM3 -> colorScheme.surfaceContainerHigh
        isActive -> accent.copy(alpha = 0.18f)
        else -> lsColors.textPrimary.copy(alpha = 0.06f)
    }
    val borderMod = if (isM3) {
        Modifier
    } else {
        val borderColor = if (isActive) accent.copy(alpha = 0.38f) else lsColors.textPrimary.copy(alpha = 0.10f)
        val borderWidth = if (isActive) 0.8.dp else 0.75.dp
        Modifier.border(borderWidth, borderColor, shape)
    }
    val iconColor = when {
        isM3 && isActive -> colorScheme.primary
        isM3 -> colorScheme.onSurfaceVariant
        isActive -> accent
        else -> lsColors.textPrimary.copy(alpha = 0.85f)
    }
    val labelColor = when {
        isM3 && isActive -> colorScheme.onSecondaryContainer
        isM3 -> colorScheme.onSurfaceVariant
        else -> lsColors.textPrimary
    }
    val statusColor = when {
        isM3 && isActive -> colorScheme.primary
        isM3 -> colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        isActive -> accent
        else -> lsColors.textSecondary
    }

    Column(
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(borderMod)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = status,
            fontSize = 8.sp,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            maxLines = 1
        )
    }
}
