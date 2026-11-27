package com.sbf.lightspeed.system.hud

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckSegmentedRow
import com.sbf.lightspeed.settings.DeckStylePresets
import com.sbf.lightspeed.settings.DeckSubChip
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.VistaDeckManager
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedRefreshRateEngine
import com.sbf.lightspeed.system.display.LightspeedTelemetryHudOverlay
import com.sbf.lightspeed.system.display.LightspeedTelemetryHudRenderer
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Vista Deck Accordion: Overdrive Refresh Rate & Pass-Through Dual-Handle Telemetry Micro-HUD.
 *
 * Uses modern tonal 12-Theme / M3 cards, `DeckSubChip` capsules, and `DeckSegmentedRow` tracks.
 */
@Composable
fun VistaTelemetryHudSection(
    service: AccessibilityService,
    onOpenTelemetryAppPicker: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val snapshot = LocalDeckTheme.current
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
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
    var hudStyle by remember {
        mutableStateOf(
            LightspeedTelemetryHudRenderer.normalizeStyleKey(
                prefs.getString(LightspeedPreferences.KEY_TELEMETRY_HUD_STYLE, LightspeedPreferences.DEFAULT_TELEMETRY_HUD_STYLE)
            )
        )
    }
    var hudCompact by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_COMPACT, false))
    }
    val selectedApps = prefs.getStringSet(LightspeedPreferences.KEY_TELEMETRY_HUD_PACKAGES, emptySet()) ?: emptySet()

    var showFps by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_FPS, true)) }
    var showCpu by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_CPU, true)) }
    var showTemp by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_TEMP, true)) }
    var showRam by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_RAM, false)) }
    var showBat by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_BAT, true)) }
    var showWatt by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_SHOW_WATT, true)) }

    val cardShape = RoundedCornerShape(14.dp)
    val cardBg = if (isM3) colorScheme.surfaceContainerLow else lsColors.textPrimary.copy(alpha = 0.05f)
    val cardMod = if (isM3) {
        Modifier.fillMaxWidth().clip(cardShape).background(cardBg).padding(12.dp)
    } else {
        Modifier.fillMaxWidth().clip(cardShape).background(cardBg)
            .border(0.75.dp, lsColors.textPrimary.copy(alpha = 0.10f), cardShape).padding(12.dp)
    }
    val badgeBg = if (isM3) colorScheme.secondaryContainer else lsColors.accent.copy(alpha = 0.18f)
    val badgeText = if (isM3) colorScheme.onSecondaryContainer else lsColors.accent

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // --- CARD 1: Hardware Refresh Rate Overdrive ---
        Box(modifier = cardMod) {
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
                            text = lsString(R.string.thud_rr_title),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (isSingleFixedRate) {
                                lsString(R.string.thud_rr_fixed_fmt, supportedRates.firstOrNull() ?: 60)
                            } else if (floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED) {
                                lsString(R.string.thud_rr_locked_fmt, targetHz)
                            } else {
                                lsString(R.string.thud_rr_adaptive_fmt, effectiveLowestHz, targetHz)
                            },
                            fontSize = 9.5.sp,
                            color = lsColors.textSecondary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(badgeBg)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isSingleFixedRate) {
                                lsString(R.string.thud_badge_native_fmt, supportedRates.firstOrNull() ?: 60)
                            } else if (floorMode == LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED) {
                                lsString(R.string.thud_badge_locked_fmt, targetHz)
                            } else {
                                lsString(R.string.thud_badge_peak_fmt, targetHz)
                            },
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = badgeText
                        )
                    }
                }

                if (!isSingleFixedRate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        supportedRates.forEach { hz ->
                            DeckSubChip(
                                text = "${hz}Hz",
                                isActive = targetHz == hz,
                                snapshot = snapshot,
                                modifier = Modifier.weight(1f),
                                onClick = {
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
                            )
                        }
                    }

                    if (supportedRates.size > 1 || targetHz >= 60) {
                        val lowestHz = remember(supportedRates) {
                            LightspeedRefreshRateEngine.getLowestHardwareRefreshRate(service)
                        }
                        val effectiveLowestHz = lowestHz.takeIf { it > 0 } ?: (supportedRates.minOrNull() ?: 60)
                        DeckSegmentedRow(
                            options = listOf(
                                LightspeedPreferences.OVERDRIVE_FLOOR_ADAPTIVE to lsString(R.string.thud_mode_adaptive_fmt, effectiveLowestHz, targetHz),
                                LightspeedPreferences.OVERDRIVE_FLOOR_LOCKED to lsString(R.string.thud_mode_locked_fmt, targetHz)
                            ),
                            selectedKey = floorMode,
                            onSelect = { nextMode ->
                                LightspeedHapticEngine.tick(service)
                                floorMode = nextMode
                                LightspeedRefreshRateEngine.applyRefreshRate(service, targetHz, nextMode)
                            },
                            snapshot = snapshot
                        )
                    }
                }
            }
        }

        // --- CARD 2: Pass-Through Telemetry Micro-HUD ---
        Box(modifier = cardMod) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.thud_title),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (selectedApps.isEmpty()) {
                                lsString(R.string.thud_sub_empty)
                            } else {
                                lsString(R.string.thud_sub_armed_fmt, selectedApps.size)
                            },
                            fontSize = 9.5.sp,
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

                // Target Apps, Compact Toggle, Preview, Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DeckSubChip(
                        text = lsString(R.string.thud_btn_apps_fmt, selectedApps.size),
                        icon = Icons.Default.Apps,
                        isActive = selectedApps.isNotEmpty(),
                        snapshot = snapshot,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenTelemetryAppPicker
                    )
                    DeckSubChip(
                        text = lsString(R.string.thud_btn_preview),
                        icon = Icons.Default.Visibility,
                        isActive = false,
                        snapshot = snapshot,
                        onClick = {
                            LightspeedHapticEngine.tick(service)
                            LightspeedTelemetryHudOverlay.triggerLivePreview(service)
                        }
                    )
                    DeckSubChip(
                        text = lsString(R.string.thud_btn_reset),
                        icon = Icons.Default.CenterFocusStrong,
                        isActive = false,
                        snapshot = snapshot,
                        onClick = {
                            LightspeedHapticEngine.tick(service)
                            LightspeedTelemetryHudOverlay.resetPosition(service)
                        }
                    )
                }

                // HUD Style Selector (4 Modern Styles)
                Text(
                    text = lsString(R.string.thud_style_hdr),
                    fontSize = 9.5.sp,
                    color = lsColors.textSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val styleRows = LightspeedTelemetryHudRenderer.TELEMETRY_HUD_STYLES.chunked(2)
                    styleRows.forEach { rowPairs ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPairs.forEach { (key, _) ->
                                DeckSubChip(
                                    text = LightspeedTelemetryHudRenderer.localizedStyleLabel(key),
                                    isActive = hudStyle == key,
                                    snapshot = snapshot,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        LightspeedHapticEngine.tick(service)
                                        hudStyle = key
                                        prefs.edit().putString(LightspeedPreferences.KEY_TELEMETRY_HUD_STYLE, key).apply()
                                        LightspeedTelemetryHudOverlay.triggerLivePreview(service)
                                    }
                                )
                            }
                        }
                    }
                }

                // Animated Device Font & Scale Picker
                VistaTelemetryTypographyControls(service = service, snapshot = snapshot)

                // Compact Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.thud_compact_title),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = lsString(R.string.thud_compact_sub),
                            fontSize = 9.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = hudCompact,
                        onCheckedChange = {
                            hudCompact = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_TELEMETRY_HUD_COMPACT, it).apply()
                            LightspeedTelemetryHudOverlay.refreshState(service)
                        },
                        modifier = Modifier.height(22.dp)
                    )
                }

                // Individual Readout Chips (FPS, CPU, TEMP, RAM, BAT, WATT)
                Text(
                    text = lsString(R.string.thud_readouts_hdr),
                    fontSize = 9.5.sp,
                    color = lsColors.textSecondary
                )
                val toggleRows = listOf(
                    listOf(
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
                    ),
                    listOf(
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
                )

                toggleRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { (label, isChecked, onToggle) ->
                            DeckSubChip(
                                text = label,
                                isActive = isChecked,
                                snapshot = snapshot,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    LightspeedHapticEngine.tick(service)
                                    onToggle(!isChecked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
