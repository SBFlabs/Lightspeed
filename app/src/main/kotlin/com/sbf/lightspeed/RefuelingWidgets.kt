package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun WidgetEngineToolbar(
    widgetCount: Int,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAddWidget: () -> Unit,
    isLandscape: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Cockpit Telemetry Chip
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF070A10).copy(alpha = 0.85f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                val modeLabel = if (widgetLayoutMode == "smart_stack") {
                    "STACK // ${widgetCount.toString().padStart(2, '0')}"
                } else {
                    val orientTag = if (isLandscape) "LAND" else "PORT"
                    "GRID [$orientTag] // ${widgetCount.toString().padStart(2, '0')}"
                }
                Text(
                    text = modeLabel,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Right Cockpit Action Modules (NO [X] EXIT BUTTON)
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Stack vs Grid)
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0E131C).copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                    .clickable { onToggleLayoutMode() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                    contentDescription = "Switch Layout",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (widgetLayoutMode == "smart_stack") "GRID" else "STACK",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Edit / Configure Mode Toggle
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else Color(0xFF0E131C).copy(alpha = 0.85f)
                    )
                    .border(
                        1.dp,
                        if (isEditMode) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onToggleEditMode() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Done else Icons.Default.Tune,
                    contentDescription = "Edit Widgets",
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (isEditMode) "LOCK" else "CONFIG",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
                )
            }

            // Add Widget Module [+ MODULE]
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .clickable { onAddWidget() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Widget",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "MODULE",
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Integrated Tactical HUD badge for reordering and ejecting widget modules in Edit Mode.
 */
@Composable
private fun TacticalWidgetEditControls(
    canMoveBack: Boolean,
    canMoveForward: Boolean,
    onMoveBack: () -> Unit,
    onMoveForward: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val btnSize = if (isCompact) 20.dp else 26.dp
    val iconSize = if (isCompact) 11.dp else 14.dp

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B12).copy(alpha = 0.96f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.50f), RoundedCornerShape(6.dp))
            .padding(horizontal = if (isCompact) 3.dp else 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 2.dp else 4.dp)
    ) {
        if (canMoveBack) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Shift Prev",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        if (canMoveForward) {
            Box(
                modifier = Modifier
                    .size(btnSize)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveForward() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Shift Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(iconSize)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(if (isCompact) 12.dp else 14.dp)
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.25f))
        )

        // Eject / Remove Button
        Row(
            modifier = Modifier
                .height(btnSize)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFF3B30).copy(alpha = 0.22f))
                .border(0.8.dp, Color(0xFFFF3B30).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clickable { onRemove() }
                .padding(horizontal = if (isCompact) 5.dp else 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Eject",
                tint = Color(0xFFFF453A),
                modifier = Modifier.size(iconSize)
            )
            if (!isCompact) {
                Text(
                    text = "EJECT",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF453A)
                )
            }
        }
    }
}

