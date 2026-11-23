package com.sbf.lightspeed

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.SystemClock
import kotlin.math.cos
import kotlin.math.sin

/**
 * Dedicated renderer for Deep Space cosmic starfield and animated galactic nebula backdrop.
 */
internal class LightspeedCosmicBackdropRenderer {

    private val elementPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Origin-centered Galactic Nebula background shader cache (rebuilt only on radius/color change, never per-frame)
    private var nebulaShader1Key: Long = Long.MIN_VALUE
    private var cachedNebulaShader1: RadialGradient? = null

    // Origin-centered secondary animated blob shader cache
    private var nebulaShader2Key: Long = Long.MIN_VALUE
    private var cachedNebulaShader2: RadialGradient? = null

    private fun nebulaKey(radius: Float, color: Int): Long =
        (radius.toBits().toLong() shl 32) xor (color.toLong() and 0xFFFFFFFFL)

    fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) {
        val starPaint = elementPaint
        starPaint.style = Paint.Style.FILL
        starPaint.alpha = 255

        val time = SystemClock.uptimeMillis()
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
        val t = (SystemClock.uptimeMillis() % 100000L) / 1000.0
        val layerAlpha = (alphaFactor.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        if (layerAlpha <= 0) return

        val rP = Color.red(m3Primary)
        val gP = Color.green(m3Primary)
        val bP = Color.blue(m3Primary)

        val r1 = (radius * 1.3f).coerceAtLeast(1f)
        val key1 = nebulaKey(r1, m3Primary)
        if (key1 != nebulaShader1Key) {
            cachedNebulaShader1 = RadialGradient(
                0f, 0f, r1,
                intArrayOf(
                    Color.argb(115, rP, gP, bP),
                    Color.argb(60, (rP * 0.7f).toInt(), (gP * 0.8f).toInt(), bP),
                    Color.argb(25, (rP * 0.4f).toInt(), (gP * 0.5f).toInt(), (bP * 0.7f).toInt()),
                    Color.argb(0, 4, 6, 12)
                ),
                floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
                Shader.TileMode.CLAMP
            )
            nebulaShader1Key = key1
        }

        val r2 = (radius * 0.9f).coerceAtLeast(1f)
        val key2 = nebulaKey(r2, m3Primary)
        if (key2 != nebulaShader2Key) {
            cachedNebulaShader2 = RadialGradient(
                0f, 0f, r2,
                intArrayOf(
                    Color.argb(65, rP, gP, bP),
                    Color.argb(28, 200, 225, 255),
                    Color.argb(0, 0, 0, 0)
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
            nebulaShader2Key = key2
        }

        elementPaint.style = Paint.Style.FILL
        elementPaint.alpha = layerAlpha

        elementPaint.shader = cachedNebulaShader1
        canvas.save()
        canvas.translate(cx, cy)
        canvas.drawCircle(0f, 0f, r1, elementPaint)
        canvas.restore()

        val offX = (sin(t * 0.8) * 20.0).toFloat()
        val offY = (cos(t * 0.6) * 15.0).toFloat()
        elementPaint.shader = cachedNebulaShader2
        canvas.save()
        canvas.translate(cx + offX, cy + offY)
        canvas.drawCircle(0f, 0f, r2, elementPaint)
        canvas.restore()

        elementPaint.shader = null
        elementPaint.alpha = 255
    }
}
