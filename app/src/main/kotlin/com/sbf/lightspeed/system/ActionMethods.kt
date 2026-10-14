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

    private fun canonical(token: String): String {
        return if (token == "system:freeform") "system:popup_window" else token
    }

    fun isMethodAction(token: String): Boolean {
        val c = canonical(token)
        return c == "system:split_screen" || c == "system:close_app" || c == "system:popup_window"
    }

    fun options(token: String): List<MethodOption> {
        return when (canonical(token)) {
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
            else -> emptyList()
        }
    }

    fun getSelected(context: Context, token: String): Set<String> {
        val canon = canonical(token)
        val raw = LightspeedPreferences.getActionBehavior(context, canon, AUTO)
        val validIds = setOf(AUTO, SHIZUKU, A11Y_ACTION, RECENTS_CLICK, RECENTS_DISMISS, RECENTS_BUTTON)
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
}
