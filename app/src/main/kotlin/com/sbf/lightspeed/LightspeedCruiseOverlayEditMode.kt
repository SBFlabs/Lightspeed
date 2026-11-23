package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.provider.Settings
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedUsageStats
import com.sbf.lightspeed.system.defaultPrefs

private val editModeArrowPath = Path()

/**
 * Ensures optical readability on dark / low-luminance themes (like Dark Blur).
 * If the luminance of the color is below 0.5f, returns bright cyan/accent (0xFF38BDF8).
 */
internal fun ensureHighContrastColor(colorArgb: Int): Int {
    val r = Color.red(colorArgb) / 255f
    val g = Color.green(colorArgb) / 255f
    val b = Color.blue(colorArgb) / 255f
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b
    return if (luminance < 0.5f) {
        0xFF38BDF8.toInt()
    } else {
        colorArgb
    }
}

/** Category Cruise name-spacing presets (percent of the default row height). */
internal val CATEGORY_SPACING_OPTIONS = listOf(70 to "TIGHT", 100 to "NORMAL", 130 to "WIDE", 160 to "WIDER")

/**
 * Category Cruise color engine modes. Independent from every HUD / Cybernetic / gear-set color engine.
 * Drives: focused-app spotlight + ring, focused-category nebula, the ✦ star beacon and the edit panel accent.
 * The central pill (Core Astrogation) is intentionally excluded.
 */
internal val CATEGORY_COLOR_ENGINE_OPTIONS = listOf(
    LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12 to "12-THEME",
    LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC to "MATERIAL 3",
    LightspeedPreferences.COCKPIT_COLOR_MODE_HYBRID_FUSION to "HYBRID"
)

internal fun LightspeedCruiseOverlay.categoryColorMode(): String =
    context.defaultPrefs().getString(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_COLOR_MODE,
        LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC
    ) ?: LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC

/** Accent ARGB for Category Cruise highlights, or null in 12-Theme mode (keep legacy per-element theme colors). */
internal fun LightspeedCruiseOverlay.categoryAccentOverride(): Int? {
    val mode = categoryColorMode()
    if (mode == LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12) return null
    return com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
        .resolveCanvasPalette(context, mode).primaryAccentArgb
}

/**
 * Ensures the focused Category Cruise icon + label always share one unmistakable non-white chromatic accent
 * across 12-Theme, Hybrid, and Material 3 modes (so focused categories stand out sharply from white neighbors).
 */
internal fun LightspeedCruiseOverlay.categoryFocusAccentColor(fallbackAccent: Int): Int {
    val rawAccent = categoryAccentOverride() ?: fallbackAccent
    return com.sbf.lightspeed.settings.ensureChromaticAccentColor(rawAccent)
}

/** Border ARGB for Category Cruise panels & dividers, or null if using 12-theme borders. */
internal fun LightspeedCruiseOverlay.categoryBorderOverride(): Int? {
    val mode = categoryColorMode()
    if (mode != LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC) return null
    return com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
        .resolveCanvasPalette(context, mode).borderArgb
}

/** Live multiplier applied to the Category Cruise row height (1.0 = default spacing). */
internal fun LightspeedCruiseOverlay.categorySpacingScale(): Float =
    context.defaultPrefs().getInt(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_SPACING_PERCENT,
        LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_SPACING_PERCENT
    ).coerceIn(40, 250) / 100f

/** Category Cruise slider feel presets (stored values match the gear-ring physics tokens). */
internal val CATEGORY_PHYSICS_OPTIONS = listOf("magnetic" to "SNAPPY", "fluid" to "FLUID", "heavy" to "HEAVY")

/** Category Cruise HUD widget corner roundness presets (in dp). */
internal val CATEGORY_HUD_RADIUS_OPTIONS = listOf(
    0 to "SHARP",
    8 to "SUBTLE",
    16 to "ROUNDED",
    26 to "PILL"
)

internal val CATEGORY_LAUNCH_SPARK_OPTIONS = listOf(
    "photon_burst" to "PHOTON",
    "nova_bloom" to "HALO",
    "quantum_cross" to "FLARE",
    "ion_embers" to "EMBERS",
    "clean_fade" to "MINIMAL"
)

internal fun LightspeedCruiseOverlay.categoryLaunchSparkStyle(): String =
    context.defaultPrefs().getString(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_LAUNCH_SPARK_STYLE,
        LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_LAUNCH_SPARK_STYLE
    ) ?: LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_LAUNCH_SPARK_STYLE

