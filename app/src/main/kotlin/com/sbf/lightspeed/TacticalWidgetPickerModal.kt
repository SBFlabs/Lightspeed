package com.sbf.lightspeed

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.ensureReadableContrast
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val pickerHackerGlyphs = arrayOf(
    "0", "1", "2", "3", "4", "5", "6", "7",
    "8", "9", "0", "4", "7", "9", "A", "E", "F"
)

@Composable
private fun HackerWidgetLoadingRain(
    accentColor: Color,
    panelColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameMillis { frameMs = it }
        }
    }

    val glyphPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val colStep = 14f * density
            val rowStep = 16f * density
            val cols = (size.width / colStep).toInt().coerceIn(8, 36)
            val rows = (size.height / rowStep).toInt().coerceIn(6, 28)
            val gridStartX = (size.width - cols * colStep) * 0.5f + colStep * 0.5f
            val gridStartY = (size.height - rows * rowStep) * 0.5f + rowStep * 0.72f

            val accentArgb = accentColor.toArgb()
            val accentR = android.graphics.Color.red(accentArgb)
            val accentG = android.graphics.Color.green(accentArgb)
            val accentB = android.graphics.Color.blue(accentArgb)
            glyphPaint.textSize = 10.5f * density

            drawIntoCanvas { drawCanvas ->
                val native = drawCanvas.nativeCanvas
                for (c in 0 until cols) {
                    val speed = 0.0085f + ((c * 37 + 11) % 7) * 0.0022f
                    val phase = ((c * 53) % 19) * 0.63f
                    val trailLen = 4f + ((c * 19) % 5)
                    val headRow = (frameMs * speed + phase) % (rows + trailLen)
                    val flipBucket = frameMs / (90L + ((c * 29) % 6) * 28L)

                    for (r in 0 until rows) {
                        val dist = headRow - r
                        if (dist < 0f || dist > trailLen) continue
                        val gIdx = ((flipBucket + c * 17L + r * 31L).toInt() and 0x7FFFFFFF) % pickerHackerGlyphs.size
                        glyphPaint.color = if (dist < 1f) {
                            android.graphics.Color.argb(235, 245, 250, 255)
                        } else {
                            val frac = (1f - dist / trailLen).coerceIn(0f, 1f)
                            val alpha = (26 + 185 * frac * frac).toInt().coerceIn(0, 255)
                            android.graphics.Color.argb(alpha, accentR, accentG, accentB)
                        }
                        native.drawText(
                            pickerHackerGlyphs[gIdx],
                            gridStartX + c * colStep,
                            gridStartY + r * rowStep,
                            glyphPaint
                        )
                    }
                }
            }
        }

        val dotPhase = ((frameMs / 280L) % 4L).toInt()
        val statusDots = when (dotPhase) {
            1 -> "."
            2 -> ".."
            3 -> "..."
            else -> ""
        }
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(panelColor.copy(alpha = 0.88f))
                .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "SCANNING WIDGETS$statusDots",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accentColor,
                letterSpacing = 1.sp
            )
            Text(
                text = "Discovering installed widget providers",
                fontSize = 11.sp,
                color = labelColor.copy(alpha = 0.72f)
            )
        }
    }
}

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
    val appIcon: Bitmap?,
    val key: String = providerInfo.provider.flattenToShortString()
)

sealed class WidgetPickerRowItem {
    abstract val key: String

    data class AppHeader(
        val packageName: String,
        val appName: String,
        val widgetCount: Int,
        val isExpanded: Boolean,
        val isPinned: Boolean
    ) : WidgetPickerRowItem() {
        override val key: String get() = "app_header:$packageName"
    }

    data class WidgetItem(
        val item: TacticalWidgetItem,
        val appName: String
    ) : WidgetPickerRowItem() {
        override val key: String get() = "widget:${item.key}"
    }
}

object TacticalWidgetPreviewCache {
    private val memoryCache = androidx.collection.LruCache<String, Bitmap>(24)
    private var cachedGroups: List<TacticalAppWidgetGroup>? = null

    fun get(key: String): Bitmap? = synchronized(memoryCache) { memoryCache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(memoryCache) { memoryCache.put(key, bitmap) }
    }

    fun clear() {
        synchronized(memoryCache) { memoryCache.evictAll() }
        cachedGroups = null
    }

    fun getCachedGroups(): List<TacticalAppWidgetGroup>? = cachedGroups

    fun setCachedGroups(groups: List<TacticalAppWidgetGroup>) {
        cachedGroups = groups
    }

