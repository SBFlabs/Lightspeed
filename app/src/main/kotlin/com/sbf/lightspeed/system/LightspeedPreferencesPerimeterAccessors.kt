package com.sbf.lightspeed.system

import android.content.Context

enum class PerimeterPolicy {
    NORMAL,
    ALWAYS_REVIVE,
    KEEP_OFF
}

/**
 * Accessors for Perimeter Defense, Sentinel protection policies,
 * pinned accessibility services, and pinned battery exempt applications.
 */
object LightspeedPreferencesPerimeterAccessors : LightspeedPerimeterAccessorsContract {

    override fun getPerimeterProtectedServices(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(LightspeedPreferences.KEY_PERIMETER_PROTECTED_SERVICES, emptySet()) ?: emptySet()

    override fun setPerimeterProtectedServices(context: Context, services: Set<String>) {
        context.defaultPrefs().edit().putStringSet(LightspeedPreferences.KEY_PERIMETER_PROTECTED_SERVICES, services).apply()
    }

    override fun togglePerimeterProtectedService(context: Context, componentId: String): Boolean {
        val current = getPerimeterProtectedServices(context).toMutableSet()
        val isNowProtected = if (current.contains(componentId)) {
            current.remove(componentId)
            false
        } else {
            current.add(componentId)
            true
        }
        setPerimeterProtectedServices(context, current)
        return isNowProtected
    }

    override fun getPerimeterServicePolicies(context: Context): Map<String, PerimeterPolicy> {
        val set = context.defaultPrefs().getStringSet(LightspeedPreferences.KEY_PERIMETER_SERVICE_POLICIES, emptySet()) ?: emptySet()
        val map = mutableMapOf<String, PerimeterPolicy>()
        for (entry in set) {
            val idx = entry.lastIndexOf('=')
            if (idx > 0 && idx < entry.length - 1) {
                val componentId = entry.substring(0, idx)
                val policyStr = entry.substring(idx + 1)
                val policy = try {
                    PerimeterPolicy.valueOf(policyStr)
                } catch (_: Exception) {
                    PerimeterPolicy.NORMAL
                }
                map[componentId] = policy
            }
        }
        return map
    }

    override fun getPerimeterPolicy(context: Context, componentId: String): PerimeterPolicy {
        return getPerimeterServicePolicies(context)[componentId] ?: PerimeterPolicy.NORMAL
    }

    override fun setPerimeterPolicy(context: Context, componentId: String, policy: PerimeterPolicy) {
        val policies = getPerimeterServicePolicies(context).toMutableMap()
        if (policy == PerimeterPolicy.NORMAL) {
            policies.remove(componentId)
        } else {
            policies[componentId] = policy
        }
        val set = policies.map { "${it.key}=${it.value.name}" }.toSet()
        context.defaultPrefs().edit().putStringSet(LightspeedPreferences.KEY_PERIMETER_SERVICE_POLICIES, set).apply()
    }

    override fun cyclePerimeterPolicy(context: Context, componentId: String): PerimeterPolicy {
        val current = getPerimeterPolicy(context, componentId)
        val next = when (current) {
            PerimeterPolicy.NORMAL -> PerimeterPolicy.ALWAYS_REVIVE
            PerimeterPolicy.ALWAYS_REVIVE -> PerimeterPolicy.KEEP_OFF
            PerimeterPolicy.KEEP_OFF -> PerimeterPolicy.NORMAL
        }
        setPerimeterPolicy(context, componentId, next)
        return next
    }

    override fun getPinnedAccessibilityServices(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(LightspeedPreferences.KEY_PINNED_ACCESSIBILITY_SERVICES, emptySet()) ?: emptySet()

    override fun setPinnedAccessibilityServices(context: Context, services: Set<String>) {
        context.defaultPrefs().edit().putStringSet(LightspeedPreferences.KEY_PINNED_ACCESSIBILITY_SERVICES, services).apply()
    }

    override fun togglePinnedAccessibilityService(context: Context, componentId: String): Boolean {
        val current = getPinnedAccessibilityServices(context).toMutableSet()
        val isNowPinned = if (current.contains(componentId)) {
            current.remove(componentId)
            false
        } else {
            current.add(componentId)
            true
        }
        setPinnedAccessibilityServices(context, current)
        return isNowPinned
    }

    override fun getPinnedBatteryExemptApps(context: Context): Set<String> =
        context.defaultPrefs().getStringSet(LightspeedPreferences.KEY_PINNED_BATTERY_EXEMPT_APPS, emptySet()) ?: emptySet()

    override fun setPinnedBatteryExemptApps(context: Context, apps: Set<String>) {
        context.defaultPrefs().edit().putStringSet(LightspeedPreferences.KEY_PINNED_BATTERY_EXEMPT_APPS, apps).apply()
    }

    override fun togglePinnedBatteryExemptApp(context: Context, packageName: String): Boolean {
        val current = getPinnedBatteryExemptApps(context).toMutableSet()
        val isNowPinned = if (current.contains(packageName)) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        setPinnedBatteryExemptApps(context, current)
        return isNowPinned
    }
}
