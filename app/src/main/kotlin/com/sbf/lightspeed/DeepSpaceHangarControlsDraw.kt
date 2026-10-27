package com.sbf.lightspeed

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

internal const val KEY_HANGAR_OPEN_ACCORDION = "pref_hangar_open_accordion"

internal data class HangarLayoutMetrics(
    val isLandscape: Boolean,
    val ctrlCx: Float,
    val deckW: Float,
    val leftX: Float,
    val rightX: Float,
    val gap: Float,
    val topHangarY: Float,
    val r0Y: Float,
    val rBayY: Float,
    val rAccY: Float,
    val sub1Y: Float,
    val sub2Y: Float,
    val sub3Y: Float,
    val bottomCtrlY: Float,
    val openAccordion: Int,
    val gimbalCx: Float,
    val cyGimbal: Float,
    val landscapeGimbalFit: Float,
    val exitRect: RectF
)

internal fun computeHangarLayoutMetrics(
    screenW: Float,
    screenH: Float,
    d: Float,
    openAccordion: Int
): HangarLayoutMetrics {
    val isLandscape = screenW > screenH
    val ctrlCx = if (isLandscape) screenW * 0.27f else screenW / 2f
    val deckW = if (isLandscape) (screenW * 0.48f).coerceAtMost(450f * d) else (screenW * 0.92f).coerceAtMost(480f * d)
    val leftX = ctrlCx - (deckW / 2f)
    val rightX = ctrlCx + (deckW / 2f)
    val gap = 7f * d
    val topHangarY = if (isLandscape) (screenH * 0.08f).coerceAtLeast(28f * d) else (screenH * 0.06f).coerceAtLeast(48f * d)

    val r0Y = topHangarY + 34f * d
    val rBayY = r0Y + 36f * d
    val rAccY = rBayY + 36f * d

    val rowStep = if (isLandscape) 31f * d else 34f * d
    val sub1Y = rAccY + rowStep
    val sub2Y = sub1Y + rowStep
    val sub3Y = sub2Y + rowStep

    val bottomCtrlY = when (openAccordion) {
        0 -> sub3Y
        1 -> sub3Y
        2 -> sub3Y
        else -> rAccY
    }

    val gimbalCx = if (isLandscape) screenW * 0.74f else screenW / 2f
    val topSafeY = bottomCtrlY + 26f * d
    val bottomSafeY = screenH - 132f * d
    val portraitAvailSpan = (bottomSafeY - topSafeY).coerceAtLeast(145f * d)

    val cyGimbal = if (isLandscape) {
        (screenH * 0.41f).coerceIn(110f * d, screenH - 115f * d)
    } else {
        ((topSafeY + bottomSafeY) * 0.50f).coerceIn(topSafeY + 72f * d, screenH - 130f * d)
    }

    val landscapeGimbalFit = if (isLandscape) {
        ((screenH * 0.34f) / (135f * d)).coerceIn(0.58f, 1.0f)
    } else {
        ((portraitAvailSpan * 0.44f) / (135f * d)).coerceIn(0.54f, 0.94f)
    }

    val exitRect = if (isLandscape) {
        RectF(ctrlCx - (deckW * 0.44f), screenH - 46f * d, ctrlCx + (deckW * 0.44f), screenH - 12f * d)
    } else {
        RectF(ctrlCx - (deckW * 0.42f), screenH - 56f * d, ctrlCx + (deckW * 0.42f), screenH - 18f * d)
    }

    return HangarLayoutMetrics(
        isLandscape = isLandscape,
        ctrlCx = ctrlCx,
        deckW = deckW,
        leftX = leftX,
        rightX = rightX,
        gap = gap,
        topHangarY = topHangarY,
        r0Y = r0Y,
        rBayY = rBayY,
        rAccY = rAccY,
        sub1Y = sub1Y,
        sub2Y = sub2Y,
        sub3Y = sub3Y,
        bottomCtrlY = bottomCtrlY,
        openAccordion = openAccordion,
        gimbalCx = gimbalCx,
        cyGimbal = cyGimbal,
        landscapeGimbalFit = landscapeGimbalFit,
        exitRect = exitRect
    )
}

