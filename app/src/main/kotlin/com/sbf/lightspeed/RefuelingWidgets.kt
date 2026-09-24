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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import com.sbf.lightspeed.system.defaultPrefs
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
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

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
    onReconfigureWidget: ((Int) -> Unit)? = null,
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
        val prefs = remember { activity.defaultPrefs() }
        val rememberPage = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE, false)

        LaunchedEffect(widgetIds.size, rememberPage) {
            if (widgetIds.isNotEmpty()) {
                val target = if (rememberPage) {
                    val lastIndex = prefs.getInt(LightspeedPreferences.KEY_REFUELING_STACK_LAST_PAGE, 0)
                    lastIndex.coerceIn(0, widgetIds.size - 1)
                } else {
                    0
                }
                pagerState.scrollToPage(target)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (rememberPage) {
                prefs.edit().putInt(LightspeedPreferences.KEY_REFUELING_STACK_LAST_PAGE, pagerState.currentPage).apply()
            }
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
                                val hasConfig = remember(widgetId) {
                                    appWidgetManager?.getAppWidgetInfo(widgetId)?.configure != null
                                }
                                TacticalWidgetEditControls(
                                    canMoveBack = pageIndex > 0,
                                    canMoveForward = pageIndex < widgetIds.size - 1,
                                    onMoveBack = { onReorderWidget(pageIndex, pageIndex - 1) },
                                    onMoveForward = { onReorderWidget(pageIndex, pageIndex + 1) },
                                    onRemove = { onRemoveWidget(widgetId) },
                                    onConfigure = if (hasConfig) {
                                        { onReconfigureWidget?.invoke(widgetId) }
                                    } else null,
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
        val prefs = activity.defaultPrefs()
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
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White, modifier = Modifier.size(11.dp))
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
                                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White, modifier = Modifier.size(11.dp))
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
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White) }
                                                
                                                Text("X:${currentWidthPct}%", color = Color.Magenta, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                
                                                IconButton(onClick = { 
                                                    val newW = (currentWidthPct + 10).coerceAtMost(100)
                                                    prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                                    currentWidthPct = newW
                                                }, modifier = Modifier.size(26.dp)) { androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }
                                            }
                                        }
                                    }

                                    val hasConfig = remember(widgetId) {
                                        appWidgetManager?.getAppWidgetInfo(widgetId)?.configure != null
                                    }
                                    TacticalWidgetEditControls(
                                        canMoveBack = index > 0,
                                        canMoveForward = index < widgetIds.size - 1,
                                        onMoveBack = { onReorderWidget(index, index - 1) },
                                        onMoveForward = { onReorderWidget(index, index + 1) },
                                        onRemove = { onRemoveWidget(widgetId) },
                                        onConfigure = if (hasConfig) {
                                            { onReconfigureWidget?.invoke(widgetId) }
                                        } else null,
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
