package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.abs

internal val HANGAR_GHOST_TIP_RES_IDS = intArrayOf(
    R.string.ck_hangar_tip_1,
    R.string.ck_hangar_tip_2,
    R.string.ck_hangar_tip_3,
    R.string.ck_hangar_tip_4,
    R.string.ck_hangar_tip_5,
    R.string.ck_hangar_tip_6,
    R.string.ck_hangar_tip_7,
    R.string.ck_hangar_tip_8
)

internal const val HANGAR_TIP_COUNT: Int = 8

private val sharedTipPrefixPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}
private val sharedTipBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    style = Paint.Style.FILL
    textAlign = Paint.Align.LEFT
}

internal fun drawInlineGhostTipSingleLine(
    canvas: Canvas,
    tipBody: String,
    cx: Float,
    baselineY: Float,
    maxWidth: Float,
    density: Float,
    baseTextSize: Float,
    accentColor: Int,
    boldTypeface: Typeface,
    regularTypeface: Typeface,
    alphaFactor: Float = 1f,
    minTextSize: Float = (9.4f * density).coerceAtMost(baseTextSize),
    maxLines: Int = 1
) {
    val cleanBody = tipBody.trim()
    if (cleanBody.isEmpty() || maxWidth <= 0f) return
    val clampedAlpha = alphaFactor.coerceIn(0f, 1f)
    if (clampedAlpha <= 0.01f) return

    val prefix = "✦ ${com.sbf.lightspeed.system.lsString(R.string.ck_hangar_tip_kicker).ifBlank { "TIP" }}: "
    sharedTipPrefixPaint.typeface = boldTypeface
    sharedTipBodyPaint.typeface = regularTypeface

    val effectiveMinSize = minTextSize.coerceIn(7.8f * density, baseTextSize)
    sharedTipPrefixPaint.textSize = baseTextSize
    sharedTipBodyPaint.textSize = baseTextSize

    val rawTotalW = sharedTipPrefixPaint.measureText(prefix) + sharedTipBodyPaint.measureText(cleanBody)

    val shadowColor = Color.argb((225 * clampedAlpha).toInt().coerceIn(0, 255), 0, 0, 0)
    sharedTipPrefixPaint.color = Color.argb(
        (238 * clampedAlpha).toInt().coerceIn(0, 255),
        Color.red(accentColor),
        Color.green(accentColor),
        Color.blue(accentColor)
    )
    sharedTipBodyPaint.color = Color.argb((235 * clampedAlpha).toInt().coerceIn(0, 255), 235, 243, 255)

    try {
        sharedTipPrefixPaint.setShadowLayer(5.5f * density, 0f, 1.5f * density, shadowColor)
        sharedTipBodyPaint.setShadowLayer(5.5f * density, 0f, 1.5f * density, shadowColor)

        if (maxLines >= 2 && rawTotalW > maxWidth) {
            val singleFitSize = if (rawTotalW > 0f) baseTextSize * (maxWidth / rawTotalW) else baseTextSize
            if (singleFitSize < baseTextSize * 0.90f) {
                var wrapSize = baseTextSize
                sharedTipPrefixPaint.textSize = wrapSize
                sharedTipBodyPaint.textSize = wrapSize
                var wrapped = wrapBalancedGhostTipLines(
                    prefix = prefix,
                    body = cleanBody,
                    prefixPaint = sharedTipPrefixPaint,
                    bodyPaint = sharedTipBodyPaint,
                    maxWidth = maxWidth
                )
                while (wrapped.size > 2 && wrapSize > effectiveMinSize) {
                    wrapSize = (wrapSize - 0.5f * density).coerceAtLeast(effectiveMinSize)
                    sharedTipPrefixPaint.textSize = wrapSize
                    sharedTipBodyPaint.textSize = wrapSize
                    wrapped = wrapBalancedGhostTipLines(
                        prefix = prefix,
                        body = cleanBody,
                        prefixPaint = sharedTipPrefixPaint,
                        bodyPaint = sharedTipBodyPaint,
                        maxWidth = maxWidth
                    )
                }
                if (wrapped.size >= 2) {
                    val line0 = wrapped[0]
                    var line1 = wrapped.drop(1).joinToString(" ")
                    if (sharedTipBodyPaint.measureText(line1) > maxWidth) {
                        while (line1.length > 1 && sharedTipBodyPaint.measureText("$line1…") > maxWidth) {
                            line1 = line1.dropLast(1).trimEnd()
                        }
                        if (line1.isNotEmpty()) line1 = "$line1…"
                    }
                    val fm = sharedTipBodyPaint.fontMetrics
                    val lineH = (fm.descent - fm.ascent) * 1.22f
                    val line0BaselineY = baselineY - lineH
                    val prefixW = sharedTipPrefixPaint.measureText(prefix)
                    val line0W = sharedTipBodyPaint.measureText(line0)
                    val line0StartX = cx - (prefixW + line0W) * 0.5f
                    canvas.drawText(prefix, line0StartX, line0BaselineY, sharedTipPrefixPaint)
                    canvas.drawText(line0, line0StartX + prefixW, line0BaselineY, sharedTipBodyPaint)
                    val line1W = sharedTipBodyPaint.measureText(line1)
                    canvas.drawText(line1, cx - line1W * 0.5f, baselineY, sharedTipBodyPaint)
                    return
                }
            }
        }

        val fittedSize = if (rawTotalW > maxWidth && rawTotalW > 0f) {
            (baseTextSize * (maxWidth / rawTotalW)).coerceIn(effectiveMinSize, baseTextSize)
        } else {
            baseTextSize
        }
        sharedTipPrefixPaint.textSize = fittedSize
        sharedTipBodyPaint.textSize = fittedSize

        val prefixW = sharedTipPrefixPaint.measureText(prefix)
        var renderedBody = cleanBody
        if (prefixW + sharedTipBodyPaint.measureText(renderedBody) > maxWidth) {
            val availBodyW = (maxWidth - prefixW).coerceAtLeast(24f * density)
            while (renderedBody.length > 1 && sharedTipBodyPaint.measureText("$renderedBody…") > availBodyW) {
                renderedBody = renderedBody.dropLast(1).trimEnd()
            }
            if (renderedBody.isNotEmpty()) renderedBody = "$renderedBody…"
        }

        val bodyW = sharedTipBodyPaint.measureText(renderedBody)
        val startX = cx - (prefixW + bodyW) * 0.5f
        canvas.drawText(prefix, startX, baselineY, sharedTipPrefixPaint)
        canvas.drawText(renderedBody, startX + prefixW, baselineY, sharedTipBodyPaint)
    } finally {
        sharedTipPrefixPaint.clearShadowLayer()
        sharedTipBodyPaint.clearShadowLayer()
    }
}

