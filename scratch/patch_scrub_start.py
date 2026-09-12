import re

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content = f.read()

# Add scrubStartX to the state
if 'var scrubStartX = 0f' not in content:
    content = content.replace('var lastTouchRawY = 0f', 'var lastTouchRawY = 0f\n    var scrubStartX = 0f')

# When entering SCRUBBING
enter_scrub = """                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                activeHoldScrubAction = assignedScrub"""
new_enter_scrub = """                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                scrubStartX = rawX
                                activeHoldScrubAction = assignedScrub"""
content = content.replace(enter_scrub, new_enter_scrub)

# Also check for SWIPE_RIGHT enter scrubbing just in case
enter_scrub_right = """                        MacroGesture.SWIPE_RIGHT -> {
                            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
                            val dynamicZone = if (prefs().getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = prefs().getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                activeHoldScrubAction = assignedScrub"""
new_enter_scrub_right = """                        MacroGesture.SWIPE_RIGHT -> {
                            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
                            val dynamicZone = if (prefs().getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = prefs().getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                scrubStartX = rawX
                                activeHoldScrubAction = assignedScrub"""
content = content.replace(enter_scrub_right, new_enter_scrub_right)


# Deep pull check
deep_pull = """                            val horizontalPull = kotlin.math.abs(rawX - gestureStartX)"""
new_deep_pull = """                            val horizontalPull = kotlin.math.abs(rawX - scrubStartX)"""
content = content.replace(deep_pull, new_deep_pull)

# Also lower the threshold a bit to make it comfortable, since they already traveled 33% of screen.
# If they travelled 33% (360px), and now they need 420px more, that's 780px (very far!).
# Let's make it 80f * density (240px more).
deep_pull_threshold = """if (horizontalPull > 140f * density && (isVolume || isBrightness)) {"""
new_deep_pull_threshold = """if (horizontalPull > 80f * density && (isVolume || isBrightness)) {"""
content = content.replace(deep_pull_threshold, new_deep_pull_threshold)

with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content)
