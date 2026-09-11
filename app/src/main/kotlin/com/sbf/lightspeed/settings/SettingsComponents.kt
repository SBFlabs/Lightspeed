package com.sbf.lightspeed.settings

import androidx.compose.ui.text.style.TextAlign
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import com.sbf.lightspeed.system.LightspeedHapticEngine



@Composable
fun PowerGestureMappingRow(
    context: Context,
    prefs: SharedPreferences,
    prefKey: String,
    title: String,
    slot: com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot,
    isSinglePress: Boolean,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockStep: () -> Unit,
    installedTools: List<com.sbf.lightspeed.system.TacticalToolItem>,
    options: List<String>,
    labelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    var currentValue by remember {
        mutableStateOf(
            if (isSinglePress && !isSinglePressUnlocked) "none"
            else prefs.getString(prefKey, if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD) "system:tactical_flyout" else "none") ?: "none"
        )
    }

    LaunchedEffect(isSinglePressUnlocked) {
        currentValue = if (isSinglePress && !isSinglePressUnlocked) "none"
        else prefs.getString(prefKey, if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD) "system:tactical_flyout" else "none") ?: "none"
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentValue = prefs.getString(prefKey, "none") ?: "none"
            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
            onRefreshNeeded()
        }
    }

    var isToolsDropdownOpen by remember { mutableStateOf(false) }

    val isLocked = isSinglePress && !isSinglePressUnlocked

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (isLocked) 0.6f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                if (isLocked) {
                    onSinglePressUnlockStep()
                } else {
                    val intent = Intent(context, CockpitGearPickerActivity::class.java).apply {
                        putExtra("SINGLE_SELECT_PREF_KEY", prefKey)
                        putExtra("SINGLE_SELECT_TITLE", "$title Action")
                    }
                    launcher.launch(intent)
                }
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isLocked) 0.15f else 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isLocked) Color.Gray.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    // Custom Leading Badge
                    Surface(
                        modifier = Modifier.wrapContentWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = (if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary).copy(alpha = 0.16f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            (if (isLocked) Color.Gray else MaterialTheme.colorScheme.primary).copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = if (isLocked) Color.LightGray else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            when (slot) {
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS -> {
                                    Text(
                                        text = "1×",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isLocked) Color.LightGray else MaterialTheme.colorScheme.primary
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS -> {
                                    Text(
                                        text = "2×",
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD -> {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Hold",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_PRESS_THEN_HOLD -> {
                                    Text(
                                        text = "➔",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = "Hold",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isLocked) Color.LightGray else Color.White
                        )
                        if (isLocked) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Mapped to Default: System Sleep / Wake",
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    overflow = TextOverflow.Visible
                                )
                            }
                            Text(
                                text = "Native OS Interlock (Tap 7× to unlock override)",
                                fontSize = 10.5.sp,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        } else {
                            val displayLabel = labelCache[currentValue] ?: resolveDynamicTokenLabel(context, currentValue)
                            Text(
                                text = displayLabel,
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                maxLines = 1
                            )
                        }
                    }
                }

                if (!isLocked) {
                    // Inline Installed Tools Dropdown Button
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isToolsDropdownOpen = true },
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Tools",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = isToolsDropdownOpen,
                            onDismissRequest = { isToolsDropdownOpen = false },
                            modifier = Modifier
                                .background(Color(0xF012141A))
                                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = Color(0xF012141A)
                        ) {
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 48.dp),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.PowerSettingsNew,
                                        contentDescription = null,
                                        tint = Color.LightGray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                text = { Text("None (Native / Sleep)", fontWeight = FontWeight.Normal, fontSize = 12.5.sp, color = Color.LightGray) },
                                onClick = {
                                    isToolsDropdownOpen = false
                                    currentValue = "none"
                                    prefs.edit().putString(prefKey, "none").apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color(0x33FFFFFF))

                            var currentCategory = ""
                            installedTools.forEach { tool ->
                                if (tool.category != currentCategory) {
                                    currentCategory = tool.category
                                    DropdownMenuItem(
                                        modifier = Modifier.heightIn(min = 36.dp),
                                        text = {
                                            Text(
                                                text = currentCategory.uppercase(java.util.Locale.US),
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        onClick = {},
                                        enabled = false
                                    )
                                }
                                val isSelected = (currentValue == tool.token)
                                DropdownMenuItem(
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    leadingIcon = {
                                        val icon = when (tool.token) {
                                            "system:torch", "system:flashlight" -> Icons.Default.FlashlightOn
                                            "system:tactical_flyout" -> Icons.Default.Dashboard
                                            LightspeedPreferences.ACTION_CHATGPT -> Icons.Default.AutoAwesome
                                            LightspeedPreferences.ACTION_CLAUDE -> Icons.Default.Psychology
                                            LightspeedPreferences.ACTION_GEMINI -> Icons.Default.Stars
                                            LightspeedPreferences.ACTION_FOLAX -> Icons.Default.Assistant
                                            LightspeedPreferences.ACTION_LENS -> Icons.Default.CenterFocusStrong
                                            LightspeedPreferences.ACTION_QR_SCANNER -> Icons.Default.QrCodeScanner
                                            LightspeedPreferences.ACTION_CAMERA_PHOTO -> Icons.Default.CameraAlt
                                            LightspeedPreferences.ACTION_CAMERA_VIDEO -> Icons.Default.Videocam
                                            else -> Icons.Default.Widgets
                                        }
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    text = {
                                        Text(
                                            text = tool.label,
                                            fontSize = 12.5.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White
                                        )
                                    },
                                    onClick = {
                                        isToolsDropdownOpen = false
                                        currentValue = tool.token
                                        prefs.edit().putString(prefKey, tool.token).apply()
                                        LightspeedHapticEngine.tick(context)
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        onRefreshNeeded()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (slot == com.sbf.lightspeed.system.LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS && currentValue != "none") {
                val isDoublePressDefault = remember(currentValue) {
                    try {
                        val pm = context.packageManager
                        val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE)
                        val resolve = pm.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                        resolve?.activityInfo?.packageName == context.packageName
                    } catch (_: Exception) { false }
                }

                if (!isDoublePressDefault) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tap to authorize double-press hardware trigger (Select 'Always')",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
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
fun LanguageEngineCard(
    context: android.content.Context
) {
    val currentMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    androidx.compose.material.icons.Icons.Default.Language,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "COMMUNICATION PROTOCOL",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
            }

            Text(
                "Select the operational terminology used throughout the vessel:",
                fontSize = 11.5.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                lineHeight = 16.sp
            )

            val modes = listOf(
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE to "Vessel Lore",
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT to "Co-Pilot",
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS to "Clear Comms"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                modes.forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) colorScheme.primary else Color.Transparent)
                            .clickable {
                                com.sbf.lightspeed.system.LightspeedLanguageEngine.setMode(context, mode)
                                com.sbf.lightspeed.system.LightspeedHapticEngine.tick(context)
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            val modeDesc = when (currentMode) {
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.VESSEL_LORE ->
                    "Full spaceship immersion. Uses terms like 'Deflectors' and 'HUD Strip'."
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CO_PILOT ->
                    "Bilingual bridge. Displays Vessel Lore with plain Android subtitles for easy onboarding."
                com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS ->
                    "Plain Android terminology. Uses terms like 'Gesture Sidebars' and 'Status Bar'."
            }

            Text(
                text = modeDesc,
                fontSize = 11.sp,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                lineHeight = 14.sp
            )
        }
    }
}
