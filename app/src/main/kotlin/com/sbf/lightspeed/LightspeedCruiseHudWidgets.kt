package com.sbf.lightspeed

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.provider.Settings
import android.widget.Toast
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.defaultPrefs
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val hudStripRect = RectF()
private val hudContentBounds = RectF()
private val hudSlot1Rect = RectF()
private val hudSlot2Rect = RectF()

/**
 * Calculates safe top inset below the status bar, display cutout, and top sensor strip.
 */
internal fun LightspeedCruiseOverlay.getSafeHudTopOffset(): Float {
    val dp = resources.displayMetrics.density
    val isLandscape = width > height
    val sensorThicknessDp = (if (isLandscape) {
        prefs().getInt(LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE, 35)
    } else {
        prefs().getInt(LightspeedPreferences.KEY_STATUSBAR_THICKNESS, 35)
    }).coerceIn(10, 52)
    val sensorPx = sensorThicknessDp * dp

    val cutoutBottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        rootWindowInsets?.displayCutout?.boundingRectTop?.bottom?.toFloat()
            ?: LightspeedNotchOverlay.cachedCutoutRect?.bottom?.toFloat() ?: 0f
    } else {
        LightspeedNotchOverlay.cachedCutoutRect?.bottom?.toFloat() ?: 0f
    }

    val isSensorEnabled = prefs().getBoolean(LightspeedPreferences.KEY_STATUSBAR_ENABLED, false)
    val sensorSafe = if (isSensorEnabled) sensorPx + 2f * dp else 0f
    val cutoutSafe = if (cutoutBottom > 0f) cutoutBottom + 2f * dp else 0f
    val baseSafeTop = if (isLandscape) 16f * dp else 34f * dp
    return maxOf(baseSafeTop, cutoutSafe, sensorSafe).coerceIn(16f * dp, 76f * dp)
}

internal fun LightspeedCruiseOverlay.getHudScale(): Float {
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()
    val swDp = minOf(w, h) / dp
    val isLandscape = w > h
    val isTablet = swDp >= 600f

    return when {
        isTablet && isLandscape -> 1.22f
        isTablet -> 1.10f
        isLandscape -> 1.12f
        else -> 1.0f
    }
}

/**
 * Scales card-internal density smoothly downward when a card's width or height is smaller than
 * its reference dashboard layout size so headers, gauges, and text columns never overlap.
 */
internal fun resolveCardRenderDensity(
    bounds: RectF,
    baseDensity: Float,
    minLogicalW: Float = 300f,
    minLogicalH: Float = 128f
): Float {
    val wFit = bounds.width() / (minLogicalW * baseDensity).coerceAtLeast(1f)
    val hFit = bounds.height() / (minLogicalH * baseDensity).coerceAtLeast(1f)
    val fitScale = minOf(wFit, hFit, 1f).coerceIn(0.64f, 1f)
    return baseDensity * fitScale
}

