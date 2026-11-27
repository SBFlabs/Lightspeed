package com.sbf.lightspeed

import com.sbf.lightspeed.settings.verticalScrollWithEdgeFade

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckSignatureSliderRow
import com.sbf.lightspeed.settings.blockScrimPassthrough
import com.sbf.lightspeed.settings.deckGlassSurface
import com.sbf.lightspeed.settings.overlayScrimDismiss
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedOverlayDismissCoordinator
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlin.math.roundToInt

/**
 * State snapshot published by [MultiWidgetContainer] when hosted inside Refueling Bay so
 * [RefuelingWidgetTunePanel] can dock directly into the opposite pane.
 */
data class DockedWidgetTuneData(
    val widgetId: Int,
    val widgetLabel: String,
    val spec: RefuelingWidgetSpec,
    val gridGapDp: Int,
    val isGridMode: Boolean,
    val onSpecChange: (RefuelingWidgetSpec) -> Unit,
    val onGapChange: (Int) -> Unit,
    val onResetSpec: () -> Unit,
    val onDismiss: () -> Unit
)

/**
 * Docked full-pane live widget tuner used inside Refueling Bay's opposite pane
 * (Clock/Gauge area), keeping the entire widget grid or stack completely visible.
 */
@Composable
fun RefuelingWidgetTunePanel(
    widgetLabel: String,
    spec: RefuelingWidgetSpec,
    gridGapDp: Int,
    isGridMode: Boolean,
    isLandscape: Boolean,
    onSpecChange: (RefuelingWidgetSpec) -> Unit,
    onGapChange: (Int) -> Unit,
    onResetSpec: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDismiss)

    val activeSnapshot = LocalDeckTheme.current
    val shape = RoundedCornerShape(activeSnapshot.cornerRadiusDp.dp)

    Box(
        modifier = modifier
            .fillMaxSize()
            .deckGlassSurface(
                snapshot = activeSnapshot,
                isBlurAvailable = false,
                isFlyout = true,
                withNoise = true,
                shapeOverride = shape
            )
    ) {
        RefuelingWidgetTuneBody(
            widgetLabel = widgetLabel,
            spec = spec,
            gridGapDp = gridGapDp,
            isGridMode = isGridMode,
            isLandscape = isLandscape,
            onSpecChange = onSpecChange,
            onGapChange = onGapChange,
            onResetSpec = onResetSpec,
            onDismiss = onDismiss,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (isLandscape) 14.dp else 16.dp, vertical = 12.dp)
        )
    }
}

/**
 * Standalone Dialog wrapper used when tuning Home Stack widgets without a split opposite pane.
 */
@Composable
fun RefuelingWidgetTuneModal(
    widgetLabel: String,
    spec: RefuelingWidgetSpec,
    gridGapDp: Int,
    isGridMode: Boolean,
    isLandscape: Boolean,
    onSpecChange: (RefuelingWidgetSpec) -> Unit,
    onGapChange: (Int) -> Unit,
    onResetSpec: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activeDensity = LocalDensity.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        CompositionLocalProvider(LocalDensity provides activeDensity) {
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
                    .overlayScrimDismiss(
                        context = context,
                        onTapOutside = onDismiss,
                        onLongPressOutside = {
                            onDismiss()
                            LightspeedOverlayDismissCoordinator.dismissAll(context)
                        }
                    )
            )
            DeckGlassCardSurface(
                isFlyout = true,
                modifier = Modifier
                    .systemBarsPadding()
                    .displayCutoutPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .heightIn(max = 540.dp)
                    .blockScrimPassthrough()
            ) {
                RefuelingWidgetTuneBody(
                    widgetLabel = widgetLabel,
                    spec = spec,
                    gridGapDp = gridGapDp,
                    isGridMode = isGridMode,
                    isLandscape = isLandscape,
                    onSpecChange = onSpecChange,
                    onGapChange = onGapChange,
                    onResetSpec = onResetSpec,
                    onDismiss = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                )
            }
        }
        }
    }
}

