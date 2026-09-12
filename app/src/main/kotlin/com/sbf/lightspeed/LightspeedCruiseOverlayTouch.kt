package com.sbf.lightspeed
import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.Toast
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedHudRenderer
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.defaultPrefs
import java.net.URISyntaxException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin


internal fun LightspeedCruiseOverlay.handleTouchEvent(event: MotionEvent, superCall: () -> Boolean): Boolean {
        val x = event.x; val y = event.y
        val rawX = event.rawX; val rawY = event.rawY
        currentTouchY = if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) -1f else y
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            val d = resources.displayMetrics.density
            val screenW = width.toFloat()
            val screenH = height.toFloat()
            val cx = screenW / 2f
            val cy = screenH / 2f
            val deckW = (screenW * 0.92f).coerceAtMost(480f * d)
            val leftX = cx - (deckW / 2f)
            val rightX = cx + (deckW / 2f)
            val gap = 8f * d

            val topHangarY = (screenH * 0.07f).coerceAtLeast(54f * d)
            val prefs = prefs()
            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
            if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
            val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"

            val r0Y = topHangarY + 40f * d
            val r1Y = r0Y + 38f * d
            val r2Y = r1Y + 44f * d
            val r3Y = r2Y + 44f * d
            val r4Y = r3Y + 38f * d
            val r5Y = r4Y + 38f * d
            val r6Y = r5Y + 38f * d
            val r7Y = r6Y + 35f * d

            val cyGimbal = (r7Y + 160f * d).coerceAtLeast(screenH * 0.62f)
            val radOuter = 135f * d
            val radInner = 84f * d
            val radHub = 32f * d
            val shiftBarY = cyGimbal + radOuter + 26f * d
            val transferBarY = cyGimbal + radOuter + 58f * d

            val bayCount = setsList.size + 1
            val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
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
                    isDraggingHangarBays = (y in (r2Y - 24f * d)..(r2Y + 24f * d)) && (x in (leftX - 10f * d)..(rightX + 10f * d))

                    if (isDraggingHangarBays) {
                        for (gIndex in setsList.indices) {
                            val btnX = railStartX + (gIndex + 0.5f) * slotW
                            val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)
                            if (bayRect.contains(x, y)) {
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
                                postDelayed(runnable, 400)
                                break
                            }
                        }
                    } else {
                        // 0. Flank Switcher: Port (Left) vs Starboard (Right)
                        val halfBtnW = (deckW - gap) / 2f
                        val portRect = RectF(leftX, r0Y - 16f * d, leftX + halfBtnW, r0Y + 16f * d)
                        val starboardRect = RectF(rightX - halfBtnW, r0Y - 16f * d, rightX, r0Y + 16f * d)
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

                        // 1. Startup Default Mode: Always First vs Resume Last (Per-Flank)
                        val btn1Rect = RectF(leftX, r1Y - 16f * d, leftX + halfBtnW, r1Y + 16f * d)
                        val btn2Rect = RectF(rightX - halfBtnW, r1Y - 16f * d, rightX, r1Y + 16f * d)
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

                        // 3. Reorder & Delete Active Profile in Rotation (Per-Flank)
                        val shiftW = deckW * 0.35f
                        val deleteW = deckW * 0.26f
                        val shiftLeftRect = RectF(leftX, r3Y - 16f * d, leftX + shiftW, r3Y + 16f * d)
                        val deleteBayRect = RectF(cx - deleteW / 2f, r3Y - 16f * d, cx + deleteW / 2f, r3Y + 16f * d)
                        val shiftRightRect = RectF(rightX - shiftW, r3Y - 16f * d, rightX, r3Y + 16f * d)

                        if (shiftLeftRect.contains(x, y) && activeGearSetIndex > 0) {
                            val temp = setsList[activeGearSetIndex]
                            setsList[activeGearSetIndex] = setsList[activeGearSetIndex - 1]
                            setsList[activeGearSetIndex - 1] = temp
                            activeGearSetIndex--
                            saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                            val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                            hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                            triggerHardwareHaptic(30, 160)
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
                            triggerHardwareHaptic(30, 160)
                            invalidate()
                            return true
                        }
                        if (deleteBayRect.contains(x, y)) {
                            if (setsList.size > 1) {
                                val removedId = setsList.removeAt(activeGearSetIndex)
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
                                android.widget.Toast.makeText(context, "Cannot delete the last remaining gear set", android.widget.Toast.LENGTH_SHORT).show()
                                return true
                            }
                        }

                        // 4. Icon Theme Carousel
                        val iconThemeRect = RectF(leftX, r4Y - 18f * d, rightX, r4Y + 18f * d)
                        if (iconThemeRect.contains(x, y)) {
                            val availablePacks = com.sbf.lightspeed.system.LightspeedIconManager.getAvailableIconPacks(context)
                            if (availablePacks.isNotEmpty()) {
                                val activePack = com.sbf.lightspeed.system.LightspeedIconManager.getActiveIconPack(context)
                                val currentIndex = availablePacks.indexOfFirst { it.packageName == activePack }
                                val nextIndex = (currentIndex + 1) % availablePacks.size
                                com.sbf.lightspeed.system.LightspeedIconManager.setActiveIconPack(context, availablePacks[nextIndex].packageName)
                                triggerHardwareHaptic(35, 180)
                                invalidate()
                                return true
                            }
                        }

                        // 5. Flight Momentum
                        val physW = (deckW - (gap * 2)) / 3f
                        val phys1Rect = RectF(leftX, r5Y - 16f * d, leftX + physW, r5Y + 16f * d)
                        val phys2Rect = RectF(leftX + physW + gap, r5Y - 16f * d, leftX + physW * 2 + gap, r5Y + 16f * d)
                        val phys3Rect = RectF(rightX - physW, r5Y - 16f * d, rightX, r5Y + 16f * d)
                        if (phys1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "magnetic").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (phys2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "fluid").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (phys3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "heavy").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 6. Tactile Ratchet Haptics
                        val hapW = (deckW - (gap * 3)) / 4f
                        val hap1Rect = RectF(leftX, r6Y - 15f * d, leftX + hapW, r6Y + 15f * d)
                        val hap2Rect = RectF(leftX + (hapW + gap), r6Y - 15f * d, leftX + (hapW + gap) + hapW, r6Y + 15f * d)
                        val hap3Rect = RectF(leftX + (hapW + gap) * 2, r6Y - 15f * d, leftX + (hapW + gap) * 2 + hapW, r6Y + 15f * d)
                        val hap4Rect = RectF(rightX - hapW, r6Y - 15f * d, rightX, r6Y + 15f * d)
                        if (hap1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "subtle").apply()
                            triggerHardwareHaptic(10, 60)
                            invalidate()
                            return true
                        }
                        if (hap2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "tactical").apply()
                            triggerHardwareHaptic(20, 140)
                            invalidate()
                            return true
                        }
                        if (hap3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "heavy").apply()
                            triggerHardwareHaptic(35, 240)
                            invalidate()
                            return true
                        }
                        if (hap4Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "off").apply()
                            invalidate()
                            return true
                        }

                        // 7. Reticle Crosshair Style
                        val retW = (deckW - (gap * 3)) / 4f
                        val ret1Rect = RectF(leftX, r7Y - 14f * d, leftX + retW, r7Y + 14f * d)
                        val ret2Rect = RectF(leftX + (retW + gap), r7Y - 14f * d, leftX + (retW + gap) + retW, r7Y + 14f * d)
                        val ret3Rect = RectF(leftX + (retW + gap) * 2, r7Y - 14f * d, leftX + (retW + gap) * 2 + retW, r7Y + 14f * d)
                        val ret4Rect = RectF(rightX - retW, r7Y - 14f * d, rightX, r7Y + 14f * d)
                        if (ret1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "tactical").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "cyber").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "cross").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret4Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "diamond").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 7. Live Gimbal Touch & Interaction
                        val shiftCogLeftRect = RectF(cx - 150f * d, shiftBarY - 14f * d, cx - 60f * d, shiftBarY + 14f * d)
                        val shiftCogRightRect = RectF(cx + 60f * d, shiftBarY - 14f * d, cx + 150f * d, shiftBarY + 14f * d)
                        val transferRect = RectF(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
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
                                val diff = kotlin.math.abs(norm - 180f)
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
                                val diff = kotlin.math.abs(norm - 180f)
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
                                val diff = kotlin.math.abs(norm - 180f)
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

                        val distFromCore = kotlin.math.hypot(x - cx, y - cyGimbal)
                        val reticleX = cx - (if (activeHangarRing == 0) radOuter else radInner)
                        val reticleY = cyGimbal
                        val distFromReticle = kotlin.math.hypot(x - reticleX, y - reticleY)
                        val targetBadgeRect = RectF(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)

                        // 1. Center Command Core Tap -> Open Gear Picker for Active Ring
                        if (distFromCore <= radHub) {
                            isHangarEjectArmed = false
                            val intent = android.content.Intent(context, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SET_ID", currentSetId)
                                putExtra("RING_INDEX", activeHangarRing)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
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
                                val diff = kotlin.math.abs(norm - 180f)
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
                                postDelayed(runnable, 400)
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
                        val exitBtnRect = RectF(cx - (deckW * 0.42f), screenH - 58f * d, cx + (deckW * 0.42f), screenH - 18f * d)
                        if (exitBtnRect.contains(x, y) || y < (20f * d) || y > (height - 15f * d)) {
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
                        val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
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
                                val diff = kotlin.math.abs(norm - 180f)
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
                                val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)
                                if (bayRect.contains(x, y)) {
                                    activeGearSetIndex = gIndex
                                    persistActiveGearSetIndex()
                                    triggerHardwareHaptic(25, 140)
                                    invalidate()
                                    return true
                                }
                            }
                            val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
                            val plusRect = RectF(plusBtnX - slotW * 0.42f, r2Y - 18f * d, plusBtnX + slotW * 0.42f, r2Y + 18f * d)
                            if (plusRect.contains(x, y)) {
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

        if (isStickyPinned) {
            val hF = height.toFloat()
            val gridTopLimit = hF * 0.15f
            val gridBottomLimit = hF * 0.94f
            val gridHeightScope = gridBottomLimit - gridTopLimit
            val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = x; touchDownY = y; lastTouchY = y
                    activeItem = null
                    if (y in gridTopLimit..gridBottomLimit) {
                        val absoluteY = y + viewportScrollOffset
                        for (placedItem in placedAppsList) {
                            if (placedItem.bounds.contains(x, absoluteY)) { activeItem = placedItem.app; break }
                        }
                    }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = y - lastTouchY
                    lastTouchY = y
                    if (abs(y - touchDownY) > 16f || abs(x - touchDownX) > 16f) activeItem = null
                    if (maxScroll > 0f) {
                        viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                        if (y in gridTopLimit..gridBottomLimit) activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                        else dismissOverlay()
                    }
                    activeItem = null; invalidate(); return true
                }
                MotionEvent.ACTION_CANCEL -> { isCurrentlyTouched = false; activeItem = null; invalidate(); return true }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = rawX; touchDownRawY = rawY
                lastTouchRawX = rawX; lastTouchRawY = rawY
                touchDownTime = System.currentTimeMillis()
                gestureStartX = rawX; gestureStartY = rawY

                trackingStateLocked = false
                isCurrentlyTouched = true; invalidate()
                initialLeftSweepDistance = 0f
                lowestXReached = rawX
                highestYReached = rawY
                lowestYReached = rawY
                aggregateScrubAccumulator = 0f
                isScrubEntranceHapticFired = false
                currentDetectedGesture = MacroGesture.NONE
                overScrollBoundaryAccumulator = 0f
                settingsCategoryAppended = false

                currentActiveZone = when {
                    centerTouchBounds.contains(x, y) -> TouchZone.CENTER_CRUISE
                    topTouchBounds.contains(x, y) -> TouchZone.TOP_EDGE
                    bottomTouchBounds.contains(x, y) -> TouchZone.BOTTOM_EDGE
                    else -> TouchZone.NONE
                }

                if (currentActiveZone == TouchZone.CENTER_CRUISE) {
                    startCruiseFromFlank(isLeft = false, startRawX = rawX, startRawY = rawY)
                } else if (currentActiveZone != TouchZone.NONE) {
                    macroTrackingActive = true
                    uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
                if (isCruising) {
                    val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
                    val deltaY = abs(rawY - touchDownRawY)
                    if (deltaY > maxVerticalDisplacement) {
                        maxVerticalDisplacement = deltaY
                    }

                    // 1. Direct Lateral Swipe from rest -> Open Gears / Cockpit immediately
                    // Zero category flicker because NEUTRAL never painted categories on screen!
                    if ((currentLayer == CruiseLayer.NEUTRAL || currentLayer == CruiseLayer.CATEGORY) &&
                        !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX > (deltaY * 1.1f)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        val cPrefs = prefs()
                        val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                        val launchBehavior = getFlankLaunchBehavior(isOpenedFromLeftFlank)
                        val lastActiveKey = if (isOpenedFromLeftFlank) "last_active_set_index_left" else "last_active_set_index_right"
                        if (launchBehavior == "last") {
                            val lastIndex = cPrefs.getInt(lastActiveKey, cPrefs.getInt("last_active_set_index", 0))
                            activeGearSetIndex = if (lastIndex in setsList.indices) lastIndex else 0
                        } else {
                            activeGearSetIndex = 0
                        }
                        currentLayer = CruiseLayer.FAVORITES_GEARS
                        triggerHardwareHaptic(30, 180)
                    }

                    // 2. Deliberate vertical movement -> Engage Category 3D Cylinder
                    if (currentLayer == CruiseLayer.NEUTRAL && maxVerticalDisplacement > (14f * density)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        categoryScrubbingEngaged = true
                        currentLayer = CruiseLayer.CATEGORY
                        entranceStartTime = System.currentTimeMillis()
                    }

                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        processGyroscopeTouchPhysics(rawX, rawY)
                    } else if (currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) {
                        evaluateSpatialMetrics(rawX, rawY, x, y)
                    }
                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                } else if (macroTrackingActive) {
                    val previousGesture = currentDetectedGesture
                    val deltaX = rawX - gestureStartX
                    val deltaY = rawY - gestureStartY

                    if (rawX < lowestXReached) lowestXReached = rawX
                    if (rawY > highestYReached) highestYReached = rawY
                    if (rawY < lowestYReached) lowestYReached = rawY

                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val thresholdX_Scrub = screenW * 0.333f 
                    val thresholdY_Compound = 60f

                    if (currentDetectedGesture == MacroGesture.NONE) {
                        if (abs(deltaX) > (22f * density) && abs(deltaX) > abs(deltaY)) {
                            currentDetectedGesture = MacroGesture.SWIPE_LEFT
                            initialLeftSweepDistance = abs(deltaX)
                        } else if (deltaY < (-25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_UP
                        } else if (deltaY > (25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_DOWN
                        }
                    }

                    when (currentDetectedGesture) {
                        MacroGesture.SWIPE_LEFT -> {
                            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
                            val dynamicZone = if (prefs().getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = prefs().getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                scrubStartX = rawX
                                activeHoldScrubAction = assignedScrub
                                activeHoldScrubActionKey = "pref_macro_action_${dynamicZone}_SCRUBBING"
                                uiHandler.removeCallbacks(holdTimerRunnable)
                                aggregateScrubAccumulator = 0f
                                if (!isScrubEntranceHapticFired) {
                                    triggerHardwareHaptic(65, 255)
                                    isScrubEntranceHapticFired = true
                                }
                                when (assignedScrub) {
                                    "system:volume", "scrub:volume" -> {
                                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val volResolution = prefs().getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                                        val pct = kotlin.math.round(currentVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                                        activeScrubVolumePct = pct
                                        scrubHudTitle = "MEDIA VOLUME"
                                        scrubHudValue = "$pct%"
                                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (pct * volResolution / 100).coerceIn(0, volResolution), volResolution)
                                    }
                                    "system:brightness", "scrub:brightness" -> {
                                        val currentBrightness = try {
                                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                        } catch (_: Exception) { 128 }
                                        val brightResolution = prefs().getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                                        scrubHudTitle = "BRIGHTNESS"
                                        scrubHudValue = "${(currentBrightness * 100 / 255)}%"
                                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (currentBrightness * brightResolution / 255), brightResolution)
                                    }
                                    "system:screen_timeout" -> {
                                        scrubHudTitle = "SHIP GOES DARK IN"
                                        val idx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                                        scrubHudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[idx].second
                                        dispatchScrubHud(scrubHudTitle, scrubHudValue, idx, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                                    }
                                }
                                invalidate()
                            } else if (deltaY < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_UP
                            } else if (deltaY > (25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_DOWN
                            } else {
                                val currentReturnRightDistance = rawX - lowestXReached
                                if (currentReturnRightDistance > (18f * density)) {
                                    currentDetectedGesture = MacroGesture.SWIPE_LEFT_BACK
                                }
                            }
                        }
                        MacroGesture.SWIPE_UP -> {
                            if ((rawY - lowestYReached) > (22f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_DOWN
                            } else if (deltaX < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_LEFT
                            }
                        }
                        MacroGesture.SWIPE_DOWN -> {
                            if ((highestYReached - rawY) > (22f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_UP
                            } else if (deltaX < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_LEFT
                            }
                        }
                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            
                            val isVolume = activeHoldScrubAction == "scrub:volume" || activeHoldScrubAction == "system:volume"
                            val isBrightness = activeHoldScrubAction == "scrub:brightness" || activeHoldScrubAction == "system:brightness"
                            
                            val horizontalPull = kotlin.math.abs(rawX - scrubStartX)
                            if (horizontalPull > 80f * density && (isVolume || isBrightness)) {
                                currentDetectedGesture = MacroGesture.NONE
                                macroTrackingActive = false // Portal Line Lock: Prevent re-triggering until finger lifts
                                isCruising = false
                                LightspeedStatusBarOverlay.dismissActionHud(0L)
                                val actionStr = if (isVolume) "com.sbf.lightspeed.OMNISCIENT_AUDIO" else "com.sbf.lightspeed.OMNISCIENT_DISPLAY"
                                val intent = android.content.Intent(actionStr).apply {
                                    setPackage(context.packageName)
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK or android.content.Intent.FLAG_ACTIVITY_NO_ANIMATION)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {}
                                return true
                            }
                            
                            val dx = rawX - lastTouchRawX
                            val dy = rawY - lastTouchRawY
                            // Axis Lock: Lightspeed side deflectors ALWAYS use Y-axis.
                            val pixelDelta = -dy
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)
                        }
                        else -> {}
                    }

                    if (currentDetectedGesture != MacroGesture.SCRUBBING && !currentDetectedGesture.name.endsWith("_HOLD")) {
                        val moveDelta = hypot(rawX - lastTouchRawX, rawY - lastTouchRawY)
                        if (currentDetectedGesture != previousGesture || moveDelta > (3f * density)) {
                            if (currentDetectedGesture != previousGesture && previousGesture == MacroGesture.NONE) {
                                if (com.sbf.lightspeed.system.LightspeedPreferences.isDeflectorGlowOnGestureStep(context)) {
                                    triggerGlow(500L)
                                }
                            }
                            resetHoldTimer()
                        }
                    }

                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isCurrentlyTouched = false
                isScrubEntranceHapticFired = false
                uiHandler.removeCallbacks(holdTimerRunnable)
                if (isCruising) {
                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
                        if (packages.isNotEmpty() && activeGearRing in 0..1) {
                            val itemCount = packages.size
                            val currentRotation = gearRingRotations[activeGearRing]
                            
                            var targetedPackage: String? = null
                            var minAngleDiff = Float.MAX_VALUE
                            
                            for (i in packages.indices) {
                                val itemAngle = (currentRotation + i * (360f / itemCount)) % 360f
                                val normalizedAngle = if (itemAngle < 0) itemAngle + 360f else itemAngle
                                val diff = abs(normalizedAngle - 180f)
                                if (diff < minAngleDiff) {
                                    minAngleDiff = diff
                                    targetedPackage = packages[i]
                                }
                            }
                            
                            if (targetedPackage != null) {
                                val density = resources.displayMetrics.density
                                val cx = width / 2f
                                val cy = height / 2f
                                val rad0 = 310f * (density / 2.6f).coerceAtLeast(0.9f)
                                val rad1 = 190f * (density / 2.6f).coerceAtLeast(0.9f)
                                val currentTrackRadius = if (activeGearRing == 0) rad0 else rad1
                                val targetedX = cx - currentTrackRadius
                                val targetedY = cy

                                triggerHyperdriveWarpLaunch(targetedX, targetedY) {
                                    com.sbf.lightspeed.system.ActionDispatcher.execute(service ?: context, targetedPackage)
                                }
                                return true
                            }
                        } else if (activeGearRing == 2) {
                            triggerHardwareHaptic(50, 220)
                            persistActiveGearSetIndex()
                            
                            // Align hangar bay profile rail to center the active gear set
                            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                            val d = resources.displayMetrics.density
                            val deckW = width * 0.88f
                            val bayCount = setsList.size + 1
                            val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
                            val totalBayRailW = bayCount * slotW
                            val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
                            if (totalBayRailW > deckW) {
                                val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                                hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                            } else {
                                hangarBayScrollOffset = 0f
                            }
                            
                            currentLayer = CruiseLayer.COCKPIT_HANGAR
                            invalidate()
                            return true
                        }
                        currentLayer = CruiseLayer.HIDDEN
                        isCruising = false
                        service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
                        updateMetricsDimensions()
                        invalidate()
                        return true
                    }

                    uiHandler.removeCallbacks(neutralToCategoryRunnable)
                    if (currentLayer == CruiseLayer.STICKY_PIN) {
                        isStickyPinned = true; currentLayer = CruiseLayer.GRID
                        updateMetricsDimensions(); invalidate()
                    } else if (currentLayer == CruiseLayer.CATEGORY && activeCatIndex in cachedCategories.indices && cachedCategories[activeCatIndex].id == "launcher_settings_virtual_id") {
                        launchLauncherSettings()
                    } else if (currentLayer == CruiseLayer.NEUTRAL) {
                        val duration = System.currentTimeMillis() - touchDownTime
                        val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
                        dismissOverlay()
                        if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                            triggerHardwareHaptic(25, 120)
                            service?.triggerDeflectorsGlow() ?: triggerGlow()
                        }
                    } else {
                        val launchTarget = activeItem
                        if (launchTarget != null) {
                            val placed = placedAppsList.find { it.app == launchTarget }
                            val focalX = placed?.bounds?.centerX() ?: (width / 2f)
                            val focalY = placed?.bounds?.centerY() ?: (height / 2f)
                            triggerHyperdriveWarpLaunch(focalX, focalY) {
                                executeLaunch(launchTarget)
                            }
                        } else {
                            dismissOverlay()
                        }
                    }
                    isCruising = false
                } else if (macroTrackingActive) {
                    macroTrackingActive = false
                    if (currentDetectedGesture != MacroGesture.NONE && currentDetectedGesture != MacroGesture.SCRUBBING) {
                        executeMacroAction(currentActiveZone, currentDetectedGesture)
                    } else if (currentDetectedGesture == MacroGesture.NONE) {
                        val duration = System.currentTimeMillis() - touchDownTime
                        val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
                        if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                            triggerHardwareHaptic(25, 120)
                            service?.triggerDeflectorsGlow() ?: triggerGlow()
                        }
                    }
                }
                if (currentDetectedGesture == MacroGesture.SCRUBBING) {
                    LightspeedStatusBarOverlay.dismissActionHud(1200L)
                }
                activeHoldScrubAction = null
                activeHoldScrubActionKey = null
                activeScrubVolumePct = -1
                scrubHudTitle = ""
                scrubHudValue = ""
                invalidate()
                currentActiveZone = TouchZone.NONE
                return true
            }
        }
        return superCall()
}
