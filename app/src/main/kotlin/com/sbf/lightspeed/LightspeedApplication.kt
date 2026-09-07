package com.sbf.lightspeed

import android.app.Application
import com.sbf.lightspeed.system.LightspeedCrashSentinel

class LightspeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.sbf.lightspeed.system.LightspeedLanguageEngine.init(this)
        LightspeedCrashSentinel.init(this)
    }
}
