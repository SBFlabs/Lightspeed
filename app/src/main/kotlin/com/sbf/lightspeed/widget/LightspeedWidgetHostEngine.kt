package com.sbf.lightspeed.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import java.util.concurrent.ConcurrentHashMap

/**
 * Background widget host engine that captures live RemoteViews from 3rd-party widgets
 * configured in the Refueling Bay so they can be nested directly inside the home screen Widget Stack.
 * Architecture adapted from Essentials (Sameera Sandakelum, MIT License).
 */
object LightspeedWidgetHostEngine {

    const val WIDGET_STACK_HOST_ID = 2050

    val contents = ConcurrentHashMap<Int, RemoteViews>()
    val rawContents = ConcurrentHashMap<Int, RemoteViews>()
    val unsupported: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val checkedLayouts = ConcurrentHashMap<Int, Int>()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var host: CapturingWidgetHost? = null
    private val hostViews = ConcurrentHashMap<Int, CapturingHostView>()
    private var overlayContainer: FrameLayout? = null
    private var activeWindowManager: WindowManager? = null
    private var isListening = false
    private var isScreenReceiverRegistered = false
    private var registeredContext: Context? = null
    private var pendingNotifyRunnable: Runnable? = null

    private class CapturingHostView(context: Context) : AppWidgetHostView(context) {
        override fun updateAppWidget(remoteViews: RemoteViews?) {
            val id = appWidgetId
            if (remoteViews == null) {
                rawContents.remove(id)
                contents.remove(id)
                unsupported.remove(id)
                checkedLayouts.remove(id)
            } else {
                rawContents[id] = remoteViews
                accept(context, id, remoteViews)
            }
            onHostedWidgetChanged(context)
        }
    }

    private class CapturingWidgetHost(context: Context) : AppWidgetHost(context, WIDGET_STACK_HOST_ID) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView = CapturingHostView(context)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> sync(context)
                Intent.ACTION_SCREEN_OFF -> {
                    // Host view keeps its cached RemoteViews; no unnecessary updates while screen is asleep
                }
            }
        }
    }

    fun startIfNeeded(context: Context) {
        val hostedIds = LightspeedPreferences.getRefuelingStackWidgetIds(context)
        if (hostedIds.isNotEmpty()) {
            sync(context)
        }
    }

    fun sync(context: Context) {
        val app = context.applicationContext
        mainHandler.post {
            val hostedIds = LightspeedPreferences.getRefuelingStackWidgetIds(app)
            if (hostedIds.isEmpty()) {
                stop()
                return@post
            }

            registerScreenReceiver(app)

            if (host == null) {
                host = CapturingWidgetHost(app).also {
                    try {
                        it.startListening()
                        isListening = true
                    } catch (e: Exception) {
                        logSwallowed("LightspeedWidgetHostEngine", "startListening", e)
                    }
                }
            }

            attachOverlay(app)
            val container = overlayContainer
            val awm = AppWidgetManager.getInstance(app)

            // Remove views for widgets no longer in the stack
            val currentKeys = hostViews.keys.toList()
            for (id in currentKeys) {
                if (id !in hostedIds) {
                    hostViews.remove(id)?.let { v ->
                        try { container?.removeView(v) } catch (_: Exception) {}
                    }
                    contents.remove(id)
                    rawContents.remove(id)
                    unsupported.remove(id)
                    checkedLayouts.remove(id)
                }
            }

            // Create and attach host views for new widgets
            for (id in hostedIds) {
                if (!hostViews.containsKey(id)) {
                    val info = awm.getAppWidgetInfo(id)
                    if (info != null) {
                        try {
                            val view = host?.createView(app, id, info)
                            if (view is CapturingHostView) {
                                hostViews[id] = view
                                container?.addView(view, FrameLayout.LayoutParams(1, 1))
                                NestedWidgetSupport.requestUpdate(app, awm, id)
                            }
                        } catch (e: Exception) {
                            logSwallowed("LightspeedWidgetHostEngine", "createView for $id", e)
                        }
                    }
                }
            }
        }
    }

    private fun accept(context: Context, id: Int, raw: RemoteViews) {
        val resolved = NestedWidgetSupport.resolveSized(context, raw, null)
        if (checkedLayouts[id] == resolved.layoutId || NestedWidgetSupport.canRender(context, resolved)) {
            checkedLayouts[id] = resolved.layoutId
            contents[id] = resolved
            unsupported.remove(id)
        } else {
            checkedLayouts.remove(id)
            contents.remove(id)
            unsupported.add(id)
        }
    }

    private fun onHostedWidgetChanged(context: Context) {
        pendingNotifyRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            LightspeedStackWidgetProvider.notifyStackDataChanged(context)
        }
        pendingNotifyRunnable = r
        mainHandler.postDelayed(r, 120L)
    }

    private fun attachOverlay(context: Context) {
        if (overlayContainer != null) return
        val accessibilityService = LightspeedAccessibilityService.instance
        val wm = accessibilityService?.windowManager
            ?: (if (Settings.canDrawOverlays(context)) context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager else null)
            ?: return

        val layoutType = if (accessibilityService != null) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        }

        try {
            val container = FrameLayout(context.applicationContext)
            val params = WindowManager.LayoutParams(
                1, 1,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = -100
                y = -100
            }
            wm.addView(container, params)
            overlayContainer = container
            activeWindowManager = wm
        } catch (e: Exception) {
            logSwallowed("LightspeedWidgetHostEngine", "attachOverlay failed", e)
        }
    }

    fun stop() {
        mainHandler.post {
            detachOverlay()
            hostViews.clear()
            contents.clear()
            rawContents.clear()
            unsupported.clear()
            checkedLayouts.clear()
            if (isScreenReceiverRegistered) {
                try {
                    registeredContext?.unregisterReceiver(screenReceiver)
                } catch (e: Exception) {
                    logSwallowed("LightspeedWidgetHostEngine", "unregisterScreenReceiver", e)
                }
                isScreenReceiverRegistered = false
                registeredContext = null
            }
            if (isListening) {
                try {
                    host?.stopListening()
                } catch (e: Exception) {
                    logSwallowed("LightspeedWidgetHostEngine", "stopListening", e)
                }
                isListening = false
            }
            host = null
        }
    }

    private fun detachOverlay() {
        val container = overlayContainer ?: return
        val wm = activeWindowManager
        try {
            wm?.removeView(container)
        } catch (_: Exception) {}
        overlayContainer = null
        activeWindowManager = null
    }

    private fun registerScreenReceiver(context: Context) {
        if (isScreenReceiverRegistered) return
        val appCtx = context.applicationContext ?: context
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                appCtx,
                screenReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            registeredContext = appCtx
            isScreenReceiverRegistered = true
        } catch (e: Exception) {
            logSwallowed("LightspeedWidgetHostEngine", "registerScreenReceiver", e)
        }
    }
}
