package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Sub-Component: Horizon Rail Micro-Text Ticker & Typography.
 * Controls text ticker stream, orientation filters, metadata density,
 * font family scanner, letter casing, marquee velocity, and cutout avoidance.
 */
@Composable
fun HorizonRailTickerSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    CollapsibleSubSection(
        title = "Horizon Rail Micro-Text Ticker",
        subtitle = "Typography, dual-wing marquee, bounce velocity & orientation rules",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        var isRailTextEnabled by rememberSaveable {
            mutableStateOf(prefs.getBoolean("pref_horizon_rail_text_enabled", true))
        }

        PrefToggleRow(
            title = "Micro-Text Telemetry Ticker",
            subtitle = "Streams active download filenames, song titles, episode numbers, and live track progress along the rail.",
            isChecked = isRailTextEnabled,
            onCheckedChange = {
                isRailTextEnabled = it
                prefs.edit().putBoolean("pref_horizon_rail_text_enabled", it).apply()
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                onRefreshNeeded()
            }
        )

        if (isRailTextEnabled) {
            val currentTextOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, "both") ?: "both"
            val textOrientOptions = listOf(
                "both" to "🔄 Both Orientations",
                "landscape_only" to "📐 Landscape Only",
                "portrait_only" to "📱 Portrait Only"
            )
            PrefDropdownSelector(
                title = "MICRO-TEXT ORIENTATION FILTER",
                currentKey = currentTextOrientMode,
                options = textOrientOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            val currentMetadataMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive"
            val metadataOptions = listOf(
                "adaptive" to "⚡ Adaptive (Title in Portrait, Full Metadata in Landscape)",
                "full" to "📜 Full Metadata Always (Title + Artist + Episode/Album)",
                "title_only" to "🏷 Title Only Always"
            )
            PrefDropdownSelector(
                title = "METADATA DENSITY & ORIENTATION MODE",
                currentKey = currentMetadataMode,
                options = metadataOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP,
                defaultVal = false,
                title = "Include Playback Progress Timestamp",
                subtitle = "Appends live track position and duration (e.g. 02:45 / 05:10) to the micro-text ticker.",
                onChanged = { onRefreshNeeded() }
            )

            val currentCasing = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, "natural") ?: "natural"
            val casingOptions = listOf(
                "natural" to "✨ Original / Natural Casing (Title & Artist)",
                "all_caps" to "🔤 ALL CAPS (Aviation HUD Avionics)",
                "title_case" to "🔠 Title Case (Capitalize Every Word)"
            )
            PrefDropdownSelector(
                title = "LETTER CASING FORMAT",
                currentKey = currentCasing,
                options = casingOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            val currentFont = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, "system_default") ?: "system_default"
            val availableFonts = remember { DeviceFontScanner.getInstalledFonts() }
            PrefDropdownSelector(
                title = "MICRO-TEXT FONT FAMILY",
                currentKey = currentFont,
                options = availableFonts,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_size", "", "Micro-Font Size (dp)", 7, 16, 1, 9)

            val currentTextPos = prefs.getString("pref_horizon_rail_text_position", "below") ?: "below"
            val textPosOptions = listOf(
                "below" to "Below Rail Line (Recommended)",
                "above" to "Above Rail Line",
                "embedded" to "Centered Inside Track",
                "below_statusbar" to "Below Entire Status Bar Deck"
            )
            PrefDropdownSelector(
                title = "MICRO-TEXT POSITION",
                currentKey = currentTextPos,
                options = textPosOptions,
                onSelected = { key ->
                    prefs.edit().putString("pref_horizon_rail_text_position", key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, "", "Vertical Fine Y-Offset (dp)", -20, 40, 1, 0)

            val currentMarqueeScope = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings"
            val marqueeScopeOptions = listOf(
                "both_wings" to "🔀 Dual-Wing Independent (Marquee Overflowing Side)",
                "right_wing_only" to "👉 Right Wing Only (Artist / Episode / Sider)",
                "left_wing_only" to "👈 Left Wing Only (Title)",
                "unified" to "🔗 Unified Stream (Split Across Wings & Marquee)"
            )
            PrefDropdownSelector(
                title = "MARQUEE SCOPE & SIDER TARGET",
                currentKey = currentMarqueeScope,
                options = marqueeScopeOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            val currentMarqueeAnim = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap"
            val marqueeAnimOptions = listOf(
                "continuous_wrap" to "♾️ Continuous Loop (Seamless Wrap Across Edges)",
                "bounce" to "🏓 Bounce / Ping-Pong (Back & Forth with Edge Pauses)"
            )
            PrefDropdownSelector(
                title = "MARQUEE ANIMATION STYLE",
                currentKey = currentMarqueeAnim,
                options = marqueeAnimOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            val currentMarqueeDir = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl"
            val marqueeDirOptions = listOf(
                "rtl" to "⬅️ Right-to-Left (Standard RTL)",
                "ltr" to "➡️ Left-to-Right (LTR)"
            )
            PrefDropdownSelector(
                title = "MARQUEE SCROLL DIRECTION",
                currentKey = currentMarqueeDir,
                options = marqueeDirOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_speed", "", "Scroll Velocity (px/sec)", 10, 80, 5, 20)

            var isAvoidCutout by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false)) }
            PrefToggleRow(
                title = "Hardware Cutout & Punch-Hole Avoidance",
                subtitle = "Splits title and subtitle into dual symmetrical wings around the camera cutout.",
                isChecked = isAvoidCutout,
                onCheckedChange = {
                    isAvoidCutout = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, it).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                    onRefreshNeeded()
                }
            )

            if (isAvoidCutout) {
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, "", "Wing Clearance Breathing Margin (0 to 16dp)", 0, 16, 1, 2)
            }
        }
    }
}
