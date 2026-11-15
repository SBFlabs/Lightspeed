package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.LightspeedStatusBarOverlay
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedMediaManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

private fun triggerHudStyleLivePreview(
    context: Context,
    prefs: SharedPreferences,
    key: String,
    currentRawValue: String,
    styleKey: String
) {
    LightspeedHapticEngine.heavyClick(context)
    if (styleKey == "prism_timeline") {
        LightspeedMediaManager.showScrubber(context)
        return
    }
    val hudColorMode = LightspeedPreferences.resolveHudColorMode(prefs, key, currentRawValue)
    val (sampleTitle, sampleVal, sampleStep, sampleTotal) = when {
        currentRawValue.contains("brightness") ->
            Quad("BRIGHTNESS", "68%", 22, 32)
        currentRawValue.contains("timeout") ->
            Quad("SHIP GOES DARK IN", "5 MIN", 4, 8)
        currentRawValue.contains("scroll") ->
            Quad("SCROLL SCRUB", "▲ SCROLL UP", 1, 1)
        currentRawValue.contains("media") ->
            Quad("MEDIA TIMELINE", "01:42 / 03:50", 45, 100)
        else ->
            Quad("MEDIA VOLUME", "72%", 72, 100)
    }
    LightspeedStatusBarOverlay.showActionHud(
        title = sampleTitle,
        value = sampleVal,
        stepIndex = sampleStep,
        totalSteps = sampleTotal,
        durationMs = 2400L,
        style = styleKey,
        colorMode = hudColorMode
    )
}

private data class Quad(
    val title: String,
    val value: String,
    val stepIndex: Int,
    val totalSteps: Int
)

/**
 * Dropdown menus for HUD Style selection (grouped into 3 collapsible sub-accordions with
 * long-press live on-screen preview), Media Scrub step/acceleration, Media Skip interval,
 * and Quick Media Action presets.
 */
