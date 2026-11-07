package com.sbf.lightspeed.system.bot

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedForegroundTracker
import com.sbf.lightspeed.system.TaskInspector

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
     * Resolves the primary underlying application window.
     * Iterates service.windows in topmost-first order, picking the first window where
     * window.type == AccessibilityWindowInfo.TYPE_APPLICATION and bounds height >= 50% screen height,
     * effectively ignoring floating overlay panels (e.g. Ramblr, SmartPanel), system UI, input methods, and own overlays.
     */
    fun resolveUnderlyingAppWindow(service: AccessibilityService?): AccessibilityWindowInfo? {
        if (service == null) return null
        val windows = try { service.windows } catch (_: Exception) { null } ?: return null
        if (windows.isEmpty()) return null

        val metrics = service.resources.displayMetrics
        val screenH = metrics.heightPixels
        val minHeight = (screenH * 0.5f).toInt()
        val ownPkg = service.packageName

        val rect = Rect()
        // Topmost window has the highest layer in AccessibilityService window stack
        val sorted = try {
            windows.sortedByDescending { it.layer }
        } catch (_: Exception) {
            windows.reversed()
        }

        for (win in sorted) {
            if (win.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue

            win.getBoundsInScreen(rect)
            if (rect.height() < minHeight) continue

            val r = try { win.root } catch (_: Exception) { null } ?: continue
            val pkg = r.packageName?.toString() ?: continue
            if (pkg.isBlank() || pkg == ownPkg || pkg == "com.android.systemui") continue
            if (LightspeedForegroundTracker.isInputMethodPackage(service, pkg)) continue

            return win
        }
        return null
    }

    fun isLayoutContainer(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className.endsWith("Layout") ||
                className.endsWith("ViewGroup") ||
                className.contains("LinearLayout") ||
                className.contains("FrameLayout") ||
                className.contains("RelativeLayout") ||
                className.contains("CoordinatorLayout") ||
                className.contains("ConstraintLayout") ||
                className.contains("DrawerLayout")
    }

    fun isPreferredLeafWidget(className: String?): Boolean {
        if (className.isNullOrBlank()) return false
        return className.endsWith("Button") ||
                className.endsWith("ImageButton") ||
                className.endsWith("ImageView") ||
                className.endsWith("TextView") ||
                className.endsWith("CheckBox") ||
                className.endsWith("Switch") ||
                className.contains("ActionMenuItem") ||
                className.contains("Tab")
    }

    /**
     * Deepest Child Hit-Testing:
     * Traverses the node hierarchy containing (x, y) and picks the deepest, smallest actionable node.
     * Prioritizes clickable leaf widgets (Button, ImageButton, ImageView, TextView) over layout containers (LinearLayout, FrameLayout).
     */
    fun findDeepestNodeAt(root: AccessibilityNodeInfo?, x: Float, y: Float): AccessibilityNodeInfo? {
        if (root == null) return null
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val rect = Rect()
        val ix = x.toInt()
        val iy = y.toInt()

        var visited = 0
        while (queue.isNotEmpty() && visited < 450) {
            visited++
            val node = queue.removeFirst()
            node.getBoundsInScreen(rect)
            if (rect.contains(ix, iy)) {
                val hasId = !node.viewIdResourceName.isNullOrBlank()
                val hasText = !node.text.isNullOrBlank()
                val hasDesc = !node.contentDescription.isNullOrBlank()
                val clickable = node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }

                if (clickable || hasId || hasText || hasDesc) {
                    candidates.add(node)
                }
            }
            for (i in 0 until node.childCount) {
                try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
            }
        }

        if (candidates.isEmpty()) return null

        val nodeRect = Rect()
        return candidates.minWithOrNull(
            compareBy<AccessibilityNodeInfo> { node ->
                val cls = node.className?.toString() ?: ""
                when {
                    isPreferredLeafWidget(cls) -> 0
                    !isLayoutContainer(cls) -> 1
                    else -> 2
                }
            }.thenByDescending { node ->
                node.isClickable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_CLICK }
            }.thenBy { node ->
                node.getBoundsInScreen(nodeRect)
                nodeRect.width().toLong() * nodeRect.height().toLong()
            }
        )
    }

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
            val underlyingWin = resolveUnderlyingAppWindow(service)
            val underlyingRoot = underlyingWin?.root
            if (underlyingRoot != null) {
                roots.add(underlyingRoot)
            } else {
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

        // Prioritize Burger Menus / Android Menus first, then leaf widgets over layout containers, then smaller specific targets
        val sorted = candidates.sortedWith(
            compareByDescending<DetectedUiTarget> { it.isMenuOrNav }
                .thenBy { isLayoutContainer(it.className) }
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
            .minWithOrNull(
                compareBy<DetectedUiTarget> { isLayoutContainer(it.className) }
                    .thenBy { it.bounds.width().toLong() * it.bounds.height().toLong() }
            )
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

    fun isRealComponentClass(cls: String?): Boolean {
        if (cls.isNullOrBlank()) return false
        if (cls.startsWith("android.widget.") ||
            cls.startsWith("android.view.") ||
            cls.startsWith("com.android.internal.") ||
            cls.startsWith("android.inputmethodservice.")
        ) return false
        if (cls.startsWith("com.sbf.lightspeed")) return false
        if (cls.contains("FrameLayout") ||
            cls.contains("LinearLayout") ||
            cls.contains("RelativeLayout") ||
            cls.contains("DecorView") ||
            cls.contains("PopupWindow") ||
            cls.contains("Popup")
        ) return false
        return true
    }

    fun isGenericActivity(act: String?): Boolean {
        if (act.isNullOrBlank()) return true
        val lower = act.lowercase()
        return lower == "mainactivity" ||
                lower == "activity" ||
                lower == "componentactivity" ||
                lower == "fragmentactivity" ||
                lower == "appcompatactivity" ||
                lower == "baseactivity" ||
                lower == "containeractivity" ||
                lower == "trampolineactivity" ||
                lower == "framelayout"
    }

    private fun isIgnoredViewId(id: String): Boolean {
        val lower = id.lowercase()
        return lower == "content" ||
                lower == "parent" ||
                lower == "root" ||
                lower == "decor_content_parent" ||
                lower == "action_bar_root" ||
                lower == "action_bar_container" ||
                lower == "fl_content" ||
                lower == "action_bar" ||
                lower == "status_bar_container" ||
                lower == "navigation_bar_container"
    }

    fun sanitizeToken(raw: String?): String {
        if (raw.isNullOrBlank()) return "Main"
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(32)
        return if (cleaned.isBlank() || cleaned.equals("FrameLayout", ignoreCase = true)) "Main" else cleaned
    }

    /**
     * Deterministically resolves the active Window / Screen ID combining the verified Activity class
     * and screen-header / structure signatures from rootInActiveWindow.
     * Prevents FrameLayout pollution and distinguishes separate screens (e.g. DeckPicker vs Statistics in AnkiDroid).
     */
    fun resolveActiveWindowId(service: AccessibilityService?): String {
        if (service == null) {
            val cls = LightspeedForegroundTracker.currentActivityClass
            return sanitizeToken(cls?.substringAfterLast('.')?.removeSuffix("Activity") ?: "Main")
        }

        var shizukuActivityShort: String? = null
        if (ElevatedTaskCloser.isShizukuActive) {
            try {
                val taskInfo = TaskInspector.getTopForegroundTaskInfo(service)
                val comp = taskInfo.componentName
                if (!comp.isNullOrBlank()) {
                    val act = comp.substringAfterLast('/').removePrefix(".")
                    val shortAct = act.substringAfterLast('.').removeSuffix("Activity")
                    if (isRealComponentClass(shortAct)) {
                        shizukuActivityShort = shortAct
                    }
                }
            } catch (_: Exception) {}
        }

        val tracked = LightspeedForegroundTracker.currentActivityClass
        val trackedShort = tracked?.substringAfterLast('.')?.removeSuffix("Activity")

        val baseActivity = when {
            !shizukuActivityShort.isNullOrBlank() && !isGenericActivity(shizukuActivityShort) -> shizukuActivityShort
            !trackedShort.isNullOrBlank() && !isGenericActivity(trackedShort) -> trackedShort
            !shizukuActivityShort.isNullOrBlank() -> shizukuActivityShort
            !trackedShort.isNullOrBlank() -> trackedShort
            else -> null
        }

        var detectedHeaderTitle: String? = null
        val distinctIds = mutableListOf<String>()

        try {
            val appWin = resolveUnderlyingAppWindow(service)
            var root = appWin?.root ?: service.rootInActiveWindow
            val ownPkg = service.packageName
            if (root == null || root.packageName?.toString() == ownPkg) {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (win in windows) {
                        val r = try { win.root } catch (_: Exception) { null } ?: continue
                        val p = r.packageName?.toString()
                        if (p != ownPkg && p != "com.android.systemui") {
                            root = r
                            break
                        }
                    }
                }
            }

            if (root != null) {
                val metrics = service.resources.displayMetrics
                val topThreshold = (metrics.heightPixels * 0.22f).toInt()
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                queue.add(root)
                var visited = 0
                val rect = Rect()

                while (queue.isNotEmpty() && visited < 130) {
                    visited++
                    val node = queue.removeFirst()
                    node.getBoundsInScreen(rect)

                    val viewId = node.viewIdResourceName
                    val leafId = viewId?.substringAfterLast('/')
                    if (!leafId.isNullOrBlank() && !isIgnoredViewId(leafId)) {
                        if (!distinctIds.contains(leafId)) {
                            distinctIds.add(leafId)
                        }
                    }

                    if (detectedHeaderTitle == null && rect.top < topThreshold && rect.bottom > 0) {
                        val nodeCls = node.className?.toString() ?: ""
                        val lowerId = leafId?.lowercase() ?: ""
                        val isToolbarArea = lowerId.contains("title") ||
                                lowerId.contains("toolbar") ||
                                lowerId.contains("action_bar") ||
                                nodeCls.contains("Toolbar", ignoreCase = true) ||
                                lowerId.contains("header")
                        val rawText = node.text?.toString()?.trim()
                        val rawDesc = node.contentDescription?.toString()?.trim()
                        val candidate = if (!rawText.isNullOrBlank()) rawText else rawDesc

                        if (!candidate.isNullOrBlank() && candidate.length in 2..36) {
                            val lower = candidate.lowercase()
                            val isNavOrAction = lower.contains("navigate up") ||
                                    lower.contains("more options") ||
                                    lower.contains("search") ||
                                    lower.contains("drawer") ||
                                    lower.contains("close") ||
                                    lower.contains("back") ||
                                    lower.contains("cancel") ||
                                    lower.matches(Regex("""^\d{1,2}:\d{2}.*"""))

                            if (!isNavOrAction && (isToolbarArea || nodeCls.endsWith("TextView"))) {
                                detectedHeaderTitle = sanitizeToken(candidate)
                            }
                        }
                    }

                    for (i in 0 until node.childCount) {
                        try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        val resolved = when {
            detectedHeaderTitle != null -> {
                if (baseActivity == null || isGenericActivity(baseActivity)) {
                    detectedHeaderTitle
                } else if (detectedHeaderTitle.equals("Statistics", ignoreCase = true)) {
                    "Statistics"
                } else if (detectedHeaderTitle.equals(baseActivity, ignoreCase = true) ||
                    detectedHeaderTitle.equals("Decks", ignoreCase = true) ||
                    detectedHeaderTitle.equals("AnkiDroid", ignoreCase = true)
                ) {
                    baseActivity
                } else {
                    "${baseActivity}_$detectedHeaderTitle"
                }
            }
            !baseActivity.isNullOrBlank() && !isGenericActivity(baseActivity) -> baseActivity
            distinctIds.isNotEmpty() -> "${baseActivity ?: "Main"}_${distinctIds.first()}"
            else -> baseActivity ?: "Main"
        }

        val sanitized = sanitizeToken(resolved)
        return if (sanitized == "FrameLayout" || sanitized.isBlank()) "Main" else sanitized
    }
}
