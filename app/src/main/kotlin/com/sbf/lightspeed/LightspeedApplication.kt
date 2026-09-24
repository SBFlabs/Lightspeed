package com.sbf.lightspeed

import android.app.Application
import com.sbf.lightspeed.system.LightspeedCrashSentinel

class LightspeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.sbf.lightspeed.system.LightspeedPreferences.initializeDefaults(this)
        com.sbf.lightspeed.system.LightspeedLanguageEngine.init(this)
        LightspeedCrashSentinel.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= 10 /* TRIM_MEMORY_RUNNING_LOW / TRIM_MEMORY_BACKGROUND */ || level == TRIM_MEMORY_UI_HIDDEN) {
            com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
            com.sbf.lightspeed.system.LightspeedShortcutManager.clearMemoryCache()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
        com.sbf.lightspeed.system.LightspeedShortcutManager.clearMemoryCache()
    }
}
