package com.sbf.lightspeed.system

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.LightspeedRefuelingActivity
import com.sbf.lightspeed.LightspeedStatusBarOverlay
import com.sbf.lightspeed.dismissOverlay
import com.sbf.lightspeed.overrideZeroTransition
import java.util.Collections

/**
 * Coordinates single-gesture dismissal of all stacked Lightspeed overlays,
 * Central Command floating windows, and transient modal activities when the
 * user long-presses the outside dimmed scrim.
 */
object LightspeedOverlayDismissCoordinator {
    private const val REOPEN_SUPPRESS_WINDOW_MS = 1500L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeActivities = Collections.synchronizedList(mutableListOf<Activity>())

    @Volatile
    private var initialized = false

    @Volatile
    private var suppressReopenUntilUptimeMs: Long = 0L

    fun init(application: Application) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    synchronized(activeActivities) {
                        if (activeActivities.none { it === activity }) {
                            activeActivities.add(activity)
                        }
                    }
                }

                override fun onActivityStarted(activity: Activity) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

                override fun onActivityDestroyed(activity: Activity) {
                    synchronized(activeActivities) {
                        activeActivities.removeAll { it === activity }
                    }
                }
            })
        }
    }

    fun shouldSuppressReopen(): Boolean =
        SystemClock.uptimeMillis() < suppressReopenUntilUptimeMs

    fun dismissAll(context: Context) {
        suppressReopenUntilUptimeMs = SystemClock.uptimeMillis() + REOPEN_SUPPRESS_WINDOW_MS
        LightspeedHapticEngine.heavyClick(context)

        runCatching {
            context.defaultPrefs().edit()
                .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
                .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_BOTTOM_STATUSBAR_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_SECTION_SENSOR_DECK_EXPANDED, false)
                .putBoolean(LightspeedPreferences.KEY_SECTION_TELEMETRY_EXPANDED, false)
                .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_GEOM, false)
                .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_COLOR, false)
                .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_TEXT, false)
                .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_CUSTOM, false)
                .apply()
            safeReloadPreferences()
        }

        val runDismiss = {
            LightspeedAccessibilityService.instance?.let { svc ->
                svc.overlayView?.dismissOverlay()
                SonicDeckManager.dismiss(svc)
                VistaDeckManager.dismiss(svc)
            } ?: run {
                SonicDeckManager.dismiss()
                VistaDeckManager.dismiss()
            }
            LightspeedStatusBarOverlay.dismissActionHud(0L)

            val snapshot = synchronized(activeActivities) {
                activeActivities.toList().asReversed()
            }
            for (act in snapshot) {
                if (act is LightspeedRefuelingActivity) continue
                if (!act.isFinishing && !act.isDestroyed) {
                    runCatching {
                        act.finishAndRemoveTask()
                        act.finish()
                        act.overrideZeroTransition()
                    }
                }
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runDismiss()
        } else {
            mainHandler.post(runDismiss)
        }
    }
}
