package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness4
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
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val cautionAmber = lsColors.accentWarn
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()

    HazardAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.EXPERIMENTAL_LABS, currentLanguageMode),
        subtitle = "Features in this deck are unstable and/or not well tested yet. Use at your own discretion.",
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
                        text = "Experimental Labs: Active testing ground for cutting-edge features, hardware-specific gesture remaps, and deep system automations. These experimental tools are in active testing and may be unstable or behave differently across various OEM Android firmwares.",
                        fontSize = 11.sp,
                        color = cautionAmber.copy(alpha = 0.95f),
                        lineHeight = 14.5.sp
                    )
                }
            }

            // 0. Cybernetic Trackpad (3-Button Macro Clicker & Cursor Dome) [Experimental]
            var isTrackpadLabsExpanded by rememberSaveable { mutableStateOf(false) }
            var isTrackpadLabsEnabled by remember {
                mutableStateOf(com.sbf.lightspeed.system.bot.LightspeedBotContract.isLabsEnabled(prefs))
            }
            var trackpadFlankLeft by rememberSaveable {
                mutableStateOf(
                    prefs.getBoolean(com.sbf.lightspeed.system.bot.LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED, false)
                )
            }
            CollapsibleSubSection(
                title = "Cybernetic Trackpad",
                subtitle = "120Hz cursor dome & 3 per-window smart macro buttons",
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
                            text = "Enable Cybernetic Trackpad",
                            fontSize = 12.sp,
                            color = lsColors.textPrimary
                        )
                        Text(
                            text = if (isTrackpadLabsEnabled) "Visible in Deflector tabs. Configure below or jump to Deflector." else "Hidden from Deflector tabs and overlays.",
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
                                    val flankKey = if (trackpadFlankLeft) "left" else "right"
                                    ed.putBoolean("pref_accordion_trackpad_$flankKey", true)
                                }
                                ed.apply()
                                com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay.reload(context)
                                onRefreshNeeded()
                            }
                        )
                    }
                }

                if (isTrackpadLabsEnabled) {
                    HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

                    // Flank selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TrackpadPillButton(
                            text = "RIGHT DEFLECTOR",
                            isSelected = !trackpadFlankLeft,
                            onClick = { trackpadFlankLeft = false },
                            modifier = Modifier.weight(1f)
                        )
                        TrackpadPillButton(
                            text = "LEFT DEFLECTOR",
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
                        isParentExpanded = isTrackpadLabsExpanded
                    )

                    // Physics & Timing Sub-Accordion
                    TrackpadPhysicsSubSection(
                        isLeft = trackpadFlankLeft,
                        prefs = prefs
                    )
                }
            }

            // 1. Sonic Deck / Universal Audio Control (Experimental)
            var isSonicDeckExpanded by rememberSaveable { mutableStateOf(false) }
            var showSonicPowerWarning by remember { mutableStateOf(false) }
            val (sonicDeckTitle, sonicDeckSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.SONIC_DECK, currentLanguageMode)
            val sonicSubtitle = if (sonicDeckSub != null) "$sonicDeckSub • Per-app volume mixer" else "Per-app volume mixer & sovereign audio controls"
            CollapsibleSubSection(
                title = sonicDeckTitle,
                subtitle = sonicSubtitle,
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSonicDeckExpanded,
                onToggle = { isSonicDeckExpanded = !isSonicDeckExpanded }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_SONIC_DECK_ENABLED,
                    defaultVal = false,
                    title = "Enable $sonicDeckTitle",
                    subtitle = "Pull outward (horizontally from deflectors or vertically from top progress rail) while scrubbing volume to open per-app mixer.",
                    onChanged = { enabled ->
                        SonicDeckManager.onMasterToggle(context, enabled)
                        if (enabled && !prefs.getBoolean(LightspeedPreferences.KEY_SONIC_POWER_WARNING_DISMISSED, false)) {
                            showSonicPowerWarning = true
                        }
                        onRefreshNeeded()
                    }
                )
                Text(
                    text = "⚠ Uses extra RAM and battery: runs a separate background audio service while enabled.",
                    fontSize = 11.sp,
                    color = androidx.compose.ui.graphics.Color(0xFFFF5252),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
                if (showSonicPowerWarning) {
                    var dontRemind by remember { mutableStateOf(false) }
                    val dismissWarning = {
                        if (dontRemind) {
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_SONIC_POWER_WARNING_DISMISSED, true).apply()
                        }
                        showSonicPowerWarning = false
                    }
                    DeckDialog(
                        onDismissRequest = dismissWarning,
                        title = {
                            Text(
                                text = "⚠ Power & memory usage",
                                fontSize = 15.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFFFF5252)
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Sonic Deck keeps a separate privileged audio service running (about 100–150 MB of RAM) and keeps monitoring audio players. This costs extra memory and battery. Turn Sonic Deck off to stop the service completely.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { dontRemind = !dontRemind }
                                ) {
                                    Checkbox(checked = dontRemind, onCheckedChange = { dontRemind = it })
                                    Text("Don't remind me again", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = dismissWarning) {
                                Text("GOT IT", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
                if (prefs.getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false)) {
                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_SONIC_DECK_LEFT_ENABLED,
                        defaultVal = true,
                        title = "Left Deflector Launch",
                        subtitle = "Pull inward from left deflector during volume scrub to launch Sonic Deck.",
                        onChanged = { onRefreshNeeded() }
                    )
                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_SONIC_DECK_RIGHT_ENABLED,
                        defaultVal = true,
                        title = "Right Deflector Launch",
                        subtitle = "Pull inward from right deflector during volume scrub to launch Sonic Deck.",
                        onChanged = { onRefreshNeeded() }
                    )
                }
            }

            // 1b. Vista Deck / Display & Visual Optics Control (Experimental)
            var isVistaDeckExpanded by rememberSaveable { mutableStateOf(false) }
            val (vistaDeckTitle, vistaDeckSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.VISTA_DECK, currentLanguageMode)
            val vistaSubtitle = if (vistaDeckSub != null) "$vistaDeckSub • Brightness & display optics" else "Display wellness, video takeover & optics controls"
            CollapsibleSubSection(
                title = vistaDeckTitle,
                subtitle = vistaSubtitle,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Brightness4,
                        contentDescription = null,
                        tint = cautionAmber,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isVistaDeckExpanded,
                onToggle = { isVistaDeckExpanded = !isVistaDeckExpanded }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_VISTA_DECK_ENABLED,
                    defaultVal = true,
                    title = "Enable $vistaDeckTitle",
                    subtitle = "Pull outward (horizontally from deflectors or vertically from top progress rail) while scrubbing brightness to open expanded display controls.",
                    onChanged = { onRefreshNeeded() }
                )
                if (prefs.getBoolean(LightspeedPreferences.KEY_VISTA_DECK_ENABLED, true)) {
                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_VISTA_DECK_LEFT_ENABLED,
                        defaultVal = true,
                        title = "Left Deflector Launch",
                        subtitle = "Pull inward from left deflector during brightness scrub to launch Vista Deck.",
                        onChanged = { onRefreshNeeded() }
                    )
                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_VISTA_DECK_RIGHT_ENABLED,
                        defaultVal = true,
                        title = "Right Deflector Launch",
                        subtitle = "Pull inward from right deflector during brightness scrub to launch Vista Deck.",
                        onChanged = { onRefreshNeeded() }
                    )
                }
            }

            // 2. Orbital Capsule (Camera Cutout HUD) [Experimental]
            OrbitalCapsuleLabSubSection(
                context = context,
                prefs = prefs,
                cautionAmber = cautionAmber,
                currentLanguageMode = currentLanguageMode,
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
                currentLanguageMode = currentLanguageMode,
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
                title = "Core Cooling Schedule",
                subtitle = "Configurable weekly maintenance reminder",
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
                    title = "Scheduled Core Cooling Reminder",
                    subtitle = "Dispatches a gentle reminder when system core cooling is recommended.",
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
                        text = "STRICT COLD-START POLICY: Zero automatic reboots. When weekly cooling cycle is reached, dispatches a silent status reminder to the HUD and Orbital Capsule.",
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
