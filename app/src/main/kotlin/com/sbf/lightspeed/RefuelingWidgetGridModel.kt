package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Per-widget layout and visual tuning parameters for Refueling Bay & Home Stack cards.
 * Mirrors Category Cruise's 10-column skyline cell model and non-occluding live tuner metrics.
 */
data class RefuelingWidgetSpec(
    val widgetId: Int,
    val wCells: Int = 10,
    val hCells: Int = 4,
    val opacity: Int = RefuelingGridConstants.OPACITY_MAX,
    val cornerRadiusDp: Int = RefuelingGridConstants.RADIUS_AUTO,
    val padLeftDp: Int = 0,
    val padTopDp: Int = 0,
    val padRightDp: Int = 0,
    val padBottomDp: Int = 0
) {
    fun clamped(): RefuelingWidgetSpec = copy(
        wCells = wCells.coerceIn(RefuelingGridConstants.MIN_CELLS, RefuelingGridConstants.GRID_COLUMNS),
        hCells = hCells.coerceIn(RefuelingGridConstants.MIN_CELLS, RefuelingGridConstants.MAX_CELLS),
        opacity = opacity.coerceIn(RefuelingGridConstants.OPACITY_MIN, RefuelingGridConstants.OPACITY_MAX),
        cornerRadiusDp = if (cornerRadiusDp < 0) {
            RefuelingGridConstants.RADIUS_AUTO
        } else {
            cornerRadiusDp.coerceIn(0, RefuelingGridConstants.MAX_RADIUS_DP)
        },
        padLeftDp = padLeftDp.coerceIn(0, RefuelingGridConstants.MAX_PAD_DP),
        padTopDp = padTopDp.coerceIn(0, RefuelingGridConstants.MAX_PAD_DP),
        padRightDp = padRightDp.coerceIn(0, RefuelingGridConstants.MAX_PAD_DP),
        padBottomDp = padBottomDp.coerceIn(0, RefuelingGridConstants.MAX_PAD_DP)
    )
}

/**
 * Resolved 2D skyline placement rectangle (in Dp) for one Refueling Bay grid card.
 */
data class PlacedRefuelingWidget(
    val index: Int,
    val widgetId: Int,
    val spec: RefuelingWidgetSpec,
    val xDp: Float,
    val yDp: Float,
    val widthDp: Float,
    val heightDp: Float
)

/**
 * Full 10-column skyline grid layout result for [MultiWidgetContainer].
 */
data class RefuelingGridLayoutResult(
    val placements: List<PlacedRefuelingWidget>,
    val totalHeightDp: Float
)

/**
 * Shared constants and defaults for Refueling Bay 10-cell grid and card visual tuning.
 */
object RefuelingGridConstants {
    const val GRID_COLUMNS = 10
    const val MIN_CELLS = 1
    const val MAX_CELLS = 10
    const val CELL_HEIGHT_DP = 44f
    const val DEFAULT_GAP_DP = 8
    const val MAX_GAP_DP = 24
    const val OPACITY_MIN = 10
    const val OPACITY_MAX = 100
    const val RADIUS_AUTO = -1
    const val MAX_RADIUS_DP = 36
    const val MAX_PAD_DP = 32

    fun defaultSpec(widgetId: Int, isLandscape: Boolean): RefuelingWidgetSpec {
        return RefuelingWidgetSpec(
            widgetId = widgetId,
            wCells = if (isLandscape) 5 else 10,
            hCells = 4,
            opacity = OPACITY_MAX,
            cornerRadiusDp = RADIUS_AUTO
        )
    }
}

/**
 * Computes a 10-column 2D skyline masonry layout in Dp so Refueling Bay cards pack tightly
 * without gaps while preserving explicit 1..10 cell width and height overrides.
 */
