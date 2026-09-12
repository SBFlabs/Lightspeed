package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlin.math.roundToInt



@Composable
fun PrefToggleRow(
    title: String,
    subtitle: String = "",
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .clickable { onCheckedChange(!isChecked) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
            if (subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f))
            }
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = Color.White.copy(alpha = 0.75f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                uncheckedBorderColor = Color.White.copy(alpha = 0.25f)
            )
        )
    }
}

@Composable
fun PrefToggleRow(
    prefs: SharedPreferences,
    prefKey: String,
    defaultVal: Boolean,
    title: String,
    subtitle: String = "",
    onChanged: ((Boolean) -> Unit)? = null
) {
    var checked by remember(prefKey) { mutableStateOf(prefs.getBoolean(prefKey, defaultVal)) }

    DisposableEffect(prefs, prefKey) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == prefKey) {
                checked = prefs.getBoolean(prefKey, defaultVal)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    PrefToggleRow(
        title = title,
        subtitle = subtitle,
        isChecked = checked,
        onCheckedChange = { newValue ->
            checked = newValue
            prefs.edit().putBoolean(prefKey, newValue).apply()
            try {
                com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
            } catch (_: Exception) {}
            onChanged?.invoke(newValue)
        }
    )
}

@Composable
fun PrefToggleRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    titleResName: String,
    summaryResName: String,
    defaultTitle: String,
    defaultSummary: String,
    defaultVal: Boolean = false
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    val summary = remember(summaryResName) { resStr(context, summaryResName, defaultSummary) }

    PrefToggleRow(
        prefs = prefs,
        prefKey = key,
        defaultVal = defaultVal,
        title = title,
        subtitle = summary
    )
}

object SliderPresetManager {
    data class PresetItem(
        val raw: String,
        val label: String?,
        val valueStr: String
    ) {
        val displayTitle: String
            get() = if (!label.isNullOrBlank()) label else valueStr
    }

    fun getPresets(prefs: SharedPreferences, sliderKey: String): List<PresetItem> {
        val raw = prefs.getString("pref_slider_presets_$sliderKey", null) ?: return emptyList()
        return raw.split(";;").mapNotNull { entry ->
            val clean = entry.trim()
            if (clean.isEmpty()) null
            else {
                val parts = clean.split("::")
                if (parts.size >= 2) {
                    PresetItem(clean, parts[0].trim(), parts[1].trim())
                } else {
                    PresetItem(clean, null, clean)
                }
            }
        }
    }

    fun addPreset(prefs: SharedPreferences, sliderKey: String, label: String?, valueStr: String): Boolean {
        val current = getPresets(prefs, sliderKey).toMutableList()
        if (current.any { it.valueStr.trim() == valueStr.trim() }) {
            return false
        }
        val formatted = if (label.isNullOrBlank()) valueStr.trim() else "${label.trim()}::${valueStr.trim()}"
        val newRawList = mutableListOf(formatted)
        newRawList.addAll(current.map { it.raw })
        prefs.edit().putString("pref_slider_presets_$sliderKey", newRawList.joinToString(";;")).apply()
        return true
    }

    fun updatePresetLabel(prefs: SharedPreferences, sliderKey: String, oldItem: PresetItem, newLabel: String) {
        val current = getPresets(prefs, sliderKey).toMutableList()
        val index = current.indexOfFirst { it.raw == oldItem.raw }
        val formatted = if (newLabel.isBlank()) oldItem.valueStr else "${newLabel.trim()}::${oldItem.valueStr}"
        if (index != -1) {
            current[index] = PresetItem(formatted, if (newLabel.isBlank()) null else newLabel.trim(), oldItem.valueStr)
        }
        prefs.edit().putString("pref_slider_presets_$sliderKey", current.map { it.raw }.joinToString(";;")).apply()
    }

    fun removePreset(prefs: SharedPreferences, sliderKey: String, item: PresetItem) {
        val current = getPresets(prefs, sliderKey).toMutableList()
        current.removeAll { it.raw == item.raw }
        prefs.edit().putString("pref_slider_presets_$sliderKey", current.map { it.raw }.joinToString(";;")).apply()
    }
}

