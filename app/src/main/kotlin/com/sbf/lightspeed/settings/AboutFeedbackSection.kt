package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.lsString

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.DistributionVerifier
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.SignatureStatus
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

// ---- Constants kept together ----
private const val TEXT_NO_BROWSER = "No browser found; link copied"
private const val TEXT_UNKNOWN = "unknown"
private const val TEXT_BUTTON_SHARE = "Share Lightspeed"
private const val TEXT_SHARE_CHOOSER = "Share Lightspeed"
private const val TEXT_SHARE_BODY =
    "Lightspeed: a free, ad-free, 100% offline meta-launcher & Android control layer. https://github.com/SBFlabs/Lightspeed"
private const val COFFEE_SHOP_URL = INFINITY_SHOP_URL
private const val XDA_THREAD_URL = "https://xdaforums.com/t/4804419/"

private const val ISSUE_TITLE_BUG = "[Bug] "
private const val ISSUE_TITLE_LANGUAGE = "[Language] "
private const val ISSUE_LABEL_BUG = "bug"
private const val ISSUE_LABEL_LANGUAGE = "language"
private const val BODY_BUG_WHAT_HAPPENED = "What happened:"
private const val BODY_BUG_EXPECTED = "What you expected:"
private const val BODY_BUG_STEPS = "Steps to reproduce:"
private const val BODY_LANG_SCREEN = "Screen or text that is wrong:"
private const val BODY_LANG_SUGGESTION = "Suggested correction:"
// ---------------------------------------------------------------------------------------

private const val ISSUE_HOST = "github.com"
private val ISSUE_PATH_SEGMENTS = listOf("SBFlabs", "Lightspeed", "issues", "new")

