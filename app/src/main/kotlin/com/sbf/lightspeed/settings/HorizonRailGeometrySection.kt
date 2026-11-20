package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Sub-Component: Horizon Rail Geometry & Multi-Rail Stacking.
 * Controls physical dimensions, orientation rules, alignment,
 * offsets, drop shadow, status bar guards, and stream priority.
 */
@Composable
fun HorizonRailGeometrySection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    val screenWidthDp = remember {
        (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt()
    }

    CollapsibleSubSection(
        title = lsString(R.string.sec_horizon_rail_geometry_stacking),
        subtitle = lsString(R.string.secd_span_orientation_display_rules),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val currentRailOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both"
        val railOrientOptions = listOf(
            "both" to lsString(R.string.hr_orient_both),
            "landscape_only" to lsString(R.string.hr_orient_land),
            "portrait_only" to lsString(R.string.hr_orient_port)
        )
        PrefDropdownSelector(
            title = lsString(R.string.hr_orient_rule),
            currentKey = currentRailOrientMode,
            options = railOrientOptions,
            onSelected = { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )

        PrefDottedSliderRow(
            context = context,
            prefs = prefs,
            keyResName = LightspeedPreferences.KEY_HORIZON_RAIL_SPAN,
            titleResName = "",
            defaultTitle = "Span (≥1000 = Full Width)",
            minVal = 50,
            maxVal = 1080,
            step = 10,
            defaultVal = 1080
        )

        val currentRailAlign = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, "center") ?: "center"
        val railAlignOptions = listOf(
            "center" to lsString(R.string.hr_align_center),
            "left" to lsString(R.string.hr_align_left),
            "right" to lsString(R.string.hr_align_right)
        )
        PrefDropdownSelector(
            title = lsString(R.string.hr_align_header),
            currentKey = currentRailAlign,
            options = railAlignOptions,
            onSelected = { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_ALIGN, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )

        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_X, "", "Horizontal Offset (X Axis)", -100, 100, 5, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, "", "Vertical Offset Y (0 to 40dp)", 0, 40, 1, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_THICKNESS, "", "Line Thickness (dp)", 1, 6, 1, 2)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_STACK_SPACING, "", "Inter-Rail Stack Spacing (0 to 6dp)", 0, 6, 1, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_GLOW, "", "Glow Radiance Intensity (%)", 0, 100, 5, 60)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TRACK_OPACITY, "", "Inactive Track Opacity (%)", 0, 100, 5, 15)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, "", "Max Concurrent Rails (1 to 3)", 1, 3, 1, 2)

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_DROP_SHADOW,
            defaultVal = true,
            title = lsString(R.string.hr_shadow_title),
            subtitle = lsString(R.string.hr_shadow_sub),
            onChanged = { onRefreshNeeded() }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_TERMINAL_CAPS,
            defaultVal = true,
            title = lsString(R.string.hr_caps_title),
            subtitle = lsString(R.string.hr_caps_sub),
            onChanged = { onRefreshNeeded() }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD,
            defaultVal = true,
            title = lsString(R.string.hr_safeguard),
            subtitle = "Keeps ticker text away from the system clock and battery icons in both portrait and landscape, using the safe margins in the \"Horizon Rail Micro-Text Ticker\" section.",
            onChanged = { onRefreshNeeded() }
        )

        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, "", "Left Safe Margin Inset (dp)", 0, 80, 2, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, "", "Right Safe Margin Inset (dp)", 0, 80, 2, 0)

        val currentPriority = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top"
        val priorityOptions = listOf(
            "downloads_top" to lsString(R.string.hr_pin_dl),
            "media_top" to lsString(R.string.hr_pin_media),
            "most_recent" to lsString(R.string.hr_pin_recent)
        )
        Text(
            text = lsString(R.string.hr_pin_desc),
            fontSize = 11.sp,
            color = LightspeedStyle.colors.textDisabled,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        PrefDropdownSelector(
            title = lsString(R.string.hr_pin_header),
            currentKey = currentPriority,
            options = priorityOptions,
            onSelected = { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )
    }
}
