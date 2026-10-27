package com.sbf.lightspeed.widget

import android.app.WallpaperManager
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import com.sbf.lightspeed.settings.DeckThemeSnapshot

/**
 * Captures wallpaper, applies fast box-stack blur, and composites 12-theme optical glass.
 */
object WallpaperBlurHelper {

    fun createBlurredWallpaperGlassCard(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        snapshot: DeckThemeSnapshot
    ): Bitmap {
        val w = widthPx.coerceAtLeast(120)
        val h = heightPx.coerceAtLeast(120)

        // Downscale for fast blur execution
        val blurW = (w / 3).coerceAtLeast(40)
        val blurH = (h / 3).coerceAtLeast(40)

        val rawWallpaper = getRawWallpaperBitmap(context, blurW, blurH, snapshot)
        val blurredWallpaper = fastBoxBlur(rawWallpaper, (snapshot.windowBlurRadiusPx / 3).coerceIn(4, 25))

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val rect = RectF(4f, 4f, w - 4f, h - 4f)
        val cornerRadius = (snapshot.cornerRadiusDp * 2.2f).coerceIn(12f, 44f)

        // 1. Clip and draw blurred wallpaper
        val path = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)
        canvas.drawBitmap(blurredWallpaper, null, Rect(0, 0, w, h), paint)

        // 2. Composite Central Command 12-Theme Glass Panel Tint
        paint.style = Paint.Style.FILL
        paint.color = snapshot.panelColorArgb.toInt()
        paint.alpha = (snapshot.panelAlpha * 255).toInt().coerceIn(35, 230)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        // 3. Specular Highlight / Gradient
        val specPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                Color.argb(45, 255, 255, 255),
                Color.argb(0, 255, 255, 255),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), specPaint)
        canvas.restore()

        // 4. 12-Theme Refractive Border Stroke
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = snapshot.borderColorArgb.toInt()
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)

        return output
    }

    private fun getRawWallpaperBitmap(
        context: Context,
        w: Int,
        h: Int,
        snapshot: DeckThemeSnapshot
    ): Bitmap {
        try {
            val wm = WallpaperManager.getInstance(context)
            val drawable = wm.drawable ?: wm.peekDrawable()
            if (drawable is BitmapDrawable && drawable.bitmap != null) {
                return Bitmap.createScaledBitmap(drawable.bitmap, w, h, true)
            } else if (drawable != null) {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(c)
                return bmp
            }
        } catch (_: Exception) {}

        // Fallback: theme panel gradient if wallpaper access is restricted
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint().apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                snapshot.panelColorArgb.toInt(),
                snapshot.tintArgb.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        return bmp
    }

    private fun fastBoxBlur(src: Bitmap, radius: Int): Bitmap {
        val r = radius.coerceAtLeast(1)
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)

        val outPix = IntArray(w * h)
        // Horizontal pass
        val div = r + r + 1
        for (y in 0 until h) {
            val yOffset = y * w
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = pix[yOffset + i.coerceIn(0, w - 1)]
                aSum += (p ushr 24); rSum += (p ushr 16 and 0xFF); gSum += (p ushr 8 and 0xFF); bSum += (p and 0xFF)
            }
            for (x in 0 until w) {
                outPix[yOffset + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val pOut = pix[yOffset + (x - r).coerceIn(0, w - 1)]
                val pIn = pix[yOffset + (x + r + 1).coerceIn(0, w - 1)]
                aSum += (pIn ushr 24) - (pOut ushr 24)
                rSum += (pIn ushr 16 and 0xFF) - (pOut ushr 16 and 0xFF)
                gSum += (pIn ushr 8 and 0xFF) - (pOut ushr 8 and 0xFF)
                bSum += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }

        // Vertical pass
        val finalPix = IntArray(w * h)
        for (x in 0 until w) {
            var aSum = 0; var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -r..r) {
                val p = outPix[i.coerceIn(0, h - 1) * w + x]
                aSum += (p ushr 24); rSum += (p ushr 16 and 0xFF); gSum += (p ushr 8 and 0xFF); bSum += (p and 0xFF)
            }
            for (y in 0 until h) {
                finalPix[y * w + x] = ((aSum / div) shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val pOut = outPix[(y - r).coerceIn(0, h - 1) * w + x]
                val pIn = outPix[(y + r + 1).coerceIn(0, h - 1) * w + x]
                aSum += (pIn ushr 24) - (pOut ushr 24)
                rSum += (pIn ushr 16 and 0xFF) - (pOut ushr 16 and 0xFF)
                gSum += (pIn ushr 8 and 0xFF) - (pOut ushr 8 and 0xFF)
                bSum += (pIn and 0xFF) - (pOut and 0xFF)
            }
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(finalPix, 0, w, 0, 0, w, h)
        return result
    }
}
