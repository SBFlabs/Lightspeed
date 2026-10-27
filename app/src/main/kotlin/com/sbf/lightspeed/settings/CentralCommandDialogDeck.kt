package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences

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

    val notificationAccessPrompt by viewModel.notificationAccessPrompt.collectAsState()
    val importStatusMessage by viewModel.importStatusMessage.collectAsState()
    LaunchedEffect(notificationAccessPrompt, importStatusMessage) {
        if (notificationAccessPrompt && importStatusMessage == null) {
            showNotificationAccessDialogState.value = true
            viewModel.consumeNotificationAccessPrompt()
        }
    }

    DisposableEffect(Unit) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, changedKey ->
            if (changedKey == null) return@OnSharedPreferenceChangeListener
            try {
                val value = sp.getString(changedKey, null)
                if (value != null && value.startsWith("system:media_")) {
                    val granted = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")?.contains(context.packageName) == true || LightspeedNotificationListener.instance != null
                    if (!granted) {
                        showNotificationAccessDialogState.value = true
                    }
                }
            } catch (_: Exception) {
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

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
