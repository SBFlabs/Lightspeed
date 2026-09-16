import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

system_row_composable = """@Composable
fun SystemVolumeRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, streamType: Int, audioManager: AudioManager, tint: Color) {
    val maxVol = remember { audioManager.getStreamMaxVolume(streamType).toFloat().coerceAtLeast(1f) }
    var vol by remember { mutableFloatStateOf(audioManager.getStreamVolume(streamType).toFloat()) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Slider(
                value = vol,
                onValueChange = { 
                    vol = it 
                    audioManager.setStreamVolume(streamType, it.roundToInt(), 0)
                },
                valueRange = 0f..maxVol,
                modifier = Modifier.height(20.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = tint, inactiveTrackColor = Color.White.copy(alpha=0.1f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("${((vol / maxVol) * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
"""

if 'fun SystemVolumeRow' not in content:
    content = content + "\n" + system_row_composable

old_ui = """                        Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (activeAppsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                Text("No Active Sessions", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.name, app.icon, 0.8f, isPinned = false, dynamicPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))"""

new_ui = """                        Text("SYSTEM STREAMS", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SystemVolumeRow("RINGER & NOTIFICATIONS", Icons.Default.Notifications, AudioManager.STREAM_RING, audioManager, Color(0xFF3B82F6))
                            SystemVolumeRow("ALARMS", Icons.Default.AccessAlarm, AudioManager.STREAM_ALARM, audioManager, Color(0xFFF59E0B))
                            SystemVolumeRow("VOICE CALLS", Icons.Default.Phone, AudioManager.STREAM_VOICE_CALL, audioManager, Color(0xFF10B981))
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (activeAppsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                Text("No Active Sessions", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.name, app.icon, 0.8f, isPinned = false, dynamicPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))"""
content = content.replace(old_ui, new_ui)

# Need to import icons
imports = """import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Phone"""
if 'import androidx.compose.material.icons.filled.Notifications' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.PushPin', 'import androidx.compose.material.icons.filled.PushPin\n' + imports)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
