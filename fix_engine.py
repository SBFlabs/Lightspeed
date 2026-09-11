with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationEngine.kt", "r") as f:
    content = f.read()

old_face = """        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                ElevatedTaskCloser.execShizuku("settings put secure camera_autorotate $target")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing camera_autorotate via Shizuku: ${e.message}")
            }
        }
        if (ElevatedTaskCloser.isRootActive) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure camera_autorotate $target")).waitFor()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing camera_autorotate via Root: ${e.message}")
            }
        }"""

new_face = """        if (ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                if (ElevatedTaskCloser.isShizukuActive) {
                    try {
                        ElevatedTaskCloser.execShizuku("settings put secure camera_autorotate $target")
                        return@launch
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing camera_autorotate via Shizuku: ${e.message}")
                    }
                }
                if (ElevatedTaskCloser.isRootActive) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put secure camera_autorotate $target")).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing camera_autorotate via Root: ${e.message}")
                    }
                }
            }
            return true
        }"""

old_sys = """        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                ElevatedTaskCloser.execShizuku("settings put system $name $value")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Shizuku: ${e.message}")
            }
        }
        if (ElevatedTaskCloser.isRootActive) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $name $value")).waitFor()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing setting $name=$value via Root: ${e.message}")
            }
        }"""

new_sys = """        if (ElevatedTaskCloser.isShizukuActive || ElevatedTaskCloser.isRootActive) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                if (ElevatedTaskCloser.isShizukuActive) {
                    try {
                        ElevatedTaskCloser.execShizuku("settings put system $name $value")
                        return@launch
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing setting $name=$value via Shizuku: ${e.message}")
                    }
                }
                if (ElevatedTaskCloser.isRootActive) {
                    try {
                        Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $name $value")).waitFor()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing setting $name=$value via Root: ${e.message}")
                    }
                }
            }
            return true
        }"""

if old_face in content:
    content = content.replace(old_face, new_face)
else:
    print("Could not find face old")

if old_sys in content:
    content = content.replace(old_sys, new_sys)
else:
    print("Could not find sys old")

with open("app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedOrientationEngine.kt", "w") as f:
    f.write(content)