internal fun LightspeedCruiseOverlay.computeHudSlotBounds() {
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()
    val isLandscape = w > h
    val isTablet = (minOf(w, h) / dp) >= 600f
    val cat = cachedCategories.getOrNull(activeCatIndex)
    val catId = cat?.id ?: "comm"
    cachedHudBoundCatId = catId
    cachedHudBoundLayer = currentLayer
    cachedHudBoundEditMode = isHudEditMode
    cachedHudBoundLeftFlank = isOpenedFromLeftFlank
    cachedHudBoundW = width
    cachedHudBoundH = height

    val allItems = CategoryHudManager.getHudItems(context, catId, isLandscape)
    val isCruiseLayer = (currentLayer == CruiseLayer.CATEGORY)
    val visibleItems = if (isHudEditMode) {
        allItems
    } else {
        allItems.filter { CategoryHudManager.isVisibleInLayer(it, isCruiseLayer) }
    }
    activeCategoryHudItems.clear()
    activeCategoryHudItems.addAll(visibleItems)

    if (activeCategoryHudItems.isEmpty() && !isHudEditMode) {
        hudSlot1Rect.set(0f, 0f, 0f, 0f)
        hudSlot2Rect.set(0f, 0f, 0f, 0f)
        hudStripRect.set(0f, 0f, 0f, 0f)
        hudContentBounds.set(0f, 0f, 0f, 0f)
        hudMaxScroll = 0f
        hudScrollOffset = 0f
        return
    }

    val safeTop = getSafeHudTopOffset()
    val topToolbarY = safeTop
    val topY = if (isHudEditMode) topToolbarY + 38f * dp else safeTop
    val gapDp = context.defaultPrefs().getInt(LightspeedPreferences.KEY_CATEGORY_HUD_GAP_DP, LightspeedPreferences.DEFAULT_CATEGORY_HUD_GAP_DP).toFloat()
    val isFarSplit = isFarHalfSplitMode()

    val overallBounds = CategoryHudManager.computeLayout(
        activeCategoryHudItems, w, topY, dp, getHudScale(), isLandscape, gapDp,
        isFarHalfSplit = isFarSplit,
        isOpenedFromLeftFlank = isOpenedFromLeftFlank
    )

    hudContentBounds.set(overallBounds)

    val maxAllowedHudHeight = if (isHudEditMode) h * 0.88f else h * 0.48f
    val rawHudBottomLimit = if (isFarSplit || isHudEditMode) {
        (h * 0.94f).coerceAtLeast(topY + 120f * dp)
    } else {
        minOf(topY + maxAllowedHudHeight, h * 0.58f)
    }
    val hudBottomLimit = if (shouldShowCategoryCruiseRotatingTip()) {
        minOf(rawHudBottomLimit, getCategoryCruiseTipReserveTopY() - 4f * dp).coerceAtLeast(topY + 120f * dp)
    } else {
        rawHudBottomLimit
    }
    val visibleBottom = if (overallBounds.bottom > hudBottomLimit) {
        hudBottomLimit
    } else {
        overallBounds.bottom
    }
    val nextMaxScroll = if (activeCategoryHudItems.isEmpty()) 0f else (overallBounds.bottom - visibleBottom).coerceAtLeast(0f)
    hudMaxScroll = nextMaxScroll
    hudScrollOffset = hudScrollOffset.coerceIn(0f, nextMaxScroll)

    if (isHudEditMode) {
        val marginX = if (isTablet) 28f * dp else if (isLandscape) 20f * dp else 16f * dp
        val stripStartX = if (isFarSplit) {
            if (isOpenedFromLeftFlank) (w * 0.5f) + marginX else marginX
        } else marginX
        val stripEndX = if (isFarSplit) {
            if (isOpenedFromLeftFlank) w - marginX else (w * 0.5f) - marginX
        } else w - marginX

        if (activeCategoryHudItems.isEmpty()) {
            hudStripRect.set(stripStartX, topToolbarY, stripEndX, topToolbarY + 110f * dp)
            hudContentBounds.set(hudStripRect)
        } else {
            hudStripRect.set(stripStartX, topToolbarY, stripEndX, visibleBottom)
        }
    } else {
        hudStripRect.set(overallBounds.left, overallBounds.top, overallBounds.right, visibleBottom)
    }

    if (activeCategoryHudItems.isNotEmpty()) {
        hudSlot1Rect.set(activeCategoryHudItems[0].bounds)
    } else {
        hudSlot1Rect.set(0f, 0f, 0f, 0f)
    }

    if (activeCategoryHudItems.size > 1) {
        hudSlot2Rect.set(activeCategoryHudItems[1].bounds)
    } else {
        hudSlot2Rect.set(0f, 0f, 0f, 0f)
    }

    if (isHudTuneModalOpen) {
        hudTuneTargetItem?.let { target ->
            activeCategoryHudItems.firstOrNull { it.id == target.id }?.let { fresh ->
                hudTuneTargetItem = fresh
            }
        }
    }
}

internal fun LightspeedCruiseOverlay.getTopHudSlot1Bounds(): RectF {
    computeHudSlotBounds()
    return hudSlot1Rect
}

internal fun LightspeedCruiseOverlay.getTopHudSlot2Bounds(): RectF {
    computeHudSlotBounds()
    return hudSlot2Rect
}

internal fun LightspeedCruiseOverlay.getTopHudStripBounds(): RectF {
    computeHudSlotBounds()
    return hudStripRect
}

internal fun LightspeedCruiseOverlay.getHudContentBounds(): RectF {
    computeHudSlotBounds()
    return hudContentBounds
}

internal fun LightspeedCruiseOverlay.getCategoryGridTop(catId: String): Float {
    if (isFarHalfSplitMode()) {
        val dp = resources.displayMetrics.density
        return getSafeHudTopOffset() + 4f * dp
    }
    val bounds = getTopHudStripBounds()
    val dp = resources.displayMetrics.density
    val hF = height.toFloat().coerceAtLeast(100f)
    return if (bounds.height() > 10f) {
        bounds.bottom + 10f * dp
    } else {
        if (width > height) 20f * dp else hF * 0.08f
    }
}

