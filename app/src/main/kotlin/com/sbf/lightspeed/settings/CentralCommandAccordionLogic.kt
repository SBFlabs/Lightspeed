package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import com.sbf.lightspeed.system.LightspeedPreferences

internal fun matchesAccordionPin(pinned: String?, secId: String): Boolean =
    pinned == secId || (pinned != null && pinned.startsWith("${secId}_"))

private fun tabParentEntries(
    tabId: Int,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState,
    hudStrip: HudStripAccordionState
): List<Triple<String, MutableState<Boolean>, String>> = when (tabId) {
    0 -> listOf(
        Triple("left_center", leftDeflector.isCenterExpandedState, LightspeedPreferences.KEY_SECTION_LEFT_CENTER_EXPANDED),
        Triple("left_top", leftDeflector.isTopExpandedState, LightspeedPreferences.KEY_SECTION_LEFT_TOP_EXPANDED),
        Triple("left_bottom", leftDeflector.isBottomExpandedState, LightspeedPreferences.KEY_SECTION_LEFT_BOTTOM_EXPANDED),
        Triple("left_unified", leftDeflector.isUnifiedExpandedState, LightspeedPreferences.KEY_SECTION_LEFT_UNIFIED_EXPANDED),
        Triple("left_trackpad", leftDeflector.isTrackpadExpandedState, "pref_accordion_trackpad_left")
    )
    1 -> listOf(
        Triple("sensor_deck", hudStrip.isSensorDeckExpandedState, LightspeedPreferences.KEY_SECTION_SENSOR_DECK_EXPANDED),
        Triple("telemetry_indicators", hudStrip.isTelemetryExpandedState, LightspeedPreferences.KEY_SECTION_TELEMETRY_EXPANDED),
        Triple("astrogation_airlock", hudStrip.isAstrogationAirlockExpandedState, LightspeedPreferences.KEY_SECTION_ASTROGATION_AIRLOCK_EXPANDED),
        Triple("tactical_hardware", hudStrip.isTacticalHardwareExpandedState, LightspeedPreferences.KEY_SECTION_TACTICAL_HARDWARE_EXPANDED),
        Triple("synthetic_gravity", hudStrip.isSyntheticGravityExpandedState, LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED),
        Triple("refueling_bay", hudStrip.isRefuelingExpandedState, LightspeedPreferences.KEY_SECTION_REFUELING_EXPANDED),
        Triple("system_overrides", hudStrip.isSystemOverridesExpandedState, LightspeedPreferences.KEY_SECTION_SYSTEM_OVERRIDES_EXPANDED),
        Triple("config_vault", hudStrip.isConfigVaultExpandedState, LightspeedPreferences.KEY_SECTION_BACKUP_EXPANDED),
        Triple("sonic_deck", hudStrip.isSonicDeckExpandedState, LightspeedPreferences.KEY_SECTION_SONIC_DECK_EXPANDED),
        Triple("vista_deck", hudStrip.isVistaDeckExpandedState, LightspeedPreferences.KEY_SECTION_VISTA_DECK_EXPANDED),
        Triple("experimental_labs", hudStrip.isExperimentalLabsExpandedState, LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED)
    )
    2 -> listOf(
        Triple("center", rightDeflector.isCenterExpandedState, LightspeedPreferences.KEY_SECTION_CENTER_EXPANDED),
        Triple("top", rightDeflector.isTopExpandedState, LightspeedPreferences.KEY_SECTION_TOP_EXPANDED),
        Triple("bottom", rightDeflector.isBottomExpandedState, LightspeedPreferences.KEY_SECTION_BOTTOM_EXPANDED),
        Triple("unified", rightDeflector.isUnifiedExpandedState, LightspeedPreferences.KEY_SECTION_RIGHT_UNIFIED_EXPANDED),
        Triple("trackpad", rightDeflector.isTrackpadExpandedState, "pref_accordion_trackpad_right")
    )
    else -> emptyList()
}

