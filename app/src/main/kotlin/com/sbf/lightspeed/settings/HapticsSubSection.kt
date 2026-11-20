package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

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
import com.sbf.lightspeed.system.LightspeedBatterySentinel
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
    var isCustomDeflectorHapticsExpanded by remember { mutableStateOf(false) }
    var isCockpitExpanded by remember { mutableStateOf(false) }
    var isCruiseExpanded by remember { mutableStateOf(false) }

    CollapsibleSubSection(
        title = lsString(R.string.sec_tactile_haptics_engine),
        subtitle = lsString(R.string.secd_hardware_agnostic_resonance_th),
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
                title = lsString(R.string.hap_sys_title),
                subtitle = lsString(R.string.hap_sys_sub),
                currentKey = LightspeedPreferences.KEY_HAPTIC_PROFILE,
                options = LightspeedHapticEngine.STANDARD_PROFILES
            ) { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_PROFILE, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }

            // 2. Throttle Cooldown Slider
            HapticThrottleCooldownCard(context, prefs)

            // 2b. Keep Haptics Alive in Battery Saver
            KeepHapticsAliveCard(context, prefs, onRefreshNeeded)

            // 3. Central Command Sub-Accordion
            CollapsibleSubSection(
                title = lsString(R.string.sec_central_command),
                subtitle = lsString(R.string.secd_settings_ui_taps_deflector_edg),
                icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                isExpanded = isCentralCommandExpanded,
                onToggle = { isCentralCommandExpanded = !isCentralCommandExpanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = lsString(R.string.hap_cc_title),
                        subtitle = lsString(R.string.hap_cc_sub),
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
                        title = lsString(R.string.hap_def_title),
                        subtitle = lsString(R.string.hap_def_sub),
                        currentKey = LightspeedPreferences.KEY_DEFLECTOR_HAPTICS,
                        options = LightspeedHapticEngine.EXTENDED_PROFILES
                    ) { key ->
                        prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_HAPTICS, key).apply()
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }

                    // Collapsible sub-card: Customize Deflector Gesture Haptics
                    CollapsibleSubSection(
                        title = lsString(R.string.sec_customize_deflector_gesture_ha),
                        subtitle = lsString(R.string.secd_individual_tactile_profiles_fo),
                        icon = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp)) },
                        isExpanded = isCustomDeflectorHapticsExpanded,
                        onToggle = { isCustomDeflectorHapticsExpanded = !isCustomDeflectorHapticsExpanded }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.gest_tap),
                                subtitle = lsString(R.string.hap_def_tap_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_TAP,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_TAP, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }

                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.hap_def_swipe_title),
                                subtitle = lsString(R.string.hap_def_swipe_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_SWIPE,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_SWIPE, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }

                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.hap_def_twostep_title),
                                subtitle = lsString(R.string.hap_def_twostep_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_TWOSTEP,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_TWOSTEP, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }

                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.hap_def_rebound_title),
                                subtitle = lsString(R.string.hap_def_rebound_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_REBOUND,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_REBOUND, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }

                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.hap_def_hold_title),
                                subtitle = lsString(R.string.hap_def_hold_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_HOLD,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_HOLD, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }

                            HapticDomainSliderRow(
                                context = context,
                                prefs = prefs,
                                title = lsString(R.string.hap_def_deep_title),
                                subtitle = lsString(R.string.hap_def_deep_sub),
                                currentKey = LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_DEEP_PULL,
                                options = LightspeedHapticEngine.EXTENDED_PROFILES
                            ) { key ->
                                prefs.edit().putString(LightspeedPreferences.KEY_HAPTIC_DEFLECTOR_DEEP_PULL, key).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            }
                        }
                    }

                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = lsString(R.string.hap_scrub_title),
                        subtitle = lsString(R.string.hap_scrub_sub),
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
                title = lsString(R.string.sec_cockpit_hangar_gears),
                subtitle = lsString(R.string.secd_rotary_circle_spinning_and_gea),
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
                        title = lsString(R.string.hap_rotary_title),
                        subtitle = lsString(R.string.hap_rotary_sub),
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
                        title = lsString(R.string.hap_gear_jump_title),
                        subtitle = lsString(R.string.hap_gear_jump_sub),
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
                title = lsString(R.string.sec_category_cruise_tactile_engine),
                subtitle = lsString(R.string.secd_spatial_transitions_category_c),
                icon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(15.dp)) },
                isExpanded = isCruiseExpanded,
                onToggle = { isCruiseExpanded = !isCruiseExpanded }
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HapticDomainSliderRow(
                        context = context,
                        prefs = prefs,
                        title = lsString(R.string.hap_cruise_cat_title),
                        subtitle = lsString(R.string.hap_cruise_cat_sub),
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
                        title = lsString(R.string.hap_cruise_layer_title),
                        subtitle = lsString(R.string.hap_cruise_layer_sub),
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
                        title = lsString(R.string.hap_cruise_focus_title),
                        subtitle = lsString(R.string.hap_cruise_focus_sub),
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
                Text(lsString(R.string.hap_throttle_title), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(lsString(R.string.hap_throttle_desc), fontSize = 11.sp, color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.8f), lineHeight = 14.sp)
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
                Text(if (cooldown == 0) lsString(R.string.hap_throttle_raw_badge) else "$cooldown ms (~$maxHz Hz)", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
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
            listOf(0 to lsString(R.string.hap_throttle_raw), 45 to lsString(R.string.hap_throttle_balanced), 100 to "100ms", 200 to lsString(R.string.hap_throttle_max)).forEach { (ms, lbl) ->
                Text(lbl, fontSize = 9.5.sp, fontWeight = if (cooldown == ms) FontWeight.Bold else FontWeight.Normal, color = if (cooldown == ms) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.clickable {
                    cooldown = ms
                    prefs.edit().putInt(LightspeedPreferences.KEY_HAPTIC_COOLDOWN_MS, ms).apply()
                    LightspeedHapticEngine.tick(context)
                })
            }
        }
    }
}

@Composable
private fun KeepHapticsAliveCard(
    context: Context,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    var keepHaptics by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_KEEP_HAPTICS_IN_BATTERY_SAVER, true))
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = lsString(R.string.hap_keep_alive_title),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LightspeedStyle.colors.textPrimary
                )
                Text(
                    text = lsString(R.string.hap_keep_alive_sub),
                    fontSize = 10.5.sp,
                    color = LightspeedStyle.colors.textSecondary,
                    lineHeight = 13.5.sp
                )
            }
            Switch(
                checked = keepHaptics,
                onCheckedChange = { checked ->
                    keepHaptics = checked
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_KEEP_HAPTICS_IN_BATTERY_SAVER, checked).apply()
                    LightspeedBatterySentinel.setKeepHapticsAlive(context, checked)
                    onRefreshNeeded()
                },
                modifier = Modifier.height(24.dp)
            )
        }
    }
}
