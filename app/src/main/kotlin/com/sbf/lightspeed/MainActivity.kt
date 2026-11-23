package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
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
import androidx.compose.runtime.collectAsState
import com.sbf.lightspeed.settings.MainSettingsScreen
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import rikka.shizuku.Shizuku
import com.sbf.lightspeed.system.ShizukuGate

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
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.registerHostWindow(window)
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)

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
        } catch (e: Exception) { logSwallowed("MainActivity", "onCreate:59", e) }

        checkAndRequest()
        com.sbf.lightspeed.system.LightspeedShortcutManager.purgeCorruptedIcons(this)
        lifecycleScope.launch(Dispatchers.Default) {
            LightspeedToggleActivity.updateDynamicShortcuts(this@MainActivity)
        }

        setContent {
            val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.snapshotFlow.collectAsState().value
            val liveBackdropOverride = com.sbf.lightspeed.settings.DeckThemeManager.liveBackdropOpacityOverride.collectAsState().value
            val blurMode = com.sbf.lightspeed.settings.BackdropBlurModeManager.modeFlow.collectAsState().value
            val fallbackBitmap = com.sbf.lightspeed.settings.CentralCommandBackdropBlur.backdropBitmap
            androidx.compose.runtime.LaunchedEffect(snapshot, liveBackdropOverride, fallbackBitmap, blurMode) {
                val effectiveDimAmount = (liveBackdropOverride ?: snapshot.dimAmount).coerceIn(0f, 0.95f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val activeBlurMode = com.sbf.lightspeed.settings.BackdropBlurModeManager.getMode(this@MainActivity)
                    val nativeSupported = com.sbf.lightspeed.settings.BackdropBlurModeManager.isNativeBlurSupported(windowManager)
                    val useNative = com.sbf.lightspeed.settings.BackdropBlurModeManager.shouldUseNativeBlur(windowManager, activeBlurMode) &&
                        snapshot.windowBlurRadiusPx > 0
                    val useScreenshot = com.sbf.lightspeed.settings.BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, activeBlurMode) &&
                        snapshot.windowBlurRadiusPx > 0

                    com.sbf.lightspeed.settings.CentralCommandActivity.updateCrossWindowBlurState(nativeSupported && activeBlurMode != com.sbf.lightspeed.settings.BackdropBlurMode.SCREENSHOT)
                    if (useNative) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        val lp = window.attributes
                        lp.blurBehindRadius = snapshot.windowBlurRadiusPx.coerceIn(0, 150)
                        window.attributes = lp
                        com.sbf.lightspeed.settings.BackdropBlurModeManager.applyWindowDimAmount(window, effectiveDimAmount)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        val lp = window.attributes
                        lp.blurBehindRadius = 0
                        window.attributes = lp
                        val targetDim = if (useScreenshot && fallbackBitmap != null) 0f else effectiveDimAmount
                        com.sbf.lightspeed.settings.BackdropBlurModeManager.applyWindowDimAmount(window, targetDim)
                    }
                } else {
                    com.sbf.lightspeed.settings.CentralCommandActivity.updateCrossWindowBlurState(false)
                    com.sbf.lightspeed.settings.BackdropBlurModeManager.applyWindowDimAmount(window, effectiveDimAmount)
                }
            }

            LightspeedTheme {
                DeckTheme {
                    MainSettingsScreen()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (com.sbf.lightspeed.settings.CentralCommandBackdropBlur.backdropBitmap == null) {
            com.sbf.lightspeed.settings.CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)
        }
        checkAndRequest()
    }

    override fun onPause() {
        super.onPause()
        defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
            .apply()
        safeReloadPreferences()
    }

    private fun checkAndRequest() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                ShizukuGate.requestIfAllowed()
                } else {
                    autoReviveServiceIfShizukuAvailable()
                }
            }
        } catch (e: Exception) { logSwallowed("MainActivity", "checkAndRequest:116", e) }
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
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.unregisterHostWindow(window)
        com.sbf.lightspeed.settings.CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) { logSwallowed("MainActivity", "onDestroy:138", e) }
    }
}
