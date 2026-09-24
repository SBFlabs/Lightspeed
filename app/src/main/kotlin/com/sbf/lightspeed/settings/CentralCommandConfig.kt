package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
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
    val state = rememberCentralCommandState(
        context = context,
        prefs = prefs,
        toggleAllTrigger = toggleAllTrigger,
        onRefreshNeeded = onRefreshNeeded
    )

    val scope = rememberCoroutineScope()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromFile(context, uri)
        }
    }

    LaunchedEffect(jumpTargetTab, jumpTargetSection) {
        if (jumpTargetTab in 0..2) {
            state.pagerState.animateScrollToPage(jumpTargetTab)
        }
        when (jumpTargetSection) {
            "statusbar", "sensor_deck" -> state.isSensorDeckExpandedState.value = true
            "gravity", "synthetic_gravity", "orientation", "attitude" -> {
                state.isExperimentalLabsExpandedState.value = true
                state.isSyntheticGravityExpandedState.value = true
            }
            "telemetry", "telemetry_indicators", "beacons", "notch_beacon" -> state.isTelemetryExpandedState.value = true
            "refueling", "refueling_bay" -> {
                state.isExperimentalLabsExpandedState.value = true
                state.isRefuelingExpandedState.value = true
            }
            "backup", "config_vault", "vault" -> state.isConfigVaultExpandedState.value = true
            "system_overrides" -> state.isSystemOverridesExpandedState.value = true
            "experimental_labs", "labs", "experimental", "core_cooling" -> state.isExperimentalLabsExpandedState.value = true
            "power", "power_button" -> {
                state.isExperimentalLabsExpandedState.value = true
                state.isSubPowerExpandedState.value = true
            }
            "orbital", "orbital_capsule", "notch" -> {
                state.isTelemetryExpandedState.value = true
                state.isNotchCalibExpandedState.value = true
            }
            "wings" -> {
                if (jumpTargetTab == 0) state.isLeftCenterExpandedState.value = true
                else state.isCenterExpandedState.value = true
            }
        }
    }

    // Auto-hide / auto-show preview on tab swipe
    LaunchedEffect(state.pagerState) {
        snapshotFlow { state.pagerState.currentPage }
            .drop(1)
            .collect { page ->
                val leftActive = page == 0 && (
                    state.isLeftCenterExpandedState.value ||
                    (if (state.isLeftFlankUnifiedState.value) state.isLeftUnifiedExpandedState.value else state.isLeftTopExpandedState.value || state.isLeftBottomExpandedState.value)
                )
                val canopyActive = page == 1 && state.isSensorDeckExpandedState.value && state.isStatusBarGeoExpandedState.value
                val rightActive = page == 2 && (
                    state.isCenterExpandedState.value ||
                    (if (state.isRightFlankUnifiedState.value) state.isRightUnifiedExpandedState.value else state.isTopExpandedState.value || state.isBottomExpandedState.value)
                )
                val anyRail = state.isHorizonRailGeomExpandedState.value || state.isHorizonRailColorExpandedState.value || state.isHorizonRailTextExpandedState.value
                val notchActive = page == 1 && state.isTelemetryExpandedState.value && state.isNotchCalibExpandedState.value
                val railActive = page == 1 && state.isTelemetryExpandedState.value && anyRail
                prefs.edit()
                    .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, leftActive)
                    .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, canopyActive)
                    .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, rightActive)
                    .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, notchActive)
                    .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, railActive)
                    .apply()
                safeReloadPreferences()
            }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        var isA11yActive by remember {
            mutableStateOf(com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context))
        }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isA11yActive = com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        LaunchedEffect(isA11yActive) {
            if (!isA11yActive) {
                while (!isA11yActive) {
                    kotlinx.coroutines.delay(400)
                    if (com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                        isA11yActive = true
                        break
                    }
                }
            }
        }

        if (!isA11yActive) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Text(
                            text = "CORE AVIONICS DORMANT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = "Lightspeed requires Accessibility Service to attach screen deflectors and capture gesture maneuvers.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (rikka.shizuku.Shizuku.pingBinder()) {
                                    if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        rikka.shizuku.Shizuku.requestPermission(com.sbf.lightspeed.system.ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                                        android.widget.Toast.makeText(context, "Authorize Lightspeed in Shizuku / Shevery", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                }
                                val revived = com.sbf.lightspeed.system.LightspeedWatchdogEngine.reviveAccessibilityService(context)
                                if (revived) {
                                    isA11yActive = true
                                    android.widget.Toast.makeText(context, "⚡ Core Avionics Armed!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    com.sbf.lightspeed.system.LightspeedWatchdogEngine.openAccessibilitySettings(context)
                                    android.widget.Toast.makeText(context, "Enable Lightspeed in Accessibility Settings", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("⚡ ARM SERVICE", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                context.startActivity(android.content.Intent(context, com.sbf.lightspeed.system.LightspeedPerimeterActivity::class.java))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🛡️ PERIMETER", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

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
            val tabTitles = listOf(
                LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_LEFT),
                LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_CENTER),
                LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_RIGHT)
            )
            tabTitles.forEachIndexed { index, tabTitle ->
                val isSelected = state.pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                if (isSelected) {
                                    LightspeedHapticEngine.heavyClick(context)
                                    state.popoverTabTargetState.value = index
                                } else {
                                    scope.launch { state.pagerState.animateScrollToPage(index) }
                                }
                            },
                            onLongClick = {
                                LightspeedHapticEngine.heavyClick(context)
                                state.popoverTabTargetState.value = index
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

        // Preview Sync
        LaunchedEffect(
            state.pagerState.currentPage,
            state.isLeftTopGeoExpandedState.value, state.isLeftBottomGeoExpandedState.value, state.isLeftUnifiedGeoExpandedState.value,
            state.isTopGeoExpandedState.value, state.isBottomGeoExpandedState.value, state.isRightUnifiedGeoExpandedState.value,
            state.isStatusBarGeoExpandedState.value
        ) {
            val editor = prefs.edit()
            val showLeftPreview = when (state.pagerState.currentPage) {
                0 -> state.isLeftTopGeoExpandedState.value || state.isLeftBottomGeoExpandedState.value || state.isLeftUnifiedGeoExpandedState.value
                else -> false
            }
            val showStatusBarPreview = when (state.pagerState.currentPage) {
                1 -> state.isStatusBarGeoExpandedState.value
                else -> false
            }
            val showRightPreview = when (state.pagerState.currentPage) {
                2 -> state.isTopGeoExpandedState.value || state.isBottomGeoExpandedState.value || state.isRightUnifiedGeoExpandedState.value
                else -> false
            }

            editor.putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, showLeftPreview)
            editor.putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, showRightPreview)
            editor.putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, showStatusBarPreview)
            editor.apply()

            safeReloadPreferences()
        }

        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            beyondViewportPageCount = 1
        ) { pageIndex ->
            when (pageIndex) {
                0 -> {
                    LeftDeflectorTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isLeftBottomExpandedState = state.isLeftBottomExpandedState,
                        isLeftBottomGeoExpandedState = state.isLeftBottomGeoExpandedState,
                        isLeftBottomGesturesExpandedState = state.isLeftBottomGesturesExpandedState,
                        isLeftBottomScrubExpandedState = state.isLeftBottomScrubExpandedState,
                        isLeftCenterExpandedState = state.isLeftCenterExpandedState,
                        isLeftCenterGeoExpandedState = state.isLeftCenterGeoExpandedState,
                        isLeftFlankUnifiedState = state.isLeftFlankUnifiedState,
                        isLeftTopExpandedState = state.isLeftTopExpandedState,
                        isLeftTopGeoExpandedState = state.isLeftTopGeoExpandedState,
                        isLeftTopGesturesExpandedState = state.isLeftTopGesturesExpandedState,
                        isLeftTopScrubExpandedState = state.isLeftTopScrubExpandedState,
                        isLeftUnifiedExpandedState = state.isLeftUnifiedExpandedState,
                        isLeftUnifiedGeoExpandedState = state.isLeftUnifiedGeoExpandedState,
                        isLeftUnifiedGesturesExpandedState = state.isLeftUnifiedGesturesExpandedState,
                        isLeftUnifiedScrubExpandedState = state.isLeftUnifiedScrubExpandedState,
                        listState0 = state.listState0,
                        onRefreshNeeded = onRefreshNeeded,
                        pinnedSection0State = state.pinnedSection0State,
                        prefs = prefs,
                        sectionOrder0StrState = state.sectionOrder0StrState,
                        sectionTitles0 = state.sectionTitles0,
                        showUnifyInfoDialogState = state.showUnifyInfoDialogState,
                        showUnifyTemplateDialogForLeftState = state.showUnifyTemplateDialogForLeftState,
                        toggleSection = state.toggleSection,
                        tokenLabelCache = state.tokenLabelCache
                    )
                }

                1 -> {
                    HudStripTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        currentThresholdState = state.currentThresholdState,
                        isSystemOverridesExpandedState = state.isSystemOverridesExpandedState,
                        currentZImpulseState = state.currentZImpulseState,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isConfigVaultExpandedState = state.isConfigVaultExpandedState,
                        isExperimentalLabsExpandedState = state.isExperimentalLabsExpandedState,
                        isHorizonRailColorExpandedState = state.isHorizonRailColorExpandedState,
                        isHorizonRailGeomExpandedState = state.isHorizonRailGeomExpandedState,
                        isHorizonRailTextExpandedState = state.isHorizonRailTextExpandedState,
                        isMarqueeSubSectionExpandedState = state.isMarqueeSubSectionExpandedState,
                        isNotchCalibExpandedState = state.isNotchCalibExpandedState,
                        isOemNoticeDemotedState = state.isOemNoticeDemotedState,
                        isRefuelingExpandedState = state.isRefuelingExpandedState,
                        isSensorDeckExpandedState = state.isSensorDeckExpandedState,
                        isSinglePressUnlockedState = state.isSinglePressUnlockedState,
                        isStatusBarGeoExpandedState = state.isStatusBarGeoExpandedState,
                        isStatusBarGesturesExpandedState = state.isStatusBarGesturesExpandedState,
                        isSubHullTapExpandedState = state.isSubHullTapExpandedState,
                        isSubPowerExpandedState = state.isSubPowerExpandedState,
                        isSubVolumeExpandedState = state.isSubVolumeExpandedState,
                        isSyntheticGravityExpandedState = state.isSyntheticGravityExpandedState,
                        isTacticalHardwareExpandedState = state.isTacticalHardwareExpandedState,
                        isTelemetryExpandedState = state.isTelemetryExpandedState,
                        listState1 = state.listState1,
                        onRefreshNeeded = onRefreshNeeded,
                        pendingBackTapScopeState = state.pendingBackTapScopeState,
                        pinnedSection1State = state.pinnedSection1State,
                        prefs = prefs,
                        sectionOrder1StrState = state.sectionOrder1StrState,
                        showAmoledWarningDialogState = state.showAmoledWarningDialogState,
                        showBatteryWarningDialogState = state.showBatteryWarningDialogState,
                        showImportOptionsDialogState = state.showImportOptionsDialogState,
                        showNotificationAccessDialogState = state.showNotificationAccessDialogState,
                        showOemShieldDialogState = state.showOemShieldDialogState,
                        showResetConfirmDialogState = state.showResetConfirmDialogState,
                        singlePressTapCountState = state.singlePressTapCountState,
                        sectionTitles1 = state.sectionTitles1,
                        thresholdCrossedFlashState = state.thresholdCrossedFlashState,
                        toggleSection = state.toggleSection,
                        tokenLabelCache = state.tokenLabelCache
                    )
                }

                2 -> {
                    RightDeflectorTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isBottomExpandedState = state.isBottomExpandedState,
                        isBottomGeoExpandedState = state.isBottomGeoExpandedState,
                        isBottomGesturesExpandedState = state.isBottomGesturesExpandedState,
                        isBottomScrubExpandedState = state.isBottomScrubExpandedState,
                        isCenterExpandedState = state.isCenterExpandedState,
                        isCenterGeoExpandedState = state.isCenterGeoExpandedState,
                        isLeftFlankUnifiedState = state.isLeftFlankUnifiedState,
                        isRightFlankUnifiedState = state.isRightFlankUnifiedState,
                        isRightUnifiedExpandedState = state.isRightUnifiedExpandedState,
                        isRightUnifiedGeoExpandedState = state.isRightUnifiedGeoExpandedState,
                        isRightUnifiedGesturesExpandedState = state.isRightUnifiedGesturesExpandedState,
                        isRightUnifiedScrubExpandedState = state.isRightUnifiedScrubExpandedState,
                        isTopExpandedState = state.isTopExpandedState,
                        isTopGeoExpandedState = state.isTopGeoExpandedState,
                        isTopGesturesExpandedState = state.isTopGesturesExpandedState,
                        isTopScrubExpandedState = state.isTopScrubExpandedState,
                        listState2 = state.listState2,
                        onRefreshNeeded = onRefreshNeeded,
                        pinnedSection2State = state.pinnedSection2State,
                        prefs = prefs,
                        sectionOrder2StrState = state.sectionOrder2StrState,
                        sectionTitles2 = state.sectionTitles2,
                        showUnifyInfoDialogState = state.showUnifyInfoDialogState,
                        showUnifyTemplateDialogForRightState = state.showUnifyTemplateDialogForRightState,
                        toggleSection = state.toggleSection,
                        tokenLabelCache = state.tokenLabelCache
                    )
                }
            }
        }

        // Central Command Modal Dialogs Deck
        CentralCommandDialogDeck(
            context = context,
            prefs = prefs,
            viewModel = viewModel,
            showResetConfirmDialogState = state.showResetConfirmDialogState,
            showImportOptionsDialogState = state.showImportOptionsDialogState,
            showPasteJsonDialogState = state.showPasteJsonDialogState,
            pastedJsonTextState = state.pastedJsonTextState,
            showSymmetryInfoDialogState = state.showSymmetryInfoDialogState,
            showUnifyInfoDialogState = state.showUnifyInfoDialogState,
            showUnifyTemplateDialogForLeftState = state.showUnifyTemplateDialogForLeftState,
            showUnifyTemplateDialogForRightState = state.showUnifyTemplateDialogForRightState,
            showOemShieldDialogState = state.showOemShieldDialogState,
            showBatteryWarningDialogState = state.showBatteryWarningDialogState,
            showNotificationAccessDialogState = state.showNotificationAccessDialogState,
            showAmoledWarningDialogState = state.showAmoledWarningDialogState,
            pendingBackTapScope = state.pendingBackTapScopeState.value,
            importLauncher = importLauncher,
            onSetLeftFlankUnified = { state.isLeftFlankUnifiedState.value = it },
            onSetRightFlankUnified = { state.isRightFlankUnifiedState.value = it },
            onRefreshNeeded = onRefreshNeeded
        )

        val popoverTarget = state.popoverTabTargetState.value
        if (popoverTarget != null) {
            val tabTitle = when (popoverTarget) {
                0 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_LEFT)
                1 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_CENTER)
                2 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_RIGHT)
                else -> "Avionics Tab"
            }
            val currentMode = when (popoverTarget) {
                0 -> state.tabMode0State.value
                1 -> state.tabMode1State.value
                2 -> state.tabMode2State.value
                else -> "sticky"
            }
            val currentPinned = when (popoverTarget) {
                0 -> state.pinnedSection0State.value
                1 -> state.pinnedSection1State.value
                2 -> state.pinnedSection2State.value
                else -> null
            }
            val secTitles = when (popoverTarget) {
                0 -> state.sectionTitles0
                1 -> state.sectionTitles1
                2 -> state.sectionTitles2
                else -> emptyMap()
            }

            TabAccordionPopover(
                tabIndex = popoverTarget,
                tabTitle = tabTitle,
                currentMode = currentMode,
                pinnedSectionId = currentPinned,
                sectionTitles = secTitles,
                onSelectMode = { newMode: String ->
                    when (popoverTarget) {
                        0 -> {
                            state.tabMode0State.value = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, newMode).apply()
                        }
                        1 -> {
                            state.tabMode1State.value = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, newMode).apply()
                        }
                        2 -> {
                            state.tabMode2State.value = newMode
                            prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, newMode).apply()
                        }
                    }
                    if (newMode == "custom_pinned" && (currentPinned == null || currentPinned == "none" || currentPinned.isEmpty())) {
                        state.blueprintTabTargetState.value = popoverTarget
                    }
                    state.popoverTabTargetState.value = null
                    onRefreshNeeded()
                },
                onToggleBlueprintMode = {
                    state.blueprintTabTargetState.value = popoverTarget
                },
                onDismiss = {
                    state.popoverTabTargetState.value = null
                }
            )
        }
    }
}
