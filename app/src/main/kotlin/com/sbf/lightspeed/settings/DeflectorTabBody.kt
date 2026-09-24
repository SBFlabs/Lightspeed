package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary

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

    val customVectors = remember(isLeft) {
        if (isLeft) {
            listOf(
                "TAP" to ("Tap" to ArrowDirection.TAP),
                "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
                "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
                "SWIPE_RIGHT" to ("Swipe Inward" to ArrowDirection.SWIPE_RIGHT),
                "SWIPE_UP_DOWN" to ("Rebound Up" to ArrowDirection.SWIPE_UP_DOWN),
                "SWIPE_DOWN_UP" to ("Rebound Down" to ArrowDirection.SWIPE_DOWN_UP),
                "SWIPE_RIGHT_BACK" to ("Rebound Inward" to ArrowDirection.RIGHT_BACK),
                "SWIPE_UP_RIGHT" to ("Two-Step: Up → Inward" to ArrowDirection.SWIPE_UP_RIGHT),
                "SWIPE_DOWN_RIGHT" to ("Two-Step: Down → Inward" to ArrowDirection.SWIPE_DOWN_RIGHT),
                "SWIPE_RIGHT_UP" to ("Two-Step: Inward → Up" to ArrowDirection.RIGHT_UP),
            )
        } else {
            listOf(
                "TAP" to ("Tap" to ArrowDirection.TAP),
                "SWIPE_UP" to ("Swipe Up" to ArrowDirection.SWIPE_UP),
                "SWIPE_DOWN" to ("Swipe Down" to ArrowDirection.SWIPE_DOWN),
                "SWIPE_LEFT" to ("Swipe Inward" to ArrowDirection.SWIPE_LEFT),
                "SWIPE_UP_DOWN" to ("Rebound Up" to ArrowDirection.SWIPE_UP_DOWN),
                "SWIPE_DOWN_UP" to ("Rebound Down" to ArrowDirection.SWIPE_DOWN_UP),
                "SWIPE_LEFT_BACK" to ("Rebound Inward" to ArrowDirection.LEFT_BACK),
                "SWIPE_UP_LEFT" to ("Two-Step: Up → Inward" to ArrowDirection.SWIPE_UP_LEFT),
                "SWIPE_DOWN_LEFT" to ("Two-Step: Down → Inward" to ArrowDirection.SWIPE_DOWN_LEFT),
                "SWIPE_LEFT_UP" to ("Two-Step: Inward → Up" to ArrowDirection.LEFT_UP),
            )
        }
    }

    val defaultOrder = if (isFlankUnified) listOf(centerSecId, unifiedSecId) else listOf(centerSecId, topSecId, bottomSecId)
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
        BlueprintWireframeView(
            tabTitle = tabTitle,
            sectionIds = currentOrder,
            pinnedSectionId = pinnedSection,
            sectionTitles = sectionTitles,
            onMoveUp = { idx: Int ->
                if (idx > 0) {
                    val mutable = currentOrder.toMutableList()
                    val item = mutable.removeAt(idx)
                    mutable.add(idx - 1, item)
                    val newStr = mutable.joinToString(",")
                    sectionOrderStr = newStr
                    prefs.edit().putString(sectionOrderPrefKey, newStr).apply()
                }
            },
            onMoveDown = { idx: Int ->
                if (idx < currentOrder.size - 1) {
                    val mutable = currentOrder.toMutableList()
                    val item = mutable.removeAt(idx)
                    mutable.add(idx + 1, item)
                    val newStr = mutable.joinToString(",")
                    sectionOrderStr = newStr
                    prefs.edit().putString(sectionOrderPrefKey, newStr).apply()
                }
            },
            onPinSection = { secId: String ->
                pinnedSection = secId
                prefs.edit().putString(pinnedAccordionPrefKey, secId).apply()
            },
            onExitBlueprint = {
                blueprintTabTarget = null
            },
            subSections = mapOf(
                topSecId to currentSubTop,
                bottomSecId to currentSubBottom,
                unifiedSecId to currentSubUnified
            ),
            subSectionTitles = mapOf(
                "geo" to "Sensor Geometry",
                "scrub" to "Inward Scrubbing Control",
                "gestures" to "Gesture Actions & Macro Mappings"
            ),
            onMoveSubUp = { secId, idx ->
                if (idx > 0) {
                    val (list, key) = when (secId) {
                        topSecId -> currentSubTop to "pref_sub_order_${topSecId}_2"
                        bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}_2"
                        unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}_2"
                        else -> return@BlueprintWireframeView
                    }
                    val mutable = list.toMutableList()
                    val item = mutable.removeAt(idx)
                    mutable.add(idx - 1, item)
                    when (secId) {
                        topSecId -> currentSubTop = mutable
                        bottomSecId -> currentSubBottom = mutable
                        unifiedSecId -> currentSubUnified = mutable
                    }
                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                }
            },
            onMoveSubDown = { secId, idx ->
                val (list, key) = when (secId) {
                    topSecId -> currentSubTop to "pref_sub_order_${topSecId}_2"
                    bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}_2"
                    unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}_2"
                    else -> return@BlueprintWireframeView
                }
                if (idx < list.size - 1) {
                    val mutable = list.toMutableList()
                    val item = mutable.removeAt(idx)
                    mutable.add(idx + 1, item)
                    when (secId) {
                        topSecId -> currentSubTop = mutable
                        bottomSecId -> currentSubBottom = mutable
                        unifiedSecId -> currentSubUnified = mutable
                    }
                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                }
            }
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
                    val cautionAmber = Color(0xFFFFB300)
                    val noticeContainerColor = if (exceedsEdgeThreshold) Color(0xFF332010).copy(alpha = 0.70f) else Color(0xFF263238).copy(alpha = 0.55f)
                    val noticeBorderColor = if (exceedsEdgeThreshold) cautionAmber.copy(alpha = 0.50f) else Color(0xFF80DEEA).copy(alpha = 0.35f)
                    val noticeAccentColor = if (exceedsEdgeThreshold) cautionAmber else Color(0xFF80DEEA)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = noticeContainerColor,
                        border = androidx.compose.foundation.BorderStroke(1.dp, noticeBorderColor),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (exceedsEdgeThreshold) Icons.Default.Warning else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = noticeAccentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (exceedsEdgeThreshold) {
                                            "ANDROID EDGE GESTURE NOTICE · HIGH SPAN (${(deflectorCoverageRatio * 100).toInt()}%)"
                                        } else {
                                            "ANDROID EDGE GESTURE NOTICE"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = noticeAccentColor
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        dismissedWarningSession = true
                                        prefs.edit().putBoolean("pref_has_seen_edge_sovereignty_tip", true).apply()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = Color.LightGray.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (exceedsEdgeThreshold) {
                                    "Total deflector span along the display edge (${totalDeflectorSpanDp}dp) exceeds 25% of the side (${(screenHeightDp * 0.25f).toInt()}dp recommended threshold). This may overlap or conflict with Android's native back swipe gestures. You can reduce vector heights or neutralize edge back insets in System Overrides."
                                } else {
                                    "Android's native back gestures are active along the screen edges and may conflict with deflector swipes. You can neutralize native back insets anytime under Central Command → HUD Strip → System Overrides."
                                },
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                lineHeight = 16.sp
                            )

                            if (exceedsEdgeThreshold) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = { showOverridesDeck = true },
                                    modifier = Modifier.fillMaxWidth().height(34.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, cautionAmber.copy(alpha = 0.45f)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = cautionAmber.copy(alpha = 0.12f),
                                        contentColor = cautionAmber
                                    ),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        "⚡ OPEN SYSTEM OVERRIDES (EDGE NEUTRALIZATION)",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
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

            item {
                DeflectorLandscapeGeometryCard(
                    context = context,
                    prefs = prefs,
                    onRefreshNeeded = onRefreshNeeded
                )
            }

            currentOrder.forEach { secId ->
                when (secId) {
                    centerSecId -> {
                        item(key = centerSecId) {
                            CompactAccordionSection(
                                title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CORE_ZONE),
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
                                    toggleSection(tabIndex, centerSecId, isCenterExpanded) { isCenterExpanded = it }
                                    prefs.edit()
                                        .putBoolean("pref_section_${centerSecId}_expanded", isCenterExpanded)
                                        .apply()
                                    safeReloadPreferences()
                                    onRefreshNeeded()
                                }
                            ) {
                                DeflectorPillStylingContent(
                                    context = context,
                                    prefs = prefs,
                                    isLeft = isLeft,
                                    onRefreshNeeded = onRefreshNeeded
                                )
                            }
                        }
                    }
                    unifiedSecId -> {
                        if (isFlankUnified) {
                            item(key = unifiedSecId) {
                                DeflectorUnifiedSection(
                                    isLeft = isLeft,
                                    context = context,
                                    prefs = prefs,
                                    dynamicActionTokens = dynamicActionTokens,
                                    tokenLabelCache = tokenLabelCache,
                                    isExpanded = isUnifiedExpanded,
                                    onToggle = {
                                        toggleSection(tabIndex, unifiedSecId, isUnifiedExpanded) { isUnifiedExpanded = it }
                                        prefs.edit()
                                            .putBoolean("pref_section_${unifiedSecId}_expanded", isUnifiedExpanded)
                                            .apply()
                                        safeReloadPreferences()
                                    },
                                    subBlueprintTarget = subBlueprintTarget,
                                    onSubBlueprintToggle = {
                                        subBlueprintTarget = if (subBlueprintTarget == unifiedSecId) null else unifiedSecId
                                    },
                                    isGeoExpanded = isUnifiedGeoExpanded,
                                    onGeoToggle = {
                                        isUnifiedGeoExpanded = !isUnifiedGeoExpanded
                                        prefs.edit().putBoolean("pref_sub_geo_${unifiedSecId}", isUnifiedGeoExpanded).apply()
                                        safeReloadPreferences()
                                        onRefreshNeeded()
                                    },
                                    isScrubExpanded = isUnifiedScrubExpanded,
                                    onScrubToggle = {
                                        isUnifiedScrubExpanded = !isUnifiedScrubExpanded
                                        prefs.edit().putBoolean("pref_sub_scrub_${unifiedSecId}", isUnifiedScrubExpanded).apply()
                                    },
                                    isGesturesExpanded = isUnifiedGesturesExpanded,
                                    onGesturesToggle = {
                                        isUnifiedGesturesExpanded = !isUnifiedGesturesExpanded
                                        prefs.edit().putBoolean("pref_sub_gestures_${unifiedSecId}", isUnifiedGesturesExpanded).apply()
                                    },
                                    customVectors = customVectors,
                                    listState = listState,
                                    coroutineScope = coroutineScope,
                                    onMooringConfirm = { pendingMooringConfirm = it }
                                )
                            }
                        }
                    }
                    topSecId -> {
                        if (!isFlankUnified) {
                            item(key = topSecId) {
                                DeflectorFlankSection(
                                    isLeft = isLeft,
                                    isUpper = true,
                                    context = context,
                                    prefs = prefs,
                                    dynamicActionTokens = dynamicActionTokens,
                                    tokenLabelCache = tokenLabelCache,
                                    isExpanded = isTopExpanded,
                                    onToggle = {
                                        toggleSection(tabIndex, topSecId, isTopExpanded) { isTopExpanded = it }
                                        prefs.edit()
                                            .putBoolean("pref_section_${topSecId}_expanded", isTopExpanded)
                                            .apply()
                                        safeReloadPreferences()
                                    },
                                    subBlueprintTarget = subBlueprintTarget,
                                    onSubBlueprintToggle = {
                                        subBlueprintTarget = if (subBlueprintTarget == topSecId) null else topSecId
                                    },
                                    isGeoExpanded = isTopGeoExpanded,
                                    onGeoToggle = {
                                        isTopGeoExpanded = !isTopGeoExpanded
                                        prefs.edit().putBoolean("pref_sub_geo_${topSecId}", isTopGeoExpanded).apply()
                                        safeReloadPreferences()
                                        onRefreshNeeded()
                                    },
                                    isScrubExpanded = isTopScrubExpanded,
                                    onScrubToggle = {
                                        isTopScrubExpanded = !isTopScrubExpanded
                                        prefs.edit().putBoolean("pref_sub_scrub_${topSecId}", isTopScrubExpanded).apply()
                                    },
                                    isGesturesExpanded = isTopGesturesExpanded,
                                    onGesturesToggle = {
                                        isTopGesturesExpanded = !isTopGesturesExpanded
                                        prefs.edit().putBoolean("pref_sub_gestures_${topSecId}", isTopGesturesExpanded).apply()
                                    },
                                    customVectors = customVectors,
                                    onRefreshNeeded = onRefreshNeeded
                                )
                            }
                        }
                    }
                    bottomSecId -> {
                        if (!isFlankUnified) {
                            item(key = bottomSecId) {
                                DeflectorFlankSection(
                                    isLeft = isLeft,
                                    isUpper = false,
                                    context = context,
                                    prefs = prefs,
                                    dynamicActionTokens = dynamicActionTokens,
                                    tokenLabelCache = tokenLabelCache,
                                    isExpanded = isBottomExpanded,
                                    onToggle = {
                                        toggleSection(tabIndex, bottomSecId, isBottomExpanded) { isBottomExpanded = it }
                                        prefs.edit()
                                            .putBoolean("pref_section_${bottomSecId}_expanded", isBottomExpanded)
                                            .apply()
                                        safeReloadPreferences()
                                    },
                                    subBlueprintTarget = subBlueprintTarget,
                                    onSubBlueprintToggle = {
                                        subBlueprintTarget = if (subBlueprintTarget == bottomSecId) null else bottomSecId
                                    },
                                    isGeoExpanded = isBottomGeoExpanded,
                                    onGeoToggle = {
                                        isBottomGeoExpanded = !isBottomGeoExpanded
                                        prefs.edit().putBoolean("pref_sub_geo_${bottomSecId}", isBottomGeoExpanded).apply()
                                        safeReloadPreferences()
                                        onRefreshNeeded()
                                    },
                                    isScrubExpanded = isBottomScrubExpanded,
                                    onScrubToggle = {
                                        isBottomScrubExpanded = !isBottomScrubExpanded
                                        prefs.edit().putBoolean("pref_sub_scrub_${bottomSecId}", isBottomScrubExpanded).apply()
                                    },
                                    isGesturesExpanded = isBottomGesturesExpanded,
                                    onGesturesToggle = {
                                        isBottomGesturesExpanded = !isBottomGesturesExpanded
                                        prefs.edit().putBoolean("pref_sub_gestures_${bottomSecId}", isBottomGesturesExpanded).apply()
                                    },
                                    customVectors = customVectors,
                                    onRefreshNeeded = onRefreshNeeded
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    MooringConfirmDialog(
        confirmData = pendingMooringConfirm,
        onDismiss = { pendingMooringConfirm = null }
    )

    if (showOverridesDeck) {
        Dialog(
            onDismissRequest = { showOverridesDeck = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val glassVisuals = rememberDeckGlassVisuals(context)
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.96f)
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(glassVisuals.shapeCornerRadius))
                    .background(glassVisuals.backgroundBrush)
                    .border(glassVisuals.borderWidth, glassVisuals.borderBrush, RoundedCornerShape(glassVisuals.shapeCornerRadius)),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(glassVisuals.shapeCornerRadius)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SYSTEM OVERRIDES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White
                        )
                        IconButton(onClick = { showOverridesDeck = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SystemOverrideDeckContents(context, prefs)
                    }
                }
            }
        }
    }
}
