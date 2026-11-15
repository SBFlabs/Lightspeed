package com.sbf.lightspeed

import android.content.Context
import android.graphics.RectF
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.defaultPrefs

/**
 * Data model for a dynamic, freely resizable (1x1 to 10x10) widget module
 * embedded in the Category Cruise Avionics HUD.
 *
 * Every field below is persisted PER INSTANCE and PER CATEGORY inside the item token
 * (see [CategoryHudManager.saveHudItems]), so two widgets of the same type are fully independent.
 */
data class CategoryHudItem(
    val id: String,
    val type: String,
    var widgetId: Int = -1,
    var wCells: Int = 10, // 1..10 (10 = 100% full row width)
    var hCells: Int = 3,  // 1..10 (~36dp per row cell)
    var opacity: Int = 100, // 10..100% (100 = full opacity)
    var cornerRadiusDp: Int = -1, // -1 = use group default; 0..36 = custom dp
    var padLeftDp: Int = 0, // -24..+24 dp
    var padTopDp: Int = 0,
    var padRightDp: Int = 0,
    var padBottomDp: Int = 0,
    var scope: Int = CategoryHudManager.defaultScopeForType(type), // where this widget shows (see CategoryHudManager.SCOPE_*)
    var slotIndex: Int = 1, // 1-based position in the saved category list (stable even when scope-filtered)
    var titleFontSizeSp: Int = 0, // 0 = Auto; 10..24 = custom sp for primary title
    var subtitleFontSizeSp: Int = 0, // 0 = Auto; 7..16 = custom sp for subtitle
    var fontFamily: Int = 0, // 0 = Sans Bold, 1 = Monospace, 2 = Serif, 3 = Condensed
    var fontKey: String = "", // DeviceFontScanner key or file path; "" falls back to fontFamily
    val bounds: RectF = RectF()
)

object CategoryHudManager {

    /** Visible in both Category Cruise and the pinned App Grid. */
    const val SCOPE_BOTH = 0
    /** Visible only while cruising the category list. */
    const val SCOPE_CRUISE = 1
    /** Visible only on the pinned App Grid. */
    const val SCOPE_GRID = 2

    const val MAX_CELLS = 10
    const val OPACITY_MIN = 10
    const val OPACITY_MAX = 100
    const val RADIUS_MAX_DP = 36
    const val PAD_MAX_DP = 32

    /** Keep at most one Focused App Inspector (`app_and_cat`) per category. */
    private fun deduplicateInspectors(items: MutableList<CategoryHudItem>): Pair<MutableList<CategoryHudItem>, Boolean> {
        var seenInspector = false
        var changed = false
        val out = mutableListOf<CategoryHudItem>()
        for (item in items) {
            if (item.type == LightspeedCruiseHudPods.MODE_APP_AND_CAT) {
                if (seenInspector) {
                    changed = true
                    continue
                }
                seenInspector = true
            }
            out.add(item)
        }
        return out to changed
    }

    /** True when [item] should render for the given layer (edit mode always shows everything). */
    fun isVisibleInLayer(item: CategoryHudItem, isCruiseLayer: Boolean): Boolean = when (item.scope) {
        SCOPE_CRUISE -> isCruiseLayer
        SCOPE_GRID -> !isCruiseLayer
        else -> true
    }

