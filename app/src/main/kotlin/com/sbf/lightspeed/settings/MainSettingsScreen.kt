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
import com.sbf.lightspeed.CockpitSettingsActivity
import com.sbf.lightspeed.GearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
    SWIPE_UP_LEFT, SWIPE_DOWN_LEFT, SWIPE_UP_RIGHT, SWIPE_DOWN_RIGHT,
    LEFT_BACK, LEFT_UP, LEFT_DOWN, SCRUB, TAP, DOUBLE_TAP,
    SWIPE_RIGHT, SWIPE_RIGHT_BACK, RIGHT_BACK, RIGHT_UP, RIGHT_DOWN
}

object LightspeedActionRegistry {
    var isIndexed by mutableStateOf(false)
    val allTokens = mutableStateListOf<String>()
    val labelCache = mutableStateMapOf<String, String>()
    val iconBitmapCache = java.util.concurrent.ConcurrentHashMap<String, android.graphics.Bitmap>()

    fun getIconBitmap(context: Context, pkg: String): android.graphics.Bitmap? {
        return com.sbf.lightspeed.system.LightspeedIconManager.getIconBitmap(context, pkg)
    }

    fun getBaseTokens(): List<String> = listOf(
        "none", "system:close_app", "system:home", "system:back", "system:recents",
        "system:notifications", "system:quick_settings", "system:scroll_to_top"
    )

