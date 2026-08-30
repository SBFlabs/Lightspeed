package com.sbf.lightspeed.system

import android.content.Intent
import android.service.dreams.DreamService
import com.sbf.lightspeed.LightspeedRefuelingActivity

/**
 * Android DreamService screensaver implementation for Lightspeed Refueling Bay.
 */
class LightspeedDreamService : DreamService() {

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true

        val intent = Intent(this, LightspeedRefuelingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {}
        finish()
    }
}
