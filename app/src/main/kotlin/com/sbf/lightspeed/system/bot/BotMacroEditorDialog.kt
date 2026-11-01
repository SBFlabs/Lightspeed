package com.sbf.lightspeed.system.bot

import android.content.Intent
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
import com.sbf.lightspeed.CockpitDialogActivity
import com.sbf.lightspeed.settings.DeckButton
import com.sbf.lightspeed.settings.DeckCard
import com.sbf.lightspeed.settings.DeckDialog
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.TrackpadPillButton
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedInfinityManager
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

        setContent {
            val lsColors = LightspeedStyle.colors
            val prefs = remember { defaultPrefs() }
            val buttonNumber = ((botId - 1) % 3) + 1
            val isInfinityUnlocked = remember { LightspeedInfinityManager.isUnlocked(this@BotMacroEditorDialog) }
            val repeatPrefKey = remember { "${LightspeedBotContract.KEY_BOT_MACRO_REPEAT_PREFIX}$botId" }
            val useRecordedDelayPrefKey = remember { "${LightspeedBotContract.KEY_BOT_USE_RECORDED_DELAY_PREFIX}$botId" }
            val stepDelayMsPrefKey = remember { "${LightspeedBotContract.KEY_BOT_STEP_DELAY_MS_PREFIX}$botId" }
            var repeatCount by remember { mutableIntStateOf(prefs.getInt(repeatPrefKey, 1)) }
            var useRecordedDelay by remember { mutableStateOf(prefs.getBoolean(useRecordedDelayPrefKey, false)) }
            var customStepDelayMs by remember { mutableFloatStateOf(prefs.getInt(stepDelayMsPrefKey, 60).toFloat()) }
            var showLoopWarningDialog by remember { mutableStateOf(false) }
            var dontRemindLoopWarn by remember { mutableStateOf(false) }
            val activeScope = remember { LightspeedBotEngine.resolveActiveScope(this@BotMacroEditorDialog) }
            val scopeLabel = remember { LightspeedBotEngine.getActiveScopeDisplayLabel(this@BotMacroEditorDialog) }
            var steps by remember { mutableStateOf(LightspeedBotEngine.getMacroSteps(this, botId)) }
            var isLooping by remember { mutableStateOf(LightspeedBotEngine.isLooping) }

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
                            text = "◈ SCOPE: $scopeLabel",
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
                        Text(
                            text = "RECORDED STEPS (${scopeLabel.uppercase()}): ${steps.size}",
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
                                                packageName = activeScope.first
                                            )
                                        )
                                    }
                                    steps = updated
                                    LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, updated, activeScope)
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
                                                packageName = activeScope.first
                                            )
                                        )
                                    }
                                    steps = updated
                                    LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, updated, activeScope)
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
                                                packageName = activeScope.first
                                            )
                                        )
                                    }
                                    steps = updated
                                    LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, updated, activeScope)
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
                                    val isLocked = (count != 1 && !isInfinityUnlocked)
                                    val isSelected = if (count == -1) isLooping else (repeatCount == count && !isLooping)
                                    TrackpadPillButton(
                                        text = label,
                                        isSelected = isSelected,
                                        isLocked = isLocked,
                                        onClick = {
                                            if (isLocked) {
                                                LightspeedHapticEngine.triggerWarning(this@BotMacroEditorDialog)
                                                startActivity(Intent(this@BotMacroEditorDialog, CockpitDialogActivity::class.java).apply {
                                                    action = CockpitDialogActivity.ACTION_SHOW_INFINITY
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                                })
                                            } else {
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
                                    LightspeedBotEngine.playBot(this@BotMacroEditorDialog, botId)
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
                                    steps = mutableListOf()
                                    LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, emptyList(), activeScope)
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
                                            LightspeedBotEngine.startContinuousLoop(this@BotMacroEditorDialog, botId)
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
                                                    step.contentDescription == LightspeedBotScreenScanner.TOKEN_BURGER_MENU -> "Smart Target: ⌖ Burger / Drawer Menu"
                                                    step.contentDescription == LightspeedBotScreenScanner.TOKEN_ANDROID_MORE_MENU -> "Smart Target: ⌖ Android 3-Dot Menu"
                                                    step.contentDescription == LightspeedBotScreenScanner.TOKEN_SEARCH_BUTTON -> "Smart Target: ⌖ Search Button"
                                                    !step.viewIdResourceName.isNullOrBlank() -> "ID: ${step.viewIdResourceName.substringAfterLast('/')}"
                                                    !step.text.isNullOrBlank() -> "Text: \"${step.text}\""
                                                    !step.contentDescription.isNullOrBlank() -> "Desc: \"${step.contentDescription}\""
                                                    else -> "Coords: (${(step.xRatio * 100).toInt()}%, ${(step.yRatio * 100).toInt()}%)"
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
                                                    steps = updated
                                                    LightspeedBotEngine.saveMacroSteps(this@BotMacroEditorDialog, botId, updated, activeScope)
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
    }
}
