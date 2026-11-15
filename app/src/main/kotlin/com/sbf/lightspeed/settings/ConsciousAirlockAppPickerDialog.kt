package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.LightspeedAppCategorizer
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val FULL_ALPHABET_INDEX = listOf('#') + ('A'..'Z').toList()
private val COMPACT_DISPLAY_ALPHABET = listOf('#', 'A', 'C', 'E', 'G', 'I', 'K', 'M', 'O', 'Q', 'S', 'U', 'W', 'Z')

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val isGame: Boolean = false
)

@Composable
fun ConsciousAirlockAppPickerDialog(
    context: Context,
    prefs: SharedPreferences,
    onDismissRequest: () -> Unit,
    prefKey: String = LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES,
    title: String = "Select Gated Applications",
    subtitleTemplate: (Int) -> String = { count -> "Selected: $count apps gated with 5s airlock" },
    prioritizeGames: Boolean = false,
    loaderStyle: AppPickerLoaderStyle = AppPickerLoaderStyle.AIRLOCK_SEAL,
    onSelectionChanged: ((Set<String>) -> Unit)? = null
) {
    DeckDialog(onDismissRequest = onDismissRequest) {
        ConsciousAirlockAppPickerContent(
            context = context,
            prefs = prefs,
            onDismissRequest = onDismissRequest,
            prefKey = prefKey,
            title = title,
            subtitleTemplate = subtitleTemplate,
            prioritizeGames = prioritizeGames,
            loaderStyle = loaderStyle,
            onSelectionChanged = onSelectionChanged
        )
    }
}