    fun loadPreview(context: Context, info: AppWidgetProviderInfo): Bitmap? {
        val key = info.provider.flattenToShortString()
        get(key)?.let { return it }
        val bmp = try {
            val drawable = info.loadPreviewImage(context, 0)
            drawable?.let { d ->
                val w = d.intrinsicWidth.takeIf { it > 0 } ?: 360
                val h = d.intrinsicHeight.takeIf { it > 0 } ?: 180
                val scale = minOf(480f / w, 260f / h, 1f)
                val finalW = (w * scale).toInt().coerceAtLeast(1)
                val finalH = (h * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                d.setBounds(0, 0, finalW, finalH)
                d.draw(canvas)
                bitmap
            }
        } catch (_: Exception) {
            null
        }
        if (bmp != null) put(key, bmp)
        return bmp
    }
}

/**
 * Tactical In-App Widget Accordion Picker with 0ms instant text load,
 * dynamic A-Z fast scroller, long-press pin-to-top, and async preview rendering.
 */
@Composable
fun TacticalWidgetAccordionPicker(
    title: String = "MODULE DOCK",
    subtitle: String = "SELECT COMPATIBLE WIDGET TO DEPLOY",
    onDismiss: () -> Unit,
    onSelectWidget: (AppWidgetProviderInfo, String, String) -> Unit,
    dynamicPrimary: Color? = null,
    dynamicSecondary: Color? = null,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    val context = LocalContext.current
    val activeSnapshot = LocalDeckTheme.current
    val optical = activeSnapshot.resolveOpticalColors()
    val defaultPrimary = optical.readableTintColor
    val defaultSecondary = ensureReadableContrast(optical.borderColor)
    val effectivePrimary = dynamicPrimary ?: defaultPrimary
    val effectiveSecondary = dynamicSecondary ?: defaultSecondary
    val searchCornerRadius = activeSnapshot.cornerRadiusDp.coerceIn(10f, 18f).dp
    val prefs = remember { context.defaultPrefs() }
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var searchQuery by remember { mutableStateOf("") }
    val cached = remember { TacticalWidgetPreviewCache.getCachedGroups() }
    val rawGroups = remember {
        mutableStateListOf<TacticalAppWidgetGroup>().apply {
            if (cached != null) addAll(cached)
        }
    }
    var isLoading by remember { mutableStateOf(cached == null) }
    var expandedApps by remember { mutableStateOf(setOf<String>()) }
    var pinnedApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_WIDGET_PICKER_PINNED_APPS, emptySet()) ?: emptySet())
    }

    var showHud by remember { mutableStateOf(false) }
    var hudLetter by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        onDispose {
            TacticalWidgetPreviewCache.clear()
        }
    }

    // Instant indexing: loads strings & dimensions asynchronously, caching for 0ms subsequent opens
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

                        val key = info.provider.flattenToShortString()
                        val preview = TacticalWidgetPreviewCache.get(key)

                        TacticalWidgetItem(
                            providerInfo = info,
                            label = label,
                            description = desc,
                            cellWidth = cellW,
                            cellHeight = cellH,
                            previewBitmap = preview,
                            appIcon = null,
                            key = key
                        )
                    }.sortedBy { it.label.lowercase() }

                    TacticalAppWidgetGroup(
                        packageName = pkg,
                        appName = appName,
                        appIcon = null,
                        widgets = widgetItems
                    )
                } catch (_: Exception) {
                    null
                }
            }.sortedBy { it.appName.lowercase() }

            TacticalWidgetPreviewCache.setCachedGroups(list)

            withContext(Dispatchers.Main) {
                rawGroups.clear()
                rawGroups.addAll(list)
                isLoading = false
            }
        }
    }

    // Filter by query and sort pinned apps to top
    val sortedAndFilteredGroups = remember(rawGroups.toList(), searchQuery, pinnedApps) {
        val query = searchQuery.trim().lowercase()
        val base = if (query.isEmpty()) {
            rawGroups.toList()
        } else {
            rawGroups.mapNotNull { group ->
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
        base.sortedWith(
            compareByDescending<TacticalAppWidgetGroup> { pinnedApps.contains(it.packageName) }
                .thenBy { it.appName.lowercase() }
        )
    }

    // Build virtual flat list for LazyColumn
    val flatItemsList = remember(sortedAndFilteredGroups, expandedApps, searchQuery, pinnedApps) {
        val isSearching = searchQuery.isNotBlank()
        val flat = mutableListOf<WidgetPickerRowItem>()

        for (group in sortedAndFilteredGroups) {
            val isPinned = pinnedApps.contains(group.packageName)
            val isExpanded = isSearching || expandedApps.contains(group.packageName)

            flat.add(
                WidgetPickerRowItem.AppHeader(
                    packageName = group.packageName,
                    appName = group.appName,
                    widgetCount = group.widgets.size,
                    isExpanded = isExpanded,
                    isPinned = isPinned
                )
            )

            if (isExpanded) {
                for (widget in group.widgets) {
                    flat.add(WidgetPickerRowItem.WidgetItem(widget, group.appName))
                }
            }
        }
        flat
    }

    // Letter indices for unpinned apps
    val letterIndices = remember(flatItemsList) {
        val map = mutableMapOf<Char, Int>()
        flatItemsList.forEachIndexed { idx, item ->
            if (item is WidgetPickerRowItem.AppHeader && !item.isPinned) {
                val firstChar = item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                val key = if (firstChar in 'A'..'Z') firstChar else '#'
                if (!map.containsKey(key)) {
                    map[key] = idx
                }
            }
        }
        map
    }

    fun togglePinApp(packageName: String, appName: String) {
        LightspeedHapticEngine.heavyClick(context)
        val nowPinned = !pinnedApps.contains(packageName)
        val newSet = if (nowPinned) pinnedApps + packageName else pinnedApps - packageName
        pinnedApps = newSet
        prefs.edit().putStringSet(LightspeedPreferences.KEY_WIDGET_PICKER_PINNED_APPS, newSet).apply()
        val toastMsg = if (nowPinned) "✦ Pinned $appName to top" else "Unpinned $appName"
        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
    }

    fun toggleExpandApp(packageName: String) {
        LightspeedHapticEngine.click(context)
        expandedApps = if (expandedApps.contains(packageName)) {
            expandedApps - packageName
        } else {
            expandedApps + packageName
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        // 1. Header Row (Matches CockpitGearPickerHeaderRow)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = effectiveSecondary
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .background(lsColors.textPrimary.copy(alpha = 0.08f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = lsColors.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // 2. Search Field (Matches CockpitGearPickerSearchBar)
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    "Search apps & widgets (${sortedAndFilteredGroups.size} apps)...",
                    color = lsColors.textPrimary.copy(alpha = 0.45f),
                    fontSize = 13.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = lsColors.textPrimary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = lsColors.textPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = optical.panelColor.copy(alpha = 0.42f),
                unfocusedContainerColor = optical.panelColor.copy(alpha = 0.28f),
                focusedTextColor = lsColors.textPrimary,
                unfocusedTextColor = lsColors.textPrimary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(searchCornerRadius),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .border(
                    width = activeSnapshot.borderWidthDp.coerceIn(0.75f, 1.5f).dp,
                    color = optical.borderColor.copy(alpha = 0.28f),
                    shape = RoundedCornerShape(searchCornerRadius)
                ),
            singleLine = true
        )

        // 3. Content Area: LazyColumn + Scroller + HUD Letter
        if (isLoading) {
            HackerWidgetLoadingRain(
                accentColor = effectivePrimary,
                panelColor = optical.panelColor,
                labelColor = lsColors.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (flatItemsList.isEmpty()) {
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
                    // Virtualized LazyColumn with app accordions and widget cards
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(end = 8.dp, bottom = 16.dp)
                    ) {
                        items(items = flatItemsList, key = { it.key }) { rowItem ->
                            when (rowItem) {
                                is WidgetPickerRowItem.AppHeader -> {
                                    TacticalWidgetAppHeaderRow(
                                        item = rowItem,
                                        context = context,
                                        dynamicPrimary = effectivePrimary,
                                        dynamicSecondary = effectiveSecondary,
                                        onToggleExpand = { toggleExpandApp(rowItem.packageName) },
                                        onLongClick = { togglePinApp(rowItem.packageName, rowItem.appName) }
                                    )
                                }
                                is WidgetPickerRowItem.WidgetItem -> {
                                    TacticalWidgetItemCard(
                                        item = rowItem.item,
                                        appName = rowItem.appName,
                                        dynamicPrimary = effectivePrimary,
                                        dynamicSecondary = effectiveSecondary,
                                        onClick = {
                                            onSelectWidget(rowItem.item.providerInfo, rowItem.item.label, rowItem.appName)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // A-Z Dynamic Alphabet Scroller (morphs into scrollbar thumb only when actively dragging on the rail)
                    TacticalWidgetAlphabetScroller(
                        flatItemsList = flatItemsList,
                        letterIndices = letterIndices,
                        listState = listState,
                        coroutineScope = coroutineScope,
                        dynamicPrimary = effectivePrimary,
                        dynamicSecondary = effectiveSecondary,
                        onHudStateChange = { show, letter ->
                            showHud = show
                            hudLetter = letter
                        }
                    )
                }

                // Top-Centered Floating HUD Letter Pop-up Bubble (matching CockpitGearPicker)
                if (showHud && hudLetter.isNotEmpty()) {
                    val hudRadius = activeSnapshot.cornerRadiusDp.coerceIn(10f, 18f).dp
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 48.dp)
                            .size(80.dp)
                            .clip(RoundedCornerShape(hudRadius))
                            .background(optical.panelColor.copy(alpha = 0.94f))
                            .border(2.dp, effectivePrimary, RoundedCornerShape(hudRadius)),
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
    }
}

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
    val activeSnapshot = LocalDeckTheme.current
    val backdropDim = activeSnapshot.dimAmount.coerceIn(0.32f, 0.72f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropDim))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        DeckGlassCardSurface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clickable(enabled = false, onClick = {})
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                TacticalWidgetAccordionPicker(
                    title = "REFUELING DOCK",
                    subtitle = "SELECT COMPATIBLE WIDGET TO DEPLOY",
                    onDismiss = onDismiss,
                    onSelectWidget = { providerInfo, _, _ ->
                        onSelectProvider(providerInfo)
                    }
                )
            }
        }
    }
}
