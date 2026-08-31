package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.OemNotchDetector
import com.sbf.lightspeed.system.TacticalFlyoutLauncher
import com.sbf.lightspeed.system.TacticalAudioEngine
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SidebarMatrixConfigurationFields(
    context: Context,
    prefs: SharedPreferences,
    toggleAllTrigger: Int = 0,
    jumpTargetTab: Int = -1,
    jumpTargetSection: String? = null,
    onRefreshNeeded: () -> Unit = {}
) {
    // Tab Display Profiles & Blueprint State
    var tabMode0 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, "custom_pinned") ?: "custom_pinned") }
    var pinnedSection0 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, "left_top") ?: "left_top") }
    var sectionOrder0Str by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, "left_center,left_top,left_bottom") ?: "left_center,left_top,left_bottom") }

    var tabMode1 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, "custom_pinned") ?: "custom_pinned") }
    var pinnedSection1 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, "sensor_deck") ?: "sensor_deck") }
    var sectionOrder1Str by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, "sensor_deck,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") ?: "sensor_deck,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") }

    var tabMode2 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, "custom_pinned") ?: "custom_pinned") }
    var pinnedSection2 by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, "top") ?: "top") }
    var sectionOrder2Str by rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, "center,top,bottom") ?: "center,top,bottom") }

    var popoverTabTarget by rememberSaveable { mutableStateOf<Int?>(null) }
    var blueprintTabTarget by rememberSaveable { mutableStateOf<Int?>(null) }

    // Left Deflector Accordion States
    var isLeftCenterExpanded by rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_center" else prefs.getBoolean("pref_section_left_center_expanded", false)) }
    var isLeftTopExpanded by rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_top" else prefs.getBoolean("pref_section_left_top_expanded", true)) }
    var isLeftBottomExpanded by rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_bottom" else prefs.getBoolean("pref_section_left_bottom_expanded", false)) }
    var isLeftFlankUnified by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)) }
    var isLeftUnifiedExpanded by rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_unified" else prefs.getBoolean("pref_section_left_unified_expanded", false)) }

    // Left Deflector Sub-Section States
    var isLeftCenterGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_center", true)) }

    var isLeftUnifiedGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_unified", true)) }
    var isLeftUnifiedScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_unified", true)) }
    var isLeftUnifiedGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_unified", true)) }

    var isLeftTopGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_top", true)) }
    var isLeftTopScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_top", true)) }
    var isLeftTopGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_top", true)) }

    var isLeftBottomGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_bottom", true)) }
    var isLeftBottomScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_bottom", true)) }
    var isLeftBottomGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_bottom", true)) }

    // HUD Strip Accordion States
    var isSensorDeckExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "sensor_deck" else prefs.getBoolean("pref_section_statusbar_expanded", true)) }
    var isTelemetryExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "telemetry_indicators" else prefs.getBoolean("pref_section_telemetry_expanded", false)) }
    var isTacticalHardwareExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "tactical_hardware" else prefs.getBoolean("pref_section_tactical_hardware_expanded", false)) }
    var isRefuelingExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "refueling_bay" else prefs.getBoolean("pref_section_refueling_expanded", false)) }
    var isConfigVaultExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "config_vault" else prefs.getBoolean("pref_section_backup_expanded", false)) }
    var isExperimentalLabsExpanded by rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "experimental_labs" else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, false)) }

    // Power Button Safety Interlock & Dynamic Tools
    var singlePressTapCount by rememberSaveable { mutableIntStateOf(0) }
    var isSinglePressUnlocked by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, false)) }
    val installedTacticalTools = remember { com.sbf.lightspeed.system.InstalledTacticalToolsScanner.scan(context) }

    // Tactical Hardware Deck Sub-Sections
    var isSubVolumeExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_volume_expanded", true)) }
    var isSubPowerExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_power_expanded", true)) }
    var isSubHullTapExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_hulltap_expanded", false)) }

    var showOemShieldDialog by rememberSaveable { mutableStateOf(false) }
    var showBatteryWarningDialog by rememberSaveable { mutableStateOf(false) }
    var showNotificationAccessDialog by rememberSaveable { mutableStateOf(false) }
    var showAmoledWarningDialog by rememberSaveable { mutableStateOf(false) }
    var pendingBackTapScope by rememberSaveable { mutableStateOf("screen_on") }

    var isStatusBarGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_statusbar", true)) }
    var isStatusBarScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_statusbar", true)) }
    var isStatusBarGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_statusbar", true)) }
    var isNotchCalibExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_calib", true)) }
    var isMarqueeSubSectionExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_marquee", true)) }
    var isOrientationSubSectionExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_orientation", true)) }

    // Live Impulse Calibration Meter State (Hull Tap)
    var currentZImpulse by remember { mutableFloatStateOf(0f) }
    var currentThreshold by remember { mutableFloatStateOf(LightspeedBackTapEngine.getThreshold(context)) }
    var thresholdCrossedFlash by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    val scope = rememberCoroutineScope()

    // STRICT LIFECYCLE HOOK: Hull Tap accelerometer polling strictly active ONLY when
    // Tactical Hardware is expanded, Hull Tap subsection is expanded, AND active tab is HUD Strip!
    val isHullTapMeterActive = isTacticalHardwareExpanded && isSubHullTapExpanded && (pagerState.currentPage == 1)

    DisposableEffect(isHullTapMeterActive) {
        if (isHullTapMeterActive) {
            LightspeedBackTapEngine.startLiveSampling(context)
            LightspeedBackTapEngine.onLiveImpulseListener = { zVal, thresh, isCrossed ->
                currentZImpulse = zVal
                currentThreshold = thresh
                thresholdCrossedFlash = isCrossed
            }
        } else {
            currentZImpulse = 0f
            thresholdCrossedFlash = false
        }
        onDispose {
            LightspeedBackTapEngine.stopLiveSampling()
            currentZImpulse = 0f
            thresholdCrossedFlash = false
        }
    }

    // Right Deflector Accordion States
    var isCenterExpanded by rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "center" else prefs.getBoolean("pref_section_center_expanded", false)) }
    var isTopExpanded by rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "top" else prefs.getBoolean("pref_section_top_expanded", true)) }
    var isBottomExpanded by rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "bottom" else prefs.getBoolean("pref_section_bottom_expanded", false)) }
    var isRightFlankUnified by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, false)) }
    var isRightUnifiedExpanded by rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "unified" else prefs.getBoolean("pref_section_right_unified_expanded", false)) }

    // Dual Watchdog Sub-Section States
    var isInnerWatchdogExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_inner_watchdog_labs", true)) }
    var isOuterWatchdogExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_outer_watchdog_labs", true)) }

    // Right Deflector Sub-Section States
    var isCenterGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_center", true)) }

    var isRightUnifiedGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_right_unified", true)) }
    var isRightUnifiedScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_right_unified", true)) }
    var isRightUnifiedGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_right_unified", true)) }

    var isTopGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_top", true)) }
    var isTopScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_top", true)) }
    var isTopGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_top", true)) }

    var isBottomGeoExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_bottom", true)) }
    var isBottomScrubExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_bottom", true)) }
    var isBottomGesturesExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_bottom", true)) }

    val listState0 = rememberLazyListState()
    val listState1 = rememberLazyListState()
    val listState2 = rememberLazyListState()

    var showUnifyInfoDialog by remember { mutableStateOf(false) }
    var showSymmetryInfoDialog by remember { mutableStateOf(false) }
    var showPasteJsonDialog by remember { mutableStateOf(false) }
    var pastedJsonText by remember { mutableStateOf("") }
    var selectedTemplateOption by remember { mutableIntStateOf(0) }

    fun cloneFlankActions(sourcePrefix: String, targetPrefix: String) {
        val editor = prefs.edit()
        val allEntries = prefs.all
        for ((key, value) in allEntries) {
            if (key.startsWith("pref_macro_action_${sourcePrefix}_")) {
                val suffix = key.removePrefix("pref_macro_action_${sourcePrefix}_")
                val targetKey = "pref_macro_action_${targetPrefix}_$suffix"
                if (value is String) {
                    editor.putString(targetKey, value)
                }
            }
        }
        editor.apply()
    }

    var showUnifyTemplateDialogForLeft by remember { mutableStateOf(false) }
    var showUnifyTemplateDialogForRight by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var importStatusMessage by remember { mutableStateOf<String?>(null) }
    var isImportSuccess by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    LightspeedBackupEngine.exportToFile(context, uri)
                }
                result.onSuccess { count ->
                    Toast.makeText(context, "Successfully exported $count settings to backup!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Failed to export backup: ${err.message ?: err.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    LightspeedBackupEngine.importFromFile(context, uri)
                }
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

    val sectionTitles0 = remember(isLeftFlankUnified) {
        if (isLeftFlankUnified) {
            mapOf("left_unified" to "Left Deflector — Flank Vector Zones (Upper & Lower)")
        } else {
            mapOf(
                "left_center" to "Left Deflector — Astrogation Core Zone",
                "left_top" to "Left Deflector — Upper Vector Zone",
                "left_bottom" to "Left Deflector — Lower Vector Zone"
            )
        }
    }

    val sectionTitles1 = remember {
        mapOf(
            "sensor_deck" to "Sensor Deck",
            "telemetry_indicators" to "Telemetry & Indicators",
            "tactical_hardware" to "Tactical Hardware Deck",
            "refueling_bay" to "Refueling Bay",
            "config_vault" to "Configuration Vault",
            "experimental_labs" to "Experimental Labs"
        )
    }

    val sectionTitles2 = remember(isRightFlankUnified) {
        if (isRightFlankUnified) {
            mapOf("unified" to "Right Deflector — Flank Vector Zones (Upper & Lower)")
        } else {
            mapOf(
                "center" to "Right Deflector — Astrogation Core Zone",
                "top" to "Right Deflector — Upper Vector Zone",
                "bottom" to "Right Deflector — Lower Vector Zone"
            )
        }
    }

    fun toggleSection(tabId: Int, sectionId: String, currentExpanded: Boolean, onSetExpanded: (Boolean) -> Unit) {
        val mode = when (tabId) {
            0 -> tabMode0
            1 -> tabMode1
            2 -> tabMode2
            else -> "custom_pinned"
        }

        val pinned = when (tabId) {
            0 -> pinnedSection0
            1 -> pinnedSection1
            2 -> pinnedSection2
            else -> null
        }

        if (mode == "solo") {
            if (!currentExpanded) {
                // Focus / Solo Mode: snaps all other cards shut in this tab
                when (tabId) {
                    0 -> {
                        isLeftCenterExpanded = false
                        isLeftTopExpanded = false
                        isLeftBottomExpanded = false
                        isLeftUnifiedExpanded = false
                    }
                    1 -> {
                        isSensorDeckExpanded = false
                        isTelemetryExpanded = false
                        isTacticalHardwareExpanded = false
                        isRefuelingExpanded = false
                        isConfigVaultExpanded = false
                        isExperimentalLabsExpanded = false
                    }
                    2 -> {
                        isCenterExpanded = false
                        isTopExpanded = false
                        isBottomExpanded = false
                        isRightUnifiedExpanded = false
                    }
                }
                onSetExpanded(true)
            } else {
                onSetExpanded(false)
            }
        } else if (mode == "custom_pinned") {
            // Anchored Solo: Designated anchor stays permanently open; opening any other card operates in Solo mode
            if (sectionId == pinned) {
                onSetExpanded(!currentExpanded)
            } else {
                if (!currentExpanded) {
                    when (tabId) {
                        0 -> {
                            if (pinned != "left_center") isLeftCenterExpanded = false
                            if (pinned != "left_top") isLeftTopExpanded = false
                            if (pinned != "left_bottom") isLeftBottomExpanded = false
                            if (pinned != "left_unified") isLeftUnifiedExpanded = false
                        }
                        1 -> {
                            if (pinned != "sensor_deck") isSensorDeckExpanded = false
                            if (pinned != "telemetry_indicators") isTelemetryExpanded = false
                            if (pinned != "tactical_hardware") isTacticalHardwareExpanded = false
                            if (pinned != "refueling_bay") isRefuelingExpanded = false
                            if (pinned != "config_vault") isConfigVaultExpanded = false
                            if (pinned != "experimental_labs") isExperimentalLabsExpanded = false
                        }
                        2 -> {
                            if (pinned != "center") isCenterExpanded = false
                            if (pinned != "top") isTopExpanded = false
                            if (pinned != "bottom") isBottomExpanded = false
                            if (pinned != "unified") isRightUnifiedExpanded = false
                        }
                    }
                    onSetExpanded(true)
                } else {
                    onSetExpanded(false)
                }
            }
        } else {
            onSetExpanded(!currentExpanded)
        }
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
                    isLeftUnifiedExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_left_center_expanded", allExpandedState)
                        .putBoolean("pref_section_left_top_expanded", allExpandedState)
                        .putBoolean("pref_section_left_bottom_expanded", allExpandedState)
                        .putBoolean("pref_section_left_unified_expanded", allExpandedState)
                        .putBoolean("pref_sidebar_left_preview", allExpandedState)
                        .apply()
                }
                1 -> {
                    isSensorDeckExpanded = allExpandedState
                    isTelemetryExpanded = allExpandedState
                    isTacticalHardwareExpanded = allExpandedState
                    isRefuelingExpanded = allExpandedState
                    isConfigVaultExpanded = allExpandedState
                    isExperimentalLabsExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_statusbar_expanded", allExpandedState)
                        .putBoolean("pref_section_telemetry_expanded", allExpandedState)
                        .putBoolean("pref_section_tactical_hardware_expanded", allExpandedState)
                        .putBoolean("pref_section_refueling_expanded", allExpandedState)
                        .putBoolean("pref_section_backup_expanded", allExpandedState)
                        .putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, allExpandedState)
                        .putBoolean("pref_statusbar_preview", allExpandedState)
                        .apply()
                }
                2 -> {
                    isCenterExpanded = allExpandedState
                    isTopExpanded = allExpandedState
                    isBottomExpanded = allExpandedState
                    isRightUnifiedExpanded = allExpandedState
                    prefs.edit()
                        .putBoolean("pref_section_center_expanded", allExpandedState)
                        .putBoolean("pref_section_top_expanded", allExpandedState)
                        .putBoolean("pref_section_bottom_expanded", allExpandedState)
                        .putBoolean("pref_section_right_unified_expanded", allExpandedState)
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
            "statusbar", "sensor_deck" -> isSensorDeckExpanded = true
            "telemetry", "telemetry_indicators" -> isTelemetryExpanded = true
            "volumekeys", "tactical_hardware", "power", "backtap" -> isTacticalHardwareExpanded = true
            "refueling", "refueling_bay" -> isRefuelingExpanded = true
            "backup", "config_vault", "shizuku_jettison" -> isConfigVaultExpanded = true
            "experimental_labs", "labs", "experimental", "watchdog", "core_cooling" -> isExperimentalLabsExpanded = true
            "wings" -> {
                if (jumpTargetTab == 0) isLeftCenterExpanded = true
                else isCenterExpanded = true
            }
        }
    }

    // Auto-hide / auto-show preview on tab swipe — drop(1) skips initial composition
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { page ->
                val leftActive = page == 0 && (
                    isLeftCenterExpanded ||
                    (if (isLeftFlankUnified) isLeftUnifiedExpanded else isLeftTopExpanded || isLeftBottomExpanded)
                )
                val canopyActive = page == 1 && isSensorDeckExpanded && isStatusBarGeoExpanded
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
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tabTitles = listOf("◀ Deflectors", "HUD STRIP", "Deflectors ▶")
            tabTitles.forEachIndexed { index, tabTitle ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            },
                            onLongClick = {
                                LightspeedHapticEngine.heavyClick(context)
                                popoverTabTarget = index
                            }
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }

        // High-Performance Swipable Pages
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            when (pageIndex) {
                // PAGE 0: LEFT DEFLECTOR
                0 -> {
                    val defaultOrder0 = if (isLeftFlankUnified) listOf("left_unified") else listOf("left_center", "left_top", "left_bottom")
                    val currentOrder0 = sectionOrder0Str.split(",").map { it.trim() }.filter { it in defaultOrder0 }.distinct().let { list ->
                        list + (defaultOrder0 - list.toSet())
                    }

                    if (blueprintTabTarget == 0) {
                        BlueprintWireframeView(
                            tabTitle = "◀ Deflectors",
                            sectionIds = currentOrder0,
                            pinnedSectionId = pinnedSection0,
                            sectionTitles = sectionTitles0,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder0.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder0Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder0.size - 1) {
                                    val mutable = currentOrder0.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder0Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection0 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState0,
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
                                            prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", false).commit()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    },
                                    onInfoClick = { showUnifyInfoDialog = true }
                                )
                            }

                            currentOrder0.forEach { secId ->
                                when (secId) {
                                    "left_center" -> {
                                        item(key = "left_center") {
                                            CompactAccordionSection(
                                                title = "Left Deflector — Astrogation Core Zone",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Navigation,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isLeftCenterExpanded,
                                                onToggle = {
                                                    toggleSection(0, "left_center", isLeftCenterExpanded) { isLeftCenterExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_left_center_expanded", isLeftCenterExpanded)
                                                        .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded || isLeftUnifiedExpanded)
                                                        .apply()
                                                }
                                            ) {
                                                 Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    CollapsibleSubSection(
                                                        title = "Sensor Geometry",
                                                        subtitle = "Deflector span, touch reach, offset & stealth glow",
                                                        isExpanded = isLeftCenterGeoExpanded,
                                                        onToggle = {
                                                            isLeftCenterGeoExpanded = !isLeftCenterGeoExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_geo_left_center", isLeftCenterGeoExpanded)
                                                                .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded && isLeftCenterGeoExpanded)
                                                                .apply()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_left_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "left_unified" -> {
                                        if (isLeftFlankUnified) {
                                            item(key = "left_unified") {
                                                CompactAccordionSection(
                                                    title = "Left Deflector — Flank Vector Zones (Upper & Lower)",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.SwapVert,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftUnifiedExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_unified", isLeftUnifiedExpanded) { isLeftUnifiedExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_unified_expanded", isLeftUnifiedExpanded)
                                                            .putBoolean("pref_sidebar_left_preview", (isLeftCenterExpanded && isLeftCenterGeoExpanded) || (isLeftUnifiedExpanded && isLeftUnifiedGeoExpanded))
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Independent height, touch reach & stealth glow",
                                                            isExpanded = isLeftUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGeoExpanded = !isLeftUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_unified", isLeftUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftUnifiedExpanded && isLeftUnifiedGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            Text("UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Upper Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Lower Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Dual Inward Scrubber Controls",
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

                                                        CollapsibleSubSection(
                                                            title = "Unified Gesture Matrix",
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
                                        }
                                    }
                                    "left_top" -> {
                                        if (!isLeftFlankUnified) {
                                            item(key = "left_top") {
                                                CompactAccordionSection(
                                                    title = "Left Deflector — Upper Vector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftTopExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_top", isLeftTopExpanded) { isLeftTopExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_top_expanded", isLeftTopExpanded)
                                                            .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded)
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftTopGeoExpanded,
                                                            onToggle = {
                                                                isLeftTopGeoExpanded = !isLeftTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_top", isLeftTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftTopExpanded && isLeftTopGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isLeftTopScrubExpanded,
                                                            onToggle = {
                                                                isLeftTopScrubExpanded = !isLeftTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_top", isLeftTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
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
                                        }
                                    }
                                    "left_bottom" -> {
                                        if (!isLeftFlankUnified) {
                                            item(key = "left_bottom") {
                                                CompactAccordionSection(
                                                    title = "Left Deflector — Lower Vector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftBottomExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_bottom", isLeftBottomExpanded) { isLeftBottomExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_bottom_expanded", isLeftBottomExpanded)
                                                            .putBoolean("pref_sidebar_left_preview", isLeftCenterExpanded || isLeftTopExpanded || isLeftBottomExpanded)
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftBottomGeoExpanded,
                                                            onToggle = {
                                                                isLeftBottomGeoExpanded = !isLeftBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_bottom", isLeftBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftBottomExpanded && isLeftBottomGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isLeftBottomScrubExpanded,
                                                            onToggle = {
                                                                isLeftBottomScrubExpanded = !isLeftBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_bottom", isLeftBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
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
                            }
                        }
                    }
                }

                // PAGE 1: HUD STRIP
                1 -> {
                    val defaultOrder1 = listOf("sensor_deck", "telemetry_indicators", "tactical_hardware", "refueling_bay", "config_vault", "experimental_labs")
                    val currentOrder1 = sectionOrder1Str.split(",").map { it.trim() }.filter { it in defaultOrder1 }.distinct().let { list ->
                        list + (defaultOrder1 - list.toSet())
                    }

                    if (blueprintTabTarget == 1) {
                        BlueprintWireframeView(
                            tabTitle = "HUD STRIP",
                            sectionIds = currentOrder1,
                            pinnedSectionId = pinnedSection1,
                            sectionTitles = sectionTitles1,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder1.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder1Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder1.size - 1) {
                                    val mutable = currentOrder1.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder1Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection1 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState1,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            currentOrder1.forEach { sectionId ->
                                when (sectionId) {
                                    "sensor_deck" -> {
                                        item(key = "sensor_deck") {
                                            CompactAccordionSection(
                                                title = "Sensor Deck",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Sensors,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isSensorDeckExpanded,
                                                onToggle = {
                                                    toggleSection(1, "sensor_deck", isSensorDeckExpanded) { isSensorDeckExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_statusbar_expanded", isSensorDeckExpanded)
                                                        .putBoolean("pref_statusbar_preview", isSensorDeckExpanded && isStatusBarGeoExpanded)
                                                        .apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    PrefToggleRow(
                                                        title = "Enable Sensor Deck Gestures",
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
                                                        title = "Sensor Geometry",
                                                        subtitle = "Horizon rail span, thickness, offset & idle glow",
                                                        isExpanded = isStatusBarGeoExpanded,
                                                        onToggle = {
                                                            isStatusBarGeoExpanded = !isStatusBarGeoExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_geo_statusbar", isStatusBarGeoExpanded)
                                                                .putBoolean("pref_statusbar_preview", isSensorDeckExpanded && isStatusBarGeoExpanded)
                                                                .apply()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_span", "", "Horizon Rail Span (≥1000 = full width)", 50, 1080, 10, 1080)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_thickness", "", "Horizon Rail Thickness (Height)", 20, 52, 2, 48)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_x", "", "Horizontal Offset (X Axis)", -300, 300, 5, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_offset_y", "", "Vertical Offset (Y Axis)", -100, 200, 5, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_sensitivity", "", "Touch Vector Sensitivity", 10, 100, 5, 40)
                                                        PrefDottedSliderRow(context, prefs, "pref_statusbar_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                    }

                                                    // 3. Gestures
                                                    CollapsibleSubSection(
                                                        title = "Sensor Deck Gestures & Macros",
                                                        subtitle = "Tap, double-tap, left & right swipes with Hold Modifiers",
                                                        isExpanded = isStatusBarGesturesExpanded,
                                                        onToggle = {
                                                            isStatusBarGesturesExpanded = !isStatusBarGesturesExpanded
                                                            prefs.edit().putBoolean("pref_sub_gestures_statusbar", isStatusBarGesturesExpanded).apply()
                                                        }
                                                    ) {
                                                        val statusVectors = listOf(
                                                            "TAP" to ("Tap" to ArrowDirection.TAP),
                                                            "DOUBLE_TAP" to ("Tap (Double)" to ArrowDirection.DOUBLE_TAP),
                                                            "SWIPE_LEFT" to ("Swipe Left" to ArrowDirection.SWIPE_LEFT),
                                                            "SWIPE_RIGHT" to ("Swipe Right" to ArrowDirection.SWIPE_RIGHT),
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
                                    }
                                    "telemetry_indicators" -> {
                                        item(key = "telemetry_indicators") {
                                            CompactAccordionSection(
                                                title = "Telemetry & Indicators",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Speed,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isTelemetryExpanded,
                                                onToggle = {
                                                    toggleSection(1, "telemetry_indicators", isTelemetryExpanded) { isTelemetryExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_telemetry_expanded", isTelemetryExpanded).apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    val oemFeatureName = remember { OemNotchDetector.getDetectedFeatureName() }
                                                    var isOemNoticeDemoted by remember { mutableStateOf(prefs.getBoolean("pref_oem_notch_notice_demoted", false)) }
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
                                                    val currentDl = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, "notch_pill") ?: "notch_pill"
                                                    var isDlDropdownOpen by remember { mutableStateOf(false) }
                                                    val routingOptions = listOf(
                                                        "none" to "None (Disabled)",
                                                        "top_line" to "Horizon Rail",
                                                        "notch_pill" to "Orbital Capsule",
                                                        "both" to "Both (Horizon Rail & Orbital Capsule)"
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
                                                                    Text(routingOptions.firstOrNull { it.first == currentDl }?.second ?: "Orbital Capsule", color = Color.White)
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isDlDropdownOpen,
                                                                onDismissRequest = { isDlDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                routingOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isDlDropdownOpen = false
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_TELEMETRY_DOWNLOADS_ROUTING, key).apply()
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
                                                    val currentMedia = prefs.getString(LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, "none") ?: "none"
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
                                                                onDismissRequest = { isMediaDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                routingOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isMediaDropdownOpen = false
                                                                            prefs.edit().putString(LightspeedPreferences.KEY_TELEMETRY_MEDIA_ROUTING, key).apply()
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

                                                    // Orbital Capsule Calibration
                                                    CollapsibleSubSection(
                                                        title = "Orbital Capsule Calibration",
                                                        subtitle = "Live alignment, offsets & expansion",
                                                        isExpanded = isNotchCalibExpanded,
                                                        onToggle = {
                                                            isNotchCalibExpanded = !isNotchCalibExpanded
                                                            prefs.edit().putBoolean("pref_sub_notch_calib", isNotchCalibExpanded).apply()
                                                        }
                                                    ) {
                                                        // OEM Dynamic Notch / Dynamic Bar Advisory Glass Callout
                                                        if (!isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                                                            Card(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                            ) {
                                                                Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                    Row(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        verticalAlignment = Alignment.CenterVertically
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Info,
                                                                            contentDescription = null,
                                                                            tint = MaterialTheme.colorScheme.primary,
                                                                            modifier = Modifier.size(20.dp)
                                                                        )
                                                                        Spacer(modifier = Modifier.width(8.dp))
                                                                        Column(modifier = Modifier.weight(1f)) {
                                                                            Text(
                                                                                text = "OEM Dynamic Island Conflicts",
                                                                                fontSize = 12.sp,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = Color.White
                                                                            )
                                                                            Text(
                                                                                text = "Your device may have $oemFeatureName enabled. Disable it in system settings to prevent overlapping indicators.",
                                                                                fontSize = 11.sp,
                                                                                color = Color.LightGray.copy(alpha = 0.85f),
                                                                                lineHeight = 14.sp
                                                                            )
                                                                        }
                                                                        IconButton(
                                                                            onClick = {
                                                                                isOemNoticeDemoted = true
                                                                                prefs.edit().putBoolean("pref_oem_notch_notice_demoted", true).apply()
                                                                            },
                                                                            modifier = Modifier.size(24.dp)
                                                                        ) {
                                                                            Icon(Icons.Outlined.VerticalAlignBottom, contentDescription = "Demote to Footnote", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                                                                        }
                                                                    }
                                                                    Button(
                                                                        onClick = { OemNotchDetector.openSearch(context) },
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                                    ) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text("Open $oemFeatureName Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        PrefToggleRow(
                                                            title = "Test Beacon Live Alignment",
                                                            subtitle = "Projects a persistent illuminated liquid-glass capsule around the cutout for live alignment.",
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
                                                            Text("ORBITAL CAPSULE LAYOUT MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
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
                                                                    onDismissRequest = { isCapsuleDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    capsuleOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
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

                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_PADDING_SNUGNESS, "", "Capsule Snugness Padding (0 to 8dp)", 0, 8, 1, 2)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_OFFSET_Y, "", "Vertical Offset Y (-30 to +30dp)", -30, 30, 1, 0)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_OFFSET_X, "", "Horizontal Offset X (-30 to +30dp)", -30, 30, 1, 0)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_EXPANSION_WIDTH, "", "Capsule Expansion Width (0 to 80dp)", 0, 80, 2, 0)
                                                    }

                                                    // Title Overflow & Marquee Engine
                                                    CollapsibleSubSection(
                                                        title = "Title Overflow & Marquee Engine",
                                                        subtitle = "Marquee speed, pause delays, clipping & truncation",
                                                        isExpanded = isMarqueeSubSectionExpanded,
                                                        onToggle = {
                                                            isMarqueeSubSectionExpanded = !isMarqueeSubSectionExpanded
                                                            prefs.edit().putBoolean("pref_sub_notch_marquee", isMarqueeSubSectionExpanded).apply()
                                                        }
                                                    ) {
                                                        PrefToggleRow(
                                                            title = "Enable Text Marquee Animation",
                                                            subtitle = "Smoothly scrolls overflowing download filenames and song titles across the HUD capsule.",
                                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_MARQUEE_ENABLED, true),
                                                            onCheckedChange = {
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_NOTCH_MARQUEE_ENABLED, it).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        )
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_SPEED, "", "Scroll Velocity (px/sec)", 15, 80, 5, 30)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MARQUEE_INITIAL_DELAY, "", "Initial Pause Delay (ms)", 500, 3000, 250, 1500)
                                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_NOTCH_MAX_CAPSULE_WIDTH, "", "Max HUD Capsule Width (dp)", 120, 320, 10, 200)
                                                    }

                                                    // System Attitude & Orientation Engine
                                                    var isAutoRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isAutoRotateEnabled(context)) }
                                                    var isFaceRotateActive by remember { mutableStateOf(LightspeedOrientationEngine.isFaceRotateEnabled(context)) }
                                                    var selectedAttitudeBucketForAppPicker by remember { mutableStateOf<LightspeedOrientationEngine.AttitudeBucket?>(null) }

                                                    DisposableEffect(Unit) {
                                                        val observer = LightspeedOrientationEngine.registerObserver(
                                                            context,
                                                            onAutoRotateChanged = { isAutoRotateActive = it },
                                                            onFaceRotateChanged = { isFaceRotateActive = it }
                                                        )
                                                        onDispose {
                                                            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
                                                        }
                                                    }

                                                    if (selectedAttitudeBucketForAppPicker != null) {
                                                        AttitudeAppAssignmentSheet(
                                                            context = context,
                                                            bucket = selectedAttitudeBucketForAppPicker!!,
                                                            onDismiss = { selectedAttitudeBucketForAppPicker = null },
                                                            onUpdated = { onRefreshNeeded() }
                                                        )
                                                    }

                                                    CollapsibleSubSection(
                                                        title = "Synthetic Gravity Engine",
                                                        subtitle = "Auto-rotate, face detection, 2x2 mode buckets & context guardrails",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Rotate90DegreesCw,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isOrientationSubSectionExpanded,
                                                        onToggle = {
                                                            isOrientationSubSectionExpanded = !isOrientationSubSectionExpanded
                                                            prefs.edit().putBoolean("pref_sub_orientation", isOrientationSubSectionExpanded).apply()
                                                        }
                                                    ) {
                                                        // 1. Master Auto-Rotate Switch
                                                        PrefToggleRow(
                                                            title = "Master Auto-Rotate",
                                                            subtitle = "Global Android accelerometer orientation trigger (Settings.System.ACCELEROMETER_ROTATION)",
                                                            isChecked = isAutoRotateActive,
                                                            onCheckedChange = {
                                                                LightspeedOrientationEngine.setAutoRotateEnabled(context, it)
                                                                isAutoRotateActive = it
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        // 2. Face-Oriented Auto-Rotate (CAMERA_AUTOROTATE)
                                                        if (isAutoRotateActive && LightspeedOrientationEngine.isFaceRotateSupported(context)) {
                                                            Card(
                                                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                                ) {
                                                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            Icon(Icons.Default.Face, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text("Face-Oriented Auto-Rotate", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                                                        }
                                                                        Spacer(modifier = Modifier.height(2.dp))
                                                                        Text("Uses front camera facial posture to prevent accidental rotations while lying down", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                                                    }
                                                                    Switch(
                                                                        checked = isFaceRotateActive,
                                                                        onCheckedChange = {
                                                                            val ok = LightspeedOrientationEngine.setFaceRotateEnabled(context, it)
                                                                            if (ok) isFaceRotateActive = it
                                                                            onRefreshNeeded()
                                                                        },
                                                                        colors = SwitchDefaults.colors(
                                                                            checkedThumbColor = Color.White,
                                                                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                                            checkedBorderColor = Color.Transparent,
                                                                            uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                                                                            uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                                                                            uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        // 3. 2x2 Attitude Mode Bucket Grid
                                                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                            Text("ATTITUDE MODE BUCKETS (PER-APP RULES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            Text("Tap a bucket to assign apps to automatically enforce that rotation policy upon launch:", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.75f))

                                                            val buckets = listOf(
                                                                LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT,
                                                                LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT,
                                                                LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE,
                                                                LightspeedOrientationEngine.AttitudeBucket.SENSOR_360
                                                            )

                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                listOf(buckets[0], buckets[1]).forEach { bucket ->
                                                                    val assignedCount = remember(bucket, prefs.getStringSet(bucket.prefKey, null)) {
                                                                        LightspeedOrientationEngine.getAssignedPackages(context, bucket).size
                                                                    }
                                                                    Card(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .clip(RoundedCornerShape(12.dp))
                                                                            .clickable { selectedAttitudeBucketForAppPicker = bucket }
                                                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                                                        shape = RoundedCornerShape(12.dp),
                                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                                    ) {
                                                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                                Icon(
                                                                                    imageVector = when (bucket) {
                                                                                        LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                                                                                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
                                                                                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                                                                                        else -> Icons.Default.ScreenRotation
                                                                                    },
                                                                                    contentDescription = null,
                                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                                    modifier = Modifier.size(18.dp)
                                                                                )
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(6.dp),
                                                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                                ) {
                                                                                    Text(
                                                                                        text = "$assignedCount apps",
                                                                                        fontSize = 9.5.sp,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Spacer(modifier = Modifier.height(6.dp))
                                                                            Text(bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                                            Spacer(modifier = Modifier.height(2.dp))
                                                                            Text(bucket.subtitle, fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.7f), lineHeight = 12.sp)
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                listOf(buckets[2], buckets[3]).forEach { bucket ->
                                                                    val assignedCount = remember(bucket, prefs.getStringSet(bucket.prefKey, null)) {
                                                                        LightspeedOrientationEngine.getAssignedPackages(context, bucket).size
                                                                    }
                                                                    Card(
                                                                        modifier = Modifier
                                                                            .weight(1f)
                                                                            .clip(RoundedCornerShape(12.dp))
                                                                            .clickable { selectedAttitudeBucketForAppPicker = bucket }
                                                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                                                                        shape = RoundedCornerShape(12.dp),
                                                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                                                    ) {
                                                                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                                                Icon(
                                                                                    imageVector = when (bucket) {
                                                                                        LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                                                                                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
                                                                                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                                                                                        else -> Icons.Default.ScreenRotation
                                                                                    },
                                                                                    contentDescription = null,
                                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                                    modifier = Modifier.size(18.dp)
                                                                                )
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(6.dp),
                                                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                                                ) {
                                                                                    Text(
                                                                                        text = "$assignedCount apps",
                                                                                        fontSize = 9.5.sp,
                                                                                        fontWeight = FontWeight.Bold,
                                                                                        color = MaterialTheme.colorScheme.primary,
                                                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                                                    )
                                                                                }
                                                                            }
                                                                            Spacer(modifier = Modifier.height(6.dp))
                                                                            Text(bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                                                            Spacer(modifier = Modifier.height(2.dp))
                                                                            Text(bucket.subtitle, fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.7f), lineHeight = 12.sp)
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        // 4. Orientation Policy & Guardrails
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
                                                                    onDismissRequest = { isOrientationDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    orientationOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
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

                                                        // OEM Advisory Footnote: OEM Dynamic Island Conflicts (Demoted)
                                                        if (isOemNoticeDemoted && !oemFeatureName.isNullOrBlank()) {
                                                            Card(
                                                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                                shape = RoundedCornerShape(12.dp),
                                                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text("OEM Advisory Footnote: OEM Dynamic Island Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                                                        Text("Your device may have $oemFeatureName enabled. Tap to manage settings if indicators overlap.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f))
                                                                    }
                                                                    TextButton(
                                                                        onClick = { OemNotchDetector.openSearch(context) },
                                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                    ) {
                                                                        Text("SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "tactical_hardware" -> {
                                        item(key = "tactical_hardware") {
                                            CompactAccordionSection(
                                                title = "Tactical Hardware Deck",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Tune,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isTacticalHardwareExpanded,
                                                onToggle = {
                                                    toggleSection(1, "tactical_hardware", isTacticalHardwareExpanded) { isTacticalHardwareExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_tactical_hardware_expanded", isTacticalHardwareExpanded).apply()
                                                    if (isTacticalHardwareExpanded && !prefs.getBoolean(LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, false)) {
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
                                                    // Volume Key Matrix (Combos & Chords)
                                                    CollapsibleSubSection(
                                                        title = "Volume Key Matrix",
                                                        subtitle = "Hardware chording, sequences, hold auto-repeat & suppression",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Tune,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubVolumeExpanded,
                                                        onToggle = {
                                                            isSubVolumeExpanded = !isSubVolumeExpanded
                                                            prefs.edit().putBoolean("pref_sub_volume_expanded", isSubVolumeExpanded).apply()
                                                        }
                                                    ) {
                                                        PrefToggleRow(
                                                            title = "Enable Volume Key Gestures",
                                                            subtitle = "Low-latency hardware chording, sequences & hold triggers",
                                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, true),
                                                            onCheckedChange = { checked ->
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_VOL_GESTURES_ENABLED, checked).apply()
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
                                                            Triple(LightspeedPreferences.KEY_VOL_UP_LONG_PRESS, "Volume Up Long Press (~400ms)", "VOL ▲ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_VOL_DOWN_LONG_PRESS, "Volume Down Long Press (~400ms)", "VOL ▼ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_CHORD_DOWN_HOLD_UP_TAP, "Hold Vol Down + Tap Vol Up", "VOL ▼ + VOL ▲"),
                                                            Triple(LightspeedPreferences.KEY_CHORD_UP_HOLD_DOWN_TAP, "Hold Vol Up + Tap Vol Down", "VOL ▲ + VOL ▼"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_UP_THEN_DOWN, "Sequence: Vol Up → Vol Down (<300ms)", "VOL ▲ ➔ VOL ▼"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_DOWN_THEN_UP, "Sequence: Vol Down → Vol Up (<300ms)", "VOL ▼ ➔ VOL ▲"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_DOWN_TAP_THEN_UP_HOLD, "Tap Vol Down → Hold Vol Up (~400ms)", "VOL ▼ ➔ VOL ▲ (HOLD)"),
                                                            Triple(LightspeedPreferences.KEY_SEQ_UP_TAP_THEN_DOWN_HOLD, "Tap Vol Up → Hold Vol Down (~400ms)", "VOL ▲ ➔ VOL ▼ (HOLD)")
                                                        )

                                                        volumeGestures.forEach { (prefKey, title, badge) ->
                                                            GestureMappingRow(
                                                                context = context,
                                                                prefs = prefs,
                                                                direction = ArrowDirection.TAP,
                                                                isHold = badge.contains("HOLD"),
                                                                keyResName = prefKey,
                                                                defaultTitle = title,
                                                                options = dynamicActionTokens,
                                                                labelCache = tokenLabelCache,
                                                                badgeText = badge,
                                                                showMediaQuickAccess = true
                                                            )
                                                        }
                                                    }

                                                    // Power Button Engine
                                                    CollapsibleSubSection(
                                                        title = "Power Button Engine",
                                                        subtitle = "Single, Double, Hold (~400ms) & Press-then-Hold triggers",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.PowerSettingsNew,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubPowerExpanded,
                                                        onToggle = {
                                                            isSubPowerExpanded = !isSubPowerExpanded
                                                            prefs.edit().putBoolean("pref_sub_power_expanded", isSubPowerExpanded).apply()
                                                        }
                                                    ) {
                                                        // Emergency Reset Hardware Notice Banner
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.WarningAmber,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text(
                                                                    text = "Emergency Notice: 10s hardware power hold forces device reset. Disabling Accessibility restores system defaults.",
                                                                    fontSize = 11.sp,
                                                                    color = Color.LightGray.copy(alpha = 0.85f),
                                                                    lineHeight = 14.sp
                                                                )
                                                            }
                                                        }

                                                        PrefToggleRow(
                                                            title = "Enable Power Button Gestures",
                                                            subtitle = "Low-latency physical power button gesture interception",
                                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_POWER_GESTURES_ENABLED, true),
                                                            onCheckedChange = { checked ->
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_POWER_GESTURES_ENABLED, checked).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        val powerGestures = listOf(
                                                            Triple(LightspeedPreferences.KEY_POWER_SINGLE_PRESS, "Single Press", LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS),
                                                            Triple(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS, "Double Press (<300ms)", LightspeedKeyEngine.PowerTriggerSlot.POWER_DOUBLE_PRESS),
                                                            Triple(LightspeedPreferences.KEY_POWER_HOLD, "Hold (~400ms)", LightspeedKeyEngine.PowerTriggerSlot.POWER_HOLD),
                                                            Triple(LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD, "Press-then-Hold", LightspeedKeyEngine.PowerTriggerSlot.POWER_PRESS_THEN_HOLD)
                                                        )

                                                        powerGestures.forEach { (prefKey, title, slot) ->
                                                            PowerGestureMappingRow(
                                                                context = context,
                                                                prefs = prefs,
                                                                prefKey = prefKey,
                                                                title = title,
                                                                slot = slot,
                                                                isSinglePress = slot == LightspeedKeyEngine.PowerTriggerSlot.POWER_SINGLE_PRESS,
                                                                isSinglePressUnlocked = isSinglePressUnlocked,
                                                                onSinglePressUnlockStep = {
                                                                    singlePressTapCount++
                                                                    if (singlePressTapCount in 4..6) {
                                                                        android.widget.Toast.makeText(
                                                                            context,
                                                                            "You are ${7 - singlePressTapCount} steps away from unlocking Single Press remap.",
                                                                            android.widget.Toast.LENGTH_SHORT
                                                                        ).show()
                                                                    } else if (singlePressTapCount >= 7) {
                                                                        isSinglePressUnlocked = true
                                                                        prefs.edit().putBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, true).apply()
                                                                        android.widget.Toast.makeText(
                                                                            context,
                                                                            "Single Press Remapping Unlocked",
                                                                            android.widget.Toast.LENGTH_SHORT
                                                                        ).show()
                                                                        com.sbf.lightspeed.system.LightspeedHapticEngine.heavyClick(context)
                                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                        onRefreshNeeded()
                                                                    }
                                                                },
                                                                installedTools = installedTacticalTools,
                                                                options = dynamicActionTokens,
                                                                labelCache = tokenLabelCache,
                                                                onRefreshNeeded = onRefreshNeeded
                                                            )
                                                        }
                                                    }

                                                    // Hull Tap Sensors (Back Tap)
                                                    CollapsibleSubSection(
                                                        title = "Hull Tap Sensors",
                                                        subtitle = "Accelerometer Z-axis impulse detection for double & triple back taps",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.TouchApp,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isSubHullTapExpanded,
                                                        onToggle = {
                                                            isSubHullTapExpanded = !isSubHullTapExpanded
                                                            prefs.edit().putBoolean("pref_sub_hulltap_expanded", isSubHullTapExpanded).apply()
                                                        }
                                                    ) {
                                                        PrefToggleRow(
                                                            title = "Enable Back Tap Gestures",
                                                            subtitle = "Detect double and triple taps on the back of your device",
                                                            isChecked = prefs.getBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, false),
                                                            onCheckedChange = { checked ->
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, checked).apply()
                                                                if (!checked) {
                                                                    currentZImpulse = 0f
                                                                    thresholdCrossedFlash = false
                                                                }
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        val currentScope = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on") ?: "screen_on"
                                                        var isScopeDropdownOpen by remember { mutableStateOf(false) }
                                                        val scopeOptions = listOf(
                                                            "screen_on" to "Screen On Only",
                                                            "screen_off" to "Screen Off (High Drain)",
                                                            "always" to "Always (High Drain)"
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
                                                                    onDismissRequest = { isScopeDropdownOpen = false },
                                                                    modifier = Modifier
                                                                        .background(Color(0xF012141A))
                                                                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                    shape = RoundedCornerShape(16.dp),
                                                                    containerColor = Color(0xF012141A)
                                                                ) {
                                                                    scopeOptions.forEach { (key, label) ->
                                                                        DropdownMenuItem(
                                                                            modifier = Modifier.heightIn(min = 48.dp),
                                                                            text = { Text(label) },
                                                                            onClick = {
                                                                                isScopeDropdownOpen = false
                                                                                if (key == "screen_off" || key == "always") {
                                                                                    pendingBackTapScope = key
                                                                                    showBatteryWarningDialog = true
                                                                                } else {
                                                                                    prefs.edit().putString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, key).apply()
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

                                                        // Live Impulse Calibration Meter (Active strictly when this section is expanded)
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

                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(18.dp)
                                                                        .clip(RoundedCornerShape(9.dp))
                                                                        .background(Color.Black.copy(alpha = 0.5f))
                                                                ) {
                                                                    val impulseFraction = (currentZImpulse / 20.0f).coerceIn(0f, 1f)
                                                                    val threshFraction = (currentThreshold / 20.0f).coerceIn(0f, 1f)

                                                                    Box(
                                                                        modifier = Modifier
                                                                            .fillMaxHeight()
                                                                            .fillMaxWidth(impulseFraction)
                                                                            .background(if (thresholdCrossedFlash) Color(0xFF00E676) else MaterialTheme.colorScheme.primary)
                                                                    )

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

                                                        GestureMappingRow(
                                                            context = context,
                                                            prefs = prefs,
                                                            direction = ArrowDirection.DOUBLE_TAP,
                                                            isHold = false,
                                                            keyResName = LightspeedPreferences.KEY_BACK_TAP_DOUBLE,
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
                                                            keyResName = LightspeedPreferences.KEY_BACK_TAP_TRIPLE,
                                                            defaultTitle = "Triple Back Tap (≤ 700ms)",
                                                            options = dynamicActionTokens,
                                                            labelCache = tokenLabelCache,
                                                            showMediaQuickAccess = true
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "refueling_bay" -> {
                                        item(key = "refueling_bay") {
                                            CompactAccordionSection(
                                                title = "Refueling Bay",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.BatteryChargingFull,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isRefuelingExpanded,
                                                onToggle = {
                                                    toggleSection(1, "refueling_bay", isRefuelingExpanded) { isRefuelingExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_refueling_expanded", isRefuelingExpanded).apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    val isInfinixOrTranssion = remember {
                                                        val m = android.os.Build.MANUFACTURER.lowercase()
                                                        val b = android.os.Build.BRAND.lowercase()
                                                        m.contains("infinix") || m.contains("transsion") || m.contains("tecno") || m.contains("itel") ||
                                                        b.contains("infinix") || b.contains("transsion") || b.contains("tecno") || b.contains("itel")
                                                    }
                                                    var isWarningDemoted by remember { mutableStateOf(prefs.getBoolean("pref_infinix_standby_warning_demoted", false)) }

                                                    if (isInfinixOrTranssion && !isWarningDemoted) {
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                                        ) {
                                                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Icon(
                                                                        imageVector = Icons.Default.Info,
                                                                        contentDescription = null,
                                                                        tint = MaterialTheme.colorScheme.primary,
                                                                        modifier = Modifier.size(20.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column(modifier = Modifier.weight(1f)) {
                                                                        Text(
                                                                            text = "OEM Ambient Display / Dock Conflicts",
                                                                            fontSize = 12.sp,
                                                                            fontWeight = FontWeight.Bold,
                                                                            color = Color.White
                                                                        )
                                                                        Text(
                                                                            text = "OEM standby display style may overlap with Refueling Bay. Disable it in system settings to prevent screen collisions.",
                                                                            fontSize = 11.sp,
                                                                            color = Color.LightGray.copy(alpha = 0.85f),
                                                                            lineHeight = 14.sp
                                                                        )
                                                                    }
                                                                    IconButton(
                                                                        onClick = {
                                                                            isWarningDemoted = true
                                                                            prefs.edit().putBoolean("pref_infinix_standby_warning_demoted", true).apply()
                                                                        },
                                                                        modifier = Modifier.size(24.dp)
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Outlined.VerticalAlignBottom,
                                                                            contentDescription = "Demote to Footnote",
                                                                            tint = Color.LightGray,
                                                                            modifier = Modifier.size(16.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Button(
                                                                    onClick = {
                                                                        try {
                                                                            val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            }
                                                                            context.startActivity(intent)
                                                                        } catch (_: Exception) {
                                                                            try {
                                                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                })
                                                                            } catch (_: Exception) {}
                                                                        }
                                                                    },
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    shape = RoundedCornerShape(8.dp),
                                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                                ) {
                                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                                        Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Open OEM Standby Settings", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

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
                                                                onDismissRequest = { isTriggerDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                triggerOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
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

                                                    val currentTimeout = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s"
                                                    var isTimeoutDropdownOpen by remember { mutableStateOf(false) }
                                                    val timeoutOptions = listOf(
                                                        "30s" to "30 Seconds",
                                                        "60s" to "60 Seconds (Recommended)",
                                                        "120s" to "2 Minutes",
                                                        "300s" to "5 Minutes",
                                                        "never" to "Never (⚠️ AMOLED Risk)"
                                                    )

                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                                        Text("INACTIVITY SLEEP TIMEOUT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Box {
                                                            OutlinedButton(
                                                                onClick = { isTimeoutDropdownOpen = true },
                                                                modifier = Modifier.fillMaxWidth(),
                                                                shape = RoundedCornerShape(12.dp)
                                                            ) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(timeoutOptions.firstOrNull { it.first == currentTimeout }?.second ?: "60 Seconds", color = Color.White, fontSize = 12.sp)
                                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                            DropdownMenu(
                                                                expanded = isTimeoutDropdownOpen,
                                                                onDismissRequest = { isTimeoutDropdownOpen = false },
                                                                modifier = Modifier
                                                                    .background(Color(0xF012141A))
                                                                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                                                                shape = RoundedCornerShape(16.dp),
                                                                containerColor = Color(0xF012141A)
                                                            ) {
                                                                timeoutOptions.forEach { (key, label) ->
                                                                    DropdownMenuItem(
                                                                        modifier = Modifier.heightIn(min = 48.dp),
                                                                        text = { Text(label) },
                                                                        onClick = {
                                                                            isTimeoutDropdownOpen = false
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

                                                    PrefToggleRow(
                                                        title = "Enable Pixel Shift Burn-In Shield",
                                                        subtitle = "Subtly shifts text and indicators by 2-4px every 2 minutes to protect OLED panels.",
                                                        isChecked = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_PIXEL_SHIFT, true),
                                                        onCheckedChange = {
                                                            prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_PIXEL_SHIFT, it).apply()
                                                            onRefreshNeeded()
                                                        }
                                                    )

                                                    Button(
                                                        onClick = {
                                                            val intent = Intent(context, com.sbf.lightspeed.LightspeedRefuelingActivity::class.java).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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

                                                    // OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts (Demoted)
                                                    if (isWarningDemoted && isInfinixOrTranssion) {
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column(modifier = Modifier.weight(1f)) {
                                                                    Text("OEM Advisory Footnote: OEM Ambient Display / Dock Conflicts", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                                                    Text("OEM standby display style may overlap with Refueling Bay. Tap to manage settings.", fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.65f))
                                                                }
                                                                TextButton(
                                                                    onClick = {
                                                                        try {
                                                                            val intent = Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                            }
                                                                            context.startActivity(intent)
                                                                        } catch (_: Exception) {
                                                                            try {
                                                                                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                                })
                                                                            } catch (_: Exception) {}
                                                                        }
                                                                    },
                                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                                ) {
                                                                    Text("SETTINGS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "config_vault" -> {
                                        item(key = "config_vault") {
                                            CompactAccordionSection(
                                                title = "Configuration Vault",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Lock,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isConfigVaultExpanded,
                                                onToggle = {
                                                    toggleSection(1, "config_vault", isConfigVaultExpanded) { isConfigVaultExpanded = it }
                                                    prefs.edit().putBoolean("pref_section_backup_expanded", isConfigVaultExpanded).apply()
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
                                    "experimental_labs" -> {
                                        item(key = "experimental_labs") {
                                            val cautionAmber = Color(0xFFFFB300)
                                            HazardAccordionSection(
                                                title = "Experimental Labs",
                                                subtitle = "Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
                                                isExpanded = isExperimentalLabsExpanded,
                                                onToggle = {
                                                    toggleSection(1, "experimental_labs", isExperimentalLabsExpanded) { isExperimentalLabsExpanded = it }
                                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, isExperimentalLabsExpanded).apply()
                                                }
                                            ) {
                                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                                    // Maintenance Bay Intro Badge
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = CardDefaults.cardColors(containerColor = cautionAmber.copy(alpha = 0.08f)),
                                                        border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.35f))
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(18.dp))
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Text(
                                                                text = "Experimental Labs: Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
                                                                fontSize = 11.sp,
                                                                color = cautionAmber.copy(alpha = 0.95f),
                                                                lineHeight = 14.sp
                                                            )
                                                        }
                                                    }

                                                    // 1. Core Watchdog
                                                    val sentinelEnabled = prefs.getBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, false)
                                                    val isServiceRunning = LightspeedAccessibilityService.instance != null
                                                    val isSentinelActive = LightspeedWatchdogEngine.isSentinelRunning()

                                                    CollapsibleSubSection(
                                                        title = "Core Watchdog",
                                                        subtitle = "Monitors and revives Lightspeed's accessibility service via Shizuku.",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Shield,
                                                                contentDescription = null,
                                                                tint = cautionAmber,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        trailingBadge = {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFFFF3D00).copy(alpha = 0.18f),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF3D00).copy(alpha = 0.5f))
                                                            ) {
                                                                Text(
                                                                    text = if (isServiceRunning) "ONLINE" else "OFFLINE",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isServiceRunning) Color(0xFF00E676) else Color(0xFFFF3D00),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        },
                                                        isExpanded = isInnerWatchdogExpanded,
                                                        onToggle = {
                                                            isInnerWatchdogExpanded = !isInnerWatchdogExpanded
                                                            prefs.edit().putBoolean("pref_sub_inner_watchdog_labs", isInnerWatchdogExpanded).apply()
                                                        }
                                                    ) {
                                                        // Row 1: Master Enable Toggle
                                                        PrefToggleRow(
                                                            title = "Enable Core Watchdog Sentinel",
                                                            subtitle = "Background sentinel thread polls Lightspeed health every 20s and automatically revives via Shizuku shell if killed by OEM battery management.",
                                                            isChecked = sentinelEnabled,
                                                            onCheckedChange = { checked ->
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_ACCESSIBILITY_SENTINEL_ENABLED, checked).apply()
                                                                if (checked) {
                                                                    LightspeedWatchdogEngine.initSentinel(context)
                                                                } else {
                                                                    LightspeedWatchdogEngine.stopSentinel()
                                                                }
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        // Health Status Badge
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF3D00).copy(alpha = 0.15f)
                                                            ),
                                                            border = androidx.compose.foundation.BorderStroke(
                                                                1.dp,
                                                                if (isServiceRunning) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFF3D00).copy(alpha = 0.5f)
                                                            )
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(
                                                                        imageVector = if (isServiceRunning) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                                        contentDescription = null,
                                                                        tint = if (isServiceRunning) Color(0xFF00E676) else Color(0xFFFF3D00),
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Column {
                                                                        Text(
                                                                            text = if (isServiceRunning) "LIGHTSPEED SERVICE: ONLINE" else "LIGHTSPEED SERVICE: OFFLINE",
                                                                            fontWeight = FontWeight.Bold,
                                                                            fontSize = 11.5.sp,
                                                                            color = if (isServiceRunning) Color(0xFF00E676) else Color(0xFFFF3D00)
                                                                        )
                                                                        Text(
                                                                            text = if (isSentinelActive) "Core Sentinel: Active (20s cycle)" else "Core Sentinel: Standby",
                                                                            fontSize = 10.5.sp,
                                                                            color = Color.LightGray.copy(alpha = 0.75f)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                val ok = LightspeedWatchdogEngine.reviveAccessibilityService(context)
                                                                if (ok) {
                                                                    android.widget.Toast.makeText(context, "Space Watchdog pulse sent via Shizuku", android.widget.Toast.LENGTH_SHORT).show()
                                                                } else {
                                                                    android.widget.Toast.makeText(context, "Shizuku or Root required for service revival", android.widget.Toast.LENGTH_SHORT).show()
                                                                }
                                                            },
                                                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                                                            shape = RoundedCornerShape(10.dp),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.6f))
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(Icons.Default.HealthAndSafety, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(16.dp))
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Text("Trigger Core Sentinel Pulse", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = cautionAmber)
                                                            }
                                                        }
                                                    }

                                                    // 2. Perimeter Watchdog
                                                    val a11yManager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager }
                                                    val pm = context.packageManager
                                                    val installedA11y = remember(isOuterWatchdogExpanded) {
                                                        try {
                                                            a11yManager?.getInstalledAccessibilityServiceList() ?: emptyList()
                                                        } catch (_: Exception) {
                                                            emptyList()
                                                        }
                                                    }
                                                    val enabledA11y = remember(isOuterWatchdogExpanded) {
                                                        try {
                                                            a11yManager?.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK) ?: emptyList()
                                                        } catch (_: Exception) {
                                                            emptyList()
                                                        }
                                                    }
                                                    val enabledPkgSet = remember(enabledA11y) {
                                                        enabledA11y.mapNotNull { it.resolveInfo?.serviceInfo?.packageName }.toSet()
                                                    }
                                                    val thirdPartyServices = remember(installedA11y) {
                                                        installedA11y.filter { it.resolveInfo?.serviceInfo?.packageName != context.packageName }
                                                    }
                                                    val isShizukuActive = com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive

                                                    CollapsibleSubSection(
                                                        title = "Perimeter Watchdog",
                                                        subtitle = "Monitors and reports status on third-party accessibility sentinels.",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.Security,
                                                                contentDescription = null,
                                                                tint = cautionAmber,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        trailingBadge = {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.18f) else Color(0xFFFF9800).copy(alpha = 0.18f),
                                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.5f) else Color(0xFFFF9800).copy(alpha = 0.5f))
                                                            ) {
                                                                Text(
                                                                    text = if (isShizukuActive) "ACTIVE" else "STANDBY",
                                                                    fontSize = 9.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (isShizukuActive) Color(0xFF00E676) else Color(0xFFFF9800),
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        },
                                                        isExpanded = isOuterWatchdogExpanded,
                                                        onToggle = {
                                                            isOuterWatchdogExpanded = !isOuterWatchdogExpanded
                                                            prefs.edit().putBoolean("pref_sub_outer_watchdog_labs", isOuterWatchdogExpanded).apply()
                                                        }
                                                    ) {
                                                        // Row 1: Shizuku Status Banner
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                            shape = RoundedCornerShape(12.dp),
                                                            colors = CardDefaults.cardColors(
                                                                containerColor = if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.12f) else Color(0xFFFF9800).copy(alpha = 0.12f)
                                                            ),
                                                            border = androidx.compose.foundation.BorderStroke(
                                                                1.dp,
                                                                if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.4f) else Color(0xFFFF9800).copy(alpha = 0.4f)
                                                            )
                                                        ) {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Icon(
                                                                    imageVector = if (isShizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
                                                                    contentDescription = null,
                                                                    tint = if (isShizukuActive) Color(0xFF00E676) else Color(0xFFFF9800),
                                                                    modifier = Modifier.size(18.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(8.dp))
                                                                Column {
                                                                    Text(
                                                                        text = if (isShizukuActive) "SHIZUKU PRIVILEGED BRIDGE: ACTIVE" else "SHIZUKU PRIVILEGED BRIDGE: STANDBY",
                                                                        fontWeight = FontWeight.Bold,
                                                                        fontSize = 11.sp,
                                                                        color = if (isShizukuActive) Color(0xFF00E676) else Color(0xFFFF9800)
                                                                    )
                                                                    Text(
                                                                        text = if (isShizukuActive) "Ready to monitor & report external accessibility sentinels" else "Requires Shizuku authorization for outer sentinel management",
                                                                        fontSize = 10.sp,
                                                                        color = Color.LightGray.copy(alpha = 0.75f)
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (thirdPartyServices.isEmpty()) {
                                                            Text(
                                                                text = "No third-party accessibility services detected.",
                                                                fontSize = 11.5.sp,
                                                                color = Color.LightGray.copy(alpha = 0.6f),
                                                                modifier = Modifier.padding(vertical = 8.dp)
                                                            )
                                                        } else {
                                                            Column(
                                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                                                modifier = Modifier.padding(top = 4.dp)
                                                            ) {
                                                                thirdPartyServices.forEach { sInfo ->
                                                                    val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: "unknown"
                                                                    val sLabel = try {
                                                                        sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                                                                    } catch (_: Exception) { sPkg }
                                                                    val isServiceEnabled = enabledPkgSet.contains(sPkg)

                                                                    Surface(
                                                                        modifier = Modifier.fillMaxWidth(),
                                                                        shape = RoundedCornerShape(10.dp),
                                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                                    ) {
                                                                        Row(
                                                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                                                            verticalAlignment = Alignment.CenterVertically,
                                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                                        ) {
                                                                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                                                                Text(sLabel, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White, maxLines = 1)
                                                                                Text(sPkg, fontSize = 10.sp, color = Color.Gray, maxLines = 1)
                                                                            }
                                                                            Surface(
                                                                                shape = RoundedCornerShape(6.dp),
                                                                                color = if (isServiceEnabled) Color(0xFF00E676).copy(alpha = 0.18f) else Color.Gray.copy(alpha = 0.2f),
                                                                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isServiceEnabled) Color(0xFF00E676).copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f))
                                                                            ) {
                                                                                Text(
                                                                                    text = if (isServiceEnabled) "ENABLED" else "DISABLED",
                                                                                    fontSize = 9.sp,
                                                                                    fontWeight = FontWeight.Bold,
                                                                                    color = if (isServiceEnabled) Color(0xFF00E676) else Color.LightGray,
                                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                                )
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }

                                                    // 3. Core Cooling Schedule
                                                    var isCoreCoolingExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_core_cooling_labs", true)) }
                                                    CollapsibleSubSection(
                                                        title = "Core Cooling Schedule",
                                                        subtitle = "Configurable weekly maintenance reminder",
                                                        icon = {
                                                            Icon(
                                                                imageVector = Icons.Outlined.AcUnit,
                                                                contentDescription = null,
                                                                tint = cautionAmber,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        },
                                                        isExpanded = isCoreCoolingExpanded,
                                                        onToggle = {
                                                            isCoreCoolingExpanded = !isCoreCoolingExpanded
                                                            prefs.edit().putBoolean("pref_sub_core_cooling_labs", isCoreCoolingExpanded).apply()
                                                        }
                                                    ) {
                                                        val coreCoolingEnabled = prefs.getBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, false)
                                                        var targetDay by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, java.util.Calendar.SUNDAY)) }
                                                        var targetHour by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, 3)) }

                                                        PrefToggleRow(
                                                            title = "Scheduled Core Cooling Reminder",
                                                            subtitle = "Dispatches a gentle reminder when system core cooling is recommended.",
                                                            isChecked = coreCoolingEnabled,
                                                            onCheckedChange = { checked ->
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, checked).apply()
                                                                onRefreshNeeded()
                                                            }
                                                        )

                                                        if (coreCoolingEnabled) {
                                                            CoreCoolingRotarySchedulePicker(
                                                                selectedDay = targetDay,
                                                                selectedHour = targetHour,
                                                                onScheduleChanged = { day, hour ->
                                                                    targetDay = day
                                                                    targetHour = hour
                                                                    prefs.edit()
                                                                        .putInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, day)
                                                                        .putInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, hour)
                                                                        .apply()
                                                                    onRefreshNeeded()
                                                                }
                                                            )
                                                        }

                                                        // Strict Cold-Start Policy Notice (Strictly Placed Inside Core Cooling at Bottom)
                                                        Card(
                                                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                            shape = RoundedCornerShape(10.dp),
                                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
                                                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                                        ) {
                                                            Text(
                                                                text = "STRICT COLD-START POLICY: Zero automatic reboots. When weekly cooling cycle is reached, dispatches a silent status reminder to the HUD and Orbital Capsule.",
                                                                fontSize = 10.5.sp,
                                                                color = Color.LightGray.copy(alpha = 0.8f),
                                                                modifier = Modifier.padding(10.dp),
                                                                lineHeight = 13.sp
                                                            )
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
                }

                // PAGE 2: RIGHT DEFLECTOR
                2 -> {
                    val defaultOrder2 = if (isRightFlankUnified) listOf("unified") else listOf("center", "top", "bottom")
                    val currentOrder2 = sectionOrder2Str.split(",").map { it.trim() }.filter { it in defaultOrder2 }.distinct().let { list ->
                        list + (defaultOrder2 - list.toSet())
                    }

                    if (blueprintTabTarget == 2) {
                        BlueprintWireframeView(
                            tabTitle = "Deflectors ▶",
                            sectionIds = currentOrder2,
                            pinnedSectionId = pinnedSection2,
                            sectionTitles = sectionTitles2,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder2.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder2Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder2.size - 1) {
                                    val mutable = currentOrder2.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder2Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection2 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            }
                        )
                    } else {
                        LazyColumn(
                            state = listState2,
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
                                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", false).commit()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    },
                                    onInfoClick = { showUnifyInfoDialog = true }
                                )
                            }

                            currentOrder2.forEach { secId ->
                                when (secId) {
                                    "center" -> {
                                        item(key = "center") {
                                            CompactAccordionSection(
                                                title = "Right Deflector — Astrogation Core Zone",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Navigation,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isCenterExpanded,
                                                onToggle = {
                                                    toggleSection(2, "center", isCenterExpanded) { isCenterExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_center_expanded", isCenterExpanded)
                                                        .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || isBottomExpanded || isRightUnifiedExpanded)
                                                        .apply()
                                                }
                                            ) {
                                                 Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    CollapsibleSubSection(
                                                        title = "Sensor Geometry",
                                                        subtitle = "Deflector span, touch reach, offset & stealth glow",
                                                        isExpanded = isCenterGeoExpanded,
                                                        onToggle = {
                                                            isCenterGeoExpanded = !isCenterGeoExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_geo_center", isCenterGeoExpanded)
                                                                .putBoolean("pref_sidebar_preview", isCenterExpanded && isCenterGeoExpanded)
                                                                .apply()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "unified" -> {
                                        if (isRightFlankUnified) {
                                            item(key = "unified") {
                                                CompactAccordionSection(
                                                    title = "Right Deflector — Flank Vector Zones (Upper & Lower)",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.SwapVert,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isRightUnifiedExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "unified", isRightUnifiedExpanded) { isRightUnifiedExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_right_unified_expanded", isRightUnifiedExpanded)
                                                            .putBoolean("pref_sidebar_preview", (isCenterExpanded && isCenterGeoExpanded) || (isRightUnifiedExpanded && isRightUnifiedGeoExpanded))
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Independent height, touch reach & stealth glow",
                                                            isExpanded = isRightUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_unified", isRightUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isRightUnifiedExpanded && isRightUnifiedGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            Text("UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Upper Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Lower Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Dual Inward Scrubber Controls",
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

                                                        CollapsibleSubSection(
                                                            title = "Unified Gesture Matrix",
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
                                        }
                                    }
                                    "top" -> {
                                        if (!isRightFlankUnified) {
                                            item(key = "top") {
                                                CompactAccordionSection(
                                                    title = "Right Deflector — Upper Vector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isTopExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "top", isTopExpanded) { isTopExpanded = it }
                                                        val newTop = isTopExpanded
                                                        prefs.edit()
                                                            .putBoolean("pref_section_top_expanded", newTop)
                                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || newTop || isBottomExpanded)
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isTopGeoExpanded,
                                                            onToggle = {
                                                                isTopGeoExpanded = !isTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_top", isTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isTopExpanded && isTopGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isTopScrubExpanded,
                                                            onToggle = {
                                                                isTopScrubExpanded = !isTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_top", isTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
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
                                        }
                                    }
                                    "bottom" -> {
                                        if (!isRightFlankUnified) {
                                            item(key = "bottom") {
                                                CompactAccordionSection(
                                                    title = "Right Deflector — Lower Vector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isBottomExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "bottom", isBottomExpanded) { isBottomExpanded = it }
                                                        val newBottom = isBottomExpanded
                                                        prefs.edit()
                                                            .putBoolean("pref_section_bottom_expanded", newBottom)
                                                            .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || newBottom)
                                                            .apply()
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isBottomGeoExpanded,
                                                            onToggle = {
                                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isBottomExpanded && isBottomGeoExpanded)
                                                                    .apply()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isBottomScrubExpanded,
                                                            onToggle = {
                                                                isBottomScrubExpanded = !isBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_bottom", isBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
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
                            prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", true).commit()
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
                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", true).commit()
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

        if (popoverTabTarget != null) {
            val tabId = popoverTabTarget!!
            val tabTitle = when (tabId) {
                0 -> "◀ Deflectors"
                1 -> "HUD STRIP"
                2 -> "Deflectors ▶"
                else -> "Avionics Tab"
            }
            val currentMode = when (tabId) {
                0 -> tabMode0
                1 -> tabMode1
                2 -> tabMode2
                else -> "sticky"
            }
            val currentPinned = when (tabId) {
                0 -> pinnedSection0
                1 -> pinnedSection1
                2 -> pinnedSection2
                else -> null
            }
            val secTitles = when (tabId) {
                0 -> sectionTitles0
                1 -> sectionTitles1
                2 -> sectionTitles2
                else -> emptyMap()
            }

            TabAccordionPopover(
                tabIndex = tabId,
                tabTitle = tabTitle,
                currentMode = currentMode,
                pinnedSectionId = currentPinned,
                sectionTitles = secTitles,
                onSelectMode = { newMode: String ->
                    when (tabId) {
                        0 -> {
                            tabMode0 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, newMode).apply()
                        }
                        1 -> {
                            tabMode1 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, newMode).apply()
                        }
                        2 -> {
                            tabMode2 = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, newMode).apply()
                        }
                    }
                    popoverTabTarget = null
                    onRefreshNeeded()
                },
                onToggleBlueprintMode = {
                    blueprintTabTarget = tabId
                },
                onDismiss = {
                    popoverTabTarget = null
                }
            )
        }
    }
}

@Composable
fun TabAccordionPopover(
    tabIndex: Int,
    tabTitle: String,
    currentMode: String,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onSelectMode: (String) -> Unit,
    onToggleBlueprintMode: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("$tabTitle Blueprint", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Select default accordion display behavior for this tab:",
                    fontSize = 12.sp,
                    color = Color.LightGray.copy(alpha = 0.9f)
                )

                val modes = listOf(
                    Triple("sticky", "Remember Last State (Sticky)", "Preserve the exact open and collapsed states of each section across app restarts."),
                    Triple("custom_pinned", "Anchored Solo", "Designated anchor card stays open; non-pinned cards swap in Solo focus mode."),
                    Triple("solo", "Focus / Solo Mode", "Expanding any card automatically snaps all other cards shut."),
                    Triple("all_expanded", "All Expanded", "All accordion cards default open on tab entry."),
                    Triple("all_collapsed", "All Collapsed", "All accordion cards default closed on tab entry.")
                )

                modes.forEach { (modeKey, title, desc) ->
                    val isSelected = currentMode == modeKey
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSelectMode(modeKey)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onSelectMode(modeKey) },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                    if (modeKey == "custom_pinned" && pinnedSectionId != null) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = "Anchor: ${sectionTitles[pinnedSectionId] ?: pinnedSectionId}",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(desc, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.75f), lineHeight = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        onToggleBlueprintMode()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.DashboardCustomize, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Edit Section Blueprint & Reorder", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = Color(0xFF10121C)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttitudeAppAssignmentSheet(
    context: Context,
    bucket: LightspeedOrientationEngine.AttitudeBucket,
    onDismiss: () -> Unit,
    onUpdated: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val assignedPackages = remember(bucket) {
        mutableStateListOf<String>().apply {
            addAll(LightspeedOrientationEngine.getAssignedPackages(context, bucket))
        }
    }

    val installedApps = remember {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        pm.queryIntentActivities(intent, 0).map {
            val pkg = it.activityInfo.packageName
            val label = it.loadLabel(pm).toString()
            Pair(pkg, label)
        }.distinctBy { it.first }.sortedBy { it.second.lowercase(Locale.ROOT) }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.second.contains(searchQuery, ignoreCase = true) || it.first.contains(searchQuery, ignoreCase = true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF10121C),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (bucket) {
                        LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT -> Icons.Default.ScreenRotationAlt
                        LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE -> Icons.Default.StayCurrentLandscape
                        else -> Icons.Default.ScreenRotation
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Assign Apps: ${bucket.title}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = bucket.subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(count = filteredApps.size, key = { filteredApps[it].first }) { index ->
                    val app = filteredApps[index]
                    val pkg = app.first
                    val label = app.second
                    val isAssigned = assignedPackages.contains(pkg)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (isAssigned) assignedPackages.remove(pkg)
                                else assignedPackages.add(pkg)
                                LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                onUpdated()
                            },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isAssigned) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isAssigned,
                                onCheckedChange = { checked ->
                                    if (checked) assignedPackages.add(pkg)
                                    else assignedPackages.remove(pkg)
                                    LightspeedOrientationEngine.setAssignedPackages(context, bucket, assignedPackages.toSet())
                                    onUpdated()
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color.White)
                                Text(pkg, fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlueprintWireframeView(
    tabTitle: String,
    sectionIds: List<String>,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onPinSection: (String) -> Unit,
    onExitBlueprint: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$tabTitle — Blueprint Reorder Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Use ▲ / ▼ to reorder sections. Tap 📌 to designate the anchor open section.", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.85f))
                }
            }
        }

        sectionIds.forEachIndexed { index, secId ->
            val isPinned = (secId == pinnedSectionId)
            val title = sectionTitles[secId] ?: secId

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF141724).copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag Handle",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        if (isPinned) {
                            Text(
                                "📌 Default Pinned Section",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Pin Anchor Button
                    IconButton(
                        onClick = { onPinSection(secId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Up
                    IconButton(
                        onClick = { onMoveUp(index) },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Down
                    IconButton(
                        onClick = { onMoveDown(index) },
                        enabled = index < sectionIds.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            tint = if (index < sectionIds.size - 1) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = onExitBlueprint,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Done (Exit Blueprint)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
