package com.sbf.lightspeed.system.bot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.TrackpadPillButton
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * 12-Theme Dialog for Inspecting, Recording, Looping & Editing Bot Macro Steps and Screen Conditions.
 */
class BotMacroEditorDialog : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LightspeedBotTrackpadOverlay.setSuspendedForDialog(true)
        val botId = intent?.getIntExtra(EXTRA_BOT_ID, 1)?.coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS) ?: 1
        val targetPkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)?.takeIf { it.isNotBlank() }
            ?: LightspeedBotEngine.lastResolvedPkg
        val targetWin = intent?.getStringExtra(EXTRA_TARGET_WINDOW_ID)?.takeIf { it.isNotBlank() }
            ?: LightspeedBotEngine.lastResolvedWindow
        val boundScope = targetPkg to targetWin

        setContent {
            val lsColors = LightspeedStyle.colors
            val prefs = remember { defaultPrefs() }
            val buttonNumber = ((botId - 1) % 3) + 1
            val repeatPrefKey = remember { "${LightspeedBotContract.KEY_BOT_MACRO_REPEAT_PREFIX}$botId" }
            val useRecordedDelayPrefKey = remember { "${LightspeedBotContract.KEY_BOT_USE_RECORDED_DELAY_PREFIX}$botId" }
            val stepDelayMsPrefKey = remember { "${LightspeedBotContract.KEY_BOT_STEP_DELAY_MS_PREFIX}$botId" }
            val scopePrefKey = remember {
                "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_win_${boundScope.first}_${boundScope.second}"
            }
            var repeatCount by remember { mutableIntStateOf(prefs.getInt(repeatPrefKey, 1)) }
            var useRecordedDelay by remember { mutableStateOf(prefs.getBoolean(useRecordedDelayPrefKey, false)) }
            var customStepDelayMs by remember { mutableFloatStateOf(prefs.getInt(stepDelayMsPrefKey, 60).toFloat()) }
            var showLoopWarningDialog by remember { mutableStateOf(false) }
            var dontRemindLoopWarn by remember { mutableStateOf(false) }
            var selectedScope by remember {
                mutableStateOf(
                    prefs.getString(scopePrefKey, LightspeedBotContract.SCOPE_THIS_WINDOW)
                        ?: LightspeedBotContract.SCOPE_THIS_WINDOW
                )
            }
            var steps by remember {
                mutableStateOf(
                    LightspeedBotEngine.getMacroSteps(
                        this@BotMacroEditorDialog,
                        botId,
                        boundScope,
                        selectedScope
                    )
                )
            }
            var isLooping by remember { mutableStateOf(LightspeedBotEngine.isLooping) }

            fun persistSteps(updated: List<BotStep>) {
                steps = updated.toMutableList()
                LightspeedBotEngine.saveMacroSteps(
                    this@BotMacroEditorDialog,
                    botId,
                    updated,
                    boundScope,
                    selectedScope
                )
            }

            fun onScopeChanged(newScope: String) {
                selectedScope = newScope
                prefs.edit().putString(scopePrefKey, newScope).apply()
                if (newScope == LightspeedBotContract.SCOPE_THIS_APP) {
                    prefs.edit().putString(
                        "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_app_${boundScope.first}",
                        LightspeedBotContract.SCOPE_THIS_APP
                    ).apply()
                } else if (newScope == LightspeedBotContract.SCOPE_GLOBAL) {
                    prefs.edit().putString(
                        "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_global",
                        LightspeedBotContract.SCOPE_GLOBAL
                    ).apply()
                }
                var loaded = LightspeedBotEngine.getMacroSteps(
                    this@BotMacroEditorDialog,
                    botId,
                    boundScope,
                    newScope
                )
                if (loaded.isEmpty() && steps.isNotEmpty()) {
                    LightspeedBotEngine.saveMacroSteps(
                        this@BotMacroEditorDialog,
                        botId,
                        steps,
                        boundScope,
                        newScope
                    )
                    loaded = steps
                }
                steps = loaded
                LightspeedHapticEngine.tick(this@BotMacroEditorDialog)
            }

            DeckDialog(
                title = {
                    Column {
                        Text(
                            text = "B$buttonNumber MACRO & SCREEN CONDITIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = "◈ ${boundScope.first} • ${boundScope.second}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = lsColors.accentInfo
                        )
                    }
                },
                onDismissRequest = { finish() },
                content = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Bound Package • Window ID Badge
                        DeckCard(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "BOUND TARGET",
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = lsColors.accentInfo
                                    )
                                    Text(
                                        text = "${boundScope.first} • ${boundScope.second}",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lsColors.textPrimary,
                                        maxLines = 1
                                    )
                                }
                                val scopeBadgeText = when (selectedScope) {
                                    LightspeedBotContract.SCOPE_THIS_APP -> "APP-WIDE"
                                    LightspeedBotContract.SCOPE_GLOBAL -> "GLOBAL"
                                    else -> "THIS WINDOW"
                                }
                                Text(
                                    text = "[$scopeBadgeText]",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.accent
                                )
                            }
                        }

                        // Scope Selector Chips: [ This Window | This App | Global ]
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "MACRO SCOPE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = lsColors.accentInfo
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TrackpadPillButton(
                                    text = "This Window",
                                    isSelected = selectedScope == LightspeedBotContract.SCOPE_THIS_WINDOW,
                                    onClick = { onScopeChanged(LightspeedBotContract.SCOPE_THIS_WINDOW) },
                                    modifier = Modifier.weight(1f)
                                )
                                TrackpadPillButton(
                                    text = "This App",
                                    isSelected = selectedScope == LightspeedBotContract.SCOPE_THIS_APP,
                                    onClick = { onScopeChanged(LightspeedBotContract.SCOPE_THIS_APP) },
                                    modifier = Modifier.weight(1f)
                                )
                                TrackpadPillButton(
                                    text = "Global",
                                    isSelected = selectedScope == LightspeedBotContract.SCOPE_GLOBAL,
                                    onClick = { onScopeChanged(LightspeedBotContract.SCOPE_GLOBAL) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Text(
                            text = "RECORDED STEPS (${when (selectedScope) {
                                LightspeedBotContract.SCOPE_THIS_APP -> "APP-WIDE"
                                LightspeedBotContract.SCOPE_GLOBAL -> "GLOBAL"
                                else -> boundScope.second.uppercase()
                            }}): ${steps.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.accentInfo
                        )

                        // Quick Smart Screen Element Triggers (Burger Menu, Android 3-Dot Menu, Search)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val updated = steps.toMutableList().apply {
                                        add(
                                            BotStep(
                                                stepType = BotStep.TYPE_TAP,
                                                xRatio = 0.08f,
                                                yRatio = 0.07f,
                                                delayMs = 250L,
                                                durationMs = 60L,
                                                contentDescription = LightspeedBotScreenScanner.TOKEN_BURGER_MENU,
                                                packageName = boundScope.first,
                                                useElementTarget = true,
                                                windowId = boundScope.second
                                            )
                                        )
                                    }
                                    persistSteps(updated)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(30.dp)
                            ) {
                                Text("⌖ Burger Menu", fontSize = 9.5.sp, color = lsColors.textPrimary, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val updated = steps.toMutableList().apply {
                                        add(
                                            BotStep(
                                                stepType = BotStep.TYPE_TAP,
                                                xRatio = 0.92f,
                                                yRatio = 0.07f,
                                                delayMs = 250L,
                                                durationMs = 60L,
                                                contentDescription = LightspeedBotScreenScanner.TOKEN_ANDROID_MORE_MENU,
                                                packageName = boundScope.first,
                                                useElementTarget = true,
                                                windowId = boundScope.second
                                            )
                                        )
                                    }
                                    persistSteps(updated)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(1f).height(30.dp)
                            ) {
                                Text("⌖ Android Menu", fontSize = 9.5.sp, color = lsColors.textPrimary, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val updated = steps.toMutableList().apply {
                                        add(
                                            BotStep(
                                                stepType = BotStep.TYPE_TAP,
                                                xRatio = 0.80f,
                                                yRatio = 0.07f,
                                                delayMs = 250L,
                                                durationMs = 60L,
                                                contentDescription = LightspeedBotScreenScanner.TOKEN_SEARCH_BUTTON,
                                                packageName = boundScope.first,
                                                useElementTarget = true,
                                                windowId = boundScope.second
                                            )
                                        )
                                    }
                                    persistSteps(updated)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.weight(0.85f).height(30.dp)
                            ) {
                                Text("⌖ Search", fontSize = 9.5.sp, color = lsColors.textPrimary, maxLines = 1)
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
                                                            LightspeedBotEngine.startContinuousLoop(this@BotMacroEditorDialog, botId)
                                                            isLooping = true
                                                            finish()
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
                                    LightspeedBotEngine.playBot(this@BotMacroEditorDialog, botId, stepsOverride = steps)
                                    finish()
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
                                    LightspeedBotEngine.startRecording(this@BotMacroEditorDialog, botId)
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(androidx.compose.ui.graphics.Color(0xFFFF3B30))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Record", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            }

                            DeckButton(
                                onClick = {
                                    persistSteps(emptyList())
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
                                title = {
                                    Text(
                                        "INFINITE LOOP AUTOMATION",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = lsColors.textPrimary
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "You are arming an infinite macro loop. This macro will repeat continuously until interrupted.\n\nPress Volume Up, Volume Down, or Power at any time for instant emergency cancel.",
                                            fontSize = 11.sp,
                                            color = lsColors.textSecondary
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { dontRemindLoopWarn = !dontRemindLoopWarn }
                                        ) {
                                            Checkbox(
                                                checked = dontRemindLoopWarn,
                                                onCheckedChange = { dontRemindLoopWarn = it },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = lsColors.accent,
                                                    uncheckedColor = lsColors.textSecondary
                                                )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Don't remind me again",
                                                fontSize = 10.5.sp,
                                                color = lsColors.textPrimary
                                            )
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            if (dontRemindLoopWarn) {
                                                prefs.edit().putBoolean(LightspeedBotContract.KEY_BOT_LOOP_WARN_DISMISSED, true).apply()
                                            }
                                            showLoopWarningDialog = false
                                            LightspeedBotEngine.startContinuousLoop(this@BotMacroEditorDialog, botId, stepsOverride = steps)
                                            isLooping = true
                                            finish()
                                        }
                                    ) {
                                        Text("Start Loop", fontWeight = FontWeight.Bold, color = lsColors.accentWarn)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLoopWarningDialog = false }) {
                                        Text("Cancel", color = lsColors.textSecondary)
                                    }
                                }
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
                                                    else -> "◈ Coords: (${(step.xRatio * 100).toInt()}%, ${(step.yRatio * 100).toInt()}%)"
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

    override fun onDestroy() {
        super.onDestroy()
        LightspeedBotTrackpadOverlay.setSuspendedForDialog(false)
    }

    companion object {
        const val EXTRA_BOT_ID = "extra_bot_id"
        const val EXTRA_TARGET_PACKAGE = "extra_target_package"
        const val EXTRA_TARGET_WINDOW_ID = "extra_target_window_id"
    }
}
