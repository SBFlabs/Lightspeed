with open("app/src/main/kotlin/com/sbf/lightspeed/LightspeedAccessibilityService.kt", "r") as f:
    content = f.read()

old_event = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val currentPkg = event.packageName?.toString()
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = keyguardManager?.isKeyguardLocked == true
        
        updateOverlaysVisibility(isLocked, currentPkg)
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val isFullScreen = event.isFullScreen
            val className = event.className?.toString()
            val isLikelyActivity = isFullScreen || (className != null && (className.endsWith("Activity") || className.endsWith("Launcher")))
            if (isLikelyActivity) {
                com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this, isLocked, currentPkg)
            }
        }
    }"""

new_event = """    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val currentPkg = event.packageName?.toString()
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            val isLocked = keyguardManager?.isKeyguardLocked == true
            
            updateOverlaysVisibility(isLocked, currentPkg)
            
            val isFullScreen = event.isFullScreen
            val className = event.className?.toString()
            val isLikelyActivity = isFullScreen || (className != null && (className.endsWith("Activity") || className.endsWith("Launcher")))
            if (isLikelyActivity) {
                com.sbf.lightspeed.system.LightspeedOrientationManager.evaluateContextGuardrails(this, isLocked, currentPkg)
            }
        }
    }"""

if old_event in content:
    content = content.replace(old_event, new_event)
else:
    print("Could not find onAccessibilityEvent")

with open("app/src/main/kotlin/com/sbf/lightspeed/LightspeedAccessibilityService.kt", "w") as f:
    f.write(content)
