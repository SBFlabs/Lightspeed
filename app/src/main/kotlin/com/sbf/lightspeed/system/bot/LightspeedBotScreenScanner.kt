package com.sbf.lightspeed.system.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Clean-Room Smart Screen Element Detector for Cybernetic Trackpad & Bot Recorder.
 * Automatically detects Burger Menus, Android 3-Dot Overflow Menus, Toolbar icons,
 * Tabs, and all clickable/actionable UI elements on the active window.
 */
object LightspeedBotScreenScanner {

    const val TOKEN_BURGER_MENU = "@SMART_BURGER_MENU"
    const val TOKEN_ANDROID_MORE_MENU = "@SMART_MORE_MENU"
    const val TOKEN_SEARCH_BUTTON = "@SMART_SEARCH"
    const val TOKEN_NAV_BACK_UP = "@SMART_NAV_UP"

    data class DetectedUiTarget(
        val label: String,
        val isMenuOrNav: Boolean,
        val bounds: Rect,
        val viewIdResourceName: String?,
        val text: String?,
        val contentDescription: String?,
        val className: String?,
        val packageName: String?
    )

    private val burgerKeywords = listOf(
        "navigation drawer", "open navigation", "open drawer", "drawer",
        "burger", "main menu", "side menu", "show roots", "open menu"
    )

    private val moreMenuKeywords = listOf(
        "more options", "overflow", "more actions", "options menu", "action_menu"
    )

    private val navUpKeywords = listOf(
        "navigate up", "back", "close", "dismiss"
    )

    /**
     * Scans the active application window(s) and returns up to 28 distinct clickable/actionable
     * screen elements, prioritizing Burger Menus, Android 3-Dot Menus, and interactive controls.
     */
    fun scanScreenTargets(service: AccessibilityService?): List<DetectedUiTarget> {
        if (service == null) return emptyList()
        val metrics = service.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val ownPkg = service.packageName

        val roots = mutableListOf<AccessibilityNodeInfo>()
        try {
            val windows = service.windows
            if (!windows.isNullOrEmpty()) {
                for (win in windows) {
                    val r = try { win.root } catch (_: Exception) { null } ?: continue
                    val pkg = r.packageName?.toString() ?: ""
                    if (pkg != ownPkg && pkg != "com.android.systemui") {
                        roots.add(r)
                    }
                }
            }
            if (roots.isEmpty()) {
                service.rootInActiveWindow?.let { r ->
                    if (r.packageName?.toString() != ownPkg) {
                        roots.add(r)
                    }
                }
            }
        } catch (_: Exception) {}

        val candidates = mutableListOf<DetectedUiTarget>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        roots.forEach { queue.add(it) }

        var visited = 0
        while (queue.isNotEmpty() && visited < 450) {
            visited++
            val node = queue.removeFirst()
            val visible = try { node.isVisibleToUser } catch (_: Exception) { true }
            if (!visible) continue

            val rect = Rect()
            node.getBoundsInScreen(rect)
            val w = rect.width()
            val h = rect.height()

            if (w in 20..(screenW * 0.92f).toInt() && h in 20..(screenH * 0.35f).toInt()) {
                val viewId = node.viewIdResourceName
                val text = node.text?.toString()?.trim()
                val desc = node.contentDescription?.toString()?.trim()
                val cls = node.className?.toString() ?: ""
                val pkg = node.packageName?.toString()

                val clickable = node.isClickable ||
                        node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK } ||
                        cls.endsWith("Button", ignoreCase = true) ||
                        cls.endsWith("ImageButton", ignoreCase = true) ||
                        cls.contains("ActionMenuItem", ignoreCase = true) ||
                        cls.contains("Tab", ignoreCase = true)

                val combined = "${desc ?: ""} ${text ?: ""} ${viewId?.substringAfterLast('/') ?: ""}".lowercase()
                val isTopToolbarArea = rect.top < (screenH * 0.22f).toInt()
                val isBurger = burgerKeywords.any { combined.contains(it) } ||
                        (isTopToolbarArea && rect.left < (screenW * 0.22f).toInt() &&
                                (combined.contains("navigate up") || combined.contains("menu") ||
                                        (cls.endsWith("ImageButton") && desc.isNullOrBlank() && text.isNullOrBlank())))
                val isMoreMenu = moreMenuKeywords.any { combined.contains(it) } ||
                        (isTopToolbarArea && rect.right > (screenW * 0.78f).toInt() && combined.contains("more"))

                if (clickable || isBurger || isMoreMenu) {
                    val smartDesc = when {
                        isBurger && desc.isNullOrBlank() -> TOKEN_BURGER_MENU
                        isMoreMenu && desc.isNullOrBlank() -> TOKEN_ANDROID_MORE_MENU
                        else -> desc
                    }
                    val label = when {
                        isBurger -> "⌖ Burger Menu"
                        isMoreMenu -> "⌖ Android Menu"
                        !desc.isNullOrBlank() -> "⌖ ${desc.take(16)}"
                        !text.isNullOrBlank() -> "⌖ ${text.take(16)}"
                        !viewId.isNullOrBlank() -> "⌖ ${viewId.substringAfterLast('/').take(16)}"
                        cls.endsWith("ImageButton") && isTopToolbarArea -> "⌖ Toolbar Btn"
                        else -> ""
                    }
                    if (label.isNotEmpty()) {
                        candidates.add(
                            DetectedUiTarget(
                                label = label,
                                isMenuOrNav = isBurger || isMoreMenu || navUpKeywords.any { combined.contains(it) },
                                bounds = Rect(rect),
                                viewIdResourceName = viewId,
                                text = text,
                                contentDescription = smartDesc,
                                className = cls,
                                packageName = pkg
                            )
                        )
                    }
                }
            }

            val count = node.childCount
            for (i in 0 until count) {
                try {
                    node.getChild(i)?.let { queue.add(it) }
                } catch (_: Exception) {}
            }
        }

