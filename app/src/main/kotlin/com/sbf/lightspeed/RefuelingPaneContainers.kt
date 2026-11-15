package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
internal fun RefuelingPrimaryPane(
    context: Context,
    activity: Activity,
    dockedWidgetTune: DockedWidgetTuneData?,
    isLandscape: Boolean,
    wattage: Float,
    batteryTempC: Float,
    batteryPct: Int,
    chargeTypeLabel: String,
    timeRemainingMinutes: Long,
    isCharging: Boolean,
    isFull: Boolean,
    batteryStyle: String,
    gaugeScale: Float,
    graphMode: String,
    graphSpan: String,
    graphUnpluggedMode: String,
    showGraphLabels: Boolean,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    currentTimeStr: String,
    currentDateStr: String,
    nextAlarmStr: String?,
    showAlarm: Boolean,
    clockFormat: String,
    clockFont: String,
    onOpenCustomization: () -> Unit,
    onCycleGraphSpan: () -> Unit,
    onUserInteraction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Crossfade(
            targetState = dockedWidgetTune != null,
            animationSpec = tween(durationMillis = 180),
            label = "primary_pane_crossfade"
        ) { isTuningWidget ->
            val tuneData = dockedWidgetTune
            if (isTuningWidget && tuneData != null) {
                RefuelingWidgetTunePanel(
                    widgetLabel = tuneData.widgetLabel,
                    spec = tuneData.spec,
                    gridGapDp = tuneData.gridGapDp,
                    isGridMode = tuneData.isGridMode,
                    isLandscape = isLandscape,
                    onSpecChange = tuneData.onSpecChange,
                    onGapChange = tuneData.onGapChange,
                    onResetSpec = tuneData.onResetSpec,
                    onDismiss = tuneData.onDismiss
                )
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val showStripGraph = graphMode == "strip"
                    val showBackdropGraph = graphMode == "backdrop"
                    val showGaugeGraph = graphMode == "gauge"

                    val baseGaugeDp = if (isLandscape) 152f else 180f
                    val reservedHeightDp = 72f + (if (showStripGraph) 96f else 8f)
                    val maxAllowedDp = minOf(
                        (maxWidth.value - 12f).coerceAtLeast(104f),
                        (maxHeight.value - reservedHeightDp).coerceAtLeast(104f)
                    )
                    val computedGaugeSize = (baseGaugeDp * gaugeScale).coerceIn(104f, maxAllowedDp).dp

                    if (showBackdropGraph) {
                        RefuelingTelemetryGraphSurface(
                            wattage = wattage,
                            batteryTempC = batteryTempC,
                            isCharging = isCharging,
                            graphSpan = graphSpan,
                            graphUnpluggedMode = graphUnpluggedMode,
                            showLabels = showGraphLabels,
                            dynamicArcColor = dynamicArcColor,
                            thermalBadgeColor = thermalBadgeColor,
                            onCycleSpan = onCycleGraphSpan,
                            modifier = Modifier
                                .matchParentSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onOpenCustomization() })
                            }
                            .refuelingBayUnlockSwipe(context, activity, onDismiss, onUserInteraction),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CryoClockHeader(
                            currentTimeStr = currentTimeStr,
                            currentDateStr = currentDateStr,
                            nextAlarmStr = nextAlarmStr,
                            showAlarm = showAlarm,
                            clockFormat = clockFormat,
                            clockFont = clockFont,
                            isLandscape = isLandscape,
                            onLongPress = onOpenCustomization
                        )

                        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 10.dp))

                        Box(
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onOpenCustomization() })
                            }
                        ) {
                            BatteryTelemetryCircle(
                                batteryPct = batteryPct,
                                wattage = wattage,
                                batteryTempC = batteryTempC,
                                chargeTypeLabel = chargeTypeLabel,
                                timeRemainingMinutes = timeRemainingMinutes,
                                isCharging = isCharging,
                                isFull = isFull,
                                batteryStyle = batteryStyle,
                                dynamicArcColor = dynamicArcColor,
                                thermalBadgeColor = thermalBadgeColor,
                                sizeDp = computedGaugeSize,
                                showInsideGraph = showGaugeGraph,
                                graphSpan = graphSpan,
                                graphUnpluggedMode = graphUnpluggedMode
                            )
                        }

                        if (showStripGraph) {
                            Spacer(modifier = Modifier.height(8.dp))
                            RefuelingTelemetryGraphSurface(
                                wattage = wattage,
                                batteryTempC = batteryTempC,
                                isCharging = isCharging,
                                graphSpan = graphSpan,
                                graphUnpluggedMode = graphUnpluggedMode,
                                showLabels = showGraphLabels,
                                dynamicArcColor = dynamicArcColor,
                                thermalBadgeColor = thermalBadgeColor,
                                onCycleSpan = onCycleGraphSpan,
                                modifier = Modifier
                                    .fillMaxWidth(0.94f)
                                    .height(if (showGraphLabels) 88.dp else 68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.03f))
                                    .border(0.6.dp, LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RefuelingSecondaryPane(
    activity: Activity,
    showCustomizationPanel: Boolean,
    isLandscape: Boolean,
    widgetIds: List<Int>,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    reorderVersion: Int,
    isStackMemoryEnabled: Boolean,
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
    tuneDismissSignal: Int,
    onDockedTuneChange: (DockedWidgetTuneData?) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAddWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onReorderWidget: (Int, Int) -> Unit,
    onReconfigureWidget: ((Int) -> Unit)?,
    onToggleStackMemory: () -> Unit,
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
    onDismissCustomization: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Crossfade(
            targetState = showCustomizationPanel,
            animationSpec = tween(durationMillis = 180),
            label = "secondary_pane_crossfade"
        ) { editingClockAndGauge ->
            if (editingClockAndGauge) {
                RefuelingCustomizationPanel(
                    isLandscape = isLandscape,
                    batteryStyle = batteryStyle,
                    gaugeScale = gaugeScale,
                    graphMode = graphMode,
                    graphSpan = graphSpan,
                    graphUnpluggedMode = graphUnpluggedMode,
                    showGraphLabels = showGraphLabels,
                    sleepTimeout = sleepTimeout,
                    sleepAction = sleepAction,
                    splitRatio = splitRatio,
                    clockFormat = clockFormat,
                    dateFormat = dateFormat,
                    clockFont = clockFont,
                    showAlarm = showAlarm,
                    onBatteryStyleChange = onBatteryStyleChange,
                    onGaugeScaleChange = onGaugeScaleChange,
                    onGraphModeChange = onGraphModeChange,
                    onGraphSpanChange = onGraphSpanChange,
                    onGraphUnpluggedModeChange = onGraphUnpluggedModeChange,
                    onShowGraphLabelsChange = onShowGraphLabelsChange,
                    onSleepTimeoutChange = onSleepTimeoutChange,
                    onSleepActionChange = onSleepActionChange,
                    onSplitRatioChange = onSplitRatioChange,
                    onClockFormatChange = onClockFormatChange,
                    onDateFormatChange = onDateFormatChange,
                    onClockFontChange = onClockFontChange,
                    onShowAlarmChange = onShowAlarmChange,
                    onDismiss = onDismissCustomization
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WidgetEngineToolbar(
                        widgetCount = widgetIds.size,
                        widgetLayoutMode = widgetLayoutMode,
                        isEditMode = isEditMode,
                        onToggleLayoutMode = onToggleLayoutMode,
                        onToggleEditMode = onToggleEditMode,
                        onAddWidget = onAddWidget,
                        isLandscape = isLandscape,
                        isStackMemoryEnabled = isStackMemoryEnabled,
                        onToggleStackMemory = onToggleStackMemory
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        MultiWidgetContainer(
                            activity = activity,
                            widgetIds = widgetIds,
                            widgetLayoutMode = widgetLayoutMode,
                            isEditMode = isEditMode,
                            isLandscape = isLandscape,
                            appWidgetHost = appWidgetHost,
                            appWidgetManager = appWidgetManager,
                            onPickWidget = onAddWidget,
                            onRemoveWidget = onRemoveWidget,
                            onReorderWidget = onReorderWidget,
                            onReconfigureWidget = onReconfigureWidget,
                            reorderVersion = reorderVersion,
                            onDockedTuneChange = onDockedTuneChange,
                            externalTuneDismissSignal = tuneDismissSignal
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RefuelingSplitHostLayout(
    isLandscape: Boolean,
    splitRatio: Float,
    context: Context,
    activity: Activity,
    onDismiss: () -> Unit,
    onUserInteraction: () -> Unit,
    primaryPane: @Composable (Modifier) -> Unit,
    secondaryPane: @Composable (Modifier) -> Unit
) {
    if (isLandscape) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            primaryPane(
                Modifier
                    .weight(splitRatio)
                    .fillMaxHeight()
            )
            secondaryPane(
                Modifier
                    .weight(1f - splitRatio)
                    .fillMaxHeight()
            )
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            primaryPane(
                Modifier
                    .fillMaxWidth()
                    .weight(splitRatio)
            )
            secondaryPane(
                Modifier
                    .fillMaxWidth(0.96f)
                    .weight(1f - splitRatio)
                    .padding(bottom = 6.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 44.dp)
                    .refuelingBayUnlockSwipe(context, activity, onDismiss, onUserInteraction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Double Tap to Exit · Swipe Up to Unlock",
                    fontSize = 11.sp,
                    color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.45f),
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}
