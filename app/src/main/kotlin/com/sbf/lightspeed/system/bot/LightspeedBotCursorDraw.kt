package com.sbf.lightspeed.system.bot

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.resolveCanvasPalette

/**
 * Zero-Allocation 120Hz Tactical Cursor & Reticle Canvas Renderer for Cyber-Bot Trackpad.
 */
object LightspeedBotCursorDraw {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val cachedArrowPath = Path()
    private val cachedDiamondPath = Path()
    private val cachedBracketRect = RectF()

    fun drawTacticalCursor(
        canvas: Canvas,
        cursorX: Float,
        cursorY: Float,
        isLooping: Boolean,
        loopCount: Int,
        theme: DeckThemeSnapshot,
        density: Float,
        cursorShape: String = "crosshair",
        context: android.content.Context? = null,
        colorMode: String = LightspeedBotContract.COLOR_MODE_HYBRID_FUSION,
        isHoldActive: Boolean = false
    ) {
        if (cursorX.isNaN() || cursorY.isNaN()) return
        val palette = theme.resolveCanvasPalette(context, colorMode)
        val accent = palette.borderArgb
        val tint = palette.primaryAccentArgb
        val panelFill = palette.panelInnerArgb
        val r = 22f * density

        if (isHoldActive) {
            val holdR = r * 0.95f
            glowPaint.style = Paint.Style.STROKE
            glowPaint.strokeWidth = 3.6f * density
            glowPaint.color = tint
            glowPaint.alpha = 190
            canvas.drawCircle(cursorX, cursorY, holdR, glowPaint)

            strokePaint.style = Paint.Style.STROKE
            strokePaint.strokeWidth = 1.6f * density
            strokePaint.color = Color.WHITE
            strokePaint.alpha = 240
            canvas.drawCircle(cursorX, cursorY, holdR, strokePaint)
        }

        when (cursorShape) {
            "pointer" -> {
                // Sleek Glowing Neon Arrow Pointer (tip at cursorX, cursorY)
                val arrowPath = cachedArrowPath
                arrowPath.rewind()
                arrowPath.moveTo(cursorX, cursorY)
                arrowPath.lineTo(cursorX, cursorY + 24f * density)
                arrowPath.lineTo(cursorX + 6.5f * density, cursorY + 18.5f * density)
                arrowPath.lineTo(cursorX + 11.5f * density, cursorY + 27.5f * density)
                arrowPath.lineTo(cursorX + 15.5f * density, cursorY + 25.5f * density)
                arrowPath.lineTo(cursorX + 10.5f * density, cursorY + 16.5f * density)
                arrowPath.lineTo(cursorX + 18f * density, cursorY + 16.5f * density)
                arrowPath.close()
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 5f * density
                glowPaint.color = tint
                glowPaint.alpha = 110
                canvas.drawPath(arrowPath, glowPaint)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = panelFill
                bgPaint.alpha = 235
                canvas.drawPath(arrowPath, bgPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.color = accent
                strokePaint.alpha = 250
                canvas.drawPath(arrowPath, strokePaint)
            }
            "ring" -> {
                // Dual Glowing Laser Ring + Center Dot
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 6f * density
                glowPaint.color = tint
                glowPaint.alpha = 90
                canvas.drawCircle(cursorX, cursorY, 16f * density, glowPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 2f * density
                strokePaint.color = accent
                strokePaint.alpha = 245
                canvas.drawCircle(cursorX, cursorY, 16f * density, strokePaint)

                strokePaint.strokeWidth = 1.2f * density
                strokePaint.color = Color.WHITE
                strokePaint.alpha = 180
                canvas.drawCircle(cursorX, cursorY, 8f * density, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 3f * density, nodePaint)
            }
            "diamond" -> {
                // Faceted Tactical Diamond Collimator
                val d = 20f * density
                val dmPath = cachedDiamondPath
                dmPath.rewind()
                dmPath.moveTo(cursorX, cursorY - d)
                dmPath.lineTo(cursorX + d, cursorY)
                dmPath.lineTo(cursorX, cursorY + d)
                dmPath.lineTo(cursorX - d, cursorY)
                dmPath.close()
                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 5f * density
                glowPaint.color = tint
                glowPaint.alpha = 90
                canvas.drawPath(dmPath, glowPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.color = accent
                strokePaint.alpha = 245
                canvas.drawPath(dmPath, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = Color.WHITE
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 2.8f * density, nodePaint)
            }
            "dot" -> {
                // Minimal Precision Neon Dot with Halo
                glowPaint.style = Paint.Style.FILL
                glowPaint.color = tint
                glowPaint.alpha = 95
                canvas.drawCircle(cursorX, cursorY, 12f * density, glowPaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 240
                canvas.drawCircle(cursorX, cursorY, 6f * density, nodePaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.6f * density
                strokePaint.color = Color.WHITE
                strokePaint.alpha = 250
                canvas.drawCircle(cursorX, cursorY, 6f * density, strokePaint)
            }
            "tactical_triangle" -> {
                // Downward-pointing HUD Vector Triangle
                val triPath = cachedArrowPath
                triPath.rewind()
                val triSize = 13f * density
                triPath.moveTo(cursorX, cursorY)
                triPath.lineTo(cursorX - triSize * 0.9f, cursorY - triSize * 1.5f)
                triPath.lineTo(cursorX + triSize * 0.9f, cursorY - triSize * 1.5f)
                triPath.close()

                glowPaint.style = Paint.Style.STROKE
                glowPaint.strokeWidth = 4f * density
                glowPaint.color = tint
                glowPaint.alpha = 90
                canvas.drawPath(triPath, glowPaint)

                bgPaint.style = Paint.Style.FILL
                bgPaint.color = panelFill
                bgPaint.alpha = 220
                canvas.drawPath(triPath, bgPaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.6f * density
                strokePaint.color = accent
                strokePaint.alpha = 245
                canvas.drawPath(triPath, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                canvas.drawCircle(cursorX, cursorY, 2.2f * density, nodePaint)
            }
            "hud_ring" -> {
                // Segmented Combat Flight Ring with Crosshairs
                val ringR = 14f * density
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.color = accent
                strokePaint.alpha = 240
                cachedBracketRect.set(cursorX - ringR, cursorY - ringR, cursorX + ringR, cursorY + ringR)
                canvas.drawArc(cachedBracketRect, 30f, 60f, false, strokePaint)
                canvas.drawArc(cachedBracketRect, 150f, 60f, false, strokePaint)
                canvas.drawArc(cachedBracketRect, 270f, 60f, false, strokePaint)

                canvas.drawLine(cursorX, cursorY - ringR * 1.35f, cursorX, cursorY - ringR * 0.45f, strokePaint)
                canvas.drawLine(cursorX, cursorY + ringR * 0.45f, cursorX, cursorY + ringR * 1.35f, strokePaint)
                canvas.drawLine(cursorX - ringR * 1.35f, cursorY, cursorX - ringR * 0.45f, cursorY, strokePaint)
                canvas.drawLine(cursorX + ringR * 0.45f, cursorY, cursorX + ringR * 1.35f, cursorY, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                canvas.drawCircle(cursorX, cursorY, 2.2f * density, nodePaint)
            }
            "cyber_bracket" -> {
                // Cybernetic Targeting Corners
                val bSize = 13f * density
                val bArm = 6f * density
                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 2f * density
                strokePaint.color = tint
                strokePaint.alpha = 250
                // Top-Left corner
                canvas.drawLine(cursorX - bSize, cursorY - bSize, cursorX - bSize + bArm, cursorY - bSize, strokePaint)
                canvas.drawLine(cursorX - bSize, cursorY - bSize, cursorX - bSize, cursorY - bSize + bArm, strokePaint)
                // Top-Right corner
                canvas.drawLine(cursorX + bSize, cursorY - bSize, cursorX + bSize - bArm, cursorY - bSize, strokePaint)
                canvas.drawLine(cursorX + bSize, cursorY - bSize, cursorX + bSize, cursorY - bSize + bArm, strokePaint)
                // Bottom-Left corner
                canvas.drawLine(cursorX - bSize, cursorY + bSize, cursorX - bSize + bArm, cursorY + bSize, strokePaint)
                canvas.drawLine(cursorX - bSize, cursorY + bSize, cursorX - bSize, cursorY + bSize - bArm, strokePaint)
                // Bottom-Right corner
                canvas.drawLine(cursorX + bSize, cursorY + bSize, cursorX + bSize - bArm, cursorY + bSize, strokePaint)
                canvas.drawLine(cursorX + bSize, cursorY + bSize, cursorX + bSize, cursorY + bSize - bArm, strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = Color.WHITE
                canvas.drawCircle(cursorX, cursorY, 2f * density, nodePaint)
            }
            "stealth_dot" -> {
                // Micro Precision Dot with Target Ring
                glowPaint.style = Paint.Style.FILL
                glowPaint.color = tint
                glowPaint.alpha = 80
                canvas.drawCircle(cursorX, cursorY, 8f * density, glowPaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = Color.WHITE
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 3f * density, nodePaint)

                strokePaint.style = Paint.Style.STROKE
                strokePaint.strokeWidth = 1f * density
                strokePaint.color = accent
                strokePaint.alpha = 210
                canvas.drawCircle(cursorX, cursorY, 6f * density, strokePaint)
            }
            else -> {
                // Default "crosshair" Tactical Reticle
                strokePaint.style = Paint.Style.STROKE
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.8f * density
                strokePaint.alpha = 245

                val bracketGap = 8f * density
                cachedBracketRect.set(cursorX - r, cursorY - r, cursorX + r, cursorY + r)
                canvas.drawArc(cachedBracketRect, 25f, 40f, false, strokePaint)
                canvas.drawArc(cachedBracketRect, 115f, 40f, false, strokePaint)
                canvas.drawArc(cachedBracketRect, 205f, 40f, false, strokePaint)
                canvas.drawArc(cachedBracketRect, 295f, 40f, false, strokePaint)

                canvas.drawLine(cursorX - (r * 1.35f), cursorY, cursorX - bracketGap, cursorY, strokePaint)
                canvas.drawLine(cursorX + bracketGap, cursorY, cursorX + (r * 1.35f), cursorY, strokePaint)
                canvas.drawLine(cursorX, cursorY - (r * 1.35f), cursorX, cursorY - bracketGap, strokePaint)
                canvas.drawLine(cursorX, cursorY + bracketGap, cursorX, cursorY + (r * 1.35f), strokePaint)

                nodePaint.style = Paint.Style.FILL
                nodePaint.color = tint
                nodePaint.alpha = 255
                canvas.drawCircle(cursorX, cursorY, 2.5f * density, nodePaint)
            }
        }

        if (isLooping) {
            val badgeX = cursorX + (22f * density)
            val badgeY = cursorY - (22f * density)
            nodePaint.color = 0xEEFF3333.toInt()
            canvas.drawCircle(badgeX, badgeY, 13f * density, nodePaint)

            strokePaint.color = Color.WHITE
            strokePaint.strokeWidth = 1.2f * density
            canvas.drawCircle(badgeX, badgeY, 13f * density, strokePaint)

            textPaint.textSize = 9f * density
            textPaint.color = Color.WHITE
            canvas.drawText("$loopCount", badgeX, badgeY + (3.2f * density), textPaint)
        }
    }
}
