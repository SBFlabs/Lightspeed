package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary

@Composable
fun DeflectorFlankSection(
    isLeft: Boolean,
    isUpper: Boolean,
    context: Context,
    prefs: SharedPreferences,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subBlueprintTarget: String?,
    onSubBlueprintToggle: () -> Unit,
    isGeoExpanded: Boolean,
    onGeoToggle: () -> Unit,
    isScrubExpanded: Boolean,
    onScrubToggle: () -> Unit,
    isGesturesExpanded: Boolean,
    onGesturesToggle: () -> Unit,
    customVectors: List<Pair<String, Pair<String, ArrowDirection>>>,
    onRefreshNeeded: () -> Unit
) {
    val zone = if (isUpper) "top" else "bottom"
    val zoneUpper = if (isUpper) "TOP" else "BOTTOM"
    val macroPrefix = if (isLeft) "LEFT_" else ""
    val sidebarPrefix = if (isLeft) "pref_sidebar_left_" else "pref_sidebar_"
    val secKey = if (isLeft) "left_$zone" else zone
    val subOrderKey = if (isLeft) "pref_sub_order_left_$zone" else "pref_sub_order_$zone"
    val subPinnedKey = if (isLeft) "pref_sub_pinned_left_$zone" else "pref_sub_pinned_$zone"

    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(
            if (isUpper) LightspeedVocabulary.Key.UPPER_FLANK
            else LightspeedVocabulary.Key.LOWER_FLANK
        ),
        icon = {
            Icon(
                imageVector = if (isUpper) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle,
        onLongToggle = onSubBlueprintToggle
    ) {
        var subOrderStr by remember { mutableStateOf(prefs.getString(subOrderKey, "geo,scrub,gestures")!!) }
        var pinnedSection by remember { mutableStateOf(prefs.getString(subPinnedKey, "geo")) }
        val defaultSubOrder = listOf("geo", "scrub", "gestures")
        val currentSubOrder = subOrderStr.split(",").map { it.trim() }.filter { it in defaultSubOrder }.distinct().let { list ->
            list + (defaultSubOrder - list.toSet())
        }

        if (subBlueprintTarget == secKey) {
            BlueprintWireframeView(
                tabTitle = if (isUpper) "Upper Deflector Zone" else "Lower Deflector Zone",
                sectionIds = currentSubOrder,
                pinnedSectionId = pinnedSection,
                sectionTitles = mapOf(
                    "geo" to "Sensor Geometry",
                    "scrub" to "Inward Scrubbing Control",
                    "gestures" to "Gesture Actions & Macro Mappings"
                ),
                onMoveUp = { idx ->
                    if (idx > 0) {
                        val mutable = currentSubOrder.toMutableList()
                        val item = mutable.removeAt(idx)
                        mutable.add(idx - 1, item)
                        val newStr = mutable.joinToString(",")
                        subOrderStr = newStr
                        prefs.edit().putString(subOrderKey, newStr).apply()
                    }
                },
                onMoveDown = { idx ->
                    if (idx < currentSubOrder.size - 1) {
                        val mutable = currentSubOrder.toMutableList()
                        val item = mutable.removeAt(idx)
                        mutable.add(idx + 1, item)
                        val newStr = mutable.joinToString(",")
                        subOrderStr = newStr
                        prefs.edit().putString(subOrderKey, newStr).apply()
                    }
                },
                onPinSection = { id ->
                    pinnedSection = id
                    prefs.edit().putString(subPinnedKey, id).apply()
                },
                onExitBlueprint = onSubBlueprintToggle
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                currentSubOrder.forEach { subKey ->
                    when (subKey) {
                        "geo" -> {
                            CollapsibleSubSection(
                                title = "Sensor Geometry",
                                subtitle = if (isUpper) "Upper deflector span, touch reach & stealth glow" else "Lower deflector span, touch reach & stealth glow",
                                isExpanded = isGeoExpanded,
                                onToggle = onGeoToggle
                            ) {
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                val landscapeMode = prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
                                val isCustomLandscape = landscapeMode == com.sbf.lightspeed.system.LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
                                val defaultHeight = if (isUpper) 220 else 250

                                if (isLandscape && isCustomLandscape) {
                                    val defaultLandscapeH = (prefs.getInt("${sidebarPrefix}${zone}_height", defaultHeight) * 0.45f).toInt().coerceAtLeast(30)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_height_landscape", "", "Deflector Span (Height) [Landscape]", 30, 600, 10, defaultLandscapeH)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_touch_width", "", "Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                } else {
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_height", "", "Deflector Span (Height)", 50, 600, 10, defaultHeight)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_touch_width", "", "Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}${zone}_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                }
                            }
                        }
                        "scrub" -> {
                            CollapsibleSubSection(
                                title = "Inward Scrubbing Control",
                                subtitle = if (isUpper) "Upper vector inward sweep scrubber" else "Lower vector inward sweep scrubber",
                                isExpanded = isScrubExpanded,
                                onToggle = onScrubToggle
                            ) {
                                GestureMappingRow(
                                    context,
                                    prefs,
                                    ArrowDirection.SCRUB,
                                    false,
                                    "pref_macro_action_${macroPrefix}${zoneUpper}_SCRUBBING",
                                    "Extended Inward Sweep (Scrubbing)",
                                    listOf("none", "system:volume", "system:brightness", "system:screen_timeout"),
                                    tokenLabelCache
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, "", "Long Swipe Distance Cutoff [Portrait]", 40, 240, 5, 90)
                                Spacer(modifier = Modifier.height(4.dp))
                                PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, "", "Long Swipe Distance Cutoff [Landscape]", 40, 320, 10, 130)
                            }
                        }
                        "gestures" -> {
                            CollapsibleSubSection(
                                title = "Gesture Actions & Macro Mappings",
                                subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                isExpanded = isGesturesExpanded,
                                onToggle = onGesturesToggle
                            ) {
                                customVectors.forEach { (vectorKey, pairInfo) ->
                                    val (vectorTitle, arrowEnum) = pairInfo
                                    GestureMappingRow(
                                        context,
                                        prefs,
                                        arrowEnum,
                                        false,
                                        "pref_macro_action_${macroPrefix}${zoneUpper}_${vectorKey}",
                                        vectorTitle,
                                        dynamicActionTokens,
                                        tokenLabelCache
                                    )
                                    GestureMappingRow(
                                        context,
                                        prefs,
                                        arrowEnum,
                                        true,
                                        "pref_macro_action_${macroPrefix}${zoneUpper}_${vectorKey}_HOLD",
                                        "$vectorTitle + Hold Modifier",
                                        dynamicActionTokens,
                                        tokenLabelCache
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
