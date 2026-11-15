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
import com.sbf.lightspeed.ui.theme.DeckTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

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
    val telemetry = rememberRefuelingBatteryTelemetry(context)
    val batteryPct = telemetry.batteryPct
    val batteryTempC = telemetry.batteryTempC
    val isCharging = telemetry.isCharging
    val isFull = telemetry.isFull
    val wattage = telemetry.wattage
    val timeRemainingMinutes = telemetry.timeRemainingMinutes
    val chargeTypeLabel = telemetry.chargeTypeLabel

    // Keep charging power & thermal history sampling continuously while Refueling Bay is open (including Sleep Shield)
    val latestSampleWattage by rememberUpdatedState(wattage)
    val latestSampleTempC by rememberUpdatedState(batteryTempC)
    val latestIsCharging by rememberUpdatedState(isCharging)
    val latestBatteryPct by rememberUpdatedState(batteryPct)
    LaunchedEffect(Unit) {
        RefuelingTelemetryHistoryBuffer.ensureLoaded(context)
        while (latestBatteryPct <= 0) {
            delay(50L)
        }
        while (true) {
            RefuelingTelemetryHistoryBuffer.record(
                wattage = latestSampleWattage,
                tempC = latestSampleTempC,
                isCharging = latestIsCharging,
                context = context
            )
            delay(2000L)
        }
    }

    // Style, Sleep & Resizable Split Settings
    var batteryStyle by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, "halo") ?: "halo") }
    var gaugeScale by remember { mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_REFUELING_GAUGE_SCALE, 1.0f).coerceIn(0.75f, 1.45f)) }
    var graphMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_GRAPH_MODE, "strip") ?: "strip") }
    var graphSpan by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_GRAPH_SPAN, "charge") ?: "charge") }
    var graphUnpluggedMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_GRAPH_UNPLUGGED_MODE, "live_15m") ?: "live_15m") }
    var showGraphLabels by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_GRAPH_LABELS, true)) }
    var sleepTimeoutSetting by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, "60s") ?: "60s") }
    var sleepAction by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_SLEEP_ACTION, "overlay") ?: "overlay") }
    var splitRatioPortrait by remember { mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_PORTRAIT, 0.52f).coerceIn(0.3f, 0.7f)) }
    var splitRatioLandscape by remember { mutableFloatStateOf(prefs.getFloat(LightspeedPreferences.KEY_REFUELING_SPLIT_RATIO_LANDSCAPE, 0.44f).coerceIn(0.3f, 0.7f)) }
    var clockFormat by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_CLOCK_FORMAT, "24h_sec") ?: "24h_sec") }
    var dateFormat by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_DATE_FORMAT, "full") ?: "full") }
    var clockFont by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_REFUELING_CLOCK_FONT, "mono") ?: "mono") }
    var showAlarm by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_REFUELING_SHOW_ALARM, true)) }
    var showCustomizationModal by remember { mutableStateOf(false) }
    var dockedWidgetTune by remember { mutableStateOf<DockedWidgetTuneData?>(null) }
    var tuneDismissSignal by remember { mutableIntStateOf(0) }

    val sleepTimeoutMs = when (normalizeRefuelingTimeout(sleepTimeoutSetting)) {
        "5s" -> 5_000L
        "15s" -> 15_000L
        "30s" -> 30_000L
        "60s" -> 60_000L
        "120s" -> 120_000L
        "300s" -> 300_000L
        "never" -> Long.MAX_VALUE
        else -> 60_000L
    }
    val sleepTimeoutMsLatest by rememberUpdatedState(sleepTimeoutMs)

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

    // Dynamic Arc Colors
    val dynamicArcColor = when {
        batteryPct >= 100 -> LightspeedStyle.colors.accentOk // Emerald Fusion (100%)
        batteryTempC >= 40.0f -> LightspeedTokens.StatusDanger // High Thermal Crimson (>40°C)
        wattage >= 20.0f -> LightspeedStyle.colors.accentInfo // Warp Electric Cyan (>20W)
        wattage >= 10.0f -> LightspeedStyle.colors.accentWarn // Cruising Solar Gold (10W-20W)
        else -> MaterialTheme.colorScheme.primary
    }

    val thermalBadgeColor = when {
        batteryTempC > 42.0f -> LightspeedTokens.StatusDanger // Throttle (>42°C)
        batteryTempC >= 38.0f -> LightspeedStyle.colors.accentWarn // Warm Caution (38°C-42°C)
        else -> LightspeedStyle.colors.accentInfo.copy(alpha = 0.85f) // Normal (<38°C)
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
                    val year = cal.get(java.util.Calendar.YEAR)
                    val day = cal.get(java.util.Calendar.DAY_OF_YEAR)
                    String.format(Locale.US, "SD %04d.%03d · %02d:%02d", year, day, cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
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

            // Ensure FLAG_KEEP_SCREEN_ON is held continuously until custom sleep timer triggers with "screen_off"
            if (!isSleeping || sleepAction != "screen_off") {
                (activity as? LightspeedRefuelingActivity)?.enforceKeepScreenOn()
            }

            // Check auto-sleep timeout
            val idleDuration = System.currentTimeMillis() - lastInteractionTimestamp
            if (sleepTimeoutMsLatest < Long.MAX_VALUE && idleDuration >= sleepTimeoutMsLatest && !isSleeping) {
                LightspeedAccessibilityService.noteBayDark()
                if (sleepAction == "screen_off") {
                    lastInteractionTimestamp = System.currentTimeMillis()
                    (activity as? LightspeedRefuelingActivity)?.clearKeepScreenOnForCustomSleep()
                    if (com.sbf.lightspeed.system.ElevatedTaskCloser.isShizukuActive) {
                        LightspeedRefuelingActivity.shizukuPanelOff = true
                        com.sbf.lightspeed.system.ElevatedTaskCloser.execShizuku("cmd display power-off 0")
                    } else {
                        com.sbf.lightspeed.system.ActionDispatcher.execute(context, "system:lock_screen")
                    }
                } else {
                    isSleeping = true
                    (activity as? LightspeedRefuelingActivity)?.enforceKeepScreenOn()
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
            try {
                if (isBlackoutEnabled && LightspeedRefuelingActivity.isActive) {
                    LightspeedAccessibilityService.instance?.showBaySleepBlackout()
                }
                while (isSleeping && LightspeedRefuelingActivity.isActive) {
                    if (!lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                        break
                    }
                    LightspeedAccessibilityService.noteBayDark()
                    if (isBlackoutEnabled) {
                        LightspeedAccessibilityService.instance?.sendBaySleepHeartbeat()
                    }
                    delay(1000L)
                }
            } finally {
                LightspeedAccessibilityService.instance?.hideBaySleepBlackout()
            }
        } else {
            LightspeedAccessibilityService.instance?.hideBaySleepBlackout()
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Function to wake up from sleep shield
    fun wakeShield(durationMs: Long = 15_000L) {
        val window = activity.window
        (activity as? LightspeedRefuelingActivity)?.isCustomSleepScreenOffTriggered = false
        (activity as? LightspeedRefuelingActivity)?.enforceKeepScreenOn()
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
                val openClockGaugeStudio = {
                    tuneDismissSignal++
                    dockedWidgetTune = null
                    showCustomizationModal = true
                }

                RefuelingSplitHostLayout(
                    isLandscape = isLandscape,
                    splitRatio = if (isLandscape) splitRatioLandscape else splitRatioPortrait,
                    context = context,
                    activity = activity,
                    onDismiss = onDismiss,
                    onUserInteraction = { lastInteractionTimestamp = System.currentTimeMillis() },
                    primaryPane = { paneModifier ->
                        RefuelingPrimaryPane(
                            context = context,
                            activity = activity,
                            dockedWidgetTune = dockedWidgetTune,
                            isLandscape = isLandscape,
                            wattage = wattage,
                            batteryTempC = batteryTempC,
                            batteryPct = batteryPct,
                            chargeTypeLabel = chargeTypeLabel,
                            timeRemainingMinutes = timeRemainingMinutes,
                            isCharging = isCharging,
                            isFull = isFull,
                            batteryStyle = batteryStyle,
                            gaugeScale = gaugeScale,
                            graphMode = graphMode,
                            graphSpan = graphSpan,
                            graphUnpluggedMode = graphUnpluggedMode,
                            showGraphLabels = showGraphLabels,
                            dynamicArcColor = dynamicArcColor,
                            thermalBadgeColor = thermalBadgeColor,
                            currentTimeStr = currentTimeStr,
                            currentDateStr = currentDateStr,
                            nextAlarmStr = nextAlarmStr,
                            showAlarm = showAlarm,
                            clockFormat = clockFormat,
                            clockFont = clockFont,
                            onOpenCustomization = openClockGaugeStudio,
                            onCycleGraphSpan = {
                                val nextSpan = nextRefuelingGraphSpan(graphSpan)
                                graphSpan = nextSpan
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_GRAPH_SPAN, nextSpan).apply()
                            },
                            onUserInteraction = { lastInteractionTimestamp = System.currentTimeMillis() },
                            onDismiss = onDismiss,
                            modifier = paneModifier
                        )
                    },
                    secondaryPane = { paneModifier ->
                        RefuelingSecondaryPane(
                            activity = activity,
                            showCustomizationPanel = showCustomizationModal,
                            isLandscape = isLandscape,
                            widgetIds = widgetIds,
                            widgetLayoutMode = widgetLayoutMode,
                            isEditMode = isEditMode,
                            appWidgetHost = appWidgetHost,
                            appWidgetManager = appWidgetManager,
                            reorderVersion = reorderVersion,
                            isStackMemoryEnabled = isStackMemoryEnabled,
                            batteryStyle = batteryStyle,
                            gaugeScale = gaugeScale,
                            graphMode = graphMode,
                            graphSpan = graphSpan,
                            graphUnpluggedMode = graphUnpluggedMode,
                            showGraphLabels = showGraphLabels,
                            sleepTimeout = sleepTimeoutSetting,
                            sleepAction = sleepAction,
                            splitRatio = if (isLandscape) splitRatioLandscape else splitRatioPortrait,
                            clockFormat = clockFormat,
                            dateFormat = dateFormat,
                            clockFont = clockFont,
                            showAlarm = showAlarm,
                            tuneDismissSignal = tuneDismissSignal,
                            onDockedTuneChange = { tune ->
                                dockedWidgetTune = tune
                                if (tune != null) showCustomizationModal = false
                            },
                            onToggleLayoutMode = onToggleLayoutMode,
                            onToggleEditMode = onToggleEditMode,
                            onAddWidget = { showTacticalWidgetPicker = true },
                            onRemoveWidget = onRemoveWidget,
                            onReorderWidget = onReorderWidget,
                            onReconfigureWidget = onReconfigureWidget,
                            onToggleStackMemory = {
                                val newVal = !isStackMemoryEnabled
                                isStackMemoryEnabled = newVal
                                LightspeedPreferences.setRefuelingStackMemoryEnabled(context, newVal)
                            },
                            onBatteryStyleChange = {
                                batteryStyle = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_BATTERY_STYLE, it).apply()
                            },
                            onGaugeScaleChange = {
                                gaugeScale = it
                                prefs.edit().putFloat(LightspeedPreferences.KEY_REFUELING_GAUGE_SCALE, it).apply()
                            },
                            onGraphModeChange = {
                                graphMode = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_GRAPH_MODE, it).apply()
                            },
                            onGraphSpanChange = {
                                graphSpan = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_GRAPH_SPAN, it).apply()
                            },
                            onGraphUnpluggedModeChange = {
                                graphUnpluggedMode = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_GRAPH_UNPLUGGED_MODE, it).apply()
                            },
                            onShowGraphLabelsChange = {
                                showGraphLabels = it
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_GRAPH_LABELS, it).apply()
                            },
                            onSleepTimeoutChange = {
                                sleepTimeoutSetting = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_TIMEOUT, it).apply()
                            },
                            onSleepActionChange = {
                                sleepAction = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_SLEEP_ACTION, it).apply()
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
                            onClockFormatChange = {
                                clockFormat = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_CLOCK_FORMAT, it).apply()
                            },
                            onDateFormatChange = {
                                dateFormat = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_DATE_FORMAT, it).apply()
                            },
                            onClockFontChange = {
                                clockFont = it
                                prefs.edit().putString(LightspeedPreferences.KEY_REFUELING_CLOCK_FONT, it).apply()
                            },
                            onShowAlarmChange = {
                                showAlarm = it
                                prefs.edit().putBoolean(LightspeedPreferences.KEY_REFUELING_SHOW_ALARM, it).apply()
                            },
                            onDismissCustomization = { showCustomizationModal = false },
                            modifier = paneModifier
                        )
                    }
                )
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
            DeckTheme {
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
}
