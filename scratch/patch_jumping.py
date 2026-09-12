import re
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# 1. Stable sorting
old_launch = """        var refreshTrigger by remember { mutableIntStateOf(0) }
        
        LaunchedEffect(refreshTrigger) {"""
new_launch = """        var refreshTrigger by remember { mutableIntStateOf(0) }
        val stableSortSet = remember { mutableSetOf<String>() }
        
        LaunchedEffect(refreshTrigger) {"""
content = content.replace(old_launch, new_launch)

old_sort = """            // Sort so pinned apps appear first
            activeAppsList = apps.sortedByDescending { it.pkg in pinnedPackages }"""
new_sort = """            // Prevent jumping: keep unpinned items at the top until dock closes
            stableSortSet.addAll(pinnedPackages)
            activeAppsList = apps.sortedByDescending { it.pkg in stableSortSet }"""
content = content.replace(old_sort, new_sort)


# 2. Add key(app.pkg)
old_row = """                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, app.pkg in pinnedSet, dynamicPrimary) { isPinned ->
                                        if (isPinned) addPinnedApp(service, app.pkg) else removePinnedApp(service, app.pkg)
                                        refreshTrigger++
                                    }
                                }"""
new_row = """                                activeAppsList.forEach { app ->
                                    androidx.compose.runtime.key(app.pkg) {
                                        AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, app.pkg in pinnedSet, dynamicPrimary) { isPinned ->
                                            if (isPinned) addPinnedApp(service, app.pkg) else removePinnedApp(service, app.pkg)
                                            refreshTrigger++
                                        }
                                    }
                                }"""
content = content.replace(old_row, new_row)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
