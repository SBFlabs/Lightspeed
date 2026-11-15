package com.sbf.lightspeed

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.deckGlassSurface
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme

/**
 * Full-section docked Clock, Gauge & Telemetry Graph customization panel.
 * Fills the entire opposite Refueling Bay pane (Widget pane) while leaving the live Clock,
 * Battery Gauge, and Charging Graph completely unobstructed for instant preview.
 */
@Composable
fun RefuelingCustomizationPanel(
    isLandscape: Boolean,
    batteryStyle: String,
    gaugeScale: Float,
    graphMode: String,
    graphSpan: String,
    graphUnpluggedMode: String,
    showGraphLabels: Boolean,
    sleepTimeout: String,
    sleepAction: String,
    splitRatio: Float,
    clockFormat: String,
    dateFormat: String,
    clockFont: String,
    showAlarm: Boolean,
    onBatteryStyleChange: (String) -> Unit,
    onGaugeScaleChange: (Float) -> Unit,
    onGraphModeChange: (String) -> Unit,
    onGraphSpanChange: (String) -> Unit,
    onGraphUnpluggedModeChange: (String) -> Unit,
    onShowGraphLabelsChange: (Boolean) -> Unit,
    onSleepTimeoutChange: (String) -> Unit,
    onSleepActionChange: (String) -> Unit,
    onSplitRatioChange: (Float) -> Unit,
    onClockFormatChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onClockFontChange: (String) -> Unit,
    onShowAlarmChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val scrollState = rememberScrollState()
    LaunchedEffect(isLandscape) {
        scrollState.scrollTo(0)
    }

    var isFontListExpanded by remember { mutableStateOf(false) }
    var isStyleListExpanded by remember { mutableStateOf(false) }
    var deviceFonts by remember { mutableStateOf<List<Triple<String, String, FontFamily?>>?>(null) }
    LaunchedEffect(isFontListExpanded) {
        if (isFontListExpanded && deviceFonts == null) {
            deviceFonts = DeviceFontScanner.loadCleanResolvedFontsOnDemand()
        }
    }

    val activeSnapshot = LocalDeckTheme.current
    val opticalColors = activeSnapshot.resolveOpticalColors()
    val shape = RoundedCornerShape(activeSnapshot.cornerRadiusDp.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .deckGlassSurface(
                snapshot = activeSnapshot,
                isBlurAvailable = false,
                isFlyout = true,
                withNoise = true,
                shapeOverride = shape
            )
            .padding(horizontal = if (isLandscape) 14.dp else 16.dp, vertical = if (isLandscape) 10.dp else 14.dp)
    ) {
        val useTwoColumns = isLandscape && maxWidth >= 460.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(if (isLandscape) 8.dp else 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = opticalColors.readableTintColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "GAUGE & CLOCK STUDIO",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = opticalColors.readableTintColor,
                        letterSpacing = 1.1.sp
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
            ) {
                if (useTwoColumns) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ClockFormatSection(clockFormat, onClockFormatChange)
                            DateFormatSection(dateFormat, onDateFormatChange)
                            FontAndAlarmSection(
                                clockFont = clockFont,
                                showAlarm = showAlarm,
                                isFontListExpanded = isFontListExpanded,
                                onToggleFontList = { isFontListExpanded = !isFontListExpanded },
                                deviceFonts = deviceFonts,
                                onClockFontChange = { key ->
                                    onClockFontChange(key)
                                    isFontListExpanded = false
                                },
                                onShowAlarmChange = onShowAlarmChange
                            )
                            LayoutRatioSection(isLandscape = true, splitRatio = splitRatio, onSplitRatioChange = onSplitRatioChange)
                        }
                        Column(
                            modifier = Modifier.weight(1.06f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BatteryStyleAndSizeSection(
                                batteryStyle = batteryStyle,
                                gaugeScale = gaugeScale,
                                isStyleListExpanded = isStyleListExpanded,
                                onToggleStyleList = { isStyleListExpanded = !isStyleListExpanded },
                                onBatteryStyleChange = { style ->
                                    onBatteryStyleChange(style)
                                    isStyleListExpanded = false
                                },
                                onGaugeScaleChange = onGaugeScaleChange
                            )
                            TelemetryGraphSection(
                                graphMode = graphMode,
                                graphSpan = graphSpan,
                                graphUnpluggedMode = graphUnpluggedMode,
                                showGraphLabels = showGraphLabels,
                                onGraphModeChange = onGraphModeChange,
                                onGraphSpanChange = onGraphSpanChange,
                                onGraphUnpluggedModeChange = onGraphUnpluggedModeChange,
                                onShowGraphLabelsChange = onShowGraphLabelsChange
                            )
                            InactivityActionSection(sleepAction, onSleepActionChange)
                            InactivityTimeoutSection(sleepTimeout, onSleepTimeoutChange, compactWrap = true)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        BatteryStyleAndSizeSection(
                            batteryStyle = batteryStyle,
                            gaugeScale = gaugeScale,
                            isStyleListExpanded = isStyleListExpanded,
                            onToggleStyleList = { isStyleListExpanded = !isStyleListExpanded },
                            onBatteryStyleChange = { style ->
                                onBatteryStyleChange(style)
                                isStyleListExpanded = false
                            },
                            onGaugeScaleChange = onGaugeScaleChange
                        )
                        TelemetryGraphSection(
                            graphMode = graphMode,
                            graphSpan = graphSpan,
                            graphUnpluggedMode = graphUnpluggedMode,
                            showGraphLabels = showGraphLabels,
                            onGraphModeChange = onGraphModeChange,
                            onGraphSpanChange = onGraphSpanChange,
                            onGraphUnpluggedModeChange = onGraphUnpluggedModeChange,
                            onShowGraphLabelsChange = onShowGraphLabelsChange
                        )
                        ClockFormatSection(clockFormat, onClockFormatChange)
                        DateFormatSection(dateFormat, onDateFormatChange)
                        FontAndAlarmSection(
                            clockFont = clockFont,
                            showAlarm = showAlarm,
                            isFontListExpanded = isFontListExpanded,
                            onToggleFontList = { isFontListExpanded = !isFontListExpanded },
                            deviceFonts = deviceFonts,
                            onClockFontChange = { key ->
                                onClockFontChange(key)
                                isFontListExpanded = false
                            },
                            onShowAlarmChange = onShowAlarmChange
                        )
                        LayoutRatioSection(isLandscape = isLandscape, splitRatio = splitRatio, onSplitRatioChange = onSplitRatioChange)
                        InactivityActionSection(sleepAction, onSleepActionChange)
                        InactivityTimeoutSection(sleepTimeout, onSleepTimeoutChange, compactWrap = !isLandscape)
                    }
                }
            }

            HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DeckButton(
                    text = "DONE",
                    isActive = true,
                    onClick = onDismiss
                )
            }
        }
    }
}
