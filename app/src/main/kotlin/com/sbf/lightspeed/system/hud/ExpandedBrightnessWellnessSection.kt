package com.sbf.lightspeed.system.hud

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.sbf.lightspeed.settings.ConsciousAirlockConfigActivity
import com.sbf.lightspeed.settings.DeckSubChip
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedDisplayWellnessResetEngine
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
internal fun ExpandedBrightnessWellnessSection(
    service: AccessibilityService,
    onOpenAirlockPicker: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    val prefs = service.defaultPrefs()

    var systemBrightness by remember {
        val cur = LightspeedDisplayWellnessResetEngine.getSystemBrightness(service)
        mutableFloatStateOf(cur / 255f)
    }

    var videoTakeoverZone by remember {
        val curOv = LightspeedDisplayWellnessResetEngine.getActiveVideoOverride()
        mutableFloatStateOf(if (curOv != null && curOv > 0f) 1.0f + (curOv * 0.2f) else 1.0f)
    }

    var extraDimEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)) }
    var extraDimLevel by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40).toFloat()) }

    var eyeCareEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)) }
    var eyeCareTemp by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3200).toFloat()) }
    var eyeCareMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native") ?: "native") }

    var smartPixelsEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) }
    var smartPixelsPattern by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)) }
    var smartPixelsDisableOnCast by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, true)) }

    var eyeRestEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) }
    var autoResetMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, LightspeedPreferences.AUTO_RESET_OFF) ?: LightspeedPreferences.AUTO_RESET_OFF)
    }

    var airlockEnabled by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false))
    }
    var airlockApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet()) ?: emptySet())
    }
    var showAirlockAppPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Brightness & Video Takeover Slider
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("BRIGHTNESS & ⚡ VIDEO TAKEOVER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
            val isBoost = videoTakeoverZone > 1.0f
            Text(
                text = if (isBoost) "⚡ ${(videoTakeoverZone * 100).toInt()}% (Video Override)" else "${(systemBrightness * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isBoost) lsColors.accentWarn else lsColors.accentInfo
            )
        }
        Slider(
            value = if (videoTakeoverZone > 1.0f) videoTakeoverZone else systemBrightness,
            valueRange = 0.05f..1.20f,
            onValueChange = { newPct ->
                if (newPct > 1.0f) {
                    videoTakeoverZone = newPct
                    LightspeedDisplayWellnessResetEngine.setVideoOverride(service, 1.0f)
                } else {
                    videoTakeoverZone = 1.0f
                    systemBrightness = newPct
                    LightspeedDisplayWellnessResetEngine.clearVideoOverride(service)
                    LightspeedDisplayWellnessResetEngine.setSystemBrightness(service, kotlin.math.round(newPct * 255).toInt())
                }
            },
            modifier = Modifier.height(26.dp)
        )

        // 2. Auto-Brightness Reset Chips
        Text("AUTO-BRIGHTNESS RESET TRIGGER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentInfo)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val chips: List<Pair<String, String>> = listOf(
                Pair(LightspeedPreferences.AUTO_RESET_SCREEN_OFF, "Screen Off"),
                Pair(LightspeedPreferences.AUTO_RESET_APP_SWITCH, "App Switch"),
                Pair(LightspeedPreferences.AUTO_RESET_RECENTS_CLOSE, "Recents Close")
            )
            chips.forEach { (mode, title) ->
                val isSelected = autoResetMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) lsColors.accentInfo.copy(alpha = 0.3f) else lsColors.dialogSurface.copy(alpha = 0.3f))
                        .clickable {
                            val next = if (isSelected) LightspeedPreferences.AUTO_RESET_OFF else mode
                            autoResetMode = next
                            prefs.edit().putString(LightspeedPreferences.KEY_BRIGHTNESS_AUTO_RESET_MODE, next).apply()
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) lsColors.accentInfo else lsColors.textSecondary)
                }
            }
        }

        // 3. Extra Dim & Eye Care Blue Light
        Text("EXTRA DIM & EYE CARE BLUE LIGHT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentWarn)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Extra Dim: ${extraDimLevel.toInt()}%", fontSize = 9.5.sp, color = lsColors.textPrimary)
            Switch(
                checked = extraDimEnabled,
                onCheckedChange = {
                    extraDimEnabled = it
                    LightspeedEyeCareEngine.applyExtraDim(service, it, extraDimLevel.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (extraDimEnabled) {
            Slider(
                value = extraDimLevel,
                valueRange = 10f..90f,
                onValueChange = {
                    extraDimLevel = it
                    LightspeedEyeCareEngine.applyExtraDim(service, true, it.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Eye Care Kelvin: ${eyeCareTemp.toInt()}K", fontSize = 9.5.sp, color = lsColors.textPrimary)
            Switch(
                checked = eyeCareEnabled,
                onCheckedChange = {
                    eyeCareEnabled = it
                    LightspeedEyeCareEngine.applyEyeCare(service, it, eyeCareTemp.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (eyeCareEnabled) {
            Slider(
                value = eyeCareTemp,
                valueRange = 1800f..5500f,
                onValueChange = {
                    eyeCareTemp = it
                    LightspeedEyeCareEngine.applyEyeCare(service, true, it.toInt())
                },
                modifier = Modifier.height(22.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    Pair("native", "Native"),
                    Pair("surfaceflinger", "Shizuku Compositor"),
                    Pair("overlay", "Overlay")
                ).forEach { (m, label) ->
                    val isSel = eyeCareMode == m
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSel) lsColors.accentWarn.copy(alpha = 0.25f) else lsColors.dialogSurface.copy(alpha = 0.3f))
                            .clickable {
                                eyeCareMode = m
                                prefs.edit().putString(LightspeedPreferences.KEY_EYE_CARE_MODE, m).apply()
                                LightspeedEyeCareEngine.applyEyeCare(service, true, eyeCareTemp.toInt())
                            }
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 8.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal, color = if (isSel) lsColors.accentWarn else lsColors.textSecondary)
                    }
                }
            }
        }

        // 4. OLED Smart Pixels & 20-20-20 Eye Rest
        Text("OLED SMART PIXELS & 20-20-20 REST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentOk)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Smart Pixels Density: $smartPixelsPattern%", fontSize = 9.5.sp, color = lsColors.textPrimary)
            Switch(
                checked = smartPixelsEnabled,
                onCheckedChange = {
                    smartPixelsEnabled = it
                    LightspeedSmartPixelsOverlay.setEnabled(service, it, smartPixelsPattern)
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (smartPixelsEnabled) {
            Slider(
                value = smartPixelsPattern.toFloat(),
                valueRange = 5f..85f,
                onValueChange = {
                    val intVal = it.roundToInt().coerceIn(5, 85)
                    smartPixelsPattern = intVal
                    LightspeedSmartPixelsOverlay.setEnabled(service, true, intVal)
                },
                modifier = Modifier.height(22.dp)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Bypass on Screen Record / Cast", fontSize = 9f.sp, color = lsColors.textSecondary)
                Switch(
                    checked = smartPixelsDisableOnCast,
                    onCheckedChange = {
                        smartPixelsDisableOnCast = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, it).apply()
                        LightspeedSmartPixelsOverlay.refreshCaptureBypass(service)
                    },
                    modifier = Modifier.height(20.dp)
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("20-20-20 Eye Rest Timer", fontSize = 9.5.sp, color = lsColors.textPrimary)
            Switch(
                checked = eyeRestEnabled,
                onCheckedChange = {
                    eyeRestEnabled = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, it).apply()
                    LightspeedEyeCareEngine.onScreenStateChanged(service, it)
                },
                modifier = Modifier.height(22.dp)
            )
        }

        // 5. Conscious 5s App Launch (Mindful Airlock)
        Text("CONSCIOUS 5s APP LAUNCH (MINDFUL AIRLOCK)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.accentAlert)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Breathing Interlock Delay", fontSize = 9.5.sp, color = lsColors.textPrimary)
                Text("${airlockApps.size} gated apps with 5s mindful pause", fontSize = 8.5.sp, color = lsColors.textSecondary)
            }
            Switch(
                checked = airlockEnabled,
                onCheckedChange = {
                    airlockEnabled = it
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, it).apply()
                },
                modifier = Modifier.height(22.dp)
            )
        }
        if (airlockEnabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                DeckSubChip(
                    text = "Gated Apps (${airlockApps.size}) ⚙",
                    icon = Icons.Default.LockClock,
                    isActive = airlockApps.isNotEmpty(),
                    onClick = onOpenAirlockPicker
                )
            }
        }
    }
}
