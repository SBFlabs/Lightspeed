package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.overrideZeroTransition
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.ui.theme.LightspeedTheme

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class CentralCommandActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isActive: Boolean = false
            private set
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequestShizuku()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            autoReviveServiceIfShizukuAvailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true
        enableEdgeToEdge()

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}

        checkAndRequestShizuku()

        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        setContent {
            val backdrop = rememberDeckBackdropVisuals(this)
            SideEffect {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val lp = window.attributes
                    lp.blurBehindRadius = backdrop.blurBehindRadius
                    window.attributes = lp
                }
                window.setDimAmount(backdrop.dimAmount)

                // Coordinate the window-level blur with the liquid glass style
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val glassStyle = com.sbf.lightspeed.system.LightspeedPreferences.getDeckGlassStyle(this)
                    val extraBlur = if (glassStyle == "liquid") LIQUID_GLASS_BLUR_RADIUS else 0
                    if (extraBlur > 0) {
                        val lp2 = window.attributes
                        lp2.blurBehindRadius = (lp2.blurBehindRadius + extraBlur).coerceAtMost(100)
                        window.attributes = lp2
                    }
                }
            }

            LightspeedTheme {
                MainSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isActive = true
    }

    override fun onPause() {
        super.onPause()
        isActive = false
        // Clear all preview highlights whenever settings leaves the screen — covers
        // every exit path: X button, background tap, back gesture, or an external
        // action (gesture launching another app) that kills the activity unexpectedly.
        defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
            .apply()
        safeReloadPreferences()
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
        com.sbf.lightspeed.system.LightspeedIconManager.temporaryPickerCache.clear()
        com.sbf.lightspeed.system.LightspeedShortcutManager.temporaryPickerCache.clear()
        isActive = false
    }

    private fun checkAndRequestShizuku() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(com.sbf.lightspeed.system.ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                } else {
                    autoReviveServiceIfShizukuAvailable()
                }
            }
        } catch (_: Exception) {}
    }

    private fun autoReviveServiceIfShizukuAvailable() {
        if (LightspeedAccessibilityService.instance == null || !com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.sbf.lightspeed.system.LightspeedWatchdogEngine.reviveAccessibilityService(this@CentralCommandActivity)
            }
        }
    }
}
