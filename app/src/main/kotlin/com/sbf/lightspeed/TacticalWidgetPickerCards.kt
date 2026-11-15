package com.sbf.lightspeed

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * App Header Accordion row for the Widget Selection Menu.
 * Matches CockpitGearPickerAppRows ergonomics with Pin-to-top and rotating chevron.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TacticalWidgetAppHeaderRow(
    item: WidgetPickerRowItem.AppHeader,
    context: Context,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onToggleExpand: () -> Unit,
    onLongClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val activeSnapshot = LocalDeckTheme.current
    val optical = activeSnapshot.resolveOpticalColors()
    val cardCorner = activeSnapshot.cornerRadiusDp.coerceIn(8f, 16f).dp
    val borderW = activeSnapshot.borderWidthDp.coerceIn(0.75f, 1.5f).dp
    val appIcon by produceState<Bitmap?>(initialValue = LightspeedIconManager.temporaryPickerCache[item.packageName], item.packageName) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                LightspeedIconManager.getIconBitmap(context, item.packageName)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Main Board (App Icon, Name, Pin, Count)
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = cardCorner, bottomStart = cardCorner, topEnd = 4.dp, bottomEnd = 4.dp))
                .background(
                    if (item.isPinned) LightspeedTokens.StatusGoldPinSubtle
                    else optical.panelColor.copy(alpha = 0.36f)
                )
                .border(
                    width = borderW,
                    color = if (item.isPinned) LightspeedTokens.StatusGoldPin
                    else optical.borderColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(topStart = cardCorner, bottomStart = cardCorner, topEnd = 4.dp, bottomEnd = 4.dp)
                )
                .combinedClickable(
                    onClick = onToggleExpand,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon with tactical fallback
            if (appIcon != null) {
                Image(
                    bitmap = appIcon!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(26.dp)
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .padding(end = 8.dp)
                        .background(lsColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.appName.firstOrNull()?.uppercase() ?: "?",
                        color = lsColors.textPrimary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // App Name & Subtitle
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = LightspeedTokens.StatusGoldPin,
                            modifier = Modifier
                                .size(14.dp)
                                .padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.appName,
                        fontSize = 13.5.sp,
                        fontWeight = if (item.isPinned) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (item.isPinned) LightspeedTokens.StatusGoldBright else lsColors.textPrimary,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${item.widgetCount} widget${if (item.widgetCount == 1) "" else "s"}",
                    fontSize = 10.sp,
                    color = dynamicSecondary.copy(alpha = 0.85f),
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 12.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(3.dp))

        // Accordion Chevron Toggle Button
        Box(
            modifier = Modifier
                .width(42.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = cardCorner, bottomEnd = cardCorner))
                .background(
                    if (item.isExpanded) dynamicPrimary.copy(alpha = 0.30f)
                    else optical.panelColor.copy(alpha = 0.32f)
                )
                .border(
                    width = borderW,
                    color = if (item.isExpanded) dynamicSecondary.copy(alpha = 0.70f) else optical.borderColor.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = cardCorner, bottomEnd = cardCorner)
                )
                .clickable(onClick = onToggleExpand),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = if (item.isExpanded) "Collapse" else "Expand",
                tint = if (item.isExpanded) dynamicSecondary else dynamicSecondary.copy(alpha = 0.9f),
                modifier = Modifier
                    .size(22.dp)
                    .rotate(if (item.isExpanded) 180f else 0f)
            )
        }
    }
}

/**
 * Individual Tactical Widget Card with Preview Image, Cell Dimensions, and Deploy Action.
 */
@Composable
internal fun TacticalWidgetItemCard(
    item: TacticalWidgetItem,
    appName: String,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val context = LocalContext.current
    val activeSnapshot = LocalDeckTheme.current
    val optical = activeSnapshot.resolveOpticalColors()
    val cardCorner = activeSnapshot.cornerRadiusDp.coerceIn(8f, 16f).dp
    val innerCorner = (activeSnapshot.cornerRadiusDp * 0.65f).coerceIn(6f, 12f).dp
    val borderW = activeSnapshot.borderWidthDp.coerceIn(0.75f, 1.5f).dp
    val previewState by produceState<Bitmap?>(initialValue = item.previewBitmap ?: TacticalWidgetPreviewCache.get(item.key), item.key) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) {
                TacticalWidgetPreviewCache.loadPreview(context, item.providerInfo)
            }
            if (loaded != null) {
                value = loaded
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 2.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(cardCorner))
            .background(optical.panelColor.copy(alpha = 0.32f))
            .border(borderW, optical.borderColor.copy(alpha = 0.24f), RoundedCornerShape(cardCorner))
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Widget Preview Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 150.dp)
                    .clip(RoundedCornerShape(innerCorner))
                    .background(optical.panelColor.copy(alpha = 0.22f))
                    .border(borderW, optical.borderColor.copy(alpha = 0.18f), RoundedCornerShape(innerCorner)),
                contentAlignment = Alignment.Center
            ) {
                if (previewState != null) {
                    Image(
                        bitmap = previewState!!.asImageBitmap(),
                        contentDescription = item.label,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    TacticalBlueprintPlaceholder(item = item, dynamicSecondary = dynamicSecondary)
                }
            }

            // Widget Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.textPrimary
                    )
                    if (!item.description.isNullOrBlank()) {
                        Text(
                            text = item.description,
                            fontSize = 10.5.sp,
                            color = lsColors.textSecondary.copy(alpha = 0.75f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Dimensions & Deploy Action
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Grid Dimension Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(lsColors.textPrimary.copy(alpha = 0.06f))
                            .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "${item.cellWidth} × ${item.cellHeight}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary.copy(alpha = 0.85f)
                        )
                    }

                    // Deploy Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(dynamicPrimary.copy(alpha = 0.22f))
                            .border(1.dp, dynamicPrimary.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "+ SELECT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = dynamicPrimary
                        )
                    }
                }
            }
        }
    }
}

