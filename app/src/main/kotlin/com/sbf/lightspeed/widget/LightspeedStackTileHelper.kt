package com.sbf.lightspeed.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

/**
 * Helper to generate high-contrast tactical system deck tile icons and telemetry badges (< 120 lines).
 */
object LightspeedStackTileHelper {

    fun createThemedTileBitmap(
        context: Context,
        drawableResId: Int,
        isActive: Boolean,
        accentColorArgb: Int
    ): Bitmap {
        val density = context.resources.displayMetrics.density
        val sizePx = (34 * density).toInt()
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Subtle circular tile halo
        val rect = RectF(1f * density, 1f * density, sizePx - (1f * density), sizePx - (1f * density))
        paint.style = Paint.Style.FILL
        paint.color = if (isActive) accentColorArgb else Color.WHITE
        paint.alpha = if (isActive) 55 else 18
        canvas.drawRoundRect(rect, 8f * density, 8f * density, paint)

        // Border stroke if active
        if (isActive) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.2f * density
            paint.color = accentColorArgb
            paint.alpha = 200
            canvas.drawRoundRect(rect, 8f * density, 8f * density, paint)
        }

        // Draw tinted icon
        val drawable = ContextCompat.getDrawable(context, drawableResId)?.mutate()
        if (drawable != null) {
            val pad = (6 * density).toInt()
            drawable.setBounds(pad, pad, sizePx - pad, sizePx - pad)
            drawable.setColorFilter(if (isActive) accentColorArgb else Color.WHITE, PorterDuff.Mode.SRC_IN)
            drawable.draw(canvas)
        }

        return bitmap
    }

    fun getBatteryTelemetry(context: Context): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            val isCharging = bm?.isCharging == true
            val icon = if (isCharging) "⚡ " else ""
            "$icon$pct%"
        } catch (_: Exception) {
            "100%"
        }
    }

    fun getImmunityTelemetry(context: Context): String {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val isImmune = pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            if (isImmune) "100% UNRESTRICTED" else "OPTIMIZED"
        } catch (_: Exception) {
            "ACTIVE"
        }
    }
}
