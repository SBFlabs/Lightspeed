package com.sbf.lightspeed.system

import android.content.Context

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
                MethodOption(AUTO, "Automatic", "Accessibility with Shizuku fallback"),
                MethodOption(A11Y_ACTION, "Accessibility action", "performGlobalAction"),
                MethodOption(SHIZUKU, "Shizuku", "Direct input injection / lsinputd")
            )
            "system:split_screen" -> listOf(
                MethodOption(AUTO, "Automatic", "current fallback chain"),
                MethodOption(SHIZUKU, "Shizuku", ""),
                MethodOption(A11Y_ACTION, "Accessibility action", "system split action"),
                MethodOption(RECENTS_CLICK, "Recents click", "taps through Recents like a finger")
            )
            "system:close_app" -> listOf(
                MethodOption(AUTO, "Automatic", ""),
                MethodOption(SHIZUKU, "Shizuku", ""),
                MethodOption(RECENTS_DISMISS, "Recents dismiss", "uses the card's dismiss action")
            )
            "system:popup_window" -> listOf(
                MethodOption(AUTO, "Automatic", "current behavior"),
                MethodOption(SHIZUKU, "Shizuku", "native freeform launch"),
                MethodOption(RECENTS_BUTTON, "Recents button", "taps the pop-up button on the app's Recents card")
            )
            "system:scroll_to_top", "system:scroll_to_bottom" -> listOf(
                MethodOption(AUTO, "Automatic", "current fallback chain"),
                MethodOption(SCROLL_POSITION, "Scroll to position", "direct position jump"),
                MethodOption(SCROLL_PAGE, "Page scroll", "repeated page up/down"),
                MethodOption(SCROLL_STEP, "Step scroll", "repeated directional scroll"),
                MethodOption(SCROLL_SWIPE, "Swipe gesture", "simulated finger swipes")
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
            return "Auto"
        }
        val opts = options(token)
        val selectedTitles = opts.filter { selected.contains(it.id) }.map { it.title }
        return if (selectedTitles.isEmpty()) "Auto" else selectedTitles.joinToString(" + ")
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
        val result = mutableSetOf<String>()
        val allMethods = options(canon).map { it.id }
        for (m in allMethods) {
            val apps = getMethodApps(context, canon, m)
            if (packageName in apps) {
                result.add(m)
            }
        }
        return result
    }

    fun getAssignedAppsCount(context: Context, token: String, methodId: String): Int {
        return getMethodApps(context, token, methodId).size
    }
}
