package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.sbf.lightspeed.system.lsString

internal fun LightspeedCruiseOverlay.drawUnsavedPrompt(canvas: Canvas, paint: Paint) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Dimming backdrop
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(120, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, paint)

    val promptBounds = getUnsavedPromptBounds()

    // Theme-driven optical styling
    val promptSnapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (promptSnapshot.cornerRadiusDp * 0.65f).coerceIn(12f, 24f) * dp
    val borderColor = promptSnapshot.borderColorArgb.toInt()

    // 1-4. Full 12-theme optical flyout surface
    drawDeckThemedFlyoutSurface(canvas, promptBounds, promptSnapshot, cornerRadius, dp, paint)

    // Header area
    val btnRowH = 40f * dp
    val headerH = promptBounds.height() - btnRowH

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = overlayTypeface(isBold = true)
    paint.textSize = 13f * dp
    paint.color = Color.argb(245, 240, 245, 255)
    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
    val titleY = promptBounds.top + (headerH * 0.4f) - textYOffset
    canvas.drawText(lsString(R.string.ed_unsaved_title), promptBounds.centerX(), titleY, paint)

    paint.textSize = 10.5f * dp
    paint.typeface = overlayTypeface(isBold = false)
    paint.color = Color.argb(180, 200, 220, 255)
    val subFontMetrics = paint.fontMetrics
    val subTextYOffset = (subFontMetrics.descent + subFontMetrics.ascent) / 2f
    val subY = promptBounds.top + (headerH * 0.75f) - subTextYOffset
    canvas.drawText(lsString(R.string.ed_unsaved_msg), promptBounds.centerX(), subY, paint)

    // Divider above buttons
    val btnRowTop = promptBounds.bottom - btnRowH
    val btnRowCenterY = btnRowTop + (btnRowH / 2f)
    val btnW = promptBounds.width() / 3f

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(60, 200, 220, 255)
    canvas.drawLine(promptBounds.left, btnRowTop, promptBounds.right, btnRowTop, paint)

    // Vertical dividers between buttons
    canvas.drawLine(promptBounds.left + btnW, btnRowTop + 4f * dp, promptBounds.left + btnW, promptBounds.bottom - 4f * dp, paint)
    canvas.drawLine(promptBounds.left + (btnW * 2f), btnRowTop + 4f * dp, promptBounds.left + (btnW * 2f), promptBounds.bottom - 4f * dp, paint)

    val saveLbl = lsString(R.string.ed_btn_save)
    val discardLbl = lsString(R.string.ed_unsaved_discard)
    val keepLbl = lsString(R.string.ed_unsaved_keep)

    // Button labels
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = overlayTypeface(isBold = true)
    var btnTextSize = 10.5f * dp
    paint.textSize = btnTextSize
    val longestBtn = listOf(saveLbl, discardLbl, keepLbl).maxByOrNull { paint.measureText(it) } ?: keepLbl
    while (paint.measureText(longestBtn) > btnW - 6f * dp && btnTextSize > 7.5f * dp) {
        btnTextSize -= 0.5f * dp
        paint.textSize = btnTextSize
    }

    val bfm = paint.fontMetrics
    val btnYOffset = (bfm.descent + bfm.ascent) / 2f

    // "Save"
    paint.color = Color.argb(255, 120, 210, 255)
    canvas.drawText(saveLbl, promptBounds.left + (btnW * 0.5f), btnRowCenterY - btnYOffset, paint)

    // "Discard"
    paint.color = Color.argb(240, 255, 130, 130)
    canvas.drawText(discardLbl, promptBounds.left + (btnW * 1.5f), btnRowCenterY - btnYOffset, paint)

    // "Keep editing"
    paint.color = Color.argb(230, 200, 220, 255)
    canvas.drawText(keepLbl, promptBounds.left + (btnW * 2.5f), btnRowCenterY - btnYOffset, paint)
}
