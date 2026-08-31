package com.sbf.lightspeed

import android.app.Activity
import android.app.AlarmManager
import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Paint
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

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
                onDismiss = { finish() }
            )
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

@Composable
fun RefuelingBayScreen(
    activity: Activity,
    widgetIds: List<Int>,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onReorderWidget: (Int, Int) -> Unit,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Live Telemetry States
    var batteryPct by remember { mutableIntStateOf(0) }
    var batteryTempC by remember { mutableFloatStateOf(28.0f) }
    var isCharging by remember { mutableStateOf(false) }
    var isFull by remember { mutableStateOf(false) }
    var wattage by remember { mutableFloatStateOf(0f) }
    var voltageMv by remember { mutableIntStateOf(0) }
    var currentMa by remember { mutableIntStateOf(0) }
    var timeRemainingMinutes by remember { mutableLongStateOf(-1L) }
    var chargeTypeLabel by remember { mutableStateOf("Standard Charge") }

    // Style & Sleep Settings
    val batteryStyle = prefs.getString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, "halo") ?: "halo"
    val sleepTimeoutSetting = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s"
    val sleepTimeoutMs = remember(sleepTimeoutSetting) {
        when (sleepTimeoutSetting) {
            "30s" -> 30_000L
            "60s" -> 60_000L
            "3m" -> 180_000L
            "5m" -> 300_000L
            "never" -> Long.MAX_VALUE
            else -> 60_000L
        }
    }

    // OLED Burn-In Sleep Shield States
    var isSleeping by remember { mutableStateOf(false) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Cryo Clock States
    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var nextAlarmStr by remember { mutableStateOf<String?>(null) }

    // AMOLED Burn-In Drift (Micro translation)
    var driftOffsetX by remember { mutableFloatStateOf(0f) }
    var driftOffsetY by remember { mutableFloatStateOf(0f) }

    // Receiver for Live Battery & Thermal Telemetry
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100f / scale).roundToInt()
                }

                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                batteryTempC = tempRaw / 10.0f

                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                isFull = status == BatteryManager.BATTERY_STATUS_FULL || batteryPct >= 100

                voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)

                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                if (bm != null) {
                    val currentNowUa = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                    currentMa = (abs(currentNowUa) / 1000).coerceAtLeast(0)
                    val rawWattage = (voltageMv / 1000f) * (abs(currentNowUa) / 1_000_000f)
                    wattage = if (rawWattage > 0.05f) rawWattage else 0f

                    chargeTypeLabel = when {
                        !isCharging -> "Discharging (Auxiliary Power)"
                        wattage >= 45f -> "⚡ Cryo-Hyper Turbo Charge"
                        wattage >= 20f -> "⚡ Super Fast Warp Charge"
                        wattage >= 10f -> "⚡ Fast Refueling"
                        else -> "⚡ Standard Dock Refuel"
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && isCharging && !isFull) {
                        val remainingMs = bm.computeChargeTimeRemaining()
                        timeRemainingMinutes = if (remainingMs > 0) remainingMs / 60000L else -1L
                    }
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
        }
    }

    // Dynamic Arc Colors
    val dynamicArcColor = when {
        batteryPct >= 100 -> Color(0xFF00E676) // Emerald Fusion (100%)
        batteryTempC >= 40.0f -> Color(0xFFFF3D00) // High Thermal Crimson (>40°C)
        wattage >= 20.0f -> Color(0xFF00E5FF) // Warp Electric Cyan (>20W)
        wattage >= 10.0f -> Color(0xFFFFB300) // Cruising Solar Gold (10W-20W)
        else -> MaterialTheme.colorScheme.primary
    }

    val thermalBadgeColor = when {
        batteryTempC > 42.0f -> Color(0xFFFF3D00) // Throttle (>42°C)
        batteryTempC >= 38.0f -> Color(0xFFFFB300) // Warm Caution (38°C-42°C)
        else -> Color(0xFF00E5FF).copy(alpha = 0.85f) // Normal (<38°C)
    }

    // Ticking Clock, Sleep Shield Timer & Burn-In Drift Loop
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        var driftCounter = 0

        while (true) {
            val now = Date()
            currentTimeStr = timeFormat.format(now)
            currentDateStr = dateFormat.format(now)

            val nextAlarm = alarmManager?.nextAlarmClock
            if (nextAlarm != null) {
                val alarmTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(nextAlarm.triggerTime))
                nextAlarmStr = "Next Alarm: $alarmTime"
            } else {
                nextAlarmStr = null
            }

            // Check auto-sleep timeout
            val idleDuration = System.currentTimeMillis() - lastInteractionTimestamp
            if (sleepTimeoutMs < Long.MAX_VALUE && idleDuration >= sleepTimeoutMs && !isSleeping) {
                isSleeping = true
                val lp = activity.window.attributes
                lp.screenBrightness = 0.01f
                activity.window.attributes = lp
            }

            // Drift every 60 iterations (60 seconds)
            driftCounter++
            if (driftCounter >= 60) {
                driftCounter = 0
                // Shift between -2px and +2px to avoid OLED phosphor burn-in
                driftOffsetX = ((-2..2).random()).toFloat()
                driftOffsetY = ((-2..2).random()).toFloat()
            }

            kotlinx.coroutines.delay(1000L)
        }
    }

    // Function to wake up from sleep shield
    fun wakeShield(durationMs: Long = 15_000L) {
        isSleeping = false
        val lp = activity.window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
        if (sleepTimeoutMs < Long.MAX_VALUE) {
            lastInteractionTimestamp = System.currentTimeMillis() - (sleepTimeoutMs - durationMs).coerceAtLeast(0L)
        } else {
            lastInteractionTimestamp = System.currentTimeMillis()
        }
    }

    val animatedContentAlpha by animateFloatAsState(
        targetValue = if (isSleeping) 0f else 1f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "sleep_fade"
    )

    // True Black OLED Background with gestures
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isSleeping) {
                detectTapGestures(
                    onTap = {
                        if (isSleeping) {
                            wakeShield(15_000L)
                        } else {
                            onDismiss()
                        }
                    },
                    onDoubleTap = {
                        onDismiss()
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -120f) {
                            // Swipe-Up gesture: dismiss keyguard and finish
                            try {
                                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                km?.requestDismissKeyguard(activity, null)
                            } catch (_: Exception) {}
                            onDismiss()
                        }
                    },
                    onDragCancel = { totalDragY = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        totalDragY += dragAmount
                    }
                )
            }
            .offset { IntOffset(driftOffsetX.roundToInt(), driftOffsetY.roundToInt()) }
            .padding(16.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(animatedContentAlpha)
        ) {
            if (isLandscape) {
                // =========================================================================
                // LANDSCAPE: HORIZONTAL SPLIT (Left = Cryo Telemetry, Right = Multi-Widget Container)
                // =========================================================================
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Cryo Clock & Battery Arc Telemetry
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Minimalist Monospace Cryo Clock
                        Text(
                            text = currentTimeStr,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Text(
                            text = currentDateStr.uppercase(Locale.US),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                        if (nextAlarmStr != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = Color.LightGray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = nextAlarmStr!!,
                                    fontSize = 10.5.sp,
                                    color = Color.LightGray.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Battery Telemetry Arc (Selectable Style)
                        BatteryTelemetryCircle(
                            batteryPct = batteryPct,
                            wattage = wattage,
                            batteryTempC = batteryTempC,
                            chargeTypeLabel = chargeTypeLabel,
                            timeRemainingMinutes = timeRemainingMinutes,
                            isCharging = isCharging,
                            isFull = isFull,
                            batteryStyle = batteryStyle,
                            dynamicArcColor = dynamicArcColor,
                            thermalBadgeColor = thermalBadgeColor,
                            sizeDp = 156.dp
                        )
                    }

                    // Right Column: Multi-Widget Engine Container
                    Column(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header Toolbar for Widget Controls
                        WidgetEngineToolbar(
                            widgetCount = widgetIds.size,
                            widgetLayoutMode = widgetLayoutMode,
                            isEditMode = isEditMode,
                            onToggleLayoutMode = onToggleLayoutMode,
                            onToggleEditMode = onToggleEditMode,
                            onAddWidget = onPickWidget,
                            onDismiss = onDismiss
                        )

                        // Multi-Widget Surface
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            MultiWidgetContainer(
                                widgetIds = widgetIds,
                                widgetLayoutMode = widgetLayoutMode,
                                isEditMode = isEditMode,
                                isLandscape = true,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onPickWidget = onPickWidget,
                                onRemoveWidget = onRemoveWidget,
                                onReorderWidget = onReorderWidget
                            )
                        }
                    }
                }
            } else {
                // =========================================================================
                // PORTRAIT: VERTICAL STACK (Top = Clock & Battery Telemetry, Bottom = Multi-Widget Container)
                // =========================================================================
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header: Minimalist Cryo Clock
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = currentTimeStr,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Light,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.sp,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                        Text(
                            text = currentDateStr.uppercase(Locale.US),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.4.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                        )
                        if (nextAlarmStr != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Alarm,
                                    contentDescription = null,
                                    tint = Color.LightGray.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = nextAlarmStr!!,
                                    fontSize = 11.sp,
                                    color = Color.LightGray.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }

                    // Center Battery Telemetry Arc (Selectable Style)
                    BatteryTelemetryCircle(
                        batteryPct = batteryPct,
                        wattage = wattage,
                        batteryTempC = batteryTempC,
                        chargeTypeLabel = chargeTypeLabel,
                        timeRemainingMinutes = timeRemainingMinutes,
                        isCharging = isCharging,
                        isFull = isFull,
                        batteryStyle = batteryStyle,
                        dynamicArcColor = dynamicArcColor,
                        thermalBadgeColor = thermalBadgeColor,
                        sizeDp = 184.dp
                    )

                    // Multi-Widget Section & Toolbar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetEngineToolbar(
                            widgetCount = widgetIds.size,
                            widgetLayoutMode = widgetLayoutMode,
                            isEditMode = isEditMode,
                            onToggleLayoutMode = onToggleLayoutMode,
                            onToggleEditMode = onToggleEditMode,
                            onAddWidget = onPickWidget,
                            onDismiss = onDismiss
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 240.dp)
                        ) {
                            MultiWidgetContainer(
                                widgetIds = widgetIds,
                                widgetLayoutMode = widgetLayoutMode,
                                isEditMode = isEditMode,
                                isLandscape = false,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onPickWidget = onPickWidget,
                                onRemoveWidget = onRemoveWidget,
                                onReorderWidget = onReorderWidget
                            )
                        }
                    }

                    // Bottom Exit Note & Gestures
                    Text(
                        text = "✦ Tap to Exit · Swipe Up for Keyguard ✦",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BatteryTelemetryCircle(
    batteryPct: Int,
    wattage: Float,
    batteryTempC: Float,
    chargeTypeLabel: String,
    timeRemainingMinutes: Long,
    isCharging: Boolean,
    isFull: Boolean,
    batteryStyle: String,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    sizeDp: androidx.compose.ui.unit.Dp
) {
    // Fast-charge (>20W) pulsating glow transition
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val isPulseActive = isCharging && wattage >= 20.0f

    Box(
        modifier = Modifier
            .size(sizeDp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

            when (batteryStyle) {
                "reactor_ticks" -> {
                    // Style 2: 20 discrete laser-cut radial tick marks
                    val tickCount = 20
                    val activeTicks = (batteryPct / 100f * tickCount).roundToInt()
                    val tickLength = 10.dp.toPx()

                    for (i in 0 until tickCount) {
                        val angleDeg = i * (360f / tickCount) - 90f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(angleRad).toFloat()
                        val sinVal = sin(angleRad).toFloat()

                        val outerX = center.x + radius * cosVal
                        val outerY = center.y + radius * sinVal
                        val innerX = center.x + (radius - tickLength) * cosVal
                        val innerY = center.y + (radius - tickLength) * sinVal

                        val isActive = i < activeTicks
                        val tickColor = if (isActive) {
                            if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        }
                        val tickWidth = if (isActive) 3.5.dp.toPx() else 2.dp.toPx()

                        drawLine(
                            color = tickColor,
                            start = androidx.compose.ui.geometry.Offset(innerX, innerY),
                            end = androidx.compose.ui.geometry.Offset(outerX, outerY),
                            strokeWidth = tickWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                "dual_wings" -> {
                    // Style 3: Symmetrical brackets sweeping upward from bottom (90°) to top (270° / -90°)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    val halfSweep = (batteryPct / 100f) * 180f
                    // Left wing (counter-clockwise from bottom 90°)
                    drawArc(
                        color = dynamicArcColor,
                        startAngle = 90f,
                        sweepAngle = -halfSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    // Right wing (clockwise from bottom 90°)
                    drawArc(
                        color = dynamicArcColor,
                        startAngle = 90f,
                        sweepAngle = halfSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                "tachometer" -> {
                    // Style 4: 240° cockpit sweep (7 to 5 o'clock, 150° to 390°) with micro-graduations
                    // Background track (150° with sweep 240°)
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // Micro-graduations along the 240° sweep
                    val subTickLength = 5.dp.toPx()
                    val majorTickLength = 9.dp.toPx()
                    for (step in 0..24) {
                        val angleDeg = 150f + step * 10f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(angleRad).toFloat()
                        val sinVal = sin(angleRad).toFloat()
                        val isMajor = step % 6 == 0
                        val tLen = if (isMajor) majorTickLength else subTickLength

                        val outerX = center.x + (radius - strokeWidthPx / 2f - 2.dp.toPx()) * cosVal
                        val outerY = center.y + (radius - strokeWidthPx / 2f - 2.dp.toPx()) * sinVal
                        val innerX = center.x + (radius - strokeWidthPx / 2f - 2.dp.toPx() - tLen) * cosVal
                        val innerY = center.y + (radius - strokeWidthPx / 2f - 2.dp.toPx() - tLen) * sinVal

                        drawLine(
                            color = Color.White.copy(alpha = if (isMajor) 0.35f else 0.15f),
                            start = androidx.compose.ui.geometry.Offset(innerX, innerY),
                            end = androidx.compose.ui.geometry.Offset(outerX, outerY),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Active sweep arc
                    val activeSweep = (batteryPct / 100f) * 240f
                    drawArc(
                        brush = Brush.sweepGradient(listOf(dynamicArcColor.copy(alpha = 0.7f), dynamicArcColor)),
                        startAngle = 150f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                else -> {
                    // Style 1: "halo" (Default smooth continuous neon arc)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    val sweep = (batteryPct / 100f) * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(dynamicArcColor.copy(alpha = 0.6f), dynamicArcColor, dynamicArcColor.copy(alpha = 0.9f))
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$batteryPct",
                    fontSize = if (sizeDp > 160.dp) 44.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "%",
                    fontSize = if (sizeDp > 160.dp) 18.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dynamicArcColor,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }

            // Wattage & Thermal Telemetry (°C) Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (wattage > 0f) {
                    Text(
                        text = String.format(Locale.US, "%.1f W", wattage),
                        fontSize = if (sizeDp > 160.dp) 13.5.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicArcColor
                    )
                }

                // Real-time SoC Thermal Telemetry
                Text(
                    text = String.format(Locale.US, "%.1f°C", batteryTempC),
                    fontSize = if (sizeDp > 160.dp) 12.5.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = thermalBadgeColor
                )
            }

            Text(
                text = if (isFull) "FULL TANK" else chargeTypeLabel,
                fontSize = if (sizeDp > 160.dp) 10.5.sp else 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (timeRemainingMinutes > 0 && isCharging && !isFull) {
                Text(
                    text = if (timeRemainingMinutes >= 60) {
                        "Full in ${timeRemainingMinutes / 60}h ${timeRemainingMinutes % 60}m"
                    } else {
                        "Full in ${timeRemainingMinutes}m"
                    },
                    fontSize = 9.5.sp,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun WidgetEngineToolbar(
    widgetCount: Int,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onAddWidget: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Widgets,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = if (widgetLayoutMode == "smart_stack") "SMART STACK ($widgetCount)" else "ADAPTIVE GRID ($widgetCount)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Right Action Controls
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Switcher (Stack vs Grid)
            IconButton(
                onClick = onToggleLayoutMode,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = if (widgetLayoutMode == "smart_stack") Icons.Default.GridView else Icons.Default.ViewCarousel,
                    contentDescription = "Switch Layout",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Edit Mode Toggle
            IconButton(
                onClick = onToggleEditMode,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (isEditMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Edit Widgets",
                    tint = if (isEditMode) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Add Widget [+]
            IconButton(
                onClick = onAddWidget,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Widget",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Exit [✕]
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun MultiWidgetContainer(
    widgetIds: List<Int>,
    widgetLayoutMode: String,
    isEditMode: Boolean,
    isLandscape: Boolean,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    onRemoveWidget: (Int) -> Unit,
    onReorderWidget: (Int, Int) -> Unit
) {
    if (widgetIds.isEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp)
        ) {
            EmptyWidgetSlot(onPickWidget = onPickWidget)
        }
        return
    }

    if (widgetLayoutMode == "smart_stack") {
        // =========================================================================
        // MODE 1: SMART STACK (SWIPEABLE PAGER)
        // =========================================================================
        val pagerState = rememberPagerState(pageCount = { widgetIds.size })

        Card(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))),
                    RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            shape = RoundedCornerShape(20.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { pageIndex ->
                    val widgetId = widgetIds.getOrNull(pageIndex)
                    if (widgetId != null && appWidgetHost != null && appWidgetManager != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppWidgetContainerView(
                                widgetId = widgetId,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )

                            // Edit Overlay Controls
                            if (isEditMode) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    if (pageIndex > 0) {
                                        IconButton(
                                            onClick = { onReorderWidget(pageIndex, pageIndex - 1) },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Left", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    if (pageIndex < widgetIds.size - 1) {
                                        IconButton(
                                            onClick = { onReorderWidget(pageIndex, pageIndex + 1) },
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.7f))
                                        ) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Right", tint = Color.White, modifier = Modifier.size(13.dp))
                                        }
                                    }
                                    IconButton(
                                        onClick = { onRemoveWidget(widgetId) },
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Delete Widget", tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Subtle Pager Indicator Dots at Bottom
                if (widgetIds.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(widgetIds.size) { index ->
                            val isActive = pagerState.currentPage == index
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 12.dp else 5.dp, 5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // MODE 2: ADAPTIVE GRID (1 Column Portrait, 2 Columns Landscape)
        // =========================================================================
        val gridColumns = if (isLandscape) 2 else 1

        LazyVerticalGrid(
            columns = GridCells.Fixed(gridColumns),
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(widgetIds) { index, widgetId ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            1.dp,
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.03f))),
                            RoundedCornerShape(18.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (appWidgetHost != null && appWidgetManager != null) {
                            AppWidgetContainerView(
                                widgetId = widgetId,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }

                        if (isEditMode) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (index > 0) {
                                    IconButton(
                                        onClick = { onReorderWidget(index, index - 1) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Move Up", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                if (index < widgetIds.size - 1) {
                                    IconButton(
                                        onClick = { onReorderWidget(index, index + 1) },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.7f))
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Move Down", tint = Color.White, modifier = Modifier.size(12.dp))
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveWidget(widgetId) },
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = 0.7f))
                                    ) {
                                    Icon(Icons.Default.Close, contentDescription = "Delete Widget", tint = Color.White, modifier = Modifier.size(12.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Optional Append Item: Add Widget Card in Grid
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { onPickWidget() }
                        .background(Color.White.copy(alpha = 0.02f))
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(18.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mount Widget Slot", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AppWidgetContainerView(
    widgetId: Int,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    modifier: Modifier = Modifier
) {
    val appWidgetInfo = remember(widgetId) {
        try {
            appWidgetManager.getAppWidgetInfo(widgetId)
        } catch (_: Exception) {
            null
        }
    }

    if (appWidgetInfo != null) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                try {
                    val hostView = appWidgetHost.createView(ctx, widgetId, appWidgetInfo)
                    hostView.setAppWidget(widgetId, appWidgetInfo)
                    hostView
                } catch (e: Exception) {
                    android.widget.TextView(ctx).apply {
                        text = "Widget Error"
                        setTextColor(android.graphics.Color.WHITE)
                    }
                }
            }
        )
    } else {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = "Widget Unavailable (ID: $widgetId)",
                color = Color.LightGray.copy(alpha = 0.6f),
                fontSize = 11.5.sp
            )
        }
    }
}

@Composable
fun EmptyWidgetSlot(onPickWidget: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onPickWidget() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Widgets,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Embed Android Widget",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
        Text(
            text = "Tap to mount Weather, Music, Clock or Notes",
            fontSize = 11.sp,
            color = Color.LightGray.copy(alpha = 0.65f)
        )
    }
}
