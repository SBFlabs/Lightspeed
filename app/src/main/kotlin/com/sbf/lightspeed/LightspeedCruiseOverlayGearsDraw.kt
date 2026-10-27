package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.sbf.lightspeed.system.CockpitGimbalConfigCache
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import kotlin.math.abs

private val cachedGearHeaderRect = RectF()

internal fun LightspeedCruiseOverlay.drawFavoritesGearsLayer(canvas: Canvas, m3Primary: Int, m3Secondary: Int) {
    val density = resources.displayMetrics.density
    val cx = width / 2f
    val cy = height / 2f

    // 1. Deep Space Astrogation Backdrop with subtle radial vignette — cached shader
    val gradRadius = (width.toFloat().coerceAtLeast(height.toFloat()) * 0.75f)
    val gradKey = (cx.toBits().toLong() xor (cy.toBits().toLong() shl 16)) xor gradRadius.toBits().toLong()
    if (gradKey != spaceGradKey) {
        spaceGradShader = android.graphics.RadialGradient(
            cx, cy, gradRadius,
            intArrayOf(Color.argb(210, 10, 14, 22), Color.argb(245, 4, 6, 10)),
            floatArrayOf(0.0f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        spaceGradKey = gradKey
    }
    elementPaint.style = Paint.Style.FILL
    elementPaint.shader = spaceGradShader
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), elementPaint)
    elementPaint.shader = null

    // 2. Flight Telemetry Grid & Attitude Axis Crosshairs
    elementPaint.style = Paint.Style.STROKE
    elementPaint.strokeWidth = 1f * density
    elementPaint.color = Color.argb(24, 200, 220, 255)
    canvas.drawLine(cx - (380f * density / 2.6f), cy, cx + (380f * density / 2.6f), cy, elementPaint)
    canvas.drawLine(cx, cy - (380f * density / 2.6f), cx, cy + (380f * density / 2.6f), elementPaint)

    // Cockpit Gimbal & Geometry Resolution
    val gearShape = CockpitGimbalConfigCache.getGearShape(context)
    val unitScale = CockpitGimbalConfigCache.getUnitScale(context)
    val customDiameterScale = CockpitGimbalConfigCache.getCustomDiameterScale(context)
    val dGimbal = (unitScale / 2.6f) * customDiameterScale

    val rad0 = 310f * dGimbal
    val rad1 = 190f * dGimbal
    val radHub = 64f * dGimbal

    val r0Apps = getAppsForActiveGear(activeGearSetIndex, 0)
    val r1Apps = getAppsForActiveGear(activeGearSetIndex, 1)

    // Draw Outer Orbital Gimbal Ring (Ring 0, 16 Magnetic Cogs)
    drawSpaceshipGimbalRing(
        canvas = canvas,
        cx = cx,
        cy = cy,
        trackRadius = rad0,
        trackWidth = 48f * density / 2.6f,
        teethCount = 16,
        toothDepth = 16f * density / 2.6f,
        rotationDeg = gearRingRotations[0],
        isActive = (activeGearRing == 0),
        m3Primary = m3Primary,
        gearShape = gearShape,
        segmentCount = r0Apps.size.coerceAtLeast(6)
    )

    // Draw Inner Orbital Gimbal Ring (Ring 1, 12 Magnetic Cogs)
    drawSpaceshipGimbalRing(
        canvas = canvas,
        cx = cx,
        cy = cy,
        trackRadius = rad1,
        trackWidth = 42f * density / 2.6f,
        teethCount = 12,
        toothDepth = 14f * density / 2.6f,
        rotationDeg = gearRingRotations[1],
        isActive = (activeGearRing == 1),
        m3Primary = m3Primary,
        gearShape = gearShape,
        segmentCount = r1Apps.size.coerceAtLeast(6)
    )

    // Draw Central Holographic Warp Core / Cockpit Command Node (Ring 2) across ALL 8 styles
    if (gearShape != LightspeedPreferences.GEAR_SHAPE_CIRCLE) {
        DeepSpaceGearGeometry.buildGearRingPath(cx, cy, radHub * 1.08f, 0f, gearShape, gearRingPath)
        elementPaint.style = Paint.Style.FILL
        elementPaint.color = if (activeGearRing == 2) m3Primary else Color.argb(180, 20, 26, 40)
        canvas.drawPath(gearRingPath, elementPaint)
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (activeGearRing == 2) 2.6f * density else 1.6f * density
        elementPaint.color = if (activeGearRing == 2) Color.WHITE else m3Primary
        canvas.drawPath(gearRingPath, elementPaint)
    }
    drawHolographicReactorCore(
        canvas = canvas,
        cx = cx,
        cy = cy,
        coreRadius = if (gearShape == LightspeedPreferences.GEAR_SHAPE_CIRCLE) radHub else radHub * 0.88f,
        isActive = (activeGearRing == 2),
        m3Primary = m3Primary,
        density = density
    )

    // --- ORBITAL APP FLIGHT PODS & ICONS PASS ---
    val sizeRaw = (42f * density).toInt()

    var activeHighlightedCX = 0f
    var activeHighlightedCY = 0f
    var activeHighlightedBracketSize = 0f
    var activeHighlightedLabel: String? = null

    for (r in 0..1) {
        val radius = if (r == 0) rad0 else rad1
        val ringApps = getAppsForActiveGear(activeGearSetIndex, r)
        if (ringApps.isEmpty()) continue

        val count = ringApps.size
        val baseRotation = gearRingRotations[r]
        val isRingFocused = (activeGearRing == r)

        for (i in ringApps.indices) {
            val slotRad = Math.toRadians(i * (360.0 / count))
            val rotRad = Math.toRadians(baseRotation.toDouble())
            val (iconCX, iconCY) = DeepSpaceGearGeometry.pointOnGearPerimeter(cx, cy, radius, slotRad, rotRad, gearShape)
            val angleDeg = (baseRotation + i * (360f / count)) % 360f

            val isFlankAware = renderCacheReticleOrientation == LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK
            val targetFocusAngle = if (isFlankAware && isOpenedFromLeftFlank) 0f else 180f
            val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
            val angleDiff = abs(normalizedDeg - targetFocusAngle)
            val effectiveDiff = if (angleDiff > 180f) 360f - angleDiff else angleDiff
            val isHighlighted = isRingFocused && effectiveDiff < (180f / count)
            val currentScale = if (isHighlighted) 1.28f else 1.0f
            val currentSize = (sizeRaw * currentScale).toInt()

            val itemToken = ringApps[i]
            val appLabel = resolveCleanAppLabel(itemToken)

            if (isHighlighted) {
                activeHighlightedCX = iconCX
                activeHighlightedCY = iconCY
                activeHighlightedBracketSize = currentSize + 22f * density
                activeHighlightedLabel = appLabel
            }

            // 1. Orbital Flight Pod Socket Behind Icon
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isHighlighted) Color.argb(200, 22, 28, 44) else Color.argb(90, 16, 20, 30)
            canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isHighlighted) 2.4f * density else 1.2f * density
            highlightPaint.color = if (isHighlighted) m3Primary else Color.argb(55, 200, 220, 255)
            canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

            // 2. Icon Render Pass
            try {
                val iconDrawable = LightspeedIconManager.getCachedIconDrawable(itemToken)
                if (iconDrawable != null) {
                    iconDrawable.setBounds(
                        (iconCX - currentSize / 2).toInt(),
                        (iconCY - currentSize / 2).toInt(),
                        (iconCX + currentSize / 2).toInt(),
                        (iconCY + currentSize / 2).toInt()
                    )
                    iconDrawable.draw(canvas)
                } else {
                    val initial = appLabel.firstOrNull()?.uppercase() ?: "?"
                    textPaint.color = Color.WHITE
                    textPaint.textSize = currentSize * 0.44f
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textPaint.textAlign = Paint.Align.CENTER
                    canvas.drawText(initial, iconCX, iconCY + (currentSize * 0.16f), textPaint)
                    LightspeedIconManager.loadIconAsync(context, itemToken) {
                        postInvalidate()
                    }
                }
            } catch (e: Exception) { logSwallowed("LightspeedCruiseOverlayGearsDraw", "drawFavoritesGearsLayer:drawIcon", e) }
        }
    }

    // 3. Draw Flight Lock Targeting Reticle
    if (activeHighlightedLabel != null) {
        drawFlightLockReticle(
            canvas = canvas,
            targetCX = activeHighlightedCX,
            targetCY = activeHighlightedCY,
            bracketSize = activeHighlightedBracketSize,
            m3Primary = m3Primary,
            m3Secondary = m3Secondary,
            appName = activeHighlightedLabel,
            density = density,
            reticleStyle = renderCacheReticleStyle
        )
    }

    // Top Flight Telemetry HUD Header
    val topBadgeY = 54f * density
    val profileName = when (activeGearSetIndex) {
        0 -> getGearSetNameByIndex(0)
        1 -> getGearSetNameByIndex(1)
        2 -> getGearSetNameByIndex(2)
        else -> getGearSetNameByIndex(3)
    }

    val headerText = "◈ ASTROGATION // PROFILE $activeGearSetIndex: $profileName ◈"
    textPaint.textSize = 12f * density
    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    textPaint.textAlign = Paint.Align.CENTER
    val headerWidth = textPaint.measureText(headerText)

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(175, 12, 16, 28)
    cachedGearHeaderRect.set(cx - headerWidth / 2f - 16f * density, topBadgeY - 15f * density, cx + headerWidth / 2f + 16f * density, topBadgeY + 15f * density)
    canvas.drawRoundRect(cachedGearHeaderRect, 10f * density, 10f * density, highlightPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * density
    highlightPaint.color = Color.argb(80, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
    canvas.drawRoundRect(cachedGearHeaderRect, 10f * density, 10f * density, highlightPaint)

    textPaint.color = Color.WHITE
    canvas.drawText(headerText, cx, topBadgeY + 4f * density, textPaint)

    textPaint.textSize = 8.5f * density
    textPaint.typeface = android.graphics.Typeface.DEFAULT
    textPaint.color = Color.argb(160, 180, 210, 245)
    canvas.drawText(
        if (isOpenedFromLeftFlank) "PULL RIGHT >> SWITCH PROFILE  •  SLIDE LEFT << CANCEL"
        else "PULL LEFT >> SWITCH PROFILE  •  SLIDE RIGHT << CANCEL",
        cx, topBadgeY + 28f * density, textPaint
    )
    drawCruiseCentralPill(canvas, m3Primary)
}
