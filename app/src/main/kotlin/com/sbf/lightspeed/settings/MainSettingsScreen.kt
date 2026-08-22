package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.sbf.lightspeed.system.LightspeedBackupEngine
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.ElevatedTaskCloser
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ArrowDirection {
    SWIPE_UP, SWIPE_DOWN, SWIPE_LEFT, SWIPE_UP_DOWN, SWIPE_DOWN_UP,
    SWIPE_UP_LEFT, SWIPE_DOWN_LEFT, LEFT_BACK, LEFT_UP, LEFT_DOWN, SCRUB, TAP, SWIPE_RIGHT, SWIPE_RIGHT_BACK
}

object LightspeedActionRegistry {
    var isIndexed by mutableStateOf(false)
    val allTokens = mutableStateListOf<String>()
    val labelCache = mutableStateMapOf<String, String>()

        fun getBaseTokens(): List<String> = listOf(
        "none", "system:close_app", "system:home", "system:back", "system:recents",
        "system:notifications", "system:quick_settings", "system:scroll_to_top"
    )

    fun initializeSync(context: Context) {
        if (allTokens.isEmpty()) {
            val base = getBaseTokens()
            allTokens.addAll(base)
            base.forEach { labelCache[it] = resolveDynamicTokenLabel(context, it) }
        }
    }

