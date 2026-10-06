package com.sbf.lightspeed.system

/**
 * Safely calls reloadPreferences() on LightspeedAccessibilityService.instance without throwing exceptions.
 */
fun safeReloadPreferences() {
    try {
        com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences()
    } catch (e: Exception) { logSwallowed("LightspeedActivityExtensions", "safeReloadPreferences:9", e) }
}
