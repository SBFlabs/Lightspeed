package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Sub-Component: Hardware Cutout & Punch-Hole Calibration.
 * Calibrates the physical device camera punch-hole cutout geometry
 * and provides a live HUD reticle test beacon for sub-millimeter precision.
 */
@Composable
fun HardwareCutoutCalibrationSection(
    context: Context,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    var isCutoutCalibExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_cutout_calib", false))
    }

    CollapsibleSubSection(
        title = "Hardware Cutout & Punch-Hole Calibration",
        subtitle = "Physical camera hole diameter & alignment shared across Horizon Rail and Orbital Capsule",
        isExpanded = isCutoutCalibExpanded,
        onToggle = {
            isCutoutCalibExpanded = !isCutoutCalibExpanded
            prefs.edit()
                .putBoolean("pref_sub_cutout_calib", isCutoutCalibExpanded)
                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isCutoutCalibExpanded)
                .apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
            onRefreshNeeded()
        }
    ) {
        PrefDottedSliderRow(
            context, prefs,
            LightspeedPreferences.KEY_HARDWARE_CUTOUT_WIDTH,
            "", "Camera Lens Punch-Hole Diameter (0 to 60dp)",
            0, 60, 1, 20
        )
        PrefDottedSliderRow(
            context, prefs,
            LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_X,
            "", "Horizontal Center Offset X (-30 to +30dp)",
            -30, 30, 1, 0
        )
        PrefDottedSliderRow(
            context, prefs,
            LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_Y,
            "", "Vertical Center Offset Y (-30 to +30dp)",
            -30, 30, 1, 0
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_NOTCH_TEST_BEACON,
            defaultVal = false,
            title = "Live Alignment Test Beacon",
            subtitle = "Renders a live HUD calibration reticle over the camera hole while calibrating.",
            onChanged = { onRefreshNeeded() }
        )
    }
}