@Composable
fun DragOnlySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tapToJump: Boolean = false
) {
    val context = LocalContext.current
    val range = valueRange.endInclusive - valueRange.start
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var lastHapticSteppedVal by remember { mutableFloatStateOf(value) }
    val currentVal by androidx.compose.runtime.rememberUpdatedState(value)

    LaunchedEffect(value) {
        if (!isDragging) {
            dragProgress = if (range > 0f) ((currentVal - valueRange.start) / range).coerceIn(0f, 1f) else 0f
            lastHapticSteppedVal = currentVal
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(enabled, valueRange, steps, tapToJump) {
                if (!enabled || !tapToJump) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        val totalW = size.width.toFloat()
                        val thumbRadiusPx = 14.dp.toPx()
                        val usableWidth = (totalW - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        val clickProgress = ((offset.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                        val rawValue = valueRange.start + clickProgress * range
                        val steppedValue = if (steps > 0) {
                            val stepSize = range / (steps + 1)
                            (kotlin.math.round((rawValue - valueRange.start) / stepSize) * stepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)
                        } else {
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        }
                        if (steppedValue != lastHapticSteppedVal) {
                            LightspeedHapticEngine.scrubTick(context)
                            lastHapticSteppedVal = steppedValue
                        }
                        onValueChange(steppedValue)
                        onValueChangeFinished?.invoke()
                    }
                )
            }
            .pointerInput(enabled, valueRange, steps) {
                if (!enabled) return@pointerInput
                var accumulatedY = 0f
                detectDragGestures(
                    onDragStart = { _ ->
                        isDragging = true
                        accumulatedY = 0f
                        dragProgress = if (range > 0f) ((currentVal - valueRange.start) / range).coerceIn(0f, 1f) else 0f
                        lastHapticSteppedVal = currentVal
                    },
                    onDragEnd = {
                        onValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onDragCancel = {
                        onValueChangeFinished?.invoke()
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedY += dragAmount.y
                        val totalW = size.width.toFloat()
                        val thumbRadiusPx = 14.dp.toPx()
                        val usableWidth = (totalW - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        
                        // Precision Scrubbing: For every 75px moved vertically, slider speed halves
                        val precisionScale = 1f / (1f + (kotlin.math.abs(accumulatedY) / 75f))
                        val deltaProgress = (dragAmount.x / usableWidth) * precisionScale
                        
                        dragProgress = (dragProgress + deltaProgress).coerceIn(0f, 1f)
                        val rawValue = valueRange.start + dragProgress * range
                        val steppedValue = if (steps > 0) {
                            val normalStepSize = range / (steps + 1)
                            val isPrecisionScrubbing = kotlin.math.abs(accumulatedY) > 40f
                            val activeStepSize = if (isPrecisionScrubbing) normalStepSize / 10f else normalStepSize
                            (kotlin.math.round((rawValue - valueRange.start) / activeStepSize) * activeStepSize + valueRange.start).coerceIn(valueRange.start, valueRange.endInclusive)
                        } else {
                            rawValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        }
                        if (steppedValue != lastHapticSteppedVal) {
                            LightspeedHapticEngine.scrubTick(context)
                            lastHapticSteppedVal = steppedValue
                        }
                        onValueChange(steppedValue)
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val currentProgress = if (isDragging) dragProgress else {
            if (range > 0f) ((value - valueRange.start) / range).coerceIn(0f, 1f) else 0f
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val inactiveColor = primaryColor.copy(alpha = 0.18f)

        // 1. Inactive Track (Full Width)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(inactiveColor)
        )

        // 2. Active Track
        Box(
            modifier = Modifier
                .fillMaxWidth(currentProgress.coerceIn(0.005f, 1f))
                .height(6.dp)
                .clip(CircleShape)
                .background(primaryColor)
        )

        // 3. Ticks
        if (steps in 1..30) {
            val stepFraction = 1f / (steps + 1)
            for (i in 1..steps) {
                val tickPos = i * stepFraction
                Box(
                    modifier = Modifier
                        .fillMaxWidth(tickPos)
                        .wrapContentWidth(Alignment.End)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (tickPos <= currentProgress) Color.White.copy(alpha = 0.8f)
                                else primaryColor.copy(alpha = 0.4f)
                            )
                    )
                }
            }
        }

        // 4. M3 Liquid-Glass Reactive Thumb Indicator
        val thumbWidth by animateDpAsState(
            targetValue = if (isDragging) 28.dp else 18.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "thumbWidth"
        )
        val thumbHeight by animateDpAsState(
            targetValue = if (isDragging) 20.dp else 18.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "thumbHeight"
        )
        val thumbFill by animateColorAsState(
            targetValue = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
            label = "thumbFill"
        )
        val thumbBorderColor by animateColorAsState(
            targetValue = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            label = "thumbBorder"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(currentProgress.coerceIn(0f, 1f))
                .wrapContentWidth(Alignment.End)
        ) {
            Surface(
                modifier = Modifier
                    .size(width = thumbWidth, height = thumbHeight)
                    .clip(RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                color = thumbFill,
                shadowElevation = if (isDragging) 8.dp else 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, thumbBorderColor)
            ) {
                if (isDragging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 4.dp, height = 10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrefDottedSliderRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    titleResName: String,
    defaultTitle: String,
    minVal: Int,
    maxVal: Int,
    step: Int = 1,
    defaultVal: Int
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    val title = remember(titleResName) { resStr(context, titleResName, defaultTitle) }
    var value by remember { mutableStateOf(prefs.getInt(key, defaultVal)) }
    val steps = remember(minVal, maxVal, step) { ((maxVal - minVal) / step) - 1 }
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$key", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)

            // Interactive Tactile Value Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            isFlyoutOpen = true
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            isFlyoutOpen = true
                        }
                    )
            ) {
                Text(
                    text = value.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        DragOnlySlider(
            value = value.toFloat(),
            onValueChange = {
                val near = (it.roundToInt() / step) * step
                value = near.coerceIn(minVal, maxVal)
                prefs.edit().putInt(key, value).apply()
            },
            valueRange = minVal.toFloat()..maxVal.toFloat(),
            steps = steps,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = key,
            currentValueStr = value.toString(),
            defaultValueStr = defaultVal.toString(),
            onValueTyped = { typedStr ->
                val parsed = typedStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putInt(key, value).apply()
                }
            },
            onResetToDefault = {
                value = defaultVal
                prefs.edit().putInt(key, defaultVal).apply()
            },
            onSelectProfile = { profileStr ->
                val parsed = profileStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putInt(key, value).apply()
                }
            },
            onDismiss = { isFlyoutOpen = false },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = { enabled ->
                isTapToJumpEnabled = enabled
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$key", enabled).apply()
            }
        )
    }
}

fun resKey(context: Context, resourceName: String): String {
    return resourceName
}

fun resStr(context: Context, resourceName: String, fallback: String): String {
    return fallback.ifEmpty { resourceName }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PrefFloatDottedSliderRow(
    context: Context,
    prefs: SharedPreferences,
    keyResName: String,
    title: String,
    minVal: Float,
    maxVal: Float,
    step: Float = 0.5f,
    defaultVal: Float,
    unit: String = "m/s²",
    onValueChanged: (Float) -> Unit = {}
) {
    val key = remember(keyResName) { resKey(context, keyResName) }
    var value by remember {
        val current = try {
            if (prefs.contains(key)) {
                try {
                    prefs.getFloat(key, defaultVal)
                } catch (_: Exception) {
                    prefs.getInt(key, (defaultVal * 10).toInt()) / 10f
                }
            } else defaultVal
        } catch (_: Exception) { defaultVal }
        mutableFloatStateOf(current)
    }
    val steps = remember(minVal, maxVal, step) { (((maxVal - minVal) / step).roundToInt() - 1).coerceAtLeast(0) }
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$key", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)

            // Interactive Tactile Value Pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            isFlyoutOpen = true
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            isFlyoutOpen = true
                        }
                    )
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%.1f %s", value, unit),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        DragOnlySlider(
            value = value,
            onValueChange = {
                val near = ((it / step).roundToInt() * step).coerceIn(minVal, maxVal)
                value = near
                prefs.edit().putFloat(key, value).apply()
                onValueChanged(value)
            },
            valueRange = minVal..maxVal,
            steps = steps,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = key,
            currentValueStr = String.format(java.util.Locale.US, "%.1f %s", value, unit),
            defaultValueStr = String.format(java.util.Locale.US, "%.1f %s", defaultVal, unit),
            onValueTyped = { typedStr ->
                val clean = typedStr.replace(unit, "").trim()
                val parsed = clean.toFloatOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putFloat(key, value).apply()
                    onValueChanged(value)
                }
            },
            onResetToDefault = {
                value = defaultVal
                prefs.edit().putFloat(key, defaultVal).apply()
                onValueChanged(defaultVal)
            },
            onSelectProfile = { profileStr ->
                val parsed = profileStr.split(" ").firstOrNull()?.toFloatOrNull()
                if (parsed != null) {
                    value = parsed.coerceIn(minVal, maxVal)
                    prefs.edit().putFloat(key, value).apply()
                    onValueChanged(value)
                }
            },
            onDismiss = { isFlyoutOpen = false },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = { enabled ->
                isTapToJumpEnabled = enabled
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$key", enabled).apply()
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun <T> RotaryWheelColumn(
    items: List<T>,
    selectedIndex: Int,
    onItemSelected: (Int, T) -> Unit,
    labelProvider: (T) -> String,
    modifier: Modifier = Modifier
) {
    val itemHeight = 40.dp
    val visibleItems = 3
    val totalHeight = itemHeight * visibleItems
    val initialIndex = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val centerIndex by remember {
        derivedStateOf {
            val firstIdx = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            if (offset > itemHeight.value / 2) (firstIdx + 1).coerceIn(0, items.size - 1)
            else firstIdx.coerceIn(0, items.size - 1)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (!listState.isScrollInProgress) {
            val target = centerIndex
            if (target in items.indices && target != selectedIndex) {
                onItemSelected(target, items[target])
            }
        }
    }

    Box(
        modifier = modifier
            .height(totalHeight)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF10131A))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Optical Center Selection Lens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
        )

        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items) { index, item ->
                val isSelected = (index == centerIndex)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = labelProvider(item),
                        fontSize = if (isSelected) 14.5.sp else 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
}

@Composable
fun Material3ExpressiveLoader() {
    CircularProgressIndicator(
        modifier = Modifier.size(72.dp),
        color = MaterialTheme.colorScheme.primary
    )
}

/**
 * Maintenance Bay styled Accordion with 45° alternating electric-blue and black hazard stripe border.
 */