fun computeRefuelingGridLayout(
    widgetIds: List<Int>,
    specsById: Map<Int, RefuelingWidgetSpec>,
    usableWidthDp: Float,
    gapDp: Float,
    isLandscape: Boolean
): RefuelingGridLayoutResult {
    if (widgetIds.isEmpty() || usableWidthDp <= 0f) {
        return RefuelingGridLayoutResult(placements = emptyList(), totalHeightDp = 0f)
    }

    val safeGap = gapDp.coerceIn(0f, RefuelingGridConstants.MAX_GAP_DP.toFloat())
    val cols = RefuelingGridConstants.GRID_COLUMNS
    val singleColW = ((usableWidthDp - (cols - 1) * safeGap) / cols).coerceAtLeast(8f)
    val colBottomY = FloatArray(cols) { 0f }
    val placements = ArrayList<PlacedRefuelingWidget>(widgetIds.size)

    widgetIds.forEachIndexed { index, widgetId ->
        val spec = (specsById[widgetId] ?: RefuelingGridConstants.defaultSpec(widgetId, isLandscape)).clamped()
        val w = spec.wCells
        val h = spec.hCells

        var bestCol = 0
        var bestY = Float.MAX_VALUE
        for (startCol in 0..(cols - w)) {
            var spanPeakY = 0f
            for (c in startCol until (startCol + w)) {
                if (colBottomY[c] > spanPeakY) spanPeakY = colBottomY[c]
            }
            if (spanPeakY < bestY) {
                bestY = spanPeakY
                bestCol = startCol
            }
        }

        val itemX = bestCol * (singleColW + safeGap)
        val itemW = (w * singleColW + (w - 1) * safeGap).coerceAtMost(usableWidthDp)
        val itemH = h * RefuelingGridConstants.CELL_HEIGHT_DP + (h - 1) * safeGap
        val nextBottom = bestY + itemH + safeGap
        for (c in bestCol until (bestCol + w)) {
            colBottomY[c] = nextBottom
        }

        placements += PlacedRefuelingWidget(
            index = index,
            widgetId = widgetId,
            spec = spec,
            xDp = itemX,
            yDp = bestY,
            widthDp = itemW,
            heightDp = itemH
        )
    }

    val maxBottom = colBottomY.maxOrNull() ?: 0f
    val totalHeightDp = (maxBottom - safeGap).coerceAtLeast(0f)
    return RefuelingGridLayoutResult(placements = placements, totalHeightDp = totalHeightDp)
}

/**
 * Persistent SharedPreferences store for Refueling Bay per-orientation 10-cell widget specs,
 * global grid spacing, and seamless migration from legacy X% / Ydp keys.
 */
object RefuelingWidgetSpecsStore {

    private fun specsKey(isLandscape: Boolean): String =
        if (isLandscape) {
            LightspeedPreferences.KEY_REFUELING_GRID_SPECS_LANDSCAPE
        } else {
            LightspeedPreferences.KEY_REFUELING_GRID_SPECS_PORTRAIT
        }

    fun getGridGapDp(context: Context): Int = getGridGapDp(context.defaultPrefs())

    fun getGridGapDp(prefs: SharedPreferences): Int =
        prefs.getInt(LightspeedPreferences.KEY_REFUELING_GRID_GAP_DP, RefuelingGridConstants.DEFAULT_GAP_DP)
            .coerceIn(0, RefuelingGridConstants.MAX_GAP_DP)

    fun setGridGapDp(prefs: SharedPreferences, gapDp: Int) {
        prefs.edit()
            .putInt(
                LightspeedPreferences.KEY_REFUELING_GRID_GAP_DP,
                gapDp.coerceIn(0, RefuelingGridConstants.MAX_GAP_DP)
            )
            .apply()
    }

    fun loadSpecs(
        prefs: SharedPreferences,
        widgetIds: List<Int>,
        isLandscape: Boolean
    ): Map<Int, RefuelingWidgetSpec> {
        val parsed = parseSpecsJson(prefs.getString(specsKey(isLandscape), null))
        var migratedAny = false
        val result = LinkedHashMap<Int, RefuelingWidgetSpec>(widgetIds.size + parsed.size)
        result.putAll(parsed)

        widgetIds.forEach { widgetId ->
            if (!result.containsKey(widgetId)) {
                val migrated = buildFromLegacyOrDefault(prefs, widgetId, isLandscape)
                result[widgetId] = migrated.first
                if (migrated.second) migratedAny = true
            }
        }

        if (migratedAny) {
            persistSpecsJson(prefs, isLandscape, result)
        }
        return result
    }

    fun saveSpec(
        prefs: SharedPreferences,
        isLandscape: Boolean,
        spec: RefuelingWidgetSpec
    ) {
        val clamped = spec.clamped()
        val map = parseSpecsJson(prefs.getString(specsKey(isLandscape), null)).toMutableMap()
        map[clamped.widgetId] = clamped
        persistSpecsJson(prefs, isLandscape, map)
    }

