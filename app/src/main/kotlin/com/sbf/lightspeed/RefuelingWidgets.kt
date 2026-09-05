package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.view.WindowManager
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
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = if (widgetLayoutMode == "smart_stack") "SMART STACK ($widgetCount)" else "ADAPTIVE GRID ($widgetCount)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Right Action Controls
        Row(
            modifier = Modifier.wrapContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Stack vs Grid)
            IconButton(
                onClick = onToggleLayoutMode,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                    contentDescription = "Switch Layout",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Edit Mode Toggle
            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Edit Widgets",
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Add Widget [+]
            IconButton(
                onClick = onAddWidget,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Widget",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Exit [✕]
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
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
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(pagerState.pageCount) {
                        detectVerticalDragGestures { change, dragAmount ->
                            change.consume()
                            if (abs(dragAmount) > 25f) {
                                val target = if (dragAmount < 0) pagerState.currentPage + 1 else pagerState.currentPage - 1
                                if (target in 0 until pagerState.pageCount) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(target) }
                                }
                            }
                        }
                    }
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

                            // Edit Overlay Controls
                            if (isEditMode) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (pageIndex > 0) {
                                        IconButton(
                                            onClick = { onReorderWidget(pageIndex, pageIndex - 1) },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    if (pageIndex < widgetIds.size - 1) {
                                        IconButton(
                                            onClick = { onReorderWidget(pageIndex, pageIndex + 1) },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveWidget(widgetId) },
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete Widget", tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
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
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = { onReorderWidget(index, index - 1) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Up", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                if (index < widgetIds.size - 1) {
                                    IconButton(
                                        onClick = { onReorderWidget(index, index + 1) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Down", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveWidget(widgetId) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.7f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Widget", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Optional Append Item: Add Widget Card in Grid
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPickWidget() }
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(18.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mount Widget Slot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
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
                    val hostView = appWidgetHost.createView(ctx, widgetId, appWidgetInfo)
                    hostView.setAppWidget(widgetId, appWidgetInfo)
                    hostView.setOnTouchListener { _, ev ->
                        if (ev.action == android.view.MotionEvent.ACTION_DOWN) {
                            try {
                                val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                                if (km?.isKeyguardLocked == true) {
                                    km.requestDismissKeyguard(activity, null)
                                }
                            } catch (_: Exception) {}
                        }
                        false
                    }
                    hostView
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
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Embed Android Widget",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "Tap to mount Weather, Music, Clock or Notes",
            fontSize = 11.sp,
            color = Color.LightGray.copy(alpha = 0.65f)
        )
    }
}
