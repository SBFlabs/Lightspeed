package com.sbf.lightspeed

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Refueling Bay: Ambient Charging & Cryo Dashboard for Lightspeed.
 * Features live charging wattage, time to full, minimalist cryo-clock,
 * AMOLED burn-in drift protection, and an interactive 3rd-party AppWidgetHost container.
 */
class LightspeedRefuelingActivity : ComponentActivity() {

    companion object {
        const val APPWIDGET_HOST_ID = 2048
        const val REQUEST_PICK_APPWIDGET = 101
        const val REQUEST_CREATE_APPWIDGET = 102

        @Volatile
        var isActive: Boolean = false
            private set
    }

    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var pendingWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID

    private var configuredWidgetIdState = mutableIntStateOf(-1)

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
            saveWidgetId(widgetId)
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

        // Keep screen on while docked / refueling
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        appWidgetHost = AppWidgetHost(applicationContext, APPWIDGET_HOST_ID)
        appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        appWidgetHost?.startListening()

        val savedWidgetId = defaultPrefs().getInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, -1)
        configuredWidgetIdState.intValue = savedWidgetId

        setContent {
            RefuelingBayScreen(
                activity = this,
                configuredWidgetId = configuredWidgetIdState.intValue,
                appWidgetHost = appWidgetHost,
                appWidgetManager = appWidgetManager,
                onPickWidget = { pickAppWidget() },
                onRemoveWidget = { removeConfiguredWidget() },
                onDismiss = { finish() }
            )
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
            saveWidgetId(widgetId)
        }
    }

    private fun saveWidgetId(widgetId: Int) {
        pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        configuredWidgetIdState.intValue = widgetId
        defaultPrefs().edit().putInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, widgetId).apply()
    }

    private fun removeConfiguredWidget() {
        val currentId = configuredWidgetIdState.intValue
        if (currentId != -1) {
            appWidgetHost?.deleteAppWidgetId(currentId)
            configuredWidgetIdState.intValue = -1
            defaultPrefs().edit().putInt(LightspeedPreferences.KEY_REFUELING_WIDGET_ID, -1).apply()
        }
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
    configuredWidgetId: Int,
    appWidgetHost: AppWidgetHost?,
    appWidgetManager: AppWidgetManager?,
    onPickWidget: () -> Unit,
    onRemoveWidget: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Live Telemetry States
    var batteryPct by remember { mutableIntStateOf(0) }
    var isCharging by remember { mutableStateOf(false) }
    var isFull by remember { mutableStateOf(false) }
    var wattage by remember { mutableFloatStateOf(0f) }
    var voltageMv by remember { mutableIntStateOf(0) }
    var currentMa by remember { mutableIntStateOf(0) }
    var timeRemainingMinutes by remember { mutableLongStateOf(-1L) }
    var chargeTypeLabel by remember { mutableStateOf("Standard Charge") }

    // Cryo Clock States
    var currentTimeStr by remember { mutableStateOf("") }
    var currentDateStr by remember { mutableStateOf("") }
    var nextAlarmStr by remember { mutableStateOf<String?>(null) }

    // AMOLED Burn-In Drift (Micro translation)
    var driftOffsetX by remember { mutableFloatStateOf(0f) }
    var driftOffsetY by remember { mutableFloatStateOf(0f) }

    // Receiver for Live Battery Telemetry
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level >= 0 && scale > 0) {
                    batteryPct = (level * 100f / scale).roundToInt()
                }

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
                        wattage >= 25f -> "⚡ Super Fast Warp Charge"
                        wattage >= 15f -> "⚡ Fast Refueling"
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

    // Ticking Clock & Burn-In Drift Loop
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

    // Pulse animation for charging ring
    val infiniteTransition = rememberInfiniteTransition(label = "refuel_pulse")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // True Black OLED Background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { onDismiss() }
            .offset { IntOffset(driftOffsetX.roundToInt(), driftOffsetY.roundToInt()) }
            .padding(24.dp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header / Cryo Clock
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = currentTimeStr,
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                    color = Color.White.copy(alpha = 0.95f)
                )
                Text(
                    text = currentDateStr.uppercase(Locale.US),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
                if (nextAlarmStr != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = Color.LightGray.copy(alpha = 0.7f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = nextAlarmStr!!,
                            fontSize = 11.5.sp,
                            color = Color.LightGray.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Center Battery Telemetry Ring
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.secondary

                // Outer Background Circle Track
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Progress Arc
                    val sweep = (batteryPct / 100f) * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor.copy(alpha = 0.6f), secondaryColor, primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "$batteryPct",
                            fontSize = 58.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp, start = 2.dp)
                        )
                    }

                    if (wattage > 0f) {
                        Text(
                            text = String.format(Locale.US, "%.1f W", wattage),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (wattage >= 25f) Color(0xFF00E676) else MaterialTheme.colorScheme.secondary
                        )
                    }

                    Text(
                        text = if (isFull) "FULL TANK" else chargeTypeLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )

                    if (timeRemainingMinutes > 0 && isCharging && !isFull) {
                        Text(
                            text = if (timeRemainingMinutes >= 60) {
                                "Time to full: ${timeRemainingMinutes / 60}h ${timeRemainingMinutes % 60}m"
                            } else {
                                "Time to full: ${timeRemainingMinutes}m"
                            },
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Dynamic 3rd-Party AppWidgetHost Surface
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(min = 120.dp, max = 220.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.18f), Color.White.copy(alpha = 0.04f))
                        ),
                        RoundedCornerShape(20.dp)
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (configuredWidgetId != -1 && appWidgetHost != null && appWidgetManager != null) {
                    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(configuredWidgetId)
                    if (appWidgetInfo != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                factory = { ctx ->
                                    val hostView = appWidgetHost.createView(ctx, configuredWidgetId, appWidgetInfo)
                                    hostView.setAppWidget(configuredWidgetId, appWidgetInfo)
                                    hostView
                                }
                            )

                            // Quick Edit / Remove Floating Controls
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconButton(
                                    onClick = { onPickWidget() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Change Widget", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onRemoveWidget() },
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove Widget", tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    } else {
                        // Stale or uninstalled widget ID
                        EmptyWidgetSlot(onPickWidget = onPickWidget)
                    }
                } else {
                    // Unconfigured / Empty Slot
                    EmptyWidgetSlot(onPickWidget = onPickWidget)
                }
            }

            // Bottom Exit Note
            Text(
                text = "✦ Tap anywhere to exit Refueling Bay ✦",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp)
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
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
