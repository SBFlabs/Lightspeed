package com.sbf.lightspeed.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Back Tap Engine for Lightspeed.
 *
 * Utilizes Sensor.TYPE_LINEAR_ACCELERATION (Z-axis) to detect sharp impulse peaks
 * for Double Tap (250-450ms) and Triple Tap (<= 700ms) gestures.
 *
 * Features a smart failsafe guardrail: automatically disables screen-off monitoring
 * and releases PARTIAL_WAKE_LOCK when battery <= 20% or system Battery Saver is active.
 */
object LightspeedBackTapEngine : SensorEventListener {
    private const val TAG = "LightspeedBackTap"
    private const val ACCEL_THRESHOLD = 14.0f // m/s^2 peak threshold
    private const val MIN_INTER_TAP_MS = 120L // debounces physical ringing of single tap
    private const val DOUBLE_TAP_MAX_WINDOW_MS = 450L
    private const val TRIPLE_TAP_MAX_WINDOW_MS = 700L
    private const val TAP_DISAMBIGUATION_DELAY_MS = 250L

    private val engineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var appContext: Context? = null
    private var sensorManager: SensorManager? = null
    private var linearAccelSensor: Sensor? = null
    private var powerManager: PowerManager? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var isSensorRegistered = false
    private var isScreenOn = true
    private var isBatteryLowOrPowerSave = false
    private var currentBatteryLevel = 100

    private val tapTimestamps = mutableListOf<Long>()
    private var pendingDoubleTapJob: Job? = null
    private var lastPeakTime = 0L

