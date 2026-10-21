package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.logSwallowed
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.*
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun HudSensorDeckSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isStatusBarGeoExpanded: Boolean,
    onToggleStatusBarGeo: () -> Unit,
    isStatusBarGesturesExpanded: Boolean,
    onToggleStatusBarGestures: () -> Unit,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    onRefreshNeeded: () -> Unit
) {
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val lsColors = LightspeedStyle.colors
    val (sensorTitle, sensorSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.SENSOR_AREA, currentLanguageMode)

    CompactAccordionSection(
        title = sensorTitle,
        subtitle = sensorSub,
        icon = {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_STATUSBAR_ENABLED,
                defaultVal = false,
                title = "Enable Sensor Area Gestures",
                subtitle = "Top-edge gesture detection",
                onChanged = {
                    val isEnabled = prefs.getBoolean(LightspeedPreferences.KEY_STATUSBAR_ENABLED, false)
                    if (isStatusBarGeoExpanded) {
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_STATUSBAR_PREVIEW, isEnabled).apply()
                    }
                    safeReloadPreferences()
                    onRefreshNeeded()
                }
            )

            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val spanKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_SPAN_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_SPAN
            val defaultSpan = if (isLandscape) 150 else 200
            val thicknessKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_THICKNESS
            val defaultThickness = 35
            val offsetXKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_OFFSET_X_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_OFFSET_X
            val defaultOffsetX = if (isLandscape) 300 else 105

            // 1. Geometry & Sensitivity
            CollapsibleSubSection(
                title = "Geometry & Sensitivity",
                subtitle = "Span, thickness, offsets & idle glow",
                isExpanded = isStatusBarGeoExpanded,
                onToggle = onToggleStatusBarGeo
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isLandscape) "✦ Active Sensor Calibration: LANDSCAPE (Tablet Mode)" else "✦ Active Sensor Calibration: PORTRAIT (Phone Mode)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLandscape) {
                                "Calibrating landscape sensor deck dimensions directly (Span: 150dp, Thickness: 35dp, Offset X: Max)."
                            } else {
                                "Calibrating portrait phone dimensions directly (Span: 200dp, Thickness: 35dp, Offset X: 105dp). Rotate to landscape to calibrate tablet mode."
                            },
                            fontSize = 10.5.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
                PrefDottedSliderRow(context, prefs, spanKey, "", "Span (≥1000 = Full Width)", 50, 1080, 10, defaultSpan)
                PrefDottedSliderRow(context, prefs, thicknessKey, "", "Thickness", 10, 52, 2, defaultThickness)
                PrefDottedSliderRow(context, prefs, offsetXKey, "", "Horizontal Offset (X Axis)", -300, 300, 5, defaultOffsetX)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_OFFSET_Y, "", "Vertical Offset (Y Axis)", -100, 200, 5, 0)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_SENSITIVITY, "", "Touch Sensitivity", 10, 100, 5, 25)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_TRANSPARENCY, "", "Stealth Idle Glow", 0, 100, 5, 0)
            }

            // 2. Gestures
            CollapsibleSubSection(
                title = "Gestures & Macros",
                subtitle = "Tap, double-tap, left & right swipes with Hold Modifiers",
                isExpanded = isStatusBarGesturesExpanded,
                onToggle = onToggleStatusBarGestures
            ) {
                val statusVectors = listOf(
                    "TAP" to ("Tap" to ArrowDirection.TAP),
                    "DOUBLE_TAP" to ("Tap (Double)" to ArrowDirection.DOUBLE_TAP),
                    "SWIPE_LEFT" to ("Swipe Left" to ArrowDirection.SWIPE_LEFT),
                    "SWIPE_RIGHT" to ("Swipe Right" to ArrowDirection.SWIPE_RIGHT),
                    "SWIPE_LEFT_BACK" to ("Rebound Left" to ArrowDirection.LEFT_BACK),
                    "SWIPE_RIGHT_BACK" to ("Rebound Right" to ArrowDirection.RIGHT_BACK)
                )

                statusVectors.forEach { (vectorKey, pairInfo) ->
                    val (vectorTitle, arrowEnum) = pairInfo
                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", "$vectorTitle + Hold Modifier", dynamicActionTokens, tokenLabelCache)
                }
            }

            // 3. HUD Strip Swipe-Down to Notifications
            var isSwipeDownExpanded by rememberSaveable {
                mutableStateOf(prefs.getBoolean("pref_sub_swipe_down_statusbar", false))
            }
            CollapsibleSubSection(
                title = "HUD Strip Swipe-Down to Notifications",
                subtitle = "Dual-Action Right Side Pull for split Notifications & Quick Settings",
                icon = {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isSwipeDownExpanded,
                onToggle = {
                    isSwipeDownExpanded = !isSwipeDownExpanded
                    prefs.edit().putBoolean("pref_sub_swipe_down_statusbar", isSwipeDownExpanded).apply()
                }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS,
                    defaultVal = false,
                    title = "Swipe Down to Expand Notifications",
                    subtitle = "Dual-Action Right Side Pull: If your system status bar opens Quick Settings on the right side, this sensor zone lets you access Notifications from the same side:\n• Short Swipe: Pull down within the thickness of the sensor strip to expand Notifications.\n• Deep Swipe / Adjacent Gap: Pull further down or swipe the exposed native bar beside the strip to open Quick Settings.",
                    onChanged = {
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                )
            }
        }
    }
}