internal fun DeepSpaceRenderer.drawHangarControlRows(
    state: HangarRenderState,
    layout: HangarLayoutMetrics
): Float {
    val canvas = state.canvas
    val context = state.context
    val d = state.density
    val m3Primary = state.m3Primary
    val m3Secondary = state.m3Secondary
    val isOpenedFromLeftFlank = state.isOpenedFromLeftFlank
    val activeGearSetIndex = state.activeGearSetIndex
    val hangarBayScrollOffset = state.hangarBayScrollOffset
    val setsList = state.setsList
    val getFlankLaunchBehavior = state.getFlankLaunchBehavior

    val cx = layout.ctrlCx
    val deckW = layout.deckW
    val leftX = layout.leftX
    val rightX = layout.rightX
    val gap = layout.gap
    val r0Y = layout.r0Y
    val rBayY = layout.rBayY
    val rAccY = layout.rAccY

    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val palette = snapshot.resolveCanvasPalette(context)
    val panelIn = palette.panelInnerArgb
    val panelOut = palette.panelOuterArgb

    fun drawPill(rect: RectF, label: String, isSelected: Boolean, accentColor: Int = m3Primary, fontSizeDp: Float = 9.5f, isLocked: Boolean = false) {
        val goldColor = 0xFFFFD700.toInt()
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = when {
            isLocked -> Color.argb(45, 255, 215, 0)
            isSelected -> Color.argb(195, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
            else -> Color.argb(185, Color.red(panelOut), Color.green(panelOut), Color.blue(panelOut))
        }
        canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isSelected || isLocked) 1.4f * d else 0.9f * d
        highlightPaint.color = when {
            isLocked -> Color.argb(165, 255, 215, 0)
            isSelected -> accentColor
            else -> Color.argb(75, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
        }
        canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
        textPaint.textSize = fontSizeDp * d
        textPaint.typeface = if (isSelected || isLocked) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        textPaint.color = when {
            isLocked -> goldColor
            isSelected -> Color.WHITE
            else -> Color.argb(190, 210, 228, 255)
        }
        val displayLabel = if (isLocked && !label.startsWith("🔒")) "🔒 $label" else label
        canvas.drawText(displayLabel, rect.centerX(), rect.centerY() + 3.5f * d, textPaint)
    }

    // ROW 0: FLANK SELECTOR (PORT vs STARBOARD)
    val halfBtnW = (deckW - gap) / 2f
    val portRect = RectF(leftX, r0Y - 14f * d, leftX + halfBtnW, r0Y + 14f * d)
    val starboardRect = RectF(rightX - halfBtnW, r0Y - 14f * d, rightX, r0Y + 14f * d)
    drawPill(portRect, "◀ PORT (LEFT FLANK)", isOpenedFromLeftFlank, m3Primary, 9.5f)
    drawPill(starboardRect, "STARBOARD (RIGHT) ▶", !isOpenedFromLeftFlank, m3Primary, 9.5f)

    // ROW 1: DOCKING BAYS (HORIZONTALLY SCROLLABLE RAIL)
    var activeGearSetIndexLocal = activeGearSetIndex
    if (activeGearSetIndexLocal >= setsList.size) { activeGearSetIndexLocal = 0 }
    val bayCount = setsList.size + 1
    val slotW = (84f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
    val totalBayRailW = bayCount * slotW
    val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
    val railStartX = if (totalBayRailW <= deckW) (leftX + (deckW - totalBayRailW) / 2f) else (leftX + hangarBayScrollOffset.coerceIn(-maxScroll, 0f))

    canvas.save()
    canvas.clipRect(leftX - 4f * d, rBayY - 18f * d, rightX + 4f * d, rBayY + 18f * d)

    val isInfinityUnlocked = LightspeedInfinityManager.isUnlocked(context)
    for (g in setsList.indices) {
        val setId = setsList[g]
        val setName = CockpitGearRepository.getGearSetNameById(context, setId)
        val btnX = railStartX + (g + 0.5f) * slotW
        val isCurrentBay = (activeGearSetIndexLocal == g)
        val bayRect = RectF(btnX - slotW * 0.46f, rBayY - 15f * d, btnX + slotW * 0.46f, rBayY + 15f * d)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (isCurrentBay) Color.argb(195, 24, 32, 54) else Color.argb(75, 14, 18, 28)
        canvas.drawRoundRect(bayRect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isCurrentBay) 1.7f * d else 0.8f * d
        highlightPaint.color = if (isCurrentBay) m3Primary else Color.argb(45, 200, 220, 255)
        canvas.drawRoundRect(bayRect, 9f * d, 9f * d, highlightPaint)

        val isBayLocked = (g >= 2 && !isInfinityUnlocked)
        val shortLabel = if (isBayLocked) "🔒 ${g + 1}: ${setName.take(5)}" else "${g + 1}: ${setName.take(6)}"

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = when {
            isBayLocked -> Color.argb(45, 255, 215, 0)
            isCurrentBay -> Color.argb(195, 24, 32, 54)
            else -> Color.argb(75, 14, 18, 28)
        }
        canvas.drawRoundRect(bayRect, 9f * d, 9f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = if (isCurrentBay || isBayLocked) 1.5f * d else 0.8f * d
        highlightPaint.color = when {
            isBayLocked -> Color.argb(175, 255, 215, 0)
            isCurrentBay -> m3Primary
            else -> Color.argb(45, 200, 220, 255)
        }
        canvas.drawRoundRect(bayRect, 9f * d, 9f * d, highlightPaint)

        textPaint.textSize = if (isBayLocked) 9f * d else 10f * d
        textPaint.typeface = if (isCurrentBay || isBayLocked) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
        textPaint.color = if (isBayLocked) 0xFFFFD700.toInt() else if (isCurrentBay) Color.WHITE else Color.argb(175, 200, 220, 255)
        canvas.drawText(shortLabel, btnX, rBayY + 3.5f * d, textPaint)
    }

    val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
    val plusRect = RectF(plusBtnX - slotW * 0.42f, rBayY - 15f * d, plusBtnX + slotW * 0.42f, rBayY + 15f * d)
    val isPlusLocked = (setsList.size >= 2 && !isInfinityUnlocked)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (isPlusLocked) Color.argb(45, 255, 215, 0) else Color.argb(75, 20, 26, 40)
    canvas.drawRoundRect(plusRect, 9f * d, 9f * d, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = if (isPlusLocked) 1.4f * d else 0.8f * d
    highlightPaint.color = if (isPlusLocked) Color.argb(175, 255, 215, 0) else Color.argb(60, 200, 220, 255)
    canvas.drawRoundRect(plusRect, 9f * d, 9f * d, highlightPaint)
    textPaint.textSize = if (isPlusLocked) 10f * d else 14f * d
    textPaint.color = if (isPlusLocked) 0xFFFFD700.toInt() else Color.WHITE
    canvas.drawText(if (isPlusLocked) "🔒 +" else "+", plusBtnX, rBayY + 4f * d, textPaint)
    canvas.restore()

    // ROW 2: 3 COLLAPSIBLE ACCORDION TABS (Zero colored emojis; crisp tactical symbols)
    val accW = (deckW - gap * 2f) / 3f
    val acc0Rect = RectF(leftX, rAccY - 14f * d, leftX + accW, rAccY + 14f * d)
    val acc1Rect = RectF(leftX + accW + gap, rAccY - 14f * d, leftX + accW * 2f + gap, rAccY + 14f * d)
    val acc2Rect = RectF(rightX - accW, rAccY - 14f * d, rightX, rAccY + 14f * d)

    drawPill(acc0Rect, if (layout.openAccordion == 0) "◈ TRIGGER ZONES ▾" else "◈ TRIGGER ZONES ▸", layout.openAccordion == 0, m3Secondary, 8.5f)
    drawPill(acc1Rect, if (layout.openAccordion == 1) "⌖ RETICLE ▾" else "⌖ RETICLE ▸", layout.openAccordion == 1, m3Secondary, 9.2f)
    drawPill(acc2Rect, if (layout.openAccordion == 2) "⬡ SHAPE ▾" else "⬡ SHAPE ▸", layout.openAccordion == 2, m3Secondary, 9.2f)

    val prefs = context.defaultPrefs()

    // Draw elevated 12-theme glass card backing when an accordion is expanded
    if (layout.openAccordion in 0..2) {
        val trayRect = RectF(
            leftX - 6f * d,
            layout.sub1Y - 18f * d,
            rightX + 6f * d,
            layout.bottomCtrlY + 18f * d
        )
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(238, Color.red(panelIn), Color.green(panelIn), Color.blue(panelIn))
        canvas.drawRoundRect(trayRect, 12f * d, 12f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = Color.argb(145, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(trayRect, 12f * d, 12f * d, highlightPaint)
    }

    // EXPANDED ACCORDION CONTENT
    when (layout.openAccordion) {
        0 -> {
            // Sub-row 1: Startup Default Mode
            val launchBehavior = getFlankLaunchBehavior(isOpenedFromLeftFlank)
            val btn1Rect = RectF(leftX, layout.sub1Y - 13f * d, leftX + halfBtnW, layout.sub1Y + 13f * d)
            val btn2Rect = RectF(rightX - halfBtnW, layout.sub1Y - 13f * d, rightX, layout.sub1Y + 13f * d)
            drawPill(btn1Rect, "STARTUP: FIRST", launchBehavior == "default", m3Primary, 9f)
            drawPill(btn2Rect, "STARTUP: LAST", launchBehavior == "last", m3Primary, 9f)

            // Sub-row 2: Reorder & Delete Active Profile
            val shiftW = deckW * 0.35f
            val deleteW = deckW * 0.26f
            val shiftLeftRect = RectF(leftX, layout.sub2Y - 13f * d, leftX + shiftW, layout.sub2Y + 13f * d)
            val deleteBayRect = RectF(cx - deleteW / 2f, layout.sub2Y - 13f * d, cx + deleteW / 2f, layout.sub2Y + 13f * d)
            val shiftRightRect = RectF(rightX - shiftW, layout.sub2Y - 13f * d, rightX, layout.sub2Y + 13f * d)
            drawPill(shiftLeftRect, "◀ SHIFT LEFT", activeGearSetIndexLocal > 0, m3Secondary, 9f)
            val canDelete = setsList.size > 1
            drawPill(deleteBayRect, "✕ DELETE", canDelete, Color.parseColor("#EF5350"), 9f)
            drawPill(shiftRightRect, "SHIFT RIGHT ▶", activeGearSetIndexLocal < setsList.size - 1, m3Secondary, 9f)

            // Sub-row 3: Icon Pack Theme
            val activePack = LightspeedIconManager.getActiveIconPack(context)
            val availablePacks = LightspeedIconManager.getAvailableIconPacks(context)
            val currentPackLabel = availablePacks.firstOrNull { it.packageName == activePack }?.label ?: "System Default"
            val iconThemeRect = RectF(leftX, layout.sub3Y - 13f * d, rightX, layout.sub3Y + 13f * d)
            drawPill(iconThemeRect, "ICON PACK: ${currentPackLabel.take(16)} [ ➔ CYCLE ]", false, m3Secondary, 9.5f)
        }
        1 -> {
            // Sub-row 1: Flight Momentum
            val physicsProfile = prefs.getString(LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE, "magnetic") ?: "magnetic"
            val physW = (deckW - (gap * 2f)) / 3f
            val phys1Rect = RectF(leftX, layout.sub1Y - 13f * d, leftX + physW, layout.sub1Y + 13f * d)
            val phys2Rect = RectF(leftX + physW + gap, layout.sub1Y - 13f * d, leftX + physW * 2f + gap, layout.sub1Y + 13f * d)
            val phys3Rect = RectF(rightX - physW, layout.sub1Y - 13f * d, rightX, layout.sub1Y + 13f * d)
            drawPill(phys1Rect, "◈ SNAPPY", physicsProfile == "magnetic")
            drawPill(phys2Rect, "≈ FLUID", physicsProfile == "fluid")
            drawPill(phys3Rect, "❖ HEAVY", physicsProfile == "heavy")

            // Sub-row 2: Reticle Crosshair Style
            val retW = (deckW - (gap * 3f)) / 4f
            val ret1Rect = RectF(leftX, layout.sub2Y - 13f * d, leftX + retW, layout.sub2Y + 13f * d)
            val ret2Rect = RectF(leftX + (retW + gap), layout.sub2Y - 13f * d, leftX + (retW + gap) + retW, layout.sub2Y + 13f * d)
            val ret3Rect = RectF(leftX + (retW + gap) * 2f, layout.sub2Y - 13f * d, leftX + (retW + gap) * 2f + retW, layout.sub2Y + 13f * d)
            val ret4Rect = RectF(rightX - retW, layout.sub2Y - 13f * d, rightX, layout.sub2Y + 13f * d)
            val reticleStyle = prefs.getString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "tactical") ?: "tactical"
            drawPill(ret1Rect, "[ ] TACTICAL", reticleStyle == "tactical", m3Primary, 8.5f)
            drawPill(ret2Rect, "( ) CYBER", reticleStyle == "cyber", m3Primary, 8.5f)
            drawPill(ret3Rect, "+ CROSS", reticleStyle == "cross", m3Primary, 8.5f)
            drawPill(ret4Rect, "◇ DIAMOND", reticleStyle == "diamond", m3Primary, 8.5f)

            // Sub-row 3: Reticle Targeting Horizon
            val ori1Rect = RectF(leftX, layout.sub3Y - 13f * d, leftX + halfBtnW, layout.sub3Y + 13f * d)
            val ori2Rect = RectF(rightX - halfBtnW, layout.sub3Y - 13f * d, rightX, layout.sub3Y + 13f * d)
            val reticleOri = prefs.getString(LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180) ?: LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
            drawPill(ori1Rect, "⌖ 180° HORIZON", reticleOri == LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180)
            drawPill(ori2Rect, "⇄ DYNAMIC FLANK", reticleOri == LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK)
        }
        2 -> {
            // Sub-row 1: Sculpted Mockup Styles (Row 1 of 2)
            val shapeW = (deckW - (gap * 3f)) / 4f
            val shape1Rect = RectF(leftX, layout.sub1Y - 13f * d, leftX + shapeW, layout.sub1Y + 13f * d)
            val shape2Rect = RectF(leftX + (shapeW + gap), layout.sub1Y - 13f * d, leftX + (shapeW + gap) + shapeW, layout.sub1Y + 13f * d)
            val shape3Rect = RectF(leftX + (shapeW + gap) * 2f, layout.sub1Y - 13f * d, leftX + (shapeW + gap) * 2f + shapeW, layout.sub1Y + 13f * d)
            val shape4Rect = RectF(rightX - shapeW, layout.sub1Y - 13f * d, rightX, layout.sub1Y + 13f * d)
            val currentShape = CockpitGearRepository.getGearShape(context)
            drawPill(shape1Rect, "⚙ GEAR", currentShape == LightspeedPreferences.GEAR_SHAPE_CIRCLE, m3Primary, 8.8f)
            drawPill(shape2Rect, "⬡ HEX", currentShape == LightspeedPreferences.GEAR_SHAPE_SQUARE, m3Primary, 8.8f)
            drawPill(shape3Rect, "✦ PETAL", currentShape == LightspeedPreferences.GEAR_SHAPE_SQUIRCLE, m3Primary, 8.8f)
            drawPill(shape4Rect, "◈ BLADE", currentShape == LightspeedPreferences.GEAR_SHAPE_STAR, m3Primary, 8.8f)

            // Sub-row 2: Sculpted Mockup Styles (Row 2 of 2 — Infinity Tier)
            val isInf = LightspeedInfinityManager.isUnlocked(context)
            val shape5Rect = RectF(leftX, layout.sub2Y - 13f * d, leftX + shapeW, layout.sub2Y + 13f * d)
            val shape6Rect = RectF(leftX + (shapeW + gap), layout.sub2Y - 13f * d, leftX + (shapeW + gap) + shapeW, layout.sub2Y + 13f * d)
            val shape7Rect = RectF(leftX + (shapeW + gap) * 2f, layout.sub2Y - 13f * d, leftX + (shapeW + gap) * 2f + shapeW, layout.sub2Y + 13f * d)
            val shape8Rect = RectF(rightX - shapeW, layout.sub2Y - 13f * d, rightX, layout.sub2Y + 13f * d)
            drawPill(shape5Rect, "ORBIT", isInf && currentShape == LightspeedPreferences.GEAR_SHAPE_ORBITAL, m3Primary, 8.2f, isLocked = !isInf)
            drawPill(shape6Rect, "DMND", isInf && currentShape == LightspeedPreferences.GEAR_SHAPE_DIAMOND, m3Primary, 8.2f, isLocked = !isInf)
            drawPill(shape7Rect, "CRESC", isInf && currentShape == LightspeedPreferences.GEAR_SHAPE_CRESCENT, m3Primary, 8.2f, isLocked = !isInf)
            drawPill(shape8Rect, "TECH", isInf && currentShape == LightspeedPreferences.GEAR_SHAPE_TECH, m3Primary, 8.2f, isLocked = !isInf)

            // Sub-row 3: Gimbal Diameter & DPI Independence
            val customDiameterScale = CockpitGearRepository.getGearCustomDiameterScale(context)
            val isDpiIndep = CockpitGearRepository.isGearDpiIndependent(context)
            val diamSectionW = deckW * 0.58f
            val dpiBtnW = deckW - diamSectionW - gap
            val stepBtnW = 34f * d
            val diamMinusRect = RectF(leftX, layout.sub3Y - 13f * d, leftX + stepBtnW, layout.sub3Y + 13f * d)
            val diamLabelRect = RectF(leftX + stepBtnW + 3f * d, layout.sub3Y - 13f * d, leftX + diamSectionW - stepBtnW - 3f * d, layout.sub3Y + 13f * d)
            val diamPlusRect = RectF(leftX + diamSectionW - stepBtnW, layout.sub3Y - 13f * d, leftX + diamSectionW, layout.sub3Y + 13f * d)
            val dpiToggleRect = RectF(rightX - dpiBtnW, layout.sub3Y - 13f * d, rightX, layout.sub3Y + 13f * d)

            drawPill(diamMinusRect, "−", false)
            val pct = (customDiameterScale * 100f).toInt()
            drawPill(diamLabelRect, "SIZE: $pct%", customDiameterScale != 1.0f)
            drawPill(diamPlusRect, "+", false)
            val dpiLabel = if (isDpiIndep) "◈ DPI: INDEP" else "DPI: SYS"
            drawPill(dpiToggleRect, dpiLabel, isDpiIndep)
        }
    }

    return layout.bottomCtrlY
}
