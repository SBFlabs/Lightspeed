package com.sbf.lightspeed.system

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface contract for typed getter and setter accessors of Lightspeed avionics,
 * gestures, and hardware triggers.
 */
interface LightspeedPreferencesAccessorsContract {
    fun getAppCategoryOverride(context: Context, pkg: String): String?
    fun setAppCategoryOverride(context: Context, pkg: String, categoryKey: String)
    fun getCategoryOrder(context: Context): List<String>
    fun setCategoryOrder(context: Context, keys: List<String>)
    fun isScrubRegionsLinked(context: Context, isLeft: Boolean): Boolean
    fun setScrubRegionsLinked(context: Context, isLeft: Boolean, linked: Boolean)
    fun getGestureUnifiedKey(isLeft: Boolean, gestureKey: String): String
    fun getDeflectorLongSwipeThresholdDp(context: Context, isLandscape: Boolean): Int
    fun isGestureUnified(context: Context, isLeft: Boolean, gestureKey: String): Boolean
    fun setGestureUnified(context: Context, isLeft: Boolean, gestureKey: String, unified: Boolean)
    fun resolveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String? = null): String
    fun saveHudStyle(prefs: SharedPreferences, gestureActionKey: String?, actionToken: String?, style: String)
    fun getEffectiveCutoutOffsetX(prefs: SharedPreferences): Int
    fun getEffectiveCutoutOffsetY(prefs: SharedPreferences): Int
    fun getManualNativeRotationPolicy(context: Context): String
    fun getCallDeflectorMode(context: Context): String
    fun getPinnedGearLeft(context: Context): Boolean
    fun getMediaSkipSeconds(prefs: SharedPreferences, forward: Boolean): Int
    fun setMediaSkipSeconds(prefs: SharedPreferences, forward: Boolean, sec: Int)
    fun getRefuelingStackWidgetIds(context: Context): List<Int>
    fun saveRefuelingStackWidgetIds(context: Context, ids: List<Int>)
    fun getRefuelingGridWidgetIds(context: Context, isLandscape: Boolean = false): List<Int>
    fun saveRefuelingGridWidgetIds(context: Context, ids: List<Int>, isLandscape: Boolean = false)
    fun isRefuelingStackMemoryEnabled(context: Context): Boolean
    fun setRefuelingStackMemoryEnabled(context: Context, enabled: Boolean)
    fun isMasterFlightArmed(context: Context): Boolean
    fun setMasterFlightArmed(context: Context, armed: Boolean)
    fun isLeftDeflectorEnabled(context: Context): Boolean
    fun setLeftDeflectorEnabled(context: Context, enabled: Boolean)
    fun getActionBehavior(context: Context, token: String, default: String = "toggle"): String
    fun setActionBehavior(context: Context, token: String, behavior: String)
    fun isRightDeflectorEnabled(context: Context): Boolean
    fun setRightDeflectorEnabled(context: Context, enabled: Boolean)
    fun isLeftDeflectorGlowEnabled(context: Context): Boolean
    fun setLeftDeflectorGlowEnabled(context: Context, enabled: Boolean)
    fun isRightDeflectorGlowEnabled(context: Context): Boolean
    fun setRightDeflectorGlowEnabled(context: Context, enabled: Boolean)
    fun isDeflectorUseM3Color(context: Context): Boolean
    fun setDeflectorUseM3Color(context: Context, enabled: Boolean)
    fun getDeflectorGlowStyle(context: Context): String
    fun getDeflectorPillStyle(context: Context, isLeft: Boolean): String
    fun setDeflectorPillStyle(context: Context, isLeft: Boolean, style: String)
    fun setDeflectorGlowStyle(context: Context, style: String)
    fun isDeflectorGlowOnGestureStep(context: Context): Boolean
    fun setDeflectorGlowOnGestureStep(context: Context, enabled: Boolean)
    fun getDeflectorGlowDurationMs(context: Context): Long
    fun isFlightNotificationEnabled(context: Context): Boolean
    fun setFlightNotificationEnabled(context: Context, enabled: Boolean)
    fun getCentralCommandLongPressAction(context: Context): String
    fun setCentralCommandLongPressAction(context: Context, action: String)
    fun isHudBrightnessEnabled(context: Context): Boolean
    fun isHudVolumeEnabled(context: Context): Boolean
    fun isVolumeShowNativeSlider(context: Context): Boolean
    fun getVolumeScrubResolution(context: Context): Int
    fun getGestureHoldDurationMs(context: Context, gesturePrefKey: String? = null): Long
    fun getGravityEnforcementEngine(context: Context): String
}

/**
 * Interface contract for Perimeter Defense and Sentinel protection accessors.
 */
interface LightspeedPerimeterAccessorsContract {
    fun getPerimeterProtectedServices(context: Context): Set<String>
    fun setPerimeterProtectedServices(context: Context, services: Set<String>)
    fun togglePerimeterProtectedService(context: Context, componentId: String): Boolean
    fun getPerimeterServicePolicies(context: Context): Map<String, PerimeterPolicy>
    fun getPerimeterPolicy(context: Context, componentId: String): PerimeterPolicy
    fun setPerimeterPolicy(context: Context, componentId: String, policy: PerimeterPolicy)
    fun cyclePerimeterPolicy(context: Context, componentId: String): PerimeterPolicy
    fun getPinnedAccessibilityServices(context: Context): Set<String>
    fun setPinnedAccessibilityServices(context: Context, services: Set<String>)
    fun togglePinnedAccessibilityService(context: Context, componentId: String): Boolean
    fun getPinnedBatteryExemptApps(context: Context): Set<String>
    fun setPinnedBatteryExemptApps(context: Context, apps: Set<String>)
    fun togglePinnedBatteryExemptApp(context: Context, packageName: String): Boolean
}

/**
 * Interface contract for Reactive StateFlow state holders and theme/deck style accessors.
 */
interface LightspeedPreferencesFlowsContract {
    val themeModeFlow: StateFlow<String?>
    fun getThemeMode(context: Context): String
    fun setThemeMode(context: Context, mode: String)
    fun refreshThemeMode(context: Context)
    val deckGlassStyleFlow: StateFlow<String?>
    fun getDeckGlassStyle(context: Context): String
    fun setDeckGlassStyle(context: Context, style: String)
    fun refreshDeckGlassStyle(context: Context)
    val deckBackdropStyleFlow: StateFlow<String?>
    fun getDeckBackdropStyle(context: Context): String
    fun setDeckBackdropStyle(context: Context, style: String)
    fun refreshDeckBackdropStyle(context: Context)
    val liquidGlassConfigFlow: StateFlow<LiquidGlassConfig?>
    fun getLiquidGlassConfig(context: Context): LiquidGlassConfig
    fun setLiquidGlassConfig(context: Context, config: LiquidGlassConfig)
    fun refreshLiquidGlassConfig(context: Context)
}
