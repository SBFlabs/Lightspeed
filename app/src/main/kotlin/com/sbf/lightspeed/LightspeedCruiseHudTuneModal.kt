package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.roundToInt

// Cached hit rects for non-occluding Per-Widget Tune Panel (split from LightspeedCruiseHudEditControls.kt)
private val tuneModalRect = RectF()
private val tuneMoveUpBtnRect = RectF()
private val tuneMoveDownBtnRect = RectF()
private val tuneMovePrevBtnRect = RectF()
private val tuneMoveNextBtnRect = RectF()
private val tuneCloseBtnRect = RectF()
private val tuneDoneBtnRect = RectF()
private val tuneResetBtnRect = RectF()
private val tuneZeroAllPadsBtnRect = RectF()
private val tuneMoveCategoryBtnRect = RectF()
private val tuneCategoryPickerRect = RectF()
private val tuneCategoryPickerItemRects = mutableListOf<Pair<String, RectF>>()
private var isTuneCategoryPickerOpen: Boolean = false
private val tuneSliderTrackRects = mutableMapOf<String, RectF>()
private val tuneSliderValueRects = mutableMapOf<String, RectF>()
private val tuneScopePillRects = mutableMapOf<Int, RectF>()
private var tuneSliderDragStartX: Float = 0f
private var tuneSliderDragStartPos: Int = 0
private var tuneSliderDragTapToJump: Boolean = false

// Pending long-press tracking on slider numeric value boxes (400ms reset to default vs <400ms calibration popup)
private var pendingValueBoxOverlay: LightspeedCruiseOverlay? = null
private var pendingValueBoxCatId: String? = null
private var pendingValueBoxItem: CategoryHudItem? = null
private var pendingValueBoxSliderId: String? = null
private var pendingValueBoxDownX: Float = 0f
private var pendingValueBoxDownY: Float = 0f
private var pendingValueBoxLongTriggered: Boolean = false
private var tuneStatusText: String? = null
private var tuneStatusUntilMs: Long = 0L

private val tuneValueBoxLongPressRunnable = Runnable {
    val overlay = pendingValueBoxOverlay ?: return@Runnable
    val catId = pendingValueBoxCatId ?: return@Runnable
    val item = overlay.hudTuneTargetItem ?: pendingValueBoxItem ?: return@Runnable
    val sliderId = pendingValueBoxSliderId ?: return@Runnable
    if (!overlay.isHudTuneModalOpen) return@Runnable
    val spec = buildTuneSliderSpecs(item).firstOrNull { it.id == sliderId } ?: return@Runnable

    pendingValueBoxLongTriggered = true
    spec.applyAutoOrReset(item, overlay.width > overlay.height)
    tuneStatusText = "RESET ${spec.label} -> DEFAULT"
    tuneStatusUntilMs = SystemClock.uptimeMillis() + 1200L
    overlay.triggerHardwareHaptic(32, 130)
    overlay.applyLiveHudTuneItemChanges(catId, item)
    overlay.postInvalidateDelayed(1250L)
}

private fun cancelPendingValueBoxHold(overlay: LightspeedCruiseOverlay?) {
    overlay?.removeCallbacks(tuneValueBoxLongPressRunnable)
    pendingValueBoxOverlay = null
    pendingValueBoxCatId = null
    pendingValueBoxItem = null
    pendingValueBoxSliderId = null
    pendingValueBoxLongTriggered = false
}

