package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats

enum class EditModeTab {
    PAGE,
    HUD
}

internal fun LightspeedCruiseOverlay.closeEditMode() {
    isEditModeOpen = false
    editModeTab = EditModeTab.PAGE
    isEditModePromptOpen = false
    editModeSavedCategories = emptyList()
    stickyPinLongPressFired = false
    editModeHoldFired = false
    cancelEditModeDrag()
}

internal fun LightspeedCruiseOverlay.openEditMode(tab: EditModeTab = EditModeTab.PAGE) {
    closePinnedAppMenu()
    editModeTab = tab
    editModeCategories.clear()
    val saved = dataBridge.getOrderedCategories()
    editModeCategories.addAll(saved)
    editModeSavedCategories = saved.toList()
    isEditModePromptOpen = false
    isEditModeOpen = true
    cancelEditModeDrag()
}

internal fun LightspeedCruiseOverlay.getUnsavedPromptBounds(): RectF {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val promptW = (280f * dp).coerceAtMost(screenW - 24f * dp)
    val promptH = 100f * dp
    val left = (screenW - promptW) / 2f
    val top = (screenH - promptH) / 2f
    return RectF(left, top, left + promptW, top + promptH)
}

internal fun LightspeedCruiseOverlay.getEditModePanelBounds(): RectF {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val margin = 12f * dp
    val numCategories = editModeCategories.size
    val totalRows = numCategories + 2
    val baseRowH = 34f * dp
    val nominalH = totalRows * baseRowH
    val maxAvailableH = screenH - (margin * 2f)
    val actualRowH = if (nominalH > maxAvailableH) maxAvailableH / totalRows else baseRowH
    val panelH = actualRowH * totalRows
    val panelW = (240f * dp).coerceAtMost(screenW - (margin * 2f))
    val left = margin
    val right = left + panelW
    val top = ((screenH - panelH) / 2f).coerceAtLeast(margin)
    val bottom = top + panelH
    return RectF(left, top, right, bottom)
}

internal fun LightspeedCruiseOverlay.saveAndApplyEditModeOrder() {
    val keys = editModeCategories.map { it.id }
    LightspeedPreferences.setCategoryOrder(context, keys)
    editModeSavedCategories = editModeCategories.toList()
    val currentCatId = cachedCategories.getOrNull(activeCatIndex)?.id
    cachedCategories = dataBridge.getLiveCategories()
    if (currentCatId != null) {
        val newIndex = cachedCategories.indexOfFirst { it.id == currentCatId }
        if (newIndex >= 0) {
            activeCatIndex = newIndex
        } else if (activeCatIndex !in cachedCategories.indices && cachedCategories.isNotEmpty()) {
            activeCatIndex = 0
        }
    } else if (activeCatIndex !in cachedCategories.indices && cachedCategories.isNotEmpty()) {
        activeCatIndex = 0
    }
    categoryAppsCache.clear()
    categoryGridCache.clear()
    categoryHeightCache.clear()
    lastLoadedCategoryId = null
    loadActiveCategoryGrid()
    invalidate()
}