private fun deflectorSubEntries(
    parentSecId: String,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState
): List<Triple<String, MutableState<Boolean>, String>> = when (parentSecId) {
    "left_center" -> listOf(
        Triple("geo", leftDeflector.isCenterGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_LEFT_CENTER)
    )
    "left_unified" -> listOf(
        Triple("geo", leftDeflector.isUnifiedGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_LEFT_UNIFIED),
        Triple("scrub", leftDeflector.isUnifiedScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_LEFT_UNIFIED),
        Triple("gestures", leftDeflector.isUnifiedGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_LEFT_UNIFIED)
    )
    "left_top" -> listOf(
        Triple("geo", leftDeflector.isTopGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_LEFT_TOP),
        Triple("scrub", leftDeflector.isTopScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_LEFT_TOP),
        Triple("gestures", leftDeflector.isTopGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_LEFT_TOP)
    )
    "left_bottom" -> listOf(
        Triple("geo", leftDeflector.isBottomGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_LEFT_BOTTOM),
        Triple("scrub", leftDeflector.isBottomScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_LEFT_BOTTOM),
        Triple("gestures", leftDeflector.isBottomGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_LEFT_BOTTOM)
    )
    "left_trackpad" -> listOf(
        Triple("geo", leftDeflector.isTrackpadGeoExpandedState, "pref_sub_trackpad_geo_left"),
        Triple("physics", leftDeflector.isTrackpadPhysicsExpandedState, "pref_sub_trackpad_physics_left")
    )
    "center" -> listOf(
        Triple("geo", rightDeflector.isCenterGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_CENTER)
    )
    "unified" -> listOf(
        Triple("geo", rightDeflector.isUnifiedGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_RIGHT_UNIFIED),
        Triple("scrub", rightDeflector.isUnifiedScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_RIGHT_UNIFIED),
        Triple("gestures", rightDeflector.isUnifiedGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_RIGHT_UNIFIED)
    )
    "top" -> listOf(
        Triple("geo", rightDeflector.isTopGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_TOP),
        Triple("scrub", rightDeflector.isTopScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_TOP),
        Triple("gestures", rightDeflector.isTopGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_TOP)
    )
    "bottom" -> listOf(
        Triple("geo", rightDeflector.isBottomGeoExpandedState, LightspeedPreferences.KEY_SUB_GEO_BOTTOM),
        Triple("scrub", rightDeflector.isBottomScrubExpandedState, LightspeedPreferences.KEY_SUB_SCRUB_BOTTOM),
        Triple("gestures", rightDeflector.isBottomGesturesExpandedState, LightspeedPreferences.KEY_SUB_GESTURES_BOTTOM)
    )
    "trackpad" -> listOf(
        Triple("geo", rightDeflector.isTrackpadGeoExpandedState, "pref_sub_trackpad_geo_right"),
        Triple("physics", rightDeflector.isTrackpadPhysicsExpandedState, "pref_sub_trackpad_physics_right")
    )
    else -> emptyList()
}

private fun collapseSiblingParents(
    tabId: Int,
    keepParentId: String,
    pinned: String?,
    onlyIfUnpinned: Boolean,
    prefs: SharedPreferences,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState,
    hudStrip: HudStripAccordionState
) {
    val entries = tabParentEntries(tabId, leftDeflector, rightDeflector, hudStrip)
    if (entries.isEmpty()) return
    val ed = prefs.edit()
    var anyChanged = false
    for ((id, state, prefKey) in entries) {
        if (id == keepParentId) continue
        if (onlyIfUnpinned && matchesAccordionPin(pinned, id)) continue
        if (state.value || prefs.getBoolean(prefKey, false)) {
            state.value = false
            ed.putBoolean(prefKey, false)
            anyChanged = true
        }
    }
    if (anyChanged) ed.apply()
}

