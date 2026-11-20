package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed

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
        title = lsString(R.string.sec_horizon_rail_micro_text_ticker),
        subtitle = lsString(R.string.secd_typography_dual_wing_marquee_b),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        var isRailTextEnabled by rememberSaveable {
            mutableStateOf(prefs.getBoolean("pref_horizon_rail_text_enabled", true))
        }

        PrefToggleRow(
            title = lsString(R.string.hr_ticker_title),
            subtitle = lsString(R.string.hr_ticker_sub),
            isChecked = isRailTextEnabled,
            onCheckedChange = {
                isRailTextEnabled = it
                prefs.edit().putBoolean("pref_horizon_rail_text_enabled", it).apply()
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:42", e) }
                onRefreshNeeded()
            }
        )

        if (isRailTextEnabled) {
            val currentTextOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, "both") ?: "both"
            val textOrientOptions = listOf(
                "both" to lsString(R.string.hr_both_orient),
                "landscape_only" to lsString(R.string.hr_land_only),
                "portrait_only" to lsString(R.string.hr_orient_port)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_ticker_orient),
                currentKey = currentTextOrientMode,
                options = textOrientOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:60", e) }
                    onRefreshNeeded()
                }
            )

            val currentMetadataMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive"
            val metadataOptions = listOf(
                "adaptive" to lsString(R.string.hr_meta_adapt),
                "full" to lsString(R.string.hr_meta_full),
                "title_only" to lsString(R.string.hr_meta_title)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_meta_header),
                currentKey = currentMetadataMode,
                options = metadataOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:77", e) }
                    onRefreshNeeded()
                }
            )

            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP,
                defaultVal = false,
                title = lsString(R.string.hr_ts_title),
                subtitle = lsString(R.string.hr_ts_sub),
                onChanged = { onRefreshNeeded() }
            )

            val currentCasing = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, "natural") ?: "natural"
            val casingOptions = listOf(
                "natural" to lsString(R.string.hr_case_orig),
                "all_caps" to lsString(R.string.hr_case_caps),
                "title_case" to lsString(R.string.hr_case_title)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_case_header),
                currentKey = currentCasing,
                options = casingOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:103", e) }
                    onRefreshNeeded()
                }
            )

            val currentFont = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, "system_default") ?: "system_default"
            val availableFonts = remember { DeviceFontScanner.getInstalledFonts() }
            val fontCache = remember { mutableMapOf<String, FontFamily?>() }
            PrefDropdownSelector(
                title = lsString(R.string.hr_font_header),
                currentKey = currentFont,
                options = availableFonts,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:116", e) }
                    onRefreshNeeded()
                },
                fontFor = { key ->
                    fontCache.getOrPut(key) {
                        if (key == "system_default") {
                            null
                        } else {
                            try {
                                val tf = if (key.startsWith("/")) {
                                    Typeface.createFromFile(key)
                                } else {
                                    Typeface.create(key, Typeface.NORMAL)
                                }
                                FontFamily(tf)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                }
            )

            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_size", "", "Micro-Font Size (dp)", 7, 16, 1, 9)

            val currentTextPos = prefs.getString("pref_horizon_rail_text_position", "below") ?: "below"
            val textPosOptions = listOf(
                "below" to lsString(R.string.hr_pos_below),
                "above" to lsString(R.string.hr_pos_above),
                "embedded" to lsString(R.string.hr_pos_center),
                "below_statusbar" to lsString(R.string.hr_pos_below_sb)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_pos_header),
                currentKey = currentTextPos,
                options = textPosOptions,
                onSelected = { key ->
                    prefs.edit().putString("pref_horizon_rail_text_position", key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:136", e) }
                    onRefreshNeeded()
                }
            )

            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, "", "Vertical Fine Y-Offset (dp)", -20, 40, 1, 0)
            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SAFE_LEFT, "", "Text Safe Margin Left, clock side (dp)", 0, 200, 2, 42)
            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SAFE_RIGHT, "", "Text Safe Margin Right, battery side (dp)", 0, 200, 2, 58)

            val currentMarqueeScope = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings"
            val marqueeScopeOptions = listOf(
                "both_wings" to lsString(R.string.hr_marq_dual),
                "right_wing_only" to lsString(R.string.hr_marq_right),
                "left_wing_only" to lsString(R.string.hr_marq_left),
                "unified" to lsString(R.string.hr_marq_unified)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_marq_scope),
                currentKey = currentMarqueeScope,
                options = marqueeScopeOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:156", e) }
                    onRefreshNeeded()
                }
            )

            val currentMarqueeAnim = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap"
            val marqueeAnimOptions = listOf(
                "continuous_wrap" to lsString(R.string.hr_marq_loop),
                "bounce" to lsString(R.string.hr_marq_bounce)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_marq_style),
                currentKey = currentMarqueeAnim,
                options = marqueeAnimOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:173", e) }
                    onRefreshNeeded()
                }
            )

            val currentMarqueeDir = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl"
            val marqueeDirOptions = listOf(
                "rtl" to lsString(R.string.hr_dir_rtl),
                "ltr" to lsString(R.string.hr_dir_ltr)
            )
            PrefDropdownSelector(
                title = lsString(R.string.hr_dir_header),
                currentKey = currentMarqueeDir,
                options = marqueeDirOptions,
                onSelected = { key ->
                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, key).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:188", e) }
                    onRefreshNeeded()
                }
            )

            PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_speed", "", "Scroll Velocity (px/sec)", 10, 80, 5, 20)

            var isAvoidCutout by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, false)) }
            PrefToggleRow(
                title = lsString(R.string.hr_cutout_title),
                subtitle = lsString(R.string.hr_cutout_sub),
                isChecked = isAvoidCutout,
                onCheckedChange = {
                    isAvoidCutout = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_AVOID_CUTOUT, it).apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HorizonRailTickerSection.kt", "HorizonRailTickerSection:203", e) }
                    onRefreshNeeded()
                }
            )

            if (isAvoidCutout) {
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_CUTOUT_PADDING, "", "Wing Clearance Breathing Margin (0 to 16dp)", 0, 16, 1, 2)
            }
        }
    }
}
