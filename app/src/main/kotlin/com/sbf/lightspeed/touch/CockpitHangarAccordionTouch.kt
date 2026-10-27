package com.sbf.lightspeed

import android.graphics.RectF
import android.widget.Toast
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Handles touch events for Cockpit Hangar Accordion tabs (Fleet, Reticle, Shape)
 * and their expanded configuration sub-controls.
 */
internal fun LightspeedCruiseOverlay.handleCockpitHangarAccordionTouch(
    x: Float,
    y: Float,
    d: Float,
    layout: HangarLayoutMetrics,
    openAccordion: Int,
    setsList: MutableList<String>,
    slotW: Float,
    maxScroll: Float,
    customDiameterScale: Float
): Boolean {
    val ctrlCx = layout.ctrlCx
    val deckW = layout.deckW
    val leftX = layout.leftX
    val rightX = layout.rightX
    val gap = layout.gap
    val rAccY = layout.rAccY
    val halfBtnW = (deckW - gap) / 2f
    val prefs = prefs()

    // 2. Accordion Tabs (0: FLEET, 1: RETICLE, 2: SHAPE)
    val accW = (deckW - gap * 2f) / 3f
    val acc0Rect = RectF(leftX, rAccY - 15f * d, leftX + accW, rAccY + 15f * d)
    val acc1Rect = RectF(leftX + accW + gap, rAccY - 15f * d, leftX + accW * 2f + gap, rAccY + 15f * d)
    val acc2Rect = RectF(rightX - accW, rAccY - 15f * d, rightX, rAccY + 15f * d)

    if (acc0Rect.contains(x, y)) {
        val next = if (openAccordion == 0) -1 else 0
        prefs.edit().putInt(KEY_HANGAR_OPEN_ACCORDION, next).apply()
        triggerHardwareHaptic(20, 120)
        invalidate()
        return true
    }
    if (acc1Rect.contains(x, y)) {
        val next = if (openAccordion == 1) -1 else 1
        prefs.edit().putInt(KEY_HANGAR_OPEN_ACCORDION, next).apply()
        triggerHardwareHaptic(20, 120)
        invalidate()
        return true
    }
    if (acc2Rect.contains(x, y)) {
        val next = if (openAccordion == 2) -1 else 2
        prefs.edit().putInt(KEY_HANGAR_OPEN_ACCORDION, next).apply()
        triggerHardwareHaptic(20, 120)
        invalidate()
        return true
    }

    // 3. Expanded Accordion Controls
    when (openAccordion) {
        0 -> {
            // Sub-row 1: Startup Default Mode
            val btn1Rect = RectF(leftX, layout.sub1Y - 14f * d, leftX + halfBtnW, layout.sub1Y + 14f * d)
            val btn2Rect = RectF(rightX - halfBtnW, layout.sub1Y - 14f * d, rightX, layout.sub1Y + 14f * d)
            if (btn1Rect.contains(x, y)) {
                setFlankLaunchBehavior(isOpenedFromLeftFlank, "default")
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (btn2Rect.contains(x, y)) {
                setFlankLaunchBehavior(isOpenedFromLeftFlank, "last")
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }

            // Sub-row 2: Reorder & Delete Active Profile
            val shiftW = deckW * 0.35f
            val deleteW = deckW * 0.26f
            val shiftLeftRect = RectF(leftX, layout.sub2Y - 14f * d, leftX + shiftW, layout.sub2Y + 14f * d)
            val deleteBayRect = RectF(ctrlCx - deleteW / 2f, layout.sub2Y - 14f * d, ctrlCx + deleteW / 2f, layout.sub2Y + 14f * d)
            val shiftRightRect = RectF(rightX - shiftW, layout.sub2Y - 14f * d, rightX, layout.sub2Y + 14f * d)

            if (shiftLeftRect.contains(x, y) && activeGearSetIndex > 0) {
                val temp = setsList[activeGearSetIndex]
                setsList[activeGearSetIndex] = setsList[activeGearSetIndex - 1]
                setsList[activeGearSetIndex - 1] = temp
                activeGearSetIndex--
                saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                com.sbf.lightspeed.system.LightspeedHapticEngine.cockpitGearJump(context)
                invalidate()
                return true
            }
            if (shiftRightRect.contains(x, y) && activeGearSetIndex < setsList.size - 1) {
                val temp = setsList[activeGearSetIndex]
                setsList[activeGearSetIndex] = setsList[activeGearSetIndex + 1]
                setsList[activeGearSetIndex + 1] = temp
                activeGearSetIndex++
                saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                com.sbf.lightspeed.system.LightspeedHapticEngine.cockpitGearJump(context)
                invalidate()
                return true
            }
            if (deleteBayRect.contains(x, y)) {
                if (setsList.size > 1) {
                    setsList.removeAt(activeGearSetIndex)
                    if (activeGearSetIndex >= setsList.size) {
                        activeGearSetIndex = setsList.size - 1
                    }
                    saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                    val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                    hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                    triggerHardwareHaptic(45, 230)
                    invalidate()
                    return true
                } else {
                    Toast.makeText(context, "Cannot delete the last remaining gear set", Toast.LENGTH_SHORT).show()
                    return true
                }
            }

            // Sub-row 3: Icon Theme Carousel
            val iconThemeRect = RectF(leftX, layout.sub3Y - 14f * d, rightX, layout.sub3Y + 14f * d)
            if (iconThemeRect.contains(x, y)) {
                val availablePacks = LightspeedIconManager.getAvailableIconPacks(context)
                if (availablePacks.isNotEmpty()) {
                    val activePack = LightspeedIconManager.getActiveIconPack(context)
                    val currentIndex = availablePacks.indexOfFirst { it.packageName == activePack }
                    val nextIndex = (currentIndex + 1) % availablePacks.size
                    LightspeedIconManager.setActiveIconPack(context, availablePacks[nextIndex].packageName)
                    triggerHardwareHaptic(35, 180)
                    invalidate()
                    return true
                }
            }
        }
        1 -> {
            // Sub-row 1: Flight Momentum
            val physW = (deckW - (gap * 2f)) / 3f
            val phys1Rect = RectF(leftX, layout.sub1Y - 14f * d, leftX + physW, layout.sub1Y + 14f * d)
            val phys2Rect = RectF(leftX + physW + gap, layout.sub1Y - 14f * d, leftX + physW * 2f + gap, layout.sub1Y + 14f * d)
            val phys3Rect = RectF(rightX - physW, layout.sub1Y - 14f * d, rightX, layout.sub1Y + 14f * d)
            if (phys1Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE, "magnetic").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (phys2Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE, "fluid").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (phys3Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE, "heavy").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }

            // Sub-row 2: Reticle Crosshair Style
            val retW = (deckW - (gap * 3f)) / 4f
            val ret1Rect = RectF(leftX, layout.sub2Y - 14f * d, leftX + retW, layout.sub2Y + 14f * d)
            val ret2Rect = RectF(leftX + (retW + gap), layout.sub2Y - 14f * d, leftX + (retW + gap) + retW, layout.sub2Y + 14f * d)
            val ret3Rect = RectF(leftX + (retW + gap) * 2f, layout.sub2Y - 14f * d, leftX + (retW + gap) * 2f + retW, layout.sub2Y + 14f * d)
            val ret4Rect = RectF(rightX - retW, layout.sub2Y - 14f * d, rightX, layout.sub2Y + 14f * d)
            if (ret1Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "tactical").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (ret2Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "cyber").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (ret3Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "cross").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (ret4Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                prefs.edit().putString(LightspeedPreferences.KEY_GEAR_RETICLE_STYLE, "diamond").apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }

            // Sub-row 3: Reticle Targeting Horizon
            val ori1Rect = RectF(leftX, layout.sub3Y - 14f * d, leftX + halfBtnW, layout.sub3Y + 14f * d)
            val ori2Rect = RectF(rightX - halfBtnW, layout.sub3Y - 14f * d, rightX, layout.sub3Y + 14f * d)
            if (ori1Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180).apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (ori2Rect.contains(x, y)) {
                prefs.edit().putString(LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK).apply()
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
        }
        2 -> {
            // Sub-row 1: Sculpted Mockup Styles (Row 1 of 2)
            val shapeW = (deckW - (gap * 3f)) / 4f
            val shape1Rect = RectF(leftX, layout.sub1Y - 14f * d, leftX + shapeW, layout.sub1Y + 14f * d)
            val shape2Rect = RectF(leftX + (shapeW + gap), layout.sub1Y - 14f * d, leftX + (shapeW + gap) + shapeW, layout.sub1Y + 14f * d)
            val shape3Rect = RectF(leftX + (shapeW + gap) * 2f, layout.sub1Y - 14f * d, leftX + (shapeW + gap) * 2f + shapeW, layout.sub1Y + 14f * d)
            val shape4Rect = RectF(rightX - shapeW, layout.sub1Y - 14f * d, rightX, layout.sub1Y + 14f * d)
            if (shape1Rect.contains(x, y)) {
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_CIRCLE)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape2Rect.contains(x, y)) {
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_SQUARE)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape3Rect.contains(x, y)) {
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_SQUIRCLE)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape4Rect.contains(x, y)) {
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_STAR)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }

            // Sub-row 2: Sculpted Mockup Styles (Row 2 of 2 — Infinity Tier)
            val shape5Rect = RectF(leftX, layout.sub2Y - 14f * d, leftX + shapeW, layout.sub2Y + 14f * d)
            val shape6Rect = RectF(leftX + (shapeW + gap), layout.sub2Y - 14f * d, leftX + (shapeW + gap) + shapeW, layout.sub2Y + 14f * d)
            val shape7Rect = RectF(leftX + (shapeW + gap) * 2f, layout.sub2Y - 14f * d, leftX + (shapeW + gap) * 2f + shapeW, layout.sub2Y + 14f * d)
            val shape8Rect = RectF(rightX - shapeW, layout.sub2Y - 14f * d, rightX, layout.sub2Y + 14f * d)
            if (shape5Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_ORBITAL)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape6Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_DIAMOND)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape7Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_CRESCENT)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }
            if (shape8Rect.contains(x, y)) {
                if (!com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                    triggerHardwareHaptic(50, 255)
                    showInfinityPrompt()
                    invalidate()
                    return true
                }
                CockpitGearRepository.setGearShape(context, LightspeedPreferences.GEAR_SHAPE_TECH)
                triggerHardwareHaptic(20, 120)
                invalidate()
                return true
            }

            // Sub-row 3: Gimbal Diameter & DPI Independence
            val isDpiIndep = CockpitGearRepository.isGearDpiIndependent(context)
            val diamSectionW = deckW * 0.58f
            val dpiBtnW = deckW - diamSectionW - gap
            val stepBtnW = 34f * d
            val diamMinusRect = RectF(leftX, layout.sub3Y - 14f * d, leftX + stepBtnW, layout.sub3Y + 14f * d)
            val diamLabelRect = RectF(leftX + stepBtnW + 3f * d, layout.sub3Y - 14f * d, leftX + diamSectionW - stepBtnW - 3f * d, layout.sub3Y + 14f * d)
            val diamPlusRect = RectF(leftX + diamSectionW - stepBtnW, layout.sub3Y - 14f * d, leftX + diamSectionW, layout.sub3Y + 14f * d)
            val dpiToggleRect = RectF(rightX - dpiBtnW, layout.sub3Y - 14f * d, rightX, layout.sub3Y + 14f * d)

            if (diamMinusRect.contains(x, y)) {
                val newScale = kotlin.math.round((customDiameterScale - 0.1f) * 10f) / 10f
                CockpitGearRepository.setGearCustomDiameterScale(context, newScale.coerceIn(0.5f, 2.0f))
                triggerHardwareHaptic(20, 100)
                invalidate()
                return true
            }
            if (diamLabelRect.contains(x, y)) {
                CockpitGearRepository.setGearCustomDiameterScale(context, 1.0f)
                triggerHardwareHaptic(25, 120)
                invalidate()
                return true
            }
            if (diamPlusRect.contains(x, y)) {
                val newScale = kotlin.math.round((customDiameterScale + 0.1f) * 10f) / 10f
                CockpitGearRepository.setGearCustomDiameterScale(context, newScale.coerceIn(0.5f, 2.0f))
                triggerHardwareHaptic(20, 100)
                invalidate()
                return true
            }
            if (dpiToggleRect.contains(x, y)) {
                CockpitGearRepository.setGearDpiIndependent(context, !isDpiIndep)
                triggerHardwareHaptic(25, 140)
                invalidate()
                return true
            }
        }
    }
    return false
}
