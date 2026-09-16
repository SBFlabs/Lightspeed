import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'r') as f:
    content = f.read()

old_oem = """                        // 0. Unpack OEM Dialer / Hidden Intents
                        if (launchIntent.hasExtra("shortcut_action")) {
                            launchIntent.action = launchIntent.getStringExtra("shortcut_action")
                            launchIntent.component = null
                        }"""

new_oem = """                        // 0. Unpack OEM Dialer / Hidden Intents
                        if (launchIntent.hasExtra("shortcut_action")) {
                            launchIntent.action = launchIntent.getStringExtra("shortcut_action")
                            launchIntent.component = null
                            launchIntent.setPackage(null)
                        }"""
content = content.replace(old_oem, new_oem)

old_error_handler = """    private fun handleLaunchException(context: Context, e: Exception) {
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
    }"""

new_error_handler = """    private fun handleLaunchException(context: Context, e: Exception) {
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
        } else if (e is android.content.ActivityNotFoundException) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(context, "Activity Not Found: Target app might be restricted or missing", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }"""
content = content.replace(old_error_handler, new_error_handler)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/LightspeedShortcutManager.kt', 'w') as f:
    f.write(content)
