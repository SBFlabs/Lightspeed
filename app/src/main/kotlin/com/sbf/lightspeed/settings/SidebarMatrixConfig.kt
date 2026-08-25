package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedBackupEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SidebarMatrixConfigurationFields(
    context: Context,
    prefs: SharedPreferences,
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
                        OutlinedTextField(
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
