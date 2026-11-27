package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.ui.theme.LocalDeckTheme

import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.system.logSwallowed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.ui.theme.LightspeedStyle

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

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = { Text(lsString(R.string.cc_dlg_reset_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This will wipe all customized gestures, sensitivity sliders, and custom gear sets. This action cannot be undone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_reset_btn),
                onClick = {
                    viewModel.resetToDefaults(context)
                    showState.value = false
                    Toast.makeText(context, lsString(R.string.cc_dlg_reset_toast), Toast.LENGTH_SHORT).show()
                    onRefreshNeeded()
                },
                isDestructive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_cancel),
                onClick = { showState.value = false }
            )
        }
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

    DeckDialog(
        onDismissRequest = { viewModel.clearImportStatus() },
        title = {
            Text(
                text = if (isImportSuccess) lsString(R.string.cc_dlg_backup_restored) else lsString(R.string.cc_dlg_import_status),
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
            DeckButton(
                text = if (isImportSuccess) lsString(R.string.cc_dlg_done) else lsString(R.string.cc_dlg_dismiss),
                onClick = {
                    val wasSuccess = isImportSuccess
                    viewModel.clearImportStatus()
                    if (wasSuccess) {
                        (context as? Activity)?.recreate() ?: onRefreshNeeded()
                    }
                },
                isActive = isImportSuccess
            )
        }
    )
}

@Composable
fun ImportOptionsDialog(
    showState: MutableState<Boolean>,
    onSelectFilePicker: () -> Unit,
    onSelectPasteJson: () -> Unit
) {
    if (!showState.value) return

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(lsString(R.string.cc_dlg_restore_title), fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    lsString(R.string.cc_dlg_select_restore),
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
                        Text(lsString(R.string.cc_dlg_choose_file), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightspeedStyle.colors.textPrimary)
                        Text(lsString(R.string.cc_dlg_choose_file_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text(lsString(R.string.cc_dlg_paste_json), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = LightspeedStyle.colors.textPrimary)
                        Text(lsString(R.string.cc_dlg_paste_json_sub), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.VAULT_IMPORT_WARNING),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.error,
                        lineHeight = 13.5.sp
                    )
                }
            }
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_cancel),
                onClick = { showState.value = false }
            )
        }
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

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = { Text(lsString(R.string.cc_dlg_paste_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(lsString(R.string.cc_dlg_paste_hint), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = pastedJsonTextState.value,
                    onValueChange = { pastedJsonTextState.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    placeholder = { Text("{\n  \"settings\": {\n    ...\n  }\n}", fontSize = 12.sp) },
                    maxLines = 15,
                    textStyle = TextStyle(fontSize = 12.sp, color = LightspeedStyle.colors.textPrimary)
                )
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_restore_btn),
                onClick = {
                    val text = pastedJsonTextState.value.trim()
                    showState.value = false
                    if (text.isNotBlank()) {
                        viewModel.importFromJson(context, text)
                    }
                },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_cancel),
                onClick = { showState.value = false }
            )
        }
    )
}

