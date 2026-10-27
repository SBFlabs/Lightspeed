package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
    isParentExpanded: Boolean = true
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
    val orientationKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_LEFT else LightspeedBotContract.KEY_BOT_TRACKPAD_ORIENTATION_RIGHT

    var isSubExpanded by remember {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_geo_$flankKey", false))
    }
    var isTriggerStripExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_trigger_strip_$flankKey", true))
    }
    var isThreeArmsExpanded by rememberSaveable {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_three_arms_$flankKey", true))
    }
    var trackpadStyle by remember { mutableStateOf(prefs.getString(styleKey, "classic_arc") ?: "classic_arc") }
    var trackpadOrientation by remember { mutableStateOf(prefs.getString(orientationKey, "arc") ?: "arc") }
    var cursorShape by remember { mutableStateOf(prefs.getString(LightspeedBotContract.KEY_BOT_CURSOR_SHAPE, "crosshair") ?: "crosshair") }

    // Automatic Live Trigger Zone Preview while Geometry & Calibration is open
    val shouldPreview = isParentExpanded && isSubExpanded
    DisposableEffect(shouldPreview) {
        prefs.edit().putBoolean(previewKey, shouldPreview).apply()
        LightspeedBotTrackpadOverlay.reload(context)
        onDispose {
            prefs.edit().putBoolean(previewKey, false).apply()
            LightspeedBotTrackpadOverlay.reload(context)
        }
    }

    // Auto-reload overlay when any trackpad slider changes
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && (key.startsWith("pref_bot_trackpad_") || key.contains("trackpad"))) {
                LightspeedBotTrackpadOverlay.reload(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    CollapsibleSubSection(
        title = "Geometry & Calibration",
        subtitle = "Style, 3-button layout & automatic live trigger preview",
        isExpanded = isSubExpanded,
        onToggle = {
            isSubExpanded = !isSubExpanded
            prefs.edit().putBoolean("pref_sub_trackpad_geo_$flankKey", isSubExpanded).apply()
        },
        icon = {
            Icon(Icons.Default.DisplaySettings, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(18.dp))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("3-BUTTON LAYOUT ORIENTATION", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val orientations = listOf("arc" to "Radial Arc (Outward)", "edge" to "Edge Strip (Near Edge)")
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

            val isInfinity = com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
            fun promptInfinity() {
                LightspeedHapticEngine.triggerWarning(context)
                try {
                    context.startActivity(
                        android.content.Intent(context, com.sbf.lightspeed.CockpitDialogActivity::class.java).apply {
                            action = com.sbf.lightspeed.CockpitDialogActivity.ACTION_SHOW_INFINITY
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        }
                    )
                } catch (_: Exception) {}
            }

            Text("CYBERNETIC CHASSIS STYLE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            val freeStyles = setOf("classic_arc", "angular", "floating", "gear_tooth")
            val stylesRow1 = listOf(
                "classic_arc" to "Classic Arc",
                "angular" to "Angular",
                "floating" to "Floating"
            )
            val stylesRow2 = listOf(
                "gear_tooth" to "Gear Tooth",
                "soft_wing" to "Soft Wing",
                "liquid_glass" to "Liquid Glass"
            )
            val stylesRow3 = listOf(
                "delta" to "Delta",
                "shield" to "Shield",
                "minimal_arms" to "Minimal"
            )
            for (rowStyles in listOf(stylesRow1, stylesRow2, stylesRow3)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((id, label) in rowStyles) {
                        val isLocked = !isInfinity && id !in freeStyles
                        TrackpadPillButton(
                            text = label,
                            isSelected = trackpadStyle == id && !isLocked,
                            isLocked = isLocked,
                            onClick = {
                                if (isLocked) {
                                    promptInfinity()
                                } else {
                                    trackpadStyle = id
                                    prefs.edit().putString(styleKey, id).apply()
                                    LightspeedBotTrackpadOverlay.reload(context)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text("TACTICAL CURSOR SHAPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val freeCursors = setOf("crosshair", "pointer")
                val cursorShapes = listOf(
                    "crosshair" to "⊕ Cross",
                    "pointer" to "➤ Arrow",
                    "ring" to "◎ Ring",
                    "diamond" to "❖ Dmnd",
                    "dot" to "● Dot"
                )
                for ((id, label) in cursorShapes) {
                    val isLocked = !isInfinity && id !in freeCursors
                    TrackpadPillButton(
                        text = label,
                        isSelected = cursorShape == id && !isLocked,
                        isLocked = isLocked,
                        onClick = {
                            if (isLocked) {
                                promptInfinity()
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

            HorizontalDivider(color = lsColors.borderSubtle.copy(alpha = 0.25f), thickness = 0.6.dp)

            // Card 1: Edge Trigger Strip (Sub-accordion with 4 signature sliders)
            CollapsibleSubSection(
                title = "Edge Trigger Strip",
                subtitle = "Vertical offset, height, width & idle opacity",
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
                        defaultTitle = "Vertical Offset",
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
                        defaultTitle = "Height",
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
                        defaultTitle = "Width",
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
                        defaultTitle = "Opacity",
                        minVal = 0,
                        maxVal = 100,
                        step = 5,
                        defaultVal = 50
                    )
                }
            }

            // Card 2: Three-Button Arm & Dome (Sub-accordion with 4 signature sliders)
            CollapsibleSubSection(
                title = "Three-Button Arm & Dome",
                subtitle = "Half-circle trackpad radius, reach & opacities",
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
                        defaultTitle = "Radius",
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
                        defaultTitle = "Dome Opacity",
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
                        defaultTitle = "Extension Reach",
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
                        defaultTitle = "Arms Opacity",
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
    prefs: SharedPreferences
) {
    val lsColors = LightspeedStyle.colors
    val flankKey = if (isLeft) "left" else "right"
    val sensitivityKey = LightspeedBotContract.KEY_BOT_TRACKPAD_SENSITIVITY
    val lingerKey = LightspeedBotContract.KEY_BOT_TRACKPAD_LINGER_MS

    var isSubExpanded by remember {
        mutableStateOf(prefs.getBoolean("pref_sub_trackpad_physics_$flankKey", false))
    }
    var sensitivity by remember { mutableFloatStateOf(prefs.getFloat(sensitivityKey, 1.3f)) }
    var lingerDelay by remember { mutableFloatStateOf(prefs.getInt(lingerKey, 1800).toFloat()) }

    CollapsibleSubSection(
        title = "Cursor Physics & Timing",
        subtitle = "Tracking sensitivity & finger-lift linger delay",
        isExpanded = isSubExpanded,
        onToggle = {
            isSubExpanded = !isSubExpanded
            prefs.edit().putBoolean("pref_sub_trackpad_physics_$flankKey", isSubExpanded).apply()
        },
        icon = {
            Icon(Icons.Default.Speed, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(18.dp))
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sensFormatted = String.format(Locale.US, "%.1fx", sensitivity)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cursor Sensitivity / Speed", fontSize = 11.sp, color = lsColors.textPrimary)
                Text(sensFormatted, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            }
            Slider(
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
                Text("Finger-Lift Linger Delay", fontSize = 11.sp, color = lsColors.textPrimary)
                Text("${lingerDelay.toInt()} ms ($lingerFormatted)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
            }
            Slider(
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
