package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences

@Composable
fun DeflectorTabBody(
    isLeft: Boolean,
    tabIndex: Int,
    tabTitle: String,
    blueprintTabTargetState: MutableState<Int?>,
    context: Context,
    prefs: SharedPreferences,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    listState: LazyListState,
    onRefreshNeeded: () -> Unit,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    pinnedSectionState: MutableState<String>,
    sectionOrderStrState: MutableState<String>,
    sectionTitles: Map<String, String>,
    isFlankUnifiedState: MutableState<Boolean>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogState: MutableState<Boolean>,
    isCenterExpandedState: MutableState<Boolean>,
    isCenterGeoExpandedState: MutableState<Boolean>,
    isUnifiedExpandedState: MutableState<Boolean>,
    isUnifiedGeoExpandedState: MutableState<Boolean>,
    isUnifiedGesturesExpandedState: MutableState<Boolean>,
    isUnifiedScrubExpandedState: MutableState<Boolean>,
    isTopExpandedState: MutableState<Boolean>,
    isTopGeoExpandedState: MutableState<Boolean>,
    isTopGesturesExpandedState: MutableState<Boolean>,
    isTopScrubExpandedState: MutableState<Boolean>,
    isBottomExpandedState: MutableState<Boolean>,
    isBottomGeoExpandedState: MutableState<Boolean>,
    isBottomGesturesExpandedState: MutableState<Boolean>,
    isBottomScrubExpandedState: MutableState<Boolean>
) {
    var blueprintTabTarget by blueprintTabTargetState
    var isFlankUnified by isFlankUnifiedState
    var pinnedSection by pinnedSectionState
    var sectionOrderStr by sectionOrderStrState
    var showUnifyInfoDialog by showUnifyInfoDialogState
    var showUnifyTemplateDialog by showUnifyTemplateDialogState

    var isCenterExpanded by isCenterExpandedState
    var isUnifiedExpanded by isUnifiedExpandedState
    var isUnifiedGeoExpanded by isUnifiedGeoExpandedState
    var isUnifiedGesturesExpanded by isUnifiedGesturesExpandedState
    var isUnifiedScrubExpanded by isUnifiedScrubExpandedState

    var isTopExpanded by isTopExpandedState
    var isTopGeoExpanded by isTopGeoExpandedState
    var isTopGesturesExpanded by isTopGesturesExpandedState
    var isTopScrubExpanded by isTopScrubExpandedState

    var isBottomExpanded by isBottomExpandedState
    var isBottomGeoExpanded by isBottomGeoExpandedState
    var isBottomGesturesExpanded by isBottomGesturesExpandedState
    var isBottomScrubExpanded by isBottomScrubExpandedState

    val coroutineScope = rememberCoroutineScope()
    var subBlueprintTarget by remember { mutableStateOf<String?>(null) }
    var pendingMooringConfirm by remember { mutableStateOf<MooringConfirmData?>(null) }
    var showOverridesDeck by remember { mutableStateOf(false) }

    val secPrefix = if (isLeft) "left_" else ""
    val topSecId = "${secPrefix}top"
    val bottomSecId = "${secPrefix}bottom"
    val unifiedSecId = "${secPrefix}unified"
    val centerSecId = "${secPrefix}center"
    val trackpadSecId = "${secPrefix}trackpad"

    val customVectors = remember(isLeft) { getCustomVectorsForFlank(isLeft) }
    val isTrackpadLabsEnabled = com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs)

    val defaultOrder = buildList {
        add(centerSecId)
        if (isFlankUnified) {
            add(unifiedSecId)
        } else {
            add(topSecId)
            add(bottomSecId)
        }
        if (isTrackpadLabsEnabled) {
            add(trackpadSecId)
        }
    }
    val currentOrder = sectionOrderStr.split(",").map { it.trim() }.filter { it in defaultOrder }.distinct().let { list ->
        list + (defaultOrder - list.toSet())
    }

    val defaultSub = listOf("geo", "scrub", "gestures")
    val subTopStr = prefs.getString("pref_sub_order_${topSecId}_2", "geo,scrub,gestures")!!
    var currentSubTop by remember { mutableStateOf(subTopStr.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) }) }

    val subBottomStr = prefs.getString("pref_sub_order_${bottomSecId}_2", "geo,scrub,gestures")!!
    var currentSubBottom by remember { mutableStateOf(subBottomStr.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) }) }

    val subUnifiedStr = prefs.getString("pref_sub_order_${unifiedSecId}_2", "geo,scrub,gestures")!!
    var currentSubUnified by remember { mutableStateOf(subUnifiedStr.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) }) }

    val sectionOrderPrefKey = if (isLeft) LightspeedPreferences.KEY_TAB_SECTION_ORDER_0 else LightspeedPreferences.KEY_TAB_SECTION_ORDER_2
    val pinnedAccordionPrefKey = if (isLeft) LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0 else LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2

    if (blueprintTabTarget == tabIndex) {
        DeflectorBlueprintSection(
            tabTitle = tabTitle,
            currentOrder = currentOrder,
            pinnedSection = pinnedSection,
            sectionTitles = sectionTitles,
            topSecId = topSecId,
            bottomSecId = bottomSecId,
            unifiedSecId = unifiedSecId,
            currentSubTop = currentSubTop,
            currentSubBottom = currentSubBottom,
            currentSubUnified = currentSubUnified,
            sectionOrderPrefKey = sectionOrderPrefKey,
            pinnedAccordionPrefKey = pinnedAccordionPrefKey,
            prefs = prefs,
            onOrderChange = { sectionOrderStr = it },
            onPinnedChange = { pinnedSection = it },
            onExitBlueprint = { blueprintTabTarget = null },
            onSubTopChange = { currentSubTop = it },
            onSubBottomChange = { currentSubBottom = it },
            onSubUnifiedChange = { currentSubUnified = it }
        )
    } else {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landscapeMode = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        val isCustomLandscape = isLandscape && (landscapeMode == LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM)

        var prefsChangeTrigger by remember { mutableIntStateOf(0) }
        DisposableEffect(prefs) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key != null && (key.contains("sidebar") || key.contains("deflector"))) {
                    prefsChangeTrigger++
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val sidebarPrefKey = if (isLeft) "pref_sidebar_left_" else "pref_sidebar_"
        val centerHeight = remember(prefsChangeTrigger, isCustomLandscape, isLeft) {
            if (isCustomLandscape) prefs.getInt("${sidebarPrefKey}center_height_landscape", 70)
            else prefs.getInt("${sidebarPrefKey}center_height", 70)
        }
        val topHeight = remember(prefsChangeTrigger, isCustomLandscape, isLeft) {
            if (isCustomLandscape) prefs.getInt("${sidebarPrefKey}top_height_landscape", 220)
            else prefs.getInt("${sidebarPrefKey}top_height", 220)
        }
        val bottomHeight = remember(prefsChangeTrigger, isCustomLandscape, isLeft) {
            if (isCustomLandscape) prefs.getInt("${sidebarPrefKey}bottom_height_landscape", 250)
            else prefs.getInt("${sidebarPrefKey}bottom_height", 250)
        }

        val totalDeflectorSpanDp = topHeight + centerHeight + bottomHeight
        val screenHeightDp = configuration.screenHeightDp
        val deflectorCoverageRatio = if (screenHeightDp > 0) totalDeflectorSpanDp.toFloat() / screenHeightDp.toFloat() else 0f
        val exceedsEdgeThreshold = deflectorCoverageRatio > 0.25f

        val isNativeInsetsZeroed = remember(prefsChangeTrigger) {
            try {
                val left = android.provider.Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_left", -1f)
                val right = android.provider.Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_right", -1f)
                val prefSavedScale = prefs.getFloat("sys_override_edge_gesture", -1f)
                (left in 0.0f..0.01f && right in 0.0f..0.01f) || (prefSavedScale in 0.0f..0.01f)
            } catch (_: Exception) {
                val prefSavedScale = prefs.getFloat("sys_override_edge_gesture", -1f)
                prefSavedScale in 0.0f..0.01f
            }
        }

        val hasSeenEdgeNotice = prefs.getBoolean("pref_has_seen_edge_sovereignty_tip", false)
        var dismissedWarningSession by remember { mutableStateOf(false) }

        val showEdgeSovereigntyNotice = !isNativeInsetsZeroed && ((exceedsEdgeThreshold && !dismissedWarningSession) || (!hasSeenEdgeNotice))

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "${secPrefix}deflector_master") {
                var isEnabled by remember {
                    mutableStateOf(
                        if (isLeft) LightspeedPreferences.isLeftDeflectorEnabled(context)
                        else LightspeedPreferences.isRightDeflectorEnabled(context)
                    )
                }
                var startupMode by remember {
                    mutableStateOf(prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, "always_armed") ?: "always_armed")
                }

                DeflectorMasterCard(
                    flankName = if (isLeft) "Left Deflector" else "Right Deflector",
                    isEnabled = isEnabled,
                    onToggle = { enabled ->
                        isEnabled = enabled
                        if (isLeft) LightspeedPreferences.setLeftDeflectorEnabled(context, enabled)
                        else LightspeedPreferences.setRightDeflectorEnabled(context, enabled)
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                        LightspeedFlightNotificationManager.update(context)
                    },
                    startupMode = startupMode,
                    onStartupModeChange = { mode ->
                        startupMode = mode
                        prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, mode).apply()
                    }
                )
            }

            if (showEdgeSovereigntyNotice) {
                item(key = "${secPrefix}edge_sovereignty_tip") {
                    EdgeSovereigntyNoticeCard(
                        exceedsEdgeThreshold = exceedsEdgeThreshold,
                        deflectorCoverageRatio = deflectorCoverageRatio,
                        totalDeflectorSpanDp = totalDeflectorSpanDp,
                        screenHeightDp = screenHeightDp,
                        onDismiss = {
                            dismissedWarningSession = true
                            prefs.edit().putBoolean("pref_has_seen_edge_sovereignty_tip", true).apply()
                        },
                        onOpenOverrides = { showOverridesDeck = true }
                    )
                }
            }

            item {
                DeflectorLandscapeGeometryCard(
                    context = context,
                    prefs = prefs,
                    onRefreshNeeded = onRefreshNeeded
                )
            }

            item {
                UnifyFlankActionsCard(
                    isUnified = isFlankUnified,
                    onToggle = { enable ->
                        if (enable) {
                            showUnifyTemplateDialog = true
                        } else {
                            isFlankUnified = false
                            val key = if (isLeft) "pref_sidebar_left_link_flank_actions" else "pref_sidebar_right_link_flank_actions"
                            prefs.edit().putBoolean(key, false).apply()
                            safeReloadPreferences()
                            onRefreshNeeded()
                        }
                    },
                    onInfoClick = { showUnifyInfoDialog = true }
                )
            }

            val expStates = buildDeflectorExpansionStates(
                tabIndex = tabIndex,
                centerSecId = centerSecId,
                unifiedSecId = unifiedSecId,
                topSecId = topSecId,
                bottomSecId = bottomSecId,
                isCenterExpanded = isCenterExpanded,
                isUnifiedExpanded = isUnifiedExpanded,
                isTopExpanded = isTopExpanded,
                isBottomExpanded = isBottomExpanded,
                isUnifiedGeoExpanded = isUnifiedGeoExpanded,
                isUnifiedScrubExpanded = isUnifiedScrubExpanded,
                isUnifiedGesturesExpanded = isUnifiedGesturesExpanded,
                isTopGeoExpanded = isTopGeoExpanded,
                isTopScrubExpanded = isTopScrubExpanded,
                isTopGesturesExpanded = isTopGesturesExpanded,
                isBottomGeoExpanded = isBottomGeoExpanded,
                isBottomScrubExpanded = isBottomScrubExpanded,
                isBottomGesturesExpanded = isBottomGesturesExpanded,
                prefs = prefs,
                toggleSection = toggleSection,
                onSetCenterExpanded = { isCenterExpanded = it },
                onSetUnifiedExpanded = { isUnifiedExpanded = it },
                onSetTopExpanded = { isTopExpanded = it },
                onSetBottomExpanded = { isBottomExpanded = it },
                onSetUnifiedGeoExpanded = { isUnifiedGeoExpanded = it },
                onSetUnifiedScrubExpanded = { isUnifiedScrubExpanded = it },
                onSetUnifiedGesturesExpanded = { isUnifiedGesturesExpanded = it },
                onSetTopGeoExpanded = { isTopGeoExpanded = it },
                onSetTopScrubExpanded = { isTopScrubExpanded = it },
                onSetTopGesturesExpanded = { isTopGesturesExpanded = it },
                onSetBottomGeoExpanded = { isBottomGeoExpanded = it },
                onSetBottomScrubExpanded = { isBottomScrubExpanded = it },
                onSetBottomGesturesExpanded = { isBottomGesturesExpanded = it },
                onRefreshNeeded = onRefreshNeeded
            )

            renderDeflectorOrderedSections(
                currentOrder = currentOrder,
                centerSecId = centerSecId,
                unifiedSecId = unifiedSecId,
                topSecId = topSecId,
                bottomSecId = bottomSecId,
                trackpadSecId = trackpadSecId,
                isFlankUnified = isFlankUnified,
                isLeft = isLeft,
                context = context,
                prefs = prefs,
                dynamicActionTokens = dynamicActionTokens,
                tokenLabelCache = tokenLabelCache,
                customVectors = customVectors,
                listState = listState,
                coroutineScope = coroutineScope,
                subBlueprintTarget = subBlueprintTarget,
                onSubBlueprintToggle = { secId ->
                    subBlueprintTarget = if (subBlueprintTarget == secId) null else secId
                },
                exp = expStates,
                onMooringConfirm = { pendingMooringConfirm = it },
                onRefreshNeeded = onRefreshNeeded
            )
        }
    }

    MooringConfirmDialog(
        confirmData = pendingMooringConfirm,
        onDismiss = { pendingMooringConfirm = null }
    )

    DeflectorOverridesDialog(
        visible = showOverridesDeck,
        context = context,
        prefs = prefs,
        onDismiss = { showOverridesDeck = false }
    )
}
