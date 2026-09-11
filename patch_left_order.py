import re

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", "r") as f:
    content = f.read()

def inject_sub_iterator(content, section_name, array_name):
    # find Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    # and replace with Column(...) { currentSubLeftTop.forEach { subKey -> when (subKey) { "geo" -> { Collapsible... } "scrub" -> { Collapsible... } "gestures" -> { Collapsible... } } } }
    
    # Actually, it's easier to just match each CollapsibleSubSection manually.
    
    # For geo:
    geo_pattern = re.compile(r'(CollapsibleSubSection\([^)]*title = "Sensor Geometry".*?\}\n\s*\})', re.DOTALL)
    scrub_pattern = re.compile(r'(CollapsibleSubSection\([^)]*title = "Inward Scrubbing Control".*?\}\n\s*\})', re.DOTALL)
    gestures_pattern = re.compile(r'(CollapsibleSubSection\([^)]*title = "Gesture Actions & Macro Mappings".*?\}\n\s*\})', re.DOTALL)
    
    # Because there are multiple zones, we can do it globally if we can scope it.
    pass

# We will just use the same block replacement.
left_top_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftTopGeoExpanded,
                                                            onToggle = {
                                                                isLeftTopGeoExpanded = !isLeftTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_top", isLeftTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isLeftTopScrubExpanded,
                                                            onToggle = {
                                                                isLeftTopScrubExpanded = !isLeftTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_top", isLeftTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftTopGesturesExpanded,
                                                            onToggle = {
                                                                isLeftTopGesturesExpanded = !isLeftTopGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_top", isLeftTopGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftTop[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_TOP_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""
left_top_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubLeftTop.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Upper deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftTopGeoExpanded,
                                                            onToggle = {
                                                                isLeftTopGeoExpanded = !isLeftTopGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_top", isLeftTopGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftTopGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_top_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Upper vector inward sweep scrubber",
                                                            isExpanded = isLeftTopScrubExpanded,
                                                            onToggle = {
                                                                isLeftTopScrubExpanded = !isLeftTopScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_top", isLeftTopScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_TOP_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftTopGesturesExpanded,
                                                            onToggle = {
                                                                isLeftTopGesturesExpanded = !isLeftTopGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_top", isLeftTopGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftTop[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_TOP_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""
content = content.replace(left_top_old, left_top_new)


left_bottom_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftBottomGeoExpanded,
                                                            onToggle = {
                                                                isLeftBottomGeoExpanded = !isLeftBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_bottom", isLeftBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isLeftBottomScrubExpanded,
                                                            onToggle = {
                                                                isLeftBottomScrubExpanded = !isLeftBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_bottom", isLeftBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftBottomGesturesExpanded,
                                                            onToggle = {
                                                                isLeftBottomGesturesExpanded = !isLeftBottomGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_bottom", isLeftBottomGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftBottom[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_BOTTOM_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""
left_bottom_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubLeftBottom.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Lower deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftBottomGeoExpanded,
                                                            onToggle = {
                                                                isLeftBottomGeoExpanded = !isLeftBottomGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_bottom", isLeftBottomGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftBottomGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_height", "", "Deflector Span (Height)", 50, 600, 10, 200)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_bottom_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Lower vector inward sweep scrubber",
                                                            isExpanded = isLeftBottomScrubExpanded,
                                                            onToggle = {
                                                                isLeftBottomScrubExpanded = !isLeftBottomScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_bottom", isLeftBottomScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_BOTTOM_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftBottomGesturesExpanded,
                                                            onToggle = {
                                                                isLeftBottomGesturesExpanded = !isLeftBottomGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_bottom", isLeftBottomGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftBottom[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_BOTTOM_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""
content = content.replace(left_bottom_old, left_bottom_new)


left_unified_old = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Unified deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGeoExpanded = !isLeftUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_unified", isLeftUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftUnifiedGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Unified inward sweep scrubber",
                                                            isExpanded = isLeftUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedScrubExpanded = !isLeftUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_unified", isLeftUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_UNIFIED_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }

                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGesturesExpanded = !isLeftUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_unified", isLeftUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftUnified[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_UNIFIED_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                    }"""
left_unified_new = """                                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                                        currentSubLeftUnified.forEach { subKey ->
                                                            when (subKey) {
                                                                "geo" -> {
                                                        CollapsibleSubSection(
                                                            title = "Sensor Geometry",
                                                            subtitle = "Unified deflector span, touch reach & stealth glow",
                                                            isExpanded = isLeftUnifiedGeoExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGeoExpanded = !isLeftUnifiedGeoExpanded
                                                                prefs.edit()
                                                                    .putBoolean("pref_sub_geo_left_unified", isLeftUnifiedGeoExpanded)
                                                                    .putBoolean("pref_sidebar_left_preview", isLeftUnifiedGeoExpanded)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                onRefreshNeeded()
                                                            }
                                                        ) {
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_height", "", "Deflector Span (Height)", 50, 1000, 10, 400)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_touch_width", "", "Touch Vector Reach", 10, 100, 5, 40)
                                                            PrefDottedSliderRow(context, prefs, "pref_sidebar_left_unified_transparency", "", "Stealth Idle Glow", 0, 100, 5, 0)
                                                        }
                                                                }
                                                                "scrub" -> {
                                                        CollapsibleSubSection(
                                                            title = "Inward Scrubbing Control",
                                                            subtitle = "Unified inward sweep scrubber",
                                                            isExpanded = isLeftUnifiedScrubExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedScrubExpanded = !isLeftUnifiedScrubExpanded
                                                                prefs.edit().putBoolean("pref_sub_scrub_left_unified", isLeftUnifiedScrubExpanded).apply()
                                                            }
                                                        ) {
                                                            GestureMappingRow(context, prefs, ArrowDirection.SCRUB, true, "pref_macro_action_LEFT_UNIFIED_SCRUBBING", "Extended Inward Sweep (Scrubbing)", listOf("none", "system:volume", "system:brightness"), tokenLabelCache)
                                                        }
                                                                }
                                                                "gestures" -> {
                                                        CollapsibleSubSection(
                                                            title = "Gesture Actions & Macro Mappings",
                                                            subtitle = "Tap · Swipe · Rebound · Two-Step · Hold Modifiers",
                                                            isExpanded = isLeftUnifiedGesturesExpanded,
                                                            onToggle = {
                                                                isLeftUnifiedGesturesExpanded = !isLeftUnifiedGesturesExpanded
                                                                prefs.edit().putBoolean("pref_sub_gestures_left_unified", isLeftUnifiedGesturesExpanded).apply()
                                                            }
                                                        ) {
                                                            dynamicActionTokens.forEach { token ->
                                                                val (desc, dir) = actionTokenToDirectionLeftUnified[token] ?: return@forEach
                                                                GestureMappingRow(context, prefs, dir, true, "pref_macro_action_LEFT_UNIFIED_$token", desc, LightspeedVocabulary.getAllMacroIds(), tokenLabelCache)
                                                            }
                                                        }
                                                                }
                                                            }
                                                        }
                                                    }"""
content = content.replace(left_unified_old, left_unified_new)


with open("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", "w") as f:
    f.write(content)

