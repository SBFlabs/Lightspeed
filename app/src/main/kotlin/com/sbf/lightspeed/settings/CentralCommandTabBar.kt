package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.launch

/**
 * Isolated 3-tab navigator for Central Command.
 * Isolates [PagerState.currentPage] inside a derived state and glides the active pill
 * via [Modifier.drawBehind] so horizontal swipes never invalidate sibling tabs or
 * trigger layout passes across the 50% threshold.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CentralCommandTabBar(
    context: Context,
    pagerState: PagerState,
    languageMode: LightspeedLanguageEngine.LanguageMode,
    onOpenPopover: (Int) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scope = rememberCoroutineScope()
    val localeState = LightspeedLocaleEngine.state

    val selectedPage by remember(pagerState) {
        derivedStateOf { pagerState.currentPage }
    }

    var lastTapTab by remember { mutableIntStateOf(-1) }
    var lastTapTimeMs by remember { mutableLongStateOf(0L) }

    val tabTitles = remember(languageMode, localeState) {
        listOf(
            LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_LEFT, languageMode),
            LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_CENTER, languageMode),
            LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_RIGHT, languageMode)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.06f))
            .padding(4.dp)
            .drawBehind {
                val gapPx = 4.dp.toPx()
                val tabWidth = ((size.width - gapPx * 2f) / 3f).coerceAtLeast(1f)
                val rawPos = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                val effectiveIndex = if (isRtl) 2f - rawPos else rawPos
                val pillX = effectiveIndex * (tabWidth + gapPx)
                val radiusPx = 12.dp.toPx()
                drawRoundRect(
                    color = primaryColor,
                    topLeft = Offset(pillX, 0f),
                    size = Size(tabWidth, size.height),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val unselectedTextColor = lsColors.textPrimary.copy(alpha = 0.75f)
        tabTitles.forEachIndexed { index, tabTitle ->
            val isSelected = selectedPage == index
            val selectionFraction by remember(pagerState, index) {
                derivedStateOf {
                    val rawPos = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 2f)
                    (1f - kotlin.math.abs(rawPos - index)).coerceIn(0f, 1f)
                }
            }
            val tabTextColor = androidx.compose.ui.graphics.lerp(unselectedTextColor, onPrimaryColor, selectionFraction)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = {
                            val now = android.os.SystemClock.uptimeMillis()
                            val isRapidDoubleTap = (lastTapTab == index && now - lastTapTimeMs <= 420L)
                            lastTapTab = index
                            lastTapTimeMs = now
                            if (isSelected || isRapidDoubleTap) {
                                LightspeedHapticEngine.heavyClick(context)
                                onOpenPopover(index)
                            } else {
                                scope.launch { pagerState.animateScrollToPage(index) }
                            }
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            onOpenPopover(index)
                        }
                    )
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = tabTextColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 14.dp, height = 2.dp)
                            .graphicsLayer { alpha = 0.75f * selectionFraction }
                            .clip(CircleShape)
                            .background(onPrimaryColor)
                    )
                }
            }
        }
    }
}
