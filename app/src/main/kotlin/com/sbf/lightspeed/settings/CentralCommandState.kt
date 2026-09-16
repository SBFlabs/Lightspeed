package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedBackTapEngine
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary

@Composable
fun rememberCentralCommandState(
    context: Context,
    prefs: SharedPreferences,
    toggleAllTrigger: Int,
    onRefreshNeeded: () -> Unit
): CentralCommandState {
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()

    // Tab Display Profiles & Blueprint State
    val tabMode0State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, "custom_pinned") ?: "custom_pinned") }
    var tabMode0 by tabMode0State
    val pinnedSection0State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, "left_center") ?: "left_center") }
    val pinnedSection0 by pinnedSection0State
    val sectionOrder0StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, "left_center,left_top,left_bottom") ?: "left_center,left_top,left_bottom") }

    val tabMode1State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, "custom_pinned") ?: "custom_pinned") }
    var tabMode1 by tabMode1State
    val pinnedSection1State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, "sensor_deck") ?: "sensor_deck") }
    val pinnedSection1 by pinnedSection1State
    val sectionOrder1StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, "sensor_deck,synthetic_gravity,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") ?: "sensor_deck,synthetic_gravity,telemetry_indicators,tactical_hardware,refueling_bay,config_vault,experimental_labs") }

    val tabMode2State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, "custom_pinned") ?: "custom_pinned") }
    var tabMode2 by tabMode2State
    val pinnedSection2State = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, "center") ?: "center") }
    val pinnedSection2 by pinnedSection2State
    val sectionOrder2StrState = rememberSaveable { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, "center,top,bottom") ?: "center,top,bottom") }

    val popoverTabTargetState = rememberSaveable { mutableStateOf<Int?>(null) }
    val blueprintTabTargetState = rememberSaveable { mutableStateOf<Int?>(null) }

    // Left Deflector Accordion States
    val isLeftCenterExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_center" else prefs.getBoolean("pref_section_left_center_expanded", true)) }
    var isLeftCenterExpanded by isLeftCenterExpandedState
    val isLeftTopExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_top" || pinnedSection0.startsWith("left_top_") else prefs.getBoolean("pref_section_left_top_expanded", true)) }
    var isLeftTopExpanded by isLeftTopExpandedState
    val isLeftBottomExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_bottom" || pinnedSection0.startsWith("left_bottom_") else prefs.getBoolean("pref_section_left_bottom_expanded", false)) }
    var isLeftBottomExpanded by isLeftBottomExpandedState
    val isLeftFlankUnifiedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)) }
    val isLeftFlankUnified by isLeftFlankUnifiedState
    val isLeftUnifiedExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "all_expanded") true else if (tabMode0 == "all_collapsed") false else if (tabMode0 == "custom_pinned") pinnedSection0 == "left_unified" || pinnedSection0.startsWith("left_unified_") else prefs.getBoolean("pref_section_left_unified_expanded", false)) }
    var isLeftUnifiedExpanded by isLeftUnifiedExpandedState

    // Left Deflector Sub-Section States
    val isLeftCenterGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_left_center", true)) }
    val isLeftUnifiedGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_unified_geo" else prefs.getBoolean("pref_sub_geo_left_unified", true)) }
    val isLeftUnifiedScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_unified_scrub" else prefs.getBoolean("pref_sub_scrub_left_unified", true)) }
    val isLeftUnifiedGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_unified_gestures" else prefs.getBoolean("pref_sub_gestures_left_unified", true)) }
    val isLeftTopGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_top_geo" else prefs.getBoolean("pref_sub_geo_left_top", true)) }
    val isLeftTopScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_top_scrub" else prefs.getBoolean("pref_sub_scrub_left_top", true)) }
    val isLeftTopGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_top_gestures" else prefs.getBoolean("pref_sub_gestures_left_top", true)) }
    val isLeftBottomGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_bottom_geo" else prefs.getBoolean("pref_sub_geo_left_bottom", true)) }
    val isLeftBottomScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_bottom_scrub" else prefs.getBoolean("pref_sub_scrub_left_bottom", true)) }
    val isLeftBottomGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "left_bottom_gestures" else prefs.getBoolean("pref_sub_gestures_left_bottom", true)) }

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
    val isSystemOverridesExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "system_overrides" else prefs.getBoolean("pref_section_system_overrides_expanded", false)) }
    val isExperimentalLabsExpandedState = rememberSaveable { mutableStateOf(if (tabMode1 == "all_expanded") true else if (tabMode1 == "all_collapsed") false else if (tabMode1 == "custom_pinned") pinnedSection1 == "experimental_labs" else prefs.getBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, false)) }
    var isExperimentalLabsExpanded by isExperimentalLabsExpandedState

    // Power Button & Sub-Sections
    val singlePressTapCountState = rememberSaveable { mutableIntStateOf(0) }
    val isSinglePressUnlockedState = rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, false)) }
    val isSubVolumeExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_volume_expanded", true)) }
    val isSubPowerExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_power_expanded", true)) }
    val isSubHullTapExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_hulltap_expanded", false)) }
    val isSubHullTapExpanded by isSubHullTapExpandedState

    val showOemShieldDialogState = rememberSaveable { mutableStateOf(false) }
    val showBatteryWarningDialogState = rememberSaveable { mutableStateOf(false) }
    val showNotificationAccessDialogState = rememberSaveable { mutableStateOf(false) }
    val showAmoledWarningDialogState = rememberSaveable { mutableStateOf(false) }
    val pendingBackTapScopeState = rememberSaveable { mutableStateOf("screen_on") }

    val isStatusBarGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_statusbar", false)) }
    val isStatusBarGeoExpanded by isStatusBarGeoExpandedState
    val isStatusBarScrubExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_scrub_statusbar", false)) }
    val isStatusBarGesturesExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_gestures_statusbar", false)) }
    val isHorizonRailGeomExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_geom", false)) }
    val isHorizonRailGeomExpanded by isHorizonRailGeomExpandedState
    val isHorizonRailColorExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_color", false)) }
    val isHorizonRailColorExpanded by isHorizonRailColorExpandedState
    val isHorizonRailTextExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_horizon_rail_text", false)) }
    val isHorizonRailTextExpanded by isHorizonRailTextExpandedState
    val isNotchCalibExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_calib", false)) }
    val isNotchCalibExpanded by isNotchCalibExpandedState
    val isMarqueeSubSectionExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_notch_marquee", false)) }
    val isOemNoticeDemotedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_oem_notch_notice_demoted", false)) }

    // Live Impulse Calibration Meter State (Hull Tap)
    val currentZImpulseState = remember { mutableFloatStateOf(0f) }
    var currentZImpulse by currentZImpulseState
    val currentThresholdState = remember { mutableFloatStateOf(LightspeedBackTapEngine.getThreshold(context)) }
    var currentThreshold by currentThresholdState
    val thresholdCrossedFlashState = remember { mutableStateOf(false) }
    var thresholdCrossedFlash by thresholdCrossedFlashState

    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })

    // Accelerometer sampling hook
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
    val isCenterExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "center" else prefs.getBoolean("pref_section_center_expanded", true)) }
    var isCenterExpanded by isCenterExpandedState
    val isTopExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "top" || pinnedSection2.startsWith("top_") else prefs.getBoolean("pref_section_top_expanded", true)) }
    var isTopExpanded by isTopExpandedState
    val isBottomExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "bottom" || pinnedSection2.startsWith("bottom_") else prefs.getBoolean("pref_section_bottom_expanded", false)) }
    var isBottomExpanded by isBottomExpandedState
    val isRightFlankUnifiedState = rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK, false)) }
    val isRightFlankUnified by isRightFlankUnifiedState
    val isRightUnifiedExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "all_expanded") true else if (tabMode2 == "all_collapsed") false else if (tabMode2 == "custom_pinned") pinnedSection2 == "unified" || pinnedSection2.startsWith("unified_") else prefs.getBoolean("pref_section_right_unified_expanded", false)) }
    var isRightUnifiedExpanded by isRightUnifiedExpandedState

    // Dual Watchdog Sub-Section States
    val isInnerWatchdogExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_inner_watchdog_labs", true)) }
    val isOuterWatchdogExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_outer_watchdog_labs", true)) }

    // Right Deflector Sub-Section States
    val isCenterGeoExpandedState = rememberSaveable { mutableStateOf(prefs.getBoolean("pref_sub_geo_center", true)) }
    val isRightUnifiedGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "unified_geo" else prefs.getBoolean("pref_sub_geo_right_unified", true)) }
    val isRightUnifiedScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "unified_scrub" else prefs.getBoolean("pref_sub_scrub_right_unified", true)) }
    val isRightUnifiedGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "unified_gestures" else prefs.getBoolean("pref_sub_gestures_right_unified", true)) }
    val isTopGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "top_geo" else prefs.getBoolean("pref_sub_geo_top", true)) }
    val isTopScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "top_scrub" else prefs.getBoolean("pref_sub_scrub_top", true)) }
    val isTopGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "top_gestures" else prefs.getBoolean("pref_sub_gestures_top", true)) }
    val isBottomGeoExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "bottom_geo" else prefs.getBoolean("pref_sub_geo_bottom", true)) }
    val isBottomScrubExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "bottom_scrub" else prefs.getBoolean("pref_sub_scrub_bottom", true)) }
    val isBottomGesturesExpandedState = rememberSaveable { mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "bottom_gestures" else prefs.getBoolean("pref_sub_gestures_bottom", true)) }

    val listState0 = rememberLazyListState()
    val listState1 = rememberLazyListState()
    val listState2 = rememberLazyListState()

    val showUnifyInfoDialogState = remember { mutableStateOf(false) }
    val showSymmetryInfoDialogState = remember { mutableStateOf(false) }
    val showPasteJsonDialogState = remember { mutableStateOf(false) }
    val pastedJsonTextState = remember { mutableStateOf("") }
    val showUnifyTemplateDialogForLeftState = remember { mutableStateOf(false) }
    val showUnifyTemplateDialogForRightState = remember { mutableStateOf(false) }
    val showImportOptionsDialogState = remember { mutableStateOf(false) }
    val showResetConfirmDialogState = remember { mutableStateOf(false) }

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
            mapOf("left_unified" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.UNIFIED_DEFLECTORS))
        } else {
            mapOf(
                "left_center" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_ZONE),
                "left_top" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.UPPER_FLANK),
                "left_bottom" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.LOWER_FLANK)
            )
        }
    }

    val sectionTitles1 = remember(currentLanguageMode) {
        mapOf(
            "sensor_deck" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SENSOR_AREA),
            "synthetic_gravity" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.GRAVITY_ENGINE),
            "telemetry_indicators" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS),
            "tactical_hardware" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TACTICAL_HARDWARE),
            "refueling_bay" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.REFUELING_BAY),
            "config_vault" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.SHIP_DATA_VAULT),
            "experimental_labs" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.EXPERIMENTAL_LABS)
        )
    }

    val sectionTitles2 = remember(isRightFlankUnified, currentLanguageMode) {
        if (isRightFlankUnified) {
            mapOf("unified" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.UNIFIED_DEFLECTORS))
        } else {
            mapOf(
                "center" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_ZONE),
                "top" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.UPPER_FLANK),
                "bottom" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.LOWER_FLANK)
            )
        }
    }

    val toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit = { tabId, sectionId, currentExpanded, onSetExpanded ->
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

    return CentralCommandState(
        tabMode0State = tabMode0State,
        pinnedSection0State = pinnedSection0State,
        sectionOrder0StrState = sectionOrder0StrState,
        tabMode1State = tabMode1State,
        pinnedSection1State = pinnedSection1State,
        sectionOrder1StrState = sectionOrder1StrState,
        tabMode2State = tabMode2State,
        pinnedSection2State = pinnedSection2State,
        sectionOrder2StrState = sectionOrder2StrState,
        popoverTabTargetState = popoverTabTargetState,
        blueprintTabTargetState = blueprintTabTargetState,
        isLeftCenterExpandedState = isLeftCenterExpandedState,
        isLeftTopExpandedState = isLeftTopExpandedState,
        isLeftBottomExpandedState = isLeftBottomExpandedState,
        isLeftFlankUnifiedState = isLeftFlankUnifiedState,
        isLeftUnifiedExpandedState = isLeftUnifiedExpandedState,
        isLeftCenterGeoExpandedState = isLeftCenterGeoExpandedState,
        isLeftUnifiedGeoExpandedState = isLeftUnifiedGeoExpandedState,
        isLeftUnifiedScrubExpandedState = isLeftUnifiedScrubExpandedState,
        isLeftUnifiedGesturesExpandedState = isLeftUnifiedGesturesExpandedState,
        isLeftTopGeoExpandedState = isLeftTopGeoExpandedState,
        isLeftTopScrubExpandedState = isLeftTopScrubExpandedState,
        isLeftTopGesturesExpandedState = isLeftTopGesturesExpandedState,
        isLeftBottomGeoExpandedState = isLeftBottomGeoExpandedState,
        isLeftBottomScrubExpandedState = isLeftBottomScrubExpandedState,
        isLeftBottomGesturesExpandedState = isLeftBottomGesturesExpandedState,
        isSensorDeckExpandedState = isSensorDeckExpandedState,
        isSyntheticGravityExpandedState = isSyntheticGravityExpandedState,
        isTelemetryExpandedState = isTelemetryExpandedState,
        isTacticalHardwareExpandedState = isTacticalHardwareExpandedState,
        isRefuelingExpandedState = isRefuelingExpandedState,
        isConfigVaultExpandedState = isConfigVaultExpandedState,
        isSystemOverridesExpandedState = isSystemOverridesExpandedState,
        isExperimentalLabsExpandedState = isExperimentalLabsExpandedState,
        singlePressTapCountState = singlePressTapCountState,
        isSinglePressUnlockedState = isSinglePressUnlockedState,
        isSubVolumeExpandedState = isSubVolumeExpandedState,
        isSubPowerExpandedState = isSubPowerExpandedState,
        isSubHullTapExpandedState = isSubHullTapExpandedState,
        isStatusBarGeoExpandedState = isStatusBarGeoExpandedState,
        isStatusBarScrubExpandedState = isStatusBarScrubExpandedState,
        isStatusBarGesturesExpandedState = isStatusBarGesturesExpandedState,
        isHorizonRailGeomExpandedState = isHorizonRailGeomExpandedState,
        isHorizonRailColorExpandedState = isHorizonRailColorExpandedState,
        isHorizonRailTextExpandedState = isHorizonRailTextExpandedState,
        isNotchCalibExpandedState = isNotchCalibExpandedState,
        isMarqueeSubSectionExpandedState = isMarqueeSubSectionExpandedState,
        isOemNoticeDemotedState = isOemNoticeDemotedState,
        currentZImpulseState = currentZImpulseState,
        currentThresholdState = currentThresholdState,
        thresholdCrossedFlashState = thresholdCrossedFlashState,
        isCenterExpandedState = isCenterExpandedState,
        isTopExpandedState = isTopExpandedState,
        isBottomExpandedState = isBottomExpandedState,
        isRightFlankUnifiedState = isRightFlankUnifiedState,
        isRightUnifiedExpandedState = isRightUnifiedExpandedState,
        isInnerWatchdogExpandedState = isInnerWatchdogExpandedState,
        isOuterWatchdogExpandedState = isOuterWatchdogExpandedState,
        isCenterGeoExpandedState = isCenterGeoExpandedState,
        isRightUnifiedGeoExpandedState = isRightUnifiedGeoExpandedState,
        isRightUnifiedScrubExpandedState = isRightUnifiedScrubExpandedState,
        isRightUnifiedGesturesExpandedState = isRightUnifiedGesturesExpandedState,
        isTopGeoExpandedState = isTopGeoExpandedState,
        isTopScrubExpandedState = isTopScrubExpandedState,
        isTopGesturesExpandedState = isTopGesturesExpandedState,
        isBottomGeoExpandedState = isBottomGeoExpandedState,
        isBottomScrubExpandedState = isBottomScrubExpandedState,
        isBottomGesturesExpandedState = isBottomGesturesExpandedState,
        showUnifyInfoDialogState = showUnifyInfoDialogState,
        showSymmetryInfoDialogState = showSymmetryInfoDialogState,
        showPasteJsonDialogState = showPasteJsonDialogState,
        pastedJsonTextState = pastedJsonTextState,
        showUnifyTemplateDialogForLeftState = showUnifyTemplateDialogForLeftState,
        showUnifyTemplateDialogForRightState = showUnifyTemplateDialogForRightState,
        showImportOptionsDialogState = showImportOptionsDialogState,
        showResetConfirmDialogState = showResetConfirmDialogState,
        showOemShieldDialogState = showOemShieldDialogState,
        showBatteryWarningDialogState = showBatteryWarningDialogState,
        showNotificationAccessDialogState = showNotificationAccessDialogState,
        showAmoledWarningDialogState = showAmoledWarningDialogState,
        pendingBackTapScopeState = pendingBackTapScopeState,
        listState0 = listState0,
        listState1 = listState1,
        listState2 = listState2,
        pagerState = pagerState,
        dynamicActionTokens = dynamicActionTokens,
        tokenLabelCache = tokenLabelCache,
        sectionTitles0 = sectionTitles0,
        sectionTitles1 = sectionTitles1,
        sectionTitles2 = sectionTitles2,
        toggleSection = toggleSection
    )
}
