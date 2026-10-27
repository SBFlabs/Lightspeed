package com.sbf.lightspeed

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TacticalAppWidgetGroup(
    val packageName: String,
    val appName: String,
    val appIcon: Bitmap?,
    val widgets: List<TacticalWidgetItem>
)

data class TacticalWidgetItem(
    val providerInfo: AppWidgetProviderInfo,
    val label: String,
    val description: String?,
    val cellWidth: Int,
    val cellHeight: Int,
    val previewBitmap: Bitmap?,
    val appIcon: Bitmap?
)

/**
 * Tactical In-App Widget Picker for Refueling Bay.
 * Renders an OLED spaceship cockpit modal over the lockscreen with instant search,
 * A-Z scrubber sidebar, widget preview images, and aerospace telemetry cards.
 */
@Composable
fun TacticalWidgetPickerModal(
    onDismiss: () -> Unit,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    val widgetGroups = remember { mutableStateListOf<TacticalAppWidgetGroup>() }
    var isLoading by remember { mutableStateOf(true) }

    // A-Z Scrubber Sidebar State
    var sideBarHeight by remember { mutableFloatStateOf(1f) }
    var isDraggingSideBar by remember { mutableStateOf(false) }
    var hudLetter by remember { mutableStateOf("") }

    // Load and index installed widget providers on background thread
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            val manager = AppWidgetManager.getInstance(context)
            val pm = context.packageManager
            val providers = try {
                manager.installedProviders ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }

            val grouped = providers.groupBy { it.provider.packageName }
            val list = grouped.mapNotNull { (pkg, providerList) ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val appIcon = LightspeedIconManager.getIconBitmap(context, pkg)

                    val widgetItems = providerList.map { info ->
                        val label = try {
                            info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: appName
                        } catch (_: Exception) {
                            appName
                        }

                        val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                info.loadDescription(context)?.toString()
                            } catch (_: Exception) {
                                null
                            }
                        } else null

                        val cellW = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0) {
                            info.targetCellWidth
                        } else {
                            ((info.minWidth + 30) / 70).coerceIn(1, 6)
                        }

                        val cellH = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0) {
                            info.targetCellHeight
                        } else {
                            ((info.minHeight + 30) / 70).coerceIn(1, 6)
                        }

                        val preview = try {
                            val drawable = info.loadPreviewImage(context, 0)
                            drawable?.let { d ->
                                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 360
                                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 180
                                val scale = minOf(640f / w, 360f / h, 1f)
                                val finalW = (w * scale).toInt().coerceAtLeast(1)
                                val finalH = (h * scale).toInt().coerceAtLeast(1)
                                val bmp = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bmp)
                                d.setBounds(0, 0, finalW, finalH)
                                d.draw(canvas)
                                bmp
                            }
                        } catch (_: Exception) {
                            null
                        }

                        TacticalWidgetItem(
                            providerInfo = info,
                            label = label,
                            description = desc,
                            cellWidth = cellW,
                            cellHeight = cellH,
                            previewBitmap = preview,
                            appIcon = appIcon
                        )
                    }.sortedBy { it.label.lowercase() }

                    TacticalAppWidgetGroup(
                        packageName = pkg,
                        appName = appName,
                        appIcon = appIcon,
                        widgets = widgetItems
                    )
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.appName.lowercase() }

            withContext(Dispatchers.Main) {
                widgetGroups.clear()
                widgetGroups.addAll(list)
                isLoading = false
            }
        }
    }

    // Filter by query
    val filteredGroups = remember(widgetGroups.toList(), searchQuery) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            widgetGroups.toList()
        } else {
            widgetGroups.mapNotNull { group ->
                val appMatches = group.appName.lowercase().contains(query)
                val matchingWidgets = group.widgets.filter { w ->
                    appMatches ||
                            w.label.lowercase().contains(query) ||
                            (w.description?.lowercase()?.contains(query) == true)
                }
                if (matchingWidgets.isNotEmpty()) {
                    group.copy(widgets = matchingWidgets)
                } else null
            }
        }
    }

    // Map starting letters to list indices
    val alphabet = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    val letterToGroupIndex = remember(filteredGroups) {
        val map = mutableMapOf<Char, Int>()
        filteredGroups.forEachIndexed { index, group ->
            val firstChar = group.appName.firstOrNull()?.uppercaseChar() ?: '#'
            val letterKey = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!map.containsKey(letterKey)) {
                map[letterKey] = index
            }
        }
        map
    }

    // Root Scrim & Dialog Container (OLED Space Black)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LightspeedTokens.TacticalSurfaceDeep.copy(alpha = 0.98f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .zIndex(10000f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 28.dp, top = 8.dp, bottom = 8.dp)
        ) {
            // 1. Cockpit Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = "AVIONICS // MODULE DOCK",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "SELECT COMPATIBLE WIDGET TO DEPLOY INTO REFUELING BAY",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.textPrimary.copy(alpha = 0.5f),
                        letterSpacing = 0.8.sp
                    )
                }

                // Close / Abort Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(lsColors.textPrimary.copy(alpha = 0.08f))
                        .border(1.dp, lsColors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Abort",
                        tint = lsColors.textPrimary.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 2. Tactical Search Console
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(LightspeedTokens.TacticalSurfaceCard)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )

                    BasicTextSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = "QUERY MODULE OR APPLICATION..."
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = lsColors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Main Content: Widget List + A-Z Scrubber Sidebar
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp
                        )
                        Text(
                            text = "SCANNING INSTALLED AVIONICS MODULES...",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = lsColors.textPrimary.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            } else if (filteredGroups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "NO COMPATIBLE MODULES FOUND",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.textPrimary.copy(alpha = 0.45f),
                        letterSpacing = 1.sp
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // Left: Scrollable Widget Cards
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(end = 6.dp, bottom = 16.dp)
                        ) {
                            items(filteredGroups, key = { it.packageName }) { group ->
                                TacticalAppGroupCard(
                                    group = group,
                                    onSelectProvider = onSelectProvider
                                )
                            }
                        }

                        // Right: A-Z Quick Scrubber Sidebar
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(26.dp)
                                .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                .pointerInput(filteredGroups) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        isDraggingSideBar = true
                                        val currentY = down.position.y.coerceIn(0f, sideBarHeight)
                                        val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                        val letterIdx = (ratio * (alphabet.length - 1)).toInt().coerceIn(0, alphabet.length - 1)
                                        val targetLetter = alphabet[letterIdx]
                                        hudLetter = targetLetter.toString()

                                        letterToGroupIndex[targetLetter]?.let { targetGroupIdx ->
                                            coroutineScope.launch { listState.scrollToItem(targetGroupIdx) }
                                        }
                                        down.consume()

                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val dragChange = event.changes.firstOrNull()
                                            if (dragChange != null && dragChange.pressed) {
                                                val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                val r = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                val lIdx = (r * (alphabet.length - 1)).toInt().coerceIn(0, alphabet.length - 1)
                                                val letter = alphabet[lIdx]
                                                hudLetter = letter.toString()

                                                letterToGroupIndex[letter]?.let { targetGroupIdx ->
                                                    coroutineScope.launch { listState.scrollToItem(targetGroupIdx) }
                                                }
                                                dragChange.consume()
                                            } else {
                                                isDraggingSideBar = false
                                                break
                                            }
                                        }
                                    }
                                },
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            alphabet.forEach { char ->
                                val hasApps = letterToGroupIndex.containsKey(char)
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = char.toString(),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasApps) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.2f)
                                    )
                                }
                            }
                        }
                    }

                    // Tactical HUD Letter Overlay (Center Pop-up on Scrubber Hold)
                    if (isDraggingSideBar && hudLetter.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightspeedTokens.TacticalSurfacePanel.copy(alpha = 0.94f))
                                .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hudLetter,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = lsColors.textPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * App Group Card rendering the Application Header and all its available widgets.
 */
@Composable
private fun TacticalAppGroupCard(
    group: TacticalAppWidgetGroup,
    onSelectProvider: (AppWidgetProviderInfo) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(LightspeedTokens.TacticalSurfaceDark.copy(alpha = 0.85f))
            .border(1.dp, lsColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // App Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (group.appIcon != null) {
                    Image(
                        bitmap = group.appIcon.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(lsColors.textPrimary.copy(alpha = 0.1f))
                    )
                }

                Text(
                    text = group.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.8.sp,
                    color = lsColors.textPrimary
                )
            }

            // Module Count Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${group.widgets.size.toString().padStart(2, '0')} MODULES",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Widgets Grid / Column
        group.widgets.forEach { item ->
            TacticalWidgetItemCard(
                item = item,
                onClick = { onSelectProvider(item.providerInfo) }
            )
        }
    }
}
