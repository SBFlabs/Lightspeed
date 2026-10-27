package com.sbf.lightspeed.system.bot

import android.graphics.*
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveCanvasPalette
import kotlin.math.*

/**
 * High-Precision Aerospace Canvas Drawing Engine for the Cybernetic Half-Circle Trackpad,
 * 3-Segment Contoured Radial Wedge / Edge Strip Buttons, and Tactical Cursor (< 390 lines).
 */
object LightspeedBotTrackpadDraw {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    // Reusable cached paths and rects for zero-allocation 120Hz rendering
    private val cachedTrackpadPath = Path()
    private val cachedWedgePath = Path()
    private val cachedArcRect = RectF()
    private val cachedOuterRect = RectF()
    private val cachedInnerRect = RectF()
    private val cachedArrowPath = Path()
    private val cachedDiamondPath = Path()

    private fun getAngles(armCount: Int): List<Float> = when (armCount.coerceIn(1, 3)) {
        1 -> listOf(0f)
        2 -> listOf(-42f, 42f)
        else -> listOf(-56f, 0f, 56f)
    }

    fun getArmNodeCenter(
        armIdx: Int,
        armCount: Int,
        isLeft: Boolean,
        centerY: Float,
        radiusPx: Float,
        deployProgress: Float,
        wF: Float,
        density: Float,
        armReachPx: Float = 52f * density,
        orientation: String = "arc"
    ): PointF {
        if (orientation == "edge") {
            val edgeX = if (isLeft) 22f * density else wF - (22f * density)
            val segH = (radiusPx * 0.68f).coerceAtLeast(36f * density)
            val offsetIdx = (armIdx - 1).toFloat()
            return PointF(edgeX, centerY + (offsetIdx * segH))
        }

        val angles = getAngles(armCount)
        val angle = angles.getOrElse(armIdx.coerceIn(0, angles.size - 1)) { 0f }
        val rad = Math.toRadians(angle.toDouble())
        val midReach = radiusPx + (4.5f * density) + ((armReachPx - 4.5f * density) * 0.52f * deployProgress.coerceIn(0.1f, 1f))
        val x = if (isLeft) (midReach * cos(rad)).toFloat() else (wF - (midReach * cos(rad)).toFloat())
        val y = centerY + (midReach * sin(rad)).toFloat()
        return PointF(x, y)
    }

    fun hitTestArm(
        x: Float,
        y: Float,
        armCount: Int,
        isLeft: Boolean,
        centerY: Float,
        radiusPx: Float,
        deployProgress: Float,
        wF: Float,
        density: Float,
        armReachPx: Float = 52f * density,
        orientation: String = "arc"
    ): Int? {
        val count = armCount.coerceIn(1, 3)
        if (orientation == "edge") {
            val stripW = 46f * density
            val inStripX = if (isLeft) (x <= stripW) else (x >= wF - stripW)
            if (!inStripX) return null
            val segH = (radiusPx * 0.68f).coerceAtLeast(36f * density)
            for (i in 0 until count) {
                val cy = centerY + ((i - 1) * segH)
                if (abs(y - cy) <= segH * 0.5f) return i + 1
            }
            return null
        }

        val originX = if (isLeft) 0f else wF
        val dx = if (isLeft) (x - originX) else (originX - x)
        val dy = y - centerY
        val dist = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        // Require finger to be outside the trackpad cursor dome to hit macro arms
        val rInner = radiusPx + (2f * density)
        val rOuter = radiusPx + armReachPx + (14f * density)
        if (dist in rInner..rOuter && dx >= 0f) {
            val deg = Math.toDegrees(atan2(dy.toDouble(), dx.coerceAtLeast(1f).toDouble())).toFloat()
            return when {
                deg < -28f -> 1
                deg <= 28f -> 2
                else -> 3
            }
        }
        return null
    }

