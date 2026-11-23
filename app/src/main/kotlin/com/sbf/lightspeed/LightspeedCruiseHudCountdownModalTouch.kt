package com.sbf.lightspeed

import android.content.Intent
import android.graphics.RectF
import android.view.MotionEvent
import com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine

/**
 * Touch interaction handler for the Category Cruise Target Countdown Timer modal.
 * Split from [LightspeedCruiseHudCountdownModal.kt] to keep rendering and input cleanly modular.
 */
internal fun LightspeedCruiseOverlay.handleCountdownModalTouch(event: MotionEvent): Boolean {
    if (!isCountdownModalOpen) return false
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()

    val modalW = (COUNTDOWN_MODAL_WIDTH_DP * dp).coerceAtMost(w - 24f * dp)
    val modalH = (COUNTDOWN_MODAL_HEIGHT_DP * dp).coerceAtMost(h - 24f * dp)
    val modalLeft = (w - modalW) / 2f
    val modalTop = (h - modalH) / 2f
    val bounds = RectF(modalLeft, modalTop, modalLeft + modalW, modalTop + modalH)

    val x = event.x
    val y = event.y
    val wheelStepPx = 22f * dp
    val isCalMode = (countdownModalSource == LightspeedCalendarCountdownEngine.SOURCE_CALENDAR)

    fun stepWheel(wheelIdx: Int, deltaSteps: Int) {
        if (deltaSteps == 0) return
        val now = System.currentTimeMillis()
        val parts = decomposeCountdownTarget(countdownModalTargetMs, now)
        val maxVal = COUNTDOWN_WHEEL_MAX[wheelIdx]
        parts[wheelIdx] = wrapWheelValue(parts[wheelIdx] + deltaSteps, maxVal)
        countdownModalTargetMs = composeCountdownTarget(now, parts[0], parts[1], parts[2], parts[3])
        triggerHardwareHaptic(11, 60)
        invalidate()
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            if (isCalMode && !countdownCalListRect.isEmpty && countdownCalListRect.contains(x, y)) {
                countdownCalDragging = true
                countdownCalDragLastY = y
                countdownCalDidDrag = false
                return true
            }
            if (!isCalMode) {
                for (i in countdownWheelRects.indices) {
                    if (countdownWheelRects[i].contains(x, y)) {
                        countdownActiveWheelIdx = i
                        countdownWheelStartY = y
                        countdownWheelAccumDy = 0f
                        countdownWheelDidDrag = false
                        invalidate()
                        return true
                    }
                }
            }
            return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (countdownCalDragging) {
                val dy = y - countdownCalDragLastY
                countdownCalDragLastY = y
                if (kotlin.math.abs(dy) > 2f * dp) countdownCalDidDrag = true
                if (countdownCalMaxScroll > 0f) {
                    countdownCalScrollOffset = (countdownCalScrollOffset - dy * 1.25f).coerceIn(0f, countdownCalMaxScroll)
                    invalidate()
                }
                return true
            }
            if (countdownActiveWheelIdx in 0..3) {
                val dy = y - countdownWheelStartY
                countdownWheelStartY = y
                countdownWheelAccumDy += dy
                if (kotlin.math.abs(countdownWheelAccumDy) >= wheelStepPx) {
                    val steps = (-countdownWheelAccumDy / wheelStepPx).toInt()
                    countdownWheelAccumDy += steps * wheelStepPx
                    countdownWheelDidDrag = true
                    stepWheel(countdownActiveWheelIdx, steps)
                } else {
                    if (kotlin.math.abs(dy) > 3f * dp) countdownWheelDidDrag = true
                    invalidate()
                }
                return true
            }
            return true
        }
        MotionEvent.ACTION_CANCEL -> {
            countdownCalDragging = false
            countdownCalDidDrag = false
            countdownActiveWheelIdx = -1
            countdownWheelAccumDy = 0f
            countdownWheelDidDrag = false
            invalidate()
            return true
        }
        MotionEvent.ACTION_UP -> {
            if (countdownCalDragging) {
                val wasDrag = countdownCalDidDrag
                countdownCalDragging = false
                countdownCalDidDrag = false
                if (wasDrag) return true
            }
            if (countdownActiveWheelIdx in 0..3) {
                val idx = countdownActiveWheelIdx
                val wasDrag = countdownWheelDidDrag
                countdownActiveWheelIdx = -1
                countdownWheelAccumDy = 0f
                countdownWheelDidDrag = false
                if (!wasDrag) {
                    val r = countdownWheelRects[idx]
                    val midY = r.top + 15f * dp + (r.bottom - (r.top + 15f * dp)) * 0.5f
                    if (y < midY - 10f * dp) stepWheel(idx, -1)
                    else if (y > midY + 10f * dp) stepWheel(idx, +1)
                    else invalidate()
                } else {
                    invalidate()
                }
                return true
            }
        }
        else -> return true
    }

    // Tap outside to close
    if (!bounds.contains(x, y)) {
        isCountdownModalOpen = false
        triggerHardwareHaptic(14, 70)
        invalidate()
        return true
    }

    // Close button & [ ! ] Info toggle
    val closeRect = RectF(bounds.right - 30f * dp, bounds.top + 8f * dp, bounds.right - 8f * dp, bounds.top + 30f * dp)
    if (closeRect.contains(x, y)) {
        countdownModalInfoOpen = false
        isCountdownModalOpen = false
        triggerHardwareHaptic(14, 70)
        invalidate()
        return true
    }
    if (!countdownInfoBtnRect.isEmpty && countdownInfoBtnRect.contains(x, y)) {
        countdownModalInfoOpen = !countdownModalInfoOpen
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }
    if (countdownModalInfoOpen && !countdownInfoCardRect.isEmpty && countdownInfoCardRect.contains(x, y)) {
        countdownModalInfoOpen = false
        triggerHardwareHaptic(14, 70)
        invalidate()
        return true
    }

    // Mode & Scope toggles
    if (countdownSourceManualRect.contains(x, y)) {
        countdownModalSource = LightspeedCalendarCountdownEngine.SOURCE_MANUAL
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }
    if (countdownSourceCalRect.contains(x, y)) {
        countdownModalSource = LightspeedCalendarCountdownEngine.SOURCE_CALENDAR
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }
    if (countdownScopeToggleRect.contains(x, y)) {
        countdownModalUsePerWidget = !countdownModalUsePerWidget
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }

    // Calendar Mode interactions
    if (isCalMode) {
        if (!countdownCalGrantBtnRect.isEmpty && countdownCalGrantBtnRect.contains(x, y)) {
            triggerHardwareHaptic(18, 90)
            isCountdownModalOpen = false
            dismissOverlay()
            try {
                val intent = Intent(context, com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(
                        com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.EXTRA_MODE,
                        com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.MODE_REQUEST_CALENDAR_PERMISSION
                    )
                    putExtra(
                        com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity.EXTRA_RETURN_CATEGORY_ID,
                        countdownModalCatId
                    )
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
            return true
        }
        for ((calKey, rect) in countdownCalItemRects) {
            if (rect.contains(x, y)) {
                if (calKey == "__ALL__") {
                    countdownModalCalendarIds.clear()
                } else {
                    if (calKey in countdownModalCalendarIds) {
                        countdownModalCalendarIds.remove(calKey)
                    } else {
                        countdownModalCalendarIds.add(calKey)
                    }
                }
                LightspeedCalendarCountdownEngine.invalidateCaches()
                triggerHardwareHaptic(16, 80)
                invalidate()
                return true
            }
        }
    }

    // Action Row (shared by Manual & Calendar modes)
    val actionY = bounds.bottom - 34f * dp
    val actionH = 25f * dp
    val actionW = (bounds.width() - 28f * dp - 10f * dp) / 2f
    val resetRect = RectF(bounds.left + 14f * dp, actionY, bounds.left + 14f * dp + actionW, actionY + actionH)
    val saveRect = RectF(bounds.left + 14f * dp + actionW + 10f * dp, actionY, bounds.right - 14f * dp, actionY + actionH)

    if (resetRect.contains(x, y)) {
        LightspeedCalendarCountdownEngine.saveConfig(
            context = context,
            catId = countdownModalCatId,
            slotIndex = countdownModalSlotIndex,
            config = LightspeedCalendarCountdownEngine.CountdownConfig(
                usePerWidget = countdownModalUsePerWidget,
                enabled = false,
                source = countdownModalSource,
                title = countdownModalTitle,
                targetMs = countdownModalTargetMs,
                calendarIds = countdownModalCalendarIds.toSet()
            )
        )
        isCountdownModalOpen = false
        triggerHardwareHaptic(20, 100)
        invalidate()
        return true
    }

    if (saveRect.contains(x, y)) {
        val nowMs = System.currentTimeMillis()
        LightspeedCalendarCountdownEngine.saveConfig(
            context = context,
            catId = countdownModalCatId,
            slotIndex = countdownModalSlotIndex,
            config = LightspeedCalendarCountdownEngine.CountdownConfig(
                usePerWidget = countdownModalUsePerWidget,
                enabled = true,
                source = countdownModalSource,
                title = countdownModalTitle,
                targetMs = countdownModalTargetMs,
                calendarIds = countdownModalCalendarIds.toSet(),
                startMs = nowMs
            )
        )
        LightspeedCalendarCountdownEngine.invalidateCaches()
        countdownModalInfoOpen = false
        isCountdownModalOpen = false
        triggerHardwareHaptic(22, 110)
        invalidate()
        return true
    }

    if (isCalMode) return true

    // Rename Button or Preview Card Top Title Area (Manual Mode)
    val previewTop = bounds.top + 60f * dp
    val previewRect = RectF(bounds.left + 14f * dp, previewTop, bounds.right - 14f * dp, previewTop + 52f * dp)
    val renameBtnW = 68f * dp
    val renameBtnH = 16f * dp
    val renameBtnRect = RectF(previewRect.right - renameBtnW - 8f * dp, previewRect.top + 6f * dp, previewRect.right - 8f * dp, previewRect.top + 6f * dp + renameBtnH)

    val openCustomRenameAction = {
        val intent = Intent(context, CockpitDialogActivity::class.java).apply {
            action = CockpitDialogActivity.ACTION_TIMELINE_COUNTDOWN
            putExtra(CockpitDialogActivity.EXTRA_CATEGORY_ID, countdownModalCatId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        }
        isCountdownModalOpen = false
        dismissOverlay()
        context.startActivity(intent)
    }

    if (renameBtnRect.contains(x, y)) {
        triggerHardwareHaptic(18, 90)
        openCustomRenameAction()
        return true
    }

    val eventCaptionY = previewRect.bottom + 10f * dp
    val eventRow1Y = eventCaptionY + 5f * dp
    val eventPillH = 19f * dp
    val eventColW = (bounds.width() - 28f * dp - 12f * dp) / 3f

    // Event Presets Row 1: ["TARGET", "VACATION", "LAUNCH"]
    val row1 = listOf("TARGET", "VACATION", "LAUNCH")
    for (i in row1.indices) {
        val pLeft = bounds.left + 14f * dp + (i * (eventColW + 6f * dp))
        val pRect = RectF(pLeft, eventRow1Y, pLeft + eventColW, eventRow1Y + eventPillH)
        if (pRect.contains(x, y)) {
            countdownModalTitle = row1[i]
            triggerHardwareHaptic(16, 80)
            invalidate()
            return true
        }
    }

    // Event Presets Row 2: ["GOAL", "EXAM", "✎ CUSTOM"]
    val eventRow2Y = eventRow1Y + eventPillH + 5f * dp
    val row2 = listOf("GOAL", "EXAM")
    for (i in row2.indices) {
        val pLeft = bounds.left + 14f * dp + (i * (eventColW + 6f * dp))
        val pRect = RectF(pLeft, eventRow2Y, pLeft + eventColW, eventRow2Y + eventPillH)
        if (pRect.contains(x, y)) {
            countdownModalTitle = row2[i]
            triggerHardwareHaptic(16, 80)
            invalidate()
            return true
        }
    }

    val customBtnLeft = bounds.left + 14f * dp + (2 * (eventColW + 6f * dp))
    val customBtnRect = RectF(customBtnLeft, eventRow2Y, customBtnLeft + eventColW, eventRow2Y + eventPillH)
    if (customBtnRect.contains(x, y)) {
        triggerHardwareHaptic(18, 90)
        openCustomRenameAction()
        return true
    }

    return true
}
