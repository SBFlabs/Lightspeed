package com.sbf.lightspeed

import com.sbf.lightspeed.system.lsString

import com.sbf.lightspeed.R

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.system.NightlyFeatures
import java.util.Collections

// Cached bounds for HUD edit controls to guarantee surgical hit testing
private val doneBtnRect = RectF()
private val addBtnRect = RectF()
private val hudEmptyCardRect = RectF()
private val modalRect = RectF()
private val modalOptionRects = mutableListOf<Pair<String, RectF>>()

// Cached hit rects for Delete Confirmation Modal
private val deleteModalRect = RectF()
private val deleteCancelBtnRect = RectF()
private val deleteConfirmBtnRect = RectF()

internal class HudCardControlGeometry(
    val isCompact: Boolean,
    val tagRect: RectF,
    val ejectRect: RectF,
    val tuneRect: RectF,
    val configRect: RectF?,
    val nextRect: RectF?,
    val prevRect: RectF?
)

private fun hasWidgetConfigureActivity(context: Context, item: CategoryHudItem): Boolean {
    if (item.type != LightspeedCruiseHudPods.MODE_ANDROID_WIDGET || item.widgetId <= 0) return false
    return try {
        AppWidgetManager.getInstance(context).getAppWidgetInfo(item.widgetId)?.configure != null
    } catch (_: Exception) {
        false
    }
}

internal fun computeHudCardControls(
    b: RectF,
    dp: Float,
    index: Int,
    totalCount: Int,
    hasConfigure: Boolean
): HudCardControlGeometry {
    val isCompact = b.height() < 64f * dp
    val ctlBtnSize = if (isCompact) 22f * dp else 24f * dp
    val ctlH = if (isCompact) ctlBtnSize.coerceAtMost(b.height() - 6f * dp) else ctlBtnSize
    val ctlY = if (isCompact) b.centerY() - (ctlH / 2f) else b.top + 6f * dp
    val gap = 4f * dp

    // 1. Far Right: Eject [✕]
    val ejectLeft = b.right - 6f * dp - ctlBtnSize
    val ejectRect = RectF(ejectLeft, ctlY, ejectLeft + ctlBtnSize, ctlY + ctlH)

    // 2. Tune / Sliders
    val tuneLeft = ejectLeft - ctlBtnSize - gap
    val tuneRect = RectF(tuneLeft, ctlY, tuneLeft + ctlBtnSize, ctlY + ctlH)
    var leftBoundary = tuneLeft

    // 3. Native Widget Config (Pencil) when configure != null
    var configRect: RectF? = null
    if (hasConfigure) {
        val cfgLeft = leftBoundary - ctlBtnSize - gap
        configRect = RectF(cfgLeft, ctlY, cfgLeft + ctlBtnSize, ctlY + ctlH)
        leftBoundary = cfgLeft
    }

    // 4. Reorder buttons [◀] [▶]
    var nextRect: RectF? = null
    if (index < totalCount - 1) {
        val nextLeft = leftBoundary - ctlBtnSize - gap
        nextRect = RectF(nextLeft, ctlY, nextLeft + ctlBtnSize, ctlY + ctlH)
        leftBoundary = nextLeft
    }

    var prevRect: RectF? = null
    if (index > 0) {
        val prevLeft = leftBoundary - ctlBtnSize - gap
        prevRect = RectF(prevLeft, ctlY, prevLeft + ctlBtnSize, ctlY + ctlH)
        leftBoundary = prevLeft
    }

    val tagRightLimit = (leftBoundary - 6f * dp).coerceAtLeast(b.left + 6f * dp)
    val tagRect = RectF(b.left + 6f * dp, ctlY, tagRightLimit, ctlY + ctlH)

    return HudCardControlGeometry(
        isCompact = isCompact,
        tagRect = tagRect,
        ejectRect = ejectRect,
        tuneRect = tuneRect,
        configRect = configRect,
        nextRect = nextRect,
        prevRect = prevRect
    )
}

