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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.text.style.TextOverflow
import android.content.Intent
import com.sbf.lightspeed.LightspeedAccessibilityService
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedBackupEngine
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedKeyEngine
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.OemNotchDetector
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CentralCommandMatrixFields(
    context: Context,
    prefs: SharedPreferences,
    viewModel: CentralCommandViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    toggleAllTrigger: Int = 0,
    jumpTargetTab: Int = -1,
    jumpTargetSection: String? = null,
    onRefreshNeeded: () -> Unit = {}
) {
    val importStatusMessage by viewModel.importStatusMessage.collectAsState()
    val isImportSuccess by viewModel.isImportSuccess.collectAsState()
    val currentLanguageMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()
    val prefsVersionState = remember { mutableIntStateOf(0) }
    var prefsVersion by prefsVersionState
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            prefsVersion++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    // Tab Display Profiles & Blueprint State
    val tabMode0State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, "custom_pinned") ?: "custom_pinned") }
    var tabMode0 by tabMode0State
    val pinnedSection0State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, "left_top") ?: "left_top") }
    var pinnedSection0 by pinnedSection0State
    val sectionOrder0StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, "left_center,left_top,left_bottom") ?: "left_center,left_top,left_bottom") }
    var sectionOrder0Str by sectionOrder0StrState

    val tabMode1State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, "custom_pinned") ?: "custom_pinned") }
    var tabMode1 by tabMode1State
    val pinnedSection1State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, "sensor_deck") ?: "sensor_deck") }
    var pinnedSection1 by pinnedSection1State
    val sectionOrder1StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, "sensor_deck,synthetic_gravity,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") ?: "sensor_deck,synthetic_gravity,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") }
    var sectionOrder1Str by sectionOrder1StrState

    val tabMode2State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, "custom_pinned") ?: "custom_pinned") }
    var tabMode2 by tabMode2State
    val pinnedSection2State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, "top") ?: "top") }
    var pinnedSection2 by pinnedSection2State
    val sectionOrder2StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, "center,top,bottom") ?: "center,top,bottom") }
    var sectionOrder2Str by sectionOrder2StrState

    val popoverTabTargetState = rememberSaveable { mutableStateOf<Int?>(null) }
    var popoverTabTarget by popoverTabTargetState
    val blueprintTabTargetState = rememberSaveable { mutableStateOf<Int?>(null) }
    var blueprintTabTarget by blueprintTabTargetState

    // Left Deflector Accordion States
    val isLeftCenterExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_center" else prefs.getBoolean("pref_section_left_center_expanded", false)) }
    var isLeftCenterExpanded by isLeftCenterExpandedState
    val isLeftTopExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_top" else prefs.getBoolean("pref_section_left_top_expanded", true)) }
    var isLeftTopExpanded by isLeftTopExpandedState
    val isLeftBottomExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_bottom" else prefs.getBoolean("pref_section_left_bottom_expanded", false)) }
    var isLeftBottomExpanded by isLeftBottomExpandedState
    val isLeftFlankUnifiedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)) }
    var isLeftFlankUnified by isLeftFlankUnifiedState
    val isLeftUnifiedExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_unified" else prefs.getBoolean("pref_section_left_unified_expanded", false)) }
    var isLeftUnifiedExpanded by isLeftUnifiedExpandedState

    // Left Deflector Sub-Section States
    val isLeftCenterGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_center", true)) }
    var isLeftCenterGeoExpanded by isLeftCenterGeoExpandedState

    val isLeftUnifiedGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_unified", true)) }
    var isLeftUnifiedGeoExpanded by isLeftUnifiedGeoExpandedState
    val isLeftUnifiedScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_unified", true)) }
    var isLeftUnifiedScrubExpanded by isLeftUnifiedScrubExpandedState
    val isLeftUnifiedGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_unified", true)) }
    var isLeftUnifiedGesturesExpanded by isLeftUnifiedGesturesExpandedState

    val isLeftTopGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_top", true)) }
    var isLeftTopGeoExpanded by isLeftTopGeoExpandedState
    val isLeftTopScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_top", true)) }
    var isLeftTopScrubExpanded by isLeftTopScrubExpandedState
    val isLeftTopGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_top", true)) }
    var isLeftTopGesturesExpanded by isLeftTopGesturesExpandedState

    val isLeftBottomGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_bottom", true)) }
    var isLeftBottomGeoExpanded by isLeftBottomGeoExpandedState
    val isLeftBottomScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_left_bottom", true)) }
    var isLeftBottomScrubExpanded by isLeftBottomScrubExpandedState
    val isLeftBottomGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_left_bottom", true)) }
    var isLeftBottomGesturesExpanded by isLeftBottomGesturesExpandedState

    // HUD Strip Accordion States
    val isSensorDeckExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "sensor_deck" else prefs.getBoolean("pref_section_statusbar_expanded", true)) }
    var isSensorDeckExpanded by isSensorDeckExpandedState
    val isSyntheticGravityExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "synthetic_gravity" else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, false)) }
    var isSyntheticGravityExpanded by isSyntheticGravityExpandedState
    val isTelemetryExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "telemetry_indicators" else prefs.getBoolean("pref_section_telemetry_expanded", false)) }
    var isTelemetryExpanded by isTelemetryExpandedState
    val isTacticalHardwareExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "tactical_hardware" else prefs.getBoolean("pref_section_tactical_hardware_expanded", false)) }
    var isTacticalHardwareExpanded by isTacticalHardwareExpandedState
    val isRefuelingExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "refueling_bay" else prefs.getBoolean("pref_section_refueling_expanded", false)) }
    var isRefuelingExpanded by isRefuelingExpandedState
    val isConfigVaultExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "config_vault" else prefs.getBoolean("pref_section_backup_expanded", false)) }
    var isConfigVaultExpanded by isConfigVaultExpandedState
    val isExperimentalLabsExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "experimental_labs" else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, false)) }
    var isExperimentalLabsExpanded by isExperimentalLabsExpandedState

    // Power Button Safety Interlock & Dynamic Tools
    val singlePressTapCountState = rememberSaveable { mutableIntStateOf(0) }
    var singlePressTapCount by singlePressTapCountState
    val isSinglePressUnlockedState = rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, false)) }
    var isSinglePressUnlocked by isSinglePressUnlockedState
    val installedTacticalTools by produceState(initialValue = emptyList<com.sbf.lightspeed.system.TacticalToolItem>()) {
        value = withContext(Dispatchers.IO) {
            com.sbf.lightspeed.system.InstalledTacticalToolsScanner.scan(context)
        }
    }

    // Tactical Hardware Deck Sub-Sections
    val isSubVolumeExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_volume_expanded", true)) }
    var isSubVolumeExpanded by isSubVolumeExpandedState
    val isSubPowerExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_power_expanded", true)) }
    var isSubPowerExpanded by isSubPowerExpandedState
    val isSubHullTapExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_hulltap_expanded", false)) }
    var isSubHullTapExpanded by isSubHullTapExpandedState

    val showOemShieldDialogState = rememberSaveable { mutableStateOf(false) }
    var showOemShieldDialog by showOemShieldDialogState
    val showBatteryWarningDialogState = rememberSaveable { mutableStateOf(false) }
    var showBatteryWarningDialog by showBatteryWarningDialogState
    val showNotificationAccessDialogState = rememberSaveable { mutableStateOf(false) }
    var showNotificationAccessDialog by showNotificationAccessDialogState
    val showAmoledWarningDialogState = rememberSaveable { mutableStateOf(false) }
    var showAmoledWarningDialog by showAmoledWarningDialogState
    val pendingBackTapScopeState = rememberSaveable { mutableStateOf("screen_on") }
    var pendingBackTapScope by pendingBackTapScopeState

    val isStatusBarGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_statusbar", false)) }
    var isStatusBarGeoExpanded by isStatusBarGeoExpandedState
    val isStatusBarScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_statusbar", false)) }
    var isStatusBarScrubExpanded by isStatusBarScrubExpandedState
    val isStatusBarGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_statusbar", false)) }
    var isStatusBarGesturesExpanded by isStatusBarGesturesExpandedState
    val isHorizonRailGeomExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_geom", false)) }
    var isHorizonRailGeomExpanded by isHorizonRailGeomExpandedState
    val isHorizonRailColorExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_color", false)) }
    var isHorizonRailColorExpanded by isHorizonRailColorExpandedState
    val isHorizonRailTextExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_text", false)) }
    var isHorizonRailTextExpanded by isHorizonRailTextExpandedState
    val isNotchCalibExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_calib", false)) }
    var isNotchCalibExpanded by isNotchCalibExpandedState
    val isMarqueeSubSectionExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_marquee", false)) }
    var isMarqueeSubSectionExpanded by isMarqueeSubSectionExpandedState
    val oemFeatureName = remember { OemNotchDetector.getDetectedFeatureName() }
    val isOemNoticeDemotedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_oem_notch_notice_demoted", false)) }
    var isOemNoticeDemoted by isOemNoticeDemotedState

    // Live Impulse Calibration Meter State (Hull Tap)
    val currentZImpulseState = remember { mutableFloatStateOf(0f) }
    var currentZImpulse by currentZImpulseState
    val currentThresholdState = remember { mutableFloatStateOf(LightspeedBackTapEngine.getThreshold(context)) }
    var currentThreshold by currentThresholdState
    val thresholdCrossedFlashState = remember { mutableStateOf(false) }
    var thresholdCrossedFlash by thresholdCrossedFlashState

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
    val isCenterExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "center" else prefs.getBoolean("pref_section_center_expanded", false)) }
    var isCenterExpanded by isCenterExpandedState
    val isTopExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "top" else prefs.getBoolean("pref_section_top_expanded", true)) }
    var isTopExpanded by isTopExpandedState
    val isBottomExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "bottom" else prefs.getBoolean("pref_section_bottom_expanded", false)) }
    var isBottomExpanded by isBottomExpandedState
    val isRightFlankUnifiedState = rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, false)) }
    var isRightFlankUnified by isRightFlankUnifiedState
    val isRightUnifiedExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "unified" else prefs.getBoolean("pref_section_right_unified_expanded", false)) }
    var isRightUnifiedExpanded by isRightUnifiedExpandedState

    // Dual Watchdog Sub-Section States
    val isInnerWatchdogExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_inner_watchdog_labs", true)) }
    var isInnerWatchdogExpanded by isInnerWatchdogExpandedState
    val isOuterWatchdogExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_outer_watchdog_labs", true)) }
    var isOuterWatchdogExpanded by isOuterWatchdogExpandedState

    // Right Deflector Sub-Section States
    val isCenterGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_center", true)) }
    var isCenterGeoExpanded by isCenterGeoExpandedState

    val isRightUnifiedGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_right_unified", true)) }
    var isRightUnifiedGeoExpanded by isRightUnifiedGeoExpandedState
    val isRightUnifiedScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_right_unified", true)) }
    var isRightUnifiedScrubExpanded by isRightUnifiedScrubExpandedState
    val isRightUnifiedGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_right_unified", true)) }
    var isRightUnifiedGesturesExpanded by isRightUnifiedGesturesExpandedState

    val isTopGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_top", true)) }
    var isTopGeoExpanded by isTopGeoExpandedState
    val isTopScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_top", true)) }
    var isTopScrubExpanded by isTopScrubExpandedState
    val isTopGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_top", true)) }
    var isTopGesturesExpanded by isTopGesturesExpandedState

    val isBottomGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_bottom", true)) }
    var isBottomGeoExpanded by isBottomGeoExpandedState
    val isBottomScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_bottom", true)) }
    var isBottomScrubExpanded by isBottomScrubExpandedState
    val isBottomGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_bottom", true)) }
    var isBottomGesturesExpanded by isBottomGesturesExpandedState

    val listState0 = rememberLazyListState()
    val listState1 = rememberLazyListState()
    val listState2 = rememberLazyListState()

    val showUnifyInfoDialogState = remember { mutableStateOf(false) }
    var showUnifyInfoDialog by showUnifyInfoDialogState
    val showSymmetryInfoDialogState = remember { mutableStateOf(false) }
    var showSymmetryInfoDialog by showSymmetryInfoDialogState
    val showPasteJsonDialogState = remember { mutableStateOf(false) }
    var showPasteJsonDialog by showPasteJsonDialogState
    val pastedJsonTextState = remember { mutableStateOf("") }
    var pastedJsonText by pastedJsonTextState
    val selectedTemplateOptionState = remember { mutableIntStateOf(0) }
    var selectedTemplateOption by selectedTemplateOptionState

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
            if (key.startsWith("pref_macro_hud_style_${sourcePrefix}_")) {
                val suffix = key.removePrefix("pref_macro_hud_style_${sourcePrefix}_")
                val targetKey = "pref_macro_hud_style_${targetPrefix}_$suffix"
                if (value is String) {
                    editor.putString(targetKey, value)
                }
            }
        }
        editor.apply()
    }

    val showUnifyTemplateDialogForLeftState = remember { mutableStateOf(false) }
    var showUnifyTemplateDialogForLeft by showUnifyTemplateDialogForLeftState
    val showUnifyTemplateDialogForRightState = remember { mutableStateOf(false) }
    var showUnifyTemplateDialogForRight by showUnifyTemplateDialogForRightState
    val showImportOptionsDialogState = remember { mutableStateOf(false) }
    var showImportOptionsDialog by showImportOptionsDialogState
    val showResetConfirmDialogState = remember { mutableStateOf(false) }
    var showResetConfirmDialog by showResetConfirmDialogState

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
            viewModel.importFromFile(context, uri)
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

    val sectionTitles0 = remember(isLeftFlankUnified, currentLanguageMode) {
        if (isLeftFlankUnified) {
            mapOf("left_unified" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Flank Vector Zones (Upper & Lower)")
        } else {
            mapOf(
                "left_center" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Astrogation Core Zone",
                "left_top" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Upper Vector Zone",
                "left_bottom" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LEFT_DEFLECTOR)} — Lower Vector Zone"
            )
        }
    }

    val sectionTitles1 = remember(currentLanguageMode) {
        mapOf(
            "sensor_deck" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SENSOR_AREA),
            "synthetic_gravity" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.GRAVITY_ENGINE),
            "telemetry_indicators" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS),
            "tactical_hardware" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.TACTICAL_HARDWARE),
            "refueling_bay" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.REFUELING_BAY),
            "config_vault" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.SHIP_DATA_VAULT),
            "experimental_labs" to com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.EXPERIMENTAL_LABS)
        )
    }

    val sectionTitles2 = remember(isRightFlankUnified, currentLanguageMode) {
        if (isRightFlankUnified) {
            mapOf("unified" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Flank Vector Zones (Upper & Lower)")
        } else {
            mapOf(
                "center" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Astrogation Core Zone",
                "top" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Upper Vector Zone",
                "bottom" to "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Lower Vector Zone"
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
                        isSyntheticGravityExpanded = false
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
                            if (pinned != "synthetic_gravity") isSyntheticGravityExpanded = false
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

    val allExpandedStateState = remember { mutableStateOf(false) }
    var allExpandedState by allExpandedStateState
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
                    isSyntheticGravityExpanded = allExpandedState
                    isTelemetryExpanded = allExpandedState
                    isTacticalHardwareExpanded = allExpandedState
                    isRefuelingExpanded = allExpandedState
                    isConfigVaultExpanded = allExpandedState
                    isExperimentalLabsExpanded = allExpandedState
                    val anyRail = isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded
                    val notchActive = allExpandedState && isNotchCalibExpanded
                    val railActive = allExpandedState && anyRail
                    prefs.edit()
                        .putBoolean("pref_section_statusbar_expanded", allExpandedState)
                        .putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, allExpandedState)
                        .putBoolean("pref_section_telemetry_expanded", allExpandedState)
                        .putBoolean("pref_section_tactical_hardware_expanded", allExpandedState)
                        .putBoolean("pref_section_refueling_expanded", allExpandedState)
                        .putBoolean("pref_section_backup_expanded", allExpandedState)
                        .putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, allExpandedState)
                        .putBoolean("pref_statusbar_preview", allExpandedState && isStatusBarGeoExpanded)
                        .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, notchActive)
                        .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, railActive)
                        .apply()
                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
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
            "gravity", "synthetic_gravity", "orientation", "attitude" -> isSyntheticGravityExpanded = true
            "telemetry", "telemetry_indicators", "beacons", "notch_beacon" -> isTelemetryExpanded = true
            "volumekeys", "tactical_hardware", "backtap", "maneuvers", "hardware" -> isTacticalHardwareExpanded = true
            "refueling", "refueling_bay" -> isRefuelingExpanded = true
            "backup", "config_vault", "vault", "shizuku_jettison" -> isConfigVaultExpanded = true
            "experimental_labs", "labs", "experimental", "core_cooling" -> isExperimentalLabsExpanded = true
            "power", "power_button" -> {
                isExperimentalLabsExpanded = true
                isSubPowerExpanded = true
            }
            "orbital", "orbital_capsule", "notch" -> {
                isTelemetryExpanded = true
                isNotchCalibExpanded = true
            }
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
                val anyRail = isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded
                val notchActive = page == 1 && isTelemetryExpanded && isNotchCalibExpanded
                val railActive = page == 1 && isTelemetryExpanded && anyRail
                prefs.edit()
                    .putBoolean("pref_sidebar_left_preview", leftActive)
                    .putBoolean("pref_statusbar_preview", canopyActive)
                    .putBoolean("pref_sidebar_preview", rightActive)
                    .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, notchActive)
                    .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, railActive)
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
            val deflStr = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.DEFLECTORS).replace("Left & Right ", ""); val hudStr = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.HUD_STRIP).uppercase(); val tabTitles = listOf("◀ $deflStr", hudStr, "$deflStr ▶")
            tabTitles.forEachIndexed { index, tabTitle ->
                val isSelected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (isSelected) {
                                    LightspeedHapticEngine.heavyClick(context)
                                    popoverTabTarget = index
                                } else {
                                    scope.launch { pagerState.animateScrollToPage(index) }
                                }
                            },
                            onLongClick = {
                                LightspeedHapticEngine.heavyClick(context)
                                popoverTabTarget = index
                            }
                        )
                        .padding(vertical = 7.dp, horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
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
                        if (isSelected) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(width = 14.dp, height = 2.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                            )
                        }
                    }
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
                    LeftDeflectorTabContent(
        blueprintTabTargetState = blueprintTabTargetState,
        context = context,
        dynamicActionTokens = dynamicActionTokens,
        isLeftBottomExpandedState = isLeftBottomExpandedState,
        isLeftBottomGeoExpandedState = isLeftBottomGeoExpandedState,
        isLeftBottomGesturesExpandedState = isLeftBottomGesturesExpandedState,
        isLeftBottomScrubExpandedState = isLeftBottomScrubExpandedState,
        isLeftCenterExpandedState = isLeftCenterExpandedState,
        isLeftCenterGeoExpandedState = isLeftCenterGeoExpandedState,
        isLeftFlankUnifiedState = isLeftFlankUnifiedState,
        isLeftTopExpandedState = isLeftTopExpandedState,
        isLeftTopGeoExpandedState = isLeftTopGeoExpandedState,
        isLeftTopGesturesExpandedState = isLeftTopGesturesExpandedState,
        isLeftTopScrubExpandedState = isLeftTopScrubExpandedState,
        isLeftUnifiedExpandedState = isLeftUnifiedExpandedState,
        isLeftUnifiedGeoExpandedState = isLeftUnifiedGeoExpandedState,
        isLeftUnifiedGesturesExpandedState = isLeftUnifiedGesturesExpandedState,
        isLeftUnifiedScrubExpandedState = isLeftUnifiedScrubExpandedState,
        listState0 = listState0,
        onRefreshNeeded = onRefreshNeeded,
        pinnedSection0State = pinnedSection0State,
        prefs = prefs,
        sectionOrder0StrState = sectionOrder0StrState,
        sectionTitles0 = sectionTitles0,
        showUnifyInfoDialogState = showUnifyInfoDialogState,
        showUnifyTemplateDialogForLeftState = showUnifyTemplateDialogForLeftState,
        toggleSection = ::toggleSection,
        tokenLabelCache = tokenLabelCache
                    )
                }

                // PAGE 1: HUD STRIP
                1 -> {
                    HudStripTabContent(
        blueprintTabTargetState = blueprintTabTargetState,
        context = context,
        currentThresholdState = currentThresholdState,
        currentZImpulseState = currentZImpulseState,
        dynamicActionTokens = dynamicActionTokens,
        isConfigVaultExpandedState = isConfigVaultExpandedState,
        isExperimentalLabsExpandedState = isExperimentalLabsExpandedState,
        isHorizonRailColorExpandedState = isHorizonRailColorExpandedState,
        isHorizonRailGeomExpandedState = isHorizonRailGeomExpandedState,
        isHorizonRailTextExpandedState = isHorizonRailTextExpandedState,
        isMarqueeSubSectionExpandedState = isMarqueeSubSectionExpandedState,
        isNotchCalibExpandedState = isNotchCalibExpandedState,
        isOemNoticeDemotedState = isOemNoticeDemotedState,
        isRefuelingExpandedState = isRefuelingExpandedState,
        isSensorDeckExpandedState = isSensorDeckExpandedState,
        isSinglePressUnlockedState = isSinglePressUnlockedState,
        isStatusBarGeoExpandedState = isStatusBarGeoExpandedState,
        isStatusBarGesturesExpandedState = isStatusBarGesturesExpandedState,
        isSubHullTapExpandedState = isSubHullTapExpandedState,
        isSubPowerExpandedState = isSubPowerExpandedState,
        isSubVolumeExpandedState = isSubVolumeExpandedState,
        isSyntheticGravityExpandedState = isSyntheticGravityExpandedState,
        isTacticalHardwareExpandedState = isTacticalHardwareExpandedState,
        isTelemetryExpandedState = isTelemetryExpandedState,
        listState1 = listState1,
        onRefreshNeeded = onRefreshNeeded,
        pendingBackTapScopeState = pendingBackTapScopeState,
        pinnedSection1State = pinnedSection1State,
        prefs = prefs,
        sectionOrder1StrState = sectionOrder1StrState,
        showAmoledWarningDialogState = showAmoledWarningDialogState,
        showBatteryWarningDialogState = showBatteryWarningDialogState,
        showImportOptionsDialogState = showImportOptionsDialogState,
        showNotificationAccessDialogState = showNotificationAccessDialogState,
        showOemShieldDialogState = showOemShieldDialogState,
        showResetConfirmDialogState = showResetConfirmDialogState,
        singlePressTapCountState = singlePressTapCountState,
        sectionTitles1 = sectionTitles1,
        thresholdCrossedFlashState = thresholdCrossedFlashState,
        toggleSection = ::toggleSection,
        tokenLabelCache = tokenLabelCache
                    )
                }

                // PAGE 2: RIGHT DEFLECTOR
                2 -> {
                    RightDeflectorTabContent(
        blueprintTabTargetState = blueprintTabTargetState,
        context = context,
        dynamicActionTokens = dynamicActionTokens,
        isBottomExpandedState = isBottomExpandedState,
        isBottomGeoExpandedState = isBottomGeoExpandedState,
        isBottomGesturesExpandedState = isBottomGesturesExpandedState,
        isBottomScrubExpandedState = isBottomScrubExpandedState,
        isCenterExpandedState = isCenterExpandedState,
        isCenterGeoExpandedState = isCenterGeoExpandedState,
        isLeftFlankUnifiedState = isLeftFlankUnifiedState,
        isRightFlankUnifiedState = isRightFlankUnifiedState,
        isRightUnifiedExpandedState = isRightUnifiedExpandedState,
        isRightUnifiedGeoExpandedState = isRightUnifiedGeoExpandedState,
        isRightUnifiedGesturesExpandedState = isRightUnifiedGesturesExpandedState,
        isRightUnifiedScrubExpandedState = isRightUnifiedScrubExpandedState,
        isTopExpandedState = isTopExpandedState,
        isTopGeoExpandedState = isTopGeoExpandedState,
        isTopGesturesExpandedState = isTopGesturesExpandedState,
        isTopScrubExpandedState = isTopScrubExpandedState,
        listState2 = listState2,
        onRefreshNeeded = onRefreshNeeded,
        pastedJsonTextState = pastedJsonTextState,
        pendingBackTapScopeState = pendingBackTapScopeState,
        pinnedSection2State = pinnedSection2State,
        prefs = prefs,
        sectionOrder2StrState = sectionOrder2StrState,
        selectedTemplateOptionState = selectedTemplateOptionState,
        showAmoledWarningDialogState = showAmoledWarningDialogState,
        showBatteryWarningDialogState = showBatteryWarningDialogState,
        showImportOptionsDialogState = showImportOptionsDialogState,
        showNotificationAccessDialogState = showNotificationAccessDialogState,
        showOemShieldDialogState = showOemShieldDialogState,
        showPasteJsonDialogState = showPasteJsonDialogState,
        showResetConfirmDialogState = showResetConfirmDialogState,
        showSymmetryInfoDialogState = showSymmetryInfoDialogState,
        showUnifyInfoDialogState = showUnifyInfoDialogState,
        showUnifyTemplateDialogForLeftState = showUnifyTemplateDialogForLeftState,
        showUnifyTemplateDialogForRightState = showUnifyTemplateDialogForRightState,
        sectionTitles2 = sectionTitles2,
        toggleSection = ::toggleSection,
        tokenLabelCache = tokenLabelCache
                    )
                }
            }  // end when(pageIndex)
        }  // end HorizontalPager

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