        // Prioritize Burger Menus / Android Menus first, then smaller specific targets, deduplicating overlapping boxes
        val sorted = candidates.sortedWith(
            compareByDescending<DetectedUiTarget> { it.isMenuOrNav }
                .thenBy { it.bounds.width().toLong() * it.bounds.height().toLong() }
        )

        val accepted = mutableListOf<DetectedUiTarget>()
        for (c in sorted) {
            val cx = c.bounds.centerX()
            val cy = c.bounds.centerY()
            val duplicate = accepted.any { existing ->
                kotlin.math.abs(existing.bounds.centerX() - cx) < 28 &&
                        kotlin.math.abs(existing.bounds.centerY() - cy) < 28
            }
            if (!duplicate) {
                accepted.add(c)
                if (accepted.size >= 26) break
            }
        }
        return accepted
    }

    fun findTargetAtPoint(targets: List<DetectedUiTarget>, x: Float, y: Float): DetectedUiTarget? {
        val ix = x.toInt()
        val iy = y.toInt()
        return targets
            .filter { it.bounds.contains(ix, iy) }
            .minByOrNull { it.bounds.width().toLong() * it.bounds.height().toLong() }
    }

    /**
     * Matches a recorded BotStep against the live AccessibilityNodeInfo tree, including
     * smart semantic matching for Burger Menus, Android 3-Dot Overflow Menus, and Navigation buttons.
     */
    fun findSmartMatchingNode(root: AccessibilityNodeInfo, step: BotStep, screenW: Int, screenH: Int): AccessibilityNodeInfo? {
        // 1. Exact View ID match
        if (!step.viewIdResourceName.isNullOrBlank()) {
            val nodes = try { root.findAccessibilityNodeInfosByViewId(step.viewIdResourceName) } catch (_: Exception) { null }
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        // 2. Exact Text match
        if (!step.text.isNullOrBlank()) {
            val nodes = try { root.findAccessibilityNodeInfosByText(step.text) } catch (_: Exception) { null }
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        // 3. Exact Content Description match
        val desc = step.contentDescription
        if (!desc.isNullOrBlank() && !desc.startsWith("@SMART_")) {
            val nodes = try { root.findAccessibilityNodeInfosByText(desc) } catch (_: Exception) { null }
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }

        // 4. Semantic Smart Token / Heuristic Menu Matching (Burger Menu, Android 3-Dot Menu, Back/Up, Search)
        val wantBurger = desc == TOKEN_BURGER_MENU ||
                burgerKeywords.any { (desc ?: "").lowercase().contains(it) || (step.text ?: "").lowercase().contains(it) }
        val wantMoreMenu = desc == TOKEN_ANDROID_MORE_MENU ||
                moreMenuKeywords.any { (desc ?: "").lowercase().contains(it) || (step.viewIdResourceName ?: "").lowercase().contains(it) }
        val wantSearch = desc == TOKEN_SEARCH_BUTTON || (desc ?: "").lowercase().contains("search")
        val wantNavUp = desc == TOKEN_NAV_BACK_UP || navUpKeywords.any { (desc ?: "").lowercase().contains(it) }

        if (!wantBurger && !wantMoreMenu && !wantSearch && !wantNavUp && desc.isNullOrBlank()) {
            return null
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val rect = Rect()
        var visited = 0
        while (queue.isNotEmpty() && visited < 350) {
            visited++
            val node = queue.removeFirst()
            node.getBoundsInScreen(rect)
            val nodeDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            val nodeText = node.text?.toString()?.lowercase() ?: ""
            val nodeId = node.viewIdResourceName?.lowercase() ?: ""
            val nodeCls = node.className?.toString() ?: ""
            val combined = "$nodeDesc $nodeText $nodeId"
            val isTopBar = rect.top < (screenH * 0.22f).toInt()

            if (wantBurger) {
                if (burgerKeywords.any { combined.contains(it) } ||
                    (isTopBar && rect.left < (screenW * 0.22f).toInt() &&
                            (combined.contains("navigate up") || combined.contains("menu") || nodeCls.endsWith("ImageButton")))
                ) {
                    return node
                }
            }
            if (wantMoreMenu) {
                if (moreMenuKeywords.any { combined.contains(it) } ||
                    (isTopBar && rect.right > (screenW * 0.78f).toInt() && combined.contains("more"))
                ) {
                    return node
                }
            }
            if (wantSearch && combined.contains("search")) {
                return node
            }
            if (wantNavUp && navUpKeywords.any { combined.contains(it) }) {
                return node
            }
            if (!desc.isNullOrBlank() && !desc.startsWith("@SMART_") && nodeDesc.contains(desc.lowercase())) {
                return node
            }

            for (i in 0 until node.childCount) {
                try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
            }
        }
        return null
    }
}