internal fun LightspeedCruiseOverlay.drawHudEditModeOverlay(
    canvas: Canvas,
    items: List<CategoryHudItem>,
    density: Float,
    accent: Int
) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val isLandscape = width > height
    val isTablet = (minOf(screenW, height.toFloat()) / dp) >= 600f
    val marginX = if (isTablet) 28f * dp else if (isLandscape) 20f * dp else 16f * dp
    val toolbarTop = getSafeHudTopOffset()
    val toolbarH = 34f * dp
    val isFarSplit = isFarHalfSplitMode()
    val tStartX = if (isFarSplit) {
        if (isOpenedFromLeftFlank) (screenW * 0.5f) + marginX else marginX
    } else marginX
    val tEndX = if (isFarSplit) {
        if (isOpenedFromLeftFlank) screenW - marginX else (screenW * 0.5f) - marginX
    } else screenW - marginX
    val toolbarRect = RectF(tStartX, toolbarTop, tEndX, toolbarTop + toolbarH)

    val cornerRadius = 8f * dp

    // 1. Top HUD Edit Mode Toolbar (no global Tune button)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(220, 12, 16, 26)
    canvas.drawRoundRect(toolbarRect, cornerRadius, cornerRadius, highlightPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1f * dp
    highlightPaint.color = Color.argb(110, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(toolbarRect, cornerRadius, cornerRadius, highlightPaint)

    val isCompactPhone = !isTablet && !isLandscape && (screenW / dp < 420f)

    val starCx = toolbarRect.left + 13f * dp
    LightspeedHudVectorIcons.drawVectorDiamondStar(canvas, starCx, toolbarRect.centerY(), 4.5f * dp, accent)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
    textPaint.textSize = (if (isCompactPhone) 8.8f * dp else 10f * dp) * overlayTextScale()
    textPaint.color = Color.WHITE
    textPaint.alpha = 240
    val fm = textPaint.fontMetrics
    val textY = toolbarRect.centerY() - ((fm.descent + fm.ascent) / 2f)
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_hud_edit_mode).ifBlank { "HUD EDIT MODE" }, toolbarRect.left + 21f * dp, textY, textPaint)

    // Right Action Buttons: [+ ADD] and [DONE]
    val btnH = 26f * dp
    val btnY = toolbarRect.centerY() - (btnH / 2f)

    val doneW = if (isCompactPhone) 52f * dp else 58f * dp
    val doneLeft = toolbarRect.right - doneW - 4f * dp
    doneBtnRect.set(doneLeft, btnY, doneLeft + doneW, btnY + btnH)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(doneBtnRect, 5f * dp, 5f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.5f * dp * overlayTextScale()
    textPaint.color = Color.BLACK
    textPaint.alpha = 255
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_done).ifBlank { "DONE" }, doneBtnRect.centerX(), doneBtnRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

    val addW = if (isCompactPhone) 68f * dp else 86f * dp
    val addLeft = doneLeft - addW - 5f * dp
    addBtnRect.set(addLeft, btnY, addLeft + addW, btnY + btnH)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(60, 255, 255, 255)
    canvas.drawRoundRect(addBtnRect, 5f * dp, 5f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1f * dp
    highlightPaint.color = Color.argb(120, 255, 255, 255)
    canvas.drawRoundRect(addBtnRect, 5f * dp, 5f * dp, highlightPaint)

    textPaint.color = Color.WHITE
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_add).ifBlank { "+ ADD" }, addBtnRect.centerX(), addBtnRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

    // 2. Empty state card if no widgets exist
    if (items.isEmpty()) {
        val emptyRect = RectF(tStartX, toolbarRect.bottom + 12f * dp, tEndX, toolbarRect.bottom + 90f * dp)
        hudEmptyCardRect.set(emptyRect)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(160, 14, 18, 28)
        canvas.drawRoundRect(emptyRect, 10f * dp, 10f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1f * dp
        highlightPaint.color = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(emptyRect, 10f * dp, 10f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 11.5f * dp * overlayTextScale()
        textPaint.color = Color.WHITE
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_no_widgets).ifBlank { "[ NO HUD WIDGETS CONFIGURED ]" }, emptyRect.centerX(), emptyRect.centerY() - 6f * dp, textPaint)
        textPaint.typeface = overlayTypeface(isBold = false)
        textPaint.textSize = 9.5f * dp * overlayTextScale()
        textPaint.color = accent
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tap_add_widgets).ifBlank { "Tap here or '+ ADD' above to mount Widgets" }, emptyRect.centerX(), emptyRect.centerY() + 12f * dp, textPaint)
    } else {
        hudEmptyCardRect.set(0f, 0f, 0f, 0f)

        // 3. In-place header controls over each widget card (no W-/W+/H-/H+ bars)
        val scrollTop = toolbarTop + toolbarH + 2f * dp
        val rawScrollBottom = height.toFloat() * 0.96f
        val scrollBottom = if (shouldShowCategoryCruiseRotatingTip()) {
            minOf(rawScrollBottom, getCategoryCruiseTipReserveTopY() - 4f * dp)
        } else {
            rawScrollBottom
        }
        val editLayerCount = canvas.saveLayer(tStartX, scrollTop, tEndX, scrollBottom, null)
        canvas.clipRect(tStartX, scrollTop, tEndX, scrollBottom)
        canvas.save()
        canvas.translate(0f, -hudScrollOffset)
        for ((index, item) in items.withIndex()) {
            val b = item.bounds
            if (b.width() < 10f || b.height() < 10f) continue

            val hasConfig = hasWidgetConfigureActivity(context, item)
            val geom = computeHudCardControls(b, dp, index, items.size, hasConfig)
            val isBeingTuned = isHudTuneModalOpen && hudTuneTargetItem?.id == item.id

            // Semi-transparent edit shield over card
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(if (isBeingTuned) 35 else 65, 0, 0, 0)
            canvas.drawRoundRect(b, 12f * density, 12f * density, highlightPaint)

            // Highlight edit frame
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isBeingTuned) 2.2f * dp else 1.5f * dp
            highlightPaint.color = Color.argb(if (isBeingTuned) 245 else 180, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(b, 12f * density, 12f * density, highlightPaint)

            // Tag badge (omitted for bound 3rd party app widgets to keep widget face clean)
            val isBoundAppWidget = (item.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && item.widgetId > 0)
            if (!isBoundAppWidget && geom.tagRect.width() > 16f * dp && geom.tagRect.height() > 10f * dp) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(210, 16, 20, 30)
                canvas.drawRoundRect(geom.tagRect, 4f * dp, 4f * dp, highlightPaint)

                val typeLabel = hudWidgetDisplayName(item.type, short = geom.isCompact && geom.tagRect.width() < 110f * dp)
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
                textPaint.textSize = (8.5f * dp * overlayTextScale()).coerceAtMost(geom.tagRect.height() * 0.45f)
                textPaint.color = accent
                textPaint.alpha = 240
                canvas.drawText(typeLabel, geom.tagRect.left + 5f * dp, geom.tagRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
            }

            // Eject / Remove Button (red)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(210, 220, 40, 60)
            canvas.drawRoundRect(geom.ejectRect, 4f * dp, 4f * dp, highlightPaint)
            LightspeedHudVectorIcons.drawVectorClose(
                canvas = canvas,
                cx = geom.ejectRect.centerX(),
                cy = geom.ejectRect.centerY(),
                r = (4.2f * dp).coerceAtMost(geom.ejectRect.height() * 0.24f),
                color = Color.WHITE,
                strokeW = 1.6f * dp
            )

            // Tune / Sliders Button
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isBeingTuned) accent else Color.argb(175, 40, 50, 70)
            canvas.drawRoundRect(geom.tuneRect, 4f * dp, 4f * dp, highlightPaint)
            drawSlidersGlyph(canvas, geom.tuneRect, dp, if (isBeingTuned) Color.BLACK else accent)

            // Native Configure Button (Pencil) for 3rd-party widgets with configure != null
            geom.configRect?.let { cr ->
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(175, 40, 50, 70)
                canvas.drawRoundRect(cr, 4f * dp, 4f * dp, highlightPaint)
                drawPencilGlyph(canvas, cr, dp, accent)
            }

            // Move Next
            geom.nextRect?.let { nr ->
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 40, 50, 70)
                canvas.drawRoundRect(nr, 4f * dp, 4f * dp, highlightPaint)
                LightspeedHudVectorIcons.drawVectorArrow(
                    canvas = canvas,
                    cx = nr.centerX(),
                    cy = nr.centerY(),
                    r = (4.0f * dp).coerceAtMost(nr.height() * 0.24f),
                    direction = 2,
                    color = Color.WHITE,
                    strokeW = 1.4f * dp,
                    filledTriangle = true
                )
            }

            // Move Prev
            geom.prevRect?.let { pr ->
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 40, 50, 70)
                canvas.drawRoundRect(pr, 4f * dp, 4f * dp, highlightPaint)
                LightspeedHudVectorIcons.drawVectorArrow(
                    canvas = canvas,
                    cx = pr.centerX(),
                    cy = pr.centerY(),
                    r = (4.0f * dp).coerceAtMost(pr.height() * 0.24f),
                    direction = 3,
                    color = Color.WHITE,
                    strokeW = 1.4f * dp,
                    filledTriangle = true
                )
            }
        }
        canvas.restore()
        LightspeedHudVectorIcons.applyScrollEdgeFeathering(
            canvas = canvas,
            left = tStartX,
            top = scrollTop,
            right = tEndX,
            bottom = scrollBottom,
            scrollOffset = hudScrollOffset,
            maxScroll = hudMaxScroll,
            featherTopPx = 18f * dp,
            featherBottomPx = 24f * dp,
            density = dp,
            seed = 8606
        )
        canvas.restoreToCount(editLayerCount)
    }

    // 4. Modal dialog if [+ ADD] was tapped
    if (isHudAddWidgetMenuOpen) {
        drawHudAddWidgetModal(canvas, density, accent)
    }

    // 5. Non-occluding Per-Widget Tune Panel
    if (isHudTuneModalOpen && hudTuneTargetItem != null) {
        drawHudTuneModal(canvas, density, accent)
    }

    // 6. Safety Delete Confirmation Popup
    if (pendingDeleteHudItem != null) {
        drawHudDeleteConfirmModal(canvas, accent)
    }
}

