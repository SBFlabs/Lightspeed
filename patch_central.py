with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "r") as f:
    content = f.read()

# Replace main accordion states for tab 2
content = content.replace(
    'pinnedSection2 == "top"',
    'pinnedSection2 == "top" || pinnedSection2.startsWith("top_")'
).replace(
    'pinnedSection2 == "bottom"',
    'pinnedSection2 == "bottom" || pinnedSection2.startsWith("bottom_")'
).replace(
    'pinnedSection2 == "unified"',
    'pinnedSection2 == "unified" || pinnedSection2.startsWith("unified_")'
)

# Replace sub-accordion states for tab 2
subs = [
    ("isTopGeoExpandedState", "pref_sub_geo_top", "top_geo"),
    ("isTopScrubExpandedState", "pref_sub_scrub_top", "top_scrub"),
    ("isTopGesturesExpandedState", "pref_sub_gestures_top", "top_gestures"),
    
    ("isBottomGeoExpandedState", "pref_sub_geo_bottom", "bottom_geo"),
    ("isBottomScrubExpandedState", "pref_sub_scrub_bottom", "bottom_scrub"),
    ("isBottomGesturesExpandedState", "pref_sub_gestures_bottom", "bottom_gestures"),
    
    ("isRightUnifiedGeoExpandedState", "pref_sub_geo_right_unified", "unified_geo"),
    ("isRightUnifiedScrubExpandedState", "pref_sub_scrub_right_unified", "unified_scrub"),
    ("isRightUnifiedGesturesExpandedState", "pref_sub_gestures_right_unified", "unified_gestures")
]

for state, pref, pin_id in subs:
    old = f'val {state} = rememberSaveable {{ mutableStateOf(prefs.getBoolean("{pref}", true)) }}'
    new = f'val {state} = rememberSaveable {{ mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "{pin_id}" else prefs.getBoolean("{pref}", true)) }}'
    # some defaults are false
    old_false = f'val {state} = rememberSaveable {{ mutableStateOf(prefs.getBoolean("{pref}", false)) }}'
    new_false = f'val {state} = rememberSaveable {{ mutableStateOf(if (tabMode2 == "custom_pinned" && pinnedSection2.contains("_")) pinnedSection2 == "{pin_id}" else prefs.getBoolean("{pref}", false)) }}'
    content = content.replace(old, new).replace(old_false, new_false)

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "w") as f:
    f.write(content)