    // Receiver for Battery, Power Save, Screen On/Off
    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    evaluateMonitoringState()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    evaluateMonitoringState()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level >= 0 && scale > 0) {
                        currentBatteryLevel = (level * 100) / scale
                    }
                    checkFailsafeStatus()
                }
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    checkFailsafeStatus()
                }
            }
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        sensorManager = appContext?.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        powerManager = appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager

        try {
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lightspeed:BackTapWakeLock")
            wakeLock?.setReferenceCounted(false)
        } catch (_: Exception) {}

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        try {
            appContext?.registerReceiver(stateReceiver, filter)
        } catch (_: Exception) {}

        isScreenOn = powerManager?.isInteractive ?: true
        checkFailsafeStatus()
        evaluateMonitoringState()
    }

    fun reloadPreferences() {
        checkFailsafeStatus()
        evaluateMonitoringState()
    }

    private fun checkFailsafeStatus() {
        val ctx = appContext ?: return
        val pm = powerManager ?: (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)
        val isPowerSave = pm?.isPowerSaveMode == true
        val isLowBattery = currentBatteryLevel <= 20

        val oldState = isBatteryLowOrPowerSave
        isBatteryLowOrPowerSave = isPowerSave || isLowBattery

        if (oldState != isBatteryLowOrPowerSave) {
            evaluateMonitoringState()
        }
    }

    private fun evaluateMonitoringState() {
        val ctx = appContext ?: return
        val prefs = ctx.defaultPrefs()
        val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_BACK_TAP_ENABLED, false)
        val scope = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_SCOPE, "screen_on") ?: "screen_on"

        if (!isEnabled || linearAccelSensor == null) {
            stopMonitoring()
            return
        }

        val shouldMonitor = when (scope) {
            "screen_on" -> isScreenOn
            "screen_off" -> !isScreenOn && !isBatteryLowOrPowerSave
            "always" -> isScreenOn || (!isBatteryLowOrPowerSave)
            else -> isScreenOn
        }

        if (shouldMonitor) {
            startMonitoring(allowWakeLock = (!isScreenOn && (scope == "screen_off" || scope == "always") && !isBatteryLowOrPowerSave))
        } else {
            stopMonitoring()
        }
    }

    private fun startMonitoring(allowWakeLock: Boolean) {
        if (!isSensorRegistered && linearAccelSensor != null) {
            val registered = sensorManager?.registerListener(this, linearAccelSensor, SensorManager.SENSOR_DELAY_GAME) == true
            isSensorRegistered = registered
            Log.d(TAG, "Back Tap sensor registered: $registered")
        }

        if (allowWakeLock) {
            try {
                if (wakeLock?.isHeld != true) {
                    wakeLock?.acquire(2 * 60 * 60 * 1000L) // Safety timeout 2 hours
                    Log.d(TAG, "Acquired partial wakelock for screen-off back tap")
                }
            } catch (_: Exception) {}
        } else {
            releaseWakeLock()
        }
    }

    private fun stopMonitoring() {
        if (isSensorRegistered) {
            try {
                sensorManager?.unregisterListener(this)
            } catch (_: Exception) {}
            isSensorRegistered = false
            Log.d(TAG, "Back Tap sensor unregistered")
        }
        releaseWakeLock()
        resetState()
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Released partial wakelock")
            }
        } catch (_: Exception) {}
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return

        val zAccel = event.values.getOrNull(2) ?: return
        val now = SystemClock.uptimeMillis()

        if (abs(zAccel) >= ACCEL_THRESHOLD) {
            if (now - lastPeakTime < MIN_INTER_TAP_MS) {
                return // Ignore ringing resonance from same physical tap
            }
            lastPeakTime = now
            handleTapDetected(now)
        }
    }

    private fun handleTapDetected(now: Long) {
        val ctx = appContext ?: return
        val prefs = ctx.defaultPrefs()
        val doubleAction = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_DOUBLE, null)?.takeIf { it.isNotBlank() && it != "none" }
        val tripleAction = prefs.getString(LightspeedPreferences.KEY_BACK_TAP_TRIPLE, null)?.takeIf { it.isNotBlank() && it != "none" }

        if (doubleAction == null && tripleAction == null) return

        // Prune timestamps older than window
        tapTimestamps.removeAll { now - it > TRIPLE_TAP_MAX_WINDOW_MS }
        tapTimestamps.add(now)

        when (tapTimestamps.size) {
            1 -> {
                // First tap in sequence: wait for subsequent taps
            }
            2 -> {
                val dt = tapTimestamps[1] - tapTimestamps[0]
                if (dt in 150L..DOUBLE_TAP_MAX_WINDOW_MS) {
                    if (tripleAction != null) {
                        // Delay double tap execution briefly to check if a 3rd tap arrives
                        pendingDoubleTapJob?.cancel()
                        pendingDoubleTapJob = engineScope.launch {
                            delay(TAP_DISAMBIGUATION_DELAY_MS)
                            if (doubleAction != null) {
                                executeTapAction(doubleAction, isTriple = false)
                            }
                            resetState()
                        }
                    } else if (doubleAction != null) {
                        executeTapAction(doubleAction, isTriple = false)
                        resetState()
                    }
                } else {
                    // Tap spacing outside valid window -> treat this as first tap of new sequence
                    tapTimestamps.clear()
                    tapTimestamps.add(now)
                }
            }
            3 -> {
                val dtTotal = tapTimestamps[2] - tapTimestamps[0]
                val dtLast = tapTimestamps[2] - tapTimestamps[1]
                if (dtTotal <= TRIPLE_TAP_MAX_WINDOW_MS && dtLast in 150L..DOUBLE_TAP_MAX_WINDOW_MS) {
                    pendingDoubleTapJob?.cancel()
                    pendingDoubleTapJob = null
                    if (tripleAction != null) {
                        executeTapAction(tripleAction, isTriple = true)
                    } else if (doubleAction != null) {
                        executeTapAction(doubleAction, isTriple = false)
                    }
                    resetState()
                } else {
                    resetState()
                }
            }
            else -> {
                resetState()
            }
        }
    }

    private fun executeTapAction(actionToken: String, isTriple: Boolean) {
        val ctx = appContext ?: return
        if (isTriple) {
            LightspeedHapticEngine.heavyClick(ctx)
        } else {
            LightspeedHapticEngine.click(ctx)
        }
        ActionDispatcher.dispatch(actionToken, ctx)
    }

    private fun resetState() {
        tapTimestamps.clear()
        pendingDoubleTapJob?.cancel()
        pendingDoubleTapJob = null
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun destroy() {
        stopMonitoring()
        try {
            appContext?.unregisterReceiver(stateReceiver)
        } catch (_: Exception) {}
        appContext = null
        sensorManager = null
        linearAccelSensor = null
        powerManager = null
        wakeLock = null
    }
}
