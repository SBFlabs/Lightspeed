package com.sbf.lightspeed

import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedPreferences

val ScrewdriverVector: ImageVector = ImageVector.Builder(
    name = "Screwdriver",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = PathParser().parsePathString(
        "M21.71,3.71L20.29,2.29C19.9,1.9 19.27,1.9 18.88,2.29L13.88,7.29L16.71,10.12L21.71,5.12C22.1,4.73 22.1,4.1 21.71,3.71M15.29,11.54L12.46,8.71L4.88,16.29L3.46,14.88L2.05,16.29L4.88,19.12L2.05,21.95L3.46,23.36L6.29,20.54L9.12,23.36L10.54,21.95L9.12,20.54L16.71,12.95L15.29,11.54Z"
    ).toNodes(),
    fill = SolidColor(Color.White)
).build()

sealed class PickerRowItem {
    abstract val key: String

    data class SystemHeader(val count: Int, val isExpanded: Boolean) : PickerRowItem() {
        override val key = "header_sys"
    }

    data class SystemCategoryHeader(
        val categoryKey: String,
        val title: String,
        val count: Int,
        val isExpanded: Boolean
    ) : PickerRowItem() {
        override val key = "sys_cat_$categoryKey"
    }

    data class SystemAction(
        val token: String,
        val label: String,
        val isCustomizable: Boolean = false,
        val isExpanded: Boolean = false
    ) : PickerRowItem() {
        override val key = "action_$token"
    }

    data class SystemCustomizationOption(
        val parentToken: String,
        val optionKey: String,
        val title: String,
        val subtitle: String,
        val isSelected: Boolean
    ) : PickerRowItem() {
        override val key = "sys_opt_${parentToken}_$optionKey"
    }

    data class SystemCustomizationSlider(
        val parentToken: String,
        val prefKey: String,
        val title: String,
        val subtitle: String,
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int = 0,
        val formatValue: (Float) -> String
    ) : PickerRowItem() {
        override val key = "sys_slider_${parentToken}_$prefKey"
    }

    data class AppHeader(
        val appName: String,
        val packageName: String,
        val appToken: String,
        val isExpanded: Boolean,
        val totalShortcuts: Int,
        val isPinned: Boolean = false
    ) : PickerRowItem() {
        override val key = "app_header_$packageName"
    }

    data class SubHeader(
        val packageName: String,
        val subKey: String,
        val label: String,
        val isExpanded: Boolean
    ) : PickerRowItem() {
        override val key = "sub_header_$subKey"
    }

    data class ShortcutAction(
        val token: String,
        val label: String,
        val packageName: String,
        val isPlugin: Boolean
    ) : PickerRowItem() {
        override val key = "shortcut_$token"
    }
}

