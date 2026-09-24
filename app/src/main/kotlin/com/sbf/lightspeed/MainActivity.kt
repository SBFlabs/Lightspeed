package com.sbf.lightspeed

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.runtime.SideEffect
import com.sbf.lightspeed.settings.MainSettingsScreen
import com.sbf.lightspeed.settings.rememberDeckBackdropVisuals
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequest()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            autoReviveServiceIfShizukuAvailable()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        // 1. Ensure core defaults are initialized on clean installs
        com.sbf.lightspeed.system.LightspeedPreferences.initializeDefaults(this)

        // 2. Request Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}

        checkAndRequest()
        com.sbf.lightspeed.system.LightspeedShortcutManager.purgeCorruptedIcons(this)
        lifecycleScope.launch(Dispatchers.Default) {
            LightspeedToggleActivity.updateDynamicShortcuts(this@MainActivity)
        }

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
            }

            LightspeedTheme {
                MainSettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAndRequest()
    }

    override fun onPause() {
        super.onPause()
        defaultPrefs().edit()
            .putBoolean("pref_sidebar_left_preview", false)
            .putBoolean("pref_statusbar_preview", false)
            .putBoolean("pref_sidebar_preview", false)
            .apply()
        safeReloadPreferences()
    }

    private fun checkAndRequest() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                    Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
                } else {
                    autoReviveServiceIfShizukuAvailable()
                }
            }
        } catch (_: Exception) {}
    }

    private fun autoReviveServiceIfShizukuAvailable() {
        if (LightspeedAccessibilityService.instance == null || !com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.sbf.lightspeed.system.LightspeedWatchdogEngine.reviveAccessibilityService(this@MainActivity)
            }
        }
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
    }
}