    private fun buildTrackpadPath(
        style: String,
        isLeft: Boolean,
        originX: Float,
        centerY: Float,
        currentRadius: Float,
        dir: Float
    ): Path {
        val path = cachedTrackpadPath
        path.rewind()
        when (style) {
            "angular" -> {
                path.moveTo(originX, centerY - currentRadius)
                path.lineTo(originX + dir * currentRadius * 0.68f, centerY - currentRadius * 0.76f)
                path.lineTo(originX + dir * currentRadius, centerY)
                path.lineTo(originX + dir * currentRadius * 0.68f, centerY + currentRadius * 0.76f)
                path.lineTo(originX, centerY + currentRadius)
                path.close()
            }
            "delta" -> {
                path.moveTo(originX, centerY - currentRadius * 1.02f)
                path.quadTo(originX + dir * currentRadius * 1.18f, centerY, originX, centerY + currentRadius * 1.02f)
                path.close()
            }
            "shield" -> {
                path.moveTo(originX, centerY - currentRadius)
                path.lineTo(originX + dir * currentRadius * 0.84f, centerY - currentRadius * 0.64f)
                path.quadTo(originX + dir * currentRadius * 1.08f, centerY, originX + dir * currentRadius * 0.84f, centerY + currentRadius * 0.64f)
                path.lineTo(originX, centerY + currentRadius)
                path.close()
            }
            "soft_wing" -> {
                path.moveTo(originX, centerY - currentRadius * 1.04f)
                path.cubicTo(
                    originX + dir * currentRadius * 0.96f, centerY - currentRadius * 0.82f,
                    originX + dir * currentRadius * 1.06f, centerY + currentRadius * 0.56f,
                    originX, centerY + currentRadius * 0.98f
                )
                path.close()
            }
            else -> {
                cachedArcRect.set(
                    originX - currentRadius,
                    centerY - currentRadius,
                    originX + currentRadius,
                    centerY + currentRadius
                )
                path.moveTo(originX, centerY - currentRadius)
                if (isLeft) {
                    path.arcTo(cachedArcRect, -90f, 180f, false)
                } else {
                    path.arcTo(cachedArcRect, 90f, 180f, false)
                }
                path.lineTo(originX, centerY + currentRadius)
                path.close()
            }
        }
        return path
    }

    private fun buildWedgeSegmentPath(
        isLeft: Boolean,
        originX: Float,
        centerY: Float,
        rInner: Float,
        rOuter: Float,
        startDeg: Float,
        sweepDeg: Float,
        style: String
    ): Path {
        val path = cachedWedgePath
        path.rewind()
        val dir = if (isLeft) 1f else -1f
        if (style == "angular" || style == "delta") {
            val midDeg = startDeg + sweepDeg * 0.5f
            val r0 = Math.toRadians(startDeg.toDouble())
            val rMid = Math.toRadians(midDeg.toDouble())
            val r1 = Math.toRadians((startDeg + sweepDeg).toDouble())
            path.moveTo(originX + dir * (rInner * cos(r0)).toFloat(), centerY + (rInner * sin(r0)).toFloat())
            path.lineTo(originX + dir * (rOuter * cos(r0)).toFloat(), centerY + (rOuter * sin(r0)).toFloat())
            val peakBoost = if (style == "delta") 1.06f else 1.02f
            path.lineTo(originX + dir * (rOuter * peakBoost * cos(rMid)).toFloat(), centerY + (rOuter * peakBoost * sin(rMid)).toFloat())
            path.lineTo(originX + dir * (rOuter * cos(r1)).toFloat(), centerY + (rOuter * sin(r1)).toFloat())
            path.lineTo(originX + dir * (rInner * cos(r1)).toFloat(), centerY + (rInner * sin(r1)).toFloat())
            path.lineTo(originX + dir * (rInner * cos(rMid)).toFloat(), centerY + (rInner * sin(rMid)).toFloat())
            path.close()
            return path
        }

        cachedOuterRect.set(originX - rOuter, centerY - rOuter, originX + rOuter, centerY + rOuter)
        cachedInnerRect.set(originX - rInner, centerY - rInner, originX + rInner, centerY + rInner)
        if (isLeft) {
            path.arcTo(cachedOuterRect, startDeg, sweepDeg)
            path.arcTo(cachedInnerRect, startDeg + sweepDeg, -sweepDeg)
        } else {
            val mirStart = 180f - startDeg
            path.arcTo(cachedOuterRect, mirStart, -sweepDeg)
            path.arcTo(cachedInnerRect, mirStart - sweepDeg, sweepDeg)
        }
        path.close()
        return path
    }