@Composable
fun rememberToggleSection(
    prefs: SharedPreferences,
    nav: CentralCommandNavState,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState,
    hudStrip: HudStripAccordionState
): (Int, String, Boolean, (Boolean) -> Unit) -> Unit = remember {
    { tabId, sectionId, currentExpanded, onSetExpanded ->
        val mode = when (tabId) {
            0 -> nav.tabMode0State.value
            1 -> nav.tabMode1State.value
            2 -> nav.tabMode2State.value
            else -> "custom_pinned"
        }
        val pinned = when (tabId) {
            0 -> nav.pinnedSection0State.value
            1 -> nav.pinnedSection1State.value
            2 -> nav.pinnedSection2State.value
            else -> null
        }

        if (currentExpanded) {
            onSetExpanded(false)
        } else {
            when (mode) {
                "solo" -> collapseSiblingParents(
                    tabId = tabId,
                    keepParentId = sectionId,
                    pinned = pinned,
                    onlyIfUnpinned = false,
                    prefs = prefs,
                    leftDeflector = leftDeflector,
                    rightDeflector = rightDeflector,
                    hudStrip = hudStrip
                )
                "custom_pinned" -> collapseSiblingParents(
                    tabId = tabId,
                    keepParentId = sectionId,
                    pinned = pinned,
                    onlyIfUnpinned = true,
                    prefs = prefs,
                    leftDeflector = leftDeflector,
                    rightDeflector = rightDeflector,
                    hudStrip = hudStrip
                )
            }
            onSetExpanded(true)
        }
    }
}

@Composable
fun rememberToggleSubSection(
    prefs: SharedPreferences,
    nav: CentralCommandNavState,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState,
    hudStrip: HudStripAccordionState
): (Int, String, String, Boolean, (Boolean) -> Unit) -> Unit = remember {
    { tabId, parentSecId, subId, currentSubExpanded, onSetSubExpanded ->
        val mode = when (tabId) {
            0 -> nav.tabMode0State.value
            1 -> nav.tabMode1State.value
            2 -> nav.tabMode2State.value
            else -> "custom_pinned"
        }
        val pinned = when (tabId) {
            0 -> nav.pinnedSection0State.value
            1 -> nav.pinnedSection1State.value
            2 -> nav.pinnedSection2State.value
            else -> null
        }

        val subEntries = deflectorSubEntries(parentSecId, leftDeflector, rightDeflector)
        val selfPrefKey = subEntries.firstOrNull { it.first == subId }?.third

        if (currentSubExpanded) {
            onSetSubExpanded(false)
            if (selfPrefKey != null) {
                prefs.edit().putBoolean(selfPrefKey, false).apply()
            }
        } else {
            if (mode == "solo" || mode == "custom_pinned") {
                val onlyUnpinned = mode == "custom_pinned"
                collapseSiblingParents(
                    tabId = tabId,
                    keepParentId = parentSecId,
                    pinned = pinned,
                    onlyIfUnpinned = onlyUnpinned,
                    prefs = prefs,
                    leftDeflector = leftDeflector,
                    rightDeflector = rightDeflector,
                    hudStrip = hudStrip
                )
                val ed = prefs.edit()
                for ((otherSub, otherState, otherPrefKey) in subEntries) {
                    if (otherSub == subId) continue
                    val fullOtherId = "${parentSecId}_$otherSub"
                    if (onlyUnpinned && pinned == fullOtherId) continue
                    if (otherState.value || prefs.getBoolean(otherPrefKey, false)) {
                        otherState.value = false
                        ed.putBoolean(otherPrefKey, false)
                    }
                }
                if (selfPrefKey != null) {
                    ed.putBoolean(selfPrefKey, true)
                }
                ed.apply()
            } else if (selfPrefKey != null) {
                prefs.edit().putBoolean(selfPrefKey, true).apply()
            }
            onSetSubExpanded(true)
        }
    }
}

