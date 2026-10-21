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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.normalizeRefuelingTimeout
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale

@Composable
fun HudTacticalHardwareSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isSubVolumeExpanded: Boolean,
    onToggleSubVolume: () -> Unit,
    isSubHullTapExpanded: Boolean,
    onToggleSubHullTap: () -> Unit,
    currentThreshold: Float,
    onThresholdChange: (Float) -> Unit,
    currentZImpulse: Float,
    onZImpulseChange: (Float) -> Unit,
    thresholdCrossedFlash: Boolean,
    onThresholdCrossedFlashChange: (Boolean) -> Unit,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    onShowOemShieldDialog: () -> Unit,
    onSelectHighDrainScope: (String) -> Unit,
    installedTools: List<com.sbf.lightspeed.system.TacticalToolItem> = emptyList(),
    onRefreshNeeded: () -> Unit
) {
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val (tacticalTitle, tacticalSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.TACTICAL_HARDWARE, currentLanguageMode)

    CompactAccordionSection(
        title = tacticalTitle,
        subtitle = tacticalSub,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle,
        headerTrailing = {
            IconButton(
                onClick = onShowOemShieldDialog,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "OEM Compatibility Shield",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Volume Key Matrix (Combos & Chords)
            CollapsibleSubSection(
                title = "Volume Key Matrix",
                subtitle = "Hardware chording, sequences, hold auto-repeat & suppression",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSubVolumeExpanded,
                onToggle = onToggleSubVolume
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_VOL_GESTURES_ENABLED,
                    defaultVal = false,
                    title = "Enable Volume Key Gestures",
                    subtitle = "Low-latency hardware chording, sequences & hold triggers",
                    onChanged = { onRefreshNeeded() }
                )

                // 3-Stage Volume Suppression Profile Selector
                val currentProfile = remember(prefs.getString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, null)) {
                    LightspeedKeyEngine.getSuppressionProfile(context)
                }
                var selectedProfile by remember { mutableStateOf(currentProfile) }

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("VOLUME SUPPRESSION PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)

                    val profileOptions = listOf(
                        Triple("instant_reflex", "Instant Reflex (0ms)", "Raw pass-through. Volume steps on ACTION_DOWN; chords execute immediately with potential single volume tick leak."),
                        Triple("balanced_holds", "Balanced Holds", "Suppresses volume jumps during long-press holds; chords execute immediately."),
                        Triple("total_clean", "Total Clean (150ms Buffer)", "Consumes first key event and waits up to 150ms. If second chord key is pressed, fires chord with 0 volume changes. If released without chord/hold, steps volume manually.")
                    )

                    profileOptions.forEach { (profileKey, title, desc) ->
                        val isSelected = selectedProfile == profileKey
                        val borderMod = if (isSelected) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        } else Modifier
                        DeckCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(borderMod),
                            shape = RoundedCornerShape(12.dp),
                            onClick = {
                                selectedProfile = profileKey
                                prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                onRefreshNeeded()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        selectedProfile = profileKey
                                        prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                        onRefreshNeeded()
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = LightspeedStyle.colors.textPrimary)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 14.sp)
                                }
                            }
                        }
                    }

                    if (selectedProfile == "total_clean") {
                        DeckCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Adds a 150ms buffer window before single volume taps register to guarantee zero volume leaks on chords.",
                                    fontSize = 11.sp,
                                    color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.9f),
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }

                // Key Hold Auto-Repeat
                var autoRepeatEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, false)) }
                PrefToggleRow(
                    title = "Hardware Key Hold Auto-Repeat",
                    subtitle = "Continuous auto-repeat for hold actions while volume buttons remain pressed",
                    isChecked = autoRepeatEnabled,
                    onCheckedChange = {
                        autoRepeatEnabled = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, it).apply()
                        onRefreshNeeded()
                    }
                )

                if (autoRepeatEnabled) {
                    PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_KEY_REPEAT_INTERVAL_MS, "", "Auto-Repeat Interval (ms)", 100, 500, 25, 200)
                }

                // Rocker Advisory Banner
                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Rocker Notice: Devices with a single physical rocker bar may mechanically lever switches when pressing both ends. If chords misfire, Sequential gestures are recommended for 100% reliability.",
                            fontSize = 11.5.sp,
                            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.9f),
                            lineHeight = 15.sp
                        )
                    }
                }

                val volumeGestures = listOf(
                    Triple(LightspeedPreferences.KEY_VOL_UP_LONG_PRESS, "Volume Up Long Press (~400ms)", ArrowDirection.SWIPE_UP to true),
                    Triple(LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS, "Volume Down Long Press (~400ms)", ArrowDirection.SWIPE_DOWN to true),
                    Triple(LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP, "Hold Vol Down + Tap Vol Up", ArrowDirection.SWIPE_DOWN_UP to true),
                    Triple(LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP, "Hold Vol Up + Tap Vol Down", ArrowDirection.SWIPE_UP_DOWN to true),
                    Triple(LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN, "Sequence: Vol Up → Vol Down (<300ms)", ArrowDirection.SWIPE_UP_DOWN to false),
                    Triple(LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP, "Sequence: Vol Down → Vol Up (<300ms)", ArrowDirection.SWIPE_DOWN_UP to false),
                    Triple(LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD, "Tap Vol Down → Hold Vol Up (~400ms)", ArrowDirection.SWIPE_DOWN_UP to true),
                    Triple(LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD, "Tap Vol Up → Hold Vol Down (~400ms)", ArrowDirection.SWIPE_UP_DOWN to true)
                )

                volumeGestures.forEach { (prefKey, title, motion) ->
                    val (dir, hold) = motion
                    GestureMappingRow(
                        context = context,
                        prefs = prefs,
                        direction = dir,
                        isHold = hold,
                        keyResName = prefKey,
                        defaultTitle = title,
                        options = dynamicActionTokens,
                        labelCache = tokenLabelCache,
                        showMediaQuickAccess = true
                    )
                }
            }

            // Hull Tap Sensors (Back Tap)
            CollapsibleSubSection(
                title = "Hull Tap Sensors",
                subtitle = "Accelerometer Z-axis impulse detection for double & triple back taps",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSubHullTapExpanded,
                onToggle = onToggleSubHullTap
            ) {
                val isBackTapSupported = remember { LightspeedBackTapEngine.isSupported(context) }
                if (!isBackTapSupported) {
                    DeckCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Not supported on this device",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.error,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                var isBackTapActive by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, false)) }
                PrefToggleRow(
                    title = "Enable Back Tap Gestures",
                    subtitle = "Detect double and triple taps on the back of your device",
                    isChecked = isBackTapActive,
                    onCheckedChange = { checked ->
                        isBackTapActive = checked
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, checked).apply()
                        if (!checked) {
                            onZImpulseChange(0f)
                            onThresholdCrossedFlashChange(false)
                        }
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                )

                val currentScope = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on") ?: "screen_on"
                var isScopeDropdownOpen by remember { mutableStateOf(false) }
                val scopeOptions = listOf(
                    "screen_on" to "Screen On Only",
                    "screen_off" to "Screen Off (High Drain)",
                    "always" to "Always (High Drain)"
                )

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("ACTIVATION SCOPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    DeckDropdownAnchorBox { anchorWidth ->
                        OutlinedButton(
                            onClick = { isScopeDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(scopeOptions.firstOrNull { it.first == currentScope }?.second ?: "Screen On Only", color = LightspeedStyle.colors.textPrimary)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DeckThemedDropdownMenu(
                            expanded = isScopeDropdownOpen,
                            onDismissRequest = { isScopeDropdownOpen = false },
                            anchorWidth = anchorWidth
                        ) {
                            scopeOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    text = { Text(label) },
                                    onClick = {
                                        isScopeDropdownOpen = false
                                        if (key == "screen_off" || key == "always") {
                                            onSelectHighDrainScope(key)
                                        } else {
                                            prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, key).apply()
                                            onRefreshNeeded()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // Smart Battery Failsafe Banner
                DeckCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "⚡ Smart Battery Failsafe Active: Screen-off sensor is automatically paused and wake lock released if battery drops ≤ 20% or Battery Saver is on.",
                            fontSize = 11.sp,
                            color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.85f),
                            lineHeight = 14.sp
                        )
                    }
                }

                // Live Impulse Calibration Meter (Active strictly when this section is expanded)
                val impulseBorder = if (thresholdCrossedFlash) {
                    Modifier.border(1.2.dp, LightspeedStyle.colors.accentOk, RoundedCornerShape(14.dp))
                } else {
                    Modifier.border(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                }
                DeckCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(impulseBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Speed,
                                    contentDescription = null,
                                    tint = if (thresholdCrossedFlash) LightspeedStyle.colors.accentOk else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "LIVE IMPULSE METER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (thresholdCrossedFlash) LightspeedStyle.colors.accentOk else MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f m/s²", currentZImpulse),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (thresholdCrossedFlash) LightspeedStyle.colors.accentOk else LightspeedStyle.colors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(18.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            val impulseFraction = (currentZImpulse / 20.0f).coerceIn(0f, 1f)
                            val threshFraction = (currentThreshold / 20.0f).coerceIn(0f, 1f)

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(impulseFraction)
                                    .background(if (thresholdCrossedFlash) LightspeedStyle.colors.accentOk else MaterialTheme.colorScheme.primary)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(threshFraction)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(2.5.dp)
                                        .fillMaxHeight()
                                        .background(LightspeedStyle.colors.textPrimary)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.0", fontSize = 10.sp, color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.5f))
                            Text(
                                if (thresholdCrossedFlash) "✓ THRESHOLD BREACHED" else String.format(Locale.US, "Threshold: %.1f m/s²", currentThreshold),
                                fontSize = 10.5.sp,
                                fontWeight = if (thresholdCrossedFlash) FontWeight.Bold else FontWeight.Normal,
                                color = if (thresholdCrossedFlash) LightspeedStyle.colors.accentOk else MaterialTheme.colorScheme.primary
                            )
                            Text("20.0 m/s²", fontSize = 10.sp, color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.5f))
                        }
                    }
                }

                PrefFloatDottedSliderRow(
                    context = context,
                    prefs = prefs,
                    keyResName = LightspeedPreferences.KEY_BACK_TAP_THRESHOLD,
                    title = "Strike Force Threshold",
                    minVal = 3.0f,
                    maxVal = 18.0f,
                    step = 0.5f,
                    defaultVal = 7.5f,
                    unit = "m/s²",
                    onValueChanged = {
                        onThresholdChange(it)
                        safeReloadPreferences()
                    }
                )

                GestureMappingRow(
                    context = context,
                    prefs = prefs,
                    direction = ArrowDirection.DOUBLE_TAP,
                    isHold = false,
                    keyResName = LightspeedPreferences.KEY_BACK_TAP_DOUBLE,
                    defaultTitle = "Double Back Tap (250-450ms)",
                    options = dynamicActionTokens,
                    labelCache = tokenLabelCache,
                    showMediaQuickAccess = false,
                    showToolsQuickAccess = true,
                    installedTools = installedTools
                )

                GestureMappingRow(
                    context = context,
                    prefs = prefs,
                    direction = ArrowDirection.TAP,
                    isHold = true,
                    keyResName = LightspeedPreferences.KEY_BACK_TAP_TRIPLE,
                    defaultTitle = "Triple Back Tap (≤ 700ms)",
                    options = dynamicActionTokens,
                    labelCache = tokenLabelCache,
                    showMediaQuickAccess = false,
                    showToolsQuickAccess = true,
                    installedTools = installedTools
                )
            }
        }
    }
}
