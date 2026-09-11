import re

def fix_file(filepath, preview_key):
    with open(filepath, "r") as f:
        content = f.read()

    # Find and remove `.putBoolean("pref_sidebar_preview", ...)` from the main toggles.
    # It usually looks like:
    # .putBoolean("pref_section_..._expanded", ...)
    # .putBoolean("pref_sidebar_preview", ...)
    
    # We can just match `.putBoolean("KEY", ...)` where KEY is preview_key and it's inside a main toggle.
    # Since only the main toggles have `isTopExpanded && isTopGeoExpanded` or `isTopExpanded || ...`
    # We can just regex out those specific lines.
    
    lines = content.splitlines()
    new_lines = []
    for line in lines:
        if preview_key in line:
            # Check if this line is in a main toggle by looking at its logic
            if "&&" in line or "||" in line:
                # It's a main accordion toggle, skip it
                continue
            # Also center pill has `isTopExpanded || isBottomExpanded || isRightUnifiedExpanded` which has `||`
            # Wait, is there any other main toggle that just sets it?
            # Let's see: `isCenterExpanded` sets it to `isTopExpanded || ...` (skip)
            # `isRightUnifiedExpanded` sets it to `... && ...` (skip)
            # The only ones we want to keep are:
            # `.putBoolean("pref_sidebar_preview", isTopGeoExpanded)`
            # `.putBoolean("pref_sidebar_preview", isBottomGeoExpanded)`
            # `.putBoolean("pref_sidebar_preview", isRightUnifiedGeoExpanded)`
            # `.putBoolean("pref_sidebar_preview", isLeftTopGeoExpanded)`
            # etc.
            # So if the line contains `isTopGeoExpanded)` or `isBottomGeoExpanded)` etc (without `&&`), keep it.
            if "&&" not in line and "||" not in line:
                new_lines.append(line)
            else:
                pass # skip
        else:
            new_lines.append(line)
            
    with open(filepath, "w") as f:
        f.write("\n".join(new_lines))

fix_file("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", '"pref_sidebar_preview"')
fix_file("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", '"pref_sidebar_left_preview"')