/**
 * A-Z Alphabet Fast-Scroll Scroller rail for the Widget Selection Menu.
 * Fully responsive in both portrait and compact landscape, morphing into a dynamic
 * proportional scrollbar thumb ONLY during active dragging on the fast-scroller rail itself.
 */
@Composable
internal fun TacticalWidgetAlphabetScroller(
    flatItemsList: List<WidgetPickerRowItem>,
    letterIndices: Map<Char, Int>,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onHudStateChange: (show: Boolean, letter: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    var sideBarHeight by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var currentDragY by remember { mutableFloatStateOf(0f) }
    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(32.dp)
            .padding(start = 6.dp)
            .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(flatItemsList, letterIndices) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var hasDragged = false
                    val initialY = down.position.y.coerceIn(0f, sideBarHeight)

                    fun calculateInterpolatedIndex(yPos: Float) {
                        if (flatItemsList.isEmpty()) return
                        val ratio = (yPos / sideBarHeight).coerceIn(0f, 0.999f)
                        val letterArray = alphabet.toCharArray()
                        val fractionalIndex = ratio * letterArray.size
                        val letterIdx = fractionalIndex.toInt()
                        val fractionWithinLetter = fractionalIndex - letterIdx

                        var startListIdx = -1
                        var searchStart = letterIdx
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
                        var searchEnd = letterIdx + 1
                        while (searchEnd < letterArray.size) {
                            val l = letterArray[searchEnd]
                            if (letterIndices.containsKey(l)) {
                                endListIdx = letterIndices[l]!!
                                break
                            }
                            searchEnd++
                        }
                        if (endListIdx == -1) endListIdx = flatItemsList.size - 1

                        val finalFloatIdx = startListIdx + (fractionWithinLetter * (endListIdx - startListIdx))
                        val finalListIdx = finalFloatIdx.toInt().coerceIn(0, flatItemsList.size - 1)

                        coroutineScope.launch { listState.scrollToItem(finalListIdx) }

                        val item = flatItemsList[finalListIdx]
                        val firstChar = when (item) {
                            is WidgetPickerRowItem.AppHeader -> item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                            is WidgetPickerRowItem.WidgetItem -> item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                        }
                        val displayHudText = if (firstChar in 'A'..'Z') firstChar.toString() else "#"
                        onHudStateChange(true, displayHudText)
                    }

                    // Immediate tap feedback
                    calculateInterpolatedIndex(initialY)
                    down.consume()

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
                            onHudStateChange(false, "")
                            break
                        }
                    }
                }
            }
    ) {
        if (isDragging) {
            // Morph into a physical scrollbar track and thumb ONLY during active dragging on the fast scroller rail
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(lsColors.textPrimary.copy(alpha = 0.1f), RoundedCornerShape(2.dp))
                    .align(Alignment.Center)
            )

            val density = LocalDensity.current
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
            val availableHeightDp = with(LocalDensity.current) { sideBarHeight.toDp() }
            val isCompactVertical = availableHeightDp < 380.dp
            val displayLetters = remember(isCompactVertical, letterIndices) {
                if (!isCompactVertical) {
                    alphabet.toList()
                } else {
                    val populated = letterIndices.keys
                    val sampleAlphabet = "#ADGJMPSVYZ".toList()
                    (populated + sampleAlphabet).distinct().sorted()
                }
            }
            val fontSizeSp = if (isCompactVertical) 7.5.sp else 9.sp

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
                            color = if (hasApps) dynamicSecondary else lsColors.textPrimary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-tech tactical blueprint placeholder when a widget does not provide a preview image.
 */
@Composable
internal fun TacticalBlueprintPlaceholder(item: TacticalWidgetItem, dynamicSecondary: Color) {
    val lsColors = LightspeedStyle.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 16.dp.toPx()
            val gridColor = lsColors.textPrimary.copy(alpha = 0.04f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += step
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.appIcon != null) {
                Image(
                    bitmap = item.appIcon.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column {
                Text(
                    text = "${item.cellWidth} × ${item.cellHeight} CELLS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = dynamicSecondary
                )
                Text(
                    text = "STANDALONE WIDGET MODULE",
                    fontSize = 9.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.45f)
                )
            }
        }
    }
}
