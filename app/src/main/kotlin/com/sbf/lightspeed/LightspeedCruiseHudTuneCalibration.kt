package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.SliderPresetManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.defaultPrefs
import kotlin.math.roundToInt

/**
 * Shared 12-theme palette & contrast bundle for Category Cruise HUD modal cards.
 */
internal data class TuneThemeColors(
    val snapshot: DeckThemeSnapshot,
    val cornerRadiusPx: Float,
    val accentColor: Int,
    val onAccentColor: Int,
    val textPrimary: Int,
    val textMuted: Int,
    val pillBg: Int,
    val pillBorder: Int,
    val dividerColor: Int
)

internal fun LightspeedCruiseOverlay.resolveTuneThemeColors(dp: Float): TuneThemeColors {
    val baseSnapshot = DeckThemeManager.getSnapshot(context)
    val borderOverride = categoryBorderOverride()
    val snapshot = if (borderOverride != null) {
        baseSnapshot.copy(borderColorArgb = (borderOverride.toLong() and 0xFFFFFFFFL))
    } else {
        baseSnapshot
    }
    val cornerRadiusPx = (snapshot.cornerRadiusDp * 0.65f).coerceIn(10f, 22f) * dp
    val rawAccent = categoryAccentOverride() ?: snapshot.effectiveTintArgb.toInt()
    val accentColor = ensureHighContrastColor(rawAccent)

    val panelCol = snapshot.panelColorArgb.toInt()
    val panelLum = (Color.red(panelCol) * 299 + Color.green(panelCol) * 587 + Color.blue(panelCol) * 114) / 1000
    val isLightSurface = panelLum > 165 && snapshot.panelAlpha > 0.55f

    val accentLum = (Color.red(accentColor) * 299 + Color.green(accentColor) * 587 + Color.blue(accentColor) * 114) / 1000
    val onAccentColor = if (accentLum > 145) Color.BLACK else Color.WHITE

    val textPrimary = if (isLightSurface) Color.argb(242, 20, 24, 34) else Color.argb(245, 242, 246, 255)
    val textMuted = if (isLightSurface) Color.argb(175, 48, 56, 72) else Color.argb(185, 200, 214, 236)
    val accR = Color.red(accentColor)
    val accG = Color.green(accentColor)
    val accB = Color.blue(accentColor)

    return TuneThemeColors(
        snapshot = snapshot,
        cornerRadiusPx = cornerRadiusPx,
        accentColor = accentColor,
        onAccentColor = onAccentColor,
        textPrimary = textPrimary,
        textMuted = textMuted,
        pillBg = Color.argb(if (isLightSurface) 28 else 40, accR, accG, accB),
        pillBorder = Color.argb(115, accR, accG, accB),
        dividerColor = Color.argb(55, accR, accG, accB)
    )
}

/**
 * Unified descriptor for every HUD widget tuning slider so rendering, rail scrubbing,
 * calibration stepping, and long-press single resets share one source of truth.
 */
internal data class TuneSliderSpec(
    val id: String,
    val label: String,
    val minVal: Int,
    val maxVal: Int,
    val unit: String,
    val supportsAuto: Boolean,
    val explicitPresetMin: Int,
    val dotsEveryStep: Boolean,
    val defaultDisplay: (isLandscape: Boolean, itemType: String) -> String,
    val isAtDefault: (item: CategoryHudItem, isLandscape: Boolean) -> Boolean,
    val isAutoActive: (item: CategoryHudItem) -> Boolean,
    val getSliderPos: (item: CategoryHudItem) -> Int,
    val getDisplayValue: (item: CategoryHudItem) -> String,
    val applySliderPos: (item: CategoryHudItem, pos: Int) -> Unit,
    val applySteppedDelta: (item: CategoryHudItem, delta: Int) -> Unit,
    val applyAutoOrReset: (item: CategoryHudItem, isLandscape: Boolean) -> Unit
)

internal fun defaultHudWidgetWidth(isLandscape: Boolean): Int = if (isLandscape) 5 else 10

internal fun defaultHudWidgetHeight(type: String): Int = when (type) {
    LightspeedCruiseHudPods.MODE_APP_AND_CAT -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 2 else 1
    LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> 4
    else -> 3
}

