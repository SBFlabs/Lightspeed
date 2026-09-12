import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# Add a function to load pinned apps from prefs
pinned_engine = """
    private fun getPinnedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        return prefs.getStringSet("pinned_audio_apps", emptySet()) ?: emptySet()
    }

    private fun addPinnedApp(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pinned_audio_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(pkg)
        prefs.edit().putStringSet("pinned_audio_apps", current).apply()
    }
    
    private fun removePinnedApp(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pinned_audio_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(pkg)
        prefs.edit().putStringSet("pinned_audio_apps", current).apply()
    }
"""

if 'private fun getPinnedApps' not in content:
    content = content.replace('private fun getRawActiveAudioPackages', pinned_engine + '\n    private fun getRawActiveAudioPackages')

# Update activeAppsList to also include pinned apps!
old_effect = """        LaunchedEffect(Unit) {
            val activeControllers = LightspeedMediaManager.getActiveControllers(service)
            val sessionPackages = activeControllers.map { it.packageName }
            val rawPackages = getRawActiveAudioPackages(service)
            
            val mergedPackages = (sessionPackages + rawPackages).distinct()
            
            val apps = mergedPackages.mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    ActiveAppInfo(pkg, label, icon)
                } catch (e: Exception) {
                    null
                }
            }
            activeAppsList = apps
        }"""

new_effect = """        var refreshTrigger by remember { mutableIntStateOf(0) }
        
        LaunchedEffect(refreshTrigger) {
            val activeControllers = LightspeedMediaManager.getActiveControllers(service)
            val sessionPackages = activeControllers.map { it.packageName }
            val rawPackages = getRawActiveAudioPackages(service)
            val pinnedPackages = getPinnedApps(service)
            
            val mergedPackages = (sessionPackages + rawPackages + pinnedPackages).distinct()
            
            val apps = mergedPackages.mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    ActiveAppInfo(pkg, label, icon)
                } catch (e: Exception) {
                    null
                }
            }
            // Sort so pinned apps appear first
            activeAppsList = apps.sortedByDescending { it.pkg in pinnedPackages }
        }"""
content = content.replace(old_effect, new_effect)


# Modify AppVolumeRow to toggle pin
old_row = """@Composable
fun AppVolumeRow(name: String, iconDrawable: Drawable?, initialVol: Float, isPinned: Boolean, tint: Color) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }"""
new_row = """@Composable
fun AppVolumeRow(pkg: String, name: String, iconDrawable: Drawable?, initialVol: Float, isPinned: Boolean, tint: Color, onPinToggled: (Boolean) -> Unit) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }"""
content = content.replace(old_row, new_row)

old_icon = """        IconButton(
            onClick = { pinned = !pinned },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }"""
new_icon = """        IconButton(
            onClick = { 
                pinned = !pinned
                onPinToggled(pinned)
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }"""
content = content.replace(old_icon, new_icon)

# Pass the lambda from the UI
old_foreach = """                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.name, app.icon, 0.8f, isPinned = false, dynamicPrimary)
                                }
                            }"""
new_foreach = """                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val pinnedSet = remember(refreshTrigger) { getPinnedApps(service) }
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, isPinned = app.pkg in pinnedSet, dynamicPrimary) { isPinned ->
                                        if (isPinned) addPinnedApp(service, app.pkg) else removePinnedApp(service, app.pkg)
                                        refreshTrigger++
                                    }
                                }
                            }"""
content = content.replace(old_foreach, new_foreach)


# Add new streams and the "+ ADD APP" button
old_streams = """                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SystemVolumeRow("RINGER & NOTIFICATIONS", Icons.Default.Notifications, AudioManager.STREAM_RING, audioManager, Color(0xFF3B82F6))
                            SystemVolumeRow("ALARMS", Icons.Default.AccessAlarm, AudioManager.STREAM_ALARM, audioManager, Color(0xFFF59E0B))
                            SystemVolumeRow("VOICE CALLS", Icons.Default.Phone, AudioManager.STREAM_VOICE_CALL, audioManager, Color(0xFF10B981))
                        }"""
new_streams = """                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SystemVolumeRow("RINGER & NOTIFICATIONS", Icons.Default.Notifications, AudioManager.STREAM_RING, audioManager, Color(0xFF3B82F6))
                            SystemVolumeRow("ALARMS", Icons.Default.AccessAlarm, AudioManager.STREAM_ALARM, audioManager, Color(0xFFF59E0B))
                            SystemVolumeRow("VOICE CALLS", Icons.Default.Phone, AudioManager.STREAM_VOICE_CALL, audioManager, Color(0xFF10B981))
                            SystemVolumeRow("ASSISTANT (GEMINI)", Icons.Default.Android, 11, audioManager, Color(0xFF8B5CF6))
                            SystemVolumeRow("ACCESSIBILITY", Icons.Default.Accessibility, 10, audioManager, Color(0xFFEC4899))
                        }"""
content = content.replace(old_streams, new_streams)

old_sovereignty_header = """                        Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))"""
new_sovereignty_header = """                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("+ ADD APP", color = dynamicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.clickable {
                                // TODO: Open App Selector Dialog
                            }.padding(4.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))"""
content = content.replace(old_sovereignty_header, new_sovereignty_header)


# Check if we need to add import for Accessibility icon
if 'androidx.compose.material.icons.filled.Accessibility' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Android', 'import androidx.compose.material.icons.filled.Android\nimport androidx.compose.material.icons.filled.Accessibility')

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