@Composable
fun SymmetryInfoDialog(showState: MutableState<Boolean>) {
    if (!showState.value) return

    DeckDialog(
        onDismissRequest = { showState.value = false },
        icon = { Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(lsString(R.string.cc_dlg_sym_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lsString(R.string.cc_dlg_sym_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(lsString(R.string.cc_dlg_sym_left), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lsString(R.string.cc_dlg_sym_indep), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lsString(R.string.cc_dlg_sym_right), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(lsString(R.string.cc_dlg_sym_safe), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_got_it),
                onClick = { showState.value = false },
                isActive = true
            )
        }
    )
}

@Composable
fun UnifyInfoDialog(showState: MutableState<Boolean>) {
    if (!showState.value) return

    DeckDialog(
        onDismissRequest = { showState.value = false },
        icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(lsString(R.string.cc_dlg_unify_title), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(lsString(R.string.cc_dlg_unify_intro), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                Text(lsString(R.string.cc_dlg_unify_b1), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lsString(R.string.cc_dlg_unify_b2), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(lsString(R.string.cc_dlg_unify_b3), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(lsString(R.string.cc_dlg_unify_safe), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_got_it),
                onClick = { showState.value = false },
                isActive = true
            )
        }
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

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Text(
                if (isLeft) "Activate Unified Left Flank" else "Activate Unified Right Flank",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(lsString(R.string.cc_dlg_unify_choose), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                listOf(
                    0 to lsString(R.string.cc_dlg_unify_clone_up),
                    1 to lsString(R.string.cc_dlg_unify_clone_lo),
                    2 to lsString(R.string.cc_dlg_unify_ded)
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
                        Text(optLabel, fontSize = 12.5.sp, color = LightspeedStyle.colors.textPrimary)
                    }
                }
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_unify_btn),
                onClick = {
                    showState.value = false
                    onConfirm(selectedOption)
                },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_cancel),
                onClick = { showState.value = false }
            )
        }
    )
}

@Composable
fun OemShieldDialog(
    showState: MutableState<Boolean>,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    val lsColors = LightspeedStyle.colors
    var preserveScreenshot by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, true)) }
    var preserveAccessibility by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)) }

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(lsString(R.string.cc_dlg_oem_shield), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    lsString(R.string.cc_dlg_oem_shield_desc),
                    fontSize = 13.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.85f),
                    lineHeight = 17.sp
                )
                PrefToggleRow(
                    title = lsString(R.string.cc_dlg_pres_shot),
                    subtitle = lsString(R.string.cc_dlg_pres_shot_sub),
                    isChecked = preserveScreenshot,
                    onCheckedChange = { preserveScreenshot = it }
                )
                PrefToggleRow(
                    title = lsString(R.string.cc_dlg_pres_a11y),
                    subtitle = lsString(R.string.cc_dlg_pres_a11y_sub),
                    isChecked = preserveAccessibility,
                    onCheckedChange = { preserveAccessibility = it }
                )
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_apply_shield),
                onClick = {
                    prefs.edit()
                        .putBoolean(LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, true)
                        .putBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, preserveScreenshot)
                        .putBoolean(LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, preserveAccessibility)
                        .apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_dismiss_caps),
                onClick = { showState.value = false }
            )
        }
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

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Text(lsString(R.string.cc_dlg_high_bat_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Text(
                "Detecting back taps while the screen is off keeps your CPU awake (Partial Wake Lock), preventing Android from entering deep sleep. This typically consumes 2% to 4% battery per hour while idle.",
                fontSize = 13.sp,
                color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_enable_anyway_caps),
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, pendingBackTapScope).apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                isDestructive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = "CANCEL",
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on").apply()
                    showState.value = false
                    onRefreshNeeded()
                }
            )
        }
    )
}

@Composable
fun NotificationAccessDialog(
    showState: MutableState<Boolean>,
    context: Context
) {
    if (!showState.value) return

    DeckDialog(
        onDismissRequest = { showState.value = false },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(lsString(R.string.cc_dlg_notif_req), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Text(
                "Lightspeed needs Notification Access for two things:\n\n• Media control: gestures that play, pause or skip tracks can only reach your music or video app through Notification Access.\n• Live status: showing real-time download progress and active media playback in your status bar or notch pill.\n\nLightspeed is 100% offline and never records, stores, or transmits your personal notifications.",
                fontSize = 13.sp,
                color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_open_settings),
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
                        } catch (e: Exception) { logSwallowed("CentralCommandDialogs", "NotificationAccessDialog:548", e) }
                    }
                },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_not_now),
                onClick = { showState.value = false }
            )
        }
    )
}

@Composable
fun AmoledWarningDialog(
    showState: MutableState<Boolean>,
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    if (!showState.value) return

    DeckDialog(
        onDismissRequest = {
            showState.value = false
            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
            onRefreshNeeded()
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(lsString(R.string.cc_dlg_amoled_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        },
        text = {
            Text(
                lsString(R.string.cc_dlg_amoled_msg),
                fontSize = 13.sp,
                color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.9f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_keep_autosleep),
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                isActive = true
            )
        },
        dismissButton = {
            DeckButton(
                text = lsString(R.string.cc_dlg_enable_aod),
                onClick = {
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "never").apply()
                    showState.value = false
                    onRefreshNeeded()
                },
                isDestructive = true
            )
        }
    )
}
