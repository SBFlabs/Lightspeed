package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Emergency
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun OrbitalCapsuleLabSubSection(
    context: Context,
    prefs: SharedPreferences,
    cautionAmber: Color,
    currentLanguageMode: LightspeedLanguageEngine.LanguageMode,
    isNotchCalibExpanded: Boolean,
    onToggleNotchCalib: () -> Unit,
    isOemNoticeDemoted: Boolean,
    onOemNoticeDemotedChange: (Boolean) -> Unit,
    oemFeatureName: String?,
    isMarqueeSubSectionExpanded: Boolean,
    onToggleMarqueeSubSection: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val (capsuleTitle, capsuleSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.ORBITAL_CAPSULE, currentLanguageMode)
    val capsuleSubtitle = if (capsuleSub != null) "$capsuleSub • Dynamic punch-hole cutout" else "Dynamic punch-hole cutout HUD for download progress and media telemetry"

    CollapsibleSubSection(
        title = capsuleTitle,
        subtitle = capsuleSubtitle,
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
        var isCapsuleMasterEnabled by rememberSaveable {
            mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_ORBITAL_CAPSULE_ENABLED, false))
        }
        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_ORBITAL_CAPSULE_ENABLED,
            defaultVal = false,
            title = "Enable $capsuleTitle",
            subtitle = "Renders floating dynamic punch-hole capsule around camera cutout for downloads and media telemetry.",
            onChanged = {
                isCapsuleMasterEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ORBITAL_CAPSULE_ENABLED, false)
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("OrbitalCapsuleLabSubSection", "toggle", e) }
                onRefreshNeeded()
            }
        )

        if (isCapsuleMasterEnabled) {
            if (!isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                DeckCard(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                                    color = lsColors.textPrimary
                                )
                                Text(
                                    text = "Your device may have $oemFeatureName enabled. Disable it in system settings to prevent overlapping indicators.",
                                    fontSize = 11.sp,
                                    color = lsColors.textSecondary.copy(alpha = 0.85f),
                                    lineHeight = 14.sp
                                )
                            }
                            IconButton(
                                onClick = { onOemNoticeDemotedChange(true) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = "Demote to Footnote", tint = lsColors.textSecondary, modifier = Modifier.size(16.dp))
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
                            Text(capsuleOrientOptions.firstOrNull { it.first == currentCapsuleOrientMode }?.second ?: "🔄 Both Orientations", color = lsColors.textPrimary, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeckThemedDropdownMenu(
                        expanded = isCapsuleOrientDropdownOpen,
                        onDismissRequest = { isCapsuleOrientDropdownOpen = false }
                    ) {
                        capsuleOrientOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isCapsuleOrientDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_ORIENTATION_MODE, key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("OrbitalCapsuleLabSubSection", "orient", e) }
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
                            Text(capsuleOptions.firstOrNull { it.first == currentCapsuleLayout }?.second ?: "Unified Right (Compact)", color = lsColors.textPrimary, fontSize = 12.sp)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DeckThemedDropdownMenu(
                        expanded = isCapsuleDropdownOpen,
                        onDismissRequest = { isCapsuleDropdownOpen = false }
                    ) {
                        capsuleOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isCapsuleDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("OrbitalCapsuleLabSubSection", "layout", e) }
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, "", "Capsule Vertical Snugness Padding (0 to 8dp)", 0, 8, 1, 2)
            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, "", "Capsule Expansion Width (0 to 80dp)", 0, 80, 2, 0)

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

            if (isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                DeckCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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
                                Text("Your device may have $oemFeatureName enabled. Tap to manage settings if indicators overlap.", fontSize = 9.5.sp, color = lsColors.textSecondary.copy(alpha = 0.65f), lineHeight = 13.sp)
                            }
                            IconButton(
                                onClick = { onOemNoticeDemotedChange(false) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Outlined.VerticalAlignTop, contentDescription = "Move Upward", tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                            }
                        }
                        DeckButton(
                            text = "Settings",
                            icon = Icons.Default.Settings,
                            onClick = { OemNotchDetector.openSearch(context) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
