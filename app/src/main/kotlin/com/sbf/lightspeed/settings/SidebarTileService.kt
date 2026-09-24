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
 * Interactive Quick Settings Tile for Lightspeed Master Flight Mode.
 * Short tap: Toggles Master Armed / Standby.
 */
class SidebarTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val current = LightspeedPreferences.isMasterFlightArmed(this)
        val newArmed = !current
        LightspeedPreferences.setMasterFlightArmed(this, newArmed)

        LightspeedHapticEngine.tick(this)
        val msg = if (newArmed) "// FLIGHT DECK // Armed ⚡" else "// FLIGHT DECK // Standby ⏸"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        updateTileState()
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        LightspeedFlightNotificationManager.update(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isArmed = LightspeedPreferences.isMasterFlightArmed(this)

        tile.state = if (isArmed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Lightspeed"
        tile.icon = Icon.createWithResource(
            this,
            if (isArmed) R.drawable.ic_qs_flight_armed else R.drawable.ic_qs_flight_standby
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isArmed) "Armed ⚡" else "Standby ⏸"
        }
        tile.updateTile()
    }
}