internal fun buildTuneSliderSpecs(item: CategoryHudItem): List<TuneSliderSpec> {
    val isInspector = item.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT
    val minHCells = when (item.type) {
        LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 3 else 1
        LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME,
        LightspeedCruiseHudPods.MODE_APP_AND_CAT -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 2 else 1
        else -> 1
    }

    val specs = mutableListOf(
        TuneSliderSpec(
            id = "item_w",
            label = "WIDTH",
            minVal = 1,
            maxVal = CategoryHudManager.MAX_CELLS,
            unit = "/10",
            supportsAuto = false,
            explicitPresetMin = 1,
            dotsEveryStep = true,
            defaultDisplay = { isLand, _ -> "${defaultHudWidgetWidth(isLand)}/10" },
            isAtDefault = { it, isLand -> it.wCells == defaultHudWidgetWidth(isLand) },
            isAutoActive = { false },
            getSliderPos = { it.wCells.coerceIn(1, CategoryHudManager.MAX_CELLS) },
            getDisplayValue = { "${it.wCells}/10" },
            applySliderPos = { it, pos -> it.wCells = pos.coerceIn(1, CategoryHudManager.MAX_CELLS) },
            applySteppedDelta = { it, d -> it.wCells = (it.wCells + d).coerceIn(1, CategoryHudManager.MAX_CELLS) },
            applyAutoOrReset = { it, isLand -> it.wCells = defaultHudWidgetWidth(isLand) }
        ),
        TuneSliderSpec(
            id = "item_h",
            label = "HEIGHT",
            minVal = minHCells,
            maxVal = CategoryHudManager.MAX_CELLS,
            unit = "/10",
            supportsAuto = false,
            explicitPresetMin = minHCells,
            dotsEveryStep = true,
            defaultDisplay = { _, t -> "${defaultHudWidgetHeight(t)}/10" },
            isAtDefault = { it, _ -> it.hCells == defaultHudWidgetHeight(it.type) },
            isAutoActive = { false },
            getSliderPos = { it.hCells.coerceIn(minHCells, CategoryHudManager.MAX_CELLS) },
            getDisplayValue = { "${it.hCells}/10" },
            applySliderPos = { it, pos -> it.hCells = pos.coerceIn(minHCells, CategoryHudManager.MAX_CELLS) },
            applySteppedDelta = { it, d -> it.hCells = (it.hCells + d).coerceIn(minHCells, CategoryHudManager.MAX_CELLS) },
            applyAutoOrReset = { it, _ -> it.hCells = defaultHudWidgetHeight(it.type) }
        ),
        TuneSliderSpec(
            id = "item_opacity",
            label = "OPACITY",
            minVal = CategoryHudManager.OPACITY_MIN,
            maxVal = CategoryHudManager.OPACITY_MAX,
            unit = "%",
            supportsAuto = false,
            explicitPresetMin = CategoryHudManager.OPACITY_MIN,
            dotsEveryStep = false,
            defaultDisplay = { _, _ -> "100%" },
            isAtDefault = { it, _ -> it.opacity == 100 },
            isAutoActive = { false },
            getSliderPos = { it.opacity.coerceIn(CategoryHudManager.OPACITY_MIN, CategoryHudManager.OPACITY_MAX) },
            getDisplayValue = { "${it.opacity}%" },
            applySliderPos = { it, pos -> it.opacity = pos.coerceIn(CategoryHudManager.OPACITY_MIN, CategoryHudManager.OPACITY_MAX) },
            applySteppedDelta = { it, d -> it.opacity = (it.opacity + d).coerceIn(CategoryHudManager.OPACITY_MIN, CategoryHudManager.OPACITY_MAX) },
            applyAutoOrReset = { it, _ -> it.opacity = 100 }
        ),
        TuneSliderSpec(
            id = "item_radius",
            label = "CORNER RADIUS",
            minVal = 0,
            maxVal = CategoryHudManager.RADIUS_MAX_DP,
            unit = "dp",
            supportsAuto = true,
            explicitPresetMin = 0,
            dotsEveryStep = false,
            defaultDisplay = { _, _ -> "AUTO" },
            isAtDefault = { it, _ -> it.cornerRadiusDp < 0 },
            isAutoActive = { it.cornerRadiusDp < 0 },
            getSliderPos = { if (it.cornerRadiusDp < 0) 12 else it.cornerRadiusDp.coerceIn(0, CategoryHudManager.RADIUS_MAX_DP) },
            getDisplayValue = { if (it.cornerRadiusDp < 0) "AUTO" else "${it.cornerRadiusDp}dp" },
            applySliderPos = { it, pos -> it.cornerRadiusDp = pos.coerceIn(0, CategoryHudManager.RADIUS_MAX_DP) },
            applySteppedDelta = { it, d ->
                val base = if (it.cornerRadiusDp < 0) 12 else it.cornerRadiusDp
                it.cornerRadiusDp = (base + d).coerceIn(0, CategoryHudManager.RADIUS_MAX_DP)
            },
            applyAutoOrReset = { it, _ -> it.cornerRadiusDp = -1 }
        ),
        buildPadSliderSpec("item_pad_l", "PAD LEFT", { it.padLeftDp }, { it, v -> it.padLeftDp = v }),
        buildPadSliderSpec("item_pad_t", "PAD TOP", { it.padTopDp }, { it, v -> it.padTopDp = v }),
        buildPadSliderSpec("item_pad_r", "PAD RIGHT", { it.padRightDp }, { it, v -> it.padRightDp = v }),
        buildPadSliderSpec("item_pad_b", "PAD BOTTOM", { it.padBottomDp }, { it, v -> it.padBottomDp = v })
    )

    if (isInspector) {
        specs += TuneSliderSpec(
            id = "item_title_fs",
            label = "TITLE SIZE",
            minVal = 9,
            maxVal = 24,
            unit = "sp",
            supportsAuto = true,
            explicitPresetMin = 10,
            dotsEveryStep = false,
            defaultDisplay = { _, _ -> "AUTO" },
            isAtDefault = { it, _ -> it.titleFontSizeSp <= 0 },
            isAutoActive = { it.titleFontSizeSp <= 0 },
            getSliderPos = { if (it.titleFontSizeSp <= 0) 9 else it.titleFontSizeSp.coerceIn(10, 24) },
            getDisplayValue = { if (it.titleFontSizeSp <= 0) "AUTO" else "${it.titleFontSizeSp}sp" },
            applySliderPos = { it, pos -> it.titleFontSizeSp = if (pos <= 9) 0 else pos.coerceIn(10, 24) },
            applySteppedDelta = { it, d ->
                val base = if (it.titleFontSizeSp <= 0) 9 else it.titleFontSizeSp
                val next = (base + d).coerceIn(9, 24)
                it.titleFontSizeSp = if (next <= 9) 0 else next
            },
            applyAutoOrReset = { it, _ -> it.titleFontSizeSp = 0 }
        )
        specs += TuneSliderSpec(
            id = "item_sub_fs",
            label = "SUB SIZE",
            minVal = 6,
            maxVal = 16,
            unit = "sp",
            supportsAuto = true,
            explicitPresetMin = 7,
            dotsEveryStep = true,
            defaultDisplay = { _, _ -> "AUTO" },
            isAtDefault = { it, _ -> it.subtitleFontSizeSp <= 0 },
            isAutoActive = { it.subtitleFontSizeSp <= 0 },
            getSliderPos = { if (it.subtitleFontSizeSp <= 0) 6 else it.subtitleFontSizeSp.coerceIn(7, 16) },
            getDisplayValue = { if (it.subtitleFontSizeSp <= 0) "AUTO" else "${it.subtitleFontSizeSp}sp" },
            applySliderPos = { it, pos -> it.subtitleFontSizeSp = if (pos <= 6) 0 else pos.coerceIn(7, 16) },
            applySteppedDelta = { it, d ->
                val base = if (it.subtitleFontSizeSp <= 0) 6 else it.subtitleFontSizeSp
                val next = (base + d).coerceIn(6, 16)
                it.subtitleFontSizeSp = if (next <= 6) 0 else next
            },
            applyAutoOrReset = { it, _ -> it.subtitleFontSizeSp = 0 }
        )
    }

    return specs
}

