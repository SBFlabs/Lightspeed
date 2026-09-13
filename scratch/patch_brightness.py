import re

# 1. LightspeedCruiseOverlay.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'r') as f:
    content = f.read()

# Add activeScrubBrightness
content = content.replace("internal var activeScrubVolumePct: Int = -1", "internal var activeScrubVolumePct: Int = -1\n    internal var activeScrubBrightness: Int = -1")

# Update executeLinearScrubTrack
old_bright = """                    val currentBrightness = try {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { 128 }
                    val targetBrightness = (currentBrightness + (steps * brightStep)).coerceIn(0, 255)
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)"""
new_bright = """                    if (activeScrubBrightness < 0) {
                        activeScrubBrightness = try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        } catch (_: Exception) { 128 }
                    }
                    val targetBrightness = (activeScrubBrightness + (steps * brightStep)).coerceIn(0, 255)
                    activeScrubBrightness = targetBrightness
                    try {
                        // Force manual brightness so auto-brightness doesn't fight the user's manual scrub
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)"""
content = content.replace(old_bright, new_bright)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'w') as f:
    f.write(content)


# 2. LightspeedCruiseOverlayTouch.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

old_touch_start = """                                        val currentBrightness = try {
                                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                        } catch (_: Exception) { 128 }
                                        val brightResolution = prefs().getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                                        scrubHudTitle = "BRIGHTNESS"
                                        scrubHudValue = "${(currentBrightness * 100 / 255)}%\""""
new_touch_start = """                                        val currentBrightness = try {
                                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                                        } catch (_: Exception) { 128 }
                                        activeScrubBrightness = currentBrightness
                                        val brightResolution = prefs().getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                                        scrubHudTitle = "BRIGHTNESS"
                                        scrubHudValue = "${(currentBrightness * 100 / 255)}%\""""
content = content.replace(old_touch_start, new_touch_start)

old_touch_end = """                activeScrubVolumePct = -1
                scrubHudTitle = ""
                scrubHudValue = \"\""""
new_touch_end = """                activeScrubVolumePct = -1
                activeScrubBrightness = -1
                scrubHudTitle = ""
                scrubHudValue = \"\""""
content = content.replace(old_touch_end, new_touch_end)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
