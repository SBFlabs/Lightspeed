import re

# Fix LightspeedCruiseOverlay.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'r') as f:
    content = f.read()
if 'internal var scrubStartX = 0f' not in content:
    content = content.replace('internal var lastTouchRawY = 0f', 'internal var lastTouchRawY = 0f\n    internal var scrubStartX = 0f')
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlay.kt', 'w') as f:
    f.write(content)

# Fix OmniscientAudioDockActivity.kt
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'r') as f:
    content2 = f.read()
content2 = content2.replace('android.media.AudioPlaybackConfiguration.PLAYER_STATE_STARTED', '2')
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'w') as f:
    f.write(content2)

# Check LightspeedCruiseOverlayTouch compareTo issue
# e: file:///home/Sherif/Lightspeed/app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt:851:48 'operator' modifier is required on 'FirNamedFunctionSymbol kotlin/compareTo' in 'compareTo'.
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'r') as f:
    content3 = f.read()
content3 = content3.replace('val horizontalPull = kotlin.math.abs(rawX - scrubStartX)', 'val horizontalPull = kotlin.math.abs(rawX - scrubStartX)')
with open('app/src/main/kotlin/com/sbf/lightspeed/LightspeedCruiseOverlayTouch.kt', 'w') as f:
    f.write(content3)