private fun buildPadSliderSpec(
    id: String,
    label: String,
    get: (CategoryHudItem) -> Int,
    set: (CategoryHudItem, Int) -> Unit
): TuneSliderSpec = TuneSliderSpec(
    id = id,
    label = label,
    minVal = 0,
    maxVal = CategoryHudManager.PAD_MAX_DP,
    unit = "dp",
    supportsAuto = false,
    explicitPresetMin = 0,
    dotsEveryStep = false,
    defaultDisplay = { _, _ -> "0dp" },
    isAtDefault = { it, _ -> get(it) == 0 },
    isAutoActive = { false },
    getSliderPos = { get(it).coerceIn(0, CategoryHudManager.PAD_MAX_DP) },
    getDisplayValue = { "${get(it)}dp" },
    applySliderPos = { it, pos -> set(it, pos.coerceIn(0, CategoryHudManager.PAD_MAX_DP)) },
    applySteppedDelta = { it, d -> set(it, (get(it) + d).coerceIn(0, CategoryHudManager.PAD_MAX_DP)) },
    applyAutoOrReset = { it, _ -> set(it, 0) }
)

internal fun LightspeedCruiseOverlay.applyLiveHudTuneItemChanges(catId: String, item: CategoryHudItem) {
    val idx = activeCategoryHudItems.indexOfFirst { it.id == item.id }
    if (idx >= 0) {
        activeCategoryHudItems[idx] = item
    }
    CategoryHudManager.saveHudItems(context, catId, activeCategoryHudItems)
    computeHudSlotBounds()
    hudTuneTargetItem = activeCategoryHudItems.firstOrNull { it.id == item.id } ?: item
    updateCategoryAppWidgetView()
    categoryGridCache.clear()
    invalidate()
}

// --- Inline 12-Theme Canvas Slider Calibration Popup (mirrors Central Command SliderCalibrationFlyoutDialog) ---
private var tuneCalibSliderId: String? = null
private var tuneCalibKeypadOpen: Boolean = false
private var tuneCalibTypedBuffer: String = ""
private var tuneCalibReplaceOnNextDigit: Boolean = true
private var tuneCalibProfilesExpanded: Boolean = true

private val tuneCalibModalRect = RectF()
private val tuneCalibCloseBtnRect = RectF()
private val tuneCalibTypeCardRect = RectF()
private val tuneCalibApplyBtnRect = RectF()
private val tuneCalibFineMinusRect = RectF()
private val tuneCalibFinePlusRect = RectF()
private val tuneCalibTapToJumpRect = RectF()
private val tuneCalibResetBtnRect = RectF()
private val tuneCalibProfilesHeaderRect = RectF()
private val tuneCalibAddProfileRect = RectF()
private val tuneCalibKeypadBtnRects = mutableListOf<Pair<String, RectF>>()
private data class TuneProfileRowHit(
    val preset: SliderPresetManager.PresetItem,
    val rowRect: RectF,
    val deleteRect: RectF
)
private val tuneCalibProfileRows = mutableListOf<TuneProfileRowHit>()

internal fun isTuneCalibrationModalOpen(): Boolean = tuneCalibSliderId != null

internal fun LightspeedCruiseOverlay.openTuneCalibrationModal(sliderId: String) {
    tuneCalibSliderId = sliderId
    tuneCalibKeypadOpen = false
    val item = hudTuneTargetItem
    val spec = item?.let { buildTuneSliderSpecs(it).firstOrNull { s -> s.id == sliderId } }
    tuneCalibTypedBuffer = if (item != null && spec != null) spec.getSliderPos(item).toString() else ""
    tuneCalibReplaceOnNextDigit = true
    isTuneFontDropdownOpen = false
    tuneFontDropdownTracking = false
    invalidate()
}

internal fun LightspeedCruiseOverlay.closeTuneCalibrationModal() {
    if (tuneCalibSliderId != null) {
        tuneCalibSliderId = null
        tuneCalibKeypadOpen = false
        tuneCalibKeypadBtnRects.clear()
        tuneCalibProfileRows.clear()
        invalidate()
    }
}

