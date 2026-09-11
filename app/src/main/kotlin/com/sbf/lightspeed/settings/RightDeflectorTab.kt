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
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
    pendingBackTapScopeState: MutableState<String?>,
    pinnedSection2State: MutableState<String?>,
    prefs: android.content.SharedPreferences,
    sectionOrder2StrState: MutableState<String>,
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
    toggleSection: (String) -> Unit,
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

                            item(key = "right_deflector_glow") {
                                DeflectorGlowCard(context = context, prefs = prefs)
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
                                                title = "${com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.RIGHT_DEFLECTOR)} — Astrogation Core Zone",
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
                                                }
                                            ) {
                                                 Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                    CollapsibleSubSection(
                                                        title = "Sensor Geometry",
                                                        subtitle = "Deflector span, touch reach, offset & stealth glow",
                                                        isExpanded = isCenterGeoExpanded,
                                                        onToggle = {
                                                            isCenterGeoExpanded = !isCenterGeoExpanded
                                                            prefs.edit()
                                                                .putBoolean("pref_sub_geo_center", isCenterGeoExpanded)
                                                                .putBoolean("pref_sidebar_preview", isCenterGeoExpanded)
                                                                .apply()
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            onRefreshNeeded()
                                                        }
                                                    ) {
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_y_offset", "", "Deflector Alignment Offset", -300, 300, 10, 0)
                                                        PrefDottedSliderRow(context, prefs, "pref_sidebar_center_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                    }
                                                }
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
            }
        }

        // Dialogs
        if (showResetConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showResetConfirmDialog = false },
                title = { Text("Reset to Factory Defaults?", fontWeight = FontWeight.Bold, color = Color.White) },
                text = { Text("This will wipe all customized gestures, sensitivity sliders, and custom gear sets. This action cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.resetToDefaults(context)
                            showResetConfirmDialog = false
                            Toast.makeText(context, "Preferences reset to factory defaults", Toast.LENGTH_SHORT).show()
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Reset Everything", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (importStatusMessage != null) {
            AlertDialog(
                onDismissRequest = { viewModel.clearImportStatus() },
                title = {
                    Text(
                        text = if (isImportSuccess) "Backup Restored" else "Import Status",
                        fontWeight = FontWeight.Bold,
                        color = if (isImportSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                },
                text = {
                    Text(
                        text = importStatusMessage ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val wasSuccess = isImportSuccess
                            viewModel.clearImportStatus()
                            if (wasSuccess) {
                                (context as? Activity)?.recreate() ?: onRefreshNeeded()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isImportSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(if (isImportSuccess) "Done" else "Dismiss", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showImportOptionsDialog) {
            AlertDialog(
                onDismissRequest = { showImportOptionsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Restore Configuration", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "SELECT RESTORE METHOD",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Choose via system picker
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .clickable {
                                    showImportOptionsDialog = false
                                    importLauncher.launch("*/*")
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Choose Backup File", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                Text("Select your backup .json file from storage", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Paste JSON Directly
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                                .clickable {
                                    showImportOptionsDialog = false
                                    pastedJsonText = ""
                                    showPasteJsonDialog = true
                                }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Paste JSON Text Directly", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                                Text("Paste backup payload from clipboard", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showImportOptionsDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showPasteJsonDialog) {
            AlertDialog(
                onDismissRequest = { showPasteJsonDialog = false },
                title = { Text("Paste Backup JSON", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("Paste your exported JSON payload below:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = pastedJsonText,
                            onValueChange = { pastedJsonText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            placeholder = { Text("{\n  \"settings\": {\n    ...\n  }\n}", fontSize = 12.sp) },
                            maxLines = 15,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp, color = Color.White)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val text = pastedJsonText.trim()
                            showPasteJsonDialog = false
                            if (text.isBlank()) {
                                viewModel.importFromJson(context, text)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Restore", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteJsonDialog = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showSymmetryInfoDialog) {
            AlertDialog(
                onDismissRequest = { showSymmetryInfoDialog = false },
                icon = { Icon(Icons.Default.SyncAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Deflector Symmetry & Coupling", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("The 3-position selector acts as a non-destructive Flight Profile Switch:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("• CLONE LEFT: Left Wing is master. Right Wing automatically mirrors its geometry or inverts and executes its gestures.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• INDEPENDENT: Bilateral multi-role setup. Both wings have dedicated, independent gesture maps and dimensions.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• CLONE RIGHT: Right Wing is master. Left Wing automatically mirrors its geometry or opens the Cockpit / executes its gestures.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🔒 Switching profiles never deletes your custom setups. Switching back to INDEPENDENT restores all unique mappings instantly.", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = {
                    Button(onClick = { showSymmetryInfoDialog = false }) {
                        Text("Got it", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyInfoDialog) {
            AlertDialog(
                onDismissRequest = { showUnifyInfoDialog = false },
                icon = { Icon(Icons.Default.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Unified Flank Actions & Dual Scrubbers", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("This unifies your gesture configuration while preserving ergonomics:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("• Single Gesture Set: Configure standard directional gestures (Tap, Swipe In, Swipe Up/Down, Hold) once for the whole flank.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Dual Scrubbers: Independent inward scrubbing selectors for the upper half (e.g. Brightness) and lower half (e.g. Volume).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("• Independent Geometry: Top and bottom wing spans, reaches, and glows remain independently tunable for natural grip comfort.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("🔒 Toggling OFF immediately restores your previous separate upper and lower gesture mappings without data loss.", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                },
                confirmButton = {
                    Button(onClick = { showUnifyInfoDialog = false }) {
                        Text("Got it", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyTemplateDialogForLeft) {
            AlertDialog(
                onDismissRequest = { showUnifyTemplateDialogForLeft = false },
                title = { Text("Activate Unified Left Flank", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose an action template to initialize your unified flank set:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        listOf(
                            0 to "Clone Upper Vector Actions (Top Half)",
                            1 to "Clone Lower Vector Actions (Bottom Half)",
                            2 to "Use Dedicated Unified Set"
                        ).forEach { (optIdx, optLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedTemplateOption == optIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTemplateOption = optIdx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTemplateOption == optIdx,
                                    onClick = { selectedTemplateOption = optIdx },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(optLabel, fontSize = 12.5.sp, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedTemplateOption == 0) {
                                cloneFlankActions("LEFT_TOP", "LEFT_UNIFIED")
                            } else if (selectedTemplateOption == 1) {
                                cloneFlankActions("LEFT_BOTTOM", "LEFT_UNIFIED")
                            }
                            isLeftFlankUnified = true
                            prefs.edit().putBoolean("pref_sidebar_left_link_flank_actions", true).commit()
                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            showUnifyTemplateDialogForLeft = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Unify Actions", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnifyTemplateDialogForLeft = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showUnifyTemplateDialogForRight) {
            AlertDialog(
                onDismissRequest = { showUnifyTemplateDialogForRight = false },
                title = { Text("Activate Unified Right Flank", fontWeight = FontWeight.Bold, color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Choose an action template to initialize your unified flank set:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        listOf(
                            0 to "Clone Upper Vector Actions (Top Half)",
                            1 to "Clone Lower Vector Actions (Bottom Half)",
                            2 to "Use Dedicated Unified Set"
                        ).forEach { (optIdx, optLabel) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedTemplateOption == optIdx) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { selectedTemplateOption = optIdx }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedTemplateOption == optIdx,
                                    onClick = { selectedTemplateOption = optIdx },
                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(optLabel, fontSize = 12.5.sp, color = Color.White)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (selectedTemplateOption == 0) {
                                cloneFlankActions("TOP", "UNIFIED")
                            } else if (selectedTemplateOption == 1) {
                                cloneFlankActions("BOTTOM", "UNIFIED")
                            }
                            isRightFlankUnified = true
                            prefs.edit().putBoolean("pref_sidebar_right_link_flank_actions", true).commit()
                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                            showUnifyTemplateDialogForRight = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Unify Actions", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnifyTemplateDialogForRight = false }) {
                        Text("Cancel", color = Color.White)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showOemShieldDialog) {
            var preserveScreenshot by remember { mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, true)) }
            var preserveAccessibility by remember { mutableStateOf(prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, true)) }

            AlertDialog(
                onDismissRequest = { showOemShieldDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("OEM Compatibility Shield", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Configure hardware-level passthrough guardrails to ensure native Android system shortcuts remain responsive on your specific device hardware.",
                            fontSize = 13.sp,
                            color = Color.LightGray.copy(alpha = 0.85f),
                            lineHeight = 17.sp
                        )
                        PrefToggleRow(
                            title = "Preserve Screenshot Shortcut",
                            subtitle = "Whitelist immediate pass-through for Power + Vol Down screenshot captures",
                            isChecked = preserveScreenshot,
                            onCheckedChange = { preserveScreenshot = it }
                        )
                        PrefToggleRow(
                            title = "Preserve Accessibility Shortcut",
                            subtitle = "Bypass custom chords after 1.5s simultaneous hold and yield directly to Android TalkBack / accessibility shortcut",
                            isChecked = preserveAccessibility,
                            onCheckedChange = { preserveAccessibility = it }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit()
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_SHIELD_COMPLETED, true)
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_SCREENSHOT, preserveScreenshot)
                                .putBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_OEM_PRESERVE_ACCESSIBILITY, preserveAccessibility)
                                .apply()
                            showOemShieldDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("APPLY SHIELD", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showOemShieldDialog = false }) {
                        Text("DISMISS", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showBatteryWarningDialog) {
            AlertDialog(
                onDismissRequest = {
                    showBatteryWarningDialog = false
                },
                title = {
                    Text("⚠️ High Battery Usage Warning", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                },
                text = {
                    Text(
                        "Detecting back taps while the screen is off keeps your CPU awake (Partial Wake Lock), preventing Android from entering deep sleep. This typically consumes 2% to 4% battery per hour while idle.",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, pendingBackTapScope).apply()
                            showBatteryWarningDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("ENABLE ANYWAY", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on").apply()
                            showBatteryWarningDialog = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("CANCEL", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showNotificationAccessDialog) {
            AlertDialog(
                onDismissRequest = { showNotificationAccessDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    }
                },
                text = {
                    Text(
                        "To display real-time download progress and active media playback in your status bar or notch pill, Android requires Notification Access (Device & App Notifications).\n\nLightspeed is 100% offline and never records, stores, or transmits your personal notifications.",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showNotificationAccessDialog = false
                            try {
                                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("OPEN SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showNotificationAccessDialog = false }) {
                        Text("NOT NOW", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (showAmoledWarningDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAmoledWarningDialog = false
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                    onRefreshNeeded()
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Hardware Notice: AMOLED Image Retention", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                    }
                },
                text = {
                    Text(
                        "Prolonged static display on OLED panels can cause permanent subpixel degradation (burn-in). Keep auto-sleep enabled?",
                        fontSize = 13.sp,
                        color = Color.LightGray.copy(alpha = 0.9f),
                        lineHeight = 18.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s").apply()
                            showAmoledWarningDialog = false
                            onRefreshNeeded()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Keep Auto-Sleep (Recommended)", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "never").apply()
                            showAmoledWarningDialog = false
                            onRefreshNeeded()
                        }
                    ) {
                        Text("Enable Always-On (My Device Has No OLED / I Understand)", color = MaterialTheme.colorScheme.error)
                    }
                },
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
}
