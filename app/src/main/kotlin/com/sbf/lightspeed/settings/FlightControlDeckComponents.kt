package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FlightControlDeckCard(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    var isArmed by remember { mutableStateOf(LightspeedPreferences.isMasterFlightArmed(context)) }
    var isNotifEnabled by remember { mutableStateOf(LightspeedPreferences.isFlightNotificationEnabled(context)) }
    var isAutomationDocsExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isArmed) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color(0xFFFF9800).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val setFlightArmed: (Boolean) -> Unit = { armed ->
                isArmed = armed
                LightspeedPreferences.setMasterFlightArmed(context, armed)
                LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
                onStateChanged()
            }

            // Master Flight Control: Interactive 1-Tap Cockpit Hero Pad
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        LightspeedHapticEngine.heavyClick(context)
                        setFlightArmed(!isArmed)
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isArmed) Color(0xFF00E676).copy(alpha = 0.12f)
                    else Color(0xFFFF9800).copy(alpha = 0.10f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isArmed) Color(0xFF00E676).copy(alpha = 0.5f)
                    else Color(0xFFFF9800).copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isArmed) Color(0xFF00E676).copy(alpha = 0.22f)
                                else Color(0xFFFF9800).copy(alpha = 0.22f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isArmed) Icons.Default.Bolt else Icons.Default.PowerSettingsNew,
                            contentDescription = null,
                            tint = if (isArmed) Color(0xFF00E676) else Color(0xFFFF9800),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "MASTER FLIGHT DECK",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            if (isArmed) "ARMED // All sensors & hangars live"
                            else "STANDBY // Overlays detached",
                            fontSize = 11.sp,
                            color = if (isArmed) Color(0xFF00E676) else Color(0xFFFF9800),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            var showLiquidGlassDialog by remember { mutableStateOf(false) }
            var showInfinityDialog by remember { mutableStateOf(false) }
            if (showLiquidGlassDialog) {
                LiquidGlassCustomizationDialog(
                    context = context,
                    onDismiss = { showLiquidGlassDialog = false }
                )
            }
            if (showInfinityDialog) {
                LightspeedInfinityDialog(
                    context = context,
                    onDismiss = { showInfinityDialog = false },
                    onUnlocked = { onStateChanged() }
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Live Glass Material Prototype Selector
            val styleFlow by LightspeedPreferences.deckGlassStyleFlow.collectAsState()
            val currentGlassStyle = styleFlow ?: remember { LightspeedPreferences.getDeckGlassStyle(context) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CENTRAL COMMAND THEME",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        when (currentGlassStyle) {
                            "frost" -> "Deep Frosted Matte"
                            "obsidian" -> "Tactical Stealth"
                            else -> "\"Refractive Liquid Glass\""
                        },
                        fontSize = 10.sp,
                        color = Color.LightGray.copy(alpha = 0.75f)
                    )
                }

                val isInfinityUnlocked = com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "liquid" to "\"Liquid Glass\"",
                        "frost" to "Deep Frost",
                        "obsidian" to "Obsidian"
                    ).forEach { (styleKey, title) ->
                        val isSelected = currentGlassStyle == styleKey
                        val isLocked = styleKey == "obsidian" && !isInfinityUnlocked
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (isLocked) {
                                            LightspeedHapticEngine.triggerWarning(context)
                                            showInfinityDialog = true
                                        } else {
                                            LightspeedPreferences.setDeckGlassStyle(context, styleKey)
                                            LightspeedHapticEngine.tick(context)
                                            onStateChanged()
                                        }
                                    },
                                    onLongClick = if (styleKey == "liquid") {
                                        {
                                            LightspeedHapticEngine.tick(context)
                                            showLiquidGlassDialog = true
                                        }
                                    } else null
                                ),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isLocked) "$title 🔒" else title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isLocked) Color(0xFFFFD700) else if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Lightspeed Infinity Status / Upgrade Card
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            showInfinityDialog = true
                        },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isInfinityUnlocked) Color(0xFFFFD700).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isInfinityUnlocked) Color(0xFFFFD700).copy(alpha = 0.45f) else Color.White.copy(alpha = 0.1f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(if (isInfinityUnlocked) "★" else "☕", fontSize = 12.sp, color = if (isInfinityUnlocked) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary)
                            Text(
                                text = if (isInfinityUnlocked) "LIGHTSPEED INFINITY ACTIVE" else "UPGRADE TO LIGHTSPEED INFINITY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isInfinityUnlocked) Color(0xFFFFD700) else Color.White.copy(alpha = 0.85f),
                                letterSpacing = 0.8.sp
                            )
                        }
                        Text(
                            text = if (isInfinityUnlocked) "UNLIMITED DECKS" else "2 DECKS (FREE) ▾",
                            fontSize = 9.5.sp,
                            color = if (isInfinityUnlocked) Color(0xFFFFD700).copy(alpha = 0.85f) else Color.Gray,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Cockpit Backdrop / Area Behind Window Selector
            val backdropFlow by LightspeedPreferences.deckBackdropStyleFlow.collectAsState()
            val currentBackdropStyle = backdropFlow ?: remember { LightspeedPreferences.getDeckBackdropStyle(context) }
            val currentBackdropVisuals = remember(currentBackdropStyle) { DeckBackdropTheme.resolve(currentBackdropStyle) }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "BACKDROP THEME",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        currentBackdropVisuals.subtitle,
                        fontSize = 9.5.sp,
                        color = Color.LightGray.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    DeckBackdropTheme.STYLES.forEach { backdrop ->
                        val isSelected = currentBackdropStyle == backdrop.styleKey
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    LightspeedPreferences.setDeckBackdropStyle(context, backdrop.styleKey)
                                    LightspeedHapticEngine.tick(context)
                                    onStateChanged()
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
                            )
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = backdrop.title,
                                    fontSize = 9.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.75f),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Row 3: Home Screen 1-Tap Shortcut Buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "HOME SCREEN 1-TAP SHORTCUTS",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { com.sbf.lightspeed.LightspeedToggleActivity.pinMasterToggleShortcut(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pin Flight Mode", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = { com.sbf.lightspeed.LightspeedToggleActivity.pinDeflectorsToggleShortcut(context) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pin Deflectors", fontSize = 11.5.sp)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // Row 4: Persistent Flight Control Notification Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Flight Control Notification",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "Persistent notification with quick controls",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
                Switch(
                    checked = isNotifEnabled,
                    onCheckedChange = { enabled ->
                        isNotifEnabled = enabled
                        LightspeedPreferences.setFlightNotificationEnabled(context, enabled)
                        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
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

            // Row 5: Quick Settings Tiles Notice
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    "Quick Settings: Two interactive tiles are available in your Android QS shade: 'Lightspeed' (Master Toggle) and 'Deflectors' (Flanks Toggle).",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    lineHeight = 15.sp
                )
            }

            // Row 5: Automation & Tasker / MacroDroid Accordion
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAutomationDocsExpanded = !isAutomationDocsExpanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "MacroDroid / Tasker / Termux API",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Icon(
                            if (isAutomationDocsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isAutomationDocsExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Send Broadcast Intents from Tasker, MacroDroid, or ADB shell to control Lightspeed programmatically:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager

                        listOf(
                            Triple("Toggle Master Flight Mode", "com.sbf.lightspeed.action.TOGGLE_FLIGHT_MODE", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_FLIGHT_MODE"),
                            Triple("Toggle All Deflectors", "com.sbf.lightspeed.action.TOGGLE_DEFLECTORS", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_DEFLECTORS"),
                            Triple("Toggle Left Deflector", "com.sbf.lightspeed.action.TOGGLE_LEFT_DEFLECTOR", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_LEFT_DEFLECTOR"),
                            Triple("Toggle Right Deflector", "com.sbf.lightspeed.action.TOGGLE_RIGHT_DEFLECTOR", "am broadcast -a com.sbf.lightspeed.action.TOGGLE_RIGHT_DEFLECTOR")
                        ).forEach { (label, actionStr, adbCmd) ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    IconButton(
                                        onClick = {
                                            clipboardManager?.setPrimaryClip(android.content.ClipData.newPlainText("Intent Action", actionStr))
                                            Toast.makeText(context, "Action copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                    }
                                }
                                Text(
                                    actionStr,
                                    fontSize = 10.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
