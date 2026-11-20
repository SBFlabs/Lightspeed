package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.roundToInt

// Cached hit rects for non-occluding Per-Widget Tune Panel (split from LightspeedCruiseHudEditControls.kt)
internal val tuneModalRect = RectF()
internal val tuneMoveUpBtnRect = RectF()
internal val tuneMoveDownBtnRect = RectF()
internal val tuneMovePrevBtnRect = RectF()
internal val tuneMoveNextBtnRect = RectF()
internal val tuneCloseBtnRect = RectF()
internal val tuneDoneBtnRect = RectF()
internal val tuneResetBtnRect = RectF()
internal val tuneZeroAllPadsBtnRect = RectF()
internal val tuneMoveCategoryBtnRect = RectF()
internal val tuneCategoryPickerRect = RectF()
internal val tuneCategoryPickerItemRects = mutableListOf<Pair<String, RectF>>()
internal var isTuneCategoryPickerOpen: Boolean = false
internal val tuneSliderTrackRects = mutableMapOf<String, RectF>()
internal val tuneSliderValueRects = mutableMapOf<String, RectF>()
internal val tuneScopePillRects = mutableMapOf<Int, RectF>()
internal var tuneSliderDragStartX: Float = 0f
internal var tuneSliderDragStartPos: Int = 0
internal var tuneSliderDragTapToJump: Boolean = false

// Pending long-press tracking on slider numeric value boxes (400ms reset to default vs <400ms calibration popup)
internal var pendingValueBoxOverlay: LightspeedCruiseOverlay? = null
internal var pendingValueBoxCatId: String? = null
internal var pendingValueBoxItem: CategoryHudItem? = null
internal var pendingValueBoxSliderId: String? = null
internal var pendingValueBoxDownX: Float = 0f
internal var pendingValueBoxDownY: Float = 0f
internal var pendingValueBoxLongTriggered: Boolean = false
internal var tuneStatusText: String? = null
internal var tuneStatusUntilMs: Long = 0L
private var lockedTuneModalTop: Float? = null

private val tuneHeaderEllipsizePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
private val tuneTempFillRect = RectF()
private val tuneTempRowRect = RectF()

