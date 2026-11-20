package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.sbf.lightspeed.settings.SliderPresetManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

// --- Inline 12-Theme Canvas Slider Calibration Popup (mirrors Central Command SliderCalibrationFlyoutDialog) ---
private var tuneCalibSliderId: String? = null
private var tuneCalibKeypadOpen: Boolean = false
private var tuneCalibTypedBuffer: String = ""
private var tuneCalibReplaceOnNextDigit: Boolean = true
private var tuneCalibProfilesExpanded: Boolean = true

private val tuneCalibClipPath = Path()
private val tuneCalibModalRect = RectF()
private val tuneCalibCloseBtnRect = RectF()
private val tuneCalibTypeCardRect = RectF()
private val tuneCalibApplyBtnRect = RectF()
private val tuneCalibTile2Rect = RectF()
private val tuneCalibTile3Rect = RectF()
private val tuneCalibPadBoxRect = RectF()
private val tuneCalibSwitchRect = RectF()
private val tuneCalibProfBoxRect = RectF()
private val tuneCalibFineMinusRect = RectF()
private val tuneCalibFinePlusRect = RectF()
private val tuneCalibTapToJumpRect = RectF()
private val tuneCalibResetBtnRect = RectF()
private val tuneCalibProfilesHeaderRect = RectF()
private val tuneCalibAddProfileRect = RectF()
private val tuneCalibKeypadBtnRects = mutableListOf<Pair<String, RectF>>()
private data class TuneProfileRowHit(
    val preset: SliderPresetManager.PresetItem,
    val rowRect: RectF,
    val deleteRect: RectF
)
private val tuneCalibProfileRows = mutableListOf<TuneProfileRowHit>()

internal fun isTuneCalibrationModalOpen(): Boolean = tuneCalibSliderId != null

