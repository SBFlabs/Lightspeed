import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

old_grid_start = """    } else {
        // =========================================================================
        // MODE 2: CUSTOM DIMENSIONAL GRID (X/Y Axis Authority)
        // =========================================================================
        val gridColumns = if (isLandscape) 4 else 2
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(activity)

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = widgetIds,
                key = { _, id -> id },
                span = { _, widgetId ->
                    val defaultSpan = if (isLandscape) 2 else 2
                    androidx.compose.foundation.lazy.grid.GridItemSpan(prefs.getInt("widget_span_$widgetId", defaultSpan).coerceIn(1, gridColumns))
                }
            ) { index, widgetId ->"""

# We must find the end of the itemsIndexed block and the item { } block.
