package com.sbf.lightspeed.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle

// ---- User-visible strings (kept together so a later language step can extract them) ----
private const val TEXT_SECTION_TITLE = "About & Feedback"
private const val TEXT_BUTTON_BUG = "Report a bug"
private const val TEXT_BUTTON_LANGUAGE = "Language issue"
private const val TEXT_HINT = "Opens a pre-filled page in your browser. Nothing is sent until you submit it there."
private const val TEXT_NO_BROWSER = "No browser found; link copied"
private const val TEXT_UNKNOWN = "unknown"
private const val TEXT_BUTTON_SHARE = "Share Lightspeed"
private const val TEXT_BUTTON_COFFEE = "Coffee"
private const val TEXT_BUTTON_INFINITY = "Lightspeed Infinity"
private const val TEXT_SHARE_CHOOSER = "Share Lightspeed"
private const val TEXT_SHARE_BODY =
    "Lightspeed: a free, ad-free, 100% offline gesture workstation, launcher, and Shizuku control layer for Android. https://github.com/SBFlabs/Lightspeed"
private const val COFFEE_URL = "https://buymeacoffee.com/sbflabs"

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
    var showInfinityDialog by rememberSaveable { mutableStateOf(false) }
    val lsColors = LightspeedStyle.colors

    if (showInfinityDialog) {
        LightspeedInfinityDialog(context = context, onDismiss = { showInfinityDialog = false })
    }

    if (showLicensesDialog) {
        DeckDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Notices", fontWeight = FontWeight.Bold) },
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
        title = TEXT_SECTION_TITLE,
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
            Text(
                text = versionLine(context),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = lsColors.textPrimary
            )
            Button(
                onClick = { shareApp(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📡  $TEXT_BUTTON_SHARE", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { openIssuePage(context, buildBugUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(TEXT_BUTTON_BUG, fontSize = 11.5.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { openIssuePage(context, buildLanguageUri(context)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(TEXT_BUTTON_LANGUAGE, fontSize = 11.5.sp, textAlign = TextAlign.Center)
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
                    Text("📊", fontSize = 12.sp)
                    Text("Contribute OEM Diagnostics", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Text(
                text = TEXT_HINT,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )

            OutlinedButton(
                onClick = { showLicensesDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open-Source Licenses (MIT / Apache 2.0)", fontSize = 11.5.sp)
            }

            // Support lives at the bottom: coffee, then the Infinity supporter tier.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { openUrl(context, COFFEE_URL) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("☕ $TEXT_BUTTON_COFFEE", fontSize = 11.5.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = { showInfinityDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Lightspeed ∞", fontSize = 11.5.sp, textAlign = TextAlign.Center)
                }
            }
        }
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
        clipboard?.setPrimaryClip(ClipData.newPlainText("Lightspeed issue link", uri.toString()))
        Toast.makeText(context, TEXT_NO_BROWSER, Toast.LENGTH_SHORT).show()
    }
}
