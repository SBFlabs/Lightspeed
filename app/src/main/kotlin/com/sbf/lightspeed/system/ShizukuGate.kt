package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuGate {
    private const val PREF_NAME = "shizuku_gate"
    private const val KEY_MODE = "mode"
    private const val KEY_DENIED = "denied"
    const val MODE_UNSET = 0
    const val MODE_FULL = 1
    const val MODE_ACCESSIBILITY_ONLY = 2

    private var prefs: SharedPreferences? = null
    private var requestedThisProcess = false
    private var listenerRegistered = false

    private val resultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ElevatedTaskCloser.SHIZUKU_REQ_CODE) {
            setDenied(grantResult != PackageManager.PERMISSION_GRANTED)
        }
    }

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun getMode(): Int = prefs?.getInt(KEY_MODE, MODE_UNSET) ?: MODE_UNSET
    fun setMode(mode: Int) { prefs?.edit()?.putInt(KEY_MODE, mode)?.apply() }
    fun isAccessibilityOnly(): Boolean = getMode() == MODE_ACCESSIBILITY_ONLY
    private fun isDenied(): Boolean = prefs?.getBoolean(KEY_DENIED, false) ?: false
    private fun setDenied(value: Boolean) { prefs?.edit()?.putBoolean(KEY_DENIED, value)?.apply() }

    fun requestIfAllowed(): Boolean {
        if (getMode() == MODE_UNSET) return false
        if (isAccessibilityOnly()) return false
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setDenied(false)
                return false
            }
        } catch (_: Exception) { }
        if (isDenied() || requestedThisProcess) return false
        requestedThisProcess = true
        return try {
            if (!listenerRegistered) {
                listenerRegistered = true
                Shizuku.addRequestPermissionResultListener(resultListener)
            }
            Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
            true
        } catch (_: Exception) {
            requestedThisProcess = false
            false
        }
    }
    fun needsChoice(): Boolean {
        if (getMode() != MODE_UNSET) return false
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setMode(MODE_FULL)
                return false
            }
        } catch (_: Exception) { }
        return true
    }

    fun chooseFull() {
        setMode(MODE_FULL)
        setDenied(false)
        requestedThisProcess = false
        requestIfAllowed()
    }

    fun chooseAccessibilityOnly() { setMode(MODE_ACCESSIBILITY_ONLY) }

    private var lastNeedsToast = 0L
    fun blockedByMode(context: Context): Boolean {
        if (!isAccessibilityOnly()) return false
        if (ElevatedTaskCloser.isShizukuActive) return false
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNeedsToast > 3000) {
            lastNeedsToast = now
            val app = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(app, "This needs Shizuku. Turn off \"Accessibility Service only\" in System Override.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        return true
    }

}
