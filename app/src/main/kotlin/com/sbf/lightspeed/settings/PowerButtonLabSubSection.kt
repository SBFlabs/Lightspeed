package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PowerButtonLabSubSection(
    context: Context,
    prefs: SharedPreferences,
    cautionAmber: Color,
    currentLanguageMode: LightspeedLanguageEngine.LanguageMode,
    isSubPowerExpanded: Boolean,
    onToggleSubPower: () -> Unit,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockedChange: (Boolean) -> Unit,
    singlePressTapCount: Int,
    onSinglePressTapCountChange: (Int) -> Unit,
    installedTacticalTools: List<TacticalToolItem>,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val (powerTitle, powerSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.IGNITION_OVERRIDE, currentLanguageMode)

    CollapsibleSubSection(
        title = powerTitle,
        subtitle = if (powerSub != null) "$powerSub • Single, Double, Hold & Press-then-Hold" else lsString(R.string.pwr_sub),
        icon = {
            Icon(
                imageVector = Icons.Outlined.PowerSettingsNew,
                contentDescription = null,
                tint = cautionAmber,
                modifier = Modifier.size(16.dp)
            )
        },
        isExpanded = isSubPowerExpanded,
        onToggle = onToggleSubPower
    ) {
        // Emergency Reset Hardware Notice Banner
        DeckCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = lsString(R.string.pwr_emerg),
                    fontSize = 11.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.85f),
                    lineHeight = 14.sp
                )
            }
        }

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_POWER_GESTURES_ENABLED,
            defaultVal = false,
            title = lsString(R.string.pwr_enable_title),
            subtitle = lsString(R.string.pwr_enable_sub),
            onChanged = { onRefreshNeeded(); PowerLongPressTakeover.sync(context) }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_POWER_HOLD_TAKEOVER,
            defaultVal = false,
            title = lsString(R.string.pwr_block_asst),
            subtitle = lsString(R.string.pwr_block_asst_sub),
            onChanged = { PowerLongPressTakeover.sync(context); onRefreshNeeded() }
        )

        PrefToggleRow(
            prefs = prefs,
            prefKey = LightspeedPreferences.KEY_POWER_GRAB_HELPER,
            defaultVal = false,
            title = lsString(R.string.pwr_grab_title),
            subtitle = lsString(R.string.pwr_grab_sub),
            onChanged = {
                LightspeedPowerKeyEngine.stopShizukuPowerMonitor()
                if (LightspeedPowerKeyEngine.isPowerEnabled(context)) {
                    LightspeedPowerKeyEngine.startShizukuPowerMonitor(context)
                }
                onRefreshNeeded()
            }
        )

        // Warning Card for Power Button Grab
        val (grabWarnTitle, grabWarnSub) = LightspeedLanguageEngine.resolvePair(
            LightspeedVocabulary.Key.POWER_GRAB_WARNING, currentLanguageMode
        )
        DeckCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = cautionAmber,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = grabWarnTitle,
                        fontSize = 11.sp,
                        color = cautionAmber,
                        lineHeight = 14.sp
                    )
                    if (grabWarnSub != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = grabWarnSub,
                            fontSize = 10.sp,
                            color = lsColors.textSecondary.copy(alpha = 0.85f),
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // Double-Press Window Slider & Text Field
        var doublePressWindow by remember(prefs) {
            mutableStateOf(prefs.getLong(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW, LightspeedPreferences.DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS))
        }

        val doublePressTitle = when (currentLanguageMode) {
            LightspeedLanguageEngine.LanguageMode.VESSEL_LORE -> "Double-Press Inter-Press Window"
            LightspeedLanguageEngine.LanguageMode.CO_PILOT -> "Double-Press Window ⇄ Inter-Press Timeout"
            LightspeedLanguageEngine.LanguageMode.CLEAR_COMMS -> "Double-Press Inter-Press Timeout"
        }

        val minWindowVal = 200L
        val maxWindowVal = 600L
        val stepWindowMs = 25L
        val stepsWindowCount = (((maxWindowVal - minWindowVal) / stepWindowMs) - 1).toInt()

        fun applyWindowValue(valueMs: Long) {
            val clamped = valueMs.coerceIn(minWindowVal, maxWindowVal)
            val rounded = minWindowVal + kotlin.math.round((clamped - minWindowVal).toDouble() / stepWindowMs.toDouble()).toLong() * stepWindowMs
            val finalVal = rounded.coerceIn(minWindowVal, maxWindowVal)
            if (doublePressWindow != finalVal) {
                doublePressWindow = finalVal
                prefs.edit().putLong(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW, finalVal).apply()
                try {
                    LightspeedAccessibilityService.instance?.reloadPreferences()
                } catch (e: Exception) {
                    logSwallowed("PowerButtonLabSubSection", "DoublePressWindowSlider", e)
                }
                onRefreshNeeded()
            }
        }

        var isFlyoutOpen by remember { mutableStateOf(false) }
        var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.keySliderTapToJump(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW), false)) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = doublePressTitle,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = lsColors.textPrimary,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .combinedClickable(
                            onClick = {
                                LightspeedHapticEngine.tick(context)
                                isFlyoutOpen = true
                            },
                            onLongClick = {
                                LightspeedHapticEngine.heavyClick(context)
                                applyWindowValue(LightspeedPreferences.DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS)
                            }
                        )
                ) {
                    Text(
                        text = "${doublePressWindow} ms",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            DragOnlySlider(
                value = doublePressWindow.toFloat(),
                onValueChange = { floatVal ->
                    val rounded = (minWindowVal + kotlin.math.round((floatVal - minWindowVal.toFloat()) / stepWindowMs.toFloat()) * stepWindowMs).toLong().coerceIn(minWindowVal, maxWindowVal)
                    applyWindowValue(rounded)
                },
                valueRange = minWindowVal.toFloat()..maxWindowVal.toFloat(),
                steps = stepsWindowCount,
                tapToJump = isTapToJumpEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (isFlyoutOpen) {
            SliderCalibrationFlyoutDialog(
                title = doublePressTitle,
                sliderKey = LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW,
                currentValueStr = doublePressWindow.toString(),
                defaultValueStr = LightspeedPreferences.DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS.toString(),
                onValueTyped = { typedStr ->
                    val parsed = typedStr.filter { it.isDigit() }.toLongOrNull()
                    if (parsed != null) applyWindowValue(parsed)
                },
                onResetToDefault = {
                    applyWindowValue(LightspeedPreferences.DEFAULT_POWER_DOUBLE_PRESS_WINDOW_MS)
                },
                onSelectProfile = { profileStr ->
                    val parsed = profileStr.filter { it.isDigit() }.toLongOrNull()
                    if (parsed != null) applyWindowValue(parsed)
                },
                onDismiss = { isFlyoutOpen = false },
                prefs = prefs,
                context = context,
                isTapToJumpEnabled = isTapToJumpEnabled,
                onTapToJumpChanged = { enabled ->
                    isTapToJumpEnabled = enabled
                    prefs.edit().putBoolean(LightspeedPreferences.keySliderTapToJump(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS_WINDOW), enabled).apply()
                }
            )
        }

        val powerGestures = listOf(
            Triple(LightspeedPreferences.KEY_POWER_SINGLE_PRESS, "Single Press", PowerTriggerSlot.POWER_SINGLE_PRESS),
            Triple(LightspeedPreferences.KEY_POWER_DOUBLE_PRESS, "Double Press (<${doublePressWindow}ms)", PowerTriggerSlot.POWER_DOUBLE_PRESS),
            Triple(LightspeedPreferences.KEY_POWER_HOLD, "Hold (~400ms)", PowerTriggerSlot.POWER_HOLD),
            Triple(LightspeedPreferences.KEY_POWER_PRESS_THEN_HOLD, "Press-then-Hold", PowerTriggerSlot.POWER_PRESS_THEN_HOLD)
        )

        powerGestures.forEach { (prefKey, title, slot) ->
            PowerGestureMappingRow(
                context = context,
                prefs = prefs,
                prefKey = prefKey,
                title = title,
                slot = slot,
                isSinglePress = slot == PowerTriggerSlot.POWER_SINGLE_PRESS,
                isSinglePressUnlocked = isSinglePressUnlocked,
                onSinglePressUnlockStep = {
                    val newCount = singlePressTapCount + 1
                    onSinglePressTapCountChange(newCount)
                    if (newCount in 4..6) {
                        Toast.makeText(context, "You are ${7 - newCount} steps away from unlocking Single Press remap.", Toast.LENGTH_SHORT).show()
                    } else if (newCount >= 7) {
                        onSinglePressUnlockedChange(true)
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_POWER_SINGLE_PRESS_UNLOCKED, true).apply()
                        Toast.makeText(context, lsString(R.string.pwr_unlocked), Toast.LENGTH_SHORT).show()
                        LightspeedHapticEngine.heavyClick(context)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (e: Exception) { logSwallowed("PowerButtonLabSubSection", "unlock", e) }
                        onRefreshNeeded()
                    }
                },
                installedTools = installedTacticalTools,
                options = dynamicActionTokens,
                labelCache = tokenLabelCache,
                onRefreshNeeded = onRefreshNeeded
            )
        }
    }
}
