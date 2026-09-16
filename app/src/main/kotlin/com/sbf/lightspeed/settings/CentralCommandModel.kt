package com.sbf.lightspeed.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState

class CentralCommandState(
    // Tab Display Profiles & Blueprint State
    val tabMode0State: MutableState<String>,
    val pinnedSection0State: MutableState<String>,
    val sectionOrder0StrState: MutableState<String>,
    val tabMode1State: MutableState<String>,
    val pinnedSection1State: MutableState<String>,
    val sectionOrder1StrState: MutableState<String>,
    val tabMode2State: MutableState<String>,
    val pinnedSection2State: MutableState<String>,
    val sectionOrder2StrState: MutableState<String>,
    val popoverTabTargetState: MutableState<Int?>,
    val blueprintTabTargetState: MutableState<Int?>,

    // Left Deflector Accordion States
    val isLeftCenterExpandedState: MutableState<Boolean>,
    val isLeftTopExpandedState: MutableState<Boolean>,
    val isLeftBottomExpandedState: MutableState<Boolean>,
    val isLeftFlankUnifiedState: MutableState<Boolean>,
    val isLeftUnifiedExpandedState: MutableState<Boolean>,
    val isLeftCenterGeoExpandedState: MutableState<Boolean>,
    val isLeftUnifiedGeoExpandedState: MutableState<Boolean>,
    val isLeftUnifiedScrubExpandedState: MutableState<Boolean>,
    val isLeftUnifiedGesturesExpandedState: MutableState<Boolean>,
    val isLeftTopGeoExpandedState: MutableState<Boolean>,
    val isLeftTopScrubExpandedState: MutableState<Boolean>,
    val isLeftTopGesturesExpandedState: MutableState<Boolean>,
    val isLeftBottomGeoExpandedState: MutableState<Boolean>,
    val isLeftBottomScrubExpandedState: MutableState<Boolean>,
    val isLeftBottomGesturesExpandedState: MutableState<Boolean>,

    // HUD Strip Accordion States
    val isSensorDeckExpandedState: MutableState<Boolean>,
    val isSyntheticGravityExpandedState: MutableState<Boolean>,
    val isTelemetryExpandedState: MutableState<Boolean>,
    val isTacticalHardwareExpandedState: MutableState<Boolean>,
    val isRefuelingExpandedState: MutableState<Boolean>,
    val isConfigVaultExpandedState: MutableState<Boolean>,
    val isSystemOverridesExpandedState: MutableState<Boolean>,
    val isExperimentalLabsExpandedState: MutableState<Boolean>,
    val singlePressTapCountState: MutableIntState,
    val isSinglePressUnlockedState: MutableState<Boolean>,
    val isSubVolumeExpandedState: MutableState<Boolean>,
    val isSubPowerExpandedState: MutableState<Boolean>,
    val isSubHullTapExpandedState: MutableState<Boolean>,
    val isStatusBarGeoExpandedState: MutableState<Boolean>,
    val isStatusBarScrubExpandedState: MutableState<Boolean>,
    val isStatusBarGesturesExpandedState: MutableState<Boolean>,
    val isHorizonRailGeomExpandedState: MutableState<Boolean>,
    val isHorizonRailColorExpandedState: MutableState<Boolean>,
    val isHorizonRailTextExpandedState: MutableState<Boolean>,
    val isNotchCalibExpandedState: MutableState<Boolean>,
    val isMarqueeSubSectionExpandedState: MutableState<Boolean>,
    val isOemNoticeDemotedState: MutableState<Boolean>,
    val currentZImpulseState: MutableFloatState,
    val currentThresholdState: MutableFloatState,
    val thresholdCrossedFlashState: MutableState<Boolean>,

    // Right Deflector Accordion States
    val isCenterExpandedState: MutableState<Boolean>,
    val isTopExpandedState: MutableState<Boolean>,
    val isBottomExpandedState: MutableState<Boolean>,
    val isRightFlankUnifiedState: MutableState<Boolean>,
    val isRightUnifiedExpandedState: MutableState<Boolean>,
    val isInnerWatchdogExpandedState: MutableState<Boolean>,
    val isOuterWatchdogExpandedState: MutableState<Boolean>,
    val isCenterGeoExpandedState: MutableState<Boolean>,
    val isRightUnifiedGeoExpandedState: MutableState<Boolean>,
    val isRightUnifiedScrubExpandedState: MutableState<Boolean>,
    val isRightUnifiedGesturesExpandedState: MutableState<Boolean>,
    val isTopGeoExpandedState: MutableState<Boolean>,
    val isTopScrubExpandedState: MutableState<Boolean>,
    val isTopGesturesExpandedState: MutableState<Boolean>,
    val isBottomGeoExpandedState: MutableState<Boolean>,
    val isBottomScrubExpandedState: MutableState<Boolean>,
    val isBottomGesturesExpandedState: MutableState<Boolean>,

    // Dialog States
    val showUnifyInfoDialogState: MutableState<Boolean>,
    val showSymmetryInfoDialogState: MutableState<Boolean>,
    val showPasteJsonDialogState: MutableState<Boolean>,
    val pastedJsonTextState: MutableState<String>,
    val showUnifyTemplateDialogForLeftState: MutableState<Boolean>,
    val showUnifyTemplateDialogForRightState: MutableState<Boolean>,
    val showImportOptionsDialogState: MutableState<Boolean>,
    val showResetConfirmDialogState: MutableState<Boolean>,
    val showOemShieldDialogState: MutableState<Boolean>,
    val showBatteryWarningDialogState: MutableState<Boolean>,
    val showNotificationAccessDialogState: MutableState<Boolean>,
    val showAmoledWarningDialogState: MutableState<Boolean>,
    val pendingBackTapScopeState: MutableState<String>,

    // List & Pager States
    val listState0: LazyListState,
    val listState1: LazyListState,
    val listState2: LazyListState,
    val pagerState: PagerState,

    // Data Models
    val dynamicActionTokens: List<String>,
    val tokenLabelCache: MutableMap<String, String>,
    val sectionTitles0: Map<String, String>,
    val sectionTitles1: Map<String, String>,
    val sectionTitles2: Map<String, String>,
    val toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit
)
