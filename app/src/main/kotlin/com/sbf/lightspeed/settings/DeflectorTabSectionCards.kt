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
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.safeReloadPreferences
import kotlinx.coroutines.CoroutineScope

fun getCustomVectorsForFlank(isLeft: Boolean): List<Pair<String, Pair<String, ArrowDirection>>> {
    return if (isLeft) {
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

class DeflectorSectionExpansionStates(
    val isCenterExpanded: Boolean,
    val onCenterToggle: () -> Unit,
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
)

fun buildDeflectorExpansionStates(
    tabIndex: Int,
    centerSecId: String,
    unifiedSecId: String,
    topSecId: String,
    bottomSecId: String,
    isCenterExpanded: Boolean,
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
    prefs: SharedPreferences,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    onSetCenterExpanded: (Boolean) -> Unit,
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
        isUnifiedExpanded = isUnifiedExpanded,
        onUnifiedToggle = {
            toggleSection(tabIndex, unifiedSecId, isUnifiedExpanded, onSetUnifiedExpanded)
            prefs.edit().putBoolean("pref_section_${unifiedSecId}_expanded", !isUnifiedExpanded).apply()
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
            onSetUnifiedGeoExpanded(!isUnifiedGeoExpanded)
            prefs.edit().putBoolean("pref_sub_geo_${unifiedSecId}", !isUnifiedGeoExpanded).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isUnifiedScrubExpanded = isUnifiedScrubExpanded,
        onUnifiedScrubToggle = {
            onSetUnifiedScrubExpanded(!isUnifiedScrubExpanded)
            prefs.edit().putBoolean("pref_sub_scrub_${unifiedSecId}", !isUnifiedScrubExpanded).apply()
        },
        isUnifiedGesturesExpanded = isUnifiedGesturesExpanded,
        onUnifiedGesturesToggle = {
            onSetUnifiedGesturesExpanded(!isUnifiedGesturesExpanded)
            prefs.edit().putBoolean("pref_sub_gestures_${unifiedSecId}", !isUnifiedGesturesExpanded).apply()
        },
        isTopGeoExpanded = isTopGeoExpanded,
        onTopGeoToggle = {
            onSetTopGeoExpanded(!isTopGeoExpanded)
            prefs.edit().putBoolean("pref_sub_geo_${topSecId}", !isTopGeoExpanded).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isTopScrubExpanded = isTopScrubExpanded,
        onTopScrubToggle = {
            onSetTopScrubExpanded(!isTopScrubExpanded)
            prefs.edit().putBoolean("pref_sub_scrub_${topSecId}", !isTopScrubExpanded).apply()
        },
        isTopGesturesExpanded = isTopGesturesExpanded,
        onTopGesturesToggle = {
            onSetTopGesturesExpanded(!isTopGesturesExpanded)
            prefs.edit().putBoolean("pref_sub_gestures_${topSecId}", !isTopGesturesExpanded).apply()
        },
        isBottomGeoExpanded = isBottomGeoExpanded,
        onBottomGeoToggle = {
            onSetBottomGeoExpanded(!isBottomGeoExpanded)
            prefs.edit().putBoolean("pref_sub_geo_${bottomSecId}", !isBottomGeoExpanded).apply()
            safeReloadPreferences()
            onRefreshNeeded()
        },
        isBottomScrubExpanded = isBottomScrubExpanded,
        onBottomScrubToggle = {
            onSetBottomScrubExpanded(!isBottomScrubExpanded)
            prefs.edit().putBoolean("pref_sub_scrub_${bottomSecId}", !isBottomScrubExpanded).apply()
        },
        isBottomGesturesExpanded = isBottomGesturesExpanded,
        onBottomGesturesToggle = {
            onSetBottomGesturesExpanded(!isBottomGesturesExpanded)
            prefs.edit().putBoolean("pref_sub_gestures_${bottomSecId}", !isBottomGesturesExpanded).apply()
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
    exp: DeflectorSectionExpansionStates,
    onMooringConfirm: (MooringConfirmData) -> Unit,
    onRefreshNeeded: () -> Unit
) {
    currentOrder.forEach { secId ->
        when (secId) {
            trackpadSecId -> {
                if (com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs)) {
                    item(key = trackpadSecId) {
                        CyberneticTrackpadAccordion(
                            isLeft = isLeft,
                            context = context,
                            prefs = prefs,
                            onRefreshNeeded = onRefreshNeeded
                        )
                    }
                }
            }
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
                        isExpanded = exp.isCenterExpanded,
                        onToggle = exp.onCenterToggle
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
                            onMooringConfirm = onMooringConfirm
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
                            onRefreshNeeded = onRefreshNeeded
                        )
                    }
                }
            }
        }
    }
}
