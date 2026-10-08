package com.sbf.lightspeed.system

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.sbf.lightspeed.LightspeedAccessibilityService

object CallStateTracker {
    private const val TAG = "CallStateTracker"

    @Volatile
    var lastHideOnCall: Boolean? = null

    @Volatile
    private var lastInCall: Boolean? = null

    private var modeListener: Any? = null

    fun isCallMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL ||
        mode == AudioManager.MODE_RINGTONE

    fun isCallActive(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val mode = audioManager?.mode ?: AudioManager.MODE_NORMAL
            isCallMode(mode)
        } catch (e: Exception) {
            logSwallowed(TAG, "isCallActive", e)
            false
        }
    }

    fun register(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (modeListener != null) return
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
                val listener = AudioManager.OnModeChangedListener { mode ->
                    val inCall = isCallMode(mode)
                    if (lastInCall != inCall) {
                        lastInCall = inCall
                        Log.i(TAG, "call mode changed: $mode inCall=$inCall")
                        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                    }
                }
                audioManager.addOnModeChangedListener(context.mainExecutor, listener)
                modeListener = listener
            } catch (e: Exception) {
                logSwallowed(TAG, "register", e)
            }
        } else {
            Log.i(TAG, "API < 31: CallStateTracker registration bypassed, using window-change re-evaluation fallback")
        }
    }

    fun unregister(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = modeListener as? AudioManager.OnModeChangedListener ?: return
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                audioManager?.removeOnModeChangedListener(listener)
            } catch (e: Exception) {
                logSwallowed(TAG, "unregister", e)
            } finally {
                modeListener = null
                lastInCall = null
            }
        }
    }
}