@Composable
fun rememberApplyTabMode(
    prefs: SharedPreferences,
    nav: CentralCommandNavState,
    leftDeflector: DeflectorAccordionState,
    rightDeflector: DeflectorAccordionState,
    hudStrip: HudStripAccordionState
): (Int, String) -> Unit = remember {
    { tabId, newMode ->
        when (tabId) {
            0 -> {
                nav.tabMode0State.value = newMode
                val pinned = nav.pinnedSection0State.value
                prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_0, newMode).apply()
                val ed = prefs.edit()
                when (newMode) {
                    "all_expanded" -> {
                        leftDeflector.isCenterExpandedState.value = true
                        leftDeflector.isTopExpandedState.value = true
                        leftDeflector.isBottomExpandedState.value = true
                        leftDeflector.isUnifiedExpandedState.value = true
                        leftDeflector.isTrackpadExpandedState.value = true
                    }
                    "all_collapsed" -> {
                        leftDeflector.isCenterExpandedState.value = false
                        leftDeflector.isTopExpandedState.value = false
                        leftDeflector.isBottomExpandedState.value = false
                        leftDeflector.isUnifiedExpandedState.value = false
                        leftDeflector.isTrackpadExpandedState.value = false
                    }
                    "custom_pinned" -> {
                        leftDeflector.isCenterExpandedState.value = matchesAccordionPin(pinned, "left_center")
                        leftDeflector.isTopExpandedState.value = matchesAccordionPin(pinned, "left_top")
                        leftDeflector.isBottomExpandedState.value = matchesAccordionPin(pinned, "left_bottom")
                        leftDeflector.isUnifiedExpandedState.value = matchesAccordionPin(pinned, "left_unified")
                        leftDeflector.isTrackpadExpandedState.value = matchesAccordionPin(pinned, "left_trackpad")
                        if (pinned.contains("_")) {
                            val uGeo = pinned == "left_unified_geo"
                            val uScrub = pinned == "left_unified_scrub"
                            val uGest = pinned == "left_unified_gestures"
                            val tGeo = pinned == "left_top_geo"
                            val tScrub = pinned == "left_top_scrub"
                            val tGest = pinned == "left_top_gestures"
                            val bGeo = pinned == "left_bottom_geo"
                            val bScrub = pinned == "left_bottom_scrub"
                            val bGest = pinned == "left_bottom_gestures"
                            val tpGeo = pinned == "left_trackpad_geo"
                            val tpPhys = pinned == "left_trackpad_physics"
                            if (pinned.startsWith("left_unified_")) {
                                leftDeflector.isUnifiedGeoExpandedState.value = uGeo
                                leftDeflector.isUnifiedScrubExpandedState.value = uScrub
                                leftDeflector.isUnifiedGesturesExpandedState.value = uGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_LEFT_UNIFIED, uGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_LEFT_UNIFIED, uScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_LEFT_UNIFIED, uGest)
                            } else if (pinned.startsWith("left_top_")) {
                                leftDeflector.isTopGeoExpandedState.value = tGeo
                                leftDeflector.isTopScrubExpandedState.value = tScrub
                                leftDeflector.isTopGesturesExpandedState.value = tGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_LEFT_TOP, tGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_LEFT_TOP, tScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_LEFT_TOP, tGest)
                            } else if (pinned.startsWith("left_bottom_")) {
                                leftDeflector.isBottomGeoExpandedState.value = bGeo
                                leftDeflector.isBottomScrubExpandedState.value = bScrub
                                leftDeflector.isBottomGesturesExpandedState.value = bGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_LEFT_BOTTOM, bGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_LEFT_BOTTOM, bScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_LEFT_BOTTOM, bGest)
                            } else if (pinned.startsWith("left_trackpad_")) {
                                leftDeflector.isTrackpadGeoExpandedState.value = tpGeo
                                leftDeflector.isTrackpadPhysicsExpandedState.value = tpPhys
                                ed.putBoolean("pref_sub_trackpad_geo_left", tpGeo)
                                    .putBoolean("pref_sub_trackpad_physics_left", tpPhys)
                            }
                        }
                    }
                    "solo" -> {
                        val states = listOf(
                            leftDeflector.isCenterExpandedState.value,
                            leftDeflector.isUnifiedExpandedState.value,
                            leftDeflector.isTopExpandedState.value,
                            leftDeflector.isBottomExpandedState.value,
                            leftDeflector.isTrackpadExpandedState.value
                        )
                        val firstIdx = states.indexOfFirst { it }.let { if (it >= 0) it else 0 }
                        leftDeflector.isCenterExpandedState.value = firstIdx == 0
                        leftDeflector.isUnifiedExpandedState.value = firstIdx == 1
                        leftDeflector.isTopExpandedState.value = firstIdx == 2
                        leftDeflector.isBottomExpandedState.value = firstIdx == 3
                        leftDeflector.isTrackpadExpandedState.value = firstIdx == 4
                    }
                }
                ed.putBoolean(LightspeedPreferences.KEY_SECTION_LEFT_CENTER_EXPANDED, leftDeflector.isCenterExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_LEFT_TOP_EXPANDED, leftDeflector.isTopExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_LEFT_BOTTOM_EXPANDED, leftDeflector.isBottomExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_LEFT_UNIFIED_EXPANDED, leftDeflector.isUnifiedExpandedState.value)
                    .putBoolean("pref_accordion_trackpad_left", leftDeflector.isTrackpadExpandedState.value)
                    .apply()
            }
            1 -> {
                nav.tabMode1State.value = newMode
                val pinned = nav.pinnedSection1State.value
                prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_1, newMode).apply()
                val ed = prefs.edit()
                when (newMode) {
                    "all_expanded" -> {
                        hudStrip.isSensorDeckExpandedState.value = true
                        hudStrip.isTelemetryExpandedState.value = true
                        hudStrip.isTacticalHardwareExpandedState.value = true
                        hudStrip.isSyntheticGravityExpandedState.value = true
                        hudStrip.isAstrogationAirlockExpandedState.value = true
                        hudStrip.isSonicDeckExpandedState.value = true
                        hudStrip.isVistaDeckExpandedState.value = true
                        hudStrip.isRefuelingExpandedState.value = true
                        hudStrip.isSystemOverridesExpandedState.value = true
                        hudStrip.isConfigVaultExpandedState.value = true
                        hudStrip.isExperimentalLabsExpandedState.value = true
                    }
                    "all_collapsed" -> {
                        hudStrip.isSensorDeckExpandedState.value = false
                        hudStrip.isTelemetryExpandedState.value = false
                        hudStrip.isTacticalHardwareExpandedState.value = false
                        hudStrip.isSyntheticGravityExpandedState.value = false
                        hudStrip.isAstrogationAirlockExpandedState.value = false
                        hudStrip.isSonicDeckExpandedState.value = false
                        hudStrip.isVistaDeckExpandedState.value = false
                        hudStrip.isRefuelingExpandedState.value = false
                        hudStrip.isSystemOverridesExpandedState.value = false
                        hudStrip.isConfigVaultExpandedState.value = false
                        hudStrip.isExperimentalLabsExpandedState.value = false
                    }
                    "custom_pinned" -> {
                        hudStrip.isSensorDeckExpandedState.value = pinned == "sensor_deck"
                        hudStrip.isTelemetryExpandedState.value = pinned == "telemetry_indicators"
                        hudStrip.isTacticalHardwareExpandedState.value = pinned == "tactical_hardware"
                        hudStrip.isSyntheticGravityExpandedState.value = pinned == "synthetic_gravity"
                        hudStrip.isAstrogationAirlockExpandedState.value = pinned == "astrogation_airlock"
                        hudStrip.isSonicDeckExpandedState.value = pinned == "sonic_deck"
                        hudStrip.isVistaDeckExpandedState.value = pinned == "vista_deck"
                        hudStrip.isRefuelingExpandedState.value = pinned == "refueling_bay"
                        hudStrip.isSystemOverridesExpandedState.value = pinned == "system_overrides"
                        hudStrip.isConfigVaultExpandedState.value = pinned == "config_vault"
                        hudStrip.isExperimentalLabsExpandedState.value = pinned == "experimental_labs"
                    }
                    "solo" -> {
                        val states = listOf(
                            hudStrip.isSensorDeckExpandedState.value,
                            hudStrip.isTelemetryExpandedState.value,
                            hudStrip.isAstrogationAirlockExpandedState.value,
                            hudStrip.isTacticalHardwareExpandedState.value,
                            hudStrip.isSyntheticGravityExpandedState.value,
                            hudStrip.isRefuelingExpandedState.value,
                            hudStrip.isSystemOverridesExpandedState.value,
                            hudStrip.isConfigVaultExpandedState.value,
                            hudStrip.isSonicDeckExpandedState.value,
                            hudStrip.isVistaDeckExpandedState.value,
                            hudStrip.isExperimentalLabsExpandedState.value
                        )
                        val firstIdx = states.indexOfFirst { it }.let { if (it >= 0) it else 0 }
                        hudStrip.isSensorDeckExpandedState.value = firstIdx == 0
                        hudStrip.isTelemetryExpandedState.value = firstIdx == 1
                        hudStrip.isAstrogationAirlockExpandedState.value = firstIdx == 2
                        hudStrip.isTacticalHardwareExpandedState.value = firstIdx == 3
                        hudStrip.isSyntheticGravityExpandedState.value = firstIdx == 4
                        hudStrip.isRefuelingExpandedState.value = firstIdx == 5
                        hudStrip.isSystemOverridesExpandedState.value = firstIdx == 6
                        hudStrip.isConfigVaultExpandedState.value = firstIdx == 7
                        hudStrip.isSonicDeckExpandedState.value = firstIdx == 8
                        hudStrip.isVistaDeckExpandedState.value = firstIdx == 9
                        hudStrip.isExperimentalLabsExpandedState.value = firstIdx == 10
                    }
                }
                ed.putBoolean(LightspeedPreferences.KEY_SECTION_SENSOR_DECK_EXPANDED, hudStrip.isSensorDeckExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_TELEMETRY_EXPANDED, hudStrip.isTelemetryExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_TACTICAL_HARDWARE_EXPANDED, hudStrip.isTacticalHardwareExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, hudStrip.isSyntheticGravityExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_ASTROGATION_AIRLOCK_EXPANDED, hudStrip.isAstrogationAirlockExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_SONIC_DECK_EXPANDED, hudStrip.isSonicDeckExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_VISTA_DECK_EXPANDED, hudStrip.isVistaDeckExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_REFUELING_EXPANDED, hudStrip.isRefuelingExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_SYSTEM_OVERRIDES_EXPANDED, hudStrip.isSystemOverridesExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_BACKUP_EXPANDED, hudStrip.isConfigVaultExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, hudStrip.isExperimentalLabsExpandedState.value)
                    .apply()
            }
            2 -> {
                nav.tabMode2State.value = newMode
                val pinned = nav.pinnedSection2State.value
                prefs.edit().putString(LightspeedPreferences.KEY_TAB_ACCORDION_MODE_2, newMode).apply()
                val ed = prefs.edit()
                when (newMode) {
                    "all_expanded" -> {
                        rightDeflector.isCenterExpandedState.value = true
                        rightDeflector.isTopExpandedState.value = true
                        rightDeflector.isBottomExpandedState.value = true
                        rightDeflector.isUnifiedExpandedState.value = true
                        rightDeflector.isTrackpadExpandedState.value = true
                    }
                    "all_collapsed" -> {
                        rightDeflector.isCenterExpandedState.value = false
                        rightDeflector.isTopExpandedState.value = false
                        rightDeflector.isBottomExpandedState.value = false
                        rightDeflector.isUnifiedExpandedState.value = false
                        rightDeflector.isTrackpadExpandedState.value = false
                    }
                    "custom_pinned" -> {
                        rightDeflector.isCenterExpandedState.value = matchesAccordionPin(pinned, "center")
                        rightDeflector.isTopExpandedState.value = matchesAccordionPin(pinned, "top")
                        rightDeflector.isBottomExpandedState.value = matchesAccordionPin(pinned, "bottom")
                        rightDeflector.isUnifiedExpandedState.value = matchesAccordionPin(pinned, "unified")
                        rightDeflector.isTrackpadExpandedState.value = matchesAccordionPin(pinned, "trackpad")
                        if (pinned.contains("_")) {
                            val uGeo = pinned == "unified_geo"
                            val uScrub = pinned == "unified_scrub"
                            val uGest = pinned == "unified_gestures"
                            val tGeo = pinned == "top_geo"
                            val tScrub = pinned == "top_scrub"
                            val tGest = pinned == "top_gestures"
                            val bGeo = pinned == "bottom_geo"
                            val bScrub = pinned == "bottom_scrub"
                            val bGest = pinned == "bottom_gestures"
                            val tpGeo = pinned == "trackpad_geo"
                            val tpPhys = pinned == "trackpad_physics"
                            if (pinned.startsWith("unified_")) {
                                rightDeflector.isUnifiedGeoExpandedState.value = uGeo
                                rightDeflector.isUnifiedScrubExpandedState.value = uScrub
                                rightDeflector.isUnifiedGesturesExpandedState.value = uGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_RIGHT_UNIFIED, uGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_RIGHT_UNIFIED, uScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_RIGHT_UNIFIED, uGest)
                            } else if (pinned.startsWith("top_")) {
                                rightDeflector.isTopGeoExpandedState.value = tGeo
                                rightDeflector.isTopScrubExpandedState.value = tScrub
                                rightDeflector.isTopGesturesExpandedState.value = tGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_TOP, tGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_TOP, tScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_TOP, tGest)
                            } else if (pinned.startsWith("bottom_")) {
                                rightDeflector.isBottomGeoExpandedState.value = bGeo
                                rightDeflector.isBottomScrubExpandedState.value = bScrub
                                rightDeflector.isBottomGesturesExpandedState.value = bGest
                                ed.putBoolean(LightspeedPreferences.KEY_SUB_GEO_BOTTOM, bGeo)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_SCRUB_BOTTOM, bScrub)
                                    .putBoolean(LightspeedPreferences.KEY_SUB_GESTURES_BOTTOM, bGest)
                            } else if (pinned.startsWith("trackpad_")) {
                                rightDeflector.isTrackpadGeoExpandedState.value = tpGeo
                                rightDeflector.isTrackpadPhysicsExpandedState.value = tpPhys
                                ed.putBoolean("pref_sub_trackpad_geo_right", tpGeo)
                                    .putBoolean("pref_sub_trackpad_physics_right", tpPhys)
                            }
                        }
                    }
                    "solo" -> {
                        val states = listOf(
                            rightDeflector.isCenterExpandedState.value,
                            rightDeflector.isUnifiedExpandedState.value,
                            rightDeflector.isTopExpandedState.value,
                            rightDeflector.isBottomExpandedState.value,
                            rightDeflector.isTrackpadExpandedState.value
                        )
                        val firstIdx = states.indexOfFirst { it }.let { if (it >= 0) it else 0 }
                        rightDeflector.isCenterExpandedState.value = firstIdx == 0
                        rightDeflector.isUnifiedExpandedState.value = firstIdx == 1
                        rightDeflector.isTopExpandedState.value = firstIdx == 2
                        rightDeflector.isBottomExpandedState.value = firstIdx == 3
                        rightDeflector.isTrackpadExpandedState.value = firstIdx == 4
                    }
                }
                ed.putBoolean(LightspeedPreferences.KEY_SECTION_CENTER_EXPANDED, rightDeflector.isCenterExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_TOP_EXPANDED, rightDeflector.isTopExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_BOTTOM_EXPANDED, rightDeflector.isBottomExpandedState.value)
                    .putBoolean(LightspeedPreferences.KEY_SECTION_RIGHT_UNIFIED_EXPANDED, rightDeflector.isUnifiedExpandedState.value)
                    .putBoolean("pref_accordion_trackpad_right", rightDeflector.isTrackpadExpandedState.value)
                    .apply()
            }
        }
    }
}
