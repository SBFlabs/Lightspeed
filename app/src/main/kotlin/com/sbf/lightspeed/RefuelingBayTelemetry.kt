package com.sbf.lightspeed

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class RefuelingBatteryState(
    val batteryPct: Int,
    val batteryTempC: Float,
    val isCharging: Boolean,
    val isFull: Boolean,
    val wattage: Float,
    val voltageMv: Int,
    val currentMa: Int,
    val timeRemainingMinutes: Long,
    val chargeTypeLabel: String
)

@Composable
internal fun rememberRefuelingBatteryTelemetry(context: Context): RefuelingBatteryState {
    var batteryPct by remember { mutableIntStateOf(0) }
    var batteryTempC by remember { mutableFloatStateOf(28.0f) }
    var isCharging by remember { mutableStateOf(false) }
    var isFull by remember { mutableStateOf(false) }
    var wattage by remember { mutableFloatStateOf(0f) }
    var voltageMv by remember { mutableIntStateOf(0) }
    var currentMa by remember { mutableIntStateOf(0) }
    var timeRemainingMinutes by remember { mutableLongStateOf(-1L) }
    var chargeTypeLabel by remember { mutableStateOf("Standard Charge") }

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
                    val currentUa: Int = if (abs(currentNowUa) < 10_000) abs(currentNowUa) * 1000 else abs(currentNowUa)
                    currentMa = (currentUa / 1000).coerceAtLeast(0)
                    val rawWattage = (voltageMv / 1000f) * (currentUa / 1_000_000f)
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

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                val latestIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                if (latestIntent != null) {
                    receiver.onReceive(context, latestIntent)
                }
                handler.postDelayed(this, 3000L)
            }
        }
        handler.post(runnable)

        onDispose {
            handler.removeCallbacksAndMessages(null)
            try { context.unregisterReceiver(receiver) } catch (e: Exception) { logSwallowed("RefuelingBayScreen", "RefuelingBayContent:229", e) }
        }
    }

    return RefuelingBatteryState(
        batteryPct = batteryPct,
        batteryTempC = batteryTempC,
        isCharging = isCharging,
        isFull = isFull,
        wattage = wattage,
        voltageMv = voltageMv,
        currentMa = currentMa,
        timeRemainingMinutes = timeRemainingMinutes,
        chargeTypeLabel = chargeTypeLabel
    )
}

internal fun Modifier.refuelingBayUnlockSwipe(
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

fun normalizeRefuelingTimeout(value: String): String = when (value) {
    "1m" -> "60s"
    "2m" -> "120s"
    "5m" -> "300s"
    else -> value
}

@Composable
internal fun CryoClockHeader(
    currentTimeStr: String,
    currentDateStr: String,
    nextAlarmStr: String?,
    showAlarm: Boolean,
    clockFormat: String,
    clockFont: String,
    isLandscape: Boolean,
    onLongPress: (() -> Unit)? = null
) {
    val clockFontFamily = when (clockFont) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        else -> FontFamily.Monospace
    }
    val clockLetterSpacing = when {
        clockFormat == "orbital" || clockFormat == "stardate" -> if (isLandscape) 1.0.sp else 1.2.sp
        clockFont == "cyber" -> if (isLandscape) 3.0.sp else 3.5.sp
        else -> 2.sp
    }
    val clockFontSize = when {
        clockFormat == "orbital" || clockFormat == "stardate" -> if (isLandscape) 21.sp else 25.sp
        currentTimeStr.length > 8 -> if (isLandscape) 28.sp else 34.sp
        else -> if (isLandscape) 36.sp else 44.sp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = if (onLongPress != null) {
            Modifier
                .padding(top = 4.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onLongPress() })
                }
        } else {
            Modifier
        }
    ) {
        Text(
            text = currentTimeStr,
            fontSize = clockFontSize,
            fontWeight = if (clockFont == "cyber") FontWeight.Bold else FontWeight.Light,
            fontFamily = clockFontFamily,
            letterSpacing = clockLetterSpacing,
            maxLines = 1,
            softWrap = false,
            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.95f)
        )
        if (currentDateStr.isNotEmpty() || isLandscape) {
            Text(
                text = currentDateStr.uppercase(Locale.US),
                fontSize = if (isLandscape) 11.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = if (isLandscape) 1.2.sp else 1.4.sp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
            )
        }
        if ((showAlarm || isLandscape) && nextAlarmStr != null) {
            Spacer(modifier = Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = LightspeedStyle.colors.textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = nextAlarmStr,
                    fontSize = if (isLandscape) 10.5.sp else 11.sp,
                    color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.7f)
                )
            }
        }
    }
}
