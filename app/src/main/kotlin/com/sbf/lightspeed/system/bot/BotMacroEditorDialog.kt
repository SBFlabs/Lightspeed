package com.sbf.lightspeed.system.bot

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AllInclusive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
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
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * 12-Theme Dialog for Inspecting, Recording, Looping & Editing Bot Macro Steps and Screen Conditions.
 */
class BotMacroEditorDialog : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val botId = intent?.getIntExtra(EXTRA_BOT_ID, 1)?.coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS) ?: 1

        setContent {
            val lsColors = LightspeedStyle.colors
            val activeScope = remember { LightspeedBotEngine.resolveActiveScope(this@BotMacroEditorDialog) }
            val scopeLabel = remember { LightspeedBotEngine.getActiveScopeDisplayLabel(this@BotMacroEditorDialog) }
            var steps by remember { mutableStateOf(LightspeedBotEngine.getMacroSteps(this, botId)) }
            var isLooping by remember { mutableStateOf(LightspeedBotEngine.isLooping) }

            DeckDialog(
                title = {
                    Column {
                        Text(
                            text = "BUTTON #$botId MACRO & SCREEN CONDITIONS",
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

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            DeckButton(
                                text = "Play",
                                icon = Icons.Default.PlayArrow,
                                onClick = {
                                    LightspeedBotEngine.playBot(this@BotMacroEditorDialog, botId)
                                    finish()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            DeckButton(
                                text = "Record",
                                icon = Icons.Default.FiberManualRecord,
                                onClick = {
                                    LightspeedBotEngine.startRecording(this@BotMacroEditorDialog, botId)
                                    finish()
                                },
                                modifier = Modifier.weight(1f)
                            )

                            DeckButton(
                                text = if (isLooping) "Stop Loop" else "Loop",
                                icon = Icons.Default.AllInclusive,
                                isActive = isLooping,
                                onClick = {
                                    if (!LightspeedInfinityManager.isUnlocked(this@BotMacroEditorDialog)) {
                                        startActivity(Intent(this@BotMacroEditorDialog, CockpitDialogActivity::class.java).apply {
                                            action = CockpitDialogActivity.ACTION_SHOW_INFINITY
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                        })
                                        finish()
                                    } else {
                                        if (LightspeedBotEngine.isLooping) {
                                            LightspeedBotEngine.stopContinuousLoop()
                                            isLooping = false
                                        } else {
                                            LightspeedBotEngine.startContinuousLoop(this@BotMacroEditorDialog, botId)
                                            isLooping = true
                                            finish()
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (steps.isEmpty()) {
                            Text(
                                text = "No steps recorded yet. Long-press Button #$botId on the Cybernetic Trackpad (or tap Record above) to record taps, swipes, and screen elements.",
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

    companion object {
        const val EXTRA_BOT_ID = "extra_bot_id"
    }
}