internal fun LightspeedCruiseOverlay.openTuneCalibrationModal(sliderId: String) {
    tuneCalibSliderId = sliderId
    tuneCalibKeypadOpen = false
    val item = hudTuneTargetItem
    val spec = item?.let { buildTuneSliderSpecs(it).firstOrNull { s -> s.id == sliderId } }
    tuneCalibTypedBuffer = if (item != null && spec != null) spec.getSliderPos(item).toString() else ""
    tuneCalibReplaceOnNextDigit = true
    isTuneFontDropdownOpen = false
    tuneFontDropdownTracking = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeTuneCalibrationModal() {
    if (tuneCalibSliderId != null) {
        tuneCalibSliderId = null
        tuneCalibKeypadOpen = false
        tuneCalibKeypadBtnRects.clear()
        tuneCalibProfileRows.clear()
        invalidate()
    }
}

internal fun LightspeedCruiseOverlay.drawTuneCalibrationModal(
    canvas: Canvas,
    dp: Float,
    anchorRect: RectF,
    item: CategoryHudItem,
    colors: TuneThemeColors
) {
    val targetId = tuneCalibSliderId ?: return
    val spec = buildTuneSliderSpecs(item).firstOrNull { it.id == targetId } ?: run {
        tuneCalibSliderId = null
        return
    }

    val isLandscape = width > height
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val prefs = context.defaultPrefs()
    val presets = SliderPresetManager.getPresets(prefs, spec.id)
    val isTapToJump = prefs.getBoolean(LightspeedPreferences.keySliderTapToJump(spec.id), false)

    // Dim only inside the underlying Tune card so the live widget stays crystal clear
    canvas.save()
    tuneCalibClipPath.reset()
    tuneCalibClipPath.addRoundRect(anchorRect, colors.cornerRadiusPx, colors.cornerRadiusPx, Path.Direction.CW)
    canvas.clipPath(tuneCalibClipPath)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(140, 0, 0, 0)
    canvas.drawRect(anchorRect, highlightPaint)
    canvas.restore()

    val accR = Color.red(colors.accentColor)
    val accG = Color.green(colors.accentColor)
    val accB = Color.blue(colors.accentColor)
    val mutR = Color.red(colors.textMuted)
    val mutG = Color.green(colors.textMuted)
    val mutB = Color.blue(colors.textMuted)
    val mutedCardBg = Color.argb(38, mutR, mutG, mutB)
    val mutedCardBorder = Color.argb(72, mutR, mutG, mutB)

    val padX = 13f * dp
    val headerH = 40f * dp
    val cardsH = 48f * dp
    val keypadRowH = 25f * dp
    val keypadH = if (tuneCalibKeypadOpen) {
        (keypadRowH * 2f + 5f * dp + (if (spec.supportsAuto) keypadRowH + 5f * dp else 0f) + 8f * dp)
    } else 0f
    val tapCardH = 38f * dp
    val resetBtnH = 32f * dp
    val maxAvailH = (screenH - getSafeHudTopOffset() - 18f * dp).coerceAtLeast(240f * dp)
    val baseNoProfilesH = 11f * dp + headerH + 6f * dp + cardsH + (if (tuneCalibKeypadOpen) keypadH + 6f * dp else 0f) +
        8f * dp + tapCardH + 7f * dp + resetBtnH + 9f * dp + 34f * dp + 10f * dp
    val addRowH = 26f * dp
    val profRowH = 25f * dp
    val maxVisibleProfiles = if (tuneCalibProfilesExpanded && presets.isNotEmpty()) {
        val roomForRows = (maxAvailH - baseNoProfilesH - addRowH - 12f * dp).coerceAtLeast(0f)
        (roomForRows / (profRowH + 3f * dp)).toInt().coerceIn(1, 4).coerceAtMost(presets.size)
    } else 0
    val profilesBoxH = if (tuneCalibProfilesExpanded) {
        val rowsBlock = if (presets.isEmpty()) 22f * dp else (maxVisibleProfiles * (profRowH + 3f * dp))
        34f * dp + addRowH + 6f * dp + rowsBlock + 4f * dp
    } else 34f * dp

    val modalW = (anchorRect.width() - 8f * dp).coerceIn(264f * dp, 344f * dp)
    val modalH = (11f * dp + headerH + 6f * dp + cardsH + (if (tuneCalibKeypadOpen) keypadH + 6f * dp else 0f) +
        8f * dp + tapCardH + 7f * dp + resetBtnH + 9f * dp + profilesBoxH + 10f * dp).coerceAtMost(maxAvailH)

    val minTop = getSafeHudTopOffset() + 8f * dp
    val maxTop = (screenH - modalH - 8f * dp).coerceAtLeast(minTop)
    val left = (anchorRect.centerX() - modalW / 2f).coerceIn(8f * dp, screenW - modalW - 8f * dp)
    val top = (anchorRect.centerY() - modalH / 2f).coerceIn(minTop, maxTop)
    tuneCalibModalRect.set(left, top, left + modalW, top + modalH)

    // 12-theme optical glass surface (same engine as pinned app menu & Central Command flyouts)
    drawDeckThemedFlyoutSurface(canvas, tuneCalibModalRect, colors.snapshot, colors.cornerRadiusPx, dp, highlightPaint)

    val contentLeft = left + padX
    val contentRight = left + modalW - padX
    val contentW = contentRight - contentLeft

    // 1. Header (Circular Tune Icon Badge + Title / Subtitle + Close X)
    val badgeSize = 26f * dp
    val badgeCx = contentLeft + badgeSize / 2f
    val badgeCy = top + 11f * dp + badgeSize / 2f
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, highlightPaint)
    // Geometric Tune sliders glyph inside badge
    highlightPaint.strokeWidth = 1.4f * dp
    highlightPaint.color = colors.accentColor
    canvas.drawLine(badgeCx - 6f * dp, badgeCy - 3f * dp, badgeCx + 6f * dp, badgeCy - 3f * dp, highlightPaint)
    canvas.drawLine(badgeCx - 6f * dp, badgeCy + 3f * dp, badgeCx + 6f * dp, badgeCy + 3f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.FILL
    canvas.drawCircle(badgeCx + 2.2f * dp, badgeCy - 3f * dp, 2.0f * dp, highlightPaint)
    canvas.drawCircle(badgeCx - 2.2f * dp, badgeCy + 3f * dp, 2.0f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 10.6f * dp
    textPaint.color = colors.textPrimary
    val titleFm = textPaint.fontMetrics
    canvas.drawText(spec.label, contentLeft + badgeSize + 8f * dp, top + 19f * dp - (titleFm.descent + titleFm.ascent) / 2f, textPaint)

    textPaint.typeface = overlayTypeface(isBold = false)
    textPaint.textSize = 8.1f * dp
    textPaint.color = colors.textMuted
    val subFm = textPaint.fontMetrics
    canvas.drawText("Precision Control & Presets", contentLeft + badgeSize + 8f * dp, top + 31f * dp - (subFm.descent + subFm.ascent) / 2f, textPaint)

    val closeSize = 22f * dp
    tuneCalibCloseBtnRect.set(contentRight - closeSize, top + 12f * dp, contentRight, top + 12f * dp + closeSize)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawRoundRect(tuneCalibCloseBtnRect, 6f * dp, 6f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(tuneCalibCloseBtnRect, 6f * dp, 6f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 10f * dp
    textPaint.color = colors.textPrimary
    val cFm = textPaint.fontMetrics
    canvas.drawText("✕", tuneCalibCloseBtnRect.centerX(), tuneCalibCloseBtnRect.centerY() - (cFm.descent + cFm.ascent) / 2f, textPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawLine(contentLeft, top + 45f * dp, contentRight, top + 45f * dp, highlightPaint)

    // 2. Three Readout & Control Cards Row (Type Value | Factory Default | Fine Gearing +-1)
    var curY = top + 51f * dp
    val cardGap = 6f * dp
    val cardW = (contentW - cardGap * 2f) / 3f

    // Tile 1: Type Value
    tuneCalibTypeCardRect.set(contentLeft, curY, contentLeft + cardW, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (tuneCalibKeypadOpen) Color.argb(68, accR, accG, accB) else colors.pillBg
    canvas.drawRoundRect(tuneCalibTypeCardRect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = if (tuneCalibKeypadOpen) 1.3f * dp else 0.9f * dp
    highlightPaint.color = colors.accentColor
    canvas.drawRoundRect(tuneCalibTypeCardRect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.accentColor
    val lblFm = textPaint.fontMetrics
    canvas.drawText("Type Value", tuneCalibTypeCardRect.left + 7f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    // Apply check button inside Type Value tile
    val checkSize = 18f * dp
    tuneCalibApplyBtnRect.set(
        tuneCalibTypeCardRect.right - checkSize - 5f * dp,
        curY + cardsH - checkSize - 5f * dp,
        tuneCalibTypeCardRect.right - 5f * dp,
        curY + cardsH - 5f * dp
    )
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (tuneCalibKeypadOpen) colors.accentColor else Color.argb(45, accR, accG, accB)
    canvas.drawRoundRect(tuneCalibApplyBtnRect, 4.5f * dp, 4.5f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 9.5f * dp
    textPaint.color = if (tuneCalibKeypadOpen) colors.onAccentColor else colors.accentColor
    val kFm = textPaint.fontMetrics
    canvas.drawText(if (tuneCalibKeypadOpen) "✓" else "✎", tuneCalibApplyBtnRect.centerX(), tuneCalibApplyBtnRect.centerY() - (kFm.descent + kFm.ascent) / 2f, textPaint)

    val typeDisp = if (tuneCalibKeypadOpen) {
        (tuneCalibTypedBuffer.ifEmpty { "0" }) + "_"
    } else {
        spec.getDisplayValue(item)
    }
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 11.4f * dp
    textPaint.color = colors.textPrimary
    val vFm = textPaint.fontMetrics
    canvas.drawText(typeDisp, tuneCalibTypeCardRect.left + 7f * dp, curY + 34f * dp - (vFm.descent + vFm.ascent) / 2f, textPaint)

    // Tile 2: Factory Default
    tuneCalibTile2Rect.set(tuneCalibTypeCardRect.right + cardGap, curY, tuneCalibTypeCardRect.right + cardGap + cardW, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tuneCalibTile2Rect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = mutedCardBorder
    canvas.drawRoundRect(tuneCalibTile2Rect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.textMuted
    canvas.drawText("Factory Default", tuneCalibTile2Rect.left + 7f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    val defaultDisp = spec.defaultDisplay(isLandscape, item.type)
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 11.2f * dp
    textPaint.color = colors.textPrimary
    canvas.drawText(defaultDisp, tuneCalibTile2Rect.left + 7f * dp, curY + 34f * dp - (vFm.descent + vFm.ascent) / 2f, textPaint)

    // Tile 3: Fine Gearing (±1 Step with interactive - / + buttons)
    tuneCalibTile3Rect.set(tuneCalibTile2Rect.right + cardGap, curY, contentRight, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tuneCalibTile3Rect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(tuneCalibTile3Rect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.accentColor
    canvas.drawText("↕ Fine Gearing", tuneCalibTile3Rect.left + 6f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    val stepBtnW = 20f * dp
    val stepBtnH = 19f * dp
    val stepBtnY = curY + cardsH - stepBtnH - 5f * dp
    tuneCalibFineMinusRect.set(tuneCalibTile3Rect.left + 5f * dp, stepBtnY, tuneCalibTile3Rect.left + 5f * dp + stepBtnW, stepBtnY + stepBtnH)
    tuneCalibFinePlusRect.set(tuneCalibTile3Rect.right - 5f * dp - stepBtnW, stepBtnY, tuneCalibTile3Rect.right - 5f * dp, stepBtnY + stepBtnH)
    for ((sym, rect) in listOf("-" to tuneCalibFineMinusRect, "+" to tuneCalibFinePlusRect)) {
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(rect, 4.5f * dp, 4.5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = colors.pillBorder
        canvas.drawRoundRect(rect, 4.5f * dp, 4.5f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 10.5f * dp
        textPaint.color = colors.textPrimary
        val sFm = textPaint.fontMetrics
        canvas.drawText(sym, rect.centerX(), rect.centerY() - (sFm.descent + sFm.ascent) / 2f, textPaint)
    }
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.0f * dp
    textPaint.color = colors.textPrimary
    canvas.drawText("±1", tuneCalibTile3Rect.centerX(), stepBtnY + stepBtnH / 2f - (vFm.descent + vFm.ascent) / 2f, textPaint)

    curY += cardsH + 6f * dp

    // 2b. Inline Compact Numeric Keypad (opened by tapping Type Value tile)
    tuneCalibKeypadBtnRects.clear()
    if (tuneCalibKeypadOpen) {
        tuneCalibPadBoxRect.set(contentLeft, curY, contentRight, curY + keypadH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = mutedCardBg
        canvas.drawRoundRect(tuneCalibPadBoxRect, 8f * dp, 8f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = colors.dividerColor
        canvas.drawRoundRect(tuneCalibPadBoxRect, 8f * dp, 8f * dp, highlightPaint)

        val kpPad = 4f * dp
        val keysRow1 = listOf("1", "2", "3", "4", "5", "⌫")
        val keysRow2 = listOf("6", "7", "8", "9", "0", "✓")
        val keyW = (contentW - kpPad * 7f) / 6f
        val rows = listOf(keysRow1, keysRow2)
        var ky = curY + kpPad
        for (row in rows) {
            for ((idx, keyLabel) in row.withIndex()) {
                val kx = contentLeft + kpPad + idx * (keyW + kpPad)
                val rect = RectF(kx, ky, kx + keyW, ky + keypadRowH)
                tuneCalibKeypadBtnRects += keyLabel to rect
                val isCommit = keyLabel == "✓"
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (isCommit) colors.accentColor else colors.pillBg
                canvas.drawRoundRect(rect, 5f * dp, 5f * dp, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 0.8f * dp
                highlightPaint.color = if (isCommit) colors.accentColor else colors.pillBorder
                canvas.drawRoundRect(rect, 5f * dp, 5f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 10f * dp
                textPaint.color = if (isCommit) colors.onAccentColor else colors.textPrimary
                val pFm = textPaint.fontMetrics
                canvas.drawText(keyLabel, rect.centerX(), rect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)
            }
            ky += keypadRowH + kpPad
        }
        if (spec.supportsAuto) {
            val autoRect = RectF(contentLeft + kpPad, ky, contentRight - kpPad, ky + keypadRowH)
            tuneCalibKeypadBtnRects += "AUTO" to autoRect
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = colors.pillBg
            canvas.drawRoundRect(autoRect, 5f * dp, 5f * dp, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.85f * dp
            highlightPaint.color = colors.accentColor
            canvas.drawRoundRect(autoRect, 5f * dp, 5f * dp, highlightPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = overlayTypeface(isBold = true)
            textPaint.textSize = 9.0f * dp
            textPaint.color = colors.accentColor
            val aFm = textPaint.fontMetrics
            canvas.drawText("RESET TO AUTO", autoRect.centerX(), autoRect.centerY() - (aFm.descent + aFm.ascent) / 2f, textPaint)
        }
        curY += keypadH + 6f * dp
    }

    // 3. Tap-to-Jump on Track Switch Card
    curY += 2f * dp
    tuneCalibTapToJumpRect.set(contentLeft, curY, contentRight, curY + tapCardH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tuneCalibTapToJumpRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = mutedCardBorder
    canvas.drawRoundRect(tuneCalibTapToJumpRect, 9f * dp, 9f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.4f * dp
    textPaint.color = colors.textPrimary
    val swTitleFm = textPaint.fontMetrics
    canvas.drawText("Tap-to-Jump on Track", contentLeft + 10f * dp, curY + 14f * dp - (swTitleFm.descent + swTitleFm.ascent) / 2f, textPaint)

    textPaint.typeface = overlayTypeface(isBold = false)
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.textMuted
    val swSubFm = textPaint.fontMetrics
    canvas.drawText("Snap thumb to tap position on rail", contentLeft + 10f * dp, curY + 26.5f * dp - (swSubFm.descent + swSubFm.ascent) / 2f, textPaint)

    val swW = 34f * dp
    val swH = 19f * dp
    tuneCalibSwitchRect.set(contentRight - swW - 10f * dp, tuneCalibTapToJumpRect.centerY() - swH / 2f, contentRight - 10f * dp, tuneCalibTapToJumpRect.centerY() + swH / 2f)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (isTapToJump) colors.accentColor else Color.argb(65, mutR, mutG, mutB)
    canvas.drawRoundRect(tuneCalibSwitchRect, swH / 2f, swH / 2f, highlightPaint)
    val thumbR = (swH - 5f * dp) / 2f
    val thumbCx = if (isTapToJump) tuneCalibSwitchRect.right - swH / 2f else tuneCalibSwitchRect.left + swH / 2f
    highlightPaint.color = if (isTapToJump) colors.onAccentColor else colors.textPrimary
    canvas.drawCircle(thumbCx, tuneCalibSwitchRect.centerY(), thumbR, highlightPaint)

    curY += tapCardH + 7f * dp

    // 4. Reset to Default Button
    tuneCalibResetBtnRect.set(contentLeft, curY, contentRight, curY + resetBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, 175, 55, 65)
    canvas.drawRoundRect(tuneCalibResetBtnRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(125, 255, 120, 130)
    canvas.drawRoundRect(tuneCalibResetBtnRect, 9f * dp, 9f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.3f * dp
    textPaint.color = Color.argb(240, 255, 195, 200)
    val rstFm = textPaint.fontMetrics
    canvas.drawText("↻  Reset to Default ($defaultDisp)", tuneCalibResetBtnRect.centerX(), tuneCalibResetBtnRect.centerY() - (rstFm.descent + rstFm.ascent) / 2f, textPaint)

    curY += resetBtnH + 9f * dp

    // 5. Saved Profiles Collapsible Card (backed directly by SliderPresetManager)
    tuneCalibProfBoxRect.set(contentLeft, curY, contentRight, (tuneCalibModalRect.bottom - 10f * dp).coerceAtLeast(curY + 34f * dp))
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tuneCalibProfBoxRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawRoundRect(tuneCalibProfBoxRect, 9f * dp, 9f * dp, highlightPaint)

    tuneCalibProfilesHeaderRect.set(tuneCalibProfBoxRect.left, tuneCalibProfBoxRect.top, tuneCalibProfBoxRect.right, tuneCalibProfBoxRect.top + 32f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textSize = 9.4f * dp
    textPaint.color = colors.textPrimary
    val phFm = textPaint.fontMetrics
    canvas.drawText("Saved Profiles (${presets.size})", tuneCalibProfBoxRect.left + 10f * dp, tuneCalibProfilesHeaderRect.centerY() - (phFm.descent + phFm.ascent) / 2f, textPaint)

    textPaint.textAlign = Paint.Align.RIGHT
    textPaint.textSize = 10.5f * dp
    textPaint.color = colors.textMuted
    canvas.drawText(if (tuneCalibProfilesExpanded) "⌃" else "⌄", tuneCalibProfBoxRect.right - 10f * dp, tuneCalibProfilesHeaderRect.centerY() - (phFm.descent + phFm.ascent) / 2f, textPaint)

    tuneCalibAddProfileRect.setEmpty()
    tuneCalibProfileRows.clear()
    if (tuneCalibProfilesExpanded) {
        val currentDisp = spec.getDisplayValue(item)
        val rawSaveValue = if (spec.isAutoActive(item)) "AUTO" else spec.getSliderPos(item).toString()
        val alreadySaved = presets.any { it.valueStr.trim() == rawSaveValue || it.valueStr.trim() == currentDisp }

        val addY = tuneCalibProfilesHeaderRect.bottom + 2f * dp
        tuneCalibAddProfileRect.set(tuneCalibProfBoxRect.left + 8f * dp, addY, tuneCalibProfBoxRect.right - 8f * dp, addY + addRowH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (alreadySaved) Color.argb(28, mutR, mutG, mutB) else colors.pillBg
        canvas.drawRoundRect(tuneCalibAddProfileRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = if (alreadySaved) mutedCardBorder else colors.pillBorder
        canvas.drawRoundRect(tuneCalibAddProfileRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = overlayTypeface(isBold = true)
        textPaint.textSize = 8.4f * dp
        textPaint.color = if (alreadySaved) colors.textMuted else colors.accentColor
        val adFm = textPaint.fontMetrics
        val addText = if (alreadySaved) "✓  Current Value '$currentDisp' Saved" else "+  Add '$currentDisp' as Profile"
        canvas.drawText(addText, tuneCalibAddProfileRect.centerX(), tuneCalibAddProfileRect.centerY() - (adFm.descent + adFm.ascent) / 2f, textPaint)

        var rY = tuneCalibAddProfileRect.bottom + 5f * dp
        if (presets.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = overlayTypeface(isBold = false)
            textPaint.color = colors.textMuted
            canvas.drawText("No saved profiles yet.", tuneCalibProfBoxRect.centerX(), rY + 10f * dp, textPaint)
        } else {
            for (preset in presets.take(maxVisibleProfiles)) {
                if (rY + profRowH > tuneCalibProfBoxRect.bottom - 4f * dp) break
                val rowRect = RectF(tuneCalibProfBoxRect.left + 8f * dp, rY, tuneCalibProfBoxRect.right - 8f * dp, rY + profRowH)
                val delW = 24f * dp
                val delRect = RectF(rowRect.right - delW, rowRect.top, rowRect.right, rowRect.bottom)
                tuneCalibProfileRows += TuneProfileRowHit(preset, rowRect, delRect)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(32, mutR, mutG, mutB)
                canvas.drawRoundRect(rowRect, 6f * dp, 6f * dp, highlightPaint)

                val unitStr = if (preset.valueStr.equals("AUTO", ignoreCase = true) || preset.valueStr.endsWith(spec.unit)) {
                    preset.valueStr
                } else {
                    "${preset.valueStr}${spec.unit}"
                }
                val labelText = if (!preset.label.isNullOrBlank()) "$unitStr  •  ${preset.label}" else unitStr
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 8.8f * dp
                textPaint.color = colors.accentColor
                val pFm = textPaint.fontMetrics
                canvas.drawText(labelText, rowRect.left + 9f * dp, rowRect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 9.0f * dp
                textPaint.color = Color.argb(215, 255, 130, 140)
                canvas.drawText("✕", delRect.centerX(), delRect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)

                rY += profRowH + 3f * dp
            }
        }
    }
}

internal fun LightspeedCruiseOverlay.handleTuneCalibrationTouch(
    event: MotionEvent,
    catId: String,
    item: CategoryHudItem
): Boolean {
    val targetId = tuneCalibSliderId ?: return false
    val spec = buildTuneSliderSpecs(item).firstOrNull { it.id == targetId } ?: run {
        closeTuneCalibrationModal()
        return true
    }

    fun commitBuffer() {
        val raw = tuneCalibTypedBuffer.trim()
        val parsed = raw.toIntOrNull()
        if (parsed != null) {
            spec.applySliderPos(item, parsed)
            applyLiveHudTuneItemChanges(catId, item)
        }
        tuneCalibKeypadOpen = false
    }

    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        val x = event.x
        val y = event.y
        val prefs = context.defaultPrefs()

        if (!tuneCalibModalRect.contains(x, y) || tuneCalibCloseBtnRect.contains(x, y)) {
            triggerHardwareHaptic(18, 75)
            closeTuneCalibrationModal()
            return true
        }

        if (tuneCalibApplyBtnRect.contains(x, y)) {
            triggerHardwareHaptic(22, 95)
            if (tuneCalibKeypadOpen) {
                commitBuffer()
            } else {
                tuneCalibKeypadOpen = true
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
            }
            invalidate()
            return true
        }

        if (tuneCalibTypeCardRect.contains(x, y)) {
            triggerHardwareHaptic(18, 80)
            tuneCalibKeypadOpen = !tuneCalibKeypadOpen
            if (tuneCalibKeypadOpen) {
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
            }
            invalidate()
            return true
        }

        if (tuneCalibFineMinusRect.contains(x, y)) {
            triggerHardwareHaptic(18, 85)
            spec.applySteppedDelta(item, -1)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibFinePlusRect.contains(x, y)) {
            triggerHardwareHaptic(18, 85)
            spec.applySteppedDelta(item, +1)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibKeypadOpen) {
            for ((key, rect) in tuneCalibKeypadBtnRects) {
                if (rect.contains(x, y)) {
                    triggerHardwareHaptic(16, 75)
                    when (key) {
                        "⌫" -> {
                            tuneCalibReplaceOnNextDigit = false
                            tuneCalibTypedBuffer = tuneCalibTypedBuffer.dropLast(1)
                            invalidate()
                        }
                        "✓" -> {
                            commitBuffer()
                            invalidate()
                        }
                        "AUTO" -> {
                            spec.applyAutoOrReset(item, width > height)
                            tuneCalibKeypadOpen = false
                            applyLiveHudTuneItemChanges(catId, item)
                        }
                        else -> {
                            if (tuneCalibReplaceOnNextDigit) {
                                tuneCalibTypedBuffer = key
                                tuneCalibReplaceOnNextDigit = false
                            } else if (tuneCalibTypedBuffer.length < 4) {
                                tuneCalibTypedBuffer = (tuneCalibTypedBuffer + key).trimStart('0').ifEmpty { key }
                            }
                            invalidate()
                        }
                    }
                    return true
                }
            }
        }

        if (tuneCalibTapToJumpRect.contains(x, y)) {
            triggerHardwareHaptic(20, 90)
            val key = LightspeedPreferences.keySliderTapToJump(spec.id)
            val current = prefs.getBoolean(key, false)
            prefs.edit().putBoolean(key, !current).apply()
            invalidate()
            return true
        }

        if (tuneCalibResetBtnRect.contains(x, y)) {
            triggerHardwareHaptic(28, 115)
            spec.applyAutoOrReset(item, width > height)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibProfilesHeaderRect.contains(x, y)) {
            triggerHardwareHaptic(16, 75)
            tuneCalibProfilesExpanded = !tuneCalibProfilesExpanded
            invalidate()
            return true
        }

        if (tuneCalibProfilesExpanded && tuneCalibAddProfileRect.contains(x, y)) {
            val rawSaveValue = if (spec.isAutoActive(item)) "AUTO" else spec.getSliderPos(item).toString()
            val added = SliderPresetManager.addPreset(prefs, spec.id, null, rawSaveValue)
            triggerHardwareHaptic(if (added) 24 else 14, 90)
            invalidate()
            return true
        }

        for (hit in tuneCalibProfileRows) {
            if (hit.deleteRect.contains(x, y)) {
                triggerHardwareHaptic(18, 80)
                SliderPresetManager.removePreset(prefs, spec.id, hit.preset)
                invalidate()
                return true
            }
            if (hit.rowRect.contains(x, y)) {
                triggerHardwareHaptic(22, 95)
                val vStr = hit.preset.valueStr.trim()
                if (vStr.equals("AUTO", ignoreCase = true)) {
                    spec.applyAutoOrReset(item, width > height)
                } else {
                    val parsed = vStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                    if (parsed != null) {
                        spec.applySliderPos(item, parsed)
                    }
                }
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
                applyLiveHudTuneItemChanges(catId, item)
                return true
            }
        }
        return true
    }
    return true
}
