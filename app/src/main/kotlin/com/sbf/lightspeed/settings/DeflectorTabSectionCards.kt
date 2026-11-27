package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.system.safeReloadPreferences
import kotlinx.coroutines.CoroutineScope

fun getCustomVectorsForFlank(isLeft: Boolean): List<Pair<String, Pair<String, ArrowDirection>>> {
    return if (isLeft) {
        listOf(
            "TAP" to (lsString(R.string.gest_tap) to ArrowDirection.TAP),
            "SWIPE_UP" to (lsString(R.string.gest_swipe_up) to ArrowDirection.SWIPE_UP),
            "SWIPE_DOWN" to (lsString(R.string.gest_swipe_down) to ArrowDirection.SWIPE_DOWN),
            "SWIPE_RIGHT" to (lsString(R.string.gest_swipe_inward) to ArrowDirection.SWIPE_RIGHT),
            "SWIPE_UP_DOWN" to (lsString(R.string.gest_rebound_up) to ArrowDirection.SWIPE_UP_DOWN),
            "SWIPE_DOWN_UP" to (lsString(R.string.gest_rebound_down) to ArrowDirection.SWIPE_DOWN_UP),
            "SWIPE_RIGHT_BACK" to (lsString(R.string.gest_rebound_inward) to ArrowDirection.RIGHT_BACK),
            "SWIPE_UP_RIGHT" to (lsString(R.string.gest_two_step_up_in) to ArrowDirection.SWIPE_UP_RIGHT),
            "SWIPE_DOWN_RIGHT" to (lsString(R.string.gest_two_step_down_in) to ArrowDirection.SWIPE_DOWN_RIGHT),
            "SWIPE_RIGHT_UP" to (lsString(R.string.gest_two_step_in_up) to ArrowDirection.RIGHT_UP),
        )
    } else {
        listOf(
            "TAP" to (lsString(R.string.gest_tap) to ArrowDirection.TAP),
            "SWIPE_UP" to (lsString(R.string.gest_swipe_up) to ArrowDirection.SWIPE_UP),
            "SWIPE_DOWN" to (lsString(R.string.gest_swipe_down) to ArrowDirection.SWIPE_DOWN),
            "SWIPE_LEFT" to (lsString(R.string.gest_swipe_inward) to ArrowDirection.SWIPE_LEFT),
            "SWIPE_UP_DOWN" to (lsString(R.string.gest_rebound_up) to ArrowDirection.SWIPE_UP_DOWN),
            "SWIPE_DOWN_UP" to (lsString(R.string.gest_rebound_down) to ArrowDirection.SWIPE_DOWN_UP),
            "SWIPE_LEFT_BACK" to (lsString(R.string.gest_rebound_inward) to ArrowDirection.LEFT_BACK),
            "SWIPE_UP_LEFT" to (lsString(R.string.gest_two_step_up_in) to ArrowDirection.SWIPE_UP_LEFT),
            "SWIPE_DOWN_LEFT" to (lsString(R.string.gest_two_step_down_in) to ArrowDirection.SWIPE_DOWN_LEFT),
            "SWIPE_LEFT_UP" to (lsString(R.string.gest_two_step_in_up) to ArrowDirection.LEFT_UP),
        )
    }
}

