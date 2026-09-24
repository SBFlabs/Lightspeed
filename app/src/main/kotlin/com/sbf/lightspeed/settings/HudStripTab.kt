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

    val defaultOrder1 = listOf("sensor_deck", "telemetry_indicators", "tactical_hardware", "system_overrides", "config_vault", "experimental_labs")
    var currentOrder1 = sectionOrder1Str.split(",").map { it.trim() }.filter { it in defaultOrder1 }.distinct().let { list ->
        list + (defaultOrder1 - list.toSet())
    }.toMutableList()

    LaunchedEffect(Unit) {
        if (currentOrder1.lastOrNull() == "system_overrides" && currentOrder1.contains("config_vault")) {
            currentOrder1.remove("system_overrides")
            currentOrder1.add(currentOrder1.indexOf("config_vault"), "system_overrides")
            prefs.edit().putString("pref_tab_order_1", currentOrder1.joinToString(",")).apply()
        }
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
                                onRefreshNeeded = onRefreshNeeded
                            )
                        }
                    }
                    "config_vault" -> {
                        item(key = "config_vault") {
                            HudConfigVaultSection(
                                isExpanded = isConfigVaultExpanded,
                                onToggle = {
                                    toggleSection(1, "config_vault", isConfigVaultExpanded) { isConfigVaultExpanded = it }
                                    prefs.edit().putBoolean("pref_section_backup_expanded", isConfigVaultExpanded).apply()
                                },
                                onExportClicked = { exportLauncher.launch("lightspeed-backup.json") },
                                onShowImportOptions = { showImportOptionsDialog = true },
                                onShowResetConfirm = { showResetConfirmDialog = true }
                            )
                        }
                    }
                    "system_overrides" -> {
                        item(key = "system_overrides") {
                            HudSystemOverridesSection(
                                context = context,
                                prefs = prefs,
                                isExpanded = isSystemOverridesExpanded,
                                onToggle = {
                                    toggleSection(1, "system_overrides", isSystemOverridesExpanded) { isSystemOverridesExpanded = it }
                                    prefs.edit()
                                        .putBoolean("pref_section_system_overrides_expanded", isSystemOverridesExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                }
                            )
                        }
                    }
                    "experimental_labs" -> {
                        item(key = "experimental_labs") {
                            HudExperimentalLabsSection(
                                context = context,
                                prefs = prefs,
                                isExpanded = isExperimentalLabsExpanded,
                                onToggle = {
                                    toggleSection(1, "experimental_labs", isExperimentalLabsExpanded) { isExperimentalLabsExpanded = it }
                                    prefs.edit()
                                        .putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, isExperimentalLabsExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                },
                                isSyntheticGravityExpanded = isSyntheticGravityExpanded,
                                onToggleSyntheticGravity = {
                                    isSyntheticGravityExpanded = !isSyntheticGravityExpanded
                                    prefs.edit()
                                        .putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, isSyntheticGravityExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                },
                                isNotchCalibExpanded = isNotchCalibExpanded,
                                onToggleNotchCalib = {
                                    isNotchCalibExpanded = !isNotchCalibExpanded
                                    prefs.edit()
                                        .putBoolean("pref_sub_notch_calib", isNotchCalibExpanded)
                                        .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, isNotchCalibExpanded)
                                        .apply()
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
                                    prefs.edit().putBoolean("pref_sub_power_expanded", isSubPowerExpanded).apply()
                                },
                                isSinglePressUnlocked = isSinglePressUnlocked,
                                onSinglePressUnlockedChange = { isSinglePressUnlocked = it },
                                singlePressTapCount = singlePressTapCount,
                                onSinglePressTapCountChange = { singlePressTapCount = it },
                                installedTacticalTools = installedTacticalTools,
                                dynamicActionTokens = dynamicActionTokens,
                                tokenLabelCache = tokenLabelCache,
                                isRefuelingExpanded = isRefuelingExpanded,
                                onToggleRefueling = {
                                    isRefuelingExpanded = !isRefuelingExpanded
                                    prefs.edit().putBoolean("pref_section_refueling_expanded", isRefuelingExpanded).apply()
                                },
                                onShowAmoledWarning = { showAmoledWarningDialog = true },
                                onRefreshNeeded = onRefreshNeeded
                            )
                        }
                    }
                }
            }
        }
    }
}