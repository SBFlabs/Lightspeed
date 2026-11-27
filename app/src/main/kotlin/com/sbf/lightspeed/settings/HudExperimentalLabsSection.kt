package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Calendar

@Composable
fun HudExperimentalLabsSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isNotchCalibExpanded: Boolean,
    onToggleNotchCalib: () -> Unit,
    isOemNoticeDemoted: Boolean,
    onOemNoticeDemotedChange: (Boolean) -> Unit,
    oemFeatureName: String?,
    isMarqueeSubSectionExpanded: Boolean,
    onToggleMarqueeSubSection: () -> Unit,
    isSubPowerExpanded: Boolean,
    onToggleSubPower: () -> Unit,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockedChange: (Boolean) -> Unit,
    singlePressTapCount: Int,
    onSinglePressTapCountChange: (Int) -> Unit,
    installedTacticalTools: List<TacticalToolItem>,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    isRefuelingExpanded: Boolean = false,
    onToggleRefueling: () -> Unit = {},
    onShowAmoledWarning: () -> Unit = {},
    onRefreshNeeded: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val lsColors = LightspeedStyle.colors
    val cautionAmber = lsColors.accentWarn

    HazardAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.EXPERIMENTAL_LABS, languageMode),
        subtitle = lsString(R.string.secd_features_in_this_deck_are_unst),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Maintenance Bay Intro Badge
            DeckCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = lsString(R.string.lab_warn_banner),
                        fontSize = 11.sp,
                        color = cautionAmber.copy(alpha = 0.95f),
                        lineHeight = 14.5.sp
                    )
                }
            }

            // 0. Cybernetic Trackpad (3-Button Macro Clicker & Cursor Dome) [Experimental]
            var isTrackpadLabsExpanded by rememberSaveable { mutableStateOf(false) }
            val expandLabsTrackpadTick by CentralCommandTabNavigator.expandLabsTrackpadTick.collectAsState()
            var isTrackpadGeoExpanded by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(expandLabsTrackpadTick) {
                if (expandLabsTrackpadTick > 0) {
                    isTrackpadLabsExpanded = true
                    isTrackpadGeoExpanded = true
                }
            }
            var isTrackpadLabsEnabled by remember {
                mutableStateOf(com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs))
            }
            var trackpadFlankLeft by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
                )
            }
            CollapsibleSubSection(
                title = lsString(R.string.sec_cybernetic_trackpad),
                subtitle = lsString(R.string.secd_120hz_cursor_dome_3_per_window),
                icon = {
                    Icon(
                        imageVector = Icons.Default.ControlCamera,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isTrackpadLabsExpanded,
                onToggle = { isTrackpadLabsExpanded = !isTrackpadLabsExpanded }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = lsString(R.string.lab_trackpad_title),
                            fontSize = 12.sp,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (isTrackpadLabsEnabled) lsString(R.string.lab_trackpad_vis) else lsString(R.string.lab_trackpad_hid),
                            fontSize = 10.sp,
                            color = lsColors.textSecondary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isTrackpadLabsEnabled) {
                            IconButton(
                                onClick = {
                                    val targetTab = if (trackpadFlankLeft) 0 else 2
                                    val flankKey = if (trackpadFlankLeft) "left" else "right"
                                    prefs.edit().putBoolean("pref_accordion_trackpad_$flankKey", true).apply()
                                    CentralCommandTabNavigator.triggerJump(targetTab)
                                    onRefreshNeeded()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = if (trackpadFlankLeft) "Jump to Left Deflector Trackpad" else "Jump to Right Deflector Trackpad",
                                    tint = lsColors.accentInfo,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Switch(
                            checked = isTrackpadLabsEnabled,
                            onCheckedChange = { enabled ->
                                isTrackpadLabsEnabled = enabled
                                val ed = prefs.edit().putBoolean(
                                    com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LABS_ENABLED,
                                    enabled
                                )
                                if (!enabled) {
                                    ed.putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
                                    ed.putBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED, false)
                                } else {
                                    val targetKey = if (trackpadFlankLeft) com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED else com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED
                                    ed.putBoolean(targetKey, true)
                                }
                                ed.apply()
                                com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(context)
                                onRefreshNeeded()
                            },
                            colors = deckSwitchColors()
                        )
                    }
                }

                if (isTrackpadLabsEnabled) {
                    HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

                    TrackpadRotatingTipBanner()

                    // Flank selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrackpadPillButton(
                            text = lsString(R.string.lab_right_def),
                            isSelected = !trackpadFlankLeft,
                            onClick = { trackpadFlankLeft = false },
                            modifier = Modifier.weight(1f)
                        )
                        TrackpadPillButton(
                            text = lsString(R.string.lab_left_def),
                            isSelected = trackpadFlankLeft,
                            onClick = { trackpadFlankLeft = true },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Geometry & Calibration Sub-Accordion
                    TrackpadGeometrySubSection(
                        isLeft = trackpadFlankLeft,
                        context = context,
                        prefs = prefs,
                        isParentExpanded = isTrackpadLabsExpanded,
                        isSubExpanded = isTrackpadGeoExpanded,
                        onToggle = { isTrackpadGeoExpanded = !isTrackpadGeoExpanded }
                    )

                    // Physics & Timing Sub-Accordion
                    TrackpadPhysicsSubSection(
                        isLeft = trackpadFlankLeft,
                        prefs = prefs
                    )
                }
            }

            // 1. Orbital Capsule (Camera Cutout HUD) [Experimental]
            OrbitalCapsuleLabSubSection(
                context = context,
                prefs = prefs,
                cautionAmber = cautionAmber,
                currentLanguageMode = languageMode,
                isNotchCalibExpanded = isNotchCalibExpanded,
                onToggleNotchCalib = onToggleNotchCalib,
                isOemNoticeDemoted = isOemNoticeDemoted,
                onOemNoticeDemotedChange = onOemNoticeDemotedChange,
                oemFeatureName = oemFeatureName,
                isMarqueeSubSectionExpanded = isMarqueeSubSectionExpanded,
                onToggleMarqueeSubSection = onToggleMarqueeSubSection,
                onRefreshNeeded = onRefreshNeeded
            )

            // 3. Power Button Remapping / Ignition Override
            PowerButtonLabSubSection(
                context = context,
                prefs = prefs,
                cautionAmber = cautionAmber,
                currentLanguageMode = languageMode,
                isSubPowerExpanded = isSubPowerExpanded,
                onToggleSubPower = onToggleSubPower,
                isSinglePressUnlocked = isSinglePressUnlocked,
                onSinglePressUnlockedChange = onSinglePressUnlockedChange,
                singlePressTapCount = singlePressTapCount,
                onSinglePressTapCountChange = onSinglePressTapCountChange,
                installedTacticalTools = installedTacticalTools,
                dynamicActionTokens = dynamicActionTokens,
                tokenLabelCache = tokenLabelCache,
                onRefreshNeeded = onRefreshNeeded
            )

            // 4. Core Cooling Schedule
            var isCoreCoolingExpanded by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_CORE_COOLING_LABS, false)) }
            CollapsibleSubSection(
                title = lsString(R.string.sec_core_cooling_schedule),
                subtitle = lsString(R.string.secd_configurable_weekly_maintenanc),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.AcUnit,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isCoreCoolingExpanded,
                onToggle = {
                    isCoreCoolingExpanded = !isCoreCoolingExpanded
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_CORE_COOLING_LABS, isCoreCoolingExpanded).apply()
                }
            ) {
                var coreCoolingEnabled by rememberSaveable { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, false)) }
                var targetDay by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, Calendar.SUNDAY)) }
                var targetHour by rememberSaveable { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, 3)) }

                PrefToggleRow(
                    title = lsString(R.string.lab_cool_rem_title),
                    subtitle = lsString(R.string.lab_cool_rem_sub),
                    isChecked = coreCoolingEnabled,
                    onCheckedChange = { checked ->
                        coreCoolingEnabled = checked
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_CORE_COOLING_ENABLED, checked).apply()
                        onRefreshNeeded()
                    }
                )

                if (coreCoolingEnabled) {
                    CoreCoolingRotarySchedulePicker(
                        selectedDay = targetDay,
                        selectedHour = targetHour,
                        onScheduleChanged = { day, hour ->
                            targetDay = day
                            targetHour = hour
                            prefs.edit()
                                .putInt(LightspeedPreferences.KEY_CORE_COOLING_DAY_OF_WEEK, day)
                                .putInt(LightspeedPreferences.KEY_CORE_COOLING_HOUR, hour)
                                .apply()
                            onRefreshNeeded()
                        }
                    )
                }

                DeckCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(
                        text = lsString(R.string.lab_cool_policy),
                        fontSize = 10.5.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
