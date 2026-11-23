package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowInsets
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.CockpitGimbalConfigCache
import com.sbf.lightspeed.system.lsString
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

internal enum class AllTipsSurface {
    COCKPIT_HANGAR,
    CATEGORY_CRUISE
}

/**
 * Isolated interaction and scroll state for the All Tips ghost overlay & Category Cruise rotating tip.
 */
internal class AllTipsOverlayState {
    var dismissArmed: Boolean = false
    var scrollOffset: Float = 0f
    var maxScroll: Float = 0f
    var touchDownX: Float = 0f
    var touchDownY: Float = 0f
    var lastTouchY: Float = 0f
    var dragged: Boolean = false
    var touchingCruiseTip: Boolean = false

    fun reset() {
        dismissArmed = false
        scrollOffset = 0f
        maxScroll = 0f
        touchDownX = 0f
        touchDownY = 0f
        lastTouchY = 0f
        dragged = false
        touchingCruiseTip = false
    }
}

private val allTipsCardRect = RectF()

internal fun LightspeedCruiseOverlay.shouldShowCategoryCruiseRotatingTip(): Boolean =
    (currentLayer == CruiseLayer.CATEGORY ||
        currentLayer == CruiseLayer.GRID ||
        currentLayer == CruiseLayer.STICKY_PIN ||
        isStickyPinned) &&
        !isEditModeOpen &&
        !isHudTuneModalOpen &&
        !isHudAddWidgetMenuOpen &&
        pendingDeleteHudItem == null &&
        !isCountdownModalOpen &&
        !isAlertsHistoryModalOpen &&
        !isFirewallModalOpen &&
        !isVitalsModalOpen &&
        !isPrivacyScoreInfoBubbleOpen &&
        !isScreenTimeInfoBubbleOpen &&
        !isBatterySpanInfoBubbleOpen &&
        !isPinnedAppMenuOpen() &&
        allTipsOverlaySurface == null

internal fun LightspeedCruiseOverlay.getCategoryCruiseTipBaselineY(): Float {
    val d = resources.displayMetrics.density
    val h = height.toFloat()
    val isLandscape = width > height
    val bottomInset = getHangarBottomInsetPx()
    return h - bottomInset - (if (isLandscape) 11f else 12f) * d
}

internal fun LightspeedCruiseOverlay.getCategoryCruiseTipReserveTopY(): Float {
    val d = resources.displayMetrics.density
    val isLandscape = width > height
    val tipScale = overlayTextScale().coerceIn(0.85f, 1.18f)
    val reserveAboveBaseline = if (isLandscape) {
        (22f * tipScale + 4f) * d
    } else {
        (36f * tipScale + 6f) * d
    }
    return (getCategoryCruiseTipBaselineY() - reserveAboveBaseline).coerceAtLeast(0f)
}

internal fun LightspeedCruiseOverlay.isTouchOnCategoryCruiseTip(x: Float, y: Float): Boolean =
    !categoryCruiseTipHitRect.isEmpty && categoryCruiseTipHitRect.contains(x, y)

internal fun LightspeedCruiseOverlay.openAllTipsOverlay(
    surface: AllTipsSurface,
    promoteFromCruise: Boolean = false
) {
    if (promoteFromCruise) {
        uiHandler.removeCallbacks(neutralToCategoryRunnable)
        isStickyPinned = true
        isCruising = false
        isCurrentlyTouched = false
        categoryScrubbingEngaged = false
        maxVerticalDisplacement = 0f
        if (cachedCategories.getOrNull(activeCatIndex)?.id == "launcher_settings_virtual_id") {
            activeCatIndex = cachedCategories.indexOfLast { it.id != "launcher_settings_virtual_id" }.coerceAtLeast(0)
        }
        if (currentLayer == CruiseLayer.CATEGORY) {
            currentLayer = CruiseLayer.GRID
            loadActiveCategoryGrid()
        } else if (currentLayer == CruiseLayer.STICKY_PIN) {
            currentLayer = CruiseLayer.GRID
        }
        cruiseMultiTouch.secondaryActionCommitted = true
        cruiseMultiTouch.activeCruisePointerId = MotionEvent.INVALID_POINTER_ID
        cruiseMultiTouch.secondaryPointerId = MotionEvent.INVALID_POINTER_ID
        cruiseMultiTouch.secondaryWidgetTarget = null
        updateMetricsDimensions()
    }
    allTipsState.reset()
    allTipsOverlaySurface = surface
    triggerHardwareHaptic(20, 110)
    invalidate()
}