    fun drawHalfCircleTrackpad(
        canvas: Canvas,
        isLeft: Boolean,
        centerY: Float,
        radiusPx: Float,
        deployProgress: Float,
        thumbX: Float,
        thumbY: Float,
        wF: Float,
        theme: DeckThemeSnapshot,
        density: Float,
        alphaFactor: Float = 1f,
        style: String = "classic_arc",
        orientation: String = "arc"
    ) {
        val currentRadius = radiusPx * deployProgress.coerceIn(0.05f, 1f)
        val dir = if (isLeft) 1f else -1f
        val edgeStripOffset = if (orientation == "edge") 44f * density else 0f
        val originX = if (isLeft) edgeStripOffset else (wF - edgeStripOffset)
        val clampedAlpha = alphaFactor.coerceIn(0.05f, 1f)
        val palette = theme.resolveCanvasPalette()
        val innerCol = palette.panelInnerArgb
        val outerCol = palette.panelOuterArgb
        val borderCol = palette.borderArgb
        val tintCol = palette.primaryAccentArgb
        val secCol = palette.secondaryAccentArgb

        val trackpadPath = buildTrackpadPath(style, isLeft, originX, centerY, currentRadius, dir)

        // 1. 12-Theme Glass Radial Gradient Dome Body
        bgPaint.style = Paint.Style.FILL
        bgPaint.shader = RadialGradient(
            originX + dir * (currentRadius * 0.35f),
            centerY,
            currentRadius * 1.15f,
            intArrayOf(
                Color.argb((Color.alpha(innerCol) * clampedAlpha).toInt().coerceIn(40, 250), Color.red(innerCol), Color.green(innerCol), Color.blue(innerCol)),
                Color.argb((Color.alpha(outerCol) * clampedAlpha).toInt().coerceIn(50, 252), Color.red(outerCol), Color.green(outerCol), Color.blue(outerCol))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trackpadPath, bgPaint)
        bgPaint.shader = null

        // 2. Outer Dual-Tone Theme Glow Seam around Trackpad Dome
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = 6.0f * density
        glowPaint.color = secCol
        glowPaint.alpha = ((85 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(trackpadPath, glowPaint)

        // 3. Crisp Bright Inner & Outer Bevel Rim
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2.2f * density
        strokePaint.color = borderCol
        strokePaint.alpha = ((245 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawPath(trackpadPath, strokePaint)

        // 4. Center Collimator Crosshair (+) matching mockups
        val crossCX = if (abs(thumbX - originX) > 8f * density) thumbX else (originX + dir * (currentRadius * 0.46f))
        val crossCY = thumbY
        val arm = 8.5f * density
        val gap = 2.8f * density
        strokePaint.strokeWidth = 1.4f * density
        strokePaint.color = Color.WHITE
        strokePaint.alpha = ((200 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawLine(crossCX - arm, crossCY, crossCX - gap, crossCY, strokePaint)
        canvas.drawLine(crossCX + gap, crossCY, crossCX + arm, crossCY, strokePaint)
        canvas.drawLine(crossCX, crossCY - arm, crossCX, crossCY - gap, strokePaint)
        canvas.drawLine(crossCX, crossCY + gap, crossCX, crossCY + arm, strokePaint)

        nodePaint.style = Paint.Style.FILL
        nodePaint.color = tintCol
        nodePaint.alpha = ((255 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawCircle(crossCX, crossCY, 2.0f * density, nodePaint)
    }

    fun drawRobotArms(
        canvas: Canvas,
        isLeft: Boolean,
        centerY: Float,
        radiusPx: Float,
        deployProgress: Float,
        armCount: Int,
        hoveredArm: Int?,
        activeBotIds: List<Int>,
        hasStepsFlags: List<Boolean>,
        isRecording: Boolean,
        recordingBotId: Int,
        wF: Float,
        theme: DeckThemeSnapshot,
        density: Float,
        armReachPx: Float = 52f * density,
        alphaFactor: Float = 1f,
        style: String = "classic_arc",
        orientation: String = "arc"
    ) {
        if (deployProgress < 0.2f) return
        val clampedAlpha = alphaFactor.coerceIn(0.05f, 1f)
        val count = armCount.coerceIn(1, 3)
        val dir = if (isLeft) 1f else -1f
        val originX = if (isLeft) 0f else wF
        val palette = theme.resolveCanvasPalette()
        val innerCol = palette.panelInnerArgb
        val outerCol = palette.panelOuterArgb
        val panelCol = innerCol
        val borderCol = palette.borderArgb
        val tintCol = palette.primaryAccentArgb
        val secCol = palette.secondaryAccentArgb

        // 1. Edge Strip Orientation (3 Contiguous Beveled Segments along screen edge)
        if (orientation == "edge") {
            val stripW = 42f * density
            val segH = (radiusPx * 0.68f).coerceAtLeast(36f * density)
            val left = if (isLeft) 2f * density else (wF - stripW)
            val right = if (isLeft) stripW else (wF - 2f * density)

            for (i in 0 until count) {
                val armIndex = i + 1
                val botId = activeBotIds.getOrElse(i) { armIndex }
                val hasSteps = hasStepsFlags.getOrElse(i) { false }
                val isThisRec = isRecording && recordingBotId == botId
                val isHovered = (hoveredArm == armIndex)
                val cy = centerY + ((i - 1) * segH)
                val segRect = RectF(left, cy - segH * 0.47f, right, cy + segH * 0.47f)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = if (isHovered) tintCol else panelCol
                bgPaint.alpha = ((if (isHovered) 165 else 235) * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(segRect, 9f * density, 9f * density, bgPaint)

                val seamColor = when {
                    isThisRec -> 0xFFFF3333.toInt()
                    isHovered || hasSteps -> tintCol
                    else -> borderCol
                }
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = (if (isHovered || hasSteps) 2.0f else 1.4f) * density
                strokePaint.color = seamColor
                strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(segRect, 9f * density, 9f * density, strokePaint)

                drawButtonGlyphAndLabel(canvas, segRect.centerX(), segRect.centerY(), armIndex, isThisRec, isHovered, hasSteps, density, clampedAlpha)
            }
            return
        }

        val rInner = radiusPx + (5f * density)
        val rOuter = radiusPx + (armReachPx * deployProgress.coerceIn(0.2f, 1f))

        // 2. Outer Gear Ring Backing when style == "gear_tooth" or "shield"
        if (style == "gear_tooth" || style == "shield") {
            val gearOuterR = rOuter + (9f * density)
            val gearRect = RectF(originX - gearOuterR, centerY - gearOuterR, originX + gearOuterR, centerY + gearOuterR)
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 2.0f * density
            strokePaint.color = borderCol
            strokePaint.alpha = (180 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(gearRect, -88f, 176f, false, strokePaint)
            else canvas.drawArc(gearRect, 92f, 176f, false, strokePaint)

            if (style == "gear_tooth") {
                strokePaint.strokeWidth = 5.5f * density
                for (t in -75..75 step 15) {
                    val rad = Math.toRadians(t.toDouble())
                    val x1 = originX + dir * (rOuter * cos(rad)).toFloat()
                    val y1 = centerY + (rOuter * sin(rad)).toFloat()
                    val x2 = originX + dir * ((gearOuterR + 4f * density) * cos(rad)).toFloat()
                    val y2 = centerY + ((gearOuterR + 4f * density) * sin(rad)).toFloat()
                    canvas.drawLine(x1, y1, x2, y2, strokePaint)
                }
            }
        }

        // 3. Minimal Outer Orbital Arc Track when style == "minimal_arms"
        if (style == "minimal_arms") {
            val midR = (rInner + rOuter) * 0.5f
            val arcRect = RectF(originX - midR, centerY - midR, originX + midR, centerY + midR)
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 10f * density
            glowPaint.color = tintCol
            glowPaint.alpha = (45 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(arcRect, -82f, 164f, false, glowPaint)
            else canvas.drawArc(arcRect, 98f, 164f, false, glowPaint)

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.6f * density
            strokePaint.color = borderCol
            strokePaint.alpha = (195 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(arcRect, -82f, 164f, false, strokePaint)
            else canvas.drawArc(arcRect, 98f, 164f, false, strokePaint)
        }

        // 4. Draw the 3 Contoured Radial Wedge / Capsule Segments (B1, B2, B3)
        val wedgeSpans = listOf(-84f to 50f, -25f to 50f, 34f to 50f)
        for (i in 0 until count) {
            val armIndex = i + 1
            val botId = activeBotIds.getOrElse(i) { armIndex }
            val hasSteps = hasStepsFlags.getOrElse(i) { false }
            val isThisRec = isRecording && recordingBotId == botId
            val isHovered = (hoveredArm == armIndex)
            val nodeCenter = getArmNodeCenter(i, count, isLeft, centerY, radiusPx, deployProgress, wF, density, armReachPx, orientation)

            val seamColor = when {
                isThisRec -> 0xFFFF3333.toInt()
                isHovered -> Color.WHITE
                hasSteps -> tintCol
                else -> borderCol
            }

            if (style == "minimal_arms" || style == "floating") {
                val podR = (if (style == "floating") 21f else 19f) * density
                glowPaint.style = Paint.Style.FILL
                glowPaint.color = seamColor
                glowPaint.alpha = ((if (isHovered || isThisRec) 95 else 45) * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawCircle(nodeCenter.x, nodeCenter.y, podR * 1.25f, glowPaint)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = panelCol
                bgPaint.alpha = (238 * clampedAlpha).toInt().coerceIn(0, 255)
                if (style == "floating") {
                    val rRect = RectF(nodeCenter.x - podR * 1.15f, nodeCenter.y - podR * 0.85f, nodeCenter.x + podR * 1.15f, nodeCenter.y + podR * 0.85f)
                    canvas.drawRoundRect(rRect, 11f * density, 11f * density, bgPaint)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 2.0f * density
                    strokePaint.color = seamColor
                    strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(rRect, 11f * density, 11f * density, strokePaint)
                } else {
                    canvas.drawCircle(nodeCenter.x, nodeCenter.y, podR, bgPaint)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 2.0f * density
                    strokePaint.color = seamColor
                    strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawCircle(nodeCenter.x, nodeCenter.y, podR, strokePaint)
                }
            } else {
                val (startDeg, sweepDeg) = wedgeSpans.getOrElse(i) { -25f to 50f }
                val wedgePath = buildWedgeSegmentPath(isLeft, originX, centerY, rInner, rOuter, startDeg, sweepDeg, style)

                // Wedge 12-theme glass gradient fill
                bgPaint.style = Paint.Style.FILL
                bgPaint.shader = RadialGradient(
                    nodeCenter.x, nodeCenter.y, (rOuter - rInner).coerceAtLeast(16f),
                    intArrayOf(
                        if (isHovered) Color.argb((210 * clampedAlpha).toInt(), Color.red(tintCol), Color.green(tintCol), Color.blue(tintCol))
                        else Color.argb((Color.alpha(innerCol) * clampedAlpha).toInt().coerceIn(50, 250), Color.red(innerCol), Color.green(innerCol), Color.blue(innerCol)),
                        if (hasSteps) Color.argb((220 * clampedAlpha).toInt(), Color.red(secCol) / 2 + Color.red(outerCol) / 2, Color.green(secCol) / 2 + Color.green(outerCol) / 2, Color.blue(secCol) / 2 + Color.blue(outerCol) / 2)
                        else Color.argb((Color.alpha(outerCol) * clampedAlpha).toInt().coerceIn(60, 252), Color.red(outerCol), Color.green(outerCol), Color.blue(outerCol))
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(wedgePath, bgPaint)
                bgPaint.shader = null

                // Outer Neon Glow Seam
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 4.8f * density
                glowPaint.color = seamColor
                glowPaint.alpha = ((if (isHovered || hasSteps || isThisRec) 105 else 60) * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawPath(wedgePath, glowPaint)

                // Crisp Bright Seam Outline
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = (if (isHovered || hasSteps) 2.1f else 1.6f) * density
                strokePaint.color = seamColor
                strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawPath(wedgePath, strokePaint)
            }

            drawButtonGlyphAndLabel(canvas, nodeCenter.x, nodeCenter.y, armIndex, isThisRec, isHovered, hasSteps, density, clampedAlpha)
        }
    }

    private fun drawButtonGlyphAndLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        armIndex: Int,
        isRecording: Boolean,
        isHovered: Boolean,
        hasSteps: Boolean,
        density: Float,
        clampedAlpha: Float
    ) {
        val glyph = when {
            isRecording -> "●"
            armIndex == 1 -> "↶"
            armIndex == 2 -> "⌂"
            else -> "❐"
        }
        val label = if (isRecording) "REC" else "B$armIndex"

        textPaint.color = when {
            isRecording -> 0xFFFF4444.toInt()
            isHovered || hasSteps -> Color.WHITE
            else -> 0xFFD0DCFF.toInt()
        }
        textPaint.alpha = (250 * clampedAlpha).toInt().coerceIn(0, 255)
        textPaint.textSize = 11.5f * density
        canvas.drawText(glyph, cx, cy - (1.5f * density), textPaint)

        textPaint.textSize = 8.2f * density
        textPaint.alpha = (215 * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawText(label, cx, cy + (9.5f * density), textPaint)
    }

    fun drawTacticalCursor(
        canvas: Canvas,
        cursorX: Float,
        cursorY: Float,
        isLooping: Boolean,
        loopCount: Int,
        theme: DeckThemeSnapshot,
        density: Float,
        cursorShape: String = "crosshair"
    ) {
        if (cursorX.isNaN() || cursorY.isNaN()) return
        val palette = theme.resolveCanvasPalette()
        val accent = palette.borderArgb
        val tint = palette.primaryAccentArgb
        val r = 22f * density

        when (cursorShape) {
            "pointer" -> {
                // Sleek Glowing Neon Arrow Pointer (tip at cursorX, cursorY)
                val arrowPath = cachedArrowPath
                arrowPath.rewind()
                arrowPath.moveTo(cursorX, cursorY)
                arrowPath.lineTo(cursorX, cursorY + 24f * density)
                arrowPath.lineTo(cursorX + 6.5f * density, cursorY + 18.5f * density)
                arrowPath.lineTo(cursorX + 11.5f * density, cursorY + 27.5f * density)
                arrowPath.lineTo(cursorX + 15.5f * density, cursorY + 25.5f * density)
                arrowPath.lineTo(cursorX + 10.5f * density, cursorY + 16.5f * density)
                arrowPath.lineTo(cursorX + 18f * density, cursorY + 16.5f * density)
                arrowPath.close()
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 5f * density
                glowPaint.color = tint
                glowPaint.alpha = 110
                canvas.drawPath(arrowPath, glowPaint)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = theme.panelColorArgb.toInt()
                bgPaint.alpha = 235
                canvas.drawPath(arrowPath, bgPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.color = Color.WHITE
                strokePaint.alpha = 250
                canvas.drawPath(arrowPath, strokePaint)
            }
            "ring" -> {
                // Dual Glowing Laser Ring + Center Dot
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 6f * density
                glowPaint.color = tint
                glowPaint.alpha = 90
                canvas.drawCircle(cursorX, cursorY, 16f * density, glowPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 2f * density
                strokePaint.color = accent
                strokePaint.alpha = 245
                canvas.drawCircle(cursorX, cursorY, 16f * density, strokePaint)

                strokePaint.strokeWidth = 1.2f * density
                strokePaint.color = Color.WHITE
                strokePaint.alpha = 180
                canvas.drawCircle(cursorX, cursorY, 8f * density, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 3f * density, nodePaint)
            }
            "diamond" -> {
                // Faceted Tactical Diamond Collimator
                val d = 20f * density
                val dmPath = cachedDiamondPath
                dmPath.rewind()
                dmPath.moveTo(cursorX, cursorY - d)
                dmPath.lineTo(cursorX + d, cursorY)
                dmPath.lineTo(cursorX, cursorY + d)
                dmPath.lineTo(cursorX - d, cursorY)
                dmPath.close()
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 5f * density
                glowPaint.color = tint
                glowPaint.alpha = 90
                canvas.drawPath(dmPath, glowPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.color = accent
                strokePaint.alpha = 245
                canvas.drawPath(dmPath, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = Color.WHITE
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 2.8f * density, nodePaint)
            }
            "dot" -> {
                // Minimal Precision Neon Dot with Halo
                glowPaint.style = Paint.Style.FILL
                glowPaint.color = tint
                glowPaint.alpha = 95
                canvas.drawCircle(cursorX, cursorY, 12f * density, glowPaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 240
                canvas.drawCircle(cursorX, cursorY, 6f * density, nodePaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.6f * density
                strokePaint.color = Color.WHITE
                strokePaint.alpha = 250
                canvas.drawCircle(cursorX, cursorY, 6f * density, strokePaint)
            }
            else -> {
                // Default "crosshair" Tactical Reticle
                strokePaint.style = Paint.Style.STROKE
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.alpha = 245

                val bracketGap = 8f * density
                val bracketRect = RectF(cursorX - r, cursorY - r, cursorX + r, cursorY + r)
                canvas.drawArc(bracketRect, 25f, 40f, false, strokePaint)
                canvas.drawArc(bracketRect, 115f, 40f, false, strokePaint)
                canvas.drawArc(bracketRect, 205f, 40f, false, strokePaint)
                canvas.drawArc(bracketRect, 295f, 40f, false, strokePaint)

                canvas.drawLine(cursorX - (r * 1.35f), cursorY, cursorX - bracketGap, cursorY, strokePaint)
                canvas.drawLine(cursorX + bracketGap, cursorY, cursorX + (r * 1.35f), cursorY, strokePaint)
                canvas.drawLine(cursorX, cursorY - (r * 1.35f), cursorX, cursorY - bracketGap, strokePaint)
                canvas.drawLine(cursorX, cursorY + bracketGap, cursorX, cursorY + (r * 1.35f), strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 2.5f * density, nodePaint)
            }
        }

        if (isLooping) {
            val badgeX = cursorX + (22f * density)
            val badgeY = cursorY - (22f * density)
            nodePaint.color = 0xEEFF3333.toInt()
            canvas.drawCircle(badgeX, badgeY, 13f * density, nodePaint)

            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 1.2f * density
            canvas.drawCircle(badgeX, badgeY, 13f * density, strokePaint)

            textPaint.textSize = 9f * density
            textPaint.color = Color.WHITE
            canvas.drawText("$loopCount", badgeX, badgeY + (3.2f * density), textPaint)
        }
    }
}
