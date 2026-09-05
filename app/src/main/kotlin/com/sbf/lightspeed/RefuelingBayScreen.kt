package com.sbf.lightspeed

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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

            delay(1000L)
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
                        }
                    },
                    onDoubleTap = {
                        if (!isSleeping) {
                            onDismiss()
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                var totalDragY = 0f
                detectVerticalDragGestures(
                    onDragStart = { totalDragY = 0f },
                    onDragEnd = {
                        if (totalDragY < -120f) {
                            try {
                                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
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
            .padding(16.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
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
                                activity = activity,
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
                                activity = activity,
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
