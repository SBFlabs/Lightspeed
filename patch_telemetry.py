import sys

file_path = "app/src/main/kotlin/com/sbf/lightspeed/RefuelingBatteryTelemetry.kt"
with open(file_path, 'r') as f:
    content = f.read()

# Add imports if necessary for Path
if "import androidx.compose.ui.graphics.Path" not in content:
    content = content.replace("import androidx.compose.ui.graphics.Color", "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Path\nimport kotlinx.coroutines.delay")

# Add history state
history_code = """
    val history = remember { mutableStateListOf<Pair<Float, Float>>() }
    LaunchedEffect(isCharging) {
        while(isCharging) {
            history.add(Pair(wattage, batteryTempC))
            if (history.size > 50) {
                history.removeAt(0)
            }
            delay(3000)
        }
    }
    
    androidx.compose.foundation.layout.Box("""

content = content.replace("    androidx.compose.foundation.layout.Box(", history_code)

# Add drawing logic to Canvas
draw_code = """        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

            // --- DRAW BACKGROUND GRAPH ---
            if (history.size > 1) {
                val graphPath = Path()
                val innerRadius = radius * 0.7f
                val graphWidth = innerRadius * 1.5f
                val graphHeight = innerRadius * 1.0f
                val startX = center.x - graphWidth / 2f
                val bottomY = center.y + graphHeight / 2f

                val maxWattage = history.maxOfOrNull { it.first }?.coerceAtLeast(15f) ?: 15f
                val stepX = graphWidth / (50 - 1).coerceAtLeast(1)

                graphPath.moveTo(startX, bottomY - (history[0].first / maxWattage) * graphHeight)
                for (i in 1 until history.size) {
                    val x = startX + i * stepX
                    val y = bottomY - (history[i].first / maxWattage) * graphHeight
                    graphPath.lineTo(x, y)
                }
                
                drawPath(
                    path = graphPath,
                    color = dynamicArcColor.copy(alpha = 0.2f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            // -----------------------------
"""

content = content.replace("""        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)""", draw_code)

with open(file_path, 'w') as f:
    f.write(content)

print("Graph patched via Python.")