    fun initializeSync(context: Context) {
        if (allTokens.isEmpty()) {
            val base = getBaseTokens()
            allTokens.addAll(base)
            base.forEach { labelCache[it] = resolveDynamicTokenLabel(context, it) }

            // Instant Phase 0: Fast synchronous load of launcher apps (< 30ms)
            try {
                val pm = context.packageManager
                val mainIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
                val resolvedApps = pm.queryIntentActivities(mainIntent, 0)
                val appTokens = mutableListOf<String>()
                resolvedApps.forEach { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    val appToken = "app:$pkg"
                    appTokens.add(appToken)
                    val appLabel = pm.getApplicationLabel(resolveInfo.activityInfo.applicationInfo).toString()
                    labelCache[appToken] = appLabel
                }
                allTokens.addAll(appTokens.distinct())
            } catch (_: Exception) {}
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

            val shizukuShortcutsMap = mutableMapOf<String, MutableList<Pair<String, String>>>()
            if (ElevatedTaskCloser.isShizukuActive) {
                try {
                    val proc = ElevatedTaskCloser.execShizuku("dumpsys shortcut")
                    if (proc != null) {
                        val output = proc.inputStream.bufferedReader().readText()
                        proc.waitFor()

                        val shortcutBlocks = output.split("ShortcutInfo {").drop(1)
                        val idRegex = Regex("""id=([^\r\n, ]+)""")
                        val pkgRegex = Regex("""packageName=([^\r\n, ]+)""")
                        val labelRegex = Regex("""shortLabel=([^,\r\n]+)""")

                        for (block in shortcutBlocks) {
                            val pkgMatch = pkgRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                            val idMatch = idRegex.find(block)?.groupValues?.getOrNull(1)?.trim()
                            val labelMatch = labelRegex.find(block)?.groupValues?.getOrNull(1)?.trim()

                            if (!pkgMatch.isNullOrBlank() && !idMatch.isNullOrBlank()) {
                                val cleanLabel = (labelMatch ?: idMatch).trim().removeSurrounding("\"")
                                shizukuShortcutsMap.getOrPut(pkgMatch) { mutableListOf() }
                                    .add(Pair(idMatch, cleanLabel))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

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

                    // Pre-warm icon cache on background worker thread
                    getIconBitmap(context, pkg)

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

                    // 3. LauncherApps & Shizuku Dynamic / Home Shortcuts (Android 7.1+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                        var foundShortcuts = false
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
                                foundShortcuts = true
                            }
                        } catch (_: Exception) {}

                        // If not the default launcher, use pre-parsed Shizuku shortcuts for THIS specific package
                        if (!foundShortcuts) {
                            shizukuShortcutsMap[pkg]?.forEach { (shortcutId, label) ->
                                val shortcutToken = "shortcut:label=$label;pkg=$pkg;type=home_shortcut;id=$shortcutId"
                                baseTokens.add(shortcutToken)
                                temporaryLabels[shortcutToken] = label
                            }
                        }
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
        prefs.edit()
            .putBoolean("pref_statusbar_preview", false)
            .putBoolean("pref_sidebar_preview", false)
            .putBoolean("pref_sidebar_left_preview", false)
            .putBoolean("pref_section_statusbar_expanded", false)
            .putBoolean("pref_section_center_expanded", false)
            .putBoolean("pref_section_top_expanded", false)
            .putBoolean("pref_section_bottom_expanded", false)
            .putBoolean("pref_section_left_center_expanded", false)
            .putBoolean("pref_section_left_top_expanded", false)
            .putBoolean("pref_section_left_bottom_expanded", false)
            .apply()
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
                    var toggleAllTrigger by remember { mutableStateOf(0) }

                    FloatingOverlayContainer(
                        title = "Deflector Wings Controller",
                        onDismiss = { dismissAction() },
                        headerControl = {
                            IconButton(
                                onClick = { toggleAllTrigger++ },
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
                                toggleAllTrigger = toggleAllTrigger,
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
    toggleAllTrigger: Int = 0,
    onRefreshNeeded: () -> Unit = {}
) {
    // Left Wing States
    var isLeftCenterExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_center_expanded", false)) }
    var isLeftTopExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_top_expanded", true)) }
    var isLeftBottomExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_bottom_expanded", false)) }
    var isLeftFlankUnified by remember { mutableStateOf(prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)) }
    var isLeftUnifiedExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_unified_expanded", false)) }

    // Center Avionics States
    var isStatusBarExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_statusbar_expanded", true)) }
    var isBackupExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_backup_expanded", false)) }

    // Right Wing States
    var isCenterExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_center_expanded", false)) }
    var isTopExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_top_expanded", true)) }
    var isBottomExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_bottom_expanded", false)) }
    var isRightFlankUnified by remember { mutableStateOf(prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)) }
    var isRightUnifiedExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_right_unified_expanded", false)) }

    var showSymmetryInfoDialog by remember { mutableStateOf(false) }
    var showUnifyInfoDialog by remember { mutableStateOf(false) }
    var showUnifyTemplateDialogForLeft by remember { mutableStateOf(false) }
    var showUnifyTemplateDialogForRight by remember { mutableStateOf(false) }
    var selectedTemplateOption by remember { mutableStateOf(0) }

    fun cloneFlankActions(fromZone: String, toZone: String) {
        val keys = prefs.all.keys.filter { it.startsWith("pref_macro_action_${fromZone}_") }
        val edit = prefs.edit()
        keys.forEach { srcKey ->
            val suffix = srcKey.removePrefix("pref_macro_action_${fromZone}_")
            val value = prefs.getString(srcKey, "none") ?: "none"
            edit.putString("pref_macro_action_${toZone}_$suffix", value)
        }
        edit.apply()
    }

    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportSuccess by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = LightspeedBackupEngine.exportToFile(context, uri)
                result.onSuccess { count ->
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Exported $count settings to JSON successfully!", Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { err ->
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "Export failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val result = LightspeedBackupEngine.importFromFile(context, uri)
                result.onSuccess { count ->
                    isImportSuccess = true
                    importStatusMessage = "Successfully restored $count settings and shortcut configurations!"
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                }.onFailure { err ->
                    isImportSuccess = false
                    importStatusMessage = "Import Failed:\n${err.message ?: err.javaClass.simpleName}"
                }
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

    var allExpandedState by remember { mutableStateOf(false) }
    LaunchedEffect(toggleAllTrigger) {
        if (toggleAllTrigger > 0) {
            allExpandedState = !allExpandedState
            when (pagerState.currentPage) {
                0 -> {
                    isLeftCenterExpanded = allExpandedState
                    isLeftTopExpanded = allExpandedState
                    isLeftBottomExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_left_center_expanded", allExpandedState)
                        .putBoolean("pref_section_left_top_expanded", allExpandedState)
                        .putBoolean("pref_section_left_bottom_expanded", allExpandedState)
                        .putBoolean("pref_sidebar_left_preview", allExpandedState)
                        .apply()
                }
                1 -> {
                    isStatusBarExpanded = allExpandedState
                    isBackupExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_statusbar_expanded", allExpandedState)
                        .putBoolean("pref_section_backup_expanded", allExpandedState)
                        .putBoolean("pref_statusbar_preview", allExpandedState)
                        .apply()
                }
                2 -> {
                    isCenterExpanded = allExpandedState
                    isTopExpanded = allExpandedState
                    isBottomExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_center_expanded", allExpandedState)
                        .putBoolean("pref_section_top_expanded", allExpandedState)
                        .putBoolean("pref_section_bottom_expanded", allExpandedState)
                        .putBoolean("pref_sidebar_preview", allExpandedState)
                        .apply()
                }
            }
        }
    }

    val leftCustomVectors = listOf(
        "TAP" to ("Single Tap" to ArrowDirection.TAP),
        "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
        "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
        "SWIPE_RIGHT" to ("Swipe Right (Inward)" to ArrowDirection.SWIPE_RIGHT),
        "SWIPE_UP_RIGHT" to ("Swipe Up ➔ Inward" to ArrowDirection.SWIPE_UP_RIGHT),
        "SWIPE_DOWN_RIGHT" to ("Swipe Down ➔ Inward" to ArrowDirection.SWIPE_DOWN_RIGHT),
        "SWIPE_UP_DOWN" to ("Swipe Up & Down" to ArrowDirection.SWIPE_UP_DOWN),
        "SWIPE_DOWN_UP" to ("Swipe Down & Up" to ArrowDirection.SWIPE_DOWN_UP),
        "SWIPE_RIGHT_BACK" to ("Swipe Right & Return" to ArrowDirection.RIGHT_BACK),
        "SWIPE_RIGHT_UP" to ("Swipe Right & Up" to ArrowDirection.RIGHT_UP),
        "SWIPE_RIGHT_DOWN" to ("Swipe Right & Down" to ArrowDirection.RIGHT_DOWN)
    )

    val rightCustomVectors = listOf(
        "TAP" to ("Single Tap" to ArrowDirection.TAP),
        "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
        "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
        "SWIPE_LEFT" to ("Swipe Left (Inward)" to ArrowDirection.SWIPE_LEFT),
        "SWIPE_UP_LEFT" to ("Swipe Up ➔ Inward" to ArrowDirection.SWIPE_UP_LEFT),
        "SWIPE_DOWN_LEFT" to ("Swipe Down ➔ Inward" to ArrowDirection.SWIPE_DOWN_LEFT),
        "SWIPE_UP_DOWN" to ("Swipe Up & Down" to ArrowDirection.SWIPE_UP_DOWN),
        "SWIPE_DOWN_UP" to ("Swipe Down & Up" to ArrowDirection.SWIPE_DOWN_UP),
        "SWIPE_LEFT_BACK" to ("Swipe Left & Return" to ArrowDirection.LEFT_BACK),
        "SWIPE_LEFT_UP" to ("Swipe Left & Up" to ArrowDirection.LEFT_UP),
        "SWIPE_LEFT_DOWN" to ("Swipe Left & Down" to ArrowDirection.LEFT_DOWN)
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        
        // Tactical Sci-Fi 3-Tab Navigator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("◂ PORT WING", "◈ AVIONICS DECK ◈", "STARBOARD WING ▸").forEachIndexed { index, tabTitle ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }

        // 3 Swipable Pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) { pageIndex ->
            when (pageIndex) {
                // PAGE 0: PORT (LEFT) DEFLECTOR WING
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UnifyFlankActionsCard(
                            isUnified = isLeftFlankUnified,
                            onToggle = { enable ->
                                if (enable) {
                                    showUnifyTemplateDialogForLeft = true
                                } else {
                                    isLeftFlankUnified = false
                                    prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", false).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            },
                            onInfoClick = { showUnifyInfoDialog = true }
                        )

                        CompactAccordionSection(
                            title = "Left Deflector Wing — Astrogation Core Zone",
                            isExpanded = isLeftCenterExpanded,
                            onToggle = {
                                isLeftCenterExpanded = !isLeftCenterExpanded
                                prefs.edit()
                                    .putBoolean("pref_section_left_center_expanded", isLeftCenterExpanded)
                                    .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded || isLeftUnifiedExpanded)
                                    .apply()
                            }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_height", "", "Wing Span (Height)", 50, 1000, 10, 400)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_CENTER_SCRUBBING", "Swipe Inward & Pull Down (2-Step Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)

                                leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                    val (vectorTitle, arrowEnum) = pairInfo
                                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_CENTER_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_CENTER_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                }
                            }
                        }

                        if (isLeftFlankUnified) {
                            CompactAccordionSection(
                                title = "Left Deflector Wing — Flank Vector Zones (Upper & Lower)",
                                isExpanded = isLeftUnifiedExpanded,
                                onToggle = {
                                    isLeftUnifiedExpanded = !isLeftUnifiedExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_left_unified_expanded", isLeftUnifiedExpanded)
                                        .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftUnifiedExpanded)
                                        .apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("📐 UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Upper Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("📐 LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Lower Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("🎛️ DUAL SCRUBBER CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("⚡ UNIFIED GESTURE MATRIX", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                                    leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                        GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                    }
                                }
                            }
                        } else {
                            CompactAccordionSection(
                                title = "Left Deflector Wing — Upper Vector Zone",
                                isExpanded = isLeftTopExpanded,
                                onToggle = {
                                    isLeftTopExpanded = !isLeftTopExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_left_top_expanded", isLeftTopExpanded)
                                        .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded)
                                        .apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)

                                    leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_TOP_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                        GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_TOP_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                    }
                                }
                            }

                            CompactAccordionSection(
                                title = "Left Deflector Wing — Lower Vector Zone",
                                isExpanded = isLeftBottomExpanded,
                                onToggle = {
                                    isLeftBottomExpanded = !isLeftBottomExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_left_bottom_expanded", isLeftBottomExpanded)
                                        .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded)
                                        .apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)

                                    leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_BOTTOM_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                        GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_BOTTOM_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                    }
                                }
                            }
                        }
                    }
                }

                // PAGE 1: AVIONICS DECK (CENTER)
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SymmetryCouplingCard(
                            context = context,
                            prefs = prefs,
                            onModeChanged = { onRefreshNeeded() },
                            onInfoClick = { showSymmetryInfoDialog = true }
                        )

                        CompactAccordionSection(title = "Overhead Canopy (Top Status Bar)", isExpanded = isStatusBarExpanded, onToggle = {
                            isStatusBarExpanded = !isStatusBarExpanded
                            prefs.edit()
                                .putBoolean("pref_section_statusbar_expanded", isStatusBarExpanded)
                                .putBoolean("pref_statusbar_preview", isStatusBarExpanded)
                                .apply()
                        }) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrefToggleRow(context, prefs, "pref_statusbar_enabled", "", "", "Enable Overhead Canopy Gestures", "Enable full touch, tap, and swipe gesture matrix parsing over the overhead canopy zone.")
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_span", "", "Canopy Span", 50, 2000, 50, 1080)
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_thickness", "", "Canopy Thickness", 10, 300, 5, 80)
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_sensitivity", "", "Touch Vector Reach", 10, 100, 5, 40)
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_x", "", "Horizontal Offset", -500, 500, 10, 0)
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_y", "", "Vertical Offset", -200, 200, 5, 0)
                                PrefDottedSliderRow(context, prefs, "pref_statusbar_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_STATUSBAR_SCRUBBING", "Swipe Along Bar & Pull Down (2-Step Scrubbing)", listOf("none", "system:screen_timeout", "system:volume", "system:brightness", "system:scroll_to_top"), tokenLabelCache)

                                val statusBarVectors = listOf(
                                    Triple("TAP", "Single Tap", ArrowDirection.TAP),
                                    Triple("DOUBLE_TAP", "Double Tap", ArrowDirection.DOUBLE_TAP),
                                    Triple("SWIPE_LEFT", "Swipe Left", ArrowDirection.SWIPE_LEFT),
                                    Triple("SWIPE_RIGHT", "Swipe Right", ArrowDirection.SWIPE_RIGHT),
                                    Triple("SWIPE_LEFT_BACK", "Swipe Left & Return", ArrowDirection.LEFT_BACK),
                                    Triple("SWIPE_RIGHT_BACK", "Swipe Right & Return", ArrowDirection.SWIPE_RIGHT_BACK),
                                    Triple("SWIPE_LEFT_DOWN", "Swipe Left & Down (Quick Trigger)", ArrowDirection.LEFT_DOWN),
                                    Triple("SWIPE_RIGHT_DOWN", "Swipe Right & Down (Quick Trigger)", ArrowDirection.RIGHT_DOWN)
                                )

                                statusBarVectors.forEach { (vectorKey, vectorTitle, arrowEnum) ->
                                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                }
                            }
                        }

                        CompactAccordionSection(
                            title = "Backup & Restore Engine",
                            isExpanded = isBackupExpanded,
                            onToggle = {
                                isBackupExpanded = !isBackupExpanded
                                prefs.edit().putBoolean("pref_section_backup_expanded", isBackupExpanded).apply()
                            }
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
                                        .clickable { showImportOptionsDialog = true }
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
                    }
                }

                // PAGE 2: STARBOARD (RIGHT) DEFLECTOR WING
                2 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UnifyFlankActionsCard(
                            isUnified = isRightFlankUnified,
                            onToggle = { enable ->
                                if (enable) {
                                    showUnifyTemplateDialogForRight = true
                                } else {
                                    isRightFlankUnified = false
                                    prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", false).apply()
                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    onRefreshNeeded()
                                }
                            },
                            onInfoClick = { showUnifyInfoDialog = true }
                        )

                        CompactAccordionSection(
                            title = "Right Deflector Wing — Astrogation Core Zone",
                            isExpanded = isCenterExpanded,
                            onToggle = {
                                isCenterExpanded = !isCenterExpanded
                                prefs.edit()
                                    .putBoolean("pref_section_center_expanded", isCenterExpanded)
                                    .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || isBottomExpanded || isRightUnifiedExpanded)
                                    .apply()
                            }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_height", "", "Wing Span (Height)", 50, 1000, 10, 400)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                PrefDottedSliderRow(context, prefs, "pref_sidebar_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                            }
                        }

                        if (isRightFlankUnified) {
                            CompactAccordionSection(
                                title = "Right Deflector Wing — Flank Vector Zones (Upper & Lower)",
                                isExpanded = isRightUnifiedExpanded,
                                onToggle = {
                                    isRightUnifiedExpanded = !isRightUnifiedExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_right_unified_expanded", isRightUnifiedExpanded)
                                        .putBoolean("pref_sidebar_preview", isCenterExpanded || isRightUnifiedExpanded)
                                        .apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("📐 UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Upper Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("📐 LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Lower Wing Span (Height)", 50, 600, 10, 200)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                    PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("🎛️ DUAL SCRUBBER CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary, letterSpacing = 1.sp)
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                    GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                    Text("⚡ UNIFIED GESTURE MATRIX", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, letterSpacing = 1.sp)
                                    rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                        GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                    }
                                }
                            }
                        } else {
                            val interfaceZones = listOf("TOP" to "Right Deflector Wing — Upper Vector Zone", "BOTTOM" to "Right Deflector Wing — Lower Vector Zone")
                            interfaceZones.forEachIndexed { idx, (zoneKey, zoneTitle) ->
                                val isCurrentExpanded = if (idx == 0) isTopExpanded else isBottomExpanded
                                CompactAccordionSection(title = zoneTitle, isExpanded = isCurrentExpanded, onToggle = {
                                    if (idx == 0) {
                                        isTopExpanded = !isTopExpanded
                                        val newTop = isTopExpanded
                                        prefs.edit()
                                            .putBoolean("pref_section_top_expanded", newTop)
                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || newTop || isBottomExpanded)
                                            .apply()
                                    } else {
                                        isBottomExpanded = !isBottomExpanded
                                        val newBottom = isBottomExpanded
                                        prefs.edit()
                                            .putBoolean("pref_section_bottom_expanded", newBottom)
                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || newBottom)
                                            .apply()
                                    }
                                }) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        val zonePrefix = if (idx == 0) "pref_sidebar_top" else "pref_sidebar_bottom"
                                        PrefDottedSliderRow(context, prefs, "${zonePrefix}_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                        PrefDottedSliderRow(context, prefs, "${zonePrefix}_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                        PrefDottedSliderRow(context, prefs, "${zonePrefix}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)

                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 4.dp))
                                        GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_${zoneKey}_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)

                                        rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                            val (vectorTitle, arrowEnum) = pairInfo
                                            GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_${zoneKey}_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                            GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_${zoneKey}_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                        }
                                    }
                                }
                            }
                        }
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

        if (importStatusMessage != null) {
            AlertDialog(
                onDismissRequest = { importStatusMessage = null },
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
                            importStatusMessage = null
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

        if (showImportOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showImportOptionsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Restore Configuration", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    showImportOptionsDialog = false
                                    importLauncher.launch("*/*")
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
                                    showImportOptionsDialog = false
                                    pastedJsonText = ""
                                    showPasteJsonDialog = true
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
                    TextButton(onClick = { showImportOptionsDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showPasteJsonDialog) {
            AlertDialog(
                onDismissRequest = { showPasteJsonDialog = false },
                title = { Text("Paste Backup JSON", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Paste your exported JSON payload below:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = pastedJsonText,
                            onValueChange = { pastedJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            placeholder = { Text("{\n  \"settings\": {\n    ...\n  }\n}", fontSize = 12.sp) },
                            maxLines = 15,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val text = pastedJsonText.trim()
                            showPasteJsonDialog = false
                            if (text.isBlank()) {
                                isImportSuccess = false
                                importStatusMessage = "Pasted text is empty."
                            } else {
                                scope.launch(Dispatchers.IO) {
                                    val res = LightspeedBackupEngine.importFromJson(context, text)
                                    res.onSuccess { count ->
                                        isImportSuccess = true
                                        importStatusMessage = "Successfully restored $count settings and shortcut configurations!"
                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                    }.onFailure { err ->
                                        isImportSuccess = false
                                        importStatusMessage = "Import Failed:\n${err.message}"
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteJsonDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showSymmetryInfoDialog) {
            AlertDialog(
                onDismissRequest = { showSymmetryInfoDialog = false },
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
                    Button(onClick = { showSymmetryInfoDialog = false }) {
                        Text("Got it", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyInfoDialog) {
            AlertDialog(
                onDismissRequest = { showUnifyInfoDialog = false },
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
                    Button(onClick = { showUnifyInfoDialog = false }) {
                        Text("Got it", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyTemplateDialogForLeft) {
            AlertDialog(
                onDismissRequest = { showUnifyTemplateDialogForLeft = false },
                title = { Text("Activate Unified Left Flank", fontWeight = FontWeight.Bold, color = Color.White) },
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
                                    .background(if (selectedTemplateOption == optIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTemplateOption = optIdx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTemplateOption == optIdx,
                                    onClick = { selectedTemplateOption = optIdx },
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
                            if (selectedTemplateOption == 0) {
                                cloneFlankActions("LEFT_TOP", "LEFT_UNIFIED")
                            } else if (selectedTemplateOption == 1) {
                                cloneFlankActions("LEFT_BOTTOM", "LEFT_UNIFIED")
                            }
                            isLeftFlankUnified = true
                            prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", true).apply()
                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            showUnifyTemplateDialogForLeft = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Unify Actions", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnifyTemplateDialogForLeft = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyTemplateDialogForRight) {
            AlertDialog(
                onDismissRequest = { showUnifyTemplateDialogForRight = false },
                title = { Text("Activate Unified Right Flank", fontWeight = FontWeight.Bold, color = Color.White) },
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
                                    .background(if (selectedTemplateOption == optIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTemplateOption = optIdx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTemplateOption == optIdx,
                                    onClick = { selectedTemplateOption = optIdx },
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
                            if (selectedTemplateOption == 0) {
                                cloneFlankActions("TOP", "UNIFIED")
                            } else if (selectedTemplateOption == 1) {
                                cloneFlankActions("BOTTOM", "UNIFIED")
                            }
                            isRightFlankUnified = true
                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", true).apply()
                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            showUnifyTemplateDialogForRight = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Unify Actions", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnifyTemplateDialogForRight = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
fun UnifyFlankActionsCard(
    isUnified: Boolean,
    onToggle: (Boolean) -> Unit,
    onInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Link,
                contentDescription = null,
                tint = if (isUnified) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Unify Upper & Lower Actions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    color = Color.White
                )
                Text(
                    if (isUnified) "Single unified gesture set • Dual scrubbers active" else "Independent upper & lower gesture sets",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = isUnified,
                onCheckedChange = { onToggle(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
fun SymmetryCouplingCard(
    context: Context,
    prefs: android.content.SharedPreferences,
    onModeChanged: () -> Unit = {},
    onInfoClick: () -> Unit = {}
) {
    var geomMode by remember { mutableStateOf(prefs.getString("pref_symmetry_geometry_mode", "independent") ?: "independent") }
    var gestMode by remember { mutableStateOf(prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Deflector Symmetry & Coupling", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    Text("Synchronize wings or maintain bilateral independence", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                }
                IconButton(
                    onClick = onInfoClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            // 1. Physical Geometry Switch
            ThreeWayTacticalSelector(
                title = "Physical Geometry (Span, Reach, Offset, Glow)",
                subtitle = when (geomMode) {
                    "right" -> "Starboard master — Port mirrors right wing geometry"
                    "left" -> "Port master — Starboard mirrors left wing geometry"
                    else -> "Independent — Each wing has custom geometry"
                },
                selectedMode = geomMode,
                onSelect = { mode ->
                    geomMode = mode
                    prefs.edit().putString("pref_symmetry_geometry_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )

            // 2. Astrogation & Gestures Switch
            ThreeWayTacticalSelector(
                title = "Astrogation & Gestures (Cockpit & Macros)",
                subtitle = when (gestMode) {
                    "right" -> "Starboard master — Port inverts & executes right actions"
                    "left" -> "Port master — Starboard inverts & executes left actions"
                    else -> "Independent — Each wing has dedicated gesture maps"
                },
                selectedMode = gestMode,
                onSelect = { mode ->
                    gestMode = mode
                    prefs.edit().putString("pref_symmetry_gesture_mode", mode).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    onModeChanged()
                }
            )
        }
    }
}

@Composable
fun ThreeWayTacticalSelector(
    title: String,
    subtitle: String,
    selectedMode: String,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
        Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val options = listOf(
                "left" to "◂ CLONE LEFT",
                "independent" to "◈ INDEPENDENT",
                "right" to "CLONE RIGHT ▸"
            )

            options.forEach { (modeKey, modeTitle) ->
                val isSelected = selectedMode == modeKey
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(modeKey) }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = modeTitle,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
        }
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
                if (actionProgress < 0.5f) { cy = h*0.8f + (h*0.3f - h*0.8f) * (actionProgress * 2f); cx = w*0.7f } else { cx = w*0.7f + (w*0.2f - w*0.7f) * ((actionProgress - 0.5f) * 2f); cy = h*0.3f }
            }
            ArrowDirection.SWIPE_DOWN_LEFT -> {
                path.moveTo(w*0.7f, h*0.2f); path.lineTo(w*0.7f, h*0.7f); path.lineTo(w*0.2f, h*0.7f)
                if (actionProgress < 0.5f) { cy = h*0.2f + (h*0.7f - h*0.2f) * (actionProgress * 2f); cx = w*0.7f } else { cx = w*0.7f + (w*0.2f - w*0.7f) * ((actionProgress - 0.5f) * 2f); cy = h*0.7f }
            }
            ArrowDirection.SWIPE_UP_RIGHT -> {
                path.moveTo(w*0.3f, h*0.8f); path.lineTo(w*0.3f, h*0.3f); path.lineTo(w*0.8f, h*0.3f)
                if (actionProgress < 0.5f) { cy = h*0.8f + (h*0.3f - h*0.8f) * (actionProgress * 2f); cx = w*0.3f } else { cx = w*0.3f + (w*0.8f - w*0.3f) * ((actionProgress - 0.5f) * 2f); cy = h*0.3f }
            }
            ArrowDirection.SWIPE_DOWN_RIGHT -> {
                path.moveTo(w*0.3f, h*0.2f); path.lineTo(w*0.3f, h*0.7f); path.lineTo(w*0.8f, h*0.7f)
                if (actionProgress < 0.5f) { cy = h*0.2f + (h*0.7f - h*0.2f) * (actionProgress * 2f); cx = w*0.3f } else { cx = w*0.3f + (w*0.8f - w*0.3f) * ((actionProgress - 0.5f) * 2f); cy = h*0.7f }
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
            ArrowDirection.RIGHT_BACK -> {
                path.moveTo(w*0.2f, h*0.5f); path.lineTo(w*0.8f, h*0.5f); path.lineTo(w*0.5f, h*0.5f)
                if (actionProgress < 0.6f) { cy = h*0.5f; cx = w*0.2f + (w*0.8f - w*0.2f) * (actionProgress / 0.6f) } else { cy = h*0.5f; cx = w*0.8f + (w*0.5f - w*0.8f) * ((actionProgress - 0.6f) / 0.4f) }
            }
            ArrowDirection.RIGHT_UP -> {
                path.moveTo(w*0.2f, h*0.7f); path.lineTo(w*0.7f, h*0.7f); path.lineTo(w*0.7f, h*0.2f)
                if (actionProgress < 0.5f) { cy = h*0.7f; cx = w*0.2f + (w*0.7f - w*0.2f) * (actionProgress * 2f) } else { cx = w*0.7f; cy = h*0.7f + (h*0.2f - h*0.7f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.RIGHT_DOWN -> {
                path.moveTo(w*0.2f, h*0.3f); path.lineTo(w*0.7f, h*0.3f); path.lineTo(w*0.7f, h*0.8f)
                if (actionProgress < 0.5f) { cy = h*0.3f; cx = w*0.2f + (w*0.7f - w*0.2f) * (actionProgress * 2f) } else { cx = w*0.7f; cy = h*0.3f + (h*0.8f - h*0.3f) * ((actionProgress - 0.5f) * 2f) }
            }
            ArrowDirection.TAP, ArrowDirection.DOUBLE_TAP -> {
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
                           direction != ArrowDirection.SCRUB &&
                           direction != ArrowDirection.TAP &&
                           direction != ArrowDirection.DOUBLE_TAP

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
                ArrowDirection.RIGHT_DOWN -> {
                    arrowheadPath.moveTo(w * 0.7f - arrowSize, h * 0.8f - arrowSize)
                    arrowheadPath.lineTo(w * 0.7f, h * 0.8f)
                    arrowheadPath.lineTo(w * 0.7f + arrowSize, h * 0.8f - arrowSize)
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
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
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
    var showScrubMenu by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentRawValue = prefs.getString(key, "none") ?: "none"
        }
    }

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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
            .clickable {
                if (direction == ArrowDirection.SCRUB) {
                    showScrubMenu = true
                } else {
                    val intent = Intent(context, GearPickerActivity::class.java).apply {
                        putExtra("SINGLE_SELECT_PREF_KEY", key)
                        putExtra("SINGLE_SELECT_TITLE", "$defaultTitle Action")
                    }
                    pickerLauncher.launch(intent)
                }
            }
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
        if (direction == ArrowDirection.SCRUB) {
            Box {
                DropdownMenu(expanded = showScrubMenu, onDismissRequest = { showScrubMenu = false }) {
                    options.forEach { opt ->
                        val optLabel = when (opt) {
                            "none" -> "None"
                            "system:screen_timeout" -> "Screen Timeout (15s – 10m)"
                            "system:volume" -> "Volume (Media Stream)"
                            "system:brightness" -> "Screen Brightness"
                            "system:scroll_to_top" -> "Scroll to Top"
                            else -> labelCache[opt] ?: opt
                        }
                        DropdownMenuItem(
                            text = { Text(optLabel) },
                            onClick = {
                                currentRawValue = opt
                                prefs.edit().putString(key, opt).apply()
                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                showScrubMenu = false
                            }
                        )
                    }
                }
            }
        }
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