/**
 * Recomputes HUD widget slot bounds, clamps HUD scroll, updates AppWidget views, and rebuilds the
 * app grid layout below the updated widget area so adding, removing, resizing, or reopening widgets
 * immediately expands or springs back the app grid space.
 */
internal fun LightspeedCruiseOverlay.refreshHudAndAppGridLayout() {
    computeHudSlotBounds()
    hudScrollOffset = hudScrollOffset.coerceIn(0f, hudMaxScroll)
    updateCategoryAppWidgetView()
    categoryGridCache.clear()
    categoryHeightCache.clear()
    if (activeCatIndex in cachedCategories.indices) {
        val catId = cachedCategories[activeCatIndex].id
        if (cachedApps.isEmpty()) {
            cachedApps = dataBridge.getAppsForCategory(catId)
            categoryAppsCache[catId] = cachedApps
        }
        buildPackedGridLayout()
        categoryGridCache[catId] = ArrayList(placedAppsList)
        categoryHeightCache[catId] = totalGridContentHeight
        val gTop = getCategoryGridTop(catId)
        val gBot = if (shouldShowCategoryCruiseRotatingTip()) {
            getCategoryCruiseTipReserveTopY().coerceAtMost(height.toFloat() * 0.98f)
        } else {
            height.toFloat() * 0.98f
        }
        val gScope = (gBot - gTop).coerceAtLeast(1f)
        val maxAppScroll = kotlin.math.max(0f, totalGridContentHeight - gScope)
        viewportScrollOffset = viewportScrollOffset.coerceIn(0f, maxAppScroll)
    }
    invalidate()
}

internal fun LightspeedCruiseOverlay.handleTopHudStripTap(x: Float, y: Float): Boolean {
    if (isHudEditMode) {
        return handleHudEditModeTap(x, y)
    }

    val cat = cachedCategories.getOrNull(activeCatIndex) ?: return false
    val catId = cat.id

    computeHudSlotBounds()

    val tapY = y + hudScrollOffset
    for (item in activeCategoryHudItems) {
        if (item.bounds.contains(x, tapY)) {
            return handleSingleHudCardTap(x, tapY, item, catId, cat.label, slotIndex = item.slotIndex)
        }
    }

    return false
}

