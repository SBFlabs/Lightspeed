package com.sbf.lightspeed.settings

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState

class CentralCommandNavState(
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
    val blueprintTabTargetState: MutableState<Int?>
)

class DeflectorAccordionState(
    val isCenterExpandedState: MutableState<Boolean>,
    val isTopExpandedState: MutableState<Boolean>,
    val isBottomExpandedState: MutableState<Boolean>,
    val isFlankUnifiedState: MutableState<Boolean>,
    val isUnifiedExpandedState: MutableState<Boolean>,
    val isCenterGeoExpandedState: MutableState<Boolean>,
    val isUnifiedGeoExpandedState: MutableState<Boolean>,
    val isUnifiedScrubExpandedState: MutableState<Boolean>,
    val isUnifiedGesturesExpandedState: MutableState<Boolean>,
    val isTopGeoExpandedState: MutableState<Boolean>,
    val isTopScrubExpandedState: MutableState<Boolean>,
    val isTopGesturesExpandedState: MutableState<Boolean>,
    val isBottomGeoExpandedState: MutableState<Boolean>,
    val isBottomScrubExpandedState: MutableState<Boolean>,
    val isBottomGesturesExpandedState: MutableState<Boolean>,
    val isInnerWatchdogExpandedState: MutableState<Boolean>? = null,
    val isOuterWatchdogExpandedState: MutableState<Boolean>? = null
)

class HudStripAccordionState(
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
    val thresholdCrossedFlashState: MutableState<Boolean>
)

class CentralCommandDialogState(
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
    val pendingBackTapScopeState: MutableState<String>
)

