package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.ShizukuGate
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow

object CentralCommandTabNavigator {
    val jumpChannel = Channel<Int>(Channel.CONFLATED)
    val pendingJumpTab = kotlinx.coroutines.flow.MutableStateFlow<Int?>(null)
    val flightDeckChannel = Channel<Unit>(Channel.CONFLATED)
    val trackpadShowcaseChannel = Channel<Unit>(Channel.CONFLATED)
    val expandLabsTrackpadTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun triggerJump(targetTab: Int) {
        jumpChannel.trySend(targetTab)
        pendingJumpTab.value = targetTab
    }

    fun triggerFlightDeck() {
        flightDeckChannel.trySend(Unit)
    }

    fun triggerTrackpadShowcase() {
        trackpadShowcaseChannel.trySend(Unit)
    }
}

@Composable
fun CentralCommandMatrixFields(
    context: Context,
    prefs: SharedPreferences,
    viewModel: CentralCommandViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    toggleAllTrigger: Int = 0,
    jumpTargetTab: Int = -1,
    jumpTargetSection: String? = null,
    onRefreshNeeded: () -> Unit = {},
    onWindowStateChanged: (activeTab: Int, isBlueprintActive: Boolean) -> Unit = { _, _ -> }
) {
    val lsColors = LightspeedStyle.colors
    val state = rememberCentralCommandState(
        context = context,
        prefs = prefs,
        toggleAllTrigger = toggleAllTrigger,
        onRefreshNeeded = onRefreshNeeded
    )

    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val uiAlreadyWarm = remember { CentralCommandWarmupCoordinator.isUiWarmed }

    LaunchedEffect(
        state.pagerState.currentPage,
        state.blueprintTabTargetState.value,
        state.popoverTabTargetState.value
    ) {
        onWindowStateChanged(
            state.pagerState.currentPage,
            state.blueprintTabTargetState.value != null || state.popoverTabTargetState.value != null
        )
    }

    LaunchedEffect(Unit) {
        CentralCommandTabNavigator.trackpadShowcaseChannel.receiveAsFlow().collect {
            val leftEnabled = prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
            val rightEnabled = prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
            if (leftEnabled || rightEnabled) {
                val targetTab = if (rightEnabled || !leftEnabled) 2 else 0
                val flankKey = if (targetTab == 0) "left" else "right"
                prefs.edit()
                    .putBoolean("pref_accordion_trackpad_$flankKey", true)
                    .putBoolean("pref_sub_trackpad_geo_$flankKey", true)
                    .apply()
                if (targetTab == 0) {
                    state.isLeftTrackpadGeoExpandedState.value = true
                } else {
                    state.isTrackpadGeoExpandedState.value = true
                }
                CentralCommandTabNavigator.triggerJump(targetTab)
            } else {
                state.pagerState.animateScrollToPage(1)
                state.isExperimentalLabsExpandedState.value = true
                prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, true).apply()
                CentralCommandTabNavigator.expandLabsTrackpadTick.value =
                    CentralCommandTabNavigator.expandLabsTrackpadTick.value + 1
                kotlinx.coroutines.delay(180L)
                val labsIdx = state.listState1.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "experimental_labs" }?.index
                if (labsIdx != null) {
                    state.listState1.animateScrollToItem(labsIdx)
                } else {
                    val order = HudStripTabOrderHelper.resolveOrder(state.sectionOrder1StrState.value, prefs)
                    val pos = order.indexOf("experimental_labs")
                    if (pos >= 0) {
                        state.listState1.animateScrollToItem((pos + 1).coerceAtMost((state.listState1.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        CentralCommandTabNavigator.jumpChannel.receiveAsFlow().collect { target ->
            if (target in 0..2) {
                state.pagerState.animateScrollToPage(target)
                kotlinx.coroutines.delay(220L)
                val listState = if (target == 0) state.listState0 else if (target == 2) state.listState2 else null
                if (listState != null) {
                    val isLeft = target == 0
                    val prefix = if (isLeft) "left_" else ""
                    val trackpadKey = "${prefix}trackpad"
                    val flankKey = if (isLeft) "left" else "right"
                    val expState = if (isLeft) state.isLeftTrackpadExpandedState else state.isTrackpadExpandedState
                    if (!expState.value) {
                        state.toggleSection(target, trackpadKey, false) { expState.value = it }
                        prefs.edit().putBoolean("pref_accordion_trackpad_$flankKey", expState.value).apply()
                    }
                    val total = listState.layoutInfo.totalItemsCount
                    if (total > 0) {
                        val visibleMatch = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == trackpadKey }?.index
                        val targetIndex = if (visibleMatch != null) {
                            visibleMatch
                        } else {
                            val isUnified = if (isLeft) state.isLeftFlankUnifiedState.value else state.isRightFlankUnifiedState.value
                            val defOrder = buildList {
                                add("${prefix}center")
                                if (isUnified) add("${prefix}unified") else { add("${prefix}top"); add("${prefix}bottom") }
                                if (com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs)) add(trackpadKey)
                            }
                            val raw = if (isLeft) state.sectionOrder0StrState.value else state.sectionOrder2StrState.value
                            val order = raw.split(",").map { it.trim() }.filter { it in defOrder }.distinct().let { it + (defOrder - it.toSet()) }
                            val pos = order.indexOf(trackpadKey)
                            val unifyIdx = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "${prefix}unify_flank_actions" }?.index ?: 1
                            if (pos >= 0) (unifyIdx + 1 + pos).coerceIn(0, total - 1) else (total - 1).coerceAtLeast(0)
                        }
                        listState.animateScrollToItem(targetIndex)
                    }
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importFromFile(context, uri)
        }
    }

    LaunchedEffect(jumpTargetTab, jumpTargetSection) {
        if (jumpTargetTab in 0..2) {
            state.pagerState.animateScrollToPage(jumpTargetTab)
        }
        when (jumpTargetSection) {
            "statusbar", "sensor_deck" -> state.isSensorDeckExpandedState.value = true
            "gravity", "synthetic_gravity", "orientation", "attitude" -> state.isSyntheticGravityExpandedState.value = true
            "telemetry", "telemetry_indicators", "beacons", "notch_beacon" -> state.isTelemetryExpandedState.value = true
            "volumekeys", "tactical_hardware" -> {
                state.isTacticalHardwareExpandedState.value = true
                state.isSubVolumeExpandedState.value = true
            }
            "backtap" -> {
                state.isTacticalHardwareExpandedState.value = true
                state.isSubHullTapExpandedState.value = true
            }
            "refueling", "refueling_bay" -> {
                state.isRefuelingExpandedState.value = true
            }
            "backup", "config_vault", "vault" -> state.isConfigVaultExpandedState.value = true
            "airlock", "astrogation_airlock" -> state.isAstrogationAirlockExpandedState.value = true
            "sonic_deck" -> state.isSonicDeckExpandedState.value = true
            "vista_deck", "display_wellness", "wellness", "smart_pixels", "eye_care", "telemetry_hud", "overdrive" -> state.isVistaDeckExpandedState.value = true
            "system_overrides", "scrubbers", "space_pods", "pods" -> state.isSystemOverridesExpandedState.value = true
            "experimental_labs", "labs", "experimental", "core_cooling" -> state.isExperimentalLabsExpandedState.value = true
            "power", "power_button" -> {
                state.isExperimentalLabsExpandedState.value = true
                state.isSubPowerExpandedState.value = true
            }
            "orbital", "orbital_capsule", "notch" -> {
                state.isExperimentalLabsExpandedState.value = true
                state.isNotchCalibExpandedState.value = true
            }
            "wings" -> {
                if (jumpTargetTab == 0) state.isLeftCenterExpandedState.value = true
                else state.isCenterExpandedState.value = true
            }
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isWindowFocused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    var isActivityResumed by remember { mutableStateOf(true) }
    var subAccordionPrefTick by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner, prefs) {
        fun clearAllPreviews() {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_BOTTOM_STATUSBAR_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
                .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
                .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)
                .putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_LEFT, false)
                .putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_RIGHT, false)
                .apply()
            com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(context)
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> isActivityResumed = true
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    isActivityResumed = false
                    clearAllPreviews()
                }
                else -> {}
            }
        }
        val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("pref_sub_") || key.startsWith("pref_accordion_trackpad_") || key.startsWith("pref_bot_trackpad_"))) {
                subAccordionPrefTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            clearAllPreviews()
        }
    }

    // Auto-hide / auto-show preview strictly when parent accordion AND geometry sub-accordion are open AND in focus
    LaunchedEffect(state.pagerState, isActivityResumed, isWindowFocused) {
        snapshotFlow {
            val tick = subAccordionPrefTick
            val inFocus = isActivityResumed && isWindowFocused && !state.pagerState.isScrollInProgress
            val page = state.pagerState.currentPage

            val leftActive = inFocus && page == 0 && (
                (state.isLeftCenterExpandedState.value && state.isLeftCenterGeoExpandedState.value) ||
                (if (state.isLeftFlankUnifiedState.value) {
                    state.isLeftUnifiedExpandedState.value && state.isLeftUnifiedGeoExpandedState.value
                } else {
                    (state.isLeftTopExpandedState.value && state.isLeftTopGeoExpandedState.value) ||
                    (state.isLeftBottomExpandedState.value && state.isLeftBottomGeoExpandedState.value)
                })
            )
            val canopyActive = inFocus && page == 1 &&
                state.isSensorDeckExpandedState.value &&
                state.isStatusBarGeoExpandedState.value
            val bottomCanopyActive = inFocus && page == 1 &&
                state.isSensorDeckExpandedState.value &&
                prefs.getBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_EXPANDED, false) &&
                prefs.getBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_GEO, false)
            val rightActive = inFocus && page == 2 && (
                (state.isCenterExpandedState.value && state.isCenterGeoExpandedState.value) ||
                (if (state.isRightFlankUnifiedState.value) {
                    state.isRightUnifiedExpandedState.value && state.isRightUnifiedGeoExpandedState.value
                } else {
                    (state.isTopExpandedState.value && state.isTopGeoExpandedState.value) ||
                    (state.isBottomExpandedState.value && state.isBottomGeoExpandedState.value)
                })
            )
            val trackpadLeftActive = inFocus && page == 0 &&
                prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false) &&
                state.isLeftTrackpadExpandedState.value &&
                state.isLeftTrackpadGeoExpandedState.value
            val trackpadRightActive = inFocus && page == 2 &&
                prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false) &&
                state.isTrackpadExpandedState.value &&
                state.isTrackpadGeoExpandedState.value

            val anyRail = state.isHorizonRailGeomExpandedState.value || state.isHorizonRailColorExpandedState.value || state.isHorizonRailTextExpandedState.value
            val notchActive = inFocus && page == 1 && state.isTelemetryExpandedState.value && state.isNotchCalibExpandedState.value
            val railActive = inFocus && page == 1 && state.isTelemetryExpandedState.value && anyRail
            @Suppress("UNUSED_VARIABLE")
            val _ignore = tick
            CentralCommandPreviewState(
                left = leftActive,
                canopy = canopyActive,
                bottomCanopy = bottomCanopyActive,
                right = rightActive,
                notch = notchActive,
                rail = railActive,
                trackpadLeft = trackpadLeftActive,
                trackpadRight = trackpadRightActive
            )
        }
            .distinctUntilChanged()
            .collect { target ->
                val curLeft = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
                val curCanopy = prefs.getBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
                val curBottomCanopy = prefs.getBoolean(LightspeedPreferences.KEY_BOTTOM_STATUSBAR_PREVIEW, false)
                val curRight = prefs.getBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
                val curNotch = prefs.getBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
                val curRail = prefs.getBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)
                val curTpLeft = prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_LEFT, false)
                val curTpRight = prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_RIGHT, false)

                if (curLeft != target.left || curCanopy != target.canopy || curBottomCanopy != target.bottomCanopy ||
                    curRight != target.right || curNotch != target.notch || curRail != target.rail ||
                    curTpLeft != target.trackpadLeft || curTpRight != target.trackpadRight
                ) {
                    prefs.edit()
                        .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, target.left)
                        .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, target.canopy)
                        .putBoolean(LightspeedPreferences.KEY_BOTTOM_STATUSBAR_PREVIEW, target.bottomCanopy)
                        .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, target.right)
                        .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, target.notch)
                        .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, target.rail)
                        .putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_LEFT, target.trackpadLeft)
                        .putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_RIGHT, target.trackpadRight)
                        .apply()
                    if (curTpLeft != target.trackpadLeft || curTpRight != target.trackpadRight) {
                        com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(context)
                    }
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        var isA11yActive by remember {
            mutableStateOf(com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context))
        }
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    isA11yActive = com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        LaunchedEffect(isA11yActive) {
            if (!isA11yActive) {
                while (!isA11yActive) {
                    kotlinx.coroutines.delay(400)
                    if (com.sbf.lightspeed.system.LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                        isA11yActive = true
                        break
                    }
                }
            }
        }

        if (!isA11yActive) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "[!]",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = lsString(R.string.cc_core_dormant_title),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Text(
                        text = lsString(R.string.cc_core_dormant_body),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                if (rikka.shizuku.Shizuku.pingBinder()) {
                                    if (rikka.shizuku.Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                        val requested = ShizukuGate.requestIfAllowed()
                                        if (requested) {
                                            android.widget.Toast.makeText(context, lsString(R.string.cc_toast_authorize_shizuku), android.widget.Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                    }
                                }
                                val revived = com.sbf.lightspeed.system.LightspeedWatchdogEngine.reviveAccessibilityService(context)
                                if (revived) {
                                    isA11yActive = true
                                    android.widget.Toast.makeText(context, lsString(R.string.cc_toast_core_armed), android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    com.sbf.lightspeed.system.LightspeedWatchdogEngine.openAccessibilitySettings(context)
                                    val serviceLabel = context.getString(R.string.accessibility_service_label)
                                    android.widget.Toast.makeText(context, lsString(R.string.cc_toast_enable_a11y, serviceLabel), android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(lsString(R.string.cc_core_dormant_arm), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                context.startActivity(android.content.Intent(context, com.sbf.lightspeed.system.LightspeedPerimeterActivity::class.java))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(lsString(R.string.cc_core_dormant_perimeter), fontSize = 12.sp)
                        }
                    }
                    OutlinedButton(
                        onClick = { openLightspeedAppInfo(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lsString(R.string.cc_core_dormant_restricted), fontSize = 11.5.sp)
                    }
                }
            }
        }
        var mediaNeedsAccess by remember { mutableStateOf(false) }
        var mediaAccessCheckTick by remember { mutableIntStateOf(0) }
        LaunchedEffect(mediaAccessCheckTick) {
            mediaNeedsAccess = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val hasMedia = prefs.all.values.any { it is String && it.startsWith("system:media_") }
                    val granted = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                        ?.contains(context.packageName) == true || LightspeedNotificationListener.instance != null
                    hasMedia && !granted
                } catch (_: Exception) {
                    false
                }
            }
        }
        DisposableEffect(lifecycleOwner, prefs) {
            val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key.startsWith("pref_macro_action_") || key.startsWith("pref_key_")) {
                    mediaAccessCheckTick++
                }
            }
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    mediaAccessCheckTick++
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
        if (mediaNeedsAccess) {
            DeckCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                onClick = { state.showNotificationAccessDialogState.value = true }
            ) {
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lsString(R.string.cc_media_notif_title), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = lsColors.textPrimary)
                        Text(lsString(R.string.cc_media_notif_body), fontSize = 11.sp, color = lsColors.textPrimary.copy(alpha = 0.85f))
                    }
                }
            }
        }

        // Keep physical Left Deflector | HUD Strip | Right Deflector tab order & horizontal swipe
        // anchored to physical LTR coordinates even when RTL / Left-Handed Layout is active.
        val activeLayoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr
        ) {
        // Balanced, Optically Centered 3-Tab Navigator
        CentralCommandTabBar(
            context = context,
            pagerState = state.pagerState,
            languageMode = currentLanguageMode,
            onOpenPopover = { state.popoverTabTargetState.value = it }
        )

        Spacer(modifier = Modifier.height(10.dp))

        var beyondTabCount by remember { mutableIntStateOf(if (uiAlreadyWarm) 2 else 0) }
        LaunchedEffect(uiAlreadyWarm) {
            if (beyondTabCount < 2) {
                androidx.compose.runtime.withFrameNanos { }
                beyondTabCount = 2
            }
        }
        val onApplyCustomPinned0 = remember(state) {
            { if (state.tabMode0State.value == "custom_pinned") state.applyTabMode(0, "custom_pinned") }
        }
        val onApplyCustomPinned1 = remember(state) {
            { if (state.tabMode1State.value == "custom_pinned") state.applyTabMode(1, "custom_pinned") }
        }
        val onApplyCustomPinned2 = remember(state) {
            { if (state.tabMode2State.value == "custom_pinned") state.applyTabMode(2, "custom_pinned") }
        }
        HorizontalPager(
            state = state.pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 12.dp,
            beyondViewportPageCount = beyondTabCount,
            key = { it }
        ) { pageIndex ->
            CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides activeLayoutDirection
            ) {
            when (pageIndex) {
                0 -> {
                    LeftDeflectorTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isLeftBottomExpandedState = state.isLeftBottomExpandedState,
                        isLeftBottomGeoExpandedState = state.isLeftBottomGeoExpandedState,
                        isLeftBottomGesturesExpandedState = state.isLeftBottomGesturesExpandedState,
                        isLeftBottomScrubExpandedState = state.isLeftBottomScrubExpandedState,
                        isLeftCenterExpandedState = state.isLeftCenterExpandedState,
                        isLeftCenterGeoExpandedState = state.isLeftCenterGeoExpandedState,
                        isLeftFlankUnifiedState = state.isLeftFlankUnifiedState,
                        isLeftTopExpandedState = state.isLeftTopExpandedState,
                        isLeftTopGeoExpandedState = state.isLeftTopGeoExpandedState,
                        isLeftTopGesturesExpandedState = state.isLeftTopGesturesExpandedState,
                        isLeftTopScrubExpandedState = state.isLeftTopScrubExpandedState,
                        isLeftUnifiedExpandedState = state.isLeftUnifiedExpandedState,
                        isLeftUnifiedGeoExpandedState = state.isLeftUnifiedGeoExpandedState,
                        isLeftUnifiedGesturesExpandedState = state.isLeftUnifiedGesturesExpandedState,
                        isLeftUnifiedScrubExpandedState = state.isLeftUnifiedScrubExpandedState,
                        isLeftTrackpadExpandedState = state.isLeftTrackpadExpandedState,
                        isLeftTrackpadGeoExpandedState = state.isLeftTrackpadGeoExpandedState,
                        isLeftTrackpadPhysicsExpandedState = state.isLeftTrackpadPhysicsExpandedState,
                        listState0 = state.listState0,
                        onRefreshNeeded = onRefreshNeeded,
                        pinnedSection0State = state.pinnedSection0State,
                        prefs = prefs,
                        sectionOrder0StrState = state.sectionOrder0StrState,
                        sectionTitles0 = state.sectionTitles0,
                        showUnifyInfoDialogState = state.showUnifyInfoDialogState,
                        showUnifyTemplateDialogForLeftState = state.showUnifyTemplateDialogForLeftState,
                        toggleSection = state.toggleSection,
                        toggleSubSection = state.toggleSubSection,
                        tokenLabelCache = state.tokenLabelCache,
                        onApplyCustomPinned = onApplyCustomPinned0
                    )
                }
                1 -> {
                    HudStripTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        currentThresholdState = state.currentThresholdState,
                        isSystemOverridesExpandedState = state.isSystemOverridesExpandedState,
                        currentZImpulseState = state.currentZImpulseState,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isConfigVaultExpandedState = state.isConfigVaultExpandedState,
                        isExperimentalLabsExpandedState = state.isExperimentalLabsExpandedState,
                        isHorizonRailColorExpandedState = state.isHorizonRailColorExpandedState,
                        isHorizonRailGeomExpandedState = state.isHorizonRailGeomExpandedState,
                        isHorizonRailTextExpandedState = state.isHorizonRailTextExpandedState,
                        isMarqueeSubSectionExpandedState = state.isMarqueeSubSectionExpandedState,
                        isNotchCalibExpandedState = state.isNotchCalibExpandedState,
                        isOemNoticeDemotedState = state.isOemNoticeDemotedState,
                        isRefuelingExpandedState = state.isRefuelingExpandedState,
                        isSensorDeckExpandedState = state.isSensorDeckExpandedState,
                        isSinglePressUnlockedState = state.isSinglePressUnlockedState,
                        isStatusBarGeoExpandedState = state.isStatusBarGeoExpandedState,
                        isStatusBarGesturesExpandedState = state.isStatusBarGesturesExpandedState,
                        isSubHullTapExpandedState = state.isSubHullTapExpandedState,
                        isSubPowerExpandedState = state.isSubPowerExpandedState,
                        isSubVolumeExpandedState = state.isSubVolumeExpandedState,
                        isSyntheticGravityExpandedState = state.isSyntheticGravityExpandedState,
                        isAstrogationAirlockExpandedState = state.isAstrogationAirlockExpandedState,
                        isSonicDeckExpandedState = state.isSonicDeckExpandedState,
                        isVistaDeckExpandedState = state.isVistaDeckExpandedState,
                        isTacticalHardwareExpandedState = state.isTacticalHardwareExpandedState,
                        isTelemetryExpandedState = state.isTelemetryExpandedState,
                        listState1 = state.listState1,
                        onRefreshNeeded = onRefreshNeeded,
                        pendingBackTapScopeState = state.pendingBackTapScopeState,
                        pinnedSection1State = state.pinnedSection1State,
                        prefs = prefs,
                        sectionOrder1StrState = state.sectionOrder1StrState,
                        showAmoledWarningDialogState = state.showAmoledWarningDialogState,
                        showBatteryWarningDialogState = state.showBatteryWarningDialogState,
                        showImportOptionsDialogState = state.showImportOptionsDialogState,
                        showNotificationAccessDialogState = state.showNotificationAccessDialogState,
                        showOemShieldDialogState = state.showOemShieldDialogState,
                        showResetConfirmDialogState = state.showResetConfirmDialogState,
                        singlePressTapCountState = state.singlePressTapCountState,
                        sectionTitles1 = state.sectionTitles1,
                        thresholdCrossedFlashState = state.thresholdCrossedFlashState,
                        toggleSection = state.toggleSection,
                        tokenLabelCache = state.tokenLabelCache,
                        onApplyCustomPinned = onApplyCustomPinned1
                    )
                }
                2 -> {
                    RightDeflectorTabContent(
                        blueprintTabTargetState = state.blueprintTabTargetState,
                        context = context,
                        dynamicActionTokens = state.dynamicActionTokens,
                        isBottomExpandedState = state.isBottomExpandedState,
                        isBottomGeoExpandedState = state.isBottomGeoExpandedState,
                        isBottomGesturesExpandedState = state.isBottomGesturesExpandedState,
                        isBottomScrubExpandedState = state.isBottomScrubExpandedState,
                        isCenterExpandedState = state.isCenterExpandedState,
                        isCenterGeoExpandedState = state.isCenterGeoExpandedState,
                        isLeftFlankUnifiedState = state.isLeftFlankUnifiedState,
                        isRightFlankUnifiedState = state.isRightFlankUnifiedState,
                        isRightUnifiedExpandedState = state.isRightUnifiedExpandedState,
                        isRightUnifiedGeoExpandedState = state.isRightUnifiedGeoExpandedState,
                        isRightUnifiedGesturesExpandedState = state.isRightUnifiedGesturesExpandedState,
                        isRightUnifiedScrubExpandedState = state.isRightUnifiedScrubExpandedState,
                        isTopExpandedState = state.isTopExpandedState,
                        isTopGeoExpandedState = state.isTopGeoExpandedState,
                        isTopGesturesExpandedState = state.isTopGesturesExpandedState,
                        isTopScrubExpandedState = state.isTopScrubExpandedState,
                        isTrackpadExpandedState = state.isTrackpadExpandedState,
                        isTrackpadGeoExpandedState = state.isTrackpadGeoExpandedState,
                        isTrackpadPhysicsExpandedState = state.isTrackpadPhysicsExpandedState,
                        listState2 = state.listState2,
                        onRefreshNeeded = onRefreshNeeded,
                        pinnedSection2State = state.pinnedSection2State,
                        prefs = prefs,
                        sectionOrder2StrState = state.sectionOrder2StrState,
                        sectionTitles2 = state.sectionTitles2,
                        showUnifyInfoDialogState = state.showUnifyInfoDialogState,
                        showUnifyTemplateDialogForRightState = state.showUnifyTemplateDialogForRightState,
                        toggleSection = state.toggleSection,
                        toggleSubSection = state.toggleSubSection,
                        tokenLabelCache = state.tokenLabelCache,
                        onApplyCustomPinned = onApplyCustomPinned2
                    )
                }
            }
            }
        }
        }
        }
        // Central Command Modal Dialogs Deck
        CentralCommandDialogDeck(
            context = context,
            prefs = prefs,
            viewModel = viewModel,
            showResetConfirmDialogState = state.showResetConfirmDialogState,
            showImportOptionsDialogState = state.showImportOptionsDialogState,
            showPasteJsonDialogState = state.showPasteJsonDialogState,
            pastedJsonTextState = state.pastedJsonTextState,
            showSymmetryInfoDialogState = state.showSymmetryInfoDialogState,
            showUnifyInfoDialogState = state.showUnifyInfoDialogState,
            showUnifyTemplateDialogForLeftState = state.showUnifyTemplateDialogForLeftState,
            showUnifyTemplateDialogForRightState = state.showUnifyTemplateDialogForRightState,
            showOemShieldDialogState = state.showOemShieldDialogState,
            showBatteryWarningDialogState = state.showBatteryWarningDialogState,
            showNotificationAccessDialogState = state.showNotificationAccessDialogState,
            showAmoledWarningDialogState = state.showAmoledWarningDialogState,
            pendingBackTapScope = state.pendingBackTapScopeState.value,
            importLauncher = importLauncher,
            onSetLeftFlankUnified = { state.isLeftFlankUnifiedState.value = it },
            onSetRightFlankUnified = { state.isRightFlankUnifiedState.value = it },
            onRefreshNeeded = onRefreshNeeded
        )

        val popoverTarget = state.popoverTabTargetState.value
        if (popoverTarget != null) {
            val tabTitle = when (popoverTarget) {
                0 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_LEFT, currentLanguageMode)
                1 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_CENTER, currentLanguageMode)
                2 -> LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TAB_RIGHT, currentLanguageMode)
                else -> "Avionics Tab"
            }
            val currentMode = when (popoverTarget) {
                0 -> state.tabMode0State.value
                1 -> state.tabMode1State.value
                2 -> state.tabMode2State.value
                else -> "custom_pinned"
            }
            val currentPinned = when (popoverTarget) {
                0 -> state.pinnedSection0State.value
                1 -> state.pinnedSection1State.value
                2 -> state.pinnedSection2State.value
                else -> null
            }
            val secTitles = when (popoverTarget) {
                0 -> state.sectionTitles0
                1 -> state.sectionTitles1
                2 -> state.sectionTitles2
                else -> emptyMap()
            }

            TabAccordionPopover(
                tabIndex = popoverTarget,
                tabTitle = tabTitle,
                currentMode = currentMode,
                pinnedSectionId = currentPinned,
                sectionTitles = secTitles,
                onSelectMode = { newMode: String ->
                    state.applyTabMode(popoverTarget, newMode)
                    if (newMode == "custom_pinned" && (currentPinned == null || currentPinned == "none" || currentPinned.isEmpty())) {
                        state.blueprintTabTargetState.value = popoverTarget
                    }
                    state.popoverTabTargetState.value = null
                    onRefreshNeeded()
                },
                onToggleBlueprintMode = {
                    state.blueprintTabTargetState.value = popoverTarget
                },
                onDismiss = {
                    state.popoverTabTargetState.value = null
                }
            )
        }
    }
}

private data class CentralCommandPreviewState(
    val left: Boolean,
    val canopy: Boolean,
    val bottomCanopy: Boolean,
    val right: Boolean,
    val notch: Boolean,
    val rail: Boolean,
    val trackpadLeft: Boolean,
    val trackpadRight: Boolean
)

