package com.sbf.lightspeed.system

import android.content.Context
import android.util.Log

/**
 * Out-of-process guard helper for Lightspeed core accessibility service.
 * Manages an independent shell daemon executing via Shizuku that monitors
 * dumpsys accessibility and recovers Lightspeed if crashed.
 */
object LightspeedGuardHelper {
    private const val TAG = "LightspeedGuardHelper"
    private const val SCRIPT_PATH = "/data/local/tmp/ls_guard.sh"
    private const val PID_PATH = "/data/local/tmp/ls_guard.pid"
    private const val PAUSE_PATH = "/data/local/tmp/ls_guard_pause"
    private const val TARGET_PKG = "com.sbf.lightspeed.nightly"
    private const val TARGET_CMP = "com.sbf.lightspeed.nightly/com.sbf.lightspeed.LightspeedAccessibilityService"
    private const val TARGET_CMP_SHORT = "com.sbf.lightspeed.nightly/.LightspeedAccessibilityService"

    fun start(context: Context) {
        if (!ElevatedTaskCloser.isShizukuActive) {
            Log.w(TAG, "Shizuku is unavailable; cannot start guard helper daemon")
            return
        }

        try {
            val checkCmd = "if [ -f $PID_PATH ]; then PID=\$(cat $PID_PATH | tr -d '[:space:]'); if [ -n \"\$PID\" ] && [ -f \"/proc/\$PID/cmdline\" ] && grep -q \"ls_guard\" \"/proc/\$PID/cmdline\" 2>/dev/null; then echo \"running\"; fi; fi"
            val checkProc = ElevatedTaskCloser.execShizuku(checkCmd)
            val output = checkProc?.inputStream?.bufferedReader()?.readLine()?.trim()
            checkProc?.waitForOrKill()
            if (output == "running") {
                Log.i(TAG, "Guard helper daemon is already running")
                return
            }

            val scriptContent = """
                #!/system/bin/sh
                PIDFILE="$PID_PATH"
                PAUSEFILE="$PAUSE_PATH"
                PKG="$TARGET_PKG"
                CMP="$TARGET_CMP"
                CMP_SHORT="$TARGET_CMP_SHORT"

                echo ${'$'}${'$'} > "${'$'}PIDFILE"
                LAST_RECOVERY=0

                while true; do
                    sleep 30
                    if [ -f "${'$'}PAUSEFILE" ]; then
                        continue
                    fi
                    pm path "${'$'}PKG" >/dev/null 2>&1 || { rm -f "${'$'}PIDFILE"; exit 0; }

                    DUMP=${'$'}(dumpsys accessibility 2>&1)
                    CRASHED=${'$'}(echo "${'$'}DUMP" | grep "Crashed services:")

                    BODY=${'$'}(echo "${'$'}CRASHED" | sed 's/.*Crashed services://' | tr -d '[:space:]' | sed 's/{}//g; s/\[\]//g')
                    if [ -n "${'$'}BODY" ]; then
                        PKG_PAT=${'$'}(echo "${'$'}PKG" | sed 's/\./\\./g')
                        if echo "${'$'}CRASHED" | grep -E "(^|[^A-Za-z0-9_.])"${'$'}PKG_PAT"([^A-Za-z0-9_.]|$)" >/dev/null 2>&1; then
                            NOW=${'$'}(date +%s)
                            DIFF=${'$'}((NOW - LAST_RECOVERY))
                            if [ "${'$'}DIFF" -ge 60 ]; then
                                LAST_RECOVERY=${'$'}NOW
                                killall -9 "${'$'}PKG" 2>/dev/null
                                am force-stop "${'$'}PKG" 2>/dev/null

                                CUR=${'$'}(settings get secure enabled_accessibility_services 2>/dev/null)
                                if [ "${'$'}CUR" != "null" ] && [ -n "${'$'}CUR" ]; then
                                    NEW=${'$'}(echo "${'$'}CUR" | tr ':' '\n' | grep -v "^${'$'}CMP${'$'}" | grep -v "^${'$'}CMP_SHORT${'$'}" | paste -sd ":" -)
                                    settings put secure enabled_accessibility_services "${'$'}NEW" 2>/dev/null
                                fi

                                sleep 0.5

                                CUR=${'$'}(settings get secure enabled_accessibility_services 2>/dev/null)
                                if [ -z "${'$'}CUR" ] || [ "${'$'}CUR" = "null" ]; then
                                    NEW="${'$'}CMP"
                                else
                                    NEW="${'$'}CUR:${'$'}CMP"
                                fi
                                settings put secure enabled_accessibility_services "${'$'}NEW" 2>/dev/null
                                settings put secure accessibility_enabled 1 2>/dev/null
                            fi
                        fi
                    fi
                done
            """.trimIndent()

            val writeCmd = "cat << 'EOF' > $SCRIPT_PATH\n$scriptContent\nEOF\nchmod 755 $SCRIPT_PATH"
            val writeProc = ElevatedTaskCloser.execShizuku(writeCmd)
            writeProc?.waitForOrKill()

            val startCmd = "setsid nohup sh $SCRIPT_PATH >/dev/null 2>&1 &"
            val startProc = ElevatedTaskCloser.execShizuku(startCmd)
            startProc?.waitForOrKill()

            Log.i(TAG, "Guard helper daemon started detached process successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting guard helper daemon", e)
        }
    }

    fun stop() {
        if (!ElevatedTaskCloser.isShizukuActive) {
            Log.w(TAG, "Shizuku is unavailable; cannot stop guard helper daemon")
            return
        }

        try {
            val stopCmd = "pkill -f $SCRIPT_PATH 2>/dev/null; rm -f $PID_PATH $PAUSE_PATH $SCRIPT_PATH 2>/dev/null"
            val proc = ElevatedTaskCloser.execShizuku(stopCmd)
            proc?.waitForOrKill()
            Log.i(TAG, "Guard helper daemon stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping guard helper daemon", e)
        }
    }

    fun setPaused(paused: Boolean) {
        if (!ElevatedTaskCloser.isShizukuActive) {
            Log.w(TAG, "Shizuku is unavailable; cannot setPaused($paused)")
            return
        }

        try {
            val cmd = if (paused) "touch $PAUSE_PATH" else "rm -f $PAUSE_PATH"
            val proc = ElevatedTaskCloser.execShizuku(cmd)
            proc?.waitForOrKill()
            Log.i(TAG, "Guard helper pause state set to: $paused")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting guard helper pause state ($paused)", e)
        }
    }
}
