package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HudStripTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: Context,
    currentThresholdState: MutableFloatState,
    currentZImpulseState: MutableFloatState,
    dynamicActionTokens: List<String>,
    isConfigVaultExpandedState: MutableState<Boolean>,
    isSystemOverridesExpandedState: MutableState<Boolean>,
    isExperimentalLabsExpandedState: MutableState<Boolean>,
    isHorizonRailColorExpandedState: MutableState<Boolean>,
    isHorizonRailGeomExpandedState: MutableState<Boolean>,
    isHorizonRailTextExpandedState: MutableState<Boolean>,
    isMarqueeSubSectionExpandedState: MutableState<Boolean>,
    isNotchCalibExpandedState: MutableState<Boolean>,
    isOemNoticeDemotedState: MutableState<Boolean>,
    isRefuelingExpandedState: MutableState<Boolean>,
    isSensorDeckExpandedState: MutableState<Boolean>,
    isSinglePressUnlockedState: MutableState<Boolean>,
    isStatusBarGeoExpandedState: MutableState<Boolean>,
    isStatusBarGesturesExpandedState: MutableState<Boolean>,
    isSubHullTapExpandedState: MutableState<Boolean>,
    isSubPowerExpandedState: MutableState<Boolean>,
    isSubVolumeExpandedState: MutableState<Boolean>,
    isSyntheticGravityExpandedState: MutableState<Boolean>,
    isTacticalHardwareExpandedState: MutableState<Boolean>,
    isTelemetryExpandedState: MutableState<Boolean>,
    listState1: androidx.compose.foundation.lazy.LazyListState,
    onRefreshNeeded: () -> Unit,
    pendingBackTapScopeState: MutableState<String>,
    pinnedSection1State: MutableState<String>,
    prefs: SharedPreferences,
    sectionOrder1StrState: MutableState<String>,
    sectionTitles1: Map<String, String>,
    showAmoledWarningDialogState: MutableState<Boolean>,
    showBatteryWarningDialogState: MutableState<Boolean>,
    showImportOptionsDialogState: MutableState<Boolean>,
    showNotificationAccessDialogState: MutableState<Boolean>,
    showOemShieldDialogState: MutableState<Boolean>,
    showResetConfirmDialogState: MutableState<Boolean>,
    singlePressTapCountState: MutableIntState,
    thresholdCrossedFlashState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    var blueprintTabTarget by blueprintTabTargetState
    var currentThreshold by currentThresholdState
    var currentZImpulse by currentZImpulseState
    var isSystemOverridesExpanded by isSystemOverridesExpandedState
    var isConfigVaultExpanded by isConfigVaultExpandedState
    var isExperimentalLabsExpanded by isExperimentalLabsExpandedState
    var isHorizonRailColorExpanded by isHorizonRailColorExpandedState
    var isHorizonRailGeomExpanded by isHorizonRailGeomExpandedState
    var isHorizonRailTextExpanded by isHorizonRailTextExpandedState
    var isMarqueeSubSectionExpanded by isMarqueeSubSectionExpandedState
    var isNotchCalibExpanded by isNotchCalibExpandedState
    var isOemNoticeDemoted by isOemNoticeDemotedState
    var isRefuelingExpanded by isRefuelingExpandedState
    var isSensorDeckExpanded by isSensorDeckExpandedState
    var isSinglePressUnlocked by isSinglePressUnlockedState
    var isStatusBarGeoExpanded by isStatusBarGeoExpandedState
    var isStatusBarGesturesExpanded by isStatusBarGesturesExpandedState
    var isSubHullTapExpanded by isSubHullTapExpandedState
    var isSubPowerExpanded by isSubPowerExpandedState
    var isSubVolumeExpanded by isSubVolumeExpandedState
    var isSyntheticGravityExpanded by isSyntheticGravityExpandedState
    var isTacticalHardwareExpanded by isTacticalHardwareExpandedState
    var isTelemetryExpanded by isTelemetryExpandedState
    var pendingBackTapScope by pendingBackTapScopeState
    var pinnedSection1 by pinnedSection1State
    var sectionOrder1Str by sectionOrder1StrState
    var showAmoledWarningDialog by showAmoledWarningDialogState
    var showBatteryWarningDialog by showBatteryWarningDialogState
    var showImportOptionsDialog by showImportOptionsDialogState
    var showNotificationAccessDialog by showNotificationAccessDialogState
    var showOemShieldDialog by showOemShieldDialogState
    var showResetConfirmDialog by showResetConfirmDialogState
    var singlePressTapCount by singlePressTapCountState
    var thresholdCrossedFlash by thresholdCrossedFlashState

    val oemFeatureName = remember { OemNotchDetector.getDetectedFeatureName() }
    val installedTacticalTools by produceState(initialValue = emptyList<TacticalToolItem>()) {
        value = withContext(Dispatchers.IO) {
            InstalledTacticalToolsScanner.scan(context)
        }
    }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val res = withContext(Dispatchers.IO) { LightspeedBackupEngine.exportToFile(context, uri) }
            res.onSuccess { Toast.makeText(context, "Successfully exported $it settings to backup!", Toast.LENGTH_SHORT).show() }
                .onFailure { Toast.makeText(context, "Failed to export: ${it.message ?: it.javaClass.simpleName}", Toast.LENGTH_SHORT).show() }
        }
    }

    var currentOrder1 = remember(sectionOrder1Str) {
        HudStripTabOrderHelper.resolveOrder(sectionOrder1Str, prefs).toMutableList()
    }

    if (blueprintTabTarget == 1) {
        BlueprintWireframeView(
            tabTitle = "HUD STRIP",
            sectionIds = currentOrder1,
            pinnedSectionId = pinnedSection1,
            sectionTitles = sectionTitles1,
            onMoveUp = { sectionOrder1Str = HudStripTabOrderHelper.moveUp(currentOrder1, it, prefs) },
            onMoveDown = { sectionOrder1Str = HudStripTabOrderHelper.moveDown(currentOrder1, it, prefs) },
            onPinSection = {
                pinnedSection1 = it
                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_1, it).apply()
            },
            onExitBlueprint = { blueprintTabTarget = null }
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
                            HudSensorDeckSection(
                                context = context,
                                prefs = prefs,
                                isExpanded = isSensorDeckExpanded,
                                onToggle = {
                                    toggleSection(1, "sensor_deck", isSensorDeckExpanded) { isSensorDeckExpanded = it }
                                    prefs.edit()
                                        .putBoolean("pref_section_statusbar_expanded", isSensorDeckExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                },
                                isStatusBarGeoExpanded = isStatusBarGeoExpanded,
                                onToggleStatusBarGeo = {
                                    isStatusBarGeoExpanded = !isStatusBarGeoExpanded
                                    prefs.edit()
                                        .putBoolean("pref_sub_geo_statusbar", isStatusBarGeoExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                                isStatusBarGesturesExpanded = isStatusBarGesturesExpanded,
                                onToggleStatusBarGestures = {
                                    isStatusBarGesturesExpanded = !isStatusBarGesturesExpanded
                                    prefs.edit().putBoolean("pref_sub_gestures_statusbar", isStatusBarGesturesExpanded).apply()
                                },
                                dynamicActionTokens = dynamicActionTokens,
                                tokenLabelCache = tokenLabelCache,
                                onRefreshNeeded = onRefreshNeeded
                            )
                        }
                    }
                    "telemetry_indicators" -> {
                        item(key = "telemetry_indicators") {
                            HudTelemetryIndicatorsSection(
                                context = context,
                                prefs = prefs,
                                isExpanded = isTelemetryExpanded,
                                onToggle = {
                                    toggleSection(1, "telemetry_indicators", isTelemetryExpanded) { isTelemetryExpanded = it }
                                    val anyRail = isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded
                                    val railActive = isTelemetryExpanded && anyRail
                                    val notchActive = isTelemetryExpanded && isNotchCalibExpanded
                                    prefs.edit()
                                        .putBoolean("pref_section_telemetry_expanded", isTelemetryExpanded)
                                        .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, railActive)
                                        .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, notchActive)
                                        .apply()
                                    safeReloadPreferences()
                                },
                                isHorizonRailGeomExpanded = isHorizonRailGeomExpanded,
                                onToggleHorizonRailGeom = {
                                    isHorizonRailGeomExpanded = !isHorizonRailGeomExpanded
                                    prefs.edit().putBoolean("pref_sub_horizon_rail_geom", isHorizonRailGeomExpanded).apply()
                                    val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                                isHorizonRailColorExpanded = isHorizonRailColorExpanded,
                                onToggleHorizonRailColor = {
                                    isHorizonRailColorExpanded = !isHorizonRailColorExpanded
                                    prefs.edit().putBoolean("pref_sub_horizon_rail_color", isHorizonRailColorExpanded).apply()
                                    val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                                isHorizonRailTextExpanded = isHorizonRailTextExpanded,
                                onToggleHorizonRailText = {
                                    isHorizonRailTextExpanded = !isHorizonRailTextExpanded
                                    prefs.edit().putBoolean("pref_sub_horizon_rail_text", isHorizonRailTextExpanded).apply()
                                    val anyActive = isTelemetryExpanded && (isHorizonRailGeomExpanded || isHorizonRailColorExpanded || isHorizonRailTextExpanded)
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, anyActive).apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                                onShowNotificationAccessDialog = { showNotificationAccessDialog = true },
                                onRefreshNeeded = onRefreshNeeded
                            )
                        }
                    }
                    "tactical_hardware" -> {
                        item(key = "tactical_hardware") {
                            HudTacticalHardwareSection(
                                context = context,
                                prefs = prefs,
                                isExpanded = isTacticalHardwareExpanded,
                                onToggle = {
                                    toggleSection(1, "tactical_hardware", isTacticalHardwareExpanded) { isTacticalHardwareExpanded = it }
                                    prefs.edit().putBoolean("pref_section_tactical_hardware_expanded", isTacticalHardwareExpanded).apply()
                                    if (isTacticalHardwareExpanded && !prefs.getBoolean(LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, false)) {
                                        showOemShieldDialog = true
                                    }
                                },
                                isSubVolumeExpanded = isSubVolumeExpanded,
                                onToggleSubVolume = {
                                    isSubVolumeExpanded = !isSubVolumeExpanded
                                    prefs.edit().putBoolean("pref_sub_volume_expanded", isSubVolumeExpanded).apply()
                                },
                                isSubHullTapExpanded = isSubHullTapExpanded,
                                onToggleSubHullTap = {
                                    isSubHullTapExpanded = !isSubHullTapExpanded
                                    prefs.edit().putBoolean("pref_sub_hulltap_expanded", isSubHullTapExpanded).apply()
                                },
                                currentThreshold = currentThreshold,
                                onThresholdChange = { currentThreshold = it },
                                currentZImpulse = currentZImpulse,
                                onZImpulseChange = { currentZImpulse = it },
                                thresholdCrossedFlash = thresholdCrossedFlash,
                                onThresholdCrossedFlashChange = { thresholdCrossedFlash = it },
                                dynamicActionTokens = dynamicActionTokens,
                                tokenLabelCache = tokenLabelCache,
                                onShowOemShieldDialog = { showOemShieldDialog = true },
                                onSelectHighDrainScope = { key ->
                                    pendingBackTapScope = key
                                    showBatteryWarningDialog = true
                                },
                                installedTools = installedTacticalTools,
                                onRefreshNeeded = onRefreshNeeded
                            )
                        }
                    }
                    else -> {
                        renderHudStripAdvancedSection(
                            sectionId = sectionId,
                            context = context,
                            prefs = prefs,
                            toggleSection = toggleSection,
                            onRefreshNeeded = onRefreshNeeded,
                            isSyntheticGravityExpanded = isSyntheticGravityExpanded,
                            onToggleSyntheticGravity = { isSyntheticGravityExpanded = it },
                            isRefuelingExpanded = isRefuelingExpanded,
                            onToggleRefueling = { isRefuelingExpanded = it },
                            onShowAmoledWarning = { showAmoledWarningDialog = true },
                            isConfigVaultExpanded = isConfigVaultExpanded,
                            onToggleConfigVault = { isConfigVaultExpanded = it },
                            exportLauncher = exportLauncher,
                            onShowImportOptions = { showImportOptionsDialog = true },
                            onShowResetConfirm = { showResetConfirmDialog = true },
                            isSystemOverridesExpanded = isSystemOverridesExpanded,
                            onToggleSystemOverrides = { isSystemOverridesExpanded = it },
                            isExperimentalLabsExpanded = isExperimentalLabsExpanded,
                            onToggleExperimentalLabs = { isExperimentalLabsExpanded = it },
                            isNotchCalibExpanded = isNotchCalibExpanded,
                            onToggleNotchCalib = {
                                isNotchCalibExpanded = !isNotchCalibExpanded
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_NOTCH_CALIB, isNotchCalibExpanded)
                                    .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isNotchCalibExpanded).apply()
                                safeReloadPreferences()
                                onRefreshNeeded()
                            },
                            isOemNoticeDemoted = isOemNoticeDemoted,
                            onOemNoticeDemotedChange = { demoted ->
                                isOemNoticeDemoted = demoted
                                prefs.edit().putBoolean("pref_oem_notch_notice_demoted", demoted).apply()
                            },
                            oemFeatureName = oemFeatureName,
                            isMarqueeSubSectionExpanded = isMarqueeSubSectionExpanded,
                            onToggleMarqueeSubSection = {
                                isMarqueeSubSectionExpanded = !isMarqueeSubSectionExpanded
                                prefs.edit().putBoolean("pref_sub_notch_marquee", isMarqueeSubSectionExpanded).apply()
                            },
                            isSubPowerExpanded = isSubPowerExpanded,
                            onToggleSubPower = {
                                isSubPowerExpanded = !isSubPowerExpanded
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_POWER_EXPANDED, isSubPowerExpanded).apply()
                            },
                            isSinglePressUnlocked = isSinglePressUnlocked,
                            onSinglePressUnlockedChange = { isSinglePressUnlocked = it },
                            singlePressTapCount = singlePressTapCount,
                            onSinglePressTapCountChange = { singlePressTapCount = it },
                            installedTacticalTools = installedTacticalTools,
                            dynamicActionTokens = dynamicActionTokens,
                            tokenLabelCache = tokenLabelCache
                        )
                    }
                }
            }
            item(key = "about_feedback") {
                AboutFeedbackSection(context = context)
            }
        }
    }
}