internal fun hudWidgetDisplayName(type: String, short: Boolean = false): String {
    return if (short) {
        when (type) {
            LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> com.sbf.lightspeed.system.lsString(R.string.hud_short_time).ifBlank { "TIME" }
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> com.sbf.lightspeed.system.lsString(R.string.hud_short_battery).ifBlank { "BATTERY" }
            LightspeedCruiseHudPods.MODE_APP_AND_CAT,
            LightspeedCruiseHudPods.MODE_APP_ONLY,
            LightspeedCruiseHudPods.MODE_LS_PRIVACY -> com.sbf.lightspeed.system.lsString(R.string.hud_short_inspect).ifBlank { "INSPECT" }
            LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> com.sbf.lightspeed.system.lsString(R.string.hud_short_widget).ifBlank { "WIDGET" }
            else -> "HUD"
        }
    } else {
        when (type) {
            LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> com.sbf.lightspeed.system.lsString(R.string.hud_screen_time).ifBlank { "SCREEN TIME" }
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> com.sbf.lightspeed.system.lsString(R.string.hud_battery_sys).ifBlank { "BATTERY & SYS" }
            LightspeedCruiseHudPods.MODE_APP_AND_CAT,
            LightspeedCruiseHudPods.MODE_APP_ONLY,
            LightspeedCruiseHudPods.MODE_LS_PRIVACY -> com.sbf.lightspeed.system.lsString(R.string.hud_focus_inspector).ifBlank { "FOCUS INSPECTOR" }
            LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> com.sbf.lightspeed.system.lsString(R.string.hud_app_widget).ifBlank { "APP WIDGET" }
            else -> type.uppercase()
        }
    }
}

