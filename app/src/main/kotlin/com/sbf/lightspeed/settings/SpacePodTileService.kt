package com.sbf.lightspeed.settings

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.sbf.lightspeed.system.SpacePodGestureDispatcher
import com.sbf.lightspeed.widget.LightspeedWidgetConfigActivity

/**
 * Base Multi-Gesture Quick Settings Tile Service for Space Pods.
 * Supports CLICK and DOUBLE_CLICK on taps, and coordinates with
 * SpacePodLongClickActivity for HOLD and CLICK_THEN_HOLD.
 */
abstract class BaseSpacePodTileService(private val podIndex: Int) : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!SpacePodGestureDispatcher.isPodConfigured(this, podIndex)) {
            val intent = Intent(this, LightspeedWidgetConfigActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(LightspeedWidgetConfigActivity.EXTRA_POD_INDEX, podIndex)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    podIndex,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }
        SpacePodGestureDispatcher.onPodTapped(this, podIndex)
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = Tile.STATE_ACTIVE
        tile.label = "Pod $podIndex"
        tile.contentDescription = "Tactical Space Pod $podIndex"
        tile.updateTile()
    }
}

class SpacePodTileService : BaseSpacePodTileService(1)
class SpacePodTileService1 : BaseSpacePodTileService(1)
class SpacePodTileService2 : BaseSpacePodTileService(2)
class SpacePodTileService3 : BaseSpacePodTileService(3)
class SpacePodTileService4 : BaseSpacePodTileService(4)
