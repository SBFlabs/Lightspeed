with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "r") as f:
    content = f.read()

effect_code = """
    LaunchedEffect(
        pagerState.currentPage,
        isLeftTopGeoExpanded, isLeftBottomGeoExpanded, isLeftUnifiedGeoExpanded,
        isTopGeoExpanded, isBottomGeoExpanded, isRightUnifiedGeoExpanded,
        isStatusBarGeoExpanded
    ) {
        val editor = prefs.edit()
        
        var showLeftPreview = false
        var showRightPreview = false
        var showStatusBarPreview = false
        
        when (pagerState.currentPage) {
            0 -> {
                showLeftPreview = isLeftTopGeoExpanded || isLeftBottomGeoExpanded || isLeftUnifiedGeoExpanded
            }
            1 -> {
                showStatusBarPreview = isStatusBarGeoExpanded
            }
            2 -> {
                showRightPreview = isTopGeoExpanded || isBottomGeoExpanded || isRightUnifiedGeoExpanded
            }
        }
        
        editor.putBoolean("pref_sidebar_left_preview", showLeftPreview)
        editor.putBoolean("pref_sidebar_preview", showRightPreview)
        editor.putBoolean("pref_statusbar_preview", showStatusBarPreview)
        editor.apply()
        
        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
    }
"""

# Find HorizontalPager and insert the effect right before it.
# HorizontalPager is around line 694. Let's find `HorizontalPager(`
content = content.replace("        HorizontalPager(", effect_code + "\n        HorizontalPager(")

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "w") as f:
    f.write(content)

