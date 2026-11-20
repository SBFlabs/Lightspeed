package com.sbf.lightspeed

import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Handles touch when the overlay is in sticky-pinned grid mode.
 *
 * @return true if the event was consumed.
 */
internal fun LightspeedCruiseOverlay.handleStickyPinTouch(event: MotionEvent): Boolean {
    val x = event.x; val y = event.y
    val hF = height.toFloat()
    val activeCatId = cachedCategories.getOrNull(activeCatIndex)?.id ?: "comm"
    val gridTopLimit = getCategoryGridTop(activeCatId)
    val gridBottomLimit = hF * 0.98f
    val gridHeightScope = gridBottomLimit - gridTopLimit
    val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

    if (isAlertsHistoryModalOpen) {
        return handleAlertsHistoryModalTouch(event)
    }

    if (isFirewallModalOpen) {
        return handleFirewallManagerModalTouch(event)
    }

    if (isVitalsModalOpen) {
        return handleVitalsTaskManagerModalTouch(event)
    }

    if (isPrivacyScoreInfoBubbleOpen) {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            isPrivacyScoreInfoBubbleOpen = false
            invalidate()
        }
        return true
    }

    if (isHudEditMode) {
        if (isHudTuneModalOpen) {
            return handleHudTuneModalTouch(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x; touchDownY = y; lastTouchY = y
                stickyPinLongPressFired = false
                editModeHoldFired = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = y - lastTouchY
                lastTouchY = y
                computeHudSlotBounds()
                if (hudMaxScroll > 0f) {
                    hudScrollOffset = (hudScrollOffset - deltaY).coerceIn(0f, hudMaxScroll)
                    updateCategoryAppWidgetView()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (stickyPinLongPressFired || editModeHoldFired) {
                    stickyPinLongPressFired = false
                    editModeHoldFired = false
                    return true
                }
                val dp = resources.displayMetrics.density
                val maxTapSlop = (24f * dp).coerceAtLeast(24f)
                val isTap = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < maxTapSlop
                if (isTap) {
                    handleHudEditModeTap(x, y)
                }
                activeItem = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                stickyPinLongPressFired = false
                editModeHoldFired = false
                activeItem = null
                invalidate()
                return true
            }
        }
        return true
    }

    if (isEditModeOpen) {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            editModeHoldFired = false
        }
        if (editModeHoldFired) {
            return true
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x; touchDownY = y; lastTouchY = y
                editModeHasDragged = false
                cancelEditModeDrag()
                hudVisibilityLongPressRunnable?.let { removeCallbacks(it) }
                hudVisibilityLongPressRunnable = null
                if (!isEditModePromptOpen && !isCategoryIconPickerOpen) {
                    if (editModeTab == EditModeTab.PAGE) {
                        val hitRow = getEditModeRowAt(x, y)
                        if (hitRow >= 0) {
                            val runnable = Runnable {
                                startEditModeDrag(hitRow, lastTouchY)
                            }
                            editModeDragRunnable = runnable
                            postDelayed(runnable, 500L)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouchY = y
                if (isCategoryIconPickerOpen) {
                    return true
                }
                val dp = resources.displayMetrics.density
                val dist = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble())

                if (editModeDragSourceIndex >= 0) {
                    if (dist > 12f * dp) {
                        editModeHasDragged = true
                    }
                    editModeDragY = y
                    val bounds = getEditModePanelBounds()
                    val numCategories = editModeCategories.size
                    if (numCategories > 1) {
                        val totalRows = numCategories + 2
                        val actualRowH = bounds.height() / totalRows
                        val tabRowBottom = bounds.top + actualRowH
                        val halfH = actualRowH / 2f
                        val clampedY = y.coerceIn(tabRowBottom + halfH, tabRowBottom + numCategories * actualRowH - halfH)
                        val target = ((clampedY - tabRowBottom) / actualRowH).toInt().coerceIn(0, numCategories - 1)
                        if (target != editModeDragTargetIndex) {
                            editModeDragTargetIndex = target
                            editModeHasDragged = true
                            triggerHardwareHaptic(10, 60)
                        }
                    }
                    invalidate()
                    return true
                }
                if (dist > 16f) {
                    editModeDragRunnable?.let { removeCallbacks(it) }
                    editModeDragRunnable = null
                    hudVisibilityLongPressRunnable?.let { removeCallbacks(it) }
                    hudVisibilityLongPressRunnable = null
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                editModeDragRunnable?.let { removeCallbacks(it) }
                editModeDragRunnable = null
                hudVisibilityLongPressRunnable?.let { removeCallbacks(it) }
                hudVisibilityLongPressRunnable = null

                if (editModeHoldFired) {
                    editModeHoldFired = false
                    activeItem = null
                    invalidate()
                    return true
                }

                if (editModeDragSourceIndex >= 0) {
                    val src = editModeDragSourceIndex
                    val tgt = editModeDragTargetIndex
                    val didDrag = editModeHasDragged || (src != tgt)

                    if (didDrag) {
                        if (src != tgt && src in editModeCategories.indices && tgt in editModeCategories.indices) {
                            val item = editModeCategories.removeAt(src)
                            editModeCategories.add(tgt, item)
                            triggerHardwareHaptic(20, 100)
                        }
                    } else {
                        // Long-pressed and lifted finger without dragging
                        if (src in editModeCategories.indices) {
                            openCategoryIconPackPicker(editModeCategories[src])
                        }
                    }
                    editModeDragSourceIndex = -1
                    editModeDragTargetIndex = -1
                    editModeHasDragged = false
                    activeItem = null
                    invalidate()
                    return true
                }

                val isTap = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f
                if (isTap) {
                    handleEditModeTap(x, y)
                }
                activeItem = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                editModeDragRunnable?.let { removeCallbacks(it) }
                editModeDragRunnable = null
                hudVisibilityLongPressRunnable?.let { removeCallbacks(it) }
                hudVisibilityLongPressRunnable = null
                editModeHoldFired = false

                if (editModeDragSourceIndex >= 0) {
                    editModeDragSourceIndex = -1
                    editModeDragTargetIndex = -1
                    editModeHasDragged = false
                    activeItem = null
                    invalidate()
                    return true
                }

                if (isCategoryIconPickerOpen) {
                    closeCategoryIconPackPicker()
                    activeItem = null
                    invalidate()
                    return true
                }

                closeEditMode()
                activeItem = null
                invalidate()
                return true
            }
        }
        return true
    }

    if (isPinnedAppMenuOpen() && !stickyPinLongPressFired) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = x; touchDownY = y; lastTouchY = y
                stickyPinLongPressRunnable?.let { removeCallbacks(it) }
                stickyPinLongPressRunnable = null
                if (!pinnedMenuCategoryMode && !pinnedMenuNetPillRect.isEmpty && pinnedMenuNetPillRect.contains(x, y)) {
                    val runnable = Runnable {
                        stickyPinLongPressFired = true
                        closePinnedAppMenu()
                        openFirewallManagerModal()
                        triggerHardwareHaptic(35, 180)
                    }
                    stickyPinLongPressRunnable = runnable
                    postDelayed(runnable, 420L)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) > 16f) {
                    stickyPinLongPressRunnable?.let { removeCallbacks(it) }
                    stickyPinLongPressRunnable = null
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                stickyPinLongPressRunnable?.let { removeCallbacks(it) }
                stickyPinLongPressRunnable = null
                val isTap = hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f
                if (isTap) {
                    handlePinnedAppMenuTap(x, y)
                } else {
                    closePinnedAppMenu()
                }
                activeItem = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                stickyPinLongPressRunnable?.let { removeCallbacks(it) }
                stickyPinLongPressRunnable = null
                closePinnedAppMenu()
                activeItem = null
                invalidate()
                return true
            }
        }
        return true
    }

    when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            touchDownX = x; touchDownY = y; lastTouchY = y
            activeItem = null
            stickyPinLongPressFired = false
            editModeHoldFired = false
            stickyPinLongPressRunnable?.let { removeCallbacks(it) }
            stickyPinLongPressRunnable = null

            // Pinned-view Edge Category Switcher: tap or slide along edge icons with haptic ticks
            if ((isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) &&
                isTouchOnPinnedEdgeSwitcher(x, y) &&
                cachedCategories.isNotEmpty()
            ) {
                isDraggingEdgeCategorySwitcher = true
                val count = cachedCategories.size
                val frac = ((y - pinnedEdgeSwitcherRect.top) / pinnedEdgeSwitcherRect.height().coerceAtLeast(1f)).coerceIn(0f, 0.9999f)
                val targetIdx = (frac * count).toInt().coerceIn(0, count - 1)
                if (targetIdx != activeCatIndex) {
                    activeCatIndex = targetIdx
                    viewportScrollOffset = 0f
                    hudScrollOffset = 0f
                    activeItem = null
                    loadActiveCategoryGrid()
                    triggerHardwareHaptic(18, 95)
                } else {
                    triggerHardwareHaptic(12, 60)
                }
                invalidate()
                return true
            }

            if (y in gridTopLimit..gridBottomLimit) {
                val absoluteY = y + viewportScrollOffset
                for (placedItem in placedAppsList) {
                    if (placedItem.bounds.contains(x, absoluteY)) { activeItem = placedItem.app; break }
                }
            }

            val pressedApp = activeItem
            if (pressedApp != null) {
                val runnable = Runnable {
                    stickyPinLongPressFired = true
                    pinnedMenuApp = pressedApp
                    pinnedMenuX = touchDownX
                    pinnedMenuY = touchDownY
                    pinnedMenuCategoryMode = false
                    triggerHardwareHaptic(35, 180)
                    invalidate()
                }
                stickyPinLongPressRunnable = runnable
                postDelayed(runnable, 500L)
            } else {
                // Tapping or long-pressing empty spaces anywhere (whether above or in the grid)
                // If in the top HUD area (above grid top limit), enter in-place HUD edit mode
                val isFarSplit = isFarHalfSplitMode()
                val isHudArea = if (isFarSplit) {
                    val bounds = getTopHudStripBounds()
                    bounds.contains(x, y) || (if (isOpenedFromLeftFlank) x >= width / 2f else x <= width / 2f)
                } else {
                    y < gridTopLimit
                }
                if (isHudArea) {
                    val runnable = Runnable {
                        stickyPinLongPressFired = true
                        if (!hudFirewallPillRect.isEmpty && hudFirewallPillRect.contains(touchDownX, touchDownY)) {
                            openFirewallManagerModal()
                        } else {
                            isHudEditMode = true
                            editModeHoldFired = true
                        }
                        triggerHardwareHaptic(35, 180)
                        invalidate()
                    }
                    stickyPinLongPressRunnable = runnable
                    postDelayed(runnable, 400L)
                } else {
                    val runnable = Runnable {
                        stickyPinLongPressFired = true
                        openEditMode()
                        editModeHoldFired = true
                        triggerHardwareHaptic(35, 180)
                        invalidate()
                    }
                    stickyPinLongPressRunnable = runnable
                    postDelayed(runnable, 500L)
                }
            }

            invalidate(); return true
        }
        MotionEvent.ACTION_MOVE -> {
            if (isDraggingEdgeCategorySwitcher && cachedCategories.isNotEmpty() && !pinnedEdgeSwitcherRect.isEmpty) {
                val count = cachedCategories.size
                val frac = ((y - pinnedEdgeSwitcherRect.top) / pinnedEdgeSwitcherRect.height().coerceAtLeast(1f)).coerceIn(0f, 0.9999f)
                val targetIdx = (frac * count).toInt().coerceIn(0, count - 1)
                if (targetIdx != activeCatIndex) {
                    activeCatIndex = targetIdx
                    viewportScrollOffset = 0f
                    hudScrollOffset = 0f
                    activeItem = null
                    loadActiveCategoryGrid()
                    triggerHardwareHaptic(16, 85)
                    invalidate()
                }
                return true
            }
            val deltaY = y - lastTouchY
            lastTouchY = y
            if (stickyPinLongPressFired) {
                return true
            }
            if (abs(y - touchDownY) > 16f || abs(x - touchDownX) > 16f) {
                stickyPinLongPressRunnable?.let { removeCallbacks(it) }
                stickyPinLongPressRunnable = null
                activeItem = null
            }

            val catId = cachedCategories.getOrNull(activeCatIndex)?.id ?: "comm"
            val isFarSplit = isFarHalfSplitMode()
            val isHudArea = if (isFarSplit) {
                val bounds = getTopHudStripBounds()
                bounds.contains(touchDownX, touchDownY) || (if (isOpenedFromLeftFlank) touchDownX >= width / 2f else touchDownX <= width / 2f)
            } else {
                touchDownY < gridTopLimit
            }

            if (isHudArea) {
                val hudTouchY = touchDownY + hudScrollOffset
                val bounds1 = getTopHudSlot1Bounds()
                val bounds2 = getTopHudSlot2Bounds()
                val isScreenTime1 = LightspeedCruiseHudPods.getCategoryHudMode(context, catId) == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME
                val isScreenTime2 = LightspeedCruiseHudPods.getCategoryHudSlot2Mode(context, catId) == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME
                val targetHudBounds = if (isScreenTime1 && bounds1.contains(touchDownX, hudTouchY)) bounds1
                    else if (isScreenTime2 && bounds2.contains(touchDownX, hudTouchY)) bounds2
                    else null

                val innerStCanScrollDir = targetHudBounds != null &&
                    touchDownX >= (targetHudBounds.left + targetHudBounds.width() * 0.44f) &&
                    screenTimeAppsMaxScroll > 0f &&
                    ((deltaY < 0f && screenTimeAppsScrollOffset < screenTimeAppsMaxScroll - 0.5f) ||
                        (deltaY > 0f && screenTimeAppsScrollOffset > 0.5f))
                if (innerStCanScrollDir) {
                    screenTimeAppsScrollOffset = (screenTimeAppsScrollOffset - deltaY).coerceIn(0f, screenTimeAppsMaxScroll)
                    invalidate()
                    return true
                }

                val touchedBatteryItem = activeCategoryHudItems.firstOrNull { item ->
                    item.type == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM &&
                        LightspeedCruiseHudPods.getBatteryMode(context, catId, item.slotIndex) == "apps" &&
                        item.bounds.contains(touchDownX, hudTouchY)
                }
                val batMaxScroll = touchedBatteryItem?.let { getBatteryAppsMaxScroll(it.slotIndex) } ?: 0f
                val curBatScroll = touchedBatteryItem?.let { getBatteryAppsScrollOffset(it.slotIndex) } ?: 0f
                val innerBatCanScrollDir = touchedBatteryItem != null &&
                    batMaxScroll > 0f &&
                    ((deltaY < 0f && curBatScroll < batMaxScroll - 0.5f) || (deltaY > 0f && curBatScroll > 0.5f))
                if (innerBatCanScrollDir) {
                    val item = touchedBatteryItem!!
                    val nextOffset = (curBatScroll - deltaY).coerceIn(0f, batMaxScroll)
                    setBatteryAppsScrollOffset(item.slotIndex, nextOffset)
                    invalidate()
                    return true
                }

                computeHudSlotBounds()
                if (hudMaxScroll > 0f) {
                    hudScrollOffset = (hudScrollOffset - deltaY).coerceIn(0f, hudMaxScroll)
                    updateCategoryAppWidgetView()
                    invalidate()
                }
            } else {
                if (maxScroll > 0f) {
                    viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                    invalidate()
                }
            }
            return true
        }
        MotionEvent.ACTION_UP -> {
            if (isDraggingEdgeCategorySwitcher) {
                isDraggingEdgeCategorySwitcher = false
                invalidate()
                return true
            }
            stickyPinLongPressRunnable?.let { removeCallbacks(it) }
            stickyPinLongPressRunnable = null

            if (stickyPinLongPressFired) {
                stickyPinLongPressFired = false
                activeItem = null
                invalidate()
                return true
            }

            if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                val isFarSplit = isFarHalfSplitMode()
                val isHudArea = if (isFarSplit) {
                    val bounds = getTopHudStripBounds()
                    bounds.contains(x, y) || (if (isOpenedFromLeftFlank) x >= width / 2f else x <= width / 2f)
                } else {
                    y < gridTopLimit
                }
                if (isHudArea) {
                    val bounds = getTopHudStripBounds()
                    if (bounds.contains(x, y) && handleTopHudStripTap(x, y)) {
                        activeItem = null
                        invalidate()
                        return true
                    }
                    dismissOverlay()
                } else if (y in gridTopLimit..gridBottomLimit) {
                    val launchTarget = activeItem
                    if (launchTarget != null) {
                        val placed = placedAppsList.find { it.app == launchTarget }
                        val (focalX, focalY) = resolvePlacedItemIconCenter(placed)
                        triggerHyperdriveWarpLaunch(focalX, focalY) {
                            executeLaunch(launchTarget)
                        }
                    } else {
                        dismissOverlay()
                    }
                } else {
                    dismissOverlay()
                }
            }
            activeItem = null; invalidate(); return true
        }
        MotionEvent.ACTION_CANCEL -> {
            isDraggingEdgeCategorySwitcher = false
            stickyPinLongPressRunnable?.let { removeCallbacks(it) }
            stickyPinLongPressRunnable = null
            if (stickyPinLongPressFired) {
                stickyPinLongPressFired = false
                activeItem = null
                invalidate()
                return true
            }
            closePinnedAppMenu()
            closeEditMode()
            isCurrentlyTouched = false; activeItem = null; invalidate(); return true
        }
    }
    return true
}
