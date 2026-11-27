package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.sbf.lightspeed.settings.BackdropBlurMode
import com.sbf.lightspeed.settings.BackdropBlurModeManager
import com.sbf.lightspeed.settings.CentralCommandActivity
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTheme

enum class SystemDeckMode {
    SONIC,
    VISTA
}

/**
 * Single consolidated Overlay WindowManager, OverlayLifecycleOwner, focusability & blur host
 * for both System Decks ([SystemDeckMode.SONIC] & [SystemDeckMode.VISTA]).
 */
object UnifiedSystemDeckHost {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var windowParams: WindowManager.LayoutParams? = null

    private val activeModeState = mutableStateOf(SystemDeckMode.VISTA)
    private val continuumEntranceState = mutableStateOf(false)
    private var activeBackHandler: (() -> Boolean)? = null

    fun isShowing(): Boolean = composeView != null

    fun isShowingMode(mode: SystemDeckMode): Boolean =
        composeView != null && activeModeState.value == mode

    fun updateWindowFocusable(focusable: Boolean) {
        mainHandler.post {
            val view = composeView ?: return@post
            val wm = windowManager ?: return@post
            val params = windowParams ?: return@post
            if (focusable) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                logSwallowed("UnifiedSystemDeckHost", "updateWindowFocusable", e)
            }
            if (focusable) {
                view.isFocusableInTouchMode = true
                view.requestFocus()
                view.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        activeBackHandler?.invoke() ?: false
                    } else false
                }
            } else {
                view.setOnKeyListener(null)
            }
        }
    }

    internal fun setBackHandler(handler: (() -> Boolean)?) {
        activeBackHandler = handler
    }

    fun show(
        service: AccessibilityService,
        mode: SystemDeckMode,
        fromContinuum: Boolean = false
    ) {
        mainHandler.post {
            activeModeState.value = mode
            continuumEntranceState.value = fromContinuum

            if (composeView != null) {
                return@post
            }

            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            CentralCommandBackdropBlur.prepareForCentralCommand(wm, ignoreZeroThemeBlur = true)
            val blurMode = BackdropBlurModeManager.getMode(service)
            val nativeSupported = BackdropBlurModeManager.isNativeBlurSupported(wm)
            CentralCommandActivity.updateCrossWindowBlurState(
                nativeSupported && blurMode != BackdropBlurMode.SCREENSHOT
            )

            val view = ComposeView(service)
            val owner = OverlayLifecycleOwner()
            owner.attachToView(view)
            owner.onCreate()
            owner.onStart()
            owner.onResume()
            lifecycleOwner = owner

            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                LightspeedTheme(forceDark = true) {
                    DeckTheme {
                        UnifiedSystemDeckDialog(
                            service = service,
                            mode = activeModeState.value,
                            fromContinuum = continuumEntranceState.value,
                            onSelectMode = { nextMode ->
                                activeModeState.value = nextMode
                                continuumEntranceState.value = false
                            },
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
            composeView = view

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setFitInsetsTypes(0)
                    setFitInsetsSides(0)
                    setFitInsetsIgnoringVisibility(true)
                }
            }
            windowParams = params

            try {
                wm.addView(view, params)
                CentralCommandBackdropBlur.registerHostOverlayView(view)
            } catch (e: Exception) {
                logSwallowed("UnifiedSystemDeckHost", "show.addView", e)
                dismiss()
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            activeBackHandler = null
            composeView?.let { view ->
                CentralCommandBackdropBlur.unregisterHostOverlayView(view)
                try {
                    lifecycleOwner?.onPause()
                    lifecycleOwner?.onStop()
                    lifecycleOwner?.onDestroy()
                    lifecycleOwner = null
                    view.setContent {}
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    logSwallowed("UnifiedSystemDeckHost", "dismiss.removeView", e)
                }
                composeView = null
                windowParams = null
            }
            windowManager = null
            if (!CentralCommandActivity.isActive) {
                CentralCommandBackdropBlur.onWindowHidden(false)
            }
        }
    }

    fun onSonicMasterToggle(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        if (!enabled) {
            if (isShowingMode(SystemDeckMode.SONIC)) {
                dismiss()
            }
            val wasVoiceActive = SonicVoiceIsolationEngine.isEnabled ||
                prefs.getBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, false)
            prefs.edit().putBoolean(SonicDeckManager.KEY_SONIC_VOICE_ISOLATION_STASHED, wasVoiceActive).apply()
            SonicVoiceIsolationEngine.stop(appCtx)
            SonicAppVolumeEngine.stopMonitoring(appCtx)
            SonicChannelRouter.teardown(appCtx)
            SonicVoiceIsolationEngine.teardown(appCtx)
        } else {
            if (SonicVoiceIsolationEngine.needsPrivilegedServiceInBackground(appCtx)) {
                SonicVoiceIsolationEngine.ensureBound(appCtx)
            }
            val shouldRestoreVoice = prefs.getBoolean(SonicDeckManager.KEY_SONIC_VOICE_ISOLATION_STASHED, false)
            if (shouldRestoreVoice) {
                SonicVoiceIsolationEngine.start(appCtx)
            }
            SonicAppVolumeEngine.startMonitoring(appCtx)
            SonicAppVolumeEngine.reapplyAllVolumesToUserService(appCtx)
            SonicAppVolumeEngine.refreshActiveAudioPlayers(appCtx)
            SonicChannelRouter.reapplyPrivilegedRoutes(appCtx)
        }
    }
}