class DeflectorSectionExpansionStates(
    val isCenterExpanded: Boolean,
    val onCenterToggle: () -> Unit,
    val isCenterGeoExpanded: Boolean,
    val onCenterGeoToggle: () -> Unit,
    val isUnifiedExpanded: Boolean,
    val onUnifiedToggle: () -> Unit,
    val isTopExpanded: Boolean,
    val onTopToggle: () -> Unit,
    val isBottomExpanded: Boolean,
    val onBottomToggle: () -> Unit,
    val isUnifiedGeoExpanded: Boolean,
    val onUnifiedGeoToggle: () -> Unit,
    val isUnifiedScrubExpanded: Boolean,
    val onUnifiedScrubToggle: () -> Unit,
    val isUnifiedGesturesExpanded: Boolean,
    val onUnifiedGesturesToggle: () -> Unit,
    val isTopGeoExpanded: Boolean,
    val onTopGeoToggle: () -> Unit,
    val isTopScrubExpanded: Boolean,
    val onTopScrubToggle: () -> Unit,
    val isTopGesturesExpanded: Boolean,
    val onTopGesturesToggle: () -> Unit,
    val isBottomGeoExpanded: Boolean,
    val onBottomGeoToggle: () -> Unit,
    val isBottomScrubExpanded: Boolean,
    val onBottomScrubToggle: () -> Unit,
    val isBottomGesturesExpanded: Boolean,
    val onBottomGesturesToggle: () -> Unit,
    val isTrackpadExpanded: Boolean,
    val onTrackpadToggle: () -> Unit,
    val isTrackpadGeoExpanded: Boolean,
    val onTrackpadGeoToggle: () -> Unit,
    val isTrackpadPhysicsExpanded: Boolean,
    val onTrackpadPhysicsToggle: () -> Unit,
)

