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

@Composable
fun RightDeflectorTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: android.content.Context,
    dynamicActionTokens: List<String>,
    isBottomExpandedState: MutableState<Boolean>,
    isBottomGeoExpandedState: MutableState<Boolean>,
    isBottomGesturesExpandedState: MutableState<Boolean>,
    isBottomScrubExpandedState: MutableState<Boolean>,
    isCenterExpandedState: MutableState<Boolean>,
    isCenterGeoExpandedState: MutableState<Boolean>,
    isLeftFlankUnifiedState: MutableState<Boolean>,
    isRightFlankUnifiedState: MutableState<Boolean>,
    isRightUnifiedExpandedState: MutableState<Boolean>,
    isRightUnifiedGeoExpandedState: MutableState<Boolean>,
    isRightUnifiedGesturesExpandedState: MutableState<Boolean>,
    isRightUnifiedScrubExpandedState: MutableState<Boolean>,
    isTopExpandedState: MutableState<Boolean>,
    isTopGeoExpandedState: MutableState<Boolean>,
    isTopGesturesExpandedState: MutableState<Boolean>,
    isTopScrubExpandedState: MutableState<Boolean>,
    listState2: androidx.compose.foundation.lazy.LazyListState,
    onRefreshNeeded: () -> Unit,
    pinnedSection2State: MutableState<String>,
    prefs: android.content.SharedPreferences,
    sectionOrder2StrState: MutableState<String>,
    sectionTitles2: Map<String, String>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForRightState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    var blueprintTabTarget by blueprintTabTargetState
    var isBottomExpanded by isBottomExpandedState
    var isBottomGeoExpanded by isBottomGeoExpandedState
    var isBottomGesturesExpanded by isBottomGesturesExpandedState
    var isBottomScrubExpanded by isBottomScrubExpandedState
    var isCenterExpanded by isCenterExpandedState
    var isCenterGeoExpanded by isCenterGeoExpandedState
    var isLeftFlankUnified by isLeftFlankUnifiedState
    var isRightFlankUnified by isRightFlankUnifiedState
    var isRightUnifiedExpanded by isRightUnifiedExpandedState
    var isRightUnifiedGeoExpanded by isRightUnifiedGeoExpandedState
    var isRightUnifiedGesturesExpanded by isRightUnifiedGesturesExpandedState
    var isRightUnifiedScrubExpanded by isRightUnifiedScrubExpandedState
    var isRightScrubLinked by remember { mutableStateOf(com.sbf.lightspeed.system.LightspeedPreferences.isScrubRegionsLinked(context, isLeft = false)) }
    val coroutineScope = rememberCoroutineScope()
    var isTopExpanded by isTopExpandedState
    var isTopGeoExpanded by isTopGeoExpandedState
    var isTopGesturesExpanded by isTopGesturesExpandedState
    var isTopScrubExpanded by isTopScrubExpandedState
    var pinnedSection2 by pinnedSection2State
    var sectionOrder2Str by sectionOrder2StrState
    var showUnifyInfoDialog by showUnifyInfoDialogState
    var showUnifyTemplateDialogForRight by showUnifyTemplateDialogForRightState
    var subBlueprintTarget by remember { mutableStateOf<String?>(null) }
    val rightCustomVectors = listOf(
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
    var rightGestureTiedState by remember {
        mutableStateOf(
            rightCustomVectors.flatMap {
                listOf(
                    it.first to com.sbf.lightspeed.system.LightspeedPreferences.isGestureUnified(context, isLeft = false, it.first),
                    "${it.first}_HOLD" to com.sbf.lightspeed.system.LightspeedPreferences.isGestureUnified(context, isLeft = false, "${it.first}_HOLD")
                )
            }.toMap()
        )
    }
                    val defaultOrder2 = if (isRightFlankUnified) listOf("center", "unified") else listOf("center", "top", "bottom")
                    val currentOrder2 = sectionOrder2Str.split(",").map { it.trim() }.filter { it in defaultOrder2 }.distinct().let { list ->
                        list + (defaultOrder2 - list.toSet())
                    }


                    val defaultSubTop = listOf("geo", "scrub", "gestures")
                    val subTopStr = prefs.getString("pref_sub_order_top_2", "geo,scrub,gestures")!!
                    var currentSubTop by remember { mutableStateOf(subTopStr.split(",").filter { it in defaultSubTop }.let { it + (defaultSubTop - it.toSet()) }) }
                    

                    val defaultSubBottom = listOf("geo", "scrub", "gestures")
                    val subBottomStr = prefs.getString("pref_sub_order_bottom_2", "geo,scrub,gestures")!!
                    var currentSubBottom by remember { mutableStateOf(subBottomStr.split(",").filter { it in defaultSubBottom }.let { it + (defaultSubBottom - it.toSet()) }) }
                    

                    val defaultSubUnified = listOf("geo", "scrub", "gestures")
                    val subUnifiedStr = prefs.getString("pref_sub_order_unified_2", "geo,scrub,gestures")!!
                    var currentSubUnified by remember { mutableStateOf(subUnifiedStr.split(",").filter { it in defaultSubUnified }.let { it + (defaultSubUnified - it.toSet()) }) }
                    

                    if (blueprintTabTarget == 2) {
                        BlueprintWireframeView(
                            tabTitle = "Deflectors ▶",
                            sectionIds = currentOrder2,
                            pinnedSectionId = pinnedSection2,
                            sectionTitles = sectionTitles2,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder2.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder2Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder2.size - 1) {
                                    val mutable = currentOrder2.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder2Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_2, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection2 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_2, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            },
                            subSections = mapOf(
                                "top" to currentSubTop,
                                "bottom" to currentSubBottom,
                                "unified" to currentSubUnified
                            ),
                            subSectionTitles = mapOf(
                                "geo" to "Sensor Geometry",
                                "scrub" to "Inward Scrubbing Control",
                                "gestures" to "Gesture Actions & Macro Mappings"
                            ),

                            onMoveSubUp = { secId, idx ->
                                if (idx > 0) {
                                    val (list, key) = when (secId) {
                                        "top" -> currentSubTop to "pref_sub_order_top_2"
                                        "bottom" -> currentSubBottom to "pref_sub_order_bottom_2"
                                        "unified" -> currentSubUnified to "pref_sub_order_unified_2"
                                        else -> return@BlueprintWireframeView
                                    }
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    when (secId) {
                                        "top" -> currentSubTop = mutable
                                        "bottom" -> currentSubBottom = mutable
                                        "unified" -> currentSubUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },
                            onMoveSubDown = { secId, idx ->
                                val (list, key) = when (secId) {
                                    "top" -> currentSubTop to "pref_sub_order_top_2"
                                    "bottom" -> currentSubBottom to "pref_sub_order_bottom_2"
                                    "unified" -> currentSubUnified to "pref_sub_order_unified_2"
                                    else -> return@BlueprintWireframeView
                                }
                                if (idx < list.size - 1) {
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    when (secId) {
                                        "top" -> currentSubTop = mutable
                                        "bottom" -> currentSubBottom = mutable
                                        "unified" -> currentSubUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },

                        )
                    } else {
                        LazyColumn(
                            state = listState2,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            item(key = "right_deflector_master") {
                                var isRightEnabled by remember { mutableStateOf(LightspeedPreferences.isRightDeflectorEnabled(context)) }
                                var rightStartupMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, "always_armed") ?: "always_armed") }

                                DeflectorMasterCard(
                                    flankName = "Right Deflector",
                                    isEnabled = isRightEnabled,
                                    onToggle = { enabled ->
                                        isRightEnabled = enabled
                                        LightspeedPreferences.setRightDeflectorEnabled(context, enabled)
                                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
                                    },
                                    startupMode = rightStartupMode,
                                    onStartupModeChange = { mode ->
                                        rightStartupMode = mode
                                        prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, mode).apply()
                                    }
                                )
                            }

                            item {
                                UnifyFlankActionsCard(
                                    isUnified = isRightFlankUnified,
                                    onToggle = { enable ->
                                        if (enable) {
                                            showUnifyTemplateDialogForRight = true
                                        } else {
                                            isRightFlankUnified = false
                                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", false).apply()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    },
                                    onInfoClick = { showUnifyInfoDialog = true }
                                )
                            }

                            currentOrder2.forEach { secId ->
                                when (secId) {
                                    "center" -> {
                                        item(key = "center") {
                                             CompactAccordionSection(
                                                title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.CORE_ZONE),
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
                                                    toggleSection(2, "center", isCenterExpanded) { isCenterExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_center_expanded", isCenterExpanded)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    onRefreshNeeded()
                                                }
                                            ) {
                                                DeflectorPillStylingContent(
                                                    context = context,
                                                    prefs = prefs,
                                                    isLeft = false,
                                                    onRefreshNeeded = onRefreshNeeded
                                                )
                                            }
                                        }
                                    }
                                    "unified" -> {
                                        if (isRightFlankUnified) {
                                            item(key = "unified") {
                                                CompactAccordionSection(
                                                    title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.UNIFIED_DEFLECTORS),
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.SwapVert,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isRightUnifiedExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "unified", isRightUnifiedExpanded) { isRightUnifiedExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_right_unified_expanded", isRightUnifiedExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    },
                                                    onLongToggle = { subBlueprintTarget = if (subBlueprintTarget == "unified") null else "unified" }
                                                ) {
                                                    var unifiedSubOrderStr by remember { mutableStateOf(prefs.getString("pref_sub_order_unified", "geo,scrub,gestures")!!) }
                                                    var unifiedPinnedSection by remember { mutableStateOf(prefs.getString("pref_sub_pinned_unified", "geo")) }
                                                    val defaultUnifiedSubOrder = listOf("geo", "scrub", "gestures")
                                                    val currentUnifiedSubOrder = unifiedSubOrderStr.split(",").map { it.trim() }.filter { it in defaultUnifiedSubOrder }.distinct().let { list ->
                                                        list + (defaultUnifiedSubOrder - list.toSet())
                                                    }
                                                    if (subBlueprintTarget == "unified") {
                                                        BlueprintWireframeView(
                                                            tabTitle = "Flank Vector Zones",
                                                            sectionIds = currentUnifiedSubOrder,
                                                            pinnedSectionId = unifiedPinnedSection,
                                                            sectionTitles = mapOf("geo" to "Sensor Geometry", "scrub" to "Separate Deflector Controls", "gestures" to "Unified Gesture Matrix"),
                                                            onMoveUp = { idx ->
                                                                if (idx > 0) {
                                                                    val mutable = currentUnifiedSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx - 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    unifiedSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_unified", newStr).apply()
                                                                }
                                                            },
                                                            onMoveDown = { idx ->
                                                                if (idx < currentUnifiedSubOrder.size - 1) {
                                                                    val mutable = currentUnifiedSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx + 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    unifiedSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_unified", newStr).apply()
                                                                }
                                                            },
                                                            onPinSection = { id -> 
                                                                unifiedPinnedSection = id
                                                                prefs.edit().putString("pref_sub_pinned_unified", id).apply() 
                                                            },
                                                            onExitBlueprint = { subBlueprintTarget = null }
                                                        )
                                                    } else {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            currentUnifiedSubOrder.forEach { subKey ->
                                                                when (subKey) {
                                                                    "geo" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Sensor Geometry",
                                                                            subtitle = "Independent height, touch reach & stealth glow",
                                                                            isExpanded = isRightUnifiedGeoExpanded,
                                                                            onToggle = {
                                                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                                                prefs.edit()
                                                                                    .putBoolean("pref_sub_geo_unified", isRightUnifiedGeoExpanded)
                                                                                    .apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        ) {
                                                                            Text("UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Upper Deflector Span (Height)", 50, 600, 10, 200)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                                                            Spacer(modifier = Modifier.height(4.dp))
                                                                            Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Lower Deflector Span (Height)", 50, 600, 10, 200)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                                                        }
                                                                    }
                                                                    "scrub" -> {
                                                                        val sepCount = (if (!isRightScrubLinked) 1 else 0) + rightGestureTiedState.values.count { !it }
                                                                        CollapsibleSubSection(
                                                                            title = "Separate Deflector Controls",
                                                                            subtitle = if (sepCount == 0) "All gestures tied in Unified Matrix" else "$sepCount unlinked dual control vector(s)",
                                                                            isExpanded = isRightUnifiedScrubExpanded,
                                                                            onToggle = {
                                                                                isRightUnifiedScrubExpanded = !isRightUnifiedScrubExpanded
                                                                                prefs.edit().putBoolean("pref_sub_scrub_unified", isRightUnifiedScrubExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            if (sepCount == 0) {
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(10.dp),
                                                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                                                ) {
                                                                                    Text(
                                                                                        text = "All deflector gestures are currently tied in the Unified Gesture Matrix. Tap the mooring rope on any gesture to unlink it into independent Upper & Lower controls.",
                                                                                        fontSize = 12.sp,
                                                                                        color = Color.LightGray.copy(alpha = 0.8f),
                                                                                        modifier = Modifier.padding(12.dp)
                                                                                    )
                                                                                }
                                                                            } else {
                                                                                // 1. Scrubber (if unlinked/cut)
                                                                                if (!isRightScrubLinked) {
                                                                                    GestureMappingRow(
                                                                                        context = context,
                                                                                        prefs = prefs,
                                                                                        direction = ArrowDirection.SCRUB,
                                                                                        isHold = false,
                                                                                        keyResName = "pref_macro_action_TOP_SCRUBBING",
                                                                                        defaultTitle = "Upper Sector · Inward Sweep",
                                                                                        badgeText = "UPPER",
                                                                                        options = listOf("none", "system:brightness", "system:volume", "system:screen_timeout"),
                                                                                        labelCache = tokenLabelCache,
                                                                                        showMooringRope = true,
                                                                                        isMooringTied = false,
                                                                                        onToggleMooring = { _ ->
                                                                                            isRightScrubLinked = true
                                                                                            com.sbf.lightspeed.system.LightspeedPreferences.setScrubRegionsLinked(context, isLeft = false, true)
                                                                                            val topVal = prefs.getString("pref_macro_action_TOP_SCRUBBING", "system:brightness") ?: "system:brightness"
                                                                                            prefs.edit().putString("pref_macro_action_BOTTOM_SCRUBBING", topVal).apply()
                                                                                            if (!isRightUnifiedGesturesExpanded) {
                                                                                                isRightUnifiedGesturesExpanded = true
                                                                                                prefs.edit().putBoolean("pref_sub_gestures_unified", true).apply()
                                                                                            }
                                                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                            coroutineScope.launch {
                                                                                                kotlinx.coroutines.delay(60)
                                                                                                try { listState2.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                                                            }
                                                                                        }
                                                                                    )
                                                                                    Spacer(modifier = Modifier.height(4.dp))
                                                                                    GestureMappingRow(
                                                                                        context = context,
                                                                                        prefs = prefs,
                                                                                        direction = ArrowDirection.SCRUB,
                                                                                        isHold = false,
                                                                                        keyResName = "pref_macro_action_BOTTOM_SCRUBBING",
                                                                                        defaultTitle = "Lower Sector · Inward Sweep",
                                                                                        badgeText = "LOWER",
                                                                                        options = listOf("none", "system:volume", "system:brightness", "system:screen_timeout"),
                                                                                        labelCache = tokenLabelCache
                                                                                    )
                                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                                }

                                                                                // 2. Custom vectors (only unlinked/cut)
                                                                                rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                                    val (vectorTitle, arrowEnum) = pairInfo
                                                                                    val isStandardTied = rightGestureTiedState[vectorKey] ?: true
                                                                                    val isHoldTied = rightGestureTiedState["${vectorKey}_HOLD"] ?: true

                                                                                    if (!isStandardTied) {
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = false,
                                                                                            keyResName = "pref_macro_action_TOP_${vectorKey}",
                                                                                            defaultTitle = "Upper Sector · $vectorTitle",
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            badgeText = "UPPER",
                                                                                            showMooringRope = true,
                                                                                            isMooringTied = false,
                                                                                            onToggleMooring = { _ ->
                                                                                                rightGestureTiedState = rightGestureTiedState + (vectorKey to true)
                                                                                                com.sbf.lightspeed.system.LightspeedPreferences.setGestureUnified(context, isLeft = false, vectorKey, true)
                                                                                                val topVal = prefs.getString("pref_macro_action_TOP_${vectorKey}", "none") ?: "none"
                                                                                                prefs.edit()
                                                                                                    .putString("pref_macro_action_UNIFIED_${vectorKey}", topVal)
                                                                                                    .putString("pref_macro_action_BOTTOM_${vectorKey}", topVal)
                                                                                                    .apply()
                                                                                                if (!isRightUnifiedGesturesExpanded) {
                                                                                                    isRightUnifiedGesturesExpanded = true
                                                                                                    prefs.edit().putBoolean("pref_sub_gestures_unified", true).apply()
                                                                                                }
                                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                                coroutineScope.launch {
                                                                                                    kotlinx.coroutines.delay(60)
                                                                                                    try { listState2.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                                                                }
                                                                                            }
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = false,
                                                                                            keyResName = "pref_macro_action_BOTTOM_${vectorKey}",
                                                                                            defaultTitle = "Lower Sector · $vectorTitle",
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            badgeText = "LOWER"
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(8.dp))
                                                                                    }

                                                                                    if (!isHoldTied) {
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = true,
                                                                                            keyResName = "pref_macro_action_TOP_${vectorKey}_HOLD",
                                                                                            defaultTitle = "Upper Sector · $vectorTitle + Hold",
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            badgeText = "UPPER",
                                                                                            showMooringRope = true,
                                                                                            isMooringTied = false,
                                                                                            onToggleMooring = { _ ->
                                                                                                val holdKey = "${vectorKey}_HOLD"
                                                                                                rightGestureTiedState = rightGestureTiedState + (holdKey to true)
                                                                                                com.sbf.lightspeed.system.LightspeedPreferences.setGestureUnified(context, isLeft = false, holdKey, true)
                                                                                                val topVal = prefs.getString("pref_macro_action_TOP_${vectorKey}_HOLD", "none") ?: "none"
                                                                                                prefs.edit()
                                                                                                    .putString("pref_macro_action_UNIFIED_${vectorKey}_HOLD", topVal)
                                                                                                    .putString("pref_macro_action_BOTTOM_${vectorKey}_HOLD", topVal)
                                                                                                    .apply()
                                                                                                if (!isRightUnifiedGesturesExpanded) {
                                                                                                    isRightUnifiedGesturesExpanded = true
                                                                                                    prefs.edit().putBoolean("pref_sub_gestures_unified", true).apply()
                                                                                                }
                                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                                coroutineScope.launch {
                                                                                                    kotlinx.coroutines.delay(60)
                                                                                                    try { listState2.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                                                                }
                                                                                            }
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(4.dp))
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = true,
                                                                                            keyResName = "pref_macro_action_BOTTOM_${vectorKey}_HOLD",
                                                                                            defaultTitle = "Lower Sector · $vectorTitle + Hold",
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            badgeText = "LOWER"
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(8.dp))
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                    "gestures" -> {
                                                                        val unifiedCount = (if (isRightScrubLinked) 1 else 0) + rightGestureTiedState.values.count { it }
                                                                        CollapsibleSubSection(
                                                                            title = "Unified Gesture Matrix",
                                                                            subtitle = if (unifiedCount == 0) "All gestures unlinked into Separate Controls" else "$unifiedCount unified full-flank vector(s)",
                                                                            isExpanded = isRightUnifiedGesturesExpanded,
                                                                            onToggle = {
                                                                                isRightUnifiedGesturesExpanded = !isRightUnifiedGesturesExpanded
                                                                                prefs.edit().putBoolean("pref_sub_gestures_unified", isRightUnifiedGesturesExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            if (unifiedCount == 0) {
                                                                                Surface(
                                                                                    shape = RoundedCornerShape(10.dp),
                                                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                                                                ) {
                                                                                    Text(
                                                                                        text = "All deflector gestures have been unlinked into Separate Controls. Tap the mooring rope on any separate gesture to tie it back into the Unified Matrix.",
                                                                                        fontSize = 12.sp,
                                                                                        color = Color.LightGray.copy(alpha = 0.8f),
                                                                                        modifier = Modifier.padding(12.dp)
                                                                                    )
                                                                                }
                                                                            } else {
                                                                                // 1. Scrubber (if tied)
                                                                                if (isRightScrubLinked) {
                                                                                    GestureMappingRow(
                                                                                        context = context,
                                                                                        prefs = prefs,
                                                                                        direction = ArrowDirection.SCRUB,
                                                                                        isHold = false,
                                                                                        keyResName = "pref_macro_action_TOP_SCRUBBING",
                                                                                        defaultTitle = "Linked Inward Sweep (Scrubbing)",
                                                                                        options = listOf("none", "system:brightness", "system:volume", "system:screen_timeout"),
                                                                                        labelCache = tokenLabelCache,
                                                                                        showMooringRope = true,
                                                                                        isMooringTied = true,
                                                                                        onToggleMooring = { _ ->
                                                                                            isRightScrubLinked = false
                                                                                            com.sbf.lightspeed.system.LightspeedPreferences.setScrubRegionsLinked(context, isLeft = false, false)
                                                                                            if (!isRightUnifiedScrubExpanded) {
                                                                                                isRightUnifiedScrubExpanded = true
                                                                                                prefs.edit().putBoolean("pref_sub_scrub_unified", true).apply()
                                                                                            }
                                                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                            coroutineScope.launch {
                                                                                                kotlinx.coroutines.delay(60)
                                                                                                try { listState2.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                                                            }
                                                                                        }
                                                                                    )
                                                                                    LaunchedEffect(prefs.getString("pref_macro_action_TOP_SCRUBBING", null)) {
                                                                                        val currentTop = prefs.getString("pref_macro_action_TOP_SCRUBBING", null)
                                                                                        if (currentTop != null && currentTop != prefs.getString("pref_macro_action_BOTTOM_SCRUBBING", null)) {
                                                                                            prefs.edit().putString("pref_macro_action_BOTTOM_SCRUBBING", currentTop).apply()
                                                                                        }
                                                                                    }
                                                                                    Spacer(modifier = Modifier.height(8.dp))
                                                                                }

                                                                                // 2. Custom vectors (only tied)
                                                                                rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                                    val (vectorTitle, arrowEnum) = pairInfo
                                                                                    val isStandardTied = rightGestureTiedState[vectorKey] ?: true
                                                                                    val isHoldTied = rightGestureTiedState["${vectorKey}_HOLD"] ?: true

                                                                                    if (isStandardTied) {
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = false,
                                                                                            keyResName = "pref_macro_action_UNIFIED_${vectorKey}",
                                                                                            defaultTitle = vectorTitle,
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            showMooringRope = true,
                                                                                            isMooringTied = true,
                                                                                            onToggleMooring = { _ ->
                                                                                                rightGestureTiedState = rightGestureTiedState + (vectorKey to false)
                                                                                                com.sbf.lightspeed.system.LightspeedPreferences.setGestureUnified(context, isLeft = false, vectorKey, false)
                                                                                                val currentVal = prefs.getString("pref_macro_action_UNIFIED_${vectorKey}", "none") ?: "none"
                                                                                                prefs.edit()
                                                                                                    .putString("pref_macro_action_TOP_${vectorKey}", currentVal)
                                                                                                    .putString("pref_macro_action_BOTTOM_${vectorKey}", currentVal)
                                                                                                    .apply()
                                                                                                if (!isRightUnifiedScrubExpanded) {
                                                                                                    isRightUnifiedScrubExpanded = true
                                                                                                    prefs.edit().putBoolean("pref_sub_scrub_unified", true).apply()
                                                                                                }
                                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                                coroutineScope.launch {
                                                                                                    kotlinx.coroutines.delay(60)
                                                                                                    try { listState2.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                                                                }
                                                                                            }
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(8.dp))
                                                                                    }

                                                                                    if (isHoldTied) {
                                                                                        GestureMappingRow(
                                                                                            context = context,
                                                                                            prefs = prefs,
                                                                                            direction = arrowEnum,
                                                                                            isHold = true,
                                                                                            keyResName = "pref_macro_action_UNIFIED_${vectorKey}_HOLD",
                                                                                            defaultTitle = "$vectorTitle + Hold Modifier",
                                                                                            options = dynamicActionTokens,
                                                                                            labelCache = tokenLabelCache,
                                                                                            showMooringRope = true,
                                                                                            isMooringTied = true,
                                                                                            onToggleMooring = { _ ->
                                                                                                val holdKey = "${vectorKey}_HOLD"
                                                                                                rightGestureTiedState = rightGestureTiedState + (holdKey to false)
                                                                                                com.sbf.lightspeed.system.LightspeedPreferences.setGestureUnified(context, isLeft = false, holdKey, false)
                                                                                                val currentVal = prefs.getString("pref_macro_action_UNIFIED_${vectorKey}_HOLD", "none") ?: "none"
                                                                                                prefs.edit()
                                                                                                    .putString("pref_macro_action_TOP_${vectorKey}_HOLD", currentVal)
                                                                                                    .putString("pref_macro_action_BOTTOM_${vectorKey}_HOLD", currentVal)
                                                                                                    .apply()
                                                                                                if (!isRightUnifiedScrubExpanded) {
                                                                                                    isRightUnifiedScrubExpanded = true
                                                                                                    prefs.edit().putBoolean("pref_sub_scrub_unified", true).apply()
                                                                                                }
                                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                                coroutineScope.launch {
                                                                                                    kotlinx.coroutines.delay(60)
                                                                                                    try { listState2.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                                                                }
                                                                                            }
                                                                                        )
                                                                                        Spacer(modifier = Modifier.height(8.dp))
                                                                                    }
                                                                                }
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "top" -> {
                                        if (!isRightFlankUnified) {
                                            item(key = "top") {
                                                CompactAccordionSection(
                                                    title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.UPPER_FLANK),
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isTopExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "top", isTopExpanded) { isTopExpanded = it }
                                                        val newTop = isTopExpanded
                                                        prefs.edit()
                                                            .putBoolean("pref_section_top_expanded", newTop)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    },
                                                    onLongToggle = { subBlueprintTarget = if (subBlueprintTarget == "top") null else "top" }
                                                ) {
                                                    var topSubOrderStr by remember { mutableStateOf(prefs.getString("pref_sub_order_top", "geo,scrub,gestures")!!) }
                                                    var topPinnedSection by remember { mutableStateOf(prefs.getString("pref_sub_pinned_top", "geo")) }
                                                    val defaultTopSubOrder = listOf("geo", "scrub", "gestures")
                                                    val currentTopSubOrder = topSubOrderStr.split(",").map { it.trim() }.filter { it in defaultTopSubOrder }.distinct().let { list ->
                                                        list + (defaultTopSubOrder - list.toSet())
                                                    }
                                                    if (subBlueprintTarget == "top") {
                                                        BlueprintWireframeView(
                                                            tabTitle = "Upper Deflector Zone",
                                                            sectionIds = currentTopSubOrder,
                                                            pinnedSectionId = topPinnedSection,
                                                            sectionTitles = mapOf("geo" to "Sensor Geometry", "scrub" to "Inward Scrubbing Control", "gestures" to "Gesture Actions & Macro Mappings"),
                                                            onMoveUp = { idx ->
                                                                if (idx > 0) {
                                                                    val mutable = currentTopSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx - 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    topSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_top", newStr).apply()
                                                                }
                                                            },
                                                            onMoveDown = { idx ->
                                                                if (idx < currentTopSubOrder.size - 1) {
                                                                    val mutable = currentTopSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx + 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    topSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_top", newStr).apply()
                                                                }
                                                            },
                                                            onPinSection = { id -> 
                                                                topPinnedSection = id
                                                                prefs.edit().putString("pref_sub_pinned_top", id).apply() 
                                                            },
                                                            onExitBlueprint = { subBlueprintTarget = null }
                                                        )
                                                    } else {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            currentTopSubOrder.forEach { subKey ->
                                                                when (subKey) {
                                                                    "geo" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Sensor Geometry",
                                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                                            isExpanded = isTopGeoExpanded,
                                                                            onToggle = {
                                                                                isTopGeoExpanded = !isTopGeoExpanded
                                                                                prefs.edit()
                                                                                    .putBoolean("pref_sub_geo_top", isTopGeoExpanded)
                                                                                    .apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        ) {
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                                        }
                                                                    }
                                                                    "scrub" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Inward Scrubbing Control",
                                                                            subtitle = "Upper vector inward sweep scrubber",
                                                                            isExpanded = isTopScrubExpanded,
                                                                            onToggle = {
                                                                                isTopScrubExpanded = !isTopScrubExpanded
                                                                                prefs.edit().putBoolean("pref_sub_scrub_top", isTopScrubExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                                        }
                                                                    }
                                                                    "gestures" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Gesture Actions & Macro Mappings",
                                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                                            isExpanded = isTopGesturesExpanded,
                                                                            onToggle = {
                                                                                isTopGesturesExpanded = !isTopGesturesExpanded
                                                                                prefs.edit().putBoolean("pref_sub_gestures_top", isTopGesturesExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_TOP_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_TOP_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "bottom" -> {
                                        if (!isRightFlankUnified) {
                                            item(key = "bottom") {
                                                CompactAccordionSection(
                                                    title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.LOWER_FLANK),
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isBottomExpanded,
                                                    onToggle = {
                                                        toggleSection(2, "bottom", isBottomExpanded) { isBottomExpanded = it }
                                                        val newBottom = isBottomExpanded
                                                        prefs.edit()
                                                            .putBoolean("pref_section_bottom_expanded", newBottom)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    },
                                                    onLongToggle = { subBlueprintTarget = if (subBlueprintTarget == "bottom") null else "bottom" }
                                                ) {
                                                    var bottomSubOrderStr by remember { mutableStateOf(prefs.getString("pref_sub_order_bottom", "geo,scrub,gestures")!!) }
                                                    var bottomPinnedSection by remember { mutableStateOf(prefs.getString("pref_sub_pinned_bottom", "geo")) }
                                                    val defaultBottomSubOrder = listOf("geo", "scrub", "gestures")
                                                    val currentBottomSubOrder = bottomSubOrderStr.split(",").map { it.trim() }.filter { it in defaultBottomSubOrder }.distinct().let { list ->
                                                        list + (defaultBottomSubOrder - list.toSet())
                                                    }
                                                    if (subBlueprintTarget == "bottom") {
                                                        BlueprintWireframeView(
                                                            tabTitle = "Lower Deflector Zone",
                                                            sectionIds = currentBottomSubOrder,
                                                            pinnedSectionId = bottomPinnedSection,
                                                            sectionTitles = mapOf("geo" to "Sensor Geometry", "scrub" to "Inward Scrubbing Control", "gestures" to "Gesture Actions & Macro Mappings"),
                                                            onMoveUp = { idx ->
                                                                if (idx > 0) {
                                                                    val mutable = currentBottomSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx - 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    bottomSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_bottom", newStr).apply()
                                                                }
                                                            },
                                                            onMoveDown = { idx ->
                                                                if (idx < currentBottomSubOrder.size - 1) {
                                                                    val mutable = currentBottomSubOrder.toMutableList()
                                                                    val item = mutable.removeAt(idx)
                                                                    mutable.add(idx + 1, item)
                                                                    val newStr = mutable.joinToString(",")
                                                                    bottomSubOrderStr = newStr
                                                                    prefs.edit().putString("pref_sub_order_bottom", newStr).apply()
                                                                }
                                                            },
                                                            onPinSection = { id -> 
                                                                bottomPinnedSection = id
                                                                prefs.edit().putString("pref_sub_pinned_bottom", id).apply() 
                                                            },
                                                            onExitBlueprint = { subBlueprintTarget = null }
                                                        )
                                                    } else {
                                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                            currentBottomSubOrder.forEach { subKey ->
                                                                when (subKey) {
                                                                    "geo" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Sensor Geometry",
                                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                                            isExpanded = isBottomGeoExpanded,
                                                                            onToggle = {
                                                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                                                prefs.edit()
                                                                                    .putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded)
                                                                                    .apply()
                                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                                onRefreshNeeded()
                                                                            }
                                                                        ) {
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                                        }
                                                                    }
                                                                    "scrub" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Inward Scrubbing Control",
                                                                            subtitle = "Lower vector inward sweep scrubber",
                                                                            isExpanded = isBottomScrubExpanded,
                                                                            onToggle = {
                                                                                isBottomScrubExpanded = !isBottomScrubExpanded
                                                                                prefs.edit().putBoolean("pref_sub_scrub_bottom", isBottomScrubExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                                        }
                                                                    }
                                                                    "gestures" -> {
                                                                        CollapsibleSubSection(
                                                                            title = "Gesture Actions & Macro Mappings",
                                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                                            isExpanded = isBottomGesturesExpanded,
                                                                            onToggle = {
                                                                                isBottomGesturesExpanded = !isBottomGesturesExpanded
                                                                                prefs.edit().putBoolean("pref_sub_gestures_bottom", isBottomGesturesExpanded).apply()
                                                                            }
                                                                        ) {
                                                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_BOTTOM_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_BOTTOM_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                                                            }
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }