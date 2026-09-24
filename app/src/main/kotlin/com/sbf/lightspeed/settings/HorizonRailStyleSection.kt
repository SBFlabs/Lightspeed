package com.sbf.lightspeed.settings

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
    CollapsibleSubSection(
        title = "Horizon Rail Colors & Contrast",
        subtitle = "Cover art dynamic sampling, palette presets & ambient outline",
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        val currentColorMode = prefs.getString("pref_horizon_rail_color_mode", "cover_art") ?: "cover_art"
        val colorModeOptions = listOf(
            "cover_art" to "🖼 Follow Media Cover Art (Auto Fallback)",
            "app_icon" to "🎨 Notification App Icon Color",
            "material3" to "🌈 Material 3 Dynamic Accent",
            "inverted" to "☯ Inverted Screen Contrast",
            "custom" to "🎯 Custom Matrix Cyber Chip Palette"
        )

        PrefDropdownSelector(
            title = "HORIZON RAIL COLOR MODE",
            currentKey = currentColorMode,
            options = colorModeOptions,
            onSelected = { key ->
                prefs.edit().putString("pref_horizon_rail_color_mode", key).apply()
                safeReloadPreferences()
                onRefreshNeeded()
            }
        )

        if (currentColorMode == "custom") {
            val customHex = prefs.getString("pref_horizon_rail_custom_color", "#00E5FF") ?: "#00E5FF"
            val presetColors = listOf(
                "#00E5FF" to "Cyan",
                "#00E676" to "Green",
                "#FFD600" to "Amber",
                "#FF6D00" to "Orange",
                "#FF1744" to "Crimson",
                "#FF007F" to "Pink",
                "#D500F9" to "Purple",
                "#FFFFFF" to "White"
            )
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("CUSTOM PALETTE PRESETS", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = Color.LightGray)
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
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.3f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    prefs.edit().putString("pref_horizon_rail_custom_color", hex).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = if (hex == "#FFFFFF") Color.Black else Color.White, modifier = Modifier.size(16.dp))
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
            title = "High-Contrast Ambient Outline",
            subtitle = "Renders a dark semi-transparent outline halo around glyphs to guarantee 100% legibility on pure white backgrounds.",
            onChanged = { onRefreshNeeded() }
        )
    }
}