    /**
     * Parse items from comma-delimited persistence token:
     * Format: "type[:widgetId]:w:h:opacity:radius:padL:padT:padR:padB:scope"
     * Tokens written by older builds may lack trailing fields; [legacyInsideOnly] seeds the scope for those.
     */
    fun parseItems(raw: String, legacyInsideOnly: Boolean = false): MutableList<CategoryHudItem> {
        val list = mutableListOf<CategoryHudItem>()
        if (raw.isBlank()) return list
        val tokens = raw.split(",")
        for ((index, token) in tokens.withIndex()) {
            val parts = token.trim().split(":")
            if (parts.isEmpty()) continue
            val type = LightspeedCruiseHudPods.canonicalHudType(parts[0])
            if (type.isBlank()) continue

            var widgetId = -1
            var wCells = 10
            var hCells = 3
            var opacity = 100
            var cornerRadiusDp = -1
            var padLeftDp = 0
            var padTopDp = 0
            var padRightDp = 0
            var padBottomDp = 0
            var scope = if (legacyInsideOnly) SCOPE_GRID else defaultScopeForType(type)
            var titleFontSizeSp = 0
            var subtitleFontSizeSp = 0
            var fontFamily = 0
            var fontKey = ""

            if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) {
                // Format: app_widget:widgetId:w:h[:opacity:radius:padL:padT:padR:padB:scope:titleSp:subSp:fontFam:fontKey]
                widgetId = parts.getOrNull(1)?.toIntOrNull() ?: -1
                wCells = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 10) ?: 10
                hCells = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(1, 10) ?: 4
                opacity = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(10, 100) ?: 100
                cornerRadiusDp = parts.getOrNull(5)?.toIntOrNull()?.coerceIn(-1, 36) ?: -1
                padLeftDp = parts.getOrNull(6)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padTopDp = parts.getOrNull(7)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padRightDp = parts.getOrNull(8)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padBottomDp = parts.getOrNull(9)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                parts.getOrNull(10)?.toIntOrNull()?.let { scope = it.coerceIn(SCOPE_BOTH, SCOPE_GRID) }
                titleFontSizeSp = parts.getOrNull(11)?.toIntOrNull()?.coerceIn(0, 24) ?: 0
                subtitleFontSizeSp = parts.getOrNull(12)?.toIntOrNull()?.coerceIn(0, 16) ?: 0
                fontFamily = parts.getOrNull(13)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
                fontKey = parts.getOrNull(14)?.replace("~c~", ":")?.replace("~m~", ",") ?: ""
            } else {
                // Format: type:w:h[:opacity:radius:padL:padT:padR:padB:scope:titleSp:subSp:fontFam:fontKey]
                wCells = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 10) ?: 10
                hCells = parts.getOrNull(2)?.toIntOrNull()?.coerceIn(1, 10) ?: if (
                    type == LightspeedCruiseHudPods.MODE_APP_ONLY || type == LightspeedCruiseHudPods.MODE_APP_AND_CAT
                ) 1 else 3
                opacity = parts.getOrNull(3)?.toIntOrNull()?.coerceIn(10, 100) ?: 100
                cornerRadiusDp = parts.getOrNull(4)?.toIntOrNull()?.coerceIn(-1, 36) ?: -1
                padLeftDp = parts.getOrNull(5)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padTopDp = parts.getOrNull(6)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padRightDp = parts.getOrNull(7)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                padBottomDp = parts.getOrNull(8)?.toIntOrNull()?.coerceIn(0, PAD_MAX_DP) ?: 0
                parts.getOrNull(9)?.toIntOrNull()?.let { scope = it.coerceIn(SCOPE_BOTH, SCOPE_GRID) }
                titleFontSizeSp = parts.getOrNull(10)?.toIntOrNull()?.coerceIn(0, 24) ?: 0
                subtitleFontSizeSp = parts.getOrNull(11)?.toIntOrNull()?.coerceIn(0, 16) ?: 0
                fontFamily = parts.getOrNull(12)?.toIntOrNull()?.coerceIn(0, 3) ?: 0
                fontKey = parts.getOrNull(13)?.replace("~c~", ":")?.replace("~m~", ",") ?: ""
            }

            val id = if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) {
                "widget_${widgetId}_$index"
            } else {
                "${type}_$index"
            }

            list.add(
                CategoryHudItem(
                    id = id,
                    type = type,
                    widgetId = widgetId,
                    wCells = wCells,
                    hCells = hCells,
                    opacity = opacity,
                    cornerRadiusDp = cornerRadiusDp,
                    padLeftDp = padLeftDp,
                    padTopDp = padTopDp,
                    padRightDp = padRightDp,
                    padBottomDp = padBottomDp,
                    scope = scope,
                    slotIndex = index + 1,
                    titleFontSizeSp = titleFontSizeSp,
                    subtitleFontSizeSp = subtitleFontSizeSp,
                    fontFamily = fontFamily,
                    fontKey = fontKey
                )
            )
        }
        return list
    }

    /**
     * Retrieve all HUD items for a category with backward-compatible fallback to slot1/slot2.
     */
    fun getHudItems(context: Context, categoryId: String, isLandscape: Boolean = false): MutableList<CategoryHudItem> {
        val prefs = context.defaultPrefs()
        val raw = prefs.getString(LightspeedPreferences.KEY_CATEGORY_HUD_ITEMS_PREFIX + categoryId, null)
        if (!raw.isNullOrBlank()) {
            val parsed = parseItems(raw, LightspeedCruiseHudPods.isHudInsideCategoryOnly(context, categoryId))
            if (parsed.isNotEmpty()) {
                val widgetCounts = parsed.filter { it.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && it.widgetId > 0 }
                    .groupingBy { it.widgetId }
                    .eachCount()
                val hasDuplicateWidgets = widgetCounts.any { it.value > 1 }

                if (hasDuplicateWidgets) {
                    val seenWidgets = mutableSetOf<Int>()
                    val healed = mutableListOf<CategoryHudItem>()
                    for ((index, item) in parsed.withIndex()) {
                        if (item.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && item.widgetId > 0) {
                            if (widgetCounts[item.widgetId]!! > 1 && index == 0) {
                                // Slot 1 was overwritten by duplicate widget ID from previous bug; restore built-in Screen Time
                                healed.add(
                                    item.copy(
                                        id = "${LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME}_0",
                                        type = LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME,
                                        widgetId = -1,
                                        hCells = 3
                                    )
                                )
                                continue
                            }
                            if (item.widgetId in seenWidgets) {
                                continue
                            }
                            seenWidgets.add(item.widgetId)
                        }
                        healed.add(item)
                    }
                    val deduped = deduplicateInspectors(healed)
                    saveHudItems(context, categoryId, deduped.first)
                    return deduped.first
                }
                val deduped = deduplicateInspectors(parsed)
                if (deduped.second) {
                    saveHudItems(context, categoryId, deduped.first)
                }
                return deduped.first
            }
        }

        // Backward-compatibility fallback from Slot 1 & Slot 2
        val slot1Mode = LightspeedCruiseHudPods.getCategoryHudMode(context, categoryId)
        val slot2Mode = LightspeedCruiseHudPods.getCategoryHudSlot2Mode(context, categoryId)
        val list = mutableListOf<CategoryHudItem>()

        if (slot1Mode != LightspeedCruiseHudPods.MODE_EMPTY) {
            val wId = prefs.getInt(LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + categoryId, -1)
            val defaultW = if (slot2Mode != LightspeedCruiseHudPods.MODE_EMPTY && isLandscape) 5 else 10
            val defaultH = if (slot1Mode == LightspeedCruiseHudPods.MODE_APP_ONLY || slot1Mode == LightspeedCruiseHudPods.MODE_APP_AND_CAT) 1 else 3
            list.add(
                CategoryHudItem(
                    id = if (slot1Mode == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) "widget_${wId}_0" else "${slot1Mode}_0",
                    type = slot1Mode,
                    widgetId = wId,
                    wCells = defaultW,
                    hCells = defaultH,
                    scope = defaultScopeForType(slot1Mode)
                )
            )
        }

        if (slot2Mode != LightspeedCruiseHudPods.MODE_EMPTY) {
            val wId = prefs.getInt(LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + categoryId, -1)
            val defaultW = if (isLandscape) 5 else 10
            list.add(
                CategoryHudItem(
                    id = if (slot2Mode == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) "widget_${wId}_1" else "${slot2Mode}_1",
                    type = slot2Mode,
                    widgetId = wId,
                    wCells = defaultW,
                    hCells = 3,
                    scope = defaultScopeForType(slot2Mode),
                    slotIndex = 2
                )
            )
        }

        return list
    }

    /**
     * Persist HUD items to SharedPreferences and synchronize legacy slot1/slot2 keys.
     */
    fun saveHudItems(context: Context, categoryId: String, items: List<CategoryHudItem>) {
        items.forEachIndexed { index, item -> item.slotIndex = index + 1 }
        val encoded = items.joinToString(",") { item ->
            val safeFontKey = item.fontKey.replace(":", "~c~").replace(",", "~m~")
            if (item.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) {
                "${item.type}:${item.widgetId}:${item.wCells}:${item.hCells}:${item.opacity}:${item.cornerRadiusDp}:${item.padLeftDp}:${item.padTopDp}:${item.padRightDp}:${item.padBottomDp}:${item.scope}:${item.titleFontSizeSp}:${item.subtitleFontSizeSp}:${item.fontFamily}:$safeFontKey"
            } else {
                "${item.type}:${item.wCells}:${item.hCells}:${item.opacity}:${item.cornerRadiusDp}:${item.padLeftDp}:${item.padTopDp}:${item.padRightDp}:${item.padBottomDp}:${item.scope}:${item.titleFontSizeSp}:${item.subtitleFontSizeSp}:${item.fontFamily}:$safeFontKey"
            }
        }

        val prefs = context.defaultPrefs()
        prefs.edit()
            .putString(LightspeedPreferences.KEY_CATEGORY_HUD_ITEMS_PREFIX + categoryId, encoded)
            .apply()

        // Sync legacy slot 1 & slot 2 for backward compatibility
        val first = items.firstOrNull()
        val second = items.getOrNull(1)

        if (first != null) {
            LightspeedCruiseHudPods.setCategoryHudMode(context, categoryId, first.type)
            if (first.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && first.widgetId > 0) {
                prefs.edit().putInt(LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + categoryId, first.widgetId).apply()
            }
        } else {
            LightspeedCruiseHudPods.setCategoryHudMode(context, categoryId, LightspeedCruiseHudPods.MODE_EMPTY)
        }

        if (second != null) {
            LightspeedCruiseHudPods.setCategoryHudSlot2Mode(context, categoryId, second.type)
            if (second.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && second.widgetId > 0) {
                prefs.edit().putInt(LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + categoryId, second.widgetId).apply()
            }
        } else {
            LightspeedCruiseHudPods.setCategoryHudSlot2Mode(context, categoryId, LightspeedCruiseHudPods.MODE_EMPTY)
        }
    }

    /**
     * Append a new widget to category HUD.
     */
    /**
     * Default visibility scope for Category Cruise widgets:
     * only Focus Inspector appears in both App Grid and Category Cruise Layer by default;
     * all other tactical HUD pods default to App Grid only.
     */
    fun defaultScopeForType(type: String): Int = when (type) {
        LightspeedCruiseHudPods.MODE_APP_AND_CAT,
        LightspeedCruiseHudPods.MODE_APP_ONLY -> SCOPE_BOTH
        else -> SCOPE_GRID
    }

    private fun seedDistinctDuplicateSlotView(
        context: Context,
        categoryId: String,
        type: String,
        newSlotIndex: Int,
        existingItems: List<CategoryHudItem>
    ) {
        if (newSlotIndex <= 1) return
        when (type) {
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> {
                val existing = existingItems.filter { it.type == LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM }
                if (existing.isEmpty()) return
                val usedModes = existing.map {
                    LightspeedCruiseHudPods.getBatteryMode(context, categoryId, it.slotIndex)
                }.toSet()
                val nextMode = LightspeedCruiseHudPods.BATTERY_MODE_OPTIONS
                    .map { it.first }
                    .firstOrNull { it !in usedModes }
                    ?: LightspeedCruiseHudPods.BATTERY_MODE_OPTIONS[existing.size % LightspeedCruiseHudPods.BATTERY_MODE_OPTIONS.size].first
                LightspeedCruiseHudPods.setBatteryMode(context, categoryId, nextMode, newSlotIndex)
            }
            LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> {
                val existing = existingItems.filter { it.type == LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME }
                if (existing.isEmpty()) return
                val usedSpans = existing.map {
                    LightspeedCruiseHudPods.getTimeSpan(context, categoryId, it.slotIndex)
                }.toSet()
                val nextSpan = LightspeedCruiseHudPods.TIME_SPAN_OPTIONS
                    .map { it.first }
                    .firstOrNull { it !in usedSpans }
                    ?: LightspeedCruiseHudPods.TIME_SPAN_OPTIONS[existing.size % LightspeedCruiseHudPods.TIME_SPAN_OPTIONS.size].first
                LightspeedCruiseHudPods.setTimeSpan(context, categoryId, nextSpan, newSlotIndex)
            }
        }
    }

    fun addHudItem(context: Context, categoryId: String, type: String, widgetId: Int = -1, isLandscape: Boolean = false) {
        val current = getHudItems(context, categoryId, isLandscape)
        val newSlotIndex = current.size + 1
        val defaultW = if (isLandscape) 5 else 10
        val defaultH = when (type) {
            LightspeedCruiseHudPods.MODE_APP_AND_CAT -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 2 else 1
            LightspeedCruiseHudPods.MODE_ANDROID_WIDGET -> 4
            else -> 3
        }
        val id = if (type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET) "widget_${widgetId}_${System.currentTimeMillis()}" else "${type}_${System.currentTimeMillis()}"
        current.add(
            CategoryHudItem(
                id = id,
                type = type,
                widgetId = widgetId,
                wCells = defaultW,
                hCells = defaultH,
                scope = defaultScopeForType(type),
                slotIndex = newSlotIndex
            )
        )
        saveHudItems(context, categoryId, current)
        seedDistinctDuplicateSlotView(context, categoryId, type, newSlotIndex, current.dropLast(1))
    }

    /**
     * Bind a newly picked Android App Widget to an existing unbound placeholder, specific slot, or append a new item.
     */
    fun bindOrAddHudWidget(
        context: Context,
        categoryId: String,
        widgetId: Int,
        isLandscape: Boolean = false,
        targetSlotIndex: Int = -1
    ) {
        val current = getHudItems(context, categoryId, isLandscape)
        if (targetSlotIndex > 0 && targetSlotIndex <= current.size) {
            val idx = targetSlotIndex - 1
            val old = current[idx]
            current[idx] = old.copy(
                id = "widget_${widgetId}_${System.currentTimeMillis()}",
                type = LightspeedCruiseHudPods.MODE_ANDROID_WIDGET,
                widgetId = widgetId
            )
        } else {
            val unboundIndex = current.indexOfFirst { it.type == LightspeedCruiseHudPods.MODE_ANDROID_WIDGET && it.widgetId <= 0 }
            if (unboundIndex >= 0) {
                val old = current[unboundIndex]
                current[unboundIndex] = old.copy(
                    id = "widget_${widgetId}_${System.currentTimeMillis()}",
                    widgetId = widgetId
                )
            } else {
                val defaultW = if (isLandscape) 5 else 10
                val defaultH = 4
                current.add(
                    CategoryHudItem(
                        id = "widget_${widgetId}_${System.currentTimeMillis()}",
                        type = LightspeedCruiseHudPods.MODE_ANDROID_WIDGET,
                        widgetId = widgetId,
                        wCells = defaultW,
                        hCells = defaultH,
                        scope = defaultScopeForType(LightspeedCruiseHudPods.MODE_ANDROID_WIDGET)
                    )
                )
            }
        }
        saveHudItems(context, categoryId, current)
    }

    /**
     * Moves [itemId] from [sourceCategoryId] to [targetCategoryId], preserving all widget layout,
     * font, visibility scope, and per-slot telemetry view settings under the assigned slot index.
     */
    fun moveHudItemToCategory(
        context: Context,
        sourceCategoryId: String,
        targetCategoryId: String,
        itemId: String,
        isLandscape: Boolean = false
    ): CategoryHudItem? {
        if (sourceCategoryId.isBlank() || targetCategoryId.isBlank() || sourceCategoryId == targetCategoryId) {
            return null
        }
        val sourceItems = getHudItems(context, sourceCategoryId, isLandscape)
        val sourceIdx = sourceItems.indexOfFirst { it.id == itemId }
        if (sourceIdx < 0) return null

        val sourceItem = sourceItems.removeAt(sourceIdx)
        val oldSlotIndex = sourceItem.slotIndex
        saveHudItems(context, sourceCategoryId, sourceItems)

        val targetItems = getHudItems(context, targetCategoryId, isLandscape)
        val newSlotIndex = targetItems.size + 1
        val movedItem = sourceItem.copy(
            bounds = RectF(),
            slotIndex = newSlotIndex
        )
        targetItems.add(movedItem)
        saveHudItems(context, targetCategoryId, targetItems)

        migrateSlotViewPrefs(context, sourceCategoryId, oldSlotIndex, targetCategoryId, newSlotIndex, movedItem.type)
        return movedItem
    }

    private fun migrateSlotViewPrefs(
        context: Context,
        sourceCategoryId: String,
        oldSlotIndex: Int,
        targetCategoryId: String,
        newSlotIndex: Int,
        type: String
    ) {
        when (type) {
            LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> {
                val mode = LightspeedCruiseHudPods.getBatteryMode(context, sourceCategoryId, oldSlotIndex)
                val span = LightspeedCruiseHudPods.getBatterySpan(context, sourceCategoryId, oldSlotIndex)
                LightspeedCruiseHudPods.setBatteryMode(context, targetCategoryId, mode, newSlotIndex)
                LightspeedCruiseHudPods.setBatterySpan(context, targetCategoryId, span, newSlotIndex)
            }
            LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME -> {
                val span = LightspeedCruiseHudPods.getTimeSpan(context, sourceCategoryId, oldSlotIndex)
                val scope = LightspeedCruiseHudPods.getTimeScope(context, sourceCategoryId, oldSlotIndex)
                LightspeedCruiseHudPods.setTimeSpan(context, targetCategoryId, span, newSlotIndex)
                LightspeedCruiseHudPods.setTimeScope(context, targetCategoryId, scope, newSlotIndex)
            }
            LightspeedCruiseHudPods.MODE_LS_PRIVACY -> {
                val view = LightspeedCruiseHudPods.getPrivacyView(context, sourceCategoryId, oldSlotIndex)
                LightspeedCruiseHudPods.setPrivacyView(context, targetCategoryId, view, newSlotIndex)
            }
        }
    }

    /**
     * Compute multi-row 2D masonry bin-packed responsive layout for all HUD items within 10-column grid space.
     * Allows taller widgets (e.g. 8 cells) to occupy their column while adjacent shorter widgets (e.g. two 4-cell widgets)
     * stack cleanly in their own column without creating dead empty space below the shorter widget.
     */
    fun computeLayout(
        items: List<CategoryHudItem>,
        screenW: Float,
        topY: Float,
        dp: Float,
        scale: Float,
        isLandscape: Boolean,
        gapDp: Float = 8f,
        isFarHalfSplit: Boolean = false,
        isOpenedFromLeftFlank: Boolean = false
    ): RectF {
        val overallBounds = RectF(0f, 0f, 0f, 0f)
        if (items.isEmpty()) return overallBounds

        val isTablet = (screenW / dp) >= 600f
        val marginX = if (isTablet) 28f * dp else if (isLandscape) 20f * dp else 16f * dp
        val usableW = if (isFarHalfSplit) {
            (screenW * 0.5f) - (marginX * 2f)
        } else {
            screenW - (marginX * 2f)
        }
        val startX = if (isFarHalfSplit) {
            if (isOpenedFromLeftFlank) (screenW * 0.5f) + marginX else marginX
        } else {
            marginX
        }
        val gap = gapDp.coerceIn(0f, 24f) * dp
        val cellH = 46f * dp * scale
        val singleColW = (usableW - (9f * gap)) / 10f

        // Column bottom trackers for each of the 10 grid columns (0..9)
        val colBottomY = FloatArray(10) { topY }

        for (item in items) {
            val wCols = item.wCells.coerceIn(1, 10)
            val itemW = (wCols * singleColW + ((wCols - 1) * gap)).coerceAtLeast(30f * dp)
            val minCells = when (item.type) {
                LightspeedCruiseHudPods.MODE_LS_BATTERY_SYSTEM -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 3 else 1
                LightspeedCruiseHudPods.MODE_LS_SCREEN_TIME,
                LightspeedCruiseHudPods.MODE_APP_AND_CAT -> if (NightlyFeatures.isCategoryCruiseHudWidgetsEnabled) 2 else 1
                else -> 1
            }
            val itemH = (item.hCells.coerceAtLeast(minCells) * cellH).coerceAtLeast(40f * dp)

            // Find best starting column (0 .. 10 - wCols) that yields the minimum top Y position.
            // In case of ties, choose the leftmost column.
            var bestStartCol = 0
            var bestY = Float.MAX_VALUE

            for (startCol in 0..(10 - wCols)) {
                var maxBottomInRange = 0f
                for (c in startCol until (startCol + wCols)) {
                    if (colBottomY[c] > maxBottomInRange) {
                        maxBottomInRange = colBottomY[c]
                    }
                }
                if (maxBottomInRange < bestY) {
                    bestY = maxBottomInRange
                    bestStartCol = startCol
                }
            }

            // Calculate item placement
            val itemX = startX + (bestStartCol * (singleColW + gap))
            val itemY = bestY

            item.bounds.set(itemX, itemY, itemX + itemW, itemY + itemH)

            // Update column bottoms for all columns occupied by this item
            val newBottom = itemY + itemH + gap
            for (c in bestStartCol until (bestStartCol + wCols)) {
                colBottomY[c] = newBottom
            }
        }

        val firstTop = items.minOfOrNull { it.bounds.top } ?: topY
        val lastBottom = items.maxOfOrNull { it.bounds.bottom } ?: topY
        overallBounds.set(startX, firstTop, startX + usableW, lastBottom)
        return overallBounds
    }
}
