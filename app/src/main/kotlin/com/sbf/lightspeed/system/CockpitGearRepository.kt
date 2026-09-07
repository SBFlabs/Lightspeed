package com.sbf.lightspeed.system

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context

object CockpitGearRepository {

    fun persistActiveGearSetIndex(context: Context, setIndex: Int, isLeft: Boolean) {
        val prefs = context.defaultPrefs()
        val editor = prefs.edit().putInt("last_active_set_index", setIndex)
        if (isLeft) {
            editor.putInt("last_active_set_index_left", setIndex)
        } else {
            editor.putInt("last_active_set_index_right", setIndex)
        }
        editor.apply()
    }

    fun getGearSetsOrder(context: Context, isLeft: Boolean): MutableList<String> {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "gear_sets_order_left" else "gear_sets_order_right"
        val saved = prefs.getString(key, null)
        if (!saved.isNullOrEmpty()) {
            return saved.split(",").filter { it.isNotEmpty() }.toMutableList()
        }
        val legacy = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        return legacy.split(",").filter { it.isNotEmpty() }.toMutableList()
    }

    fun saveGearSetsOrder(context: Context, isLeft: Boolean, list: List<String>) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "gear_sets_order_left" else "gear_sets_order_right"
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }

    fun getFlankLaunchBehavior(context: Context, isLeft: Boolean): String {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "cockpit_launch_behavior_left" else "cockpit_launch_behavior_right"
        val saved = prefs.getString(key, null)
        if (!saved.isNullOrEmpty()) {
            return saved
        }
        return prefs.getString("cockpit_launch_behavior", "default") ?: "default"
    }

    fun setFlankLaunchBehavior(context: Context, isLeft: Boolean, behavior: String) {
        val prefs = context.defaultPrefs()
        val key = if (isLeft) "cockpit_launch_behavior_left" else "cockpit_launch_behavior_right"
        prefs.edit().putString(key, behavior).apply()
    }

    fun getAppsForActiveGear(context: Context, isLeft: Boolean, setIndex: Int, ringIndex: Int): List<String> {
        val prefs = context.defaultPrefs()
        val setsList = getGearSetsOrder(context, isLeft)
        val setId = if (setIndex in setsList.indices) setsList[setIndex] else setIndex.toString()
        val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", null)
            ?: prefs.getString("gear_set_${setId}_ring${ringIndex}", null)
        if (!csvString.isNullOrEmpty()) {
            return csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return when (setId) {
            "0" -> if (ringIndex == 0) listOf("com.android.vending", "org.fdroid.fdroid") else listOf("com.termux", "com.android.settings")
            else -> emptyList()
        }
    }

    fun getGearSetNameById(context: Context, setId: String): String {
        val prefs = context.defaultPrefs()
        val defaultName = when(setId) {
            "0" -> "POWER USER ANDROID"
            "1" -> "MY APP STORES"
            "2" -> "UTILITIES SECTOR"
            "3" -> "ENTERTAINMENT DECK"
            else -> "CUSTOM SET"
        }
        val saved = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
        return if (saved == "SET A" || saved == "SET B" || saved == "SET C" || saved == "SET D" || saved == "SET" || saved.isEmpty()) defaultName else saved
    }

    fun getGearSetNameByIndex(context: Context, isLeft: Boolean, index: Int): String {
        val setsList = getGearSetsOrder(context, isLeft)
        if (index in setsList.indices) {
            return getGearSetNameById(context, setsList[index])
        }
        val alphabetLabel = if (index in 0..25) "${'A' + index}" else index.toString()
        return "SET " + alphabetLabel
    }
}