@OptIn(ExperimentalFoundationApi::class)
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

    val signatureStyles = remember(currentRawValue) {
        when {
            currentRawValue.contains("media") -> listOf(
                "prism_timeline" to "Interactive Timeline Scrubber Card"
            )
            currentRawValue.contains("brightness") -> listOf(
                "signature_solar_lux" to "Solar Lux Photometer (Nits + Corona)"
            )
            currentRawValue.contains("timeout") -> listOf(
                "signature_chrono_dial" to "Chrono Countdown Dial (Sleep Ring)"
            )
            currentRawValue.contains("scroll") -> listOf(
                "signature_kinetic_thruster" to "Kinetic Thruster Gauge (Velocity Vector)"
            )
            else -> listOf(
                "signature_sonic_eq" to "Sonic Equalizer Deck (dB + Wave Bars)"
            )
        }
    }
    val modernStyles = remember {
        listOf(
            "modern_symbol_pill" to "Modern Symbol Pill (Icon + Bar)",
            "modern_floating_orb" to "Modern Symbol Orb (Compact Ring)",
            "modern_slim_slider" to "Modern Glass Slider (Liquid Fill)"
        )
    }
    val rectangularStyles = remember {
        listOf(
            "canopy_droppod" to "Tactical Canopy Drop-Pod",
            "quantum_horizon" to "Quantum Synthetic Horizon",
            "edge_blade" to "Dynamic Edge Blade"
        )
    }
    val circularStyles = remember {
        listOf(
            "cockpit_reticle" to "Holographic Cockpit Reticle",
            "tachyon_dial" to "Tachyon Orbital Radar"
        )
    }

    var expandSignature by remember(hudStyle) {
        mutableStateOf(signatureStyles.any { it.first == hudStyle })
    }
    var expandModern by remember(hudStyle) {
        mutableStateOf(modernStyles.any { it.first == hudStyle })
    }
    var expandRectangular by remember(hudStyle) {
        mutableStateOf(
            rectangularStyles.any { it.first == hudStyle } ||
                (!signatureStyles.any { it.first == hudStyle } &&
                    !modernStyles.any { it.first == hudStyle } &&
                    !circularStyles.any { it.first == hudStyle })
        )
    }
    var expandCircular by remember(hudStyle) {
        mutableStateOf(circularStyles.any { it.first == hudStyle })
    }

    Box {
        val hudName = when (hudStyle) {
            "prism_timeline" -> "Timeline Card"
            "signature_sonic_eq" -> "Sonic EQ"
            "signature_solar_lux" -> "Solar Lux"
            "signature_chrono_dial" -> "Chrono Dial"
            "signature_kinetic_thruster" -> "Thruster"
            "modern_symbol_pill" -> "Symbol Pill"
            "modern_floating_orb" -> "Symbol Orb"
            "modern_slim_slider" -> "Glass Slider"
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
            val sections = listOf(
                Triple("Action Signature HUD (${signatureStyles.size})", expandSignature, signatureStyles) to { expandSignature = !expandSignature },
                Triple("Modern Symbol HUDs (${modernStyles.size})", expandModern, modernStyles) to { expandModern = !expandModern },
                Triple("Rectangular Visor & Blade (${rectangularStyles.size})", expandRectangular, rectangularStyles) to { expandRectangular = !expandRectangular },
                Triple("Circular Reticle & Radar (${circularStyles.size})", expandCircular, circularStyles) to { expandCircular = !expandCircular }
            )

            sections.forEachIndexed { secIdx, (secData, onToggle) ->
                val (headerTitle, isExpanded, items) = secData
                val hasActiveChild = items.any { it.first == hudStyle }

                if (secIdx > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }

                // Sub-Accordion Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            onToggle()
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (isExpanded) "▾" else "▸"}  $headerTitle",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasActiveChild) MaterialTheme.colorScheme.primary else lsColors.textPrimary
                    )
                    if (hasActiveChild && !isExpanded) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isExpanded) {
                    items.forEach { (styleKey, styleTitle) ->
                        val isSelected = styleKey == hudStyle
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 42.dp)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .combinedClickable(
                                    onClick = {
                                        hudStyle = styleKey
                                        LightspeedPreferences.saveHudStyle(prefs, key, currentRawValue, styleKey)
                                        try {
                                            LightspeedAccessibilityService.instance?.reloadPreferences()
                                        } catch (e: Exception) {
                                            logSwallowed("GestureHudAndMediaDropdowns", "SaveHudStyle", e)
                                        }
                                        showHudMenu = false
                                    },
                                    onLongClick = {
                                        triggerHudStyleLivePreview(context, prefs, key, currentRawValue, styleKey)
                                    }
                                )
                                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = if (isSelected) "✓ $styleTitle" else styleTitle,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else lsColors.textPrimary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "✦ Tip: Long-press any style to preview on screen",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = lsColors.textSecondary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
fun GestureMediaScrubControls(
    context: Context,
    prefs: SharedPreferences,
    activeSnapshot: DeckThemeSnapshot
) {
    var stepSec by remember {
        mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_MEDIA_SCRUB_STEP_SEC, 5).coerceIn(2, 60))
    }
    var accelEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_MEDIA_SCRUB_ACCEL_ENABLED, true))
    }
    var showStepMenu by remember { mutableStateOf(false) }

    Box {
        DeckSubChip(
            text = "Step: ${stepSec}s ▾",
            icon = Icons.Default.Speed,
            isActive = false,
            snapshot = activeSnapshot,
            onClick = { showStepMenu = true }
        )
        DeckThemedDropdownMenu(
            expanded = showStepMenu,
            onDismissRequest = { showStepMenu = false },
            snapshotOverride = activeSnapshot
        ) {
            listOf(
                2 to "2s Step (Fine Precision)",
                5 to "5s Step (Default)",
                10 to "10s Step (Medium)",
                15 to "15s Step (Audiobook / Podcast)",
                30 to "30s Step (Chapter Leap)"
            ).forEach { (sec, label) ->
                val isSelected = sec == stepSec
                DropdownMenuItem(
                    modifier = Modifier.heightIn(min = 44.dp),
                    text = {
                        Text(
                            text = if (isSelected) "✓ $label" else label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    onClick = {
                        stepSec = sec
                        prefs.edit().putInt(LightspeedPreferences.KEY_MEDIA_SCRUB_STEP_SEC, sec).apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHudAndMediaDropdowns", "MediaScrubStep", e) }
                        showStepMenu = false
                    }
                )
            }
        }
    }

    DeckSubChip(
        text = if (accelEnabled) "Accel: ON" else "Accel: OFF",
        isActive = accelEnabled,
        snapshot = activeSnapshot,
        onClick = {
            accelEnabled = !accelEnabled
            prefs.edit().putBoolean(LightspeedPreferences.KEY_MEDIA_SCRUB_ACCEL_ENABLED, accelEnabled).apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("GestureHudAndMediaDropdowns", "MediaScrubAccel", e) }
        }
    )
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
