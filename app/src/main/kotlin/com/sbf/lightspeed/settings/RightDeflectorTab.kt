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
    pastedJsonTextState: MutableState<String>,
    pendingBackTapScopeState: MutableState<String>,
    pinnedSection2State: MutableState<String>,
    prefs: android.content.SharedPreferences,
    sectionOrder2StrState: MutableState<String>,
    sectionTitles2: Map<String, String>,
    selectedTemplateOptionState: MutableIntState,
    showAmoledWarningDialogState: MutableState<Boolean>,
    showBatteryWarningDialogState: MutableState<Boolean>,
    showImportOptionsDialogState: MutableState<Boolean>,
    showNotificationAccessDialogState: MutableState<Boolean>,
    showOemShieldDialogState: MutableState<Boolean>,
    showPasteJsonDialogState: MutableState<Boolean>,
    showResetConfirmDialogState: MutableState<Boolean>,
    showSymmetryInfoDialogState: MutableState<Boolean>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForLeftState: MutableState<Boolean>,
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
    var isTopExpanded by isTopExpandedState
    var isTopGeoExpanded by isTopGeoExpandedState
    var isTopGesturesExpanded by isTopGesturesExpandedState
    var isTopScrubExpanded by isTopScrubExpandedState
    var pastedJsonText by pastedJsonTextState
    var pendingBackTapScope by pendingBackTapScopeState
    var pinnedSection2 by pinnedSection2State
    var sectionOrder2Str by sectionOrder2StrState
    var selectedTemplateOption by selectedTemplateOptionState
    var showAmoledWarningDialog by showAmoledWarningDialogState
    var showBatteryWarningDialog by showBatteryWarningDialogState
    var showImportOptionsDialog by showImportOptionsDialogState
    var showNotificationAccessDialog by showNotificationAccessDialogState
    var showOemShieldDialog by showOemShieldDialogState
    var showPasteJsonDialog by showPasteJsonDialogState
    var showResetConfirmDialog by showResetConfirmDialogState
    var showSymmetryInfoDialog by showSymmetryInfoDialogState
    var showUnifyInfoDialog by showUnifyInfoDialogState
    var showUnifyTemplateDialogForLeft by showUnifyTemplateDialogForLeftState
    var showUnifyTemplateDialogForRight by showUnifyTemplateDialogForRightState
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
        "SWIPE_LEFT_DOWN" to ("Two-Step: Inward → Down" to ArrowDirection.LEFT_DOWN)
    )
                    val defaultOrder2 = if (isRightFlankUnified) listOf("unified") else listOf("center", "top", "bottom")
                    val currentOrder2 = sectionOrder2Str.split(",").map { it.trim() }.filter { it in defaultOrder2 }.distinct().let { list ->
                        list + (defaultOrder2 - list.toSet())
                    }

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
                            }
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
                                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", false).commit()
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
                                                title = "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Central Pill (Core Zone)",
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
                                                        .putBoolean("pref_sidebar_preview", isCenterExpanded || isTopExpanded || isBottomExpanded || isRightUnifiedExpanded)
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
                                                    title = "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Flank Vector Zones (Upper & Lower)",
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
                                                            .putBoolean("pref_sidebar_preview", isRightUnifiedExpanded && isRightUnifiedGeoExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Independent height, touch reach & stealth glow",
                                                            isExpanded = isRightUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_unified", isRightUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isRightUnifiedGeoExpanded)
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

                                                        CollapsibleSubSection(
                                                            title = "Dual Inward Scrubber Controls",
                                                            subtitle = "Independent upper & lower half scrubbers",
                                                            isExpanded = isRightUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isRightUnifiedScrubExpanded = !isRightUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_unified", isRightUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Upper Half Inward Sweep (Scrubbing)", listOf("none", "system:brightness", "system:volume", "system:screen_timeout"), tokenLabelCache)
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Lower Half Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness", "system:screen_timeout"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Unified Gesture Matrix",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isRightUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGesturesExpanded = !isRightUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_unified", isRightUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            rightCustomVectors.forEach { (vectorKey, pairInfo) ->
                                                                val (vectorTitle, arrowEnum) = pairInfo
                                                                GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_UNIFIED_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                                                                GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_UNIFIED_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
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
                                                    title = "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Upper Vector Zone",
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
                                                            .putBoolean("pref_sidebar_preview", newTop && isTopGeoExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isTopGeoExpanded,
                                                            onToggle = {
                                                                isTopGeoExpanded = !isTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_top", isTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

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
                                    "bottom" -> {
                                        if (!isRightFlankUnified) {
                                            item(key = "bottom") {
                                                CompactAccordionSection(
                                                    title = "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Lower Vector Zone",
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
                                                            .putBoolean("pref_sidebar_preview", newBottom && isBottomGeoExpanded)
                                                            .apply()
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                    }
                                                ) {
                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isBottomGeoExpanded,
                                                            onToggle = {
                                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

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