    suspend fun ensureIndexed(context: Context) {
        initializeSync(context)
        if (isIndexed) return

        withContext(Dispatchers.IO) {
            val baseTokens = mutableListOf<String>()
            baseTokens.addAll(getBaseTokens())
            val temporaryLabels = mutableMapOf<String, String>()
            baseTokens.forEach { temporaryLabels[it] = resolveDynamicTokenLabel(context, it) }

            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolvedApps = pm.queryIntentActivities(mainIntent, 0)

                resolvedApps.forEach { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val appToken = "app:$pkg"
                    baseTokens.add(appToken)
                    val appLabel = pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
                    temporaryLabels[appToken] = appLabel

                    // 1. Exported Activity Deep Links
                    try {
                        val pkgInfo = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                        pkgInfo.activities?.forEach { activityInfo ->
                            if (activityInfo.exported && activityInfo.name != resolveInfo.activityInfo.name) {
                                val actLabel = activityInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: activityInfo.name.substringAfterLast(".")
                                val displayLabel = if (actLabel == appLabel) "$actLabel (${activityInfo.name.substringAfterLast(".")})" else actLabel
                                val activityToken = "shortcut:label=" + displayLabel + ";pkg=" + pkg + ";type=activity;activity=" + activityInfo.name
                                baseTokens.add(activityToken)
                                temporaryLabels[activityToken] = displayLabel
                            }
                        }
                    } catch (_: Exception) {}

                    // 2. ACTION_CREATE_SHORTCUT Plugins
                    try {
                        val shortcutIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(pkg)
                        pm.queryIntentActivities(shortcutIntent, 0).forEach { pluginInfo ->
                            val pluginLabel = pluginInfo.loadLabel(pm).toString().takeIf { it.isNotBlank() } ?: pluginInfo.activityInfo.name.substringAfterLast(".")
                            val pluginToken = "shortcut:label=" + pluginLabel + ";pkg=" + pkg + ";type=app_shortcut;activity=" + pluginInfo.activityInfo.name
                            baseTokens.add(pluginToken)
                            temporaryLabels[pluginToken] = pluginLabel
                        }
                    } catch (_: Exception) {}

                    // 3. LauncherApps Shortcuts (Android 7.1+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        try {
                            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                            val query = LauncherApps.ShortcutQuery().apply {
                                setPackage(pkg)
                                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST)
                            }
                            launcherApps.getShortcuts(query, Process.myUserHandle())?.forEach { shortcut ->
                                val label = shortcut.shortLabel?.toString() ?: "Shortcut"
                                val shortcutToken = "shortcut:label=" + label + ";pkg=" + pkg + ";type=home_shortcut;id=" + shortcut.id
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = label
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val distinctTokens = baseTokens.distinct()
            withContext(Dispatchers.Main) {
                allTokens.clear()
                allTokens.addAll(distinctTokens)
                labelCache.clear()
                labelCache.putAll(temporaryLabels)
                isIndexed = true
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("default", Context.MODE_PRIVATE) }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var subAllCollapsed by remember { mutableStateOf(false) }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    val dismissAction = {
        isVisible = false
        scope.launch {
            kotlinx.coroutines.delay(300)
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    val animatedScrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.25f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = animatedScrimAlpha))
            .clickable { dismissAction() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.94f)
                        .clickable(enabled = false) {}
                ) {
                    var refreshKey by remember { mutableStateOf(0) }

                    FloatingOverlayContainer(
                        title = "Sidebar Matrix Controller",
                        onDismiss = { dismissAction() },
                        headerControl = {
                            IconButton(
                                onClick = { subAllCollapsed = !subAllCollapsed },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp).offset(y = 3.dp)
                                    )
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(16.dp).offset(y = (-3).dp)
                                    )
                                }
                            }
                        }
                    ) {
                        key(refreshKey) {
                            SidebarMatrixConfigurationFields(
                                context = context,
                                prefs = prefs,
                                subAllCollapsed = subAllCollapsed,
                                onRefreshNeeded = { refreshKey++ }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarMatrixConfigurationFields(
    context: Context,
    prefs: android.content.SharedPreferences,
    subAllCollapsed: Boolean,
    onRefreshNeeded: () -> Unit = {}
) {
    var isStatusBarExpanded by remember { mutableStateOf(false) }
    var isCenterExpanded by remember { mutableStateOf(false) }
    var isTopExpanded by remember { mutableStateOf(true) }
    var isBottomExpanded by remember { mutableStateOf(false) }
    var isBackupExpanded by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            val result = LightspeedBackupEngine.exportToFile(context, uri)
            result.onSuccess { count ->
                Toast.makeText(context, "Exported $count settings to JSON successfully!", Toast.LENGTH_SHORT).show()
            }.onFailure { err ->
                Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val result = LightspeedBackupEngine.importFromFile(context, uri)
            result.onSuccess { count ->
                Toast.makeText(context, "Restored $count settings successfully!", Toast.LENGTH_SHORT).show()
                onRefreshNeeded()
            }.onFailure { err ->
                Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val dynamicActionTokens = LightspeedActionRegistry.allTokens
    val tokenLabelCache = LightspeedActionRegistry.labelCache

    LaunchedEffect(Unit) {
        LightspeedActionRegistry.initializeSync(context)
        val activeKeys = prefs.all.filterKeys { it.startsWith("pref_macro_action_") }
        activeKeys.values.forEach { rawVal ->
            val str = rawVal?.toString() ?: ""
            if (str.isNotBlank()) {
                tokenLabelCache[str] = resolveDynamicTokenLabel(context, str)
            }
        }
        LightspeedActionRegistry.ensureIndexed(context)
    }

    LaunchedEffect(subAllCollapsed) {
        val ns = !subAllCollapsed
        isStatusBarExpanded = ns; isCenterExpanded = ns; isTopExpanded = ns; isBottomExpanded = ns; isBackupExpanded = ns
    }

    val customVectors = listOf(
        "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
        "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
        "SWIPE_LEFT" to ("Swipe Left" to ArrowDirection.SWIPE_LEFT),
        "SWIPE_UP_DOWN" to ("Swipe Up & Down" to ArrowDirection.SWIPE_UP_DOWN),
        "SWIPE_DOWN_UP" to ("Swipe Down & Up" to ArrowDirection.SWIPE_DOWN_UP),
        "SWIPE_UP_LEFT" to ("Swipe Up & Left" to ArrowDirection.SWIPE_UP_LEFT),
        "SWIPE_DOWN_LEFT" to ("Swipe Down & Left" to ArrowDirection.SWIPE_DOWN_LEFT),
        "SWIPE_LEFT_BACK" to ("Swipe Left & Return" to ArrowDirection.LEFT_BACK),
        "SWIPE_LEFT_UP" to ("Swipe Left & Up" to ArrowDirection.LEFT_UP),
        "SWIPE_LEFT_DOWN" to ("Swipe Left & Down" to ArrowDirection.LEFT_DOWN)
    )

    val interfaceZones = listOf("TOP" to "Top Edge [Above Sidebar Area]", "BOTTOM" to "Bottom Edge [Below Sidebar Area]")

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        
        CompactAccordionSection(title = "Status Bar Zone Configuration", isExpanded = isStatusBarExpanded, onToggle = { isStatusBarExpanded = !isStatusBarExpanded }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrefToggleRow(context, prefs, "pref_statusbar_enabled", "", "", "Enable Status Bar Gestures", "Enable full touch, tap, and swipe gesture matrix parsing over the status bar zone.")
                PrefDottedSliderRow(context, prefs, "pref_statusbar_span", "", "Span", 50, 2000, 50, 1080)
                PrefDottedSliderRow(context, prefs, "pref_statusbar_thickness", "", "Thickness", 10, 300, 5, 80)
                PrefDottedSliderRow(context, prefs, "pref_statusbar_sensitivity", "", "Sensitivity", 10, 100, 5, 40)
                PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_x", "", "Offset X", -500, 500, 10, 0)
                PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_y", "", "Offset Y", -200, 200, 5, 0)
                PrefDottedSliderRow(context, prefs, "pref_statusbar_transparency", "", "Transparency", 0, 100, 5, 0)

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_STATUSBAR_SCRUBBING", "Extended Down Swipe (Scrubbing)", listOf("none", "system:scroll_to_top", "system:volume", "system:brightness"), tokenLabelCache)

                val statusBarVectors = listOf(
                    Triple("TAP", "Single Tap", ArrowDirection.TAP),
                    Triple("SWIPE_DOWN", "Swipe Down (Notifications)", ArrowDirection.SWIPE_DOWN),
                    Triple("SWIPE_RIGHT", "Swipe Right", ArrowDirection.SWIPE_RIGHT),
                    Triple("SWIPE_RIGHT_BACK", "Swipe Right & Return", ArrowDirection.SWIPE_RIGHT_BACK),
                    Triple("SWIPE_LEFT", "Swipe Left", ArrowDirection.SWIPE_LEFT),
                    Triple("SWIPE_LEFT_BACK", "Swipe Left & Return", ArrowDirection.LEFT_BACK)
                )

                statusBarVectors.forEach { (vectorKey, vectorTitle, arrowEnum) ->
                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                }
            }
        }

        CompactAccordionSection(title = "Center Zone Coordinates", isExpanded = isCenterExpanded, onToggle = { isCenterExpanded = !isCenterExpanded }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_height", "", "Height", 50, 1000, 10, 400)
                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_touch_width", "", "Sensitivity", 10, 100, 5, 40)
                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_visual_width", "", "Width", 0, 20, 1, 4)
                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_y_offset", "", "Vertical Offset", -300, 300, 10, 0)
            }
        }

        interfaceZones.forEachIndexed { idx, (zoneKey, zoneTitle) ->
            CompactAccordionSection(title = zoneTitle, isExpanded = if (idx == 0) isTopExpanded else isBottomExpanded, onToggle = { if (idx == 0) isTopExpanded = !isTopExpanded else isBottomExpanded = !isBottomExpanded }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val zonePrefix = if (idx == 0) "pref_sidebar_top" else "pref_sidebar_bottom"
                    PrefDottedSliderRow(context, prefs, "${zonePrefix}_height", "", "Height", 50, 600, 10, 200)
                    PrefDottedSliderRow(context, prefs, "${zonePrefix}_touch_width", "", "Sensitivity", 10, 100, 5, 40)
                    PrefDottedSliderRow(context, prefs, "${zonePrefix}_visual_width", "", "Width", 0, 20, 1, 4)

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_${zoneKey}_SCRUBBING", "Extended Left Swipe (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)

                    customVectors.forEach { (vectorKey, pairInfo) ->
                        val (vectorTitle, arrowEnum) = pairInfo
                        GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_${zoneKey}_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                        GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_${zoneKey}_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                    }
                }
            }
        }

        CompactAccordionSection(
            title = "Backup & Restore Engine",
            isExpanded = isBackupExpanded,
            onToggle = { isBackupExpanded = !isBackupExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Export Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .clickable { exportLauncher.launch(LightspeedBackupEngine.generateDefaultFileName()) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Export Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Text("Save all gesture maps, coordinates & gears to a local backup file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                }

                // Import Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                        .clickable { importLauncher.launch(arrayOf("application/json", "*/*")) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "Import", tint = MaterialTheme.colorScheme.secondary)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Import Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        Text("Restore complete settings from a previous Lightspeed backup file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                }

                // Reset Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                        .clickable { showResetConfirmDialog = true }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.RestartAlt, contentDescription = "Reset", tint = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset to Factory Defaults", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.error)
                        Text("Wipe custom settings and revert to pristine defaults", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                    }
                }
            }
        }

        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("Reset to Factory Defaults?", fontWeight = FontWeight.Bold, color = Color.White) },
                text = { Text("This will wipe all customized gestures, sensitivity sliders, and custom gear sets. This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(
                        onClick = {
                            LightspeedBackupEngine.resetToDefaults(context)
                            showResetConfirmDialog = false
                            Toast.makeText(context, "Preferences reset to factory defaults", Toast.LENGTH_SHORT).show()
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset Everything", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        PrefToggleRow(context, prefs, "pref_sidebar_link_edges", "", "", "Mirror dimensions", "Make the left and right sidebar sizes match perfectly.")
        PrefToggleRow(context, prefs, "pref_sidebar_link_gestures", "", "", "Mirror gestures", "Use the exact same gesture actions for both sides.")
    }
}

fun resolveDynamicTokenLabel(context: Context, token: String): String {
    return when {
        token == "none" -> "None"
        token == "system:close_app" || token == "shizuku:close_app" -> "Close App (Remove from Recents)"
        token == "system:home" -> "Home"
        token == "system:back" -> "Back"
        token == "system:recents" -> "Recents Overview"
        token == "system:notifications" -> "Notification Shade"
        token == "system:quick_settings" -> "Quick Settings"
        token == "system:scroll_to_top" -> "Scroll to Top"
        token.startsWith("app:") -> {
            val pkg = token.removePrefix("app:")
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast(".")
            }
        }
        token.startsWith("shortcut:") -> {
            token.substringAfter("label=").substringBefore(";")
        }
        else -> token
    }
}

@Composable
fun GestureTrailTracer(direction: ArrowDirection, isHold: Boolean, color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "gesture_track")
    val progress by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 1f, animationSpec = infiniteRepeatable(animation = tween(1600, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "progress")

    Canvas(modifier = modifier.size(36.dp)) {
        val w = size.width; val h = size.height; val trackStroke = 1.5.dp.toPx(); val arrowStroke = 2.5.dp.toPx()
        val path = Path()
        var cx = w * 0.5f; var cy = h * 0.5f

        val isHoldSegment = isHold && progress > 0.65f
        val actionProgress = if (isHold) (if (!isHoldSegment) progress / 0.65f else 1.0f) else progress
        val pulseAlpha = if (isHoldSegment) (1.0f - ((progress - 0.65f) / 0.35f)) else 0.0f

        when (direction) {
            ArrowDirection.SWIPE_UP -> { path.moveTo(w*0.5f, h*0.8f); path.lineTo(w*0.5f, h*0.2f); cy = h*0.8f + (h*0.2f - h*0.8f) * actionProgress }
            ArrowDirection.SWIPE_DOWN -> { path.moveTo(w*0.5f, h*0.2f); path.lineTo(w*0.5f, h*0.8f); cy = h*0.2f + (h*0.8f - h*0.2f) * actionProgress }
            ArrowDirection.SWIPE_RIGHT -> {
                path.moveTo(w*0.2f, h*0.5f); path.lineTo(w*0.8f, h*0.5f)
                cx = w*0.2f + (w*0.8f - w*0.2f) * actionProgress
            }
            ArrowDirection.SWIPE_RIGHT_BACK -> {
                path.moveTo(w*0.2f, h*0.5f); path.lineTo(w*0.8f, h*0.5f); path.lineTo(w*0.5f, h*0.5f)
                if (actionProgress < 0.6f) { cy = h*0.5f; cx = w*0.2f + (w*0.8f - w*0.2f) * (actionProgress / 0.6f) }
                else { cy = h*0.5f; cx = w*0.8f + (w*0.5f - w*0.8f) * ((actionProgress - 0.6f) / 0.4f) }
            }
            ArrowDirection.SWIPE_LEFT -> { path.moveTo(w*0.8f, h*0.5f); path.lineTo(w*0.2f, h*0.5f); cx = w*0.8f + (w*0.2f - w*0.8f) * actionProgress }
            ArrowDirection.SWIPE_UP_DOWN -> {
                path.moveTo(w*0.5f, h*0.8f); path.lineTo(w*0.5f, h*0.3f); path.lineTo(w*0.5f, h*0.7f)
                if (actionProgress < 0.5f) cy = h*0.8f + (h*0.3f - h*0.8f) * (actionProgress * 2f) else cy = h*0.3f + (h*0.7f - h*0.3f) * ((actionProgress - 0.5f) * 2f)
            }
            ArrowDirection.SWIPE_DOWN_UP -> {
                path.moveTo(w*0.5f, h*0.2f); path.lineTo(w*0.5f, h*0.7f); path.lineTo(w*0.5f, h*0.3f)
                if (actionProgress < 0.5f) cy = h*0.2f + (h*0.7f - h*0.2f) * (actionProgress * 2f) else cy = h*0.7f + (h*0.3f - h*0.7f) * ((actionProgress - 0.5f) * 2f)
            }
            ArrowDirection.SWIPE_UP_LEFT -> {
                path.moveTo(w*0.7f, h*0.8f); path.lineTo(w*0.7f, h*0.3f); path.lineTo(w*0.2f, h*0.3f)
                if (actionProgress < 0.5f) { cx = w*0.7f; cy = h*0.8f + (h*0.3f - h*0.8f) * (actionProgress * 2f) } else { cy = h*0.3f; cx = w*0.7f + (w*0.2f - w*0.7f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.SWIPE_DOWN_LEFT -> {
                path.moveTo(w*0.7f, h*0.2f); path.lineTo(w*0.7f, h*0.7f); path.lineTo(w*0.2f, h*0.7f)
                if (actionProgress < 0.5f) { cx = w*0.7f; cy = h*0.2f + (h*0.7f - h*0.2f) * (actionProgress * 2f) } else { cy = h*0.7f; cx = w*0.7f + (w*0.2f - w*0.7f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.LEFT_BACK -> {
                path.moveTo(w*0.8f, h*0.5f); path.lineTo(w*0.2f, h*0.5f); path.lineTo(w*0.5f, h*0.5f)
                if (actionProgress < 0.6f) { cy = h*0.5f; cx = w*0.8f + (w*0.2f - w*0.8f) * (actionProgress / 0.6f) } else { cy = h*0.5f; cx = w*0.2f + (w*0.5f - w*0.2f) * ((actionProgress - 0.6f) / 0.4f) }
            }
            ArrowDirection.LEFT_UP -> {
                path.moveTo(w*0.8f, h*0.7f); path.lineTo(w*0.3f, h*0.7f); path.lineTo(w*0.3f, h*0.2f)
                if (actionProgress < 0.5f) { cy = h*0.7f; cx = w*0.8f + (w*0.3f - w*0.8f) * (actionProgress * 2f) } else { cx = w*0.3f; cy = h*0.7f + (h*0.2f - h*0.7f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.LEFT_DOWN -> {
                path.moveTo(w*0.8f, h*0.3f); path.lineTo(w*0.3f, h*0.3f); path.lineTo(w*0.3f, h*0.8f)
                if (actionProgress < 0.5f) { cy = h*0.3f; cx = w*0.8f + (w*0.3f - w*0.8f) * (actionProgress * 2f) } else { cx = w*0.3f; cy = h*0.3f + (h*0.8f - h*0.3f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.TAP -> {
                cx = w * 0.5f; cy = h * 0.5f
            }
            ArrowDirection.SCRUB -> {
                val lp = (actionProgress * 2f)
                val tipOffsetX = if (lp > 1f) {
                    val angle = (lp - 1f) * 2f * kotlin.math.PI
                    (w * 0.12f) * kotlin.math.sin(angle).toFloat()
                } else {
                    0f
                }
                path.moveTo(w*0.5f, h*0.1f)
                path.lineTo(w*0.5f + tipOffsetX, h*0.9f)

                if (lp <= 1f) {
                    cx = w*0.5f
                    cy = h*0.1f + (h*0.8f) * lp
                } else {
                    cx = w*0.5f + tipOffsetX
                    cy = h*0.9f
                }
            }
        }
        drawPath(path = path, color = color.copy(alpha = 0.15f), style = Stroke(width = trackStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        val hasArrowhead = direction != ArrowDirection.SWIPE_UP_DOWN && 
                           direction != ArrowDirection.SWIPE_DOWN_UP && 
                           direction != ArrowDirection.LEFT_BACK &&
                               direction != ArrowDirection.SWIPE_RIGHT_BACK && 
                           direction != ArrowDirection.SCRUB

        if (hasArrowhead) {
            val arrowheadPath = Path(); val arrowSize = w * 0.14f
            when (direction) {
                ArrowDirection.SWIPE_RIGHT -> {
                    arrowheadPath.moveTo(w * 0.8f - arrowSize, h * 0.5f - arrowSize)
                    arrowheadPath.lineTo(w * 0.8f, h * 0.5f)
                    arrowheadPath.lineTo(w * 0.8f - arrowSize, h * 0.5f + arrowSize)
                }
                ArrowDirection.SWIPE_UP -> {
                    arrowheadPath.moveTo(w * 0.5f - arrowSize, h * 0.2f + arrowSize)
                    arrowheadPath.lineTo(w * 0.5f, h * 0.2f)
                    arrowheadPath.lineTo(w * 0.5f + arrowSize, h * 0.2f + arrowSize)
                }
                ArrowDirection.SWIPE_DOWN -> {
                    arrowheadPath.moveTo(w * 0.5f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.5f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.5f + arrowSize, h * 0.8f - arrowSize)
                }
                ArrowDirection.SWIPE_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.5f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.5f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.5f + arrowSize)
                }
                ArrowDirection.SWIPE_UP_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.3f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.3f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.3f + arrowSize)
                }
                ArrowDirection.SWIPE_DOWN_LEFT -> {
                    arrowheadPath.moveTo(w * 0.2f + arrowSize, h * 0.7f - arrowSize)
                    arrowheadPath.lineTo(w * 0.2f, h * 0.7f)
                    arrowheadPath.lineTo(w * 0.2f + arrowSize, h * 0.7f + arrowSize)
                }
                ArrowDirection.LEFT_UP -> {
                    arrowheadPath.moveTo(w * 0.3f - arrowSize, h * 0.2f + arrowSize)
                    arrowheadPath.lineTo(w * 0.3f, h * 0.2f)
                    arrowheadPath.lineTo(w * 0.3f + arrowSize, h * 0.2f + arrowSize)
                }
                ArrowDirection.LEFT_DOWN -> {
                    arrowheadPath.moveTo(w * 0.3f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.3f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.3f + arrowSize, h * 0.8f - arrowSize)
                }
                else -> {}
            }
            drawPath(path = arrowheadPath, color = color.copy(alpha = 0.4f), style = Stroke(width = arrowStroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        // Collapse calculation: shrinks the dot to 0px and fades alpha down linearly
        val fadeThreshold = 0.85f
        val (dotAlpha, dotRadius) = if (actionProgress > fadeThreshold) {
            val scaleFactor = (1f - actionProgress) / (1f - fadeThreshold)
            Pair(0.45f * scaleFactor, 4.dp.toPx() * scaleFactor)
        } else {
            Pair(0.45f, 4.dp.toPx())
        }

        drawCircle(color = color.copy(alpha = dotAlpha), radius = dotRadius, center = Offset(cx, cy))
        if (isHoldSegment) {
            drawCircle(color = color.copy(alpha = pulseAlpha), radius = 4.dp.toPx() + (22.dp.toPx() * ((progress - 0.65f) / 0.35f)), center = Offset(cx, cy), style = Stroke(width = 1.dp.toPx()))
        }
    }
}

@Composable
fun FloatingOverlayContainer(
    title: String,
    onDismiss: () -> Unit,
    headerControl: @Composable (RowScope.() -> Unit) = {},
    content: @Composable () -> Unit
) {
    val glassBorder = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.08f),
            Color.White.copy(alpha = 0.03f)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    radius = 1200f
                )
            )
            .border(1.2.dp, glassBorder, RoundedCornerShape(32.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    headerControl()
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDismiss,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(bottom = 12.dp))
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
    }
}

@Composable
fun CompactAccordionSection(title: String, isExpanded: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
fun GestureMappingRow(
    context: Context,
    prefs: android.content.SharedPreferences,
    direction: ArrowDirection,
    isHold: Boolean,
    keyResName: String,
    defaultTitle: String,
    options: List<String>,
    labelCache: Map<String, String>
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    var currentRawValue by remember { mutableStateOf(prefs.getString(key, "none") ?: "none") }
    var showMainMenu by remember { mutableStateOf(false) }
    var showSystemDialog by remember { mutableStateOf(false) }
    var showAppDialog by remember { mutableStateOf(false) }
    var showShortcutDialog by remember { mutableStateOf(false) }

    val activeLabel = remember(currentRawValue, labelCache[currentRawValue]) {
        if (currentRawValue.startsWith("shortcut:")) {
            val raw = currentRawValue.substringAfter("shortcut:")
            if (raw.contains(";pkg=")) {
                val pkg = raw.substringAfter(";pkg=").substringBefore(";")
                val appLabel = labelCache["app:$pkg"] ?: pkg
                val label = if (raw.contains(";label=")) raw.substringAfter(";label=").substringBefore(";") else ""
                if (label.isNotEmpty()) "$appLabel ($label)" else "$appLabel (Pinned)"
            } else if (raw.contains("intent:")) {
                val intentPart = if (raw.startsWith("intent:")) raw else raw.substringAfter("intent:")
                val pkg = if (intentPart.contains("package=")) {
                    intentPart.substringAfter("package=").substringBefore(";")
                } else if (intentPart.contains("component=")) {
                    val comp = intentPart.substringAfter("component=").substringBefore(";")
                    comp.substringBefore("/").removeSuffix(".").trim()
                } else ""

                val appLabel = if (pkg.isNotEmpty()) labelCache["app:$pkg"] ?: pkg else "Shortcut Action"
                val customLabel = if (raw.contains(";custom_label=")) {
                    raw.substringAfter(";custom_label=").substringBefore(";")
                } else ""

                if (customLabel.isNotEmpty()) {
                    "$appLabel ($customLabel)"
                } else if (intentPart.contains("MACRO_NAME=")) {
                    val macroEncoded = intentPart.substringAfter("MACRO_NAME=").substringBefore(";")
                    val macroName = try { java.net.URLDecoder.decode(macroEncoded, "UTF-8") } catch(e: Exception) { macroEncoded }
                    "$appLabel ($macroName)"
                } else {
                    "$appLabel (Shortcut)"
                }
            } else {
                labelCache[currentRawValue] ?: currentRawValue
            }
        } else {
            labelCache[currentRawValue] ?: currentRawValue
        }
    }
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .clickable { showMainMenu = true }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GestureTrailTracer(direction, isHold, MaterialTheme.colorScheme.primary, Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(defaultTitle, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Active Map: $activeLabel", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
        }
        Box {
            DropdownMenu(expanded = showMainMenu, onDismissRequest = { showMainMenu = false }) {
                DropdownMenuItem(text = { Text("None") }, onClick = { currentRawValue = "none"; prefs.edit().putString(key, "none").apply(); showMainMenu = false })
                DropdownMenuItem(text = { Text("System Action...") }, onClick = { showMainMenu = false; showSystemDialog = true })
                DropdownMenuItem(text = { Text("Launch Application...") }, onClick = { showMainMenu = false; showAppDialog = true })
                DropdownMenuItem(text = { Text("Launch App Shortcut...") }, onClick = { showMainMenu = false; showShortcutDialog = true })
            }
        }
    }

    if (showSystemDialog) {
        val systemTokens = remember(options) { options.filter { it == "none" || it.startsWith("system:") } }
        AlertDialog(
            onDismissRequest = { showSystemDialog = false },
            title = { Text("Select System Action") },
            text = {
                Box(modifier = Modifier.heightIn(max = 340.dp)) {
                    val scrollState = rememberScrollState()
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        systemTokens.filter { it != "none" }.forEach { token ->
                            Text(
                                text = labelCache[token] ?: token,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { currentRawValue = token; prefs.edit().putString(key, token).apply(); showSystemDialog = false }
                                    .padding(vertical = 14.dp, horizontal = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSystemDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAppDialog) {
        var appSearchQuery by remember { mutableStateOf("") }
        val appTokens = remember(options) {
            options.filter { it.startsWith("app:") }.sortedBy { (labelCache[it] ?: it).lowercase() }
        }
        val filteredApps = remember(appTokens, appSearchQuery) {
            if (appSearchQuery.isBlank()) appTokens
            else appTokens.filter { (labelCache[it] ?: it).contains(appSearchQuery, ignoreCase = true) }
        }
        val listState = rememberLazyListState()
        val letterIndices = remember(filteredApps) {
            val map = mutableMapOf<Char, Int>()
            filteredApps.forEachIndexed { index, token ->
                val label = labelCache[token] ?: ""
                val firstChar = label.firstOrNull()?.uppercaseChar() ?: '#'
                val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                if (!map.containsKey(targetKey)) { map[targetKey] = index }
            }
            map
        }

        AlertDialog(
            onDismissRequest = { showAppDialog = false },
            title = {
                Column {
                    Text("Select Application")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        label = { Text("Search Apps") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            text = {
                var sideBarHeight by remember { mutableStateOf(1f) }
                var isDragging by remember { mutableStateOf(false) }
                var hudLetter by remember { mutableStateOf("") }

                Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {

                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            itemsIndexed(filteredApps) { _, token ->
                                Text(
                                    text = labelCache[token] ?: token,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            currentRawValue = token
                                            prefs.edit().putString(key, token).apply()
                                            showAppDialog = false
                                        }
                                        .padding(vertical = 14.dp, horizontal = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .padding(start = 6.dp)
                                .onGloballyPositioned { sideBarHeight = it.size.height.toFloat() }
                                .pointerInput(filteredApps) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            isDragging = true
                                            val currentY = down.position.y.coerceIn(0f, sideBarHeight)

                                            if (filteredApps.isNotEmpty()) {
                                                val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                                val idx = (ratio * (filteredApps.size - 1)).toInt().coerceIn(0, filteredApps.size - 1)
                                                coroutineScope.launch { listState.scrollToItem(idx) }
                                                val label = labelCache[filteredApps[idx]] ?: filteredApps[idx]
                                                hudLetter = if (label.length >= 2) {
                                                    label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                } else {
                                                    label.uppercase()
                                                }
                                            }
                                            down.consume()

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val dragChange = event.changes.firstOrNull()
                                                if (dragChange != null && dragChange.pressed) {
                                                    val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                    if (filteredApps.isNotEmpty()) {
                                                        val ratio = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                        val idx = (ratio * (filteredApps.size - 1)).toInt().coerceIn(0, filteredApps.size - 1)
                                                        coroutineScope.launch { listState.scrollToItem(idx) }
                                                        val label = labelCache[filteredApps[idx]] ?: filteredApps[idx]
                                                        hudLetter = if (label.length >= 2) {
                                                            label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                        } else {
                                                            label.uppercase()
                                                        }
                                                    }
                                                    dragChange.consume()
                                                } else {
                                                    isDragging = false
                                                    break
                                                }
                                            }
                                        }
                                    }
                                },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                val hasApps = letterIndices.containsKey(letter)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasApps) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }

                    if (isDragging && hudLetter.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(80.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                                .border(2.2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hudLetter,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAppDialog = false }) { Text("Cancel") } }
        )
    }

    if (showShortcutDialog) {
        var shortcutSearchQuery by remember { mutableStateOf("") }
        var expandedSubsections by remember { mutableStateOf(setOf<String>()) }
        val shortcutTokens = remember(options) { options.filter { it.startsWith("shortcut:") } }

        val filteredShortcuts = remember(shortcutTokens, shortcutSearchQuery) {
            shortcutTokens.filter { token ->
                val pkg = token.substringAfter(";pkg=").substringBefore(";")
                val appLabel = labelCache["app:$pkg"] ?: ""
                val shortcutLabel = labelCache[token] ?: ""
                appLabel.contains(shortcutSearchQuery, ignoreCase = true) ||
                shortcutLabel.contains(shortcutSearchQuery, ignoreCase = true)
            }
        }

        val groupedShortcuts = remember(filteredShortcuts) {
            filteredShortcuts.groupBy { token ->
                val pkg = token.substringAfter(";pkg=").substringBefore(";")
                labelCache["app:$pkg"] ?: "System Shortcuts"
            }.toSortedMap(compareBy { it.lowercase() })
        }

        val flatShortcutsList by remember(groupedShortcuts, expandedSubsections) {
            androidx.compose.runtime.derivedStateOf {
                val list = mutableListOf<Pair<String, Int>>()
                groupedShortcuts.forEach { (appName, tokens) ->
                    val appShortcuts = tokens.filter { it.contains(";type=app_shortcut;") }
                    val homeShortcuts = tokens.filter { it.contains(";type=home_shortcut;") }
                    val deepActivities = tokens.filter { it.contains(";type=activity;") }

                    if (appShortcuts.isNotEmpty() || homeShortcuts.isNotEmpty() || deepActivities.isNotEmpty()) {
                        list.add(Pair(appName, 0))
                        if (appShortcuts.isNotEmpty()) {
                            val sectionKey = "$appName|App Shortcuts"
                            list.add(Pair(sectionKey, 1))
                            if (expandedSubsections.contains(sectionKey)) {
                                appShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach { list.add(Pair(it, 2)) }
                            }
                        }
                        if (homeShortcuts.isNotEmpty()) {
                            val sectionKey = "$appName|Home Screen Shortcuts"
                            list.add(Pair(sectionKey, 1))
                            if (expandedSubsections.contains(sectionKey)) {
                                homeShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach { list.add(Pair(it, 2)) }
                            }
                        }
                        if (deepActivities.isNotEmpty()) {
                            val sectionKey = "$appName|Deep Activities"
                            list.add(Pair(sectionKey, 1))
                            if (expandedSubsections.contains(sectionKey)) {
                                deepActivities.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach { list.add(Pair(it, 2)) }
                            }
                        }
                    }
                }
                list
            }
        }

        val shortcutConfigLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val targetPrefKey = prefs.getString("pending_automation_key", "") ?: ""
            val data = result.data

            if (data != null && targetPrefKey.isNotBlank() && result.resultCode == Activity.RESULT_OK) {
                var uriString = ""
                if (data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
                    val pinRequest = try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST", LauncherApps.PinItemRequest::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                        }
                    } catch (e: Exception) {
                        @Suppress("DEPRECATION")
                        data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                    }

                    if (pinRequest != null && pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                        try { pinRequest.accept() } catch (_: Exception) {}
                        val info = pinRequest.shortcutInfo
                        if (info != null) {
                            val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: ""
                            uriString = "shortcut:;id=${info.id};pkg=${info.`package`};label=$label;"
                        }
                    }
                }

                if (uriString.isBlank()) {
                    val shortcutIntent = try {
                        if (Build.VERSION.SDK_INT >= 33) {
                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
                        }
                    } catch (e: Exception) {
                        @Suppress("DEPRECATION")
                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT)
                    } ?: data

                    val label = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: ""
                    val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                    uriString = if (label.isNotEmpty()) "shortcut:intent:$intentUri;custom_label=$label;" else "shortcut:$intentUri"
                }

                currentRawValue = uriString
                prefs.edit().putString(targetPrefKey, uriString).remove("pending_automation_key").commit()
            }
            showShortcutDialog = false
        }

        val listState = rememberLazyListState()
        val shortcutLetterIndices = remember(flatShortcutsList) {
            val map = mutableMapOf<Char, Int>()
            flatShortcutsList.forEachIndexed { index, pair ->
                if (pair.second == 0) {
                    val firstChar = pair.first.firstOrNull()?.uppercaseChar() ?: '#'
                    val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                    if (!map.containsKey(targetKey)) { map[targetKey] = index }
                }
            }
            map
        }

        AlertDialog(
            onDismissRequest = { showShortcutDialog = false },
            title = {
                Column {
                    Text("Select App Shortcut")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shortcutSearchQuery,
                        onValueChange = { shortcutSearchQuery = it },
                        label = { Text("Search Shortcuts") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            text = {
                var sideBarHeight by remember { mutableStateOf(1f) }
                var isDragging by remember { mutableStateOf(false) }
                var hudLetter by remember { mutableStateOf("") }

                Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                            itemsIndexed(flatShortcutsList) { _, pair ->
                                when (pair.second) {
                                    0 -> {
                                        Text(
                                            text = pair.first.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                    1 -> {
                                        val isExpanded = expandedSubsections.contains(pair.first)
                                        val count = groupedShortcuts[pair.first.substringBefore("|")]?.let { tokens ->
                                            if (pair.first.endsWith("App Shortcuts")) tokens.count { it.contains(";type=app_shortcut;") }
                                            else if (pair.first.endsWith("Home Screen Shortcuts")) tokens.count { it.contains(";type=home_shortcut;") }
                                            else tokens.count { it.contains(";type=activity;") }
                                        } ?: 0

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    expandedSubsections = if (isExpanded) expandedSubsections - pair.first else expandedSubsections + pair.first
                                                }
                                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.2f))
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = if (isExpanded) "▼ ${pair.first.substringAfter("|")} ($count)" else "▶ ${pair.first.substringAfter("|")} ($count)",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    2 -> {
                                        val token = pair.first
                                        val isConfiguredAction = token.contains(";type=app_shortcut;")
                                        val isPinnedShortcut = token.contains(";type=home_shortcut;")

                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = labelCache[token] ?: token,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        if (isConfiguredAction) {
                                                            val pkg = token.substringAfter(";pkg=").substringBefore(";")
                                                            val act = token.substringAfter(";activity=").substringBefore(";")
                                                            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                                setClassName(pkg, act)
                                                            }
                                                            prefs.edit().putString("pending_automation_key", key).apply()
                                                            shortcutConfigLauncher.launch(intent)
                                                        } else {
                                                            currentRawValue = token
                                                            prefs.edit().putString(key, token).apply()
                                                            showShortcutDialog = false
                                                        }
                                                    }
                                                    .padding(vertical = 10.dp, horizontal = 8.dp),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(32.dp)
                                .padding(start = 6.dp)
                                .onGloballyPositioned { sideBarHeight = it.size.height.toFloat() }
                                .pointerInput(flatShortcutsList) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            isDragging = true
                                            val currentY = down.position.y.coerceIn(0f, sideBarHeight)

                                            if (flatShortcutsList.isNotEmpty()) {
                                                val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                                val idx = (ratio * (flatShortcutsList.size - 1)).toInt().coerceIn(0, flatShortcutsList.size - 1)
                                                coroutineScope.launch { listState.scrollToItem(idx) }
                                                val pair = flatShortcutsList[idx]
                                                val label = if (pair.second == 0) pair.first
                                                else if (pair.second == 1) pair.first.substringAfter("|")
                                                else labelCache[pair.first] ?: pair.first

                                                hudLetter = if (label.length >= 2) {
                                                    label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                } else {
                                                    label.uppercase()
                                                }
                                            }
                                            down.consume()

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val dragChange = event.changes.firstOrNull()
                                                if (dragChange != null && dragChange.pressed) {
                                                    val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                    if (flatShortcutsList.isNotEmpty()) {
                                                        val ratio = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                        val idx = (ratio * (flatShortcutsList.size - 1)).toInt().coerceIn(0, flatShortcutsList.size - 1)
                                                        coroutineScope.launch { listState.scrollToItem(idx) }
                                                        val pair = flatShortcutsList[idx]
                                                        val label = if (pair.second == 0) pair.first
                                                        else if (pair.second == 1) pair.first.substringAfter("|")
                                                        else labelCache[pair.first] ?: pair.first

                                                        hudLetter = if (label.length >= 2) {
                                                            label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                        } else {
                                                            label.uppercase()
                                                        }
                                                    }
                                                    dragChange.consume()
                                                } else {
                                                    isDragging = false
                                                    break
                                                }
                                            }
                                        }
                                    }
                                },
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                val hasShortcuts = shortcutLetterIndices.containsKey(letter)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasShortcuts) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }

                    if (isDragging && hudLetter.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(80.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                                .border(2.2.dp, MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hudLetter,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShortcutDialog = false }) { Text("Cancel") } }
        )
    }
}
@Composable
fun PrefToggleRow(
    context: Context,
    prefs: android.content.SharedPreferences,
    keyResName: String,
    titleResName: String,
    summaryResName: String,
    defaultTitle: String,
    defaultSummary: String
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    val summary = remember(summaryResName) { resStr(context, summaryResName, defaultSummary) }
    var checked by remember { mutableStateOf(prefs.getBoolean(key, false)) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            if (summary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                checked = it
                prefs.edit().putBoolean(key, it).apply()
            },
            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
        )
    }
}

@Composable
fun PrefDottedSliderRow(
    context: Context,
    prefs: android.content.SharedPreferences,
    keyResName: String,
    titleResName: String,
    defaultTitle: String,
    minVal: Int,
    maxVal: Int,
    step: Int = 1,
    defaultVal: Int
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    var value by remember { mutableStateOf(prefs.getInt(key, defaultVal)) }
    val steps = remember(minVal, maxVal, step) { ((maxVal - minVal) / step) - 1 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value.toString(), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(6.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = {
                val near = (it.roundToInt() / step) * step
                value = near.coerceIn(minVal, maxVal)
                prefs.edit().putInt(key, value).apply()
            },
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            steps = steps,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                activeTickColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                inactiveTickColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

fun resKey(context: Context, resourceName: String): String {
    return resourceName
}

fun resStr(context: Context, resourceName: String, fallback: String): String {
    return fallback.ifEmpty { resourceName }
}

@Composable
fun Material3ExpressiveLoader() {
    CircularProgressIndicator(
        modifier = Modifier.size(72.dp),
        color = MaterialTheme.colorScheme.primary
    )
}