internal fun LightspeedCruiseOverlay.handleEditModeTap(x: Float, y: Float) {
    if (!isEditModeOpen) return

    val dp = resources.displayMetrics.density

    if (isEditModePromptOpen) {
        val promptBounds = getUnsavedPromptBounds()
        if (promptBounds.contains(x, y)) {
            val btnRowTop = promptBounds.bottom - 40f * dp
            if (y >= btnRowTop) {
                val btnW = promptBounds.width() / 3f
                val relX = x - promptBounds.left
                when {
                    relX < btnW -> {
                        // "Save"
                        triggerHardwareHaptic(20, 80)
                        saveAndApplyEditModeOrder()
                        closeEditMode()
                        invalidate()
                    }
                    relX < btnW * 2f -> {
                        // "Discard"
                        triggerHardwareHaptic(20, 80)
                        closeEditMode()
                        invalidate()
                    }
                    else -> {
                        // "Keep editing"
                        triggerHardwareHaptic(20, 80)
                        isEditModePromptOpen = false
                        invalidate()
                    }
                }
            }
        }
        return
    }

    val bounds = getEditModePanelBounds()
    if (!bounds.contains(x, y)) {
        if (editModeCategories != editModeSavedCategories) {
            triggerHardwareHaptic(20, 80)
            isEditModePromptOpen = true
            invalidate()
        } else {
            closeEditMode()
            triggerHardwareHaptic(20, 80)
            invalidate()
        }
        return
    }

    val numCategories = editModeCategories.size
    val totalRows = numCategories + 2
    val actualRowH = bounds.height() / totalRows
    val tabRowBottom = bounds.top + actualRowH
    val btnRowTop = bounds.bottom - actualRowH

    if (y < tabRowBottom) {
        val midX = bounds.left + (bounds.width() / 2f)
        val selectedTab = if (x < midX) EditModeTab.PAGE else EditModeTab.HUD
        if (editModeTab != selectedTab) {
            triggerHardwareHaptic(20, 80)
            editModeTab = selectedTab
            invalidate()
        }
        return
    }

    if (y >= btnRowTop) {
        val btnW = bounds.width() / 4f
        val relX = x - bounds.left
        if (editModeTab == EditModeTab.PAGE) {
            when {
                relX < btnW -> {
                    // "A-Z"
                    triggerHardwareHaptic(25, 120)
                    editModeCategories.sortBy { it.label.lowercase() }
                    invalidate()
                }
                relX < btnW * 2f -> {
                    // "Most used"
                    handleMostUsedSort()
                }
                relX < btnW * 3f -> {
                    // "Default"
                    triggerHardwareHaptic(25, 120)
                    val defaultMap = LightspeedDataBridge.DRAWER_CATEGORIES.associateBy { it.id }
                    val defaultList = LightspeedDataBridge.DRAWER_CATEGORIES.mapNotNull { defaultMap[it.id] }
                    editModeCategories.clear()
                    editModeCategories.addAll(defaultList)
                    invalidate()
                }
                else -> {
                    // "Save"
                    triggerHardwareHaptic(20, 80)
                    saveAndApplyEditModeOrder()
                    closeEditMode()
                    invalidate()
                }
            }
        } else {
            if (relX >= btnW * 3f) {
                // "Save"
                triggerHardwareHaptic(20, 80)
                saveAndApplyEditModeOrder()
                closeEditMode()
                invalidate()
            }
        }
        return
    }

    if (editModeTab == EditModeTab.PAGE) {
        val row = ((y - tabRowBottom) / actualRowH).toInt().coerceIn(0, numCategories - 1)
        val upLeft = bounds.right - 56f * dp
        val upRight = bounds.right - 28f * dp
        val downRight = bounds.right

        if (x in upLeft..upRight) {
            if (row > 0) {
                triggerHardwareHaptic(20, 100)
                val item = editModeCategories.removeAt(row)
                editModeCategories.add(row - 1, item)
                invalidate()
            }
        } else if (x in upRight..downRight) {
            if (row < numCategories - 1) {
                triggerHardwareHaptic(20, 100)
                val item = editModeCategories.removeAt(row)
                editModeCategories.add(row + 1, item)
                invalidate()
            }
        }
    }
}

private fun LightspeedCruiseOverlay.handleMostUsedSort() {
    if (!LightspeedUsageStats.hasUsageAccess(context)) {
        triggerHardwareHaptic(20, 80)
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
        dismissOverlay()
        return
    }

    triggerHardwareHaptic(25, 120)
    Thread {
        val usageMap = LightspeedUsageStats.usageMillisByPackage(context, 30)
        val defaultIndexMap = LightspeedDataBridge.DRAWER_CATEGORIES.withIndex().associate { it.value.id to it.index }

        val currentCategories = synchronized(editModeCategories) { editModeCategories.toList() }
        val categoryUsage = currentCategories.associate { cat ->
            val totalUsage = dataBridge.getAppsForCategory(cat.id).sumOf { app ->
                usageMap[app.packageName] ?: 0L
            }
            cat.id to totalUsage
        }

        val withUsage = currentCategories.filter { (categoryUsage[it.id] ?: 0L) > 0L }
            .sortedWith(compareByDescending<LightspeedDataBridge.CategoryNode> { categoryUsage[it.id] ?: 0L }
                .thenBy { defaultIndexMap[it.id] ?: Int.MAX_VALUE })

        val zeroUsage = currentCategories.filter { (categoryUsage[it.id] ?: 0L) == 0L }
            .sortedBy { defaultIndexMap[it.id] ?: Int.MAX_VALUE }

        val sortedCategories = withUsage + zeroUsage

        uiHandler.post {
            editModeCategories.clear()
            editModeCategories.addAll(sortedCategories)
            invalidate()
        }
    }.start()
}

