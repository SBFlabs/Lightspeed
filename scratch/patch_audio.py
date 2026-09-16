import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'r') as f:
    content = f.read()

# Add getRawActiveAudioPackages function inside the Activity or companion
raw_func = """
    private fun getRawActiveAudioPackages(context: Context): List<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = context.packageManager
        val configs = audioManager.activePlaybackConfigurations
        val pkgs = mutableListOf<String>()
        
        for (config in configs) {
            val state = try {
                config.javaClass.getMethod("getPlayerState").invoke(config) as Int
            } catch (e: Exception) { -1 }
            
            if (state == android.media.AudioPlaybackConfiguration.PLAYER_STATE_STARTED) {
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
        }
        return pkgs.distinct()
    }
"""

if 'private fun getRawActiveAudioPackages' not in content:
    content = content.replace('class OmniscientAudioDockActivity : ComponentActivity() {', 'class OmniscientAudioDockActivity : ComponentActivity() {' + raw_func)


# Modify the fetch active media controllers
old_fetch = """        // Fetch active media controllers
        val activeControllers = LightspeedMediaManager.getActiveControllers(this)
        val activeAppsList = activeControllers.mapNotNull { controller ->
            val pkg = controller.packageName
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                ActiveAppInfo(pkg, label, icon)
            } catch (e: Exception) {
                null
            }
        }.distinctBy { it.pkg }"""

new_fetch = """        // Fetch active media controllers
        val activeControllers = LightspeedMediaManager.getActiveControllers(this)
        val sessionPackages = activeControllers.map { it.packageName }
        val rawPackages = getRawActiveAudioPackages(this)
        
        val mergedPackages = (sessionPackages + rawPackages).distinct()
        
        val activeAppsList = mergedPackages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                ActiveAppInfo(pkg, label, icon)
            } catch (e: Exception) {
                null
            }
        }"""

content = content.replace(old_fetch, new_fetch)

# Add FLAG_NOT_FOCUSABLE to prevent AnkiDroid from pausing!
old_flags = """        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)"""

new_flags = """        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)"""

content = content.replace(old_flags, new_flags)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'w') as f:
    f.write(content)