internal fun LightspeedCruiseOverlay.drawCategoryCruiseRotatingTip(canvas: Canvas) {
    if (!shouldShowCategoryCruiseRotatingTip()) {
        categoryCruiseTipHitRect.setEmpty()
        return
    }
    val w = width.toFloat()
    val h = height.toFloat()
    val d = resources.displayMetrics.density
    if (w <= 0f || h <= 0f) {
        categoryCruiseTipHitRect.setEmpty()
        return
    }

    val ccTipIdx = ((categoryCruiseTipIndex.coerceAtLeast(0)) % CATEGORY_CRUISE_TIP_COUNT + CATEGORY_CRUISE_TIP_COUNT) % CATEGORY_CRUISE_TIP_COUNT
    val ccTipBody = lsString(CATEGORY_CRUISE_TIP_RES_IDS[ccTipIdx]).trim()
    if (ccTipBody.isEmpty()) {
        categoryCruiseTipHitRect.setEmpty()
        return
    }

    val alphaFactor = if (currentLayer == CruiseLayer.CATEGORY) {
        sin(((SystemClock.uptimeMillis() - entranceStartTime) / 220f).coerceIn(0f, 1f) * PI / 2.0).toFloat()
    } else {
        1f
    }

    val snap = DeckThemeManager.getSnapshot(context)
    val accent = ensureHighContrastColor(categoryAccentOverride() ?: snap.tintArgb.toInt())
    val hasEdgeSwitcher = (isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) &&
        !isEditModeOpen &&
        !isHudEditMode
    val tipLeft = if (hasEdgeSwitcher && isOpenedFromLeftFlank) 36f * d else 14f * d
    val tipRight = if (hasEdgeSwitcher && !isOpenedFromLeftFlank) w - 36f * d else w - 14f * d
    val maxTipW = (tipRight - tipLeft).coerceAtLeast(120f * d)
    val tipCx = (tipLeft + tipRight) * 0.5f
    val tipBaselineY = getCategoryCruiseTipBaselineY()
    val isLandscape = w > h
    val tipScale = overlayTextScale().coerceIn(0.85f, 1.18f)
    val tipSize = (if (isLandscape) 11.5f else 12.5f) * d * tipScale
    val tipMinSize = (if (isLandscape) 9.8f else 10.4f) * d * tipScale.coerceAtMost(1.05f)
    val tipMaxLines = if (isLandscape) 1 else 2

    drawInlineGhostTipSingleLine(
        canvas = canvas,
        tipBody = ccTipBody,
        cx = tipCx,
        baselineY = tipBaselineY,
        maxWidth = maxTipW,
        density = d,
        baseTextSize = tipSize,
        accentColor = accent,
        boldTypeface = overlayTypeface(isBold = true),
        regularTypeface = overlayTypeface(isBold = false),
        alphaFactor = alphaFactor,
        minTextSize = tipMinSize,
        maxLines = tipMaxLines
    )
    categoryCruiseTipHitRect.set(
        tipLeft,
        getCategoryCruiseTipReserveTopY(),
        tipRight,
        (tipBaselineY + 11f * d).coerceAtMost(h)
    )
}

/**
 * Renders a numbered, translucent ghost card listing every tip for Cockpit Hangar or Category Cruise.
 * Adapts height to content and scrolls smoothly on compact screens; tapping dismisses the card.
 */
