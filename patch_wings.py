import sys

def patch_wing(file_path, is_left):
    with open(file_path, 'r') as f:
        content = f.read()

    # Add isCurrentlyTouched state
    if "var isCurrentlyTouched = false" not in content:
        content = content.replace("private var startRawX = 0f", "private var isCurrentlyTouched = false\n    private var startRawX = 0f")

    # In ACTION_DOWN, set isCurrentlyTouched = true and invalidate()
    content = content.replace("initialDominantAxis = \"NONE\"", "initialDominantAxis = \"NONE\"\n                isCurrentlyTouched = true\n                invalidate()")

    # In ACTION_UP / ACTION_CANCEL, set isCurrentlyTouched = false
    content = content.replace("isTwoStepDownwardScrub = false\n                isScrubbing = false", "isCurrentlyTouched = false\n                isTwoStepDownwardScrub = false\n                isScrubbing = false")

    if "MotionEvent.ACTION_UP -> {" in content:
        content = content.replace("MotionEvent.ACTION_UP -> {\n                uiHandler.removeCallbacks(holdRunnable)", "MotionEvent.ACTION_UP -> {\n                isCurrentlyTouched = false\n                invalidate()\n                uiHandler.removeCallbacks(holdRunnable)")

    if "MotionEvent.ACTION_CANCEL -> {" in content:
        content = content.replace("MotionEvent.ACTION_CANCEL -> {\n                uiHandler.removeCallbacks(holdRunnable)", "MotionEvent.ACTION_CANCEL -> {\n                isCurrentlyTouched = false\n                invalidate()\n                uiHandler.removeCallbacks(holdRunnable)")

    # Modify drawWingBlade signature
    content = content.replace("fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean) {", 
                              "fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean, zoneKey: String) {")

    # Modify drawWingBlade calls
    prefix = "LEFT" if is_left else "RIGHT"
    content = content.replace("drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded)", 
                              f'drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded, "{prefix}_TOP")')
    content = content.replace("drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded)", 
                              f'drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded, "{prefix}_CENTER")')
    content = content.replace("drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded)", 
                              f'drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded, "{prefix}_BOTTOM")')

    # Update resting mode logic to include the touch glow
    old_resting = """            } else {
                // Resting Mode: Tactical Glowing Laser Blade on the Left Edge
                val bladeW = minOf(bounds.width(), 6f * d)"""
                
    old_resting_right = """            } else {
                // Resting Mode: Tactical Glowing Laser Blade on the Right Edge
                val bladeW = minOf(bounds.width(), 6f * d)"""

    new_resting = f"""            }} else {{
                // Resting Mode & Touch Glow
                val isTouched = isCurrentlyTouched && activeZoneKey == zoneKey
                val isCenter = zoneKey.contains("CENTER")
                val baseW = minOf(bounds.width(), 6f * d)
                
                // When tapped/hovered, the central pill is thicker (14dp) than deflectors (10dp)
                val bladeW = if (isTouched) (if (isCenter) 14f * d else 10f * d) else baseW
                
                // Increase alpha significantly if touched for the glowing effect
                val finalAlpha = if (isTouched) 255 else alpha
"""
    if is_left:
        content = content.replace(old_resting, new_resting)
        content = content.replace("bladeRect = RectF(0f, bounds.top + 2f * d, bladeW, bounds.bottom - 2f * d)", 
                                  "bladeRect = RectF(0f, bounds.top + 2f * d, bladeW, bounds.bottom - 2f * d)")
    else:
        content = content.replace(old_resting_right, new_resting)
        # For right wing, the blade starts from the right edge
        content = content.replace("bladeRect = RectF(bounds.width() - bladeW, bounds.top + 2f * d, bounds.width(), bounds.bottom - 2f * d)",
                                  "bladeRect = RectF(bounds.width() - bladeW, bounds.top + 2f * d, bounds.width(), bounds.bottom - 2f * d)")

    # Replace Color.argb(alpha...) with Color.argb(finalAlpha...) in resting block
    content = content.replace("Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))", 
                              "Color.argb(finalAlpha, Color.red(color), Color.green(color), Color.blue(color))")
    content = content.replace("Color.argb((alpha * 0.9f).toInt(), 255, 255, 255)", 
                              "Color.argb((finalAlpha * 0.9f).toInt(), 255, 255, 255)")

    with open(file_path, 'w') as f:
        f.write(content)

patch_wing("app/src/main/kotlin/com/sbf/lightspeed/LightspeedLeftWingOverlay.kt", True)
patch_wing("app/src/main/kotlin/com/sbf/lightspeed/LightspeedRightWingOverlay.kt", False)

print("Wings patched via Python.")
