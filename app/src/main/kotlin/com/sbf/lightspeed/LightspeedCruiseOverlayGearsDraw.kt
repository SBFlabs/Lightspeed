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

internal const val GEAR_SET_TIP_COUNT: Int = 6
private val GEAR_SET_EXTRA_TIP_RES_IDS = intArrayOf(
    R.string.ck_gear_tip_2,
    R.string.ck_gear_tip_3,
    R.string.ck_gear_tip_4,
    R.string.ck_gear_tip_5,
    R.string.ck_gear_tip_6
)

internal data class LiveGearGeometry(
    val cx: Float,
    val cy: Float,
    val rad0: Float,
    val rad1: Float,
    val radHub: Float,
    val visualOuterR: Float,
    val isLandscape: Boolean,
    val iconScale: Float = 1f,
    val fitScale: Float = 1f
)

internal fun LightspeedCruiseOverlay.resolveLiveGearGeometry(): LiveGearGeometry {
    val density = resources.displayMetrics.density
    val wF = width.toFloat()
    val hF = height.toFloat()
    val isLandscape = wF > hF

    val unitScale = CockpitGimbalConfigCache.getUnitScale(context)
    val customDiameterScale = CockpitGimbalConfigCache.getCustomDiameterScale(context)
    val dGimbal = (unitScale / 2.6f) * customDiameterScale

    val rawRad0 = 310f * dGimbal
    val rawRad1 = 190f * dGimbal
    val rawRadHub = 64f * dGimbal
    val iconPad = (42f * density * 0.64f) + (14f * density)

    if (!isLandscape) {
        return LiveGearGeometry(
            cx = wF / 2f,
            cy = hF / 2f,
            rad0 = rawRad0,
            rad1 = rawRad1,
            radHub = rawRadHub,
            visualOuterR = rawRad0 + iconPad,
            isLandscape = false
        )
    }

    val bottomInset = getHangarBottomInsetPx()
    val topReserve = 44f * density
    val bottomReserve = bottomInset + 38f * density
    val availH = (hF - topReserve - bottomReserve).coerceAtLeast(108f * density)
    val cy = topReserve + (availH * 0.5f)
    val maxVertRad0 = (availH * 0.5f) - (iconPad * 0.85f)
    val maxHorizRad0 = (wF * 0.42f) - iconPad
    val maxLandscapeRad0 = minOf(maxVertRad0, maxHorizRad0).coerceAtLeast(48f * density)
    val fitScale = if (rawRad0 > maxLandscapeRad0) (maxLandscapeRad0 / rawRad0).coerceIn(0.45f, 1f) else 1f
    val rad0 = rawRad0 * fitScale
    val rad1 = (rawRad1 * fitScale).coerceAtMost(rad0 * 0.64f)
    val radHub = (rawRadHub * fitScale).coerceAtMost(rad1 * 0.48f)
    val iconScale = if (fitScale < 0.85f) fitScale.coerceIn(0.72f, 1f) else 1f
    val visualOuterR = rad0 + (iconPad * iconScale)

    return LiveGearGeometry(
        cx = wF * 0.5f,
        cy = cy,
        rad0 = rad0,
        rad1 = rad1,
        radHub = radHub,
        visualOuterR = visualOuterR,
        isLandscape = true,
        iconScale = iconScale,
        fitScale = fitScale
    )
}

