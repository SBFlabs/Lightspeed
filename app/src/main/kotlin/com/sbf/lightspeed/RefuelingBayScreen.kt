package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.app.AlarmManager
import android.os.BatteryManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*
import kotlinx.coroutines.delay

private fun Modifier.refuelingBayUnlockSwipe(
    context: Context,
    activity: Activity,
    onDismiss: () -> Unit,
    onInteraction: () -> Unit = {}
): Modifier = this.pointerInput(Unit) {
    var totalDragY = 0f
    detectVerticalDragGestures(
        onDragStart = {
            totalDragY = 0f
            onInteraction()
        },
        onDragEnd = {
            if (totalDragY < -120f) {
                try {
                    val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                    km?.requestDismissKeyguard(activity, null)
                } catch (e: Exception) { logSwallowed("RefuelingBayScreen", "refuelingBayUnlockSwipe:73", e) }
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                val lp = window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = lp
                onDismiss()
            }
        },
        onDragCancel = { totalDragY = 0f },
        onVerticalDrag = { _, dragAmount ->
            totalDragY += dragAmount
            onInteraction()
        }
    )
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
    onReconfigureWidget: ((Int) -> Unit)? = null,
    onToggleLayoutMode: () -> Unit,
    onToggleEditMode: () -> Unit,
    onDismiss: () -> Unit,
    externalInteractionTimestamp: Long = 0L,
    onDeployWidgetProvider: ((AppWidgetProviderInfo) -> Unit)? = null,
    reorderVersion: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(isLandscape, widgetLayoutMode) {
        if (widgetLayoutMode == "adaptive_grid") {
            (activity as? LightspeedRefuelingActivity)?.loadWidgetsForCurrentMode("adaptive_grid", isLandscape)
        }
    }

    // Tactical Widget Catalog Picker Modal State
    var showTacticalWidgetPicker by remember { mutableStateOf(false) }

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

    // Style, Sleep & Resizable Split Settings
    var batteryStyle by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, "halo") ?: "halo") }
    var sleepTimeoutSetting by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "30s") ?: "30s") }
    var sleepAction by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_ACTION, "overlay") ?: "overlay") }
    var splitRatioPortrait by remember { mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_PORTRAIT, 0.52f).coerceIn(0.3f, 0.7f)) }
    var splitRatioLandscape by remember { mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_LANDSCAPE, 0.44f).coerceIn(0.3f, 0.7f)) }
    var clockFormat by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_CLOCK_FORMAT, "24h_sec") ?: "24h_sec") }
    var dateFormat by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_DATE_FORMAT, "full") ?: "full") }
    var clockFont by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_CLOCK_FONT, "mono") ?: "mono") }
    var showAlarm by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_SHOW_ALARM, true)) }
    var showCustomizationModal by remember { mutableStateOf(false) }

    val sleepTimeoutMs = when (sleepTimeoutSetting) {
        "5s" -> 5_000L
        "15s" -> 15_000L
        "30s" -> 30_000L
        "1m" -> 60_000L
        "2m" -> 120_000L
        "5m" -> 300_000L
        "never" -> Long.MAX_VALUE
        else -> 30_000L
    }

    // OLED Burn-In Sleep Shield States
    var isSleeping by remember { mutableStateOf(false) }
    var lastInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Smart Stack Memory State
    var isStackMemoryEnabled by remember {
        mutableStateOf(LightspeedPreferences.isRefuelingStackMemoryEnabled(context))
    }

    LaunchedEffect(externalInteractionTimestamp) {
        if (externalInteractionTimestamp > 0L) {
            lastInteractionTimestamp = externalInteractionTimestamp
        }
    }

    // Cryo Clock States
    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var nextAlarmStr by remember { mutableStateOf<String?>(null) }

    // AMOLED Burn-In Drift (Micro translation)
    var driftOffsetX by remember { mutableFloatStateOf(0f) }
    var driftOffsetY by remember { mutableFloatStateOf(0f) }

    // Receiver for Live Battery & Thermal Telemetry
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: android.content.Intent?) {
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
                        !isCharging -> "Auxiliary Power"
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

        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            try { context.unregisterReceiver(receiver) } catch (e: Exception) { logSwallowed("RefuelingBayScreen", "RefuelingBayContent:229", e) }
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
    LaunchedEffect(clockFormat, dateFormat, showAlarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        var driftCounter = 0

        while (true) {
            val now = Date()
            currentTimeStr = when (clockFormat) {
                "24h" -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
                "12h_sec" -> SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(now)
                "12h" -> SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
                "orbital", "stardate" -> {
                    val cal = java.util.Calendar.getInstance()
                    val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
                    String.format(Locale.US, "D%03d · %02d:%02d", day, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
                }
                else -> SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now)
            }

            currentDateStr = when (dateFormat) {
                "short" -> SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()).format(now)
                "iso" -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
                "compact" -> SimpleDateFormat("MMM d", Locale.getDefault()).format(now)
                "hidden" -> ""
                else -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
            }

            if (showAlarm) {
                val nextAlarm = alarmManager?.nextAlarmClock
                if (nextAlarm != null) {
                    val alarmTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(nextAlarm.triggerTime))
                    nextAlarmStr = "Next Alarm: $alarmTime"
                } else {
                    nextAlarmStr = null
                }
            } else {
                nextAlarmStr = null
            }

            // Check auto-sleep timeout
            val idleDuration = System.currentTimeMillis() - lastInteractionTimestamp
            if (sleepTimeoutMs < Long.MAX_VALUE && idleDuration >= sleepTimeoutMs && !isSleeping) {
                LightspeedAccessibilityService.noteBayDark()
                if (sleepAction == "screen_off") {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    val window = activity.window
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive) {
                        LightspeedRefuelingActivity.shizukuPanelOff = true
                        com.sbf.lightspeed.system.ElevatedTaskCloser.execShizuku("cmd display power-off 0")
                    } else {
                        com.sbf.lightspeed.system.ActionDispatcher.execute(context, "system:lock_screen")
                    }
                } else {
                    isSleeping = true
                    val window = activity.window
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                }
            }


            // Drift every 60 iterations (60 seconds)
            driftCounter++
            if (driftCounter >= 60) {
                driftCounter = 0
                // Shift between -2px and +2px to avoid OLED phosphor burn-in
                driftOffsetX = ((-2..2).random()).toFloat()
                driftOffsetY = ((-2..2).random()).toFloat()
            }

            delay(1000L)
        }
    }

    // Sync System Bars & Accessibility Overlay Sleep Blackout with Sleep State
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(isSleeping) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isSleeping) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            val isBlackoutEnabled = prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_SLEEP_BLACKOUT, true)
            if (isBlackoutEnabled) {
                LightspeedAccessibilityService.instance?.showBaySleepBlackout()
            }
            while (isSleeping) {
                if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                    LightspeedAccessibilityService.noteBayDark()
                }
                if (isBlackoutEnabled) {
                    LightspeedAccessibilityService.instance?.sendBaySleepHeartbeat()
                }
                delay(1000L)
            }
        } else {
            LightspeedAccessibilityService.instance?.hideBaySleepBlackout()
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Function to wake up from sleep shield
    fun wakeShield(durationMs: Long = 15_000L) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (sleepAction == "screen_off" && com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive) {
            com.sbf.lightspeed.system.ElevatedTaskCloser.execShizuku("cmd display power-on 0")
        }
        isSleeping = false
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.show(WindowInsetsCompat.Type.systemBars())
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
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

    // True Black OLED Root Box
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Dashboard Content Layer (active only when awake or animating)
        if (!isSleeping || animatedContentAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                lastInteractionTimestamp = System.currentTimeMillis()
                            },
                            onDoubleTap = {
                                val window = activity.window
                                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                                insetsController.show(WindowInsetsCompat.Type.systemBars())
                                val lp = window.attributes
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                window.attributes = lp
                                onDismiss()
                            }
                        )
                    }
                    .padding(16.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .offset { IntOffset(driftOffsetX.roundToInt(), driftOffsetY.roundToInt()) }
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
                    // Left Column: Cryo Clock & Battery Arc Telemetry (Long-Press to Customize, Swipe-Up to Unlock Zone)
                    Column(
                        modifier = Modifier
                            .weight(splitRatioLandscape)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        showCustomizationModal = true
                                    }
                                )
                            }
                            .refuelingBayUnlockSwipe(context, activity, onDismiss) {
                                lastInteractionTimestamp = System.currentTimeMillis()
                            },
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
                                androidx.compose.material3.Icon(
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
                            .weight(1f - splitRatioLandscape)
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
                            onAddWidget = { showTacticalWidgetPicker = true },
                            isLandscape = true,
                            isStackMemoryEnabled = isStackMemoryEnabled,
                            onToggleStackMemory = {
                                val newVal = !isStackMemoryEnabled
                                isStackMemoryEnabled = newVal
                                LightspeedPreferences.setRefuelingStackMemoryEnabled(context, newVal)
                            }
                        )

                        // Multi-Widget Surface
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            MultiWidgetContainer(
                                activity = activity,
                                widgetIds = widgetIds,
                                widgetLayoutMode = widgetLayoutMode,
                                isEditMode = isEditMode,
                                isLandscape = true,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onPickWidget = { showTacticalWidgetPicker = true },
                                onRemoveWidget = onRemoveWidget,
                                onReorderWidget = onReorderWidget,
                                onReconfigureWidget = onReconfigureWidget,
                                reorderVersion = reorderVersion
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
                    // Top Interactive Region: Cryo Clock & Battery Arc (Long-Press to Customize, Swipe-Up to Unlock Zone)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(splitRatioPortrait)
                            .refuelingBayUnlockSwipe(context, activity, onDismiss) {
                                lastInteractionTimestamp = System.currentTimeMillis()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Header: Customizable Cryo Clock (Long-Press to Customize)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            showCustomizationModal = true
                                        }
                                    )
                                }
                        ) {
                            val clockFontFamily = when (clockFont) {
                                "sans" -> FontFamily.SansSerif
                                "serif" -> FontFamily.Serif
                                else -> FontFamily.Monospace
                            }
                            val clockLetterSpacing = if (clockFont == "cyber") 3.5.sp else 2.sp
                            val clockFontSize = when {
                                clockFormat == "orbital" || clockFormat == "stardate" -> 36.sp
                                currentTimeStr.length > 8 -> 34.sp
                                else -> 44.sp
                            }
                            Text(
                                text = currentTimeStr,
                                fontSize = clockFontSize,
                                fontWeight = if (clockFont == "cyber") FontWeight.Bold else FontWeight.Light,
                                fontFamily = clockFontFamily,
                                letterSpacing = clockLetterSpacing,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                            if (currentDateStr.isNotEmpty()) {
                                Text(
                                    text = currentDateStr.uppercase(Locale.US),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.4.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                )
                            }
                            if (showAlarm && nextAlarmStr != null) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Icon(
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

                        Spacer(modifier = Modifier.height(14.dp))

                        // Center Battery Telemetry Arc (Long-Press to Customize)
                        Box(
                            modifier = Modifier.pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        showCustomizationModal = true
                                    }
                                )
                            }
                        ) {
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
                        }
                    }

                    // Middle Section: Multi-Widget Section & Toolbar (Pure widget touch area - no drag stealing)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .weight(1f - splitRatioPortrait)
                            .padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WidgetEngineToolbar(
                            widgetCount = widgetIds.size,
                            widgetLayoutMode = widgetLayoutMode,
                            isEditMode = isEditMode,
                            onToggleLayoutMode = onToggleLayoutMode,
                            onToggleEditMode = onToggleEditMode,
                            onAddWidget = { showTacticalWidgetPicker = true },
                            isLandscape = false,
                            isStackMemoryEnabled = isStackMemoryEnabled,
                            onToggleStackMemory = {
                                val newVal = !isStackMemoryEnabled
                                isStackMemoryEnabled = newVal
                                LightspeedPreferences.setRefuelingStackMemoryEnabled(context, newVal)
                            }
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            MultiWidgetContainer(
                                activity = activity,
                                widgetIds = widgetIds,
                                widgetLayoutMode = widgetLayoutMode,
                                isEditMode = isEditMode,
                                isLandscape = false,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onPickWidget = { showTacticalWidgetPicker = true },
                                onRemoveWidget = onRemoveWidget,
                                onReorderWidget = onReorderWidget,
                                onReconfigureWidget = onReconfigureWidget,
                                reorderVersion = reorderVersion
                            )
                        }
                    }

                    // Bottom Interactive Region: Exit Note & Generous Bottom Swipe-Up Unlock Zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 44.dp)
                            .refuelingBayUnlockSwipe(context, activity, onDismiss) {
                                lastInteractionTimestamp = System.currentTimeMillis()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✦ Double Tap to Exit · Swipe Up to Unlock ✦",
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

        // 2. Full-Screen Sleep Shield Layer (TOPMOST)
        if (isSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(9999f)
                    .refuelingBayUnlockSwipe(context, activity, onDismiss)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = { wakeShield(15_000L) },
                            onTap = { wakeShield(15_000L) },
                            onDoubleTap = {
                                val window = activity.window
                                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                                insetsController.show(WindowInsetsCompat.Type.systemBars())
                                val lp = window.attributes
                                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                                window.attributes = lp
                                onDismiss()
                            }
                        )
                    }
            )
        }

        // 3. Tactical In-App Widget Picker Modal
        if (showTacticalWidgetPicker) {
            TacticalWidgetPickerModal(
                onDismiss = { showTacticalWidgetPicker = false },
                onSelectProvider = { provider ->
                    showTacticalWidgetPicker = false
                    onDeployWidgetProvider?.invoke(provider)
                }
            )
        }

        // 4. Refueling HUD Customization Modal (Triggered by Long-Press on Clock/Gauge)
        if (showCustomizationModal) {
            RefuelingCustomizationModal(
                isLandscape = isLandscape,
                batteryStyle = batteryStyle,
                sleepTimeout = sleepTimeoutSetting,
                sleepAction = sleepAction,
                splitRatio = if (isLandscape) splitRatioLandscape else splitRatioPortrait,
                clockFormat = clockFormat,
                dateFormat = dateFormat,
                clockFont = clockFont,
                showAlarm = showAlarm,
                onBatteryStyleChange = { newStyle ->
                    batteryStyle = newStyle
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, newStyle).apply()
                },
                onSleepTimeoutChange = { newTimeout ->
                    sleepTimeoutSetting = newTimeout
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, newTimeout).apply()
                },
                onSleepActionChange = { newAction ->
                    sleepAction = newAction
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_ACTION, newAction).apply()
                },
                onSplitRatioChange = { newRatio ->
                    if (isLandscape) {
                        splitRatioLandscape = newRatio
                        prefs.edit().putFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_LANDSCAPE, newRatio).apply()
                    } else {
                        splitRatioPortrait = newRatio
                        prefs.edit().putFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_PORTRAIT, newRatio).apply()
                    }
                },
                onClockFormatChange = { newFmt ->
                    clockFormat = newFmt
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_CLOCK_FORMAT, newFmt).apply()
                },
                onDateFormatChange = { newFmt ->
                    dateFormat = newFmt
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_DATE_FORMAT, newFmt).apply()
                },
                onClockFontChange = { newFont ->
                    clockFont = newFont
                    prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_CLOCK_FONT, newFont).apply()
                },
                onShowAlarmChange = { newShow ->
                    showAlarm = newShow
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_SHOW_ALARM, newShow).apply()
                },
                onDismiss = { showCustomizationModal = false }
            )
        }
    }
}

