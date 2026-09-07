package com.sbf.lightspeed

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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
                } catch (_: Exception) {}
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

    // Style & Sleep Settings
    val batteryStyle = prefs.getString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, "halo") ?: "halo"
    val sleepTimeoutSetting = prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "30s") ?: "30s"
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

        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
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
                val window = activity.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                val lp = window.attributes
                lp.screenBrightness = 0.01f
                window.attributes = lp
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

    // Sync System Bars with Sleep State
    LaunchedEffect(isSleeping) {
        val window = activity.window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isSleeping) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    // Function to wake up from sleep shield
    fun wakeShield(durationMs: Long = 15_000L) {
        isSleeping = false
        val window = activity.window
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
                    // Left Column: Cryo Clock & Battery Arc Telemetry (Swipe-Up to Unlock Zone)
                    Column(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
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
                            onAddWidget = { showTacticalWidgetPicker = true },
                            isLandscape = true
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
                    // Top Interactive Region: Cryo Clock & Battery Arc (Swipe-Up to Unlock Zone)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .refuelingBayUnlockSwipe(context, activity, onDismiss) {
                                lastInteractionTimestamp = System.currentTimeMillis()
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Header: Minimalist Cryo Clock
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 4.dp)
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
                    }

                    // Middle Section: Multi-Widget Section & Toolbar (Pure widget touch area - no drag stealing)
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
                            onAddWidget = { showTacticalWidgetPicker = true },
                            isLandscape = false
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 240.dp)
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
                                reorderVersion = reorderVersion
                            )
                        }
                    }

                    // Bottom Interactive Region: Exit Note & Generous Bottom Swipe-Up Unlock Zone
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 52.dp)
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
        // When sleeping, this completely covers the entire screen, blocks ALL touches from reaching widgets,
        // and instantly wakes the shield on ANY single tap or touch down anywhere on the screen!
        if (isSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(9999f)
                    .refuelingBayUnlockSwipe(context, activity, onDismiss)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                wakeShield(15_000L)
                            },
                            onTap = {
                                wakeShield(15_000L)
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
            )
        }

        // 3. Tactical In-App Widget Picker Modal (Spaceship Cockpit Catalog)
        if (showTacticalWidgetPicker) {
            TacticalWidgetPickerModal(
                onDismiss = { showTacticalWidgetPicker = false },
                onSelectProvider = { provider ->
                    showTacticalWidgetPicker = false
                    onDeployWidgetProvider?.invoke(provider)
                }
            )
        }
    }
}
