package com.sbf.lightspeed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PriorityHigh
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
import androidx.compose.ui.zIndex
import com.sbf.lightspeed.settings.deckGlassSurface
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.ui.theme.LocalDeckTheme
import kotlin.math.roundToInt

/**
 * Tactical Refueling Bay Customization Modal triggered by long-pressing clock or telemetry gauge.
 */
@Composable
fun RefuelingCustomizationModal(
    isLandscape: Boolean,
    batteryStyle: String,
    sleepTimeout: String,
    sleepAction: String,
    splitRatio: Float,
    clockFormat: String,
    dateFormat: String,
    clockFont: String,
    showAlarm: Boolean,
    onBatteryStyleChange: (String) -> Unit,
    onSleepTimeoutChange: (String) -> Unit,
    onSleepActionChange: (String) -> Unit,
    onSplitRatioChange: (Float) -> Unit,
    onClockFormatChange: (String) -> Unit,
    onDateFormatChange: (String) -> Unit,
    onClockFontChange: (String) -> Unit,
    onShowAlarmChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(Unit) {
        scrollState.scrollTo(0)
    }
    val activeSnapshot = LocalDeckTheme.current
    val shape = RoundedCornerShape(activeSnapshot.cornerRadiusDp.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() }
            .zIndex(10005f),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 640.dp)
                .clip(shape)
                .deckGlassSurface(
                    snapshot = activeSnapshot,
                    isBlurAvailable = true,
                    isFlyout = true
                )
                .clickable { /* consume tap inside card */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "⚙ HUD & DISPLAY CUSTOMIZATION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.2.sp
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f))

                // 1. Chrono Clock Format
                var showOrbitalInfo by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "CHRONO CLOCK FORMAT",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "About Stardate time",
                            tint = if (showOrbitalInfo) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.accentWarn,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showOrbitalInfo = !showOrbitalInfo }
                        )
                    }
                    if (showOrbitalInfo) {
                        Text(
                            text = "STARDATE uses the standard Solar Calendar Stardate: SD <year>.<day of year> · <24h time>. " +
                                "Example: SD 2026.292 · 16:38 means year 2026, day 292 of the year, at 16:38.",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                            lineHeight = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val formats = listOf(
                            "24h_sec" to "24H + SEC",
                            "24h" to "24H",
                            "12h_sec" to "12H + SEC",
                            "12h" to "12H",
                            "stardate" to "STARDATE"
                        )
                        formats.forEach { (key, label) ->
                            val isSelected = clockFormat == key || (key == "stardate" && clockFormat == "orbital")
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onClockFormatChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 2. Date Format
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DATE DISPLAY FORMAT",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val dateFormats = listOf(
                            "full" to "FULL",
                            "short" to "SHORT",
                            "iso" to "ISO",
                            "compact" to "COMPACT",
                            "hidden" to "HIDE"
                        )
                        dateFormats.forEach { (key, label) ->
                            val isSelected = dateFormat == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onDateFormatChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 3. Clock Font Style & Alarm Toggle
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "TYPOGRAPHY & ALARM BADGE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val fonts = listOf(
                            "mono" to "Tactical",
                            "sans" to "Clean",
                            "serif" to "Classic",
                            "cyber" to "Cyber"
                        )
                        fonts.forEach { (key, label) ->
                            val isSelected = clockFont == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onClockFontChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label.uppercase(),
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (showAlarm) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                .border(1.dp, if (showAlarm) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .clickable { onShowAlarmChange(!showAlarm) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (showAlarm) "ALARM: ON" else "ALARM: OFF",
                                fontSize = 8.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (showAlarm) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // 4. Layout Split Ratio Slider
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val firstLabel = if (isLandscape) "LEFT" else "TOP"
                    val secondLabel = if (isLandscape) "RIGHT" else "BOTTOM"
                    val firstPct = (splitRatio * 100).roundToInt()
                    val secondPct = 100 - firstPct
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$firstLabel / $secondLabel LAYOUT RATIO",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Text(
                            text = "$firstPct% / $secondPct%",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = splitRatio,
                        onValueChange = onSplitRatioChange,
                        valueRange = 0.30f..0.70f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                // 5. Battery Telemetry Style
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "GAUGE TELEMETRY STYLE",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val styles = listOf("halo" to "Halo", "reactor_ticks" to "Reactor", "dual_wings" to "Wings", "tachometer" to "Tacho")
                        styles.forEach { (key, label) ->
                            val isSelected = batteryStyle == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onBatteryStyleChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 6. Inactivity Action (Physical Screen Off vs OLED Shield)
                var showHardwareLockInfo by remember { mutableStateOf(false) }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "INACTIVITY SLEEP ACTION",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = LightspeedStyle.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.PriorityHigh,
                            contentDescription = "About Hardware Lock",
                            tint = if (showHardwareLockInfo) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.accentWarn,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHardwareLockInfo = !showHardwareLockInfo }
                        )
                    }
                    if (showHardwareLockInfo) {
                        Text(
                            text = "HARDWARE LOCK: Uses Shizuku ADB power commands to blank the physical display panel. Note: System security automatically locks the device; waking requires pressing the physical power button twice and unlocking.",
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                            lineHeight = 13.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val actions = listOf("overlay" to "OLED Shield (Overlay)", "screen_off" to "Hardware Lock (Screen Off)")
                        actions.forEach { (key, label) ->
                            val isSelected = sleepAction == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onSleepActionChange(key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                // 7. Inactivity Timeout
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "INACTIVITY TIMEOUT",
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val timeouts = listOf("5s", "15s", "30s", "60s", "120s", "300s", "never")
                        timeouts.forEach { timeout ->
                            val isSelected = normalizeRefuelingTimeout(sleepTimeout) == timeout
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else LightspeedTokens.TacticalSurfaceModal)
                                    .border(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                    .clickable { onSleepTimeoutChange(timeout) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = timeout.uppercase(),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
