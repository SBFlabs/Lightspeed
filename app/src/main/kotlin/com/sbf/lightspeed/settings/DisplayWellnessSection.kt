package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Visibility
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
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.display.LightspeedEyeCareEngine
import com.sbf.lightspeed.system.display.LightspeedSmartPixelsOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun DisplayWellnessSection(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors

    var extraDimEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EXTRA_DIM_ENABLED, false)) }
    var extraDimLevel by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EXTRA_DIM_LEVEL, 40).toFloat()) }

    var eyeCareEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_CARE_ENABLED, false)) }
    var eyeCareTemp by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_CARE_TEMP, 3200).toFloat()) }
    var eyeCareMode by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_EYE_CARE_MODE, "native") ?: "native") }
    var eyeRestEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, false)) }
    var eyeRestInterval by remember { mutableFloatStateOf(prefs.getInt(LightspeedPreferences.KEY_EYE_REST_INTERVAL_MIN, 20).toFloat()) }
    var eyeRestStrength by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_EYE_REST_STRENGTH, "fullscreen") ?: "fullscreen") }

    var smartPixelsEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) }
    var smartPixelsPattern by remember { mutableIntStateOf(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)) }
    var smartPixelsDisableOnCast by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, true)) }

    var airlockEnabled by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, false)) }
    var airlockApps by remember { mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet<String>()) ?: emptySet<String>()) }
    var showAppPickerDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // 1. Extra Dim Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Brightness4, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("EXTRA DIM (SUB-ZERO)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("Extends brightness downward into OLED sub-zero levels", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = extraDimEnabled,
                    onCheckedChange = {
                        extraDimEnabled = it
                        LightspeedEyeCareEngine.applyExtraDim(context, it, extraDimLevel.toInt())
                        onStateChanged()
                    }
                )
            }
            if (extraDimEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Intensity: ${extraDimLevel.toInt()}%", fontSize = 11.sp, color = lsColors.accentInfo)
                }
                Slider(
                    value = extraDimLevel,
                    onValueChange = {
                        extraDimLevel = it
                        LightspeedEyeCareEngine.applyExtraDim(context, true, it.toInt())
                    },
                    valueRange = 10f..90f
                )
            }
        }

        // 2. Eye Care Blue Light Filter Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = lsColors.accentWarn, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("EYE CARE & BLUE LIGHT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("Native / SurfaceFlinger 1015 / Overlay warm filter", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = eyeCareEnabled,
                    onCheckedChange = {
                        eyeCareEnabled = it
                        LightspeedEyeCareEngine.applyEyeCare(context, it, eyeCareTemp.toInt())
                        onStateChanged()
                    }
                )
            }
            if (eyeCareEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Temperature: ${eyeCareTemp.toInt()}K", fontSize = 11.sp, color = lsColors.accentWarn)
                }
                Slider(
                    value = eyeCareTemp,
                    onValueChange = {
                        eyeCareTemp = it
                        LightspeedEyeCareEngine.applyEyeCare(context, true, it.toInt())
                    },
                    valueRange = 1200f..5500f
                )
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("native" to "Native", "overlay" to "Overlay", "surfaceflinger" to "Compositor").forEach { (m, label) ->
                        val isSelected = eyeCareMode == m
                        OutlinedButton(
                            onClick = {
                                eyeCareMode = m
                                prefs.edit().putString(LightspeedPreferences.KEY_EYE_CARE_MODE, m).apply()
                                LightspeedEyeCareEngine.applyEyeCare(context, true, eyeCareTemp.toInt())
                                onStateChanged()
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else ButtonDefaults.outlinedButtonColors()
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = lsColors.borderSubtle)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("20-20-20 Eye Rest Timer", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
                        Text("Every ${eyeRestInterval.toInt()} min of continuous screen time", fontSize = 9.5.sp, color = lsColors.textSecondary)
                    }
                    Switch(
                        checked = eyeRestEnabled,
                        onCheckedChange = {
                            eyeRestEnabled = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_EYE_REST_REMINDER_ENABLED, it).apply()
                            LightspeedEyeCareEngine.onScreenStateChanged(context, it)
                            onStateChanged()
                        }
                    )
                }
                if (eyeRestEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("Reminder Strength Mode", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "fullscreen" to "Full-Screen",
                            "floating" to "Floating",
                            "notification" to "Notification"
                        ).forEach { (m, label) ->
                            val isSel = (eyeRestStrength == m)
                            OutlinedButton(
                                onClick = {
                                    eyeRestStrength = m
                                    prefs.edit().putString(LightspeedPreferences.KEY_EYE_REST_STRENGTH, m).apply()
                                    onStateChanged()
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = if (isSel) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text(label, fontSize = 9.5.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    DeckButton(
                        text = "Test 20-20-20 Reminder Now",
                        onClick = {
                            LightspeedEyeCareEngine.triggerReminder(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. OLED Smart Pixels Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.GridOn, contentDescription = null, tint = lsColors.accentOk, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("OLED SMART PIXELS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("Subpixel burn-in safe mesh + installer bypass", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = smartPixelsEnabled,
                    onCheckedChange = {
                        smartPixelsEnabled = it
                        LightspeedSmartPixelsOverlay.setEnabled(context, it, smartPixelsPattern)
                        onStateChanged()
                    }
                )
            }
            if (smartPixelsEnabled) {
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Mesh Density", fontSize = 11.sp, color = lsColors.textSecondary)
                    Text("$smartPixelsPattern%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.accentOk)
                }
                Slider(
                    value = smartPixelsPattern.toFloat(),
                    valueRange = 5f..85f,
                    onValueChange = {
                        val intVal = it.roundToInt().coerceIn(5, 85)
                        smartPixelsPattern = intVal
                        LightspeedSmartPixelsOverlay.setEnabled(context, true, intVal)
                        onStateChanged()
                    },
                    modifier = Modifier.fillMaxWidth().height(24.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Disable on Record / Cast", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
                        Text("Hides pixel mesh during screen recording or wireless mirroring", fontSize = 9.5.sp, color = lsColors.textSecondary)
                    }
                    Switch(
                        checked = smartPixelsDisableOnCast,
                        onCheckedChange = {
                            smartPixelsDisableOnCast = it
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, it).apply()
                            LightspeedSmartPixelsOverlay.refreshCaptureBypass(context)
                            onStateChanged()
                        }
                    )
                }
            }
        }

        // 4. Mindful 5s Airlock Card
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.LockClock, contentDescription = null, tint = lsColors.accentAlert, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("MINDFUL 5s AIRLOCK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text("${airlockApps.size} apps gated with breathing interlock", fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                }
                Switch(
                    checked = airlockEnabled,
                    onCheckedChange = {
                        airlockEnabled = it
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_CONSCIOUS_AIRLOCK_ENABLED, it).apply()
                        onStateChanged()
                    }
                )
            }
            if (airlockEnabled) {
                Spacer(Modifier.height(8.dp))
                DeckButton(
                    text = "Configure Gated Apps (${airlockApps.size})",
                    onClick = { showAppPickerDialog = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showAppPickerDialog) {
        ConsciousAirlockAppPickerDialog(
            context = context,
            prefs = prefs,
            onDismissRequest = {
                showAppPickerDialog = false
                airlockApps = prefs.getStringSet(LightspeedPreferences.KEY_CONSCIOUS_DELAY_PACKAGES, emptySet<String>()) ?: emptySet<String>()
            }
        )
    }
}
