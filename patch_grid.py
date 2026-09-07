import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

# Replace the GridCells and span logic
old_grid_start = """    } else {
        // =========================================================================
        // MODE 2: ADAPTIVE GRID (1 Column Portrait, 2 Columns Landscape)
        // =========================================================================
        val gridColumns = if (isLandscape) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(widgetIds) { index, widgetId ->"""

new_grid_start = """    } else {
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
            ) { index, widgetId ->
                val wHeight = prefs.getInt("widget_height_$widgetId", 180)
                var currentSpan by remember { mutableIntStateOf(prefs.getInt("widget_span_$widgetId", if (isLandscape) 2 else 2).coerceIn(1, gridColumns)) }
                var currentHeight by remember { mutableIntStateOf(wHeight) }"""

content = content.replace(old_grid_start, new_grid_start)


# Update the Card modifier to use currentHeight
old_card = """                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)"""

new_card = """                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(currentHeight.dp)"""

content = content.replace(old_card, new_card)

# Inject dimensional edit controls inside isEditMode
old_edit_controls = """                        if (isEditMode) {
                            TacticalWidgetEditControls("""

new_edit_controls = """                        if (isEditMode) {
                            // X/Y Axis Authority Controls
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 6.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Y Axis (Height)
                                    IconButton(onClick = { 
                                        val newH = (currentHeight - 20).coerceAtLeast(80)
                                        prefs.edit().putInt("widget_height_$widgetId", newH).apply()
                                        currentHeight = newH
                                    }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.Remove, contentDescription = "Decrease Height", tint = Color.White) }
                                    
                                    Text("Y", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    
                                    IconButton(onClick = { 
                                        val newH = (currentHeight + 20).coerceAtMost(600)
                                        prefs.edit().putInt("widget_height_$widgetId", newH).apply()
                                        currentHeight = newH
                                    }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Increase Height", tint = Color.White) }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    // X Axis (Span)
                                    IconButton(onClick = { 
                                        val newS = (currentSpan - 1).coerceAtLeast(1)
                                        prefs.edit().putInt("widget_span_$widgetId", newS).apply()
                                        currentSpan = newS
                                    }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White) }
                                    
                                    Text("X", color = Color.Magenta, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    
                                    IconButton(onClick = { 
                                        val newS = (currentSpan + 1).coerceAtMost(gridColumns)
                                        prefs.edit().putInt("widget_span_$widgetId", newS).apply()
                                        currentSpan = newS
                                    }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }
                                }
                            }

                            TacticalWidgetEditControls("""

content = content.replace(old_edit_controls, new_edit_controls)

with open(file_path, 'w') as f:
    f.write(content)

print("Grid patched via Python.")
