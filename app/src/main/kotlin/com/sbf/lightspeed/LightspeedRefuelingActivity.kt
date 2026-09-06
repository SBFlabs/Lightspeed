package com.sbf.lightspeed

import android.app.Activity
import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        const val REQUEST_CONFIG_WIDGET = 4096

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

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIG_WIDGET) {
            isWaitingForResult = false
            val widgetId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                saveNewWidgetId(widgetId)
            } else {
                if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    appWidgetHost?.deleteAppWidgetId(widgetId)
                }
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            }
        }
    }

    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private val widgetIdsState = mutableStateListOf<Int>()
    private var widgetLayoutModeState = mutableStateOf("smart_stack")
    private var isEditModeState = mutableStateOf(false)
    private val interactionTimestampState = mutableLongStateOf(System.currentTimeMillis())

    override fun onUserInteraction() {
        super.onUserInteraction()
        interactionTimestampState.longValue = System.currentTimeMillis()
    }

    private var isWaitingForResult: Boolean = false
    private var pendingProvider: AppWidgetProviderInfo? = null

    private val widgetBindLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isWaitingForResult = false
        val widgetId = pendingWidgetId
        val provider = pendingProvider
        if (result.resultCode == Activity.RESULT_OK && widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && provider != null) {
            checkConfigureOrSaveWidget(widgetId, provider)
        } else {
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                appWidgetHost?.deleteAppWidgetId(widgetId)
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
            pendingProvider = null
        }
    }

    private val widgetPickLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isWaitingForResult = false
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
        isWaitingForResult = false
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

        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }

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
                        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                        val lp = window.attributes
                        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                        window.attributes = lp
                        finish()
                    },
                    externalInteractionTimestamp = interactionTimestampState.longValue,
                    onDeployWidgetProvider = { provider -> bindProvider(provider) }
                )
            }
        }
    }

    private fun bindProvider(provider: AppWidgetProviderInfo) {
        val host = appWidgetHost ?: return
        val manager = appWidgetManager ?: return
        val widgetId = host.allocateAppWidgetId()
        pendingWidgetId = widgetId
        pendingProvider = provider

        val allowed = manager.bindAppWidgetIdIfAllowed(widgetId, provider.provider)
        val needsConfig = provider.configure != null
        val requiresExternalUI = !allowed || needsConfig

        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true

        if (requiresExternalUI && isLocked) {
            // Dismiss keyguard first so the configuration / permission activity can render cleanly
            km?.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    proceedWithBindingOrConfig(widgetId, provider, allowed)
                }

                override fun onDismissCancelled() {
                    host.deleteAppWidgetId(widgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    pendingProvider = null
                    isWaitingForResult = false
                }

                override fun onDismissError() {
                    host.deleteAppWidgetId(widgetId)
                    pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                    pendingProvider = null
                    isWaitingForResult = false
                }
            })
        } else {
            proceedWithBindingOrConfig(widgetId, provider, allowed)
        }
    }

    private fun proceedWithBindingOrConfig(widgetId: Int, provider: AppWidgetProviderInfo, allowed: Boolean) {
        if (allowed) {
            checkConfigureOrSaveWidget(widgetId, provider)
        } else {
            isWaitingForResult = true
            val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.provider)
            }
            widgetBindLauncher.launch(bindIntent)
        }
    }

    private fun checkConfigureOrSaveWidget(widgetId: Int, provider: AppWidgetProviderInfo) {
        pendingProvider = null
        if (provider.configure != null) {
            isWaitingForResult = true
            try {
                // Use AppWidgetHost's privileged startAppWidgetConfigureActivityForResult
                // to start unexported widget configuration activities without SecurityException
                appWidgetHost?.startAppWidgetConfigureActivityForResult(
                    this,
                    widgetId,
                    0,
                    REQUEST_CONFIG_WIDGET,
                    null
                )
            } catch (e: Exception) {
                try {
                    val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = provider.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    }
                    widgetConfigLauncher.launch(configIntent)
                } catch (ex: Exception) {
                    android.util.Log.e("RefuelingActivity", "Cannot launch configuration activity for ${provider.provider}", ex)
                    isWaitingForResult = false
                    android.widget.Toast.makeText(
                        this,
                        "Module configuration unavailable; deploying default slot",
                        android.widget.Toast.SHORT
                    ).show()
                    saveNewWidgetId(widgetId)
                }
            }
        } else {
            isWaitingForResult = false
            saveNewWidgetId(widgetId)
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

        isWaitingForResult = true
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, newWidgetId)
        }
        widgetPickLauncher.launch(pickIntent)
    }

    private fun configureOrBindWidget(widgetId: Int) {
        val manager = appWidgetManager ?: return
        val appWidgetInfo = manager.getAppWidgetInfo(widgetId)
        if (appWidgetInfo?.configure != null) {
            isWaitingForResult = true
            try {
                appWidgetHost?.startAppWidgetConfigureActivityForResult(
                    this,
                    widgetId,
                    0,
                    REQUEST_CONFIG_WIDGET,
                    null
                )
            } catch (e: Exception) {
                try {
                    val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                        component = appWidgetInfo.configure
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    }
                    widgetConfigLauncher.launch(configIntent)
                } catch (ex: Exception) {
                    android.util.Log.e("RefuelingActivity", "Cannot launch configuration activity", ex)
                    isWaitingForResult = false
                    saveNewWidgetId(widgetId)
                }
            }
        } else {
            isWaitingForResult = false
            saveNewWidgetId(widgetId)
        }
    }

    private fun saveNewWidgetId(widgetId: Int) {
        isWaitingForResult = false
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
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        isWaitingForResult = false
        appWidgetHost?.startListening()
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }

    override fun onStop() {
        super.onStop()
        isActive = false
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        appWidgetHost?.stopListening()
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
        if (!isChangingConfigurations && !isWaitingForResult) {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        isActive = false
        LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
    }
}
