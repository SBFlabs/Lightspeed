package com.sbf.lightspeed

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * CruiseHangarRenderer
 *
 * Extracted renderer for the COCKPIT_HANGAR layer of LightspeedCruiseOverlay.
 *
 * Accepts the overlay's shared Paint objects and live state references so no
 * data is duplicated. LightspeedCruiseOverlay delegates the COCKPIT_HANGAR
 * onDraw block, drawSpaceshipGimbalRing, drawFlightLockReticle,
 * drawHolographicReactorCore, drawCosmicStarfield, drawGalacticNebula, and
 * drawHyperdriveWarpSurge to this class.
 *
 * All drawing methods are internal to this class. LightspeedCruiseOverlay calls
 * only drawHangar(canvas, ...) and the two warp methods.
 */
internal class CruiseHangarRenderer(
    private val textPaint: Paint,
    private val elementPaint: Paint,
    private val highlightPaint: Paint
) {

    // -------------------------------------------------------------------------
    // Warp launch state — written by LightspeedCruiseOverlay, read here
    // -------------------------------------------------------------------------
    var isWarpLaunching = false
    var warpStartTime = 0L
    var warpFocalPointX = 0f
    var warpFocalPointY = 0f

    // -------------------------------------------------------------------------
    // Public entry point — called from LightspeedCruiseOverlay.onDraw
    // -------------------------------------------------------------------------

    /**
     * Renders the full COCKPIT_HANGAR screen onto [canvas].
     *
     * All parameters are primitive/value types or read-only data already held by
     * the overlay; no mutable overlay state is written from inside this method.
     */
    fun drawHangar(
        canvas: Canvas,
        context: Context,
        screenW: Float,
        screenH: Float,
        d: Float,
        m3Primary: Int,
        m3Secondary: Int,
        isOpenedFromLeftFlank: Boolean,
        activeGearSetIndex: Int,
        activeHangarRing: Int,
        isHangarEjectArmed: Boolean,
        gearRingRotations: FloatArray,
        hangarBayScrollOffset: Float,
        setsList: List<String>,
        resolveCleanAppLabel: (String) -> String,
        getAppsForActiveGear: (setIndex: Int, ringIndex: Int) -> List<String>,
        getFlankLaunchBehavior: (Boolean) -> String
    ) {
        val cx = screenW / 2f
        val cy = screenH / 2f
        val deckW = (screenW * 0.92f).coerceAtMost(480f * d)
        val leftX = cx - (deckW / 2f)
        val rightX = cx + (deckW / 2f)
        val gap = 8f * d

        val topHangarY = (screenH * 0.07f).coerceAtLeast(54f * d)

        // 1. Deep Space Astrogation Hangar Void with Starfield
        canvas.drawColor(Color.argb(245, 6, 8, 14))
        drawCosmicStarfield(canvas, screenW, screenH, d, 0.75f)

        // 2. Flight Telemetry Header
        val hangarHeader = "◈ ASTROGATION COCKPIT // HANGAR DECK ◈"
        textPaint.textSize = 12.5f * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        val headerWidth = textPaint.measureText(hangarHeader)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(175, 12, 16, 28)
        val headerRect = RectF(cx - headerWidth / 2f - 18f * d, topHangarY - 16f * d, cx + headerWidth / 2f + 18f * d, topHangarY + 16f * d)
        canvas.drawRoundRect(headerRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = Color.argb(90, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(headerRect, 10f * d, 10f * d, highlightPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(hangarHeader, cx, topHangarY + 4.5f * d, textPaint)

        val r0Y = topHangarY + 40f * d
        val r1Y = r0Y + 38f * d
        val r2Y = r1Y + 44f * d

        // ROW 0: FLANK SELECTOR
        val halfBtnW = (deckW - gap) / 2f
        val portRect = RectF(leftX, r0Y - 16f * d, leftX + halfBtnW, r0Y + 16f * d)
        val starboardRect = RectF(rightX - halfBtnW, r0Y - 16f * d, rightX, r0Y + 16f * d)

        val isPort = isOpenedFromLeftFlank
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isPort) Color.argb(190, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(60, 20, 26, 40)
        canvas.drawRoundRect(portRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isPort) 1.5f * d else 0.8f * d
        highlightPaint.color = if (isPort) m3Primary else Color.argb(50, 200, 220, 255)
        canvas.drawRoundRect(portRect, 10f * d, 10f * d, highlightPaint)
        textPaint.textSize = 10f * d
        textPaint.color = if (isPort) Color.WHITE else Color.argb(160, 200, 220, 255)
        canvas.drawText("◀ PORT (LEFT FLANK)", portRect.centerX(), portRect.centerY() + 3.5f * d, textPaint)

        val isStarboard = !isOpenedFromLeftFlank
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isStarboard) Color.argb(190, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(60, 20, 26, 40)
        canvas.drawRoundRect(starboardRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isStarboard) 1.5f * d else 0.8f * d
        highlightPaint.color = if (isStarboard) m3Primary else Color.argb(50, 200, 220, 255)
        canvas.drawRoundRect(starboardRect, 10f * d, 10f * d, highlightPaint)
        textPaint.color = if (isStarboard) Color.WHITE else Color.argb(160, 200, 220, 255)
        canvas.drawText("STARBOARD (RIGHT FLANK) ▶", starboardRect.centerX(), starboardRect.centerY() + 3.5f * d, textPaint)

        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val launchBehavior = getFlankLaunchBehavior(isOpenedFromLeftFlank)
        val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
        val hapticStrength = prefs.getString("pref_gear_haptic_strength", "tactical") ?: "tactical"

        // ROW 1: STARTUP DEFAULT MODE
        val btn1Rect = RectF(leftX, r1Y - 16f * d, leftX + halfBtnW, r1Y + 16f * d)
        val btn2Rect = RectF(rightX - halfBtnW, r1Y - 16f * d, rightX, r1Y + 16f * d)

        val isDef = (launchBehavior == "default")
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isDef) Color.argb(180, 24, 34, 56) else Color.argb(50, 16, 20, 32)
        canvas.drawRoundRect(btn1Rect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isDef) 1.4f * d else 0.8f * d
        highlightPaint.color = if (isDef) m3Secondary else Color.argb(40, 200, 220, 255)
        canvas.drawRoundRect(btn1Rect, 9f * d, 9f * d, highlightPaint)
        textPaint.textSize = 9.5f * d
        textPaint.color = if (isDef) Color.WHITE else Color.argb(150, 200, 220, 255)
        canvas.drawText("STARTUP: ALWAYS FIRST", btn1Rect.centerX(), btn1Rect.centerY() + 3.5f * d, textPaint)

        val isLast = (launchBehavior == "last")
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isLast) Color.argb(180, 24, 34, 56) else Color.argb(50, 16, 20, 32)
        canvas.drawRoundRect(btn2Rect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isLast) 1.4f * d else 0.8f * d
        highlightPaint.color = if (isLast) m3Secondary else Color.argb(40, 200, 220, 255)
        canvas.drawRoundRect(btn2Rect, 9f * d, 9f * d, highlightPaint)
        textPaint.color = if (isLast) Color.WHITE else Color.argb(150, 200, 220, 255)
        canvas.drawText("STARTUP: RESUME LAST", btn2Rect.centerX(), btn2Rect.centerY() + 3.5f * d, textPaint)

        // ROW 2: DOCKING BAYS (HORIZONTALLY SCROLLABLE RAIL)
        var activeGearSetIndexLocal = activeGearSetIndex
        if (activeGearSetIndexLocal >= setsList.size) { activeGearSetIndexLocal = 0 }
        val currentSetId = if (activeGearSetIndexLocal in setsList.indices) setsList[activeGearSetIndexLocal] else "0"

        val bayCount = setsList.size + 1
        val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
        val totalBayRailW = bayCount * slotW
        val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
        val railStartX = if (totalBayRailW <= deckW) (leftX + (deckW - totalBayRailW) / 2f) else (leftX + hangarBayScrollOffset.coerceIn(-maxScroll, 0f))

        canvas.save()
        canvas.clipRect(leftX - 4f * d, r2Y - 24f * d, rightX + 4f * d, r2Y + 24f * d)

        for (g in setsList.indices) {
            val setId = setsList[g]
            val defaultName = when (setId) { "0" -> "POWER"; "1" -> "STORES"; "2" -> "UTILITY"; "3" -> "MEDIA"; else -> "SET" }
            val setName = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
            val btnX = railStartX + (g + 0.5f) * slotW
            val isCurrentBay = (activeGearSetIndexLocal == g)

            val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isCurrentBay) Color.argb(190, 24, 32, 54) else Color.argb(70, 14, 18, 28)
            canvas.drawRoundRect(bayRect, 10f * d, 10f * d, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isCurrentBay) 1.8f * d else 0.8f * d
            highlightPaint.color = if (isCurrentBay) m3Primary else Color.argb(40, 200, 220, 255)
            canvas.drawRoundRect(bayRect, 10f * d, 10f * d, highlightPaint)

            val shortLabel = "${g + 1}: ${setName.take(6)}"
            textPaint.textSize = 10.5f * d
            textPaint.typeface = if (isCurrentBay) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            textPaint.color = if (isCurrentBay) Color.WHITE else Color.argb(170, 200, 220, 255)
            canvas.drawText(shortLabel, btnX, r2Y + 4f * d, textPaint)
        }

        // Plus button to add profile set
        val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
        val plusRect = RectF(plusBtnX - slotW * 0.42f, r2Y - 18f * d, plusBtnX + slotW * 0.42f, r2Y + 18f * d)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(70, 20, 26, 40)
        canvas.drawRoundRect(plusRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * d
        highlightPaint.color = Color.argb(60, 200, 220, 255)
        canvas.drawRoundRect(plusRect, 10f * d, 10f * d, highlightPaint)
        textPaint.textSize = 15f * d
        textPaint.color = Color.WHITE
        canvas.drawText("+", plusBtnX, r2Y + 5.5f * d, textPaint)

        canvas.restore()

        // Scroll indicator arrows
        if (maxScroll > 0f) {
            if (hangarBayScrollOffset < -4f * d) {
                textPaint.textSize = 9f * d
                textPaint.color = Color.argb(130, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
                canvas.drawText("◀", leftX - 1f * d, r2Y + 3.5f * d, textPaint)
            }
            if (hangarBayScrollOffset > -maxScroll + 4f * d) {
                textPaint.textSize = 9f * d
                textPaint.color = Color.argb(130, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
                canvas.drawText("▶", rightX + 1f * d, r2Y + 3.5f * d, textPaint)
            }
        }

        // ROW 3: REORDER & DELETE ACTIVE PROFILE
        val r3Y = r2Y + 44f * d
        val shiftW = deckW * 0.35f
        val deleteW = deckW * 0.26f
        val shiftLeftRect = RectF(leftX, r3Y - 16f * d, leftX + shiftW, r3Y + 16f * d)
        val deleteBayRect = RectF(cx - deleteW / 2f, r3Y - 16f * d, cx + deleteW / 2f, r3Y + 16f * d)
        val shiftRightRect = RectF(rightX - shiftW, r3Y - 16f * d, rightX, r3Y + 16f * d)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(80, 20, 26, 40)
        canvas.drawRoundRect(shiftLeftRect, 8f * d, 8f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * d
        highlightPaint.color = Color.argb(60, 200, 220, 255)
        canvas.drawRoundRect(shiftLeftRect, 8f * d, 8f * d, highlightPaint)
        textPaint.textSize = 10f * d
        textPaint.color = if (activeGearSetIndexLocal > 0) m3Secondary else Color.argb(70, 150, 150, 150)
        canvas.drawText("◀ SHIFT LEFT", shiftLeftRect.centerX(), shiftLeftRect.centerY() + 3.5f * d, textPaint)

        val canDelete = setsList.size > 1
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (canDelete) Color.argb(60, 239, 83, 80) else Color.argb(20, 60, 60, 60)
        canvas.drawRoundRect(deleteBayRect, 8f * d, 8f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * d
        highlightPaint.color = if (canDelete) Color.argb(160, 239, 83, 80) else Color.argb(30, 100, 100, 100)
        canvas.drawRoundRect(deleteBayRect, 8f * d, 8f * d, highlightPaint)
        textPaint.textSize = 9.5f * d
        textPaint.color = if (canDelete) Color.parseColor("#FFCDD2") else Color.argb(60, 150, 150, 150)
        canvas.drawText("🗑 DELETE", deleteBayRect.centerX(), deleteBayRect.centerY() + 3.5f * d, textPaint)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(80, 20, 26, 40)
        canvas.drawRoundRect(shiftRightRect, 8f * d, 8f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * d
        highlightPaint.color = Color.argb(60, 200, 220, 255)
        canvas.drawRoundRect(shiftRightRect, 8f * d, 8f * d, highlightPaint)
        textPaint.textSize = 10f * d
        textPaint.color = if (activeGearSetIndexLocal < setsList.size - 1) m3Secondary else Color.argb(70, 150, 150, 150)
        canvas.drawText("SHIFT RIGHT ▶", shiftRightRect.centerX(), shiftRightRect.centerY() + 3.5f * d, textPaint)

        // ROW 4: ICON THEME CAROUSEL
        val r4Y = r3Y + 42f * d
        val activePack = com.sbf.lightspeed.system.LightspeedIconManager.getActiveIconPack(context)
        val availablePacks = com.sbf.lightspeed.system.LightspeedIconManager.getAvailableIconPacks(context)
        val currentPackLabel = availablePacks.firstOrNull { it.packageName == activePack }?.label ?: "System Default"
        val iconThemeRect = RectF(leftX, r4Y - 18f * d, rightX, r4Y + 18f * d)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(90, 16, 22, 36)
        canvas.drawRoundRect(iconThemeRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1f * d
        highlightPaint.color = Color.argb(70, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
        canvas.drawRoundRect(iconThemeRect, 10f * d, 10f * d, highlightPaint)
        textPaint.textSize = 11f * d
        textPaint.color = Color.WHITE
        canvas.drawText("THEME: $currentPackLabel  [ ➔ CYCLE ]", iconThemeRect.centerX(), iconThemeRect.centerY() + 4f * d, textPaint)

        // ROW 5: FLIGHT MOMENTUM & HAPTICS
        val r5Y = r4Y + 42f * d
        val physW = (deckW - (gap * 2)) / 3f
        val phys1Rect = RectF(leftX, r5Y - 16f * d, leftX + physW, r5Y + 16f * d)
        val phys2Rect = RectF(leftX + physW + gap, r5Y - 16f * d, leftX + physW * 2 + gap, r5Y + 16f * d)
        val phys3Rect = RectF(rightX - physW, r5Y - 16f * d, rightX, r5Y + 16f * d)

        fun drawPill(rect: RectF, label: String, isSelected: Boolean) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isSelected) Color.argb(170, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(50, 18, 22, 34)
            canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isSelected) 1.3f * d else 0.7f * d
            highlightPaint.color = if (isSelected) m3Primary else Color.argb(40, 200, 220, 255)
            canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
            textPaint.textSize = 10f * d
            textPaint.color = if (isSelected) Color.WHITE else Color.argb(150, 200, 220, 255)
            canvas.drawText(label, rect.centerX(), rect.centerY() + 3.5f * d, textPaint)
        }

        drawPill(phys1Rect, "⚡ SNAPPY", physicsProfile == "magnetic")
        drawPill(phys2Rect, "🌊 FLUID", physicsProfile == "fluid")
        drawPill(phys3Rect, "🚀 HEAVY", physicsProfile == "heavy")

        val r6Y = r5Y + 38f * d
        val hapW = (deckW - (gap * 3)) / 4f
        val hap1Rect = RectF(leftX, r6Y - 15f * d, leftX + hapW, r6Y + 15f * d)
        val hap2Rect = RectF(leftX + (hapW + gap), r6Y - 15f * d, leftX + (hapW + gap) + hapW, r6Y + 15f * d)
        val hap3Rect = RectF(leftX + (hapW + gap) * 2, r6Y - 15f * d, leftX + (hapW + gap) * 2 + hapW, r6Y + 15f * d)
        val hap4Rect = RectF(rightX - hapW, r6Y - 15f * d, rightX, r6Y + 15f * d)

        drawPill(hap1Rect, "SUBTLE", hapticStrength == "subtle")
        drawPill(hap2Rect, "TACTICAL", hapticStrength == "tactical")
        drawPill(hap3Rect, "HEAVY", hapticStrength == "heavy")
        drawPill(hap4Rect, "OFF", hapticStrength == "off")

        // ROW 7: RETICLE CROSSHAIR STYLE
        val r7Y = r6Y + 35f * d
        val retW = (deckW - (gap * 3)) / 4f
        val ret1Rect = RectF(leftX, r7Y - 14f * d, leftX + retW, r7Y + 14f * d)
        val ret2Rect = RectF(leftX + (retW + gap), r7Y - 14f * d, leftX + (retW + gap) + retW, r7Y + 14f * d)
        val ret3Rect = RectF(leftX + (retW + gap) * 2, r7Y - 14f * d, leftX + (retW + gap) * 2 + retW, r7Y + 14f * d)
        val ret4Rect = RectF(rightX - retW, r7Y - 14f * d, rightX, r7Y + 14f * d)

        val reticleStyle = prefs.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
        drawPill(ret1Rect, "[ ] TACTICAL", reticleStyle == "tactical")
        drawPill(ret2Rect, "( ) CYBER", reticleStyle == "cyber")
        drawPill(ret3Rect, "+ CROSS", reticleStyle == "cross")
        drawPill(ret4Rect, "◇ DIAMOND", reticleStyle == "diamond")

        // 8. Live Dual Gimbal Assembly Preview & Active Ring Command Core
        val cyGimbal = (r7Y + 160f * d).coerceAtLeast(screenH * 0.62f)
        val radOuter = 135f * d
        val radInner = 84f * d
        val radHub = 32f * d

        drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radOuter, 26f * d, 16, 7.5f * d, gearRingRotations[0], (activeHangarRing == 0), m3Primary)
        drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radInner, 20f * d, 10, 5.5f * d, gearRingRotations[1], (activeHangarRing == 1), m3Primary)

        val r0 = getAppsForActiveGear(activeGearSetIndexLocal, 0)
        val r1 = getAppsForActiveGear(activeGearSetIndexLocal, 1)
        val activeAppsList = if (activeHangarRing == 0) r0 else r1
        val sizeRaw = (38f * d).toInt()

        var focusedAppLabel: String? = null

        // Draw Real App Icons on both rings
        for (r in 0..1) {
            val radius = if (r == 0) radOuter else radInner
            val ringApps = if (r == 0) r0 else r1
            if (ringApps.isEmpty()) continue

            val count = ringApps.size
            val baseRotation = gearRingRotations[r]
            val isRingFocused = (activeHangarRing == r)

            for (i in ringApps.indices) {
                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                val angleRad = Math.toRadians(angleDeg.toDouble())

                val iconCX = cx + radius * cos(angleRad).toFloat()
                val iconCY = cyGimbal + radius * sin(angleRad).toFloat()

                val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
                val isHighlighted = isRingFocused && abs(normalizedDeg - 180f) < (180f / count)
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
                    val dIcon = com.sbf.lightspeed.system.LightspeedIconManager.getIconDrawable(context, itemToken)
                    if (dIcon != null) {
                        dIcon.alpha = if (isRingFocused) (if (isHighlighted) (if (isEjectArmedHere) 140 else 255) else 220) else 140
                        val iconLeft = (iconCX - currentSize / 2f).toInt()
                        val iconTop = (iconCY - currentSize / 2f).toInt()
                        dIcon.setBounds(iconLeft, iconTop, iconLeft + currentSize, iconTop + currentSize)
                        dIcon.draw(canvas)
                    }
                } catch (_: Exception) {}

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

        // Targeting Reticle Collimator at 180 deg
        val reticleX = cx - (if (activeHangarRing == 0) radOuter else radInner)
        val reticleY = cyGimbal
        val reticleColor = if (isHangarEjectArmed) Color.argb(255, 255, 60, 60)
                           else if (activeHangarRing == 0) m3Primary
                           else m3Secondary

        val bracketSize = 54f * d
        val half = bracketSize / 2f
        val armLen = bracketSize * 0.28f

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isHangarEjectArmed) 2.4f * d else 1.8f * d
        elementPaint.color = reticleColor

        when (reticleStyle) {
            "cyber" -> {
                val arcRect = RectF(reticleX - half, reticleY - half, reticleX + half, reticleY + half)
                canvas.drawArc(arcRect, 35f, 110f, false, elementPaint)
                canvas.drawArc(arcRect, 215f, 110f, false, elementPaint)
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
                val path = Path().apply {
                    moveTo(reticleX, reticleY - dOffset)
                    lineTo(reticleX + dOffset, reticleY)
                    lineTo(reticleX, reticleY + dOffset)
                    lineTo(reticleX - dOffset, reticleY)
                    close()
                }
                elementPaint.strokeWidth = 1.8f * d
                canvas.drawPath(path, elementPaint)
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

        // Central Command Reactor Core
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(210, 16, 22, 38)
        canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.6f * d
        highlightPaint.color = activeRingColor
        canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)
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
        val shiftBarY = cyGimbal + radOuter + 26f * d
        val transferBarY = cyGimbal + radOuter + 58f * d

        if (focusedAppLabel != null) {
            if (activeAppsList.size > 1) {
                val shiftLeftRect = RectF(cx - 150f * d, shiftBarY - 14f * d, cx - 60f * d, shiftBarY + 14f * d)
                val shiftRightRect = RectF(cx + 60f * d, shiftBarY - 14f * d, cx + 150f * d, shiftBarY + 14f * d)

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

                val targetBadgeRect = RectF(cx - 56f * d, shiftBarY - 14f * d, cx + 56f * d, shiftBarY + 14f * d)
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
                    canvas.drawText("🚨 TAP ✕ TO EJECT", cx, shiftBarY + 3.5f * d, textPaint)
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
                val singleTargetRect = RectF(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)
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
                    canvas.drawText("🚨 TAP ✕ TO EJECT", cx, shiftBarY + 3.5f * d, textPaint)
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
            val transferRect = RectF(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
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
        val exitBtnRect = RectF(cx - (deckW * 0.42f), screenH - 58f * d, cx + (deckW * 0.42f), screenH - 18f * d)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(140, 24, 28, 40)
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = Color.argb(80, 200, 220, 255)
        canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
        textPaint.textSize = 11.5f * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText("✕ CLOSE HANGAR / RETURN", cx, exitBtnRect.centerY() + 4f * d, textPaint)
    }

    // -------------------------------------------------------------------------
    // Warp surge animation — called from LightspeedCruiseOverlay.onDraw
    // -------------------------------------------------------------------------

    fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) {
        if (!isWarpLaunching) return
        val elapsed = (System.currentTimeMillis() - warpStartTime).toFloat()
        val progress = (elapsed / 130f).coerceIn(0f, 1f)
        val easeProgress = progress * progress

        val cx = warpFocalPointX
        val cy = warpFocalPointY

        val shockwaveR = easeProgress * 340f * density
        val shockAlpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = (4.0f * (1f - progress) + 1.0f) * density
        elementPaint.color = Color.argb(shockAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawCircle(cx, cy, shockwaveR, elementPaint)

        elementPaint.strokeWidth = 1.4f * density
        elementPaint.color = Color.argb((shockAlpha * 0.75f).toInt(), 0, 229, 255)
        canvas.drawCircle(cx, cy, shockwaveR * 0.84f, elementPaint)

        val lineCount = 36
        val angleStep = (2 * Math.PI) / lineCount
        elementPaint.style = Paint.Style.STROKE

        for (i in 0 until lineCount) {
            val angle = i * angleStep
            val rStart = (easeProgress * 35f * density) + (i % 4) * 8f * density
            val streakLength = (easeProgress * 230f * density) + (i % 3) * 35f * density
            val rEnd = rStart + streakLength

            val x1 = cx + (rStart * cos(angle)).toFloat()
            val y1 = cy + (rStart * sin(angle)).toFloat()
            val x2 = cx + (rEnd * cos(angle)).toFloat()
            val y2 = cy + (rEnd * sin(angle)).toFloat()

            val lineAlpha = ((1f - progress) * (180 + (i * 13) % 75)).toInt().coerceIn(0, 255)
            elementPaint.strokeWidth = if (i % 3 == 0) 2.4f * density else 1.2f * density
            elementPaint.color = when (i % 3) {
                0 -> Color.argb(lineAlpha, 255, 255, 255)
                1 -> Color.argb(lineAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                else -> Color.argb(lineAlpha, 0, 229, 255)
            }
            canvas.drawLine(x1, y1, x2, y2, elementPaint)
        }

        val flashRadius = (1f - progress) * 50f * density
        val flashAlpha = ((1f - progress) * 230).toInt().coerceIn(0, 255)
        elementPaint.style = Paint.Style.FILL
        elementPaint.color = Color.argb(flashAlpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, flashRadius, elementPaint)
    }

    // -------------------------------------------------------------------------
    // Shared drawing primitives (also used by LightspeedCruiseOverlay directly)
    // -------------------------------------------------------------------------

    fun drawSpaceshipGimbalRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        trackRadius: Float,
        trackWidth: Float,
        teethCount: Int,
        toothDepth: Float,
        rotationDeg: Float,
        isActive: Boolean,
        m3Primary: Int
    ) {
        val innerR = trackRadius - trackWidth / 2f
        val outerR = trackRadius + trackWidth / 2f

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = trackWidth
        elementPaint.color = if (isActive) Color.argb(45, 255, 255, 255) else Color.argb(15, 255, 255, 255)
        canvas.drawCircle(cx, cy, trackRadius, elementPaint)

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 2.2f else 1.2f
        elementPaint.color = if (isActive) m3Primary else Color.argb(45, 200, 210, 230)
        canvas.drawCircle(cx, cy, innerR, elementPaint)
        canvas.drawCircle(cx, cy, outerR, elementPaint)

        val totalTicks = teethCount * 2
        val tickAngleStep = 360.0 / totalTicks
        val rotRad = Math.toRadians(rotationDeg.toDouble())
        elementPaint.style = Paint.Style.STROKE

        for (i in 0 until totalTicks) {
            val angleRad = Math.toRadians(i * tickAngleStep) + rotRad
            val isMajor = (i % 2 == 0)
            val tickLen = if (isMajor) (trackWidth * 0.35f) else (trackWidth * 0.18f)
            val tickR1 = outerR - tickLen
            val tickR2 = outerR

            val x1 = cx + tickR1 * cos(angleRad).toFloat()
            val y1 = cy + tickR1 * sin(angleRad).toFloat()
            val x2 = cx + tickR2 * cos(angleRad).toFloat()
            val y2 = cy + tickR2 * sin(angleRad).toFloat()

            elementPaint.strokeWidth = if (isMajor) 1.8f else 1.0f
            elementPaint.color = if (isActive) (if (isMajor) Color.argb(180, 255, 255, 255) else Color.argb(100, 255, 255, 255))
                                 else (if (isMajor) Color.argb(70, 200, 210, 230) else Color.argb(30, 200, 210, 230))
            canvas.drawLine(x1, y1, x2, y2, elementPaint)
        }

        val gearPath = Path()
        val toothAngleStep = (2.0 * Math.PI) / teethCount
        val toothInnerR = outerR - (toothDepth * 0.25f)
        val toothOuterR = outerR + toothDepth

        for (i in 0 until teethCount) {
            val angle = i * toothAngleStep + rotRad
            val p1X = cx + toothInnerR * cos(angle).toFloat()
            val p1Y = cy + toothInnerR * sin(angle).toFloat()
            if (i == 0) gearPath.moveTo(p1X, p1Y) else gearPath.lineTo(p1X, p1Y)

            val p2X = cx + toothOuterR * cos(angle + toothAngleStep * 0.15).toFloat()
            val p2Y = cy + toothOuterR * sin(angle + toothAngleStep * 0.15).toFloat()
            gearPath.lineTo(p2X, p2Y)

            val p3X = cx + toothOuterR * cos(angle + toothAngleStep * 0.35).toFloat()
            val p3Y = cy + toothOuterR * sin(angle + toothAngleStep * 0.35).toFloat()
            gearPath.lineTo(p3X, p3Y)

            val p4X = cx + toothInnerR * cos(angle + toothAngleStep * 0.50).toFloat()
            val p4Y = cy + toothInnerR * sin(angle + toothAngleStep * 0.50).toFloat()
            gearPath.lineTo(p4X, p4Y)

            val p5X = cx + toothInnerR * cos(angle + toothAngleStep).toFloat()
            val p5Y = cy + toothInnerR * sin(angle + toothAngleStep).toFloat()
            gearPath.lineTo(p5X, p5Y)
        }
        gearPath.close()

        elementPaint.style = Paint.Style.FILL
        elementPaint.color = if (isActive) Color.argb(55, 255, 255, 255) else Color.argb(18, 255, 255, 255)
        canvas.drawPath(gearPath, elementPaint)

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 2.2f else 1.2f
        elementPaint.color = if (isActive) Color.argb(220, 240, 245, 255) else Color.argb(60, 150, 160, 180)
        canvas.drawPath(gearPath, elementPaint)
    }

    fun drawFlightLockReticle(
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
                val arcRect = RectF(targetCX - half, targetCY - half, targetCX + half, targetCY + half)
                canvas.drawArc(arcRect, 35f, 110f, false, elementPaint)
                canvas.drawArc(arcRect, 215f, 110f, false, elementPaint)
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
                val path = Path().apply {
                    moveTo(targetCX, targetCY - dOffset)
                    lineTo(targetCX + dOffset, targetCY)
                    lineTo(targetCX, targetCY + dOffset)
                    lineTo(targetCX - dOffset, targetCY)
                    close()
                }
                elementPaint.strokeWidth = 2f * density
                canvas.drawPath(path, elementPaint)
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
        val badgeRect = RectF(badgeX - 8f * density, badgeY - 14f * density, badgeX + finalBadgeWidth + 12f * density, badgeY + 18f * density)
        canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * density
        highlightPaint.color = m3Primary
        canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, highlightPaint)

        textPaint.textSize = 12f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText(cleanAppName, badgeX, badgeY + 1f * density, textPaint)

        textPaint.textSize = 8f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = Color.argb(220, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
        canvas.drawText(subText, badgeX, badgeY + 12.5f * density, textPaint)
    }

    fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) {
        elementPaint.style = Paint.Style.FILL
        val coreGrad = RadialGradient(
            cx, cy, coreRadius,
            intArrayOf(
                if (isActive) m3Primary else Color.argb(180, 35, 40, 55),
                if (isActive) Color.argb(220, 20, 25, 38) else Color.argb(240, 12, 14, 20)
            ),
            floatArrayOf(0.0f, 1.0f),
            Shader.TileMode.CLAMP
        )
        elementPaint.shader = coreGrad
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)
        elementPaint.shader = null

        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 3f * density else 1.8f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(80, 200, 220, 255)
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)

        val rotAngle = (System.currentTimeMillis() % 10000L) / 10000f * 360f
        val innerR = coreRadius * 0.68f
        elementPaint.strokeWidth = 1.5f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(120, 200, 220, 255)
        canvas.drawCircle(cx, cy, innerR, elementPaint)

        for (i in 0..3) {
            val a = Math.toRadians((rotAngle + i * 90.0))
            val nx1 = cx + (innerR - 6f * density) * cos(a).toFloat()
            val ny1 = cy + (innerR - 6f * density) * sin(a).toFloat()
            val nx2 = cx + (innerR + 6f * density) * cos(a).toFloat()
            val ny2 = cy + (innerR + 6f * density) * sin(a).toFloat()
            canvas.drawLine(nx1, ny1, nx2, ny2, elementPaint)
        }

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText("COCKPIT", cx, cy - 2f * density, textPaint)

        textPaint.textSize = 7.5f * density
        textPaint.color = if (isActive) Color.WHITE else Color.argb(160, 200, 220, 255)
        canvas.drawText("HANGAR", cx, cy + 10f * density, textPaint)
    }

    fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) {
        val starPaint = elementPaint
        starPaint.style = Paint.Style.FILL

        val time = System.currentTimeMillis()
        val starCount = 42
        for (i in 0 until starCount) {
            val seedX = ((i * 137.5f) % w)
            val seedY = ((i * 269.3f) % h)
            val pulse = sin((time / 450.0) + (i * 0.75)).toFloat() * 0.35f + 0.65f
            val starSize = ((i % 3) + 1.2f) * density * (0.8f + 0.2f * pulse)
            val starAlpha = ((70 + (i * 17) % 130) * pulse * alphaFactor).toInt().coerceIn(0, 255)

            starPaint.color = when (i % 4) {
                0 -> Color.argb(starAlpha, 255, 255, 255)
                1 -> Color.argb(starAlpha, 180, 220, 255)
                2 -> Color.argb(starAlpha, 225, 190, 255)
                else -> Color.argb(starAlpha, 255, 235, 180)
            }
            canvas.drawCircle(seedX, seedY, starSize / 2f, starPaint)
        }
    }

    fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) {
        val t = (System.currentTimeMillis() % 100000) / 1000.0
        val nebulaShader1 = RadialGradient(
            cx, cy, radius * 1.3f,
            intArrayOf(
                Color.argb((140 * alphaFactor).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)),
                Color.argb((90 * alphaFactor).toInt(), 138, 43, 226),
                Color.argb((45 * alphaFactor).toInt(), 0, 229, 255),
                Color.argb(0, 4, 6, 12)
            ),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
            Shader.TileMode.CLAMP
        )
        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = nebulaShader1
        canvas.drawCircle(cx, cy, radius * 1.3f, elementPaint)

        val offX = (sin(t * 0.8) * 20.0).toFloat()
        val offY = (cos(t * 0.6) * 15.0).toFloat()
        val nebulaShader2 = RadialGradient(
            cx + offX, cy + offY, radius * 0.9f,
            intArrayOf(
                Color.argb((85 * alphaFactor).toInt(), 255, 64, 129),
                Color.argb((40 * alphaFactor).toInt(), 64, 196, 255),
                Color.argb(0, 0, 0, 0)
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            Shader.TileMode.CLAMP
        )
        elementPaint.shader = nebulaShader2
        canvas.drawCircle(cx + offX, cy + offY, radius * 0.9f, elementPaint)
        elementPaint.shader = null
    }
}
