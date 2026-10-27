package com.sbf.lightspeed.settings

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedMasterCutoff

/**
 * The single Lightspeed Quick Settings tile: Master Stealth Cloak.
 * Short tap: Cold Shutdown (cloak) / 1-Tap Resurrection.
 * Long press: Cloak Matrix configuration (via QS_TILE_PREFERENCES).
 */
class MasterCutoffTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val newArmed = !LightspeedMasterCutoff.isSystemArmed(this)
        LightspeedMasterCutoff.setSystemArmed(this, newArmed)
        LightspeedHapticEngine.tick(this)
        Toast.makeText(
            this,
            if (newArmed) "// FLIGHT DECK // Armed ⚡" else "// FLIGHT DECK // Cloaked ⏸",
            Toast.LENGTH_SHORT
        ).show()
        updateTileState()
        LightspeedFlightNotificationManager.update(this)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isArmed = LightspeedMasterCutoff.isSystemArmed(this)
        tile.state = if (isArmed) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = if (isArmed) "Reactor Active" else "Reactor Cutoff"
        tile.contentDescription = if (isArmed) "Lightspeed Active - Tap for Cold Shutdown" else "Lightspeed Cutoff - Tap to Resurrect"
        tile.updateTile()
    }
}
