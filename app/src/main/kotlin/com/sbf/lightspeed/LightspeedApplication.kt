package com.sbf.lightspeed

import android.app.Application
import com.sbf.lightspeed.system.LightspeedCrashSentinel

class LightspeedApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        LightspeedCrashSentinel.init(this)
    }
}