internal fun LightspeedCruiseOverlay.categoryHudCornerRadiusPx(): Float {
    val dp = resources.displayMetrics.density
    val radiusDp = context.defaultPrefs().getInt(
        LightspeedPreferences.KEY_CATEGORY_HUD_CORNER_RADIUS_DP,
        LightspeedPreferences.DEFAULT_CATEGORY_HUD_CORNER_RADIUS_DP
    )
    return radiusDp * dp
}

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
    sparkPreviewCancelRunnable?.let { uiHandler.removeCallbacks(it) }
    sparkPreviewCancelRunnable = null
    deepSpaceRenderer.reactorRenderer.cancelWarp()
    isEditModeOpen = false
    editModeTab = EditModeTab.PAGE
    isEditModePromptOpen = false
    isCategoryIconPickerOpen = false
    categoryIconPickerTarget = null
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

internal fun LightspeedCruiseOverlay.getEditModeRowCount(): Int =
    if (editModeTab == EditModeTab.HUD) 10 else (editModeCategories.size + 2)

internal fun LightspeedCruiseOverlay.getEditModePanelBounds(): RectF {
    val dp = resources.displayMetrics.density
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val margin = 12f * dp
    val totalRows = getEditModeRowCount()
    val baseRowH = 34f * dp
    val nominalH = totalRows * baseRowH
    val maxAvailableH = screenH - (margin * 2f)
    val actualRowH = if (nominalH > maxAvailableH) maxAvailableH / totalRows else baseRowH
    val panelH = actualRowH * totalRows
    val desiredW = if (screenW > screenH || (minOf(screenW, screenH) / dp) >= 600f) 276f * dp else 248f * dp
    val panelW = desiredW.coerceAtMost(screenW - (margin * 2f))
    val left = margin
    val right = left + panelW
    val top = ((screenH - panelH) / 2f).coerceAtLeast(margin)
    val bottom = top + panelH
    return RectF(left, top, right, bottom)
}

internal data class HudStyleTabLayout(
    val openInPlaceRect: RectF,
    val colorPills: List<Pair<String, RectF>>,
    val colorLabelY: Float,
    val spacingPills: List<Pair<Int, RectF>>,
    val spacingLabelY: Float,
    val feelPills: List<Pair<String, RectF>>,
    val feelLabelY: Float,
    val radiusPills: List<Pair<Int, RectF>>,
    val radiusLabelY: Float,
    val sparkPills: List<Pair<String, RectF>>,
    val sparkLabelY: Float
)

internal fun computeHudStyleTabLayout(
    bounds: RectF,
    tabRowBottom: Float,
    btnRowTop: Float,
    dp: Float
): HudStyleTabLayout {
    val margin = 10f * dp
    val contentW = (bounds.width() - margin * 2f).coerceAtLeast(1f)
    val contentTop = tabRowBottom + 10f * dp
    val contentH = (btnRowTop - contentTop - 8f * dp).coerceAtLeast(180f * dp)
    val scaleY = (contentH / 288f / dp).coerceIn(0.68f, 1.15f)
    val pillH = 23f * dp * scaleY
    val gap = 4f * dp
    val sectionGap = 9.5f * dp * scaleY
    val labelHeight = 13.5f * dp * scaleY

    var y = contentTop
    val openInPlaceRect = RectF(bounds.left + margin, y, bounds.right - margin, y + pillH * 1.12f)
    y = openInPlaceRect.bottom + sectionGap

    fun <T> buildRow(options: List<Pair<T, String>>): Pair<Float, List<Pair<T, RectF>>> {
        val labelY = y + labelHeight * 0.72f
        val pillTop = y + labelHeight
        val count = options.size.coerceAtLeast(1)
        val pillW = (contentW - gap * (count - 1)) / count
        val rects = options.mapIndexed { i, opt ->
            val pLeft = bounds.left + margin + i * (pillW + gap)
            opt.first to RectF(pLeft, pillTop, pLeft + pillW, pillTop + pillH)
        }
        y = pillTop + pillH + sectionGap
        return labelY to rects
    }

    val (colorLabelY, colorPills) = buildRow(CATEGORY_COLOR_ENGINE_OPTIONS)
    val (spacingLabelY, spacingPills) = buildRow(CATEGORY_SPACING_OPTIONS)
    val (feelLabelY, feelPills) = buildRow(CATEGORY_PHYSICS_OPTIONS)
    val (radiusLabelY, radiusPills) = buildRow(CATEGORY_HUD_RADIUS_OPTIONS)
    val (sparkLabelY, sparkPills) = buildRow(CATEGORY_LAUNCH_SPARK_OPTIONS)

    return HudStyleTabLayout(
        openInPlaceRect = openInPlaceRect,
        colorPills = colorPills,
        colorLabelY = colorLabelY,
        spacingPills = spacingPills,
        spacingLabelY = spacingLabelY,
        feelPills = feelPills,
        feelLabelY = feelLabelY,
        radiusPills = radiusPills,
        radiusLabelY = radiusLabelY,
        sparkPills = sparkPills,
        sparkLabelY = sparkLabelY
    )
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
    if (!isEditModeOpen || isEditModePromptOpen || isCategoryIconPickerOpen) return
    editModeDragSourceIndex = row
    editModeDragTargetIndex = row
    editModeDragY = y
    editModeHasDragged = false
    triggerHardwareHaptic(35, 180)
    invalidate()
}