internal fun LightspeedCruiseOverlay.getEditModeRowAt(x: Float, y: Float): Int {
    if (!isEditModeOpen || isEditModePromptOpen) return -1
    if (editModeTab != EditModeTab.PAGE) return -1
    val numCategories = editModeCategories.size
    if (numCategories <= 1) return -1
    val bounds = getEditModePanelBounds()
    val dp = resources.displayMetrics.density
    val arrowsLeft = bounds.right - 56f * dp
    val totalRows = numCategories + 2
    val actualRowH = bounds.height() / totalRows
    val tabRowBottom = bounds.top + actualRowH
    val btnRowTop = bounds.bottom - actualRowH
    if (x >= bounds.left && x < arrowsLeft && y >= tabRowBottom && y < btnRowTop) {
        val row = ((y - tabRowBottom) / actualRowH).toInt()
        if (row in 0 until numCategories) {
            return row
        }
    }
    return -1
}

internal fun LightspeedCruiseOverlay.startEditModeDrag(row: Int, y: Float) {
    if (!isEditModeOpen || isEditModePromptOpen) return
    editModeDragSourceIndex = row
    editModeDragTargetIndex = row
    editModeDragY = y
    triggerHardwareHaptic(35, 180)
    invalidate()
}

internal fun LightspeedCruiseOverlay.cancelEditModeDrag() {
    editModeDragRunnable?.let { removeCallbacks(it) }
    editModeDragRunnable = null
    editModeDragSourceIndex = -1
    editModeDragTargetIndex = -1
}

