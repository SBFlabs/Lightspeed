import sys

# --- PATCH 1: LightspeedLeftWingOverlay.kt ---
file_left = "app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt"
with open(file_left, 'r') as f:
    c_left = f.read()

# 1. Add isCurrentlyTouched
c_left = c_left.replace("private var startRawX = 0f", "private var isCurrentlyTouched = false\n    private var startRawX = 0f")

# 2. Add to ACTION_DOWN
old_down = """                initialDominantAxis = "NONE"
                isScrubbing = false"""
new_down = """                initialDominantAxis = "NONE"
                isCurrentlyTouched = true
                invalidate()
                isScrubbing = false"""
c_left = c_left.replace(old_down, new_down)

# 3. Add to ACTION_UP
old_up = """            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(holdRunnable)"""
new_up = """            MotionEvent.ACTION_UP -> {
                isCurrentlyTouched = false
                invalidate()
                uiHandler.removeCallbacks(holdRunnable)"""
c_left = c_left.replace(old_up, new_up)

# 4. Add to ACTION_CANCEL
old_cancel = """            MotionEvent.ACTION_CANCEL -> {
                uiHandler.removeCallbacks(holdRunnable)"""
new_cancel = """            MotionEvent.ACTION_CANCEL -> {
                isCurrentlyTouched = false
                invalidate()
                uiHandler.removeCallbacks(holdRunnable)"""
c_left = c_left.replace(old_cancel, new_cancel)

# 5. Modify drawWingBlade
c_left = c_left.replace("fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean) {", 
                        "fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean, zoneKey: String) {")

c_left = c_left.replace("drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded)", 
                        'drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded, "LEFT_TOP")')
c_left = c_left.replace("drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded)", 
                        'drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded, "LEFT_CENTER")')
c_left = c_left.replace("drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded)", 
                        'drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded, "LEFT_BOTTOM")')

# 6. Glowing logic
old_resting = """            } else {
                // Resting Mode: Tactical Glowing Laser Blade on the Left Edge
                val bladeW = minOf(bounds.width(), 6f * d)
                val bladeRect = RectF(0f, bounds.top + 2f * d, bladeW, bounds.bottom - 2f * d)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)"""

new_resting = """            } else {
                // Resting Mode & Touch Glow
                val isTouched = isCurrentlyTouched && activeZoneKey == zoneKey
                val isCenter = zoneKey.contains("CENTER")
                val baseW = minOf(bounds.width(), 6f * d)
                val bladeW = if (isTouched) (if (isCenter) 14f * d else 10f * d) else baseW
                val finalAlpha = if (isTouched) 255 else alpha

                val bladeRect = RectF(0f, bounds.top + 2f * d, bladeW, bounds.bottom - 2f * d)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(finalAlpha, Color.red(color), Color.green(color), Color.blue(color))
                canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = Color.argb((finalAlpha * 0.9f).toInt(), 255, 255, 255)"""
c_left = c_left.replace(old_resting, new_resting)

with open(file_left, 'w') as f:
    f.write(c_left)



# --- PATCH 2: LightspeedCruiseOverlay.kt (Right Wing) ---
file_right = "app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt"
with open(file_right, 'r') as f:
    c_right = f.read()

# 1. Add trackingStateLocked flag modification
old_down_r = """                trackingStateLocked = false
                initialLeftSweepDistance = 0f"""
new_down_r = """                trackingStateLocked = false
                isCurrentlyTouched = true; invalidate()
                initialLeftSweepDistance = 0f"""
if "isCurrentlyTouched = true" not in c_right:
    c_right = c_right.replace(old_down_r, new_down_r)
    c_right = c_right.replace("private var trackingStateLocked = false", "private var trackingStateLocked = false\n    private var isCurrentlyTouched = false")

old_up_r = """            MotionEvent.ACTION_UP -> {
                val dx = rawX - touchDownRawX"""
new_up_r = """            MotionEvent.ACTION_UP -> {
                isCurrentlyTouched = false; invalidate()
                val dx = rawX - touchDownRawX"""
c_right = c_right.replace(old_up_r, new_up_r)

old_cancel_r = """                MotionEvent.ACTION_CANCEL -> { activeItem = null; invalidate(); return true }"""
new_cancel_r = """                MotionEvent.ACTION_CANCEL -> { isCurrentlyTouched = false; activeItem = null; invalidate(); return true }"""
c_right = c_right.replace(old_cancel_r, new_cancel_r)

# 2. Modify drawWingBlade signature
c_right = c_right.replace("fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean) {",
                          "fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean, targetZone: TouchZone) {")

c_right = c_right.replace("drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded)",
                          "drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded, TouchZone.TOP_EDGE)")
c_right = c_right.replace("drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded)",
                          "drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded, TouchZone.CENTER_CRUISE)")
c_right = c_right.replace("drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded)",
                          "drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded, TouchZone.BOTTOM_EDGE)")

# 3. Glowing logic right
old_resting_r = """                } else {
                    // Resting Mode: Tactical Glowing Laser Blade
                    val bladeW = minOf(bounds.width(), 6f * d)
                    val bladeRect = RectF(bounds.right - bladeW, bounds.top + 2f * d, bounds.right, bounds.bottom - 2f * d)

                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
                    canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1.2f * d
                    highlightPaint.color = Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)"""

new_resting_r = """                } else {
                    // Resting Mode & Touch Glow
                    val isTouched = isCurrentlyTouched && currentActiveZone == targetZone
                    val isCenter = targetZone == TouchZone.CENTER_CRUISE
                    val baseW = minOf(bounds.width(), 6f * d)
                    val bladeW = if (isTouched) (if (isCenter) 14f * d else 10f * d) else baseW
                    val finalAlpha = if (isTouched) 255 else alpha

                    val bladeRect = RectF(bounds.right - bladeW, bounds.top + 2f * d, bounds.right, bounds.bottom - 2f * d)

                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(finalAlpha, Color.red(color), Color.green(color), Color.blue(color))
                    canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1.2f * d
                    highlightPaint.color = Color.argb((finalAlpha * 0.9f).toInt(), 255, 255, 255)"""
c_right = c_right.replace(old_resting_r, new_resting_r)

with open(file_right, 'w') as f:
    f.write(c_right)

print("Both Wings Patched!")
