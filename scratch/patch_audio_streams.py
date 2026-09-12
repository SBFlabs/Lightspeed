import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# Fix AnkiDroid detection by removing the PLAYER_STATE_STARTED check so we catch recently played/paused AudioTracks
old_raw_audio = """        for (config in configs) {
            val state = try {
                config.javaClass.getMethod("getPlayerState").invoke(config) as Int
            } catch (e: Exception) { -1 }
            
            if (state == 2) {
                try {
                    val getClientUidMethod = config.javaClass.getMethod("getClientUid")
                    val uid = getClientUidMethod.invoke(config) as Int
                    val packages = pm.getPackagesForUid(uid)
                    if (packages != null) {
                        pkgs.addAll(packages)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }"""
new_raw_audio = """        for (config in configs) {
            try {
                val getClientUidMethod = config.javaClass.getMethod("getClientUid")
                val uid = getClientUidMethod.invoke(config) as Int
                if (uid > 10000) { // Ignore system UIDs
                    val packages = pm.getPackagesForUid(uid)
                    if (packages != null) {
                        pkgs.addAll(packages)
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }"""
content = content.replace(old_raw_audio, new_raw_audio)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)

