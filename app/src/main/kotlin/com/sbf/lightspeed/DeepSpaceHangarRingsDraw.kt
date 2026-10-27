package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.sbf.lightspeed.settings.resolveCanvasPalette
import kotlin.math.cos
import kotlin.math.sin

internal fun DeepSpaceRenderer.drawSpaceshipGimbalRingImpl(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        trackRadius: Float,
        trackWidth: Float,
        teethCount: Int,
        toothDepth: Float,
        rotationDeg: Float,
        isActive: Boolean,
        m3Primary: Int,
        gearShape: String = com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_CIRCLE,
        segmentCount: Int = (teethCount / 2).coerceAtLeast(6)
    ) {
        val shape = gearShape.lowercase()
        val innerR = trackRadius - trackWidth / 2f
        val outerR = trackRadius + trackWidth / 2f

        val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.snapshotFlow.value
        val palette = snapshot.resolveCanvasPalette(null)
        val primaryCol = if (m3Primary != 0) m3Primary else palette.primaryAccentArgb
        val secCol = palette.secondaryAccentArgb
        val panelIn = palette.panelInnerArgb
        val panelOut = palette.panelOuterArgb

        val rCol = Color.red(primaryCol)
        val gCol = Color.green(primaryCol)
        val bCol = Color.blue(primaryCol)
        val brightR = (rCol + 65).coerceAtMost(255)
        val brightG = (gCol + 65).coerceAtMost(255)
        val brightB = (bCol + 65).coerceAtMost(255)

        val darkGlassColor = if (isActive) {
            Color.argb(232, Color.red(panelIn), Color.green(panelIn), Color.blue(panelIn))
        } else {
            Color.argb(190, Color.red(panelOut), Color.green(panelOut), Color.blue(panelOut))
        }
        val tintFillColor = if (isActive) {
            Color.argb(82, rCol, gCol, bCol)
        } else {
            Color.argb(38, Color.red(secCol), Color.green(secCol), Color.blue(secCol))
        }
        val haloColor = if (isActive) {
            Color.argb(95, Color.red(secCol), Color.green(secCol), Color.blue(secCol))
        } else {
            Color.argb(42, rCol, gCol, bCol)
        }
        val coreStrokeColor = if (isActive) Color.argb(250, brightR, brightG, brightB) else Color.argb(155, rCol, gCol, bCol)
        val haloStrokeW = if (isActive) 5.8f else 3.4f
        val coreStrokeW = if (isActive) 2.3f else 1.5f

        val rotRad = Math.toRadians(rotationDeg.toDouble())
        val segCount = segmentCount.coerceIn(4, 16)
        val segStep = (2.0 * Math.PI) / segCount
        val halfStep = segStep * 0.5

        fun drawStyledSegmentPath() {
            elementPaint.style = Paint.Style.FILL
            elementPaint.color = darkGlassColor
            canvas.drawPath(gearPath, elementPaint)

            elementPaint.color = tintFillColor
            canvas.drawPath(gearPath, elementPaint)

            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = haloStrokeW
            elementPaint.color = haloColor
            canvas.drawPath(gearPath, elementPaint)

            elementPaint.strokeWidth = coreStrokeW
            elementPaint.color = coreStrokeColor
            canvas.drawPath(gearPath, elementPaint)
        }

        when (shape) {
            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_SQUARE -> {
                // MOCKUP #2: HEX RING — Chamfered polygonal armor plates around the ring
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val a0 = centerA - halfStep * 0.88
                    val a1 = centerA - halfStep * 0.38
                    val a2 = centerA + halfStep * 0.38
                    val a3 = centerA + halfStep * 0.88

                    val rIn = innerR * 0.96f
                    val rOutShoulder = outerR * 0.96f
                    val rOutPeak = outerR + toothDepth * 0.85f

                    gearPath.rewind()
                    gearPath.moveTo(cx + rIn * cos(a0).toFloat(), cy + rIn * sin(a0).toFloat())
                    gearPath.lineTo(cx + rOutShoulder * cos(a0).toFloat(), cy + rOutShoulder * sin(a0).toFloat())
                    gearPath.lineTo(cx + rOutPeak * cos(a1).toFloat(), cy + rOutPeak * sin(a1).toFloat())
                    gearPath.lineTo(cx + rOutPeak * cos(a2).toFloat(), cy + rOutPeak * sin(a2).toFloat())
                    gearPath.lineTo(cx + rOutShoulder * cos(a3).toFloat(), cy + rOutShoulder * sin(a3).toFloat())
                    gearPath.lineTo(cx + rIn * cos(a3).toFloat(), cy + rIn * sin(a3).toFloat())
                    gearPath.close()
                    drawStyledSegmentPath()
                }
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_SQUIRCLE -> {
                // MOCKUP #3: PETAL MENU — Contoured flower-petal lobes peaking behind each app icon
                val curveSteps = 12
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val span = segStep * 0.88
                    val aStart = centerA - span * 0.5
                    val aEnd = centerA + span * 0.5
                    val rIn = innerR * 0.94f

                    gearPath.rewind()
                    gearPath.moveTo(cx + rIn * cos(aStart).toFloat(), cy + rIn * sin(aStart).toFloat())
                    for (s in 0..curveSteps) {
                        val frac = s.toDouble() / curveSteps
                        val a = aStart + frac * span
                        val lobe = sin(frac * Math.PI).toFloat()
                        val r = (outerR * 0.90f) + (trackWidth * 0.32f + toothDepth * 1.1f) * lobe
                        gearPath.lineTo(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
                    }
                    for (s in curveSteps downTo 0) {
                        val frac = s.toDouble() / curveSteps
                        val a = aStart + frac * span
                        gearPath.lineTo(cx + rIn * cos(a).toFloat(), cy + rIn * sin(a).toFloat())
                    }
                    gearPath.close()
                    drawStyledSegmentPath()
                }
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_STAR -> {
                // MOCKUP #9: FLOATING BLADES — Sweeping turbine blades + floating outer neon arc bars
                val bladeSteps = 8
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val span = segStep * 0.80
                    val aStart = centerA - span * 0.5
                    val aEnd = centerA + span * 0.5

                    gearPath.rewind()
                    for (s in 0..bladeSteps) {
                        val frac = s.toDouble() / bladeSteps
                        val a = aStart + frac * span
                        // Swept blade outer edge rises toward leading tip
                        val sweep = sin(frac * Math.PI * 0.85).toFloat()
                        val rOut = outerR * 0.94f + (toothDepth * 1.25f) * sweep
                        val px = cx + rOut * cos(a).toFloat()
                        val py = cy + rOut * sin(a).toFloat()
                        if (s == 0) gearPath.moveTo(px, py) else gearPath.lineTo(px, py)
                    }
                    for (s in bladeSteps downTo 0) {
                        val frac = s.toDouble() / bladeSteps
                        val a = aStart + frac * span + 0.03
                        val rIn = innerR * 0.95f
                        gearPath.lineTo(cx + rIn * cos(a).toFloat(), cy + rIn * sin(a).toFloat())
                    }
                    gearPath.close()
                    drawStyledSegmentPath()

                    // Floating outer neon accent arc (signature Mockup #9 detail)
                    val floatR = outerR + toothDepth * 1.65f
                    reticleArcRect.set(cx - floatR, cy - floatR, cx + floatR, cy + floatR)
                    val startDeg = Math.toDegrees(centerA - span * 0.32).toFloat()
                    val sweepDeg = Math.toDegrees(span * 0.64).toFloat()
                    elementPaint.style = Paint.Style.STROKE
                    elementPaint.strokeWidth = coreStrokeW * 1.15f
                    elementPaint.color = coreStrokeColor
                    canvas.drawArc(reticleArcRect, startDeg, sweepDeg, false, elementPaint)
                }
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_ORBITAL -> {
                // MOCKUP #4: ORBITAL RINGS — Concentric glowing planetary tracks + satellite nodes
                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = trackWidth
                elementPaint.color = darkGlassColor
                canvas.drawCircle(cx, cy, trackRadius, elementPaint)

                elementPaint.strokeWidth = trackWidth * 0.82f
                elementPaint.color = tintFillColor
                canvas.drawCircle(cx, cy, trackRadius, elementPaint)

                val orbitOuterR = outerR + toothDepth * 0.55f
                val orbitInnerR = innerR - toothDepth * 0.35f

                // Outer & inner halo rings
                elementPaint.strokeWidth = haloStrokeW
                elementPaint.color = haloColor
                canvas.drawCircle(cx, cy, innerR, elementPaint)
                canvas.drawCircle(cx, cy, outerR, elementPaint)
                canvas.drawCircle(cx, cy, orbitOuterR, elementPaint)

                // Crisp concentric orbital rails
                elementPaint.strokeWidth = coreStrokeW
                elementPaint.color = coreStrokeColor
                canvas.drawCircle(cx, cy, innerR, elementPaint)
                canvas.drawCircle(cx, cy, outerR, elementPaint)
                elementPaint.strokeWidth = coreStrokeW * 0.75f
                canvas.drawCircle(cx, cy, orbitOuterR, elementPaint)
                canvas.drawCircle(cx, cy, orbitInnerR, elementPaint)

                // Glowing satellite nodes on the outer & inner orbital rails between app slots
                elementPaint.style = Paint.Style.FILL
                for (i in 0 until segCount) {
                    val nodeA = (i + 0.5) * segStep + rotRad
                    val nx = cx + orbitOuterR * cos(nodeA).toFloat()
                    val ny = cy + orbitOuterR * sin(nodeA).toFloat()
                    elementPaint.color = haloColor
                    canvas.drawCircle(nx, ny, toothDepth * 0.85f, elementPaint)
                    elementPaint.color = coreStrokeColor
                    canvas.drawCircle(nx, ny, toothDepth * 0.42f, elementPaint)
                }
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_DIAMOND -> {
                // MOCKUP #6: DIAMOND — Faceted diamond shield plates framing each app slot
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val sideALeft = centerA - halfStep * 0.84
                    val sideARight = centerA + halfStep * 0.84
                    val rInPeak = innerR - toothDepth * 0.55f
                    val rOutPeak = outerR + toothDepth * 1.25f
                    val rMidIn = trackRadius - trackWidth * 0.24f
                    val rMidOut = trackRadius + trackWidth * 0.24f

                    gearPath.rewind()
                    gearPath.moveTo(cx + rInPeak * cos(centerA).toFloat(), cy + rInPeak * sin(centerA).toFloat())
                    gearPath.lineTo(cx + rMidIn * cos(sideALeft).toFloat(), cy + rMidIn * sin(sideALeft).toFloat())
                    gearPath.lineTo(cx + rMidOut * cos(sideALeft).toFloat(), cy + rMidOut * sin(sideALeft).toFloat())
                    gearPath.lineTo(cx + rOutPeak * cos(centerA).toFloat(), cy + rOutPeak * sin(centerA).toFloat())
                    gearPath.lineTo(cx + rMidOut * cos(sideARight).toFloat(), cy + rMidOut * sin(sideARight).toFloat())
                    gearPath.lineTo(cx + rMidIn * cos(sideARight).toFloat(), cy + rMidIn * sin(sideARight).toFloat())
                    gearPath.close()
                    drawStyledSegmentPath()
                }
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_CRESCENT -> {
                // MOCKUP #7: CRESCENT — Sweeping tapered crescent shells + stepped outer eclipse arc
                val arcSteps = 8
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val span = segStep * 0.88
                    val aStart = centerA - span * 0.5
                    gearPath.rewind()
                    for (s in 0..arcSteps) {
                        val frac = s.toDouble() / arcSteps
                        val a = aStart + frac * span
                        val swell = sin(frac * Math.PI).toFloat()
                        val rOut = outerR * 0.92f + (toothDepth * 1.15f) * swell
                        val px = cx + rOut * cos(a).toFloat()
                        val py = cy + rOut * sin(a).toFloat()
                        if (s == 0) gearPath.moveTo(px, py) else gearPath.lineTo(px, py)
                    }
                    for (s in arcSteps downTo 0) {
                        val frac = s.toDouble() / arcSteps
                        val a = aStart + frac * span
                        val swell = sin(frac * Math.PI).toFloat()
                        val rIn = innerR * 1.02f - (toothDepth * 0.55f) * swell
                        gearPath.lineTo(cx + rIn * cos(a).toFloat(), cy + rIn * sin(a).toFloat())
                    }
                    gearPath.close()
                    drawStyledSegmentPath()
                }
                // Stepped outer crescent shroud arc (Mockup #7 eclipse rim)
                val shroudR = outerR + toothDepth * 1.55f
                reticleArcRect.set(cx - shroudR, cy - shroudR, cx + shroudR, cy + shroudR)
                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = haloStrokeW
                elementPaint.color = haloColor
                canvas.drawArc(reticleArcRect, rotationDeg + 70f, 220f, false, elementPaint)
                elementPaint.strokeWidth = coreStrokeW * 1.2f
                elementPaint.color = coreStrokeColor
                canvas.drawArc(reticleArcRect, rotationDeg + 70f, 220f, false, elementPaint)
            }

            com.sbf.lightspeed.system.LightspeedPreferences.GEAR_SHAPE_TECH -> {
                // MOCKUP #14: TECH DISC — Beveled sci-fi disc blocks between dual precision rails
                val arcSteps = 6
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val span = segStep * 0.82
                    val aStart = centerA - span * 0.5
                    gearPath.rewind()
                    for (s in 0..arcSteps) {
                        val a = aStart + (s.toDouble() / arcSteps) * span
                        val px = cx + outerR * cos(a).toFloat()
                        val py = cy + outerR * sin(a).toFloat()
                        if (s == 0) gearPath.moveTo(px, py) else gearPath.lineTo(px, py)
                    }
                    for (s in arcSteps downTo 0) {
                        val a = aStart + (s.toDouble() / arcSteps) * span
                        gearPath.lineTo(cx + innerR * cos(a).toFloat(), cy + innerR * sin(a).toFloat())
                    }
                    gearPath.close()
                    drawStyledSegmentPath()
                }
                // Continuous outer & inner precision tech rails + degree ticks
                val railOutR = outerR + toothDepth * 0.95f
                val railInR = innerR - toothDepth * 0.45f
                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = coreStrokeW
                elementPaint.color = coreStrokeColor
                canvas.drawCircle(cx, cy, railOutR, elementPaint)
                canvas.drawCircle(cx, cy, railInR, elementPaint)
                val tickCount = segCount * 3
                val tickStep = (2.0 * Math.PI) / tickCount
                for (t in 0 until tickCount) {
                    val ta = t * tickStep + rotRad
                    val r1 = railOutR
                    val r2 = railOutR + (if (t % 3 == 0) toothDepth * 0.75f else toothDepth * 0.38f)
                    canvas.drawLine(
                        cx + r1 * cos(ta).toFloat(), cy + r1 * sin(ta).toFloat(),
                        cx + r2 * cos(ta).toFloat(), cy + r2 * sin(ta).toFloat(),
                        elementPaint
                    )
                }
            }

            else -> {
                // MOCKUP #1: GEAR WHEEL (Default "circle") — Sculpted outer gear cogs + segmented radial wedge tiles
                gearPath.rewind()
                val toothAngleStep = (2.0 * Math.PI) / teethCount
                val toothInnerR = outerR - (toothDepth * 0.15f)
                val toothOuterR = outerR + (toothDepth * 1.25f)

                for (i in 0 until teethCount) {
                    val baseAngle = i * toothAngleStep + rotRad
                    val a1 = baseAngle
                    val a2 = baseAngle + toothAngleStep * 0.14
                    val a3 = baseAngle + toothAngleStep * 0.40
                    val a4 = baseAngle + toothAngleStep * 0.54
                    val a5 = baseAngle + toothAngleStep

                    if (i == 0) {
                        gearPath.moveTo(cx + toothInnerR * cos(a1).toFloat(), cy + toothInnerR * sin(a1).toFloat())
                    } else {
                        gearPath.lineTo(cx + toothInnerR * cos(a1).toFloat(), cy + toothInnerR * sin(a1).toFloat())
                    }
                    gearPath.lineTo(cx + toothOuterR * cos(a2).toFloat(), cy + toothOuterR * sin(a2).toFloat())
                    gearPath.lineTo(cx + toothOuterR * cos(a3).toFloat(), cy + toothOuterR * sin(a3).toFloat())
                    gearPath.lineTo(cx + toothInnerR * cos(a4).toFloat(), cy + toothInnerR * sin(a4).toFloat())
                    gearPath.lineTo(cx + toothInnerR * cos(a5).toFloat(), cy + toothInnerR * sin(a5).toFloat())
                }
                gearPath.close()

                elementPaint.style = Paint.Style.FILL
                elementPaint.color = if (isActive) Color.argb(105, rCol, gCol, bCol) else Color.argb(46, rCol, gCol, bCol)
                canvas.drawPath(gearPath, elementPaint)

                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = coreStrokeW
                elementPaint.color = coreStrokeColor
                canvas.drawPath(gearPath, elementPaint)

                // Individual contoured radial wedge tiles (Mockup #1)
                val arcSteps = 6
                for (i in 0 until segCount) {
                    val centerA = i * segStep + rotRad
                    val span = segStep * 0.88
                    val aStart = centerA - span * 0.5
                    gearPath.rewind()
                    for (s in 0..arcSteps) {
                        val a = aStart + (s.toDouble() / arcSteps) * span
                        val px = cx + outerR * cos(a).toFloat()
                        val py = cy + outerR * sin(a).toFloat()
                        if (s == 0) gearPath.moveTo(px, py) else gearPath.lineTo(px, py)
                    }
                    for (s in arcSteps downTo 0) {
                        val a = aStart + (s.toDouble() / arcSteps) * span
                        gearPath.lineTo(cx + innerR * cos(a).toFloat(), cy + innerR * sin(a).toFloat())
                    }
                    gearPath.close()
                    drawStyledSegmentPath()
                }
            }
        }
    }

