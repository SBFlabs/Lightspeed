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
        "astrogation_airlock",
        "tactical_hardware",
        "synthetic_gravity",
        "refueling_bay",
        "system_overrides",
        "config_vault",
        "sonic_deck",
        "vista_deck",
        "experimental_labs"
    )

    val DEFAULT_ORDER_CSV: String = defaultOrder.joinToString(",")

    private val LEGACY_DEFAULT_ORDERS = setOf(
        "sensor_deck,telemetry_indicators,tactical_hardware,synthetic_gravity,astrogation_airlock,sonic_deck,vista_deck,refueling_bay,system_overrides,config_vault,experimental_labs",
        "sensor_deck,telemetry_indicators,tactical_hardware,synthetic_gravity,astrogation_airlock,refueling_bay,system_overrides,config_vault,experimental_labs",
        "sensor_deck,telemetry_indicators,tactical_hardware,synthetic_gravity,refueling_bay,system_overrides,config_vault,experimental_labs",
        "sensor_deck,telemetry_indicators,tactical_hardware,system_overrides,config_vault,experimental_labs"
    )

    fun resolveOrder(rawOrder: String, prefs: SharedPreferences): List<String> {
        val trimmed = rawOrder.trim()
        if (trimmed.isEmpty() || trimmed in LEGACY_DEFAULT_ORDERS) {
            if (trimmed != DEFAULT_ORDER_CSV) {
                prefs.edit().putString(LightspeedPreferences.KEY_TAB_SECTION_ORDER_1, DEFAULT_ORDER_CSV).apply()
            }
            return defaultOrder
        }

        val rawItems = trimmed.split(",").map { it.trim() }.filter { it in defaultOrder }.distinct()
        val current = (rawItems + (defaultOrder - rawItems.toSet())).toMutableList()

        var orderChanged = false
        if ("astrogation_airlock" !in rawItems) {
            val airlockIdx = current.indexOf("astrogation_airlock")
            val anchorIdx = current.indexOf("telemetry_indicators")
            if (airlockIdx != -1 && anchorIdx != -1 && airlockIdx != anchorIdx + 1) {
                current.removeAt(airlockIdx)
                current.add(current.indexOf("telemetry_indicators") + 1, "astrogation_airlock")
                orderChanged = true
            }
        }
        if ("synthetic_gravity" !in rawItems) {
            val idx = current.indexOf("synthetic_gravity")
            val tacticalIdx = current.indexOf("tactical_hardware")
            if (idx != -1 && tacticalIdx != -1 && idx != tacticalIdx + 1) {
                current.removeAt(idx)
                current.add(current.indexOf("tactical_hardware") + 1, "synthetic_gravity")
                orderChanged = true
            }
        }
        if ("refueling_bay" !in rawItems) {
            val refuelingIdx = current.indexOf("refueling_bay")
            val anchorIdx = current.indexOf("synthetic_gravity")
            if (refuelingIdx != -1 && anchorIdx != -1 && refuelingIdx != anchorIdx + 1) {
                current.removeAt(refuelingIdx)
                current.add(current.indexOf("synthetic_gravity") + 1, "refueling_bay")
                orderChanged = true
            }
        }
        if ("sonic_deck" !in rawItems) {
            val sonicIdx = current.indexOf("sonic_deck")
            val anchorIdx = current.indexOf("config_vault")
            if (sonicIdx != -1 && anchorIdx != -1 && sonicIdx != anchorIdx + 1) {
                current.removeAt(sonicIdx)
                current.add(current.indexOf("config_vault") + 1, "sonic_deck")
                orderChanged = true
            }
        }
        if ("vista_deck" !in rawItems) {
            val vistaIdx = current.indexOf("vista_deck")
            val anchorIdx = current.indexOf("sonic_deck")
            if (vistaIdx != -1 && anchorIdx != -1 && vistaIdx != anchorIdx + 1) {
                current.removeAt(vistaIdx)
                current.add(current.indexOf("sonic_deck") + 1, "vista_deck")
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
