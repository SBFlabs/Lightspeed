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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import com.sbf.lightspeed.GearPickerActivity
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.OemNotchDetector
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun SidebarMatrixConfigurationFields(
    context: Context,
    prefs: SharedPreferences,
    toggleAllTrigger: Int = 0,
    jumpTargetTab: Int = -1,
    jumpTargetSection: String? = null,
    onRefreshNeeded: () -> Unit = {}
) {
    // Left Wing Accordion States
    var isLeftCenterExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_center_expanded", false)) }
    var isLeftTopExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_top_expanded", true)) }
    var isLeftBottomExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_bottom_expanded", false)) }
    var isLeftFlankUnified by remember { mutableStateOf(prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)) }
    var isLeftUnifiedExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_left_unified_expanded", false)) }

    // Left Wing Sub-Section States
    var isLeftCenterGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_center", true)) }

    var isLeftUnifiedGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_unified", true)) }
    var isLeftUnifiedScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_unified", true)) }
    var isLeftUnifiedGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_unified", true)) }

    var isLeftTopGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_top", true)) }
    var isLeftTopScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_top", true)) }
    var isLeftTopGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_top", true)) }

    var isLeftBottomGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_bottom", true)) }
    var isLeftBottomScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_bottom", true)) }
    var isLeftBottomGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_bottom", true)) }

    // Center Avionics (Canopy / Status Bar / Hardware Keys) States
    var isStatusBarExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_statusbar_expanded", true)) }
    var isVolumeKeysExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_volumekeys_expanded", false)) }
    var isBackTapExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_backtap_expanded", false)) }
    var isTelemetryExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_telemetry_expanded", false)) }
    var isRefuelingExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_refueling_expanded", false)) }
    var isPowerAssistExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_powerassist_expanded", false)) }
    var isBackupExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_backup_expanded", false)) }

    var showOemShieldDialog by remember { mutableStateOf(false) }
    var showBatteryWarningDialog by remember { mutableStateOf(false) }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }
    var showAmoledWarningDialog by remember { mutableStateOf(false) }
    var pendingBackTapScope by remember { mutableStateOf("screen_on") }

    var isStatusBarGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_statusbar", true)) }
    var isStatusBarScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_statusbar", true)) }
    var isStatusBarGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_statusbar", true)) }
    var isNotchCalibExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_notch_calib", true)) }
    var isMarqueeSubSectionExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_notch_marquee", true)) }
    var isOrientationSubSectionExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_orientation", true)) }

    // Live Impulse Calibration Meter State (Hull Tap)
    var currentZImpulse by remember { mutableFloatStateOf(0f) }
    var currentThreshold by remember { mutableFloatStateOf(LightspeedBackTapEngine.getThreshold(context)) }
    var thresholdCrossedFlash by remember { mutableStateOf(false) }

    DisposableEffect(isBackTapExpanded) {
        if (isBackTapExpanded) {
            LightspeedBackTapEngine.startLiveSampling(context)
            LightspeedBackTapEngine.onLiveImpulseListener = { zVal, thresh, isCrossed ->
                currentZImpulse = zVal
                currentThreshold = thresh
                if (isCrossed) {
                    thresholdCrossedFlash = true
                }
            }
        }
        onDispose {
            LightspeedBackTapEngine.stopLiveSampling()
        }
    }

    LaunchedEffect(thresholdCrossedFlash) {
        if (thresholdCrossedFlash) {
            LightspeedHapticEngine.tick(context)
            kotlinx.coroutines.delay(180L)
            thresholdCrossedFlash = false
        }
    }

    // Right Wing Accordion States
    var isCenterExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_center_expanded", false)) }
    var isTopExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_top_expanded", true)) }
    var isBottomExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_bottom_expanded", false)) }
    var isRightFlankUnified by remember { mutableStateOf(prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)) }
    var isRightUnifiedExpanded by remember { mutableStateOf(prefs.getBoolean("pref_section_right_unified_expanded", false)) }

    // Right Wing Sub-Section States
    var isCenterGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_center", true)) }

    var isRightUnifiedGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_unified", true)) }
    var isRightUnifiedScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_unified", true)) }
    var isRightUnifiedGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_unified", true)) }

    var isTopGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_top", true)) }
    var isTopScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_top", true)) }
    var isTopGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_top", true)) }

    var isBottomGeoExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_geo_bottom", true)) }
    var isBottomScrubExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_scrub_bottom", true)) }
    var isBottomGesturesExpanded by remember { mutableStateOf(prefs.getBoolean("pref_sub_gestures_bottom", true)) }

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
        val activeKeys = prefs.all.filterKeys { it.startsWith("pref_macro_action_") || it.startsWith("pref_key_") }
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
                    isVolumeKeysExpanded = allExpandedState
                    isBackTapExpanded = allExpandedState
                    isTelemetryExpanded = allExpandedState
                    isRefuelingExpanded = allExpandedState
                    isBackupExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_statusbar_expanded", allExpandedState)
                        .putBoolean("pref_section_volumekeys_expanded", allExpandedState)
                        .putBoolean("pref_section_backtap_expanded", allExpandedState)
                        .putBoolean("pref_section_telemetry_expanded", allExpandedState)
                        .putBoolean("pref_section_refueling_expanded", allExpandedState)
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

    LaunchedEffect(jumpTargetTab, jumpTargetSection) {
        if (jumpTargetTab in 0..2) {
            pagerState.animateScrollToPage(jumpTargetTab)
        }
        when (jumpTargetSection) {
            "statusbar" -> isStatusBarExpanded = true
            "telemetry" -> isTelemetryExpanded = true
            "volumekeys" -> isVolumeKeysExpanded = true
            "backtap" -> isBackTapExpanded = true
            "refueling" -> isRefuelingExpanded = true
            "backup" -> isBackupExpanded = true
            "wings" -> {
                if (jumpTargetTab == 0) isLeftCenterExpanded = true
                else isCenterExpanded = true
            }
        }
    }

    // Auto-hide / auto-show preview on tab swipe — drop(1) skips initial composition
    // so the previously-saved state is NOT overwritten the moment settings opens.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1) // only react to actual user swipes, not the initial page value
            .collect { page ->
                val leftActive = page == 0 && (
                    isLeftCenterExpanded ||
                    (if (isLeftFlankUnified) isLeftUnifiedExpanded else isLeftTopExpanded || isLeftBottomExpanded)
                )
                val canopyActive = page == 1 && isStatusBarExpanded
                val rightActive = page == 2 && (
                    isCenterExpanded ||
                    (if (isRightFlankUnified) isRightUnifiedExpanded else isTopExpanded || isBottomExpanded)
                )
                prefs.edit()
                    .putBoolean("pref_sidebar_left_preview", leftActive)
                    .putBoolean("pref_statusbar_preview", canopyActive)
                    .putBoolean("pref_sidebar_preview", rightActive)
                    .apply()
                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
            }
    }

    val leftCustomVectors = listOf(
        // — Tap —
        "TAP" to ("Tap" to ArrowDirection.TAP),
        // — Swipe —
        "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
        "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
        "SWIPE_RIGHT" to ("Swipe Inward" to ArrowDirection.SWIPE_RIGHT),
        // — Rebound —
        "SWIPE_UP_DOWN" to ("Rebound Up" to ArrowDirection.SWIPE_UP_DOWN),
        "SWIPE_DOWN_UP" to ("Rebound Down" to ArrowDirection.SWIPE_DOWN_UP),
        "SWIPE_RIGHT_BACK" to ("Rebound Inward" to ArrowDirection.RIGHT_BACK),
        // — Two-Step —
        "SWIPE_UP_RIGHT" to ("Two-Step: Up → Inward" to ArrowDirection.SWIPE_UP_RIGHT),
        "SWIPE_DOWN_RIGHT" to ("Two-Step: Down → Inward" to ArrowDirection.SWIPE_DOWN_RIGHT),
        "SWIPE_RIGHT_UP" to ("Two-Step: Inward → Up" to ArrowDirection.RIGHT_UP),
        "SWIPE_RIGHT_DOWN" to ("Two-Step: Inward → Down" to ArrowDirection.RIGHT_DOWN)
    )

    val rightCustomVectors = listOf(
        // — Tap —
        "TAP" to ("Tap" to ArrowDirection.TAP),
        // — Swipe —
        "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
        "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
        "SWIPE_LEFT" to ("Swipe Inward" to ArrowDirection.SWIPE_LEFT),
        // — Rebound —
        "SWIPE_UP_DOWN" to ("Rebound Up" to ArrowDirection.SWIPE_UP_DOWN),
        "SWIPE_DOWN_UP" to ("Rebound Down" to ArrowDirection.SWIPE_DOWN_UP),
        "SWIPE_LEFT_BACK" to ("Rebound Inward" to ArrowDirection.LEFT_BACK),
        // — Two-Step —
        "SWIPE_UP_LEFT" to ("Two-Step: Up → Inward" to ArrowDirection.SWIPE_UP_LEFT),
        "SWIPE_DOWN_LEFT" to ("Two-Step: Down → Inward" to ArrowDirection.SWIPE_DOWN_LEFT),
        "SWIPE_LEFT_UP" to ("Two-Step: Inward → Up" to ArrowDirection.LEFT_UP),
        "SWIPE_LEFT_DOWN" to ("Two-Step: Inward → Down" to ArrowDirection.LEFT_DOWN)
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        
        // Balanced, Optically Centered 3-Tab Navigator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabTitles = listOf("LEFT DEFLECTOR", "HUD STRIP", "RIGHT DEFLECTOR")
            tabTitles.forEachIndexed { index, tabTitle ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                        .padding(vertical = 10.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        // High-Performance Swipable Pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 0
        ) { pageIndex ->
            when (pageIndex) {
                // PAGE 0: PORT (LEFT) DEFLECTOR WING
                0 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
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
                        }

                        // Left Astrogation Core Zone
                        item {
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
                                    CollapsibleSubSection(
                                        title = "📐 Touch Vector Geometry & Position",
                                        subtitle = "Wing span, touch reach, offset & stealth glow",
                                        isExpanded = isLeftCenterGeoExpanded,
                                        onToggle = {
                                            isLeftCenterGeoExpanded = !isLeftCenterGeoExpanded
                                            prefs.edit().putBoolean("pref_sub_geo_left_center", isLeftCenterGeoExpanded).apply()
                                        }
                                    ) {
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_height", "", "Wing Span (Height)", 50, 1000, 10, 400)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                    }
                                }
                            }
                        }

                        if (isLeftFlankUnified) {
                            item {
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
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Upper & Lower Wing Geometry",
                                            subtitle = "Independent height, touch reach & stealth glow",
                                            isExpanded = isLeftUnifiedGeoExpanded,
                                            onToggle = {
                                                isLeftUnifiedGeoExpanded = !isLeftUnifiedGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_left_unified", isLeftUnifiedGeoExpanded).apply()
                                            }
                                        ) {
                                            Text("📐 UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Upper Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("📐 LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Lower Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Dual Inward Scrubber Controls",
                                            subtitle = "Independent upper & lower half scrubbers",
                                            isExpanded = isLeftUnifiedScrubExpanded,
                                            onToggle = {
                                                isLeftUnifiedScrubExpanded = !isLeftUnifiedScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_left_unified", isLeftUnifiedScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Unified Gesture Matrix",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isLeftUnifiedGesturesExpanded,
                                            onToggle = {
                                                isLeftUnifiedGesturesExpanded = !isLeftUnifiedGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_left_unified", isLeftUnifiedGesturesExpanded).apply()
                                            }
                                        ) {
                                            leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                val (vectorTitle, arrowEnum) = pairInfo
                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Separate Upper Zone
                            item {
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
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Touch Vector Geometry & Position",
                                            subtitle = "Upper wing span, touch reach & stealth glow",
                                            isExpanded = isLeftTopGeoExpanded,
                                            onToggle = {
                                                isLeftTopGeoExpanded = !isLeftTopGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_left_top", isLeftTopGeoExpanded).apply()
                                            }
                                        ) {
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Inward Scrubbing Control",
                                            subtitle = "Upper vector inward sweep scrubber",
                                            isExpanded = isLeftTopScrubExpanded,
                                            onToggle = {
                                                isLeftTopScrubExpanded = !isLeftTopScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_left_top", isLeftTopScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Gesture Actions & Macro Mappings",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isLeftTopGesturesExpanded,
                                            onToggle = {
                                                isLeftTopGesturesExpanded = !isLeftTopGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_left_top", isLeftTopGesturesExpanded).apply()
                                            }
                                        ) {
                                            leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                val (vectorTitle, arrowEnum) = pairInfo
                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_TOP_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_TOP_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                            }
                                        }
                                    }
                                }
                            }

                            // Separate Lower Zone
                            item {
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
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Touch Vector Geometry & Position",
                                            subtitle = "Lower wing span, touch reach & stealth glow",
                                            isExpanded = isLeftBottomGeoExpanded,
                                            onToggle = {
                                                isLeftBottomGeoExpanded = !isLeftBottomGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_left_bottom", isLeftBottomGeoExpanded).apply()
                                            }
                                        ) {
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Inward Scrubbing Control",
                                            subtitle = "Lower vector inward sweep scrubber",
                                            isExpanded = isLeftBottomScrubExpanded,
                                            onToggle = {
                                                isLeftBottomScrubExpanded = !isLeftBottomScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_left_bottom", isLeftBottomScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Gesture Actions & Macro Mappings",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isLeftBottomGesturesExpanded,
                                            onToggle = {
                                                isLeftBottomGesturesExpanded = !isLeftBottomGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_left_bottom", isLeftBottomGesturesExpanded).apply()
                                            }
                                        ) {
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
                    }
                }

                // PAGE 1: AVIONICS DECK (STATUS BAR / CANOPY)
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
                            CompactAccordionSection(
                                title = "Status Bar Canopy — Sensor Deck",
                                isExpanded = isStatusBarExpanded,
                                onToggle = {
                                    isStatusBarExpanded = !isStatusBarExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_statusbar_expanded", isStatusBarExpanded)
                                        .putBoolean("pref_statusbar_preview", isStatusBarExpanded)
                                        .apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrefToggleRow(
                                        title = "Enable Canopy Gestures",
                                        subtitle = "Top-edge gesture detection & sensor scrub bar",
                                        isChecked = prefs.getBoolean("pref_statusbar_enabled", true),
                                        onCheckedChange = { checked ->
                                            prefs.edit().putBoolean("pref_statusbar_enabled", checked).apply()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    )

                                    // 1. Geometry & Sensitivity
                                    CollapsibleSubSection(
                                        title = "📐 Touch Vector Geometry & Sensitivity",
                                        subtitle = "Span, thickness, offset & idle glow",
                                        isExpanded = isStatusBarGeoExpanded,
                                        onToggle = {
                                            isStatusBarGeoExpanded = !isStatusBarGeoExpanded
                                            prefs.edit().putBoolean("pref_sub_geo_statusbar", isStatusBarGeoExpanded).apply()
                                        }
                                    ) {
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_span", "", "Canopy Span (≥1000 = full width)", 50, 1080, 10, 1080)
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_thickness", "", "Canopy Thickness (Height)", 20, 52, 2, 48)
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_x", "", "Horizontal Offset (X Axis)", -300, 300, 5, 0)
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_y", "", "Vertical Offset (Y Axis)", -100, 200, 5, 0)
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_sensitivity", "", "Touch Vector Sensitivity", 10, 100, 5, 40)
                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                    }

                                    // 2. Scrubbers
                                    CollapsibleSubSection(
                                        title = "🎛️ Pull-Down Scrubbing Control",
                                        subtitle = "Horizontal scrubbing selector for sensor bar",
                                        isExpanded = isStatusBarScrubExpanded,
                                        onToggle = {
                                            isStatusBarScrubExpanded = !isStatusBarScrubExpanded
                                            prefs.edit().putBoolean("pref_sub_scrub_statusbar", isStatusBarScrubExpanded).apply()
                                        }
                                    ) {
                                        GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_STATUSBAR_SCRUBBING", "Pull Down & Scrub Across", listOf("none", "system:screen_timeout", "system:brightness", "system:volume"), tokenLabelCache)
                                    }

                                    // 3. Gestures — SWIPE_DOWN excluded: conflicts with Android notification shade
                                    CollapsibleSubSection(
                                        title = "⚡ Canopy Gesture Actions & Macros",
                                        subtitle = "Tap, double-tap, left & right swipes with Hold Modifiers",
                                        isExpanded = isStatusBarGesturesExpanded,
                                        onToggle = {
                                            isStatusBarGesturesExpanded = !isStatusBarGesturesExpanded
                                            prefs.edit().putBoolean("pref_sub_gestures_statusbar", isStatusBarGesturesExpanded).apply()
                                        }
                                    ) {
                                        val statusVectors = listOf(
                                            // — Tap —
                                            "TAP" to ("Tap" to ArrowDirection.TAP),
                                            "DOUBLE_TAP" to ("Tap (Double)" to ArrowDirection.DOUBLE_TAP),
                                            // — Swipe —
                                            "SWIPE_LEFT" to ("Swipe Left" to ArrowDirection.SWIPE_LEFT),
                                            "SWIPE_RIGHT" to ("Swipe Right" to ArrowDirection.SWIPE_RIGHT),
                                            // — Rebound —
                                            "SWIPE_LEFT_BACK" to ("Rebound Left" to ArrowDirection.LEFT_BACK),
                                            "SWIPE_RIGHT_BACK" to ("Rebound Right" to ArrowDirection.RIGHT_BACK)
                                        )

                                        statusVectors.forEach { (vectorKey, pairInfo) ->
                                            val (vectorTitle, arrowEnum) = pairInfo
                                            GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                            GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                        }
                                    }
                                }
                            }
                        }

                        // Status Bar & Notch Telemetry
                        item {
                            CompactAccordionSection(
                                title = "Status Bar & Notch Telemetry — Live HUD Indicators",
                                isExpanded = isTelemetryExpanded,
                                onToggle = {
                                    isTelemetryExpanded = !isTelemetryExpanded
                                    prefs.edit().putBoolean("pref_section_telemetry_expanded", isTelemetryExpanded).apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val isNotifAccessGranted = remember(isTelemetryExpanded) {
                                        val pkgName = context.packageName
                                        val flat = android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                                        flat?.contains(pkgName) == true || com.sbf.lightspeed.system.LightspeedNotificationListener.instance != null
                                    }

                                    if (!isNotifAccessGranted) {
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable { showNotificationAccessDialog = true }
                                                .padding(vertical = 2.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                                    Text("Tap here to grant permission in Android Settings so Lightspeed can read download progress and media metadata.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                                                }
                                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
                                            }
                                        }
                                    }

                                    // Downloads Telemetry Selector
                                    val currentDl = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
                                    var isDlDropdownOpen by remember { mutableStateOf(false) }
                                    val routingOptions = listOf(
                                        "none" to "None (Disabled)",
                                        "top_line" to "Top-Edge Line",
                                        "notch_pill" to "Camera Cutout Notch Pill",
                                        "both" to "Both (Top Line & Notch Pill)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("DOWNLOADS TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isDlDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(routingOptions.firstOrNull { it.first == currentDl }?.second ?: "Camera Cutout Notch Pill", color = Color.White)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isDlDropdownOpen,
                                                onDismissRequest = { isDlDropdownOpen = false }
                                            ) {
                                                routingOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isDlDropdownOpen = false
                                                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, key).apply()
                                                            if (key != "none" && !isNotifAccessGranted) {
                                                                showNotificationAccessDialog = true
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Media Telemetry Selector
                                    val currentMedia = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
                                    var isMediaDropdownOpen by remember { mutableStateOf(false) }

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("MEDIA PLAYBACK TELEMETRY ROUTING", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isMediaDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(routingOptions.firstOrNull { it.first == currentMedia }?.second ?: "None (Disabled)", color = Color.White)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isMediaDropdownOpen,
                                                onDismissRequest = { isMediaDropdownOpen = false }
                                            ) {
                                                routingOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isMediaDropdownOpen = false
                                                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, key).apply()
                                                            if (key != "none" && !isNotifAccessGranted) {
                                                                showNotificationAccessDialog = true
                                                            }
                                                            onRefreshNeeded()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Camera Cutout Notch Calibration
                                    CollapsibleSubSection(
                                        title = "📐 Camera Cutout Notch Calibration",
                                        subtitle = "Live alignment, offsets & expansion",
                                        isExpanded = isNotchCalibExpanded,
                                        onToggle = {
                                            isNotchCalibExpanded = !isNotchCalibExpanded
                                            prefs.edit().putBoolean("pref_sub_notch_calib", isNotchCalibExpanded).apply()
                                        }
                                    ) {
                                        PrefToggleRow(
                                            title = "Test Beacon Live Alignment",
                                            subtitle = "Projects a persistent illuminated liquid-glass pill around the cutout for live alignment.",
                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false),
                                            onCheckedChange = {
                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, it).apply()
                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                onRefreshNeeded()
                                            }
                                        )
                                        val currentCapsuleLayout = prefs.getString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, "unified_right") ?: "unified_right"
                                        var isCapsuleDropdownOpen by remember { mutableStateOf(false) }
                                        val capsuleOptions = listOf(
                                            "unified_right" to "Unified Right (Compact)",
                                            "dual_wing" to "Dual-Wing Bridge",
                                            "unified_left" to "Unified Left"
                                        )

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Text("PILL CAPSULE LAYOUT MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box {
                                                OutlinedButton(
                                                    onClick = { isCapsuleDropdownOpen = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(capsuleOptions.firstOrNull { it.first == currentCapsuleLayout }?.second ?: "Unified Right (Compact)", color = Color.White, fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = isCapsuleDropdownOpen,
                                                    onDismissRequest = { isCapsuleDropdownOpen = false }
                                                ) {
                                                    capsuleOptions.forEach { (key, label) ->
                                                        DropdownMenuItem(
                                                            text = { Text(label) },
                                                            onClick = {
                                                                isCapsuleDropdownOpen = false
                                                                prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_CAPSULE_LAYOUT, key).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, "", "Pill Snugness Padding (0 to 8dp)", 0, 8, 1, 2)
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_OFFSET_Y, "", "Vertical Offset Y (-30 to +30dp)", -30, 30, 1, 0)
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_OFFSET_X, "", "Horizontal Offset X (-30 to +30dp)", -30, 30, 1, 0)
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, "", "Pill Expansion Width (0 to 80dp)", 0, 80, 2, 0)
                                    }

                                    // OEM Dynamic Pill Advisory Card
                                    val oemFeatureName = remember { OemNotchDetector.getDetectedFeatureName() }
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = "Your device may have $oemFeatureName enabled. Disable it in system settings to prevent overlapping indicators.",
                                                    fontSize = 12.sp,
                                                    color = Color.White.copy(alpha = 0.9f),
                                                    lineHeight = 16.sp,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            Button(
                                                onClick = { OemNotchDetector.openSearch(context) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = "Configure $oemFeatureName in Settings",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Title Marquee & Truncation Engine
                                    CollapsibleSubSection(
                                        title = "📜 Title Overflow & Marquee Engine",
                                        subtitle = "Scroll mode, loop count & ellipsis anchor",
                                        isExpanded = isMarqueeSubSectionExpanded,
                                        onToggle = {
                                            isMarqueeSubSectionExpanded = !isMarqueeSubSectionExpanded
                                            prefs.edit().putBoolean("pref_sub_notch_marquee", isMarqueeSubSectionExpanded).apply()
                                        }
                                    ) {
                                        val currentScrollMode = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_SCROLL_MODE, "loop_2x") ?: "loop_2x"
                                        var isScrollDropdownOpen by remember { mutableStateOf(false) }
                                        val scrollOptions = listOf(
                                            "infinite" to "Infinite Marquee Loop",
                                            "loop_2x" to "Loop 2x then Settle",
                                            "loop_1x" to "Loop 1x then Settle",
                                            "static" to "Static (No Scroll / Ellipsize Immediately)"
                                        )

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Text("TEXT SCROLL BEHAVIOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box {
                                                OutlinedButton(
                                                    onClick = { isScrollDropdownOpen = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(scrollOptions.firstOrNull { it.first == currentScrollMode }?.second ?: "Loop 2x then Settle", color = Color.White, fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = isScrollDropdownOpen,
                                                    onDismissRequest = { isScrollDropdownOpen = false }
                                                ) {
                                                    scrollOptions.forEach { (key, label) ->
                                                        DropdownMenuItem(
                                                            text = { Text(label) },
                                                            onClick = {
                                                                isScrollDropdownOpen = false
                                                                prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_TEXT_SCROLL_MODE, key).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        val currentTruncateAnchor = prefs.getString(LightspeedPreferences.KEY_NOTCH_TEXT_TRUNCATE_ANCHOR, "tail") ?: "tail"
                                        var isTruncateDropdownOpen by remember { mutableStateOf(false) }
                                        val truncateOptions = listOf(
                                            "tail" to "Tail Ellipsis (Track Name...)",
                                            "head" to "Head Ellipsis (...Track Name)",
                                            "core" to "Core Ellipsis (Tra...Name)"
                                        )

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Text("ELLIPSIS TRUNCATION ANCHOR", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box {
                                                OutlinedButton(
                                                    onClick = { isTruncateDropdownOpen = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(truncateOptions.firstOrNull { it.first == currentTruncateAnchor }?.second ?: "Tail Ellipsis", color = Color.White, fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = isTruncateDropdownOpen,
                                                    onDismissRequest = { isTruncateDropdownOpen = false }
                                                ) {
                                                    truncateOptions.forEach { (key, label) ->
                                                        DropdownMenuItem(
                                                            text = { Text(label) },
                                                            onClick = {
                                                                isTruncateDropdownOpen = false
                                                                prefs.edit().putString(LightspeedPreferences.KEY_NOTCH_TEXT_TRUNCATE_ANCHOR, key).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Display Orientation & Lockscreen Suppression
                                    CollapsibleSubSection(
                                        title = "🔄 Orientation & Ambient Suppression",
                                        subtitle = "360° rotation policy & OEM dock protection",
                                        isExpanded = isOrientationSubSectionExpanded,
                                        onToggle = {
                                            isOrientationSubSectionExpanded = !isOrientationSubSectionExpanded
                                            prefs.edit().putBoolean("pref_sub_orientation", isOrientationSubSectionExpanded).apply()
                                        }
                                    ) {
                                        val currentOrientationPolicy = prefs.getString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, "adaptive") ?: "adaptive"
                                        var isOrientationDropdownOpen by remember { mutableStateOf(false) }
                                        val orientationOptions = listOf(
                                            "adaptive" to "Adaptive (360° Follows All Rotations)",
                                            "portrait_only" to "Portrait Only (Auto-Hide in Landscape)"
                                        )

                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                            Text("ORIENTATION OVERLAY POLICY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box {
                                                OutlinedButton(
                                                    onClick = { isOrientationDropdownOpen = true },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(orientationOptions.firstOrNull { it.first == currentOrientationPolicy }?.second ?: "Adaptive (360°)", color = Color.White, fontSize = 12.sp)
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                    }
                                                }
                                                DropdownMenu(
                                                    expanded = isOrientationDropdownOpen,
                                                    onDismissRequest = { isOrientationDropdownOpen = false }
                                                ) {
                                                    orientationOptions.forEach { (key, label) ->
                                                        DropdownMenuItem(
                                                            text = { Text(label) },
                                                            onClick = {
                                                                isOrientationDropdownOpen = false
                                                                prefs.edit().putString(LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY, key).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        PrefToggleRow(
                                            title = "Suppress on Lock Screen & OEM Screensavers",
                                            subtitle = "Automatically hides Orbital Capsule and HUD Strip when device is locked or running OEM ambient dock.",
                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK, true),
                                            onCheckedChange = {
                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK, it).apply()
                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                onRefreshNeeded()
                                            }
                                        )

                                        PrefToggleRow(
                                            title = "Smart Orientation Context Guardrails",
                                            subtitle = "Forces strict portrait during in-progress phone/VoIP calls, and suppresses landscape rotation glitches on Default Launcher & Lock Screen (auto-reverts when leaving protected apps).",
                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_ORIENTATION_CONTEXT_GUARD_ENABLED, true),
                                            onCheckedChange = {
                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_ORIENTATION_CONTEXT_GUARD_ENABLED, it).apply()
                                                onRefreshNeeded()
                                            }
                                        )

                                        // Quick Rotation Triggers
                                        Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("SYSTEM ROTATION ENGINE CONTROLS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { com.sbf.lightspeed.system.LightspeedOrientationManager.toggleRotation(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Text("Toggle Auto", fontSize = 11.sp, color = Color.White)
                                                }
                                                Button(
                                                    onClick = { com.sbf.lightspeed.system.LightspeedOrientationManager.forcePortrait(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Text("Force 0°", fontSize = 11.sp, color = Color.White)
                                                }
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Button(
                                                    onClick = { com.sbf.lightspeed.system.LightspeedOrientationManager.forceSensor360(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Text("Sensor 360°", fontSize = 11.sp, color = Color.White)
                                                }
                                                Button(
                                                    onClick = { com.sbf.lightspeed.system.LightspeedOrientationManager.setSensorPortrait(context) },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                                ) {
                                                    Text("Portrait Only", fontSize = 11.sp, color = Color.White)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Hardware Volume Key Matrix
                        item {
                            CompactAccordionSection(
                                title = "Hardware Volume Key Matrix — Tactical Triggers",
                                isExpanded = isVolumeKeysExpanded,
                                onToggle = {
                                    isVolumeKeysExpanded = !isVolumeKeysExpanded
                                    prefs.edit().putBoolean("pref_section_volumekeys_expanded", isVolumeKeysExpanded).apply()
                                    if (isVolumeKeysExpanded && !prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, false)) {
                                        showOemShieldDialog = true
                                    }
                                },
                                headerTrailing = {
                                    IconButton(
                                        onClick = { showOemShieldDialog = true },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = "OEM Compatibility Shield",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrefToggleRow(
                                        title = "Enable Volume Key Gestures",
                                        subtitle = "Low-latency hardware chording, sequences & hold triggers",
                                        isChecked = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, true),
                                        onCheckedChange = { checked ->
                                            prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, checked).apply()
                                            onRefreshNeeded()
                                        }
                                    )

                                    // 3-Stage Volume Suppression Profile Selector
                                    val currentProfile = remember(prefs.getString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, null)) {
                                        LightspeedKeyEngine.getSuppressionProfile(context)
                                    }
                                    var selectedProfile by remember { mutableStateOf(currentProfile) }

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text("VOLUME SUPPRESSION PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)

                                        val profileOptions = listOf(
                                            Triple("instant_reflex", "Instant Reflex (0ms)", "Raw pass-through. Volume steps on ACTION_DOWN; chords execute immediately with potential single volume tick leak."),
                                            Triple("balanced_holds", "Balanced Holds", "Suppresses volume jumps during long-press holds; chords execute immediately."),
                                            Triple("total_clean", "Total Clean (150ms Buffer)", "Consumes first key event and waits up to 150ms. If second chord key is pressed, fires chord with 0 volume changes. If released without chord/hold, steps volume manually.")
                                        )

                                        profileOptions.forEach { (profileKey, title, desc) ->
                                            val isSelected = selectedProfile == profileKey
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        selectedProfile = profileKey
                                                        prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                                        onRefreshNeeded()
                                                    },
                                                shape = RoundedCornerShape(12.dp),
                                                colors = CardDefaults.cardColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                                                ),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                                                )
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            selectedProfile = profileKey
                                                            prefs.edit().putString(LightspeedPreferences.KEY_VOLUME_SUPPRESSION_PROFILE, profileKey).apply()
                                                            onRefreshNeeded()
                                                        },
                                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f), lineHeight = 14.sp)
                                                    }
                                                }
                                            }
                                        }

                                        if (selectedProfile == "total_clean") {
                                            Card(
                                                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                shape = RoundedCornerShape(10.dp),
                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                                            ) {
                                                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Adds a 150ms buffer window before single volume taps register to guarantee zero volume leaks on chords.",
                                                        fontSize = 11.sp,
                                                        color = Color.White.copy(alpha = 0.9f),
                                                        lineHeight = 14.sp
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Key Hold Auto-Repeat
                                    val autoRepeatEnabled = prefs.getBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, false)
                                    PrefToggleRow(
                                        title = "Hardware Key Hold Auto-Repeat",
                                        subtitle = "Continuous auto-repeat for hold actions while volume buttons remain pressed",
                                        isChecked = autoRepeatEnabled,
                                        onCheckedChange = {
                                            prefs.edit().putBoolean(LightspeedPreferences.KEY_KEY_HOLD_AUTO_REPEAT, it).apply()
                                            onRefreshNeeded()
                                        }
                                    )

                                    if (autoRepeatEnabled) {
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_KEY_REPEAT_INTERVAL_MS, "", "Auto-Repeat Interval (ms)", 100, 500, 25, 200)
                                    }

                                    // Rocker Advisory Banner
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = "Rocker Notice: Devices with a single physical rocker bar may mechanically lever switches when pressing both ends. If chords misfire, Sequential gestures are recommended for 100% reliability.",
                                                fontSize = 11.5.sp,
                                                color = Color.White.copy(alpha = 0.9f),
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }

                                    val volumeGestures = listOf(
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOL_UP_LONG_PRESS, "Volume Up Long Press (~400ms)", Pair(ArrowDirection.SWIPE_UP, true)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS, "Volume Down Long Press (~400ms)", Pair(ArrowDirection.SWIPE_DOWN, true)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP, "Hold Vol Down + Tap Vol Up", Pair(ArrowDirection.SWIPE_UP, false)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP, "Hold Vol Up + Tap Vol Down", Pair(ArrowDirection.SWIPE_DOWN, false)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN, "Sequence: Vol Up → Vol Down (<300ms)", Pair(ArrowDirection.SWIPE_UP_DOWN, false)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP, "Sequence: Vol Down → Vol Up (<300ms)", Pair(ArrowDirection.SWIPE_DOWN_UP, false)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD, "Tap Vol Down → Hold Vol Up (~400ms)", Pair(ArrowDirection.SWIPE_UP, true)),
                                        Triple(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD, "Tap Vol Up → Hold Vol Down (~400ms)", Pair(ArrowDirection.SWIPE_DOWN, true))
                                    )

                                    volumeGestures.forEach { (prefKey, title, visual) ->
                                        val (arrow, isHold) = visual
                                        GestureMappingRow(
                                            context = context,
                                            prefs = prefs,
                                            direction = arrow,
                                            isHold = isHold,
                                            keyResName = prefKey,
                                            defaultTitle = title,
                                            options = dynamicActionTokens,
                                            labelCache = tokenLabelCache,
                                            showMediaQuickAccess = true
                                        )
                                    }
                                }
                            }
                        }

                        // Power Button Assist Remap
                        item {
                            CompactAccordionSection(
                                title = "Power Button Assist Remap — Tactical Remap",
                                isExpanded = isPowerAssistExpanded,
                                onToggle = {
                                    isPowerAssistExpanded = !isPowerAssistExpanded
                                    prefs.edit().putBoolean("pref_section_powerassist_expanded", isPowerAssistExpanded).apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "Remap Android Digital Assistant (Hold Power Button / Long-Press Home / Corner Swipe) to execute any tactical cockpit action instantly without opening Google Assistant.",
                                        fontSize = 12.sp,
                                        color = Color.LightGray.copy(alpha = 0.85f),
                                        lineHeight = 16.sp
                                    )

                                    GestureMappingRow(
                                        context = context,
                                        prefs = prefs,
                                        direction = ArrowDirection.TAP,
                                        isHold = true,
                                        keyResName = LightspeedPreferences.KEY_POWER_LONG_PRESS_ACTION,
                                        defaultTitle = "Power Long-Press Assist Action",
                                        options = dynamicActionTokens,
                                        labelCache = tokenLabelCache,
                                        showMediaQuickAccess = true
                                    )

                                    Button(
                                        onClick = {
                                            try {
                                                val intent = Intent(android.provider.Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                try {
                                                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (_: Exception) {}
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.SettingsVoice, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Set Lightspeed as Default Digital Assistant", fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // Back Tap Sensor Matrix
                        item {
                            CompactAccordionSection(
                                title = "Back Tap Sensor Matrix — Linear Acceleration Triggers",
                                isExpanded = isBackTapExpanded,
                                onToggle = {
                                    isBackTapExpanded = !isBackTapExpanded
                                    prefs.edit().putBoolean("pref_section_backtap_expanded", isBackTapExpanded).apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrefToggleRow(
                                        title = "Enable Back Tap Gestures",
                                        subtitle = "Detect double and triple taps on the back of your device",
                                        isChecked = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_ENABLED, false),
                                        onCheckedChange = { checked ->
                                            prefs.edit().putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_ENABLED, checked).apply()
                                            onRefreshNeeded()
                                        }
                                    )

                                    // Activation Scope Dropdown
                                    val currentScope = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on") ?: "screen_on"
                                    var isScopeDropdownOpen by remember { mutableStateOf(false) }
                                    val scopeOptions = listOf(
                                        "screen_on" to "Screen On Only",
                                        "screen_off" to "Screen Off (⚡ High Drain)",
                                        "always" to "Always (⚡ High Drain)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("ACTIVATION SCOPE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isScopeDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(scopeOptions.firstOrNull { it.first == currentScope }?.second ?: "Screen On Only", color = Color.White)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isScopeDropdownOpen,
                                                onDismissRequest = { isScopeDropdownOpen = false }
                                            ) {
                                                scopeOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isScopeDropdownOpen = false
                                                            if (key == "screen_off" || key == "always") {
                                                                pendingBackTapScope = key
                                                                showBatteryWarningDialog = true
                                                            } else {
                                                                prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, key).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Smart Battery Failsafe Banner
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "⚡ Smart Battery Failsafe Active: Screen-off sensor is automatically paused and wake lock released if battery drops ≤ 20% or Battery Saver is on.",
                                                fontSize = 11.sp,
                                                color = Color.LightGray.copy(alpha = 0.85f),
                                                lineHeight = 14.sp
                                            )
                                        }
                                    }

                                    // Live Impulse Calibration Meter
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (thresholdCrossedFlash) Color(0xFF00E676).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.2.dp,
                                            if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Speed,
                                                        contentDescription = null,
                                                        tint = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "LIVE IMPULSE METER",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary,
                                                        letterSpacing = 1.sp
                                                    )
                                                }
                                                Text(
                                                    text = String.format(Locale.US, "%.1f m/s²", currentZImpulse),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (thresholdCrossedFlash) Color(0xFF00E676) else Color.White
                                                )
                                            }

                                            // Gauge Bar with Vertical Threshold Marker
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(20.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.08f))
                                            ) {
                                                val fraction = (currentZImpulse / 20f).coerceIn(0f, 1f)
                                                val threshFraction = (currentThreshold / 20f).coerceIn(0f, 1f)

                                                // Active Fill
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(fraction)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                                                        )
                                                )

                                                // Threshold Marker
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(threshFraction)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.CenterEnd)
                                                            .width(2.5.dp)
                                                            .fillMaxHeight()
                                                            .background(Color.White)
                                                    )
                                                }
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("0.0", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                                Text(
                                                    if (thresholdCrossedFlash) "✓ THRESHOLD BREACHED" else String.format(Locale.US, "Threshold: %.1f m/s²", currentThreshold),
                                                    fontSize = 10.5.sp,
                                                    fontWeight = if (thresholdCrossedFlash) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary
                                                )
                                                Text("20.0 m/s²", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                            }
                                        }
                                    }

                                    // Configurable Threshold Slider
                                    PrefFloatDottedSliderRow(
                                        context = context,
                                        prefs = prefs,
                                        keyResName = LightspeedPreferences.KEY_BACK_TAP_THRESHOLD,
                                        title = "Strike Force Threshold",
                                        minVal = 3.0f,
                                        maxVal = 18.0f,
                                        step = 0.5f,
                                        defaultVal = 7.5f,
                                        unit = "m/s²",
                                        onValueChanged = {
                                            currentThreshold = it
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                        }
                                    )

                                    // Gesture Mappings
                                    GestureMappingRow(
                                        context = context,
                                        prefs = prefs,
                                        direction = ArrowDirection.DOUBLE_TAP,
                                        isHold = false,
                                        keyResName = com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_DOUBLE,
                                        defaultTitle = "Double Back Tap (250-450ms)",
                                        options = dynamicActionTokens,
                                        labelCache = tokenLabelCache,
                                        showMediaQuickAccess = true
                                    )

                                    GestureMappingRow(
                                        context = context,
                                        prefs = prefs,
                                        direction = ArrowDirection.TAP,
                                        isHold = true,
                                        keyResName = com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_TRIPLE,
                                        defaultTitle = "Triple Back Tap (≤ 700ms)",
                                        options = dynamicActionTokens,
                                        labelCache = tokenLabelCache,
                                        showMediaQuickAccess = true
                                    )
                                }
                            }
                        }

                        // Refueling Bay: Ambient Charging & Cryo Dashboard
                        item {
                            CompactAccordionSection(
                                title = "Refueling Bay — Ambient Charging & Cryo Dashboard",
                                isExpanded = isRefuelingExpanded,
                                onToggle = {
                                    isRefuelingExpanded = !isRefuelingExpanded
                                    prefs.edit().putBoolean("pref_section_refueling_expanded", isRefuelingExpanded).apply()
                                }
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Trigger Dropdown
                                    val currentTrigger = prefs.getString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, "disabled") ?: "disabled"
                                    var isTriggerDropdownOpen by remember { mutableStateOf(false) }
                                    val triggerOptions = listOf(
                                        "disabled" to "Disabled (Manual Launch Only)",
                                        "charging_screen_off" to "Screen-Off While Charging / Plugged While Locked",
                                        "charging_dock_landscape" to "Charging in Landscape Dock Orientation",
                                        "screensaver_only" to "Android Screensaver (DreamService Only)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("AUTO-LAUNCH CHARGING TRIGGER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isTriggerDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        triggerOptions.firstOrNull {
                                                            it.first == currentTrigger ||
                                                            (it.first == "charging_dock_landscape" && currentTrigger == "landscape_charging") ||
                                                            (it.first == "charging_screen_off" && currentTrigger == "always_charging")
                                                        }?.second ?: "Disabled",
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isTriggerDropdownOpen,
                                                onDismissRequest = { isTriggerDropdownOpen = false }
                                            ) {
                                                triggerOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isTriggerDropdownOpen = false
                                                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BAY_TRIGGER, key).apply()
                                                            onRefreshNeeded()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Widget Layout Mode Selector
                                    val currentWidgetLayout = prefs.getString(LightspeedPreferences.KEY_REFUELING_WIDGET_LAYOUT, "smart_stack") ?: "smart_stack"
                                    var isWidgetLayoutDropdownOpen by remember { mutableStateOf(false) }
                                    val widgetLayoutOptions = listOf(
                                        "smart_stack" to "Smart Stack (Swipeable Pager)",
                                        "adaptive_grid" to "Adaptive Grid (1 Col Portrait / 2 Col Landscape)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("MULTI-WIDGET ENGINE LAYOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isWidgetLayoutDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(widgetLayoutOptions.firstOrNull { it.first == currentWidgetLayout }?.second ?: "Smart Stack (Swipeable Pager)", color = Color.White, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isWidgetLayoutDropdownOpen,
                                                onDismissRequest = { isWidgetLayoutDropdownOpen = false }
                                            ) {
                                                widgetLayoutOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isWidgetLayoutDropdownOpen = false
                                                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_WIDGET_LAYOUT, key).apply()
                                                            onRefreshNeeded()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Battery Arc Visual Style Selector
                                    val currentArcStyle = prefs.getString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, "halo") ?: "halo"
                                    var isArcStyleDropdownOpen by remember { mutableStateOf(false) }
                                    val arcStyleOptions = listOf(
                                        "halo" to "Halo (Continuous Neon Arc)",
                                        "reactor_ticks" to "Reactor Ticks (20 Laser Ticks + Pulse)",
                                        "dual_wings" to "Dual Wings (Symmetrical Upward Brackets)",
                                        "tachometer" to "Tachometer Cockpit (240° Cockpit Sweep)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("BATTERY ARC VISUAL STYLE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isArcStyleDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(arcStyleOptions.firstOrNull { it.first == currentArcStyle }?.second ?: "Halo", color = Color.White, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isArcStyleDropdownOpen,
                                                onDismissRequest = { isArcStyleDropdownOpen = false }
                                            ) {
                                                arcStyleOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isArcStyleDropdownOpen = false
                                                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, key).apply()
                                                            onRefreshNeeded()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // OLED Burn-In Auto-Sleep Shield Selector
                                    val currentSleepTimeout = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s"
                                    var isSleepDropdownOpen by remember { mutableStateOf(false) }
                                    val sleepTimeoutOptions = listOf(
                                        "30s" to "30 Seconds",
                                        "60s" to "60 Seconds (Default)",
                                        "3m" to "3 Minutes",
                                        "5m" to "5 Minutes",
                                        "never" to "Never (Always-On AMOLED)"
                                    )

                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                        Text("OLED BURN-IN AUTO-SLEEP SHIELD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box {
                                            OutlinedButton(
                                                onClick = { isSleepDropdownOpen = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(sleepTimeoutOptions.firstOrNull { it.first == currentSleepTimeout }?.second ?: "60 Seconds (Default)", color = Color.White, fontSize = 12.sp)
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = isSleepDropdownOpen,
                                                onDismissRequest = { isSleepDropdownOpen = false }
                                            ) {
                                                sleepTimeoutOptions.forEach { (key, label) ->
                                                    DropdownMenuItem(
                                                        text = { Text(label) },
                                                        onClick = {
                                                            isSleepDropdownOpen = false
                                                            if (key == "never") {
                                                                showAmoledWarningDialog = true
                                                            } else {
                                                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, key).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Lock Screen Curtain Mode Toggle
                                    PrefToggleRow(
                                        title = "Launch Over Lock Screen on Screen-On",
                                        subtitle = "Instantly presents Refueling Bay over Keyguard when display turns on; dismisses directly to system lock with upward swipe",
                                        isChecked = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_AS_LOCKSCREEN, false),
                                        onCheckedChange = { checked ->
                                            prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_AS_LOCKSCREEN, checked).apply()
                                            onRefreshNeeded()
                                        }
                                    )

                                    // Feature Overview Card
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Ambient Cryo Dashboard & Multi-Widget", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = Color.White)
                                            }
                                            Text(
                                                "• Lockscreen wake & launch over lock when charging / plugged\n• Orientation Sensor Reflow: adaptive Portrait & Landscape splits\n• Multi-Widget Engine: Unlimited widgets in Smart Stack or Adaptive Grid\n• Live charging wattage calculation (V × A / 1e9 W) & time-to-full\n• AMOLED burn-in protection with periodic micro-drift",
                                                fontSize = 11.5.sp,
                                                color = Color.LightGray.copy(alpha = 0.85f),
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }

                                    // Launch Test Button
                                    Button(
                                        onClick = {
                                            val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Launch Refueling Bay Dashboard", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }

                        // Cloud & Local Config Vault
                        item {
                            CompactAccordionSection(
                                title = "Cloud & Local Configuration Vault",
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
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            .clickable { exportLauncher.launch("lightspeed-backup.json") }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.CloudUpload, contentDescription = "Export", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Export Configuration (JSON)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                                            Text("Save all gesture sets, sliders, physics, and shortcuts to a standalone JSON file", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
                                        }
                                    }

                                    // Import Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                            .clickable { showImportOptionsDialog = true }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
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
                }

                // PAGE 2: STARBOARD (RIGHT) DEFLECTOR WING
                2 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        item {
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
                        }

                        // Right Astrogation Core Zone
                        item {
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
                                    CollapsibleSubSection(
                                        title = "📐 Touch Vector Geometry & Position",
                                        subtitle = "Wing span, touch reach, offset & stealth glow",
                                        isExpanded = isCenterGeoExpanded,
                                        onToggle = {
                                            isCenterGeoExpanded = !isCenterGeoExpanded
                                            prefs.edit().putBoolean("pref_sub_geo_center", isCenterGeoExpanded).apply()
                                        }
                                    ) {
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_height", "", "Wing Span (Height)", 50, 1000, 10, 400)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                    }
                                }
                            }
                        }

                        if (isRightFlankUnified) {
                            item {
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
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Upper & Lower Wing Geometry",
                                            subtitle = "Independent height, touch reach & stealth glow",
                                            isExpanded = isRightUnifiedGeoExpanded,
                                            onToggle = {
                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_unified", isRightUnifiedGeoExpanded).apply()
                                            }
                                        ) {
                                            Text("📐 UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Upper Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("📐 LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Lower Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Dual Inward Scrubber Controls",
                                            subtitle = "Independent upper & lower half scrubbers",
                                            isExpanded = isRightUnifiedScrubExpanded,
                                            onToggle = {
                                                isRightUnifiedScrubExpanded = !isRightUnifiedScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_unified", isRightUnifiedScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Unified Gesture Matrix",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isRightUnifiedGesturesExpanded,
                                            onToggle = {
                                                isRightUnifiedGesturesExpanded = !isRightUnifiedGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_unified", isRightUnifiedGesturesExpanded).apply()
                                            }
                                        ) {
                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                val (vectorTitle, arrowEnum) = pairInfo
                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Separate Upper Zone
                            item {
                                CompactAccordionSection(
                                    title = "Right Deflector Wing — Upper Vector Zone",
                                    isExpanded = isTopExpanded,
                                    onToggle = {
                                        isTopExpanded = !isTopExpanded
                                        val newTop = isTopExpanded
                                        prefs.edit()
                                            .putBoolean("pref_section_top_expanded", newTop)
                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || newTop || isBottomExpanded)
                                            .apply()
                                    }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Touch Vector Geometry & Position",
                                            subtitle = "Upper wing span, touch reach & stealth glow",
                                            isExpanded = isTopGeoExpanded,
                                            onToggle = {
                                                isTopGeoExpanded = !isTopGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_top", isTopGeoExpanded).apply()
                                            }
                                        ) {
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Inward Scrubbing Control",
                                            subtitle = "Upper vector inward sweep scrubber",
                                            isExpanded = isTopScrubExpanded,
                                            onToggle = {
                                                isTopScrubExpanded = !isTopScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_top", isTopScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Gesture Actions & Macro Mappings",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isTopGesturesExpanded,
                                            onToggle = {
                                                isTopGesturesExpanded = !isTopGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_top", isTopGesturesExpanded).apply()
                                            }
                                        ) {
                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                val (vectorTitle, arrowEnum) = pairInfo
                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_TOP_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_TOP_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                            }
                                        }
                                    }
                                }
                            }

                            // Separate Lower Zone
                            item {
                                CompactAccordionSection(
                                    title = "Right Deflector Wing — Lower Vector Zone",
                                    isExpanded = isBottomExpanded,
                                    onToggle = {
                                        isBottomExpanded = !isBottomExpanded
                                        val newBottom = isBottomExpanded
                                        prefs.edit()
                                            .putBoolean("pref_section_bottom_expanded", newBottom)
                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || newBottom)
                                            .apply()
                                    }
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        // 1. Geometry
                                        CollapsibleSubSection(
                                            title = "📐 Touch Vector Geometry & Position",
                                            subtitle = "Lower wing span, touch reach & stealth glow",
                                            isExpanded = isBottomGeoExpanded,
                                            onToggle = {
                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                prefs.edit().putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded).apply()
                                            }
                                        ) {
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Wing Span (Height)", 50, 600, 10, 200)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                        }

                                        // 2. Scrubbers
                                        CollapsibleSubSection(
                                            title = "🎛️ Inward Scrubbing Control",
                                            subtitle = "Lower vector inward sweep scrubber",
                                            isExpanded = isBottomScrubExpanded,
                                            onToggle = {
                                                isBottomScrubExpanded = !isBottomScrubExpanded
                                                prefs.edit().putBoolean("pref_sub_scrub_bottom", isBottomScrubExpanded).apply()
                                            }
                                        ) {
                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                        }

                                        // 3. Gestures
                                        CollapsibleSubSection(
                                            title = "⚡ Gesture Actions & Macro Mappings",
                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                            isExpanded = isBottomGesturesExpanded,
                                            onToggle = {
                                                isBottomGesturesExpanded = !isBottomGesturesExpanded
                                                prefs.edit().putBoolean("pref_sub_gestures_bottom", isBottomGesturesExpanded).apply()
                                            }
                                        ) {
                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                val (vectorTitle, arrowEnum) = pairInfo
                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_BOTTOM_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_BOTTOM_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Dialogs
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
                    Column(modifier = Modifier.fillMaxWidth()) {
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

        if (showOemShieldDialog) {
            var preserveScreenshot by remember { mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, true)) }
            var preserveAccessibility by remember { mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)) }

            AlertDialog(
                onDismissRequest = { showOemShieldDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OEM Compatibility Shield", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Configure hardware-level passthrough guardrails to ensure native Android system shortcuts remain responsive on your specific device hardware.",
                            fontSize = 13.sp,
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 17.sp
                        )
                        PrefToggleRow(
                            title = "Preserve Screenshot Shortcut",
                            subtitle = "Whitelist immediate pass-through for Power + Vol Down screenshot captures",
                            isChecked = preserveScreenshot,
                            onCheckedChange = { preserveScreenshot = it }
                        )
                        PrefToggleRow(
                            title = "Preserve Accessibility Shortcut",
                            subtitle = "Bypass custom chords after 1.5s simultaneous hold and yield directly to Android TalkBack / accessibility shortcut",
                            isChecked = preserveAccessibility,
                            onCheckedChange = { preserveAccessibility = it }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit()
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, true)
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, preserveScreenshot)
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, preserveAccessibility)
                                .apply()
                            showOemShieldDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("APPLY SHIELD", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOemShieldDialog = false }) {
                        Text("DISMISS", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showBatteryWarningDialog) {
            AlertDialog(
                onDismissRequest = {
                    showBatteryWarningDialog = false
                },
                title = {
                    Text("⚠️ High Battery Usage Warning", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                },
                text = {
                    Text(
                        "Detecting back taps while the screen is off keeps your CPU awake (Partial Wake Lock), preventing Android from entering deep sleep. This typically consumes 2% to 4% battery per hour while idle.",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, pendingBackTapScope).apply()
                            showBatteryWarningDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ENABLE ANYWAY", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on").apply()
                            showBatteryWarningDialog = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showNotificationAccessDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationAccessDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    }
                },
                text = {
                    Text(
                        "To display real-time download progress and active media playback in your status bar or notch pill, Android requires Notification Access (Device & App Notifications).\n\nLightspeed is 100% offline and never records, stores, or transmits your personal notifications.",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showNotificationAccessDialog = false
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OPEN SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationAccessDialog = false }) {
                        Text("NOT NOW", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showAmoledWarningDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAmoledWarningDialog = false
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                    onRefreshNeeded()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hardware Notice: AMOLED Image Retention", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    }
                },
                text = {
                    Text(
                        "Prolonged static display on OLED panels can cause permanent subpixel degradation (burn-in). Keep auto-sleep enabled?",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                            showAmoledWarningDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Keep Auto-Sleep (Recommended)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "never").apply()
                            showAmoledWarningDialog = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Enable Always-On (I Understand the Risks)", color = MaterialTheme.colorScheme.error)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
