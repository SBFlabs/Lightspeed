package com.sbf.lightspeed.settings

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun FlightControlDeckCard(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    var isArmed by remember { mutableStateOf(LightspeedPreferences.isMasterFlightArmed(context)) }
    var isNotifEnabled by remember { mutableStateOf(LightspeedPreferences.isFlightNotificationEnabled(context)) }

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

            // Live Glass Material Prototype Selector
            val styleFlow by LightspeedPreferences.deckGlassStyleFlow.collectAsState()
            val currentGlassStyle = styleFlow ?: remember { LightspeedPreferences.getDeckGlassStyle(context) }

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
                            else -> "\"Refractive Glass\""
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
                        "liquid" to "\"Glass\"",
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

            // External Automation Opt-In (Tasker, MacroDroid, ADB)
            var isAutomationExpanded by rememberSaveable { mutableStateOf(false) }
            CollapsibleSubSection(
                title = "External Automation",
                subtitle = "Tasker, MacroDroid, ADB triggers",
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isAutomationExpanded,
                onToggle = { isAutomationExpanded = !isAutomationExpanded }
            ) {
                var isAutomationAllowed by remember(prefs) {
                    mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION, false))
                }
                var showAutomationWarningDialog by remember { mutableStateOf(false) }

                DisposableEffect(prefs) {
                    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key == LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION) {
                            isAutomationAllowed = prefs.getBoolean(LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION, false)
                        }
                    }
                    prefs.registerOnSharedPreferenceChangeListener(listener)
                    onDispose {
                        prefs.unregisterOnSharedPreferenceChangeListener(listener)
                    }
                }

                if (showAutomationWarningDialog) {
                    AlertDialog(
                        onDismissRequest = { showAutomationWarningDialog = false },
                        title = {
                            Text(
                                "Security warning",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                "External automation lets any app on this device trigger Lightspeed actions. You probably don't need it, and it is a security risk. It is here to give you the ultimate choice. Enable it anyway?"
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION, true).apply()
                                    try {
                                        com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
                                    } catch (e: Exception) { logSwallowed("FlightControlDeckComponents", "FlightControlDeckCard:507", e) }
                                    showAutomationWarningDialog = false
                                }
                            ) {
                                Text("Enable anyway")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    showAutomationWarningDialog = false
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                PrefToggleRow(
                    title = "Allow external automation (Tasker, MacroDroid, ADB)",
                    subtitle = "Lets any app on this device trigger flight mode, deflectors, auto-rotate and gravity override. Off by default.",
                    isChecked = isAutomationAllowed,
                    onCheckedChange = { newValue ->
                        if (newValue) {
                            showAutomationWarningDialog = true
                        } else {
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_ALLOW_EXTERNAL_AUTOMATION, false).apply()
                            try {
                                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
                            } catch (e: Exception) { logSwallowed("FlightControlDeckComponents", "FlightControlDeckCard:537", e) }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                val pkg = context.packageName
                val actions = listOf(
                    "TOGGLE_DEFLECTORS", "ENABLE_DEFLECTORS", "DISABLE_DEFLECTORS",
                    "TOGGLE_FLIGHT_MODE", "ENABLE_FLIGHT_MODE", "DISABLE_FLIGHT_MODE",
                    "TOGGLE_LEFT_DEFLECTOR", "TOGGLE_RIGHT_DEFLECTOR",
                    "TOGGLE_AUTO_ROTATE", "TOGGLE_SYNTHETIC_GRAVITY", "TRIGGER_WATCHDOG_REVIVAL"
                )
                var expandedDropdown by remember { mutableStateOf(false) }
                var selectedAction by remember { mutableStateOf(actions[0]) }
                var selectedTab by remember { mutableIntStateOf(0) }

                val fullAction = "com.sbf.lightspeed.action.$selectedAction"
                val commandText = when (selectedTab) {
                    0 -> "adb shell am broadcast -a $fullAction -p$pkg"
                    1 -> "am broadcast -a $fullAction -p$pkg"
                    else -> "Action: $fullAction\nPackage:$pkg\nTarget: Broadcast Receiver"
                }

                // Action Dropdown
                OutlinedButton(onClick = { expandedDropdown = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Action: $selectedAction", fontSize = 12.sp)
                }
                DropdownMenu(expanded = expandedDropdown, onDismissRequest = { expandedDropdown = false }) {
                    actions.forEach { action ->
                        DropdownMenuItem(text = { Text(action, fontSize = 12.sp) }, onClick = { 
                            selectedAction = action
                            expandedDropdown = false 
                        })
                    }
                }

                // Tabs
                val tabs = listOf("ADB (PC)", "Shizuku/Shell", "Tasker")
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, fontSize = 11.sp) }
                        )
                    }
                }

                // Command Box & Copy
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        Text(text = commandText, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Command", commandText))
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (selectedTab == 1) {
                    val shizukuInstructionText = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        "Shizuku can be started with Wireless debugging (needs Wi-Fi), no PC needed after pairing, and again after every reboot."
                    } else {
                        "Wireless debugging is not available. Shizuku must be started from a PC with ADB, and again after every reboot."
                    }
                    Text(
                        text = shizukuInstructionText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}
