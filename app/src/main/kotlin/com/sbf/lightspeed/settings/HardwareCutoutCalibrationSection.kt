package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Sub-Component: Hardware Cutout & Punch-Hole Calibration.
 * Calibrates the physical device camera punch-hole cutout geometry
 * and provides a live HUD reticle test beacon for sub-millimeter precision.
 */
@Composable
fun HardwareCutoutCalibrationSection(
    context: Context,
    prefs: SharedPreferences,
    isCapsuleEnabled: Boolean,
    onRefreshNeeded: () -> Unit
) {
    if (!isCapsuleEnabled) {
        // Orbital Capsule is disabled — beacon test requires it to be enabled
        androidx.compose.material3.Text(
            text = lsString(R.string.cap_beacon_req),
            fontSize = 11.sp,
            color = LightspeedStyle.colors.textDisabled,
            modifier = androidx.compose.ui.Modifier.padding(vertical = 6.dp)
        )
        return
    }

    var isCutoutCalibExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_CUTOUT_CALIB, false))
    }

    CollapsibleSubSection(
        title = lsString(R.string.sec_hardware_cutout_punch_hole_cal),
        subtitle = lsString(R.string.secd_physical_camera_hole_diameter),
        isExpanded = isCutoutCalibExpanded,
        onToggle = {
            isCutoutCalibExpanded = !isCutoutCalibExpanded
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_SUB_CUTOUT_CALIB, isCutoutCalibExpanded)
                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isCutoutCalibExpanded)
                .apply()
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("HardwareCutoutCalibrationSection", "HardwareCutoutCalibrationSection:50", e) }
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
            title = lsString(R.string.cap_beacon_title),
            subtitle = lsString(R.string.cap_beacon_sub),
            onChanged = { onRefreshNeeded() }
        )
    }
}
