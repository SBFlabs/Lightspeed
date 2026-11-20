package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Sub-Component: Horizon Rail Colors, Dynamic Palette & Contrast.
 * Controls color sampling mode (media cover art, app icon, Material 3, inverted, custom),
 * cyber-chip palette presets, and ambient contrast outline shield.
 */
@Composable
fun HorizonRailStyleSection(
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    CollapsibleSubSection(
        title = lsString(R.string.sec_horizon_rail_colors_contrast),
        subtitle = lsString(R.string.secd_cover_art_dynamic_sampling_pal),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val currentColorMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_COLOR_MODE, "cover_art") ?: "cover_art"
        val colorModeOptions = listOf(
            "cover_art" to lsString(R.string.hr_col_cover),
            "app_icon" to lsString(R.string.hr_col_notif),
            "material3" to lsString(R.string.hr_col_m3),
            "inverted" to lsString(R.string.hr_col_invert),
            "custom" to lsString(R.string.hr_col_custom)
        )

        PrefDropdownSelector(
            title = lsString(R.string.hr_col_header),
            currentKey = currentColorMode,
            options = colorModeOptions,
            onSelected = { key ->
                prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_COLOR_MODE, key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )

        if (currentColorMode == "custom") {
            val customHex = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_CUSTOM_COLOR, "#00E5FF") ?: "#00E5FF"
            val presetColors = listOf(
                "#00E5FF" to lsString(R.string.col_cyan),
                "#00E676" to lsString(R.string.col_green),
                "#FFD600" to lsString(R.string.col_amber),
                "#FF6D00" to lsString(R.string.col_orange),
                "#FF1744" to lsString(R.string.col_crimson),
                "#FF007F" to lsString(R.string.col_pink),
                "#D500F9" to lsString(R.string.col_purple),
                "#FFFFFF" to lsString(R.string.col_white)
            )
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(lsString(R.string.hr_presets_header), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textSecondary)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    presetColors.forEach { (hex, _) ->
                        val col = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Cyan }
                        val isSelected = customHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) lsColors.textPrimary else lsColors.textPrimary.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_CUSTOM_COLOR, hex).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = if (hex == "#FFFFFF") Color.Black else lsColors.textPrimary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_CONTRAST_SHIELD,
            defaultVal = true,
            title = lsString(R.string.hr_outline_title),
            subtitle = lsString(R.string.hr_outline_sub),
            onChanged = { onRefreshNeeded() }
        )
    }
}