class CentralCommandState(
    val nav: CentralCommandNavState,
    val leftDeflector: DeflectorAccordionState,
    val rightDeflector: DeflectorAccordionState,
    val hudStrip: HudStripAccordionState,
    val dialogs: CentralCommandDialogState,
    val listState0: LazyListState,
    val listState1: LazyListState,
    val listState2: LazyListState,
    val pagerState: PagerState,
    val dynamicActionTokens: List<String>,
    val tokenLabelCache: MutableMap<String, String>,
    val sectionTitles0: Map<String, String>,
    val sectionTitles1: Map<String, String>,
    val sectionTitles2: Map<String, String>,
    val toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit
) {
    // Nav Delegations
    val tabMode0State get() = nav.tabMode0State
    val pinnedSection0State get() = nav.pinnedSection0State
    val sectionOrder0StrState get() = nav.sectionOrder0StrState
    val tabMode1State get() = nav.tabMode1State
    val pinnedSection1State get() = nav.pinnedSection1State
    val sectionOrder1StrState get() = nav.sectionOrder1StrState
    val tabMode2State get() = nav.tabMode2State
    val pinnedSection2State get() = nav.pinnedSection2State
    val sectionOrder2StrState get() = nav.sectionOrder2StrState
    val popoverTabTargetState get() = nav.popoverTabTargetState
    val blueprintTabTargetState get() = nav.blueprintTabTargetState

    // Left Deflector Delegations
    val isLeftCenterExpandedState get() = leftDeflector.isCenterExpandedState
    val isLeftTopExpandedState get() = leftDeflector.isTopExpandedState
    val isLeftBottomExpandedState get() = leftDeflector.isBottomExpandedState
    val isLeftFlankUnifiedState get() = leftDeflector.isFlankUnifiedState
    val isLeftUnifiedExpandedState get() = leftDeflector.isUnifiedExpandedState
    val isLeftCenterGeoExpandedState get() = leftDeflector.isCenterGeoExpandedState
    val isLeftUnifiedGeoExpandedState get() = leftDeflector.isUnifiedGeoExpandedState
    val isLeftUnifiedScrubExpandedState get() = leftDeflector.isUnifiedScrubExpandedState
    val isLeftUnifiedGesturesExpandedState get() = leftDeflector.isUnifiedGesturesExpandedState
    val isLeftTopGeoExpandedState get() = leftDeflector.isTopGeoExpandedState
    val isLeftTopScrubExpandedState get() = leftDeflector.isTopScrubExpandedState
    val isLeftTopGesturesExpandedState get() = leftDeflector.isTopGesturesExpandedState
    val isLeftBottomGeoExpandedState get() = leftDeflector.isBottomGeoExpandedState
    val isLeftBottomScrubExpandedState get() = leftDeflector.isBottomScrubExpandedState
    val isLeftBottomGesturesExpandedState get() = leftDeflector.isBottomGesturesExpandedState

    // HUD Strip Delegations
    val isSensorDeckExpandedState get() = hudStrip.isSensorDeckExpandedState
    val isSyntheticGravityExpandedState get() = hudStrip.isSyntheticGravityExpandedState
    val isTelemetryExpandedState get() = hudStrip.isTelemetryExpandedState
    val isTacticalHardwareExpandedState get() = hudStrip.isTacticalHardwareExpandedState
    val isRefuelingExpandedState get() = hudStrip.isRefuelingExpandedState
    val isConfigVaultExpandedState get() = hudStrip.isConfigVaultExpandedState
    val isSystemOverridesExpandedState get() = hudStrip.isSystemOverridesExpandedState
    val isExperimentalLabsExpandedState get() = hudStrip.isExperimentalLabsExpandedState
    val singlePressTapCountState get() = hudStrip.singlePressTapCountState
    val isSinglePressUnlockedState get() = hudStrip.isSinglePressUnlockedState
    val isSubVolumeExpandedState get() = hudStrip.isSubVolumeExpandedState
    val isSubPowerExpandedState get() = hudStrip.isSubPowerExpandedState
    val isSubHullTapExpandedState get() = hudStrip.isSubHullTapExpandedState
    val isStatusBarGeoExpandedState get() = hudStrip.isStatusBarGeoExpandedState
    val isStatusBarScrubExpandedState get() = hudStrip.isStatusBarScrubExpandedState
    val isStatusBarGesturesExpandedState get() = hudStrip.isStatusBarGesturesExpandedState
    val isHorizonRailGeomExpandedState get() = hudStrip.isHorizonRailGeomExpandedState
    val isHorizonRailColorExpandedState get() = hudStrip.isHorizonRailColorExpandedState
    val isHorizonRailTextExpandedState get() = hudStrip.isHorizonRailTextExpandedState
    val isNotchCalibExpandedState get() = hudStrip.isNotchCalibExpandedState
    val isMarqueeSubSectionExpandedState get() = hudStrip.isMarqueeSubSectionExpandedState
    val isOemNoticeDemotedState get() = hudStrip.isOemNoticeDemotedState
    val currentZImpulseState get() = hudStrip.currentZImpulseState
    val currentThresholdState get() = hudStrip.currentThresholdState
    val thresholdCrossedFlashState get() = hudStrip.thresholdCrossedFlashState

    // Right Deflector Delegations
    val isCenterExpandedState get() = rightDeflector.isCenterExpandedState
    val isTopExpandedState get() = rightDeflector.isTopExpandedState
    val isBottomExpandedState get() = rightDeflector.isBottomExpandedState
    val isRightFlankUnifiedState get() = rightDeflector.isFlankUnifiedState
    val isRightUnifiedExpandedState get() = rightDeflector.isUnifiedExpandedState
    val isInnerWatchdogExpandedState get() = rightDeflector.isInnerWatchdogExpandedState
    val isOuterWatchdogExpandedState get() = rightDeflector.isOuterWatchdogExpandedState
    val isCenterGeoExpandedState get() = rightDeflector.isCenterGeoExpandedState
    val isRightUnifiedGeoExpandedState get() = rightDeflector.isUnifiedGeoExpandedState
    val isRightUnifiedScrubExpandedState get() = rightDeflector.isUnifiedScrubExpandedState
    val isRightUnifiedGesturesExpandedState get() = rightDeflector.isUnifiedGesturesExpandedState
    val isTopGeoExpandedState get() = rightDeflector.isTopGeoExpandedState
    val isTopScrubExpandedState get() = rightDeflector.isTopScrubExpandedState
    val isTopGesturesExpandedState get() = rightDeflector.isTopGesturesExpandedState
    val isBottomGeoExpandedState get() = rightDeflector.isBottomGeoExpandedState
    val isBottomScrubExpandedState get() = rightDeflector.isBottomScrubExpandedState
    val isBottomGesturesExpandedState get() = rightDeflector.isBottomGesturesExpandedState

    // Dialog Delegations
    val showUnifyInfoDialogState get() = dialogs.showUnifyInfoDialogState
    val showSymmetryInfoDialogState get() = dialogs.showSymmetryInfoDialogState
    val showPasteJsonDialogState get() = dialogs.showPasteJsonDialogState
    val pastedJsonTextState get() = dialogs.pastedJsonTextState
    val showUnifyTemplateDialogForLeftState get() = dialogs.showUnifyTemplateDialogForLeftState
    val showUnifyTemplateDialogForRightState get() = dialogs.showUnifyTemplateDialogForRightState
    val showImportOptionsDialogState get() = dialogs.showImportOptionsDialogState
    val showResetConfirmDialogState get() = dialogs.showResetConfirmDialogState
    val showOemShieldDialogState get() = dialogs.showOemShieldDialogState
    val showBatteryWarningDialogState get() = dialogs.showBatteryWarningDialogState
    val showNotificationAccessDialogState get() = dialogs.showNotificationAccessDialogState
    val showAmoledWarningDialogState get() = dialogs.showAmoledWarningDialogState
    val pendingBackTapScopeState get() = dialogs.pendingBackTapScopeState
}
