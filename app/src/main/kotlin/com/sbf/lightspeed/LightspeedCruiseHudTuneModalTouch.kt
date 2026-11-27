package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.abs
import kotlin.math.roundToInt

private val tuneHitTempRect = RectF()
private val tunePickerEllipsizePaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

internal fun LightspeedCruiseOverlay.drawTuneCategoryPickerOverlay(
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
    textPaint.typeface = overlayTypeface(isBold = true, fallback = Typeface.MONOSPACE)
    textPaint.textSize = 8.4f * dp
    textPaint.color = colors.accentColor
    val hFm = textPaint.fontMetrics
    canvas.drawText(
        com.sbf.lightspeed.system.lsString(R.string.ed_tune_move_to_cat),
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
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 8.6f * dp
        textPaint.color = colors.textPrimary
        tunePickerEllipsizePaint.set(textPaint)
        val fitted = TextUtils.ellipsize(
            LightspeedDataBridge.localizedCategoryLabel(catNode.id, catNode.label).uppercase(),
            tunePickerEllipsizePaint,
            (colW - 10f * dp).coerceAtLeast(20f * dp),
            TextUtils.TruncateAt.END
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
                            refreshHudAndAppGridLayout()
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

            tuneHitTempRect.set(tuneMoveUpBtnRect)
            tuneHitTempRect.inset(-2f * dp, -3f * dp)
            if (tuneHitTempRect.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemVertical(catId, item.id, -1))
            }
            tuneHitTempRect.set(tuneMoveDownBtnRect)
            tuneHitTempRect.inset(-2f * dp, -3f * dp)
            if (tuneHitTempRect.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemVertical(catId, item.id, +1))
            }
            tuneHitTempRect.set(tuneMovePrevBtnRect)
            tuneHitTempRect.inset(-2f * dp, -3f * dp)
            if (tuneHitTempRect.contains(x, y)) {
                return handleMoveTap(moveCategoryHudItemStep(catId, item.id, -1))
            }
            tuneHitTempRect.set(tuneMoveNextBtnRect)
            tuneHitTempRect.inset(-2f * dp, -3f * dp)
            if (tuneHitTempRect.contains(x, y)) {
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

            // Device Font Dropdown Toggle (strictly for Focused App Inspector)
            if (item.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT &&
                !tuneFontDropdownRect.isEmpty &&
                tuneFontDropdownRect.contains(x, y)
            ) {
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

            // WK Progress Cycle Pills (for Screen Time Telemetry)
            for ((modeKey, rect) in tuneWeekCyclePillRects) {
                if (rect.contains(x, y)) {
                    triggerHardwareHaptic(20, 90)
                    LightspeedCruiseHudPods.setWeekCycleMode(context, modeKey)
                    invalidate()
                    return true
                }
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
                tuneHitTempRect.set(box)
                tuneHitTempRect.inset(-3f * dp, -4f * dp)
                if (tuneHitTempRect.contains(x, y)) {
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
                tuneHitTempRect.set(track)
                tuneHitTempRect.inset(-6f * dp, -10f * dp)
                if (tuneHitTempRect.contains(x, y)) {
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
                    tuneHitTempRect.set(hitRect)
                    tuneHitTempRect.inset(-6f * dp, -6f * dp)
                    if (tuneHitTempRect.contains(x, y)) {
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
