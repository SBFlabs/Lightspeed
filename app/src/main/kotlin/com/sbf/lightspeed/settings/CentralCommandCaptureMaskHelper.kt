package com.sbf.lightspeed.settings

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Single hide -> wait 1–2 frames -> capture -> restore coordinator for screenshot-backed
 * backdrop captures. Ensures Lightspeed activities, dialogs, sheets, and accessibility overlays
 * never appear inside their own backdrop snapshot during theme transitions or HUD triggers.
 */
internal object CentralCommandCaptureMaskHelper {

    private data class WindowMaskState(
        val prevVisibility: Int,
        val prevAlpha: Float,
        val prevDimAmount: Float,
        val hadDimFlag: Boolean,
        val hadBlurFlag: Boolean,
        val prevBlurBehindRadius: Int
    )

    private data class ViewMaskState(
        val prevVisibility: Int,
        val prevAlpha: Float
    )

    private val registeredHostWindows = WeakHashMap<Window, Boolean>()
    private val registeredHostViews = WeakHashMap<View, Boolean>()
    private val hiddenWindows = mutableMapOf<Window, WindowMaskState>()
    private val hiddenViews = mutableMapOf<View, ViewMaskState>()
    private val tokenGen = AtomicLong(0L)
    @Volatile
    private var activeMaskToken: Long = 0L

    fun registerWindow(window: Window?) {
        if (window == null) return
        synchronized(registeredHostWindows) {
            registeredHostWindows[window] = true
        }
    }

    fun unregisterWindow(window: Window?) {
        if (window == null) return
        synchronized(registeredHostWindows) {
            registeredHostWindows.remove(window)
        }
        hiddenWindows.remove(window)
    }

    fun hasRegisteredWindows(): Boolean {
        return synchronized(registeredHostWindows) {
            registeredHostWindows.isNotEmpty()
        }
    }

    fun registerOverlayView(view: View?) {
        if (view == null) return
        synchronized(registeredHostViews) {
            registeredHostViews[view] = true
        }
    }

    fun unregisterOverlayView(view: View?) {
        if (view == null) return
        synchronized(registeredHostViews) {
            registeredHostViews.remove(view)
        }
        hiddenViews.remove(view)
    }

    fun hasVisibleHostSurfaces(): Boolean {
        val windows = synchronized(registeredHostWindows) { registeredHostWindows.keys.toList() }
        if (windows.any { w ->
                try {
                    w.decorView.visibility == View.VISIBLE && w.decorView.alpha > 0f
                } catch (_: Exception) {
                    false
                }
            }
        ) {
            return true
        }
        val views = synchronized(registeredHostViews) { registeredHostViews.keys.toList() }
        return views.any { it.isAttachedToWindow && it.visibility == View.VISIBLE && it.alpha > 0f }
    }

    /**
     * Hides all visible Lightspeed host windows and overlay views, waits [frameWaitCount]
     * vsync frames via [Choreographer] (after optional [extraPreDelayMs]), invokes [launchCapture]
     * with an idempotent restore callback, and restores all surfaces cleanly once capture finishes.
     */
    fun runMaskedCapture(
        mainHandler: Handler,
        hideActiveHosts: Boolean = true,
        extraPreDelayMs: Long = 0L,
        frameWaitCount: Int = 2,
        launchCapture: (restoreCallback: () -> Unit) -> Unit
    ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post {
                runMaskedCapture(mainHandler, hideActiveHosts, extraPreDelayMs, frameWaitCount, launchCapture)
            }
            return
        }

        val token = tokenGen.incrementAndGet()
        activeMaskToken = token

        val didHideAny = if (hideActiveHosts) hideAllVisibleSurfaces() else false
        val restored = AtomicBoolean(false)
        val restoreCallback: () -> Unit = {
            if (restored.compareAndSet(false, true)) {
                if (Looper.myLooper() == Looper.getMainLooper()) {
                    restoreIfOwner(token)
                } else {
                    mainHandler.post { restoreIfOwner(token) }
                }
            }
        }

        val startFrameWait = Runnable {
            if (activeMaskToken != token) {
                restoreCallback()
                return@Runnable
            }
            val frames = if (didHideAny || hiddenWindows.isNotEmpty() || hiddenViews.isNotEmpty()) {
                frameWaitCount.coerceIn(1, 2)
            } else {
                0
            }
            if (frames <= 0) {
                launchCapture(restoreCallback)
            } else {
                awaitVsyncFrames(frames) {
                    if (activeMaskToken != token) {
                        restoreCallback()
                    } else {
                        launchCapture(restoreCallback)
                    }
                }
            }
        }

