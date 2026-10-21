package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.sbf.lightspeed.settings.AppAssignmentSheet
import com.sbf.lightspeed.settings.AttitudeAppAssignmentSheet
import com.sbf.lightspeed.system.ActionMethods
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Sub-dialogs host for Pinning, Deep Activities Warning, System Accordions,
 * and Attitude / Action Method per-app assignment sheets.
 */
@Composable
internal fun CockpitGearPickerSubDialogs(
    context: Context,
    prefs: SharedPreferences,
    dynamicPrimary: Color,
    allSysCategories: List<String>,
    pinnedApps: Set<String>,
    onPinnedAppsChanged: (Set<String>) -> Unit,
    pendingPinApp: Pair<String, String>?,
    onDismissPinApp: () -> Unit,
    showDeepActivityWarning: Boolean,
    pendingDeepActivitySubKey: String?,
    onDeepActivityWarningDismiss: () -> Unit,
    onDeepActivityWarningProceed: (Boolean) -> Unit,
    showSystemAccordionPrefsDialog: Boolean,
    onDismissSystemAccordionPrefs: () -> Unit,
    systemAccordionMode: String,
    onSystemAccordionModeChanged: (String) -> Unit,
    selectedAttitudeBucketForAppPicker: LightspeedOrientationEngine.AttitudeBucket?,
    onDismissAttitudeBucketPicker: () -> Unit,
    selectedActionMethodForAppPicker: Pair<String, String>?,
    onDismissActionMethodPicker: () -> Unit,
    labelCache: Map<String, String>,
    onPreferenceMutated: () -> Unit
) {
    // 1. Pin App Confirmation Dialog
    if (pendingPinApp != null) {
        val (pinPkg, pinName) = pendingPinApp
        PinAppDialog(
            packageName = pinPkg,
            appName = pinName,
            isCurrentlyPinned = pinnedApps.contains(pinPkg),
            dynamicPrimary = dynamicPrimary,
            onConfirm = { nowPinned ->
                val newSet = if (nowPinned) pinnedApps + pinPkg else pinnedApps - pinPkg
                onPinnedAppsChanged(newSet)
                prefs.edit().putStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, newSet).apply()
                onDismissPinApp()
            },
            onDismiss = onDismissPinApp
        )
    }

    // 2. Deep Activity Warning Dialog
    if (showDeepActivityWarning && pendingDeepActivitySubKey != null) {
        DeepActivityWarningDialog(
            dynamicPrimary = dynamicPrimary,
            onProceed = onDeepActivityWarningProceed,
            onDismiss = onDeepActivityWarningDismiss
        )
    }

    // 3. System Accordion Preferences Dialog
    if (showSystemAccordionPrefsDialog) {
        SystemAccordionPrefsDialog(
            dynamicPrimary = dynamicPrimary,
            currentPreference = systemAccordionMode,
            onSelectPreference = onSystemAccordionModeChanged,
            onDismiss = onDismissSystemAccordionPrefs
        )
    }

    // 4. Attitude Bucket App Assignment Sheet
    if (selectedAttitudeBucketForAppPicker != null) {
        AttitudeAppAssignmentSheet(
            context = context,
            bucket = selectedAttitudeBucketForAppPicker,
            onDismiss = onDismissAttitudeBucketPicker,
            onUpdated = onPreferenceMutated
        )
    }

    // 5. Action Method Per-App Assignment Sheet
    if (selectedActionMethodForAppPicker != null) {
        val (actionToken, methodId) = selectedActionMethodForAppPicker
        val optTitle = ActionMethods.options(actionToken).firstOrNull { it.id == methodId }?.title ?: methodId
        val actionTitle = when (actionToken) {
            "system:scroll_to_bottom" -> "Scroll to Bottom"
            "system:scroll_to_top" -> "Scroll to Top"
            "system:split_screen" -> "Split Screen"
            "system:popup_window", "system:freeform" -> "Pop-up Window"
            "system:close_app" -> "Close App"
            else -> labelCache[actionToken] ?: actionToken
        }
        AppAssignmentSheet(
            context = context,
            title = "$actionTitle: $optTitle",
            subtitle = "Run $optTitle exclusively for selected apps",
            icon = Icons.Default.SwapVert,
            initialAssigned = remember(actionToken, methodId) {
                ActionMethods.getMethodApps(context, actionToken, methodId)
            },
            onSave = { pkgs ->
                ActionMethods.setMethodApps(context, actionToken, methodId, pkgs)
            },
            onDismiss = onDismissActionMethodPicker,
            onUpdated = onPreferenceMutated
        )
    }
}
