package com.sbf.lightspeed

import android.content.Intent
import android.graphics.RectF
import android.media.AudioManager
import android.provider.Settings
import android.view.MotionEvent
import android.widget.Toast
import com.sbf.lightspeed.system.CockpitGearRepository
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine

/**
 * Handles all touch events when [LightspeedCruiseOverlay] is in
 * [CruiseLayer.COCKPIT_HANGAR] mode.
 *
 * @return true if the event was consumed.
 */
internal fun LightspeedCruiseOverlay.handleCockpitHangarTouch(event: MotionEvent): Boolean {
    val x = event.x; val y = event.y
    val d = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val prefs = prefs()
    val openAccordion = prefs.getInt(KEY_HANGAR_OPEN_ACCORDION, -1)
    val layout = computeHangarLayoutMetrics(screenW, screenH, d, openAccordion)

    val ctrlCx = layout.ctrlCx
    val deckW = layout.deckW
    val leftX = layout.leftX
    val rightX = layout.rightX
    val gap = layout.gap
    val r0Y = layout.r0Y
    val rBayY = layout.rBayY
    val rAccY = layout.rAccY

    val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
    if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
    val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"
    val reticleOri = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_COCKPIT_RETICLE_ORIENTATION, com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180) ?: com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
    val targetFocusAngle = if (reticleOri == com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_MIRRORED_FLANK && isOpenedFromLeftFlank) 0f else 180f

    val gearShape = CockpitGearRepository.getGearShape(context)
    val unitScale = DeepSpaceGearGeometry.computeGearUnitScale(context)
    val customDiameterScale = CockpitGearRepository.getGearCustomDiameterScale(context)
    val dGimbal = unitScale * customDiameterScale * layout.landscapeGimbalFit

    val gimbalCx = layout.gimbalCx
    val cyGimbal = layout.cyGimbal
    val radOuter = 135f * dGimbal
    val radInner = 84f * dGimbal
    val radHub = 32f * dGimbal
    val shiftBarY = cyGimbal + radOuter + (if (layout.isLandscape) 20f else 26f) * d
    val transferBarY = cyGimbal + radOuter + (if (layout.isLandscape) 48f else 58f) * d

    val bayCount = setsList.size + 1
    val slotW = (84f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
    val totalBayRailW = bayCount * slotW
    val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)

    if (totalBayRailW <= deckW) {
        hangarBayScrollOffset = 0f
    } else {
        hangarBayScrollOffset = hangarBayScrollOffset.coerceIn(-maxScroll, 0f)
    }
    val railStartX = if (totalBayRailW <= deckW) (leftX + (deckW - totalBayRailW) / 2f) else (leftX + hangarBayScrollOffset)

    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            hasLongPressFired = false
            longPressHangarBayRunnable?.let { removeCallbacks(it) }
            longPressCogRunnable?.let { removeCallbacks(it) }
            longPressHangarBayRunnable = null
            longPressCogRunnable = null

            hangarBayTouchDownX = x
            hangarBayTouchDownY = y
            isDraggingHangarBays = (y in (rBayY - 20f * d)..(rBayY + 20f * d)) && (x in (leftX - 10f * d)..(rightX + 10f * d))

            if (isDraggingHangarBays) {
                for (gIndex in setsList.indices) {
                    val btnX = railStartX + (gIndex + 0.5f) * slotW
                    val bayRect = RectF(btnX - slotW * 0.46f, rBayY - 16f * d, btnX + slotW * 0.46f, rBayY + 16f * d)
                    if (bayRect.contains(x, y)) {
                        if (gIndex >= 2 && !com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                            break
                        }
                        val targetSetId = setsList[gIndex]
                        val currentName = prefs.getString("gear_set_${targetSetId}_name", "SET ${gIndex + 1}") ?: "SET ${gIndex + 1}"
                        val runnable = Runnable {
                            hasLongPressFired = true
                            triggerHardwareHaptic(50, 255)
                            val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                                action = CockpitDialogActivity.ACTION_RENAME_GEAR
                                putExtra(CockpitDialogActivity.EXTRA_SET_ID, targetSetId)
                                putExtra(CockpitDialogActivity.EXTRA_SET_INDEX, gIndex)
                                putExtra(CockpitDialogActivity.EXTRA_CURRENT_NAME, currentName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            dismissOverlay()
                            context.startActivity(intent)
                        }
                        longPressHangarBayRunnable = runnable
                        postDelayed(runnable, CruiseOverlayConstants.LONG_PRESS_THRESHOLD_MS)
                        break
                    }
                }
            } else {
                // 0. Flank Switcher: Port (Left) vs Starboard (Right)
                val halfBtnW = (deckW - gap) / 2f
                val portRect = RectF(leftX, r0Y - 15f * d, leftX + halfBtnW, r0Y + 15f * d)
                val starboardRect = RectF(rightX - halfBtnW, r0Y - 15f * d, rightX, r0Y + 15f * d)
                if (portRect.contains(x, y)) {
                    isOpenedFromLeftFlank = true
                    val flankSets = getGearSetsOrder(true)
                    if (activeGearSetIndex >= flankSets.size) activeGearSetIndex = 0
                    hangarBayScrollOffset = 0f
                    triggerHardwareHaptic(25, 140)
                    invalidate()
                    return true
                }
                if (starboardRect.contains(x, y)) {
                    isOpenedFromLeftFlank = false
                    val flankSets = getGearSetsOrder(false)
                    if (activeGearSetIndex >= flankSets.size) activeGearSetIndex = 0
                    hangarBayScrollOffset = 0f
                    triggerHardwareHaptic(25, 140)
                    invalidate()
                    return true
                }

                // 2. Accordion Tabs & Expanded Controls (0: FLEET, 1: RETICLE, 2: SHAPE)
                if (handleCockpitHangarAccordionTouch(x, y, d, layout, openAccordion, setsList, slotW, maxScroll, customDiameterScale)) {
                    return true
                }

                // 7. Live Gimbal Touch & Interaction
                val shiftCogLeftRect = RectF(gimbalCx - 150f * d, shiftBarY - 14f * d, gimbalCx - 60f * d, shiftBarY + 14f * d)
                val shiftCogRightRect = RectF(gimbalCx + 60f * d, shiftBarY - 14f * d, gimbalCx + 150f * d, shiftBarY + 14f * d)
                val transferRect = RectF(gimbalCx - 115f * d, transferBarY - 14f * d, gimbalCx + 115f * d, transferBarY + 14f * d)
                val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing).toMutableList()

                // Check Inter-Ring Transfer Tap (Move App between Ring 0 and Ring 1)
                if (transferRect.contains(x, y) && ringApps.isNotEmpty()) {
                    val count = ringApps.size
                    val baseRotation = gearRingRotations[activeHangarRing]
                    var targetedIdx = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in ringApps.indices) {
                        val angleDeg = (baseRotation + i * (360f / count)) % 360f
                        val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                        val diff = kotlin.math.abs(norm - targetFocusAngle).let { if (it > 180f) 360f - it else it }
                        if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                    }
                    val destRing = if (activeHangarRing == 0) 1 else 0
                    val r0 = getAppsForActiveGear(activeGearSetIndex, 0).toMutableList()
                    val r1 = getAppsForActiveGear(activeGearSetIndex, 1).toMutableList()
                    val movedItem = if (activeHangarRing == 0) r0.removeAt(targetedIdx) else r1.removeAt(targetedIdx)
                    if (destRing == 0) r0.add(movedItem) else r1.add(movedItem)

                    prefs.edit()
                        .putString("gear_set_${currentSetId}_ring_0_packages", r0.joinToString(","))
                        .putString("gear_set_${currentSetId}_ring_1_packages", r1.joinToString(","))
                        .apply()

                    activeHangarRing = destRing
                    triggerHardwareHaptic(50, 255)
                    invalidate()
                    return true
                }

                // Check Live Orbital Shift Left
                if (shiftCogLeftRect.contains(x, y) && ringApps.size > 1) {
                    val count = ringApps.size
                    val baseRotation = gearRingRotations[activeHangarRing]
                    var targetedIdx = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in ringApps.indices) {
                        val angleDeg = (baseRotation + i * (360f / count)) % 360f
                        val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                        val diff = kotlin.math.abs(norm - targetFocusAngle).let { if (it > 180f) 360f - it else it }
                        if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                    }
                    val prevIdx = (targetedIdx - 1 + ringApps.size) % ringApps.size
                    val temp = ringApps[targetedIdx]
                    ringApps[targetedIdx] = ringApps[prevIdx]
                    ringApps[prevIdx] = temp
                    prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                    gearRingRotations[activeHangarRing] = (gearRingRotations[activeHangarRing] + (360f / count)) % 360f
                    triggerHardwareHaptic(30, 160)
                    invalidate()
                    return true
                }

                // Check Live Orbital Shift Right
                if (shiftCogRightRect.contains(x, y) && ringApps.size > 1) {
                    val count = ringApps.size
                    val baseRotation = gearRingRotations[activeHangarRing]
                    var targetedIdx = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in ringApps.indices) {
                        val angleDeg = (baseRotation + i * (360f / count)) % 360f
                        val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                        val diff = kotlin.math.abs(norm - targetFocusAngle).let { if (it > 180f) 360f - it else it }
                        if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                    }
                    val nextIdx = (targetedIdx + 1) % ringApps.size
                    val temp = ringApps[targetedIdx]
                    ringApps[targetedIdx] = ringApps[nextIdx]
                    ringApps[nextIdx] = temp
                    prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                    gearRingRotations[activeHangarRing] = (gearRingRotations[activeHangarRing] - (360f / count)) % 360f
                    triggerHardwareHaptic(30, 160)
                    invalidate()
                    return true
                }

                val distFromCore = kotlin.math.hypot(x - gimbalCx, y - cyGimbal)
                val ringRad = if (activeHangarRing == 0) radOuter else radInner
                val targetAngleRad = Math.toRadians(targetFocusAngle.toDouble())
                val rotRadFocused = Math.toRadians(gearRingRotations[activeHangarRing].toDouble())
                val (reticleX, reticleY) = DeepSpaceGearGeometry.pointOnGearPerimeter(gimbalCx, cyGimbal, ringRad, targetAngleRad - rotRadFocused, rotRadFocused, gearShape)
                val distFromReticle = kotlin.math.hypot(x - reticleX, y - reticleY)
                val targetBadgeRect = RectF(gimbalCx - 75f * d, shiftBarY - 14f * d, gimbalCx + 75f * d, shiftBarY + 14f * d)

                // 1. Center Command Core Tap -> Open Gear Picker for Active Ring
                if (distFromCore <= radHub) {
                    isHangarEjectArmed = false
                    val intent = Intent(context, CockpitGearPickerActivity::class.java).apply {
                        putExtra("SET_ID", currentSetId)
                        putExtra("RING_INDEX", activeHangarRing)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    }
                    context.startActivity(intent)
                    uiHandler.postDelayed({ dismissOverlay() }, 150)
                    return true
                }

                // 2. Focused Cog or Target Badge -> Long Press to Edit, Tap to Eject
                val isTouchingReticleOrBadge = (distFromReticle <= 48f * d) || targetBadgeRect.contains(x, y)

                if (isTouchingReticleOrBadge && ringApps.isNotEmpty()) {
                    isTouchingFocusedCog = true
                    val count = ringApps.size
                    val baseRotation = gearRingRotations[activeHangarRing]
                    var targetedIdx = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in ringApps.indices) {
                        val angleDeg = (baseRotation + i * (360f / count)) % 360f
                        val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                        val diff = kotlin.math.abs(norm - targetFocusAngle).let { if (it > 180f) 360f - it else it }
                        if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                    }

                    val targetedToken = if (targetedIdx in ringApps.indices) ringApps[targetedIdx] else null
                    if (targetedToken != null) {
                        val runnable = Runnable {
                            hasLongPressFired = true
                            triggerHardwareHaptic(50, 255)
                            val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                                action = CockpitDialogActivity.ACTION_EDIT_ITEM
                                putExtra(CockpitDialogActivity.EXTRA_TOKEN, targetedToken)
                                putExtra(CockpitDialogActivity.EXTRA_SET_INDEX, activeGearSetIndex)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            context.startActivity(intent)
                            uiHandler.postDelayed({ dismissOverlay() }, 150)
                        }
                        longPressCogRunnable = runnable
                        postDelayed(runnable, CruiseOverlayConstants.LONG_PRESS_THRESHOLD_MS)
                    }
                    return true
                }

                if (distFromCore in (radInner + 16f * d)..(radOuter + 32f * d)) {
                    isHangarEjectArmed = false
                    activeHangarRing = 0
                    isSpinningHangarRing = true
                    hangarSpinTouchY = y
                    triggerHardwareHaptic(15, 80)
                    invalidate()
                    return true
                } else if (distFromCore in (radHub + 4f * d)..(radInner + 16f * d)) {
                    isHangarEjectArmed = false
                    activeHangarRing = 1
                    isSpinningHangarRing = true
                    hangarSpinTouchY = y
                    triggerHardwareHaptic(15, 80)
                    invalidate()
                    return true
                }

                // 8. Bottom Exit Capsule
                if (layout.exitRect.contains(x, y) || y < (20f * d) || y > (height - 15f * d)) {
                    dismissOverlay()
                    return true
                }
            }
        }
        MotionEvent.ACTION_MOVE -> {
            val distMoved = kotlin.math.hypot(x - hangarBayTouchDownX, y - hangarBayTouchDownY)
            if (distMoved > 10f * d) {
                longPressHangarBayRunnable?.let { removeCallbacks(it) }
                longPressCogRunnable?.let { removeCallbacks(it) }
                longPressHangarBayRunnable = null
                longPressCogRunnable = null
                isTouchingFocusedCog = false
            }

            if (isDraggingHangarBays) {
                val dx = x - hangarBayTouchDownX
                hangarBayTouchDownX = x
                if (maxScroll > 0f) {
                    hangarBayScrollOffset = (hangarBayScrollOffset + dx).coerceIn(-maxScroll, 0f)
                    invalidate()
                }
                return true
            } else if (isSpinningHangarRing) {
                val dy = y - hangarSpinTouchY
                hangarSpinTouchY = y
                val physicsProfile = prefs.getString(LightspeedPreferences.KEY_GEAR_PHYSICS_PROFILE, "magnetic") ?: "magnetic"
                val multiplier = when (physicsProfile) {
                    "magnetic" -> 1.0f
                    "fluid" -> 1.45f
                    "heavy" -> 0.72f
                    else -> 1.0f
                }
                val baseDegreesPerDp = 1.35f * multiplier
                val dyInDp = dy / d
                val rotDelta = dyInDp * baseDegreesPerDp
                val oldRot = gearRingRotations[activeHangarRing]
                val newRot = (oldRot + rotDelta) % 360f
                gearRingRotations[activeHangarRing] = newRot

                val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing)
                if (ringApps.isNotEmpty()) {
                    val step = 360f / ringApps.size
                    val oldNotch = (((oldRot % 360f) + 360f) % 360f / step).toInt()
                    val newNotch = (((newRot % 360f) + 360f) % 360f / step).toInt()
                    if (oldNotch != newNotch) {
                        triggerGearCogHaptic()
                    }
                }
                invalidate()
                return true
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            longPressHangarBayRunnable?.let { removeCallbacks(it) }
            longPressCogRunnable?.let { removeCallbacks(it) }
            longPressHangarBayRunnable = null
            longPressCogRunnable = null

            if (hasLongPressFired) {
                hasLongPressFired = false
                isTouchingFocusedCog = false
                isSpinningHangarRing = false
                isDraggingHangarBays = false
                return true
            }

            if (isTouchingFocusedCog) {
                isTouchingFocusedCog = false
                isSpinningHangarRing = false
                val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing).toMutableList()
                if (ringApps.isNotEmpty()) {
                    val count = ringApps.size
                    val baseRotation = gearRingRotations[activeHangarRing]
                    var targetedIdx = 0
                    var minDiff = Float.MAX_VALUE
                    for (i in ringApps.indices) {
                        val angleDeg = (baseRotation + i * (360f / count)) % 360f
                        val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                        val diff = kotlin.math.abs(norm - targetFocusAngle).let { if (it > 180f) 360f - it else it }
                        if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                    }

                    if (isHangarEjectArmed && hangarEjectTargetIndex == targetedIdx && hangarEjectRing == activeHangarRing) {
                        // CONFIRM EJECT: Purge targeted cog from ring
                        ringApps.removeAt(targetedIdx)
                        prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                        isHangarEjectArmed = false
                        hangarEjectTargetIndex = -1
                        triggerHardwareHaptic(60, 255)
                        invalidate()
                        return true
                    } else {
                        // ARM EJECT: Show red containment and white X on the tapped cog, and show badge alert with eject shake
                        isHangarEjectArmed = true
                        hangarEjectTargetIndex = targetedIdx
                        hangarEjectRing = activeHangarRing
                        triggerHardwareHaptic(40, 220)
                        invalidate()
                        return true
                    }
                }
            }

            isSpinningHangarRing = false
            val distMoved = kotlin.math.hypot(x - hangarBayTouchDownX, y - hangarBayTouchDownY)
            if (isDraggingHangarBays) {
                isDraggingHangarBays = false
                if (distMoved < 14f * d) {
                    for (gIndex in setsList.indices) {
                        val btnX = railStartX + (gIndex + 0.5f) * slotW
                        val bayRect = RectF(btnX - slotW * 0.46f, rBayY - 16f * d, btnX + slotW * 0.46f, rBayY + 16f * d)
                        if (bayRect.contains(x, y)) {
                            if (gIndex >= 2 && !com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                                triggerHardwareHaptic(50, 255)
                                showInfinityPrompt()
                                invalidate()
                                return true
                            }
                            activeGearSetIndex = gIndex
                            persistActiveGearSetIndex()
                            com.sbf.lightspeed.system.LightspeedHapticEngine.cockpitGearJump(context)
                            invalidate()
                            return true
                        }
                    }
                    val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
                    val plusRect = RectF(plusBtnX - slotW * 0.42f, rBayY - 16f * d, plusBtnX + slotW * 0.42f, rBayY + 16f * d)
                    if (plusRect.contains(x, y)) {
                        if (setsList.size >= 2 && !com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)) {
                            triggerHardwareHaptic(50, 255)
                            showInfinityPrompt()
                            invalidate()
                            return true
                        }
                        val newId = System.currentTimeMillis().toString()
                        setsList.add(newId)
                        saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                        prefs.edit().putString("gear_set_${newId}_name", "SET ${setsList.size}").apply()
                        activeGearSetIndex = setsList.size - 1
                        val newTotalRailW = (setsList.size + 1) * slotW
                        val newMaxScroll = (newTotalRailW - deckW).coerceAtLeast(0f)
                        hangarBayScrollOffset = -newMaxScroll
                        triggerHardwareHaptic(40, 200)
                        invalidate()
                        return true
                    }
                }
                invalidate()
                return true
            }
        }
    }
    return true
}
