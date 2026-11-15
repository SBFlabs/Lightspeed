package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val bubbleArrowPath = Path()

internal fun LightspeedCruiseOverlay.openInPlaceCountdownModal(catId: String) {
    val prefs = context.defaultPrefs()
    val savedMs = prefs.getLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, 0L)
    val now = System.currentTimeMillis()
    countdownModalTargetMs = if (savedMs > now) savedMs else (now + 7L * 86_400_000L)
    countdownModalTitle = prefs.getString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, "TARGET")?.trim()?.ifBlank { "TARGET" } ?: "TARGET"
    countdownModalCatId = catId
    isCountdownModalOpen = true
    isScreenTimeInfoBubbleOpen = false
    triggerHardwareHaptic(18, 90)
    invalidate()
}

internal fun LightspeedCruiseOverlay.drawCountdownModal(canvas: Canvas, paint: Paint) {
    if (!isCountdownModalOpen) return
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()

    // 1. Dim Backdrop
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(140, 0, 0, 0)
    canvas.drawRect(0f, 0f, w, h, paint)

    // 2. Modal Card Bounds
    val modalW = (320f * dp).coerceAtMost(w - 24f * dp)
    val modalH = (292f * dp).coerceAtMost(h - 24f * dp)
    val modalLeft = (w - modalW) / 2f
    val modalTop = (h - modalH) / 2f
    val bounds = RectF(modalLeft, modalTop, modalLeft + modalW, modalTop + modalH)

    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val accent = ensureHighContrastColor(tintColor)

    // Card Glass Background
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(240, 10, 14, 22)
    canvas.drawRoundRect(bounds, 14f * dp, 14f * dp, paint)

    // Tactical Accent Border
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.2f * dp
    paint.color = Color.argb(125, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(bounds, 14f * dp, 14f * dp, paint)

    // Header
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 10f * dp
    paint.color = Color.WHITE
    canvas.drawText("◈ TARGET COUNTDOWN TIMER ◈", bounds.left + 14f * dp, bounds.top + 22f * dp, paint)

    // Close button [ ✕ ]
    val closeRect = RectF(bounds.right - 30f * dp, bounds.top + 8f * dp, bounds.right - 8f * dp, bounds.top + 30f * dp)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(35, 255, 255, 255)
    canvas.drawRoundRect(closeRect, 5f * dp, 5f * dp, paint)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 11f * dp
    paint.color = Color.WHITE
    canvas.drawText("✕", closeRect.centerX(), closeRect.centerY() + 4f * dp, paint)

    // 1. Preview Card
    val previewRect = RectF(bounds.left + 14f * dp, bounds.top + 32f * dp, bounds.right - 14f * dp, bounds.top + 84f * dp)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.8f * dp
    paint.color = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)

    val now = System.currentTimeMillis()
    val diffMs = countdownModalTargetMs - now
    val diffDays = (diffMs / (86_400_000L)).coerceAtLeast(0L)
    val diffHours = ((diffMs / (3_600_000L)) % 24).coerceAtLeast(0L)
    val dateStr = SimpleDateFormat("EEE, MMM d, yyyy", Locale.US).format(Date(countdownModalTargetMs))

    // Event title & Rename button in Preview Card
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 9.2f * dp
    paint.color = accent
    val displayTitle = countdownModalTitle.ifBlank { "TARGET" }.uppercase()
    canvas.drawText("EVENT: $displayTitle", previewRect.left + 10f * dp, previewRect.top + 16f * dp, paint)

    // Small [ ✎ RENAME ] button in preview card
    val renameBtnW = 68f * dp
    val renameBtnH = 16f * dp
    val renameBtnRect = RectF(previewRect.right - renameBtnW - 8f * dp, previewRect.top + 6f * dp, previewRect.right - 8f * dp, previewRect.top + 6f * dp + renameBtnH)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(55, 255, 255, 255)
    canvas.drawRoundRect(renameBtnRect, 4f * dp, 4f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.8f * dp
    paint.color = Color.argb(90, 255, 255, 255)
    canvas.drawRoundRect(renameBtnRect, 4f * dp, 4f * dp, paint)
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 7.5f * dp
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.color = Color.WHITE
    canvas.drawText("✎ RENAME", renameBtnRect.centerX(), renameBtnRect.centerY() + 2.8f * dp, paint)

    // Countdown Big Remaining & Target Date
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 13.5f * dp
    paint.color = Color.WHITE
    canvas.drawText("${diffDays}d ${diffHours}h REMAINING", previewRect.left + 10f * dp, previewRect.top + 33f * dp, paint)

    paint.typeface = Typeface.DEFAULT
    paint.textSize = 8.0f * dp
    paint.color = Color.argb(200, 200, 220, 245)
    canvas.drawText("TARGET DATE: $dateStr", previewRect.left + 10f * dp, previewRect.top + 46f * dp, paint)

    // 2. Event Name Presets Section
    val eventCaptionY = bounds.top + 94f * dp
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 7.2f * dp
    paint.color = Color.argb(160, 200, 220, 245)
    canvas.drawText("EVENT NAME (TAP PRESET OR RENAME)", bounds.left + 14f * dp, eventCaptionY, paint)

    val eventRow1Y = eventCaptionY + 5f * dp
    val eventPillH = 19f * dp
    val eventColW = (bounds.width() - 28f * dp - 12f * dp) / 3f

    val row1 = listOf("TARGET", "VACATION", "LAUNCH")
    for (i in row1.indices) {
        val preset = row1[i]
        val isSelected = countdownModalTitle.equals(preset, ignoreCase = true)
        val pLeft = bounds.left + 14f * dp + (i * (eventColW + 6f * dp))
        val pRect = RectF(pLeft, eventRow1Y, pLeft + eventColW, eventRow1Y + eventPillH)

        paint.style = Paint.Style.FILL
        paint.color = if (isSelected) Color.argb(160, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = if (isSelected) accent else Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 7.5f * dp
        paint.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = Color.WHITE
        canvas.drawText(preset, pRect.centerX(), pRect.centerY() + 2.8f * dp, paint)
    }

    val eventRow2Y = eventRow1Y + eventPillH + 5f * dp
    val row2 = listOf("GOAL", "EXAM", "✎ CUSTOM")
    for (i in row2.indices) {
        val label = row2[i]
        val isSelected = (i < 2 && countdownModalTitle.equals(label, ignoreCase = true))
        val pLeft = bounds.left + 14f * dp + (i * (eventColW + 6f * dp))
        val pRect = RectF(pLeft, eventRow2Y, pLeft + eventColW, eventRow2Y + eventPillH)

        paint.style = Paint.Style.FILL
        paint.color = if (isSelected) Color.argb(160, Color.red(accent), Color.green(accent), Color.blue(accent))
            else if (i == 2) Color.argb(55, Color.red(accent), Color.green(accent), Color.blue(accent))
            else Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = if (isSelected || i == 2) accent else Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 7.5f * dp
        paint.typeface = if (isSelected || i == 2) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        paint.color = Color.WHITE
        canvas.drawText(label, pRect.centerX(), pRect.centerY() + 2.8f * dp, paint)
    }

    // 3. Date Presets Section
    val dateCaptionY = eventRow2Y + eventPillH + 9f * dp
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 7.2f * dp
    paint.color = Color.argb(160, 200, 220, 245)
    canvas.drawText("TARGET DATE PRESETS", bounds.left + 14f * dp, dateCaptionY, paint)

    val presetsY = dateCaptionY + 5f * dp
    val presetH = 19f * dp
    val presetW = (bounds.width() - 28f * dp - (3f * 6f * dp)) / 4f
    val presets = listOf(1L to "+1 DAY", 7L to "+7 DAYS", 30L to "+30D", 90L to "+90D")
    for (i in presets.indices) {
        val (pDays, pLabel) = presets[i]
        val pLeft = bounds.left + 14f * dp + (i * (presetW + 6f * dp))
        val pRect = RectF(pLeft, presetsY, pLeft + presetW, presetsY + presetH)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(45, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = Color.argb(80, 255, 255, 255)
        canvas.drawRoundRect(pRect, 4f * dp, 4f * dp, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 7.5f * dp
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        canvas.drawText(pLabel, pRect.centerX(), pRect.centerY() + 2.8f * dp, paint)
    }

    // 4. Steppers Row (-1 DAY, +1 DAY)
    val stepperY = presetsY + presetH + 6f * dp
    val stepperW = (bounds.width() - 28f * dp - 8f * dp) / 2f
    val stepperOptions = listOf(-1L to "− 1 DAY", 1L to "+ 1 DAY")
    for (i in stepperOptions.indices) {
        val (dDelta, sLabel) = stepperOptions[i]
        val sLeft = bounds.left + 14f * dp + (i * (stepperW + 8f * dp))
        val sRect = RectF(sLeft, stepperY, sLeft + stepperW, stepperY + presetH)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(35, 255, 255, 255)
        canvas.drawRoundRect(sRect, 4f * dp, 4f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(sRect, 4f * dp, 4f * dp, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 8.0f * dp
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.color = Color.WHITE
        canvas.drawText(sLabel, sRect.centerX(), sRect.centerY() + 3f * dp, paint)
    }

    // 5. Action Row: [ RESET TO CALENDAR ]  [ ACTIVATE TARGET ]
    val actionY = bounds.bottom - 34f * dp
    val actionH = 25f * dp
    val actionW = (bounds.width() - 28f * dp - 10f * dp) / 2f

    // Reset button
    val resetRect = RectF(bounds.left + 14f * dp, actionY, bounds.left + 14f * dp + actionW, actionY + actionH)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(55, 230, 60, 60)
    canvas.drawRoundRect(resetRect, 6f * dp, 6f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(120, 230, 60, 60)
    canvas.drawRoundRect(resetRect, 6f * dp, 6f * dp, paint)

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 8.0f * dp
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.color = Color.WHITE
    canvas.drawText("RESET (CALENDAR)", resetRect.centerX(), resetRect.centerY() + 3.2f * dp, paint)

    // Save button
    val saveRect = RectF(bounds.left + 14f * dp + actionW + 10f * dp, actionY, bounds.right - 14f * dp, actionY + actionH)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(190, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(saveRect, 6f * dp, 6f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = accent
    canvas.drawRoundRect(saveRect, 6f * dp, 6f * dp, paint)

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 8.2f * dp
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.color = Color.WHITE
    canvas.drawText("ACTIVATE TARGET", saveRect.centerX(), saveRect.centerY() + 3.2f * dp, paint)
}

internal fun LightspeedCruiseOverlay.handleCountdownModalTouch(event: MotionEvent): Boolean {
    if (!isCountdownModalOpen) return false
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()

    val modalW = (320f * dp).coerceAtMost(w - 24f * dp)
    val modalH = (292f * dp).coerceAtMost(h - 24f * dp)
    val modalLeft = (w - modalW) / 2f
    val modalTop = (h - modalH) / 2f
    val bounds = RectF(modalLeft, modalTop, modalLeft + modalW, modalTop + modalH)

    val x = event.x
    val y = event.y

    if (event.actionMasked != MotionEvent.ACTION_UP) {
        return true
    }

    // Tap outside to close
    if (!bounds.contains(x, y)) {
        isCountdownModalOpen = false
        triggerHardwareHaptic(14, 70)
        invalidate()
        return true
    }

    // Close button
    val closeRect = RectF(bounds.right - 30f * dp, bounds.top + 8f * dp, bounds.right - 8f * dp, bounds.top + 30f * dp)
    if (closeRect.contains(x, y)) {
        isCountdownModalOpen = false
        triggerHardwareHaptic(14, 70)
        invalidate()
        return true
    }

    // Rename Button or Preview Card Top Title Area -> Launch Full Custom Name Activity
    val previewRect = RectF(bounds.left + 14f * dp, bounds.top + 32f * dp, bounds.right - 14f * dp, bounds.top + 84f * dp)
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

    val eventCaptionY = bounds.top + 94f * dp
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

    val now = System.currentTimeMillis()

    // Date Presets Row
    val dateCaptionY = eventRow2Y + eventPillH + 9f * dp
    val presetsY = dateCaptionY + 5f * dp
    val presetH = 19f * dp
    val presetW = (bounds.width() - 28f * dp - (3f * 6f * dp)) / 4f
    val presets = listOf(1L, 7L, 30L, 90L)
    for (i in presets.indices) {
        val pLeft = bounds.left + 14f * dp + (i * (presetW + 6f * dp))
        val pRect = RectF(pLeft, presetsY, pLeft + presetW, presetsY + presetH)
        if (pRect.contains(x, y)) {
            countdownModalTargetMs = now + (presets[i] * 86_400_000L)
            triggerHardwareHaptic(16, 80)
            invalidate()
            return true
        }
    }

    // Steppers Row
    val stepperY = presetsY + presetH + 6f * dp
    val stepperW = (bounds.width() - 28f * dp - 8f * dp) / 2f
    val stepperMinus = RectF(bounds.left + 14f * dp, stepperY, bounds.left + 14f * dp + stepperW, stepperY + presetH)
    val stepperPlus = RectF(bounds.left + 14f * dp + stepperW + 8f * dp, stepperY, bounds.right - 14f * dp, stepperY + presetH)
    if (stepperMinus.contains(x, y)) {
        countdownModalTargetMs = (countdownModalTargetMs - 86_400_000L).coerceAtLeast(now + 3_600_000L)
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }
    if (stepperPlus.contains(x, y)) {
        countdownModalTargetMs += 86_400_000L
        triggerHardwareHaptic(16, 80)
        invalidate()
        return true
    }

    // Action Row
    val actionY = bounds.bottom - 34f * dp
    val actionH = 25f * dp
    val actionW = (bounds.width() - 28f * dp - 10f * dp) / 2f
    val resetRect = RectF(bounds.left + 14f * dp, actionY, bounds.left + 14f * dp + actionW, actionY + actionH)
    val saveRect = RectF(bounds.left + 14f * dp + actionW + 10f * dp, actionY, bounds.right - 14f * dp, actionY + actionH)

    if (resetRect.contains(x, y)) {
        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, false)
            .apply()
        isCountdownModalOpen = false
        triggerHardwareHaptic(20, 100)
        invalidate()
        return true
    }

    if (saveRect.contains(x, y)) {
        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, true)
            .putLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, countdownModalTargetMs)
            .putString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, countdownModalTitle)
            .apply()
        isCountdownModalOpen = false
        triggerHardwareHaptic(22, 110)
        invalidate()
        return true
    }

    return true
}

internal fun LightspeedCruiseOverlay.drawScreenTimeInfoBubble(canvas: Canvas) {
    if (!isScreenTimeInfoBubbleOpen) return
    val dp = resources.displayMetrics.density
    val anchor = screenTimeInfoBubbleRect
    if (anchor.isEmpty) return

    val w = width.toFloat()
    val bubbleW = (236f * dp).coerceAtMost(w - 24f * dp)
    val bubbleH = 68f * dp

    val bubbleLeft = (anchor.right - bubbleW).coerceIn(12f * dp, w - bubbleW - 12f * dp)
    val bubbleTop = anchor.bottom + 5f * dp
    val bounds = RectF(bubbleLeft, bubbleTop, bubbleLeft + bubbleW, bubbleTop + bubbleH)

    val snapshot = DeckThemeManager.getSnapshot(context)
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val accent = ensureHighContrastColor(tintColor)

    val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Glass Card Surface
    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(245, 12, 16, 26)
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    // Tactical Accent Border
    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.strokeWidth = 1.2f * dp
    bubblePaint.color = accent
    canvas.drawRoundRect(bounds, 8f * dp, 8f * dp, bubblePaint)

    // Pointer Triangle pointing up to info badge
    val arrowX = anchor.centerX().coerceIn(bounds.left + 12f * dp, bounds.right - 12f * dp)
    bubbleArrowPath.reset()
    bubbleArrowPath.moveTo(arrowX - 5f * dp, bounds.top)
    bubbleArrowPath.lineTo(arrowX, bounds.top - 5f * dp)
    bubbleArrowPath.lineTo(arrowX + 5f * dp, bounds.top)
    bubbleArrowPath.close()

    bubblePaint.style = Paint.Style.FILL
    bubblePaint.color = Color.argb(245, 12, 16, 26)
    canvas.drawPath(bubbleArrowPath, bubblePaint)

    bubblePaint.style = Paint.Style.STROKE
    bubblePaint.strokeWidth = 1.2f * dp
    bubblePaint.color = accent
    canvas.drawLine(arrowX - 5f * dp, bounds.top, arrowX, bounds.top - 5f * dp, bubblePaint)
    canvas.drawLine(arrowX, bounds.top - 5f * dp, arrowX + 5f * dp, bounds.top, bubblePaint)

    // Text Contents
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        textSize = 8.8f * dp
        color = accent
    }
    canvas.drawText("◈ SCREEN TIME TELEMETRY", bounds.left + 10f * dp, bounds.top + 16f * dp, textPaint)

    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 8.0f * dp
    textPaint.color = Color.WHITE
    canvas.drawText("• DAY: Today from 00:00 to now", bounds.left + 10f * dp, bounds.top + 31f * dp, textPaint)
    canvas.drawText("• WK / MO / YR: Rolling 7 / 30 / 365 days window", bounds.left + 10f * dp, bounds.top + 45f * dp, textPaint)

    textPaint.textSize = 7.2f * dp
    textPaint.color = Color.argb(160, 200, 220, 255)
    canvas.drawText("✦ Tap anywhere to dismiss", bounds.left + 10f * dp, bounds.top + 59f * dp, textPaint)
}
