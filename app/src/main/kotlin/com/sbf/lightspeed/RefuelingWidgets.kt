package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.deckGlassSurface
import com.sbf.lightspeed.settings.resolveOpticalColors
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs

/**
 * Multi-widget host for Refueling Bay and Home Stack Overlay.
 * Aligns Edit Mode wireframes, compact top-right controls, confirmation delete dialog,
 * floating Live Tune panel, and 10-column 2D skyline masonry with Category Cruise.
 */
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
    onReorderWidget: (Int, Int) -> Unit,
    onReconfigureWidget: ((Int) -> Unit)? = null,
    reorderVersion: Int = 0,
    onDockedTuneChange: ((DockedWidgetTuneData?) -> Unit)? = null,
    externalTuneDismissSignal: Int = 0
) {
    val lsColors = LightspeedStyle.colors
    val deckTheme = LocalDeckTheme.current
    val opticalColors = deckTheme.resolveOpticalColors()
    val outerCornerDp = (deckTheme.cornerRadiusDp * 0.78f).coerceIn(12f, 24f).dp
    val gridCornerDp = (deckTheme.cornerRadiusDp * 0.68f).coerceIn(10f, 22f).dp
    val outerShape = RoundedCornerShape(outerCornerDp)

    if (widgetIds.isEmpty()) {
        DeckCard(
            modifier = Modifier.fillMaxSize(),
            shape = outerShape
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                EmptyWidgetSlot(onPickWidget = onPickWidget)
            }
        }
        return
    }

    val prefs = remember { activity.defaultPrefs() }
    val specsById = remember { mutableStateMapOf<Int, RefuelingWidgetSpec>() }
    var gridGapDp by remember { mutableIntStateOf(RefuelingWidgetSpecsStore.getGridGapDp(prefs)) }
    var tuningWidgetId by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteWidgetId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(widgetIds, isLandscape, reorderVersion) {
        val loaded = RefuelingWidgetSpecsStore.loadSpecs(prefs, widgetIds, isLandscape)
        specsById.clear()
        specsById.putAll(loaded)
        gridGapDp = RefuelingWidgetSpecsStore.getGridGapDp(prefs)
    }

    LaunchedEffect(isEditMode, widgetIds) {
        if (!isEditMode) {
            tuningWidgetId = null
            pendingDeleteWidgetId = null
        } else {
            if (tuningWidgetId != null && tuningWidgetId !in widgetIds) tuningWidgetId = null
            if (pendingDeleteWidgetId != null && pendingDeleteWidgetId !in widgetIds) pendingDeleteWidgetId = null
        }
    }

    LaunchedEffect(externalTuneDismissSignal) {
        if (externalTuneDismissSignal > 0) {
            tuningWidgetId = null
        }
    }

    if (onDockedTuneChange != null) {
        DisposableEffect(Unit) {
            onDispose { onDockedTuneChange(null) }
        }
    }

    val localeState = LightspeedLocaleEngine.state
    val widgetFallbackFmt = lsString(R.string.ref_bay_widget_fallback, 0)
    val resolveLabel = remember(appWidgetManager, widgetIds, localeState, widgetFallbackFmt) {
        { id: Int ->
            val label = try {
                appWidgetManager?.getAppWidgetInfo(id)
                    ?.loadLabel(activity.packageManager)
                    ?.toString()
            } catch (e: Exception) {
                logSwallowed("RefuelingWidgets", "resolveLabel", e)
                null
            }
            if (label.isNullOrBlank()) lsString(R.string.ref_bay_widget_fallback, id) else label
        }
    }

    val isGridMode = widgetLayoutMode != "smart_stack"

    if (!isGridMode) {
        val pagerState = rememberPagerState(pageCount = { widgetIds.size })
        val coroutineScope = rememberCoroutineScope()
        val rememberPage = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_STACK_REMEMBER_PAGE, false)
        var prevWidgetCount by remember { mutableIntStateOf(-1) }

        LaunchedEffect(rememberPage) {
            if (widgetIds.isNotEmpty() && prevWidgetCount == -1) {
                val target = if (rememberPage) {
                    prefs.getInt(LightspeedPreferences.KEY_REFUELING_STACK_LAST_PAGE, 0)
                        .coerceIn(0, widgetIds.size - 1)
                } else {
                    0
                }
                pagerState.scrollToPage(target)
                prevWidgetCount = widgetIds.size
            }
        }

        LaunchedEffect(widgetIds.size) {
            val oldCount = prevWidgetCount
            prevWidgetCount = widgetIds.size
            if (oldCount in 1 until widgetIds.size) {
                pagerState.animateScrollToPage(widgetIds.size - 1)
            } else if (pagerState.currentPage >= widgetIds.size && widgetIds.isNotEmpty()) {
                pagerState.scrollToPage(widgetIds.size - 1)
            }
        }

        LaunchedEffect(pagerState.currentPage) {
            if (rememberPage) {
                prefs.edit().putInt(LightspeedPreferences.KEY_REFUELING_STACK_LAST_PAGE, pagerState.currentPage).apply()
            }
        }

        val activeWidgetId = widgetIds.getOrNull(pagerState.currentPage)
        val activeSpec = activeWidgetId?.let { specsById[it] }
        val activeCorner = if (activeSpec != null && activeSpec.cornerRadiusDp >= 0) {
            activeSpec.cornerRadiusDp.dp
        } else {
            outerCornerDp
        }
        val activeShape = RoundedCornerShape(activeCorner)
        val activeOpacity = (activeSpec?.opacity ?: RefuelingGridConstants.OPACITY_MAX) / 100f

        Box(modifier = Modifier.fillMaxSize().clip(activeShape)) {
            // Glass surface layer modulated independently so widget clarity holds across translucency levels.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = activeOpacity }
                    .clip(activeShape)
                    .deckGlassSurface(
                        snapshot = deckTheme,
                        isBlurAvailable = false,
                        isCard = true,
                        withNoise = false,
                        shapeOverride = activeShape
                    )
            )

            HorizontalPager(
                state = pagerState,
                key = { page -> widgetIds.getOrNull(page) ?: page },
                modifier = Modifier.fillMaxSize()
            ) { pageIndex ->
                val widgetId = widgetIds.getOrNull(pageIndex)
                val spec = widgetId?.let {
                    specsById[it] ?: RefuelingGridConstants.defaultSpec(it, isLandscape)
                }
                if (widgetId != null && spec != null && appWidgetHost != null && appWidgetManager != null) {
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
                                .padding(
                                    start = spec.padLeftDp.dp,
                                    top = spec.padTopDp.dp,
                                    end = spec.padRightDp.dp,
                                    bottom = spec.padBottomDp.dp
                                ),
                            isEditMode = isEditMode,
                            isStackMode = true,
                            onSwipeDelta = { deltaX ->
                                try {
                                    pagerState.dispatchRawDelta(-deltaX)
                                } catch (e: Exception) {
                                    logSwallowed("RefuelingWidgets", "MultiWidgetContainer:dispatchRawDelta", e)
                                }
                            },
                            onSwipeEnd = { totalDx, xVel ->
                                val pageCount = widgetIds.size
                                val currentPage = pagerState.currentPage
                                val threshold = 70f
                                val targetPage = when {
                                    xVel < -800f || totalDx < -threshold -> (currentPage + 1).coerceAtMost(pageCount - 1)
                                    xVel > 800f || totalDx > threshold -> (currentPage - 1).coerceAtLeast(0)
                                    else -> currentPage
                                }
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(targetPage)
                                }
                            }
                        )

                        if (isEditMode) {
                            val hasConfig = remember(widgetId) {
                                appWidgetManager.getAppWidgetInfo(widgetId)?.configure != null
                            }
                            RefuelingEditSlotBadge(
                                text = String.format(Locale.US, "%02d · %s", pageIndex + 1, lsString(R.string.ref_bay_mode_stack)),
                                accent = opticalColors.readableTintColor,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(8.dp)
                            )
                            TacticalWidgetEditControls(
                                canMoveBack = pageIndex > 0,
                                canMoveForward = pageIndex < widgetIds.size - 1,
                                onMoveBack = { onReorderWidget(pageIndex, pageIndex - 1) },
                                onMoveForward = { onReorderWidget(pageIndex, pageIndex + 1) },
                                onRemove = { pendingDeleteWidgetId = widgetId },
                                onConfigure = if (hasConfig) {
                                    { onReconfigureWidget?.invoke(widgetId) }
                                } else null,
                                onTune = { tuningWidgetId = widgetId },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(1.5.dp, opticalColors.readableTintColor.copy(alpha = 0.78f), activeShape)
                )
            }

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
                                .size(if (isActive) 14.dp else 5.dp, 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isActive) opticalColors.readableTintColor
                                    else lsColors.textPrimary.copy(alpha = 0.35f)
                                )
                        )
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val layout = remember(widgetIds, specsById.toMap(), maxWidth, gridGapDp, isLandscape) {
                    computeRefuelingGridLayout(
                        widgetIds = widgetIds,
                        specsById = specsById,
                        usableWidthDp = maxWidth.value,
                        gapDp = gridGapDp.toFloat(),
                        isLandscape = isLandscape
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.totalHeightDp.dp.coerceAtLeast(1.dp))
                ) {
                    for (placed in layout.placements) {
                        val cardCornerDp = if (placed.spec.cornerRadiusDp >= 0) {
                            placed.spec.cornerRadiusDp.dp
                        } else {
                            gridCornerDp
                        }
                        val cardShape = RoundedCornerShape(cardCornerDp)
                        val isCompact = placed.widthDp < 210f

                        RefuelingGridWidgetCard(
                            activity = activity,
                            placed = placed,
                            totalWidgets = widgetIds.size,
                            isEditMode = isEditMode,
                            isCompact = isCompact,
                            cardShape = cardShape,
                            deckTheme = deckTheme,
                            accent = opticalColors.readableTintColor,
                            appWidgetHost = appWidgetHost,
                            appWidgetManager = appWidgetManager,
                            onReorderWidget = onReorderWidget,
                            onTuneWidget = { tuningWidgetId = placed.widgetId },
                            onRequestRemoveWidget = { pendingDeleteWidgetId = placed.widgetId },
                            onReconfigureWidget = onReconfigureWidget,
                            modifier = Modifier
                                .offset(x = placed.xDp.dp, y = placed.yDp.dp)
                                .size(width = placed.widthDp.dp, height = placed.heightDp.dp)
                        )
                    }
                }
            }
        }
    }

    val activeTuneId = tuningWidgetId
    val activeTuneSpec = if (activeTuneId != null && activeTuneId in widgetIds) {
        specsById[activeTuneId] ?: RefuelingGridConstants.defaultSpec(activeTuneId, isLandscape)
    } else {
        null
    }

    if (onDockedTuneChange != null) {
        LaunchedEffect(activeTuneId, activeTuneSpec, gridGapDp, isGridMode, isLandscape) {
            if (activeTuneId != null && activeTuneSpec != null) {
                onDockedTuneChange(
                    DockedWidgetTuneData(
                        widgetId = activeTuneId,
                        widgetLabel = resolveLabel(activeTuneId),
                        spec = activeTuneSpec,
                        gridGapDp = gridGapDp,
                        isGridMode = isGridMode,
                        onSpecChange = { updated ->
                            val clamped = updated.clamped()
                            specsById[activeTuneId] = clamped
                            RefuelingWidgetSpecsStore.saveSpec(prefs, isLandscape, clamped)
                        },
                        onGapChange = { newGap ->
                            val clampedGap = newGap.coerceIn(0, RefuelingGridConstants.MAX_GAP_DP)
                            gridGapDp = clampedGap
                            RefuelingWidgetSpecsStore.setGridGapDp(prefs, clampedGap)
                        },
                        onResetSpec = {
                            val reset = RefuelingWidgetSpecsStore.resetSpec(prefs, activeTuneId, isLandscape)
                            specsById[activeTuneId] = reset
                            if (isGridMode) {
                                gridGapDp = RefuelingGridConstants.DEFAULT_GAP_DP
                                RefuelingWidgetSpecsStore.setGridGapDp(prefs, RefuelingGridConstants.DEFAULT_GAP_DP)
                            }
                        },
                        onDismiss = { tuningWidgetId = null }
                    )
                )
            } else {
                onDockedTuneChange(null)
            }
        }
    } else if (activeTuneId != null && activeTuneSpec != null) {
        RefuelingWidgetTuneModal(
            widgetLabel = resolveLabel(activeTuneId),
            spec = activeTuneSpec,
            gridGapDp = gridGapDp,
            isGridMode = isGridMode,
            isLandscape = isLandscape,
            onSpecChange = { updated ->
                val clamped = updated.clamped()
                specsById[activeTuneId] = clamped
                RefuelingWidgetSpecsStore.saveSpec(prefs, isLandscape, clamped)
            },
            onGapChange = { newGap ->
                val clampedGap = newGap.coerceIn(0, RefuelingGridConstants.MAX_GAP_DP)
                gridGapDp = clampedGap
                RefuelingWidgetSpecsStore.setGridGapDp(prefs, clampedGap)
            },
            onResetSpec = {
                val reset = RefuelingWidgetSpecsStore.resetSpec(prefs, activeTuneId, isLandscape)
                specsById[activeTuneId] = reset
                if (isGridMode) {
                    gridGapDp = RefuelingGridConstants.DEFAULT_GAP_DP
                    RefuelingWidgetSpecsStore.setGridGapDp(prefs, RefuelingGridConstants.DEFAULT_GAP_DP)
                }
            },
            onDismiss = { tuningWidgetId = null }
        )
    }

    val deleteId = pendingDeleteWidgetId
    if (deleteId != null && deleteId in widgetIds) {
        RefuelingDeleteConfirmDialog(
            widgetLabel = resolveLabel(deleteId),
            onConfirmDelete = {
                RefuelingWidgetSpecsStore.removeSpec(prefs, deleteId)
                pendingDeleteWidgetId = null
                onRemoveWidget(deleteId)
            },
            onDismiss = { pendingDeleteWidgetId = null }
        )
    }
}

