package com.sbf.lightspeed

import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sbf.lightspeed.settings.MainSettingsScreen
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequest()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 60
        }
        window.setDimAmount(0.45f)

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}

        checkAndRequest()
        com.sbf.lightspeed.system.LightspeedShortcutManager.purgeCorruptedIcons(this)

        setContent {
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
        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
    }

    private fun checkAndRequest() {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(ElevatedTaskCloser.SHIZUKU_REQ_CODE)
            }
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (_: Exception) {}
    }
}
