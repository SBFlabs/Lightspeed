package com.sbf.lightspeed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun downsamplePreservingEnds(items: List<Char>, maxCount: Int): List<Char> {
    if (items.size <= maxCount) return items
    if (maxCount <= 0) return emptyList()
    if (maxCount == 1) return listOf(items.first())
    return (0 until maxCount).map { i ->
        val idx = ((i.toFloat() / (maxCount - 1)) * (items.size - 1)).roundToInt()
        items[idx.coerceIn(0, items.lastIndex)]
    }.distinct()
}

/**
 * A-Z Alphabet Fast-Scroll Scrubber Sidebar and floating HUD letter overlay.
 */
@Composable
internal fun CockpitGearPickerAlphabetScroller(
    flatItemsList: List<PickerRowItem>,
    letterIndices: Map<Char, Int>,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    modifier: Modifier = Modifier,
    onHudStateChange: ((show: Boolean, letter: String) -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val density = LocalDensity.current
    var sideBarHeight by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var showHud by remember { mutableStateOf(false) }
    var hudLetter by remember { mutableStateOf("") }
    var currentDragY by remember { mutableFloatStateOf(0f) }

    val hasMeasuredHeight = sideBarHeight > 1f
    val availableHeightDp = if (hasMeasuredHeight) with(density) { sideBarHeight.toDp().value } else 500f
    val isCompactVertical = hasMeasuredHeight && availableHeightDp < 380f
    val displayLetters = remember(sideBarHeight, letterIndices) {
        val fullLatin = ('A'..'Z').toList()
        val nonLatinOrdered = letterIndices.keys.filter { it != '#' && it !in 'A'..'Z' }
        val maxSlots = (availableHeightDp / 10.5f).toInt().coerceAtLeast(12)
        if (!isCompactVertical) {
            if (1 + fullLatin.size + nonLatinOrdered.size <= maxSlots) {
                listOf('#') + fullLatin + nonLatinOrdered
            } else {
                listOf('#') + fullLatin + downsamplePreservingEnds(nonLatinOrdered, (maxSlots - 27).coerceAtLeast(4))
            }
        } else {
            val nonLatinQuota = if (nonLatinOrdered.isEmpty()) 0 else minOf(nonLatinOrdered.size, maxOf(3, maxSlots / 4))
            val selectedNonLatin = downsamplePreservingEnds(nonLatinOrdered, nonLatinQuota)
            val latinQuota = (maxSlots - 1 - selectedNonLatin.size).coerceAtLeast(8)
            val populatedLatin = letterIndices.keys.filter { it in 'A'..'Z' }
            val sampleLatin = "ADGJMPSVYZ".toList()
            val mergedLatin = (populatedLatin + sampleLatin).distinct().sorted()
            val selectedLatin = downsamplePreservingEnds(mergedLatin, latinQuota)
            listOf('#') + selectedLatin + selectedNonLatin
        }
    }

    fun updateHud(show: Boolean, letter: String) {
        showHud = show
        hudLetter = letter
        onHudStateChange?.invoke(show, letter)
    }

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(32.dp)
                .padding(start = 6.dp)
                .align(Alignment.CenterEnd)
                .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(flatItemsList, letterIndices, displayLetters) {
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var hasDragged = false
                            val initialY = down.position.y
                            updateHud(true, hudLetter)

                            fun calculateInterpolatedIndex(yPos: Float) {
                                val letterArray = displayLetters
                                if (flatItemsList.isEmpty() || letterArray.isEmpty()) return
                                val letterRatio = (yPos / sideBarHeight).coerceIn(0f, 0.999f)
                                val fractionalIndex = letterRatio * letterArray.size
                                val letterIndex = fractionalIndex.toInt().coerceIn(0, letterArray.lastIndex)
                                val fractionWithinLetter = fractionalIndex - letterIndex

                                var startListIdx = -1
                                var searchStart = letterIndex
                                while (searchStart >= 0) {
                                    val l = letterArray[searchStart]
                                    if (letterIndices.containsKey(l)) {
                                        startListIdx = letterIndices[l]!!
                                        break
                                    }
                                    searchStart--
                                }
                                if (startListIdx == -1) startListIdx = 0

                                var endListIdx = -1
                                var searchEnd = letterIndex + 1
                                while (searchEnd < letterArray.size) {
                                    val l = letterArray[searchEnd]
                                    if (letterIndices.containsKey(l)) {
                                        endListIdx = letterIndices[l]!!
                                        break
                                    }
                                    searchEnd++
                                }
                                if (endListIdx == -1) endListIdx = flatItemsList.size - 1
                                if (endListIdx < startListIdx) endListIdx = startListIdx

                                val finalFloatIdx = startListIdx + (fractionWithinLetter * (endListIdx - startListIdx))
                                val finalListIdx = finalFloatIdx.toInt().coerceIn(0, flatItemsList.size - 1)

                                coroutineScope.launch { listState.scrollToItem(finalListIdx) }

                                val item = flatItemsList[finalListIdx]
                                val label = when (item) {
                                    is PickerRowItem.SystemHeader -> "System Actions"
                                    is PickerRowItem.SystemCategoryHeader -> item.title
                                    is PickerRowItem.SystemAction -> item.label
                                    is PickerRowItem.SystemCustomizationOption -> item.title
                                    is PickerRowItem.SystemCustomizationSlider -> item.title
                                    is PickerRowItem.AppHeader -> item.appName
                                    is PickerRowItem.SubHeader -> item.label.substringBefore(" (")
                                    is PickerRowItem.ShortcutAction -> item.label
                                }
                                val cleanLabel = label.trimStart {
                                    it.isWhitespace() || Character.getType(it) == Character.FORMAT.toInt()
                                }
                                val displayHudText = if (cleanLabel.length >= 2) {
                                    cleanLabel.take(1).uppercase() + cleanLabel.substring(1, 2).lowercase()
                                } else {
                                    cleanLabel.uppercase()
                                }
                                updateHud(true, displayHudText)
                            }

                            // Handle immediate tap
                            calculateInterpolatedIndex(initialY.coerceIn(0f, sideBarHeight))

                            while (true) {
                                val event = awaitPointerEvent()
                                val dragChange = event.changes.firstOrNull()
                                if (dragChange != null && dragChange.pressed) {
                                    val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                    if (!hasDragged && kotlin.math.abs(dragY - initialY) > 8f) {
                                        hasDragged = true
                                        isDragging = true
                                    }

                                    if (hasDragged) {
                                        currentDragY = dragY
                                        calculateInterpolatedIndex(dragY)
                                    }
                                    dragChange.consume()
                                } else {
                                    isDragging = false
                                    updateHud(false, hudLetter)
                                    break
                                }
                            }
                        }
                    }
                }
        ) {
            if (isDragging) {
                // Morph into a physical scrollbar track and thumb
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(lsColors.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                        .align(Alignment.Center)
                )
                val thumbHeight = 32.dp
                val thumbHeightPx = with(density) { thumbHeight.toPx() }
                val maxThumbY = (sideBarHeight - thumbHeightPx).coerceAtLeast(0f)
                val thumbY = (currentDragY - thumbHeightPx / 2f).coerceIn(0f, maxThumbY)

                Box(
                    modifier = Modifier
                        .offset(y = with(density) { thumbY.toDp() })
                        .height(thumbHeight)
                        .width(6.dp)
                        .background(dynamicPrimary, RoundedCornerShape(3.dp))
                        .align(Alignment.TopCenter)
                )
            } else {
                val fontSizeSp = when {
                    isCompactVertical -> 7.5.sp
                    displayLetters.size > 34 -> 7.5.sp
                    displayLetters.size > 29 -> 8.sp
                    else -> 9.sp
                }

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    displayLetters.forEach { letter ->
                        val hasApps = letterIndices.containsKey(letter)
                        Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                            Text(
                                text = letter.toString(),
                                fontSize = fontSizeSp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasApps) dynamicSecondary else lsColors.textPrimary.copy(alpha = 0.2f),
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeight = fontSizeSp
                                )
                            )
                        }
                    }
                }
            }
        }

        // Dragging HUD Letter Overlay (only rendered here if external listener is not consuming it)
        if (onHudStateChange == null && showHud && hudLetter.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
                    .size(80.dp)
                    .background(com.sbf.lightspeed.ui.theme.LightspeedTokens.TacticalSurfaceCard.copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                    .border(2.2.dp, dynamicPrimary, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = hudLetter,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = lsColors.textPrimary
                )
            }
        }
    }
}
