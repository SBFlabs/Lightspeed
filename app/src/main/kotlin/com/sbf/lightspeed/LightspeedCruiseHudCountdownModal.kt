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
import com.sbf.lightspeed.system.LightspeedCalendarCountdownEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private var countdownActiveWheelIdx: Int = -1
private var countdownWheelStartY: Float = 0f
private var countdownWheelAccumDy: Float = 0f
private var countdownWheelDidDrag: Boolean = false
private val countdownWheelRects = Array(4) { RectF() }
private val COUNTDOWN_WHEEL_MAX = intArrayOf(12, 29, 23, 59)
private val COUNTDOWN_WHEEL_LABELS = arrayOf("MONTHS", "DAYS", "HOURS", "MINS")
private var countdownModalSlotIndex: Int = 1
private var countdownModalUsePerWidget: Boolean = false
private var countdownModalSource: String = LightspeedCalendarCountdownEngine.SOURCE_MANUAL
private val countdownModalCalendarIds = linkedSetOf<String>()
private var countdownCalScrollOffset: Float = 0f
private var countdownCalMaxScroll: Float = 0f
private var countdownCalDragging: Boolean = false
private var countdownCalDragLastY: Float = 0f
private var countdownCalDidDrag: Boolean = false
private val countdownSourceManualRect = RectF()
private val countdownSourceCalRect = RectF()
private val countdownScopeToggleRect = RectF()
private val countdownCalGrantBtnRect = RectF()
private val countdownCalListRect = RectF()
private val countdownCalItemRects = linkedMapOf<String, RectF>()
private var countdownModalInfoOpen: Boolean = false
private val countdownInfoBtnRect = RectF()
private val countdownInfoCardRect = RectF()

private fun wrapWheelValue(v: Int, maxInclusive: Int): Int {
    val span = maxInclusive + 1
    return ((v % span) + span) % span
}

private fun decomposeCountdownTarget(targetMs: Long, nowMs: Long): IntArray {
    val diffMs = (targetMs - nowMs).coerceAtLeast(60_000L)
    val totalMins = (diffMs / 60_000L).toInt()
    val totalDays = totalMins / (24 * 60)
    val remAfterDays = totalMins % (24 * 60)
    val months = (totalDays / 30).coerceIn(0, 12)
    val days = (totalDays % 30).coerceIn(0, 29)
    val hours = (remAfterDays / 60).coerceIn(0, 23)
    val mins = (remAfterDays % 60).coerceIn(0, 59)
    return intArrayOf(months, days, hours, mins)
}

private fun composeCountdownTarget(nowMs: Long, months: Int, days: Int, hours: Int, mins: Int): Long {
    val totalDays = (months.coerceIn(0, 12) * 30L) + days.coerceIn(0, 29).toLong()
    val totalMs = (totalDays * 86_400_000L) +
        (hours.coerceIn(0, 23) * 3_600_000L) +
        (mins.coerceIn(0, 59) * 60_000L)
    return nowMs + totalMs.coerceAtLeast(60_000L)
}

