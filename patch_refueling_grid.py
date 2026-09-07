import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

if "import androidx.compose.foundation.layout.FlowRow" not in content:
    content = content.replace("import androidx.compose.foundation.layout.Row", "import androidx.compose.foundation.layout.Row\nimport androidx.compose.foundation.layout.FlowRow\nimport androidx.compose.foundation.layout.ExperimentalLayoutApi\nimport androidx.compose.foundation.rememberScrollState\nimport androidx.compose.foundation.verticalScroll")

# Find the block starting at MODE 2: CUSTOM DIMENSIONAL GRID
start_marker = "    } else {\n        // =========================================================================\n        // MODE 2: CUSTOM DIMENSIONAL GRID"
end_marker = "        }\n    }\n}\n\n/**"
start_idx = content.find(start_marker)
end_idx = content.find(end_marker, start_idx)

if start_idx == -1 or end_idx == -1:
    print("Could not find block.")
    sys.exit(1)

new_block = """    } else {
        // =========================================================================
        // MODE 2: CUSTOM DIMENSIONAL FLOW DASHBOARD (True Freeform X/Y Authority)
        // =========================================================================
        val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(activity)
        val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                widgetIds.forEachIndexed { index, widgetId ->
                    val wHeight = prefs.getInt("widget_height_$widgetId", 180)
                    val wWidthPct = prefs.getInt("widget_width_pct_$widgetId", if (isLandscape) 50 else 100)
                    
                    var currentWidthPct by remember { mutableIntStateOf(wWidthPct) }
                    var currentHeight by remember { mutableIntStateOf(wHeight) }
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(currentWidthPct / 100f)
                            .height(currentHeight.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(
                                1.dp,
                                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))),
                                RoundedCornerShape(18.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (appWidgetHost != null && appWidgetManager != null) {
                                AppWidgetContainerView(
                                    activity = activity,
                                    widgetId = widgetId,
                                    appWidgetHost = appWidgetHost,
                                    appWidgetManager = appWidgetManager,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp)
                                )
                            }

                            if (isEditMode) {
                                // X/Y Axis Authority Controls
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 6.dp)
                                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
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
                                        
                                        Text("Y:${currentHeight}", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        
                                        IconButton(onClick = { 
                                            val newH = (currentHeight + 20).coerceAtMost(600)
                                            prefs.edit().putInt("widget_height_$widgetId", newH).apply()
                                            currentHeight = newH
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.Add, contentDescription = "Increase Height", tint = Color.White) }
                                        
                                        Spacer(modifier = Modifier.width(4.dp))
                                        
                                        // X Axis (Width Pct)
                                        IconButton(onClick = { 
                                            val newW = (currentWidthPct - 10).coerceAtLeast(20)
                                            prefs.edit().putInt("widget_width_pct_$widgetId", newW).apply()
                                            currentWidthPct = newW
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White) }
                                        
                                        Text("X:${currentWidthPct}%", color = Color.Magenta, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        
                                        IconButton(onClick = { 
                                            val newW = (currentWidthPct + 10).coerceAtMost(100)
                                            prefs.edit().putInt("widget_width_pct_$widgetId", newW).apply()
                                            currentWidthPct = newW
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }
                                    }
                                }

                                TacticalWidgetEditControls(
                                    canMoveBack = index > 0,
                                    canMoveForward = index < widgetIds.size - 1,
                                    onMoveBack = { onReorderWidget(index, index - 1) },
                                    onMoveForward = { onReorderWidget(index, index + 1) },
                                    onRemove = { onRemoveWidget(widgetId) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                )
                            }
                        }
                    }
                }

                // Add Widget Card in Dashboard (Tactical Module Mount) - Fits naturally in flow row
                if (isEditMode || widgetIds.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPickWidget() }
                            .background(Color(0xFF080C14).copy(alpha = 0.6f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "[ MOUNT MODULE ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
"""

content = content[:start_idx] + new_block + content[end_idx:]

with open(file_path, 'w') as f:
    f.write(content)

print("FlowRow Grid Overhaul applied.")
