package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

@Composable
fun LeftDeflectorTabContent(
    blueprintTabTargetState: MutableState<Int?>,
    context: Context,
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
    listState0: LazyListState,
    onRefreshNeeded: () -> Unit,
    pinnedSection0State: MutableState<String>,
    prefs: SharedPreferences,
    sectionOrder0StrState: MutableState<String>,
    sectionTitles0: Map<String, String>,
    showUnifyInfoDialogState: MutableState<Boolean>,
    showUnifyTemplateDialogForLeftState: MutableState<Boolean>,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    tokenLabelCache: Map<String, String>
) {
    DeflectorTabBody(
        isLeft = true,
        tabIndex = 0,
        tabTitle = "◀ Deflectors",
        blueprintTabTargetState = blueprintTabTargetState,
        context = context,
        prefs = prefs,
        dynamicActionTokens = dynamicActionTokens,
        tokenLabelCache = tokenLabelCache,
        listState = listState0,
        onRefreshNeeded = onRefreshNeeded,
        toggleSection = toggleSection,
        pinnedSectionState = pinnedSection0State,
        sectionOrderStrState = sectionOrder0StrState,
        sectionTitles = sectionTitles0,
        isFlankUnifiedState = isLeftFlankUnifiedState,
        showUnifyInfoDialogState = showUnifyInfoDialogState,
        showUnifyTemplateDialogState = showUnifyTemplateDialogForLeftState,
        isCenterExpandedState = isLeftCenterExpandedState,
        isCenterGeoExpandedState = isLeftCenterGeoExpandedState,
        isUnifiedExpandedState = isLeftUnifiedExpandedState,
        isUnifiedGeoExpandedState = isLeftUnifiedGeoExpandedState,
        isUnifiedGesturesExpandedState = isLeftUnifiedGesturesExpandedState,
        isUnifiedScrubExpandedState = isLeftUnifiedScrubExpandedState,
        isTopExpandedState = isLeftTopExpandedState,
        isTopGeoExpandedState = isLeftTopGeoExpandedState,
        isTopGesturesExpandedState = isLeftTopGesturesExpandedState,
        isTopScrubExpandedState = isLeftTopScrubExpandedState,
        isBottomExpandedState = isLeftBottomExpandedState,
        isBottomGeoExpandedState = isLeftBottomGeoExpandedState,
        isBottomGesturesExpandedState = isLeftBottomGesturesExpandedState,
        isBottomScrubExpandedState = isLeftBottomScrubExpandedState
    )
}