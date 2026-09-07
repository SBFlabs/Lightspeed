package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
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
    onAddWidget: () -> Unit
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
                Text(
                    text = if (widgetLayoutMode == "smart_stack") "STACK // ${widgetCount.toString().padStart(2, '0')}" else "GRID // ${widgetCount.toString().padStart(2, '0')}",
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF070B12).copy(alpha = 0.94f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (canMoveBack) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Shift Prev",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        if (canMoveForward) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onMoveForward() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Shift Next",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .height(14.dp)
                .width(1.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )

        // Eject / Remove Button
        Row(
            modifier = Modifier
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFF3B30).copy(alpha = 0.2f))
                .border(0.8.dp, Color(0xFFFF3B30).copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .clickable { onRemove() }
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Eject",
                tint = Color(0xFFFF453A),
                modifier = Modifier.size(11.dp)
            )
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
    onReorderWidget: (Int, Int) -> Unit
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
                                    .padding(8.dp)
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
        // MODE 2: ADAPTIVE GRID (1 Column Portrait, 2 Columns Landscape)
        // =========================================================================
        val gridColumns = if (isLandscape) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(widgetIds) { index, widgetId ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                                    .padding(6.dp)
                            )
                        }

                        if (isEditMode) {
                            TacticalWidgetEditControls(
                                canMoveBack = index > 0,
                                canMoveForward = index < widgetIds.size - 1,
                                onMoveBack = { onReorderWidget(index, index - 1) },
                                onMoveForward = { onReorderWidget(index, index + 1) },
                                onRemove = { onRemoveWidget(widgetId) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }

            // Optional Append Item: Add Widget Card in Grid (Tactical Module Mount)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
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
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "[ MOUNT MODULE ]",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Tap to open tactical catalog",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom FrameLayout host container for AppWidgetHostView.
 * Intercepts touch direction to ensure scrollable collection widgets (e.g., Calendar events list,
 * agenda, email list) can freely scroll vertically without ancestor Compose gestures stealing the touch,
 * while allowing horizontal swipes to pass through to HorizontalPager.
 */
class ScrollableAppWidgetContainer(context: Context) : FrameLayout(context) {
    private var startX = 0f
    private var startY = 0f
    private var isVerticalScroll = false
    private var isHorizontalScroll = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.x
                startY = ev.y
                isVerticalScroll = false
                isHorizontalScroll = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!isVerticalScroll && !isHorizontalScroll) {
                    if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dx > dy && dx > touchSlop) {
                        isHorizontalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                } else if (isVerticalScroll) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (isHorizontalScroll) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isVerticalScroll = false
                isHorizontalScroll = false
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
    modifier: Modifier = Modifier
) {
    val appWidgetInfo = remember(widgetId) {
        try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (_: Exception) {
            null
        }
    }

    if (appWidgetInfo != null) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                try {
                    val container = ScrollableAppWidgetContainer(ctx)
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
            }
        )
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
