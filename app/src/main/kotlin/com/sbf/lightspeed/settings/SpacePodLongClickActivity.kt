package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.sbf.lightspeed.system.SpacePodGestureDispatcher
import com.sbf.lightspeed.widget.LightspeedWidgetConfigActivity

/**
 * Transparent Activity intercepting Quick Settings Tile Long-Press events
 * via android.service.quicksettings.action.QS_TILE_PREFERENCES.
 *
 * If triggered from a Space Pod tile, coordinates HOLD and CLICK_THEN_HOLD.
 * If triggered from Master Lightspeed tile or other surfaces, launches LightspeedTileCloakActivity.
 */
class SpacePodLongClickActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val componentName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<ComponentName>(Intent.EXTRA_COMPONENT_NAME)
        }

        val className = componentName?.className.orEmpty()
        val isSpacePod = className.contains("SpacePod", ignoreCase = true) || intent?.hasExtra(EXTRA_POD_INDEX) == true

        if (isSpacePod) {
            val podIndex = intent?.getIntExtra(EXTRA_POD_INDEX, 1)?.coerceIn(1, 4) ?: 1
            Log.i("SpacePodLongClick", "Long-press intercepted for Space Pod $podIndex")

            if (!SpacePodGestureDispatcher.isPodConfigured(this, podIndex)) {
                val configIntent = Intent(this, LightspeedWidgetConfigActivity::class.java).apply {
                    putExtra(LightspeedWidgetConfigActivity.EXTRA_POD_INDEX, podIndex)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(configIntent)
            } else {
                SpacePodGestureDispatcher.onPodLongPressed(this, podIndex)
            }
        } else {
            // Master Lightspeed QS Tile (MasterCutoffTileService) -> Launch Cloak Matrix
            Log.i("SpacePodLongClick", "Long-press intercepted on Master Tile ($className) -> Launching Cloak Matrix")
            val cloakIntent = Intent(this, LightspeedTileCloakActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(cloakIntent)
        }
        finish()
    }

    companion object {
        const val EXTRA_POD_INDEX = "extra_pod_index"
    }
}
