package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.BuildConfig
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.NativeSettingsLauncher
import com.sbf.lightspeed.system.NightlyFeatures
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedEyeRestMonitor
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlin.math.roundToInt

/**
 * Navigation / Central Command accordion section for Astrogation Airlock & 20-20-20 Eye Rest
 * (relocated out of Vista Deck into Navigation / Central Command).
 */
@Composable
fun HudAstrogationAirlockSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val lsColors = LightspeedStyle.colors
    var airlockEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false))
    }
    var airlockAbortMode by remember {
        mutableStateOf(
            prefs.getString(
                LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ABORT_MODE,
                LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP
            ) ?: LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP
        )
    }
    var airlockApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet()) ?: emptySet())
    }
    var airlockColorMode by remember {
        mutableStateOf(
            prefs.getString(
                LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_COLOR_MODE,
                LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12
            ) ?: LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12
        )
    }
    var eyeRestEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false))
    }
    var eyeRestInterval by remember {
        mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20).toFloat())
    }
    var showAirlockPicker by remember { mutableStateOf(false) }

    val (airlockTitle, airlockSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.ASTROGATION_AIRLOCK, languageMode)

    CompactAccordionSection(
        title = airlockTitle,
        subtitle = airlockSub,
        icon = {
            Icon(
                imageVector = Icons.Default.HourglassBottom,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(lsString(R.string.airlock_delay_title), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
                    Text(lsString(R.string.airlock_delay_sub_fmt, airlockApps.size), fontSize = 9.5.sp, color = lsColors.textSecondary)
                }
                DeckSwitch(
                    checked = airlockEnabled,
                    onCheckedChange = {
                        airlockEnabled = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, it).apply()
                    },
                    modifier = Modifier.height(24.dp)
                )
            }

            if (airlockEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DeckButton(
                            text = lsString(R.string.airlock_gated_apps_count, airlockApps.size),
                            icon = Icons.Default.LockClock,
                            isActive = airlockApps.isNotEmpty(),
                            onClick = { showAirlockPicker = true },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            maxLines = 1
                        )
                        DeckButton(
                            text = lsString(R.string.act_native_wellbeing),
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            isActive = false,
                            onClick = { NativeSettingsLauncher.openDigitalWellbeing(context) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
                            maxLines = 1
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = lsString(R.string.well_abort_act),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = lsColors.textSecondary
                        )
                        DeckSegmentedRow(
                            options = listOf(
                                LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP to lsString(R.string.airlock_close_app),
                                LightspeedPreferences.AIRLOCK_ABORT_MODE_GO_HOME to lsString(R.string.well_go_home)
                            ),
                            selectedKey = if (airlockAbortMode == LightspeedPreferences.AIRLOCK_ABORT_MODE_GO_HOME) {
                                LightspeedPreferences.AIRLOCK_ABORT_MODE_GO_HOME
                            } else {
                                LightspeedPreferences.AIRLOCK_ABORT_MODE_CLOSE_APP
                            },
                            onSelect = { next ->
                                airlockAbortMode = next
                                prefs.edit().putString(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ABORT_MODE, next).apply()
                            }
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val openFlightDeck = LocalOpenFlightControlDeck.current
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lsString(R.string.airlock_color_engine),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lsColors.textSecondary
                            )
                            Text(
                                text = "◈ ${lsString(R.string.fc_browse_13_themes)}",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                    .border(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                    .clickable { openFlightControlDeck(context, openFlightDeck) }
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        DeckSegmentedRow(
                            options = listOf(
                                LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12 to lsString(R.string.ce_theme_12),
                                LightspeedPreferences.COCKPIT_COLOR_MODE_M3_DYNAMIC to lsString(R.string.ce_material_3),
                                LightspeedPreferences.COCKPIT_COLOR_MODE_HYBRID_FUSION to lsString(R.string.ce_hybrid)
                            ),
                            selectedKey = airlockColorMode,
                            onSelect = { mode ->
                                airlockColorMode = mode
                                prefs.edit().putString(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_COLOR_MODE, mode).apply()
                            }
                        )
                    }
                }
            }

            if (NightlyFeatures.isEyeRestTimerEnabled) {
                HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.07f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = lsString(R.string.eyerest_title),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lsColors.textPrimary
                            )
                            if (NightlyFeatures.IS_EYE_REST_TIMER_TAGGED_NIGHTLY && BuildConfig.DEBUG) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(lsColors.accent.copy(alpha = 0.14f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = NightlyFeatures.BADGE_SUFFIX.trim(),
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lsColors.accent
                                    )
                                }
                            }
                        }
                        Text(
                            if (eyeRestEnabled) lsString(R.string.eyerest_sub_on_fmt, eyeRestInterval.toInt())
                            else lsString(R.string.eyerest_sub_off),
                            fontSize = 9.5.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    DeckSwitch(
                        checked = eyeRestEnabled,
                        onCheckedChange = {
                            eyeRestEnabled = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, it).apply()
                            LightspeedEyeCareEngine.onScreenStateChanged(context, it)
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }

                if (eyeRestEnabled) {
                    DeckSlider(
                        value = eyeRestInterval,
                        valueRange = 10f..60f,
                        onValueChange = {
                            val intVal = it.roundToInt()
                            eyeRestInterval = intVal.toFloat()
                            prefs.edit().putInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, intVal).apply()
                            LightspeedEyeRestMonitor.onIntervalChanged(context, intVal)
                        },
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }

    if (showAirlockPicker) {
        ConsciousAirlockAppPickerDialog(
            context = context,
            prefs = prefs,
            onDismissRequest = { showAirlockPicker = false },
            onSelectionChanged = { updated -> airlockApps = updated }
        )
    }
}
