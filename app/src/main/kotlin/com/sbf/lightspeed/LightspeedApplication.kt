package com.sbf.lightspeed

import android.app.Application
import com.sbf.lightspeed.system.ShizukuGate
import com.sbf.lightspeed.system.LightspeedCrashSentinel

class LightspeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.sbf.lightspeed.system.LightspeedOverlayDismissCoordinator.init(this)
        ShizukuGate.init(this)
        com.sbf.lightspeed.system.ElevatedTaskCloser.exemptHiddenApis()
        com.sbf.lightspeed.system.LightspeedPreferences.initializeDefaults(this)
        com.sbf.lightspeed.settings.DeckThemeManager.refresh(this)
        com.sbf.lightspeed.settings.BackdropBlurModeManager.init(this)
        com.sbf.lightspeed.system.LightspeedLanguageEngine.init(this)
        LightspeedCrashSentinel.init(this)
        com.sbf.lightspeed.system.FlightLog.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 10 /* TRIM_MEMORY_RUNNING_LOW / TRIM_MEMORY_BACKGROUND */ || level == TRIM_MEMORY_UI_HIDDEN) {
            trimOptionalCaches(purgeOverlayWidgets = level >= 15 /* TRIM_MEMORY_RUNNING_CRITICAL or background */)
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        trimOptionalCaches(purgeOverlayWidgets = true)
    }

    private fun trimOptionalCaches(purgeOverlayWidgets: Boolean) {
        com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
        com.sbf.lightspeed.system.LightspeedShortcutManager.clearMemoryCache()
        TacticalWidgetPreviewCache.clear()
        com.sbf.lightspeed.system.InstalledTacticalToolsScanner.clearCache()
        com.sbf.lightspeed.settings.LightspeedActionRegistry.iconBitmapCache.clear()
        if (!com.sbf.lightspeed.settings.CentralCommandActivity.isActive) {
            com.sbf.lightspeed.settings.CentralCommandBackdropBlur.clearBitmap()
            if (purgeOverlayWidgets) {
                com.sbf.lightspeed.settings.CentralCommandWarmupCoordinator.markCold()
            } else {
                com.sbf.lightspeed.settings.CentralCommandWarmupCoordinator.markUiCold()
            }
        }
        if (purgeOverlayWidgets) {
            LightspeedCruiseOverlay.instance?.let { overlay ->
                if (overlay.currentLayer == CruiseLayer.HIDDEN && !overlay.isStickyPinned) {
                    overlay.destroyAppWidgetHost()
                }
            }
        }
    }
}
