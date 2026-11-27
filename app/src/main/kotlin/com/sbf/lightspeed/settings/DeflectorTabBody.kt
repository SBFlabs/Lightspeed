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
    toggleSubSection: (Int, String, String, Boolean, (Boolean) -> Unit) -> Unit,
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
    isBottomScrubExpandedState: MutableState<Boolean>,
    isTrackpadExpandedState: MutableState<Boolean>,
    isTrackpadGeoExpandedState: MutableState<Boolean>,
    isTrackpadPhysicsExpandedState: MutableState<Boolean>,
    onApplyCustomPinned: () -> Unit = {}
) {
    var blueprintTabTarget by blueprintTabTargetState
    var isFlankUnified by isFlankUnifiedState
    var pinnedSection by pinnedSectionState
    var sectionOrderStr by sectionOrderStrState
    var showUnifyInfoDialog by showUnifyInfoDialogState
    var showUnifyTemplateDialog by showUnifyTemplateDialogState

    var isCenterExpanded by isCenterExpandedState
    var isCenterGeoExpanded by isCenterGeoExpandedState
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
    var isTrackpadExpanded by isTrackpadExpandedState
    var isTrackpadGeoExpanded by isTrackpadGeoExpandedState
    var isTrackpadPhysicsExpanded by isTrackpadPhysicsExpandedState

    val coroutineScope = rememberCoroutineScope()
    var subBlueprintTarget by remember { mutableStateOf<String?>(null) }
    var pendingMooringConfirm by remember { mutableStateOf<MooringConfirmData?>(null) }
    var showOverridesDeck by remember { mutableStateOf(false) }

    val secPrefix = if (isLeft) "left_" else ""
    val flankKey = if (isLeft) "left" else "right"
    val topSecId = "${secPrefix}top"
    val bottomSecId = "${secPrefix}bottom"
    val unifiedSecId = "${secPrefix}unified"
    val centerSecId = "${secPrefix}center"
    val trackpadSecId = "${secPrefix}trackpad"

    var prefsChangeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (
                    key.endsWith("_height") ||
                    key.endsWith("_height_landscape") ||
                    key == LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE ||
                    key == "sys_override_edge_gesture" ||
                    key == com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LABS_ENABLED ||
                    key == com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED ||
                    key == com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED
                )
            ) {
                prefsChangeTrigger++
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val customVectors = remember(isLeft, com.sbf.lightspeed.system.LightspeedLocaleEngine.state) { getCustomVectorsForFlank(isLeft) }
    val isTrackpadLabsEnabled = remember(prefsChangeTrigger) {
        com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs)
    }

    val defaultOrder = remember(centerSecId, unifiedSecId, topSecId, bottomSecId, trackpadSecId, isFlankUnified, isTrackpadLabsEnabled) {
        buildList {
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
    }
    val currentOrder = remember(sectionOrderStr, isFlankUnified, isTrackpadLabsEnabled, isLeft) {
        sectionOrderStr.split(",").map { it.trim() }.filter { it in defaultOrder }.distinct().let { list ->
            list + (defaultOrder - list.toSet())
        }
    }

    val defaultSub = remember { listOf("geo", "scrub", "gestures") }
    var currentSubTop by remember(topSecId) {
        val raw = prefs.getString("pref_sub_order_$topSecId", null)
            ?: prefs.getString("pref_sub_order_${topSecId}_2", "geo,scrub,gestures")!!
        mutableStateOf(raw.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) })
    }

    var currentSubBottom by remember(bottomSecId) {
        val raw = prefs.getString("pref_sub_order_$bottomSecId", null)
            ?: prefs.getString("pref_sub_order_${bottomSecId}_2", "geo,scrub,gestures")!!
        mutableStateOf(raw.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) })
    }

    var currentSubUnified by remember(unifiedSecId) {
        val raw = prefs.getString("pref_sub_order_$unifiedSecId", null)
            ?: prefs.getString("pref_sub_order_${unifiedSecId}_2", "geo,scrub,gestures")!!
        mutableStateOf(raw.split(",").filter { it in defaultSub }.let { it + (defaultSub - it.toSet()) })
    }

    val defaultTrackpadSub = remember { listOf("geo", "physics") }
    var currentSubTrackpad by remember(trackpadSecId) {
        val raw = prefs.getString("pref_sub_order_$trackpadSecId", "geo,physics") ?: "geo,physics"
        mutableStateOf(raw.split(",").filter { it in defaultTrackpadSub }.let { it + (defaultTrackpadSub - it.toSet()) })
    }

    val sectionOrderPrefKey = if (isLeft) LightspeedPreferences.KEY_TAB_SECTION_ORDER_0 else LightspeedPreferences.KEY_TAB_SECTION_ORDER_2
    val pinnedAccordionPrefKey = if (isLeft) LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0 else LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2
    val handlePinSection: (String) -> Unit = { secId ->
        pinnedSection = secId
        prefs.edit().putString(pinnedAccordionPrefKey, secId).apply()
        listOf(unifiedSecId, topSecId, bottomSecId, trackpadSecId).firstOrNull { secId.startsWith("${it}_") }?.let { parentId ->
            prefs.edit().putString("pref_sub_pinned_$parentId", secId.removePrefix("${parentId}_")).apply()
        }
        onApplyCustomPinned()
    }

    if (blueprintTabTarget == tabIndex) {
        DeflectorBlueprintSection(
            tabTitle = tabTitle,
            currentOrder = currentOrder,
            pinnedSection = pinnedSection,
            sectionTitles = sectionTitles,
            topSecId = topSecId,
            bottomSecId = bottomSecId,
            unifiedSecId = unifiedSecId,
            trackpadSecId = if (isTrackpadLabsEnabled) trackpadSecId else null,
            currentSubTop = currentSubTop,
            currentSubBottom = currentSubBottom,
            currentSubUnified = currentSubUnified,
            currentSubTrackpad = currentSubTrackpad,
            sectionOrderPrefKey = sectionOrderPrefKey,
            pinnedAccordionPrefKey = pinnedAccordionPrefKey,
            prefs = prefs,
            onOrderChange = { sectionOrderStr = it },
            onPinnedChange = handlePinSection,
            onExitBlueprint = { blueprintTabTarget = null },
            onSubTopChange = { currentSubTop = it },
            onSubBottomChange = { currentSubBottom = it },
            onSubUnifiedChange = { currentSubUnified = it },
            onSubTrackpadChange = { currentSubTrackpad = it }
        )
    } else {
        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val landscapeMode = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
        val isCustomLandscape = isLandscape && (landscapeMode == LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM)

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

        var isNativeInsetsZeroed by remember {
            mutableStateOf(prefs.getFloat("sys_override_edge_gesture", -1f) in 0.0f..0.01f)
        }
        LaunchedEffect(prefsChangeTrigger) {
            isNativeInsetsZeroed = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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
        }

        val isDismissedEdgeCard = prefs.getBoolean(LightspeedPreferences.KEY_DISMISS_EDGE_CONFLICT_CARD, false)
        var isEdgeNoticeDemoted by remember { mutableStateOf(isDismissedEdgeCard) }
        val showEdgeSovereigntyNotice = !isNativeInsetsZeroed
        val expStates = remember(
            tabIndex,
            centerSecId,
            unifiedSecId,
            topSecId,
            bottomSecId,
            trackpadSecId,
            flankKey,
            isCenterExpanded,
            isCenterGeoExpanded,
            isUnifiedExpanded,
            isTopExpanded,
            isBottomExpanded,
            isUnifiedGeoExpanded,
            isUnifiedScrubExpanded,
            isUnifiedGesturesExpanded,
            isTopGeoExpanded,
            isTopScrubExpanded,
            isTopGesturesExpanded,
            isBottomGeoExpanded,
            isBottomScrubExpanded,
            isBottomGesturesExpanded,
            isTrackpadExpanded,
            isTrackpadGeoExpanded,
            isTrackpadPhysicsExpanded,
            toggleSection,
            toggleSubSection
        ) {
            buildDeflectorExpansionStates(
                tabIndex = tabIndex,
                centerSecId = centerSecId,
                unifiedSecId = unifiedSecId,
                topSecId = topSecId,
                bottomSecId = bottomSecId,
                trackpadSecId = trackpadSecId,
                flankKey = flankKey,
                isCenterExpanded = isCenterExpanded,
                isCenterGeoExpanded = isCenterGeoExpanded,
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
                isTrackpadExpanded = isTrackpadExpanded,
                isTrackpadGeoExpanded = isTrackpadGeoExpanded,
                isTrackpadPhysicsExpanded = isTrackpadPhysicsExpanded,
                prefs = prefs,
                toggleSection = toggleSection,
                toggleSubSection = toggleSubSection,
                onSetCenterExpanded = { isCenterExpanded = it },
                onSetCenterGeoExpanded = { isCenterGeoExpanded = it },
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
                onSetTrackpadExpanded = { isTrackpadExpanded = it },
                onSetTrackpadGeoExpanded = { isTrackpadGeoExpanded = it },
                onSetTrackpadPhysicsExpanded = { isTrackpadPhysicsExpanded = it },
                onRefreshNeeded = onRefreshNeeded
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .verticalEdgeFade(listState),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "${secPrefix}deflector_master", contentType = "master_card") {
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
                    },
                    context = context,
                    prefs = prefs,
                    onRefreshNeeded = onRefreshNeeded
                )
            }

            if (showEdgeSovereigntyNotice && !isEdgeNoticeDemoted) {
                item(key = "${secPrefix}edge_sovereignty_tip", contentType = "notice_card") {
                    EdgeSovereigntyNoticeCard(
                        exceedsEdgeThreshold = exceedsEdgeThreshold,
                        deflectorCoverageRatio = deflectorCoverageRatio,
                        totalDeflectorSpanDp = totalDeflectorSpanDp,
                        screenHeightDp = screenHeightDp,
                        isDemoted = false,
                        onToggleDemote = {
                            isEdgeNoticeDemoted = true
                            prefs.edit()
                                .putBoolean(LightspeedPreferences.KEY_HAS_SEEN_EDGE_SOVEREIGNTY_TIP, true)
                                .putBoolean(LightspeedPreferences.KEY_DISMISS_EDGE_CONFLICT_CARD, true)
                                .apply()
                        },
                        onDismiss = {
                            isEdgeNoticeDemoted = true
                            prefs.edit()
                                .putBoolean(LightspeedPreferences.KEY_HAS_SEEN_EDGE_SOVEREIGNTY_TIP, true)
                                .putBoolean(LightspeedPreferences.KEY_DISMISS_EDGE_CONFLICT_CARD, true)
                                .apply()
                        },
                        onOpenOverrides = { showOverridesDeck = true }
                    )
                }
            }

            item(key = "${secPrefix}unify_flank_actions", contentType = "unify_card") {
                UnifyFlankActionsCard(
                    isUnified = isFlankUnified,
                    onToggle = { enable ->
                        if (enable) {
                            showUnifyTemplateDialog = true
                        } else {
                            isFlankUnified = false
                            val key = if (isLeft) LightspeedPreferences.KEY_SIDEBAR_LEFT_LINK_FLANK else LightspeedPreferences.KEY_SIDEBAR_RIGHT_LINK_FLANK
                            prefs.edit().putBoolean(key, false).apply()
                            safeReloadPreferences()
                            onRefreshNeeded()
                        }
                    },
                    onInfoClick = { showUnifyInfoDialog = true }
                )
            }

            renderDeflectorOrderedSections(
                currentOrder = currentOrder,
                centerSecId = centerSecId,
                unifiedSecId = unifiedSecId,
                topSecId = topSecId,
                bottomSecId = bottomSecId,
                trackpadSecId = trackpadSecId,
                isTrackpadLabsEnabled = isTrackpadLabsEnabled,
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
                pinnedSection = pinnedSection,
                currentSubTop = currentSubTop,
                currentSubBottom = currentSubBottom,
                currentSubUnified = currentSubUnified,
                currentSubTrackpad = currentSubTrackpad,
                onSubTopChange = { currentSubTop = it },
                onSubBottomChange = { currentSubBottom = it },
                onSubUnifiedChange = { currentSubUnified = it },
                onSubTrackpadChange = { currentSubTrackpad = it },
                onPinSection = handlePinSection,
                exp = expStates,
                onMooringConfirm = { pendingMooringConfirm = it },
                onRefreshNeeded = onRefreshNeeded
            )

            if (showEdgeSovereigntyNotice && isEdgeNoticeDemoted) {
                item(key = "${secPrefix}edge_sovereignty_tip_demoted", contentType = "notice_card") {
                    EdgeSovereigntyNoticeCard(
                        exceedsEdgeThreshold = exceedsEdgeThreshold,
                        deflectorCoverageRatio = deflectorCoverageRatio,
                        totalDeflectorSpanDp = totalDeflectorSpanDp,
                        screenHeightDp = screenHeightDp,
                        isDemoted = true,
                        onToggleDemote = {
                            isEdgeNoticeDemoted = false
                            prefs.edit()
                                .putBoolean(LightspeedPreferences.KEY_DISMISS_EDGE_CONFLICT_CARD, false)
                                .apply()
                        },
                        onDismiss = {},
                        onOpenOverrides = { showOverridesDeck = true }
                    )
                }
            }
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