@Composable
private fun RefuelingWidgetTuneBody(
    widgetLabel: String,
    spec: RefuelingWidgetSpec,
    gridGapDp: Int,
    isGridMode: Boolean,
    isLandscape: Boolean,
    onSpecChange: (RefuelingWidgetSpec) -> Unit,
    onGapChange: (Int) -> Unit,
    onResetSpec: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deckTheme = LocalDeckTheme.current
    val accent = deckTheme.resolveOpticalColors().readableTintColor
    val haptics = LocalHapticFeedback.current
    val isArabic = LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR
    val defaultW = if (isLandscape) 5 else 10
    val defaultH = 4

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lsString(R.string.ref_tune_title),
                        color = accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = if (isArabic) 0.sp else 1.1.sp
                    )
                    Text(
                        text = widgetLabel,
                        color = LightspeedStyle.colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close tuner",
                    tint = LightspeedStyle.colors.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = lsString(R.string.ref_tune_hint),
            color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.8f),
            fontSize = 9.5.sp,
            fontFamily = FontFamily.Monospace
        )

        HorizontalDivider(
            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScrollWithEdgeFade(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (isGridMode) {
                DeckSignatureSliderRow(
                    title = lsString(R.string.ref_tune_width),
                    sliderKey = "refueling_tune_width",
                    value = spec.wCells.toFloat(),
                    valueDisplayText = "${spec.wCells}/10",
                    currentValueForDialog = "${spec.wCells}",
                    defaultValueDisplay = "$defaultW",
                    valueRange = 1f..10f,
                    steps = 8,
                    precisionStep = 1f,
                    accent = accent,
                    onValueChange = { onSpecChange(spec.copy(wCells = it.roundToInt())) },
                    onResetToDefault = { onSpecChange(spec.copy(wCells = defaultW)) }
                )
                DeckSignatureSliderRow(
                    title = lsString(R.string.ref_tune_height),
                    sliderKey = "refueling_tune_height",
                    value = spec.hCells.toFloat(),
                    valueDisplayText = "${spec.hCells}/10",
                    currentValueForDialog = "${spec.hCells}",
                    defaultValueDisplay = "$defaultH",
                    valueRange = 1f..10f,
                    steps = 8,
                    precisionStep = 1f,
                    accent = accent,
                    onValueChange = { onSpecChange(spec.copy(hCells = it.roundToInt())) },
                    onResetToDefault = { onSpecChange(spec.copy(hCells = defaultH)) }
                )
                DeckSignatureSliderRow(
                    title = lsString(R.string.ref_tune_grid_gap),
                    sliderKey = "refueling_tune_gap",
                    value = gridGapDp.toFloat(),
                    valueDisplayText = "${gridGapDp}dp",
                    currentValueForDialog = "${gridGapDp}dp",
                    defaultValueDisplay = "${RefuelingGridConstants.DEFAULT_GAP_DP}dp",
                    valueRange = 0f..RefuelingGridConstants.MAX_GAP_DP.toFloat(),
                    steps = RefuelingGridConstants.MAX_GAP_DP - 1,
                    precisionStep = 1f,
                    accent = accent,
                    onValueChange = { onGapChange(it.roundToInt()) },
                    onResetToDefault = { onGapChange(RefuelingGridConstants.DEFAULT_GAP_DP) }
                )
            }

            DeckSignatureSliderRow(
                title = lsString(R.string.ref_tune_opacity),
                sliderKey = "refueling_tune_opacity",
                value = spec.opacity.toFloat(),
                valueDisplayText = "${spec.opacity}%",
                currentValueForDialog = "${spec.opacity}%",
                defaultValueDisplay = "${RefuelingGridConstants.OPACITY_MAX}%",
                valueRange = RefuelingGridConstants.OPACITY_MIN.toFloat()..RefuelingGridConstants.OPACITY_MAX.toFloat(),
                steps = 15,
                precisionStep = 1f,
                accent = accent,
                onValueChange = { onSpecChange(spec.copy(opacity = it.roundToInt())) },
                onResetToDefault = { onSpecChange(spec.copy(opacity = RefuelingGridConstants.OPACITY_MAX)) }
            )

            val autoLabel = lsString(R.string.ref_tune_auto)
            DeckSignatureSliderRow(
                title = lsString(R.string.ref_tune_corner_radius),
                sliderKey = "refueling_tune_corner_radius",
                value = spec.cornerRadiusDp.toFloat(),
                valueDisplayText = if (spec.cornerRadiusDp < 0) autoLabel else "${spec.cornerRadiusDp}dp",
                currentValueForDialog = if (spec.cornerRadiusDp < 0) autoLabel else "${spec.cornerRadiusDp}dp",
                defaultValueDisplay = "$autoLabel (-1)",
                valueRange = -1f..RefuelingGridConstants.MAX_RADIUS_DP.toFloat(),
                steps = 17,
                precisionStep = 1f,
                accent = accent,
                onValueChange = { v ->
                    val rounded = v.roundToInt()
                    onSpecChange(
                        spec.copy(
                            cornerRadiusDp = if (rounded < 0) RefuelingGridConstants.RADIUS_AUTO else rounded
                        )
                    )
                },
                onParseTypedValue = { raw ->
                    if (raw.trim().equals("auto", ignoreCase = true) || raw.trim() == autoLabel) -1f
                    else raw.filter { it.isDigit() || it == '-' }.toIntOrNull()?.toFloat()
                },
                onResetToDefault = {
                    onSpecChange(spec.copy(cornerRadiusDp = RefuelingGridConstants.RADIUS_AUTO))
                }
            )

            val padRows = listOf(
                Triple("refueling_tune_pad_left" to lsString(R.string.ref_tune_pad_left), spec.padLeftDp) { v: Int -> spec.copy(padLeftDp = v) },
                Triple("refueling_tune_pad_top" to lsString(R.string.ref_tune_pad_top), spec.padTopDp) { v: Int -> spec.copy(padTopDp = v) },
                Triple("refueling_tune_pad_right" to lsString(R.string.ref_tune_pad_right), spec.padRightDp) { v: Int -> spec.copy(padRightDp = v) },
                Triple("refueling_tune_pad_bottom" to lsString(R.string.ref_tune_pad_bottom), spec.padBottomDp) { v: Int -> spec.copy(padBottomDp = v) }
            )
            padRows.forEach { (padMeta, padVal, updateFn) ->
                val (padKey, padLabel) = padMeta
                DeckSignatureSliderRow(
                    title = padLabel,
                    sliderKey = padKey,
                    value = padVal.toFloat(),
                    valueDisplayText = "${padVal}dp",
                    currentValueForDialog = "${padVal}dp",
                    defaultValueDisplay = "0dp",
                    valueRange = 0f..RefuelingGridConstants.MAX_PAD_DP.toFloat(),
                    steps = RefuelingGridConstants.MAX_PAD_DP - 1,
                    precisionStep = 1f,
                    accent = accent,
                    onValueChange = { onSpecChange(updateFn(it.roundToInt())) },
                    onResetToDefault = { onSpecChange(updateFn(0)) }
                )
            }
        }

        HorizontalDivider(
            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f),
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DeckButton(
                text = lsString(R.string.ref_tune_zero_pad),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSpecChange(
                        spec.copy(padLeftDp = 0, padTopDp = 0, padRightDp = 0, padBottomDp = 0)
                    )
                },
                modifier = Modifier.weight(1f)
            )
            DeckButton(
                text = lsString(R.string.ref_tune_reset),
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onResetSpec()
                },
                modifier = Modifier.weight(1f)
            )
            DeckButton(
                text = lsString(R.string.ref_bay_btn_done),
                isActive = true,
                onClick = onDismiss,
                modifier = Modifier.weight(1.2f)
            )
        }
    }
}

/**
 * Branded 12-Theme confirmation modal before removing a widget from Refueling Bay / Stack.
 * Matches Category Cruise's destructive confirmation flow so accidental Edit taps never drop widgets.
 */
@Composable
fun RefuelingDeleteConfirmDialog(
    widgetLabel: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val isArabic = LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR
    DeckDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = lsString(R.string.ref_del_wid_title),
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                letterSpacing = if (isArabic) 0.sp else 1.sp
            )
        },
        text = {
            Text(
                text = lsString(R.string.ref_del_wid_msg, widgetLabel),
                color = LightspeedStyle.colors.textSecondary,
                fontSize = 12.5.sp
            )
        },
        dismissButton = {
            DeckButton(text = lsString(R.string.ref_del_wid_cancel), onClick = onDismiss)
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.ref_del_wid_confirm),
                isDestructive = true,
                onClick = onConfirmDelete
            )
        }
    )
}
