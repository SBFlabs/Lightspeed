import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

helper_func = """    private fun handleLaunchException(context: Context, e: Exception) {
        if (e is SecurityException && e.message?.contains("Permission Denial") == true) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Permission Required: Please grant in App Settings", android.widget.Toast.LENGTH_LONG).show()
            }
            try {
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }

    private fun intentToAmStartCommand(intent: Intent): String {"""

content = content.replace("    private fun intentToAmStartCommand(intent: Intent): String {", helper_func)

# Replace startActivities try-catch
old_start_activities = """                                try {
                                    context.startActivities(intentsToStart)
                                    launched = true
                                } catch (_: Exception) {}"""
new_start_activities = """                                try {
                                    context.startActivities(intentsToStart)
                                    launched = true
                                } catch (e: Exception) {
                                    handleLaunchException(context, e)
                                }"""
content = content.replace(old_start_activities, new_start_activities)

# Replace intent fallback try-catch
old_intent_fallback = """                            try {
                                context.startActivity(launchIntent)
                                launched = true
                            } catch (_: Exception) {
                                try {
                                    context.sendBroadcast(launchIntent)
                                    launched = true
                                } catch (_: Exception) {}
                            }"""
new_intent_fallback = """                            try {
                                context.startActivity(launchIntent)
                                launched = true
                            } catch (e: Exception) {
                                handleLaunchException(context, e)
                                try {
                                    context.sendBroadcast(launchIntent)
                                    launched = true
                                } catch (_: Exception) {}
                            }"""
content = content.replace(old_intent_fallback, new_intent_fallback)


with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