internal fun LightspeedCruiseOverlay.drawHudAddWidgetModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Scrim behind Add Widget modal
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(160, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, highlightPaint)

    val modalW = (320f * dp).coerceAtMost(screenW - 32f * dp)
    val options = if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) {
        listOf(
            LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME to lsString(R.string.hud_opt_st_tel),
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM to lsString(R.string.hud_opt_bat_vit),
            LightspeedCruiseHudPods.MODE_APP_AND_CAT to lsString(R.string.hud_opt_app_insp),
            LightspeedCruiseHudPods.MODE_ANDROID_WIDGET to lsString(R.string.hud_opt_wid_browse)
        )
    } else {
        listOf(
            LightspeedCruiseHudPods.MODE_APP_AND_CAT to lsString(R.string.hud_opt_sec_strip)
        )
    }

    val rowH = 44f * dp
    val headerH = 48f * dp
    val cancelH = 40f * dp
    val modalH = headerH + (rowH * options.size) + cancelH + 16f * dp

    val isFarSplit = isFarHalfSplitMode()
    val halfCenter = if (isFarSplit) {
        if (isOpenedFromLeftFlank) screenW * 0.75f else screenW * 0.25f
    } else {
        screenW / 2f
    }
    val left = (halfCenter - (modalW / 2f)).coerceIn(8f * dp, screenW - modalW - 8f * dp)
    val top = (screenH - modalH) / 2f
    modalRect.set(left, top, left + modalW, top + modalH)
    val colors = resolveTuneThemeColors(dp)
    val themeAccent = colors.accentColor

    drawDeckThemedFlyoutSurface(canvas, modalRect, colors.snapshot, colors.cornerRadiusPx, dp, highlightPaint)

    val modalTitle = lsString(R.string.hud_add_module)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
    textPaint.textSize = 11.5f * dp * overlayTextScale()
    textPaint.color = colors.textPrimary
    val titleHalfW = textPaint.measureText(modalTitle) * 0.5f
    LightspeedHudVectorIcons.drawVectorDiamondStar(canvas, modalRect.centerX() - titleHalfW - 10f * dp, top + 24f * dp, 4.2f * dp, themeAccent)
    canvas.drawText(modalTitle, modalRect.centerX(), top + 28f * dp, textPaint)
    LightspeedHudVectorIcons.drawVectorDiamondStar(canvas, modalRect.centerX() + titleHalfW + 10f * dp, top + 24f * dp, 4.2f * dp, themeAccent)

    modalOptionRects.clear()
    var currentY = top + headerH

    for ((type, label) in options) {
        val rowRect = RectF(left + 12f * dp, currentY, left + modalW - 12f * dp, currentY + rowH - 6f * dp)
        modalOptionRects.add(type to rowRect)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(rowRect, 7f * dp, 7f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) themeAccent else colors.pillBorder
        canvas.drawRoundRect(rowRect, 7f * dp, 7f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 10f * dp * overlayTextScale()
        textPaint.color = if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) themeAccent else colors.textPrimary
        val fm = textPaint.fontMetrics
        canvas.drawText(label, rowRect.left + 12f * dp, rowRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

        currentY += rowH
    }

    val cancelRect = RectF(left + 12f * dp, currentY + 4f * dp, left + modalW - 12f * dp, currentY + 4f * dp + cancelH)
    modalOptionRects.add("cancel" to cancelRect)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, 175, 55, 65)
    canvas.drawRoundRect(cancelRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(115, 255, 115, 125)
    canvas.drawRoundRect(cancelRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 10.5f * dp * overlayTextScale()
    textPaint.color = Color.argb(235, 255, 180, 185)
    val fm = textPaint.fontMetrics
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_cancel).ifBlank { "CANCEL" }, cancelRect.centerX(), cancelRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
}

