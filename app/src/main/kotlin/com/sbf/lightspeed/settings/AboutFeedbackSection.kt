package com.sbf.lightspeed.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    var showHallOfFameDialog by rememberSaveable { mutableStateOf(false) }
    val lsColors = LightspeedStyle.colors

    if (showHallOfFameDialog) {
        NebulaHallOfFameDialog(
            context = context,
            onDismissRequest = { showHallOfFameDialog = false }
        )
    }

    if (showInfinityDialog) {
        LightspeedInfinityDialog(context = context, onDismiss = { showInfinityDialog = false })
    }

    if (showLicensesDialog) {
        DeckDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open-Source Notices", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        text = "android-realtime-voice-isolation (GTCRN & ONNX Runtime)\nCopyright (c) 2026 André Naumann / Copyright (c) 2024 Rong Xiaobin\nLicensed under the MIT License.",
                        fontSize = 11.5.sp,
                        color = lsColors.textPrimary
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

/**
 * Tree of the Universe
 * Full-screen bottom-up cosmic overlay with living biological tree structure & gravity-reactive fruit.
 * 100% offline, zero emojis, tap empty space to dismiss.
 */
@Composable
fun TreeOfTheUniverseDialog(
    context: Context,
    onDismissRequest: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = androidx.compose.ui.platform.LocalDensity.current

    fun copyToClipboard(label: String, pkg: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("Companion App", "$label ($pkg)")
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "Copied $label ($pkg)", Toast.LENGTH_SHORT).show()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismissRequest,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.sbf.lightspeed.ui.theme.LightspeedTokens.TacticalDeepBlack)
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                )
        ) {
            // Cosmic starfield & biological living tree Canvas background
            val treeAccent = lsColors.accent
            val treeStem = lsColors.accentInfo
            val soilColor = lsColors.accentOk

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Starfield background
                val starCount = 65
                val time = System.currentTimeMillis()
                for (i in 0 until starCount) {
                    val sx = ((i * 197.3f) % w)
                    val sy = ((i * 311.7f) % h)
                    val pulse = kotlin.math.sin((time / 500.0) + (i * 0.8)).toFloat() * 0.35f + 0.65f
                    val starSize = ((i % 3) + 1.2f) * density.density * (0.8f + 0.2f * pulse)
                    val alpha = (0.25f + 0.65f * pulse).coerceIn(0.1f, 0.9f)
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
                        radius = starSize / 2f,
                        center = Offset(sx, sy)
                    )
                }

                // 2. Shooting star across top sky
                val shootingStarPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.05f, h * 0.12f)
                    quadraticBezierTo(w * 0.45f, h * 0.05f, w * 0.92f, h * 0.08f)
                }
                drawPath(
                    path = shootingStarPath,
                    color = treeAccent.copy(alpha = 0.55f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2.5f * density.density,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                )
                drawCircle(
                    color = treeAccent,
                    radius = 4f * density.density,
                    center = Offset(w * 0.92f, h * 0.08f)
                )

                // 3. Biological Living Tree Trunk & Roots
                val trunkBaseX = w * 0.5f
                val trunkBaseY = h * 0.82f
                val trunkTopY = h * 0.44f

                // Roots into soil
                val rootPath1 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkBaseY)
                    quadraticBezierTo(trunkBaseX - (w * 0.15f), h * 0.88f, trunkBaseX - (w * 0.32f), h * 0.95f)
                }
                val rootPath2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkBaseY)
                    quadraticBezierTo(trunkBaseX + (w * 0.15f), h * 0.88f, trunkBaseX + (w * 0.32f), h * 0.95f)
                }
                val rootPath3 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkBaseY)
                    lineTo(trunkBaseX, h * 0.96f)
                }
                drawPath(rootPath1, soilColor.copy(alpha = 0.75f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(rootPath2, soilColor.copy(alpha = 0.75f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.5f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(rootPath3, soilColor.copy(alpha = 0.60f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                // Glowing Organic Trunk (flaring gracefully at the base)
                val trunkPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX - (16f * density.density), trunkBaseY)
                    quadraticBezierTo(trunkBaseX - (8f * density.density), (trunkBaseY + trunkTopY) * 0.5f, trunkBaseX - (10f * density.density), trunkTopY)
                    lineTo(trunkBaseX + (10f * density.density), trunkTopY)
                    quadraticBezierTo(trunkBaseX + (8f * density.density), (trunkBaseY + trunkTopY) * 0.5f, trunkBaseX + (16f * density.density), trunkBaseY)
                    close()
                }
                drawPath(trunkPath, treeStem.copy(alpha = 0.45f))
                drawPath(trunkPath, treeStem.copy(alpha = 0.85f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f * density.density))

                // 4. Two Major Canopy Branches (Growing gracefully Left & Right)
                val leftBranch = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkTopY)
                    cubicTo(
                        trunkBaseX - (w * 0.18f), trunkTopY - (h * 0.04f),
                        trunkBaseX - (w * 0.35f), trunkTopY - (h * 0.02f),
                        w * 0.12f, trunkTopY - (h * 0.14f)
                    )
                }
                val rightBranch = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkTopY)
                    cubicTo(
                        trunkBaseX + (w * 0.18f), trunkTopY - (h * 0.04f),
                        trunkBaseX + (w * 0.35f), trunkTopY - (h * 0.02f),
                        w * 0.88f, trunkTopY - (h * 0.14f)
                    )
                }
                drawPath(leftBranch, treeStem.copy(alpha = 0.80f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawPath(rightBranch, treeStem.copy(alpha = 0.80f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))

                // Stem connection upwards to Crown (Lightspeed)
                val crownStem = androidx.compose.ui.graphics.Path().apply {
                    moveTo(trunkBaseX, trunkTopY)
                    lineTo(trunkBaseX, h * 0.18f)
                }
                drawPath(crownStem, treeAccent.copy(alpha = 0.65f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5f * density.density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            }

            // Foreground UI Overlay (Nodes mapped along tree structure)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // (4) TOP SKY — Lightspeed Shooting Star Anchor
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = 18.dp)
                ) {
                    Text(
                        text = "TREE OF THE UNIVERSE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = lsColors.textPrimary,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Make Your Android Your Own",
                        fontSize = 10.sp,
                        color = lsColors.accentInfo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    CosmicFruitNode(
                        title = "Lightspeed",
                        subtitle = "Offline Gesture Launcher & Cruise Workspace",
                        accentColor = lsColors.accent,
                        isCrown = true,
                        isLandscape = isLandscape,
                        onTap = { copyToClipboard("Lightspeed", "com.sbf.lightspeed") }
                    )
                }

                // (3) UPPER CANOPY — 2 Curved Branches (Left & Right) with Hanging App "Fruit" Nodes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Branch: "The Explorer"
                    Column(
                        modifier = Modifier.weight(1f).padding(end = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CANOPY · THE EXPLORER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accentInfo
                        )
                        CosmicFruitNode("MiXplorer", "The Mixed Explorer", lsColors.accentInfo, false, isLandscape) { copyToClipboard("MiXplorer", "com.mixplorer") }
                        CosmicFruitNode("WebLibre", "Sovereign Browser", lsColors.accentInfo, false, isLandscape) { copyToClipboard("WebLibre", "org.weblibre") }
                        CosmicFruitNode("Brave", "Chromium Shield", lsColors.accentInfo, false, isLandscape) { copyToClipboard("Brave", "com.brave.browser") }
                        CosmicFruitNode("InviZible Pro", "DNSCrypt / Tor / I2P", lsColors.accentInfo, false, isLandscape) { copyToClipboard("InviZible Pro", "pan.alexander.tordnscrypt.gp") }
                    }

                    // Right Branch: "Hardware & Automation"
                    Column(
                        modifier = Modifier.weight(1f).padding(start = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CANOPY · HARDWARE & AUTOMATION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accentWarn
                        )
                        CosmicFruitNode("Key Mapper", "Hardware Key Remapping", lsColors.accentWarn, false, isLandscape) { copyToClipboard("Key Mapper", "io.github.sds100.keymapper") }
                        CosmicFruitNode("Essentials", "System Utility Suite", lsColors.accentWarn, false, isLandscape) { copyToClipboard("Essentials", "com.sbf.essentials") }
                        CosmicFruitNode("MacroDroid", "Automated Device Scripts", lsColors.accentWarn, false, isLandscape) { copyToClipboard("MacroDroid", "com.arlosoft.macrodroid") }
                    }
                }

                // (2) LOWER-CENTER — The Glowing Trunk (Open App Stores)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    Text(
                        text = "THE GLOWING TRUNK · OPEN APP STORES",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.accent
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            CosmicFruitNode("Droid-ify", "F-Droid Client", lsColors.accent, false, isLandscape) { copyToClipboard("Droid-ify", "com.looker.droidify") }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CosmicFruitNode("ShizuStore", "Privileged Store", lsColors.accent, false, isLandscape) { copyToClipboard("ShizuStore", "me.bmax.shizustore") }
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            CosmicFruitNode("Orion Store", "Decentralized Store", lsColors.accent, false, isLandscape) { copyToClipboard("Orion Store", "com.orion.store") }
                        }
                    }
                }

                // (1) BOTTOM — Seeds & Roots in the Soil (Shizuku Lineage + ObtainX)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(0.92f).padding(bottom = 12.dp)
                ) {
                    Text(
                        text = "SEEDS & ROOTS IN THE SOIL · SYSTEM FOUNDATION",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.accentOk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(modifier = Modifier.weight(1.2f)) {
                            CosmicFruitNode(
                                title = "Shizuku Lineage",
                                subtitle = "Original ➔ Shizuku-Next + shevery & Stellar",
                                accentColor = lsColors.accentOk,
                                isCrown = false,
                                isLandscape = isLandscape,
                                onTap = { copyToClipboard("Shizuku Lineage", "moe.shizuku.privileged.api") }
                            )
                        }
                        Box(modifier = Modifier.weight(0.8f)) {
                            CosmicFruitNode(
                                title = "ObtainX",
                                subtitle = "Direct APK Updater",
                                accentColor = lsColors.accentOk,
                                isCrown = false,
                                isLandscape = isLandscape,
                                onTap = { copyToClipboard("ObtainX", "dev.imranr.obtainium") }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CosmicFruitNode(
    title: String,
    subtitle: String,
    accentColor: androidx.compose.ui.graphics.Color,
    isCrown: Boolean = false,
    isLandscape: Boolean = false,
    onTap: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    // In landscape orientation, gravity pulls hanging app fruit nodes downward with subtle vertical elongation
    val nodeShape = if (isLandscape) RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp, topStart = 6.dp, topEnd = 6.dp)
                    else RoundedCornerShape(8.dp)

    Surface(
        shape = nodeShape,
        color = accentColor.copy(alpha = if (isCrown) 0.22f else 0.12f),
        border = BorderStroke(if (isCrown) 1.5.dp else 1.dp, accentColor.copy(alpha = if (isCrown) 0.85f else 0.40f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = if (isLandscape) 4.dp else 6.dp),
            horizontalAlignment = if (isCrown) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Text(
                text = title,
                fontSize = if (isCrown) 12.sp else 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCrown) accentColor else lsColors.textPrimary,
                maxLines = 1,
                softWrap = false
            )
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = lsColors.textSecondary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
