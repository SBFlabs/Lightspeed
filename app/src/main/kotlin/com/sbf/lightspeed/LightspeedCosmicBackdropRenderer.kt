package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated renderer for Deep Space cosmic starfield and animated galactic nebula backdrop.
 */
internal class LightspeedCosmicBackdropRenderer {

    private val elementPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Galactic Nebula background shader cache
    private var nebulaShader1Key: Long = Long.MIN_VALUE
    private var cachedNebulaShader1: RadialGradient? = null

    // Galactic Nebula secondary animated blob (position quantized to 1px buckets)
    private var nebulaShader2Key: Long = Long.MIN_VALUE
    private var cachedNebulaShader2: RadialGradient? = null

    private fun nebulaKey(cx: Float, cy: Float, radius: Float, color: Int, alphaInt: Int): Long =
        (cx.toBits().toLong() xor (cy.toBits().toLong() shl 16)) xor
        (radius.toBits().toLong() shl 8) xor color.toLong() xor alphaInt.toLong()

    fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) {
        val starPaint = elementPaint
        starPaint.style = Paint.Style.FILL

        val time = System.currentTimeMillis()
        val starCount = 42
        for (i in 0 until starCount) {
            val seedX = ((i * 137.5f) % w)
            val seedY = ((i * 269.3f) % h)
            val pulse = sin((time / 450.0) + (i * 0.75)).toFloat() * 0.35f + 0.65f
            val starSize = ((i % 3) + 1.2f) * density * (0.8f + 0.2f * pulse)
            val starAlpha = ((70 + (i * 17) % 130) * pulse * alphaFactor).toInt().coerceIn(0, 255)

            starPaint.color = when (i % 4) {
                0 -> Color.argb(starAlpha, 255, 255, 255)
                1 -> Color.argb(starAlpha, 200, 228, 255)
                2 -> Color.argb(starAlpha, 220, 235, 255)
                else -> Color.argb(starAlpha, 185, 215, 245)
            }
            canvas.drawCircle(seedX, seedY, starSize / 2f, starPaint)
        }
    }

    fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) {
        val t = (System.currentTimeMillis() % 100000) / 1000.0
        val alphaInt = (alphaFactor * 1000).toInt() // quantize to 0.1% steps
        val rP = Color.red(m3Primary)
        val gP = Color.green(m3Primary)
        val bP = Color.blue(m3Primary)

        // Shader 1: static primary nebula blob — strictly uses active theme tint (no hardcoded purple/pink)
        val key1 = nebulaKey(cx, cy, radius * 1.3f, m3Primary, alphaInt)
        if (key1 != nebulaShader1Key) {
            cachedNebulaShader1 = RadialGradient(
                cx, cy, radius * 1.3f,
                intArrayOf(
                    Color.argb((115 * alphaFactor).toInt(), rP, gP, bP),
                    Color.argb((60 * alphaFactor).toInt(), (rP * 0.7f).toInt(), (gP * 0.8f).toInt(), bP),
                    Color.argb((25 * alphaFactor).toInt(), (rP * 0.4f).toInt(), (gP * 0.5f).toInt(), (bP * 0.7f).toInt()),
                    Color.argb(0, 4, 6, 12)
                ),
                floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP
            )
            nebulaShader1Key = key1
        }
        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = cachedNebulaShader1
        canvas.drawCircle(cx, cy, radius * 1.3f, elementPaint)

        // Shader 2: animated offset blob — uses theme primary instead of hardcoded pink
        val offX = (sin(t * 0.8) * 20.0).toFloat()
        val offY = (cos(t * 0.6) * 15.0).toFloat()
        val offXQ = offX.toInt().toFloat()  // 1px quantization
        val offYQ = offY.toInt().toFloat()
        val key2 = nebulaKey(cx + offXQ, cy + offYQ, radius * 0.9f, m3Primary, alphaInt)
        if (key2 != nebulaShader2Key) {
            cachedNebulaShader2 = RadialGradient(
                cx + offX, cy + offY, radius * 0.9f,
                intArrayOf(
                    Color.argb((65 * alphaFactor).toInt(), rP, gP, bP),
                    Color.argb((28 * alphaFactor).toInt(), 200, 225, 255),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            nebulaShader2Key = key2
        }
        elementPaint.shader = cachedNebulaShader2
        canvas.drawCircle(cx + offX, cy + offY, radius * 0.9f, elementPaint)
        elementPaint.shader = null
    }
}
