package com.sbf.lightspeed.settings

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sbf.lightspeed.R
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
        val msg = if (newState) "// DEFLECTORS // Flanks Armed ⚡" else "// DEFLECTORS // Flanks Standby ⏸"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        updateTileState()
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        LightspeedFlightNotificationManager.update(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val left = LightspeedPreferences.isLeftDeflectorEnabled(this)
        val right = LightspeedPreferences.isRightDeflectorEnabled(this)
        val isActive = left || right

        tile.label = "Deflectors"
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.icon = Icon.createWithResource(
            this,
            if (isActive) R.drawable.ic_qs_deflectors_active else R.drawable.ic_qs_deflectors_muted
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                left && right -> "Armed (Both) ⚡"
                left -> "Port Wing Only"
                right -> "Starboard Only"
                else -> "Standby ⏸"
            }
        }
        tile.updateTile()
    }
}
