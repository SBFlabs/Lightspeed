package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs

/**
 * Refueling Bay: Ambient Charging & Cryo Dashboard for Lightspeed.
 * Features lockscreen wake-over-lock, sensor-adaptive portrait/landscape reflow,
 * real-time battery thermal telemetry (°C), 4 selectable Battery Arc styles (Halo, Reactor Ticks, Dual Wings, Tachometer),
 * dynamic thermal/wattage color shifts, OLED Burn-In Shields (auto-sleep to true black & tap/swipe wake),
 * and Dual Docking Profiles (Smart Stack & Adaptive Grid layout memory).
 */
class LightspeedRefuelingActivity : ComponentActivity() {

    companion object {
        const val APPWIDGET_HOST_ID = 2048

        @Volatile
        var isActive: Boolean = false
            private set

        @Volatile
        var isChargingSessionDismissed: Boolean = false

        var isSessionDismissed: Boolean
            get() = isChargingSessionDismissed
            set(value) { isChargingSessionDismissed = value }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overridePendingTransition(0, 0)
    }

    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val widgetIdsState = mutableStateListOf<Int>()
    private var widgetLayoutModeState = mutableStateOf("smart_stack")
    private var isEditModeState = mutableStateOf(false)

    private val widgetPickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val widgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                ?: AppWidgetManager.INVALID_APPWIDGET_ID
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                configureOrBindWidget(widgetId)
            }
        } else {
            if (pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost?.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }
    }

    private val widgetConfigLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val widgetId = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
        if (result.resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            saveNewWidgetId(widgetId)
        } else {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost?.deleteAppWidgetId(widgetId)
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true

        // Lockscreen Wake & Screen-Off Launch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        appWidgetHost = AppWidgetHost(applicationContext, APPWIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        appWidgetHost?.startListening()

        // Load configured widget layout mode & independent profile memory
        val initialMode = defaultPrefs().getString(LightspeedPreferences.KEY_REFUELING_WIDGET_LAYOUT, "smart_stack") ?: "smart_stack"
        widgetLayoutModeState.value = initialMode
        loadWidgetsForCurrentMode(initialMode)

        setContent {
            com.sbf.lightspeed.ui.theme.LightspeedTheme(forceDark = true) {
                RefuelingBayScreen(
                    activity = this,
                    widgetIds = widgetIdsState,
                    widgetLayoutMode = widgetLayoutModeState.value,
                    isEditMode = isEditModeState.value,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    onPickWidget = { pickAppWidget() },
                    onRemoveWidget = { widgetId -> removeWidgetId(widgetId) },
                    onReorderWidget = { fromIdx, toIdx -> reorderWidget(fromIdx, toIdx) },
                    onToggleLayoutMode = { toggleLayoutMode() },
                    onToggleEditMode = { isEditModeState.value = !isEditModeState.value },
                    onDismiss = {
                        val lp = window.attributes
                        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                        finish()
                    }
                )
            }
        }
    }

    private fun loadWidgetsForCurrentMode(mode: String) {
        widgetIdsState.clear()
        if (mode == "smart_stack") {
            widgetIdsState.addAll(LightspeedPreferences.getRefuelingStackWidgetIds(this))
        } else {
            widgetIdsState.addAll(LightspeedPreferences.getRefuelingGridWidgetIds(this))
        }
    }

    private fun pickAppWidget() {
        val host = appWidgetHost ?: return
        val newWidgetId = host.allocateAppWidgetId()
        pendingWidgetId = newWidgetId

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
        }
        widgetPickLauncher.launch(pickIntent)
    }

    private fun configureOrBindWidget(widgetId: Int) {
        val manager = appWidgetManager ?: return
        val appWidgetInfo = manager.getAppWidgetInfo(widgetId)
        if (appWidgetInfo?.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = appWidgetInfo.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            }
            widgetConfigLauncher.launch(configIntent)
        } else {
            saveNewWidgetId(widgetId)
        }
    }

    private fun saveNewWidgetId(widgetId: Int) {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        if (!widgetIdsState.contains(widgetId)) {
            widgetIdsState.add(widgetId)
            saveCurrentModeWidgets()
        }
    }

    private fun removeWidgetId(widgetId: Int) {
        if (widgetIdsState.contains(widgetId)) {
            appWidgetHost?.deleteAppWidgetId(widgetId)
            widgetIdsState.remove(widgetId)
            saveCurrentModeWidgets()
        }
    }

    private fun reorderWidget(fromIndex: Int, toIndex: Int) {
        if (fromIndex in widgetIdsState.indices && toIndex in widgetIdsState.indices && fromIndex != toIndex) {
            val item = widgetIdsState.removeAt(fromIndex)
            widgetIdsState.add(toIndex, item)
            saveCurrentModeWidgets()
        }
    }

    private fun saveCurrentModeWidgets() {
        if (widgetLayoutModeState.value == "smart_stack") {
            LightspeedPreferences.saveRefuelingStackWidgetIds(this, widgetIdsState.toList())
        } else {
            LightspeedPreferences.saveRefuelingGridWidgetIds(this, widgetIdsState.toList())
        }
    }

    private fun toggleLayoutMode() {
        val currentMode = widgetLayoutModeState.value
        val newMode = if (currentMode == "smart_stack") "adaptive_grid" else "smart_stack"

        // 1. Save state of current profile before switching
        saveCurrentModeWidgets()

        // 2. Switch layout mode state
        widgetLayoutModeState.value = newMode
        defaultPrefs().edit().putString(LightspeedPreferences.KEY_REFUELING_WIDGET_LAYOUT, newMode).apply()

        // 3. Load target profile without deleting widget allocations
        loadWidgetsForCurrentMode(newMode)
    }

    override fun onStart() {
        super.onStart()
        isActive = true
        appWidgetHost?.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost?.stopListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        isActive = false
    }
}
