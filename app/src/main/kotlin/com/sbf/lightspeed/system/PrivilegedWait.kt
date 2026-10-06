package com.sbf.lightspeed.system

import android.os.SystemClock
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val killScheduler = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "PrivilegedKill").apply { isDaemon = true }
}

/**
 * Waits for a shell process without ever blocking forever.
 */
fun Process?.waitForOrKill(timeoutMs: Long = 5000L): Int {
    if (this == null) return -1
    return try {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                return exitValue()
            } catch (_: Exception) {
                // still running, expected: do NOT log this
            }
            Thread.sleep(25)
        }
        try {
            destroyForcibly()
        } catch (ex: Exception) {
            logSwallowed("PrivilegedWait", "waitForOrKill:destroyForcibly", ex)
        }
        -1
    } catch (e: Exception) {
        logSwallowed("PrivilegedWait", "waitForOrKill", e)
        try {
            destroyForcibly()
        } catch (ex: Exception) {
            logSwallowed("PrivilegedWait", "waitForOrKill:destroyForcibly", ex)
        }
        -1
    } finally {
        try { inputStream.close() } catch (e: Exception) { logSwallowed("PrivilegedWait", "closeInputStream", e) }
        try { errorStream.close() } catch (e: Exception) { logSwallowed("PrivilegedWait", "closeErrorStream", e) }
        try { outputStream.close() } catch (e: Exception) { logSwallowed("PrivilegedWait", "closeOutputStream", e) }
    }
}

/**
 * Reads all stdout of a shell process; kills it and returns null if it stalls.
 */
fun Process?.readTextOrKill(timeoutMs: Long = 5000L): String? {
    if (this == null) return null
    val timedOut = AtomicBoolean(false)
    val future = killScheduler.schedule({
        timedOut.set(true)
        try {
            destroyForcibly()
        } catch (e: Exception) {
            logSwallowed("PrivilegedWait", "readTextOrKill:destroyForcibly", e)
        }
    }, timeoutMs, TimeUnit.MILLISECONDS)

    val text: String? = try {
        inputStream.bufferedReader().readText()
    } catch (e: Exception) {
        logSwallowed("PrivilegedWait", "readTextOrKill", e)
        null
    } finally {
        future.cancel(false)
        waitForOrKill(1000L)
    }

    return if (timedOut.get()) null else text
}

