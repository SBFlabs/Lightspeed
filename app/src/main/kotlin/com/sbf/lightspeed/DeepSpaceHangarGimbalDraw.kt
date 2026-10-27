package com.sbf.lightspeed

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import kotlin.math.cos
import kotlin.math.sin

internal fun DeepSpaceRenderer.drawHangarGimbalAssembly(
    state: HangarRenderState,
    layout: HangarLayoutMetrics
) {
    val canvas = state.canvas
    val context = state.context
    val screenH = state.screenH
    val d = state.density
    val m3Primary = state.m3Primary
    val m3Secondary = state.m3Secondary
    val isOpenedFromLeftFlank = state.isOpenedFromLeftFlank
    val activeGearSetIndex = state.activeGearSetIndex
    val activeHangarRing = state.activeHangarRing
    val isHangarEjectArmed = state.isHangarEjectArmed
    val gearRingRotations = state.gearRingRotations
    val setsList = state.setsList
    val resolveCleanAppLabel = state.resolveCleanAppLabel
    val getAppsForActiveGear = state.getAppsForActiveGear
    val onIconResolved = state.onIconResolved

    val cx = layout.gimbalCx
    val cyGimbal = layout.cyGimbal
    val deckW = layout.deckW

    val isInfinityUnlocked = LightspeedInfinityManager.isUnlocked(context)
    val reticleStyle = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getReticleStyle(context)
    val reticleOri = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getReticleOrientation(context)

    val gearShapeRaw = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getGearShape(context)
    val isRow2Shape = gearShapeRaw == LightspeedPreferences.GEAR_SHAPE_ORBITAL ||
            gearShapeRaw == LightspeedPreferences.GEAR_SHAPE_DIAMOND ||
            gearShapeRaw == LightspeedPreferences.GEAR_SHAPE_CRESCENT ||
            gearShapeRaw == LightspeedPreferences.GEAR_SHAPE_TECH
    val gearShape = if (!isInfinityUnlocked && isRow2Shape) LightspeedPreferences.GEAR_SHAPE_CIRCLE else gearShapeRaw
    val unitScale = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getUnitScale(context)
    val customDiameterScale = com.sbf.lightspeed.system.CockpitGimbalConfigCache.getCustomDiameterScale(context)
    val dGimbal = unitScale * customDiameterScale * layout.landscapeGimbalFit

    val radOuter = 135f * dGimbal
    val radInner = 84f * dGimbal
    val radHub = 32f * dGimbal

    var activeGearSetIndexLocal = activeGearSetIndex
    if (activeGearSetIndexLocal >= setsList.size) { activeGearSetIndexLocal = 0 }

    val r0 = getAppsForActiveGear(activeGearSetIndexLocal, 0)
    val r1 = getAppsForActiveGear(activeGearSetIndexLocal, 1)
    val activeAppsList = if (activeHangarRing == 0) r0 else r1
    val sizeRaw = (38f * d).toInt()

    drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radOuter, 26f * d, 16, 7.5f * d, gearRingRotations[0], (activeHangarRing == 0), m3Primary, gearShape, r0.size.coerceAtLeast(6))
    drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radInner, 20f * d, 10, 5.5f * d, gearRingRotations[1], (activeHangarRing == 1), m3Primary, gearShape, r1.size.coerceAtLeast(6))

    var focusedAppLabel: String? = null
    val targetFocusAngle = if (reticleOri == LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK && isOpenedFromLeftFlank) 0f else 180f

    // Draw Real App Icons on both rings
    for (r in 0..1) {
        val radius = if (r == 0) radOuter else radInner
        val ringApps = if (r == 0) r0 else r1
        if (ringApps.isEmpty()) continue

        val count = ringApps.size
        val baseRotation = gearRingRotations[r]
        val isRingFocused = (activeHangarRing == r)

        for (i in ringApps.indices) {
            val slotRad = Math.toRadians(i * (360.0 / count))
            val rotRad = Math.toRadians(baseRotation.toDouble())
            val (iconCX, iconCY) = DeepSpaceGearGeometry.pointOnGearPerimeter(cx, cyGimbal, radius, slotRad, rotRad, gearShape)
            val angleDeg = (baseRotation + i * (360f / count)) % 360f

            val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
            val angleDiff = kotlin.math.abs(normalizedDeg - targetFocusAngle).let { if (it > 180f) 360f - it else it }
            val isHighlighted = isRingFocused && angleDiff < (180f / count)
            val currentScale = if (isHighlighted) 1.25f else 1.0f
            val currentSize = (sizeRaw * currentScale).toInt()

            val itemToken = ringApps[i]
            val appLabel = resolveCleanAppLabel(itemToken)

            if (isHighlighted) {
                focusedAppLabel = appLabel
            }

            val isEjectArmedHere = isHangarEjectArmed && isHighlighted && (activeHangarRing == r)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isEjectArmedHere) Color.argb(230, 220, 38, 38)
                                  else if (isHighlighted) Color.argb(220, 24, 32, 54)
                                  else Color.argb(140, 12, 16, 26)
            canvas.drawCircle(iconCX, iconCY, currentSize / 1.7f, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isEjectArmedHere) 2.6f * d else if (isHighlighted) 1.8f * d else 0.8f * d
            highlightPaint.color = if (isEjectArmedHere) Color.argb(255, 255, 100, 100)
                                  else if (isHighlighted) m3Primary
                                  else Color.argb(50, 200, 220, 255)
            canvas.drawCircle(iconCX, iconCY, currentSize / 1.7f, highlightPaint)

            try {
                val dIcon = LightspeedIconManager.getCachedIconDrawable(itemToken)
                if (dIcon != null) {
                    dIcon.alpha = if (isRingFocused) (if (isHighlighted) (if (isEjectArmedHere) 140 else 255) else 220) else 140
                    val iconLeft = (iconCX - currentSize / 2f).toInt()
                    val iconTop = (iconCY - currentSize / 2f).toInt()
                    dIcon.setBounds(iconLeft, iconTop, iconLeft + currentSize, iconTop + currentSize)
                    dIcon.draw(canvas)
                } else {
                    // Tactical Monogram Placeholder: draws instantly in 0ms without blocking onDraw
                    val initial = appLabel.firstOrNull()?.uppercase() ?: "?"
                    textPaint.color = Color.WHITE
                    textPaint.textSize = currentSize * 0.44f
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(initial, iconCX, iconCY + (currentSize * 0.16f), textPaint)

                    // Stream full icon in background without dropping any frame
                    LightspeedIconManager.loadIconAsync(context, itemToken, onIconResolved)
                }
            } catch (e: Exception) { logSwallowed("DeepSpaceHangarGimbalDraw", "drawGimbalApps", e) }

            if (isEjectArmedHere) {
                elementPaint.style = Paint.Style.STROKE
                elementPaint.strokeWidth = 3.2f * d
                elementPaint.color = Color.WHITE
                val cr = 8.5f * d
                canvas.drawLine(iconCX - cr, iconCY - cr, iconCX + cr, iconCY + cr, elementPaint)
                canvas.drawLine(iconCX + cr, iconCY - cr, iconCX - cr, iconCY + cr, elementPaint)
            }
        }
    }

    val activeRingColor = if (activeHangarRing == 0) m3Primary else m3Secondary
    val otherRingColor = if (activeHangarRing == 0) m3Secondary else m3Primary

    // Targeting Reticle Collimator at targetFocusAngle (180° Inward Horizon or 0° Dynamic Flank)
    val ringRad = if (activeHangarRing == 0) radOuter else radInner
    val targetAngleRad = Math.toRadians(targetFocusAngle.toDouble())
    val rotRadFocused = Math.toRadians(gearRingRotations[activeHangarRing].toDouble())
    val (reticleX, reticleY) = DeepSpaceGearGeometry.pointOnGearPerimeter(cx, cyGimbal, ringRad, targetAngleRad - rotRadFocused, rotRadFocused, gearShape)
    val reticleColor = if (isHangarEjectArmed) Color.argb(255, 255, 60, 60)
                       else if (activeHangarRing == 0) m3Primary
                       else m3Secondary

    val bracketSize = 54f * d
    val half = bracketSize / 2f
    val armLen = bracketSize * 0.28f

    elementPaint.style = Paint.Style.STROKE
    elementPaint.strokeWidth = if (isHangarEjectArmed) 2.4f * d else 1.8f * d
    elementPaint.color = reticleColor

    val effectiveReticle = if (!isInfinityUnlocked && (reticleStyle == "cross" || reticleStyle == "diamond")) "tactical" else reticleStyle
    when (effectiveReticle) {
        "cyber" -> {
            reticleArcRect.set(reticleX - half, reticleY - half, reticleX + half, reticleY + half)
            canvas.drawArc(reticleArcRect, 35f, 110f, false, elementPaint)
            canvas.drawArc(reticleArcRect, 215f, 110f, false, elementPaint)
            elementPaint.strokeWidth = 1.4f * d
            canvas.drawLine(reticleX, reticleY - half - 4f * d, reticleX, reticleY - half + 4f * d, elementPaint)
            canvas.drawLine(reticleX, reticleY + half - 4f * d, reticleX, reticleY + half + 4f * d, elementPaint)
            canvas.drawLine(reticleX - half - 4f * d, reticleY, reticleX - half + 4f * d, reticleY, elementPaint)
            canvas.drawLine(reticleX + half - 4f * d, reticleY, reticleX + half + 4f * d, reticleY, elementPaint)
        }
        "cross" -> {
            elementPaint.strokeWidth = 1.6f * d
            val crossGap = half * 0.52f
            canvas.drawLine(reticleX - half, reticleY, reticleX - crossGap, reticleY, elementPaint)
            canvas.drawLine(reticleX + crossGap, reticleY, reticleX + half, reticleY, elementPaint)
            canvas.drawLine(reticleX, reticleY - half, reticleX, reticleY - crossGap, elementPaint)
            canvas.drawLine(reticleX, reticleY + crossGap, reticleX, reticleY + half, elementPaint)
            elementPaint.style = Paint.Style.FILL
            canvas.drawCircle(reticleX, reticleY, 2.2f * d, elementPaint)
            elementPaint.style = Paint.Style.STROKE
        }
        "diamond" -> {
            val dOffset = half * 1.05f
            diamondPath.rewind()
            diamondPath.moveTo(reticleX, reticleY - dOffset)
            diamondPath.lineTo(reticleX + dOffset, reticleY)
            diamondPath.lineTo(reticleX, reticleY + dOffset)
            diamondPath.lineTo(reticleX - dOffset, reticleY)
            diamondPath.close()
            elementPaint.strokeWidth = 1.8f * d
            canvas.drawPath(diamondPath, elementPaint)
            elementPaint.style = Paint.Style.FILL
            canvas.drawCircle(reticleX, reticleY - dOffset, 2.2f * d, elementPaint)
            canvas.drawCircle(reticleX, reticleY + dOffset, 2.2f * d, elementPaint)
            canvas.drawCircle(reticleX - dOffset, reticleY, 2.2f * d, elementPaint)
            canvas.drawCircle(reticleX + dOffset, reticleY, 2.2f * d, elementPaint)
            elementPaint.style = Paint.Style.STROKE
        }
        else -> {
            canvas.drawLine(reticleX - half, reticleY - half + armLen, reticleX - half, reticleY - half, elementPaint)
            canvas.drawLine(reticleX - half, reticleY - half, reticleX - half + armLen, reticleY - half, elementPaint)
            canvas.drawLine(reticleX + half - armLen, reticleY - half, reticleX + half, reticleY - half, elementPaint)
            canvas.drawLine(reticleX + half, reticleY - half, reticleX + half, reticleY - half + armLen, elementPaint)
            canvas.drawLine(reticleX - half, reticleY + half - armLen, reticleX - half, reticleY + half, elementPaint)
            canvas.drawLine(reticleX - half, reticleY + half, reticleX - half + armLen, reticleY + half, elementPaint)
            canvas.drawLine(reticleX + half - armLen, reticleY + half, reticleX + half, reticleY + half, elementPaint)
            canvas.drawLine(reticleX + half, reticleY + half, reticleX + half, reticleY + half - armLen, elementPaint)
        }
    }

    // Central Command Reactor Core (with rotating inner reticle across all 8 styles)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(210, 16, 22, 38)
    if (gearShape == LightspeedPreferences.GEAR_SHAPE_CIRCLE) {
        canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)
    } else {
        DeepSpaceGearGeometry.buildGearRingPath(cx, cyGimbal, radHub, 0f, gearShape, gearPath)
        canvas.drawPath(gearPath, highlightPaint)
    }
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.6f * d
    highlightPaint.color = activeRingColor
    if (gearShape == LightspeedPreferences.GEAR_SHAPE_CIRCLE) {
        canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)
    } else {
        DeepSpaceGearGeometry.buildGearRingPath(cx, cyGimbal, radHub, 0f, gearShape, gearPath)
        canvas.drawPath(gearPath, highlightPaint)
    }
    val rotAngle = (System.currentTimeMillis() % 10000L) / 10000f * 360f
    val innerHubR = radHub * 0.76f
    elementPaint.style = Paint.Style.STROKE
    elementPaint.strokeWidth = 1.1f * d
    elementPaint.color = Color.argb(110, 200, 225, 255)
    canvas.drawCircle(cx, cyGimbal, innerHubR, elementPaint)
    for (i in 0..3) {
        val a = Math.toRadians((rotAngle + i * 90.0))
        val nx1 = cx + (innerHubR - 3.5f * d) * cos(a).toFloat()
        val ny1 = cyGimbal + (innerHubR - 3.5f * d) * sin(a).toFloat()
        val nx2 = cx + (innerHubR + 3.5f * d) * cos(a).toFloat()
        val ny2 = cyGimbal + (innerHubR + 3.5f * d) * sin(a).toFloat()
        canvas.drawLine(nx1, ny1, nx2, ny2, elementPaint)
    }
    textPaint.textSize = 8.5f * d
    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    textPaint.color = Color.WHITE
    canvas.drawText("RING 0${activeHangarRing + 1}", cx, cyGimbal - 7f * d, textPaint)
    textPaint.textSize = 8.5f * d
    textPaint.color = activeRingColor
    canvas.drawText("[ ⊕ EDIT ]", cx, cyGimbal + 4f * d, textPaint)
    textPaint.textSize = 7f * d
    textPaint.color = Color.argb(160, 200, 220, 255)
    canvas.drawText("${activeAppsList.size} APPS", cx, cyGimbal + 13f * d, textPaint)

    // Target Indicator & Live Orbital Control Deck
    drawHangarOrbitalControls(
        state = state,
        layout = layout,
        cx = cx,
        cyGimbal = cyGimbal,
        radOuter = radOuter,
        deckW = deckW,
        focusedAppLabel = focusedAppLabel,
        activeRingColor = activeRingColor,
        otherRingColor = otherRingColor,
        activeAppsCount = activeAppsList.size
    )
}