private fun LightspeedCruiseOverlay.drawHudDeleteConfirmModal(canvas: Canvas, accent: Int) {
    val item = pendingDeleteHudItem ?: return
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val colors = resolveTuneThemeColors(dp)

    // Subtle scrim for delete confirmation safety dialog
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(140, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, highlightPaint)

    val modalW = (280f * dp).coerceAtMost(screenW - 32f * dp)
    val modalH = 128f * dp
    val isFarSplit = isFarHalfSplitMode()
    val halfCenter = if (isFarSplit) {
        if (isOpenedFromLeftFlank) screenW * 0.75f else screenW * 0.25f
    } else {
        screenW / 2f
    }
    val left = (halfCenter - (modalW / 2f)).coerceIn(8f * dp, screenW - modalW - 8f * dp)
    val top = (screenH - modalH) / 2f
    deleteModalRect.set(left, top, left + modalW, top + modalH)

    drawDeckThemedFlyoutSurface(canvas, deleteModalRect, colors.snapshot, colors.cornerRadiusPx, dp, highlightPaint)

    val fm = textPaint.fontMetrics
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 12.5f * dp * overlayTextScale()
    textPaint.color = colors.textPrimary
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_remove_widget).ifBlank { "REMOVE WIDGET?" }, deleteModalRect.centerX(), top + 30f * dp, textPaint)

    textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
    textPaint.textSize = 9.5f * dp * overlayTextScale()
    textPaint.color = colors.accentColor
    canvas.drawText(hudWidgetDisplayName(item.type, short = false), deleteModalRect.centerX(), top + 52f * dp, textPaint)

    val btnH = 36f * dp
    val btnY = deleteModalRect.bottom - btnH - 14f * dp
    val btnW = (modalW - 36f * dp) / 2f

    deleteCancelBtnRect.set(left + 14f * dp, btnY, left + 14f * dp + btnW, btnY + btnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawRoundRect(deleteCancelBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(deleteCancelBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 10.5f * dp * overlayTextScale()
    textPaint.color = colors.textPrimary
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_cancel).ifBlank { "CANCEL" }, deleteCancelBtnRect.centerX(), deleteCancelBtnRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)

    deleteConfirmBtnRect.set(deleteCancelBtnRect.right + 8f * dp, btnY, left + modalW - 14f * dp, btnY + btnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(235, 220, 38, 38)
    canvas.drawRoundRect(deleteConfirmBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.color = Color.WHITE
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_delete).ifBlank { "DELETE" }, deleteConfirmBtnRect.centerX(), deleteConfirmBtnRect.centerY() - ((fm.descent + fm.ascent) / 2f), textPaint)
}

