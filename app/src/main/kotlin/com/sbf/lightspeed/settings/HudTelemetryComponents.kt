package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*

@Composable
fun HudTelemetryIndicatorsSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isHorizonRailGeomExpanded: Boolean,
    onToggleHorizonRailGeom: () -> Unit,
    isHorizonRailColorExpanded: Boolean,
    onToggleHorizonRailColor: () -> Unit,
    isHorizonRailTextExpanded: Boolean,
    onToggleHorizonRailText: () -> Unit,
    onShowNotificationAccessDialog: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val isNotifAccessGranted = remember(isExpanded) {
                val pkgName = context.packageName
                val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                flat?.contains(pkgName) == true || LightspeedNotificationListener.instance != null
            }

            // Visual Dual-Channel HUD Guide Badge Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("DUAL-CHANNEL TELEMETRY HUD", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("📏 Horizon Rail", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Text("Ultra-thin progress line on display top edge", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("💊 Orbital Capsule", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Text("Dynamic liquid-glass island on camera cutout", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                        }
                    }
                }
            }

            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK,
                defaultVal = true,
                title = "Suppress on Lock Screen & OEM Screensavers",
                subtitle = "Automatically hides Orbital Capsule and HUD Strip when device is locked or running OEM ambient dock.",
                onChanged = { onRefreshNeeded() }
            )

            if (!isNotifAccessGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onShowNotificationAccessDialog() }
                        .padding(vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                            Text("Tap here to grant permission in Android Settings so Lightspeed can read download progress and media metadata.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            // Downloads Telemetry Selector
            val currentDl = prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line") ?: "top_line"
            var isDlDropdownOpen by remember { mutableStateOf(false) }
            val routingOptions = listOf(
                "none" to "None (Disabled)",
                "top_line" to "Horizon Rail (Top-Edge Line)",
                "notch_pill" to "Orbital Capsule [Experimental Labs]",
                "both" to "Both (Horizon Rail & Orbital Capsule [Labs])"
            )

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("DOWNLOADS TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isDlDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(routingOptions.firstOrNull { it.first == currentDl }?.second ?: "Horizon Rail (Top-Edge Line)", color = Color.White)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isDlDropdownOpen,
                        onDismissRequest = { isDlDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        routingOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isDlDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, key).apply()
                                    if (key != "none" && !isNotifAccessGranted) {
                                        onShowNotificationAccessDialog()
                                    }
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            // Media Telemetry Selector
            val currentMedia = prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none"
            var isMediaDropdownOpen by remember { mutableStateOf(false) }

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("MEDIA PLAYBACK TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Box {
                    OutlinedButton(
                        onClick = { isMediaDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(routingOptions.firstOrNull { it.first == currentMedia }?.second ?: "None (Disabled)", color = Color.White)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isMediaDropdownOpen,
                        onDismissRequest = { isMediaDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        routingOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                text = { Text(label) },
                                onClick = {
                                    isMediaDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, key).apply()
                                    if (key != "none" && !isNotifAccessGranted) {
                                        onShowNotificationAccessDialog()
                                    }
                                    onRefreshNeeded()
                                }
                            )
                        }
                    }
                }
            }

            // 1. Horizon Rail Customization
            val screenWidthDp = remember { (context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt() }
            val currentColorMode = prefs.getString("pref_horizon_rail_color_mode", "cover_art") ?: "cover_art"
            var isColorModeDropdownOpen by remember { mutableStateOf(false) }
            val colorModeOptions = listOf(
                "cover_art" to "🖼 Follow Media Cover Art (Auto Fallback)",
                "app_icon" to "🎨 Notification App Icon Color",
                "material3" to "🌈 Material 3 Dynamic Accent",
                "inverted" to "☯ Inverted Screen Contrast",
                "custom" to "🎯 Custom Matrix Cyber Chip Palette"
            )

            val currentRailOrientMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, "both") ?: "both"
            var isRailOrientDropdownOpen by remember { mutableStateOf(false) }
            val railOrientOptions = listOf(
                "both" to "🔄 Both Orientations (Always Active)",
                "landscape_only" to "📐 Landscape Only (Horizontal Deck)",
                "portrait_only" to "📱 Portrait Only"
            )

            val currentRailAlign = prefs.getString("pref_horizon_rail_align", "center") ?: "center"
            var isRailAlignDropdownOpen by remember { mutableStateOf(false) }
            val railAlignOptions = listOf(
                "center" to "Center Aligned",
                "left" to "Left Aligned",
                "right" to "Right Aligned"
            )

            val currentTextPos = prefs.getString("pref_horizon_rail_text_position", "below") ?: "below"
            var isTextPosDropdownOpen by remember { mutableStateOf(false) }
            val textPosOptions = listOf(
                "below" to "Below Rail Line (Recommended)",
                "above" to "Above Rail Line",
                "embedded" to "Centered Inside Track",
                "below_statusbar" to "Below Entire Status Bar Deck"
            )

            val currentPriority = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, "downloads_top") ?: "downloads_top"
            var isPriorityDropdownOpen by remember { mutableStateOf(false) }
            val priorityOptions = listOf(
                "downloads_top" to "⬇ Pin Downloads on Top (Shows Text Ticker)",
                "media_top" to "♫ Pin Media on Top (Shows Text Ticker)",
                "most_recent" to "⏱ Most Recent Stream on Top"
            )

            // 1. Sub-Accordion: Horizon Rail Geometry & Stacking
            CollapsibleSubSection(
                title = "Horizon Rail Geometry & Stacking",
                subtitle = "Span, orientation display rules, safe-margins, glow & offsets",
                isExpanded = isHorizonRailGeomExpanded,
                onToggle = onToggleHorizonRailGeom
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("RAIL ORIENTATION DISPLAY RULE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { isRailOrientDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(railOrientOptions.firstOrNull { it.first == currentRailOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(
                            expanded = isRailOrientDropdownOpen,
                            onDismissRequest = { isRailOrientDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            railOrientOptions.forEach { (key, label) ->
                                DropMenuItemWrapper(label) {
                                    isRailOrientDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_ORIENTATION_MODE, key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            }
                        }
                    }
                }

                PrefDottedSliderRow(context, prefs, "pref_horizon_rail_span", "", "Span (Max: ${screenWidthDp}dp)", 50, screenWidthDp, 10, screenWidthDp)

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("RAIL ALIGNMENT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { isRailAlignDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(railAlignOptions.firstOrNull { it.first == currentRailAlign }?.second ?: "Center Aligned", color = Color.White, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(
                            expanded = isRailAlignDropdownOpen,
                            onDismissRequest = { isRailAlignDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            railAlignOptions.forEach { (key, label) ->
                                DropMenuItemWrapper(label) {
                                    isRailAlignDropdownOpen = false
                                    prefs.edit().putString("pref_horizon_rail_align", key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            }
                        }
                    }
                }

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

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("MULTI-RAIL PINNING & PRIORITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Select which active stream is pinned at the top and displays the typography ticker.", fontSize = 11.sp, color = Color.Gray, lineHeight = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { isPriorityDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(priorityOptions.firstOrNull { it.first == currentPriority }?.second ?: "⬇ Pin Downloads on Top", color = Color.White, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(
                            expanded = isPriorityDropdownOpen,
                            onDismissRequest = { isPriorityDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            priorityOptions.forEach { (key, label) ->
                                DropMenuItemWrapper(label) {
                                    isPriorityDropdownOpen = false
                                    prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_PRIORITY, key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            }
                        }
                    }
                }
            }

            // 2. Sub-Accordion: Horizon Rail Color & Styling
            CollapsibleSubSection(
                title = "Horizon Rail Colors & Contrast",
                subtitle = "Cover art dynamic sampling, palette presets & ambient outline",
                isExpanded = isHorizonRailColorExpanded,
                onToggle = onToggleHorizonRailColor
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("HORIZON RAIL COLOR MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { isColorModeDropdownOpen = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(colorModeOptions.firstOrNull { it.first == currentColorMode }?.second ?: "🖼 Follow Media Cover Art", color = Color.White, fontSize = 12.sp)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        DropdownMenu(
                            expanded = isColorModeDropdownOpen,
                            onDismissRequest = { isColorModeDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            colorModeOptions.forEach { (key, label) ->
                                DropMenuItemWrapper(label) {
                                    isColorModeDropdownOpen = false
                                    prefs.edit().putString("pref_horizon_rail_color_mode", key).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            }
                        }
                    }
                }

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
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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

            // 3. Sub-Accordion: Horizon Rail Micro-Text Ticker
            CollapsibleSubSection(
                title = "Horizon Rail Micro-Text Ticker",
                subtitle = "Typography, dual-wing marquee, bounce velocity & orientation rules",
                isExpanded = isHorizonRailTextExpanded,
                onToggle = onToggleHorizonRailText
            ) {
                var isRailTextEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_horizon_rail_text_enabled", true)) }
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
                    var isTextOrientDropdownOpen by remember { mutableStateOf(false) }
                    val textOrientOptions = listOf(
                        "both" to "🔄 Both Orientations",
                        "landscape_only" to "📐 Landscape Only",
                        "portrait_only" to "📱 Portrait Only"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MICRO-TEXT ORIENTATION FILTER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isTextOrientDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(textOrientOptions.firstOrNull { it.first == currentTextOrientMode }?.second ?: "🔄 Both Orientations", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isTextOrientDropdownOpen,
                                onDismissRequest = { isTextOrientDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                textOrientOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isTextOrientDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_ORIENTATION_MODE, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    val currentMetadataMode = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, "adaptive") ?: "adaptive"
                    var isMetadataDropdownOpen by remember { mutableStateOf(false) }
                    val metadataOptions = listOf(
                        "adaptive" to "⚡ Adaptive (Title in Portrait, Full Metadata in Landscape)",
                        "full" to "📜 Full Metadata Always (Title + Artist + Episode/Album)",
                        "title_only" to "🏷 Title Only Always"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("METADATA DENSITY & ORIENTATION MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isMetadataDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(metadataOptions.firstOrNull { it.first == currentMetadataMode }?.second ?: "⚡ Adaptive", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isMetadataDropdownOpen,
                                onDismissRequest = { isMetadataDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                metadataOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isMetadataDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_METADATA_MODE, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_SHOW_TIMESTAMP,
                        defaultVal = false,
                        title = "Include Playback Progress Timestamp",
                        subtitle = "Appends live track position and duration (e.g. 02:45 / 05:10) to the micro-text ticker.",
                        onChanged = { onRefreshNeeded() }
                    )

                    // Letter Casing Format Selector
                    val currentCasing = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, "natural") ?: "natural"
                    var isCasingDropdownOpen by remember { mutableStateOf(false) }
                    val casingOptions = listOf(
                        "natural" to "✨ Original / Natural Casing (Title & Artist)",
                        "all_caps" to "🔤 ALL CAPS (Aviation HUD Avionics)",
                        "title_case" to "🔠 Title Case (Capitalize Every Word)"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("LETTER CASING FORMAT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isCasingDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(casingOptions.firstOrNull { it.first == currentCasing }?.second ?: "✨ Original / Natural Casing", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isCasingDropdownOpen,
                                onDismissRequest = { isCasingDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                casingOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isCasingDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_CASING, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    // Device Installed Font Family Selector
                    val currentFont = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, "system_default") ?: "system_default"
                    var isFontDropdownOpen by remember { mutableStateOf(false) }
                    val availableFonts = remember { DeviceFontScanner.getInstalledFonts() }

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MICRO-TEXT FONT FAMILY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isFontDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(availableFonts.firstOrNull { it.first == currentFont }?.second ?: "📱 Follow Device (System Default)", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isFontDropdownOpen,
                                onDismissRequest = { isFontDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                availableFonts.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isFontDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_FONT, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    PrefDottedSliderRow(context, prefs, "pref_horizon_rail_text_size", "", "Micro-Font Size (dp)", 7, 16, 1, 9)

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MICRO-TEXT POSITION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isTextPosDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(textPosOptions.firstOrNull { it.first == currentTextPos }?.second ?: "Below Rail Line (Recommended)", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isTextPosDropdownOpen,
                                onDismissRequest = { isTextPosDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                textPosOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isTextPosDropdownOpen = false
                                        prefs.edit().putString("pref_horizon_rail_text_position", key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HORIZON_RAIL_TEXT_OFFSET_Y, "", "Vertical Fine Y-Offset (dp)", -20, 40, 1, 0)

                    // Marquee Scope & Sider Controls
                    val currentMarqueeScope = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, "both_wings") ?: "both_wings"
                    var isMarqueeScopeDropdownOpen by remember { mutableStateOf(false) }
                    val marqueeScopeOptions = listOf(
                        "both_wings" to "🔀 Dual-Wing Independent (Marquee Overflowing Side)",
                        "right_wing_only" to "👉 Right Wing Only (Artist / Episode / Sider)",
                        "left_wing_only" to "👈 Left Wing Only (Title)",
                        "unified" to "🔗 Unified Stream (Split Across Wings & Marquee)"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MARQUEE SCOPE & SIDER TARGET", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isMarqueeScopeDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(marqueeScopeOptions.firstOrNull { it.first == currentMarqueeScope }?.second ?: "🔀 Dual-Wing Independent", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isMarqueeScopeDropdownOpen,
                                onDismissRequest = { isMarqueeScopeDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                marqueeScopeOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isMarqueeScopeDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_SCOPE, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    // Marquee Animation Style: Bounce vs Continuous Loop
                    val currentMarqueeAnim = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, "continuous_wrap") ?: "continuous_wrap"
                    var isMarqueeAnimDropdownOpen by remember { mutableStateOf(false) }
                    val marqueeAnimOptions = listOf(
                        "continuous_wrap" to "♾️ Continuous Loop (Seamless Wrap Across Edges)",
                        "bounce" to "🏓 Bounce / Ping-Pong (Back & Forth with Edge Pauses)"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MARQUEE ANIMATION STYLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isMarqueeAnimDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(marqueeAnimOptions.firstOrNull { it.first == currentMarqueeAnim }?.second ?: "♾️ Continuous Loop", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isMarqueeAnimDropdownOpen,
                                onDismissRequest = { isMarqueeAnimDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                marqueeAnimOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isMarqueeAnimDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_ANIM_MODE, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

                    // Marquee Direction
                    val currentMarqueeDir = prefs.getString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, "rtl") ?: "rtl"
                    var isMarqueeDirDropdownOpen by remember { mutableStateOf(false) }
                    val marqueeDirOptions = listOf(
                        "rtl" to "⬅️ Right-to-Left (Standard RTL)",
                        "ltr" to "➡️ Left-to-Right (LTR)"
                    )

                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("MARQUEE SCROLL DIRECTION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box {
                            OutlinedButton(
                                onClick = { isMarqueeDirDropdownOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(marqueeDirOptions.firstOrNull { it.first == currentMarqueeDir }?.second ?: "⬅️ Right-to-Left (Standard RTL)", color = Color.White, fontSize = 12.sp)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            DropdownMenu(
                                expanded = isMarqueeDirDropdownOpen,
                                onDismissRequest = { isMarqueeDirDropdownOpen = false },
                                modifier = Modifier
                                    .background(Color(0xF012141A))
                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                containerColor = Color(0xF012141A)
                            ) {
                                marqueeDirOptions.forEach { (key, label) ->
                                    DropMenuItemWrapper(label) {
                                        isMarqueeDirDropdownOpen = false
                                        prefs.edit().putString(LightspeedPreferences.KEY_HORIZON_RAIL_MARQUEE_DIRECTION, key).apply()
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                }
                            }
                        }
                    }

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

            // 4. Shared Hardware Cutout & Punch-Hole Calibration
            var isCutoutCalibExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_cutout_calib", false)) }
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
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_WIDTH, "", "Camera Lens Punch-Hole Diameter (0 to 60dp)", 0, 60, 1, 20)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_X, "", "Horizontal Center Offset X (-30 to +30dp)", -30, 30, 1, 0)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_HARDWARE_CUTOUT_OFFSET_Y, "", "Vertical Center Offset Y (-30 to +30dp)", -30, 30, 1, 0)

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
    }
}

@Composable
private fun DropMenuItemWrapper(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        modifier = Modifier.heightIn(min = 48.dp),
        text = { Text(label) },
        onClick = onClick
    )
}
