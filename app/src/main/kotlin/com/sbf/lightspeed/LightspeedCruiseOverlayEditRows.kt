package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.sbf.lightspeed.settings.DeckThemeSnapshot

private val editModeArrowPath = Path()

internal fun LightspeedCruiseOverlay.drawEditModeCategoryRows(
    canvas: Canvas,
    paint: Paint,
    bounds: RectF,
    snapshot: DeckThemeSnapshot,
    actualRowH: Float,
    tabRowBottom: Float,
    numCategories: Int,
    dp: Float
) {
    val tintColor = snapshot.tintArgb.toInt()
    val borderColor = snapshot.borderColorArgb.toInt()
    val textSize = (actualRowH * 0.38f).coerceIn(10f * dp, 12.5f * dp)
    paint.style = Paint.Style.FILL
    paint.textSize = textSize
    paint.textAlign = Paint.Align.LEFT
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
    val textX = bounds.left + 12f * dp
    val arrowSize = (actualRowH * 0.16f).coerceIn(3.5f * dp, 5f * dp)
    val cxUp = bounds.right - 42f * dp
    val cxDown = bounds.right - 14f * dp

    val isDragging = editModeDragSourceIndex in 0 until numCategories
    val dragTarget = editModeDragTargetIndex.coerceIn(0, numCategories - 1)

    // Slot dividers
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 0.8f * dp
    paint.color = Color.argb(40, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
    for (s in 0 until numCategories - 1) {
        val divY = tabRowBottom + ((s + 1) * actualRowH)
        canvas.drawLine(bounds.left + 10f * dp, divY, bounds.right - 10f * dp, divY, paint)
    }

    // Drop spot indicator when dragging
    if (isDragging) {
        val dropTop = tabRowBottom + (dragTarget * actualRowH)
        val dropRect = RectF(bounds.left + 6f * dp, dropTop + 2f * dp, bounds.right - 6f * dp, dropTop + actualRowH - 2f * dp)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(40, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        canvas.drawRoundRect(dropRect, 6f * dp, 6f * dp, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * dp
        paint.color = Color.argb(120, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        canvas.drawRoundRect(dropRect, 6f * dp, 6f * dp, paint)
    }

    // Category rows (stationary / shifted)
    for (i in 0 until numCategories) {
        if (isDragging && i == editModeDragSourceIndex) continue
        val cat = editModeCategories[i]
        val slot = when {
            !isDragging -> i
            dragTarget > editModeDragSourceIndex -> if (i in (editModeDragSourceIndex + 1)..dragTarget) i - 1 else i
            dragTarget < editModeDragSourceIndex -> if (i in dragTarget until editModeDragSourceIndex) i + 1 else i
            else -> i
        }
        val rowTop = tabRowBottom + (slot * actualRowH)
        val rowCenterY = rowTop + (actualRowH / 2f)

        // Text
        paint.style = Paint.Style.FILL
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.argb(245, 240, 245, 255)
        canvas.drawText(cat.label, textX, rowCenterY - textYOffset, paint)

        // Up arrow
        val upColor = if (slot > 0) Color.argb(220, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(45, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        paint.color = upColor
        editModeArrowPath.rewind()
        editModeArrowPath.moveTo(cxUp, rowCenterY - arrowSize)
        editModeArrowPath.lineTo(cxUp + arrowSize, rowCenterY + arrowSize)
        editModeArrowPath.lineTo(cxUp - arrowSize, rowCenterY + arrowSize)
        editModeArrowPath.close()
        canvas.drawPath(editModeArrowPath, paint)

        // Down arrow
        val downColor = if (slot < numCategories - 1) Color.argb(220, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(45, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        paint.color = downColor
        editModeArrowPath.rewind()
        editModeArrowPath.moveTo(cxDown - arrowSize, rowCenterY - arrowSize)
        editModeArrowPath.lineTo(cxDown + arrowSize, rowCenterY - arrowSize)
        editModeArrowPath.lineTo(cxDown, rowCenterY + arrowSize)
        editModeArrowPath.close()
        canvas.drawPath(editModeArrowPath, paint)
    }

    // Dragged row on top
    if (isDragging) {
        val draggedCat = editModeCategories[editModeDragSourceIndex]
        val halfH = actualRowH / 2f
        val minY = tabRowBottom + halfH
        val maxY = tabRowBottom + (numCategories * actualRowH) - halfH
        val clampedCenterY = editModeDragY.coerceIn(minY, maxY)
        val rowTop = clampedCenterY - halfH
        val rowBottom = clampedCenterY + halfH

        val panelColor = snapshot.panelColorArgb.toInt()
        val dragRect = RectF(bounds.left + 4f * dp, rowTop + 1f * dp, bounds.right - 4f * dp, rowBottom - 1f * dp)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(235, Color.red(panelColor), Color.green(panelColor), Color.blue(panelColor))
        canvas.drawRoundRect(dragRect, 8f * dp, 8f * dp, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * dp
        paint.color = Color.argb(210, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        canvas.drawRoundRect(dragRect, 8f * dp, 8f * dp, paint)

        // Text
        paint.style = Paint.Style.FILL
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.argb(255, 255, 255, 255)
        canvas.drawText(draggedCat.label, textX, clampedCenterY - textYOffset, paint)

        // Up arrow
        paint.color = Color.argb(230, 160, 225, 255)
        val upPath = Path().apply {
            moveTo(cxUp, clampedCenterY - arrowSize)
            lineTo(cxUp + arrowSize, clampedCenterY + arrowSize)
            lineTo(cxUp - arrowSize, clampedCenterY + arrowSize)
            close()
        }
        canvas.drawPath(upPath, paint)

        // Down arrow
        val downPath = Path().apply {
            moveTo(cxDown - arrowSize, clampedCenterY - arrowSize)
            lineTo(cxDown + arrowSize, clampedCenterY - arrowSize)
            lineTo(cxDown, clampedCenterY + arrowSize)
            close()
        }
        canvas.drawPath(downPath, paint)
    }
}
