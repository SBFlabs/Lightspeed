package com.sbf.lightspeed.settings

import com.sbf.lightspeed.BuildConfig
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.system.safeReloadPreferences

import android.app.Activity
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedFlightNotificationManager
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level floating settings coordinator for Lightspeed.
 * Manages presentation animations, expand/collapse toggles, and matrix config views.
 */
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var isVisible by remember { mutableStateOf(false) }
    val isCapturingBackdrop = CentralCommandBackdropBlur.isCapturing
    val langMode by com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.collectAsState()
    LaunchedEffect(isCapturingBackdrop) {
        if (!isCapturingBackdrop) {
            isVisible = true
        }
    }

    var showGuidebook by remember { mutableStateOf(false) }
    var showTypographyDialog by remember { mutableStateOf(false) }
    var typographyRootTopLeft by remember { mutableStateOf(Offset.Zero) }
    var typographyIconWindowCenter by remember { mutableStateOf(Offset.Zero) }
    var typographyIconSizePx by remember { mutableFloatStateOf(0f) }
    var showCentralCommandDeck by rememberSaveable { mutableStateOf(false) }
    val initialDecision = remember { SupporterReminderController.consumeInitialLaunchGate(context) }
    val initialGate = initialDecision.gate
    var initialGateType by rememberSaveable { mutableStateOf(initialGate.name) }
    var startWelcomeForInitialGate by rememberSaveable { mutableStateOf(initialDecision.showWelcome) }
    val initialPhase = remember {
        when {
            initialGate == CentralCommandLaunchGate.FRESH_INSTALL ||
                initialGate == CentralCommandLaunchGate.VERSION_UPDATE -> "WHATS_NEW"
            !LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context) -> "CORE_SETUP"
            initialGate == CentralCommandLaunchGate.BUILD_REFRESH ||
                !CentralCommandWarmupCoordinator.isUiWarmed -> "WARMING_UP"
            else -> "READY"
        }
    }
    var launchGatePhase by rememberSaveable {
        mutableStateOf(initialPhase)
    }
    var mountMatrixDeck by rememberSaveable {
        mutableStateOf(initialPhase == "READY")
    }
    var slideSettled by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (isVisible && !slideSettled) {
            delay(340L)
            slideSettled = true
        }
    }
    var supporterReminderBadge by rememberSaveable {
        mutableStateOf<String?>(null)
    }
    var showWhatsNewMorph by rememberSaveable { mutableStateOf(false) }
    var replayWelcomeFromGuidebook by rememberSaveable { mutableStateOf(false) }
    var coreSetupPreviewMode by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(launchGatePhase) {
        if (launchGatePhase == "READY" && supporterReminderBadge == null) {
            kotlinx.coroutines.delay(650L)
            supporterReminderBadge = SupporterReminderController.checkAndConsumeReminderReason(context)
        }
    }
    var showSupporterInfinityDialog by rememberSaveable { mutableStateOf(false) }
    val initialTargetSection = remember { (context as? android.app.Activity)?.intent?.getStringExtra("target_section") }
    val initialTargetTab = remember { (context as? android.app.Activity)?.intent?.getIntExtra("target_tab", if (initialTargetSection != null) 1 else -1) ?: -1 }
    var targetJumpTab by remember { mutableIntStateOf(initialTargetTab) }
    var targetJumpSection by remember { mutableStateOf<String?>(initialTargetSection) }
    var isAllExpanded by remember { mutableStateOf(false) }

    val dismissAction = {
        isVisible = false
        prefs.edit()
            .putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_RIGHT_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SIDEBAR_LEFT_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_NOTCH_TEST_BEACON, false)
            .putBoolean(LightspeedPreferences.KEY_HORIZON_RAIL_PREVIEW, false)
            .putBoolean(LightspeedPreferences.KEY_SECTION_SENSOR_DECK_EXPANDED, false)
            .putBoolean(LightspeedPreferences.KEY_SECTION_TELEMETRY_EXPANDED, false)
            .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_GEOM, false)
            .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_COLOR, false)
            .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_TEXT, false)
            .putBoolean(LightspeedPreferences.KEY_SUB_HORIZON_RAIL_CUSTOM, false)
            .apply()
        safeReloadPreferences()
        scope.launch {
            delay(300)
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    BackHandler {
        dismissAction()
    }

    val snapshot = LocalDeckTheme.current
    val liveBackdropOverride by DeckThemeManager.liveBackdropOpacityOverride.collectAsState()
    val effectiveBackdropOpacity = (liveBackdropOverride ?: snapshot.dimAmount).coerceIn(0f, 0.95f)
    val isCapturing = CentralCommandBackdropBlur.isCapturing
    val hasActiveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (CentralCommandActivity.isCrossWindowBlurActive || CentralCommandBackdropBlur.backdropBitmap != null)

    val animatedScrimAlpha by animateFloatAsState(
        targetValue = when {
            !isVisible || isCapturing -> 0f
            hasActiveBlur -> (effectiveBackdropOpacity * 0.85f).coerceIn(0f, 0.92f)
            else -> effectiveBackdropOpacity
        },
        animationSpec = tween(durationMillis = if (liveBackdropOverride != null) 45 else 300),
        label = "scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { dismissAction() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = animatedScrimAlpha))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible && !isCapturing,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 240)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 260, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 220))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.94f)
                        .onGloballyPositioned { coords ->
                            typographyRootTopLeft = coords.positionInWindow()
                        }
                        .clickable(enabled = false) {}
                ) {
                    var refreshKey by remember { mutableStateOf(0) }
                    var toggleAllTrigger by remember { mutableStateOf(0) }
                    val hideMatrixDeck = showWhatsNewMorph || launchGatePhase != "READY"
                    val deckAlpha by animateFloatAsState(
                        targetValue = if (hideMatrixDeck) 0.005f else 1f,
                        animationSpec = tween(durationMillis = 180),
                        label = "cc_morph_alpha"
                    )

                    CompositionLocalProvider(
                        LocalOpenWhatsNew provides {
                            replayWelcomeFromGuidebook = false
                            showWhatsNewMorph = true
                        },
                        LocalTestCoreSetupGate provides if (com.sbf.lightspeed.system.NightlyFeatures.isCoreSetupGateTesterEnabled) {
                            {
                                replayWelcomeFromGuidebook = false
                                showWhatsNewMorph = false
                                mountMatrixDeck = false
                                coreSetupPreviewMode = true
                                launchGatePhase = "CORE_SETUP"
                            }
                        } else {
                            null
                        },
                        LocalTestWarmupGate provides if (com.sbf.lightspeed.system.NightlyFeatures.isWarmupGateTesterEnabled) {
                            {
                                initialGateType = CentralCommandLaunchGate.FRESH_INSTALL.name
                                startWelcomeForInitialGate = true
                                replayWelcomeFromGuidebook = false
                                showWhatsNewMorph = false
                                mountMatrixDeck = false
                                launchGatePhase = "WHATS_NEW"
                            }
                        } else {
                            null
                        }
                    ) {
                        if (mountMatrixDeck) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = deckAlpha
                                    }
                            ) {
                                FloatingOverlayContainer(
                        title = com.sbf.lightspeed.system.LightspeedLanguageEngine.resolve(com.sbf.lightspeed.system.LightspeedVocabulary.Key.CENTRAL_COMMAND, langMode),
                        onDismiss = { dismissAction() },
                        onTitleClick = {
                            showCentralCommandDeck = true
                        },
                        onTitleLongClick = {
                            val action = LightspeedPreferences.getCentralCommandLongPressAction(context)
                            when (action) {
                                "toggle_master_flight" -> {
                                    val current = LightspeedPreferences.isMasterFlightArmed(context)
                                    val next = !current
                                    LightspeedPreferences.setMasterFlightArmed(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) lsString(R.string.ls_toast_flight_armed) else lsString(R.string.ls_toast_flight_standby), Toast.LENGTH_SHORT).show()
                                }
                                "toggle_all_deflectors" -> {
                                    val leftOn = LightspeedPreferences.isLeftDeflectorEnabled(context)
                                    val rightOn = LightspeedPreferences.isRightDeflectorEnabled(context)
                                    val next = !(leftOn || rightOn)
                                    LightspeedPreferences.setLeftDeflectorEnabled(context, next)
                                    LightspeedPreferences.setRightDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) lsString(R.string.ls_toast_all_deflectors_on) else lsString(R.string.ls_toast_all_deflectors_off), Toast.LENGTH_SHORT).show()
                                }
                                "toggle_left_deflector" -> {
                                    val next = !LightspeedPreferences.isLeftDeflectorEnabled(context)
                                    LightspeedPreferences.setLeftDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) lsString(R.string.ls_toast_left_flank_on) else lsString(R.string.ls_toast_left_flank_off), Toast.LENGTH_SHORT).show()
                                }
                                "toggle_right_deflector" -> {
                                    val next = !LightspeedPreferences.isRightDeflectorEnabled(context)
                                    LightspeedPreferences.setRightDeflectorEnabled(context, next)
                                    LightspeedAccessibilityService.instance?.updateOverlaysVisibility()
                                    LightspeedFlightNotificationManager.update(context)
                                    Toast.makeText(context, if (next) lsString(R.string.ls_toast_right_flank_on) else lsString(R.string.ls_toast_right_flank_off), Toast.LENGTH_SHORT).show()
                                }
                                else -> {
                                    showCentralCommandDeck = true
                                }
                            }
                        },
                        headerControl = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .onGloballyPositioned { coords ->
                                            if (coords.size.width > 0 && coords.size.height > 0) {
                                                val pos = coords.positionInWindow()
                                                typographyIconWindowCenter = Offset(
                                                    pos.x + coords.size.width / 2f,
                                                    pos.y + coords.size.height / 2f
                                                )
                                                typographyIconSizePx = coords.size.width.toFloat()
                                            }
                                        }
                                        .clickable { showTypographyDialog = !showTypographyDialog },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.FormatSize,
                                        contentDescription = lsString(R.string.ls_cd_typography),
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                GuidebookLanguageButton(onOpenGuidebook = { showGuidebook = true })

                                Box(
                                    modifier = Modifier
                                        .width(30.dp)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable {
                                            isAllExpanded = !isAllExpanded
                                            toggleAllTrigger++
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isAllExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                        contentDescription = lsString(R.string.ls_cd_expand_collapse),
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        },
                        footer = {
                            CentralCommandFooter(
                                context = context,
                                onVersionClick = {
                                    showWhatsNewMorph = true
                                }
                            )
                        }
                    ) {
                        CentralCommandMatrixFields(
                            context = context,
                            prefs = prefs,
                            toggleAllTrigger = toggleAllTrigger,
                            jumpTargetTab = targetJumpTab,
                            jumpTargetSection = targetJumpSection,
                            onRefreshNeeded = { /* Local state reacts immediately */ }
                        )
                    }
                            }
                        }
                    }

                    val iconCenterInContainer = if (typographyIconWindowCenter != Offset.Zero) {
                        typographyIconWindowCenter - typographyRootTopLeft
                    } else Offset.Zero

                    CentralCommandTypographyMenu(
                        context = context,
                        expanded = showTypographyDialog,
                        iconCenterInContainer = iconCenterInContainer,
                        iconSizePx = typographyIconSizePx,
                        onDismiss = { showTypographyDialog = false }
                    )

                    if (showCentralCommandDeck) {
                        CentralCommandDeckDialog(
                            context = context,
                            prefs = prefs,
                            onDismiss = { showCentralCommandDeck = false },
                            onRefreshNeeded = { /* Local state reacts immediately */ }
                        )
                    }

                    if (showGuidebook) {
                        GuidebookBottomSheet(
                            onDismiss = { showGuidebook = false },
                            onNavigateToSection = { tabIdx, secKey ->
                                targetJumpTab = tabIdx
                                targetJumpSection = secKey
                                showGuidebook = false
                            },
                            onReplayWelcome = {
                                showGuidebook = false
                                replayWelcomeFromGuidebook = true
                                showWhatsNewMorph = true
                            }
                        )
                    }

                    val activeReminderBadge = supporterReminderBadge
                    if (activeReminderBadge != null) {
                        LightspeedSupporterReminderDialog(
                            context = context,
                            milestoneBadge = activeReminderBadge,
                            onDismiss = { supporterReminderBadge = null },
                            onOpenInfinityUnlock = {
                                supporterReminderBadge = null
                                showSupporterInfinityDialog = true
                            }
                        )
                    }

                    val showingWhatsNew = showWhatsNewMorph || launchGatePhase == "WHATS_NEW"
                    AnimatedVisibility(
                        visible = showingWhatsNew,
                        enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
                            scaleIn(
                                initialScale = 0.92f,
                                animationSpec = spring(
                                    dampingRatio = 0.84f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 170)) +
                            scaleOut(
                                targetScale = 0.93f,
                                animationSpec = tween(durationMillis = 170)
                            )
                    ) {
                        CentralCommandWhatsNewMorphPanel(
                            context = context,
                            startOnWelcome = replayWelcomeFromGuidebook ||
                                (!showWhatsNewMorph && startWelcomeForInitialGate),
                            onDismiss = {
                                if (launchGatePhase == "WHATS_NEW") {
                                    launchGatePhase = if (!LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                                        "CORE_SETUP"
                                    } else {
                                        "WARMING_UP"
                                    }
                                }
                                startWelcomeForInitialGate = false
                                replayWelcomeFromGuidebook = false
                                showWhatsNewMorph = false
                            },
                            onOpenInfinityUnlock = {
                                if (launchGatePhase == "WHATS_NEW") {
                                    launchGatePhase = if (!LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)) {
                                        "CORE_SETUP"
                                    } else {
                                        "WARMING_UP"
                                    }
                                }
                                startWelcomeForInitialGate = false
                                replayWelcomeFromGuidebook = false
                                showWhatsNewMorph = false
                                showSupporterInfinityDialog = true
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = launchGatePhase == "CORE_SETUP" && !showWhatsNewMorph,
                        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                            scaleIn(
                                initialScale = 0.94f,
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 170)) +
                            scaleOut(
                                targetScale = 0.96f,
                                animationSpec = tween(durationMillis = 170)
                            )
                    ) {
                        CentralCommandCoreSetupPanel(
                            context = context,
                            previewMode = coreSetupPreviewMode,
                            onClosePreview = {
                                coreSetupPreviewMode = false
                                mountMatrixDeck = true
                                launchGatePhase = "READY"
                            },
                            onCoreArmed = {
                                launchGatePhase = if (CentralCommandWarmupCoordinator.isUiWarmed) {
                                    mountMatrixDeck = true
                                    "READY"
                                } else {
                                    "WARMING_UP"
                                }
                            },
                            onOpenGuidebook = { showGuidebook = true }
                        )
                    }

                    AnimatedVisibility(
                        visible = launchGatePhase == "WARMING_UP" && !showWhatsNewMorph,
                        enter = fadeIn(animationSpec = tween(durationMillis = 220)) +
                            scaleIn(
                                initialScale = 0.95f,
                                animationSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 180)) +
                            scaleOut(
                                targetScale = 1.03f,
                                animationSpec = tween(durationMillis = 180)
                            )
                    ) {
                        CentralCommandWarmupPanel(
                            context = context,
                            canStageDeck = slideSettled,
                            onStageDeck = { mountMatrixDeck = true },
                            onWarmupComplete = {
                                mountMatrixDeck = true
                                launchGatePhase = "READY"
                            },
                            onProceedAnyway = {
                                mountMatrixDeck = true
                                launchGatePhase = "READY"
                            }
                        )
                    }

                    if (showSupporterInfinityDialog) {
                        LightspeedInfinityDialog(
                            context = context,
                            onDismiss = { showSupporterInfinityDialog = false }
                        )
                    }

                }
            }
        }
    }
}