internal fun LightspeedCruiseOverlay.drawEditModePanel(canvas: Canvas, paint: Paint) {
    if (!isEditModeOpen || editModeCategories.isEmpty()) return
    val dp = resources.displayMetrics.density
    val bounds = getEditModePanelBounds()
    val numCategories = editModeCategories.size
    val totalRows = numCategories + 2
    val actualRowH = bounds.height() / totalRows
    // Theme-driven optical styling
    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (snapshot.cornerRadiusDp * 0.65f).coerceIn(10f, 24f) * dp
    val tintColor = snapshot.tintArgb.toInt()
    val borderColor = snapshot.borderColorArgb.toInt()

    // 1-4. Full 12-theme optical flyout surface (shadow, dense substrate, light deflection, specular cap & border)
    drawDeckThemedFlyoutSurface(canvas, bounds, snapshot, cornerRadius, dp, paint)

    // Tab strip at top
    val tabRowBottom = bounds.top + actualRowH
    val tabRowCenterY = bounds.top + (actualRowH / 2f)
    val tabW = bounds.width() / 2f

    // Divider below tab strip
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(
        50,
        Color.red(borderColor),
        Color.green(borderColor),
        Color.blue(borderColor)
    )
    canvas.drawLine(bounds.left, tabRowBottom, bounds.right, tabRowBottom, paint)

    // Vertical divider between tabs
    canvas.drawLine(bounds.left + tabW, bounds.top + 4f * dp, bounds.left + tabW, tabRowBottom - 4f * dp, paint)

    // Tab labels
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = (actualRowH * 0.36f).coerceIn(10f * dp, 12.5f * dp)
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    val tabFontMetrics = paint.fontMetrics
    val tabTextYOffset = (tabFontMetrics.descent + tabFontMetrics.ascent) / 2f

    val m3Accent = tintColor
    val unselectedTabColor = Color.argb(140, Color.red(m3Accent), Color.green(m3Accent), Color.blue(m3Accent))

    // "Page" tab
    paint.color = if (editModeTab == EditModeTab.PAGE) m3Accent else unselectedTabColor
    canvas.drawText("Page", bounds.left + (tabW * 0.5f), tabRowCenterY - tabTextYOffset, paint)

    // "HUD" tab
    paint.color = if (editModeTab == EditModeTab.HUD) m3Accent else unselectedTabColor
    canvas.drawText("HUD", bounds.left + (tabW * 1.5f), tabRowCenterY - tabTextYOffset, paint)

    // Active indicator underline
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f * dp
    paint.color = m3Accent
    if (editModeTab == EditModeTab.PAGE) {
        canvas.drawLine(bounds.left + 16f * dp, tabRowBottom - 1.5f * dp, bounds.left + tabW - 16f * dp, tabRowBottom - 1.5f * dp, paint)
    } else {
        canvas.drawLine(bounds.left + tabW + 16f * dp, tabRowBottom - 1.5f * dp, bounds.right - 16f * dp, tabRowBottom - 1.5f * dp, paint)
    }

    if (editModeTab == EditModeTab.PAGE) {
        // Category rows
        val textSize = (actualRowH * 0.38f).coerceIn(10f * dp, 12.5f * dp)
        paint.style = Paint.Style.FILL
        paint.textSize = textSize
        paint.textAlign = Paint.Align.LEFT
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val fontMetrics = paint.fontMetrics
        val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
        val textX = bounds.left + 12f * dp
        val arrowSize = (actualRowH * 0.16f).coerceIn(3.5f * dp, 5f * dp)
        val cxUp = bounds.right - 42f * dp
        val cxDown = bounds.right - 14f * dp

        val isDragging = editModeDragSourceIndex in 0 until numCategories
        val dragTarget = editModeDragTargetIndex.coerceIn(0, numCategories - 1)

        // Slot dividers
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.8f * dp
        paint.color = Color.argb(40, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        for (s in 0 until numCategories - 1) {
            val divY = tabRowBottom + ((s + 1) * actualRowH)
            canvas.drawLine(bounds.left + 10f * dp, divY, bounds.right - 10f * dp, divY, paint)
        }

        // Drop spot indicator when dragging
        if (isDragging) {
            val dropTop = tabRowBottom + (dragTarget * actualRowH)
            val dropRect = RectF(bounds.left + 6f * dp, dropTop + 2f * dp, bounds.right - 6f * dp, dropTop + actualRowH - 2f * dp)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(40, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            canvas.drawRoundRect(dropRect, 6f * dp, 6f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = Color.argb(120, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
            canvas.drawRoundRect(dropRect, 6f * dp, 6f * dp, paint)
        }

        // Category rows (stationary / shifted)
        for (i in 0 until numCategories) {
            if (isDragging && i == editModeDragSourceIndex) continue
            val cat = editModeCategories[i]
            val slot = when {
                !isDragging -> i
                dragTarget > editModeDragSourceIndex -> if (i in (editModeDragSourceIndex + 1)..dragTarget) i - 1 else i
                dragTarget < editModeDragSourceIndex -> if (i in dragTarget until editModeDragSourceIndex) i + 1 else i
                else -> i
            }
            val rowTop = tabRowBottom + (slot * actualRowH)
            val rowCenterY = rowTop + (actualRowH / 2f)

            // Text
            paint.style = Paint.Style.FILL
            paint.textSize = textSize
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.color = Color.argb(245, 240, 245, 255)
            canvas.drawText(cat.label, textX, rowCenterY - textYOffset, paint)

            // Up arrow
            val upColor = if (slot > 0) Color.argb(220, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(45, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            paint.color = upColor
            val upPath = Path().apply {
                moveTo(cxUp, rowCenterY - arrowSize)
                lineTo(cxUp + arrowSize, rowCenterY + arrowSize)
                lineTo(cxUp - arrowSize, rowCenterY + arrowSize)
                close()
            }
            canvas.drawPath(upPath, paint)

            // Down arrow
            val downColor = if (slot < numCategories - 1) Color.argb(220, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(45, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
            paint.color = downColor
            val downPath = Path().apply {
                moveTo(cxDown - arrowSize, rowCenterY - arrowSize)
                lineTo(cxDown + arrowSize, rowCenterY - arrowSize)
                lineTo(cxDown, rowCenterY + arrowSize)
                close()
            }
            canvas.drawPath(downPath, paint)
        }

        // Dragged row on top
        if (isDragging) {
            val draggedCat = editModeCategories[editModeDragSourceIndex]
            val halfH = actualRowH / 2f
            val minY = tabRowBottom + halfH
            val maxY = tabRowBottom + (numCategories * actualRowH) - halfH
            val clampedCenterY = editModeDragY.coerceIn(minY, maxY)
            val rowTop = clampedCenterY - halfH
            val rowBottom = clampedCenterY + halfH

            // Dragged card background & elevation highlight
            val panelColor = snapshot.panelColorArgb.toInt()
            val dragRect = RectF(bounds.left + 4f * dp, rowTop + 1f * dp, bounds.right - 4f * dp, rowBottom - 1f * dp)
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(235, Color.red(panelColor), Color.green(panelColor), Color.blue(panelColor))
            canvas.drawRoundRect(dragRect, 8f * dp, 8f * dp, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * dp
            paint.color = Color.argb(210, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
            canvas.drawRoundRect(dragRect, 8f * dp, 8f * dp, paint)

            // Text
            paint.style = Paint.Style.FILL
            paint.textSize = textSize
            paint.textAlign = Paint.Align.LEFT
            paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            paint.color = Color.argb(255, 255, 255, 255)
            canvas.drawText(draggedCat.label, textX, clampedCenterY - textYOffset, paint)

            // Up arrow
            paint.color = Color.argb(230, 160, 225, 255)
            val upPath = Path().apply {
                moveTo(cxUp, clampedCenterY - arrowSize)
                lineTo(cxUp + arrowSize, clampedCenterY + arrowSize)
                lineTo(cxUp - arrowSize, clampedCenterY + arrowSize)
                close()
            }
            canvas.drawPath(upPath, paint)

            // Down arrow
            val downPath = Path().apply {
                moveTo(cxDown - arrowSize, clampedCenterY - arrowSize)
                lineTo(cxDown + arrowSize, clampedCenterY - arrowSize)
                lineTo(cxDown, clampedCenterY + arrowSize)
                close()
            }
            canvas.drawPath(downPath, paint)
        }
    } else {
        // Placeholder text on HUD tab
        val btnRowTop = bounds.bottom - actualRowH
        val contentCenterY = (tabRowBottom + btnRowTop) / 2f
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * dp
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = Color.argb(160, 200, 220, 255)
        val hudFontMetrics = paint.fontMetrics
        val hudTextYOffset = (hudFontMetrics.descent + hudFontMetrics.ascent) / 2f
        canvas.drawText("HUD options coming soon", bounds.centerX(), contentCenterY - hudTextYOffset, paint)
    }

    // Button row at bottom
    val btnRowTop = bounds.bottom - actualRowH
    val btnRowCenterY = btnRowTop + (actualRowH / 2f)
    val btnW = bounds.width() / 4f

    // Divider above buttons
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(50, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
    canvas.drawLine(bounds.left, btnRowTop, bounds.right, btnRowTop, paint)

    val btnTextSize = (actualRowH * 0.32f).coerceIn(8.5f * dp, 11f * dp)
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = btnTextSize
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD

    if (editModeTab == EditModeTab.PAGE) {
        // Vertical dividers between buttons
        paint.color = Color.argb(40, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        canvas.drawLine(bounds.left + btnW, btnRowTop + 4f * dp, bounds.left + btnW, bounds.bottom - 4f * dp, paint)
        canvas.drawLine(bounds.left + (btnW * 2f), btnRowTop + 4f * dp, bounds.left + (btnW * 2f), bounds.bottom - 4f * dp, paint)
        canvas.drawLine(bounds.left + (btnW * 3f), btnRowTop + 4f * dp, bounds.left + (btnW * 3f), bounds.bottom - 4f * dp, paint)

        var labelSize = btnTextSize
        while (paint.measureText("Most used") > btnW - 6f * dp && labelSize > 7.5f * dp) {
            labelSize -= 0.5f * dp
            paint.textSize = labelSize
        }

        val btnFontMetrics = paint.fontMetrics
        val btnTextYOffset = (btnFontMetrics.descent + btnFontMetrics.ascent) / 2f

        // "A-Z"
        paint.color = Color.argb(225, 235, 240, 255)
        canvas.drawText("A-Z", bounds.left + (btnW * 0.5f), btnRowCenterY - btnTextYOffset, paint)

        // "Most used"
        paint.color = Color.argb(225, 235, 240, 255)
        canvas.drawText("Most used", bounds.left + (btnW * 1.5f), btnRowCenterY - btnTextYOffset, paint)

        // "Default"
        paint.color = Color.argb(225, 235, 240, 255)
        canvas.drawText("Default", bounds.left + (btnW * 2.5f), btnRowCenterY - btnTextYOffset, paint)

        // "Save"
        paint.color = tintColor
        canvas.drawText("Save", bounds.left + (btnW * 3.5f), btnRowCenterY - btnTextYOffset, paint)
    } else {
        // Vertical divider before Save
        paint.color = Color.argb(40, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        canvas.drawLine(bounds.left + (btnW * 3f), btnRowTop + 4f * dp, bounds.left + (btnW * 3f), bounds.bottom - 4f * dp, paint)

        val btnFontMetrics = paint.fontMetrics
        val btnTextYOffset = (btnFontMetrics.descent + btnFontMetrics.ascent) / 2f

        // "Save"
        paint.color = tintColor
        canvas.drawText("Save", bounds.left + (btnW * 3.5f), btnRowCenterY - btnTextYOffset, paint)
    }

    if (isEditModePromptOpen) {
        drawUnsavedPrompt(canvas, paint)
    }
}

private fun LightspeedCruiseOverlay.drawUnsavedPrompt(canvas: Canvas, paint: Paint) {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()

    // Dimming backdrop
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(120, 0, 0, 0)
    canvas.drawRect(0f, 0f, screenW, screenH, paint)

    val promptBounds = getUnsavedPromptBounds()

    // Theme-driven optical styling
    val promptSnapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (promptSnapshot.cornerRadiusDp * 0.65f).coerceIn(12f, 24f) * dp
    val borderColor = promptSnapshot.borderColorArgb.toInt()

    // 1-4. Full 12-theme optical flyout surface (shadow, dense substrate, light deflection, specular cap & border)
    drawDeckThemedFlyoutSurface(canvas, promptBounds, promptSnapshot, cornerRadius, dp, paint)

    // Header area
    val btnRowH = 40f * dp
    val headerH = promptBounds.height() - btnRowH

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textSize = 13f * dp
    paint.color = Color.argb(245, 240, 245, 255)
    val fontMetrics = paint.fontMetrics
    val textYOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
    val titleY = promptBounds.top + (headerH * 0.4f) - textYOffset
    canvas.drawText("Unsaved Changes", promptBounds.centerX(), titleY, paint)

    paint.textSize = 10.5f * dp
    paint.typeface = android.graphics.Typeface.DEFAULT
    paint.color = Color.argb(180, 200, 220, 255)
    val subFontMetrics = paint.fontMetrics
    val subTextYOffset = (subFontMetrics.descent + subFontMetrics.ascent) / 2f
    val subY = promptBounds.top + (headerH * 0.75f) - subTextYOffset
    canvas.drawText("Save modified category order?", promptBounds.centerX(), subY, paint)

    // Divider above buttons
    val btnRowTop = promptBounds.bottom - btnRowH
    val btnRowCenterY = btnRowTop + (btnRowH / 2f)
    val btnW = promptBounds.width() / 3f

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(60, 200, 220, 255)
    canvas.drawLine(promptBounds.left, btnRowTop, promptBounds.right, btnRowTop, paint)

    // Vertical dividers between buttons
    canvas.drawLine(promptBounds.left + btnW, btnRowTop + 4f * dp, promptBounds.left + btnW, promptBounds.bottom - 4f * dp, paint)
    canvas.drawLine(promptBounds.left + (btnW * 2f), btnRowTop + 4f * dp, promptBounds.left + (btnW * 2f), promptBounds.bottom - 4f * dp, paint)

    // Button labels
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    var btnTextSize = 10.5f * dp
    paint.textSize = btnTextSize
    while (paint.measureText("Keep editing") > btnW - 6f * dp && btnTextSize > 7.5f * dp) {
        btnTextSize -= 0.5f * dp
        paint.textSize = btnTextSize
    }

    val bfm = paint.fontMetrics
    val btnYOffset = (bfm.descent + bfm.ascent) / 2f

    // "Save"
    paint.color = Color.argb(255, 120, 210, 255)
    canvas.drawText("Save", promptBounds.left + (btnW * 0.5f), btnRowCenterY - btnYOffset, paint)

    // "Discard"
    paint.color = Color.argb(240, 255, 130, 130)
    canvas.drawText("Discard", promptBounds.left + (btnW * 1.5f), btnRowCenterY - btnYOffset, paint)

    // "Keep editing"
    paint.color = Color.argb(230, 200, 220, 255)
    canvas.drawText("Keep editing", promptBounds.left + (btnW * 2.5f), btnRowCenterY - btnYOffset, paint)
}
