package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.sbf.lightspeed.system.*
import kotlin.math.roundToInt

class LightspeedAccessibilityService : AccessibilityService() {

    companion object {
        const val TAG = "LightspeedService"
        internal var instanceRef: java.lang.ref.WeakReference<LightspeedAccessibilityService>? = null

        @Volatile var lastAppPackage: String? = null
        @Volatile var lastAppUptimeMs: Long = 0L

        @Volatile var lastBayDarkMs: Long = 0L
        fun noteBayDark() { lastBayDarkMs = System.currentTimeMillis() }

        val instance: LightspeedAccessibilityService?
            get() = instanceRef?.get()

        val isAlive: Boolean
            get() = instanceRef?.get() != null

        inline fun withService(block: LightspeedAccessibilityService.() -> Unit) {
            val svc = instance
            if (svc != null) {
                svc.block()
            } else {
                Log.w(TAG, "withService: instance unavailable — action dropped (WeakRef GC'd or service not started)")
            }
        }
    }

    internal var windowManager: WindowManager? = null
    internal var displayManager: DisplayManager? = null
    internal val handler = Handler(Looper.getMainLooper())

    // Edge Sidebar Overlay
    internal var overlayView: LightspeedCruiseOverlay? = null
    internal lateinit var windowParams: WindowManager.LayoutParams
    internal val edgeWidthPx: Int by lazy { (17f * resources.displayMetrics.density).roundToInt() }

    // Status Bar Overlay
    internal var statusBarOverlayView: LightspeedStatusBarOverlay? = null
    internal lateinit var statusBarWindowParams: WindowManager.LayoutParams

    // Sensor Deck Touch Overlay
    internal var sensorTouchOverlayView: LightspeedSensorDeckTouchOverlay? = null
    internal lateinit var sensorTouchWindowParams: WindowManager.LayoutParams
    @Volatile
    var isNotificationShadeActive: Boolean = false
        internal set

    // Dedicated Notch Pill Overlay
    internal var notchOverlayView: LightspeedNotchOverlay? = null
    internal lateinit var notchWindowParams: WindowManager.LayoutParams

    // Left Deflector Wing Overlay
    internal var leftWingOverlayView: LightspeedLeftWingOverlay? = null
    internal lateinit var leftWingWindowParams: WindowManager.LayoutParams

    // Floating Media Scrubber Overlay
    internal var mediaScrubberOverlayView: LightspeedMediaScrubberOverlay? = null

    // Hardware Orientation Anchor
    internal var orientationAnchorView: View? = null
    internal var orientationAnchorParams: WindowManager.LayoutParams? = null