@Composable
private fun RefuelingGridWidgetCard(
    activity: Activity,
    placed: PlacedRefuelingWidget,
    totalWidgets: Int,
    isEditMode: Boolean,
    isCompact: Boolean,
    cardShape: RoundedCornerShape,
    deckTheme: DeckThemeSnapshot,
    accent: Color,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onReorderWidget: (Int, Int) -> Unit,
    onTuneWidget: () -> Unit,
    onRequestRemoveWidget: () -> Unit,
    onReconfigureWidget: ((Int) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val widgetId = placed.widgetId
    val index = placed.index
    val spec = placed.spec

    Box(
        modifier = modifier.clip(cardShape)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = spec.opacity / 100f }
                .clip(cardShape)
                .deckGlassSurface(
                    snapshot = deckTheme,
                    isBlurAvailable = false,
                    isCard = true,
                    withNoise = false,
                    shapeOverride = cardShape
                )
        )

        if (appWidgetHost != null && appWidgetManager != null) {
            AppWidgetContainerView(
                activity = activity,
                widgetId = widgetId,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = spec.padLeftDp.dp,
                        top = spec.padTopDp.dp,
                        end = spec.padRightDp.dp,
                        bottom = spec.padBottomDp.dp
                    ),
                isEditMode = isEditMode,
                isStackMode = false
            )
        }

        if (isEditMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(1.5.dp, accent.copy(alpha = 0.78f), cardShape)
            )

            if (placed.widthDp >= 120f) {
                RefuelingEditSlotBadge(
                    text = String.format(Locale.US, "%02d · %dx%d", index + 1, spec.wCells, spec.hCells),
                    accent = accent,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (isCompact) 4.dp else 6.dp)
                )
            }

            val hasConfig = remember(widgetId) {
                appWidgetManager?.getAppWidgetInfo(widgetId)?.configure != null
            }
            TacticalWidgetEditControls(
                canMoveBack = index > 0,
                canMoveForward = index < totalWidgets - 1,
                onMoveBack = { onReorderWidget(index, index - 1) },
                onMoveForward = { onReorderWidget(index, index + 1) },
                onRemove = onRequestRemoveWidget,
                onConfigure = if (hasConfig) {
                    { onReconfigureWidget?.invoke(widgetId) }
                } else null,
                onTune = onTuneWidget,
                isCompact = isCompact,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isCompact) 4.dp else 6.dp)
            )
        }
    }
}

@Composable
private fun RefuelingEditSlotBadge(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.56f))
            .border(1.dp, accent.copy(alpha = 0.48f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            softWrap = false
        )
    }
}
