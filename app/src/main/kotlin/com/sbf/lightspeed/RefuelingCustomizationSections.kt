package com.sbf.lightspeed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckSignatureSliderRow
import com.sbf.lightspeed.settings.DeckSwitch
import com.sbf.lightspeed.settings.FontWritingLoadingAnimation
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlin.math.roundToInt

private fun builtInClockFonts(): List<Triple<String, String, FontFamily>> = listOf(
    Triple("mono", lsString(R.string.ref_studio_font_mono), FontFamily.Monospace),
    Triple("sans", lsString(R.string.ref_studio_font_sans), FontFamily.SansSerif),
    Triple("serif", lsString(R.string.ref_studio_font_serif), FontFamily.Serif),
    Triple("cyber", lsString(R.string.ref_studio_font_cyber), FontFamily.Monospace)
)

@Composable
internal fun ClockFormatSection(
    clockFormat: String,
    onClockFormatChange: (String) -> Unit
) {
    var showOrbitalInfo by remember { mutableStateOf(false) }
    val opticalColors = LocalDeckTheme.current.resolveOpticalColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lsString(R.string.ref_studio_clock_fmt_hdr),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LightspeedStyle.colors.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.PriorityHigh,
                contentDescription = "About Stardate time",
                tint = if (showOrbitalInfo) opticalColors.readableTintColor else LightspeedStyle.colors.accentWarn,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showOrbitalInfo = !showOrbitalInfo }
            )
        }
        if (showOrbitalInfo) {
            Text(
                text = lsString(R.string.ref_studio_stardate_info),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                lineHeight = 13.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val formats = listOf(
                "24h_sec" to lsString(R.string.ref_studio_fmt_24h_s),
                "24h" to lsString(R.string.ref_studio_fmt_24h),
                "12h_sec" to lsString(R.string.ref_studio_fmt_12h_s),
                "12h" to lsString(R.string.ref_studio_fmt_12h),
                "stardate" to lsString(R.string.ref_studio_fmt_stardate)
            )
            formats.forEach { (key, label) ->
                val isSelected = clockFormat == key || (key == "stardate" && clockFormat == "orbital")
                ModalDeckChipButton(
                    label = label,
                    isSelected = isSelected,
                    onClick = { onClockFormatChange(key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun DateFormatSection(
    dateFormat: String,
    onDateFormatChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = lsString(R.string.ref_studio_date_fmt_hdr),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = LightspeedStyle.colors.textPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val dateFormats = listOf(
                "full" to lsString(R.string.ref_studio_date_full),
                "short" to lsString(R.string.ref_studio_date_short),
                "iso" to lsString(R.string.ref_studio_date_iso),
                "compact" to lsString(R.string.ref_studio_date_compact),
                "hidden" to lsString(R.string.ref_studio_date_hide)
            )
            dateFormats.forEach { (key, label) ->
                ModalDeckChipButton(
                    label = label,
                    isSelected = dateFormat == key,
                    onClick = { onDateFormatChange(key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
internal fun FontAndAlarmSection(
    clockFont: String,
    showAlarm: Boolean,
    isFontListExpanded: Boolean,
    onToggleFontList: () -> Unit,
    deviceFonts: List<Triple<String, String, FontFamily?>>?,
    onClockFontChange: (String) -> Unit,
    onShowAlarmChange: (Boolean) -> Unit
) {
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val shape = RoundedCornerShape((snapshot.cornerRadiusDp * 0.42f).coerceIn(8f, 14f).dp)

    val builtInFonts = builtInClockFonts()
    val builtInMatch = builtInFonts.firstOrNull { it.first == clockFont }
    val deviceMatch = deviceFonts?.firstOrNull { it.first == clockFont }
    val activeLabel = builtInMatch?.second
        ?: deviceMatch?.second
        ?: DeviceFontScanner.getFontLabel(clockFont)
    val activeFontFamily = builtInMatch?.third
        ?: deviceMatch?.third
        ?: remember(clockFont) { DeviceFontScanner.resolveComposeFontFamily(clockFont) ?: FontFamily.Monospace }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = lsString(R.string.ref_studio_font_alarm_hdr),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = LightspeedStyle.colors.textPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1.75f)
                    .height(34.dp)
                    .clip(shape)
                    .background(opticalColors.panelColor.copy(alpha = 0.65f))
                    .border(
                        1.dp,
                        if (isFontListExpanded) opticalColors.readableTintColor.copy(alpha = 0.72f)
                        else opticalColors.borderColor.copy(alpha = 0.55f),
                        shape
                    )
                    .clickable(onClick = onToggleFontList)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = activeLabel,
                    fontFamily = activeFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LightspeedStyle.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isFontListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Select Clock Font",
                    tint = opticalColors.readableTintColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            ModalDeckChipButton(
                label = if (showAlarm) lsString(R.string.ref_studio_alarm_on) else lsString(R.string.ref_studio_alarm_off),
                isSelected = showAlarm,
                onClick = { onShowAlarmChange(!showAlarm) },
                modifier = Modifier.weight(0.95f),
                heightDp = 34
            )
        }

        AnimatedVisibility(
            visible = isFontListExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(opticalColors.panelColor.copy(alpha = 0.85f))
                    .border(1.dp, opticalColors.readableTintColor.copy(alpha = 0.35f), shape)
                    .padding(vertical = 4.dp)
            ) {
                val loadedList = deviceFonts
                if (loadedList == null) {
                    FontWritingLoadingAnimation(
                        accentColor = opticalColors.readableTintColor,
                        textColor = LightspeedStyle.colors.textPrimary,
                        subColor = LightspeedStyle.colors.textSecondary
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = lsString(R.string.ref_studio_builtin_presets),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = opticalColors.readableTintColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        builtInFonts.forEach { (key, label, previewFamily) ->
                            FontPickerOptionRow(
                                label = label,
                                previewFamily = previewFamily,
                                selected = clockFont == key,
                                accentColor = opticalColors.readableTintColor,
                                onClick = { onClockFontChange(key) }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.14f)
                        )

                        Text(
                            text = lsString(R.string.ref_studio_device_fonts, loadedList.size),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                        loadedList.forEach { (key, label, previewFamily) ->
                            FontPickerOptionRow(
                                label = label,
                                previewFamily = previewFamily ?: FontFamily.SansSerif,
                                selected = clockFont == key,
                                accentColor = opticalColors.readableTintColor,
                                onClick = { onClockFontChange(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FontPickerOptionRow(
    label: String,
    previewFamily: FontFamily,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) accentColor.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontFamily = previewFamily,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) accentColor else LightspeedStyle.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
internal fun BatteryStyleAndSizeSection(
    batteryStyle: String,
    gaugeScale: Float,
    isStyleListExpanded: Boolean,
    onToggleStyleList: () -> Unit,
    onBatteryStyleChange: (String) -> Unit,
    onGaugeScaleChange: (Float) -> Unit
) {
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val shape = RoundedCornerShape((snapshot.cornerRadiusDp * 0.42f).coerceIn(8f, 14f).dp)
    val gaugePct = (gaugeScale * 100f).roundToInt().coerceIn(75, 145)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = lsString(R.string.ref_studio_gauge_style_hdr),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = LightspeedStyle.colors.textPrimary
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(shape)
                .background(opticalColors.panelColor.copy(alpha = 0.65f))
                .border(
                    1.dp,
                    if (isStyleListExpanded) opticalColors.readableTintColor.copy(alpha = 0.72f)
                    else opticalColors.borderColor.copy(alpha = 0.55f),
                    shape
                )
                .clickable(onClick = onToggleStyleList)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                RefuelingGaugeMiniPreview(
                    styleKey = batteryStyle,
                    accentColor = opticalColors.readableTintColor,
                    trackColor = LightspeedStyle.colors.textPrimary
                )
                Text(
                    text = resolveGaugeStyleLabel(batteryStyle),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LightspeedStyle.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = if (isStyleListExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Select Gauge Style",
                tint = opticalColors.readableTintColor,
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isStyleListExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(opticalColors.panelColor.copy(alpha = 0.85f))
                    .border(1.dp, opticalColors.readableTintColor.copy(alpha = 0.35f), shape)
                    .heightIn(max = 210.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                refuelingGaugeStyleOptions().forEach { (key, label) ->
                    val selected = batteryStyle == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) opticalColors.readableTintColor.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onBatteryStyleChange(key) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            RefuelingGaugeMiniPreview(
                                styleKey = key,
                                accentColor = if (selected) opticalColors.readableTintColor else LightspeedStyle.colors.textPrimary.copy(alpha = 0.72f),
                                trackColor = LightspeedStyle.colors.textPrimary
                            )
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) opticalColors.readableTintColor else LightspeedStyle.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = opticalColors.readableTintColor,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }

        DeckSignatureSliderRow(
            title = lsString(R.string.ref_studio_gauge_size),
            sliderKey = LightspeedPreferences.KEY_REFUELING_GAUGE_SCALE,
            value = gaugePct.toFloat(),
            valueDisplayText = "$gaugePct%",
            currentValueForDialog = "$gaugePct%",
            defaultValueDisplay = "100%",
            valueRange = 75f..145f,
            steps = 13,
            precisionStep = 1f,
            accent = opticalColors.readableTintColor,
            onValueChange = { pct ->
                onGaugeScaleChange(pct.roundToInt().coerceIn(75, 145) / 100f)
            },
            onResetToDefault = {
                onGaugeScaleChange(1.0f)
            }
        )
    }
}

@Composable
internal fun TelemetryGraphSection(
    graphMode: String,
    graphSpan: String,
    graphUnpluggedMode: String,
    showGraphLabels: Boolean,
    onGraphModeChange: (String) -> Unit,
    onGraphSpanChange: (String) -> Unit,
    onGraphUnpluggedModeChange: (String) -> Unit,
    onShowGraphLabelsChange: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = lsString(R.string.ref_studio_graph_place_hdr),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = LightspeedStyle.colors.textPrimary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            refuelingGraphModeOptions().forEach { (key, label) ->
                ModalDeckChipButton(
                    label = label,
                    isSelected = graphMode == key,
                    onClick = { onGraphModeChange(key) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (graphMode != "off") {
            Text(
                text = lsString(R.string.ref_studio_graph_span_hdr),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LightspeedStyle.colors.textSecondary
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                refuelingGraphSpanOptions().chunked(3).forEach { rowOptions ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        rowOptions.forEach { (key, label) ->
                            ModalDeckChipButton(
                                label = label,
                                isSelected = graphSpan == key,
                                onClick = { onGraphSpanChange(key) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            if (graphSpan == "charge") {
                Text(
                    text = lsString(R.string.ref_studio_when_unplugged_hdr),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = LightspeedStyle.colors.textSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    refuelingGraphUnpluggedOptions().forEach { (key, label) ->
                        ModalDeckChipButton(
                            label = label,
                            isSelected = graphUnpluggedMode == key,
                            onClick = { onGraphUnpluggedModeChange(key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text(
                    text = if (graphUnpluggedMode == "last_charge") {
                        lsString(R.string.ref_studio_unplugged_last_desc)
                    } else {
                        lsString(R.string.ref_studio_unplugged_live_desc)
                    },
                    fontSize = 10.sp,
                    color = LightspeedStyle.colors.textSecondary
                )
            }

            if (graphMode != "gauge") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.ref_studio_graph_labels_title),
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Text(
                            text = lsString(R.string.ref_studio_graph_labels_sub),
                            fontSize = 10.sp,
                            color = LightspeedStyle.colors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = showGraphLabels,
                        onCheckedChange = onShowGraphLabelsChange
                    )
                }
            }
        }
    }
}

@Composable
internal fun LayoutRatioSection(
    isLandscape: Boolean,
    splitRatio: Float,
    onSplitRatioChange: (Float) -> Unit
) {
    val opticalColors = LocalDeckTheme.current.resolveOpticalColors()
    val firstPct = (splitRatio * 100f).roundToInt().coerceIn(30, 70)
    val secondPct = 100 - firstPct
    val defaultPct = if (isLandscape) 44 else 52
    val sliderKey = if (isLandscape) {
        LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_LANDSCAPE
    } else {
        LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_PORTRAIT
    }

    DeckSignatureSliderRow(
        title = if (isLandscape) lsString(R.string.ref_studio_ratio_land) else lsString(R.string.ref_studio_ratio_port),
        sliderKey = sliderKey,
        value = firstPct.toFloat(),
        valueDisplayText = "$firstPct% / $secondPct%",
        currentValueForDialog = "$firstPct%",
        defaultValueDisplay = "$defaultPct%",
        valueRange = 30f..70f,
        steps = 7,
        precisionStep = 1f,
        accent = opticalColors.readableTintColor,
        onValueChange = { pct ->
            onSplitRatioChange(pct.roundToInt().coerceIn(30, 70) / 100f)
        },
        onResetToDefault = {
            onSplitRatioChange(defaultPct / 100f)
        }
    )
}

@Composable
internal fun InactivityActionSection(
    sleepAction: String,
    onSleepActionChange: (String) -> Unit
) {
    var showHardwareLockInfo by remember { mutableStateOf(false) }
    val opticalColors = LocalDeckTheme.current.resolveOpticalColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lsString(R.string.ref_studio_inact_action_hdr),
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = LightspeedStyle.colors.textPrimary
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.PriorityHigh,
                contentDescription = "About Hardware Lock",
                tint = if (showHardwareLockInfo) opticalColors.readableTintColor else LightspeedStyle.colors.accentWarn,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showHardwareLockInfo = !showHardwareLockInfo }
            )
        }
        if (showHardwareLockInfo) {
            Text(
                text = lsString(R.string.ref_studio_hw_lock_info),
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                lineHeight = 13.sp
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val actions = listOf(
                "overlay" to lsString(R.string.ref_studio_act_oled_shield),
                "screen_off" to lsString(R.string.ref_studio_act_hw_lock)
            )
            actions.forEach { (key, label) ->
                ModalDeckChipButton(
                    label = label,
                    isSelected = sleepAction == key,
                    onClick = { onSleepActionChange(key) },
                    modifier = Modifier.weight(1f),
                    heightDp = 30
                )
            }
        }
    }
}

@Composable
internal fun InactivityTimeoutSection(
    sleepTimeout: String,
    onSleepTimeoutChange: (String) -> Unit,
    compactWrap: Boolean
) {
    val timeouts = listOf(
        "5s" to lsString(R.string.ref_chip_5s),
        "15s" to lsString(R.string.ref_chip_15s),
        "30s" to lsString(R.string.ref_chip_30s),
        "60s" to lsString(R.string.ref_chip_60s),
        "120s" to lsString(R.string.ref_chip_120s),
        "300s" to lsString(R.string.ref_chip_300s),
        "never" to lsString(R.string.ref_chip_never)
    )
    val rows = if (compactWrap) timeouts.chunked(4) else listOf(timeouts)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = lsString(R.string.ref_studio_inact_timeout_hdr),
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = LightspeedStyle.colors.textPrimary
        )
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                rowItems.forEach { (timeoutKey, timeoutLabel) ->
                    ModalDeckChipButton(
                        label = timeoutLabel,
                        isSelected = normalizeRefuelingTimeout(sleepTimeout) == timeoutKey,
                        onClick = { onSleepTimeoutChange(timeoutKey) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (compactWrap && rowItems.size < 4) {
                    repeat(4 - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
internal fun ModalDeckChipButton(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 28
) {
    val snapshot = LocalDeckTheme.current
    val opticalColors = snapshot.resolveOpticalColors()
    val isArabic = LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR
    DeckButton(
        onClick = onClick,
        isActive = isSelected,
        snapshot = snapshot,
        modifier = modifier.height(heightDp.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = if (isArabic) 9.5.sp else 8.5.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) opticalColors.readableTintColor else LightspeedStyle.colors.textPrimary.copy(alpha = 0.88f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}