/**
 * Draws the live Cockpit Gear Set rotating tip with larger typography, luminance-lifted
 * accent prefix, and high-contrast shadow/body colors while avoiding ring overlap.
 */
internal fun drawLiveGearRotatingTip(
    canvas: Canvas,
    tipBody: String,
    cx: Float,
    topY: Float,
    maxBottomY: Float,
    maxWidth: Float,
    density: Float,
    baseTextSize: Float,
    accentColor: Int,
    boldTypeface: Typeface,
    regularTypeface: Typeface
) {
    val cleanBody = tipBody.trim()
    if (cleanBody.isEmpty() || maxWidth <= 0f) return

    val prefix = "✦ ${com.sbf.lightspeed.system.lsString(R.string.ck_hangar_tip_kicker).ifBlank { "TIP" }}: "
    sharedTipPrefixPaint.typeface = boldTypeface
    sharedTipBodyPaint.typeface = regularTypeface
    sharedTipPrefixPaint.textSize = baseTextSize
    sharedTipBodyPaint.textSize = baseTextSize

    val r = Color.red(accentColor)
    val g = Color.green(accentColor)
    val b = Color.blue(accentColor)
    val luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    val lift = if (luminance < 0.45f) 0.40f else 0f
    val liftR = (r + (255 - r) * lift).toInt().coerceIn(0, 255)
    val liftG = (g + (255 - g) * lift).toInt().coerceIn(0, 255)
    val liftB = (b + (255 - b) * lift).toInt().coerceIn(0, 255)

    val shadowColor = Color.argb(235, 0, 0, 0)
    sharedTipPrefixPaint.color = Color.argb(248, liftR, liftG, liftB)
    sharedTipBodyPaint.color = Color.argb(242, 240, 246, 255)

    val rawSingleW = sharedTipPrefixPaint.measureText(prefix) + sharedTipBodyPaint.measureText(cleanBody)
    val fmFull = sharedTipBodyPaint.fontMetrics
    val glyphH = fmFull.descent - fmFull.ascent
    val lineH = glyphH * 1.24f

    try {
        sharedTipPrefixPaint.setShadowLayer(6f * density, 0f, 1.5f * density, shadowColor)
        sharedTipBodyPaint.setShadowLayer(6f * density, 0f, 1.5f * density, shadowColor)

        if (rawSingleW <= maxWidth) {
            val prefixW = sharedTipPrefixPaint.measureText(prefix)
            val bodyW = sharedTipBodyPaint.measureText(cleanBody)
            val startX = cx - (prefixW + bodyW) * 0.5f
            val baselineY = topY - fmFull.ascent
            canvas.drawText(prefix, startX, baselineY, sharedTipPrefixPaint)
            canvas.drawText(cleanBody, startX + prefixW, baselineY, sharedTipBodyPaint)
            return
        }

        val singleFitSize = if (rawSingleW > 0f) baseTextSize * (maxWidth / rawSingleW) else baseTextSize
        val twoLineAvailH = maxBottomY - topY
        val neededTwoLineH = (lineH + glyphH) + 6f * density

        if (singleFitSize < baseTextSize * 0.90f && twoLineAvailH >= neededTwoLineH) {
            val wrapped = wrapBalancedGhostTipLines(
                prefix = prefix,
                body = cleanBody,
                prefixPaint = sharedTipPrefixPaint,
                bodyPaint = sharedTipBodyPaint,
                maxWidth = maxWidth
            )
            if (wrapped.size in 1..2) {
                val firstBaselineY = topY - fmFull.ascent
                val line0 = wrapped[0]
                val prefixW = sharedTipPrefixPaint.measureText(prefix)
                val line0W = sharedTipBodyPaint.measureText(line0)
                val line0StartX = cx - (prefixW + line0W) * 0.5f
                canvas.drawText(prefix, line0StartX, firstBaselineY, sharedTipPrefixPaint)
                canvas.drawText(line0, line0StartX + prefixW, firstBaselineY, sharedTipBodyPaint)
                if (wrapped.size == 2) {
                    val line1 = wrapped[1]
                    val line1W = sharedTipBodyPaint.measureText(line1)
                    canvas.drawText(line1, cx - line1W * 0.5f, firstBaselineY + lineH, sharedTipBodyPaint)
                }
                return
            }
        }

        val minTextSize = (9.6f * density).coerceAtMost(baseTextSize)
        val fittedSize = singleFitSize.coerceIn(minTextSize, baseTextSize)
        sharedTipPrefixPaint.textSize = fittedSize
        sharedTipBodyPaint.textSize = fittedSize

        val prefixW = sharedTipPrefixPaint.measureText(prefix)
        var renderedBody = cleanBody
        if (prefixW + sharedTipBodyPaint.measureText(renderedBody) > maxWidth) {
            val availBodyW = (maxWidth - prefixW).coerceAtLeast(24f * density)
            while (renderedBody.length > 1 && sharedTipBodyPaint.measureText("$renderedBody…") > availBodyW) {
                renderedBody = renderedBody.dropLast(1).trimEnd()
            }
            if (renderedBody.isNotEmpty()) renderedBody = "$renderedBody…"
        }
        val bodyW = sharedTipBodyPaint.measureText(renderedBody)
        val startX = cx - (prefixW + bodyW) * 0.5f
        val fmFitted = sharedTipBodyPaint.fontMetrics
        val baselineY = topY - fmFitted.ascent
        canvas.drawText(prefix, startX, baselineY, sharedTipPrefixPaint)
        canvas.drawText(renderedBody, startX + prefixW, baselineY, sharedTipBodyPaint)
    } finally {
        sharedTipPrefixPaint.clearShadowLayer()
        sharedTipBodyPaint.clearShadowLayer()
    }
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

    // Bottom Exit Capsule
    exitBtnRect.set(layout.exitRect)
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

    drawHangarGhostTip(state, layout, cyGimbal, radOuter)
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
    val prefix = "✦ ${com.sbf.lightspeed.system.lsString(R.string.ck_hangar_tip_kicker).ifBlank { "TIP" }}: "

    sharedTipPrefixPaint.typeface = hangarTypeface(state.context, isBold = true)
    sharedTipBodyPaint.typeface = hangarTypeface(state.context, isBold = false)

    var textSize = baseSize
    sharedTipPrefixPaint.textSize = textSize
    sharedTipBodyPaint.textSize = textSize

    var wrappedLines = wrapBalancedGhostTipLines(
        prefix = prefix,
        body = tipText,
        prefixPaint = sharedTipPrefixPaint,
        bodyPaint = sharedTipBodyPaint,
        maxWidth = maxTextW
    )
    if (wrappedLines.isEmpty()) return

    var fm = sharedTipBodyPaint.fontMetrics
    var glyphH = fm.descent - fm.ascent
    var lineH = glyphH * 1.24f
    var visualH = ((wrappedLines.size - 1) * lineH) + glyphH
    if (availH < visualH + 4f * d && textSize > minSize) {
        textSize = (baseSize * (availH / (visualH + 6f * d))).coerceIn(minSize, baseSize)
        sharedTipPrefixPaint.textSize = textSize
        sharedTipBodyPaint.textSize = textSize
        wrappedLines = wrapBalancedGhostTipLines(
            prefix = prefix,
            body = tipText,
            prefixPaint = sharedTipPrefixPaint,
            bodyPaint = sharedTipBodyPaint,
            maxWidth = maxTextW
        )
        if (wrappedLines.isEmpty()) return
        fm = sharedTipBodyPaint.fontMetrics
        glyphH = fm.descent - fm.ascent
        lineH = glyphH * 1.24f
        visualH = ((wrappedLines.size - 1) * lineH) + glyphH
    }
    if (availH < visualH + 4f * d) return

    val firstBaselineY = openTopY + (availH - visualH) * 0.5f - fm.ascent
    val shadowColor = Color.argb(195, 0, 0, 0)
    sharedTipPrefixPaint.color = Color.argb(
        235,
        Color.red(state.m3Primary),
        Color.green(state.m3Primary),
        Color.blue(state.m3Primary)
    )
    sharedTipBodyPaint.color = Color.argb(230, 220, 234, 252)

    try {
        sharedTipPrefixPaint.setShadowLayer(5.5f * d, 0f, 1.5f * d, shadowColor)
        sharedTipBodyPaint.setShadowLayer(5.5f * d, 0f, 1.5f * d, shadowColor)

        val line0 = wrappedLines[0]
        val prefixW = sharedTipPrefixPaint.measureText(prefix)
        val line0BodyW = sharedTipBodyPaint.measureText(line0)
        val line0StartX = tipCx - (prefixW + line0BodyW) * 0.5f
        canvas.drawText(prefix, line0StartX, firstBaselineY, sharedTipPrefixPaint)
        canvas.drawText(line0, line0StartX + prefixW, firstBaselineY, sharedTipBodyPaint)

        for (idx in 1 until wrappedLines.size) {
            val line = wrappedLines[idx]
            val lineW = sharedTipBodyPaint.measureText(line)
            canvas.drawText(line, tipCx - lineW * 0.5f, firstBaselineY + idx * lineH, sharedTipBodyPaint)
        }
    } finally {
        sharedTipPrefixPaint.clearShadowLayer()
        sharedTipBodyPaint.clearShadowLayer()
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

private fun wrapBalancedGhostTipLines(
    prefix: String,
    body: String,
    prefixPaint: Paint,
    bodyPaint: Paint,
    maxWidth: Float
): List<String> {
    val clean = body.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return emptyList()

    val prefixW = prefixPaint.measureText(prefix)
    fun line0Width(s: String): Float = prefixW + bodyPaint.measureText(s)

    if (line0Width(clean) <= maxWidth) {
        return listOf(clean)
    }

    val words = clean.split(' ').filter { it.isNotEmpty() }
    if (words.size <= 1) return listOf(clean)

    // Prefer a balanced 2-line split with zero single-word orphan on the last line
    var bestTwoLines: List<String>? = null
    var bestTwoDiff = Float.MAX_VALUE
    for (splitIdx in 0 until words.lastIndex) {
        if (words.size >= 4 && splitIdx == words.lastIndex - 1) continue
        val l1 = words.subList(0, splitIdx + 1).joinToString(" ")
        val l2 = words.subList(splitIdx + 1, words.size).joinToString(" ")
        val w1 = line0Width(l1)
        val w2 = bodyPaint.measureText(l2)
        if (w1 <= maxWidth && w2 <= maxWidth) {
            val diff = abs(w1 - w2)
            if (diff < bestTwoDiff) {
                bestTwoDiff = diff
                bestTwoLines = listOf(l1, l2)
            }
        }
    }
    if (bestTwoLines != null) return bestTwoLines

    // Fallback to balanced 3-line split on narrow landscape widths
    if (words.size >= 3) {
        var bestThreeLines: List<String>? = null
        var bestThreeSpread = Float.MAX_VALUE
        for (i in 0 until words.size - 2) {
            val l1 = words.subList(0, i + 1).joinToString(" ")
            val w1 = line0Width(l1)
            if (w1 > maxWidth) break
            for (j in (i + 1) until words.lastIndex) {
                if (words.size >= 5 && j == words.lastIndex - 1) continue
                val l2 = words.subList(i + 1, j + 1).joinToString(" ")
                val l3 = words.subList(j + 1, words.size).joinToString(" ")
                val w2 = bodyPaint.measureText(l2)
                val w3 = bodyPaint.measureText(l3)
                if (w2 <= maxWidth && w3 <= maxWidth) {
                    val spread = maxOf(w1, w2, w3) - minOf(w1, w2, w3)
                    if (spread < bestThreeSpread) {
                        bestThreeSpread = spread
                        bestThreeLines = listOf(l1, l2, l3)
                    }
                }
            }
        }
        if (bestThreeLines != null) return bestThreeLines
    }

    // Greedy last-resort wrap up to 3 lines with ellipsis on overflow
    val lines = mutableListOf<String>()
    val current = StringBuilder()
    var wordIndex = 0
    while (wordIndex < words.size) {
        val word = words[wordIndex]
        val candidate = if (current.isEmpty()) word else "$current $word"
        val candidateW = if (lines.isEmpty()) line0Width(candidate) else bodyPaint.measureText(candidate)
        if (candidateW <= maxWidth || current.isEmpty()) {
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
            wordIndex++
        } else {
            lines.add(current.toString())
            current.setLength(0)
            if (lines.size == 2) {
                val remaining = words.subList(wordIndex, words.size).joinToString(" ")
                if (bodyPaint.measureText(remaining) <= maxWidth) {
                    lines.add(remaining)
                } else {
                    var trimmed = remaining
                    while (trimmed.length > 1 && bodyPaint.measureText("$trimmed…") > maxWidth) {
                        trimmed = trimmed.dropLast(1).trimEnd()
                    }
                    lines.add("$trimmed…")
                }
                return lines
            }
        }
    }
    if (current.isNotEmpty()) {
        lines.add(current.toString())
    }
    return lines
}
