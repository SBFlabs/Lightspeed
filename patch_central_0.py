with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "r") as f:
    content = f.read()

# Replace main accordion states for tab 0
content = content.replace(
    'pinnedSection0 == "left_top"',
    'pinnedSection0 == "left_top" || pinnedSection0.startsWith("left_top_")'
).replace(
    'pinnedSection0 == "left_bottom"',
    'pinnedSection0 == "left_bottom" || pinnedSection0.startsWith("left_bottom_")'
).replace(
    'pinnedSection0 == "left_unified"',
    'pinnedSection0 == "left_unified" || pinnedSection0.startsWith("left_unified_")'
)

subs = [
    ("isLeftTopGeoExpandedState", "pref_sub_geo_left_top", "left_top_geo"),
    ("isLeftTopScrubExpandedState", "pref_sub_scrub_left_top", "left_top_scrub"),
    ("isLeftTopGesturesExpandedState", "pref_sub_gestures_left_top", "left_top_gestures"),
    
    ("isLeftBottomGeoExpandedState", "pref_sub_geo_left_bottom", "left_bottom_geo"),
    ("isLeftBottomScrubExpandedState", "pref_sub_scrub_left_bottom", "left_bottom_scrub"),
    ("isLeftBottomGesturesExpandedState", "pref_sub_gestures_left_bottom", "left_bottom_gestures"),
    
    ("isLeftUnifiedGeoExpandedState", "pref_sub_geo_left_unified", "left_unified_geo"),
    ("isLeftUnifiedScrubExpandedState", "pref_sub_scrub_left_unified", "left_unified_scrub"),
    ("isLeftUnifiedGesturesExpandedState", "pref_sub_gestures_left_unified", "left_unified_gestures")
]

for state, pref, pin_id in subs:
    old = f'val {state} = rememberSaveable {{ mutableStateOf(prefs.getBoolean("{pref}", true)) }}'
    new = f'val {state} = rememberSaveable {{ mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "{pin_id}" else prefs.getBoolean("{pref}", true)) }}'
    
    old_false = f'val {state} = rememberSaveable {{ mutableStateOf(prefs.getBoolean("{pref}", false)) }}'
    new_false = f'val {state} = rememberSaveable {{ mutableStateOf(if (tabMode0 == "custom_pinned" && pinnedSection0.contains("_")) pinnedSection0 == "{pin_id}" else prefs.getBoolean("{pref}", false)) }}'
    
    content = content.replace(old, new).replace(old_false, new_false)

with open("app/src/main/kotlin/com/sbf/lightspeed/settings/CentralCommandConfig.kt", "w") as f:
    f.write(content)
