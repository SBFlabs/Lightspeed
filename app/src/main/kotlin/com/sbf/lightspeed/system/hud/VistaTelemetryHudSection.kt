package com.sbf.lightspeed.system.hud

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.sbf.lightspeed.settings.DeckSubChip
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.VistaDeckManager
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedRefreshRateEngine
import com.sbf.lightspeed.system.display.LightspeedTelemetryHudOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Vista Deck Accordion: Overdrive Refresh Rate & Per-App Tactical Telemetry Micro-HUD.
 *
 * Strictly adheres to the 12-Theme Command Domain token system (LightspeedStyle.colors,
 * DeckSwitch, DeckSubChip) with zero emojis and hardware-adaptive refresh rate discovery.
 */
@Composable
fun VistaTelemetryHudSection(
    service: AccessibilityService,
    onOpenTelemetryAppPicker: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val prefs = service.defaultPrefs()

    val supportedRates = remember { LightspeedRefreshRateEngine.getSupportedRefreshRates(service) }
    val isSingleFixedRate = supportedRates.size <= 1

    var targetHz by remember {
        mutableIntStateOf(LightspeedRefreshRateEngine.getActiveTargetHz(service))
    }
    var floorMode by remember {
        mutableStateOf(
            prefs.getString(
                LightspeedPreferences.KEY_OVERDRIVE_FLOOR_MODE,
                LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE
            ) ?: LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE
        )
    }

    var hudEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_ENABLED, false))
    }
    var selectedApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_TELEMETRY_HUD_PACKAGES, emptySet()) ?: emptySet())
    }

    var showFps by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, true)) }
    var showCpu by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_CPU, true)) }
    var showTemp by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_TEMP, true)) }
    var showRam by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_RAM, false)) }
    var showBat by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_BAT, true)) }
    var showWatt by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_WATT, true)) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- CARD 1: Hardware Refresh Rate Overdrive ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(lsColors.dialogSurface.copy(alpha = 0.25f))
                .border(0.6.dp, lsColors.accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lowestHz = remember(supportedRates) {
                        LightspeedRefreshRateEngine.getLowestHardwareRefreshRate(service)
                    }
                    val effectiveLowestHz = lowestHz.takeIf { it > 0 } ?: (supportedRates.minOrNull() ?: 60)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hardware Refresh Rate",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (isSingleFixedRate) {
                                "Fixed ${supportedRates.firstOrNull() ?: 60}Hz hardware panel detected"
                            } else if (floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED) {
                                "Locked floor & ceiling at ${targetHz}Hz"
                            } else {
                                "Dynamic ${effectiveLowestHz}Hz to ${targetHz}Hz adaptive peak"
                            },
                            fontSize = 8.5.sp,
                            color = lsColors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(lsColors.accent.copy(alpha = 0.16f))
                            .border(0.5.dp, lsColors.accent.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 2.5.dp)
                    ) {
                        Text(
                            text = if (isSingleFixedRate) {
                                "${supportedRates.firstOrNull() ?: 60}Hz NATIVE"
                            } else if (floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED) {
                                "${targetHz}Hz LOCKED"
                            } else {
                                "${targetHz}Hz PEAK"
                            },
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accent
                        )
                    }
                }

                if (!isSingleFixedRate) {
                    // Discrete Hardware Hz Mode Pills (60Hz / 90Hz / 120Hz / 144Hz / 165Hz / 185Hz+)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        supportedRates.forEach { hz ->
                            val isSelected = targetHz == hz
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isSelected) lsColors.accent.copy(alpha = 0.24f)
                                        else lsColors.dialogSurface.copy(alpha = 0.35f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.dp else 0.5.dp,
                                        color = if (isSelected) lsColors.accent else lsColors.borderSubtle,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        LightspeedHapticEngine.tick(service)
                                        if (!Settings.System.canWrite(service) && !ElevatedTaskCloser.isShizukuActive) {
                                            VistaDeckManager.dismiss()
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                                Uri.parse("package:${service.packageName}")
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            try { service.startActivity(intent) } catch (_: Exception) {}
                                        } else {
                                            targetHz = hz
                                            LightspeedRefreshRateEngine.applyRefreshRate(service, hz, floorMode)
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${hz}Hz",
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) lsColors.accent else lsColors.textPrimary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Adaptive Peak vs Locked Floor Mode Selector (uses true hardware minimum floor)
                    if (supportedRates.size > 1 || targetHz >= 60) {
                        val lowestHz = remember(supportedRates) {
                            LightspeedRefreshRateEngine.getLowestHardwareRefreshRate(service)
                        }
                        val effectiveLowestHz = lowestHz.takeIf { it > 0 } ?: (supportedRates.minOrNull() ?: 60)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val modes = listOf(
                                LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE to "ADAPTIVE (${effectiveLowestHz}-${targetHz}Hz)",
                                LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED to "LOCKED (${targetHz}Hz FLOOR)"
                            )
                            modes.forEach { (modeKey, modeLabel) ->
                                val active = floorMode == modeKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (active) lsColors.accent.copy(alpha = 0.18f)
                                            else lsColors.dialogSurface.copy(alpha = 0.25f)
                                        )
                                        .border(
                                            width = if (active) 0.8.dp else 0.5.dp,
                                            color = if (active) lsColors.accent.copy(alpha = 0.8f) else lsColors.borderSubtle,
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable {
                                            LightspeedHapticEngine.tick(service)
                                            floorMode = modeKey
                                            LightspeedRefreshRateEngine.applyRefreshRate(service, targetHz, modeKey)
                                        }
                                        .padding(vertical = 5.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = modeLabel,
                                        fontSize = 8.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        color = if (active) lsColors.accent else lsColors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- CARD 2: Per-App Tactical Telemetry Micro-HUD ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(lsColors.dialogSurface.copy(alpha = 0.25f))
                .border(0.6.dp, lsColors.accent.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Per-App Telemetry Micro-HUD",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (selectedApps.isEmpty()) {
                                "Select target games or apps below to activate visor"
                            } else {
                                "Draggable micro-visor armed for ${selectedApps.size} selected app(s)"
                            },
                            fontSize = 8.5.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = hudEnabled,
                        onCheckedChange = {
                            hudEnabled = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_ENABLED, it).apply()
                            LightspeedTelemetryHudOverlay.refreshState(service)
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                // Target Apps & Reset Position Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckSubChip(
                        text = "Target Apps (${selectedApps.size})",
                        icon = Icons.Default.Apps,
                        isActive = selectedApps.isNotEmpty(),
                        onClick = onOpenTelemetryAppPicker
                    )

                    DeckSubChip(
                        text = "Reset Position",
                        icon = Icons.Default.CenterFocusStrong,
                        isActive = false,
                        onClick = {
                            LightspeedHapticEngine.tick(service)
                            LightspeedTelemetryHudOverlay.resetPosition(service)
                        }
                    )
                }

                // Individual Telemetry Readout Toggles (FPS, CPU, TEMP, RAM, BAT, WATT)
                Text(
                    text = "Active Visor Readouts (Drag left 6-dot grip to reposition)",
                    fontSize = 8.5.sp,
                    color = lsColors.textSecondary
                )

                val toggleItemsRow1 = listOf(
                    Triple("FPS", showFps) { next: Boolean ->
                        showFps = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    },
                    Triple("CPU", showCpu) { next: Boolean ->
                        showCpu = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_CPU, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    },
                    Triple("TEMP", showTemp) { next: Boolean ->
                        showTemp = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_TEMP, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    }
                )

                val toggleItemsRow2 = listOf(
                    Triple("RAM", showRam) { next: Boolean ->
                        showRam = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_RAM, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    },
                    Triple("BAT", showBat) { next: Boolean ->
                        showBat = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_BAT, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    },
                    Triple("WATT", showWatt) { next: Boolean ->
                        showWatt = next
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_WATT, next).apply()
                        LightspeedTelemetryHudOverlay.refreshState(service)
                    }
                )

                listOf(toggleItemsRow1, toggleItemsRow2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { (label, isChecked, onToggle) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (isChecked) lsColors.accent.copy(alpha = 0.22f)
                                        else lsColors.dialogSurface.copy(alpha = 0.3f)
                                    )
                                    .border(
                                        width = if (isChecked) 0.9.dp else 0.5.dp,
                                        color = if (isChecked) lsColors.accent else lsColors.borderSubtle,
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        LightspeedHapticEngine.tick(service)
                                        onToggle(!isChecked)
                                    }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isChecked) lsColors.accent else lsColors.textSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
