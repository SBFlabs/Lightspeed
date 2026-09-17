package com.sbf.lightspeed.settings

import android.content.Context
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
import com.sbf.lightspeed.system.*
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
    onRefreshNeeded: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TACTICAL_HARDWARE),
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
                    defaultVal = true,
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
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedProfile = profileKey
                                    prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                    onRefreshNeeded()
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                            )
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
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 14.sp)
                                }
                            }
                        }
                    }

                    if (selectedProfile == "total_clean") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Adds a 150ms buffer window before single volume taps register to guarantee zero volume leaks on chords.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f),
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
                            color = Color.White.copy(alpha = 0.9f),
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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                    Box {
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
                                Text(scopeOptions.firstOrNull { it.first == currentScope }?.second ?: "Screen On Only", color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(
                            expanded = isScopeDropdownOpen,
                            onDismissRequest = { isScopeDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
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
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 14.sp
                        )
                    }
                }

                // Live Impulse Calibration Meter (Active strictly when this section is expanded)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (thresholdCrossedFlash) Color(0xFF00E676).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                    ),
                    border = BorderStroke(
                        1.2.dp,
                        if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    )
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
                                    tint = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "LIVE IMPULSE METER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.1f m/s²", currentZImpulse),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (thresholdCrossedFlash) Color(0xFF00E676) else Color.White
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
                                    .background(if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
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
                                        .background(Color.White)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0.0", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                            Text(
                                if (thresholdCrossedFlash) "✓ THRESHOLD BREACHED" else String.format(Locale.US, "Threshold: %.1f m/s²", currentThreshold),
                                fontSize = 10.5.sp,
                                fontWeight = if (thresholdCrossedFlash) FontWeight.Bold else FontWeight.Normal,
                                color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                            )
                            Text("20.0 m/s²", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                    showMediaQuickAccess = true
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
                    showMediaQuickAccess = true
                )
            }
        }
    }
}

@Composable
fun HudRefuelingBaySection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onShowAmoledWarning: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.REFUELING_BAY),
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
            val isInfinixOrTranssion = remember {
                val m = Build.MANUFACTURER.lowercase()
                val b = Build.BRAND.lowercase()
                m.contains("infinix") || m.contains("transsion") || m.contains("tecno") || m.contains("itel") ||
                b.contains("infinix") || b.contains("transsion") || b.contains("tecno") || b.contains("itel")
            }
            var isWarningDemoted by remember { mutableStateOf(prefs.getBoolean("pref_infinix_standby_warning_demoted", false)) }

            if (isInfinixOrTranssion && !isWarningDemoted) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
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
                                    color = Color.White
                                )
                                Text(
                                    text = "OEM standby display style may overlap with Refueling Bay. Disable it in system settings to prevent screen collisions.",
                                    fontSize = 11.sp,
                                    color = Color.LightGray.copy(alpha = 0.85f),
                                    lineHeight = 14.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    isWarningDemoted = true
                                    prefs.edit().putBoolean("pref_infinix_standby_warning_demoted", true).apply()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.VerticalAlignBottom,
                                    contentDescription = "Demote to Footnote",
                                    tint = Color.LightGray,
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
                                    } catch (_: Exception) {}
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
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isTriggerDropdownOpen,
                        onDismissRequest = { isTriggerDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
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

            val currentTimeout = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s"
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
                            Text(timeoutOptions.firstOrNull { it.first == currentTimeout }?.second ?: "60 Seconds", color = Color.White, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isTimeoutDropdownOpen,
                        onDismissRequest = { isTimeoutDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
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
                                Text("OEM standby display style may overlap with Refueling Bay. Tap to manage settings.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f), lineHeight = 13.sp)
                            }
                            IconButton(
                                onClick = {
                                    isWarningDemoted = false
                                    prefs.edit().putBoolean("pref_infinix_standby_warning_demoted", false).apply()
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
                                    } catch (_: Exception) {}
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
