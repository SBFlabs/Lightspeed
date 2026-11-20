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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.DistributionVerifier
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.SignatureStatus
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle

// ---- User-visible strings (kept together so a later language step can extract them) ----
private const val TEXT_SECTION_TITLE = "About & Feedback"
private const val TEXT_BUTTON_BUG = "Report a bug"
private const val TEXT_BUTTON_LANGUAGE = "Language issue"
private const val TEXT_HINT = "Opens a pre-filled page in your browser. Nothing is sent until you submit it there."
private const val TEXT_COMMUNITIES_NOTE =
    "Use XDA Forums for discussions & setup tips.\nGitHub Issues handles bugs, feature feedback, language requests & Q&A."
private const val TEXT_SUPPORT_PAYMENT_NOTE =
    "Buy Me a Coffee ($1.99 Lightspeed ∞) supports Google Pay."
private const val TEXT_NO_BROWSER = "No browser found; link copied"
private const val TEXT_UNKNOWN = "unknown"
private const val TEXT_BUTTON_SHARE = "Share Lightspeed"
private const val TEXT_BUTTON_COFFEE = "Coffee"
private const val TEXT_BUTTON_INFINITY = "Lightspeed Infinity"
private const val TEXT_SHARE_CHOOSER = "Share Lightspeed"
private const val TEXT_SHARE_BODY =
    "Lightspeed: a free, ad-free, 100% offline meta-launcher & Android control layer. https://github.com/SBFlabs/Lightspeed"
private const val COFFEE_PAGE_URL = "https://buymeacoffee.com/sbflabs"
private const val COFFEE_SHOP_URL = "https://buymeacoffee.com/sbflabs/e/579114"
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
    var showLicensesDialog by rememberSaveable { mutableStateOf(false) }
    var showBuildVerificationDialog by rememberSaveable { mutableStateOf(false) }
    var showInfinityDialog by rememberSaveable { mutableStateOf(false) }
    var showSupporterReminderPreview by rememberSaveable { mutableStateOf(false) }
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
        LightspeedInfinityDialog(context = context, onDismiss = { showInfinityDialog = false })
    }

    if (showBuildVerificationDialog && buildVerification != null) {
        BuildVerificationDialog(
            context = context,
            verification = buildVerification!!,
            onDismiss = { showBuildVerificationDialog = false }
        )
    }

    if (showLicensesDialog) {
        DeckDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text(lsString(R.string.about_oss_title), fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Shizuku API & Provider\nCopyright (c) 2021 RikkaW\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "AndroidX & Jetpack Compose\nCopyright (c) The Android Open Source Project\nLicensed under the Apache License, Version 2.0.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "SplitAudio\nCopyright (c) 2026 splitaudio contributors\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "android-realtime-voice-isolation & GTCRN\nCopyright (c) 2026 André Naumann / Copyright (c) 2024 Rong Xiaobin\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "Microsoft ONNX Runtime\nCopyright (c) Microsoft Corporation\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "Volume++\nCopyright (c) 2026 noel-digital-fan\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "Essentials\nCopyright (c) 2025 Sameera Sandakelum\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "MIT License Notice:\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.",
                        fontSize = 10.sp,
                        color = lsColors.textSecondary
                    )
                }
            },
            confirmButton = {
                DeckButton(
                    text = "Close",
                    onClick = { showLicensesDialog = false },
                    isActive = true
                )
            }
        )
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
            if (NightlyFeatures.isWarmupGateTesterEnabled && testWarmupGate != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
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
                        .clip(RoundedCornerShape(8.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openIssuePage(context, buildBugUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(lsString(R.string.about_btn_bug), fontSize = 11.5.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { openIssuePage(context, buildLanguageUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(lsString(R.string.about_btn_lang), fontSize = 11.5.sp, textAlign = TextAlign.Center)
                }
            }

            // 3rd Button: Contribute OEM Diagnostics Matrix
            OutlinedButton(
                onClick = { openIssuePage(context, buildOemDiagnosticsUri(context)) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(lsString(R.string.about_oem_diag), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            AboutNoteBanner(text = lsString(R.string.about_hint_browser))

            OutlinedButton(
                onClick = { openUrl(context, XDA_THREAD_URL) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(lsString(R.string.about_xda), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = lsString(R.string.about_discussions),
                        fontSize = 10.sp,
                        color = lsColors.textSecondary
                    )
                }
            }

            val resolvedVerification = buildVerification
            if (resolvedVerification != null) {
                val originLabel = if (resolvedVerification.installOrigin == com.sbf.lightspeed.system.InstallOrigin.DIRECT_SIDELOAD) {
                    lsString(R.string.bver_origin_sideload)
                } else {
                    resolvedVerification.installOrigin.displayName
                }
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(verifyTint.copy(alpha = 0.09f))
                        .border(0.8.dp, verifyTint.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                        .clickable { showBuildVerificationDialog = true }
                        .padding(horizontal = 11.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Icon(
                        imageVector = verifyIcon,
                        contentDescription = null,
                        tint = verifyTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.about_verify_title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = verifySubtitle,
                            fontSize = 10.5.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Inspect build verification",
                        tint = verifyTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            OutlinedButton(
                onClick = { showLicensesDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(lsString(R.string.about_oss_lic), fontSize = 11.5.sp)
            }

            // Support: Infinity supporter tier (Coffee is already pinned in the bottom bar).
            OutlinedButton(
                onClick = { showInfinityDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lightspeed ∞", fontSize = 11.5.sp, textAlign = TextAlign.Center)
            }

            AboutNoteBanner(annotatedText = buildGooglePayAnnotatedText(lsString(R.string.about_gpay_note)))
        }
    }
}

internal fun buildGooglePayAnnotatedText(text: String): AnnotatedString {
    val keyword = "Google Pay"
    val idx = text.indexOf(keyword)
    if (idx < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, idx))
        withStyle(
            SpanStyle(
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline
            )
        ) {
            append(keyword)
        }
        append(text.substring(idx + keyword.length))
    }
}

/** Opens the system share sheet with a short pitch and the project link. */
@Composable
private fun AboutNoteBanner(
    text: String? = null,
    annotatedText: AnnotatedString? = null
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f), shape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp)
        )
        Text(
            text = annotatedText ?: AnnotatedString(text.orEmpty()),
            fontSize = 11.sp,
            lineHeight = 14.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f)
        )
    }
}

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

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