internal fun LightspeedCruiseOverlay.handleHudEditModeTap(x: Float, y: Float): Boolean {
    val dp = resources.displayMetrics.density
    val cat = cachedCategories.getOrNull(activeCatIndex) ?: return false
    val catId = cat.id
    val isLandscape = width > height

    // 0. Delete Confirmation modal takes highest priority
    val pendingDel = pendingDeleteHudItem
    if (pendingDel != null) {
        if (deleteConfirmBtnRect.contains(x, y)) {
            triggerHardwareHaptic(35, 160)
            activeCategoryHudItems.removeAll { it.id == pendingDel.id }
            CategoryHudManager.saveHudItems(context, catId, activeCategoryHudItems)
            if (hudTuneTargetItem?.id == pendingDel.id) {
                closeHudTuneModal()
            }
            pendingDeleteHudItem = null
            refreshHudAndAppGridLayout()
            return true
        }
        if (deleteCancelBtnRect.contains(x, y) || !deleteModalRect.contains(x, y)) {
            triggerHardwareHaptic(15, 60)
            pendingDeleteHudItem = null
            invalidate()
            return true
        }
        return true
    }

    // 1. If Add Widget modal is open
    if (isHudAddWidgetMenuOpen) {
        if (!modalRect.contains(x, y)) {
            isHudAddWidgetMenuOpen = false
            triggerHardwareHaptic(15, 60)
            invalidate()
            return true
        }

        for ((type, rect) in modalOptionRects) {
            val hitRow = RectF(rect).apply { inset(0f, -4f * dp) }
            if (hitRow.contains(x, y)) {
                triggerHardwareHaptic(20, 100)
                isHudAddWidgetMenuOpen = false
                if (type == "cancel") {
                    invalidate()
                    return true
                }
                if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) {
                    openCategoryWidgetPickerDialog(catId, cat.label, -1)
                } else {
                    CategoryHudManager.addHudItem(context, catId, type, isLandscape = isLandscape)
                    refreshHudAndAppGridLayout()
                }
                return true
            }
        }
        return true
    }

    // 2. Done button
    val hitDone = RectF(doneBtnRect).apply { inset(-8f * dp, -12f * dp) }
    if (hitDone.contains(x, y)) {
        triggerHardwareHaptic(20, 90)
        isHudEditMode = false
        closeHudTuneModal()
        refreshHudAndAppGridLayout()
        return true
    }

    // 3. Add Widget button
    val hitAdd = RectF(addBtnRect).apply { inset(-10f * dp, -12f * dp) }
    if (hitAdd.contains(x, y)) {
        triggerHardwareHaptic(20, 90)
        closeHudTuneModal()
        isHudAddWidgetMenuOpen = true
        invalidate()
        return true
    }

    // 3b. Empty state card tap -> opens Add Widget modal
    if (activeCategoryHudItems.isEmpty() && hudEmptyCardRect.contains(x, y)) {
        triggerHardwareHaptic(20, 90)
        isHudAddWidgetMenuOpen = true
        invalidate()
        return true
    }

    // 4. Per-widget in-place header controls
    computeHudSlotBounds()
    val items = activeCategoryHudItems
    val editClipTop = getSafeHudTopOffset() + 34f * dp
    val editClipBottom = height.toFloat() * 0.96f
    val cardY = y + hudScrollOffset
    if (y in editClipTop..editClipBottom) for (i in items.indices) {
        val item = items[i]
        val b = item.bounds
        if (b.width() < 10f || b.height() < 10f) continue

        val hasConfig = hasWidgetConfigureActivity(context, item)
        val geom = computeHudCardControls(b, dp, i, items.size, hasConfig)

        // Eject / Remove [✕] -> show red Delete confirmation popup
        val ejectHit = RectF(geom.ejectRect).apply { inset(-3f * dp, -3f * dp) }
        if (ejectHit.contains(x, cardY)) {
            triggerHardwareHaptic(25, 110)
            pendingDeleteHudItem = item
            invalidate()
            return true
        }

        // Tune / Sliders -> open non-occluding per-widget Tune panel
        val tuneHit = RectF(geom.tuneRect).apply { inset(-5f * dp, -5f * dp) }
        if (tuneHit.contains(x, cardY)) {
            triggerHardwareHaptic(20, 90)
            openHudTuneModal(item)
            return true
        }

        // Native Widget Configure (Pencil)
        geom.configRect?.let { cr ->
            val cfgHit = RectF(cr).apply { inset(-3f * dp, -3f * dp) }
            if (cfgHit.contains(x, cardY)) {
                triggerHardwareHaptic(20, 90)
                openExistingWidgetConfigureActivity(item.widgetId, catId)
                return true
            }
        }

        // Move Next [▶]
        geom.nextRect?.let { nr ->
            val nextHit = RectF(nr).apply { inset(-3f * dp, -3f * dp) }
            if (nextHit.contains(x, cardY)) {
                triggerHardwareHaptic(20, 80)
                moveCategoryHudItemStep(catId, item.id, +1)
                return true
            }
        }

        // Move Prev [◀]
        geom.prevRect?.let { pr ->
            val prevHit = RectF(pr).apply { inset(-3f * dp, -3f * dp) }
            if (prevHit.contains(x, cardY)) {
                triggerHardwareHaptic(20, 80)
                moveCategoryHudItemStep(catId, item.id, -1)
                return true
            }
        }
    }

    // Tapping outside the HUD strip down into the app grid exits HUD edit mode smoothly
    if (y >= getCategoryGridTop(catId)) {
        triggerHardwareHaptic(15, 60)
        isHudEditMode = false
        closeHudTuneModal()
        refreshHudAndAppGridLayout()
        return true
    }

    return true
}

