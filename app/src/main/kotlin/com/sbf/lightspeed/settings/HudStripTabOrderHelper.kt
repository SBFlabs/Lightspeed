package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import com.sbf.lightspeed.system.LightspeedPreferences

/**
 * Manages accordion section ordering and migration for HUD Strip (Tab 1).
 */
object HudStripTabOrderHelper {

    val defaultOrder = listOf(
        "sensor_deck",
        "telemetry_indicators",
        "tactical_hardware",
        "synthetic_gravity",
        "refueling_bay",
        "system_overrides",
        "config_vault",
        "experimental_labs"
    )

    fun resolveOrder(rawOrder: String, prefs: SharedPreferences): List<String> {
        val current = rawOrder.split(",").map { it.trim() }.filter { it in defaultOrder }.distinct().let { list ->
            list + (defaultOrder - list.toSet())
        }.toMutableList()

        var orderChanged = false
        if (current.lastOrNull() == "system_overrides" && current.contains("config_vault")) {
            current.remove("system_overrides")
            current.add(current.indexOf("config_vault"), "system_overrides")
            orderChanged = true
        }
        if (!current.contains("synthetic_gravity")) {
            val tacticalIdx = current.indexOf("tactical_hardware")
            if (tacticalIdx != -1) current.add(tacticalIdx + 1, "synthetic_gravity")
            else current.add("synthetic_gravity")
            orderChanged = true
        } else {
            val tacticalIdx = current.indexOf("tactical_hardware")
            val gravityIdx = current.indexOf("synthetic_gravity")
            if (tacticalIdx != -1 && gravityIdx != -1 && gravityIdx != tacticalIdx + 1) {
                current.removeAt(gravityIdx)
                val newTacticalIdx = current.indexOf("tactical_hardware")
                current.add(newTacticalIdx + 1, "synthetic_gravity")
                orderChanged = true
            }
        }
        val gravityIdx = current.indexOf("synthetic_gravity")
        val refuelingIdx = current.indexOf("refueling_bay")
        if (gravityIdx != -1) {
            if (refuelingIdx == -1) {
                current.add(gravityIdx + 1, "refueling_bay")
                orderChanged = true
            } else if (refuelingIdx != gravityIdx + 1) {
                current.removeAt(refuelingIdx)
                val newGravityIdx = current.indexOf("synthetic_gravity")
                current.add(newGravityIdx + 1, "refueling_bay")
                orderChanged = true
            }
        }
        if (orderChanged) {
            val newStr = current.joinToString(",")
            prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
        }
        return current
    }

    fun moveUp(list: MutableList<String>, idx: Int, prefs: SharedPreferences): String {
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(idx - 1, item)
            val newStr = list.joinToString(",")
            prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
            return newStr
        }
        return list.joinToString(",")
    }

    fun moveDown(list: MutableList<String>, idx: Int, prefs: SharedPreferences): String {
        if (idx < list.size - 1) {
            val item = list.removeAt(idx)
            list.add(idx + 1, item)
            val newStr = list.joinToString(",")
            prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, newStr).apply()
            return newStr
        }
        return list.joinToString(",")
    }
}