internal fun LightspeedCruiseOverlay.drawAllTipsGhostOverlay(canvas: Canvas) {
    val surface = allTipsOverlaySurface ?: return
    val w = width.toFloat()
    val h = height.toFloat()
    val d = resources.displayMetrics.density
    if (w <= 0f || h <= 0f) return

    val (accent, panelRgb) = when (surface) {
        AllTipsSurface.COCKPIT_HANGAR -> {
            val pal = CockpitGimbalConfigCache.resolveCockpitPalette(context)
            ensureHighContrastColor(pal.primaryAccentArgb) to pal.panelInnerArgb
        }
        AllTipsSurface.CATEGORY_CRUISE -> {
            val snap = DeckThemeManager.getSnapshot(context)
            ensureHighContrastColor(categoryAccentOverride() ?: snap.tintArgb.toInt()) to snap.panelColorArgb.toInt()
        }
    }

    val titleResId = when (surface) {
        AllTipsSurface.COCKPIT_HANGAR -> R.string.all_tips_hangar_title
        AllTipsSurface.CATEGORY_CRUISE -> R.string.all_tips_cruise_title
    }
    val tipResIds = when (surface) {
        AllTipsSurface.COCKPIT_HANGAR -> HANGAR_GHOST_TIP_RES_IDS
        AllTipsSurface.CATEGORY_CRUISE -> CATEGORY_CRUISE_TIP_RES_IDS
    }
    val tips = tipResIds.map { lsString(it).trim() }.filter { it.isNotEmpty() }
    if (tips.isEmpty()) return

    val insets = rootWindowInsets
    val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && insets != null) {
        insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top.toFloat()
    } else {
        24f * d
    }
    val bottomInset = getHangarBottomInsetPx()

    val isLandscape = w > h
    val cardW = if (isLandscape) {
        (w * 0.84f).coerceIn(340f * d, 640f * d)
    } else {
        (w - 24f * d).coerceAtMost(430f * d)
    }
    val maxCardH = (h - topInset - bottomInset - 22f * d).coerceAtLeast(220f * d)
    val padX = 18f * d
    val padY = 16f * d
    val colGap = 18f * d
    val colCount = if (isLandscape && cardW >= 460f * d && tips.size >= 4) 2 else 1
    val headerSectionH = 32f * d
    val footerSectionH = 28f * d

    val textScale = (if (surface == AllTipsSurface.COCKPIT_HANGAR) {
        deepSpaceRenderer.hangarTextScale(context)
    } else {
        overlayTextScale()
    }).coerceIn(0.85f, 1.15f)

    val boldType = overlayTypeface(isBold = true)
    val regularType = overlayTypeface(isBold = false)

    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldType
        textAlign = Paint.Align.LEFT
        textSize = (if (isLandscape) 13.0f else 14.0f) * d * textScale
        color = accent
    }
    val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldType
        textAlign = Paint.Align.RIGHT
        textSize = (if (isLandscape) 10.8f else 11.5f) * d * textScale
        color = Color.argb(205, Color.red(accent), Color.green(accent), Color.blue(accent))
    }
    val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldType
        textAlign = Paint.Align.LEFT
        color = accent
    }
    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = regularType
        textAlign = Paint.Align.LEFT
        color = Color.argb(242, 238, 245, 255)
    }
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = boldType
        textAlign = Paint.Align.CENTER
        textSize = (if (isLandscape) 10.6f else 11.2f) * d * textScale
        color = Color.argb(195, 204, 224, 255)
    }

    var bodySize = (if (isLandscape) 12.4f else 13.4f) * d * textScale
    val minBodySize = (if (isLandscape) 11.2f else 12.0f) * d
    val rowGap = (if (isLandscape) 9.5f else 11.0f) * d

    data class MeasuredTip(val numStr: String, val lines: List<String>)
    lateinit var measuredTips: List<MeasuredTip>
    var numGutterW = 0f
    var lineStep = 0f
    var totalBodyH = 0f
    var colW = 0f

    fun measureLayout(reserveScrollbar: Boolean) {
        val scrollbarReserve = if (reserveScrollbar) 8f * d else 0f
        colW = if (colCount == 2) {
            ((cardW - padX * 2f - scrollbarReserve - colGap) * 0.5f)
        } else {
            (cardW - padX * 2f - scrollbarReserve)
        }
        numPaint.textSize = bodySize
        bodyPaint.textSize = bodySize
        val fm = bodyPaint.fontMetrics
        lineStep = (fm.descent - fm.ascent) * 1.26f
        numGutterW = numPaint.measureText("${tips.size}. ") + 3f * d
        val maxTextW = (colW - numGutterW).coerceAtLeast(60f * d)

        measuredTips = tips.mapIndexed { idx, txt ->
            MeasuredTip("${idx + 1}.", wrapAllTipsLines(txt, bodyPaint, maxTextW))
        }

        totalBodyH = if (colCount == 2) {
            val splitIdx = ceil(measuredTips.size / 2.0).toInt()
            val leftH = measureColumnHeight(measuredTips.subList(0, splitIdx).map { it.lines.size }, lineStep, rowGap)
            val rightH = measureColumnHeight(measuredTips.subList(splitIdx, measuredTips.size).map { it.lines.size }, lineStep, rowGap)
            maxOf(leftH, rightH)
        } else {
            measureColumnHeight(measuredTips.map { it.lines.size }, lineStep, rowGap)
        }
    }

    while (true) {
        measureLayout(reserveScrollbar = false)
        val neededH = padY * 2f + headerSectionH + totalBodyH + footerSectionH
        if (neededH <= maxCardH || bodySize <= minBodySize) {
            break
        }
        bodySize = (bodySize - 0.5f * d).coerceAtLeast(minBodySize)
    }

    var neededH = padY * 2f + headerSectionH + totalBodyH + footerSectionH
    if (neededH > maxCardH) {
        measureLayout(reserveScrollbar = true)
        neededH = padY * 2f + headerSectionH + totalBodyH + footerSectionH
    }

    val cardH = neededH.coerceAtMost(maxCardH)
    val cardLeft = (w - cardW) * 0.5f
    val centerAnchorY = topInset + ((h - topInset - bottomInset) * 0.5f)
    val cardTop = (centerAnchorY - cardH * 0.5f).coerceIn(topInset + 8f * d, (h - bottomInset - cardH - 8f * d).coerceAtLeast(topInset + 8f * d))
    allTipsCardRect.set(cardLeft, cardTop, cardLeft + cardW, cardTop + cardH)

    // Soft full-screen translucent scrim
    elementPaint.style = Paint.Style.FILL
    elementPaint.color = Color.argb(135, 4, 7, 14)
    canvas.drawRect(0f, 0f, w, h, elementPaint)

    // Translucent ghost glass card
    val fillR = ((Color.red(panelRgb) * 0.35f) + 4f).toInt().coerceIn(4, 28)
    val fillG = ((Color.green(panelRgb) * 0.35f) + 6f).toInt().coerceIn(6, 34)
    val fillB = ((Color.blue(panelRgb) * 0.35f) + 12f).toInt().coerceIn(10, 50)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(222, fillR, fillG, fillB)
    canvas.drawRoundRect(allTipsCardRect, 14f * d, 14f * d, highlightPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * d
    highlightPaint.color = Color.argb(175, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(allTipsCardRect, 14f * d, 14f * d, highlightPaint)

    // Header + count badge + subtle divider
    val innerLeft = allTipsCardRect.left + padX
    val innerRight = allTipsCardRect.right - padX
    val hFm = headerPaint.fontMetrics
    val headerBaselineY = allTipsCardRect.top + padY + ((headerSectionH - 8f * d) - (hFm.descent + hFm.ascent)) * 0.5f
    val titleText = "✦ ${lsString(titleResId)}"
    val countText = "${tips.size}"
    canvas.drawText(countText, innerRight, headerBaselineY, countPaint)
    val maxTitleW = (innerRight - countPaint.measureText(countText) - innerLeft - 8f * d).coerceAtLeast(80f * d)
    canvas.drawText(clipTextToWidth(titleText, maxTitleW), innerLeft, headerBaselineY, headerPaint)

    val dividerY = allTipsCardRect.top + padY + headerSectionH - 6f * d
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * d
    highlightPaint.color = Color.argb(72, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawLine(innerLeft, dividerY, innerRight, dividerY, highlightPaint)

    val bodyTop = allTipsCardRect.top + padY + headerSectionH
    val bodyBottom = (allTipsCardRect.bottom - padY - footerSectionH).coerceAtLeast(bodyTop + 48f * d)
    val bodyViewportH = (bodyBottom - bodyTop).coerceAtLeast(1f)
    allTipsState.maxScroll = (totalBodyH - bodyViewportH).coerceAtLeast(0f)
    allTipsState.scrollOffset = allTipsState.scrollOffset.coerceIn(0f, allTipsState.maxScroll)
    val isScrollable = allTipsState.maxScroll > 0.5f
    val contentRight = if (isScrollable) innerRight - 8f * d else innerRight

    val fm = bodyPaint.fontMetrics
    fun drawTipColumn(items: List<MeasuredTip>, colLeft: Float) {
        var curTop = bodyTop
        for (tip in items) {
            val firstBaseY = curTop - fm.ascent
            canvas.drawText(tip.numStr, colLeft, firstBaseY, numPaint)
            for (lineIdx in tip.lines.indices) {
                canvas.drawText(tip.lines[lineIdx], colLeft + numGutterW, firstBaseY + lineIdx * lineStep, bodyPaint)
            }
            curTop += tip.lines.size * lineStep + rowGap
        }
    }

    val topAlpha = if (isScrollable) LightspeedHudVectorIcons.computeEdgeFeatherStrength(allTipsState.scrollOffset, d) else 0f
    val botAlpha = if (isScrollable) LightspeedHudVectorIcons.computeEdgeFeatherStrength(allTipsState.maxScroll - allTipsState.scrollOffset, d) else 0f
    val needsLayer = isScrollable && (topAlpha > 0.004f || botAlpha > 0.004f)
    val clipTop = bodyTop - 2f * d
    val clipBottom = bodyBottom + 2f * d
    val layerCount = if (needsLayer) {
        canvas.saveLayer(innerLeft, clipTop, contentRight, clipBottom, null)
    } else {
        canvas.save()
    }
    canvas.clipRect(innerLeft, clipTop, contentRight, clipBottom)
    canvas.save()
    canvas.translate(0f, -allTipsState.scrollOffset)
    if (colCount == 2) {
        val splitIdx = ceil(measuredTips.size / 2.0).toInt()
        drawTipColumn(measuredTips.subList(0, splitIdx), innerLeft)
        drawTipColumn(measuredTips.subList(splitIdx, measuredTips.size), innerLeft + colW + colGap)
    } else {
        drawTipColumn(measuredTips, innerLeft)
    }
    canvas.restore()
    if (needsLayer) {
        LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
            canvas = canvas,
            left = innerLeft,
            top = clipTop,
            right = contentRight,
            bottom = clipBottom,
            featherTopPx = 16f * d,
            featherBottomPx = 18f * d,
            density = d,
            seed = 9091,
            topAlpha = topAlpha,
            bottomAlpha = botAlpha
        )
    }
    canvas.restoreToCount(layerCount)

    if (isScrollable) {
        val sbW = 2.5f * d
        val sbX = allTipsCardRect.right - 8f * d
        val trackTop = bodyTop + 2f * d
        val trackBot = bodyBottom - 2f * d
        val trackH = (trackBot - trackTop).coerceAtLeast(16f * d)
        val thumbH = (trackH * (bodyViewportH / totalBodyH.coerceAtLeast(1f))).coerceIn(18f * d, trackH)
        val thumbTop = trackTop + (allTipsState.scrollOffset / allTipsState.maxScroll) * (trackH - thumbH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(46, 255, 255, 255)
        canvas.drawRoundRect(RectF(sbX, trackTop, sbX + sbW, trackBot), sbW * 0.5f, sbW * 0.5f, highlightPaint)
        highlightPaint.color = Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(RectF(sbX, thumbTop, sbX + sbW, thumbTop + thumbH), sbW * 0.5f, sbW * 0.5f, highlightPaint)
    }

    val fFm = footerPaint.fontMetrics
    val footerCenterY = allTipsCardRect.bottom - padY - (footerSectionH * 0.35f)
    val dismissText = "✦ ${lsString(R.string.all_tips_dismiss).ifBlank { "Tap anywhere to dismiss" }}"
    canvas.drawText(dismissText, allTipsCardRect.centerX(), footerCenterY - ((fFm.descent + fFm.ascent) * 0.5f), footerPaint)
}

private fun measureColumnHeight(lineCounts: List<Int>, lineStep: Float, rowGap: Float): Float {
    if (lineCounts.isEmpty()) return 0f
    var total = 0f
    for (i in lineCounts.indices) {
        total += lineCounts[i] * lineStep
        if (i < lineCounts.lastIndex) total += rowGap
    }
    return total
}

private fun wrapAllTipsLines(text: String, paint: Paint, maxW: Float): List<String> {
    val clean = text.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return emptyList()
    if (paint.measureText(clean) <= maxW) return listOf(clean)
    val out = ArrayList<String>()
    var cur = StringBuilder()
    for (word in clean.split(' ')) {
        val cand = if (cur.isEmpty()) word else "$cur $word"
        if (paint.measureText(cand) <= maxW || cur.isEmpty()) {
            cur = StringBuilder(cand)
        } else {
            out.add(cur.toString())
            cur = StringBuilder(word)
        }
    }
    if (cur.isNotEmpty()) out.add(cur.toString())
    return out
}