/**
 * Finds the swap target index in the saved category HUD list when moving [itemId] by [stepDelta] (-1 or +1).
 * In Edit Mode all slots are rendered so it steps to the direct neighbor; outside Edit Mode it steps
 * to the nearest neighbor visible in the current layer so movement stays immediately visible.
 */
private fun LightspeedCruiseOverlay.findHudMoveSwapTargetIndex(
    catId: String,
    itemId: String,
    stepDelta: Int
): Triple<MutableList<CategoryHudItem>, Int, Int>? {
    val allItems = CategoryHudManager.getHudItems(context, catId, width > height)
    val idx = allItems.indexOfFirst { it.id == itemId }
    if (idx < 0 || stepDelta == 0) return null
    val targetIdx = if (isHudEditMode) {
        (idx + stepDelta).takeIf { it in allItems.indices }
    } else {
        val isCruiseLayer = (currentLayer == CruiseLayer.CATEGORY)
        var k = idx + stepDelta
        var found: Int? = null
        while (k in allItems.indices) {
            if (CategoryHudManager.isVisibleInLayer(allItems[k], isCruiseLayer)) {
                found = k
                break
            }
            k += stepDelta
        }
        found
    } ?: return null
    return Triple(allItems, idx, targetIdx)
}

/**
 * Finds the best vertical neighbor row target in spatial grid layout for [verticalDir] (-1 = up, +1 = down).
 */
private fun LightspeedCruiseOverlay.findHudVerticalMoveTargetIndex(
    catId: String,
    itemId: String,
    verticalDir: Int
): Triple<MutableList<CategoryHudItem>, Int, Int>? {
    if (verticalDir == 0) return null
    val allItems = CategoryHudManager.getHudItems(context, catId, width > height)
    val idx = allItems.indexOfFirst { it.id == itemId }
    if (idx < 0) return null

    val selfBounds = activeCategoryHudItems.firstOrNull { it.id == itemId }?.bounds
        ?: return findHudMoveSwapTargetIndex(catId, itemId, if (verticalDir < 0) -1 else 1)
    if (selfBounds.isEmpty) {
        return findHudMoveSwapTargetIndex(catId, itemId, if (verticalDir < 0) -1 else 1)
    }

    val minRowSep = 6f * resources.displayMetrics.density
    val selfCenterY = selfBounds.centerY()
    val selfCenterX = selfBounds.centerX()
    val isCruiseLayer = (currentLayer == CruiseLayer.CATEGORY)

    var bestTargetIdx: Int? = null
    var bestDy = Float.MAX_VALUE
    var bestDx = Float.MAX_VALUE

    for (candidateIdx in allItems.indices) {
        if (candidateIdx == idx) continue
        val candidate = allItems[candidateIdx]
        if (!isHudEditMode && !CategoryHudManager.isVisibleInLayer(candidate, isCruiseLayer)) continue
        val cBounds = activeCategoryHudItems.firstOrNull { it.id == candidate.id }?.bounds ?: continue
        if (cBounds.isEmpty) continue

        val isInDirection = if (verticalDir < 0) {
            cBounds.centerY() < selfCenterY - minRowSep
        } else {
            cBounds.centerY() > selfCenterY + minRowSep
        }
        if (!isInDirection) continue

        val dy = kotlin.math.abs(cBounds.centerY() - selfCenterY)
        val dx = kotlin.math.abs(cBounds.centerX() - selfCenterX)
        if (dy < bestDy - 4f || (kotlin.math.abs(dy - bestDy) <= 4f && dx < bestDx)) {
            bestDy = dy
            bestDx = dx
            bestTargetIdx = candidateIdx
        }
    }

    return bestTargetIdx?.let { Triple(allItems, idx, it) }
}