@Composable
fun MultiWidgetContainer(
    activity: Activity,
    widgetIds: List<Int>,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    isLandscape: Boolean,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onReorderWidget: (Int, Int) -> Unit,
    reorderVersion: Int = 0
) {
    if (widgetIds.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp)
        ) {
            EmptyWidgetSlot(onPickWidget = onPickWidget)
        }
        return
    }

    if (widgetLayoutMode == "smart_stack") {
        // =========================================================================
        // MODE 1: SMART STACK (SWIPEABLE PAGER WITH 3D CUBE & 2D NAVIGATION)
        // =========================================================================
        val pagerState = rememberPagerState(pageCount = { widgetIds.size })
        val coroutineScope = rememberCoroutineScope()

        LaunchedEffect(widgetIds.size) {
            if (widgetIds.isNotEmpty()) {
                val lastIndex = android.preference.PreferenceManager.getDefaultSharedPreferences(activity).getInt("refueling_stack_memory", widgetIds.size - 1)
                val target = lastIndex.coerceIn(0, widgetIds.size - 1)
                pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            android.preference.PreferenceManager.getDefaultSharedPreferences(activity).edit().putInt("refueling_stack_memory", pagerState.currentPage).apply()
        }

        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val widgetId = widgetIds.getOrNull(pageIndex)
                    if (widgetId != null && appWidgetHost != null && appWidgetManager != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val pageOffset = (pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction
                                    transformOrigin = TransformOrigin(if (pageOffset > 0) 0f else 1f, 0.5f)
                                    rotationY = pageOffset * 90f
                                    alpha = 1f - abs(pageOffset).coerceIn(0f, 0.5f)
                                }
                        ) {
                            AppWidgetContainerView(
                                activity = activity,
                                widgetId = widgetId,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                isStackMode = true,
                                onSwipeDelta = { deltaX ->
                                    try {
                                        pagerState.dispatchRawDelta(-deltaX)
                                    } catch (_: Exception) {}
                                },
                                onSwipeEnd = { totalDx, xVel ->
                                    val pageCount = widgetIds.size
                                    val currentPage = pagerState.currentPage
                                    val threshold = 70f
                                    val targetPage = when {
                                        xVel < -800f || totalDx < -threshold -> (currentPage + 1).coerceAtMost(pageCount - 1)
                                        xVel > 800f || totalDx > threshold -> (currentPage - 1).coerceAtLeast(0)
                                        else -> currentPage
                                    }
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(targetPage)
                                    }
                                }
                            )

                            // Edit Overlay Controls (Tactical HUD Avionics Badge)
                            if (isEditMode) {
                                TacticalWidgetEditControls(
                                    canMoveBack = pageIndex > 0,
                                    canMoveForward = pageIndex < widgetIds.size - 1,
                                    onMoveBack = { onReorderWidget(pageIndex, pageIndex - 1) },
                                    onMoveForward = { onReorderWidget(pageIndex, pageIndex + 1) },
                                    onRemove = { onRemoveWidget(widgetId) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // Subtle Pager Indicator Dots at Bottom
                if (widgetIds.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(widgetIds.size) { index ->
                            val isActive = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 12.dp else 5.dp, 5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // MODE 2: CUSTOM DIMENSIONAL FLOW DASHBOARD (True Freeform X/Y Authority)
        // =========================================================================
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(activity)
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val spacing = 8.dp

                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    verticalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    widgetIds.forEachIndexed { index, widgetId ->
                        key(reorderVersion, widgetId) {
                            val orientationPrefix = if (isLandscape) "land" else "port"
                            val defaultHeight = if (isLandscape) 160 else 180
                            val defaultWidth = if (isLandscape) 50 else 100
                            val wHeight = prefs.getInt("widget_height_${orientationPrefix}_$widgetId", defaultHeight)
                            val wWidthPct = prefs.getInt("widget_width_pct_${orientationPrefix}_$widgetId", defaultWidth)
                            
                            var currentWidthPct by remember(widgetId, isLandscape) { mutableIntStateOf(wWidthPct) }
                            var currentHeight by remember(widgetId, isLandscape) { mutableIntStateOf(wHeight) }
                            val isCompact = currentWidthPct < 50
                            
                            val calculatedWidth = if (currentWidthPct >= 100) {
                                availableWidth
                            } else {
                                val maxCols = (100 / currentWidthPct).coerceAtLeast(1)
                                val totalGaps = spacing * (maxCols - 1)
                                (((availableWidth - totalGaps) * (currentWidthPct / 100f)) - 1.dp).coerceAtLeast(60.dp)
                            }

                            Card(
                                modifier = Modifier
                                    .width(calculatedWidth)
                                    .height(currentHeight.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(
                                    1.dp,
                                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))),
                                    RoundedCornerShape(18.dp)
                                ),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                if (appWidgetHost != null && appWidgetManager != null) {
                                    AppWidgetContainerView(
                                        activity = activity,
                                        widgetId = widgetId,
                                        appWidgetHost = appWidgetHost,
                                        appWidgetManager = appWidgetManager,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(6.dp),
                                        isEditMode = isEditMode,
                                        isStackMode = false
                                    )
                                }

                                if (isEditMode) {
                                    // X/Y Axis Authority Controls
                                    if (isCompact) {
                                        // 2-Row Responsive Stack for narrow widgets (< 50% width)
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 3.dp)
                                                .background(Color.Black.copy(alpha = 0.90f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(1.dp)
                                        ) {
                                            // Y Height Row
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { 
                                                        val newH = (currentHeight - 20).coerceAtLeast(80)
                                                        prefs.edit().putInt("widget_height_${orientationPrefix}_$widgetId", newH).apply()
                                                        currentHeight = newH
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease Height", tint = Color.White, modifier = Modifier.size(11.dp))
                                                }
                                                Text("Y:${currentHeight}", color = Color.Cyan, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { 
                                                        val newH = (currentHeight + 20).coerceAtMost(600)
                                                        prefs.edit().putInt("widget_height_${orientationPrefix}_$widgetId", newH).apply()
                                                        currentHeight = newH
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase Height", tint = Color.White, modifier = Modifier.size(11.dp))
                                                }
                                            }
                                            // X Width Row
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                IconButton(
                                                    onClick = { 
                                                        val newW = (currentWidthPct - 10).coerceAtLeast(20)
                                                        prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                                        currentWidthPct = newW
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White, modifier = Modifier.size(11.dp))
                                                }
                                                Text("X:${currentWidthPct}%", color = Color.Magenta, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                                                IconButton(
                                                    onClick = { 
                                                        val newW = (currentWidthPct + 10).coerceAtMost(100)
                                                        prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                                        currentWidthPct = newW
                                                    },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White, modifier = Modifier.size(11.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        // Standard Wide Row (>= 50% width)
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 6.dp)
                                                .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                // Y Axis (Height)
                                                IconButton(onClick = { 
                                                    val newH = (currentHeight - 20).coerceAtLeast(80)
                                                    prefs.edit().putInt("widget_height_${orientationPrefix}_$widgetId", newH).apply()
                                                    currentHeight = newH
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.Default.Remove, contentDescription = "Decrease Height", tint = Color.White) }
                                                
                                                Text("Y:${currentHeight}", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                
                                                IconButton(onClick = { 
                                                    val newH = (currentHeight + 20).coerceAtMost(600)
                                                    prefs.edit().putInt("widget_height_${orientationPrefix}_$widgetId", newH).apply()
                                                    currentHeight = newH
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Increase Height", tint = Color.White) }
                                                
                                                Spacer(modifier = Modifier.width(4.dp))
                                                
                                                // X Axis (Width Pct)
                                                IconButton(onClick = { 
                                                    val newW = (currentWidthPct - 10).coerceAtLeast(20)
                                                    prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                                    currentWidthPct = newW
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White) }
                                                
                                                Text("X:${currentWidthPct}%", color = Color.Magenta, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                
                                                IconButton(onClick = { 
                                                    val newW = (currentWidthPct + 10).coerceAtMost(100)
                                                    prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                                    currentWidthPct = newW
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }
                                            }
                                        }
                                    }

                                    TacticalWidgetEditControls(
                                        canMoveBack = index > 0,
                                        canMoveForward = index < widgetIds.size - 1,
                                        onMoveBack = { onReorderWidget(index, index - 1) },
                                        onMoveForward = { onReorderWidget(index, index + 1) },
                                        onRemove = { onRemoveWidget(widgetId) },
                                        isCompact = isCompact,
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(if (isCompact) 3.dp else 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Add Widget Card in Dashboard (Tactical Module Mount) - Fits naturally in flow row
                if (isEditMode || widgetIds.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPickWidget() }
                            .background(Color(0xFF080C14).copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "[ MOUNT MODULE ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
}

/**
 * Custom FrameLayout host container for AppWidgetHostView.
 * Intercepts touch direction with smart X/Y axis discrimination:
 * - In Stack mode (HorizontalPager): allows horizontal swipes to pass through to the stack
 *   pager if the widget does not support X-axis scrolling, while reserving Y-axis gestures
 *   for vertical widget collection scrolling (e.g., Calendar events, email lists, tasks).
 * - In Grid mode: allows vertical scrolls to pass through to the dashboard column if the widget
 *   does not support Y-axis scrolling.
 */
class ScrollableAppWidgetContainer(
    context: Context,
    var isStackMode: Boolean = true,
    var onSwipeDelta: ((Float) -> Unit)? = null,
    var onSwipeEnd: ((Float, Float) -> Unit)? = null
) : FrameLayout(context) {
    private var startX = 0f
    private var startY = 0f
    private var lastX = 0f
    private var isDraggingStack = false
    private var isVerticalScroll = false
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private fun hasHorizontalScrollableChild(v: View): Boolean {
        // Only consider scrollable if the view actually has horizontal overflow
        if (v.canScrollHorizontally(1) || v.canScrollHorizontally(-1)) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (hasHorizontalScrollableChild(v.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun hasVerticalScrollableChild(v: View): Boolean {
        // Dynamically checks if view actually has scrollable content.
        // If Anki or Calendar has only 1-2 items that all fit on screen, this returns false!
        if (v.canScrollVertically(1) || v.canScrollVertically(-1)) return true
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                if (hasVerticalScrollableChild(v.getChildAt(i))) return true
            }
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isStackMode || onSwipeDelta == null) {
            return handleGridModeDispatch(ev)
        }

        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(ev)

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                lastX = ev.x
                isDraggingStack = false
                isVerticalScroll = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)

                if (!isDraggingStack && !isVerticalScroll) {
                    if (dx > dy && dx > touchSlop) {
                        // Horizontal Drag Detected across widget
                        if (!hasHorizontalScrollableChild(this)) {
                            isDraggingStack = true
                            parent?.requestDisallowInterceptTouchEvent(true)

                            // Cancel touch in child views (like ListView) to release pressed states
                            val cancelEvent = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                    } else if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        if (hasVerticalScrollableChild(this)) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }

                if (isDraggingStack) {
                    val deltaX = ev.x - lastX
                    lastX = ev.x
                    onSwipeDelta?.invoke(deltaX)
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDraggingStack) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVel = velocityTracker?.xVelocity ?: 0f
                    val totalDx = ev.x - startX
                    onSwipeEnd?.invoke(totalDx, xVel)
                    isDraggingStack = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                    return true
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDraggingStack) {
                    onSwipeEnd?.invoke(0f, 0f)
                    isDraggingStack = false
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun handleGridModeDispatch(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isVerticalScroll = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!isVerticalScroll) {
                    if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        val canScrollY = hasVerticalScrollableChild(this)
                        parent?.requestDisallowInterceptTouchEvent(canScrollY)
                    } else if (dx > dy && dx > touchSlop) {
                        val canScrollX = hasHorizontalScrollableChild(this)
                        parent?.requestDisallowInterceptTouchEvent(canScrollX)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isVerticalScroll = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}

@Composable
fun AppWidgetContainerView(
    activity: Activity,
    widgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    modifier: Modifier = Modifier,
    isEditMode: Boolean = false,
    isStackMode: Boolean = true,
    onSwipeDelta: ((Float) -> Unit)? = null,
    onSwipeEnd: ((Float, Float) -> Unit)? = null
) {
    key(widgetId) {
        val appWidgetInfo = remember(widgetId) {
            try {
                appWidgetManager.getAppWidgetInfo(widgetId)
            } catch (_: Exception) {
                null
            }
        }

        if (appWidgetInfo != null) {
            Box(modifier = modifier) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        try {
                            val container = ScrollableAppWidgetContainer(
                                ctx,
                                isStackMode = isStackMode,
                                onSwipeDelta = onSwipeDelta,
                                onSwipeEnd = onSwipeEnd
                            )
                            val hostView = appWidgetHost.createView(ctx, widgetId, appWidgetInfo)
                            hostView.setAppWidget(widgetId, appWidgetInfo)
                            container.addView(
                                hostView,
                                FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.MATCH_PARENT,
                                    FrameLayout.LayoutParams.MATCH_PARENT
                                )
                            )
                            container
                        } catch (e: Exception) {
                            android.widget.TextView(ctx).apply {
                                text = "Widget Error"
                                setTextColor(android.graphics.Color.WHITE)
                            }
                        }
                    },
                    update = { view ->
                        if (view is ScrollableAppWidgetContainer) {
                            view.isStackMode = isStackMode
                            view.onSwipeDelta = onSwipeDelta
                            view.onSwipeEnd = onSwipeEnd
                        }
                    }
                )

                if (isEditMode) {
                    // Transparent shield in edit mode so tapping near edit buttons doesn't trigger widget actions
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { /* Consume widget touch in edit mode */ }
                    )
                }
            }
        } else {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Text(
                    text = "Widget Unavailable (ID: $widgetId)",
                    color = Color.LightGray.copy(alpha = 0.6f),
                    fontSize = 11.5.sp
                )
            }
        }
    }
}

@Composable
fun InfinixStandbyWarningBanner(
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.75f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OEM Standby Conflict",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Infinix XOS Standby Style may conflict with Refueling Bay. Disable in System Settings -> Special Function -> Standby Style.",
                    fontSize = 10.5.sp,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "OPEN SETTINGS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            try {
                                val intent = android.content.Intent("com.transsion.specialfunction.ACTION_STANDBY").apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    })
                                } catch (_: Exception) {}
                            }
                        }
                        .padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyWidgetSlot(onPickWidget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onPickWidget() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AddCircleOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "[ MOUNT AVIONICS MODULE ]",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.2.sp,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Tap to browse tactical widget catalog",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.LightGray.copy(alpha = 0.65f)
        )
    }
}
