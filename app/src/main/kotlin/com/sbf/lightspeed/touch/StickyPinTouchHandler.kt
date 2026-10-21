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
    val gridTopLimit = hF * 0.15f
    val gridBottomLimit = hF * 0.94f
    val gridHeightScope = gridBottomLimit - gridTopLimit
    val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

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
                cancelEditModeDrag()
                if (!isEditModePromptOpen) {
                    val hitRow = getEditModeRowAt(x, y)
                    if (hitRow >= 0) {
                        val runnable = Runnable {
                            startEditModeDrag(hitRow, lastTouchY)
                        }
                        editModeDragRunnable = runnable
                        postDelayed(runnable, 500L)
                    }
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                lastTouchY = y
                if (editModeDragSourceIndex >= 0) {
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
                            triggerHardwareHaptic(10, 60)
                        }
                    }
                    invalidate()
                    return true
                }
                if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) > 16f) {
                    editModeDragRunnable?.let { removeCallbacks(it) }
                    editModeDragRunnable = null
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                editModeDragRunnable?.let { removeCallbacks(it) }
                editModeDragRunnable = null

                if (editModeDragSourceIndex >= 0) {
                    val src = editModeDragSourceIndex
                    val tgt = editModeDragTargetIndex
                    if (src != tgt && src in editModeCategories.indices && tgt in editModeCategories.indices) {
                        val item = editModeCategories.removeAt(src)
                        editModeCategories.add(tgt, item)
                        triggerHardwareHaptic(20, 100)
                    }
                    editModeDragSourceIndex = -1
                    editModeDragTargetIndex = -1
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

                if (editModeDragSourceIndex >= 0) {
                    editModeDragSourceIndex = -1
                    editModeDragTargetIndex = -1
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
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                return true
            }
            MotionEvent.ACTION_UP -> {
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
            } else if (y in gridTopLimit..gridBottomLimit) {
                val runnable = Runnable {
                    stickyPinLongPressFired = true
                    openEditMode()
                    editModeHoldFired = true
                    triggerHardwareHaptic(35, 180)
                    invalidate()
                }
                stickyPinLongPressRunnable = runnable
                postDelayed(runnable, 500L)
            } else if (y < gridTopLimit && !isSettingsButtonHit(x, y)) {
                val runnable = Runnable {
                    stickyPinLongPressFired = true
                    openEditMode(EditModeTab.HUD)
                    editModeHoldFired = true
                    triggerHardwareHaptic(35, 180)
                    invalidate()
                }
                stickyPinLongPressRunnable = runnable
                postDelayed(runnable, 500L)
            }

            invalidate(); return true
        }
        MotionEvent.ACTION_MOVE -> {
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
            if (maxScroll > 0f) {
                viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                invalidate()
            }
            return true
        }
        MotionEvent.ACTION_UP -> {
            stickyPinLongPressRunnable?.let { removeCallbacks(it) }
            stickyPinLongPressRunnable = null

            if (stickyPinLongPressFired) {
                stickyPinLongPressFired = false
                activeItem = null
                invalidate()
                return true
            }

            if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                if (isSettingsButtonHit(x, y)) { openEditMode(); triggerHardwareHaptic(35, 180); activeItem = null; invalidate(); return true }
                if (y in gridTopLimit..gridBottomLimit) activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                else dismissOverlay()
            }
            activeItem = null; invalidate(); return true
        }
        MotionEvent.ACTION_CANCEL -> {
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
