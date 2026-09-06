package com.sbf.lightspeed.settings

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Interactive Quick Settings Tile for kinetic Deflector flanks.
 * Short tap: Toggles Left and Right Deflectors on/off.
 */
class DeflectorTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val left = LightspeedPreferences.isLeftDeflectorEnabled(this)
        val right = LightspeedPreferences.isRightDeflectorEnabled(this)

        val newState = !(left && right)
        LightspeedPreferences.setLeftDeflectorEnabled(this, newState)
        LightspeedPreferences.setRightDeflectorEnabled(this, newState)

        LightspeedHapticEngine.tick(this)
        val msg = if (newState) "// DEFLECTORS // Flanks Activated" else "// DEFLECTORS // Flanks Muted"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        updateTileState()
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        LightspeedFlightNotificationManager.update(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val left = LightspeedPreferences.isLeftDeflectorEnabled(this)
        val right = LightspeedPreferences.isRightDeflectorEnabled(this)

        tile.label = "Deflectors"
        tile.state = if (left || right) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                left && right -> "Both Active"
                left -> "Left Flank Only"
                right -> "Right Flank Only"
                else -> "Muted"
            }
        }
        tile.updateTile()
    }
}