internal fun LightspeedCruiseOverlay.drawFavoritesGearsLayer(canvas: Canvas, m3Primary: Int, m3Secondary: Int) {
    val density = resources.displayMetrics.density
    val geom = resolveLiveGearGeometry()
    val cx = geom.cx
    val cy = geom.cy

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
    val crossHalf = minOf(380f * density / 2.6f, geom.rad0 * 1.15f)
    canvas.drawLine(cx - crossHalf, cy, cx + crossHalf, cy, elementPaint)
    canvas.drawLine(cx, cy - crossHalf, cx, cy + crossHalf, elementPaint)

    // Cockpit Gimbal & Geometry Resolution
    val gearShape = CockpitGimbalConfigCache.getGearShape(context)
    val rad0 = geom.rad0
    val rad1 = geom.rad1
    val radHub = geom.radHub

    val r0Apps = getAppsForActiveGear(activeGearSetIndex, 0)
    val r1Apps = getAppsForActiveGear(activeGearSetIndex, 1)

    val trackScale = if (geom.isLandscape) geom.fitScale.coerceIn(0.76f, 1f) else 1f

    // Draw Outer Orbital Gimbal Ring (Ring 0, 16 Magnetic Cogs)
    drawSpaceshipGimbalRing(
        canvas = canvas,
        cx = cx,
        cy = cy,
        trackRadius = rad0,
        trackWidth = (48f * density / 2.6f) * trackScale,
        teethCount = 16,
        toothDepth = (16f * density / 2.6f) * trackScale,
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
        trackWidth = (42f * density / 2.6f) * trackScale,
        teethCount = 12,
        toothDepth = (14f * density / 2.6f) * trackScale,
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
    val sizeRaw = (42f * density * geom.iconScale).toInt()

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
                    textPaint.typeface = overlayTypeface(isBold = true)
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

    // Top Flight Telemetry HUD Header (base sizes matched to Cockpit Hangar header & subtext)
    val ts = deepSpaceRenderer.hangarTextScale(context)
    val wF = width.toFloat()
    val hF = height.toFloat()
    val isLandscape = geom.isLandscape
    val bottomInset = getHangarBottomInsetPx()
    val hudCx = cx
    val badgeHalfH = (if (isLandscape) 12.5f else 14f) * density * ts.coerceAtLeast(1f)
    val profileName = when (activeGearSetIndex) {
        0 -> getGearSetNameByIndex(0)
        1 -> getGearSetNameByIndex(1)
        2 -> getGearSetNameByIndex(2)
        else -> getGearSetNameByIndex(3)
    }

    val headerText = com.sbf.lightspeed.system.lsString(R.string.ck_profile_header, activeGearSetIndex, profileName)
        .ifBlank { "◈ ASTROGATION // PROFILE $activeGearSetIndex: $profileName ◈" }
    val targetHeaderSize = (if (isLandscape) 12.6f else 14.5f) * density * ts
    textPaint.textSize = targetHeaderSize
    textPaint.typeface = overlayTypeface(isBold = true)
    textPaint.textAlign = Paint.Align.CENTER
    val maxHeaderTextW = (wF - 48f * density).coerceAtLeast(120f * density)
    val measuredHeaderW = textPaint.measureText(headerText)
    if (measuredHeaderW > maxHeaderTextW) {
        textPaint.textSize = targetHeaderSize * (maxHeaderTextW / measuredHeaderW)
    }
    val headerWidth = textPaint.measureText(headerText)
    val topBadgeY = if (isLandscape) {
        val gearVisualTop = cy - rad0 - (sizeRaw * 0.64f)
        val maxCenterY = (gearVisualTop - badgeHalfH - 5f * density).coerceAtLeast(badgeHalfH + 6f * density)
        (gearVisualTop * 0.5f).coerceIn(badgeHalfH + 6f * density, maxCenterY)
    } else {
        54f * density
    }

    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(175, 12, 16, 28)
    cachedGearHeaderRect.set(hudCx - headerWidth / 2f - 16f * density, topBadgeY - badgeHalfH, hudCx + headerWidth / 2f + 16f * density, topBadgeY + badgeHalfH)
    canvas.drawRoundRect(cachedGearHeaderRect, 10f * density, 10f * density, highlightPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 1.2f * density
    highlightPaint.color = Color.argb(80, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
    canvas.drawRoundRect(cachedGearHeaderRect, 10f * density, 10f * density, highlightPaint)

    textPaint.color = Color.WHITE
    canvas.drawText(headerText, hudCx, topBadgeY + 4f * density * ts, textPaint)

    val tipIdx = ((gearSetTipIndex % GEAR_SET_TIP_COUNT) + GEAR_SET_TIP_COUNT) % GEAR_SET_TIP_COUNT
    val subText = if (tipIdx == 0) {
        if (isOpenedFromLeftFlank) {
            com.sbf.lightspeed.system.lsString(R.string.ck_gear_tip_1_left).ifBlank {
                com.sbf.lightspeed.system.lsString(R.string.ck_sub_from_left)
            }
        } else {
            com.sbf.lightspeed.system.lsString(R.string.ck_gear_tip_1_right).ifBlank {
                com.sbf.lightspeed.system.lsString(R.string.ck_sub_from_right)
            }
        }
    } else {
        com.sbf.lightspeed.system.lsString(GEAR_SET_EXTRA_TIP_RES_IDS[tipIdx - 1])
    }
    val maxSubW = if (isLandscape) (wF - 48f * density).coerceAtLeast(140f * density) else (wF - 24f * density).coerceAtLeast(120f * density)
    val subTipSize = (if (isLandscape) 11.2f else 13.2f) * density * ts.coerceIn(0.88f, 1.20f)
    val tipTopY = if (isLandscape) {
        val gearVisualBottom = cy + rad0 + (sizeRaw * 0.64f)
        (gearVisualBottom + 6f * density).coerceAtMost(hF - bottomInset - 22f * density)
    } else {
        topBadgeY + badgeHalfH + 8f * density
    }
    val tipMaxBottomY = if (isLandscape) (hF - bottomInset - 4f * density) else (cy - rad0 - (sizeRaw * 0.64f) - (10f * density))
    drawLiveGearRotatingTip(
        canvas = canvas,
        tipBody = subText,
        cx = hudCx,
        topY = tipTopY,
        maxBottomY = tipMaxBottomY,
        maxWidth = maxSubW,
        density = density,
        baseTextSize = subTipSize,
        accentColor = m3Primary,
        boldTypeface = overlayTypeface(isBold = true),
        regularTypeface = overlayTypeface(isBold = false)
    )
    drawCruiseCentralPill(canvas, m3Primary)
}
