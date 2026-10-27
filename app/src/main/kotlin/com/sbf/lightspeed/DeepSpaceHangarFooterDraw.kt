package com.sbf.lightspeed

import android.graphics.Color
import android.graphics.Paint

internal fun DeepSpaceRenderer.drawHangarOrbitalControls(
    state: HangarRenderState,
    layout: HangarLayoutMetrics,
    cx: Float,
    cyGimbal: Float,
    radOuter: Float,
    deckW: Float,
    focusedAppLabel: String?,
    activeRingColor: Int,
    otherRingColor: Int,
    activeAppsCount: Int
) {
    val canvas = state.canvas
    val d = state.density
    val activeHangarRing = state.activeHangarRing
    val isHangarEjectArmed = state.isHangarEjectArmed

    val shiftBarY = cyGimbal + radOuter + (if (layout.isLandscape) 20f else 26f) * d
    val transferBarY = cyGimbal + radOuter + (if (layout.isLandscape) 48f else 58f) * d

    if (focusedAppLabel != null) {
        if (activeAppsCount > 1) {
            shiftLeftRect.set(cx - 150f * d, shiftBarY - 14f * d, cx - 60f * d, shiftBarY + 14f * d)
            shiftRightRect.set(cx + 60f * d, shiftBarY - 14f * d, cx + 150f * d, shiftBarY + 14f * d)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(160, 18, 24, 40)
            canvas.drawRoundRect(shiftLeftRect, 9f * d, 9f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1f * d
            highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
            canvas.drawRoundRect(shiftLeftRect, 9f * d, 9f * d, highlightPaint)
            textPaint.textSize = 10f * d
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.color = activeRingColor
            canvas.drawText("◀ SHIFT COG", shiftLeftRect.centerX(), shiftLeftRect.centerY() + 3.5f * d, textPaint)

            targetBadgeRect.set(cx - 56f * d, shiftBarY - 14f * d, cx + 56f * d, shiftBarY + 14f * d)
            if (isHangarEjectArmed) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(230, 220, 38, 38)
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.4f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 8.5f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                canvas.drawText("✕ TAP TO EJECT", cx, shiftBarY + 3.5f * d, textPaint)
            } else {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 18, 24, 40)
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1f * d
                highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 11f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                canvas.drawText(focusedAppLabel.take(13), cx, shiftBarY + 3.5f * d, textPaint)
            }

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(160, 18, 24, 40)
            canvas.drawRoundRect(shiftRightRect, 9f * d, 9f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1f * d
            highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
            canvas.drawRoundRect(shiftRightRect, 9f * d, 9f * d, highlightPaint)
            textPaint.color = activeRingColor
            canvas.drawText("SHIFT COG ▶", shiftRightRect.centerX(), shiftRightRect.centerY() + 3.5f * d, textPaint)
        } else {
            singleTargetRect.set(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)
            if (isHangarEjectArmed) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(230, 220, 38, 38)
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.4f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 9f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                canvas.drawText("✕ TAP TO EJECT", cx, shiftBarY + 3.5f * d, textPaint)
            } else {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 18, 24, 40)
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = activeRingColor
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 11f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                canvas.drawText(focusedAppLabel.take(16), cx, shiftBarY + 3.5f * d, textPaint)
            }
        }

        val destRingName = if (activeHangarRing == 0) "INNER RING 02" else "OUTER RING 01"
        transferRect.set(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(160, 20, 26, 46)
        canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = otherRingColor
        canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
        textPaint.textSize = 10f * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText("⇄ TRANSFER TO $destRingName", transferRect.centerX(), transferRect.centerY() + 3.5f * d, textPaint)

        textPaint.textSize = 8.5f * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = Color.argb(150, 180, 210, 245)
        canvas.drawText("✦ DRAG TO ROTATE • TAP ✕ TO EJECT • HOLD TO EDIT ✦", cx, transferBarY + 22f * d, textPaint)
    } else {
        textPaint.textSize = 10f * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = Color.argb(140, 200, 220, 255)
        canvas.drawText("✦ TOUCH & DRAG TO SPIN // TAP CENTER TO EDIT ✦", cx, shiftBarY + 3.5f * d, textPaint)
    }

    // Bottom Exit Capsule
    exitBtnRect.set(layout.exitRect)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(140, 24, 28, 40)
    canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * d
    highlightPaint.color = Color.argb(80, 200, 220, 255)
    canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
    textPaint.textSize = (if (layout.isLandscape) 10.5f else 11.5f) * d
    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    textPaint.color = Color.WHITE
    canvas.drawText("✕ CLOSE HANGAR / RETURN", exitBtnRect.centerX(), exitBtnRect.centerY() + 4f * d, textPaint)
}