    // Refueling Bay Sleep Blackout Overlay
    internal var baySleepBlackoutView: View? = null
    internal var baySleepBlackoutParams: WindowManager.LayoutParams? = null
    internal var lastBaySleepHeartbeat: Long = 0L
    internal var baySleepSwapView: View? = null
    internal var baySleepSwapAt: Long = 0L
    internal val baySleepRaiseRunnable = object : Runnable {
        override fun run() {
            try {
                val now = System.currentTimeMillis()
                if (baySleepBlackoutView == null || now - lastBaySleepHeartbeat > 5000L) {
                    hideBaySleepBlackout()
                    return
                }
                val cur = baySleepBlackoutView
                if (cur != null) {
                    if (baySleepSwapView !== cur) {
                        baySleepSwapView = cur
                        baySleepSwapAt = now
                    } else if (now - baySleepSwapAt >= 30_000L) {
                        val oldParams = baySleepBlackoutParams
                        if (oldParams != null) {
                            val newView = View(this@LightspeedAccessibilityService).apply { setBackgroundColor(android.graphics.Color.BLACK) }
                            val newParams = WindowManager.LayoutParams().apply { copyFrom(oldParams) }
                            try {
                                windowManager?.addView(newView, newParams)
                                baySleepBlackoutView = newView
                                baySleepBlackoutParams = newParams
                                baySleepSwapView = newView
                                baySleepSwapAt = now
                                try { windowManager?.removeView(cur) } catch (e: Exception) { logSwallowed("LightspeedAccessibilityService", "baySleep:removeOld", e) }
                            } catch (e: Exception) {
                                logSwallowed("LightspeedAccessibilityService", "baySleep:swap", e)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                logSwallowed("LightspeedAccessibilityService", "baySleepRaiseRunnable", e)
            }
            handler.postDelayed(this, 2000L)
        }
    }

    internal var systemStateReceiver: BroadcastReceiver? = null
    internal var rotationContentObserver: ContentObserver? = null

    internal val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            handleDisplayOrientationChange()
        }
    }

    internal val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != null && (key.startsWith("pref_statusbar_") || key.startsWith("pref_horizon_rail_") || key.startsWith("pref_sub_") || key.startsWith("pref_section_statusbar") || key.startsWith("pref_macro_action_STATUSBAR"))) {
            updateStatusBarOverlayFromPrefs(prefs)
            updateSensorTouchOverlayFromPrefs(prefs)
            statusBarOverlayView?.postInvalidate()
        }
        if (key != null && (key.startsWith("pref_notch_") || key.startsWith("pref_telemetry_"))) {
            notchOverlayView?.postInvalidate()
        }
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_top") || key.startsWith("pref_section_center") || key.startsWith("pref_section_bottom"))) {
            updateSidebarOverlayFromPrefs(prefs)
        }
        if (key != null && (key.startsWith("pref_sidebar_left_") || key.startsWith("pref_section_left_") || key.startsWith("pref_macro_action_LEFT_"))) {
            updateLeftWingOverlayFromPrefs(prefs)
        }
        if (key == LightspeedPreferences.KEY_MASTER_FLIGHT_ARMED ||
            key == LightspeedPreferences.KEY_DEFLECTOR_LEFT_ENABLED ||
            key == LightspeedPreferences.KEY_DEFLECTOR_RIGHT_ENABLED) {
            updateOverlaysVisibility()
            LightspeedFlightNotificationManager.update(this)
        }
        if (key == LightspeedPreferences.KEY_FLIGHT_NOTIFICATION_ENABLED) {
            LightspeedFlightNotificationManager.update(this)
        }
        if (key != null && key.startsWith("pref_back_tap_")) {
            LightspeedBackTapEngine.reloadPreferences()
        }
        if (key == LightspeedPreferences.KEY_ORIENTATION_OVERLAY_POLICY) {
            handleDisplayOrientationChange()
        }
        if (key == "pref_service_intentionally_stopped") {
            val isStopped = prefs.getBoolean("pref_service_intentionally_stopped", false)
            LightspeedGuardHelper.setPaused(this, isStopped)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        handleDisplayOrientationChange()
        resyncOverlayMetrics()
        com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = java.lang.ref.WeakReference(this)
        initializeServiceSession()
    }

    fun reloadPreferences() {
        val prefs = defaultPrefs()
        updateStatusBarOverlayFromPrefs(prefs)
        updateSensorTouchOverlayFromPrefs(prefs)
        notchOverlayView?.postInvalidate()
        updateSidebarOverlayFromPrefs(prefs)
        updateLeftWingOverlayFromPrefs(prefs)
        overlayView?.postInvalidate()
        statusBarOverlayView?.updateRenderCache()
        statusBarOverlayView?.postInvalidate()
        LightspeedBackTapEngine.reloadPreferences()
        LightspeedKeyEngine.startShizukuPowerMonitor(this)
        PowerLongPressTakeover.sync(this)
        if (!LightspeedKeyEngine.isPowerEnabled(this))
            LightspeedPowerKeyEngine.resetPowerState()
        LightspeedWatchdogEngine.initSentinel(this)
        LightspeedOrientationManager.evaluateGravityCascade(this)
        updateOverlaysVisibility()
        LightspeedFlightNotificationManager.update(this)
    }

    fun reopenCockpitHangar(setIndex: Int = -1) {
        overlayView?.post {
            overlayView?.openHangarDirectly(setIndex)
        }
    }

    fun startCruiseFromLeft(rawX: Float, rawY: Float) {
        overlayView?.isDispatchingFromLeftFlankView = true
        try {
            overlayView?.startCruiseFromFlank(isLeft = true, startRawX = rawX, startRawY = rawY)
        } finally {
            overlayView?.isDispatchingFromLeftFlankView = false
        }
    }