@Composable
fun ConsciousAirlockAppPickerContent(
    context: Context,
    prefs: SharedPreferences,
    onDismissRequest: () -> Unit,
    prefKey: String = LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES,
    title: String = "Select Gated Applications",
    subtitleTemplate: (Int) -> String = { count -> "Selected: $count apps gated with 5s airlock" },
    prioritizeGames: Boolean = false,
    loaderStyle: AppPickerLoaderStyle = AppPickerLoaderStyle.AIRLOCK_SEAL,
    onSelectionChanged: ((Set<String>) -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val view = LocalView.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        dialogWindow?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    var selectedApps by remember {
        mutableStateOf(prefs.getStringSet(prefKey, emptySet<String>()) ?: emptySet<String>())
    }
    // Always locked / collapsed at startup of the popup
    var isSelectedAccordionExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var appsList by remember { mutableStateOf<List<InstalledAppEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var activeFastScrollLetter by remember { mutableStateOf<Char?>(null) }

    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val activities = pm.queryIntentActivities(intent, 0)
            activities.mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                val pkg = appInfo.packageName ?: return@mapNotNull null
                val label = resolveInfo.loadLabel(pm)?.toString() ?: pkg
                val catOverride = prefs.getString("app_category_override_$pkg", null)
                val isGameApp = catOverride == "games" ||
                    LightspeedAppCategorizer.categorize(appInfo, pkg, label) == "games"
                InstalledAppEntry(pkg, label, isGameApp)
            }.distinctBy { it.packageName }
                .sortedBy { it.label.trim().lowercase() }
        }
        appsList = loaded
        isLoading = false
    }

    val selectedEntries = remember(appsList, selectedApps) {
        appsList.filter { it.packageName in selectedApps }
    }

    val filteredApps = remember(appsList, searchQuery) {
        if (searchQuery.isBlank()) appsList
        else {
            val q = searchQuery.trim().lowercase()
            appsList.filter { it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) }
        }
    }

    val letterIndexMap = remember(filteredApps) {
        val map = mutableMapOf<Char, Int>()
        filteredApps.forEachIndexed { idx, app ->
            val firstChar = app.label.trim().firstOrNull()?.uppercaseChar() ?: '#'
            val key = if (firstChar in 'A'..'Z') firstChar else '#'
            if (!map.containsKey(key)) {
                map[key] = idx
            }
        }
        map
    }

    Column(
        modifier = Modifier
            .padding(18.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Text(subtitleTemplate(selectedApps.size), fontSize = 10.sp, color = lsColors.textSecondary)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed apps...", fontSize = 11.sp, color = lsColors.textDisabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .focusRequester(focusRequester)
                    .clickable {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = lsColors.textDisabled, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, color = lsColors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            )

            if (isLoading) {
                val (loaderTitle, loaderSubtitle) = when (loaderStyle) {
                    AppPickerLoaderStyle.AIRLOCK_SEAL ->
                        "ASTROGATION AIRLOCK" to "Preparing gated app vault..."
                    AppPickerLoaderStyle.SOLAR_BRIGHTNESS_TRIGGER ->
                        "BRIGHTNESS TRIGGER APPS" to "Loading media auto-release list..."
                    AppPickerLoaderStyle.TELEMETRY_WAVEFORM ->
                        "CALIBRATING TELEMETRY TARGETS" to "Indexing Hz & micro-HUD candidates..."
                    AppPickerLoaderStyle.BATTERY_CELL ->
                        "CHECKING POWER PROFILES" to "Scanning battery exemption candidates..."
                    AppPickerLoaderStyle.RADAR_SCAN ->
                        "SCANNING INSTALLED APPS" to "Indexing shield candidates..."
                }
                TailoredAppPickerLoader(
                    style = loaderStyle,
                    title = loaderTitle,
                    subtitle = loaderSubtitle,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            } else {
                // Top Collapsible Selected Apps Accordion (always collapsed/locked at popup startup)
                if (selectedEntries.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(lsColors.dialogSurface.copy(alpha = 0.35f))
                            .border(0.7.dp, lsColors.accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LightspeedHapticEngine.tick(context)
                                    isSelectedAccordionExpanded = !isSelectedAccordionExpanded
                                }
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isSelectedAccordionExpanded) {
                                    "▾ SELECTED APPS (${selectedEntries.size})"
                                } else {
                                    "▸ SELECTED APPS (${selectedEntries.size})"
                                },
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accent
                            )
                            Text(
                                text = if (isSelectedAccordionExpanded) "COLLAPSE" else "EXPAND",
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = lsColors.textSecondary
                            )
                        }
                        AnimatedVisibility(
                            visible = isSelectedAccordionExpanded,
                            enter = expandVertically(animationSpec = tween(160)) + fadeIn(tween(140)),
                            exit = shrinkVertically(animationSpec = tween(140)) + fadeOut(tween(110))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 135.dp)
                                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                items(selectedEntries, key = { "sel_${it.packageName}" }) { app ->
                                    AirlockAppRow(
                                        context = context,
                                        app = app,
                                        isSelected = true,
                                        onToggle = {
                                            LightspeedHapticEngine.tick(context)
                                            val next = selectedApps - app.packageName
                                            selectedApps = next
                                            prefs.edit().putStringSet(prefKey, next).apply()
                                            onSelectionChanged?.invoke(next)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Main A-Z Application List + Lightspeed A-Z Index Fast-Scroll Bar
                val showFastScroller = searchQuery.isBlank() && filteredApps.size > 6
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(265.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(end = if (showFastScroller) 22.dp else 0.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedApps.contains(app.packageName)
                            AirlockAppRow(
                                context = context,
                                app = app,
                                isSelected = isSelected,
                                onToggle = {
                                    LightspeedHapticEngine.tick(context)
                                    val next = if (isSelected) selectedApps - app.packageName else selectedApps + app.packageName
                                    selectedApps = next
                                    prefs.edit().putStringSet(prefKey, next).apply()
                                    onSelectionChanged?.invoke(next)
                                }
                            )
                        }
                    }

                    if (showFastScroller) {
                        AirlockAlphabetFastScroller(
                            letterIndexMap = letterIndexMap,
                            totalItemCount = filteredApps.size,
                            onScrubToIndex = { targetIdx, letter ->
                                activeFastScrollLetter = letter
                                coroutineScope.launch {
                                    listState.scrollToItem(targetIdx)
                                }
                            },
                            onScrubEnd = {
                                activeFastScrollLetter = null
                            },
                            onTick = {
                                LightspeedHapticEngine.tick(context)
                            },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(20.dp)
                        )
                    }

                    val hudLetter = activeFastScrollLetter
                    if (hudLetter != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(lsColors.dialogSurface.copy(alpha = 0.94f))
                                .border(1.2.dp, lsColors.accent.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = hudLetter.toString(),
                                color = lsColors.accent,
                                fontSize = 21.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            DeckButton(
                text = "Done",
                onClick = onDismissRequest,
                modifier = Modifier.fillMaxWidth()
            )
        }
}

@Composable
private fun AirlockAlphabetFastScroller(
    letterIndexMap: Map<Char, Int>,
    totalItemCount: Int,
    onScrubToIndex: (Int, Char) -> Unit,
    onScrubEnd: () -> Unit,
    onTick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    var barHeightPx by remember { mutableFloatStateOf(1f) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val lettersAlpha by animateFloatAsState(
        targetValue = if (isDragging) 0f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "airlockScrollerLettersAlpha"
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "airlockScrollerThumbAlpha"
    )

    fun resolveLetterIndex(letter: Char): Int? {
        letterIndexMap[letter]?.let { return it }
        val pos = FULL_ALPHABET_INDEX.indexOf(letter)
        if (pos > 0) {
            for (i in (pos - 1) downTo 0) {
                letterIndexMap[FULL_ALPHABET_INDEX[i]]?.let { return it }
            }
        }
        for (i in (pos + 1) until FULL_ALPHABET_INDEX.size) {
            letterIndexMap[FULL_ALPHABET_INDEX[i]]?.let { return it }
        }
        return if (totalItemCount > 0) 0 else null
    }

    Box(
        modifier = modifier
            .onSizeChanged { barHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(letterIndexMap, totalItemCount) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isDragging = true
                    var lastLetter: Char? = null

                    fun processY(y: Float) {
                        val frac = (y / barHeightPx).coerceIn(0f, 1f)
                        dragFraction = frac
                        val idx = (frac * FULL_ALPHABET_INDEX.size).toInt().coerceIn(0, FULL_ALPHABET_INDEX.lastIndex)
                        val letter = FULL_ALPHABET_INDEX[idx]
                        if (letter != lastLetter) {
                            lastLetter = letter
                            onTick()
                            val targetIdx = resolveLetterIndex(letter) ?: 0
                            onScrubToIndex(targetIdx, letter)
                        }
                    }

                    processY(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            change.consume()
                            processY(change.position.y)
                        }
                    } while (event.changes.any { it.pressed })

                    isDragging = false
                    onScrubEnd()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (lettersAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(lettersAlpha)
                    .clip(RoundedCornerShape(8.dp))
                    .background(lsColors.dialogSurface.copy(alpha = 0.35f))
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                COMPACT_DISPLAY_ALPHABET.forEach { ch ->
                    val hasApps = letterIndexMap.containsKey(ch)
                    Text(
                        text = ch.toString(),
                        color = if (hasApps) lsColors.accent.copy(alpha = 0.9f) else lsColors.textDisabled.copy(alpha = 0.38f),
                        fontSize = 7.5.sp,
                        lineHeight = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (hasApps) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (thumbAlpha > 0.01f) {
            val density = LocalDensity.current
            val thumbHeightDp = 30.dp
            val thumbHeightPx = with(density) { thumbHeightDp.toPx() }
            val maxTravelPx = (barHeightPx - thumbHeightPx).coerceAtLeast(0f)
            val thumbOffsetYPx = (dragFraction * maxTravelPx).roundToInt()

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(12.dp)
                    .alpha(thumbAlpha),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(3.dp)
                        .clip(CircleShape)
                        .background(lsColors.accent.copy(alpha = 0.25f))
                )
                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, thumbOffsetYPx) }
                        .width(6.dp)
                        .height(thumbHeightDp)
                        .clip(CircleShape)
                        .background(lsColors.accent)
                )
            }
        }
    }
}

@Composable
private fun AirlockAppRow(
    context: Context,
    app: InstalledAppEntry,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var iconBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(app.packageName) {
        val bmp = withContext(Dispatchers.IO) {
            LightspeedIconManager.getIconBitmap(context, app.packageName, useCache = true)
        }
        iconBitmap = bmp
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 5.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        color = lsColors.textPrimary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (app.isGame) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(lsColors.accent.copy(alpha = 0.16f))
                                .border(0.5.dp, lsColors.accent.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "GAME",
                                color = lsColors.accent,
                                fontSize = 7.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = app.packageName,
                    color = lsColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Checkbox(checked = isSelected, onCheckedChange = null)
    }
}
