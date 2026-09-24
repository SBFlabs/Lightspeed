package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences

@Composable
fun DeflectorGlowCard(
    context: Context,
    prefs: SharedPreferences,
    isLeft: Boolean = false
) {
    var glowEnabled by remember {
        mutableStateOf(
            if (isLeft) LightspeedPreferences.isLeftDeflectorGlowEnabled(context)
            else LightspeedPreferences.isRightDeflectorGlowEnabled(context)
        )
    }
    var useM3Color by remember { mutableStateOf(LightspeedPreferences.isDeflectorUseM3Color(context)) }
    var glowStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorGlowStyle(context)) }
    var pillStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorPillStyle(context, isLeft)) }
    var glowOnGestureStep by remember { mutableStateOf(LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) }
    var glowDuration by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_DURATION, "1500ms") ?: "1500ms")
    }

    val styleOptions = listOf(
        "progressive_frost" to "Frosted Glass",
        "material_shade" to "Material Shade",
        "crimson_reactor" to "Crimson Reactor",
        "cyber_plasma" to "Cyber Plasma"
    )

    val durationOptions = listOf(
        "800ms" to "Fast (800ms)",
        "1500ms" to "Balanced (1.5s)",
        "2200ms" to "Extended (2.2s)"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (glowEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (glowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Master Switch Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (glowEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = if (glowEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (isLeft) "Left Pill & Deflector FX" else "Right Pill & Deflector FX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (glowEnabled) Color(0xFF00E676).copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (glowEnabled) Color(0xFF00E676).copy(alpha = 0.45f)
                                else Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = if (glowEnabled) "ACTIVE" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (glowEnabled) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        if (glowEnabled) "Morphing frosted glass pill & edge telemetry flare" else "${if (isLeft) "Left" else "Right"} deflector glow and glass pill muted",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = glowEnabled,
                    onCheckedChange = { checked ->
                        glowEnabled = checked
                        if (isLeft) {
                            LightspeedPreferences.setLeftDeflectorGlowEnabled(context, checked)
                        } else {
                            LightspeedPreferences.setRightDeflectorGlowEnabled(context, checked)
                        }
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                        if (checked) {
                            LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            if (glowEnabled) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

                // Material 3 Dynamic Tint Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Material 3 Dynamic Tint",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            if (useM3Color) "Tints frosted glass & rim highlights with system theme" else "Pure crystalline ice frost with neutral specular sheen",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Switch(
                        checked = useM3Color,
                        onCheckedChange = { checked ->
                            useM3Color = checked
                            LightspeedPreferences.setDeflectorUseM3Color(context, checked)
                            LightspeedAccessibilityService.instance?.reloadPreferences()
                            LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

                // 1. Glow Style Selection
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Aesthetic Style",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                when (glowStyle) {
                                    "progressive_frost" -> "Heavy progressive frosted diffusion with luminous specular rim."
                                    "material_shade" -> "Adaptive Material 3 dark surface tone with specular accent."
                                    "crimson_reactor" -> "High-energy thermal crimson core with warning glow."
                                    "cyber_plasma" -> "Full-spectrum kinetic chromatic gradient across the edge."
                                    else -> "Smooth aerodynamic edge glow."
                                },
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("TEST FX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styleOptions.take(2).forEach { (id, label) ->
                            val isSelected = glowStyle == id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    glowStyle = id
                                    LightspeedPreferences.setDeflectorGlowStyle(context, id)
                                    LightspeedAccessibilityService.instance?.reloadPreferences()
                                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styleOptions.drop(2).forEach { (id, label) ->
                            val isSelected = glowStyle == id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    glowStyle = id
                                    LightspeedPreferences.setDeflectorGlowStyle(context, id)
                                    LightspeedAccessibilityService.instance?.reloadPreferences()
                                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

                // 2. Gesture Step Glow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Glow on Gesture Registration",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        Text(
                            "Briefly pulse deflector wing when gesture recognition begins",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                    Switch(
                        checked = glowOnGestureStep,
                        onCheckedChange = { checked ->
                            glowOnGestureStep = checked
                            LightspeedPreferences.setDeflectorGlowOnGestureStep(context, checked)
                            LightspeedAccessibilityService.instance?.reloadPreferences()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedBorderColor = Color.Transparent,
                            uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

                // 3. Glow Duration
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Glow Fade Duration",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        durationOptions.forEach { (durationKey, label) ->
                            val isSelected = glowDuration == durationKey
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    glowDuration = durationKey
                                    prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_GLOW_DURATION, durationKey).apply()
                                    LightspeedAccessibilityService.instance?.reloadPreferences()
                                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 10.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeflectorPillStylingContent(
    context: Context,
    prefs: SharedPreferences,
    isLeft: Boolean,
    onRefreshNeeded: () -> Unit
) {
    var glowEnabled by remember {
        mutableStateOf(
            if (isLeft) LightspeedPreferences.isLeftDeflectorGlowEnabled(context)
            else LightspeedPreferences.isRightDeflectorGlowEnabled(context)
        )
    }
    var useM3Color by remember { mutableStateOf(LightspeedPreferences.isDeflectorUseM3Color(context)) }
    var glowStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorGlowStyle(context)) }
    var pillStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorPillStyle(context, isLeft)) }
    var glowOnGestureStep by remember { mutableStateOf(LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) }

    val styleOptions = listOf(
        "progressive_frost" to "Frosted Glass",
        "material_shade" to "Material Shade",
        "crimson_reactor" to "Crimson Reactor",
        "cyber_plasma" to "Cyber Plasma"
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Central Pill Active Switch Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                .clickable {
                    val next = !glowEnabled
                    glowEnabled = next
                    if (isLeft) LightspeedPreferences.setLeftDeflectorGlowEnabled(context, next)
                    else LightspeedPreferences.setRightDeflectorGlowEnabled(context, next)
                    safeReloadPreferences()
                    if (next) LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                    onRefreshNeeded()
                }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Central Pill Active Glow", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                Text(
                    if (glowEnabled) "Tactile static bezel indicator active" else "Pill hidden / dormant",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            Switch(
                checked = glowEnabled,
                onCheckedChange = { checked ->
                    glowEnabled = checked
                    if (isLeft) LightspeedPreferences.setLeftDeflectorGlowEnabled(context, checked)
                    else LightspeedPreferences.setRightDeflectorGlowEnabled(context, checked)
                    safeReloadPreferences()
                    if (checked) LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                    onRefreshNeeded()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                )
            )
        }

        val prefix = if (isLeft) "pref_sidebar_left_center" else "pref_sidebar_center"

        // Geometry Sliders (Orientation-Adaptive in Custom Landscape Mode)
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landscapeMode = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        val isCustomLandscape = landscapeMode == LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM

        if (isLandscape && isCustomLandscape) {
            val defaultLandscapeH = (prefs.getInt("${prefix}_height", 70) * 0.45f).toInt().coerceAtLeast(30)
            val defaultLandscapeY = (prefs.getInt("${prefix}_y_offset", 0) * 0.5f).toInt()
            PrefDottedSliderRow(context, prefs, "${prefix}_height_landscape", "", "Deflector Span (Height) [Landscape]", 30, 800, 10, defaultLandscapeH)
            PrefDottedSliderRow(context, prefs, "${prefix}_touch_width", "", "Touch Vector Reach", 0, 100, 1, 10)
            PrefDottedSliderRow(context, prefs, "${prefix}_y_offset_landscape", "", "Deflector Alignment Offset [Landscape]", -300, 300, 10, defaultLandscapeY)
            PrefDottedSliderRow(context, prefs, "${prefix}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 35)
        } else {
            PrefDottedSliderRow(context, prefs, "${prefix}_height", "", "Deflector Span (Height)", 50, 1000, 10, 70)
            PrefDottedSliderRow(context, prefs, "${prefix}_touch_width", "", "Touch Vector Reach", 0, 100, 1, 10)
            PrefDottedSliderRow(context, prefs, "${prefix}_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
            PrefDottedSliderRow(context, prefs, "${prefix}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 35)
        }

        if (glowEnabled) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            val pillOptions1 = listOf(
                "anchored_glow" to "Anchored",
                "floating_smart_pill" to "Smart Pill",
                "neon_core" to "Neon Core"
            )
            val pillOptions2 = listOf(
                "razor_edge" to "Razor Edge",
                "kinetic_elastic" to "Elastic",
                "hollow_ghost" to "Ghost Rim"
            )
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Pill Geometry Style", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pillOptions1.forEach { (id, label) ->
                        val isSelected = pillStyle == id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                pillStyle = id
                                LightspeedPreferences.setDeflectorPillStyle(context, isLeft, id)
                                safeReloadPreferences()
                                LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                            },
                            label = {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    pillOptions2.forEach { (id, label) ->
                        val isSelected = pillStyle == id
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                pillStyle = id
                                LightspeedPreferences.setDeflectorPillStyle(context, isLeft, id)
                                safeReloadPreferences()
                                LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                            },
                            label = {
                                Text(
                                    label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            // Dynamic M3 Color Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    .clickable {
                        val next = !useM3Color
                        useM3Color = next
                        LightspeedPreferences.setDeflectorUseM3Color(context, next)
                        safeReloadPreferences()
                        LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                        onRefreshNeeded()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Material 3 Dynamic Color", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(
                        if (useM3Color) "Tints pill with dynamic wallpaper theme color" else "Use custom aesthetic shading palette",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = useM3Color,
                    onCheckedChange = { checked ->
                        useM3Color = checked
                        LightspeedPreferences.setDeflectorUseM3Color(context, checked)
                        safeReloadPreferences()
                        LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                        onRefreshNeeded()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            if (!useM3Color) {
                // Style selector chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Pill Aesthetic Style",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styleOptions.take(2).forEach { (id, label) ->
                            val isSelected = glowStyle == id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    glowStyle = id
                                    LightspeedPreferences.setDeflectorGlowStyle(context, id)
                                    safeReloadPreferences()
                                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        styleOptions.drop(2).forEach { (id, label) ->
                            val isSelected = glowStyle == id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    glowStyle = id
                                    LightspeedPreferences.setDeflectorGlowStyle(context, id)
                                    safeReloadPreferences()
                                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                                },
                                label = {
                                    Text(
                                        label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Glow on Gesture Step
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                    .clickable {
                        val next = !glowOnGestureStep
                        glowOnGestureStep = next
                        LightspeedPreferences.setDeflectorGlowOnGestureStep(context, next)
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Glow on Gesture Registration", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                    Text(
                        "Briefly pulse pill when gesture recognition begins",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = glowOnGestureStep,
                    onCheckedChange = { checked ->
                        glowOnGestureStep = checked
                        LightspeedPreferences.setDeflectorGlowOnGestureStep(context, checked)
                        safeReloadPreferences()
                        onRefreshNeeded()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )
            }

            // Test FX button
            OutlinedButton(
                onClick = {
                    LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("PREVIEW PILL GLOW FX", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DeflectorAestheticsCard(
    context: Context,
    prefs: SharedPreferences,
    isLeft: Boolean,
    onRefreshNeeded: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Pill Styling & Aesthetics",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White
                    )
                    Text(
                        "Visual geometry styles, shaders & active glow",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            DeflectorPillStylingContent(
                context = context,
                prefs = prefs,
                isLeft = isLeft,
                onRefreshNeeded = onRefreshNeeded
            )
        }
    }
}
