import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "r") as f:
    content = f.read()

# Replace top Column
top_column_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isTopGeoExpanded,
                                                            onToggle = {
                                                                isTopGeoExpanded = !isTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_top", isTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isTopScrubExpanded,
                                                            onToggle = {
                                                                isTopScrubExpanded = !isTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_top", isTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isTopGesturesExpanded,
                                                            onToggle = {
                                                                isTopGesturesExpanded = !isTopGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_top", isTopGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionTop[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_TOP_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""

top_column_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubTop.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isTopGeoExpanded,
                                                            onToggle = {
                                                                isTopGeoExpanded = !isTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_top", isTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isTopScrubExpanded,
                                                            onToggle = {
                                                                isTopScrubExpanded = !isTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_top", isTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isTopGesturesExpanded,
                                                            onToggle = {
                                                                isTopGesturesExpanded = !isTopGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_top", isTopGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionTop[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_TOP_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""

content = content.replace(top_column_old, top_column_new)


# Replace bottom Column
bottom_column_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isBottomGeoExpanded,
                                                            onToggle = {
                                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isBottomScrubExpanded,
                                                            onToggle = {
                                                                isBottomScrubExpanded = !isBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_bottom", isBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isBottomGesturesExpanded,
                                                            onToggle = {
                                                                isBottomGesturesExpanded = !isBottomGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_bottom", isBottomGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionBottom[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_BOTTOM_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""

bottom_column_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubBottom.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isBottomGeoExpanded,
                                                            onToggle = {
                                                                isBottomGeoExpanded = !isBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_bottom", isBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isBottomScrubExpanded,
                                                            onToggle = {
                                                                isBottomScrubExpanded = !isBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_bottom", isBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isBottomGesturesExpanded,
                                                            onToggle = {
                                                                isBottomGesturesExpanded = !isBottomGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_bottom", isBottomGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionBottom[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_BOTTOM_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""

content = content.replace(bottom_column_old, bottom_column_new)

# Replace unified Column
unified_column_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Unified deflector span, touch reach & stealth glow",
                                                            isExpanded = isRightUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_right_unified", isRightUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isRightUnifiedGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Unified inward sweep scrubber",
                                                            isExpanded = isRightUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isRightUnifiedScrubExpanded = !isRightUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_right_unified", isRightUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_RIGHT_UNIFIED_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isRightUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGesturesExpanded = !isRightUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_right_unified", isRightUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionUnified[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_RIGHT_UNIFIED_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""

unified_column_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubUnified.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Unified deflector span, touch reach & stealth glow",
                                                            isExpanded = isRightUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGeoExpanded = !isRightUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_right_unified", isRightUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_preview", isRightUnifiedGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_right_unified_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Unified inward sweep scrubber",
                                                            isExpanded = isRightUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isRightUnifiedScrubExpanded = !isRightUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_right_unified", isRightUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, false, "pref_macro_action_RIGHT_UNIFIED_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isRightUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isRightUnifiedGesturesExpanded = !isRightUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_right_unified", isRightUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionUnified[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, false, "pref_macro_action_RIGHT_UNIFIED_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""

content = content.replace(unified_column_old, unified_column_new)


with open("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", "w") as f:
    f.write(content)