internal fun DeepSpaceRenderer.drawFlightLockReticleImpl(
        canvas: Canvas,
        targetCX: Float,
        targetCY: Float,
        bracketSize: Float,
        m3Primary: Int,
        m3Secondary: Int,
        appName: String,
        density: Float,
        reticleStyle: String = "tactical"
    ) {
        val half = bracketSize / 2f
        val armLen = bracketSize * 0.28f

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = 2.4f * density
        elementPaint.color = m3Primary

        when (reticleStyle) {
            "cyber" -> {
                reticleArcRect.set(targetCX - half, targetCY - half, targetCX + half, targetCY + half)
                canvas.drawArc(reticleArcRect, 35f, 110f, false, elementPaint)
                canvas.drawArc(reticleArcRect, 215f, 110f, false, elementPaint)
                elementPaint.strokeWidth = 1.6f * density
                canvas.drawLine(targetCX, targetCY - half - 4f * density, targetCX, targetCY - half + 4f * density, elementPaint)
                canvas.drawLine(targetCX, targetCY + half - 4f * density, targetCX, targetCY + half + 4f * density, elementPaint)
                canvas.drawLine(targetCX - half - 4f * density, targetCY, targetCX - half + 4f * density, targetCY, elementPaint)
                canvas.drawLine(targetCX + half - 4f * density, targetCY, targetCX + half + 4f * density, targetCY, elementPaint)
            }
            "cross" -> {
                elementPaint.strokeWidth = 1.8f * density
                val crossGap = half * 0.52f
                canvas.drawLine(targetCX - half, targetCY, targetCX - crossGap, targetCY, elementPaint)
                canvas.drawLine(targetCX + crossGap, targetCY, targetCX + half, targetCY, elementPaint)
                canvas.drawLine(targetCX, targetCY - half, targetCX, targetCY - crossGap, elementPaint)
                canvas.drawLine(targetCX, targetCY + crossGap, targetCX, targetCY + half, elementPaint)
                elementPaint.style = Paint.Style.FILL
                canvas.drawCircle(targetCX, targetCY, 2.2f * density, elementPaint)
                elementPaint.style = Paint.Style.STROKE
            }
            "diamond" -> {
                val dOffset = half * 1.05f
                diamondPath.rewind()
                diamondPath.moveTo(targetCX, targetCY - dOffset)
                diamondPath.lineTo(targetCX + dOffset, targetCY)
                diamondPath.lineTo(targetCX, targetCY + dOffset)
                diamondPath.lineTo(targetCX - dOffset, targetCY)
                diamondPath.close()
                elementPaint.strokeWidth = 2f * density
                canvas.drawPath(diamondPath, elementPaint)
                elementPaint.style = Paint.Style.FILL
                canvas.drawCircle(targetCX, targetCY - dOffset, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX, targetCY + dOffset, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX - dOffset, targetCY, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX + dOffset, targetCY, 2.2f * density, elementPaint)
                elementPaint.style = Paint.Style.STROKE
            }
            else -> {
                canvas.drawLine(targetCX - half, targetCY - half + armLen, targetCX - half, targetCY - half, elementPaint)
                canvas.drawLine(targetCX - half, targetCY - half, targetCX - half + armLen, targetCY - half, elementPaint)
                canvas.drawLine(targetCX + half - armLen, targetCY - half, targetCX + half, targetCY - half, elementPaint)
                canvas.drawLine(targetCX + half, targetCY - half, targetCX + half, targetCY - half + armLen, elementPaint)
                canvas.drawLine(targetCX - half, targetCY + half - armLen, targetCX - half, targetCY + half, elementPaint)
                canvas.drawLine(targetCX - half, targetCY + half, targetCX - half + armLen, targetCY + half, elementPaint)
                canvas.drawLine(targetCX + half - armLen, targetCY + half, targetCX + half, targetCY + half, elementPaint)
                canvas.drawLine(targetCX + half, targetCY + half, targetCX + half, targetCY + half - armLen, elementPaint)
            }
        }

        elementPaint.strokeWidth = 1.2f * density
        elementPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawLine(0f, targetCY, targetCX - half - 10f, targetCY, elementPaint)

        textPaint.textSize = 12f * density
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val maxBadgeWidth = 160f * density
        var cleanAppName = appName
        if (textPaint.measureText(cleanAppName) > maxBadgeWidth) {
            while (cleanAppName.length > 3 && textPaint.measureText("$cleanAppName…") > maxBadgeWidth) {
                cleanAppName = cleanAppName.dropLast(1)
            }
            cleanAppName = "$cleanAppName…"
        }

        val subText = "TARGET LOCKED"
        textPaint.textSize = 8.5f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        val subWidth = textPaint.measureText(subText)
        textPaint.textSize = 12f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val titleWidth = textPaint.measureText(cleanAppName)
        val finalBadgeWidth = maxOf(titleWidth, subWidth).coerceAtLeast(54f * density)

        val badgeX = targetCX + half + 14f * density
        val badgeY = targetCY - 6f * density

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(190, 16, 20, 32)
        flightBadgeRect.set(badgeX - 8f * density, badgeY - 14f * density, badgeX + finalBadgeWidth + 12f * density, badgeY + 18f * density)
        canvas.drawRoundRect(flightBadgeRect, 8f * density, 8f * density, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * density
        highlightPaint.color = m3Primary
        canvas.drawRoundRect(flightBadgeRect, 8f * density, 8f * density, highlightPaint)

        textPaint.textSize = 12f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText(cleanAppName, badgeX, badgeY + 1f * density, textPaint)

        textPaint.textSize = 8f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = Color.argb(220, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
        canvas.drawText(subText, badgeX, badgeY + 12.5f * density, textPaint)
    }

