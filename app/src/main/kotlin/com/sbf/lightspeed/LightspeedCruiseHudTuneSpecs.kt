package com.sbf.lightspeed

import android.graphics.Color
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.NightlyFeatures

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
    refreshHudAndAppGridLayout()
    hudTuneTargetItem = activeCategoryHudItems.firstOrNull { it.id == item.id } ?: item
}