    fun resetSpec(
        prefs: SharedPreferences,
        widgetId: Int,
        isLandscape: Boolean
    ): RefuelingWidgetSpec {
        val def = RefuelingGridConstants.defaultSpec(widgetId, isLandscape)
        saveSpec(prefs, isLandscape, def)
        return def
    }

    fun removeSpec(
        prefs: SharedPreferences,
        widgetId: Int
    ) {
        listOf(false, true).forEach { landscape ->
            val map = parseSpecsJson(prefs.getString(specsKey(landscape), null)).toMutableMap()
            if (map.remove(widgetId) != null) {
                persistSpecsJson(prefs, landscape, map)
            }
        }
    }

    private fun buildFromLegacyOrDefault(
        prefs: SharedPreferences,
        widgetId: Int,
        isLandscape: Boolean
    ): Pair<RefuelingWidgetSpec, Boolean> {
        val orientPrefix = if (isLandscape) "land" else "port"
        val legacyWKey = "widget_width_pct_${orientPrefix}_$widgetId"
        val legacyHKey = "widget_height_${orientPrefix}_$widgetId"
        val hasLegacy = prefs.contains(legacyWKey) || prefs.contains(legacyHKey)
        if (!hasLegacy) {
            return RefuelingGridConstants.defaultSpec(widgetId, isLandscape) to false
        }

        val defaultWidthPct = if (isLandscape) 50 else 100
        val defaultHeightDp = if (isLandscape) 160 else 180
        val legacyW = prefs.getInt(legacyWKey, defaultWidthPct).coerceIn(20, 100)
        val legacyH = prefs.getInt(legacyHKey, defaultHeightDp).coerceIn(80, 600)
        val wCells = (legacyW / 10f).roundToInt().coerceIn(1, RefuelingGridConstants.GRID_COLUMNS)
        val hCells = (legacyH / RefuelingGridConstants.CELL_HEIGHT_DP).roundToInt()
            .coerceIn(1, RefuelingGridConstants.MAX_CELLS)

        return RefuelingWidgetSpec(
            widgetId = widgetId,
            wCells = wCells,
            hCells = hCells
        ).clamped() to true
    }

    private fun parseSpecsJson(raw: String?): Map<Int, RefuelingWidgetSpec> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val root = JSONObject(raw)
            val map = LinkedHashMap<Int, RefuelingWidgetSpec>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val keyStr = keys.next()
                val widgetId = keyStr.toIntOrNull() ?: continue
                val obj = root.optJSONObject(keyStr) ?: continue
                map[widgetId] = RefuelingWidgetSpec(
                    widgetId = widgetId,
                    wCells = obj.optInt("w", 10),
                    hCells = obj.optInt("h", 4),
                    opacity = obj.optInt("o", RefuelingGridConstants.OPACITY_MAX),
                    cornerRadiusDp = obj.optInt("r", RefuelingGridConstants.RADIUS_AUTO),
                    padLeftDp = obj.optInt("pl", 0),
                    padTopDp = obj.optInt("pt", 0),
                    padRightDp = obj.optInt("pr", 0),
                    padBottomDp = obj.optInt("pb", 0)
                ).clamped()
            }
            map
        } catch (e: Exception) {
            logSwallowed("RefuelingWidgetSpecsStore", "parse", e)
            emptyMap()
        }
    }

    private fun persistSpecsJson(
        prefs: SharedPreferences,
        isLandscape: Boolean,
        specs: Map<Int, RefuelingWidgetSpec>
    ) {
        val root = JSONObject()
        specs.values.forEach { rawSpec ->
            val spec = rawSpec.clamped()
            val obj = JSONObject().apply {
                put("w", spec.wCells)
                put("h", spec.hCells)
                put("o", spec.opacity)
                put("r", spec.cornerRadiusDp)
                put("pl", spec.padLeftDp)
                put("pt", spec.padTopDp)
                put("pr", spec.padRightDp)
                put("pb", spec.padBottomDp)
            }
            root.put(spec.widgetId.toString(), obj)
        }
        prefs.edit().putString(specsKey(isLandscape), root.toString()).apply()
    }
}