internal fun LightspeedCruiseOverlay.cancelEditModeDrag() {
    editModeDragRunnable?.let { removeCallbacks(it) }
    editModeDragRunnable = null
    hudVisibilityLongPressRunnable?.let { removeCallbacks(it) }
    hudVisibilityLongPressRunnable = null
    editModeDragSourceIndex = -1
    editModeDragTargetIndex = -1
    editModeHasDragged = false
}

internal fun LightspeedCruiseOverlay.drawEditModePanel(canvas: Canvas, paint: Paint) {
    if (!isEditModeOpen || editModeCategories.isEmpty()) return
    val dp = resources.displayMetrics.density
    val bounds = getEditModePanelBounds()
    val numCategories = editModeCategories.size
    val totalRows = getEditModeRowCount()
    val actualRowH = bounds.height() / totalRows
    // Theme-driven optical styling
    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val cornerRadius = (snapshot.cornerRadiusDp * 0.65f).coerceIn(10f, 24f) * dp
    val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
    val borderOverride = categoryBorderOverride()
    val borderColor = borderOverride ?: snapshot.borderColorArgb.toInt()

    // 1-4. Full optical flyout surface (incorporates dynamic M3 border in Material 3 mode)
    val effectiveSnapshot = if (borderOverride != null) {
        snapshot.copy(borderColorArgb = (borderOverride.toLong() and 0xFFFFFFFFL))
    } else {
        snapshot
    }
    drawDeckThemedFlyoutSurface(canvas, bounds, effectiveSnapshot, cornerRadius, dp, paint)

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
    paint.textSize = (actualRowH * 0.36f).coerceIn(10f * dp, 12.5f * dp) * overlayTextScale()
    paint.typeface = overlayTypeface(isBold = true)
    val tabFontMetrics = paint.fontMetrics
    val tabTextYOffset = (tabFontMetrics.descent + tabFontMetrics.ascent) / 2f

    val m3Accent = ensureHighContrastColor(tintColor)
    val unselectedTabColor = Color.argb(160, 200, 225, 255)

    // "Page" tab
    paint.color = if (editModeTab == EditModeTab.PAGE) m3Accent else unselectedTabColor
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tab_page).ifBlank { "Page" }, bounds.left + (tabW * 0.5f), tabRowCenterY - tabTextYOffset, paint)

    // "HUD Style" tab
    paint.color = if (editModeTab == EditModeTab.HUD) m3Accent else unselectedTabColor
    canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_tab_hud_style).ifBlank { "HUD Style" }, bounds.left + (tabW * 1.5f), tabRowCenterY - tabTextYOffset, paint)

    // Active indicator underline
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 2f * dp
    paint.color = m3Accent
    if (editModeTab == EditModeTab.PAGE) {
        canvas.drawLine(bounds.left + 16f * dp, tabRowBottom - 1.5f * dp, bounds.left + tabW - 16f * dp, tabRowBottom - 1.5f * dp, paint)
    } else {
        canvas.drawLine(bounds.left + tabW + 16f * dp, tabRowBottom - 1.5f * dp, bounds.right - 16f * dp, tabRowBottom - 1.5f * dp, paint)
    }

    val btnRowTop = bounds.bottom - actualRowH

    if (editModeTab == EditModeTab.PAGE) {
        drawEditModeCategoryRows(canvas, paint, bounds, effectiveSnapshot, actualRowH, tabRowBottom, numCategories, dp)
    } else {
        drawHudStyleTabBody(canvas, paint, bounds, tabRowBottom, btnRowTop, tintColor, dp)
    }

    // Button row at bottom
    val btnRowCenterY = btnRowTop + (actualRowH / 2f)
    val btnW = bounds.width() / 4f

    // Divider above buttons
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1f * dp
    paint.color = Color.argb(50, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
    canvas.drawLine(bounds.left, btnRowTop, bounds.right, btnRowTop, paint)

    val btnTextSize = (actualRowH * 0.32f).coerceIn(8.5f * dp, 11f * dp) * overlayTextScale()
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = btnTextSize
    paint.typeface = overlayTypeface(isBold = true)

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
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_btn_most_used).ifBlank { "Most used" }, bounds.left + (btnW * 1.5f), btnRowCenterY - btnTextYOffset, paint)

        // "Default"
        paint.color = Color.argb(225, 235, 240, 255)
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_btn_default).ifBlank { "Default" }, bounds.left + (btnW * 2.5f), btnRowCenterY - btnTextYOffset, paint)

        // "Save"
        paint.color = m3Accent
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_btn_save).ifBlank { "Save" }, bounds.left + (btnW * 3.5f), btnRowCenterY - btnTextYOffset, paint)
    } else {
        // Vertical divider before Save
        paint.color = Color.argb(40, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
        canvas.drawLine(bounds.left + (btnW * 3f), btnRowTop + 4f * dp, bounds.left + (btnW * 3f), bounds.bottom - 4f * dp, paint)

        val btnFontMetrics = paint.fontMetrics
        val btnTextYOffset = (btnFontMetrics.descent + btnFontMetrics.ascent) / 2f

        // "Save"
        paint.color = m3Accent
        canvas.drawText(com.sbf.lightspeed.system.lsString(R.string.ed_btn_save).ifBlank { "Save" }, bounds.left + (btnW * 3.5f), btnRowCenterY - btnTextYOffset, paint)
    }

    if (isCategoryIconPickerOpen) {
        drawCategoryIconPackPicker(canvas, paint)
    } else if (isEditModePromptOpen) {
        drawUnsavedPrompt(canvas, paint)
    }
}