        if (extraPreDelayMs > 0L && (didHideAny || hiddenWindows.isNotEmpty() || hiddenViews.isNotEmpty())) {
            mainHandler.postDelayed(startFrameWait, extraPreDelayMs)
        } else {
            startFrameWait.run()
        }
    }

    fun restoreAll(mainHandler: Handler) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { restoreAll(mainHandler) }
            return
        }
        activeMaskToken = tokenGen.incrementAndGet()
        restoreMaskedSurfaces()
    }

    private fun restoreIfOwner(token: Long) {
        if (activeMaskToken != token) return
        restoreMaskedSurfaces()
    }

    private fun hideAllVisibleSurfaces(): Boolean {
        var hidAny = false
        val windows = synchronized(registeredHostWindows) { registeredHostWindows.keys.toList() }
        for (w in windows) {
            try {
                val decor = w.decorView
                if (decor.visibility == View.VISIBLE && !hiddenWindows.containsKey(w)) {
                    val attrs = w.attributes
                    val flags = attrs?.flags ?: 0
                    val hadDim = (flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0
                    val hadBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (flags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
                    } else false
                    val prevRadius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        attrs?.blurBehindRadius ?: 0
                    } else 0
                    hiddenWindows[w] = WindowMaskState(
                        prevVisibility = decor.visibility,
                        prevAlpha = decor.alpha,
                        prevDimAmount = attrs?.dimAmount ?: 0f,
                        hadDimFlag = hadDim,
                        hadBlurFlag = hadBlur,
                        prevBlurBehindRadius = prevRadius
                    )
                    decor.alpha = 0f
                    decor.visibility = View.INVISIBLE
                    BackdropBlurModeManager.applyWindowDimAmount(w, 0f)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        val lp = w.attributes
                        if (lp != null && lp.blurBehindRadius != 0) {
                            lp.blurBehindRadius = 0
                            w.attributes = lp
                        }
                    }
                    hidAny = true
                }
            } catch (_: Exception) {}
        }

        val overlayViews = LinkedHashSet<View>()
        synchronized(registeredHostViews) {
            overlayViews.addAll(registeredHostViews.keys)
        }
        LightspeedAccessibilityService.instance?.collectVisibleOverlayViews()?.let {
            overlayViews.addAll(it)
        }
        overlayViews.addAll(LightspeedBotTrackpadOverlay.collectVisibleOverlayViews())

        for (v in overlayViews) {
            try {
                if (v.isAttachedToWindow && v.visibility == View.VISIBLE && v.alpha > 0f && !hiddenViews.containsKey(v)) {
                    hiddenViews[v] = ViewMaskState(
                        prevVisibility = v.visibility,
                        prevAlpha = v.alpha
                    )
                    // Alpha-only masking keeps RenderNode transparent without relayoutWindow or ACTION_CANCEL
                    v.alpha = 0f
                    hidAny = true
                }
            } catch (_: Exception) {}
        }
        return hidAny
    }

    private fun restoreMaskedSurfaces() {
        val hadWindows = hiddenWindows.isNotEmpty()
        if (hadWindows) {
            val snapshotHidden = hiddenWindows.entries.toList()
            hiddenWindows.clear()
            for ((w, state) in snapshotHidden) {
                try {
                    w.decorView.visibility = state.prevVisibility
                    w.decorView.alpha = state.prevAlpha
                    if (state.hadDimFlag) {
                        w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    } else {
                        w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    }
                    w.setDimAmount(state.prevDimAmount)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (state.hadBlurFlag) {
                            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        } else {
                            w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                        }
                        val lp = w.attributes
                        if (lp != null && lp.blurBehindRadius != state.prevBlurBehindRadius) {
                            lp.blurBehindRadius = state.prevBlurBehindRadius
                            w.attributes = lp
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        if (hiddenViews.isNotEmpty()) {
            val viewSnapshot = hiddenViews.entries.toList()
            hiddenViews.clear()
            for ((v, state) in viewSnapshot) {
                try {
                    v.alpha = state.prevAlpha
                } catch (_: Exception) {}
            }
        }
        if (hadWindows) {
            CentralCommandActivity.activeInstance?.updateLiveWindowBlur()
        }
    }

    private fun awaitVsyncFrames(remainingFrames: Int, onDone: () -> Unit) {
        if (remainingFrames <= 0) {
            onDone()
            return
        }
        try {
            Choreographer.getInstance().postFrameCallback {
                awaitVsyncFrames(remainingFrames - 1, onDone)
            }
        } catch (_: Exception) {
            onDone()
        }
    }
}
