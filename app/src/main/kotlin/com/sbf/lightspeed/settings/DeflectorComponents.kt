package com.sbf.lightspeed.settings

import androidx.compose.ui.text.style.TextAlign
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Security



@Composable
fun UnifyFlankActionsCard(
    isUnified: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = if (isUnified) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unify Upper & Lower Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White
                )
                Text(
                    if (isUnified) "Single unified gesture set • Dual scrubbers active" else "Independent upper & lower gesture sets",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = isUnified,
                onCheckedChange = { onToggle(it) },
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
    }
}

@Composable
fun DeflectorMasterCard(
    flankName: String,
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    startupMode: String,
    onStartupModeChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.surface.copy(alpha = 0.2f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            else Color.White.copy(alpha = 0.08f)
        )
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.08f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.Shield else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            flankName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isEnabled) Color(0xFF00E676).copy(alpha = 0.18f)
                            else Color.White.copy(alpha = 0.1f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isEnabled) Color(0xFF00E676).copy(alpha = 0.45f)
                                else Color.White.copy(alpha = 0.2f)
                            )
                        ) {
                            Text(
                                text = if (isEnabled) "ACTIVE" else "OFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isEnabled) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        if (isEnabled) "Flank touch sensors armed and responsive" else "Touch capture muted along screen edge",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = { onToggle(it) },
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

            // Startup state preference block (Full width description with selectable chips underneath)
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Startup Default State",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    if (startupMode == "standby_by_default")
                        "Starts in Standby (Muted) on service boot. Stays dormant until manually armed."
                    else
                        "Automatically armed and responsive when service boots.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    lineHeight = 15.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isAlwaysArmed = startupMode != "standby_by_default"
                    FilterChip(
                        selected = isAlwaysArmed,
                        onClick = { onStartupModeChange("always_armed") },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Armed on Boot", fontSize = 11.5.sp, fontWeight = if (isAlwaysArmed) FontWeight.Bold else FontWeight.Normal)
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isAlwaysArmed,
                        onClick = { onStartupModeChange("standby_by_default") },
                        label = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Off by Default", fontSize = 11.5.sp, fontWeight = if (!isAlwaysArmed) FontWeight.Bold else FontWeight.Normal)
                            }
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
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
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
    val prefix = if (isLeft) "pref_sidebar_left_center" else "pref_sidebar_center"

    var glowEnabled by remember {
        mutableStateOf(
            if (isLeft) LightspeedPreferences.isLeftDeflectorGlowEnabled(context)
            else LightspeedPreferences.isRightDeflectorGlowEnabled(context)
        )
    }
    var useM3Color by remember { mutableStateOf(LightspeedPreferences.isDeflectorUseM3Color(context)) }
    var glowStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorGlowStyle(context)) }
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
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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

        if (glowEnabled) {
            // Visual Thickness Slider
            PrefDottedSliderRow(context, prefs, "${prefix}_visual_width", "", "Pill Visual Thickness", 2, 40, 2, 6)
        }

        // Geometry Sliders
        PrefDottedSliderRow(context, prefs, "${prefix}_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
        PrefDottedSliderRow(context, prefs, "${prefix}_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
        PrefDottedSliderRow(context, prefs, "${prefix}_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
        PrefDottedSliderRow(context, prefs, "${prefix}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

        if (glowEnabled) {
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
fun SymmetryCouplingCard(
    context: Context,
    prefs: SharedPreferences,
    onModeChanged: () -> Unit = {},
    onInfoClick: () -> Unit = {}
) {
    var geomMode by remember { mutableStateOf(prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent") }
    var gestMode by remember { mutableStateOf(prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deflector Symmetry & Coupling", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text("Synchronize wings or maintain bilateral independence", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // 1. Physical Geometry Switch
            ThreeWayTacticalSelector(
                title = "Physical Geometry (Span, Reach, Offset, Glow)",
                subtitle = when (geomMode) {
                    "right" -> "Right Deflector master — Left Deflector mirrors right geometry"
                    "left" -> "Left Deflector master — Right Deflector mirrors left geometry"
                    else -> "Independent — Each deflector has custom geometry"
                },
                selectedMode = geomMode,
                onSelect = { mode ->
                    geomMode = mode
                    prefs.edit().putString("pref_symmetry_geometry_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )

            // 2. Astrogation & Gestures Switch
            ThreeWayTacticalSelector(
                title = "Astrogation & Gestures (Cockpit & Macros)",
                subtitle = when (gestMode) {
                    "right" -> "Right Deflector master — Left Deflector inverts & executes right actions"
                    "left" -> "Left Deflector master — Right Deflector inverts & executes left actions"
                    else -> "Independent — Each deflector has dedicated gesture maps"
                },
                selectedMode = gestMode,
                onSelect = { mode ->
                    gestMode = mode
                    prefs.edit().putString("pref_symmetry_gesture_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )
        }
    }
}