private fun LightspeedCruiseOverlay.drawHudStyleTabBody(
    canvas: Canvas,
    paint: Paint,
    bounds: RectF,
    tabRowBottom: Float,
    btnRowTop: Float,
    tintColor: Int,
    dp: Float
) {
    val layout = computeHudStyleTabLayout(bounds, tabRowBottom, btnRowTop, dp)

    // Shortcut pill jumping directly into in-place HUD widget editor
    val shortcutRect = layout.openInPlaceRect
    paint.style = Paint.Style.FILL
    paint.color = Color.argb(55, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
    canvas.drawRoundRect(shortcutRect, 8f * dp, 8f * dp, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.1f * dp
    paint.color = ensureHighContrastColor(tintColor)
    canvas.drawRoundRect(shortcutRect, 8f * dp, 8f * dp, paint)

    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = 8.4f * dp * overlayTextScale()
    paint.typeface = overlayTypeface(isBold = true)
    val editInPlaceLabel = com.sbf.lightspeed.system.lsString(R.string.ed_btn_edit_inplace).ifBlank { "EDIT HUD WIDGETS IN-PLACE" }
    while (paint.measureText(editInPlaceLabel) > shortcutRect.width() - 8f * dp && paint.textSize > 6f * dp) {
        paint.textSize -= 0.4f * dp
    }
    paint.color = Color.WHITE
    val sfm = paint.fontMetrics
    canvas.drawText(
        editInPlaceLabel,
        shortcutRect.centerX(),
        shortcutRect.centerY() - ((sfm.descent + sfm.ascent) / 2f),
        paint
    )

    val currentColorMode = categoryColorMode()
    drawHudStyleOptionGroup(
        canvas = canvas,
        paint = paint,
        title = com.sbf.lightspeed.system.lsString(R.string.ed_lbl_color_engine).ifBlank { "COLOR ENGINE" },
        titleX = bounds.left + 12f * dp,
        titleY = layout.colorLabelY,
        options = CATEGORY_COLOR_ENGINE_OPTIONS,
        pills = layout.colorPills,
        selectedValue = currentColorMode,
        tintColor = tintColor,
        textSizePx = 7.5f * dp * overlayTextScale(),
        dp = dp
    )

    val currentSpacing = context.defaultPrefs().getInt(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_SPACING_PERCENT,
        LightspeedPreferences.DEFAULT_CATEGORY_CRUISE_SPACING_PERCENT
    )
    drawHudStyleOptionGroup(
        canvas = canvas,
        paint = paint,
        title = com.sbf.lightspeed.system.lsString(R.string.ed_lbl_cat_spacing).ifBlank { "CATEGORY NAME SPACING" },
        titleX = bounds.left + 12f * dp,
        titleY = layout.spacingLabelY,
        options = CATEGORY_SPACING_OPTIONS,
        pills = layout.spacingPills,
        selectedValue = currentSpacing,
        tintColor = tintColor,
        textSizePx = 7.5f * dp * overlayTextScale(),
        dp = dp
    )

    val currentFeel = context.defaultPrefs().getString(
        LightspeedPreferences.KEY_CATEGORY_CRUISE_PHYSICS_PROFILE,
        "magnetic"
    )
    drawHudStyleOptionGroup(
        canvas = canvas,
        paint = paint,
        title = com.sbf.lightspeed.system.lsString(R.string.ed_lbl_slider_feel).ifBlank { "CATEGORY SLIDER FEEL" },
        titleX = bounds.left + 12f * dp,
        titleY = layout.feelLabelY,
        options = CATEGORY_PHYSICS_OPTIONS,
        pills = layout.feelPills,
        selectedValue = currentFeel,
        tintColor = tintColor,
        textSizePx = 8f * dp * overlayTextScale(),
        dp = dp
    )

    val currentRadius = context.defaultPrefs().getInt(
        LightspeedPreferences.KEY_CATEGORY_HUD_CORNER_RADIUS_DP,
        LightspeedPreferences.DEFAULT_CATEGORY_HUD_CORNER_RADIUS_DP
    )
    drawHudStyleOptionGroup(
        canvas = canvas,
        paint = paint,
        title = com.sbf.lightspeed.system.lsString(R.string.ed_lbl_corner_round).ifBlank { "WIDGET CORNER ROUNDNESS" },
        titleX = bounds.left + 12f * dp,
        titleY = layout.radiusLabelY,
        options = CATEGORY_HUD_RADIUS_OPTIONS,
        pills = layout.radiusPills,
        selectedValue = currentRadius,
        tintColor = tintColor,
        textSizePx = 7.8f * dp * overlayTextScale(),
        dp = dp
    )

    val currentSparkStyle = categoryLaunchSparkStyle()
    drawHudStyleOptionGroup(
        canvas = canvas,
        paint = paint,
        title = com.sbf.lightspeed.system.lsString(R.string.ed_lbl_spark_style).ifBlank { "LAUNCH SPARK STYLE" },
        titleX = bounds.left + 12f * dp,
        titleY = layout.sparkLabelY,
        options = CATEGORY_LAUNCH_SPARK_OPTIONS,
        pills = layout.sparkPills,
        selectedValue = currentSparkStyle,
        tintColor = tintColor,
        textSizePx = 6.8f * dp * overlayTextScale(),
        dp = dp
    )
}

private fun <T> LightspeedCruiseOverlay.drawHudStyleOptionGroup(
    canvas: Canvas,
    paint: Paint,
    title: String,
    titleX: Float,
    titleY: Float,
    options: List<Pair<T, String>>,
    pills: List<Pair<T, RectF>>,
    selectedValue: T?,
    tintColor: Int,
    textSizePx: Float,
    dp: Float
) {
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.LEFT
    paint.textSize = 9.2f * dp * overlayTextScale()
    paint.typeface = overlayTypeface(isBold = true)
    paint.color = Color.argb(220, 200, 220, 255)
    canvas.drawText(title, titleX, titleY, paint)

    for (i in pills.indices) {
        val (value, pRect) = pills[i]
        val label = options.getOrNull(i)?.second ?: continue
        val isSelected = (selectedValue == value)

        paint.style = Paint.Style.FILL
        paint.color = if (isSelected) {
            Color.argb(180, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
        } else {
            Color.argb(35, 255, 255, 255)
        }
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f * dp
        paint.color = if (isSelected) tintColor else Color.argb(60, 255, 255, 255)
        canvas.drawRoundRect(pRect, 6f * dp, 6f * dp, paint)

        paint.style = Paint.Style.FILL
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = textSizePx
        paint.typeface = overlayTypeface(isBold = isSelected)
        while (paint.measureText(label) > pRect.width() - 4f * dp && paint.textSize > 5.5f * dp) {
            paint.textSize -= 0.4f * dp
        }
        paint.color = if (isSelected) Color.WHITE else Color.argb(235, 226, 232, 240)
        val pfm = paint.fontMetrics
        canvas.drawText(label, pRect.centerX(), pRect.centerY() - ((pfm.descent + pfm.ascent) / 2f), paint)
    }
}