internal fun LightspeedCruiseOverlay.canMoveCategoryHudItem(
    catId: String,
    itemId: String,
    stepDelta: Int
): Boolean = findHudMoveSwapTargetIndex(catId, itemId, stepDelta) != null

internal fun LightspeedCruiseOverlay.canMoveCategoryHudItemVertical(
    catId: String,
    itemId: String,
    verticalDir: Int
): Boolean = findHudVerticalMoveTargetIndex(catId, itemId, verticalDir) != null

private fun LightspeedCruiseOverlay.executeHudItemSwap(
    catId: String,
    itemId: String,
    swapInfo: Triple<MutableList<CategoryHudItem>, Int, Int>?
): CategoryHudItem? {
    val (allItems, idx, targetIdx) = swapInfo ?: return null
    Collections.swap(allItems, idx, targetIdx)
    CategoryHudManager.saveHudItems(context, catId, allItems)
    refreshHudAndAppGridLayout()
    val updated = activeCategoryHudItems.firstOrNull { it.id == itemId }
        ?: allItems.firstOrNull { it.id == itemId }
    if (hudTuneTargetItem?.id == itemId && updated != null) {
        hudTuneTargetItem = updated
    }
    invalidate()
    return updated
}

/**
 * Swaps [itemId] horizontally/sequentially by [stepDelta] (-1 or +1) and updates live HUD state.
 */
internal fun LightspeedCruiseOverlay.moveCategoryHudItemStep(
    catId: String,
    itemId: String,
    stepDelta: Int
): CategoryHudItem? = executeHudItemSwap(catId, itemId, findHudMoveSwapTargetIndex(catId, itemId, stepDelta))

/**
 * Swaps [itemId] vertically across rows ([verticalDir] = -1 up or +1 down) and updates live HUD state.
 */
internal fun LightspeedCruiseOverlay.moveCategoryHudItemVertical(
    catId: String,
    itemId: String,
    verticalDir: Int
): CategoryHudItem? = executeHudItemSwap(catId, itemId, findHudVerticalMoveTargetIndex(catId, itemId, verticalDir))

private fun LightspeedCruiseOverlay.openExistingWidgetConfigureActivity(widgetId: Int, catId: String) {
    if (widgetId <= 0) return
    dismissOverlay()
    uiHandler.postDelayed({
        try {
            val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                action = CockpitDialogActivity.ACTION_CONFIGURE_CATEGORY_HUD_WIDGET
                putExtra(CockpitDialogActivity.EXTRA_WIDGET_ID, widgetId)
                putExtra(CockpitDialogActivity.EXTRA_RETURN_CATEGORY_ID, catId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }, 50L)
}

private val editGlyphStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}
private val editGlyphFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
}
private val editGlyphPath = Path()

private fun drawSlidersGlyph(canvas: Canvas, rect: RectF, dp: Float, accent: Int) {
    editGlyphStrokePaint.strokeWidth = 1.2f * dp
    editGlyphStrokePaint.color = Color.WHITE
    editGlyphFillPaint.color = accent

    val padX = rect.width() * 0.22f
    val startX = rect.left + padX
    val endX = rect.right - padX
    val trackLen = endX - startX

    val line1Y = rect.top + rect.height() * 0.32f
    val line2Y = rect.top + rect.height() * 0.68f

    canvas.drawLine(startX, line1Y, endX, line1Y, editGlyphStrokePaint)
    canvas.drawCircle(startX + trackLen * 0.35f, line1Y, 2.2f * dp, editGlyphFillPaint)

    canvas.drawLine(startX, line2Y, endX, line2Y, editGlyphStrokePaint)
    canvas.drawCircle(startX + trackLen * 0.70f, line2Y, 2.2f * dp, editGlyphFillPaint)
}

private fun drawPencilGlyph(canvas: Canvas, rect: RectF, dp: Float, accent: Int) {
    editGlyphStrokePaint.strokeWidth = 1.5f * dp
    editGlyphStrokePaint.color = accent
    val cx = rect.centerX()
    val cy = rect.centerY()
    val r = rect.width() * 0.24f
    editGlyphPath.reset()
    editGlyphPath.moveTo(cx - r, cy + r)
    editGlyphPath.lineTo(cx - r * 0.3f, cy + r * 0.9f)
    editGlyphPath.lineTo(cx + r, cy - r * 0.4f)
    editGlyphPath.lineTo(cx + r * 0.4f, cy - r)
    editGlyphPath.lineTo(cx - r * 0.9f, cy + r * 0.3f)
    editGlyphPath.close()
    canvas.drawPath(editGlyphPath, editGlyphStrokePaint)
}
