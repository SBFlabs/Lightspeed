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
import com.sbf.lightspeed.system.defaultPrefs

private val editModeArrowPath = Path()

/** Category Cruise name-spacing presets (percent of the default row height). */
internal val CATEGORY_SPACING_OPTIONS = listOf(70 to "TIGHT", 100 to "NORMAL", 130 to "WIDE", 160 to "WIDER")

/** Live multiplier applied to the Category Cruise row height (1.0 = default spacing). */
internal fun LightspeedCruiseOverlay.categorySpacingScale(): Float =
    context.defaultPrefs().getInt(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_SPACING_PERCENT,
        LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_SPACING_PERCENT
    ).coerceIn(40, 250) / 100f

/** Category Cruise slider feel presets (stored values match the gear-ring physics tokens). */
internal val CATEGORY_PHYSICS_OPTIONS = listOf("magnetic" to "SNAPPY", "fluid" to "FLUID", "heavy" to "HEAVY")

/**
 * Reshapes the finger-driven 0..1 Category Cruise progress by physics profile.
 * Slope near the centre is 1.0 (snappy), 1.45 (fluid) or 0.72 (heavy); both ends stay reachable.
 */
internal fun LightspeedCruiseOverlay.shapeCategoryProgress(progress: Float): Float {
    val profile = context.defaultPrefs().getString(LightspeedPreferences.KEY_CATEGORY_CRUISE_PHYSICS_PROFILE, "magnetic")
    val t = (progress * 2f - 1f).coerceIn(-1f, 1f)
    val shaped = when (profile) {
        "fluid" -> t * (1.45f - 0.45f * t * t)
        "heavy" -> t * (0.72f + 0.28f * t * t)
        else -> t
    }
    return ((shaped + 1f) / 2f).coerceIn(0f, 1f)
}

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
        drawEditModeCategoryRows(canvas, paint, bounds, snapshot, actualRowH, tabRowBottom, numCategories, dp)
    } else {
        // HUD Options tab
        val dp = resources.displayMetrics.density
        val startY = tabRowBottom + (16f * dp)
        val margin = 10f * dp
        val pillSpacing = 4f * dp

        // Settings gear alignment section
        val gearSectionY = startY
        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f * dp
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.argb(220, 200, 220, 255)
        canvas.drawText("SETTINGS GEAR POSITION", bounds.left + 12f * dp, gearSectionY, paint)

        val isGearLeft = context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_PINNED_GEAR_LEFT, false)
        val gearPillOptions = listOf(false to "TOP RIGHT", true to "TOP LEFT")
        val gearPillH = 26f * dp
        val gearPillY = gearSectionY + (10f * dp)
        val gearTotalPillW = bounds.width() - (margin * 2f)
        val gearPillW = (gearTotalPillW - pillSpacing) / 2f

        for (i in gearPillOptions.indices) {
            val (optLeft, optLabel) = gearPillOptions[i]
            val isSelected = (isGearLeft == optLeft)
            val pLeft = bounds.left + margin + (i * (gearPillW + pillSpacing))
            val pRect = RectF(pLeft, gearPillY, pLeft + gearPillW, gearPillY + gearPillH)

            // Pill background
            paint.style = Paint.Style.FILL
            paint.color = if (isSelected) Color.argb(180, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(35, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)

            // Pill border
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = if (isSelected) tintColor else Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)

            // Pill text
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 8.5f * dp
            paint.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            paint.color = if (isSelected) Color.WHITE else Color.argb(180, 220, 230, 255)
            val pfm = paint.fontMetrics
            val pTextY = pRect.centerY() - ((pfm.descent + pfm.ascent) / 2f)
            canvas.drawText(optLabel, pRect.centerX(), pTextY, paint)
        }

        // Subtitle explanation for Settings Gear Position
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 9f * dp
        paint.typeface = android.graphics.Typeface.DEFAULT
        paint.color = Color.argb(140, 180, 200, 240)
        val gearNoteY = gearPillY + gearPillH + (18f * dp)
        val gearNoteText = if (isGearLeft) "Pinned-page settings gear anchored at top-left corner." else "Pinned-page settings gear anchored at top-right corner."
        canvas.drawText(gearNoteText, bounds.left + 12f * dp, gearNoteY, paint)

        // Category Cruise name spacing section
        val spacingSectionY = gearNoteY + (22f * dp)
        paint.textSize = 10f * dp
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.argb(220, 200, 220, 255)
        canvas.drawText("CATEGORY NAME SPACING", bounds.left + 12f * dp, spacingSectionY, paint)

        val currentSpacing = context.defaultPrefs().getInt(
            LightspeedPreferences.KEY_CATEGORY_CRUISE_SPACING_PERCENT,
            LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_SPACING_PERCENT
        )
        val spacingPillY = spacingSectionY + (10f * dp)
        val spacingCount = CATEGORY_SPACING_OPTIONS.size
        val spacingPillW = (bounds.width() - (margin * 2f) - pillSpacing * (spacingCount - 1)) / spacingCount
        for (i in 0 until spacingCount) {
            val (value, label) = CATEGORY_SPACING_OPTIONS[i]
            val isSelected = (currentSpacing == value)
            val pLeft = bounds.left + margin + (i * (spacingPillW + pillSpacing))
            val pRect = RectF(pLeft, spacingPillY, pLeft + spacingPillW, spacingPillY + gearPillH)
            paint.style = Paint.Style.FILL
            paint.color = if (isSelected) Color.argb(180, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(35, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = if (isSelected) tintColor else Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 7.5f * dp
            paint.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            paint.color = if (isSelected) Color.WHITE else Color.argb(180, 220, 230, 255)
            val pfm = paint.fontMetrics
            canvas.drawText(label, pRect.centerX(), pRect.centerY() - ((pfm.descent + pfm.ascent) / 2f), paint)
        }

        // Category Cruise slider feel section
        val feelSectionY = spacingPillY + gearPillH + (22f * dp)
        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10f * dp
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.color = Color.argb(220, 200, 220, 255)
        canvas.drawText("CATEGORY SLIDER FEEL", bounds.left + 12f * dp, feelSectionY, paint)

        val currentFeel = context.defaultPrefs().getString(LightspeedPreferences.KEY_CATEGORY_CRUISE_PHYSICS_PROFILE, "magnetic")
        val feelPillY = feelSectionY + (10f * dp)
        val feelCount = CATEGORY_PHYSICS_OPTIONS.size
        val feelPillW = (bounds.width() - (margin * 2f) - pillSpacing * (feelCount - 1)) / feelCount
        for (i in 0 until feelCount) {
            val (value, label) = CATEGORY_PHYSICS_OPTIONS[i]
            val isSelected = (currentFeel == value)
            val pLeft = bounds.left + margin + (i * (feelPillW + pillSpacing))
            val pRect = RectF(pLeft, feelPillY, pLeft + feelPillW, feelPillY + gearPillH)
            paint.style = Paint.Style.FILL
            paint.color = if (isSelected) Color.argb(180, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor)) else Color.argb(35, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f * dp
            paint.color = if (isSelected) tintColor else Color.argb(60, 255, 255, 255)
            canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 8f * dp
            paint.typeface = if (isSelected) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
            paint.color = if (isSelected) Color.WHITE else Color.argb(180, 220, 230, 255)
            val pfm = paint.fontMetrics
            canvas.drawText(label, pRect.centerX(), pRect.centerY() - ((pfm.descent + pfm.ascent) / 2f), paint)
        }
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
