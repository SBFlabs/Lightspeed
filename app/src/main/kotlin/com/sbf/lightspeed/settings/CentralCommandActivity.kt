package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.system.logSwallowed

import android.content.Intent
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
import com.sbf.lightspeed.ui.theme.DeckTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.sbf.lightspeed.system.FlightLog
import com.sbf.lightspeed.system.ShizukuGate
import java.util.function.Consumer
import rikka.shizuku.Shizuku

class CentralCommandActivity : ComponentActivity() {
    companion object {
        @Volatile
        var isActive: Boolean = false
            private set

        @Volatile
        var activeInstance: CentralCommandActivity? = null
            private set

        var isCrossWindowBlurActive by mutableStateOf(true)
            private set

        internal fun updateCrossWindowBlurState(enabled: Boolean) {
            isCrossWindowBlurActive = enabled
        }
    }

    private var crossWindowBlurListener: Consumer<Boolean>? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkAndRequestShizuku()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            autoReviveServiceIfShizukuAvailable()
        }
    }

    internal fun updateLiveWindowBlur(): Int = applyWindowBlur()

    private fun applyWindowBlur(): Int {
        val snapshot = DeckThemeManager.getSnapshot(this)
        val hasFallbackBackdrop = CentralCommandBackdropBlur.backdropBitmap != null
        val blurMode = BackdropBlurModeManager.getMode(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nativeSupported = BackdropBlurModeManager.isNativeBlurSupported(windowManager)
            val useNative = BackdropBlurModeManager.shouldUseNativeBlur(windowManager, blurMode)
            val useScreenshot = BackdropBlurModeManager.shouldUseScreenshotBlur(windowManager, blurMode)

            isCrossWindowBlurActive = nativeSupported && blurMode != BackdropBlurMode.SCREENSHOT
            val targetRadius = if (useNative) {
                snapshot.windowBlurRadiusPx.coerceIn(0, 150)
            } else {
                0
            }
            if (useNative) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            }
            val lp = window.attributes
            lp.blurBehindRadius = targetRadius
            window.attributes = lp
            // When fallback screenshot blur is active inside our window, keep window dim at 0
            // so the OS does not dim behind our own blurred screenshot layer.
            window.setDimAmount(if (useScreenshot && hasFallbackBackdrop) 0f else snapshot.dimAmount)
            return targetRadius
        } else {
            isCrossWindowBlurActive = false
            window.setDimAmount(snapshot.dimAmount)
            return 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true
        activeInstance = this
        enableEdgeToEdge()
        CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) { logSwallowed("CentralCommandActivity", "onCreate:50", e) }

        checkAndRequestShizuku()

        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = Consumer<Boolean> { enabled ->
                isCrossWindowBlurActive = enabled
                applyWindowBlur()
            }
            crossWindowBlurListener = listener
            try {
                windowManager.addCrossWindowBlurEnabledListener(listener)
            } catch (e: Exception) {
                logSwallowed("CentralCommandActivity", "addCrossWindowBlurEnabledListener", e)
            }
        } else {
            isCrossWindowBlurActive = false
        }

        val initialRadius = applyWindowBlur()
        val initialStyleId = DeckThemeManager.getStyleId(this)
        FlightLog.log(
            "central_command",
            isCrossWindowBlurActive,
            "Central Command opened: api=${Build.VERSION.SDK_INT}, crossWindowBlur=$isCrossWindowBlurActive, radius=${initialRadius}px, style=$initialStyleId"
        )

        setContent {
            val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
            val blurActive = isCrossWindowBlurActive
            SideEffect {
                // Coordinate the window-level blur with the active snapshot and cross-window blur state
                snapshot
                blurActive
                applyWindowBlur()
            }

            LightspeedTheme {
                DeckTheme {
                    MainSettingsScreen()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowBlur()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val isInternalNavigation = intent.hasExtra("target_section") || intent.hasExtra("target_tab")
        val underlyingScreenChanged = !isActive && !isInternalNavigation
        if (underlyingScreenChanged) {
            CentralCommandBackdropBlur.onWindowHidden(false)
        }
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        if (CentralCommandBackdropBlur.backdropBitmap == null) {
            CentralCommandBackdropBlur.prepareForCentralCommand(windowManager)
        }
        applyWindowBlur()
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurListener?.let { listener ->
                try {
                    windowManager.removeCrossWindowBlurEnabledListener(listener)
                } catch (e: Exception) {
                    logSwallowed("CentralCommandActivity", "removeCrossWindowBlurEnabledListener", e)
                }
            }
            crossWindowBlurListener = null
        }
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) { logSwallowed("CentralCommandActivity", "onDestroy:120", e) }
        com.sbf.lightspeed.system.LightspeedIconManager.temporaryPickerCache.clear()
        com.sbf.lightspeed.system.LightspeedShortcutManager.temporaryPickerCache.clear()
        CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
        if (activeInstance === this) {
            activeInstance = null
        }
        isActive = false
    }

    private fun checkAndRequestShizuku() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ShizukuGate.requestIfAllowed()
                } else {
                    autoReviveServiceIfShizukuAvailable()
                }
            }
        } catch (e: Exception) { logSwallowed("CentralCommandActivity", "checkAndRequestShizuku:135", e) }
    }

    private fun autoReviveServiceIfShizukuAvailable() {
        if (LightspeedAccessibilityService.instance == null || !com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.sbf.lightspeed.system.LightspeedWatchdogEngine.reviveAccessibilityService(this@CentralCommandActivity)
            }
        }
    }
}
