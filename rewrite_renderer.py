import re

file_path = "app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedDeflectorRenderer.kt"
with open(file_path, "r") as f:
    content = f.read()

# 1. Update method signature
content = content.replace(
    "useM3Color: Boolean = true,",
    "useM3Color: Boolean = true,\n        pillStyle: String = \"anchored_glow\","
)

pill_logic = """
        // =========================================================================
        // 1. CENTRAL PILL
        // =========================================================================
        if (isGlowEnabled) {
            val isTouched = isCurrentlyTouched && activeZoneIsCenter

            // Base dimensions
            val baseW = if (visualWidthPx > 0f) visualWidthPx else minOf(centerTouchBounds.width(), 6f * d)
            val pillTop = centerTouchBounds.top
            val pillBottom = centerTouchBounds.bottom
            val pillH = pillBottom - pillTop

            val restingAlpha = (centerTransparency * 2.55f).toInt().coerceIn(0, 255)
            val activeAlpha = 210
            val effectiveAlpha = if (isTouched) activeAlpha else maxOf(restingAlpha, (activeAlpha * glowFraction).toInt())

            if (effectiveAlpha > 0 && pillH > 0) {
                bladePath.reset()
                
                val (baseR, baseG, baseB) = if (useM3Color) {
                    Triple(Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                } else {
                    when (glowStyle) {
                        "crimson_reactor" -> Triple(255, 45, 80)
                        "cyber_plasma" -> Triple(0, 229, 255)
                        else -> Triple(120, 160, 255)
                    }
                }
                
                when (pillStyle) {
                    "anchored_glow" -> {
                        val pillW = baseW
                        val cornerR = (pillW * 0.5f).coerceAtMost(pillH / 2f)
                        if (isLeft) {
                            val rect = RectF(0f, pillTop, pillW, pillBottom)
                            val radii = floatArrayOf(0f, 0f, cornerR, cornerR, cornerR, cornerR, 0f, 0f)
                            bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                        } else {
                            val rect = RectF(w - pillW, pillTop, w, pillBottom)
                            val radii = floatArrayOf(cornerR, cornerR, 0f, 0f, 0f, 0f, cornerR, cornerR)
                            bladePath.addRoundRect(rect, radii, Path.Direction.CW)
                        }
                        applyPillShading(isLeft, w, pillW, effectiveAlpha, m3Primary, glowStyle, useM3Color)
                        canvas.drawPath(bladePath, bladeFillPaint)
                    }
                    
                    "floating_smart_pill" -> {
                        val pillW = maxOf(baseW, 4f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        // Solid semi-transparent fill
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.45f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                    }
                    
                    "neon_core" -> {
                        val coreW = 1.5f * d
                        val floatGap = 2f * d
                        val coreTop = pillTop + pillH * 0.1f
                        val coreBottom = pillBottom - pillH * 0.1f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, coreTop, floatGap + coreW, coreBottom)
                        } else {
                            RectF(w - floatGap - coreW, coreTop, w - floatGap, coreBottom)
                        }
                        
                        // Aura
                        val auraW = 8f * d
                        val auraRect = if (isLeft) {
                            RectF(0f, pillTop, auraW, pillBottom)
                        } else {
                            RectF(w - auraW, pillTop, w, pillBottom)
                        }
                        applyPillShading(isLeft, w, auraW, (effectiveAlpha * 0.5f).toInt(), m3Primary, glowStyle, useM3Color)
                        
                        // Draw Aura
                        if (isLeft) {
                            val radii = floatArrayOf(0f, 0f, auraW, auraW, auraW, auraW, 0f, 0f)
                            bladePath.addRoundRect(auraRect, radii, Path.Direction.CW)
                        } else {
                            val radii = floatArrayOf(auraW, auraW, 0f, 0f, 0f, 0f, auraW, auraW)
                            bladePath.addRoundRect(auraRect, radii, Path.Direction.CW)
                        }
                        canvas.drawPath(bladePath, bladeFillPaint)
                        
                        // Draw Solid Core
                        bladeFillPaint.shader = null
                        bladeFillPaint.color = Color.WHITE
                        canvas.drawRoundRect(rect, coreW/2f, coreW/2f, bladeFillPaint)
                    }
                    
                    "razor_edge" -> {
                        val pillW = 1.2f * d
                        val floatGap = 1.5f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.85f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                    }
                    
                    "kinetic_elastic" -> {
                        val pillW = maxOf(baseW, 3f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        bladeFillPaint.shader = null
                        val fillAlpha = (effectiveAlpha * 0.6f).toInt().coerceIn(0, 255)
                        bladeFillPaint.color = Color.argb(fillAlpha, baseR, baseG, baseB)
                        
                        if (isTouched && touchY != null) {
                            // Bulge towards the touch point
                            val bulgeAmount = 6f * d
                            
                            val tY = touchY.coerceIn(pillTop, pillBottom)
                            val ctrlOffset = 30f * d
                            
                            if (isLeft) {
                                bladePath.moveTo(floatGap, pillTop)
                                bladePath.lineTo(floatGap + pillW, pillTop)
                                bladePath.cubicTo(
                                    floatGap + pillW, tY - ctrlOffset,
                                    floatGap + pillW + bulgeAmount, tY,
                                    floatGap + pillW, tY + ctrlOffset
                                )
                                bladePath.lineTo(floatGap + pillW, pillBottom)
                                bladePath.lineTo(floatGap, pillBottom)
                                bladePath.close()
                            } else {
                                bladePath.moveTo(w - floatGap, pillTop)
                                bladePath.lineTo(w - floatGap - pillW, pillTop)
                                bladePath.cubicTo(
                                    w - floatGap - pillW, tY - ctrlOffset,
                                    w - floatGap - pillW - bulgeAmount, tY,
                                    w - floatGap - pillW, tY + ctrlOffset
                                )
                                bladePath.lineTo(w - floatGap - pillW, pillBottom)
                                bladePath.lineTo(w - floatGap, pillBottom)
                                bladePath.close()
                            }
                            canvas.drawPath(bladePath, bladeFillPaint)
                        } else {
                            canvas.drawRoundRect(rect, cornerR, cornerR, bladeFillPaint)
                        }
                    }
                    
                    "hollow_ghost" -> {
                        val pillW = maxOf(baseW, 4f * d)
                        val floatGap = 3f * d
                        val cornerR = pillW / 2f
                        
                        val rect = if (isLeft) {
                            RectF(floatGap, pillTop, floatGap + pillW, pillBottom)
                        } else {
                            RectF(w - floatGap - pillW, pillTop, w - floatGap, pillBottom)
                        }
                        
                        reviewStrokePaint.color = Color.argb(effectiveAlpha, baseR, baseG, baseB)
                        reviewStrokePaint.strokeWidth = 1.2f * d
                        canvas.drawRoundRect(rect, cornerR, cornerR, reviewStrokePaint)
                        reviewStrokePaint.color = Color.WHITE // reset
                    }
                }
            }
        }"""

# Replace the block
content = re.sub(r'        // =========================================================================\n        // 1\. CENTRAL PILL(.*?)\n        // =========================================================================\n        // 2\. DEFLECTORS', 
    pill_logic + '\n        // =========================================================================\n        // 2. DEFLECTORS', 
    content, flags=re.DOTALL)

with open(file_path, "w") as f:
    f.write(content)
print("Updated LightspeedDeflectorRenderer.kt")
