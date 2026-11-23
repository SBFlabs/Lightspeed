package com.sbf.lightspeed.system

import android.content.Context
import com.sbf.lightspeed.R

data class MethodOption(
    val id: String,
    val title: String,
    val subtitle: String
)

object ActionMethods {
    const val AUTO = "auto"
    const val SHIZUKU = "shizuku"
    const val A11Y_ACTION = "a11y_action"
    const val RECENTS_CLICK = "recents_click"
    const val RECENTS_DISMISS = "recents_dismiss"
    const val RECENTS_BUTTON = "recents_button"
    const val SCROLL_POSITION = "scroll_position"
    const val SCROLL_PAGE = "scroll_page"
    const val SCROLL_STEP = "scroll_step"
    const val SCROLL_SWIPE = "scroll_swipe"

    private fun canonical(token: String): String {
        return if (token == "system:freeform") "system:popup_window" else token
    }

    fun isMethodAction(token: String): Boolean {
        val c = canonical(token)
        return c == "system:split_screen" || c == "system:close_app" || c == "system:popup_window" ||
                c == "system:scroll_to_top" || c == "system:scroll_to_bottom" || c == "system:back"
    }

    fun options(token: String): List<MethodOption> {
        return when (canonical(token)) {
            "system:back" -> listOf(
                MethodOption(AUTO, lsString(R.string.meth_automatic), lsString(R.string.meth_sub_a11y_shizuku)),
                MethodOption(A11Y_ACTION, lsString(R.string.meth_a11y_action), "performGlobalAction"),
                MethodOption(SHIZUKU, "Shizuku", lsString(R.string.meth_sub_direct_input))
            )
            "system:split_screen" -> listOf(
                MethodOption(AUTO, lsString(R.string.meth_automatic), lsString(R.string.meth_sub_fallback_chain)),
                MethodOption(SHIZUKU, "Shizuku", ""),
                MethodOption(A11Y_ACTION, lsString(R.string.meth_a11y_action), lsString(R.string.meth_sub_system_split)),
                MethodOption(RECENTS_CLICK, lsString(R.string.meth_recents_click), lsString(R.string.meth_sub_recents_click))
            )
            "system:close_app" -> listOf(
                MethodOption(AUTO, lsString(R.string.meth_automatic), ""),
                MethodOption(SHIZUKU, "Shizuku", ""),
                MethodOption(RECENTS_DISMISS, lsString(R.string.meth_recents_dismiss), lsString(R.string.meth_sub_recents_dismiss))
            )
            "system:popup_window" -> listOf(
                MethodOption(AUTO, lsString(R.string.meth_automatic), lsString(R.string.meth_sub_current_behavior)),
                MethodOption(SHIZUKU, "Shizuku", lsString(R.string.meth_sub_native_freeform)),
                MethodOption(RECENTS_BUTTON, lsString(R.string.meth_recents_button), lsString(R.string.meth_sub_recents_button))
            )
            "system:scroll_to_top", "system:scroll_to_bottom" -> listOf(
                MethodOption(AUTO, lsString(R.string.meth_automatic), lsString(R.string.meth_sub_fallback_chain)),
                MethodOption(SCROLL_POSITION, lsString(R.string.meth_scroll_position), lsString(R.string.meth_sub_scroll_pos)),
                MethodOption(SCROLL_PAGE, lsString(R.string.meth_scroll_page), lsString(R.string.meth_sub_scroll_page)),
                MethodOption(SCROLL_STEP, lsString(R.string.meth_scroll_step), lsString(R.string.meth_sub_scroll_step)),
                MethodOption(SCROLL_SWIPE, lsString(R.string.meth_scroll_swipe), lsString(R.string.meth_sub_scroll_swipe))
            )
            else -> emptyList()
        }
    }

    fun getSelected(context: Context, token: String): Set<String> {
        val canon = canonical(token)
        val raw = LightspeedPreferences.getActionBehavior(context, canon, AUTO)
        val validIds = setOf(AUTO, SHIZUKU, A11Y_ACTION, RECENTS_CLICK, RECENTS_DISMISS, RECENTS_BUTTON, SCROLL_POSITION, SCROLL_PAGE, SCROLL_STEP, SCROLL_SWIPE)
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && it in validIds }
            .toSet()
        return if (parsed.isEmpty()) setOf(AUTO) else parsed
    }

    fun toggle(context: Context, token: String, id: String): Set<String> {
        val canon = canonical(token)
        if (id == SHIZUKU && ShizukuGate.isAccessibilityOnly()) {
            return getSelected(context, canon)
        }
        val current = getSelected(context, canon).toMutableSet()
        if (id == AUTO) {
            current.clear()
            current.add(AUTO)
        } else {
            if (current.contains(id)) {
                current.remove(id)
                if (current.isEmpty()) {
                    current.add(AUTO)
                }
            } else {
                current.remove(AUTO)
                current.add(id)
            }
        }
        val newBehavior = current.joinToString(",")
        LightspeedPreferences.setActionBehavior(context, canon, newBehavior)
        return current
    }

    fun isOptionEnabled(id: String): Boolean {
        if (id == SHIZUKU && ShizukuGate.isAccessibilityOnly()) return false
        return true
    }

    fun summaryLabel(token: String, selected: Set<String>): String {
        if (selected.isEmpty() || selected.contains(AUTO)) {
            return lsString(R.string.meth_auto)
        }
        val opts = options(token)
        val selectedTitles = opts.filter { selected.contains(it.id) }.map { it.title }
        return if (selectedTitles.isEmpty()) lsString(R.string.meth_auto) else selectedTitles.joinToString(" + ")
    }

    fun getMethodAppsPrefKey(token: String, methodId: String): String {
        return "${LightspeedPreferences.KEY_ACTION_METHOD_APPS_PREFIX}${canonical(token)}_${methodId}"
    }

    fun getMethodApps(context: Context, token: String, methodId: String): Set<String> {
        val key = getMethodAppsPrefKey(token, methodId)
        val raw = context.defaultPrefs().getString(key, "") ?: ""
        return raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun setMethodApps(context: Context, token: String, methodId: String, apps: Set<String>) {
        val key = getMethodAppsPrefKey(token, methodId)
        val raw = apps.filter { it.isNotBlank() }.joinToString(",")
        context.defaultPrefs().edit().putString(key, raw).apply()
    }

    fun getAssignedMethodsForApp(context: Context, token: String, packageName: String): Set<String> {
        val canon = canonical(token)
        val cleanPkg = packageName.removePrefix("app:").trim()
        val result = mutableSetOf<String>()
        val allMethods = options(canon).map { it.id }
        for (m in allMethods) {
            val apps = getMethodApps(context, canon, m)
            if (cleanPkg in apps || "app:$cleanPkg" in apps) {
                result.add(m)
            }
        }
        return result
    }

    fun getAssignedAppsCount(context: Context, token: String, methodId: String): Int {
        return getMethodApps(context, token, methodId).size
    }
}
