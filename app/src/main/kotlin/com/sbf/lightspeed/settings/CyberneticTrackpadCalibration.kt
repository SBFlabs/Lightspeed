package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.bot.LightspeedBotContract
import com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale

@Composable
fun TrackpadGeometrySubSection(
    isLeft: Boolean,
    context: Context,
    prefs: SharedPreferences,
    isParentExpanded: Boolean = true,
    isSubExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val flankKey = if (isLeft) "left" else "right"
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val landscapeMode = prefs.getString(
        com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE,
        com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
    ) ?: com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
    val useCustomLandscapeKeys = isLandscape && landscapeMode == com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM

    val basePosYKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_POS_Y_RIGHT
    val baseHeightKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_HEIGHT_RIGHT
    val posYKey = if (useCustomLandscapeKeys) "${basePosYKey}_landscape" else basePosYKey
    val heightKey = if (useCustomLandscapeKeys) "${baseHeightKey}_landscape" else baseHeightKey
    val defaultPosY = prefs.getInt(basePosYKey, 78)
    val defaultHeight = if (useCustomLandscapeKeys) (prefs.getInt(baseHeightKey, 120) * 0.55f).toInt().coerceAtLeast(48) else 120
    val touchWidthKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_TOUCH_WIDTH_RIGHT
    val radiusKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_RADIUS_RIGHT
    val armReachKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_ARM_REACH_RIGHT
    val previewKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_PREVIEW_RIGHT
    val triggerOpacityKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_TRIGGER_OPACITY_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_TRIGGER_OPACITY_RIGHT
    val dpadOpacityKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_DPAD_OPACITY_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_DPAD_OPACITY_RIGHT
    val armsOpacityKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_ARMS_OPACITY_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_ARMS_OPACITY_RIGHT
    val styleKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_STYLE_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_STYLE_RIGHT
    val colorModeKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_COLOR_MODE_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_COLOR_MODE_RIGHT
    val orientationKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_RIGHT

    var localExpanded by remember {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_geo_$flankKey", false))
    }
    val effectiveExpanded = isSubExpanded ?: localExpanded
    var isTriggerStripExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_trigger_strip_$flankKey", true))
    }
    var isThreeArmsExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_three_arms_$flankKey", true))
    }
    var trackpadStyle by remember { mutableStateOf(prefs.getString(styleKey, "classic_arc") ?: "classic_arc") }
    var trackpadColorMode by remember {
        mutableStateOf(
            prefs.getString(colorModeKey, LightspeedBotContract.COLOR_MODE_HYBRID_FUSION)
                ?: LightspeedBotContract.COLOR_MODE_HYBRID_FUSION
        )
    }
    var trackpadOrientation by remember { mutableStateOf(prefs.getString(orientationKey, "arc") ?: "arc") }
    var cursorShape by remember { mutableStateOf(prefs.getString(LightspeedBotContract.KEY_BOT_CURSOR_SHAPE, "crosshair") ?: "crosshair") }

    // Auto-reload overlay when any trackpad slider changes
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("pref_bot_trackpad_") || key.contains("trackpad"))) {
                LightspeedBotTrackpadOverlay.reload(context)
            }
            if (key == colorModeKey) {
                trackpadColorMode = prefs.getString(colorModeKey, LightspeedBotContract.COLOR_MODE_HYBRID_FUSION)
                    ?: LightspeedBotContract.COLOR_MODE_HYBRID_FUSION
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    CollapsibleSubSection(
        title = lsString(R.string.sec_geometry_calibration),
        subtitle = lsString(R.string.secd_style_3_button_layout_automati),
        isExpanded = effectiveExpanded,
        onToggle = {
            if (onToggle != null) {
                onToggle()
            } else {
                localExpanded = !localExpanded
                prefs.edit().putBoolean("pref_sub_trackpad_geo_$flankKey", localExpanded).apply()
            }
        },
        icon = {
            Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(18.dp))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(lsString(R.string.tp_layout_hdr), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val orientations = listOf("arc" to lsString(R.string.tp_radial_arc), "edge" to lsString(R.string.tp_edge_strip))
                for ((id, label) in orientations) {
                    TrackpadPillButton(
                        text = label,
                        isSelected = trackpadOrientation == id,
                        onClick = {
                            trackpadOrientation = id
                            prefs.edit().putString(orientationKey, id).apply()
                            LightspeedBotTrackpadOverlay.reload(context)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            val unlockEpoch by com.sbf.lightspeed.system.LightspeedInfinityManager.unlockEpoch.collectAsState()
            val isInfinity = remember(unlockEpoch) {
                com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
            }
            var pendingInfinityFeature by remember { mutableStateOf<String?>(null) }
            if (pendingInfinityFeature != null) {
                LightspeedInfinityDialog(
                    context = context,
                    onDismiss = { pendingInfinityFeature = null },
                    featureContext = pendingInfinityFeature
                )
            }
            fun promptInfinity(featureName: String) {
                LightspeedHapticEngine.triggerWarning(context)
                pendingInfinityFeature = featureName
            }

            val openFlightDeck = LocalOpenFlightControlDeck.current
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(lsString(R.string.tp_color_engine), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
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
            val colorModes = listOf(
                LightspeedBotContract.COLOR_MODE_THEME_12 to "◈ 12-Theme",
                LightspeedBotContract.COLOR_MODE_M3_DYNAMIC to "🎨 Material 3",
                LightspeedBotContract.COLOR_MODE_HYBRID_FUSION to "⚡ Hybrid Fusion"
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for ((modeId, modeLabel) in colorModes) {
                    TrackpadPillButton(
                        text = modeLabel,
                        isSelected = trackpadColorMode == modeId,
                        onClick = {
                            trackpadColorMode = modeId
                            prefs.edit().putString(colorModeKey, modeId).apply()
                            LightspeedBotTrackpadOverlay.activeFlankIsLeft = isLeft
                            LightspeedBotTrackpadOverlay.reload(context)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(lsString(R.string.tp_cursor_shape), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            val freeCursors = setOf("crosshair", "pointer")
            val cursorRows = listOf(
                listOf(
                    "crosshair" to "⊕ Cross",
                    "pointer" to "➤ Arrow",
                    "tactical_triangle" to "▲ Vector"
                ),
                listOf(
                    "hud_ring" to "◎ HUD Ring",
                    "cyber_bracket" to "⊏ Cyber",
                    "stealth_dot" to "▪ Stealth"
                ),
                listOf(
                    "ring" to "⭕ Ring",
                    "diamond" to "❖ Diamond",
                    "dot" to "● Dot"
                )
            )

            for (row in cursorRows) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for ((id, label) in row) {
                        val isLocked = !isInfinity && id !in freeCursors
                        TrackpadPillButton(
                            text = label,
                            isSelected = cursorShape == id && !isLocked,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    promptInfinity(label)
                                } else {
                                    cursorShape = id
                                    prefs.edit().putString(LightspeedBotContract.KEY_BOT_CURSOR_SHAPE, id).apply()
                                    LightspeedBotTrackpadOverlay.reload(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(lsString(R.string.tp_style_hdr), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            val freeStyles = setOf("classic_arc", "angular", "floating", "gear_tooth")
            val chassisStyles = listOf(
                "classic_arc" to lsString(R.string.tp_st_classic),
                "angular" to lsString(R.string.tp_st_angular),
                "floating" to lsString(R.string.tp_st_floating),
                "gear_tooth" to lsString(R.string.tp_st_gear),
                "soft_wing" to lsString(R.string.tp_st_wing),
                "liquid_glass" to lsString(R.string.tp_st_glass),
                "delta" to lsString(R.string.tp_st_delta),
                "shield" to lsString(R.string.tp_st_shield),
                "minimal_arms" to lsString(R.string.tp_st_minimal)
            )
            for (chunk in chassisStyles.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((id, label) in chunk) {
                        val isLocked = !isInfinity && id !in freeStyles
                        TrackpadPillButton(
                            text = label,
                            isSelected = trackpadStyle == id && !isLocked,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    promptInfinity(label)
                                } else {
                                    trackpadStyle = id
                                    prefs.edit().putString(styleKey, id).apply()
                                    LightspeedBotTrackpadOverlay.reload(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            HorizontalDivider(color = lsColors.borderSubtle.copy(alpha = 0.25f), thickness = 0.6.dp)

            // Card 1: Edge Trigger Strip (Sub-accordion with 4 signature sliders)
            CollapsibleSubSection(
                title = lsString(R.string.sec_edge_trigger_strip),
                subtitle = lsString(R.string.secd_vertical_offset_height_width_i),
                isExpanded = isTriggerStripExpanded,
                onToggle = {
                    isTriggerStripExpanded = !isTriggerStripExpanded
                    prefs.edit().putBoolean("pref_sub_trackpad_trigger_strip_$flankKey", isTriggerStripExpanded).apply()
                },
                icon = {
                    Icon(Icons.Default.TouchApp, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(16.dp))
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = posYKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_vert_off),
                        minVal = 10,
                        maxVal = 90,
                        step = 1,
                        defaultVal = defaultPosY
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = heightKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_height),
                        minVal = 60,
                        maxVal = 320,
                        step = 5,
                        defaultVal = defaultHeight
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = touchWidthKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_width),
                        minVal = 8,
                        maxVal = 32,
                        step = 1,
                        defaultVal = 14
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = triggerOpacityKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_opacity),
                        minVal = 0,
                        maxVal = 100,
                        step = 5,
                        defaultVal = 50
                    )
                }
            }

            // Card 2: Three-Button Arm & Dome (Sub-accordion with 4 signature sliders)
            CollapsibleSubSection(
                title = lsString(R.string.sec_three_button_arm_dome),
                subtitle = lsString(R.string.secd_half_circle_trackpad_radius_re),
                isExpanded = isThreeArmsExpanded,
                onToggle = {
                    isThreeArmsExpanded = !isThreeArmsExpanded
                    prefs.edit().putBoolean("pref_sub_trackpad_three_arms_$flankKey", isThreeArmsExpanded).apply()
                },
                icon = {
                    Icon(Icons.Default.Adjust, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(16.dp))
                }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = radiusKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_radius),
                        minVal = 50,
                        maxVal = 110,
                        step = 2,
                        defaultVal = 72
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = dpadOpacityKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_dome_op),
                        minVal = 10,
                        maxVal = 100,
                        step = 5,
                        defaultVal = 100
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = armReachKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_ext_reach),
                        minVal = 36,
                        maxVal = 80,
                        step = 2,
                        defaultVal = 52
                    )
                    PrefDottedSliderRow(
                        context = context,
                        prefs = prefs,
                        keyResName = armsOpacityKey,
                        titleResName = "",
                        defaultTitle = lsString(R.string.tp_arms_op),
                        minVal = 10,
                        maxVal = 100,
                        step = 5,
                        defaultVal = 100
                    )
                }
            }
        }
    }
}

@Composable
fun TrackpadPhysicsSubSection(
    isLeft: Boolean,
    prefs: SharedPreferences,
    isSubExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val flankKey = if (isLeft) "left" else "right"
    val sensitivityKey = LightspeedBotContract.KEY_BOT_TRACKPAD_SENSITIVITY
    val lingerKey = LightspeedBotContract.KEY_BOT_TRACKPAD_LINGER_MS

    var localExpanded by remember {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_physics_$flankKey", false))
    }
    val effectiveExpanded = isSubExpanded ?: localExpanded
    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat(sensitivityKey, 1.3f)) }
    var lingerDelay by remember { mutableFloatStateOf(prefs.getInt(lingerKey, 1800).toFloat()) }

    CollapsibleSubSection(
        title = lsString(R.string.sec_cursor_physics_timing),
        subtitle = lsString(R.string.secd_tracking_sensitivity_finger_li),
        isExpanded = effectiveExpanded,
        onToggle = {
            if (onToggle != null) {
                onToggle()
            } else {
                localExpanded = !localExpanded
                prefs.edit().putBoolean("pref_sub_trackpad_physics_$flankKey", localExpanded).apply()
            }
        },
        icon = {
            Icon(Icons.Default.Speed, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(18.dp))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sensFormatted = String.format(Locale.US, "%.1fx", sensitivity)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lsString(R.string.tp_cursor_speed), fontSize = 11.sp, color = lsColors.textPrimary)
                Text(sensFormatted, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            }
            DeckSlider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    prefs.edit().putFloat(sensitivityKey, it).apply()
                },
                valueRange = 0.5f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )

            val lingerFormatted = String.format(Locale.US, "%.1fs", lingerDelay / 1000f)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(lsString(R.string.tp_linger), fontSize = 11.sp, color = lsColors.textPrimary)
                Text("${lingerDelay.toInt()} ms ($lingerFormatted)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            }
            DeckSlider(
                value = lingerDelay,
                onValueChange = {
                    lingerDelay = it
                    prefs.edit().putInt(lingerKey, it.toInt()).apply()
                },
                valueRange = 800f..3500f,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
