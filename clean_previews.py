def fix_file(filepath, preview_key):
    with open(filepath, "r") as f:
        content = f.read()

    lines = content.splitlines()
    new_lines = []
    for line in lines:
        if preview_key in line:
            pass 
        else:
            new_lines.append(line)
            
    with open(filepath, "w") as f:
        f.write("\n".join(new_lines))

fix_file("app/src/main/kotlin/com/sbf/lightspeed/settings/RightDeflectorTab.kt", '"pref_sidebar_preview"')
fix_file("app/src/main/kotlin/com/sbf/lightspeed/settings/LeftDeflectorTab.kt", '"pref_sidebar_left_preview"')