internal fun LightspeedCruiseOverlay.openInPlaceCountdownModal(catId: String, slotIndex: Int = 1) {
    val cfg = LightspeedCalendarCountdownEngine.loadConfig(context, catId, slotIndex)
    val now = System.currentTimeMillis()
    countdownModalCatId = catId
    countdownModalSlotIndex = slotIndex
    countdownModalUsePerWidget = cfg.usePerWidget
    countdownModalSource = cfg.source
    countdownModalTargetMs = if (cfg.targetMs > now) cfg.targetMs else (now + 7L * 86_400_000L)
    countdownModalTitle = cfg.title
    countdownModalCalendarIds.clear()
    countdownModalCalendarIds.addAll(cfg.calendarIds)
    countdownActiveWheelIdx = -1
    countdownWheelAccumDy = 0f
    countdownWheelDidDrag = false
    countdownCalScrollOffset = 0f
    countdownCalDragging = false
    countdownCalDidDrag = false
    countdownModalInfoOpen = false
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
    val modalW = (324f * dp).coerceAtMost(w - 24f * dp)
    val modalH = (394f * dp).coerceAtMost(h - 24f * dp)
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

    // Close button [ ✕ ] and compact [ ! ] progress-math info badge
    val closeRect = RectF(bounds.right - 30f * dp, bounds.top + 8f * dp, bounds.right - 8f * dp, bounds.top + 30f * dp)
    countdownInfoBtnRect.set(closeRect.left - 26f * dp, closeRect.top, closeRect.left - 4f * dp, closeRect.bottom)
    paint.style = Paint.Style.FILL
    paint.color = if (countdownModalInfoOpen) Color.argb(180, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(countdownInfoBtnRect, 5f * dp, 5f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.9f * dp
    paint.color = if (countdownModalInfoOpen) accent else Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(countdownInfoBtnRect, 5f * dp, 5f * dp, paint)
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 11f * dp
    paint.color = if (countdownModalInfoOpen) Color.BLACK else Color.WHITE
    canvas.drawText("!", countdownInfoBtnRect.centerX(), countdownInfoBtnRect.centerY() + 4f * dp, paint)

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(35, 255, 255, 255)
    canvas.drawRoundRect(closeRect, 5f * dp, 5f * dp, paint)
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 11f * dp
    paint.color = Color.WHITE
    canvas.drawText("✕", closeRect.centerX(), closeRect.centerY() + 4f * dp, paint)

    // Mode & Scope Row: [ MANUAL | CALENDAR ]   [ ALL WIDGETS / THIS WIDGET ]
    val modeRowTop = bounds.top + 33f * dp
    val modeRowH = 20f * dp
    val contentW = bounds.width() - 28f * dp
    val srcPillW = contentW * 0.27f
    val scopeW = contentW - srcPillW * 2f - 10f * dp
    countdownSourceManualRect.set(bounds.left + 14f * dp, modeRowTop, bounds.left + 14f * dp + srcPillW, modeRowTop + modeRowH)
    countdownSourceCalRect.set(countdownSourceManualRect.right + 4f * dp, modeRowTop, countdownSourceManualRect.right + 4f * dp + srcPillW, modeRowTop + modeRowH)
    countdownScopeToggleRect.set(bounds.right - 14f * dp - scopeW, modeRowTop, bounds.right - 14f * dp, modeRowTop + modeRowH)

    fun drawTopPill(rect: RectF, label: String, active: Boolean) {
        paint.style = Paint.Style.FILL
        paint.color = if (active) Color.argb(175, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(32, 255, 255, 255)
        canvas.drawRoundRect(rect, 5f * dp, 5f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = if (active) accent else Color.argb(70, 255, 255, 255)
        canvas.drawRoundRect(rect, 5f * dp, 5f * dp, paint)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 7.4f * dp
        paint.color = if (active) Color.BLACK else Color.WHITE
        val pFm = paint.fontMetrics
        canvas.drawText(label, rect.centerX(), rect.centerY() - ((pFm.descent + pFm.ascent) / 2f), paint)
    }

    val isCalMode = (countdownModalSource == LightspeedCalendarCountdownEngine.SOURCE_CALENDAR)
    drawTopPill(countdownSourceManualRect, "MANUAL", !isCalMode)
    drawTopPill(countdownSourceCalRect, "CALENDAR", isCalMode)
    drawTopPill(countdownScopeToggleRect, if (countdownModalUsePerWidget) "THIS WIDGET" else "ALL WIDGETS", countdownModalUsePerWidget)

    val now = System.currentTimeMillis()
    val previewTop = modeRowTop + modeRowH + 7f * dp
    val previewRect = RectF(bounds.left + 14f * dp, previewTop, bounds.right - 14f * dp, previewTop + 52f * dp)
    val actionY = bounds.bottom - 34f * dp
    val actionH = 25f * dp
    val actionW = (bounds.width() - 28f * dp - 10f * dp) / 2f

    if (isCalMode) {
        countdownCalItemRects.clear()
        countdownCalGrantBtnRect.setEmpty()
        countdownCalListRect.setEmpty()
        val hasPerm = LightspeedCalendarCountdownEngine.hasCalendarPermission(context)
        if (!hasPerm) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent))
            val cardRect = RectF(bounds.left + 14f * dp, previewTop + 14f * dp, bounds.right - 14f * dp, actionY - 20f * dp)
            canvas.drawRoundRect(cardRect, 10f * dp, 10f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.9f * dp
            paint.color = Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(cardRect, 10f * dp, 10f * dp, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 10.5f * dp
            paint.color = accent
            canvas.drawText("CALENDAR ACCESS (100% OFFLINE)", cardRect.centerX(), cardRect.top + 34f * dp, paint)

            paint.typeface = Typeface.DEFAULT
            paint.textSize = 8.6f * dp
            paint.color = Color.argb(225, 220, 232, 248)
            canvas.drawText("Reads local on-device calendars to count down", cardRect.centerX(), cardRect.top + 56f * dp, paint)
            canvas.drawText("to your next event. Choose Work, Personal, or All.", cardRect.centerX(), cardRect.top + 70f * dp, paint)

            val btnW = (cardRect.width() - 36f * dp).coerceAtLeast(140f * dp)
            val btnH = 28f * dp
            countdownCalGrantBtnRect.set(cardRect.centerX() - btnW * 0.5f, cardRect.bottom - btnH - 22f * dp, cardRect.centerX() + btnW * 0.5f, cardRect.bottom - 22f * dp)
            paint.color = accent
            canvas.drawRoundRect(countdownCalGrantBtnRect, 7f * dp, 7f * dp, paint)
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 9.0f * dp
            paint.color = Color.BLACK
            val gFm = paint.fontMetrics
            canvas.drawText("GRANT CALENDAR ACCESS", countdownCalGrantBtnRect.centerX(), countdownCalGrantBtnRect.centerY() - ((gFm.descent + gFm.ascent) / 2f), paint)
        } else {
            // Live Next Calendar Event Preview Card
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 0.8f * dp
            paint.color = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent))
            canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)

            val nextEv = LightspeedCalendarCountdownEngine.getNextUpcomingEvent(context, countdownModalCalendarIds)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT
            if (nextEv != null) {
                val evDiff = (nextEv.startMs - now).coerceAtLeast(0L)
                val evD = evDiff / 86_400_000L
                val evH = (evDiff / 3_600_000L) % 24
                val evM = (evDiff / 60_000L) % 60
                val evDate = SimpleDateFormat("EEE, MMM d · HH:mm", Locale.US).format(Date(nextEv.startMs))
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = 9.0f * dp
                paint.color = accent
                canvas.drawText(clipTextToWidth("NEXT: ${nextEv.title.uppercase()}", previewRect.width() - 20f * dp), previewRect.left + 10f * dp, previewRect.top + 16f * dp, paint)
                paint.textSize = 13.0f * dp
                paint.color = Color.WHITE
                canvas.drawText("${evD}d ${evH}h ${evM}m REMAINING", previewRect.left + 10f * dp, previewRect.top + 33f * dp, paint)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 8.0f * dp
                paint.color = Color.argb(200, 200, 220, 245)
                canvas.drawText(clipTextToWidth("$evDate · ${nextEv.calendarName}", previewRect.width() - 20f * dp), previewRect.left + 10f * dp, previewRect.top + 46f * dp, paint)
            } else {
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = 10.0f * dp
                paint.color = Color.WHITE
                canvas.drawText("NO UPCOMING EVENTS", previewRect.left + 10f * dp, previewRect.top + 24f * dp, paint)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 8.0f * dp
                paint.color = Color.argb(185, 200, 220, 245)
                canvas.drawText("No future events found in the selected calendars.", previewRect.left + 10f * dp, previewRect.top + 40f * dp, paint)
            }

            // Scrollable Calendar Picker
            val calCaptionY = previewRect.bottom + 12f * dp
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 7.4f * dp
            paint.color = Color.argb(165, 200, 220, 245)
            canvas.drawText("CHOOSE CALENDARS (WORK, PERSONAL, OR ALL)", bounds.left + 14f * dp, calCaptionY, paint)

            val listTop = calCaptionY + 5f * dp
            val listBot = actionY - 8f * dp
            countdownCalListRect.set(bounds.left + 14f * dp, listTop, bounds.right - 14f * dp, listBot)
            val calendars = LightspeedCalendarCountdownEngine.getDeviceCalendars(context)
            val rowH = 28f * dp
            val totalRows = 1 + calendars.size
            countdownCalMaxScroll = kotlin.math.max(0f, totalRows * rowH - countdownCalListRect.height())
            countdownCalScrollOffset = countdownCalScrollOffset.coerceIn(0f, countdownCalMaxScroll)

            val calLayerCount = canvas.saveLayer(countdownCalListRect, null)
            canvas.clipRect(countdownCalListRect)
            var cy = listTop - countdownCalScrollOffset

            fun drawCalRow(key: String, title: String, sub: String, dotColor: Int, selected: Boolean) {
                val r = RectF(countdownCalListRect.left, cy, countdownCalListRect.right, cy + rowH - 3f * dp)
                if (r.bottom >= listTop && r.top <= listBot) {
                    countdownCalItemRects[key] = RectF(r)
                    paint.style = Paint.Style.FILL
                    paint.color = if (selected) Color.argb(56, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(26, 255, 255, 255)
                    canvas.drawRoundRect(r, 6f * dp, 6f * dp, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 0.8f * dp
                    paint.color = if (selected) accent else Color.argb(55, 255, 255, 255)
                    canvas.drawRoundRect(r, 6f * dp, 6f * dp, paint)

                    paint.style = Paint.Style.FILL
                    paint.color = dotColor
                    canvas.drawCircle(r.left + 11f * dp, r.centerY(), 4f * dp, paint)

                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.DEFAULT_BOLD
                    paint.textSize = 9.0f * dp
                    paint.color = if (selected) accent else Color.argb(95, 200, 220, 245)
                    val cFm = paint.fontMetrics
                    canvas.drawText(if (selected) "✓" else "○", r.right - 9f * dp, r.centerY() - ((cFm.descent + cFm.ascent) / 2f), paint)

                    val maxTxtW = (r.width() - 42f * dp).coerceAtLeast(40f * dp)
                    paint.textAlign = Paint.Align.LEFT
                    paint.typeface = Typeface.DEFAULT_BOLD
                    paint.textSize = 8.6f * dp
                    paint.color = Color.WHITE
                    if (sub.isNotBlank()) {
                        canvas.drawText(clipTextToWidth(title, maxTxtW), r.left + 21f * dp, r.top + 11.5f * dp, paint)
                        paint.typeface = Typeface.DEFAULT
                        paint.textSize = 7.0f * dp
                        paint.color = Color.argb(175, 200, 220, 245)
                        canvas.drawText(clipTextToWidth(sub, maxTxtW), r.left + 21f * dp, r.top + 21f * dp, paint)
                    } else {
                        val tFm = paint.fontMetrics
                        canvas.drawText(clipTextToWidth(title, maxTxtW), r.left + 21f * dp, r.centerY() - ((tFm.descent + tFm.ascent) / 2f), paint)
                    }
                }
                cy += rowH
            }

            drawCalRow("__ALL__", "ALL CALENDARS", "Follow next upcoming event from any calendar", accent, countdownModalCalendarIds.isEmpty())
            for (c in calendars) {
                drawCalRow(c.id.toString(), c.displayName, c.accountName, c.color, c.id.toString() in countdownModalCalendarIds)
            }
            LightspeedHudVectorIcons.applyVerticalEdgeFeathering(
                canvas = canvas,
                left = countdownCalListRect.left,
                top = countdownCalListRect.top,
                right = countdownCalListRect.right,
                bottom = countdownCalListRect.bottom,
                featherTopPx = if (countdownCalScrollOffset > 0.5f) 14f * dp else 0f,
                featherBottomPx = if (countdownCalScrollOffset < countdownCalMaxScroll - 0.5f) 16f * dp else 0f,
                density = dp,
                seed = 7373
            )
            canvas.restoreToCount(calLayerCount)

            if (countdownCalMaxScroll > 0f) {
                val sbW = 2.5f * dp
                val sbX = countdownCalListRect.right + 3f * dp
                val listH = countdownCalListRect.height()
                val sbRatio = (listH / (totalRows * rowH)).coerceIn(0.18f, 0.78f)
                val thumbH = listH * sbRatio
                val thumbY = listTop + ((countdownCalScrollOffset / countdownCalMaxScroll) * (listH - thumbH))
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(55, 255, 255, 255)
                canvas.drawRoundRect(RectF(sbX, listTop, sbX + sbW, listBot), 1.5f * dp, 1.5f * dp, paint)
                paint.color = accent
                canvas.drawRoundRect(RectF(sbX, thumbY, sbX + sbW, thumbY + thumbH), 1.5f * dp, 1.5f * dp, paint)
            }
        }

        // Action Row for Calendar mode
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
        canvas.drawText("CLEAR", resetRect.centerX(), resetRect.centerY() + 3.2f * dp, paint)

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
        canvas.drawText("ACTIVATE CALENDAR", saveRect.centerX(), saveRect.centerY() + 3.2f * dp, paint)
        if (countdownModalInfoOpen) drawCountdownProgressExplainerCard(canvas, paint, bounds, accent, dp, true) else countdownInfoCardRect.setEmpty()
        return
    }

    // 1. Preview Card (Manual Mode)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(40, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.8f * dp
    paint.color = Color.argb(80, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(previewRect, 8f * dp, 8f * dp, paint)

    val diffMs = (countdownModalTargetMs - now).coerceAtLeast(0L)
    val diffDays = diffMs / 86_400_000L
    val diffHours = (diffMs / 3_600_000L) % 24
    val diffMins = (diffMs / 60_000L) % 60
    val dateStr = SimpleDateFormat("EEE, MMM d, yyyy · HH:mm", Locale.US).format(Date(countdownModalTargetMs))

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
    canvas.drawText("${diffDays}d ${diffHours}h ${diffMins}m REMAINING", previewRect.left + 10f * dp, previewRect.top + 33f * dp, paint)

    paint.typeface = Typeface.DEFAULT
    paint.textSize = 8.0f * dp
    paint.color = Color.argb(200, 200, 220, 245)
    canvas.drawText("TARGET DATE: $dateStr", previewRect.left + 10f * dp, previewRect.top + 46f * dp, paint)

    // 2. Event Name Presets Section
    val eventCaptionY = previewRect.bottom + 10f * dp
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

    // 3. Alarm-Clock Scroll Wheels (MONTHS | DAYS | HOURS | MINS)
    val parts = decomposeCountdownTarget(countdownModalTargetMs, now)
    val wheelsCaptionY = eventRow2Y + eventPillH + 10f * dp
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 7.4f * dp
    paint.color = Color.argb(165, 200, 220, 245)
    canvas.drawText("SCROLL WHEELS TO SET COUNTDOWN", bounds.left + 14f * dp, wheelsCaptionY, paint)

    val wheelsTop = wheelsCaptionY + 6f * dp
    val wheelsBot = (actionY - 10f * dp).coerceAtLeast(wheelsTop + 86f * dp)
    val wheelGap = 6f * dp
    val wheelW = (bounds.width() - 28f * dp - wheelGap * 3f) / 4f

    for (i in 0..3) {
        val wLeft = bounds.left + 14f * dp + i * (wheelW + wheelGap)
        val wRect = RectF(wLeft, wheelsTop, wLeft + wheelW, wheelsBot)
        countdownWheelRects[i].set(wRect)
        val isActive = (countdownActiveWheelIdx == i)

        // Drum body
        paint.style = Paint.Style.FILL
        paint.color = if (isActive) Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(26, 255, 255, 255)
        canvas.drawRoundRect(wRect, 8f * dp, 8f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = if (isActive) 1.2f * dp else 0.8f * dp
        paint.color = if (isActive) accent else Color.argb(72, 255, 255, 255)
        canvas.drawRoundRect(wRect, 8f * dp, 8f * dp, paint)

        // Column caption
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 7.2f * dp
        paint.color = if (isActive) accent else Color.argb(180, 200, 220, 245)
        canvas.drawText(COUNTDOWN_WHEEL_LABELS[i], wRect.centerX(), wRect.top + 11f * dp, paint)

        // Roller drum viewport
        val drumTop = wRect.top + 15f * dp
        val drumBot = wRect.bottom - 4f * dp
        val drumCy = (drumTop + drumBot) * 0.5f
        val rowStep = ((drumBot - drumTop) / 3f).coerceAtLeast(20f * dp)

        // Center selection band (classic alarm-clock paper window)
        val bandTop = drumCy - rowStep * 0.48f
        val bandBot = drumCy + rowStep * 0.48f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(38, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawRoundRect(RectF(wRect.left + 3f * dp, bandTop, wRect.right - 3f * dp, bandBot), 5f * dp, 5f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = Color.argb(135, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawLine(wRect.left + 4f * dp, bandTop, wRect.right - 4f * dp, bandTop, paint)
        canvas.drawLine(wRect.left + 4f * dp, bandBot, wRect.right - 4f * dp, bandBot, paint)

        val maxVal = COUNTDOWN_WHEEL_MAX[i]
        val curVal = parts[i]
        val dragVisualOffset = if (isActive) (countdownWheelAccumDy * 0.55f).coerceIn(-rowStep * 0.45f, rowStep * 0.45f) else 0f

        canvas.save()
        canvas.clipRect(wRect.left + 2f * dp, drumTop, wRect.right - 2f * dp, drumBot)
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        for (delta in -1..1) {
            val shown = wrapWheelValue(curVal + delta, maxVal)
            val itemCy = drumCy + delta * rowStep + dragVisualOffset
            val isCenter = (delta == 0)
            paint.typeface = if (isCenter) Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) else Typeface.MONOSPACE
            paint.textSize = if (isCenter) 16.5f * dp else 11.5f * dp
            paint.color = if (isCenter) Color.WHITE else Color.argb(105, 200, 220, 245)
            val fm = paint.fontMetrics
            canvas.drawText(String.format(Locale.US, "%02d", shown), wRect.centerX(), itemCy - ((fm.descent + fm.ascent) / 2f), paint)
        }
        canvas.restore()
    }

    // 4. Action Row: [ CLEAR ]  [ ACTIVATE TARGET ]
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
    canvas.drawText("CLEAR", resetRect.centerX(), resetRect.centerY() + 3.2f * dp, paint)

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

    if (countdownModalInfoOpen) {
        drawCountdownProgressExplainerCard(canvas, paint, bounds, accent, dp, isCalMode)
    } else {
        countdownInfoCardRect.setEmpty()
    }
}

private fun drawCountdownProgressExplainerCard(
    canvas: Canvas,
    paint: Paint,
    bounds: RectF,
    accent: Int,
    dp: Float,
    isCalMode: Boolean
) {
    val cardTop = bounds.top + 60f * dp
    val cardBot = bounds.bottom - 42f * dp
    countdownInfoCardRect.set(bounds.left + 12f * dp, cardTop, bounds.right - 12f * dp, cardBot)
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(246, 12, 16, 26)
    canvas.drawRoundRect(countdownInfoCardRect, 10f * dp, 10f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.1f * dp
    paint.color = Color.argb(165, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawRoundRect(countdownInfoCardRect, 10f * dp, 10f * dp, paint)

    val padX = 12f * dp
    val left = countdownInfoCardRect.left + padX
    val maxW = (countdownInfoCardRect.width() - padX * 2f).coerceAtLeast(80f * dp)
    var y = countdownInfoCardRect.top + 20f * dp

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 9.5f * dp
    paint.color = accent
    canvas.drawText(
        if (isCalMode) "CALENDAR PROGRESS BAR MATH" else "MANUAL PROGRESS BAR MATH",
        left, y, paint
    )
    y += 16f * dp

    val paragraphs = if (isCalMode) {
        listOf(
            "• Window Start: Uses your previous calendar event's end time if it finished within the last 24 hours.",
            "• Fallback Lead-In: If no recent event ended within 24h, starts from midnight (start of day) or 24h before the event.",
            "• Fill Formula: (now - windowStart) / (eventStart - windowStart), filling smoothly up to 100% as the event arrives.",
            "• 100% Offline: Reads only local Android CalendarContract instances with zero network access."
        )
    } else {
        listOf(
            "• Start Timestamp: Saved automatically (startMs) the moment you tap ACTIVATE TARGET.",
            "• Fill Formula: (now - startMs) / (targetMs - startMs), advancing smoothly from 0% to 100% toward your target.",
            "• Config Vault: Saved for both ALL WIDGETS (unified) and THIS WIDGET (per-widget) scopes.",
            "• Tip: Tap ACTIVATE TARGET again anytime to reset the start anchor."
        )
    }

    paint.typeface = Typeface.DEFAULT
    paint.textSize = 8.4f * dp
    paint.color = Color.argb(230, 232, 240, 250)
    val lineH = 12.2f * dp
    for (p in paragraphs) {
        var line = ""
        for (word in p.split(" ")) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxW && line.isNotEmpty()) {
                canvas.drawText(line, left, y, paint)
                y += lineH
                line = "  $word"
            } else {
                line = candidate
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line, left, y, paint)
            y += lineH + 4f * dp
        }
        if (y > countdownInfoCardRect.bottom - 22f * dp) break
    }

    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = 7.6f * dp
    paint.color = Color.argb(175, Color.red(accent), Color.green(accent), Color.blue(accent))
    canvas.drawText("Tap anywhere on this card or ! to close", countdownInfoCardRect.centerX(), countdownInfoCardRect.bottom - 10f * dp, paint)
}

internal fun LightspeedCruiseOverlay.handleCountdownModalTouch(event: MotionEvent): Boolean {
    if (!isCountdownModalOpen) return false
    val dp = resources.displayMetrics.density
    val w = width.toFloat()
    val h = height.toFloat()

    val modalW = (324f * dp).coerceAtMost(w - 24f * dp)
    val modalH = (394f * dp).coerceAtMost(h - 24f * dp)
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
