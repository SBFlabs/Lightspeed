package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlin.math.roundToInt

/**
 * Dedicated sub-section for tactile engine configurations, motor throttle cooldowns,
 * isolated System Haptics card, Central Command accordion, Cockpit Hangar & Gears accordion,
 * and Category Cruise accordion.
 */
@Composable
fun HapticsSubSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit = {}
) {
    var isCentralCommandExpanded by remember { mutableStateOf(false) }
    var isCockpitExpanded by remember { mutableStateOf(false) }
    var isCruiseExpanded by remember { mutableStateOf(false) }

    CollapsibleSubSection(
        title = "Tactile & Haptics Engine",
        subtitle = "Hardware-agnostic resonance throttle & per-domain tactile feedback",
        icon = {
            Icon(Icons.Default.Vibration, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. System Haptics (Master Baseline)
            HapticDomainSliderRow(
                context = context,
                prefs = prefs,
                title = "System Haptics",
                subtitle = "Master baseline tactile pulse for generic taps, dialogs and gestures",
                currentKey = LightspeedPreferences.KEY_HAPTIC_PROFILE,
                options = LightspeedHapticEngine.STANDARD_PROFILES
            ) { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_PROFILE, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }

            // 2. Throttle Cooldown Slider
            HapticThrottleCooldownCard(context, prefs)

            // 3. Central Command Sub-Accordion
            CollapsibleSubSection(
                title = "Central Command",
                subtitle = "Settings UI taps, deflector edge triggers, and continuous scrub",
                icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                isExpanded = isCentralCommandExpanded,
                onToggle = { isCentralCommandExpanded = !isCentralCommandExpanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Central Command UI Haptics",
                        subtitle = "Tactile feedback when tapping settings accordions, tabs, and buttons",
                        currentKey = LightspeedPreferences.KEY_CENTRAL_COMMAND_HAPTICS,
                        options = LightspeedHapticEngine.STANDARD_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_CENTRAL_COMMAND_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Deflector Gesture Haptics",
                        subtitle = "Edge sensor triggers, flick milestones, and flank zone transitions",
                        currentKey = LightspeedPreferences.KEY_DEFLECTOR_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Continuous Scrub Haptics",
                        subtitle = "Tactile ticks during continuous volume, brightness, and slider scrub",
                        currentKey = LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_GESTURE_SCRUB_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                }
            }

            // 4. Cockpit Hangar & Gears Dedicated Accordion
            CollapsibleSubSection(
                title = "Cockpit Hangar & Gears",
                subtitle = "Rotary circle spinning and gear set transitions (0, 1, 2)",
                icon = { Icon(Icons.Default.AvTimer, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(15.dp)) },
                isExpanded = isCockpitExpanded,
                onToggle = { isCockpitExpanded = !isCockpitExpanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val currentCockpit = prefs.getString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, "")?.takeIf { it.isNotEmpty() }
                        ?: (prefs.getString(LightspeedPreferences.KEY_GEAR_HAPTIC_STRENGTH, "tactical") ?: "tactical")
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Rotary Circle Spinning",
                        subtitle = "Haptic pulses when spinning the circular app/gear hangar",
                        currentValue = currentCockpit,
                        options = LightspeedHapticEngine.STANDARD_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, key).putString(LightspeedPreferences.KEY_GEAR_HAPTIC_STRENGTH, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    val currentGearJump = prefs.getString(LightspeedPreferences.KEY_COCKPIT_GEAR_JUMP_HAPTICS, "tactical") ?: "tactical"
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Gear Set Jump Haptics",
                        subtitle = "Tactile pulse when jumping between Gear sets (0, 1, 2)",
                        currentValue = currentGearJump,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_COCKPIT_GEAR_JUMP_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                }
            }

            // 5. Category Cruise Dedicated Accordion
            CollapsibleSubSection(
                title = "Category Cruise Tactile Engine",
                subtitle = "Spatial transitions, category cylinder cruise, and app scrub",
                icon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp)) },
                isExpanded = isCruiseExpanded,
                onToggle = { isCruiseExpanded = !isCruiseExpanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Category Cruise",
                        subtitle = "Tactile pulses when scrolling vertically through categories in the cylinder",
                        currentKey = LightspeedPreferences.KEY_CRUISE_CATEGORY_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_CRUISE_CATEGORY_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Spatial Layer Transits",
                        subtitle = "Tactile clicks when jumping between categories and entering apps, or returning back",
                        currentKey = LightspeedPreferences.KEY_CRUISE_LAYER_JUMP_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_CRUISE_LAYER_JUMP_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = "Cursor Focus & App Scrub",
                        subtitle = "Tactile ticks when moving selection cursor and shuffling through apps within the nebula",
                        currentKey = LightspeedPreferences.KEY_CRUISE_APP_FOCUS_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_CRUISE_APP_FOCUS_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                }
            }
        }
    }
}

/**
 * Throttle Cooldown slider at the top explaining minimum ms gap between ticks.
 */
@Composable
private fun HapticThrottleCooldownCard(context: Context, prefs: SharedPreferences) {
    var cooldown by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS, LightspeedPreferences.DEFAULT_HAPTIC_COOLDOWN_MS)) }
    val maxHz = if (cooldown > 0) (1000 / cooldown) else 1000

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)).padding(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HAPTIC PULSE THROTTLE", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Min motor rest gap (e.g. 50ms = max 20 pulses/sec; 0ms = unthrottled raw pulses. Prevents phone motor buzzing into a muddy blur).", fontSize = 11.sp, color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.8f), lineHeight = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    cooldown = LightspeedPreferences.DEFAULT_HAPTIC_COOLDOWN_MS
                    prefs.edit().putInt(LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS, cooldown).apply()
                    LightspeedHapticEngine.tick(context)
                }
            ) {
                Text(if (cooldown == 0) "0 ms (Raw)" else "$cooldown ms (~$maxHz Hz)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        DragOnlySlider(
            value = cooldown.toFloat(),
            onValueChange = { newVal ->
                cooldown = newVal.roundToInt().coerceIn(0, 200)
                prefs.edit().putInt(LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS, cooldown).apply()
            },
            valueRange = 0f..200f,
            steps = 7, // 0, 25, 50, 75, 100, 125, 150, 175, 200
            precisionStep = 1f, // Vertical drag steps by 1ms fine resolution
            tapToJump = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(0 to "0ms (Raw)", 45 to "45ms (Balanced)", 100 to "100ms", 200 to "200ms (Max)").forEach { (ms, lbl) ->
                Text(lbl, fontSize = 9.5.sp, fontWeight = if (cooldown == ms) FontWeight.Bold else FontWeight.Normal, color = if (cooldown == ms) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.clickable {
                    cooldown = ms
                    prefs.edit().putInt(LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS, ms).apply()
                    LightspeedHapticEngine.tick(context)
                })
            }
        }
    }
}