fun buildFlatItemsList(
    allTokens: List<String>,
    labelCache: Map<String, String>,
    searchQuery: String,
    expandedSubsections: Set<String>,
    pinnedApps: Set<String>,
    prefs: SharedPreferences,
    singleSelectPrefKey: String?
): List<PickerRowItem> {
    val list = mutableListOf<PickerRowItem>()
    val validTokens = allTokens.filter { it != "none" }
    val filtered = if (searchQuery.isBlank()) {
        validTokens
    } else {
        validTokens.filter { token ->
            val label = labelCache[token] ?: token
            label.contains(searchQuery, ignoreCase = true) || token.contains(searchQuery, ignoreCase = true)
        }
    }

    // 1. System Actions (Organized by Subcategories)
    val systemActions = filtered.filter { it.startsWith("system:") || it == "action_enter_gearset_nav" }
    if (systemActions.isNotEmpty()) {
        val sysKey = "category:system"
        val isExpanded = expandedSubsections.contains(sysKey) || searchQuery.isNotBlank()
        list.add(PickerRowItem.SystemHeader(systemActions.size, isExpanded))
        if (isExpanded) {
            val categories = listOf(
                Triple(
                    "sys_nav",
                    "Navigation & Multitasking",
                    listOf("action_enter_gearset_nav", "system:previous_app", "system:close_app", "system:recents", "system:home", "system:back", "system:split_screen", "system:popup_window")
                ),
                Triple(
                    "sys_hw",
                    "Hardware & System Controls",
                    listOf("system:flashlight", "system:screenshot", "system:lock_screen", "system:notifications", "system:quick_settings", "system:scroll_to_top")
                ),
                Triple(
                    "sys_media",
                    "Media Actions",
                    listOf(
                        "system:media_play_pause",
                        "system:media_next",
                        "system:media_prev",
                        "system:media_skip_forward",
                        "system:media_skip_backward",
                        "system:media_scrubber",
                        "system:media_stop"
                    )
                ),
                Triple(
                    "sys_tactical",
                    "Tactical Quick Action & AI Tools",
                    listOf(
                        "system:tactical_flyout",
                        "system:lens",
                        "system:qr_scanner",
                        "system:chatgpt",
                        "system:claude",
                        "system:gemini",
                        "system:refueling_bay"
                    )
                ),
                Triple(
                    "sys_orient",
                    "System Attitude & Orientation",
                    listOf(
                        "system:auto_rotate_toggle",
                        "system:gravity_reset",
                        "system:gravity_override_portrait",
                        "system:gravity_override_landscape",
                        "system:gravity_override_360",
                        "system:orientation_toggle",
                        "system:gravity_toggle_master",
                        "system:orientation_portrait",
                        "system:orientation_sensor_360",
                        "system:orientation_sensor_portrait"
                    )
                ),
                Triple(
                    "sys_scrub",
                    "Gesture Scrubbers & Sliders",
                    listOf("system:screen_timeout", "system:volume", "system:brightness")
                )
            )

            categories.forEach { (catKey, catTitle, tokenList) ->
                val matchingTokens = systemActions.filter { tokenList.contains(it) }
                if (matchingTokens.isNotEmpty()) {
                    val collapseKey = "collapsed:$catKey"
                    val catExpanded = if (searchQuery.isNotBlank()) true else !expandedSubsections.contains(collapseKey)

                    list.add(PickerRowItem.SystemCategoryHeader(catKey, catTitle, matchingTokens.size, catExpanded))

                    if (catExpanded) {
                        matchingTokens.forEach { token ->
                            val isCustomizable = token == "system:screen_timeout" ||
                                    token == "system:volume" ||
                                    token == "system:brightness" ||
                                    token == "system:media_skip_forward" ||
                                    token == "system:media_skip_backward"
                            val customKey = "customization:$token"
                            val isCustomExpanded = expandedSubsections.contains(customKey)
                            list.add(PickerRowItem.SystemAction(token, labelCache[token] ?: token, isCustomizable, isCustomExpanded))

                            if (isCustomizable && isCustomExpanded) {
                                if (token == "system:media_skip_forward" || token == "system:media_skip_backward") {
                                    val currentSkip = prefs.getInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
                                    val durations = listOf(
                                        Pair("5", "5 Seconds" to "Ultra-short quick leap"),
                                        Pair("10", "10 Seconds (Default)" to "Standard music & podcast skip"),
                                        Pair("15", "15 Seconds" to "Standard audiobook interval"),
                                        Pair("30", "30 Seconds" to "Fast commercial & sponsor leap"),
                                        Pair("60", "60 Seconds (1 Min)" to "Extended segment leap")
                                    )
                                    durations.forEach { (secStr, desc) ->
                                        val (title, subtitle) = desc
                                        list.add(
                                            PickerRowItem.SystemCustomizationOption(
                                                parentToken = token,
                                                optionKey = secStr,
                                                title = title,
                                                subtitle = subtitle,
                                                isSelected = currentSkip == secStr.toInt()
                                            )
                                        )
                                    }
                                } else if (token == "system:brightness") {
                                    val hudPrefKey = if (!singleSelectPrefKey.isNullOrBlank()) {
                                        singleSelectPrefKey.replace("pref_macro_action_", "pref_macro_hud_style_")
                                    } else "pref_macro_hud_style_default"
                                    val currentStyle = prefs.getString(hudPrefKey, null) ?: prefs.getString("pref_macro_hud_style_default", "canopy_droppod") ?: "canopy_droppod"
                                    val isHudOn = prefs.getBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)
                                    val brightRes = prefs.getInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32)

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "brightness_hud_toggle:on",
                                            title = "Overlay HUD: Enabled",
                                            subtitle = "Render real-time brightness telemetry HUD while scrubbing",
                                            isSelected = isHudOn
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "brightness_hud_toggle:off",
                                            title = "Overlay HUD: Disabled",
                                            subtitle = "Silent scrubbing without on-screen visual overlay",
                                            isSelected = !isHudOn
                                        )
                                    )

                                    list.add(
                                        PickerRowItem.SystemCustomizationSlider(
                                            parentToken = token,
                                            prefKey = LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION,
                                            title = "Scrub Resolution",
                                            subtitle = "Graduation steps across 0–100% brightness range (10 to 254)",
                                            value = brightRes.toFloat(),
                                            range = 10f..254f,
                                            steps = 243,
                                            formatValue = { "${it.toInt()} Steps (~${String.format(java.util.Locale.US, "%.1f", 100f / it.coerceAtLeast(1f))}%)" }
                                        )
                                    )

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:canopy_droppod",
                                            title = "Style: Tactical Canopy Drop-Pod",
                                            subtitle = "Chamfered visor below status bar with adaptive liquid glass gauge",
                                            isSelected = currentStyle == "canopy_droppod"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:cockpit_reticle",
                                            title = "Style: Holographic Cockpit Reticle",
                                            subtitle = "Upper-third focal circular tachyon arc with orbital lock pips",
                                            isSelected = currentStyle == "cockpit_reticle"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:edge_blade",
                                            title = "Style: Dynamic Edge Blade",
                                            subtitle = "Lateral energy ladder aligned to active swipe edge",
                                            isSelected = currentStyle == "edge_blade"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:quantum_horizon",
                                            title = "Style: Quantum Synthetic Horizon",
                                            subtitle = "Synthetic horizon collimator with swept flight wings & digital telemetry",
                                            isSelected = currentStyle == "quantum_horizon"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:tachyon_dial",
                                            title = "Style: Tachyon Orbital Radar",
                                            subtitle = "Concentric orbital radar dial with 360° azimuth degree hashes & target crosshair",
                                            isSelected = currentStyle == "tachyon_dial"
                                        )
                                    )
                                } else if (token == "system:volume") {
                                    val hudPrefKey = if (!singleSelectPrefKey.isNullOrBlank()) {
                                        singleSelectPrefKey.replace("pref_macro_action_", "pref_macro_hud_style_")
                                    } else "pref_macro_hud_style_default"
                                    val currentStyle = prefs.getString(hudPrefKey, null) ?: prefs.getString("pref_macro_hud_style_default", "canopy_droppod") ?: "canopy_droppod"
                                    val isHudOn = prefs.getBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)
                                    val showNative = prefs.getBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
                                    val volStep = prefs.getInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, 1)

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "volume_hud_toggle:on",
                                            title = "Overlay HUD: Enabled",
                                            subtitle = "Render real-time volume telemetry HUD while scrubbing",
                                            isSelected = isHudOn
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "volume_hud_toggle:off",
                                            title = "Overlay HUD: Disabled",
                                            subtitle = "Silent scrubbing without on-screen visual overlay",
                                            isSelected = !isHudOn
                                        )
                                    )

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "volume_native_slider:off",
                                            title = "Native Volume Slider: Hidden (Stealth)",
                                            subtitle = "Suppress Android's system volume dialog during scrub",
                                            isSelected = !showNative
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "volume_native_slider:on",
                                            title = "Native Volume Slider: Visible",
                                            subtitle = "Show Android's default system volume popup alongside scrub",
                                            isSelected = showNative
                                        )
                                    )

                                    list.add(
                                        PickerRowItem.SystemCustomizationSlider(
                                            parentToken = token,
                                            prefKey = LightspeedPreferences.KEY_VOLUME_SCRUB_STEP,
                                            title = "Volume Step Velocity",
                                            subtitle = "Audio volume steps changed per tactile notch (1 to 5)",
                                            value = volStep.toFloat(),
                                            range = 1f..5f,
                                            steps = 3,
                                            formatValue = { "${it.toInt()} ${if (it.toInt() == 1) "Step" else "Steps"}" }
                                        )
                                    )

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:canopy_droppod",
                                            title = "Style: Tactical Canopy Drop-Pod",
                                            subtitle = "Chamfered visor below status bar with adaptive liquid glass gauge",
                                            isSelected = currentStyle == "canopy_droppod"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:cockpit_reticle",
                                            title = "Style: Holographic Cockpit Reticle",
                                            subtitle = "Upper-third focal circular tachyon arc with orbital lock pips",
                                            isSelected = currentStyle == "cockpit_reticle"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:edge_blade",
                                            title = "Style: Dynamic Edge Blade",
                                            subtitle = "Lateral energy ladder aligned to active swipe edge",
                                            isSelected = currentStyle == "edge_blade"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:quantum_horizon",
                                            title = "Style: Quantum Synthetic Horizon",
                                            subtitle = "Synthetic horizon collimator with swept flight wings & digital telemetry",
                                            isSelected = currentStyle == "quantum_horizon"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "hud_style:tachyon_dial",
                                            title = "Style: Tachyon Orbital Radar",
                                            subtitle = "Concentric orbital radar dial with 360° azimuth degree hashes & target crosshair",
                                            isSelected = currentStyle == "tachyon_dial"
                                        )
                                    )
                                } else {
                                    val hudPrefKey = if (!singleSelectPrefKey.isNullOrBlank()) {
                                        singleSelectPrefKey.replace("pref_macro_action_", "pref_macro_hud_style_")
                                    } else "pref_macro_hud_style_default"
                                    val currentStyle = prefs.getString(hudPrefKey, "canopy_droppod") ?: "canopy_droppod"

                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "canopy_droppod",
                                            title = "Tactical Canopy Drop-Pod",
                                            subtitle = "Chamfered visor below status bar with 7-segment quantum gauge",
                                            isSelected = currentStyle == "canopy_droppod"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "cockpit_reticle",
                                            title = "Holographic Cockpit Reticle",
                                            subtitle = "Upper-third focal circular tachyon arc with orbital lock pips",
                                            isSelected = currentStyle == "cockpit_reticle"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "edge_blade",
                                            title = "Dynamic Edge Blade",
                                            subtitle = "Lateral energy ladder aligned to active swipe edge",
                                            isSelected = currentStyle == "edge_blade"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "quantum_horizon",
                                            title = "Quantum Synthetic Horizon",
                                            subtitle = "Synthetic horizon collimator with swept flight wings & digital telemetry",
                                            isSelected = currentStyle == "quantum_horizon"
                                        )
                                    )
                                    list.add(
                                        PickerRowItem.SystemCustomizationOption(
                                            parentToken = token,
                                            optionKey = "tachyon_dial",
                                            title = "Tachyon Orbital Radar",
                                            subtitle = "Concentric orbital radar dial with 360° azimuth degree hashes & target crosshair",
                                            isSelected = currentStyle == "tachyon_dial"
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // 2. Apps and Shortcuts grouped by package
    val nonSystem = filtered.filter { !it.startsWith("system:") && it != "action_enter_gearset_nav" }
    val appMap = mutableMapOf<String, MutableList<String>>()
    nonSystem.forEach { token ->
        val pkg = when {
            token.startsWith("app:") -> token.removePrefix("app:")
            token.startsWith("shortcut:") && token.contains(";pkg=") -> token.substringAfter(";pkg=").substringBefore(";")
            token.startsWith("shortcut:") && token.contains("package=") -> token.substringAfter("package=").substringBefore(";")
            else -> "other"
        }
        appMap.getOrPut(pkg) { mutableListOf() }.add(token)
    }

    val sortedApps = appMap.keys.map { pkg ->
        val appLabel = labelCache["app:$pkg"] ?: pkg
        val isPinned = pinnedApps.contains(pkg)
        Triple(pkg, appLabel, appMap[pkg] ?: emptyList()) to isPinned
    }.sortedWith(compareByDescending<Pair<Triple<String, String, List<String>>, Boolean>> { it.second }.thenBy { it.first.second.lowercase() })

    sortedApps.forEach { (triple, isPinned) ->
        val (pkg, appName, tokens) = triple
        val appToken = tokens.firstOrNull { it.startsWith("app:") } ?: "app:$pkg"
        val appShortcuts = tokens.filter { it.contains(";type=app_shortcut;") }
        val homeShortcuts = tokens.filter { it.contains(";type=home_shortcut;") }
        val deepActivities = tokens.filter { it.contains(";type=activity;") }

        val appKey = "app:$pkg"
        val isExpanded = expandedSubsections.contains(appKey) || searchQuery.isNotBlank()
        val totalShortcuts = appShortcuts.size + homeShortcuts.size + deepActivities.size

        list.add(PickerRowItem.AppHeader(appName, pkg, appToken, isExpanded, totalShortcuts, isPinned))

        if (isExpanded) {
            if (appShortcuts.isNotEmpty()) {
                val subKey = "$pkg|AppShortcuts"
                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                list.add(PickerRowItem.SubHeader(pkg, subKey, "📱 App Shortcuts (${appShortcuts.size})", isSubExpanded))
                if (isSubExpanded) {
                    appShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = true))
                    }
                }
            }

            if (homeShortcuts.isNotEmpty()) {
                val subKey = "$pkg|HomeShortcuts"
                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                list.add(PickerRowItem.SubHeader(pkg, subKey, "🏠 Home Shortcuts (${homeShortcuts.size})", isSubExpanded))
                if (isSubExpanded) {
                    homeShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = false))
                    }
                }
            }

            if (deepActivities.isNotEmpty()) {
                val subKey = "$pkg|DeepActivities"
                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                list.add(PickerRowItem.SubHeader(pkg, subKey, "⚡ Deep Activities (${deepActivities.size})", isSubExpanded))
                if (isSubExpanded) {
                    deepActivities.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = false))
                    }
                }
            }
        }
    }
    return list
}
