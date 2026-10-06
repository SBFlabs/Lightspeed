package com.sbf.lightspeed.system

import android.os.SystemClock
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

private val lastLogTimes = ConcurrentHashMap<String, Long>()
private const val RATE_LIMIT_MS = 10_000L

fun logSwallowed(tag: String, where: String, e: Throwable) {
    val key = "$tag::$where"
    val now = SystemClock.elapsedRealtime()
    val lastTime = lastLogTimes[key]
    if (lastTime != null && (now - lastTime) < RATE_LIMIT_MS) {
        return
    }
    lastLogTimes[key] = now
    if (lastLogTimes.size > 200) {
        lastLogTimes.clear()
        lastLogTimes[key] = now
    }

    Log.w(tag, "swallowed in $where", e)
    PopupDiagnostics.recordSwallowed(tag, where, e)
}