/** About page: version line plus two buttons that open a pre-filled public issue form in the browser. */
@Composable
fun AboutFeedbackSection(context: Context) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showCreditsDialog by rememberSaveable { mutableStateOf(false) }
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    var showBuildVerificationDialog by rememberSaveable { mutableStateOf(false) }
    var showInfinityDialog by rememberSaveable { mutableStateOf(false) }
    var expandKeyOnOpen by rememberSaveable { mutableStateOf(false) }
    var showSupporterReminderPreview by rememberSaveable { mutableStateOf(false) }
    val unlockEpoch by LightspeedInfinityManager.unlockEpoch.collectAsState()
    val isInfinityUnlocked = remember(unlockEpoch) { LightspeedInfinityManager.isUnlocked(context) }
    val buildVerification by produceState(
        initialValue = DistributionVerifier.getCachedVerification(),
        key1 = isExpanded || showBuildVerificationDialog
    ) {
        if (value == null && (isExpanded || showBuildVerificationDialog)) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                DistributionVerifier.inspectBuild(context)
            }
        }
    }
    val openWhatsNewMorph = LocalOpenWhatsNew.current
    val testWarmupGate = LocalTestWarmupGate.current
    val testCoreSetupGate = LocalTestCoreSetupGate.current
    val lsColors = LightspeedStyle.colors

    if (showSupporterReminderPreview) {
        LightspeedSupporterReminderDialog(
            context = context,
            milestoneBadge = "UPDATED TO v${com.sbf.lightspeed.BuildConfig.VERSION_NAME}",
            onDismiss = { showSupporterReminderPreview = false },
            onOpenInfinityUnlock = {
                showSupporterReminderPreview = false
                showInfinityDialog = true
            }
        )
    }

    if (showInfinityDialog) {
        LightspeedInfinityDialog(
            context = context,
            onDismiss = {
                showInfinityDialog = false
                expandKeyOnOpen = false
            },
            initialExpandKeyInput = expandKeyOnOpen
        )
    }

    if (showBuildVerificationDialog && buildVerification != null) {
        BuildVerificationDialog(
            context = context,
            verification = buildVerification!!,
            onDismiss = { showBuildVerificationDialog = false }
        )
    }

    if (showCreditsDialog) {
        LightspeedCreditsDialog(onDismiss = { showCreditsDialog = false })
    }

    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    CompactAccordionSection(
        title = lsString(R.string.about_section_title),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = { isExpanded = !isExpanded }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = versionLine(context),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = lsColors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable { shareApp(context) }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = TEXT_BUTTON_SHARE,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable {
                                if (openWhatsNewMorph != null) {
                                    openWhatsNewMorph()
                                } else {
                                    showSupporterReminderPreview = true
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = lsString(R.string.about_whats_new),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open What's New",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            // Founder's License Card near the top of About & Feedback with direct 1-tap Shop CTA
            if (isInfinityUnlocked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(lsColors.accentGold.copy(alpha = 0.12f))
                        .border(0.9.dp, lsColors.accentGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .clickable {
                            expandKeyOnOpen = false
                            showInfinityDialog = true
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.about_founder_title_unlocked),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accentGold
                        )
                        Text(
                            text = lsString(R.string.about_founder_sub_unlocked),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.82f)
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = lsColors.accentGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightspeedTokens.StatusGoldAlt.copy(alpha = 0.10f))
                        .border(1.dp, LightspeedTokens.StatusGoldAlt.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        RocketFuelCanIcon(modifier = Modifier.size(16.dp), tint = lsColors.accentGold)
                        Text(
                            text = lsString(R.string.about_founder_title_locked),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = lsColors.accentGold
                        )
                    }
                    Text(
                        text = lsString(R.string.about_founder_sub_locked),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = lsColors.textPrimary.copy(alpha = 0.85f)
                    )
                    Text(
                        text = lsString(R.string.about_founder_pricing_note),
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.7f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                openExternalUrl(context, COFFEE_SHOP_URL)
                                expandKeyOnOpen = true
                                showInfinityDialog = true
                            },
                            modifier = Modifier.weight(1.35f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LightspeedTokens.StatusGoldAlt)
                        ) {
                            RocketFuelCanIcon(modifier = Modifier.size(14.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = lsString(R.string.inf_btn_get_key_short),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                expandKeyOnOpen = true
                                showInfinityDialog = true
                            },
                            modifier = Modifier.weight(0.95f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = lsColors.accentGold.copy(alpha = 0.12f),
                                contentColor = lsColors.accentGold
                            ),
                            border = BorderStroke(0.9.dp, lsColors.accentGold.copy(alpha = 0.50f))
                        ) {
                            Text(
                                text = lsString(R.string.inf_btn_enter_key_short),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Feedback & Diagnostics Tiles (unified 10.dp tonal rectangles)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AboutActionTile(
                    text = lsString(R.string.about_btn_bug),
                    icon = Icons.Outlined.BugReport,
                    onClick = { openIssuePage(context, buildBugUri(context)) },
                    modifier = Modifier.weight(1f)
                )
                AboutActionTile(
                    text = lsString(R.string.about_btn_lang),
                    icon = Icons.Default.Translate,
                    onClick = { openIssuePage(context, buildLanguageUri(context)) },
                    modifier = Modifier.weight(1f)
                )
            }

            AboutActionTile(
                text = lsString(R.string.about_oem_diag),
                icon = Icons.Default.Analytics,
                onClick = { openIssuePage(context, buildOemDiagnosticsUri(context)) },
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = lsString(R.string.about_hint_browser),
                fontSize = 10.sp,
                lineHeight = 13.5.sp,
                color = lsColors.textSecondary.copy(alpha = 0.78f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            )

            // Community & Trust List Rows (unified 10.dp left-aligned cards with chevron)
            AboutListRow(
                title = lsString(R.string.about_xda),
                subtitle = lsString(R.string.about_discussions),
                icon = Icons.Default.Forum,
                accentTint = MaterialTheme.colorScheme.primary,
                useNeutralSurface = true,
                onClick = { openExternalUrl(context, XDA_THREAD_URL) }
            )

            val resolvedVerification = buildVerification
            if (resolvedVerification != null) {
                val originLabel = resolveInstallOriginLabel(resolvedVerification.installOrigin)
                val (verifyIcon, verifyTint, verifySubtitle) = when (resolvedVerification.signatureStatus) {
                    SignatureStatus.OFFICIAL_RELEASE -> Triple(
                        Icons.Default.CheckCircle,
                        lsColors.accentOk,
                        lsString(R.string.about_verify_official, originLabel)
                    )
                    SignatureStatus.NIGHTLY_DEV_BUILD -> Triple(
                        Icons.Default.Shield,
                        lsColors.accent,
                        lsString(R.string.about_verify_nightly, originLabel)
                    )
                    SignatureStatus.CUSTOM_OR_UNVERIFIED -> Triple(
                        Icons.Default.Warning,
                        lsColors.accentWarn,
                        lsString(R.string.about_verify_custom)
                    )
                }
                AboutListRow(
                    title = lsString(R.string.about_verify_title),
                    subtitle = verifySubtitle,
                    icon = verifyIcon,
                    accentTint = verifyTint,
                    useNeutralSurface = false,
                    onClick = { showBuildVerificationDialog = true }
                )
            }

            AboutListRow(
                title = lsString(R.string.about_contact_title),
                subtitle = lsString(R.string.about_contact_sub),
                icon = Icons.Outlined.Email,
                accentTint = MaterialTheme.colorScheme.primary,
                useNeutralSurface = true,
                singleLineSubtitle = true,
                onClick = { openSbfSupportEmail(context, versionLine(context)) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AboutActionTile(
                    text = lsString(R.string.about_btn_licenses),
                    icon = Icons.Outlined.Description,
                    onClick = { showLicensesDialog = true },
                    modifier = Modifier.weight(1f)
                )
                AboutActionTile(
                    text = lsString(R.string.about_btn_credits),
                    icon = Icons.Outlined.WorkspacePremium,
                    onClick = { showCreditsDialog = true },
                    modifier = Modifier.wrapContentWidth()
                )
            }

            // Nightly Dev Gate Testers tucked cleanly at the bottom (debug-only)
            if (NightlyFeatures.isWarmupGateTesterEnabled && testWarmupGate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lsColors.accent.copy(alpha = 0.12f))
                        .clickable { testWarmupGate() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test Welcome & Update Gate${NightlyFeatures.BADGE_SUFFIX}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.accent
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Test Welcome & Update Gate",
                        tint = lsColors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (NightlyFeatures.isCoreSetupGateTesterEnabled && testCoreSetupGate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lsColors.accent.copy(alpha = 0.12f))
                        .clickable { testCoreSetupGate() }
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Test Core Setup Gate${NightlyFeatures.BADGE_SUFFIX}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.accent
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Test Core Setup Gate",
                        tint = lsColors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutActionTile(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(lsColors.textPrimary.copy(alpha = 0.05f))
            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        var labelSizeSp by remember(text) { mutableFloatStateOf(11.5f) }
        Text(
            text = text,
            fontSize = labelSizeSp.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = lsColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if ((result.didOverflowWidth || result.hasVisualOverflow) && labelSizeSp > 9.5f) {
                    labelSizeSp = (labelSizeSp - 0.5f).coerceAtLeast(9.5f)
                }
            }
        )
    }
}

@Composable
private fun AboutListRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentTint: Color,
    useNeutralSurface: Boolean = true,
    singleLineSubtitle: Boolean = false,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (useNeutralSurface) lsColors.textPrimary.copy(alpha = 0.05f) else accentTint.copy(alpha = 0.09f))
            .border(0.8.dp, accentTint.copy(alpha = if (useNeutralSurface) 0.22f else 0.30f), shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentTint,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.textPrimary
            )
            if (singleLineSubtitle) {
                var subFontSp by remember(subtitle) { mutableFloatStateOf(10.5f) }
                Text(
                    text = subtitle,
                    fontSize = subFontSp.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { result ->
                        if ((result.didOverflowWidth || result.hasVisualOverflow) && subFontSp > 8.8f) {
                            subFontSp = (subFontSp - 0.4f).coerceAtLeast(8.8f)
                        }
                    }
                )
            } else {
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = lsColors.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accentTint.copy(alpha = 0.8f),
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Opens the system share sheet with a short pitch and the project link. */
private fun shareApp(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, TEXT_SHARE_BODY)
    }
    try {
        context.startActivity(Intent.createChooser(send, TEXT_SHARE_CHOOSER).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, TEXT_NO_BROWSER, Toast.LENGTH_SHORT).show()
    }
}

private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: TEXT_UNKNOWN
} catch (e: Exception) {
    TEXT_UNKNOWN
}

private fun versionCode(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toString()
    else @Suppress("DEPRECATION") info.versionCode.toString()
} catch (e: Exception) {
    TEXT_UNKNOWN
}

private fun versionLine(context: Context): String =
    "Lightspeed ${versionName(context)} (${versionCode(context)})"

private fun deviceLine(context: Context): String =
    "Lightspeed ${versionName(context)} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) / ${Build.MANUFACTURER} ${Build.MODEL}"

private fun buildBugUri(context: Context): Uri {
    val body = listOf(
        BODY_BUG_WHAT_HAPPENED, "", "",
        BODY_BUG_EXPECTED, "", "",
        BODY_BUG_STEPS, "", "",
        deviceLine(context)
    ).joinToString("\n")
    return buildIssueUri(ISSUE_TITLE_BUG, ISSUE_LABEL_BUG, body)
}

private fun buildLanguageUri(context: Context): Uri {
    val currentMode = context.defaultPrefs()
        .getString(LightspeedLanguageEngine.KEY_LANGUAGE_MODE, null) ?: TEXT_UNKNOWN
    val body = listOf(
        "Language mode (current: $currentMode):", "", "",
        BODY_LANG_SCREEN, "", "",
        BODY_LANG_SUGGESTION, "", "",
        deviceLine(context)
    ).joinToString("\n")
    return buildIssueUri(ISSUE_TITLE_LANGUAGE, ISSUE_LABEL_LANGUAGE, body)
}

private fun buildOemDiagnosticsUri(context: Context): Uri {
    val pm = context.packageManager
    val cr = context.contentResolver
    val hasFreeform = pm.hasSystemFeature("android.software.freeform_window_management")
    val hasPip = pm.hasSystemFeature("android.software.picture_in_picture")
    val hasTclFreeform = pm.hasSystemFeature("com.tcl.software.freeform")
    val freeformGlobal = try { android.provider.Settings.Global.getInt(cr, "enable_freeform_support", -1) } catch (_: Exception) { -1 }
    val resizableGlobal = try { android.provider.Settings.Global.getInt(cr, "force_resizable_activities", -1) } catch (_: Exception) { -1 }
    val hasLenovoSplit = try { pm.getPackageInfo("com.lenovo.screensplit", 0) != null } catch (_: Exception) { false }

    val deviceBrand = if (Build.MANUFACTURER.equals(Build.BRAND, ignoreCase = true)) {
        "${Build.BRAND} ${Build.MODEL}"
    } else {
        "${Build.MANUFACTURER} ${Build.BRAND} ${Build.MODEL}"
    }

    val oemMultiWindowPkgs = listOf(
        "com.samsung.android.multiwindow",
        "com.miui.freeform",
        "com.huawei.freeform",
        "com.coloros.floatwindow",
        "com.oplus.freeform",
        "com.transsion.multitask",
        "com.lenovo.screensplit"
    )
    val detectedOemMultiWindow = oemMultiWindowPkgs.filter { pkg ->
        try { pm.getPackageInfo(pkg, 0) != null } catch (_: Exception) { false }
    }
    val oemMultiWindowSummary = if (detectedOemMultiWindow.isNotEmpty()) detectedOemMultiWindow.joinToString(", ") else "none detected"

    val metrics = context.resources.displayMetrics
    val body = listOf(
        "[OEM Diagnostics Contribution]",
        "",
        "### 🔬 OEM Capability & Multi-Window Diagnostic Report",
        "",
        "| Parameter | Value |",
        "| :--- | :--- |",
        "| **Device** | `$deviceBrand` |",
        "| **OS** | `Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})` |",
        "| **Display** | `${metrics.widthPixels}x${metrics.heightPixels} (${metrics.densityDpi} dpi)` |",
        "| **Freeform Feature** | `$hasFreeform (TCL: $hasTclFreeform)` |",
        "| **PiP Feature** | `$hasPip` |",
        "| **Settings.Global Freeform** | `enable_freeform=$freeformGlobal, resizable=$resizableGlobal` |",
        "| **OEM Multi-Window Packages** | `$oemMultiWindowSummary` |",
        "| **Lenovo ScreenSplit** | `$hasLenovoSplit` |",
        "| **Lightspeed Version** | `${versionName(context)} (${versionCode(context)})` |",
        "",
        "### 📝 Multi-Window / Pop-up Observation",
        "<!-- Describe what happens when you trigger Pop-up, Split Screen, or Graceful Close -->",
        ""
    ).joinToString("\n")

    return buildIssueUri("OEM Diagnostic: $deviceBrand", "oem-capabilities", body)
}

private fun buildIssueUri(title: String, label: String, body: String): Uri {
    val builder = Uri.Builder().scheme("https").authority(ISSUE_HOST)
    ISSUE_PATH_SEGMENTS.forEach { builder.appendPath(it) }
    return builder
        .appendQueryParameter("title", title)
        .appendQueryParameter("labels", label)
        .appendQueryParameter("body", body)
        .build()
}

/** Opens the URL in the default browser; on failure copies it to the clipboard and shows a toast. */
private fun openIssuePage(context: Context, uri: Uri) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(lsString(R.string.about_issue_link), uri.toString()))
        Toast.makeText(context, TEXT_NO_BROWSER, Toast.LENGTH_SHORT).show()
    }
}
