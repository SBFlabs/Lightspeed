package com.sbf.lightspeed.settings

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
        title = "Horizon Rail Geometry & Stacking",
        subtitle = "Span, orientation display rules, safe-margins, glow & offsets",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val currentRailOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both"
        val railOrientOptions = listOf(
            "both" to "🔄 Both Orientations (Always Active)",
            "landscape_only" to "📐 Landscape Only (Horizontal Deck)",
            "portrait_only" to "📱 Portrait Only"
        )
        PrefDropdownSelector(
            title = "RAIL ORIENTATION DISPLAY RULE",
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

        val currentRailAlign = prefs.getString("pref_horizon_rail_align", "center") ?: "center"
        val railAlignOptions = listOf(
            "center" to "Center Aligned",
            "left" to "Left Aligned",
            "right" to "Right Aligned"
        )
        PrefDropdownSelector(
            title = "RAIL ALIGNMENT",
            currentKey = currentRailAlign,
            options = railAlignOptions,
            onSelected = { key ->
                prefs.edit().putString("pref_horizon_rail_align", key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )

        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_offset_x", "", "Horizontal Offset (X Axis)", -100, 100, 5, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_OFFSET_Y, "", "Vertical Offset Y (0 to 40dp)", 0, 40, 1, 0)
        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_thickness", "", "Line Thickness (dp)", 1, 6, 1, 2)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_STACK_SPACING, "", "Inter-Rail Stack Spacing (0 to 6dp)", 0, 6, 1, 0)
        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_glow", "", "Glow Radiance Intensity (%)", 0, 100, 5, 60)
        PrefDottedSliderRow(context, prefs, "pref_horizon_rail_track_opacity", "", "Inactive Track Opacity (%)", 0, 100, 5, 15)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_MAX_COUNT, "", "Max Concurrent Rails (1 to 3)", 1, 3, 1, 2)

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_DROP_SHADOW,
            defaultVal = true,
            title = "Ambient Drop Shadow & Contrast Trench",
            subtitle = "Renders a dark ambient occlusion shadow underneath the rails to maintain crisp separation from matching wallpapers and light backgrounds.",
            onChanged = { onRefreshNeeded() }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_TERMINAL_CAPS,
            defaultVal = true,
            title = "Tactical Head Caps & End Markers",
            subtitle = "Renders high-contrast specular notches at the progress head of each rail for pinpoint completion readout.",
            onChanged = { onRefreshNeeded() }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_STATUS_BAR_GUARD,
            defaultVal = true,
            title = "Landscape Status Bar Safe Guard",
            subtitle = "Automatically prevents long telemetry text or rails from occluding system status bar items (clock and network/battery icons).",
            onChanged = { onRefreshNeeded() }
        )

        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_LEFT, "", "Left Safe Margin Inset (dp)", 0, 80, 2, 0)
        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_SAFE_PADDING_RIGHT, "", "Right Safe Margin Inset (dp)", 0, 80, 2, 0)

        val currentPriority = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top"
        val priorityOptions = listOf(
            "downloads_top" to "⬇ Pin Downloads on Top (Shows Text Ticker)",
            "media_top" to "♫ Pin Media on Top (Shows Text Ticker)",
            "most_recent" to "⏱ Most Recent Stream on Top"
        )
        Text(
            text = "Select which active stream is pinned at the top and displays the typography ticker.",
            fontSize = 11.sp,
            color = Color.Gray,
            lineHeight = 14.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        PrefDropdownSelector(
            title = "MULTI-RAIL PINNING & PRIORITY",
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