internal fun LightspeedCruiseOverlay.handleSingleHudCardTap(
    x: Float,
    y: Float,
    item: CategoryHudItem,
    catId: String,
    catLabel: String,
    slotIndex: Int
): Boolean {
    val bounds = item.bounds
    val mode = item.type
    val dp = resources.displayMetrics.density * getHudScale()

    when (mode) {
        LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> {
            if (!LightspeedUsageStats.hasUsageAccess(context)) {
                triggerHardwareHaptic(20, 100)
                dismissOverlay()
                try {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                } catch (_: Exception) {}
                return true
            }
            val cardDp = resolveCardRenderDensity(bounds, dp)
            val chipRowBottom = bounds.top + 33f * cardDp
            if (y <= chipRowBottom) {
                val infoHit = hudChipInfoRects[slotIndex]?.takeIf { !it.isEmpty }
                    ?: RectF(bounds.right - 32f * cardDp, bounds.top, bounds.right - 12f * cardDp, chipRowBottom)
                val scopeHit = hudChipPrimaryRects[slotIndex]?.takeIf { !it.isEmpty }
                    ?: RectF(infoHit.left - 92f * cardDp, bounds.top, infoHit.left - 4f * cardDp, chipRowBottom)
                val spanHit = hudChipSecondaryRects[slotIndex]?.takeIf { !it.isEmpty }

                if (RectF(infoHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    triggerHardwareHaptic(18, 90)
                    isScreenTimeInfoBubbleOpen = !isScreenTimeInfoBubbleOpen
                    invalidate()
                    return true
                } else if (RectF(scopeHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    LightspeedCruiseHudPods.cycleTimeScope(context, catId, slotIndex)
                    triggerHardwareHaptic(16, 80)
                    invalidate()
                    return true
                } else if (spanHit != null && RectF(spanHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    LightspeedCruiseHudPods.cycleTimeSpan(context, catId, slotIndex)
                    triggerHardwareHaptic(16, 80)
                    invalidate()
                    return true
                }
            }

            val timelineTop = bounds.bottom - 31f * cardDp
            if (y in timelineTop..bounds.bottom) {
                triggerHardwareHaptic(20, 100)
                openInPlaceCountdownModal(catId, slotIndex)
                return true
            }

            val ignoreHit = screenTimeIgnoreBtnRects[slotIndex]
            if (ignoreHit != null && !ignoreHit.isEmpty && RectF(ignoreHit).apply { inset(-3f * cardDp, -3f * cardDp) }.contains(x, y)) {
                triggerHardwareHaptic(18, 90)
                dismissOverlay()
                try {
                    val intent = Intent(context, com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.EXTRA_MODE, com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.MODE_SCREEN_TIME_IGNORE)
                        putExtra(com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.EXTRA_RETURN_CATEGORY_ID, catId)
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
                return true
            }

            val wkHit = screenTimeWkBarRects[slotIndex]
            if (wkHit != null && !wkHit.isEmpty && RectF(wkHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                val nextMode = LightspeedCruiseHudPods.cycleWeekCycleMode(context)
                triggerHardwareHaptic(16, 80)
                Toast.makeText(
                    context,
                    lsString(R.string.hud_wk_cycle_toast, LightspeedCruiseHudPods.weekCycleFullLabel(nextMode)),
                    Toast.LENGTH_SHORT
                ).show()
                invalidate()
                return true
            }

            val wellbeingHit = screenTimeWellbeingBtnRects[slotIndex]
            if (wellbeingHit != null && !wellbeingHit.isEmpty && RectF(wellbeingHit).apply { inset(-3f * cardDp, -3f * cardDp) }.contains(x, y)) {
                triggerHardwareHaptic(20, 100)
                dismissOverlay()
                uiHandler.postDelayed({
                    com.sbf.lightspeed.system.NativeSettingsLauncher.openDigitalWellbeing(context)
                }, 50L)
                return true
            }

            // Consume non-interactive taps inside the widget's bounds so pinned view stays open.
            return true
        }
        LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> {
            val cardDp = resolveCardRenderDensity(bounds, dp)
            val chipRowBottom = bounds.top + 33f * cardDp
            val bMode = LightspeedCruiseHudPods.getBatteryMode(context, catId, slotIndex)
            if (y <= chipRowBottom) {
                val hasSpan = (bMode == "apps" || bMode == "system")
                val hasInfo = (bMode == "apps" || bMode == "system")
                val infoHit = if (hasInfo) hudChipInfoRects[slotIndex]?.takeIf { !it.isEmpty } else null
                val viewHit = hudChipPrimaryRects[slotIndex]?.takeIf { !it.isEmpty }
                    ?: RectF(bounds.right - 90f * cardDp, bounds.top, bounds.right - 12f * cardDp, chipRowBottom)
                val spanHit = hudChipSecondaryRects[slotIndex]?.takeIf { !it.isEmpty }
                    ?: if (hasSpan) RectF(viewHit.left - 80f * cardDp, bounds.top, viewHit.left - 4f * cardDp, chipRowBottom) else null

                if (infoHit != null && RectF(infoHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    triggerHardwareHaptic(18, 90)
                    batterySpanInfoBubbleRect.set(infoHit)
                    val curSpan = LightspeedCruiseHudPods.getBatterySpan(context, catId, slotIndex)
                    batterySpanInfoKey = if (bMode == "system") {
                        if (LightspeedCruiseHudPods.SYSTEM_SPAN_OPTIONS.any { it.first == curSpan }) curSpan else "today"
                    } else curSpan
                    batterySpanInfoMode = bMode
                    isBatterySpanInfoBubbleOpen = !isBatterySpanInfoBubbleOpen
                    invalidate()
                    return true
                } else if (RectF(viewHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    LightspeedCruiseHudPods.cycleBatteryMode(context, catId, slotIndex)
                    triggerHardwareHaptic(16, 80)
                    invalidate()
                    return true
                } else if (hasSpan && spanHit != null && RectF(spanHit).apply { inset(-3f * cardDp, -4f * cardDp) }.contains(x, y)) {
                    LightspeedCruiseHudPods.cycleBatterySpan(context, catId, slotIndex)
                    triggerHardwareHaptic(16, 80)
                    invalidate()
                    return true
                }
            } else if ((bMode == "apps" || bMode == "chart") && vitalsBatterySettingsBtnRects[slotIndex]?.let { !it.isEmpty && RectF(it).apply { inset(-4f * cardDp, -4f * cardDp) }.contains(x, y) } == true) {
                triggerHardwareHaptic(20, 100)
                dismissOverlay()
                uiHandler.postDelayed({
                    com.sbf.lightspeed.system.NativeSettingsLauncher.openBatteryUsage(context)
                }, 50L)
                return true
            } else if (bMode == "vitals" && vitalsIntStorageBarRects[slotIndex]?.let { !it.isEmpty && it.contains(x, y) } == true) {
                triggerHardwareHaptic(20, 100)
                openVitalsTaskManagerModal(initialTab = "storage", storageVolumeFilter = "INT")
                return true
            } else if (bMode == "vitals" && vitalsExtStorageBarRects[slotIndex]?.let { !it.isEmpty && it.contains(x, y) } == true) {
                triggerHardwareHaptic(20, 100)
                openVitalsTaskManagerModal(initialTab = "storage", storageVolumeFilter = "SD")
                return true
            } else if (bMode == "vitals" && y <= bounds.top + 96f * cardDp) {
                val initTab = if (x < bounds.left + bounds.width() / 3f) "cpu" else "ram"
                triggerHardwareHaptic(20, 100)
                openVitalsTaskManagerModal(initialTab = initTab)
                return true
            } else if (bMode == "system") {
                val mappedNetSpan = LightspeedNetworkUsageAnalyzer.mapSystemHudSpanToAnalyzerSpan(
                    LightspeedCruiseHudPods.getBatterySpan(context, catId, slotIndex)
                )
                if (vitalsCellularRowRects[slotIndex]?.let { !it.isEmpty && it.contains(x, y) } == true) {
                    triggerHardwareHaptic(20, 100)
                    openVitalsTaskManagerModal(
                        initialTab = "network",
                        networkTransportFilter = "CELL",
                        networkSpanKey = mappedNetSpan
                    )
                    return true
                } else if (vitalsWifiRowRects[slotIndex]?.let { !it.isEmpty && it.contains(x, y) } == true) {
                    triggerHardwareHaptic(20, 100)
                    openVitalsTaskManagerModal(
                        initialTab = "network",
                        networkTransportFilter = "WIFI",
                        networkSpanKey = mappedNetSpan
                    )
                    return true
                } else if (y <= bounds.top + 64f * cardDp) {
                    triggerHardwareHaptic(20, 100)
                    openVitalsTaskManagerModal(initialTab = "wakelocks")
                    return true
                } else {
                    LightspeedCruiseHudPods.refreshTelemetryAsync(context, force = true, activeCategoryId = catId, activeModes = setOf(LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM))
                    triggerHardwareHaptic(14, 70)
                    invalidate()
                    return true
                }
            } else if (bMode == "chart" && y >= bounds.top + 100f * cardDp && x <= bounds.centerX()) {
                triggerHardwareHaptic(20, 100)
                openVitalsTaskManagerModal(initialTab = "cpu")
                return true
            } else {
                LightspeedCruiseHudPods.refreshTelemetryAsync(context, force = true)
                triggerHardwareHaptic(14, 70)
                invalidate()
                return true
            }
            return false
        }
        LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> {
            if (item.widgetId <= 0) {
                triggerHardwareHaptic(22, 110)
                openCategoryWidgetPickerDialog(catId, catLabel, slotIndex)
                return true
            }
            return true
        }
        LightspeedCruiseHudPods.MODE_APP_AND_CAT,
        LightspeedCruiseHudPods.MODE_LS_PRIVACY,
        LightspeedCruiseHudPods.MODE_APP_ONLY -> {
            if (!privacyScoreInfoRect.isEmpty && privacyScoreInfoRect.contains(x, y)) {
                triggerHardwareHaptic(18, 90)
                isPrivacyScoreInfoBubbleOpen = !isPrivacyScoreInfoBubbleOpen
                invalidate()
                return true
            }
            if (!hudAlertsPillRect.isEmpty && hudAlertsPillRect.contains(x, y)) {
                triggerHardwareHaptic(20, 100)
                openAlertsHistoryModal(activeItem?.packageName)
                return true
            }
            if (!hudFirewallPillRect.isEmpty && hudFirewallPillRect.contains(x, y)) {
                val target = activeItem
                if (target != null) {
                    triggerHardwareHaptic(22, 110)
                    LightspeedCruiseHudPods.toggleAppFirewall(context, target.packageName, target.label)
                    invalidate()
                } else {
                    triggerHardwareHaptic(22, 110)
                    openFirewallManagerModal()
                }
                return true
            }

            // Consume non-interactive taps inside the widget's bounds so pinned view stays open.
            return true
        }
        else -> {
            return true
        }
    }
}

internal fun LightspeedCruiseOverlay.openCategoryWidgetPickerDialog(
    categoryId: String,
    categoryLabel: String,
    slotIndex: Int = 1
) {
    dismissOverlay()
    uiHandler.postDelayed({
        try {
            val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                action = CockpitDialogActivity.ACTION_PICK_CATEGORY_HUD_WIDGET
                putExtra(CockpitDialogActivity.EXTRA_CATEGORY_ID, categoryId)
                putExtra(CockpitDialogActivity.EXTRA_CATEGORY_LABEL, categoryLabel)
                putExtra(CockpitDialogActivity.EXTRA_HUD_SLOT_INDEX, slotIndex)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }, 50L)
}

internal fun LightspeedCruiseOverlay.openTimelineCountdownDialog(categoryId: String) {
    dismissOverlay()
    uiHandler.postDelayed({
        try {
            val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                action = CockpitDialogActivity.ACTION_TIMELINE_COUNTDOWN
                putExtra(CockpitDialogActivity.EXTRA_RETURN_CATEGORY_ID, categoryId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }, 50L)
}

internal fun LightspeedCruiseOverlay.drawCategoryTopHudStrip(
    canvas: Canvas,
    w: Float,
    density: Float,
    alphaFactor: Float = 1f
) {
    val cat = cachedCategories.getOrNull(activeCatIndex)
    val catId = cat?.id ?: "comm"
    val isLandscape = width > height

    if (cachedHudBoundCatId != catId ||
        cachedHudBoundLayer != currentLayer ||
        cachedHudBoundEditMode != isHudEditMode ||
        cachedHudBoundLeftFlank != isOpenedFromLeftFlank ||
        cachedHudBoundW != width ||
        cachedHudBoundH != height
    ) {
        computeHudSlotBounds()
    }

    if (activeCategoryHudItems.isEmpty() && !isHudEditMode) {
        updateCategoryAppWidgetView()
        return
    }

    val activeModes = activeCategoryHudItems.mapTo(mutableSetOf()) {
        LightspeedCruiseHudPods.normalizeHudMode(it.type)
    }
    LightspeedCruiseHudPods.onHudFrameDrawn(catId)
    LightspeedCruiseHudPods.onSnapshotUpdated = { postInvalidate() }
    LightspeedCruiseHudPods.refreshTelemetryAsync(
        context = context,
        activeCategoryId = catId,
        activeModes = activeModes
    )
    val hasTelemetryWidget = activeModes.any {
        it == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME ||
            it == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM ||
            it == LightspeedCruiseHudPods.MODE_APP_AND_CAT
    }
    uiHandler.removeCallbacks(hudLiveRefreshRunnable)
    if (hasTelemetryWidget &&
        (currentLayer == CruiseLayer.CATEGORY ||
            currentLayer == CruiseLayer.GRID ||
            currentLayer == CruiseLayer.STICKY_PIN)
    ) {
        uiHandler.postDelayed(hudLiveRefreshRunnable, 5_000L)
    }
    val hasPendingAndroidWidget = activeCategoryHudItems.any { item ->
        item.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET &&
            item.widgetId > 0 &&
            !isAppWidgetReadyAndVisible(item.widgetId)
    }
    if (LightspeedCruiseHudPods.isDigitFlipActive() || hasPendingAndroidWidget) {
        postInvalidateOnAnimation()
    }

    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val accent = ensureHighContrastColor(tintColor)
    val effectiveDensity = density * getHudScale()
    val dp = resources.displayMetrics.density

    val isFarSplit = isFarHalfSplitMode()
    val hudStartX = if (isFarSplit) {
        if (isOpenedFromLeftFlank) (w * 0.5f) else 0f
    } else 0f
    val hudEndX = if (isFarSplit) {
        if (isOpenedFromLeftFlank) w else (w * 0.5f)
    } else w
    val hudTopLimit = getSafeHudTopOffset()
    val rawHudBottomLimit = if (isFarSplit || isHudEditMode) {
        height.toFloat() * 0.94f
    } else if (hudMaxScroll > 0f) {
        hudStripRect.bottom + 2f * dp
    } else {
        hudStripRect.bottom + 10f * dp
    }
    val hudBottomLimit = if (shouldShowCategoryCruiseRotatingTip()) {
        minOf(rawHudBottomLimit, getCategoryCruiseTipReserveTopY() - 4f * dp)
    } else {
        rawHudBottomLimit
    }

    val topAlpha = LightspeedHudVectorIcons.computeEdgeFeatherStrength(hudScrollOffset, dp)
    val bottomAlpha = LightspeedHudVectorIcons.computeEdgeFeatherStrength(hudMaxScroll - hudScrollOffset, dp)
    val featherTopPx = 16f * dp
    val featherBottomPx = 18f * dp
    val needsHudFeatherLayer = hudMaxScroll > 2f && (topAlpha > 0.004f || bottomAlpha > 0.004f)
    val hudLayerCount = if (needsHudFeatherLayer) {
        canvas.saveLayer(hudStartX, hudTopLimit, hudEndX, hudBottomLimit, null)
    } else {
        canvas.save()
    }
    canvas.clipRect(hudStartX, hudTopLimit, hudEndX, hudBottomLimit)
    canvas.translate(0f, -hudScrollOffset)

    for (item in activeCategoryHudItems) {
        val b = item.bounds
        if (b.width() > 10f && b.height() > 10f) {
            val padL = item.padLeftDp * dp
            val padT = item.padTopDp * dp
            val padR = item.padRightDp * dp
            val padB = item.padBottomDp * dp
            val cardBounds = RectF(b.left + padL, b.top + padT, b.right - padR, b.bottom - padB)
            val itemCornerRadius = if (item.cornerRadiusDp >= 0) item.cornerRadiusDp * dp else categoryHudCornerRadiusPx()
            val itemAlpha = (alphaFactor * (item.opacity.coerceIn(10, 100) / 100f)).coerceIn(0.05f, 1.0f)

            val saveCount = if (itemAlpha < 0.99f) {
                canvas.saveLayerAlpha(cardBounds, (255 * itemAlpha).toInt().coerceIn(10, 255))
            } else {
                canvas.save()
            }
            try {
                drawSingleHudCard(
                    canvas, cardBounds, item.type, catId, cat?.label ?: "CRUISE", accent, effectiveDensity, 1f,
                    slotIndex = item.slotIndex, boundWidgetId = item.widgetId, cornerRadiusOverride = itemCornerRadius,
                    titleFontSizeSp = item.titleFontSizeSp, subtitleFontSizeSp = item.subtitleFontSizeSp,
                    fontFamily = item.fontFamily, fontKey = item.fontKey
                )
            } finally {
                canvas.restoreToCount(saveCount)
            }
        }
    }
    canvas.translate(0f, hudScrollOffset)
    if (needsHudFeatherLayer) {
        LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
            canvas = canvas,
            left = hudStartX,
            top = hudTopLimit,
            right = hudEndX,
            bottom = hudBottomLimit,
            featherTopPx = featherTopPx,
            featherBottomPx = featherBottomPx,
            density = dp,
            seed = 7331,
            topAlpha = topAlpha,
            bottomAlpha = bottomAlpha
        )
    }
    canvas.restoreToCount(hudLayerCount)

    if (!widgetSettlePending) {
        updateCategoryAppWidgetView()
    }
}

internal fun LightspeedCruiseOverlay.drawSingleHudCard(
    canvas: Canvas,
    bounds: RectF,
    mode: String,
    catId: String,
    catLabel: String,
    accent: Int,
    density: Float,
    alphaFactor: Float,
    slotIndex: Int,
    boundWidgetId: Int = -1,
    cornerRadiusOverride: Float? = null,
    titleFontSizeSp: Int = 0,
    subtitleFontSizeSp: Int = 0,
    fontFamily: Int = 0,
    fontKey: String = ""
) {
    val cornerRadius = cornerRadiusOverride ?: categoryHudCornerRadiusPx()

    // 3rd-party app widgets (once attached & visible) and Focused App Inspector (app_and_cat) are frameless
    val isAppWidget = (mode == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET)
    val resolvedWidgetId = if (boundWidgetId > 0) boundWidgetId else prefs().getInt(
        if (slotIndex == 2) LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + catId
        else LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + catId, -1
    )
    val isBoundAppWidget = isAppWidget && resolvedWidgetId > 0
    val isWidgetLiveVisible = isBoundAppWidget && isAppWidgetReadyAndVisible(resolvedWidgetId)
    val isFrameless = isWidgetLiveVisible || (mode == LightspeedCruiseHudPods.MODE_APP_AND_CAT)

    if (!isFrameless) {
        // Card Surface Background: use high masking opacity during Category Cruise so scrolling
        // category names sliding underneath never bleed through Lightspeed's own widgets;
        // preserve translucent 190/255 glass look in Grid & Sticky Pin.
        val baseCardAlpha = if (currentLayer == CruiseLayer.CATEGORY) 246 else 190
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb((baseCardAlpha * alphaFactor).toInt().coerceIn(0, 255), 10, 14, 22)
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, highlightPaint)

        // Tactical Border
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1f * density
        highlightPaint.color = Color.argb((95 * alphaFactor).toInt().coerceIn(0, 255), Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, highlightPaint)
    }

    when (mode) {
        LightspeedCruiseHudPods.MODE_APP_ONLY,
        LightspeedCruiseHudPods.MODE_APP_AND_CAT,
        LightspeedCruiseHudPods.MODE_LS_PRIVACY -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            drawFocusedAppInspectorWidget(
                canvas, bounds, catId, catLabel, accent, density, alphaFactor, slotIndex,
                titleFontSizeSp = titleFontSizeSp, subtitleFontSizeSp = subtitleFontSizeSp,
                fontFamily = fontFamily, fontKey = fontKey
            )
        } else {
            drawAppAndCatHudStrip(canvas, bounds, catLabel, accent, density, alphaFactor)
        }
        LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            val cardDensity = resolveCardRenderDensity(bounds, density)
            drawScreenTimeHudWidget(canvas, bounds, catId, accent, cardDensity, alphaFactor, slotIndex)
        } else {
            drawAppAndCatHudStrip(canvas, bounds, catLabel, accent, density, alphaFactor)
        }
        LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            val cardDensity = resolveCardRenderDensity(bounds, density)
            drawBatterySystemHudWidget(canvas, bounds, catId, accent, cardDensity, alphaFactor, slotIndex)
        } else {
            drawAppAndCatHudStrip(canvas, bounds, catLabel, accent, density, alphaFactor)
        }
        LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
            drawAndroidWidgetCardPlaceholder(canvas, bounds, catId, catLabel, accent, density, alphaFactor, slotIndex, boundWidgetId)
        } else {
            drawAppAndCatHudStrip(canvas, bounds, catLabel, accent, density, alphaFactor)
        }
    }
}

internal fun LightspeedCruiseOverlay.drawAppOnlyHudStrip(
    canvas: Canvas,
    bounds: RectF,
    accent: Int,
    density: Float,
    alpha: Float
) {
    val focused = activeItem
    val text = if (focused != null) focused.label.uppercase() else "LIGHTSPEED · READY"
    val sub = if (focused != null) focused.packageName else "HOVER OVER ANY APP TO INSPECT"

    val h = bounds.height()
    val isSingleCell = h < 64f * density
    val textY = if (isSingleCell) bounds.centerY() - 5f * density else bounds.top + 22f * density
    val subY = if (isSingleCell) bounds.centerY() + 11f * density else bounds.top + 39f * density

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = if (isSingleCell) 12.5f * density else 13.5f * density
    textPaint.color = Color.WHITE
    textPaint.alpha = (255 * alpha).toInt()
    canvas.drawText(text, bounds.centerX(), textY, textPaint)

    textPaint.typeface = overlayTypeface(isBold = false)
    textPaint.textSize = if (isSingleCell) 9.2f * density else 10f * density
    textPaint.color = accent
    textPaint.alpha = (210 * alpha).toInt()
    canvas.drawText(sub, bounds.centerX(), subY, textPaint)
}

internal fun LightspeedCruiseOverlay.drawAppAndCatHudStrip(
    canvas: Canvas,
    bounds: RectF,
    categoryLabel: String,
    accent: Int,
    density: Float,
    alpha: Float
) {
    val currentSector = (activeCatIndex + 1).coerceAtLeast(1).toString().padStart(2, '0')
    val totalSectors = cachedCategories.size.coerceAtLeast(1).toString().padStart(2, '0')
    val line1 = lsString(R.string.hud_sector_hdr, currentSector, totalSectors, categoryLabel.uppercase())

    val focused = activeItem
    val line2 = if (focused != null) {
        "FOCUS: ${focused.label.uppercase()}  ·  ${focused.packageName}"
    } else {
        "TAP APP TO LAUNCH  ·  LONG-PRESS FOR OPTIONS"
    }

    val h = bounds.height()
    val isSingleCell = h < 64f * density
    val line1Y = if (isSingleCell) bounds.centerY() - 5f * density else bounds.top + 22f * density
    val line2Y = if (isSingleCell) bounds.centerY() + 11f * density else bounds.top + 39f * density

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = if (isSingleCell) 11.5f * density else 12f * density
    textPaint.color = Color.WHITE
    textPaint.alpha = (255 * alpha).toInt()
    canvas.drawText(line1, bounds.centerX(), line1Y, textPaint)

    textPaint.typeface = overlayTypeface(isBold = false)
    textPaint.textSize = if (isSingleCell) 9.2f * density else 9.8f * density
    textPaint.color = accent
    textPaint.alpha = (220 * alpha).toInt()
    canvas.drawText(line2, bounds.centerX(), line2Y, textPaint)
}
