import re
with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'r') as f:
    engine = f.read()

new_funcs = """    fun setAudioFocusDenied(pkg: String, isDenied: Boolean) {
        val mode = if (isDenied) "ignore" else "allow"
        val cmd = arrayOf("cmd", "appops", "set", pkg, "TAKE_AUDIO_FOCUS", mode)
        val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
        method.isAccessible = true
        
        if (Shizuku.pingBinder()) {
            try {
                val process = method.invoke(null, cmd, null, null) as Process
                process.waitFor()
            } catch (e: Exception) {}
        }
    }

    fun isAudioFocusDenied(pkg: String): Boolean {
        if (!Shizuku.pingBinder()) return false
        try {
            val method = Shizuku::class.java.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("cmd", "appops", "get", pkg, "TAKE_AUDIO_FOCUS"), null, null) as Process
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = reader.readText()
            process.waitFor()
            return output.contains("ignore") || output.contains("deny")
        } catch (e: Exception) {
            return false
        }
    }
}"""
engine = engine.replace("}", new_funcs)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedAppSovereigntyEngine.kt', 'w') as f:
    f.write(engine)