/**
 * Tactical Refueling Bay Customization Modal triggered by long-pressing clock or telemetry gauge.
 */
@Composable
fun RefuelingCustomizationModal(
    isLandscape: Boolean,
    batteryStyle: String,
    sleepTimeout: String,
    sleepAction: String,
    splitRatio: Float,
    clockFormat: String,
    dateFormat: String,
    clockFont: String,
    showAlarm: Boolean,
    onBatteryStyleChange: (String) -> Unit,
    onSleepTimeoutChange: (String) -> Unit,
    onSleepActionChange: (String) -> Unit,
    onSplitRatioChange: (Float) -> Unit,
    onClockFormatChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onClockFontChange: (String) -> Unit,
    onShowAlarmChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() }
            .zIndex(10005f),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 640.dp)
                .clickable { /* consume tap inside card */ },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF090D14)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙ HUD & DISPLAY CUSTOMIZATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

                // 1. Chrono Clock Format
                var showOrbitalInfo by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CHRONO CLOCK FORMAT",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "About Orbital time",
                            tint = if (showOrbitalInfo) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showOrbitalInfo = !showOrbitalInfo }
                        )
                    }
                    if (showOrbitalInfo) {
                        Text(
                            text = "ORBITAL shows the clock as a mission-style day count: D<day of year> · <24h time>. " +
                                "Example: D276 · 23:51 means day 276 of the year, at 23:51.",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val formats = listOf(
                            "24h_sec" to "24H + SEC",
                            "24h" to "24H",
                            "12h_sec" to "12H + SEC",
                            "12h" to "12H",
                            "orbital" to "ORBITAL"
                        )
                        formats.forEach { (key, label) ->
                            val isSelected = clockFormat == key || (key == "orbital" && clockFormat == "stardate")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onClockFormatChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 2. Date Format
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DATE DISPLAY FORMAT",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dateFormats = listOf(
                            "full" to "FULL",
                            "short" to "SHORT",
                            "iso" to "ISO",
                            "compact" to "COMPACT",
                            "hidden" to "HIDE"
                        )
                        dateFormats.forEach { (key, label) ->
                            val isSelected = dateFormat == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onDateFormatChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 3. Clock Font Style & Alarm Toggle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TYPOGRAPHY & ALARM BADGE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fonts = listOf(
                            "mono" to "Tactical",
                            "sans" to "Clean",
                            "serif" to "Classic",
                            "cyber" to "Cyber"
                        )
                        fonts.forEach { (key, label) ->
                            val isSelected = clockFont == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onClockFontChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label.uppercase(),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showAlarm) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                .border(1.dp, if (showAlarm) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .clickable { onShowAlarmChange(!showAlarm) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showAlarm) "ALARM: ON" else "ALARM: OFF",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (showAlarm) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 4. Layout Split Ratio Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val firstLabel = if (isLandscape) "LEFT" else "TOP"
                    val secondLabel = if (isLandscape) "RIGHT" else "BOTTOM"
                    val firstPct = (splitRatio * 100).roundToInt()
                    val secondPct = 100 - firstPct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$firstLabel / $secondLabel LAYOUT RATIO",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "$firstPct% / $secondPct%",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = splitRatio,
                        onValueChange = onSplitRatioChange,
                        valueRange = 0.30f..0.70f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // 5. Battery Telemetry Style
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GAUGE TELEMETRY STYLE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val styles = listOf("halo" to "Halo", "reactor_ticks" to "Reactor", "dual_wings" to "Wings", "tachometer" to "Tacho")
                        styles.forEach { (key, label) ->
                            val isSelected = batteryStyle == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onBatteryStyleChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 6. Inactivity Action (Physical Screen Off vs OLED Shield)
                var showHardwareLockInfo by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "INACTIVITY SLEEP ACTION",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "About Hardware Lock",
                            tint = if (showHardwareLockInfo) MaterialTheme.colorScheme.primary else Color(0xFFFFB300),
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHardwareLockInfo = !showHardwareLockInfo }
                        )
                    }
                    if (showHardwareLockInfo) {
                        Text(
                            text = "HARDWARE LOCK: Uses Shizuku ADB power commands to blank the physical display panel. Note: System security automatically locks the device; waking requires pressing the physical power button twice and unlocking.",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val actions = listOf("overlay" to "OLED Shield (Overlay)", "screen_off" to "Hardware Lock (Screen Off)")
                        actions.forEach { (key, label) ->
                            val isSelected = sleepAction == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onSleepActionChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 7. Inactivity Timeout
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "INACTIVITY TIMEOUT",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val timeouts = listOf("5s", "15s", "30s", "1m", "2m", "5m", "never")
                        timeouts.forEach { timeout ->
                            val isSelected = sleepTimeout == timeout
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color(0xFF121824))
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onSleepTimeoutChange(timeout) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeout.uppercase(),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