    fun forwardTouchEventToCruise(isLeft: Boolean, event: MotionEvent): Boolean {
        if (isLeft) {
            overlayView?.isDispatchingFromLeftFlankView = true
            try {
                return overlayView?.handleFlankTouchEvent(isLeft = isLeft, event = event) ?: false
            } finally {
                overlayView?.isDispatchingFromLeftFlankView = false
            }
        }
        return overlayView?.handleFlankTouchEvent(isLeft = isLeft, event = event) ?: false
    }

    fun openCockpitFromLeft() {
        overlayView?.post {
            overlayView?.openHangarFromFlank(isLeftFlank = true)
        }
    }

    fun triggerDeflectorsGlow(durationMs: Long = -1L) = triggerDeflectorsGlowInternal(durationMs)
    fun triggerDeflectorGlow(isLeft: Boolean, durationMs: Long = -1L) = triggerDeflectorGlowInternal(isLeft, durationMs)
    fun updateOverlaysVisibility(isLocked: Boolean? = null, currentPkg: String? = null) = updateOverlaysVisibilityInternal(isLocked, currentPkg)
    fun scheduleGeometryResync() = scheduleGeometryResyncInternal()
    fun updateForcedOrientation(orientation: Int) = updateForcedOrientationInternal(orientation)
    fun updateWindowLayout(expand: Boolean) = updateWindowLayoutInternal(expand)
    fun updateNotchWindowBounds(isExpanded: Boolean, targetX: Int, targetY: Int, targetWidth: Int, targetHeight: Int, isVisible: Boolean = true) = updateNotchWindowBoundsInternal(isExpanded, targetX, targetY, targetWidth, targetHeight, isVisible)

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        if (!LightspeedPreferences.isMasterFlightArmed(this)) return false
        return LightspeedKeyEngine.onKeyEvent(this, event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            LightspeedForegroundTracker.handleWindowStateChanged(this, event)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "onInterrupt: Accessibility feedback interrupted by framework")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instanceRef?.get() === this) {
            instanceRef?.clear()
            instanceRef = null
        }
        try {
            val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            val expectedLong = ComponentName(this, LightspeedAccessibilityService::class.java).flattenToString()
            val expectedShort = ComponentName(this, LightspeedAccessibilityService::class.java).flattenToShortString()
            val isStillEnabled = enabledServices.split(":").map { it.trim() }.any { entry ->
                entry.equals(expectedLong, ignoreCase = true) ||
                entry.equals(expectedShort, ignoreCase = true) ||
                ComponentName.unflattenFromString(entry)?.let { cn ->
                    cn.packageName == packageName && cn.className == LightspeedAccessibilityService::class.java.name
                } == true
            }
            if (!isStillEnabled) {
                defaultPrefs().edit().putBoolean("pref_service_intentionally_stopped", true).apply()
                LightspeedGuardHelper.setPaused(this, true)
                Log.i(TAG, "onDestroy: Lightspeed service removed from enabled_accessibility_services. Marked pref_service_intentionally_stopped = true")
            }

            val prefs = defaultPrefs()
            prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            LightspeedKeyEngine.stopShizukuPowerMonitor()
            PowerLongPressTakeover.release(this)
            LightspeedWatchdogEngine.stopSentinel()
            LightspeedWatchdogEngine.unregisterFastTriggers(this)
            SonicDeckManager.dismiss(this)
            VistaDeckManager.dismiss(this)
            com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine.resetAllAppVolumesToUnity()
            com.sbf.lightspeed.system.sonic.SonicChannelRouter.unregisterPolicy()
            com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine.teardown(this)
            com.sbf.lightspeed.system.display.LightspeedConsciousAirlockOverlay.dismiss()
            LightspeedSpacePodOverlay.hide()
            CallStateTracker.unregister(this)
            teardown()
        } catch (e: Exception) {
            Log.e(TAG, "Exception during service onDestroy: ${e.message}", e)
        }
    }
}
