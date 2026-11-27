package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

internal val HANGAR_GHOST_TIP_RES_IDS = intArrayOf(
    R.string.ck_hangar_tip_1,
    R.string.ck_hangar_tip_2,
    R.string.ck_hangar_tip_3,
    R.string.ck_hangar_tip_4,
    R.string.ck_hangar_tip_5,
    R.string.ck_hangar_tip_6,
    R.string.ck_hangar_tip_7,
    R.string.ck_hangar_tip_8,
    R.string.ck_hangar_tip_9
)

internal const val HANGAR_TIP_COUNT: Int = 9

private val hangarTipPrefixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}
private val hangarTipBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}

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
    val ts = hangarTextScale(state.context)
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
            textPaint.textSize = 12f * d * ts
            textPaint.typeface = hangarTypeface(state.context, isBold = true)
            textPaint.color = activeRingColor
            canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_shift_cog_left).ifBlank { "◀ SHIFT COG" }, shiftLeftRect.centerX(), shiftLeftRect.centerY() + 3.5f * d, textPaint)

            targetBadgeRect.set(cx - 56f * d, shiftBarY - 14f * d, cx + 56f * d, shiftBarY + 14f * d)
            if (isHangarEjectArmed) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(230, 220, 38, 38)
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.4f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 10.5f * d * ts
                textPaint.typeface = hangarTypeface(state.context, isBold = true)
                textPaint.color = Color.WHITE
                canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_tap_eject).ifBlank { "✕ TAP TO EJECT" }, cx, shiftBarY + 3.5f * d, textPaint)
            } else {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 18, 24, 40)
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1f * d
                highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
                canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 13f * d * ts
                textPaint.typeface = hangarTypeface(state.context, isBold = true)
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
            textPaint.textSize = 12f * d * ts
            textPaint.color = activeRingColor
            canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_shift_cog_right).ifBlank { "SHIFT COG ▶" }, shiftRightRect.centerX(), shiftRightRect.centerY() + 3.5f * d, textPaint)
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
                textPaint.textSize = 11f * d * ts
                textPaint.typeface = hangarTypeface(state.context, isBold = true)
                textPaint.color = Color.WHITE
                canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_tap_eject).ifBlank { "✕ TAP TO EJECT" }, cx, shiftBarY + 3.5f * d, textPaint)
            } else {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 18, 24, 40)
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = activeRingColor
                canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 11f * d
                textPaint.typeface = hangarTypeface(state.context, isBold = true)
                textPaint.color = Color.WHITE
                canvas.drawText(focusedAppLabel.take(16), cx, shiftBarY + 3.5f * d, textPaint)
            }
        }

        val destRingName = if (activeHangarRing == 0) {
            com.sbf.lightspeed.system.lsString(R.string.ck_inner_ring).ifBlank { "INNER RING 02" }
        } else {
            com.sbf.lightspeed.system.lsString(R.string.ck_outer_ring).ifBlank { "OUTER RING 01" }
        }
        transferRect.set(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(160, 20, 26, 46)
        canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = otherRingColor
        canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
        textPaint.textSize = 10f * d
        textPaint.typeface = hangarTypeface(state.context, isBold = true)
        textPaint.color = Color.WHITE
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_transfer_to, destRingName).ifBlank { "⇄ TRANSFER TO $destRingName" }, transferRect.centerX(), transferBarY + 3.5f * d, textPaint)
    }

    // Bottom Exit Capsule OR Gold Live "Try-Before-You-Buy" Preview Dock
    exitBtnRect.set(layout.exitRect)
    val previewLabel = com.sbf.lightspeed.system.CockpitGimbalConfigCache.previewFeatureLabel
    val isPreviewBarActive = !com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(state.context) &&
        !previewLabel.isNullOrBlank()
    if (isPreviewBarActive) {
        val goldColor = Color.rgb(255, 215, 0)
        val goldSolid = Color.rgb(255, 221, 0)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(235, 22, 18, 8)
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.4f * d
        highlightPaint.color = goldColor
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)

        val padX = 8f * d
        val exitW = 54f * d
        val topRowCenterY = exitBtnRect.top + 15f * d
        previewExitRect.set(
            exitBtnRect.right - padX - exitW,
            exitBtnRect.top + 4f * d,
            exitBtnRect.right - padX,
            exitBtnRect.top + 24f * d
        )
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(110, 42, 48, 66)
        canvas.drawRoundRect(previewExitRect, 7f * d, 7f * d, highlightPaint)
        textPaint.typeface = hangarTypeface(state.context, isBold = true)
        textPaint.color = Color.WHITE
        drawFittedSingleLineText(
            canvas = canvas,
            rawText = com.sbf.lightspeed.system.lsString(R.string.ck_preview_exit).ifBlank { "✕ EXIT" },
            x = previewExitRect.centerX(),
            baselineY = previewExitRect.centerY() + 3.2f * d,
            maxWidth = (previewExitRect.width() - 6f * d).coerceAtLeast(24f * d),
            baseSize = 9.0f * d * ts,
            minSize = 7.2f * d,
            paint = textPaint,
            align = Paint.Align.CENTER
        )

        val hdrLeft = exitBtnRect.left + padX + 2f * d
        val hdrRight = previewExitRect.left - 6f * d
        val hdrMaxW = (hdrRight - hdrLeft).coerceAtLeast(60f * d)
        val hdrText = com.sbf.lightspeed.system.lsString(R.string.ck_preview_bar_hdr, previewLabel!!.uppercase())
            .ifBlank { "★ PREVIEW: ${previewLabel.uppercase()}" }
        textPaint.typeface = hangarTypeface(state.context, isBold = true)
        textPaint.color = goldColor
        drawFittedSingleLineText(
            canvas = canvas,
            rawText = hdrText,
            x = hdrLeft,
            baselineY = topRowCenterY + 2f * d,
            maxWidth = hdrMaxW,
            baseSize = (if (layout.isLandscape) 9.0f else 9.6f) * d * ts,
            minSize = 7.6f * d,
            paint = textPaint,
            align = Paint.Align.LEFT
        )

        val btnTop = exitBtnRect.top + 27f * d
        val btnBottom = exitBtnRect.bottom - 5f * d
        val btnGap = 6f * d
        val innerW = (exitBtnRect.width() - padX * 2f - btnGap).coerceAtLeast(100f * d)
        val getKeyW = innerW * 0.64f
        previewGetKeyRect.set(exitBtnRect.left + padX, btnTop, exitBtnRect.left + padX + getKeyW, btnBottom)
        previewEnterKeyRect.set(previewGetKeyRect.right + btnGap, btnTop, exitBtnRect.right - padX, btnBottom)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = goldSolid
        canvas.drawRoundRect(previewGetKeyRect, 9f * d, 9f * d, highlightPaint)
        textPaint.typeface = hangarTypeface(state.context, isBold = true)
        textPaint.color = Color.BLACK
        drawFittedSingleLineText(
            canvas = canvas,
            rawText = com.sbf.lightspeed.system.lsString(R.string.ck_preview_get_key).ifBlank { "★ GET KEY ($1.99 · GOOGLE PAY)" },
            x = previewGetKeyRect.centerX(),
            baselineY = previewGetKeyRect.centerY() + 3.5f * d,
            maxWidth = (previewGetKeyRect.width() - 10f * d).coerceAtLeast(48f * d),
            baseSize = (if (layout.isLandscape) 9.2f else 9.8f) * d * ts,
            minSize = 7.4f * d,
            paint = textPaint,
            align = Paint.Align.CENTER
        )

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(45, 255, 215, 0)
        canvas.drawRoundRect(previewEnterKeyRect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.1f * d
        highlightPaint.color = goldColor
        canvas.drawRoundRect(previewEnterKeyRect, 9f * d, 9f * d, highlightPaint)
        textPaint.color = goldColor
        drawFittedSingleLineText(
            canvas = canvas,
            rawText = com.sbf.lightspeed.system.lsString(R.string.ck_preview_enter_key).ifBlank { "ENTER KEY" },
            x = previewEnterKeyRect.centerX(),
            baselineY = previewEnterKeyRect.centerY() + 3.5f * d,
            maxWidth = (previewEnterKeyRect.width() - 8f * d).coerceAtLeast(36f * d),
            baseSize = (if (layout.isLandscape) 9.0f else 9.5f) * d * ts,
            minSize = 7.4f * d,
            paint = textPaint,
            align = Paint.Align.CENTER
        )
    } else {
        previewGetKeyRect.setEmpty()
        previewEnterKeyRect.setEmpty()
        previewExitRect.setEmpty()
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(140, 24, 28, 40)
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = Color.argb(80, 200, 220, 255)
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
        textPaint.textSize = (if (layout.isLandscape) 12.5f else 13.5f) * d * ts
        textPaint.typeface = hangarTypeface(state.context, isBold = true)
        textPaint.color = Color.WHITE
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ck_close_hangar).ifBlank { "✕ CLOSE HANGAR / RETURN" }, exitBtnRect.centerX(), exitBtnRect.centerY() + 4f * d, textPaint)
    }

    drawHangarGhostTip(state, layout, cyGimbal, radOuter)
}

