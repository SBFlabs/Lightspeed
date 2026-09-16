package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import java.util.Calendar

@Composable
fun HudConfigVaultSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onExportClicked: () -> Unit,
    onShowImportOptions: () -> Unit,
    onShowResetConfirm: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SHIP_DATA_VAULT),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Export Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .clickable { onExportClicked() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Export Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text("Save all gesture sets, sliders, physics, and shortcuts to a standalone JSON file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
            }

            // Import Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                    .clickable { onShowImportOptions() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "Import", tint = MaterialTheme.colorScheme.secondary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Import Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text("Restore complete settings from a previous Lightspeed backup file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
            }

            // Reset Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .clickable { onShowResetConfirm() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Reset to Factory Defaults", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                    Text("Wipe custom settings and revert to pristine defaults", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
            }
        }
    }
}

@Composable
fun HudSystemOverridesSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SYSTEM_OVERRIDES),
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        SystemOverrideDeckContents(
            context = context,
            prefs = prefs
        )
    }
}

@Composable
fun HudExperimentalLabsSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isNotchCalibExpanded: Boolean,
    onToggleNotchCalib: () -> Unit,
    isOemNoticeDemoted: Boolean,
    onOemNoticeDemotedChange: (Boolean) -> Unit,
    oemFeatureName: String?,
    isMarqueeSubSectionExpanded: Boolean,
    onToggleMarqueeSubSection: () -> Unit,
    isSubPowerExpanded: Boolean,
    onToggleSubPower: () -> Unit,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockedChange: (Boolean) -> Unit,
    singlePressTapCount: Int,
    onSinglePressTapCountChange: (Int) -> Unit,
    installedTacticalTools: List<TacticalToolItem>,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    val cautionAmber = Color(0xFFFFB300)
    HazardAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.EXPERIMENTAL_LABS),
        subtitle = "Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Maintenance Bay Intro Badge
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = cautionAmber.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Experimental Labs: Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
                        fontSize = 11.sp,
                        color = cautionAmber.copy(alpha = 0.95f),
                        lineHeight = 14.sp
                    )
                }
            }

            // 1. Omniscient Audio Dock (Experimental)
            var isOmniscientDockExpanded by rememberSaveable { mutableStateOf(false) }
            CollapsibleSubSection(
                title = "Omniscient Audio Dock (Per-App Volume HUD)",
                subtitle = "Full-screen per-app volume mixer & sovereign stealth focus manager",
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isOmniscientDockExpanded,
                onToggle = { isOmniscientDockExpanded = !isOmniscientDockExpanded }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_OMNISCIENT_AUDIO_DOCK_ENABLED,
                    defaultVal = false,
                    title = "Enable Omniscient Audio Dock",
                    subtitle = "Pull outward (horizontally from deflectors or vertically from top status bar) while scrubbing volume to open per-app mixer.",
                    onChanged = { onRefreshNeeded() }
                )
            }

            // 2. Orbital Capsule (Camera Cutout HUD) [Experimental]
            CollapsibleSubSection(
                title = "Orbital Capsule (Camera Cutout HUD)",
                subtitle = "Dynamic punch-hole cutout HUD for download progress and media telemetry",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Emergency,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isNotchCalibExpanded,
                onToggle = onToggleNotchCalib
            ) {
                val isCapsuleMasterEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ORBITAL_CAPSULE_ENABLED, false)
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_ORBITAL_CAPSULE_ENABLED,
                    defaultVal = false,
                    title = "Enable Orbital Capsule",
                    subtitle = "Renders floating dynamic punch-hole capsule around camera cutout for downloads and media telemetry.",
                    onChanged = {
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                        onRefreshNeeded()
                    }
                )

                if (isCapsuleMasterEnabled) {
                    // OEM Dynamic Notch / Dynamic Bar Advisory Glass Callout
                    if (!isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
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
                                            text = "OEM Camera Cutout Conflicts",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Your device may have $oemFeatureName enabled. Disable it in system settings to prevent overlapping indicators.",
                                            fontSize = 11.sp,
                                            color = Color.LightGray.copy(alpha = 0.85f),
                                            lineHeight = 14.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = { onOemNoticeDemotedChange(true) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = "Demote to Footnote", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Button(
                                    onClick = { OemNotchDetector.openSearch(context) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open $oemFeatureName Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }

                    val currentCapsuleOrientMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, "both") ?: "both"
                    var isCapsuleOrientDropdownOpen by remember { mutableStateOf(false) }
                    val capsuleOrientOptions = listOf(
                        "both" to "🔄 Both Orientations",
                        "portrait_only" to "📱 Portrait Only (Recommended for Cutout HUD)",
                        "landscape_only" to "📐 Landscape Only"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("CAPSULE ORIENTATION DISPLAY RULE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isCapsuleOrientDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(capsuleOrientOptions.firstOrNull { it.first == currentCapsuleOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isCapsuleOrientDropdownOpen,
                                onDismissRequest = { isCapsuleOrientDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                capsuleOrientOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        text = { Text(label) },
                                        onClick = {
                                            isCapsuleOrientDropdownOpen = false
                                            prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, key).apply()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val currentCapsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"
                    var isCapsuleDropdownOpen by remember { mutableStateOf(false) }
                    val capsuleOptions = listOf(
                        "unified_right" to "Unified Right (Compact)",
                        "dual_wing" to "Dual-Wing Bridge",
                        "unified_left" to "Unified Left"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("ORBITAL CAPSULE LAYOUT MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isCapsuleDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(capsuleOptions.firstOrNull { it.first == currentCapsuleLayout }?.second ?: "Unified Right (Compact)", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isCapsuleDropdownOpen,
                                onDismissRequest = { isCapsuleDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                capsuleOptions.forEach { (key, label) ->
                                    DropdownMenuItem(
                                        modifier = Modifier.heightIn(min = 48.dp),
                                        text = { Text(label) },
                                        onClick = {
                                            isCapsuleDropdownOpen = false
                                            prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, key).apply()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, "", "Capsule Vertical Snugness Padding (0 to 8dp)", 0, 8, 1, 2)
                    PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, "", "Capsule Expansion Width (0 to 80dp)", 0, 80, 2, 0)

                    // Marquee Engine
                    CollapsibleSubSection(
                        title = "Orbital Capsule Marquee Engine",
                        subtitle = "Text scroll velocity, pause delays & maximum width",
                        isExpanded = isMarqueeSubSectionExpanded,
                        onToggle = onToggleMarqueeSubSection
                    ) {
                        PrefToggleRow(
                            prefs = prefs,
                            prefKey = LightspeedPreferences.KEY_NOTCH_MARQUEE_ENABLED,
                            defaultVal = true,
                            title = "Enable Text Marquee Animation",
                            subtitle = "Smoothly scrolls overflowing download filenames and song titles across the HUD capsule.",
                            onChanged = { onRefreshNeeded() }
                        )
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_SPEED, "", "Scroll Velocity (px/sec)", 15, 80, 5, 30)
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_INITIAL_DELAY, "", "Initial Pause Delay (ms)", 500, 3000, 250, 1500)
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MAX_CAPSULE_WIDTH, "", "Max HUD Capsule Width (dp)", 120, 320, 10, 200)
                    }

                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_NOTCH_TEST_BEACON,
                        defaultVal = false,
                        title = "Live Alignment Test Beacon",
                        subtitle = "Renders a live HUD calibration reticle over the camera hole while calibrating.",
                        onChanged = { onRefreshNeeded() }
                    )

                    // OEM Advisory Footnote (Demoted)
                    if (isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
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
                                        Text("OEM Advisory Footnote: OEM Camera Cutout Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                        Text("Your device may have $oemFeatureName enabled. Tap to manage settings if indicators overlap.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f), lineHeight = 13.sp)
                                    }
                                    IconButton(
                                        onClick = { onOemNoticeDemotedChange(false) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Move Upward", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Button(
                                    onClick = { OemNotchDetector.openSearch(context) },
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

            // 3. HUD Strip Swipe-Down to Notifications [Experimental]
            var isSwipeDownExpanded by rememberSaveable { mutableStateOf(false) }
            CollapsibleSubSection(
                title = "HUD Strip Swipe-Down to Notifications",
                subtitle = "Pull down on the status bar sensor deck to expand Android notifications",
                icon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSwipeDownExpanded,
                onToggle = { isSwipeDownExpanded = !isSwipeDownExpanded }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS,
                    defaultVal = true,
                    title = "Swipe Down to Expand Notifications",
                    subtitle = "Quickly expand the Android notification shade by pulling downward on the top HUD status bar strip.",
                    onChanged = {
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                        onRefreshNeeded()
                    }
                )
            }

            // 4. Power Button Remapping
            CollapsibleSubSection(
                title = "Power Button Remapping",
                subtitle = "Single, Double, Hold (~400ms) & Press-then-Hold triggers",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.PowerSettingsNew,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSubPowerExpanded,
                onToggle = onToggleSubPower
            ) {
                // Emergency Reset Hardware Notice Banner
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = cautionAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Emergency Notice: 10s hardware power hold forces device reset. Disabling Accessibility restores system defaults.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 14.sp
                        )
                    }
                }

                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_POWER_GESTURES_ENABLED,
                    defaultVal = true,
                    title = "Enable Power Button Gestures",
                    subtitle = "Low-latency physical power button gesture interception",
                    onChanged = { onRefreshNeeded() }
                )

                val powerGestures = listOf(
                    Triple(LightspeedPreferences.KEY_POWER_SINGLE_PRESS, "Single Press", PowerTriggerSlot.POWER_SINGLE_PRESS),
                    Triple(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS, "Double Press (<300ms)", PowerTriggerSlot.POWER_DOUBLE_PRESS),
                    Triple(LightspeedPreferences.KEY_POWER_HOLD, "Hold (~400ms)", PowerTriggerSlot.POWER_HOLD),
                    Triple(LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD, "Press-then-Hold", PowerTriggerSlot.POWER_PRESS_THEN_HOLD)
                )

                powerGestures.forEach { (prefKey, title, slot) ->
                    PowerGestureMappingRow(
                        context = context,
                        prefs = prefs,
                        prefKey = prefKey,
                        title = title,
                        slot = slot,
                        isSinglePress = slot == PowerTriggerSlot.POWER_SINGLE_PRESS,
                        isSinglePressUnlocked = isSinglePressUnlocked,
                        onSinglePressUnlockStep = {
                            val newCount = singlePressTapCount + 1
                            onSinglePressTapCountChange(newCount)
                            if (newCount in 4..6) {
                                Toast.makeText(
                                    context,
                                    "You are ${7 - newCount} steps away from unlocking Single Press remap.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (newCount >= 7) {
                                onSinglePressUnlockedChange(true)
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, true).apply()
                                Toast.makeText(
                                    context,
                                    "Single Press Remapping Unlocked",
                                    Toast.LENGTH_SHORT
                                ).show()
                                LightspeedHapticEngine.heavyClick(context)
                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                onRefreshNeeded()
                            }
                        },
                        installedTools = installedTacticalTools,
                        options = dynamicActionTokens,
                        labelCache = tokenLabelCache,
                        onRefreshNeeded = onRefreshNeeded
                    )
                }
            }

            // 5. Core Cooling Schedule
            var isCoreCoolingExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_core_cooling_labs", true)) }
            CollapsibleSubSection(
                title = "Core Cooling Schedule",
                subtitle = "Configurable weekly maintenance reminder",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.AcUnit,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isCoreCoolingExpanded,
                onToggle = {
                    isCoreCoolingExpanded = !isCoreCoolingExpanded
                    prefs.edit().putBoolean("pref_sub_core_cooling_labs", isCoreCoolingExpanded).apply()
                }
            ) {
                var coreCoolingEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, false)) }
                var targetDay by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, Calendar.SUNDAY)) }
                var targetHour by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, 3)) }

                PrefToggleRow(
                    title = "Scheduled Core Cooling Reminder",
                    subtitle = "Dispatches a gentle reminder when system core cooling is recommended.",
                    isChecked = coreCoolingEnabled,
                    onCheckedChange = { checked ->
                        coreCoolingEnabled = checked
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, checked).apply()
                        onRefreshNeeded()
                    }
                )

                if (coreCoolingEnabled) {
                    CoreCoolingRotarySchedulePicker(
                        selectedDay = targetDay,
                        selectedHour = targetHour,
                        onScheduleChanged = { day, hour ->
                            targetDay = day
                            targetHour = hour
                            prefs.edit()
                                .putInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, day)
                                .putInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, hour)
                                .apply()
                            onRefreshNeeded()
                        }
                    )
                }

                // Strict Cold-Start Policy Notice (Strictly Placed Inside Core Cooling at Bottom)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                ) {
                    Text(
                        text = "STRICT COLD-START POLICY: Zero automatic reboots. When weekly cooling cycle is reached, dispatches a silent status reminder to the HUD and Orbital Capsule.",
                        fontSize = 10.5.sp,
                        color = Color.LightGray.copy(alpha = 0.85f),
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