internal fun LightspeedCruiseOverlay.drawTuneCalibrationModal(
    canvas: Canvas,
    dp: Float,
    anchorRect: RectF,
    item: CategoryHudItem,
    colors: TuneThemeColors
) {
    val targetId = tuneCalibSliderId ?: return
    val spec = buildTuneSliderSpecs(item).firstOrNull { it.id == targetId } ?: run {
        tuneCalibSliderId = null
        return
    }

    val isLandscape = width > height
    val screenW = width.toFloat()
    val screenH = height.toFloat()
    val prefs = context.defaultPrefs()
    val presets = SliderPresetManager.getPresets(prefs, spec.id)
    val isTapToJump = prefs.getBoolean(LightspeedPreferences.keySliderTapToJump(spec.id), false)

    // Dim only inside the underlying Tune card so the live widget stays crystal clear
    canvas.save()
    val clipPath = Path().apply {
        addRoundRect(anchorRect, colors.cornerRadiusPx, colors.cornerRadiusPx, Path.Direction.CW)
    }
    canvas.clipPath(clipPath)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(140, 0, 0, 0)
    canvas.drawRect(anchorRect, highlightPaint)
    canvas.restore()

    val accR = Color.red(colors.accentColor)
    val accG = Color.green(colors.accentColor)
    val accB = Color.blue(colors.accentColor)
    val mutR = Color.red(colors.textMuted)
    val mutG = Color.green(colors.textMuted)
    val mutB = Color.blue(colors.textMuted)
    val mutedCardBg = Color.argb(38, mutR, mutG, mutB)
    val mutedCardBorder = Color.argb(72, mutR, mutG, mutB)

    val padX = 13f * dp
    val headerH = 40f * dp
    val cardsH = 48f * dp
    val keypadRowH = 25f * dp
    val keypadH = if (tuneCalibKeypadOpen) {
        (keypadRowH * 2f + 5f * dp + (if (spec.supportsAuto) keypadRowH + 5f * dp else 0f) + 8f * dp)
    } else 0f
    val tapCardH = 38f * dp
    val resetBtnH = 32f * dp
    val maxAvailH = (screenH - getSafeHudTopOffset() - 18f * dp).coerceAtLeast(240f * dp)
    val baseNoProfilesH = 11f * dp + headerH + 6f * dp + cardsH + (if (tuneCalibKeypadOpen) keypadH + 6f * dp else 0f) +
        8f * dp + tapCardH + 7f * dp + resetBtnH + 9f * dp + 34f * dp + 10f * dp
    val addRowH = 26f * dp
    val profRowH = 25f * dp
    val maxVisibleProfiles = if (tuneCalibProfilesExpanded && presets.isNotEmpty()) {
        val roomForRows = (maxAvailH - baseNoProfilesH - addRowH - 12f * dp).coerceAtLeast(0f)
        (roomForRows / (profRowH + 3f * dp)).toInt().coerceIn(1, 4).coerceAtMost(presets.size)
    } else 0
    val profilesBoxH = if (tuneCalibProfilesExpanded) {
        val rowsBlock = if (presets.isEmpty()) 22f * dp else (maxVisibleProfiles * (profRowH + 3f * dp))
        34f * dp + addRowH + 6f * dp + rowsBlock + 4f * dp
    } else 34f * dp

    val modalW = (anchorRect.width() - 8f * dp).coerceIn(264f * dp, 344f * dp)
    val modalH = (11f * dp + headerH + 6f * dp + cardsH + (if (tuneCalibKeypadOpen) keypadH + 6f * dp else 0f) +
        8f * dp + tapCardH + 7f * dp + resetBtnH + 9f * dp + profilesBoxH + 10f * dp).coerceAtMost(maxAvailH)

    val minTop = getSafeHudTopOffset() + 8f * dp
    val maxTop = (screenH - modalH - 8f * dp).coerceAtLeast(minTop)
    val left = (anchorRect.centerX() - modalW / 2f).coerceIn(8f * dp, screenW - modalW - 8f * dp)
    val top = (anchorRect.centerY() - modalH / 2f).coerceIn(minTop, maxTop)
    tuneCalibModalRect.set(left, top, left + modalW, top + modalH)

    // 12-theme optical glass surface (same engine as pinned app menu & Central Command flyouts)
    drawDeckThemedFlyoutSurface(canvas, tuneCalibModalRect, colors.snapshot, colors.cornerRadiusPx, dp, highlightPaint)

    val contentLeft = left + padX
    val contentRight = left + modalW - padX
    val contentW = contentRight - contentLeft

    // 1. Header (Circular Tune Icon Badge + Title / Subtitle + Close X)
    val badgeSize = 26f * dp
    val badgeCx = contentLeft + badgeSize / 2f
    val badgeCy = top + 11f * dp + badgeSize / 2f
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawCircle(badgeCx, badgeCy, badgeSize / 2f, highlightPaint)
    // Geometric Tune sliders glyph inside badge
    highlightPaint.strokeWidth = 1.4f * dp
    highlightPaint.color = colors.accentColor
    canvas.drawLine(badgeCx - 6f * dp, badgeCy - 3f * dp, badgeCx + 6f * dp, badgeCy - 3f * dp, highlightPaint)
    canvas.drawLine(badgeCx - 6f * dp, badgeCy + 3f * dp, badgeCx + 6f * dp, badgeCy + 3f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.FILL
    canvas.drawCircle(badgeCx + 2.2f * dp, badgeCy - 3f * dp, 2.0f * dp, highlightPaint)
    canvas.drawCircle(badgeCx - 2.2f * dp, badgeCy + 3f * dp, 2.0f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 10.6f * dp
    textPaint.color = colors.textPrimary
    val titleFm = textPaint.fontMetrics
    canvas.drawText(spec.label, contentLeft + badgeSize + 8f * dp, top + 19f * dp - (titleFm.descent + titleFm.ascent) / 2f, textPaint)

    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 8.1f * dp
    textPaint.color = colors.textMuted
    val subFm = textPaint.fontMetrics
    canvas.drawText("Precision Control & Presets", contentLeft + badgeSize + 8f * dp, top + 31f * dp - (subFm.descent + subFm.ascent) / 2f, textPaint)

    val closeSize = 22f * dp
    tuneCalibCloseBtnRect.set(contentRight - closeSize, top + 12f * dp, contentRight, top + 12f * dp + closeSize)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = colors.pillBg
    canvas.drawRoundRect(tuneCalibCloseBtnRect, 6f * dp, 6f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(tuneCalibCloseBtnRect, 6f * dp, 6f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 10f * dp
    textPaint.color = colors.textPrimary
    val cFm = textPaint.fontMetrics
    canvas.drawText("✕", tuneCalibCloseBtnRect.centerX(), tuneCalibCloseBtnRect.centerY() - (cFm.descent + cFm.ascent) / 2f, textPaint)

    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawLine(contentLeft, top + 45f * dp, contentRight, top + 45f * dp, highlightPaint)

    // 2. Three Readout & Control Cards Row (Type Value | Factory Default | Fine Gearing +-1)
    var curY = top + 51f * dp
    val cardGap = 6f * dp
    val cardW = (contentW - cardGap * 2f) / 3f

    // Tile 1: Type Value
    tuneCalibTypeCardRect.set(contentLeft, curY, contentLeft + cardW, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (tuneCalibKeypadOpen) Color.argb(68, accR, accG, accB) else colors.pillBg
    canvas.drawRoundRect(tuneCalibTypeCardRect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = if (tuneCalibKeypadOpen) 1.3f * dp else 0.9f * dp
    highlightPaint.color = colors.accentColor
    canvas.drawRoundRect(tuneCalibTypeCardRect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.accentColor
    val lblFm = textPaint.fontMetrics
    canvas.drawText("Type Value", tuneCalibTypeCardRect.left + 7f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    // Apply check button inside Type Value tile
    val checkSize = 18f * dp
    tuneCalibApplyBtnRect.set(
        tuneCalibTypeCardRect.right - checkSize - 5f * dp,
        curY + cardsH - checkSize - 5f * dp,
        tuneCalibTypeCardRect.right - 5f * dp,
        curY + cardsH - 5f * dp
    )
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (tuneCalibKeypadOpen) colors.accentColor else Color.argb(45, accR, accG, accB)
    canvas.drawRoundRect(tuneCalibApplyBtnRect, 4.5f * dp, 4.5f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.textSize = 9.5f * dp
    textPaint.color = if (tuneCalibKeypadOpen) colors.onAccentColor else colors.accentColor
    val kFm = textPaint.fontMetrics
    canvas.drawText(if (tuneCalibKeypadOpen) "✓" else "✎", tuneCalibApplyBtnRect.centerX(), tuneCalibApplyBtnRect.centerY() - (kFm.descent + kFm.ascent) / 2f, textPaint)

    val typeDisp = if (tuneCalibKeypadOpen) {
        (tuneCalibTypedBuffer.ifEmpty { "0" }) + "_"
    } else {
        spec.getDisplayValue(item)
    }
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.MONOSPACE
    textPaint.textSize = 11.4f * dp
    textPaint.color = colors.textPrimary
    val vFm = textPaint.fontMetrics
    canvas.drawText(typeDisp, tuneCalibTypeCardRect.left + 7f * dp, curY + 34f * dp - (vFm.descent + vFm.ascent) / 2f, textPaint)

    // Tile 2: Factory Default
    val tile2Rect = RectF(tuneCalibTypeCardRect.right + cardGap, curY, tuneCalibTypeCardRect.right + cardGap + cardW, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tile2Rect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = mutedCardBorder
    canvas.drawRoundRect(tile2Rect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.textMuted
    canvas.drawText("Factory Default", tile2Rect.left + 7f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    val defaultDisp = spec.defaultDisplay(isLandscape, item.type)
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 11.2f * dp
    textPaint.color = colors.textPrimary
    canvas.drawText(defaultDisp, tile2Rect.left + 7f * dp, curY + 34f * dp - (vFm.descent + vFm.ascent) / 2f, textPaint)

    // Tile 3: Fine Gearing (±1 Step with interactive - / + buttons)
    val tile3Rect = RectF(tile2Rect.right + cardGap, curY, contentRight, curY + cardsH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tile3Rect, 8f * dp, 8f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.pillBorder
    canvas.drawRoundRect(tile3Rect, 8f * dp, 8f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.accentColor
    canvas.drawText("↕ Fine Gearing", tile3Rect.left + 6f * dp, curY + 12f * dp - (lblFm.descent + lblFm.ascent) / 2f, textPaint)

    val stepBtnW = 20f * dp
    val stepBtnH = 19f * dp
    val stepBtnY = curY + cardsH - stepBtnH - 5f * dp
    tuneCalibFineMinusRect.set(tile3Rect.left + 5f * dp, stepBtnY, tile3Rect.left + 5f * dp + stepBtnW, stepBtnY + stepBtnH)
    tuneCalibFinePlusRect.set(tile3Rect.right - 5f * dp - stepBtnW, stepBtnY, tile3Rect.right - 5f * dp, stepBtnY + stepBtnH)
    for ((sym, rect) in listOf("-" to tuneCalibFineMinusRect, "+" to tuneCalibFinePlusRect)) {
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = colors.pillBg
        canvas.drawRoundRect(rect, 4.5f * dp, 4.5f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = colors.pillBorder
        canvas.drawRoundRect(rect, 4.5f * dp, 4.5f * dp, highlightPaint)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.MONOSPACE
        textPaint.textSize = 10.5f * dp
        textPaint.color = colors.textPrimary
        val sFm = textPaint.fontMetrics
        canvas.drawText(sym, rect.centerX(), rect.centerY() - (sFm.descent + sFm.ascent) / 2f, textPaint)
    }
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.0f * dp
    textPaint.color = colors.textPrimary
    canvas.drawText("±1", tile3Rect.centerX(), stepBtnY + stepBtnH / 2f - (vFm.descent + vFm.ascent) / 2f, textPaint)

    curY += cardsH + 6f * dp

    // 2b. Inline Compact Numeric Keypad (opened by tapping Type Value tile)
    tuneCalibKeypadBtnRects.clear()
    if (tuneCalibKeypadOpen) {
        val padBoxRect = RectF(contentLeft, curY, contentRight, curY + keypadH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = mutedCardBg
        canvas.drawRoundRect(padBoxRect, 8f * dp, 8f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.8f * dp
        highlightPaint.color = colors.dividerColor
        canvas.drawRoundRect(padBoxRect, 8f * dp, 8f * dp, highlightPaint)

        val kpPad = 4f * dp
        val keysRow1 = listOf("1", "2", "3", "4", "5", "⌫")
        val keysRow2 = listOf("6", "7", "8", "9", "0", "✓")
        val keyW = (contentW - kpPad * 7f) / 6f
        val rows = listOf(keysRow1, keysRow2)
        var ky = curY + kpPad
        for (row in rows) {
            for ((idx, keyLabel) in row.withIndex()) {
                val kx = contentLeft + kpPad + idx * (keyW + kpPad)
                val rect = RectF(kx, ky, kx + keyW, ky + keypadRowH)
                tuneCalibKeypadBtnRects += keyLabel to rect
                val isCommit = keyLabel == "✓"
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (isCommit) colors.accentColor else colors.pillBg
                canvas.drawRoundRect(rect, 5f * dp, 5f * dp, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 0.8f * dp
                highlightPaint.color = if (isCommit) colors.accentColor else colors.pillBorder
                canvas.drawRoundRect(rect, 5f * dp, 5f * dp, highlightPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 10f * dp
                textPaint.color = if (isCommit) colors.onAccentColor else colors.textPrimary
                val pFm = textPaint.fontMetrics
                canvas.drawText(keyLabel, rect.centerX(), rect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)
            }
            ky += keypadRowH + kpPad
        }
        if (spec.supportsAuto) {
            val autoRect = RectF(contentLeft + kpPad, ky, contentRight - kpPad, ky + keypadRowH)
            tuneCalibKeypadBtnRects += "AUTO" to autoRect
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = colors.pillBg
            canvas.drawRoundRect(autoRect, 5f * dp, 5f * dp, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.85f * dp
            highlightPaint.color = colors.accentColor
            canvas.drawRoundRect(autoRect, 5f * dp, 5f * dp, highlightPaint)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT_BOLD
            textPaint.textSize = 9.0f * dp
            textPaint.color = colors.accentColor
            val aFm = textPaint.fontMetrics
            canvas.drawText("RESET TO AUTO", autoRect.centerX(), autoRect.centerY() - (aFm.descent + aFm.ascent) / 2f, textPaint)
        }
        curY += keypadH + 6f * dp
    }

    // 3. Tap-to-Jump on Track Switch Card
    curY += 2f * dp
    tuneCalibTapToJumpRect.set(contentLeft, curY, contentRight, curY + tapCardH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(tuneCalibTapToJumpRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = mutedCardBorder
    canvas.drawRoundRect(tuneCalibTapToJumpRect, 9f * dp, 9f * dp, highlightPaint)

    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.4f * dp
    textPaint.color = colors.textPrimary
    val swTitleFm = textPaint.fontMetrics
    canvas.drawText("Tap-to-Jump on Track", contentLeft + 10f * dp, curY + 14f * dp - (swTitleFm.descent + swTitleFm.ascent) / 2f, textPaint)

    textPaint.typeface = Typeface.DEFAULT
    textPaint.textSize = 7.7f * dp
    textPaint.color = colors.textMuted
    val swSubFm = textPaint.fontMetrics
    canvas.drawText("Snap thumb to tap position on rail", contentLeft + 10f * dp, curY + 26.5f * dp - (swSubFm.descent + swSubFm.ascent) / 2f, textPaint)

    val swW = 34f * dp
    val swH = 19f * dp
    val swRect = RectF(contentRight - swW - 10f * dp, tuneCalibTapToJumpRect.centerY() - swH / 2f, contentRight - 10f * dp, tuneCalibTapToJumpRect.centerY() + swH / 2f)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = if (isTapToJump) colors.accentColor else Color.argb(65, mutR, mutG, mutB)
    canvas.drawRoundRect(swRect, swH / 2f, swH / 2f, highlightPaint)
    val thumbR = (swH - 5f * dp) / 2f
    val thumbCx = if (isTapToJump) swRect.right - swH / 2f else swRect.left + swH / 2f
    highlightPaint.color = if (isTapToJump) colors.onAccentColor else colors.textPrimary
    canvas.drawCircle(thumbCx, swRect.centerY(), thumbR, highlightPaint)

    curY += tapCardH + 7f * dp

    // 4. Reset to Default Button
    tuneCalibResetBtnRect.set(contentLeft, curY, contentRight, curY + resetBtnH)
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = Color.argb(65, 175, 55, 65)
    canvas.drawRoundRect(tuneCalibResetBtnRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.9f * dp
    highlightPaint.color = Color.argb(125, 255, 120, 130)
    canvas.drawRoundRect(tuneCalibResetBtnRect, 9f * dp, 9f * dp, highlightPaint)
    textPaint.textAlign = Paint.Align.CENTER
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.3f * dp
    textPaint.color = Color.argb(240, 255, 195, 200)
    val rstFm = textPaint.fontMetrics
    canvas.drawText("↻  Reset to Default ($defaultDisp)", tuneCalibResetBtnRect.centerX(), tuneCalibResetBtnRect.centerY() - (rstFm.descent + rstFm.ascent) / 2f, textPaint)

    curY += resetBtnH + 9f * dp

    // 5. Saved Profiles Collapsible Card (backed directly by SliderPresetManager)
    val profBoxRect = RectF(contentLeft, curY, contentRight, (tuneCalibModalRect.bottom - 10f * dp).coerceAtLeast(curY + 34f * dp))
    highlightPaint.style = Paint.Style.FILL
    highlightPaint.color = mutedCardBg
    canvas.drawRoundRect(profBoxRect, 9f * dp, 9f * dp, highlightPaint)
    highlightPaint.style = Paint.Style.STROKE
    highlightPaint.strokeWidth = 0.85f * dp
    highlightPaint.color = colors.dividerColor
    canvas.drawRoundRect(profBoxRect, 9f * dp, 9f * dp, highlightPaint)

    tuneCalibProfilesHeaderRect.set(profBoxRect.left, profBoxRect.top, profBoxRect.right, profBoxRect.top + 32f * dp)
    textPaint.textAlign = Paint.Align.LEFT
    textPaint.typeface = Typeface.DEFAULT_BOLD
    textPaint.textSize = 9.4f * dp
    textPaint.color = colors.textPrimary
    val phFm = textPaint.fontMetrics
    canvas.drawText("Saved Profiles (${presets.size})", profBoxRect.left + 10f * dp, tuneCalibProfilesHeaderRect.centerY() - (phFm.descent + phFm.ascent) / 2f, textPaint)

    textPaint.textAlign = Paint.Align.RIGHT
    textPaint.textSize = 10.5f * dp
    textPaint.color = colors.textMuted
    canvas.drawText(if (tuneCalibProfilesExpanded) "⌃" else "⌄", profBoxRect.right - 10f * dp, tuneCalibProfilesHeaderRect.centerY() - (phFm.descent + phFm.ascent) / 2f, textPaint)

    tuneCalibAddProfileRect.setEmpty()
    tuneCalibProfileRows.clear()
    if (tuneCalibProfilesExpanded) {
        val currentDisp = spec.getDisplayValue(item)
        val rawSaveValue = if (spec.isAutoActive(item)) "AUTO" else spec.getSliderPos(item).toString()
        val alreadySaved = presets.any { it.valueStr.trim() == rawSaveValue || it.valueStr.trim() == currentDisp }

        val addY = tuneCalibProfilesHeaderRect.bottom + 2f * dp
        tuneCalibAddProfileRect.set(profBoxRect.left + 8f * dp, addY, profBoxRect.right - 8f * dp, addY + addRowH)
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = if (alreadySaved) Color.argb(28, mutR, mutG, mutB) else colors.pillBg
        canvas.drawRoundRect(tuneCalibAddProfileRect, 6f * dp, 6f * dp, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 0.85f * dp
        highlightPaint.color = if (alreadySaved) mutedCardBorder else colors.pillBorder
        canvas.drawRoundRect(tuneCalibAddProfileRect, 6f * dp, 6f * dp, highlightPaint)

        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.textSize = 8.4f * dp
        textPaint.color = if (alreadySaved) colors.textMuted else colors.accentColor
        val adFm = textPaint.fontMetrics
        val addText = if (alreadySaved) "✓  Current Value '$currentDisp' Saved" else "+  Add '$currentDisp' as Profile"
        canvas.drawText(addText, tuneCalibAddProfileRect.centerX(), tuneCalibAddProfileRect.centerY() - (adFm.descent + adFm.ascent) / 2f, textPaint)

        var rY = tuneCalibAddProfileRect.bottom + 5f * dp
        if (presets.isEmpty()) {
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.typeface = Typeface.DEFAULT
            textPaint.textSize = 8.1f * dp
            textPaint.color = colors.textMuted
            canvas.drawText("No saved profiles yet.", profBoxRect.centerX(), rY + 10f * dp, textPaint)
        } else {
            for (preset in presets.take(maxVisibleProfiles)) {
                if (rY + profRowH > profBoxRect.bottom - 4f * dp) break
                val rowRect = RectF(profBoxRect.left + 8f * dp, rY, profBoxRect.right - 8f * dp, rY + profRowH)
                val delW = 24f * dp
                val delRect = RectF(rowRect.right - delW, rowRect.top, rowRect.right, rowRect.bottom)
                tuneCalibProfileRows += TuneProfileRowHit(preset, rowRect, delRect)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(32, mutR, mutG, mutB)
                canvas.drawRoundRect(rowRect, 6f * dp, 6f * dp, highlightPaint)

                val unitStr = if (preset.valueStr.equals("AUTO", ignoreCase = true) || preset.valueStr.endsWith(spec.unit)) {
                    preset.valueStr
                } else {
                    "${preset.valueStr}${spec.unit}"
                }
                val labelText = if (!preset.label.isNullOrBlank()) "$unitStr  •  ${preset.label}" else unitStr
                textPaint.textAlign = Paint.Align.LEFT
                textPaint.typeface = Typeface.MONOSPACE
                textPaint.textSize = 8.8f * dp
                textPaint.color = colors.accentColor
                val pFm = textPaint.fontMetrics
                canvas.drawText(labelText, rowRect.left + 9f * dp, rowRect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)

                textPaint.textAlign = Paint.Align.CENTER
                textPaint.typeface = Typeface.DEFAULT_BOLD
                textPaint.textSize = 9.0f * dp
                textPaint.color = Color.argb(215, 255, 130, 140)
                canvas.drawText("✕", delRect.centerX(), delRect.centerY() - (pFm.descent + pFm.ascent) / 2f, textPaint)

                rY += profRowH + 3f * dp
            }
        }
    }
}

internal fun LightspeedCruiseOverlay.handleTuneCalibrationTouch(
    event: MotionEvent,
    catId: String,
    item: CategoryHudItem
): Boolean {
    val targetId = tuneCalibSliderId ?: return false
    val spec = buildTuneSliderSpecs(item).firstOrNull { it.id == targetId } ?: run {
        closeTuneCalibrationModal()
        return true
    }

    fun commitBuffer() {
        val raw = tuneCalibTypedBuffer.trim()
        val parsed = raw.toIntOrNull()
        if (parsed != null) {
            spec.applySliderPos(item, parsed)
            applyLiveHudTuneItemChanges(catId, item)
        }
        tuneCalibKeypadOpen = false
    }

    if (event.actionMasked == MotionEvent.ACTION_DOWN) {
        val x = event.x
        val y = event.y
        val prefs = context.defaultPrefs()

        if (!tuneCalibModalRect.contains(x, y) || tuneCalibCloseBtnRect.contains(x, y)) {
            triggerHardwareHaptic(18, 75)
            closeTuneCalibrationModal()
            return true
        }

        if (tuneCalibApplyBtnRect.contains(x, y)) {
            triggerHardwareHaptic(22, 95)
            if (tuneCalibKeypadOpen) {
                commitBuffer()
            } else {
                tuneCalibKeypadOpen = true
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
            }
            invalidate()
            return true
        }

        if (tuneCalibTypeCardRect.contains(x, y)) {
            triggerHardwareHaptic(18, 80)
            tuneCalibKeypadOpen = !tuneCalibKeypadOpen
            if (tuneCalibKeypadOpen) {
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
            }
            invalidate()
            return true
        }

        if (tuneCalibFineMinusRect.contains(x, y)) {
            triggerHardwareHaptic(18, 85)
            spec.applySteppedDelta(item, -1)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibFinePlusRect.contains(x, y)) {
            triggerHardwareHaptic(18, 85)
            spec.applySteppedDelta(item, +1)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibKeypadOpen) {
            for ((key, rect) in tuneCalibKeypadBtnRects) {
                if (rect.contains(x, y)) {
                    triggerHardwareHaptic(16, 75)
                    when (key) {
                        "⌫" -> {
                            tuneCalibReplaceOnNextDigit = false
                            tuneCalibTypedBuffer = tuneCalibTypedBuffer.dropLast(1)
                            invalidate()
                        }
                        "✓" -> {
                            commitBuffer()
                            invalidate()
                        }
                        "AUTO" -> {
                            spec.applyAutoOrReset(item, width > height)
                            tuneCalibKeypadOpen = false
                            applyLiveHudTuneItemChanges(catId, item)
                        }
                        else -> {
                            if (tuneCalibReplaceOnNextDigit) {
                                tuneCalibTypedBuffer = key
                                tuneCalibReplaceOnNextDigit = false
                            } else if (tuneCalibTypedBuffer.length < 4) {
                                tuneCalibTypedBuffer = (tuneCalibTypedBuffer + key).trimStart('0').ifEmpty { key }
                            }
                            invalidate()
                        }
                    }
                    return true
                }
            }
        }

        if (tuneCalibTapToJumpRect.contains(x, y)) {
            triggerHardwareHaptic(20, 90)
            val key = LightspeedPreferences.keySliderTapToJump(spec.id)
            val current = prefs.getBoolean(key, false)
            prefs.edit().putBoolean(key, !current).apply()
            invalidate()
            return true
        }

        if (tuneCalibResetBtnRect.contains(x, y)) {
            triggerHardwareHaptic(28, 115)
            spec.applyAutoOrReset(item, width > height)
            tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
            tuneCalibReplaceOnNextDigit = true
            applyLiveHudTuneItemChanges(catId, item)
            return true
        }

        if (tuneCalibProfilesHeaderRect.contains(x, y)) {
            triggerHardwareHaptic(16, 75)
            tuneCalibProfilesExpanded = !tuneCalibProfilesExpanded
            invalidate()
            return true
        }

        if (tuneCalibProfilesExpanded && tuneCalibAddProfileRect.contains(x, y)) {
            val rawSaveValue = if (spec.isAutoActive(item)) "AUTO" else spec.getSliderPos(item).toString()
            val added = SliderPresetManager.addPreset(prefs, spec.id, null, rawSaveValue)
            triggerHardwareHaptic(if (added) 24 else 14, 90)
            invalidate()
            return true
        }

        for (hit in tuneCalibProfileRows) {
            if (hit.deleteRect.contains(x, y)) {
                triggerHardwareHaptic(18, 80)
                SliderPresetManager.removePreset(prefs, spec.id, hit.preset)
                invalidate()
                return true
            }
            if (hit.rowRect.contains(x, y)) {
                triggerHardwareHaptic(22, 95)
                val vStr = hit.preset.valueStr.trim()
                if (vStr.equals("AUTO", ignoreCase = true)) {
                    spec.applyAutoOrReset(item, width > height)
                } else {
                    val parsed = vStr.filter { it.isDigit() || it == '-' }.toIntOrNull()
                    if (parsed != null) {
                        spec.applySliderPos(item, parsed)
                    }
                }
                tuneCalibTypedBuffer = spec.getSliderPos(item).toString()
                tuneCalibReplaceOnNextDigit = true
                applyLiveHudTuneItemChanges(catId, item)
                return true
            }
        }
        return true
    }
    return true
}
