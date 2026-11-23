package com.sbf.lightspeed.system.bot

import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.overrideZeroTransition
import com.sbf.lightspeed.settings.BackdropBlurMode
import com.sbf.lightspeed.settings.BackdropBlurModeManager
import com.sbf.lightspeed.settings.CentralCommandActivity
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.TrackpadPillButton
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import java.util.function.Consumer

/**
 * 12-Theme Dialog for Inspecting, Recording, Looping & Editing Bot Macro Steps and Screen Conditions.
 */
class BotMacroEditorDialog : ComponentActivity() {

    private var crossWindowBlurListener: Consumer<Boolean>? = null

    private fun applyWindowBlur() {
        val snapshot = DeckThemeManager.getSnapshot(this)
        val blurMode = BackdropBlurModeManager.getMode(this)
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val nativeSupported = BackdropBlurModeManager.isNativeBlurSupported(windowManager)
            val useNative = BackdropBlurModeManager.shouldUseNativeBlur(windowManager, blurMode) &&
                snapshot.windowBlurRadiusPx > 0
            CentralCommandActivity.updateCrossWindowBlurState(
                nativeSupported && blurMode != BackdropBlurMode.SCREENSHOT
            )
            val lp = window.attributes
            if (useNative) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                lp.blurBehindRadius = snapshot.windowBlurRadiusPx.coerceIn(0, 150)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                lp.blurBehindRadius = 0
            }
            window.attributes = lp
            BackdropBlurModeManager.applyWindowDimAmount(window, 0f)
        } else {
            CentralCommandActivity.updateCrossWindowBlurState(false)
            BackdropBlurModeManager.applyWindowDimAmount(window, 0f)
        }
    }

    private fun launchAfterDismiss(delayMs: Long = 240L, action: () -> Unit) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        finish()
        mainHandler.postDelayed(action, delayMs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        overrideZeroTransition()
        window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        isActive = true
        LightspeedBotTrackpadOverlay.setSuspendedForDialog(true)
        DeckThemeManager.getSnapshot(this)
        CentralCommandBackdropBlur.registerHostWindow(window)
        if (CentralCommandBackdropBlur.backdropBitmap == null) {
            CentralCommandBackdropBlur.prepareForCentralCommand(windowManager, settleDelayMs = 110L) {
                runOnUiThread { applyWindowBlur() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val listener = Consumer<Boolean> { applyWindowBlur() }
            crossWindowBlurListener = listener
            try {
                windowManager.addCrossWindowBlurEnabledListener(listener)
            } catch (e: Exception) {
                logSwallowed("BotMacroEditorDialog", "addCrossWindowBlurEnabledListener", e)
            }
        }
        applyWindowBlur()

        val botId = intent?.getIntExtra(EXTRA_BOT_ID, 1)?.coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS) ?: 1
        val targetPkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)?.takeIf { it.isNotBlank() }
            ?: LightspeedBotEngine.lastResolvedPkg
        val targetWin = intent?.getStringExtra(EXTRA_TARGET_WINDOW_ID)?.takeIf { it.isNotBlank() }
            ?: LightspeedBotEngine.lastResolvedWindow
        val boundScope = targetPkg to targetWin

        setContent {
            val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
            val blurMode by BackdropBlurModeManager.modeFlow.collectAsState()
            val fallbackBitmap = CentralCommandBackdropBlur.backdropBitmap
            val isCapturing = CentralCommandBackdropBlur.isCapturing
            LaunchedEffect(snapshot, fallbackBitmap, blurMode) {
                applyWindowBlur()
            }

            LightspeedTheme(forceDark = true) {
                DeckTheme {
                    if (!isCapturing) {
                        val lsColors = LightspeedStyle.colors
                        val prefs = remember { defaultPrefs() }
                        val buttonNumber = ((botId - 1) % 3) + 1
                        val repeatPrefKey = remember { "${LightspeedBotContract.KEY_BOT_MACRO_REPEAT_PREFIX}$botId" }
                        val useRecordedDelayPrefKey = remember { "${LightspeedBotContract.KEY_BOT_USE_RECORDED_DELAY_PREFIX}$botId" }
                        val stepDelayMsPrefKey = remember { "${LightspeedBotContract.KEY_BOT_STEP_DELAY_MS_PREFIX}$botId" }
                        var repeatCount by remember { mutableIntStateOf(prefs.getInt(repeatPrefKey, 1)) }
                        var useRecordedDelay by remember { mutableStateOf(prefs.getBoolean(useRecordedDelayPrefKey, false)) }
                        var customStepDelayMs by remember { mutableFloatStateOf(prefs.getInt(stepDelayMsPrefKey, 60).toFloat()) }
                        var showLoopWarningDialog by remember { mutableStateOf(false) }
                        var dontRemindLoopWarn by remember { mutableStateOf(false) }
                        var selectedScope by remember {
                            mutableStateOf(LightspeedBotEngine.resolveEffectiveScope(this@BotMacroEditorDialog, botId, boundScope))
                        }
                        var steps by remember {
                            mutableStateOf<List<BotStep>>(LightspeedBotEngine.getMacroSteps(this@BotMacroEditorDialog, botId, boundScope, selectedScope))
                        }
                        var isLooping by remember { mutableStateOf(LightspeedBotEngine.isLooping) }

                        fun clearCurrentMacro() {
                            LightspeedBotEngine.clearMacro(this@BotMacroEditorDialog, botId, boundScope, selectedScope)
                            steps = emptyList()
                            isLooping = false
                            selectedScope = LightspeedBotContract.DEFAULT_MACRO_SCOPE
                        }

                        fun persistSteps(updated: List<BotStep>) {
                            if (updated.isEmpty()) {
                                clearCurrentMacro()
                                return
                            }
                            steps = updated.toList()
                            LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, updated, boundScope, selectedScope)
                        }

                        fun onScopeChanged(newScope: String) {
                            if (newScope == selectedScope) return
                            val oldScope = selectedScope
                            val existingInNew = LightspeedBotEngine.getMacroSteps(this@BotMacroEditorDialog, botId, boundScope, newScope)
                            if (existingInNew.isEmpty() && steps.isNotEmpty()) {
                                LightspeedBotEngine.moveMacroScope(this@BotMacroEditorDialog, botId, steps, boundScope, oldScope, newScope)
                                selectedScope = newScope
                            } else {
                                selectedScope = newScope
                                if (existingInNew.isNotEmpty()) {
                                    steps = existingInNew
                                } else {
                                    val ed = prefs.edit()
                                    val winScopeKey = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_win_${boundScope.first}_${boundScope.second}"
                                    val globalScopeKey = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_global"
                                    when (newScope) {
                                        LightspeedBotContract.SCOPE_THIS_WINDOW -> {
                                            ed.putString(winScopeKey, LightspeedBotContract.SCOPE_THIS_WINDOW).remove(globalScopeKey)
                                        }
                                        LightspeedBotContract.SCOPE_GLOBAL -> {
                                            ed.putString(globalScopeKey, LightspeedBotContract.SCOPE_GLOBAL).remove(winScopeKey)
                                        }
                                        else -> {
                                            ed.remove(winScopeKey).remove(globalScopeKey)
                                        }
                                    }
                                    if (!ed.commit()) ed.apply()
                                }
                            }
                            LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
                        }

                        val targetDisplayText = when (selectedScope) {
                            LightspeedBotContract.SCOPE_GLOBAL -> lsString(R.string.bot_macro_scope_global)
                            LightspeedBotContract.SCOPE_THIS_WINDOW -> "${boundScope.first} • ${boundScope.second}"
                            else -> boundScope.first
                        }
                        val scopeBadgeText = when (selectedScope) {
                            LightspeedBotContract.SCOPE_THIS_WINDOW -> "THIS WINDOW"
                            LightspeedBotContract.SCOPE_GLOBAL -> "GLOBAL"
                            else -> "PER-APP"
                        }
                        val scopeHintText = when (selectedScope) {
                            LightspeedBotContract.SCOPE_THIS_WINDOW -> lsString(R.string.bot_macro_scope_hint_window)
                            LightspeedBotContract.SCOPE_GLOBAL -> lsString(R.string.bot_macro_scope_hint_global)
                            else -> lsString(R.string.bot_macro_scope_hint_app)
                        }
                        val stepsHeaderScope = when (selectedScope) {
                            LightspeedBotContract.SCOPE_THIS_WINDOW -> boundScope.second.uppercase()
                            LightspeedBotContract.SCOPE_GLOBAL -> "GLOBAL"
                            else -> "PER-APP"
                        }

                        DeckDialog(
                            title = {
                                Column {
                                    Text("B$buttonNumber MACRO & SCREEN CONDITIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                                    Text("◈ $targetDisplayText", fontSize = 10.sp, fontWeight = FontWeight.Medium, color = lsColors.accentInfo)
                                }
                            },
                            onDismissRequest = { finish() },
                            content = {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("BOUND TARGET", fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, color = lsColors.accentInfo)
                                                Text(targetDisplayText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary, maxLines = 1)
                                            }
                                            Text("[$scopeBadgeText]", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = lsColors.accent)
                                        }
                                    }

                                    // Scope Selector Chips: [ This App (Default) | This Window | Global ]
                                    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("MACRO SCOPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            TrackpadPillButton(
                                                text = lsString(R.string.bot_macro_scope_this_app),
                                                isSelected = selectedScope == LightspeedBotContract.SCOPE_THIS_APP,
                                                onClick = { onScopeChanged(LightspeedBotContract.SCOPE_THIS_APP) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            TrackpadPillButton(
                                                text = lsString(R.string.bot_macro_scope_this_window),
                                                isSelected = selectedScope == LightspeedBotContract.SCOPE_THIS_WINDOW,
                                                onClick = { onScopeChanged(LightspeedBotContract.SCOPE_THIS_WINDOW) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            TrackpadPillButton(
                                                text = lsString(R.string.bot_macro_scope_global),
                                                isSelected = selectedScope == LightspeedBotContract.SCOPE_GLOBAL,
                                                onClick = { onScopeChanged(LightspeedBotContract.SCOPE_GLOBAL) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        Text(scopeHintText, fontSize = 9.5.sp, color = lsColors.textSecondary, lineHeight = 12.5.sp)
                                    }

                                    Text("RECORDED STEPS ($stepsHeaderScope): ${steps.size}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.accentInfo)

                        // Quick Smart Screen Element Triggers (Burger Menu, Android 3-Dot Menu, Search)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val quickTargets = listOf(
                                Triple("⌖ Burger Menu", LightspeedBotScreenScanner.TOKEN_BURGER_MENU, 0.08f to 1f),
                                Triple("⌖ Android Menu", LightspeedBotScreenScanner.TOKEN_ANDROID_MORE_MENU, 0.92f to 1f),
                                Triple("⌖ Search", LightspeedBotScreenScanner.TOKEN_SEARCH_BUTTON, 0.80f to 0.85f)
                            )
                            for ((label, token, ratioAndWeight) in quickTargets) {
                                DeckButton(
                                    onClick = {
                                        val updated = steps + BotStep(
                                            stepType = BotStep.TYPE_TAP,
                                            xRatio = ratioAndWeight.first,
                                            yRatio = 0.07f,
                                            delayMs = 250L,
                                            durationMs = 60L,
                                            contentDescription = token,
                                            packageName = boundScope.first,
                                            useElementTarget = true,
                                            windowId = boundScope.second
                                        )
                                        persistSteps(updated)
                                    },
                                    modifier = Modifier.weight(ratioAndWeight.second).height(30.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 9.5.sp, color = lsColors.textPrimary, maxLines = 1)
                                }
                            }
                        }

                        // Macro Repeat Mode Selector: 1x, 2x, 5x, ∞ Loop
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MACRO REPEAT MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accentInfo
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val repeatModes = listOf(
                                    1 to "1x",
                                    2 to "2x",
                                    5 to "5x",
                                    -1 to "∞ Loop"
                                )
                                for ((count, label) in repeatModes) {
                                    val isSelected = if (count == -1) isLooping else (repeatCount == count && !isLooping)
                                    TrackpadPillButton(
                                        text = label,
                                        isSelected = isSelected,
                                        onClick = {
                                            run {
                                                if (count == -1) {
                                                    if (LightspeedBotEngine.isLooping) {
                                                        LightspeedBotEngine.stopContinuousLoop()
                                                        isLooping = false
                                                    } else {
                                                        val isWarnDismissed = prefs.getBoolean(LightspeedBotContract.KEY_BOT_LOOP_WARN_DISMISSED, false)
                                                        if (isWarnDismissed) {
                                                            val appCtx = applicationContext
                                                            val stepsCopy = steps.takeIf { it.isNotEmpty() }?.toList()
                                                            val scopeCopy = selectedScope
                                                            val boundCopy = boundScope
                                                            isLooping = true
                                                            launchAfterDismiss(240L) {
                                                                LightspeedBotEngine.startContinuousLoop(appCtx, botId, stepsCopy, scopeCopy, expectedScopeOverride = boundCopy)
                                                            }
                                                        } else {
                                                            showLoopWarningDialog = true
                                                        }
                                                    }
                                                } else {
                                                    if (isLooping) {
                                                        LightspeedBotEngine.stopContinuousLoop()
                                                        isLooping = false
                                                    }
                                                    repeatCount = count
                                                    prefs.edit().putInt(repeatPrefKey, count).apply()
                                                    LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.padding(top = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = lsColors.accentWarn,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = "Safety: Pressing Volume Up, Volume Down, or Power immediately cancels any active macro loop or recording.",
                                    fontSize = 9.5.sp,
                                    color = lsColors.accentWarn,
                                    lineHeight = 12.5.sp
                                )
                            }
                        }

                        // Step Timing Selector: Custom Delay vs As Recorded
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "STEP TIMING INTER-DELAY",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accentInfo
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TrackpadPillButton(
                                    text = "Custom (${customStepDelayMs.toInt()}ms)",
                                    isSelected = !useRecordedDelay,
                                    isLocked = false,
                                    onClick = {
                                        useRecordedDelay = false
                                        prefs.edit().putBoolean(useRecordedDelayPrefKey, false).apply()
                                        LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                TrackpadPillButton(
                                    text = "As Recorded",
                                    isSelected = useRecordedDelay,
                                    isLocked = false,
                                    onClick = {
                                        useRecordedDelay = true
                                        prefs.edit().putBoolean(useRecordedDelayPrefKey, true).apply()
                                        LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (!useRecordedDelay) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    DeckSlider(
                                        value = customStepDelayMs,
                                        onValueChange = {
                                            customStepDelayMs = it
                                            prefs.edit().putInt(stepDelayMsPrefKey, it.toInt()).apply()
                                        },
                                        valueRange = 0f..2000f,
                                        steps = 39,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${customStepDelayMs.toInt()} ms",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lsColors.textPrimary
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DeckButton(
                                onClick = {
                                    val appCtx = applicationContext
                                    val stepsCopy = steps.toList()
                                    val scopeCopy = selectedScope
                                    val boundCopy = boundScope
                                    launchAfterDismiss(240L) {
                                        LightspeedBotEngine.playBot(appCtx, botId, stepsCopy, scopeCopy, expectedScopeOverride = boundCopy)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp), tint = lsColors.accent)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            DeckButton(
                                onClick = {
                                    val appCtx = applicationContext
                                    val boundCopy = boundScope
                                    val scopeCopy = selectedScope
                                    LightspeedBotTrackpadOverlay.setPendingRecordingLaunch(true)
                                    launchAfterDismiss(160L) {
                                        LightspeedBotEngine.startRecording(appCtx, botId, boundCopy, scopeCopy)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(com.sbf.lightspeed.ui.theme.LightspeedTokens.StatusDangerAlt)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            DeckButton(
                                onClick = {
                                    clearCurrentMacro()
                                    LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
                                },
                                modifier = Modifier.weight(1f),
                                isDestructive = true,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }
                        }

                        if (showLoopWarningDialog) {
                            DeckDialog(
                                onDismissRequest = { showLoopWarningDialog = false },
                                title = { Text("INFINITE LOOP AUTOMATION", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = lsColors.textPrimary) },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "You are arming an infinite macro loop. This macro will repeat continuously until interrupted.\n\nPress Volume Up, Volume Down, or Power at any time for instant emergency cancel.",
                                            fontSize = 11.sp, color = lsColors.textSecondary
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { dontRemindLoopWarn = !dontRemindLoopWarn }) {
                                            Checkbox(
                                                checked = dontRemindLoopWarn,
                                                onCheckedChange = { dontRemindLoopWarn = it },
                                                colors = CheckboxDefaults.colors(checkedColor = lsColors.accent, uncheckedColor = lsColors.textSecondary)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Don't remind me again", fontSize = 10.5.sp, color = lsColors.textPrimary)
                                        }
                                    }
                                },
                                confirmButton = {
                                    DeckButton(
                                        onClick = {
                                            if (dontRemindLoopWarn) {
                                                prefs.edit().putBoolean(LightspeedBotContract.KEY_BOT_LOOP_WARN_DISMISSED, true).apply()
                                            }
                                            showLoopWarningDialog = false
                                            val appCtx = applicationContext
                                            val stepsCopy = steps.takeIf { it.isNotEmpty() }?.toList()
                                            val scopeCopy = selectedScope
                                            val boundCopy = boundScope
                                            isLooping = true
                                            launchAfterDismiss(240L) {
                                                LightspeedBotEngine.startContinuousLoop(appCtx, botId, stepsCopy, scopeCopy, expectedScopeOverride = boundCopy)
                                            }
                                        },
                                        isActive = true
                                    ) { Text("Start Loop", fontWeight = FontWeight.Bold, color = lsColors.accentWarn) }
                                },
                                dismissButton = { DeckButton(onClick = { showLoopWarningDialog = false }) { Text("Cancel", color = lsColors.textSecondary) } }
                            )
                        }

                        if (steps.isEmpty()) {
                            Text(
                                text = "No steps recorded yet. Long-press B1/B2/B3 on the Cybernetic Trackpad (or tap Record above) to record taps, swipes, and screen elements.",
                                fontSize = 11.sp,
                                color = lsColors.textSecondary,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 320.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(steps) { index, step ->
                                    DeckCard(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "STEP ${index + 1}: ${step.stepType.uppercase()} (${step.delayMs}ms)",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = lsColors.textPrimary
                                                )
                                                val condition = when {
                                                    step.useElementTarget -> when {
                                                        step.contentDescription == LightspeedBotScreenScanner.TOKEN_BURGER_MENU -> "⌖ Smart: Burger / Drawer Menu"
                                                        step.contentDescription == LightspeedBotScreenScanner.TOKEN_ANDROID_MORE_MENU -> "⌖ Smart: Android 3-Dot Menu"
                                                        step.contentDescription == LightspeedBotScreenScanner.TOKEN_SEARCH_BUTTON -> "⌖ Smart: Search Button"
                                                        !step.viewIdResourceName.isNullOrBlank() -> "⌖ ID: ${step.viewIdResourceName.substringAfterLast('/')}"
                                                        !step.text.isNullOrBlank() -> "⌖ Text: \"${step.text}\""
                                                        !step.contentDescription.isNullOrBlank() -> "⌖ Desc: \"${step.contentDescription}\""
                                                        else -> "⌖ Element Target"
                                                    }
                                                    step.stepType == BotStep.TYPE_SWIPE -> "◈ Swipe: (${(step.xRatio * 100).toInt()}%, ${(step.yRatio * 100).toInt()}%) → (${(step.endXRatio * 100).toInt()}%, ${(step.endYRatio * 100).toInt()}%) • ${step.durationMs}ms"
                                                    else -> "◈ Tap: (${(step.xRatio * 100).toInt()}%, ${(step.yRatio * 100).toInt()}%) • ${step.durationMs}ms"
                                                }
                                                Text(
                                                    text = condition,
                                                    fontSize = 10.sp,
                                                    color = lsColors.textSecondary
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    val updated = steps.toMutableList().apply { removeAt(index) }
                                                    persistSteps(updated)
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = lsColors.accentDanger)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
            }
            }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyWindowBlur()
    }

    override fun onResume() {
        super.onResume()
        isActive = true
        LightspeedBotTrackpadOverlay.setSuspendedForDialog(true)
        if (CentralCommandBackdropBlur.backdropBitmap == null) {
            CentralCommandBackdropBlur.prepareForCentralCommand(windowManager, settleDelayMs = 110L, hideActiveHosts = true) {
                runOnUiThread { applyWindowBlur() }
            }
        }
        applyWindowBlur()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            isActive = false
            LightspeedBotTrackpadOverlay.setSuspendedForDialog(false)
        }
        if (!CentralCommandActivity.isActive) {
            CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
        }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossWindowBlurListener?.let { listener ->
                try {
                    windowManager.removeCrossWindowBlurEnabledListener(listener)
                } catch (e: Exception) {
                    logSwallowed("BotMacroEditorDialog", "removeCrossWindowBlurEnabledListener", e)
                }
            }
            crossWindowBlurListener = null
        }
        CentralCommandBackdropBlur.unregisterHostWindow(window)
        if (!CentralCommandActivity.isActive) {
            CentralCommandBackdropBlur.onWindowHidden(isChangingConfigurations)
        }
        if (!isChangingConfigurations) {
            isActive = false
            LightspeedBotTrackpadOverlay.setSuspendedForDialog(false)
        }
    }

    companion object {
        @Volatile
        var isActive: Boolean = false
        const val EXTRA_BOT_ID = "extra_bot_id"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_WINDOW_ID = "extra_target_window_id"
    }
}
