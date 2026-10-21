package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import com.sbf.lightspeed.settings.CentralCommandActivity
import com.sbf.lightspeed.system.LightspeedPreferences
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

internal fun LightspeedCruiseOverlay.settingsButtonCx(): Float {
    val offset = 40f * resources.displayMetrics.density
    return if (prefs().getBoolean(LightspeedPreferences.KEY_PINNED_GEAR_LEFT, false)) offset else width.toFloat() - offset
}

internal fun LightspeedCruiseOverlay.settingsButtonCy(): Float = height.toFloat() * 0.08f

internal fun LightspeedCruiseOverlay.isSettingsButtonHit(x: Float, y: Float): Boolean =
    hypot(x - settingsButtonCx(), y - settingsButtonCy()) <= 28f * resources.displayMetrics.density

internal fun LightspeedCruiseOverlay.drawSettingsButton(canvas: Canvas, paint: Paint) {
    val dp = resources.displayMetrics.density
    val cx = settingsButtonCx()
    val cy = settingsButtonCy()

    val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
    val tintColor = snapshot.tintArgb.toInt()
    val borderColor = snapshot.borderColorArgb.toInt()

    paint.style = Paint.Style.FILL
    paint.color = Color.argb(55, Color.red(tintColor), Color.green(tintColor), Color.blue(tintColor))
    canvas.drawCircle(cx, cy, 20f * dp, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.6f * dp
    paint.color = Color.argb(210, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
    canvas.drawCircle(cx, cy, 7f * dp, paint)

    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 1.6f * dp
    paint.color = Color.argb(210, Color.red(borderColor), Color.green(borderColor), Color.blue(borderColor))
    val r1 = 10f * dp
    val r2 = 13f * dp
    for (i in 0 until 8) {
        val angle = i * (kotlin.math.PI / 4.0)
        val startX = cx + (r1 * cos(angle)).toFloat()
        val startY = cy + (r1 * sin(angle)).toFloat()
        val endX = cx + (r2 * cos(angle)).toFloat()
        val endY = cy + (r2 * sin(angle)).toFloat()
        canvas.drawLine(startX, startY, endX, endY, paint)
    }
}

internal fun LightspeedCruiseOverlay.openSettingsFromButton() {
    try {
        val intent = Intent(context, CentralCommandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("SettingsButton", "Failed to open CentralCommandActivity", e)
    }
}
