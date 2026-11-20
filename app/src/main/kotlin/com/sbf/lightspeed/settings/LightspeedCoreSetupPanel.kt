package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.ShizukuGate
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Opens Lightspeed's Android App Info screen so users on Android 13+ can tap
 * ⋮ (top right) -> "Allow restricted settings" if the Accessibility switch is greyed out.
 */
internal fun openLightspeedAppInfo(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
        Toast.makeText(
            context,
            lsString(R.string.core_toast_restricted),
            Toast.LENGTH_LONG
        ).show()
    } catch (_: Exception) {
        Toast.makeText(context, lsString(R.string.core_toast_app_info_err), Toast.LENGTH_SHORT).show()
    }
}

private fun isShizukuBinderAlive(): Boolean = try {
    Shizuku.pingBinder()
} catch (_: Exception) {
    false
}

private fun isShizukuPermGranted(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}

/**
 * Unified Core Setup gate shown after Welcome / What's New whenever Lightspeed's
 * Accessibility Service is not yet enabled. Guides first-time users cleanly through
 * either 1-Click Setup (via Shizuku) or Accessibility Service Only, includes Android 13+
 * Restricted Settings recovery, offline privacy disclosures, Backup Restore, and Guidebook.
 */
@Composable
fun CentralCommandCoreSetupPanel(
    context: Context,
    onCoreArmed: () -> Unit,
    onOpenGuidebook: () -> Unit,
    previewMode: Boolean = false,
    onClosePreview: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var isShizukuAlive by remember { mutableStateOf(isShizukuBinderAlive()) }
    var hasShizukuPerm by remember { mutableStateOf(isShizukuPermGranted()) }
    var pendingShizukuRevive by remember { mutableStateOf(false) }
    var isWorking by remember { mutableStateOf(false) }
    var statusNote by remember { mutableStateOf<String?>(null) }
    var showRestoreWarning by remember { mutableStateOf(false) }
    var showRestrictedHelp by remember { mutableStateOf(false) }
    var showPrivacyDetails by remember { mutableStateOf(false) }

    // Nightly preview only: -1 = live Shizuku state, 0 = not running, 1 = needs approval, 2 = authorized.
    var previewSim by remember { mutableIntStateOf(-1) }
    val uiShizukuAlive = if (previewMode && previewSim >= 0) previewSim >= 1 else isShizukuAlive
    val uiShizukuPerm = if (previewMode && previewSim >= 0) previewSim >= 2 else hasShizukuPerm

    val shizukuLaunchIntent = remember {
        context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val res = withContext(Dispatchers.IO) {
                    LightspeedBackupEngine.importFromFile(context, uri)
                }
                res.onSuccess { count ->
                    safeReloadPreferences()
                    DeckThemeManager.refresh(context)
                    statusNote = lsString(R.string.core_restore_success_note, count)
                    Toast.makeText(
                        context,
                        lsString(R.string.core_restore_success_toast, count),
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { err ->
                    val msg = err.message ?: err.javaClass.simpleName
                    statusNote = lsString(R.string.core_restore_failed, msg)
                    Toast.makeText(context, lsString(R.string.core_restore_failed, msg), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    suspend fun attemptReviveNow() {
        isWorking = true
        val revived = withContext(Dispatchers.IO) {
            LightspeedWatchdogEngine.reviveAccessibilityService(context)
        }
        isWorking = false
        if (revived && LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
            LightspeedHapticEngine.tick(context)
            onCoreArmed()
        } else {
            statusNote = lsString(R.string.core_auto_enable_blocked)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isShizukuAlive = isShizukuBinderAlive()
                hasShizukuPerm = isShizukuPermGranted()
                if (!previewMode && LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                    onCoreArmed()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        while (true) {
            isShizukuAlive = isShizukuBinderAlive()
            hasShizukuPerm = isShizukuPermGranted()
            if (!previewMode && LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                onCoreArmed()
                return@LaunchedEffect
            }
            if (!previewMode && pendingShizukuRevive && isShizukuAlive && hasShizukuPerm) {
                pendingShizukuRevive = false
                attemptReviveNow()
            }
            delay(350L)
        }
    }

    DeckGlassCardSurface(
        modifier = Modifier.fillMaxSize(),
        isFlyout = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    BilingualTitleText(
                        title = lsString(R.string.core_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = lsString(R.string.core_subtitle),
                        fontSize = 11.5.sp,
                        color = lsColors.textSecondary,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScrollWithEdgeFade(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (previewMode) {
                    CoreSetupPreviewBanner(
                        previewSim = previewSim,
                        onSelectSim = { previewSim = it },
                        onClosePreview = onClosePreview
                    )
                }

                // Card 1: Recommended 1-Click Setup with Shizuku
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.primary.copy(alpha = 0.10f),
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            BilingualTitleText(
                                title = lsString(R.string.core_shizuku_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary
                            )
                        }
                        Text(
                            text = when {
                                uiShizukuAlive && uiShizukuPerm ->
                                    lsString(R.string.core_shizuku_desc_perm)
                                uiShizukuAlive ->
                                    lsString(R.string.core_shizuku_desc_alive)
                                else ->
                                    lsString(R.string.core_shizuku_desc_off)
                            },
                            fontSize = 11.5.sp,
                            color = lsColors.textSecondary,
                            lineHeight = 16.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DeckButton(
                                text = when {
                                    isWorking -> lsString(R.string.core_btn_arming)
                                    uiShizukuAlive -> lsString(R.string.core_btn_arm_shizuku)
                                    else -> lsString(R.string.core_btn_1click_shizuku)
                                },
                                icon = Icons.Rounded.Bolt,
                                isActive = true,
                                isEnabled = !isWorking,
                                onClick = {
                                    LightspeedHapticEngine.tick(context)
                                    if (previewMode) {
                                        statusNote = when {
                                            !uiShizukuAlive ->
                                                "PREVIEW: Shizuku isn't running, so the real button would open the Shizuku app for you to start it."
                                            !uiShizukuPerm ->
                                                "PREVIEW: Shizuku is running but not authorized, so the real button would show Shizuku's approval prompt, then arm Accessibility automatically."
                                            else ->
                                                "PREVIEW: Shizuku is running and authorized, so the real button would arm Accessibility in one click and open Central Command."
                                        }
                                        return@DeckButton
                                    }
                                    ShizukuGate.chooseFull()
                                    if (!isShizukuBinderAlive()) {
                                        statusNote = if (shizukuLaunchIntent != null) {
                                            lsString(R.string.core_shizuku_not_running_open)
                                        } else {
                                            lsString(R.string.core_shizuku_not_running_missing)
                                        }
                                        shizukuLaunchIntent?.let {
                                            try {
                                                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                            } catch (_: Exception) {}
                                        }
                                    } else if (!isShizukuPermGranted()) {
                                        pendingShizukuRevive = true
                                        ShizukuGate.requestIfAllowed()
                                        statusNote = lsString(R.string.core_shizuku_waiting)
                                    } else {
                                        scope.launch { attemptReviveNow() }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            if (!uiShizukuAlive && shizukuLaunchIntent != null && !previewMode) {
                                DeckSubChip(
                                    text = lsString(R.string.core_btn_open_shizuku),
                                    onClick = {
                                        try {
                                            context.startActivity(shizukuLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                        } catch (_: Exception) {}
                                    }
                                )
                            }
                        }
                    }
                }

                // Card 2: Accessibility Service Only
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.65f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = lsColors.accentInfo,
                                modifier = Modifier.size(18.dp)
                            )
                            BilingualTitleText(
                                title = lsString(R.string.core_a11y_title),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary
                            )
                        }
                        Text(
                            text = lsString(R.string.core_a11y_desc),
                            fontSize = 11.5.sp,
                            color = lsColors.textSecondary,
                            lineHeight = 16.sp
                        )
                        DeckButton(
                            text = lsString(R.string.core_btn_open_a11y),
                            icon = Icons.Rounded.AccessibilityNew,
                            onClick = {
                                LightspeedHapticEngine.tick(context)
                                if (previewMode) {
                                    statusNote = "PREVIEW: the real button switches Lightspeed to Accessibility Service Only mode and opens Android's Accessibility settings; this screen then advances by itself once Lightspeed is toggled ON."
                                    return@DeckButton
                                }
                                ShizukuGate.chooseAccessibilityOnly()
                                LightspeedWatchdogEngine.openAccessibilitySettings(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Compact Expandable Row 1: Android 13+ Switch Greyed Out?
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.40f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showRestrictedHelp = !showRestrictedHelp }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f).padding(end = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LockOpen,
                                    contentDescription = null,
                                    tint = lsColors.accentWarn,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = lsString(R.string.core_restricted_title),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.textPrimary
                                )
                            }
                            Text(
                                text = if (showRestrictedHelp) lsString(R.string.core_tag_hide) else lsString(R.string.core_tag_help),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accentWarn,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (showRestrictedHelp) {
                            Text(
                                text = lsString(R.string.core_restricted_steps),
                                fontSize = 11.sp,
                                color = lsColors.textSecondary,
                                lineHeight = 15.sp
                            )
                            DeckSubChip(
                                text = lsString(R.string.core_btn_open_app_info),
                                icon = Icons.Rounded.LockOpen,
                                onClick = {
                                    LightspeedHapticEngine.tick(context)
                                    if (previewMode) {
                                        statusNote = "PREVIEW: the real chip opens Lightspeed's App Info page so you can tap the three-dot menu and Allow restricted settings."
                                    } else {
                                        openLightspeedAppInfo(context)
                                    }
                                }
                            )
                        }
                    }
                }

                // Inline live status notice if user triggered an action
                statusNote?.let { note ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.secondaryContainer.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = note,
                            fontSize = 11.5.sp,
                            color = colorScheme.onSecondaryContainer,
                            lineHeight = 15.5.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }

                // Compact Expandable Row 2: Plain-English Accessibility Disclosure & Offline Pledge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colorScheme.surfaceContainerHigh.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showPrivacyDetails = !showPrivacyDetails }
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = lsString(R.string.core_privacy_title),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.textPrimary,
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            )
                            Text(
                                text = if (showPrivacyDetails) lsString(R.string.core_tag_hide) else lsString(R.string.core_tag_details),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (showPrivacyDetails) {
                            Text(
                                text = lsString(R.string.core_privacy_body),
                                fontSize = 11.sp,
                                color = lsColors.textSecondary,
                                lineHeight = 15.5.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Escape Hatches: Restore Config Vault Backup & Guidebook
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckSubChip(
                    text = lsString(R.string.core_chip_restore),
                    icon = Icons.Rounded.Restore,
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        if (previewMode) {
                            statusNote = "PREVIEW: Restore Backup is disabled so a test cannot overwrite your settings."
                        } else {
                            showRestoreWarning = true
                        }
                    }
                )
                Text(
                    text = if (previewMode) lsString(R.string.core_status_preview) else lsString(R.string.core_status_waiting),
                    fontSize = 11.sp,
                    color = lsColors.textSecondary
                )
                DeckSubChip(
                    text = lsString(R.string.core_chip_guidebook),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        onOpenGuidebook()
                    }
                )
            }
        }
    }

    if (showRestoreWarning) {
        DeckDialog(
            onDismissRequest = { showRestoreWarning = false },
            title = { Text(lsString(R.string.core_restore_dialog_title)) },
            text = { Text(lsString(R.string.vault_import_warning)) },
            confirmButton = {
                DeckButton(
                    text = lsString(R.string.core_restore_dialog_confirm),
                    isActive = true,
                    onClick = {
                        showRestoreWarning = false
                        restoreLauncher.launch("*/*")
                    }
                )
            },
            dismissButton = {
                DeckButton(
                    text = lsString(R.string.core_cancel),
                    onClick = { showRestoreWarning = false }
                )
            }
        )
    }
}

/**
 * Nightly-only banner shown while the Core Setup gate is replayed from About. Lets the tester
 * simulate each Shizuku state (not running / needs approval / authorized) without touching the
 * real Shizuku or Accessibility permissions. Never composed in Stable.
 */
@Composable
private fun CoreSetupPreviewBanner(
    previewSim: Int,
    onSelectSim: (Int) -> Unit,
    onClosePreview: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "PREVIEW${NightlyFeatures.BADGE_SUFFIX}",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colorScheme.onTertiaryContainer
            )
            Text(
                text = "Buttons here are simulated and change nothing on your device. Pick a Shizuku state to see how the screen reacts:",
                fontSize = 11.sp,
                color = lsColors.textSecondary,
                lineHeight = 15.sp
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DeckSubChip(text = "Live", isActive = previewSim == -1, onClick = { onSelectSim(-1) })
                DeckSubChip(text = "Not running", isActive = previewSim == 0, onClick = { onSelectSim(0) })
                DeckSubChip(text = "Needs approval", isActive = previewSim == 1, onClick = { onSelectSim(1) })
                DeckSubChip(text = "Authorized", isActive = previewSim == 2, onClick = { onSelectSim(2) })
            }
            DeckButton(
                text = "Close Preview",
                onClick = onClosePreview,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