internal fun LightspeedCruiseOverlay.openHudTuneModal(item: CategoryHudItem?) {
    cancelPendingValueBoxHold(this)
    closeTuneCalibrationModal()
    tuneStatusText = null
    tuneStatusUntilMs = 0L
    hudTuneTargetItem = item
    isHudTuneModalOpen = (item != null)
    activeDraggingSliderId = null
    isTuneCategoryPickerOpen = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeHudTuneModal() {
    cancelPendingValueBoxHold(this)
    closeTuneCalibrationModal()
    tuneStatusText = null
    tuneStatusUntilMs = 0L
    isHudTuneModalOpen = false
    hudTuneTargetItem = null
    activeDraggingSliderId = null
    isTuneFontDropdownOpen = false
    tuneFontDropdownTracking = false
    isTuneCategoryPickerOpen = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.drawHudTuneModal(
    canvas: Canvas,
    density: Float,
    accent: Int
) {
    val item = hudTuneTargetItem ?: return
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val isLandscape = width > height
    val colors = resolveTuneThemeColors(dp)
    val themeAccent = colors.accentColor

    // NEVER dim or darken the screen backdrop so the tuned widget stays 100% clear!
    val isFarSplit = isFarHalfSplitMode()
    val modalW = if (isFarSplit) {
        (screenW * 0.45f).coerceAtMost(332f * dp)
    } else {
        (324f * dp).coerceAtMost(screenW - 22f * dp)
    }

    val isInspector = item.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT
    val isVitals = item.type == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM
    val specs = buildTuneSliderSpecs(item)
    val headerH = 34f * dp
    val extraRowH = if (isInspector) 40f * dp else if (isVitals) 32f * dp else 0f
    val scopeRowH = 40f * dp
    val footerH = 38f * dp
    val maxAvailH = (screenH - 24f * dp).coerceAtLeast(240f * dp)
    val rawContentNoSliders = headerH + extraRowH + scopeRowH + footerH + 10f * dp
    val sliderH = ((maxAvailH - rawContentNoSliders) / specs.size).coerceIn(22f * dp, 29f * dp)
    val modalH = (rawContentNoSliders + (sliderH * specs.size)).coerceAtMost(maxAvailH)

    val left: Float
    val top: Float
    if (isFarSplit) {
        val oppositeHalfCenter = if (isOpenedFromLeftFlank) screenW * 0.25f else screenW * 0.75f
        left = (oppositeHalfCenter - (modalW / 2f)).coerceIn(8f * dp, screenW - modalW - 8f * dp)
        top = ((screenH - modalH) / 2f).coerceAtLeast(getSafeHudTopOffset())
    } else {
        left = ((screenW - modalW) / 2f).coerceIn(8f * dp, screenW - modalW - 8f * dp)
        val minTop = getSafeHudTopOffset() + 38f * dp
        val maxTop = (screenH - modalH - 8f * dp).coerceAtLeast(minTop)
        top = if ((activeDraggingSliderId != null || isTuneCalibrationModalOpen()) && !tuneModalRect.isEmpty) {
            tuneModalRect.top.coerceIn(minTop, maxTop)
        } else {
            val widgetBottom = item.bounds.bottom - hudScrollOffset + 8f * dp
            widgetBottom.coerceIn(minTop, maxTop)
        }
    }
    tuneModalRect.set(left, top, left + modalW, top + modalH)

    // 12-Theme Optical Glass Surface (matches long-press pinned app menu)
    drawDeckThemedFlyoutSurface(canvas, tuneModalRect, colors.snapshot, colors.cornerRadiusPx, dp, highlightPaint)

    // Top-right action buttons: [▲] [▼] [◀] [▶] [✕]
    val btnSize = 19f * dp
    val btnGap = 4f * dp
    val btnTop = top + 9f * dp
    tuneCloseBtnRect.set(tuneModalRect.right - btnSize - 10f * dp, btnTop, tuneModalRect.right - 10f * dp, btnTop + btnSize)
    tuneMoveNextBtnRect.set(tuneCloseBtnRect.left - btnGap - btnSize, btnTop, tuneCloseBtnRect.left - btnGap, btnTop + btnSize)
    tuneMovePrevBtnRect.set(tuneMoveNextBtnRect.left - btnGap - btnSize, btnTop, tuneMoveNextBtnRect.left - btnGap, btnTop + btnSize)
    tuneMoveDownBtnRect.set(tuneMovePrevBtnRect.left - btnGap - btnSize, btnTop, tuneMovePrevBtnRect.left - btnGap, btnTop + btnSize)
    tuneMoveUpBtnRect.set(tuneMoveDownBtnRect.left - btnGap - btnSize, btnTop, tuneMoveDownBtnRect.left - btnGap, btnTop + btnSize)

    val catId = cachedCategories.getOrNull(activeCatIndex)?.id.orEmpty()
    val canMoveUp = catId.isNotEmpty() && canMoveCategoryHudItemVertical(catId, item.id, -1)
    val canMoveDown = catId.isNotEmpty() && canMoveCategoryHudItemVertical(catId, item.id, +1)
    val canMovePrev = catId.isNotEmpty() && canMoveCategoryHudItem(catId, item.id, -1)
    val canMoveNext = catId.isNotEmpty() && canMoveCategoryHudItem(catId, item.id, +1)

    fun scaleColorAlpha(color: Int, factor: Float): Int =
        Color.argb((Color.alpha(color) * factor).roundToInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    fun drawHeaderActionBox(rect: RectF, enabled: Boolean) {
        val alphaFactor = if (enabled) 1f else 0.36f
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = scaleColorAlpha(colors.pillBg, alphaFactor)
        canvas.drawRoundRect(rect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = scaleColorAlpha(colors.pillBorder, alphaFactor)
        canvas.drawRoundRect(rect, 6f * dp, 6f * dp, highlightPaint)
    }

    // Header Title or temporary reset/move status confirmation (ellipsized cleanly before [▲])
    val activeStatus = tuneStatusText?.takeIf { SystemClock.uptimeMillis() < tuneStatusUntilMs }
    if (activeStatus == null && tuneStatusText != null) {
        tuneStatusText = null
    }
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 9.5f * dp
    textPaint.color = if (activeStatus != null) themeAccent else colors.textPrimary
    val fm = textPaint.fontMetrics
    val rawHeaderText = activeStatus ?: "TUNE · ${hudWidgetDisplayName(item.type, short = true)}"
    val maxTitleW = (tuneMoveUpBtnRect.left - 8f * dp) - (left + 14f * dp)
    val headerText = if (maxTitleW > 12f * dp) {
        android.text.TextUtils.ellipsize(rawHeaderText, android.text.TextPaint(textPaint), maxTitleW, android.text.TextUtils.TruncateAt.END).toString()
    } else ""
    canvas.drawText(headerText, left + 14f * dp, top + 20f * dp - ((fm.descent + fm.ascent) / 2f), textPaint)

    val arrowRadius = (4.0f * dp).coerceAtMost(btnSize * 0.25f)
    fun drawHeaderArrow(rect: RectF, enabled: Boolean, direction: Int) {
        drawHeaderActionBox(rect, enabled)
        LightspeedHudVectorIcons.drawVectorArrow(
            canvas = canvas,
            cx = rect.centerX(),
            cy = rect.centerY(),
            r = arrowRadius,
            direction = direction,
            color = if (enabled) colors.textPrimary else colors.textMuted,
            strokeW = 1.4f * dp,
            filledTriangle = true
        )
    }

    drawHeaderArrow(tuneMoveUpBtnRect, canMoveUp, direction = 1)
    drawHeaderArrow(tuneMoveDownBtnRect, canMoveDown, direction = 0)
    drawHeaderArrow(tuneMovePrevBtnRect, canMovePrev, direction = 3)
    drawHeaderArrow(tuneMoveNextBtnRect, canMoveNext, direction = 2)

    drawHeaderActionBox(tuneCloseBtnRect, enabled = true)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.8f * dp
    textPaint.color = colors.textPrimary
    val cFm = textPaint.fontMetrics
    canvas.drawText("✕", tuneCloseBtnRect.centerX(), tuneCloseBtnRect.centerY() - ((cFm.descent + cFm.ascent) / 2f), textPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawLine(left + 14f * dp, top + headerH - 2f * dp, left + modalW - 14f * dp, top + headerH - 2f * dp, highlightPaint)

    tuneSliderTrackRects.clear()
    tuneSliderValueRects.clear()
    var curY = top + headerH

    val accR = Color.red(themeAccent)
    val accG = Color.green(themeAccent)
    val accB = Color.blue(themeAccent)

    fun drawTuneSlider(spec: TuneSliderSpec, sliderTop: Float) {
        val rowLeft = left + 14f * dp
        val rowRight = left + modalW - 14f * dp
        val rowW = rowRight - rowLeft
        val trackCenterY = sliderTop + (sliderH / 2f)

        val labelW = (rowW * 0.34f).coerceIn(78f * dp, 102f * dp)
        val valBoxW = (rowW * 0.17f).coerceIn(44f * dp, 52f * dp)
        val valBoxH = (sliderH - 6f * dp).coerceIn(15f * dp, 19f * dp)
        val valBoxLeft = rowLeft + labelW
        val valBoxRight = valBoxLeft + valBoxW
        val valBoxRect = RectF(valBoxLeft, trackCenterY - valBoxH / 2f, valBoxRight, trackCenterY + valBoxH / 2f)
        tuneSliderValueRects[spec.id] = valBoxRect

        val trackLeft = valBoxRight + 10f * dp
        val trackW = (rowRight - trackLeft).coerceAtLeast(60f * dp)
        val trackH = 4.6f * dp
        val trackRect = RectF(trackLeft, trackCenterY - trackH / 2f, rowRight, trackCenterY + trackH / 2f)
        tuneSliderTrackRects[spec.id] = trackRect

        // Left Label
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.3f * dp
        textPaint.color = colors.textPrimary
        val lFm = textPaint.fontMetrics
        canvas.drawText(spec.label, rowLeft, trackCenterY - ((lFm.descent + lFm.ascent) / 2f), textPaint)

        // Interactive Boxed Value Pill (short tap = calibration popup, long press = reset to default)
        val isModified = !spec.isAtDefault(item, isLandscape)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isModified) Color.argb(60, accR, accG, accB) else colors.pillBg
        canvas.drawRoundRect(valBoxRect, 5f * dp, 5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isModified) 1.1f * dp else 0.8f * dp
        highlightPaint.color = if (isModified) themeAccent else colors.pillBorder
        canvas.drawRoundRect(valBoxRect, 5f * dp, 5f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 8.1f * dp
        textPaint.color = if (isModified) themeAccent else colors.textPrimary
        val vFm = textPaint.fontMetrics
        canvas.drawText(spec.getDisplayValue(item), valBoxRect.centerX(), valBoxRect.centerY() - ((vFm.descent + vFm.ascent) / 2f), textPaint)

        // Lightspeed Signature Track: muted glass rail + active accent fill + dotted steps + Prism thumb
        val span = (spec.maxVal - spec.minVal).coerceAtLeast(1)
        val curPos = spec.getSliderPos(item)
        val fraction = ((curPos - spec.minVal).toFloat() / span.toFloat()).coerceIn(0f, 1f)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(55, Color.red(colors.textMuted), Color.green(colors.textMuted), Color.blue(colors.textMuted))
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.7f * dp
        highlightPaint.color = colors.dividerColor
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, highlightPaint)

        val fillRight = trackLeft + (trackW * fraction)
        if (fillRight > trackLeft + 3f * dp) {
            val fillRect = RectF(trackLeft, trackRect.top, fillRight, trackRect.bottom)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(210, accR, accG, accB)
            canvas.drawRoundRect(fillRect, trackH / 2f, trackH / 2f, highlightPaint)
        }

        // Signature Dotted Steps along Track
        val tickCount = if (spec.dotsEveryStep) span else 8
        val dotSpanW = (trackW - 6f * dp).coerceAtLeast(1f)
        highlightPaint.style = Paint.Style.FILL
        for (i in 0..tickCount) {
            val tFrac = i.toFloat() / tickCount.toFloat()
            val dotX = trackLeft + 3f * dp + dotSpanW * tFrac
            val isCovered = tFrac <= fraction + 0.002f
            highlightPaint.color = if (isCovered) {
                Color.argb(190, Color.red(colors.onAccentColor), Color.green(colors.onAccentColor), Color.blue(colors.onAccentColor))
            } else {
                Color.argb(110, Color.red(colors.textMuted), Color.green(colors.textMuted), Color.blue(colors.textMuted))
            }
            canvas.drawCircle(dotX, trackCenterY, 1.15f * dp, highlightPaint)
        }

        // Prism Thumb + active drag halo
        val thumbX = fillRight.coerceIn(trackLeft + 5.5f * dp, rowRight - 5.5f * dp)
        val isDraggingThis = activeDraggingSliderId == spec.id
        if (isDraggingThis) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(65, accR, accG, accB)
            canvas.drawCircle(thumbX, trackCenterY, 9.8f * dp, highlightPaint)
        }
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.WHITE
        canvas.drawCircle(thumbX, trackCenterY, if (isDraggingThis) 6.2f * dp else 5.5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.6f * dp
        highlightPaint.color = themeAccent
        canvas.drawCircle(thumbX, trackCenterY, if (isDraggingThis) 6.2f * dp else 5.5f * dp, highlightPaint)
    }

    for (spec in specs) {
        drawTuneSlider(spec, curY)
        curY += sliderH
    }

    tuneFontPillRects.clear()
    tuneTempUnitPillRect.setEmpty()

    if (isInspector) {
        val fontLabelY = curY + 10f * dp
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.0f * dp
        textPaint.color = colors.textMuted
        canvas.drawText("FONT FAMILY", left + 14f * dp, fontLabelY, textPaint)

        val installedFonts = com.sbf.lightspeed.system.DeviceFontScanner.getInstalledFonts()
        val activeFontKey = item.fontKey.ifBlank {
            when (item.fontFamily) {
                1 -> "monospace"
                2 -> "serif"
                3 -> "sans-serif-condensed"
                else -> "system_default"
            }
        }
        val activeFontLabel = installedFonts.firstOrNull { it.first == activeFontKey }?.second
            ?: activeFontKey.substringAfterLast('/').substringBeforeLast('.')
        val cleanFontLabel = activeFontLabel.replace(Regex("[^\\p{ASCII}\\p{L}\\p{N}\\p{P}\\p{Z}]"), "").trim()
        val fBtnY = fontLabelY + 4f * dp
        val fBtnH = 22f * dp
        tuneFontDropdownRect.set(left + 14f * dp, fBtnY, left + modalW - 14f * dp, fBtnY + fBtnH)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isTuneFontDropdownOpen) Color.argb(85, accR, accG, accB) else colors.pillBg
        canvas.drawRoundRect(tuneFontDropdownRect, 7f * dp, 7f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = if (isTuneFontDropdownOpen) themeAccent else colors.pillBorder
        canvas.drawRoundRect(tuneFontDropdownRect, 7f * dp, 7f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = resolveDeviceFontTypeface(activeFontKey, isBold = false) ?: Typeface.DEFAULT
        textPaint.textSize = 8.8f * dp
        textPaint.color = colors.textPrimary
        val fFm = textPaint.fontMetrics
        canvas.drawText(cleanFontLabel.take(28), tuneFontDropdownRect.left + 10f * dp, tuneFontDropdownRect.centerY() - ((fFm.descent + fFm.ascent) / 2f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 9.2f * dp
        textPaint.color = themeAccent
        canvas.drawText(if (isTuneFontDropdownOpen) "▲" else "▼", tuneFontDropdownRect.right - 10f * dp, tuneFontDropdownRect.centerY() - ((fFm.descent + fFm.ascent) / 2f), textPaint)
        curY += extraRowH
    } else if (isVitals) {
        tuneFontDropdownRect.setEmpty()
        isTuneFontDropdownOpen = false
        val isF = LightspeedCruiseHudPods.isTempFahrenheit(context)
        val uTop = curY + 4f * dp
        val uRect = RectF(left + 14f * dp, uTop, left + modalW - 14f * dp, uTop + 22f * dp)
        tuneTempUnitPillRect.set(uRect)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(uRect, 7f * dp, 7f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = themeAccent
        canvas.drawRoundRect(uRect, 7f * dp, 7f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.3f * dp
        textPaint.color = colors.textPrimary
        val tFm = textPaint.fontMetrics
        canvas.drawText("TEMPERATURE UNIT: ${if (isF) "FAHRENHEIT (°F)" else "CELSIUS (°C)"} (TAP TO SWITCH)", uRect.centerX(), uRect.centerY() - ((tFm.descent + tFm.ascent) / 2f), textPaint)
        curY += extraRowH
    }

    // 3-Way Per-Widget Visibility Scope Selector
    val scopeLabelY = curY + 11f * dp
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 8.0f * dp
    textPaint.color = colors.textMuted
    canvas.drawText("VISIBILITY SCOPE", left + 14f * dp, scopeLabelY, textPaint)

    tuneScopePillRects.clear()
    val scopeOptions = listOf(
        CategoryHudManager.SCOPE_CRUISE to "CATEGORY CRUISE",
        CategoryHudManager.SCOPE_GRID to "APP GRID",
        CategoryHudManager.SCOPE_BOTH to "BOTH"
    )
    val pillY = scopeLabelY + 5f * dp
    val pillH = 22f * dp
    val pillGap = 5f * dp
    val pillW = (modalW - 28f * dp - (pillGap * 2f)) / 3f
    for ((idx, opt) in scopeOptions.withIndex()) {
        val (scopeVal, scopeLabel) = opt
        val isSel = (item.scope == scopeVal)
        val pLeft = left + 14f * dp + idx * (pillW + pillGap)
        val pRect = RectF(pLeft, pillY, pLeft + pillW, pillY + pillH)
        tuneScopePillRects[scopeVal] = pRect

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) themeAccent else colors.pillBg
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = if (isSel) themeAccent else colors.pillBorder
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textPaint.textSize = 7.4f * dp
        textPaint.color = if (isSel) colors.onAccentColor else colors.textPrimary
        val sFm = textPaint.fontMetrics
        canvas.drawText(scopeLabel, pRect.centerX(), pRect.centerY() - ((sFm.descent + sFm.ascent) / 2f), textPaint)
    }

    // Footer Action Buttons: [0 PAD], [MOVE], [RESET], [DONE]
    val footerBtnH = 27f * dp
    val footerBtnY = tuneModalRect.bottom - footerBtnH - 10f * dp
    val footerBtnGap = 5f * dp
    val btnW = (modalW - 28f * dp - (footerBtnGap * 3f)) / 4f

    tuneZeroAllPadsBtnRect.set(left + 14f * dp, footerBtnY, left + 14f * dp + btnW, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawRoundRect(tuneZeroAllPadsBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(tuneZeroAllPadsBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 8.0f * dp
    textPaint.color = colors.textPrimary
    val zFm = textPaint.fontMetrics
    canvas.drawText("0 PAD", tuneZeroAllPadsBtnRect.centerX(), tuneZeroAllPadsBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneMoveCategoryBtnRect.set(tuneZeroAllPadsBtnRect.right + footerBtnGap, footerBtnY, tuneZeroAllPadsBtnRect.right + footerBtnGap + btnW, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (isTuneCategoryPickerOpen) themeAccent else colors.pillBg
    canvas.drawRoundRect(tuneMoveCategoryBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = if (isTuneCategoryPickerOpen) themeAccent else colors.pillBorder
    canvas.drawRoundRect(tuneMoveCategoryBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 8.0f * dp
    textPaint.color = if (isTuneCategoryPickerOpen) colors.onAccentColor else colors.textPrimary
    canvas.drawText("MOVE", tuneMoveCategoryBtnRect.centerX(), tuneMoveCategoryBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneResetBtnRect.set(tuneMoveCategoryBtnRect.right + footerBtnGap, footerBtnY, tuneMoveCategoryBtnRect.right + footerBtnGap + btnW, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, 175, 55, 65)
    canvas.drawRoundRect(tuneResetBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(115, 255, 115, 125)
    canvas.drawRoundRect(tuneResetBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 8.0f * dp
    textPaint.color = Color.argb(235, 255, 180, 185)
    canvas.drawText("RESET", tuneResetBtnRect.centerX(), tuneResetBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneDoneBtnRect.set(tuneResetBtnRect.right + footerBtnGap, footerBtnY, left + modalW - 14f * dp, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = themeAccent
    canvas.drawRoundRect(tuneDoneBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 8.6f * dp
    textPaint.color = colors.onAccentColor
    canvas.drawText("DONE", tuneDoneBtnRect.centerX(), tuneDoneBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    // Scrollable Device Font Dropdown overlay (12-Theme Glass)
    tuneFontDropdownItemRects.clear()
    if (isTuneFontDropdownOpen && !tuneFontDropdownRect.isEmpty) {
        val installedFonts = com.sbf.lightspeed.system.DeviceFontScanner.getInstalledFonts()
        val activeFontKey = item.fontKey.ifBlank {
            when (item.fontFamily) {
                1 -> "monospace"
                2 -> "serif"
                3 -> "sans-serif-condensed"
                else -> "system_default"
            }
        }
        val listH = (176f * dp).coerceAtMost((tuneModalRect.height() - 48f * dp).coerceAtLeast(80f * dp))
        val listTop = (tuneFontDropdownRect.top - listH - 4f * dp).coerceAtLeast(tuneModalRect.top + 8f * dp)
        tuneFontDropdownListRect.set(tuneFontDropdownRect.left, listTop, tuneFontDropdownRect.right, listTop + listH)

        drawDeckThemedFlyoutSurface(
            canvas,
            tuneFontDropdownListRect,
            colors.snapshot,
            (colors.cornerRadiusPx * 0.72f).coerceAtLeast(8f * dp),
            dp,
            highlightPaint
        )

        val rowH = 26f * dp
        val totalH = installedFonts.size * rowH
        tuneFontDropdownMaxScroll = kotlin.math.max(0f, totalH - (listH - 8f * dp))
        tuneFontDropdownScrollOffset = tuneFontDropdownScrollOffset.coerceIn(0f, tuneFontDropdownMaxScroll)

        canvas.save()
        canvas.clipRect(tuneFontDropdownListRect.left + 2f * dp, tuneFontDropdownListRect.top + 3f * dp, tuneFontDropdownListRect.right - 2f * dp, tuneFontDropdownListRect.bottom - 3f * dp)
        var rowY = tuneFontDropdownListRect.top + 4f * dp - tuneFontDropdownScrollOffset
        for ((fKey, fLabel) in installedFonts) {
            val rRect = RectF(tuneFontDropdownListRect.left + 6f * dp, rowY, tuneFontDropdownListRect.right - 10f * dp, rowY + rowH - 2f * dp)
            if (rRect.bottom >= tuneFontDropdownListRect.top && rRect.top <= tuneFontDropdownListRect.bottom) {
                tuneFontDropdownItemRects[fKey] = RectF(rRect)
                val isSel = (fKey == activeFontKey)
                if (isSel) {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(65, accR, accG, accB)
                    canvas.drawRoundRect(rRect, 5f * dp, 5f * dp, highlightPaint)
                }
                val cleanLabel = fLabel.replace(Regex("[^\\p{ASCII}\\p{L}\\p{N}\\p{P}\\p{Z}]"), "").trim()
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = resolveDeviceFontTypeface(fKey, isBold = isSel) ?: if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                textPaint.textSize = 9.2f * dp
                textPaint.color = if (isSel) themeAccent else colors.textPrimary
                val rFm = textPaint.fontMetrics
                canvas.drawText(cleanLabel.take(32), rRect.left + 8f * dp, rRect.centerY() - ((rFm.descent + rFm.ascent) / 2f), textPaint)
            }
            rowY += rowH
        }
        canvas.restore()

        if (tuneFontDropdownMaxScroll > 0f) {
            val sbW = 2.5f * dp
            val sbX = tuneFontDropdownListRect.right - 6f * dp
            val trackTop = tuneFontDropdownListRect.top + 6f * dp
            val trackH = tuneFontDropdownListRect.height() - 12f * dp
            val thumbH = (trackH * (trackH / totalH)).coerceIn(18f * dp, trackH * 0.6f)
            val thumbY = trackTop + (tuneFontDropdownScrollOffset / tuneFontDropdownMaxScroll) * (trackH - thumbH)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = colors.dividerColor
            canvas.drawRoundRect(RectF(sbX, trackTop, sbX + sbW, trackTop + trackH), 1.5f * dp, 1.5f * dp, highlightPaint)
            highlightPaint.color = themeAccent
            canvas.drawRoundRect(RectF(sbX, thumbY, sbX + sbW, thumbY + thumbH), 1.5f * dp, 1.5f * dp, highlightPaint)
        }
    } else {
        tuneFontDropdownListRect.setEmpty()
    }

    if (isTuneCategoryPickerOpen) {
        drawTuneCategoryPickerOverlay(canvas, dp, catId, colors)
    } else {
        tuneCategoryPickerRect.setEmpty()
        tuneCategoryPickerItemRects.clear()
    }

    // Inline 12-Theme Slider Calibration Popup (stays inside TYPE_ACCESSIBILITY_OVERLAY above Tune modal)
    if (isTuneCalibrationModalOpen()) {
        drawTuneCalibrationModal(canvas, dp, tuneModalRect, item, colors)
    }
}

private fun LightspeedCruiseOverlay.drawTuneCategoryPickerOverlay(
    canvas: Canvas,
    dp: Float,
    activeCatId: String,
    colors: TuneThemeColors
) {
    tuneCategoryPickerItemRects.clear()
    val allCats = cachedCategories.ifEmpty { LightspeedDataBridge.DRAWER_CATEGORIES }
    val destinations = allCats.filter { it.id != activeCatId }
    if (destinations.isEmpty()) {
        tuneCategoryPickerRect.setEmpty()
        return
    }

    val pickerW = (tuneModalRect.width() - 16f * dp).coerceAtLeast(220f * dp)
    val rows = (destinations.size + 1) / 2
    val headerH = 26f * dp
    val rowH = 27f * dp
    val rowGap = 4f * dp
    val pickerH = headerH + (rows * rowH) + ((rows - 1).coerceAtLeast(0) * rowGap) + 12f * dp
    val left = tuneModalRect.centerX() - pickerW * 0.5f
    val top = (tuneMoveCategoryBtnRect.top - pickerH - 6f * dp)
        .coerceIn(8f * dp, (height.toFloat() - pickerH - 8f * dp).coerceAtLeast(8f * dp))
    tuneCategoryPickerRect.set(left, top, left + pickerW, top + pickerH)

    drawDeckThemedFlyoutSurface(
        canvas = canvas,
        bounds = tuneCategoryPickerRect,
        snapshot = colors.snapshot,
        cornerRadius = (colors.cornerRadiusPx * 0.78f).coerceAtLeast(10f * dp),
        dp = dp,
        paint = highlightPaint
    )

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 8.4f * dp
    textPaint.color = colors.accentColor
    val hFm = textPaint.fontMetrics
    canvas.drawText(
        "MOVE WIDGET TO CATEGORY",
        tuneCategoryPickerRect.left + 12f * dp,
        tuneCategoryPickerRect.top + headerH * 0.54f - ((hFm.descent + hFm.ascent) / 2f),
        textPaint
    )

    val colGap = 6f * dp
    val padX = 10f * dp
    val colW = (pickerW - (padX * 2f) - colGap) / 2f
    val gridTop = tuneCategoryPickerRect.top + headerH

    destinations.forEachIndexed { idx, catNode ->
        val row = idx / 2
        val col = idx % 2
        val cLeft = tuneCategoryPickerRect.left + padX + col * (colW + colGap)
        val cTop = gridTop + row * (rowH + rowGap)
        val cRect = RectF(cLeft, cTop, cLeft + colW, cTop + rowH)
        tuneCategoryPickerItemRects.add(catNode.id to cRect)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(cRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = colors.pillBorder
        canvas.drawRoundRect(cRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.6f * dp
        textPaint.color = colors.textPrimary
        val fitted = android.text.TextUtils.ellipsize(
            catNode.label.uppercase(),
            android.text.TextPaint(textPaint),
            (colW - 10f * dp).coerceAtLeast(20f * dp),
            android.text.TextUtils.TruncateAt.END
        ).toString()
        val cFm = textPaint.fontMetrics
        canvas.drawText(fitted, cRect.centerX(), cRect.centerY() - ((cFm.descent + cFm.ascent) / 2f), textPaint)
    }
}

internal fun LightspeedCruiseOverlay.handleHudTuneModalTouch(event: MotionEvent): Boolean {
    val x = event.x
    val y = event.y
    val cat = cachedCategories.getOrNull(activeCatIndex) ?: return true
    val catId = cat.id
    val item = hudTuneTargetItem ?: run {
        closeHudTuneModal()
        return true
    }

    if (isTuneCalibrationModalOpen()) {
        return handleTuneCalibrationTouch(event, catId, item)
    }

    val specs = buildTuneSliderSpecs(item)

    fun updateSliderValueFromTouch(sliderId: String, touchX: Float) {
        val trackRect = tuneSliderTrackRects[sliderId] ?: return
        val spec = specs.firstOrNull { it.id == sliderId } ?: return
        val span = (spec.maxVal - spec.minVal).coerceAtLeast(1)
        val nextPos = if (tuneSliderDragTapToJump) {
            val frac = ((touchX - trackRect.left) / trackRect.width()).coerceIn(0f, 1f)
            (spec.minVal + frac * span).roundToInt().coerceIn(spec.minVal, spec.maxVal)
        } else {
            val dxFrac = (touchX - tuneSliderDragStartX) / trackRect.width().coerceAtLeast(1f)
            (tuneSliderDragStartPos + (dxFrac * span).roundToInt()).coerceIn(spec.minVal, spec.maxVal)
        }
        val prevPos = spec.getSliderPos(item)
        spec.applySliderPos(item, nextPos)
        if (spec.getSliderPos(item) != prevPos) {
            triggerHardwareHaptic(8, 35)
        }
        applyLiveHudTuneItemChanges(catId, item)
    }

    val dp = resources.displayMetrics.density

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            cancelPendingValueBoxHold(this)

            if (isTuneCategoryPickerOpen) {
                for ((destCatId, destRect) in tuneCategoryPickerItemRects) {
                    if (destRect.contains(x, y)) {
                        val moved = CategoryHudManager.moveHudItemToCategory(
                            context = context,
                            sourceCategoryId = catId,
                            targetCategoryId = destCatId,
                            itemId = item.id,
                            isLandscape = width > height
                        )
                        isTuneCategoryPickerOpen = false
                        if (moved != null) {
                            triggerHardwareHaptic(25, 110)
                            closeHudTuneModal()
                            computeHudSlotBounds()
                            updateCategoryAppWidgetView()
                            categoryGridCache.clear()
                            invalidate()
                        } else {
                            triggerHardwareHaptic(12, 40)
                            invalidate()
                        }
                        return true
                    }
                }
                triggerHardwareHaptic(15, 60)
                isTuneCategoryPickerOpen = false
                invalidate()
                return true
            }

            if (isTuneFontDropdownOpen) {
                if (tuneFontDropdownListRect.contains(x, y)) {
                    tuneFontDropdownTracking = true
                    tuneFontDropdownDragStartY = y
                    tuneFontDropdownLastY = y
                    tuneFontDropdownDragged = false
                    return true
                }
                if (tuneFontDropdownRect.contains(x, y)) {
                    triggerHardwareHaptic(18, 80)
                    isTuneFontDropdownOpen = false
                    invalidate()
                    return true
                }
                isTuneFontDropdownOpen = false
                invalidate()
                return true
            }

            fun handleMoveTap(moved: CategoryHudItem?): Boolean {
                triggerHardwareHaptic(if (moved != null) 20 else 8, if (moved != null) 80 else 35)
                if (moved != null) {
                    tuneStatusText = "MOVED -> SLOT #${moved.slotIndex}"
                    tuneStatusUntilMs = SystemClock.uptimeMillis() + 900L
                    postInvalidateDelayed(950L)
                }
                return true
            }

            if (RectF(tuneMoveUpBtnRect).apply { inset(-2f * dp, -3f * dp) }.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemVertical(catId, item.id, -1))
            }
            if (RectF(tuneMoveDownBtnRect).apply { inset(-2f * dp, -3f * dp) }.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemVertical(catId, item.id, +1))
            }
            if (RectF(tuneMovePrevBtnRect).apply { inset(-2f * dp, -3f * dp) }.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemStep(catId, item.id, -1))
            }
            if (RectF(tuneMoveNextBtnRect).apply { inset(-2f * dp, -3f * dp) }.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemStep(catId, item.id, +1))
            }

            if (tuneCloseBtnRect.contains(x, y) || tuneDoneBtnRect.contains(x, y)) {
                triggerHardwareHaptic(20, 80)
                closeHudTuneModal()
                return true
            }

            if (tuneMoveCategoryBtnRect.contains(x, y)) {
                triggerHardwareHaptic(20, 90)
                isTuneFontDropdownOpen = false
                isTuneCategoryPickerOpen = !isTuneCategoryPickerOpen
                invalidate()
                return true
            }

            if (tuneZeroAllPadsBtnRect.contains(x, y)) {
                triggerHardwareHaptic(25, 100)
                item.padLeftDp = 0
                item.padTopDp = 0
                item.padRightDp = 0
                item.padBottomDp = 0
                applyLiveHudTuneItemChanges(catId, item)
                return true
            }

            if (tuneResetBtnRect.contains(x, y)) {
                triggerHardwareHaptic(30, 120)
                val isLandscape = width > height
                item.wCells = defaultHudWidgetWidth(isLandscape)
                item.hCells = defaultHudWidgetHeight(item.type)
                item.opacity = 100
                item.cornerRadiusDp = -1
                item.padLeftDp = 0
                item.padTopDp = 0
                item.padRightDp = 0
                item.padBottomDp = 0
                item.scope = CategoryHudManager.defaultScopeForType(item.type)
                item.titleFontSizeSp = 0
                item.subtitleFontSizeSp = 0
                item.fontFamily = 0
                item.fontKey = ""
                applyLiveHudTuneItemChanges(catId, item)
                return true
            }

            // Device Font Dropdown Toggle (for Focused App Inspector)
            if (!tuneFontDropdownRect.isEmpty && tuneFontDropdownRect.contains(x, y)) {
                triggerHardwareHaptic(20, 90)
                isTuneFontDropdownOpen = !isTuneFontDropdownOpen
                invalidate()
                return true
            }

            // Temperature Unit Pill (for System Vitals)
            if (!tuneTempUnitPillRect.isEmpty && tuneTempUnitPillRect.contains(x, y)) {
                triggerHardwareHaptic(20, 90)
                LightspeedCruiseHudPods.toggleTempUnit(context)
                invalidate()
                return true
            }

            // 3-Way Visibility Scope Pills
            for ((scopeVal, rect) in tuneScopePillRects) {
                if (rect.contains(x, y)) {
                    triggerHardwareHaptic(20, 90)
                    item.scope = scopeVal
                    applyLiveHudTuneItemChanges(catId, item)
                    return true
                }
            }

            // Tapping outside the docked Tune panel dismisses it
            if (!tuneModalRect.contains(x, y)) {
                triggerHardwareHaptic(15, 60)
                closeHudTuneModal()
                return true
            }

            // Signature Value Boxes (short tap -> Calibration modal, >=400ms long press -> reset single slider)
            for ((id, box) in tuneSliderValueRects) {
                val hitBox = RectF(box).apply { inset(-3f * dp, -4f * dp) }
                if (hitBox.contains(x, y)) {
                    pendingValueBoxOverlay = this
                    pendingValueBoxCatId = catId
                    pendingValueBoxItem = item
                    pendingValueBoxSliderId = id
                    pendingValueBoxDownX = x
                    pendingValueBoxDownY = y
                    pendingValueBoxLongTriggered = false
                    postDelayed(tuneValueBoxLongPressRunnable, 400L)
                    return true
                }
            }

            for ((id, track) in tuneSliderTrackRects) {
                val expandedTrack = RectF(track).apply { inset(-6f * dp, -10f * dp) }
                if (expandedTrack.contains(x, y)) {
                    val spec = specs.firstOrNull { it.id == id }
                    tuneSliderDragTapToJump = context.defaultPrefs().getBoolean(LightspeedPreferences.keySliderTapToJump(id), false)
                    tuneSliderDragStartX = x
                    tuneSliderDragStartPos = spec?.getSliderPos(item) ?: 0
                    triggerHardwareHaptic(15, 60)
                    activeDraggingSliderId = id
                    if (tuneSliderDragTapToJump) {
                        updateSliderValueFromTouch(id, x)
                    } else {
                        invalidate()
                    }
                    return true
                }
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (pendingValueBoxSliderId != null) {
                if (abs(x - pendingValueBoxDownX) > 10f * dp || abs(y - pendingValueBoxDownY) > 10f * dp) {
                    cancelPendingValueBoxHold(this)
                }
                return true
            }
            if (tuneFontDropdownTracking && isTuneFontDropdownOpen) {
                val dy = y - tuneFontDropdownLastY
                tuneFontDropdownLastY = y
                if (abs(y - tuneFontDropdownDragStartY) > 10f) {
                    tuneFontDropdownDragged = true
                }
                tuneFontDropdownScrollOffset = (tuneFontDropdownScrollOffset - dy).coerceIn(0f, tuneFontDropdownMaxScroll)
                invalidate()
                return true
            }
            activeDraggingSliderId?.let { id ->
                updateSliderValueFromTouch(id, x)
                return true
            }
        }
        MotionEvent.ACTION_UP -> {
            if (pendingValueBoxSliderId != null) {
                val tappedId = pendingValueBoxSliderId
                val alreadyLongTriggered = pendingValueBoxLongTriggered
                val hitRect = tappedId?.let { tuneSliderValueRects[it] }
                cancelPendingValueBoxHold(this)
                if (!alreadyLongTriggered && tappedId != null && hitRect != null) {
                    val releaseBox = RectF(hitRect).apply { inset(-6f * dp, -6f * dp) }
                    if (releaseBox.contains(x, y)) {
                        triggerHardwareHaptic(20, 90)
                        openTuneCalibrationModal(tappedId)
                    }
                }
                return true
            }
            if (tuneFontDropdownTracking && isTuneFontDropdownOpen) {
                tuneFontDropdownTracking = false
                if (!tuneFontDropdownDragged) {
                    for ((fKey, rect) in tuneFontDropdownItemRects) {
                        if (rect.contains(x, y)) {
                            triggerHardwareHaptic(20, 90)
                            item.fontKey = fKey
                            isTuneFontDropdownOpen = false
                            applyLiveHudTuneItemChanges(catId, item)
                            return true
                        }
                    }
                }
                invalidate()
                return true
            }
            if (activeDraggingSliderId != null) {
                activeDraggingSliderId = null
                triggerHardwareHaptic(15, 60)
                invalidate()
                return true
            }
        }
        MotionEvent.ACTION_CANCEL -> {
            cancelPendingValueBoxHold(this)
            if (tuneFontDropdownTracking && isTuneFontDropdownOpen) {
                tuneFontDropdownTracking = false
                invalidate()
                return true
            }
            if (activeDraggingSliderId != null) {
                activeDraggingSliderId = null
                invalidate()
                return true
            }
        }
    }
    return true
}
