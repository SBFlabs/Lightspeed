import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingWidgets.kt"
with open(file_path, 'r') as f:
    content = f.read()

# 1. Fix ScrollableAppWidgetContainer logic to better protect widget scrolling
old_scroll_logic = """            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                if (!isVerticalScroll && !isHorizontalScroll) {
                    if (dy > dx && dy > touchSlop) {
                        isVerticalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dx > dy && dx > touchSlop) {
                        isHorizontalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                } else if (isVerticalScroll) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else if (isHorizontalScroll) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
            }"""

new_scroll_logic = """            MotionEvent.ACTION_MOVE -> {
                val dx = abs(ev.x - startX)
                val dy = abs(ev.y - startY)
                val earlySlop = touchSlop / 3f
                if (!isVerticalScroll && !isHorizontalScroll) {
                    if (dy > dx && dy > earlySlop) {
                        isVerticalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else if (dx > dy && dx > earlySlop) {
                        isHorizontalScroll = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }"""
content = content.replace(old_scroll_logic, new_scroll_logic)


# 2. Fix Orientation-independent Width Persistence
old_width_logic = """                    val wHeight = prefs.getInt("widget_height_$widgetId", 180)
                    val wWidthPct = prefs.getInt("widget_width_pct_$widgetId", if (isLandscape) 50 else 100)
                    
                    var currentWidthPct by remember { mutableIntStateOf(wWidthPct) }"""

new_width_logic = """                    val orientationPrefix = if (isLandscape) "land" else "port"
                    val wHeight = prefs.getInt("widget_height_$widgetId", 180)
                    val wWidthPct = prefs.getInt("widget_width_pct_${orientationPrefix}_$widgetId", if (isLandscape) 50 else 100)
                    
                    var currentWidthPct by remember { mutableIntStateOf(wWidthPct) }"""
content = content.replace(old_width_logic, new_width_logic)


old_width_click = """                                        // X Axis (Width Pct)
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
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }"""

new_width_click = """                                        // X Axis (Width Pct)
                                        IconButton(onClick = { 
                                            val newW = (currentWidthPct - 10).coerceAtLeast(20)
                                            prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                            currentWidthPct = newW
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Decrease Width", tint = Color.White) }
                                        
                                        Text("X:${currentWidthPct}%", color = Color.Magenta, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        
                                        IconButton(onClick = { 
                                            val newW = (currentWidthPct + 10).coerceAtMost(100)
                                            prefs.edit().putInt("widget_width_pct_${orientationPrefix}_$widgetId", newW).apply()
                                            currentWidthPct = newW
                                        }, modifier = Modifier.size(24.dp)) { androidx.compose.material3.Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Increase Width", tint = Color.White) }"""
content = content.replace(old_width_click, new_width_click)


with open(file_path, 'w') as f:
    f.write(content)

print("Widgets patched via Python.")
