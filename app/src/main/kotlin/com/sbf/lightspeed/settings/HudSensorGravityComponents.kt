package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

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
    onRefreshNeeded: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val lsColors = LightspeedStyle.colors
    val (sensorTitle, sensorSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.SENSOR_AREA, languageMode)

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
                title = lsString(R.string.sens_enable_title),
                subtitle = lsString(R.string.sens_enable_sub),
                onChanged = {
                    safeReloadPreferences()
                    onRefreshNeeded()
                }
            )

            val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val spanKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_SPAN_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_SPAN
            val defaultSpan = if (isLandscape) 150 else 160
            val thicknessKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_THICKNESS_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_THICKNESS
            val defaultThickness = 35
            val offsetXKey = if (isLandscape) LightspeedPreferences.KEY_STATUSBAR_OFFSET_X_LANDSCAPE else LightspeedPreferences.KEY_STATUSBAR_OFFSET_X
            val defaultOffsetX = 0

            // 1. Geometry & Sensitivity
            CollapsibleSubSection(
                title = lsString(R.string.sec_geometry_sensitivity),
                subtitle = lsString(R.string.secd_span_thickness_offsets_idle_gl),
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
                            text = if (isLandscape) lsString(R.string.sens_cal_land) else lsString(R.string.sens_cal_port),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isLandscape) {
                                lsString(R.string.sens_cal_land_desc)
                            } else {
                                lsString(R.string.sens_cal_port_desc)
                            },
                            fontSize = 10.5.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
                PrefDottedSliderRow(context, prefs, spanKey, "", lsString(R.string.sld_span_full), 50, 1080, 10, defaultSpan)
                PrefDottedSliderRow(context, prefs, thicknessKey, "", lsString(R.string.sld_thickness), 10, 52, 2, defaultThickness)
                PrefDottedSliderRow(context, prefs, offsetXKey, "", lsString(R.string.sld_offset_x), -300, 300, 5, defaultOffsetX)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_OFFSET_Y, "", lsString(R.string.sld_offset_y), -100, 200, 5, 0)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_SENSITIVITY, "", lsString(R.string.sld_touch_sens), 10, 100, 5, 25)
                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_STATUSBAR_TRANSPARENCY, "", lsString(R.string.sld_stealth_glow), 0, 100, 5, 0)
            }

            // 2. Gestures
            CollapsibleSubSection(
                title = lsString(R.string.sec_gestures_macros),
                subtitle = lsString(R.string.secd_tap_double_tap_left_right_swip),
                isExpanded = isStatusBarGesturesExpanded,
                onToggle = onToggleStatusBarGestures
            ) {
                val statusVectors = listOf(
                    "TAP" to (lsString(R.string.gest_tap) to ArrowDirection.TAP),
                    "DOUBLE_TAP" to (lsString(R.string.gest_double_tap) to ArrowDirection.DOUBLE_TAP),
                    "SWIPE_LEFT" to (lsString(R.string.gest_swipe_left) to ArrowDirection.SWIPE_LEFT),
                    "SWIPE_RIGHT" to (lsString(R.string.gest_swipe_right) to ArrowDirection.SWIPE_RIGHT),
                    "SWIPE_LEFT_BACK" to (lsString(R.string.gest_rebound_left) to ArrowDirection.LEFT_BACK),
                    "SWIPE_RIGHT_BACK" to (lsString(R.string.gest_rebound_right) to ArrowDirection.RIGHT_BACK)
                )

                statusVectors.forEach { (vectorKey, pairInfo) ->
                    val (vectorTitle, arrowEnum) = pairInfo
                    GestureMappingRow(context, prefs, arrowEnum, false, "pref_macro_action_STATUSBAR_${vectorKey}", vectorTitle, dynamicActionTokens, tokenLabelCache)
                    GestureMappingRow(context, prefs, arrowEnum, true, "pref_macro_action_STATUSBAR_${vectorKey}_HOLD", lsString(R.string.gest_hold_modifier, vectorTitle), dynamicActionTokens, tokenLabelCache)
                }
            }

            // 3. HUD Strip Swipe-Down to Notifications
            var isSwipeDownExpanded by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_SWIPE_DOWN_STATUSBAR, false))
            }
            CollapsibleSubSection(
                title = lsString(R.string.sec_hud_strip_swipe_down_to_notifi),
                subtitle = lsString(R.string.secd_dual_action_right_side_pull_fo),
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
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_SWIPE_DOWN_STATUSBAR, isSwipeDownExpanded).apply()
                }
            ) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_STATUSBAR_SWIPE_DOWN_NOTIFICATIONS,
                    defaultVal = false,
                    title = lsString(R.string.sens_swipe_down_title),
                    subtitle = lsString(R.string.sens_swipe_down_sub),
                    onChanged = {
                        safeReloadPreferences()
                        onRefreshNeeded()
                    }
                )
            }

            // 4. Bottom Sensor Area
            var isBottomSensorExpanded by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_EXPANDED, false))
            }
            var isBottomGeoExpanded by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_GEO, false))
            }
            var isBottomGesturesExpanded by rememberSaveable {
                mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_GESTURES, false))
            }

            val bottomSpanKey = if (isLandscape) LightspeedPreferences.KEY_BOTTOM_STATUSBAR_SPAN_LANDSCAPE else LightspeedPreferences.KEY_BOTTOM_STATUSBAR_SPAN
            val defaultBottomSpan = if (isLandscape) 320 else 200
            val bottomThicknessKey = if (isLandscape) LightspeedPreferences.KEY_BOTTOM_STATUSBAR_THICKNESS_LANDSCAPE else LightspeedPreferences.KEY_BOTTOM_STATUSBAR_THICKNESS
            val defaultBottomThickness = 35
            val bottomOffsetXKey = if (isLandscape) LightspeedPreferences.KEY_BOTTOM_STATUSBAR_OFFSET_X_LANDSCAPE else LightspeedPreferences.KEY_BOTTOM_STATUSBAR_OFFSET_X
            val defaultBottomOffsetX = 0

            CollapsibleSubSection(
                title = lsString(R.string.sec_bottom_sensor_area),
                subtitle = lsString(R.string.secd_bottom_edge_sensor_zone_for_ta),
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.TouchApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                isExpanded = isBottomSensorExpanded,
                onToggle = {
                    isBottomSensorExpanded = !isBottomSensorExpanded
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_EXPANDED, isBottomSensorExpanded).apply()
                    safeReloadPreferences()
                    onRefreshNeeded()
                }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrefToggleRow(
                        prefs = prefs,
                        prefKey = LightspeedPreferences.KEY_BOTTOM_STATUSBAR_ENABLED,
                        defaultVal = false,
                        title = lsString(R.string.sens_bot_enable_title),
                        subtitle = lsString(R.string.sens_bot_enable_sub),
                        onChanged = {
                            safeReloadPreferences()
                            onRefreshNeeded()
                        }
                    )

                    // Sub: Bottom Geometry & Sensitivity
                    CollapsibleSubSection(
                        title = lsString(R.string.sec_geometry_sensitivity),
                        subtitle = lsString(R.string.secd_span_thickness_horizontal_vert),
                        isExpanded = isBottomGeoExpanded,
                        onToggle = {
                            isBottomGeoExpanded = !isBottomGeoExpanded
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_GEO, isBottomGeoExpanded).apply()
                            safeReloadPreferences()
                            onRefreshNeeded()
                        }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isLandscape) lsString(R.string.sens_bot_cal_land) else lsString(R.string.sens_bot_cal_port),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isLandscape) {
                                        lsString(R.string.sens_bot_cal_land_desc)
                                    } else {
                                        lsString(R.string.sens_bot_cal_port_desc)
                                    },
                                    fontSize = 10.5.sp,
                                    color = lsColors.textPrimary.copy(alpha = 0.8f)
                                )
                            }
                        }
                        PrefDottedSliderRow(context, prefs, bottomSpanKey, "", lsString(R.string.sld_span_full), 50, 1080, 10, defaultBottomSpan)
                        PrefDottedSliderRow(context, prefs, bottomThicknessKey, "", lsString(R.string.sld_thickness), 10, 52, 2, defaultBottomThickness)
                        PrefDottedSliderRow(context, prefs, bottomOffsetXKey, "", lsString(R.string.sld_offset_x), -300, 300, 5, defaultBottomOffsetX)
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_BOTTOM_STATUSBAR_OFFSET_Y, "", lsString(R.string.sld_offset_y_bottom), -100, 200, 5, 0)
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_BOTTOM_STATUSBAR_SENSITIVITY, "", lsString(R.string.sld_touch_sens), 10, 100, 5, 25)
                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_BOTTOM_STATUSBAR_TRANSPARENCY, "", lsString(R.string.sld_stealth_glow), 0, 100, 5, 0)
                    }

                    // Sub: Bottom Gestures & Macros
                    CollapsibleSubSection(
                        title = lsString(R.string.sec_gestures_macros),
                        subtitle = lsString(R.string.secd_tap_double_tap_swipe_left_righ),
                        isExpanded = isBottomGesturesExpanded,
                        onToggle = {
                            isBottomGesturesExpanded = !isBottomGesturesExpanded
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_SUB_BOTTOM_STATUSBAR_GESTURES, isBottomGesturesExpanded).apply()
                        }
                    ) {
                        val bottomVectors = listOf(
                            "TAP" to (lsString(R.string.gest_tap) to ArrowDirection.TAP),
                            "DOUBLE_TAP" to (lsString(R.string.gest_double_tap) to ArrowDirection.DOUBLE_TAP),
                            "SWIPE_UP" to (lsString(R.string.gest_swipe_up) to ArrowDirection.SWIPE_UP),
                            "SWIPE_LEFT" to (lsString(R.string.gest_swipe_left) to ArrowDirection.SWIPE_LEFT),
                            "SWIPE_RIGHT" to (lsString(R.string.gest_swipe_right) to ArrowDirection.SWIPE_RIGHT),
                            "SWIPE_LEFT_BACK" to (lsString(R.string.gest_rebound_left) to ArrowDirection.LEFT_BACK),
                            "SWIPE_RIGHT_BACK" to (lsString(R.string.gest_rebound_right) to ArrowDirection.RIGHT_BACK)
                        )

                        bottomVectors.forEach { (vectorKey, pairInfo) ->
                            val (vectorTitle, arrowEnum) = pairInfo
                            GestureMappingRow(
                                context = context,
                                prefs = prefs,
                                direction = arrowEnum,
                                isHold = false,
                                keyResName = "pref_macro_action_BOTTOM_STATUSBAR_${vectorKey}",
                                defaultTitle = vectorTitle,
                                options = dynamicActionTokens,
                                labelCache = tokenLabelCache,
                                showBottomSensorQuickAccess = true
                            )
                            GestureMappingRow(
                                context = context,
                                prefs = prefs,
                                direction = arrowEnum,
                                isHold = true,
                                keyResName = "pref_macro_action_BOTTOM_STATUSBAR_${vectorKey}_HOLD",
                                defaultTitle = lsString(R.string.gest_hold_modifier, vectorTitle),
                                options = dynamicActionTokens,
                                labelCache = tokenLabelCache,
                                showBottomSensorQuickAccess = true
                            )
                        }
                    }
                }
            }
        }
    }
}
