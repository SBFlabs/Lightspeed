package com.sbf.lightspeed.system.bot

import android.graphics.*
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveCanvasPalette
import kotlin.math.*

/**
 * High-Precision Aerospace Canvas Drawing Engine for the Cybernetic Half-Circle Trackpad
 * and 3-Segment Contoured Radial Wedge / Edge Strip Buttons (< 535 lines).
 * Tactical Cursor rendering is modularized into [LightspeedBotCursorDraw].
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

    // Reusable cached paths, rects, and points for zero-allocation 120Hz rendering
    private val cachedTrackpadPath = Path()
    private val cachedWedgePath = Path()
    private val cachedArcRect = RectF()
    private val cachedOuterRect = RectF()
    private val cachedInnerRect = RectF()
    private val cachedSegRect = RectF()
    private val cachedGearRect = RectF()
    private val cachedNodePoint = PointF()

    private val ANGLES_1 = listOf(0f)
    private val ANGLES_2 = listOf(-42f, 42f)
    private val ANGLES_3 = listOf(-56f, 0f, 56f)
    private val WEDGE_SPANS = listOf(-84f to 50f, -25f to 50f, 34f to 50f)

    private fun getAngles(armCount: Int): List<Float> = when (armCount.coerceIn(1, 3)) {
        1 -> ANGLES_1
        2 -> ANGLES_2
        else -> ANGLES_3
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
            cachedNodePoint.set(edgeX, centerY + (offsetIdx * segH))
            return cachedNodePoint
        }

        val angles = getAngles(armCount)
        val angle = angles.getOrElse(armIdx.coerceIn(0, angles.size - 1)) { 0f }
        val rad = Math.toRadians(angle.toDouble())
        val midReach = radiusPx + (4.5f * density) + ((armReachPx - 4.5f * density) * 0.52f * deployProgress.coerceIn(0.1f, 1f))
        val x = if (isLeft) (midReach * cos(rad)).toFloat() else (wF - (midReach * cos(rad)).toFloat())
        val y = centerY + (midReach * sin(rad)).toFloat()
        cachedNodePoint.set(x, y)
        return cachedNodePoint
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
        orientation: String = "arc",
        context: android.content.Context? = null,
        colorMode: String = LightspeedBotContract.COLOR_MODE_HYBRID_FUSION,
        isHoldActive: Boolean = false
    ) {
        val currentRadius = radiusPx * deployProgress.coerceIn(0.05f, 1f)
        val dir = if (isLeft) 1f else -1f
        val edgeStripOffset = if (orientation == "edge") 44f * density else 0f
        val originX = if (isLeft) edgeStripOffset else (wF - edgeStripOffset)
        val clampedAlpha = alphaFactor.coerceIn(0.05f, 1f)
        val palette = theme.resolveCanvasPalette(context, colorMode)
        val innerCol = palette.panelInnerArgb
        val outerCol = palette.panelOuterArgb
        val borderCol = palette.borderArgb
        val tintCol = palette.primaryAccentArgb
        val secCol = palette.secondaryAccentArgb

        val trackpadPath = buildTrackpadPath(style, isLeft, originX, centerY, currentRadius, dir)

        // 1. Optical Glass Radial Gradient Dome Body
        bgPaint.style = Paint.Style.FILL
        bgPaint.shader = RadialGradient(
            originX + dir * (currentRadius * 0.35f),
            centerY,
            currentRadius * 1.15f,
            intArrayOf(
                Color.argb((Color.alpha(innerCol) * clampedAlpha).toInt().coerceIn(45, 250), Color.red(innerCol), Color.green(innerCol), Color.blue(innerCol)),
                Color.argb((Color.alpha(outerCol) * clampedAlpha).toInt().coerceIn(55, 252), Color.red(outerCol), Color.green(outerCol), Color.blue(outerCol))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trackpadPath, bgPaint)
        bgPaint.shader = null

        // 2. Outer Dual-Tone Neon Glow Seam around Trackpad Dome
        glowPaint.style = Paint.Style.STROKE
        glowPaint.strokeWidth = 6.2f * density
        glowPaint.shader = LinearGradient(
            originX, centerY - currentRadius,
            originX, centerY + currentRadius,
            intArrayOf(
                Color.argb(((95 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255), Color.red(tintCol), Color.green(tintCol), Color.blue(tintCol)),
                Color.argb(((95 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255), Color.red(secCol), Color.green(secCol), Color.blue(secCol))
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trackpadPath, glowPaint)
        glowPaint.shader = null

        // 3. Crisp Dual-Tone Bevel Rim
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2.2f * density
        strokePaint.shader = LinearGradient(
            originX, centerY - currentRadius,
            originX, centerY + currentRadius,
            intArrayOf(
                Color.argb(((248 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255), Color.red(tintCol), Color.green(tintCol), Color.blue(tintCol)),
                Color.argb(((248 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255), Color.red(borderCol), Color.green(borderCol), Color.blue(borderCol)),
                Color.argb(((248 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255), Color.red(secCol), Color.green(secCol), Color.blue(secCol))
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(trackpadPath, strokePaint)
        strokePaint.shader = null

        // 3b. Style-specific inner specular highlight ("liquid_glass" & "shield")
        if (style == "liquid_glass" || style == "shield") {
            val innerR = currentRadius * 0.86f
            cachedArcRect.set(originX - innerR, centerY - innerR, originX + innerR, centerY + innerR)
            strokePaint.strokeWidth = (if (style == "liquid_glass") 2.0f else 1.2f) * density
            strokePaint.color = if (style == "liquid_glass") Color.WHITE else tintCol
            strokePaint.alpha = ((if (style == "liquid_glass") 115 else 95) * deployProgress * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(cachedArcRect, -72f, if (style == "liquid_glass") 75f else 144f, false, strokePaint)
            else canvas.drawArc(cachedArcRect, 108f, if (style == "liquid_glass") 75f else 144f, false, strokePaint)
        }

        // 4. Center Collimator Crosshair (+) matching mockups
        val crossCX = if (abs(thumbX - originX) > 8f * density) thumbX else (originX + dir * (currentRadius * 0.46f))
        val crossCY = thumbY
        val arm = 8.5f * density
        val gap = 2.8f * density
        strokePaint.strokeWidth = 1.4f * density
        strokePaint.color = Color.WHITE
        strokePaint.alpha = ((210 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawLine(crossCX - arm, crossCY, crossCX - gap, crossCY, strokePaint)
        canvas.drawLine(crossCX + gap, crossCY, crossCX + arm, crossCY, strokePaint)
        canvas.drawLine(crossCX, crossCY - arm, crossCX, crossCY - gap, strokePaint)
        canvas.drawLine(crossCX, crossCY + gap, crossCX, crossCY + arm, strokePaint)

        nodePaint.style = Paint.Style.FILL
        nodePaint.color = tintCol
        nodePaint.alpha = ((255 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
        canvas.drawCircle(crossCX, crossCY, 2.1f * density, nodePaint)

        if (isHoldActive) {
            val holdR = 11.5f * density
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 3.2f * density
            glowPaint.color = tintCol
            glowPaint.alpha = ((220 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(crossCX, crossCY, holdR, glowPaint)

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.6f * density
            strokePaint.color = Color.WHITE
            strokePaint.alpha = ((255 * deployProgress) * clampedAlpha).toInt().coerceIn(0, 255)
            canvas.drawCircle(crossCX, crossCY, holdR, strokePaint)
        }
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
        orientation: String = "arc",
        context: android.content.Context? = null,
        colorMode: String = LightspeedBotContract.COLOR_MODE_HYBRID_FUSION
    ) {
        if (deployProgress < 0.2f) return
        val clampedAlpha = alphaFactor.coerceIn(0.05f, 1f)
        val count = armCount.coerceIn(1, 3)
        val dir = if (isLeft) 1f else -1f
        val originX = if (isLeft) 0f else wF
        val palette = theme.resolveCanvasPalette(context, colorMode)
        val innerCol = palette.panelInnerArgb
        val outerCol = palette.panelOuterArgb
        val panelCol = innerCol
        val borderCol = palette.borderArgb
        val tintCol = palette.primaryAccentArgb
        val secCol = palette.secondaryAccentArgb

        // 1. Edge Strip Orientation (1:1 Mockup Contoured Vertical Dock + 3 Segmented Bays)
        if (orientation == "edge") {
            val stripW = 42f * density
            val segH = (radiusPx * 0.68f).coerceAtLeast(36f * density)
            val left = if (isLeft) 2f * density else (wF - stripW)
            val right = if (isLeft) stripW else (wF - 2f * density)
            val dockTop = centerY - segH * 1.5f
            val dockBottom = centerY + segH * 1.5f
            cachedOuterRect.set(left, dockTop, right, dockBottom)

            // Contoured outer housing shell
            bgPaint.style = Paint.Style.FILL
            bgPaint.shader = LinearGradient(
                left, dockTop, right, dockBottom,
                intArrayOf(
                    Color.argb((Color.alpha(innerCol) * clampedAlpha).toInt().coerceIn(60, 250), Color.red(innerCol), Color.green(innerCol), Color.blue(innerCol)),
                    Color.argb((Color.alpha(outerCol) * clampedAlpha).toInt().coerceIn(70, 252), Color.red(outerCol), Color.green(outerCol), Color.blue(outerCol))
                ),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(cachedOuterRect, 14f * density, 14f * density, bgPaint)
            bgPaint.shader = null

            // Outer housing neon glow + crisp border
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 4.5f * density
            glowPaint.color = tintCol
            glowPaint.alpha = (75 * clampedAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(cachedOuterRect, 14f * density, 14f * density, glowPaint)

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.8f * density
            strokePaint.color = borderCol
            strokePaint.alpha = (235 * clampedAlpha).toInt().coerceIn(0, 255)
            canvas.drawRoundRect(cachedOuterRect, 14f * density, 14f * density, strokePaint)

            for (i in 0 until count) {
                val armIndex = i + 1
                val botId = activeBotIds.getOrElse(i) { armIndex }
                val hasSteps = hasStepsFlags.getOrElse(i) { false }
                val isThisRec = isRecording && recordingBotId == botId
                val isHovered = (hoveredArm == armIndex)
                val cy = centerY + ((i - 1) * segH)
                cachedSegRect.set(left + 2.5f * density, cy - segH * 0.45f, right - 2.5f * density, cy + segH * 0.45f)

                if (isHovered || hasSteps || isThisRec) {
                    bgPaint.style = Paint.Style.FILL
                    bgPaint.color = when {
                        isThisRec -> 0xFFFF3333.toInt()
                        isHovered -> tintCol
                        else -> secCol
                    }
                    bgPaint.alpha = ((if (isHovered) 155 else 65) * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(cachedSegRect, 10f * density, 10f * density, bgPaint)
                }

                // Glowing horizontal divider seam between buttons
                if (i > 0) {
                    val divY = cy - segH * 0.5f
                    strokePaint.strokeWidth = 1.2f * density
                    strokePaint.color = tintCol
                    strokePaint.alpha = (150 * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawLine(left + 5f * density, divY, right - 5f * density, divY, strokePaint)
                }

                drawButtonGlyphAndLabel(canvas, cachedSegRect.centerX(), cachedSegRect.centerY(), armIndex, botId, isThisRec, isHovered, hasSteps, tintCol, density, clampedAlpha)
            }
            return
        }

        val rInner = radiusPx + (5f * density)
        val rOuter = radiusPx + (armReachPx * deployProgress.coerceIn(0.2f, 1f))

        // 1b. Outer Orbital Guide Horizon Arc (Signature mockup horizon framing the 3 arms)
        val horizonR = rOuter + (6.5f * density)
        cachedGearRect.set(originX - horizonR, centerY - horizonR, originX + horizonR, centerY + horizonR)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.1f * density
        strokePaint.color = tintCol
        strokePaint.alpha = (95 * clampedAlpha).toInt().coerceIn(0, 255)
        if (isLeft) canvas.drawArc(cachedGearRect, -84f, 168f, false, strokePaint)
        else canvas.drawArc(cachedGearRect, 96f, 168f, false, strokePaint)

        // 2. Outer Gear Ring Backing when style == "gear_tooth" or "shield"
        if (style == "gear_tooth" || style == "shield") {
            val gearOuterR = rOuter + (9f * density)
            cachedGearRect.set(originX - gearOuterR, centerY - gearOuterR, originX + gearOuterR, centerY + gearOuterR)
            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 2.0f * density
            strokePaint.color = borderCol
            strokePaint.alpha = (185 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(cachedGearRect, -88f, 176f, false, strokePaint)
            else canvas.drawArc(cachedGearRect, 92f, 176f, false, strokePaint)

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

        // 3. Minimal / Floating Outer Orbital Arc Track
        if (style == "minimal_arms" || style == "floating") {
            val midR = (rInner + rOuter) * 0.5f
            cachedArcRect.set(originX - midR, centerY - midR, originX + midR, centerY + midR)
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 9f * density
            glowPaint.color = tintCol
            glowPaint.alpha = (48 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(cachedArcRect, -82f, 164f, false, glowPaint)
            else canvas.drawArc(cachedArcRect, 98f, 164f, false, glowPaint)

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.6f * density
            strokePaint.color = borderCol
            strokePaint.alpha = (195 * clampedAlpha).toInt().coerceIn(0, 255)
            if (isLeft) canvas.drawArc(cachedArcRect, -82f, 164f, false, strokePaint)
            else canvas.drawArc(cachedArcRect, 98f, 164f, false, strokePaint)
        }

        // 4. Draw the 3 Contoured Radial Wedge / Capsule Segments (B1, B2, B3)
        for (i in 0 until count) {
            val armIndex = i + 1
            val botId = activeBotIds.getOrElse(i) { armIndex }
            val hasSteps = hasStepsFlags.getOrElse(i) { false }
            val isThisRec = isRecording && recordingBotId == botId
            val isHovered = (hoveredArm == armIndex)
            val nodeCenter = getArmNodeCenter(i, count, isLeft, centerY, radiusPx, deployProgress, wF, density, armReachPx, orientation)
            val nodeCenterX = nodeCenter.x
            val nodeCenterY = nodeCenter.y

            val seamColor = when {
                isThisRec -> 0xFFFF3333.toInt()
                isHovered -> Color.WHITE
                hasSteps -> tintCol
                else -> borderCol
            }

            if (style == "minimal_arms" || style == "floating") {
                val podR = (if (style == "floating") 21.5f else 19.5f) * density
                glowPaint.style = Paint.Style.FILL
                glowPaint.color = seamColor
                glowPaint.alpha = ((if (isHovered || isThisRec) 100 else 50) * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawCircle(nodeCenterX, nodeCenterY, podR * 1.25f, glowPaint)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = panelCol
                bgPaint.alpha = (238 * clampedAlpha).toInt().coerceIn(0, 255)
                if (style == "floating") {
                    cachedSegRect.set(nodeCenterX - podR * 1.15f, nodeCenterY - podR * 0.88f, nodeCenterX + podR * 1.15f, nodeCenterY + podR * 0.88f)
                    canvas.drawRoundRect(cachedSegRect, 12f * density, 12f * density, bgPaint)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 2.0f * density
                    strokePaint.color = seamColor
                    strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawRoundRect(cachedSegRect, 12f * density, 12f * density, strokePaint)
                } else {
                    canvas.drawCircle(nodeCenterX, nodeCenterY, podR, bgPaint)
                    strokePaint.style = Paint.Style.STROKE
                    strokePaint.strokeWidth = 2.0f * density
                    strokePaint.color = seamColor
                    strokePaint.alpha = (245 * clampedAlpha).toInt().coerceIn(0, 255)
                    canvas.drawCircle(nodeCenterX, nodeCenterY, podR, strokePaint)
                }
            } else {
                val (startDeg, sweepDeg) = WEDGE_SPANS.getOrElse(i) { -25f to 50f }
                val wedgePath = buildWedgeSegmentPath(isLeft, originX, centerY, rInner, rOuter, startDeg, sweepDeg, style)

                // Wedge glass gradient fill
                bgPaint.style = Paint.Style.FILL
                bgPaint.shader = RadialGradient(
                    nodeCenterX, nodeCenterY, (rOuter - rInner).coerceAtLeast(16f),
                    intArrayOf(
                        if (isHovered) Color.argb((215 * clampedAlpha).toInt(), Color.red(tintCol), Color.green(tintCol), Color.blue(tintCol))
                        else Color.argb((Color.alpha(innerCol) * clampedAlpha).toInt().coerceIn(55, 250), Color.red(innerCol), Color.green(innerCol), Color.blue(innerCol)),
                        if (hasSteps) Color.argb((225 * clampedAlpha).toInt(), Color.red(secCol) / 2 + Color.red(outerCol) / 2, Color.green(secCol) / 2 + Color.green(outerCol) / 2, Color.blue(secCol) / 2 + Color.blue(outerCol) / 2)
                        else Color.argb((Color.alpha(outerCol) * clampedAlpha).toInt().coerceIn(65, 252), Color.red(outerCol), Color.green(outerCol), Color.blue(outerCol))
                    ),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawPath(wedgePath, bgPaint)
                bgPaint.shader = null

                // Outer Neon Glow Seam
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 4.8f * density
                glowPaint.color = if (armIndex == 2) secCol else seamColor
                glowPaint.alpha = ((if (isHovered || hasSteps || isThisRec) 110 else 65) * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawPath(wedgePath, glowPaint)

                // Crisp Bright Seam Outline
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = (if (isHovered || hasSteps) 2.2f else 1.7f) * density
                strokePaint.color = seamColor
                strokePaint.alpha = (248 * clampedAlpha).toInt().coerceIn(0, 255)
                canvas.drawPath(wedgePath, strokePaint)
            }

            drawButtonGlyphAndLabel(canvas, nodeCenterX, nodeCenterY, armIndex, botId, isThisRec, isHovered, hasSteps, tintCol, density, clampedAlpha)
        }
    }

    private fun drawButtonGlyphAndLabel(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        armIndex: Int,
        botId: Int,
        isRecording: Boolean,
        isHovered: Boolean,
        hasSteps: Boolean,
        tintCol: Int,
        density: Float,
        clampedAlpha: Float
    ) {
        val iconColor = when {
            isRecording -> 0xFFFF4444.toInt()
            isHovered -> Color.WHITE
            hasSteps -> tintCol
            else -> Color.WHITE
        }
        val alphaInt = (250 * clampedAlpha).toInt().coerceIn(0, 255)
        val iconY = cy - (4.2f * density)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.6f * density
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.color = iconColor
        strokePaint.alpha = alphaInt

        if (isRecording) {
            nodePaint.style = Paint.Style.FILL
            nodePaint.color = 0xFFFF4444.toInt()
            nodePaint.alpha = alphaInt
            canvas.drawCircle(cx, iconY, 4.2f * density, nodePaint)
        } else if (hasSteps) {
            // Crisp Vector Play Triangle (▶) — Recorded Macro Ready to Play
            val r = 4.2f * density
            cachedWedgePath.reset()
            cachedWedgePath.moveTo(cx - r * 0.7f, iconY - r * 0.85f)
            cachedWedgePath.lineTo(cx + r * 0.95f, iconY)
            cachedWedgePath.lineTo(cx - r * 0.7f, iconY + r * 0.85f)
            cachedWedgePath.close()
            nodePaint.style = Paint.Style.FILL
            nodePaint.color = iconColor
            nodePaint.alpha = alphaInt
            canvas.drawPath(cachedWedgePath, nodePaint)
        } else {
            // Crisp Vector Programmable Macro Node (Target Ring + Center Dot) — Hold to Record
            val r = 4.0f * density
            canvas.drawCircle(cx, iconY, r, strokePaint)
            nodePaint.style = Paint.Style.FILL
            nodePaint.color = iconColor
            nodePaint.alpha = alphaInt
            canvas.drawCircle(cx, iconY, 1.4f * density, nodePaint)
        }

        val label = if (isRecording) "REC" else "B$botId"
        textPaint.color = iconColor
        textPaint.alpha = (225 * clampedAlpha).toInt().coerceIn(0, 255)
        textPaint.textSize = 7.2f * density
        canvas.drawText(label, cx, cy + (9.2f * density), textPaint)
    }

    fun drawTacticalCursor(
        canvas: Canvas,
        cursorX: Float,
        cursorY: Float,
        isLooping: Boolean,
        loopCount: Int,
        theme: DeckThemeSnapshot,
        density: Float,
        cursorShape: String = "crosshair",
        context: android.content.Context? = null,
        colorMode: String = LightspeedBotContract.COLOR_MODE_HYBRID_FUSION,
        isHoldActive: Boolean = false
    ) {
        LightspeedBotCursorDraw.drawTacticalCursor(
            canvas = canvas,
            cursorX = cursorX,
            cursorY = cursorY,
            isLooping = isLooping,
            loopCount = loopCount,
            theme = theme,
            density = density,
            cursorShape = cursorShape,
            context = context,
            colorMode = colorMode,
            isHoldActive = isHoldActive
        )
    }
}