private fun drawFittedSingleLineText(
    canvas: Canvas,
    rawText: String,
    x: Float,
    baselineY: Float,
    maxWidth: Float,
    baseSize: Float,
    minSize: Float,
    paint: Paint,
    align: Paint.Align
) {
    val prevAlign = paint.textAlign
    try {
        paint.textAlign = align
        paint.textSize = baseSize
        val measured = paint.measureText(rawText)
        if (measured > maxWidth && measured > 0f) {
            paint.textSize = (baseSize * (maxWidth / measured)).coerceIn(minSize, baseSize)
        }
        var label = rawText
        if (paint.measureText(label) > maxWidth) {
            while (label.length > 1 && paint.measureText("$label…") > maxWidth) {
                label = label.dropLast(1).trimEnd()
            }
            if (label.isNotEmpty()) label = "$label…"
        }
        canvas.drawText(label, x, baselineY, paint)
    } finally {
        paint.textAlign = prevAlign
    }
}

private fun DeepSpaceRenderer.drawHangarGhostTip(
    state: HangarRenderState,
    layout: HangarLayoutMetrics,
    cyGimbal: Float,
    radOuter: Float
) {
    hangarTipRect.setEmpty()
    val canvas = state.canvas
    val d = state.density
    val controlsBottomY = if (layout.openAccordion in 0..2) layout.bottomCtrlY + 18f * d else layout.rAccY + 14f * d
    val openTopY = controlsBottomY + 8f * d
    val openBottomY = if (layout.isLandscape) layout.exitRect.top - 8f * d else (cyGimbal - radOuter - 30f * d) - 8f * d
    val availH = openBottomY - openTopY
    if (availH <= 0f) return

    val tipIdx = ((state.hangarTipIndex % HANGAR_GHOST_TIP_RES_IDS.size) + HANGAR_GHOST_TIP_RES_IDS.size) % HANGAR_GHOST_TIP_RES_IDS.size
    val tipText = com.sbf.lightspeed.system.lsString(HANGAR_GHOST_TIP_RES_IDS[tipIdx]).trim()
    if (tipText.isEmpty()) return

    val tipCx = layout.ctrlCx
    val maxTextW = (layout.deckW - 22f * d).coerceAtLeast(120f * d)
    val tipScale = hangarTextScale(state.context).coerceIn(0.85f, 1.18f)
    val baseSize = (if (layout.isLandscape) 11.5f else 12.5f) * d * tipScale
    val minSize = (9.5f * d * tipScale).coerceAtMost(baseSize)
    val kicker = resolveTipKickerLabel()
    val isRtl = isRtlScriptText(tipText) || isRtlScriptText(kicker)

    hangarTipPrefixPaint.typeface = hangarTypeface(state.context, isBold = true)
    hangarTipBodyPaint.typeface = hangarTypeface(state.context, isBold = false)

    var textSize = baseSize
    hangarTipPrefixPaint.textSize = textSize
    hangarTipBodyPaint.textSize = textSize

    var wrappedLines = wrapBalancedGhostTipLines(
        kicker = kicker,
        body = tipText,
        prefixPaint = hangarTipPrefixPaint,
        bodyPaint = hangarTipBodyPaint,
        maxWidth = maxTextW,
        isRtl = isRtl
    )
    if (wrappedLines.isEmpty()) return

    var fm = hangarTipBodyPaint.fontMetrics
    var glyphH = fm.descent - fm.ascent
    var lineH = glyphH * 1.24f
    var visualH = ((wrappedLines.size - 1) * lineH) + glyphH
    if (availH < visualH + 4f * d && textSize > minSize) {
        textSize = (baseSize * (availH / (visualH + 6f * d))).coerceIn(minSize, baseSize)
        hangarTipPrefixPaint.textSize = textSize
        hangarTipBodyPaint.textSize = textSize
        wrappedLines = wrapBalancedGhostTipLines(
            kicker = kicker,
            body = tipText,
            prefixPaint = hangarTipPrefixPaint,
            bodyPaint = hangarTipBodyPaint,
            maxWidth = maxTextW,
            isRtl = isRtl
        )
        if (wrappedLines.isEmpty()) return
        fm = hangarTipBodyPaint.fontMetrics
        glyphH = fm.descent - fm.ascent
        lineH = glyphH * 1.24f
        visualH = ((wrappedLines.size - 1) * lineH) + glyphH
    }
    if (availH < visualH + 4f * d) return

    val firstBaselineY = openTopY + (availH - visualH) * 0.5f - fm.ascent
    val shadowColor = Color.argb(195, 0, 0, 0)
    hangarTipPrefixPaint.color = Color.argb(
        235,
        Color.red(state.m3Primary),
        Color.green(state.m3Primary),
        Color.blue(state.m3Primary)
    )
    hangarTipBodyPaint.color = Color.argb(230, 220, 234, 252)

    try {
        hangarTipPrefixPaint.setShadowLayer(5.5f * d, 0f, 1.5f * d, shadowColor)
        hangarTipBodyPaint.setShadowLayer(5.5f * d, 0f, 1.5f * d, shadowColor)

        drawLocalizedTipFirstLine(
            canvas = canvas,
            kicker = kicker,
            bodyLine = wrappedLines[0],
            isTruncatedWithEllipsis = false,
            cx = tipCx,
            baselineY = firstBaselineY,
            prefixPaint = hangarTipPrefixPaint,
            bodyPaint = hangarTipBodyPaint,
            isRtl = isRtl
        )

        for (idx in 1 until wrappedLines.size) {
            drawCenteredBodyLine(
                canvas = canvas,
                lineText = wrappedLines[idx],
                isTruncatedWithEllipsis = false,
                cx = tipCx,
                baselineY = firstBaselineY + idx * lineH,
                bodyPaint = hangarTipBodyPaint,
                isRtl = isRtl
            )
        }
    } finally {
        hangarTipPrefixPaint.clearShadowLayer()
        hangarTipBodyPaint.clearShadowLayer()
    }

    val tipTop = firstBaselineY + fm.ascent
    val tipBottom = tipTop + visualH
    hangarTipRect.set(
        layout.leftX,
        (tipTop - 10f * d).coerceAtLeast(openTopY - 4f * d),
        layout.rightX,
        (tipBottom + 10f * d).coerceAtMost(openBottomY + 4f * d)
    )
}
