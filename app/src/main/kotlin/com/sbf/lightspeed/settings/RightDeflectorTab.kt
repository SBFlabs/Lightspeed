package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

@Composable
fun RightDeflectorTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: Context,
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
    listState2: LazyListState,
    onRefreshNeeded: () -> Unit,
    pinnedSection2State: MutableState<String>,
    prefs: SharedPreferences,
    sectionOrder2StrState: MutableState<String>,
    sectionTitles2: Map<String, String>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForRightState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    DeflectorTabBody(
        isLeft = false,
        tabIndex = 2,
        tabTitle = "Deflectors ▶",
        blueprintTabTargetState = blueprintTabTargetState,
        context = context,
        prefs = prefs,
        dynamicActionTokens = dynamicActionTokens,
        tokenLabelCache = tokenLabelCache,
        listState = listState2,
        onRefreshNeeded = onRefreshNeeded,
        toggleSection = toggleSection,
        pinnedSectionState = pinnedSection2State,
        sectionOrderStrState = sectionOrder2StrState,
        sectionTitles = sectionTitles2,
        isFlankUnifiedState = isRightFlankUnifiedState,
        showUnifyInfoDialogState = showUnifyInfoDialogState,
        showUnifyTemplateDialogState = showUnifyTemplateDialogForRightState,
        isCenterExpandedState = isCenterExpandedState,
        isCenterGeoExpandedState = isCenterGeoExpandedState,
        isUnifiedExpandedState = isRightUnifiedExpandedState,
        isUnifiedGeoExpandedState = isRightUnifiedGeoExpandedState,
        isUnifiedGesturesExpandedState = isRightUnifiedGesturesExpandedState,
        isUnifiedScrubExpandedState = isRightUnifiedScrubExpandedState,
        isTopExpandedState = isTopExpandedState,
        isTopGeoExpandedState = isTopGeoExpandedState,
        isTopGesturesExpandedState = isTopGesturesExpandedState,
        isTopScrubExpandedState = isTopScrubExpandedState,
        isBottomExpandedState = isBottomExpandedState,
        isBottomGeoExpandedState = isBottomGeoExpandedState,
        isBottomGesturesExpandedState = isBottomGesturesExpandedState,
        isBottomScrubExpandedState = isBottomScrubExpandedState
    )
}