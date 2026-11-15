package com.sbf.lightspeed

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.sbf.lightspeed.settings.CentralCommandActivity

internal fun LightspeedCruiseOverlay.settingsButtonCx(): Float = 0f

internal fun LightspeedCruiseOverlay.settingsButtonCy(): Float = 0f

internal fun LightspeedCruiseOverlay.isSettingsButtonHit(x: Float, y: Float): Boolean = false

internal fun LightspeedCruiseOverlay.drawSettingsButton(canvas: Canvas, paint: Paint) {
    // Top right settings area removed per user directive
}

internal fun LightspeedCruiseOverlay.openSettingsFromButton() {
    dismissOverlay()
    try {
        val intent = Intent(context, CentralCommandActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("SettingsButton", "Failed to open CentralCommandActivity", e)
    }
}