internal val tuneValueBoxLongPressRunnable = Runnable {
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

internal fun cancelPendingValueBoxHold(overlay: LightspeedCruiseOverlay?) {
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
    lockedTuneModalTop = null
    hudTuneTargetItem = item
    isHudTuneModalOpen = (item != null)
    activeDraggingSliderId = null
    isTuneFontDropdownOpen = false
    tuneFontDropdownTracking = false
    tuneFontDropdownRect.setEmpty()
    tuneFontDropdownListRect.setEmpty()
    tuneFontDropdownItemRects.clear()
    isTuneCategoryPickerOpen = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeHudTuneModal() {
    cancelPendingValueBoxHold(this)
    closeTuneCalibrationModal()
    tuneStatusText = null
    tuneStatusUntilMs = 0L
    lockedTuneModalTop = null
    isHudTuneModalOpen = false
    hudTuneTargetItem = null
    activeDraggingSliderId = null
    isTuneFontDropdownOpen = false
    tuneFontDropdownTracking = false
    tuneFontDropdownRect.setEmpty()
    tuneFontDropdownListRect.setEmpty()
    tuneFontDropdownItemRects.clear()
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
        val minTop = getSafeHudTopOffset() + 40f * dp
        val maxTop = (screenH - modalH - 10f * dp).coerceAtLeast(minTop)
        // Pin the tune panel at its initial docked App-Grid/top anchor while open so widget moves or resizes never jump it.
        top = (lockedTuneModalTop ?: run {
            val itemCenterY = item.bounds.centerY() - hudScrollOffset
            val dockAtTop = itemCenterY > screenH * 0.56f &&
                (item.bounds.top - hudScrollOffset - minTop) > modalH * 0.65f
            (if (dockAtTop) minTop else maxTop).also { lockedTuneModalTop = it }
        }).coerceIn(minTop, maxTop)
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
    textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
    val ts = overlayTextScale().coerceIn(0.85f, 1.25f)
    textPaint.textSize = 11.5f * dp * ts
    textPaint.color = if (activeStatus != null) themeAccent else colors.textPrimary
    val fm = textPaint.fontMetrics
    val rawHeaderText = activeStatus ?: "TUNE · ${hudWidgetDisplayName(item.type, short = true)}"
    val maxTitleW = (tuneMoveUpBtnRect.left - 8f * dp) - (left + 14f * dp)
    val headerText = if (maxTitleW > 12f * dp) {
        tuneHeaderEllipsizePaint.set(textPaint)
        TextUtils.ellipsize(rawHeaderText, tuneHeaderEllipsizePaint, maxTitleW, TextUtils.TruncateAt.END).toString()
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
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 11.8f * dp * ts
    textPaint.color = colors.textPrimary
    val cFm = textPaint.fontMetrics
    canvas.drawText("✕", tuneCloseBtnRect.centerX(), tuneCloseBtnRect.centerY() - ((cFm.descent + cFm.ascent) / 2f), textPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.8f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawLine(left + 14f * dp, top + headerH - 2f * dp, left + modalW - 14f * dp, top + headerH - 2f * dp, highlightPaint)

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
        val valBoxRect = tuneSliderValueRects.getOrPut(spec.id) { RectF() }
        valBoxRect.set(valBoxLeft, trackCenterY - valBoxH / 2f, valBoxRight, trackCenterY + valBoxH / 2f)

        val trackLeft = valBoxRight + 10f * dp
        val trackW = (rowRight - trackLeft).coerceAtLeast(60f * dp)
        val trackH = 4.6f * dp
        val trackRect = tuneSliderTrackRects.getOrPut(spec.id) { RectF() }
        trackRect.set(trackLeft, trackCenterY - trackH / 2f, rowRight, trackCenterY + trackH / 2f)

        // Left Label
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 10.3f * dp * ts
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
        textPaint.textSize = 10.1f * dp * ts
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
            tuneTempFillRect.set(trackLeft, trackRect.top, fillRight, trackRect.bottom)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(210, accR, accG, accB)
            canvas.drawRoundRect(tuneTempFillRect, trackH / 2f, trackH / 2f, highlightPaint)
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
    if (!isInspector) {
        tuneFontDropdownRect.setEmpty()
        tuneFontDropdownListRect.setEmpty()
        tuneFontDropdownItemRects.clear()
        isTuneFontDropdownOpen = false
        tuneFontDropdownTracking = false
    }

    if (isInspector) {
        val fontLabelY = curY + 10f * dp
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 10.0f * dp * ts
        textPaint.color = colors.textMuted
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tune_font).ifBlank { "FONT FAMILY" }, left + 14f * dp, fontLabelY, textPaint)

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
        val fBtnH = 26f * dp
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
        var fontBtnSize = 10.8f * dp * ts
        textPaint.textSize = fontBtnSize
        val maxFontLabelW = (tuneFontDropdownRect.width() - 30f * dp).coerceAtLeast(40f * dp)
        while (textPaint.measureText(cleanFontLabel) > maxFontLabelW && fontBtnSize > 8.5f * dp) {
            fontBtnSize -= 0.4f * dp
            textPaint.textSize = fontBtnSize
        }
        textPaint.color = colors.textPrimary
        val fFm = textPaint.fontMetrics
        canvas.drawText(clipTextToWidth(cleanFontLabel, maxFontLabelW), tuneFontDropdownRect.left + 10f * dp, tuneFontDropdownRect.centerY() - ((fFm.descent + fFm.ascent) / 2f), textPaint)

        textPaint.textAlign = Paint.Align.RIGHT
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 10.8f * dp * ts
        textPaint.color = themeAccent
        canvas.drawText(if (isTuneFontDropdownOpen) "▲" else "▼", tuneFontDropdownRect.right - 10f * dp, tuneFontDropdownRect.centerY() - ((fFm.descent + fFm.ascent) / 2f), textPaint)
        curY += extraRowH
    } else if (isVitals) {
        val isF = LightspeedCruiseHudPods.isTempFahrenheit(context)
        val uTop = curY + 4f * dp
        tuneTempUnitPillRect.set(left + 14f * dp, uTop, left + modalW - 14f * dp, uTop + 22f * dp)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(tuneTempUnitPillRect, 7f * dp, 7f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = themeAccent
        canvas.drawRoundRect(tuneTempUnitPillRect, 7f * dp, 7f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 10.0f * dp * ts
        textPaint.color = colors.textPrimary
        val tFm = textPaint.fontMetrics
        canvas.drawText("TEMPERATURE UNIT: ${if (isF) "FAHRENHEIT (°F)" else "CELSIUS (°C)"} (TAP TO SWITCH)", tuneTempUnitPillRect.centerX(), tuneTempUnitPillRect.centerY() - ((tFm.descent + tFm.ascent) / 2f), textPaint)
        curY += extraRowH
    }

    // 3-Way Per-Widget Visibility Scope Selector
    val scopeLabelY = curY + 11f * dp
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 10.0f * dp * ts
    textPaint.color = colors.textMuted
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tune_scope).ifBlank { "VISIBILITY SCOPE" }, left + 14f * dp, scopeLabelY, textPaint)

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
        val pRect = tuneScopePillRects.getOrPut(scopeVal) { RectF() }
        pRect.set(pLeft, pillY, pLeft + pillW, pillY + pillH)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isSel) themeAccent else colors.pillBg
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.9f * dp
        highlightPaint.color = if (isSel) themeAccent else colors.pillBorder
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = isSel)
        textPaint.textSize = 9.4f * dp * ts
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
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 10.0f * dp * ts
    textPaint.color = colors.textPrimary
    val zFm = textPaint.fontMetrics
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tune_zero_pad).ifBlank { "0 PAD" }, tuneZeroAllPadsBtnRect.centerX(), tuneZeroAllPadsBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneMoveCategoryBtnRect.set(tuneZeroAllPadsBtnRect.right + footerBtnGap, footerBtnY, tuneZeroAllPadsBtnRect.right + footerBtnGap + btnW, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (isTuneCategoryPickerOpen) themeAccent else colors.pillBg
    canvas.drawRoundRect(tuneMoveCategoryBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = if (isTuneCategoryPickerOpen) themeAccent else colors.pillBorder
    canvas.drawRoundRect(tuneMoveCategoryBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 10.0f * dp * ts
    textPaint.color = if (isTuneCategoryPickerOpen) colors.onAccentColor else colors.textPrimary
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tune_move).ifBlank { "MOVE" }, tuneMoveCategoryBtnRect.centerX(), tuneMoveCategoryBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneResetBtnRect.set(tuneMoveCategoryBtnRect.right + footerBtnGap, footerBtnY, tuneMoveCategoryBtnRect.right + footerBtnGap + btnW, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, 175, 55, 65)
    canvas.drawRoundRect(tuneResetBtnRect, 7f * dp, 7f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(115, 255, 115, 125)
    canvas.drawRoundRect(tuneResetBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 10.0f * dp * ts
    textPaint.color = Color.argb(235, 255, 180, 185)
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tune_reset).ifBlank { "RESET" }, tuneResetBtnRect.centerX(), tuneResetBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

    tuneDoneBtnRect.set(tuneResetBtnRect.right + footerBtnGap, footerBtnY, left + modalW - 14f * dp, footerBtnY + footerBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = themeAccent
    canvas.drawRoundRect(tuneDoneBtnRect, 7f * dp, 7f * dp, highlightPaint)
    textPaint.textSize = 10.6f * dp * ts
    textPaint.color = colors.onAccentColor
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_done).ifBlank { "DONE" }, tuneDoneBtnRect.centerX(), tuneDoneBtnRect.centerY() - ((zFm.descent + zFm.ascent) / 2f), textPaint)

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
        val minListTop = tuneModalRect.top + 8f * dp
        val maxListTop = (tuneModalRect.bottom - 8f * dp - listH).coerceAtLeast(minListTop)
        val listTop = (tuneFontDropdownRect.top - listH - 4f * dp).coerceIn(minListTop, maxListTop)
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

        val fClipL = tuneFontDropdownListRect.left + 2f * dp
        val fClipT = tuneFontDropdownListRect.top + 3f * dp
        val fClipR = tuneFontDropdownListRect.right - 2f * dp
        val fClipB = tuneFontDropdownListRect.bottom - 3f * dp
        val fLayerCount = canvas.saveLayer(fClipL, fClipT, fClipR, fClipB, null)
        canvas.clipRect(fClipL, fClipT, fClipR, fClipB)
        var rowY = tuneFontDropdownListRect.top + 4f * dp - tuneFontDropdownScrollOffset
        for ((fKey, fLabel) in installedFonts) {
            tuneTempRowRect.set(tuneFontDropdownListRect.left + 6f * dp, rowY, tuneFontDropdownListRect.right - 10f * dp, rowY + rowH - 2f * dp)
            if (tuneTempRowRect.bottom >= tuneFontDropdownListRect.top && tuneTempRowRect.top <= tuneFontDropdownListRect.bottom) {
                tuneFontDropdownItemRects.getOrPut(fKey) { RectF() }.set(tuneTempRowRect)
                val isSel = (fKey == activeFontKey)
                if (isSel) {
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(65, accR, accG, accB)
                    canvas.drawRoundRect(tuneTempRowRect, 5f * dp, 5f * dp, highlightPaint)
                }
                val cleanLabel = fLabel.replace(Regex("[^\\p{ASCII}\\p{L}\\p{N}\\p{P}\\p{Z}]"), "").trim()
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = resolveDeviceFontTypeface(fKey, isBold = isSel) ?: if (isSel) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                var itemFontSize = 11.0f * dp * ts
                textPaint.textSize = itemFontSize
                val maxItemW = (tuneTempRowRect.width() - 16f * dp).coerceAtLeast(40f * dp)
                while (textPaint.measureText(cleanLabel) > maxItemW && itemFontSize > 8.5f * dp) {
                    itemFontSize -= 0.4f * dp
                    textPaint.textSize = itemFontSize
                }
                textPaint.color = if (isSel) themeAccent else colors.textPrimary
                val rFm = textPaint.fontMetrics
                canvas.drawText(clipTextToWidth(cleanLabel, maxItemW), tuneTempRowRect.left + 8f * dp, tuneTempRowRect.centerY() - ((rFm.descent + rFm.ascent) / 2f), textPaint)
            }
            rowY += rowH
        }
        LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
            canvas = canvas,
            left = fClipL,
            top = fClipT,
            right = fClipR,
            bottom = fClipB,
            featherTopPx = if (tuneFontDropdownScrollOffset > 0.5f) 14f * dp else 0f,
            featherBottomPx = if (tuneFontDropdownScrollOffset < tuneFontDropdownMaxScroll - 0.5f) 16f * dp else 0f,
            density = dp,
            seed = 8505
        )
        canvas.restoreToCount(fLayerCount)

        if (tuneFontDropdownMaxScroll > 0f) {
            val sbW = 2.5f * dp
            val sbX = tuneFontDropdownListRect.right - 6f * dp
            val trackTop = tuneFontDropdownListRect.top + 6f * dp
            val trackH = tuneFontDropdownListRect.height() - 12f * dp
            val thumbH = (trackH * (trackH / totalH)).coerceIn(18f * dp, trackH * 0.6f)
            val thumbY = trackTop + (tuneFontDropdownScrollOffset / tuneFontDropdownMaxScroll) * (trackH - thumbH)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = colors.dividerColor
            tuneTempRowRect.set(sbX, trackTop, sbX + sbW, trackTop + trackH)
            canvas.drawRoundRect(tuneTempRowRect, 1.5f * dp, 1.5f * dp, highlightPaint)
            highlightPaint.color = themeAccent
            tuneTempRowRect.set(sbX, thumbY, sbX + sbW, thumbY + thumbH)
            canvas.drawRoundRect(tuneTempRowRect, 1.5f * dp, 1.5f * dp, highlightPaint)
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
