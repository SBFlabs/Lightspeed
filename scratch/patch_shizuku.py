import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'r') as f:
    engine_content = f.read()

# Add getAppMuted
new_engine = engine_content.replace("}", """
    fun isAppMuted(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val process = Shizuku.newProcess(arrayOf("cmd", "appops", "get", pkg, "AUDIO_MEDIA_VOLUME"), null, null)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}""")

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'w') as f:
    f.write(new_engine)


with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

old_slider = """            Slider(
                value = vol,
                onValueChange = { vol = it },
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),"""
new_slider = """            
            var isMuted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            
            androidx.compose.runtime.LaunchedEffect(pkg) {
                kotlinx.coroutines.Dispatchers.IO.invoke {
                    val muted = com.sbf.lightspeed.system.LightspeedAppSovereigntyEngine.isAppMuted(pkg)
                    if (muted) {
                        vol = 0f
                        isMuted = true
                    } else {
                        vol = 1f
                        isMuted = false
                    }
                }
            }
            
            Slider(
                value = vol,
                onValueChange = { newVol -> 
                    vol = newVol
                    val targetMute = newVol == 0f
                    if (targetMute != isMuted) {
                        isMuted = targetMute
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.sbf.lightspeed.system.LightspeedAppSovereigntyEngine.setAppMuted(pkg, targetMute)
                        }
                    }
                },
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),"""
content = content.replace(old_slider, new_slider)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
