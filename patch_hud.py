def fix_file(filepath, preview_key):
    with open(filepath, "r") as f:
        content = f.read()

    lines = content.splitlines()
    new_lines = []
    for line in lines:
        if preview_key in line:
            # For HudStripTab, we completely remove pref_statusbar_preview setting from everywhere
            # because the LaunchedEffect in CentralCommandConfig handles it entirely!
            pass 
        else:
            new_lines.append(line)
            
    with open(filepath, "w") as f:
        f.write("\n".join(new_lines))

fix_file("app/src/main/kotlin/com/sbf/lightspeed/settings/HudStripTab.kt", '"pref_statusbar_preview"')