fun buildDeflectorExpansionStates(
    tabIndex: Int,
    centerSecId: String,
    unifiedSecId: String,
    topSecId: String,
    bottomSecId: String,
    trackpadSecId: String,
    flankKey: String,
    isCenterExpanded: Boolean,
    isCenterGeoExpanded: Boolean,
    isUnifiedExpanded: Boolean,
    isTopExpanded: Boolean,
    isBottomExpanded: Boolean,
    isUnifiedGeoExpanded: Boolean,
    isUnifiedScrubExpanded: Boolean,
    isUnifiedGesturesExpanded: Boolean,
    isTopGeoExpanded: Boolean,
    isTopScrubExpanded: Boolean,
    isTopGesturesExpanded: Boolean,
    isBottomGeoExpanded: Boolean,
    isBottomScrubExpanded: Boolean,
    isBottomGesturesExpanded: Boolean,
    isTrackpadExpanded: Boolean,
    isTrackpadGeoExpanded: Boolean,
    isTrackpadPhysicsExpanded: Boolean,
    prefs: SharedPreferences,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    toggleSubSection: (Int, String, String, Boolean, (Boolean) -> Unit) -> Unit,
    onSetCenterExpanded: (Boolean) -> Unit,
    onSetCenterGeoExpanded: (Boolean) -> Unit,
    onSetUnifiedExpanded: (Boolean) -> Unit,
    onSetTopExpanded: (Boolean) -> Unit,
    onSetBottomExpanded: (Boolean) -> Unit,
    onSetUnifiedGeoExpanded: (Boolean) -> Unit,
    onSetUnifiedScrubExpanded: (Boolean) -> Unit,
    onSetUnifiedGesturesExpanded: (Boolean) -> Unit,
    onSetTopGeoExpanded: (Boolean) -> Unit,
    onSetTopScrubExpanded: (Boolean) -> Unit,
    onSetTopGesturesExpanded: (Boolean) -> Unit,
    onSetBottomGeoExpanded: (Boolean) -> Unit,
    onSetBottomScrubExpanded: (Boolean) -> Unit,
    onSetBottomGesturesExpanded: (Boolean) -> Unit,
    onSetTrackpadExpanded: (Boolean) -> Unit,
    onSetTrackpadGeoExpanded: (Boolean) -> Unit,
    onSetTrackpadPhysicsExpanded: (Boolean) -> Unit,
    onRefreshNeeded: () -> Unit
): DeflectorSectionExpansionStates {
    return DeflectorSectionExpansionStates(
        isCenterExpanded = isCenterExpanded,
        onCenterToggle = {
            toggleSection(tabIndex, centerSecId, isCenterExpanded, onSetCenterExpanded)
            prefs.edit().putBoolean("pref_section_${centerSecId}_expanded", !isCenterExpanded).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isCenterGeoExpanded = isCenterGeoExpanded,
        onCenterGeoToggle = {
            toggleSubSection(tabIndex, centerSecId, "geo", isCenterGeoExpanded, onSetCenterGeoExpanded)
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isUnifiedExpanded = isUnifiedExpanded,
        onUnifiedToggle = {
            toggleSection(tabIndex, unifiedSecId, isUnifiedExpanded, onSetUnifiedExpanded)
            val unifiedPrefKey = if (tabIndex == 0) {
                com.sbf.lightspeed.system.LightspeedPreferences.KEY_SECTION_LEFT_UNIFIED_EXPANDED
            } else {
                com.sbf.lightspeed.system.LightspeedPreferences.KEY_SECTION_RIGHT_UNIFIED_EXPANDED
            }
            prefs.edit().putBoolean(unifiedPrefKey, !isUnifiedExpanded).apply()
            safeReloadPreferences()
        },
        isTopExpanded = isTopExpanded,
        onTopToggle = {
            toggleSection(tabIndex, topSecId, isTopExpanded, onSetTopExpanded)
            prefs.edit().putBoolean("pref_section_${topSecId}_expanded", !isTopExpanded).apply()
            safeReloadPreferences()
        },
        isBottomExpanded = isBottomExpanded,
        onBottomToggle = {
            toggleSection(tabIndex, bottomSecId, isBottomExpanded, onSetBottomExpanded)
            prefs.edit().putBoolean("pref_section_${bottomSecId}_expanded", !isBottomExpanded).apply()
            safeReloadPreferences()
        },
        isUnifiedGeoExpanded = isUnifiedGeoExpanded,
        onUnifiedGeoToggle = {
            toggleSubSection(tabIndex, unifiedSecId, "geo", isUnifiedGeoExpanded, onSetUnifiedGeoExpanded)
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isUnifiedScrubExpanded = isUnifiedScrubExpanded,
        onUnifiedScrubToggle = {
            toggleSubSection(tabIndex, unifiedSecId, "scrub", isUnifiedScrubExpanded, onSetUnifiedScrubExpanded)
        },
        isUnifiedGesturesExpanded = isUnifiedGesturesExpanded,
        onUnifiedGesturesToggle = {
            toggleSubSection(tabIndex, unifiedSecId, "gestures", isUnifiedGesturesExpanded, onSetUnifiedGesturesExpanded)
        },
        isTopGeoExpanded = isTopGeoExpanded,
        onTopGeoToggle = {
            toggleSubSection(tabIndex, topSecId, "geo", isTopGeoExpanded, onSetTopGeoExpanded)
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isTopScrubExpanded = isTopScrubExpanded,
        onTopScrubToggle = {
            toggleSubSection(tabIndex, topSecId, "scrub", isTopScrubExpanded, onSetTopScrubExpanded)
        },
        isTopGesturesExpanded = isTopGesturesExpanded,
        onTopGesturesToggle = {
            toggleSubSection(tabIndex, topSecId, "gestures", isTopGesturesExpanded, onSetTopGesturesExpanded)
        },
        isBottomGeoExpanded = isBottomGeoExpanded,
        onBottomGeoToggle = {
            toggleSubSection(tabIndex, bottomSecId, "geo", isBottomGeoExpanded, onSetBottomGeoExpanded)
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isBottomScrubExpanded = isBottomScrubExpanded,
        onBottomScrubToggle = {
            toggleSubSection(tabIndex, bottomSecId, "scrub", isBottomScrubExpanded, onSetBottomScrubExpanded)
        },
        isBottomGesturesExpanded = isBottomGesturesExpanded,
        onBottomGesturesToggle = {
            toggleSubSection(tabIndex, bottomSecId, "gestures", isBottomGesturesExpanded, onSetBottomGesturesExpanded)
        },
        isTrackpadExpanded = isTrackpadExpanded,
        onTrackpadToggle = {
            toggleSection(tabIndex, trackpadSecId, isTrackpadExpanded, onSetTrackpadExpanded)
            prefs.edit().putBoolean("pref_accordion_trackpad_$flankKey", !isTrackpadExpanded).apply()
            safeReloadPreferences()
        },
        isTrackpadGeoExpanded = isTrackpadGeoExpanded,
        onTrackpadGeoToggle = {
            toggleSubSection(tabIndex, trackpadSecId, "geo", isTrackpadGeoExpanded, onSetTrackpadGeoExpanded)
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isTrackpadPhysicsExpanded = isTrackpadPhysicsExpanded,
        onTrackpadPhysicsToggle = {
            toggleSubSection(tabIndex, trackpadSecId, "physics", isTrackpadPhysicsExpanded, onSetTrackpadPhysicsExpanded)
        }
    )
}

fun LazyListScope.renderDeflectorOrderedSections(
    currentOrder: List<String>,
    centerSecId: String,
    unifiedSecId: String,
    topSecId: String,
    bottomSecId: String,
    trackpadSecId: String,
    isTrackpadLabsEnabled: Boolean,
    isFlankUnified: Boolean,
    isLeft: Boolean,
    context: Context,
    prefs: SharedPreferences,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    customVectors: List<Pair<String, Pair<String, ArrowDirection>>>,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    subBlueprintTarget: String?,
    onSubBlueprintToggle: (String) -> Unit,
    pinnedSection: String,
    currentSubTop: List<String>,
    currentSubBottom: List<String>,
    currentSubUnified: List<String>,
    currentSubTrackpad: List<String>,
    onSubTopChange: (List<String>) -> Unit,
    onSubBottomChange: (List<String>) -> Unit,
    onSubUnifiedChange: (List<String>) -> Unit,
    onSubTrackpadChange: (List<String>) -> Unit,
    onPinSection: (String) -> Unit,
    exp: DeflectorSectionExpansionStates,
    onMooringConfirm: (MooringConfirmData) -> Unit,
    onRefreshNeeded: () -> Unit
) {
    currentOrder.forEach { secId ->
        when (secId) {
            trackpadSecId -> {
                if (isTrackpadLabsEnabled) {
                    item(key = trackpadSecId, contentType = "accordion") {
                        CyberneticTrackpadAccordion(
                            isLeft = isLeft,
                            context = context,
                            prefs = prefs,
                            isExpanded = exp.isTrackpadExpanded,
                            onToggle = exp.onTrackpadToggle,
                            subBlueprintTarget = subBlueprintTarget,
                            onSubBlueprintToggle = { onSubBlueprintToggle(trackpadSecId) },
                            isGeoExpanded = exp.isTrackpadGeoExpanded,
                            onGeoToggle = exp.onTrackpadGeoToggle,
                            isPhysicsExpanded = exp.isTrackpadPhysicsExpanded,
                            onPhysicsToggle = exp.onTrackpadPhysicsToggle,
                            onRefreshNeeded = onRefreshNeeded,
                            externalSubOrder = currentSubTrackpad,
                            externalPinnedSection = pinnedSection,
                            onSubOrderChange = onSubTrackpadChange,
                            onPinSubSection = onPinSection
                        )
                    }
                }
            }
            centerSecId -> {
                item(key = centerSecId, contentType = "accordion") {
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
                        isExpanded = exp.isCenterExpanded,
                        onToggle = exp.onCenterToggle
                    ) {
                        DeflectorPillStylingContent(
                            context = context,
                            prefs = prefs,
                            isLeft = isLeft,
                            isGeoExpanded = exp.isCenterGeoExpanded,
                            onGeoToggle = exp.onCenterGeoToggle,
                            onRefreshNeeded = onRefreshNeeded
                        )
                    }
                }
            }
            unifiedSecId -> {
                if (isFlankUnified) {
                    item(key = unifiedSecId, contentType = "accordion") {
                        DeflectorUnifiedSection(
                            isLeft = isLeft,
                            context = context,
                            prefs = prefs,
                            dynamicActionTokens = dynamicActionTokens,
                            tokenLabelCache = tokenLabelCache,
                            isExpanded = exp.isUnifiedExpanded,
                            onToggle = exp.onUnifiedToggle,
                            subBlueprintTarget = subBlueprintTarget,
                            onSubBlueprintToggle = { onSubBlueprintToggle(unifiedSecId) },
                            isGeoExpanded = exp.isUnifiedGeoExpanded,
                            onGeoToggle = exp.onUnifiedGeoToggle,
                            isScrubExpanded = exp.isUnifiedScrubExpanded,
                            onScrubToggle = exp.onUnifiedScrubToggle,
                            isGesturesExpanded = exp.isUnifiedGesturesExpanded,
                            onGesturesToggle = exp.onUnifiedGesturesToggle,
                            customVectors = customVectors,
                            listState = listState,
                            coroutineScope = coroutineScope,
                            onMooringConfirm = onMooringConfirm,
                            externalSubOrder = currentSubUnified,
                            externalPinnedSection = pinnedSection,
                            onSubOrderChange = onSubUnifiedChange,
                            onPinSubSection = onPinSection
                        )
                    }
                }
            }
            topSecId -> {
                if (!isFlankUnified) {
                    item(key = topSecId, contentType = "accordion") {
                        DeflectorFlankSection(
                            isLeft = isLeft,
                            isUpper = true,
                            context = context,
                            prefs = prefs,
                            dynamicActionTokens = dynamicActionTokens,
                            tokenLabelCache = tokenLabelCache,
                            isExpanded = exp.isTopExpanded,
                            onToggle = exp.onTopToggle,
                            subBlueprintTarget = subBlueprintTarget,
                            onSubBlueprintToggle = { onSubBlueprintToggle(topSecId) },
                            isGeoExpanded = exp.isTopGeoExpanded,
                            onGeoToggle = exp.onTopGeoToggle,
                            isScrubExpanded = exp.isTopScrubExpanded,
                            onScrubToggle = exp.onTopScrubToggle,
                            isGesturesExpanded = exp.isTopGesturesExpanded,
                            onGesturesToggle = exp.onTopGesturesToggle,
                            customVectors = customVectors,
                            onRefreshNeeded = onRefreshNeeded,
                            externalSubOrder = currentSubTop,
                            externalPinnedSection = pinnedSection,
                            onSubOrderChange = onSubTopChange,
                            onPinSubSection = onPinSection
                        )
                    }
                }
            }
            bottomSecId -> {
                if (!isFlankUnified) {
                    item(key = bottomSecId, contentType = "accordion") {
                        DeflectorFlankSection(
                            isLeft = isLeft,
                            isUpper = false,
                            context = context,
                            prefs = prefs,
                            dynamicActionTokens = dynamicActionTokens,
                            tokenLabelCache = tokenLabelCache,
                            isExpanded = exp.isBottomExpanded,
                            onToggle = exp.onBottomToggle,
                            subBlueprintTarget = subBlueprintTarget,
                            onSubBlueprintToggle = { onSubBlueprintToggle(bottomSecId) },
                            isGeoExpanded = exp.isBottomGeoExpanded,
                            onGeoToggle = exp.onBottomGeoToggle,
                            isScrubExpanded = exp.isBottomScrubExpanded,
                            onScrubToggle = exp.onBottomScrubToggle,
                            isGesturesExpanded = exp.isBottomGesturesExpanded,
                            onGesturesToggle = exp.onBottomGesturesToggle,
                            customVectors = customVectors,
                            onRefreshNeeded = onRefreshNeeded,
                            externalSubOrder = currentSubBottom,
                            externalPinnedSection = pinnedSection,
                            onSubOrderChange = onSubBottomChange,
                            onPinSubSection = onPinSection
                        )
                    }
                }
            }
        }
    }
}
