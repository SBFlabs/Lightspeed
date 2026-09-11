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
fun LeftDeflectorTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: android.content.Context,
    dynamicActionTokens: List<String>,
    isLeftBottomExpandedState: MutableState<Boolean>,
    isLeftBottomGeoExpandedState: MutableState<Boolean>,
    isLeftBottomGesturesExpandedState: MutableState<Boolean>,
    isLeftBottomScrubExpandedState: MutableState<Boolean>,
    isLeftCenterExpandedState: MutableState<Boolean>,
    isLeftCenterGeoExpandedState: MutableState<Boolean>,
    isLeftFlankUnifiedState: MutableState<Boolean>,
    isLeftTopExpandedState: MutableState<Boolean>,
    isLeftTopGeoExpandedState: MutableState<Boolean>,
    isLeftTopGesturesExpandedState: MutableState<Boolean>,
    isLeftTopScrubExpandedState: MutableState<Boolean>,
    isLeftUnifiedExpandedState: MutableState<Boolean>,
    isLeftUnifiedGeoExpandedState: MutableState<Boolean>,
    isLeftUnifiedGesturesExpandedState: MutableState<Boolean>,
    isLeftUnifiedScrubExpandedState: MutableState<Boolean>,
    listState0: androidx.compose.foundation.lazy.LazyListState,
    onRefreshNeeded: () -> Unit,
    pinnedSection0State: MutableState<String>,
    prefs: android.content.SharedPreferences,
    sectionOrder0StrState: MutableState<String>,
    sectionTitles0: Map<String, String>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForLeftState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    var blueprintTabTarget by blueprintTabTargetState
    var isLeftBottomExpanded by isLeftBottomExpandedState
    var isLeftBottomGeoExpanded by isLeftBottomGeoExpandedState
    var isLeftBottomGesturesExpanded by isLeftBottomGesturesExpandedState
    var isLeftBottomScrubExpanded by isLeftBottomScrubExpandedState
    var isLeftCenterExpanded by isLeftCenterExpandedState
    var isLeftCenterGeoExpanded by isLeftCenterGeoExpandedState
    var isLeftFlankUnified by isLeftFlankUnifiedState
    var isLeftTopExpanded by isLeftTopExpandedState
    var isLeftTopGeoExpanded by isLeftTopGeoExpandedState
    var isLeftTopGesturesExpanded by isLeftTopGesturesExpandedState
    var isLeftTopScrubExpanded by isLeftTopScrubExpandedState
    var isLeftUnifiedExpanded by isLeftUnifiedExpandedState
    var isLeftUnifiedGeoExpanded by isLeftUnifiedGeoExpandedState
    var isLeftUnifiedGesturesExpanded by isLeftUnifiedGesturesExpandedState
    var isLeftUnifiedScrubExpanded by isLeftUnifiedScrubExpandedState
    var pinnedSection0 by pinnedSection0State
    var sectionOrder0Str by sectionOrder0StrState
    var showUnifyInfoDialog by showUnifyInfoDialogState
    var showUnifyTemplateDialogForLeft by showUnifyTemplateDialogForLeftState
    var subBlueprintTarget by remember { mutableStateOf<String?>(null) }
    val leftCustomVectors = listOf(
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
        "SWIPE_RIGHT_DOWN" to ("Two-Step: Inward → Down" to ArrowDirection.RIGHT_DOWN)
    )
                    val defaultOrder0 = if (isLeftFlankUnified) listOf("left_center", "left_unified") else listOf("left_center", "left_top", "left_bottom")
                    val currentOrder0 = sectionOrder0Str.split(",").map { it.trim() }.filter { it in defaultOrder0 }.distinct().let { list ->
                        list + (defaultOrder0 - list.toSet())
                    }


                    val defaultSubLeftTop = listOf("geo", "scrub", "gestures")
                    val subLeftTopStr = prefs.getString("pref_sub_order_left_top_2", "geo,scrub,gestures")!!
                    var currentSubLeftTop by remember { mutableStateOf(subLeftTopStr.split(",").filter { it in defaultSubLeftTop }.let { it + (defaultSubLeftTop - it.toSet()) }) }
                    

                    val defaultSubLeftBottom = listOf("geo", "scrub", "gestures")
                    val subLeftBottomStr = prefs.getString("pref_sub_order_left_bottom_2", "geo,scrub,gestures")!!
                    var currentSubLeftBottom by remember { mutableStateOf(subLeftBottomStr.split(",").filter { it in defaultSubLeftBottom }.let { it + (defaultSubLeftBottom - it.toSet()) }) }
                    

                    val defaultSubLeftUnified = listOf("geo", "scrub", "gestures")
                    val subLeftUnifiedStr = prefs.getString("pref_sub_order_left_unified_2", "geo,scrub,gestures")!!
                    var currentSubLeftUnified by remember { mutableStateOf(subLeftUnifiedStr.split(",").filter { it in defaultSubLeftUnified }.let { it + (defaultSubLeftUnified - it.toSet()) }) }
                    

                    if (blueprintTabTarget == 0) {
                        BlueprintWireframeView(
                            tabTitle = "◀ Deflectors",
                            sectionIds = currentOrder0,
                            pinnedSectionId = pinnedSection0,
                            sectionTitles = sectionTitles0,
                            onMoveUp = { idx: Int ->
                                if (idx > 0) {
                                    val mutable = currentOrder0.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder0Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, newStr).apply()
                                }
                            },
                            onMoveDown = { idx: Int ->
                                if (idx < currentOrder0.size - 1) {
                                    val mutable = currentOrder0.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    val newStr = mutable.joinToString(",")
                                    sectionOrder0Str = newStr
                                    prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_0, newStr).apply()
                                }
                            },
                            onPinSection = { secId: String ->
                                pinnedSection0 = secId
                                prefs.edit().putString(LightspeedPreferences.KEY_TAB_PINNED_ACCORDION_0, secId).apply()
                            },
                            onExitBlueprint = {
                                blueprintTabTarget = null
                            },
                            subSections = mapOf(
                                "left_top" to currentSubLeftTop,
                                "left_bottom" to currentSubLeftBottom,
                                "left_unified" to currentSubLeftUnified
                            ),
                            subSectionTitles = mapOf(
                                "geo" to "Sensor Geometry",
                                "scrub" to "Inward Scrubbing Control",
                                "gestures" to "Gesture Actions & Macro Mappings"
                            ),

                            onMoveSubUp = { secId, idx ->
                                if (idx > 0) {
                                    val (list, key) = when (secId) {
                                        "left_top" -> currentSubLeftTop to "pref_sub_order_left_top_2"
                                        "left_bottom" -> currentSubLeftBottom to "pref_sub_order_left_bottom_2"
                                        "left_unified" -> currentSubLeftUnified to "pref_sub_order_left_unified_2"
                                        else -> return@BlueprintWireframeView
                                    }
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx - 1, item)
                                    when (secId) {
                                        "left_top" -> currentSubLeftTop = mutable
                                        "left_bottom" -> currentSubLeftBottom = mutable
                                        "left_unified" -> currentSubLeftUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },
                            onMoveSubDown = { secId, idx ->
                                val (list, key) = when (secId) {
                                    "left_top" -> currentSubLeftTop to "pref_sub_order_left_top_2"
                                    "left_bottom" -> currentSubLeftBottom to "pref_sub_order_left_bottom_2"
                                    "left_unified" -> currentSubLeftUnified to "pref_sub_order_left_unified_2"
                                    else -> return@BlueprintWireframeView
                                }
                                if (idx < list.size - 1) {
                                    val mutable = list.toMutableList()
                                    val item = mutable.removeAt(idx)
                                    mutable.add(idx + 1, item)
                                    when (secId) {
                                        "left_top" -> currentSubLeftTop = mutable
                                        "left_bottom" -> currentSubLeftBottom = mutable
                                        "left_unified" -> currentSubLeftUnified = mutable
                                    }
                                    prefs.edit().putString(key, mutable.joinToString(",")).apply()
                                }
                            },

                        )
                    } else {
                        LazyColumn(
                            state = listState0,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            item(key = "left_deflector_master") {
                                var isLeftEnabled by remember { mutableStateOf(LightspeedPreferences.isLeftDeflectorEnabled(context)) }
                                var leftStartupMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, "always_armed") ?: "always_armed") }

                                DeflectorMasterCard(
                                    flankName = "Left Deflector",
                                    isEnabled = isLeftEnabled,
                                    onToggle = { enabled ->
                                        isLeftEnabled = enabled
                                        LightspeedPreferences.setLeftDeflectorEnabled(context, enabled)
                                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                        com.sbf.lightspeed.system.LightspeedFlightNotificationManager.update(context)
                                    },
                                    startupMode = leftStartupMode,
                                    onStartupModeChange = { mode ->
                                        leftStartupMode = mode
                                        prefs.edit().putString(LightspeedPreferences.KEY_DEFLECTOR_DEFAULT_STATE, mode).apply()
                                    }
                                )
                            }

                            item {
                                UnifyFlankActionsCard(
                                    isUnified = isLeftFlankUnified,
                                    onToggle = { enable ->
                                        if (enable) {
                                            showUnifyTemplateDialogForLeft = true
                                        } else {
                                            isLeftFlankUnified = false
                                            prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", false).commit()
                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                            onRefreshNeeded()
                                        }
                                    },
                                    onInfoClick = { showUnifyInfoDialog = true }
                                )
                            }

                            currentOrder0.forEach { secId ->
                                when (secId) {
                                    "left_center" -> {
                                        item(key = "left_center") {
                                             CompactAccordionSection(
                                                title = "Central Pill (Core Astrogation)",
                                                icon = {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Navigation,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                isExpanded = isLeftCenterExpanded,
                                                onToggle = {
                                                    toggleSection(0, "left_center", isLeftCenterExpanded) { isLeftCenterExpanded = it }
                                                    prefs.edit()
                                                        .putBoolean("pref_section_left_center_expanded", isLeftCenterExpanded)
                                                        .apply()
                                                    try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    onRefreshNeeded()
                                                }
                                            ) {
                                                DeflectorPillStylingContent(
                                                    context = context,
                                                    prefs = prefs,
                                                    isLeft = true,
                                                    onRefreshNeeded = onRefreshNeeded
                                                )
                                            }
                                        }
                                    }
                                    "left_unified" -> {
                                        if (isLeftFlankUnified) {
                                            item(key = "left_unified") {
                                                CompactAccordionSection(
                                                    title = "Unified Deflectors",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.SwapVert,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftUnifiedExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_unified", isLeftUnifiedExpanded) { isLeftUnifiedExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_unified_expanded", isLeftUnifiedExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Independent height, touch reach & stealth glow",
                                                            isExpanded = isLeftUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGeoExpanded = !isLeftUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_unified", isLeftUnifiedGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            Text("UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Upper Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Upper Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Lower Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Lower Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Dual Inward Scrubber Controls",
                                                            subtitle = "Independent upper & lower half scrubbers",
                                                            isExpanded = isLeftUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedScrubExpanded = !isLeftUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_unified", isLeftUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Unified Gesture Matrix",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGesturesExpanded = !isLeftUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_unified", isLeftUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "left_top" -> {
                                        if (!isLeftFlankUnified) {
                                            item(key = "left_top") {
                                                CompactAccordionSection(
                                                    title = "Upper Deflector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowUp,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftTopExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_top", isLeftTopExpanded) { isLeftTopExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_top_expanded", isLeftTopExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftTopGeoExpanded,
                                                            onToggle = {
                                                                isLeftTopGeoExpanded = !isLeftTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_top", isLeftTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isLeftTopScrubExpanded,
                                                            onToggle = {
                                                                isLeftTopScrubExpanded = !isLeftTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_top", isLeftTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftTopGesturesExpanded,
                                                            onToggle = {
                                                                isLeftTopGesturesExpanded = !isLeftTopGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_top", isLeftTopGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_TOP_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_TOP_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "left_bottom" -> {
                                        if (!isLeftFlankUnified) {
                                            item(key = "left_bottom") {
                                                CompactAccordionSection(
                                                    title = "Lower Deflector Zone",
                                                    icon = {
                                                        Icon(
                                                            imageVector = Icons.Outlined.KeyboardArrowDown,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    },
                                                    isExpanded = isLeftBottomExpanded,
                                                    onToggle = {
                                                        toggleSection(0, "left_bottom", isLeftBottomExpanded) { isLeftBottomExpanded = it }
                                                        prefs.edit()
                                                            .putBoolean("pref_section_left_bottom_expanded", isLeftBottomExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftBottomGeoExpanded,
                                                            onToggle = {
                                                                isLeftBottomGeoExpanded = !isLeftBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_bottom", isLeftBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isLeftBottomScrubExpanded,
                                                            onToggle = {
                                                                isLeftBottomScrubExpanded = !isLeftBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_bottom", isLeftBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftBottomGesturesExpanded,
                                                            onToggle = {
                                                                isLeftBottomGesturesExpanded = !isLeftBottomGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_bottom", isLeftBottomGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            leftCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_LEFT_BOTTOM_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_LEFT_BOTTOM_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
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