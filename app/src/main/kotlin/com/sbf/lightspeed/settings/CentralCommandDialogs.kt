package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Clones macro actions and HUD styles between wing flank zones.
 */
internal fun cloneFlankActions(prefs: SharedPreferences, sourcePrefix: String, targetPrefix: String) {
    val editor = prefs.edit()
    val allEntries = prefs.all
    for ((key, value) in allEntries) {
        if (key.startsWith("pref_macro_action_${sourcePrefix}_")) {
            val suffix = key.removePrefix("pref_macro_action_${sourcePrefix}_")
            val targetKey = "pref_macro_action_${targetPrefix}_$suffix"
            if (value is String) {
                editor.putString(targetKey, value)
            }
        }
        if (key.startsWith("pref_macro_hud_style_${sourcePrefix}_")) {
            val suffix = key.removePrefix("pref_macro_hud_style_${sourcePrefix}_")
            val targetKey = "pref_macro_hud_style_${targetPrefix}_$suffix"
            if (value is String) {
                editor.putString(targetKey, value)
            }
        }
    }
    editor.apply()
}

@Composable
fun ResetConfirmDialog(
    showState: MutableState<Boolean>,
    context: Context,
    viewModel: CentralCommandViewModel,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = { Text("Reset to Factory Defaults?", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Text(
                "This will wipe all customized gestures, sensitivity sliders, and custom gear sets. This action cannot be undone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.resetToDefaults(context)
                    showState.value = false
                    Toast.makeText(context, "Preferences reset to factory defaults", Toast.LENGTH_SHORT).show()
                    onRefreshNeeded()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset Everything", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun ImportStatusDialog(
    viewModel: CentralCommandViewModel,
    context: Context,
    onRefreshNeeded: () -> Unit
) {
    val importStatusMessage by viewModel.importStatusMessage.collectAsState()
    val isImportSuccess by viewModel.isImportSuccess.collectAsState()

    if (importStatusMessage == null) return

    AlertDialog(
        onDismissRequest = { viewModel.clearImportStatus() },
        title = {
            Text(
                text = if (isImportSuccess) "Backup Restored" else "Import Status",
                fontWeight = FontWeight.Bold,
                color = if (isImportSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = importStatusMessage ?: "",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val wasSuccess = isImportSuccess
                    viewModel.clearImportStatus()
                    if (wasSuccess) {
                        (context as? Activity)?.recreate() ?: onRefreshNeeded()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isImportSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(if (isImportSuccess) "Done" else "Dismiss", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun ImportOptionsDialog(
    showState: MutableState<Boolean>,
    onSelectFilePicker: () -> Unit,
    onSelectPasteJson: () -> Unit
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Restore Configuration", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "SELECT RESTORE METHOD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Choose via system picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .clickable {
                            showState.value = false
                            onSelectFilePicker()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Choose Backup File", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                        Text("Select your backup .json file from storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Paste JSON Directly
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                        .clickable {
                            showState.value = false
                            onSelectPasteJson()
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Paste JSON Text Directly", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                        Text("Paste backup payload from clipboard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun PasteJsonDialog(
    showState: MutableState<Boolean>,
    pastedJsonTextState: MutableState<String>,
    context: Context,
    viewModel: CentralCommandViewModel
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = { Text("Paste Backup JSON", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Paste your exported JSON payload below:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedJsonTextState.value,
                    onValueChange = { pastedJsonTextState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    placeholder = { Text("{\n  \"settings\": {\n    ...\n  }\n}", fontSize = 12.sp) },
                    maxLines = 15,
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val text = pastedJsonTextState.value.trim()
                    showState.value = false
                    if (text.isNotBlank()) {
                        viewModel.importFromJson(context, text)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Restore", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun SymmetryInfoDialog(showState: MutableState<Boolean>) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        icon = { Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Deflector Symmetry & Coupling", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The 3-position selector acts as a non-destructive Flight Profile Switch:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("• CLONE LEFT: Left Wing is master. Right Wing automatically mirrors its geometry or inverts and executes its gestures.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• INDEPENDENT: Bilateral multi-role setup. Both wings have dedicated, independent gesture maps and dimensions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• CLONE RIGHT: Right Wing is master. Left Wing automatically mirrors its geometry or opens the Cockpit / executes its gestures.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("🔒 Switching profiles never deletes your custom setups. Switching back to INDEPENDENT restores all unique mappings instantly.", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = { showState.value = false }) {
                Text("Got it", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun UnifyInfoDialog(showState: MutableState<Boolean>) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Unified Flank Actions & Dual Scrubbers", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This unifies your gesture configuration while preserving ergonomics:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("• Single Gesture Set: Configure standard directional gestures (Tap, Swipe In, Swipe Up/Down, Hold) once for the whole flank.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Dual Scrubbers: Independent inward scrubbing selectors for the upper half (e.g. Brightness) and lower half (e.g. Volume).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("• Independent Geometry: Top and bottom wing spans, reaches, and glows remain independently tunable for natural grip comfort.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("🔒 Toggling OFF immediately restores your previous separate upper and lower gesture mappings without data loss.", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            Button(onClick = { showState.value = false }) {
                Text("Got it", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun UnifyTemplateDialog(
    showState: MutableState<Boolean>,
    isLeft: Boolean,
    onConfirm: (selectedOption: Int) -> Unit
) {
    if (!showState.value) return
    var selectedOption by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Text(
                if (isLeft) "Activate Unified Left Flank" else "Activate Unified Right Flank",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose an action template to initialize your unified flank set:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                listOf(
                    0 to "Clone Upper Vector Actions (Top Half)",
                    1 to "Clone Lower Vector Actions (Bottom Half)",
                    2 to "Use Dedicated Unified Set"
                ).forEach { (optIdx, optLabel) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedOption == optIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { selectedOption = optIdx }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedOption == optIdx,
                            onClick = { selectedOption = optIdx },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(optLabel, fontSize = 12.5.sp, color = Color.White)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    showState.value = false
                    onConfirm(selectedOption)
                }
            ) {
                Text("Unify Actions", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("Cancel", color = Color.White)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun OemShieldDialog(
    showState: MutableState<Boolean>,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    var preserveScreenshot by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, true)) }
    var preserveAccessibility by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)) }

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("OEM Compatibility Shield", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Configure hardware-level passthrough guardrails to ensure native Android system shortcuts remain responsive on your specific device hardware.",
                    fontSize = 13.sp,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
                PrefToggleRow(
                    title = "Preserve Screenshot Shortcut",
                    subtitle = "Whitelist immediate pass-through for Power + Vol Down screenshot captures",
                    isChecked = preserveScreenshot,
                    onCheckedChange = { preserveScreenshot = it }
                )
                PrefToggleRow(
                    title = "Preserve Accessibility Shortcut",
                    subtitle = "Bypass custom chords after 1.5s simultaneous hold and yield directly to Android TalkBack / accessibility shortcut",
                    isChecked = preserveAccessibility,
                    onCheckedChange = { preserveAccessibility = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit()
                        .putBoolean(LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, true)
                        .putBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, preserveScreenshot)
                        .putBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, preserveAccessibility)
                        .apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("APPLY SHIELD", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("DISMISS", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun BatteryWarningDialog(
    showState: MutableState<Boolean>,
    pendingBackTapScope: String,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Text("⚠️ High Battery Usage Warning", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
        },
        text = {
            Text(
                "Detecting back taps while the screen is off keeps your CPU awake (Partial Wake Lock), preventing Android from entering deep sleep. This typically consumes 2% to 4% battery per hour while idle.",
                fontSize = 13.sp,
                color = Color.LightGray.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, pendingBackTapScope).apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("ENABLE ANYWAY", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on").apply()
                    showState.value = false
                    onRefreshNeeded()
                }
            ) {
                Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun NotificationAccessDialog(
    showState: MutableState<Boolean>,
    context: Context
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Text(
                "To display real-time download progress and active media playback in your status bar or notch pill, Android requires Notification Access (Device & App Notifications).\n\nLightspeed is 100% offline and never records, stores, or transmits your personal notifications.",
                fontSize = 13.sp,
                color = Color.LightGray.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    showState.value = false
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("OPEN SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { showState.value = false }) {
                Text("NOT NOW", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

@Composable
fun AmoledWarningDialog(
    showState: MutableState<Boolean>,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    AlertDialog(
        onDismissRequest = {
            showState.value = false
            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
            onRefreshNeeded()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Hardware Notice: AMOLED Image Retention", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Text(
                "Prolonged static display on OLED panels can cause permanent subpixel degradation (burn-in). Keep auto-sleep enabled?",
                fontSize = 13.sp,
                color = Color.LightGray.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Keep Auto-Sleep (Recommended)", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "never").apply()
                    showState.value = false
                    onRefreshNeeded()
                }
            ) {
                Text("Enable Always-On (My Device Has No OLED / I Understand)", color = MaterialTheme.colorScheme.error)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

/**
 * Host container for all Central Command modal dialogs.
 */
@Composable
fun CentralCommandDialogDeck(
    context: Context,
    prefs: SharedPreferences,
    viewModel: CentralCommandViewModel,
    showResetConfirmDialogState: MutableState<Boolean>,
    showImportOptionsDialogState: MutableState<Boolean>,
    showPasteJsonDialogState: MutableState<Boolean>,
    pastedJsonTextState: MutableState<String>,
    showSymmetryInfoDialogState: MutableState<Boolean>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForLeftState: MutableState<Boolean>,
    showUnifyTemplateDialogForRightState: MutableState<Boolean>,
    showOemShieldDialogState: MutableState<Boolean>,
    showBatteryWarningDialogState: MutableState<Boolean>,
    showNotificationAccessDialogState: MutableState<Boolean>,
    showAmoledWarningDialogState: MutableState<Boolean>,
    pendingBackTapScope: String,
    importLauncher: ActivityResultLauncher<String>,
    onSetLeftFlankUnified: (Boolean) -> Unit,
    onSetRightFlankUnified: (Boolean) -> Unit,
    onRefreshNeeded: () -> Unit
) {
    ResetConfirmDialog(
        showState = showResetConfirmDialogState,
        context = context,
        viewModel = viewModel,
        onRefreshNeeded = onRefreshNeeded
    )

    ImportStatusDialog(
        viewModel = viewModel,
        context = context,
        onRefreshNeeded = onRefreshNeeded
    )

    ImportOptionsDialog(
        showState = showImportOptionsDialogState,
        onSelectFilePicker = {
            importLauncher.launch("*/*")
        },
        onSelectPasteJson = {
            pastedJsonTextState.value = ""
            showPasteJsonDialogState.value = true
        }
    )

    PasteJsonDialog(
        showState = showPasteJsonDialogState,
        pastedJsonTextState = pastedJsonTextState,
        context = context,
        viewModel = viewModel
    )

    SymmetryInfoDialog(showState = showSymmetryInfoDialogState)

    UnifyInfoDialog(showState = showUnifyInfoDialogState)

    UnifyTemplateDialog(
        showState = showUnifyTemplateDialogForLeftState,
        isLeft = true,
        onConfirm = { option ->
            if (option == 0) {
                cloneFlankActions(prefs, "LEFT_TOP", "LEFT_UNIFIED")
            } else if (option == 1) {
                cloneFlankActions(prefs, "LEFT_BOTTOM", "LEFT_UNIFIED")
            }
            onSetLeftFlankUnified(true)
            prefs.edit().putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK, true).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        }
    )

    UnifyTemplateDialog(
        showState = showUnifyTemplateDialogForRightState,
        isLeft = false,
        onConfirm = { option ->
            if (option == 0) {
                cloneFlankActions(prefs, "TOP", "UNIFIED")
            } else if (option == 1) {
                cloneFlankActions(prefs, "BOTTOM", "UNIFIED")
            }
            onSetRightFlankUnified(true)
            prefs.edit().putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, true).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        }
    )

    OemShieldDialog(
        showState = showOemShieldDialogState,
        prefs = prefs,
        onRefreshNeeded = onRefreshNeeded
    )

    BatteryWarningDialog(
        showState = showBatteryWarningDialogState,
        pendingBackTapScope = pendingBackTapScope,
        prefs = prefs,
        onRefreshNeeded = onRefreshNeeded
    )

    NotificationAccessDialog(
        showState = showNotificationAccessDialogState,
        context = context
    )

    AmoledWarningDialog(
        showState = showAmoledWarningDialogState,
        prefs = prefs,
        onRefreshNeeded = onRefreshNeeded
    )
}
