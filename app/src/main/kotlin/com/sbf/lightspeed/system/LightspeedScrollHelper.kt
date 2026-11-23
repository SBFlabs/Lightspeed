package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.sbf.lightspeed.CruiseLayer
import com.sbf.lightspeed.LightspeedAccessibilityService

object LightspeedScrollHelper {
    private const val TAG = "LightspeedScrollHelper"
    private const val SEGMENT_DURATION_MS = 14L
    private const val FALLBACK_SINGLE_SWIPE_MS = 55L
    private const val SAFE_TOP_RATIO = 0.25f
    private const val SAFE_BOTTOM_RATIO = 0.75f
    private const val MAX_CHAINED_FLINGS = 4
    private const val CHAIN_FLING_GAP_MS = 220L
    private const val MAX_BFS_NODES = 200
    private const val MAX_BOUNDARY_BFS_NODES = 60

    // Accelerating stroke fractions: clears touchSlop gently on segment 0, then ramps sharply to peak terminal velocity at pointer-up.
    private val FLING_SEGMENT_FRACTIONS = floatArrayOf(0.00f, 0.08f, 0.28f, 0.60f, 1.00f)

    private val KNOWN_BROWSER_PACKAGES = setOf(
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "com.android.chrome",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.duckduckgo.mobile.android"
    )
    private val KNOWN_BROWSER_PREFIXES = arrayOf(
        "com.brave.browser",
        "com.chrome.",
        "org.mozilla.",
        "com.opera.",
        "com.vivaldi.",
        "com.kiwibrowser.",
        "org.cromite.",
        "app.vanadium."
    )

    private val handler = Handler(Looper.getMainLooper())
    private var activeFlingSession: FlingSession? = null

    private fun isKnownBrowserPackage(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (pkg in KNOWN_BROWSER_PACKAGES) return true
        return KNOWN_BROWSER_PREFIXES.any { pkg.startsWith(it) }
    }

    private fun isBrowserWebViewNode(node: AccessibilityNodeInfo): Boolean {
        val cls = node.className?.toString() ?: return false
        return cls.contains("webkit.", ignoreCase = true) ||
            cls.contains("chromium", ignoreCase = true) ||
            cls.contains("gecko", ignoreCase = true) ||
            (cls.contains("web", ignoreCase = true) && cls.contains("view", ignoreCase = true))
    }

    private fun getFrontPackage(service: AccessibilityService): String? {
        val ownPkg = service.packageName
        LightspeedAccessibilityService.lastAppPackage
            ?.takeIf { it.isNotBlank() && it != ownPkg && it != "com.android.systemui" }
            ?.let { return it }

        service.rootInActiveWindow?.let { root ->
            val pkg = root.packageName?.toString()
            @Suppress("DEPRECATION") root.recycle()
            if (!pkg.isNullOrBlank() && pkg != ownPkg && pkg != "com.android.systemui") {
                return pkg
            }
        }

        return service.windows
            ?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isActive }
            ?.root
            ?.let { root ->
                val pkg = root.packageName?.toString()
                @Suppress("DEPRECATION") root.recycle()
                pkg?.takeIf { it.isNotBlank() && it != ownPkg && it != "com.android.systemui" }
            }
    }

    fun cancelPendingSwipes() {
        activeFlingSession?.cancel()
        activeFlingSession = null
        handler.removeCallbacksAndMessages(null)
    }

    fun scroll(context: Context, toBottom: Boolean) {
        cancelPendingSwipes()
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return
        val token = if (toBottom) "system:scroll_to_bottom" else "system:scroll_to_top"
        val siblingToken = if (toBottom) "system:scroll_to_top" else "system:scroll_to_bottom"
        val frontPackage = getFrontPackage(service)

        val assignedMethods = if (!frontPackage.isNullOrBlank()) {
            ActionMethods.getAssignedMethodsForApp(context, token, frontPackage)
                .ifEmpty { ActionMethods.getAssignedMethodsForApp(context, siblingToken, frontPackage) }
        } else {
            emptySet()
        }

        val isAuto: Boolean
        val usePosition: Boolean
        val usePage: Boolean
        val useStep: Boolean
        val useSwipe: Boolean

        if (assignedMethods.isNotEmpty()) {
            isAuto = ActionMethods.AUTO in assignedMethods
            usePosition = isAuto || ActionMethods.SCROLL_POSITION in assignedMethods
            usePage = isAuto || ActionMethods.SCROLL_PAGE in assignedMethods
            useStep = isAuto || ActionMethods.SCROLL_STEP in assignedMethods
            useSwipe = isAuto || ActionMethods.SCROLL_SWIPE in assignedMethods
        } else {
            val methods = ActionMethods.getSelected(context, token)
            isAuto = ActionMethods.AUTO in methods
            usePosition = isAuto || ActionMethods.SCROLL_POSITION in methods
            usePage = isAuto || ActionMethods.SCROLL_PAGE in methods
            useStep = isAuto || ActionMethods.SCROLL_STEP in methods
            useSwipe = isAuto || ActionMethods.SCROLL_SWIPE in methods
        }

        Log.d(TAG, "scroll(toBottom=$toBottom, pkg=$frontPackage, assigned=$assignedMethods, auto=$isAuto, swipe=$useSwipe)")

        var anyNodeMoved = false

        if (usePosition || usePage || useStep) {
            val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()
            try {
                val activeRoot = service.rootInActiveWindow
                if (activeRoot != null) {
                    val activePkg = activeRoot.packageName?.toString()
                    if (frontPackage.isNullOrBlank() || activePkg == frontPackage || (activePkg != service.packageName && activePkg != "com.android.systemui")) {
                        collectScrollableNodes(activeRoot, scrollableNodes, toBottom)
                    }
                    @Suppress("DEPRECATION")
                    activeRoot.recycle()
                }

                if (scrollableNodes.isEmpty()) {
                    val windows = service.windows
                    if (!windows.isNullOrEmpty()) {
                        for (window in windows) {
                            if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                                val root = window.root ?: continue
                                val wPkg = root.packageName?.toString()
                                if (frontPackage.isNullOrBlank() || wPkg == frontPackage) {
                                    collectScrollableNodes(root, scrollableNodes, toBottom)
                                }
                                @Suppress("DEPRECATION")
                                root.recycle()
                                if (scrollableNodes.isNotEmpty()) break
                            }
                        }
                    }
                }

                if (scrollableNodes.isNotEmpty()) {
                    // Order candidates by visible on-screen area, biggest first
                    val targetNodes = scrollableNodes.sortedByDescending { node ->
                        val bounds = Rect()
                        node.getBoundsInScreen(bounds)
                        bounds.width().toLong() * bounds.height().toLong()
                    }
                    val isKnownBrowser = isKnownBrowserPackage(frontPackage)

                    for (node in targetNodes) {
                        try {
                            var nodeMoved = false

                            if (usePosition && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                val bundle = Bundle().apply {
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, if (toBottom) Int.MAX_VALUE else 0)
                                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT, 0)
                                }
                                val posSuccess = node.performAction(
                                    AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,
                                    bundle
                                )
                                if (posSuccess) {
                                    nodeMoved = true
                                }
                            }

                            // In AUTO mode, browsers/Chromium ignore ACTION_SCROLL_TO_POSITION and handle
                            // ACTION_PAGE/ACTION_SCROLL asynchronously (moving only ~1 viewport without fling).
                            // Skip those on browser or embedded web nodes so AUTO executes the high-velocity swipe fling.
                            val skipFallbackLoopInAuto = isAuto && !nodeMoved && (isKnownBrowser || isBrowserWebViewNode(node))
                            if (!nodeMoved && !skipFallbackLoopInAuto && (usePage || useStep)) {
                                var passes = 0
                                while (passes < 25) {
                                    var moved = false
                                    if (usePage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        val actionId = if (toBottom) {
                                            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id
                                        } else {
                                            AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id
                                        }
                                        moved = node.performAction(actionId)
                                    }
                                    if (!moved && useStep) {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            val actionId = if (toBottom) {
                                                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
                                            } else {
                                                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
                                            }
                                            moved = node.performAction(actionId)
                                        }
                                        if (!moved && !hasHorizontalScrollAction(node)) {
                                            val actionId = if (toBottom) {
                                                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                                            } else {
                                                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                                            }
                                            moved = node.performAction(actionId)
                                        }
                                    }
                                    if (!moved) break
                                    nodeMoved = true
                                    passes++
                                }
                            }

                            if (nodeMoved) {
                                anyNodeMoved = true
                                // Act on the biggest first, stop at the first node that actually moves
                                break
                            }
                        } catch (e: Exception) {
                            logSwallowed(TAG, if (toBottom) "scrollToBottom" else "scrollToTop", e)
                        }
                    }
                }
            } finally {
                for (n in scrollableNodes) {
                    @Suppress("DEPRECATION")
                    n.recycle()
                }
            }
        }

        if ((isAuto || useSwipe) && !anyNodeMoved) {
            swipeScroll(service, toBottom, frontPackage)
        }
    }

    private fun isOverlayIdle(): Boolean {
        val a11y = LightspeedAccessibilityService.instance ?: return true
        val overlay = a11y.overlayView ?: return true
        return (overlay.currentLayer == CruiseLayer.NEUTRAL || overlay.currentLayer == CruiseLayer.HIDDEN) &&
            !overlay.isEditModeOpen
    }

    private class FlingSession(
        private val service: AccessibilityService,
        private val toBottom: Boolean,
        private val initialPackage: String?
    ) {
        @Volatile
        var isCancelled = false
        private var pendingChainRunnable: Runnable? = null

        fun cancel() {
            isCancelled = true
            pendingChainRunnable?.let { handler.removeCallbacks(it) }
            pendingChainRunnable = null
        }

        fun start() {
            dispatchFling(flingIndex = 0)
        }

        private fun dispatchFling(flingIndex: Int) {
            if (isCancelled || !isOverlayIdle()) {
                cancelPendingSwipes()
                return
            }

            val displayMetrics = service.resources.displayMetrics
            val width = displayMetrics.widthPixels.toFloat()
            val height = displayMetrics.heightPixels.toFloat()
            if (width <= 0f || height <= 0f) {
                cancelPendingSwipes()
                return
            }

            val centerX = width * 0.5f
            val startY = if (toBottom) height * SAFE_BOTTOM_RATIO else height * SAFE_TOP_RATIO
            val endY = if (toBottom) height * SAFE_TOP_RATIO else height * SAFE_BOTTOM_RATIO
            val spanY = endY - startY

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                dispatchSegment(
                    flingIndex = flingIndex,
                    segmentIndex = 0,
                    prevStroke = null,
                    centerX = centerX,
                    startY = startY,
                    spanY = spanY
                )
            } else {
                val path = Path().apply {
                    moveTo(centerX, startY)
                    lineTo(centerX, endY)
                }
                val stroke = GestureDescription.StrokeDescription(path, 0L, FALLBACK_SINGLE_SWIPE_MS)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGestureStep(gesture) {
                    onFlingCycleCompleted(flingIndex)
                }
            }
        }

        private fun dispatchSegment(
            flingIndex: Int,
            segmentIndex: Int,
            prevStroke: GestureDescription.StrokeDescription?,
            centerX: Float,
            startY: Float,
            spanY: Float
        ) {
            if (isCancelled || !isOverlayIdle()) {
                cancelPendingSwipes()
                return
            }
            val segmentCount = FLING_SEGMENT_FRACTIONS.size - 1
            val y0 = startY + spanY * FLING_SEGMENT_FRACTIONS[segmentIndex]
            val y1 = startY + spanY * FLING_SEGMENT_FRACTIONS[segmentIndex + 1]
            val willContinue = segmentIndex < segmentCount - 1

            val path = Path().apply {
                moveTo(centerX, y0)
                lineTo(centerX, y1)
            }
            val stroke = if (prevStroke == null) {
                GestureDescription.StrokeDescription(path, 0L, SEGMENT_DURATION_MS, willContinue)
            } else {
                prevStroke.continueStroke(path, 0L, SEGMENT_DURATION_MS, willContinue)
            }
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGestureStep(gesture) {
                if (willContinue) {
                    dispatchSegment(flingIndex, segmentIndex + 1, stroke, centerX, startY, spanY)
                } else {
                    Log.d(TAG, "fling #$flingIndex completed (toBottom=$toBottom, pkg=$initialPackage)")
                    onFlingCycleCompleted(flingIndex)
                }
            }
        }

        private fun dispatchGestureStep(gesture: GestureDescription, onComplete: () -> Unit) {
            if (isCancelled) return
            try {
                val dispatched = service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            if (!isCancelled) {
                                onComplete()
                            }
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            super.onCancelled(gestureDescription)
                            cancelPendingSwipes()
                        }
                    },
                    handler
                )
                if (!dispatched) {
                    Log.w(TAG, "swipeScroll: dispatchGesture returned false")
                    cancelPendingSwipes()
                }
            } catch (e: Exception) {
                logSwallowed(TAG, "swipeScroll", e)
                cancelPendingSwipes()
            }
        }

        private fun onFlingCycleCompleted(flingIndex: Int) {
            if (isCancelled) return
            if (flingIndex + 1 >= MAX_CHAINED_FLINGS) {
                cancelPendingSwipes()
                return
            }
            val chainTask = Runnable {
                pendingChainRunnable = null
                if (isCancelled || !isOverlayIdle()) {
                    cancelPendingSwipes()
                    return@Runnable
                }
                val currentPkg = getFrontPackage(service)
                if (!initialPackage.isNullOrBlank() && !currentPkg.isNullOrBlank() && currentPkg != initialPackage) {
                    cancelPendingSwipes()
                    return@Runnable
                }
                if (!canStillScrollInDirection(service, initialPackage, toBottom)) {
                    Log.d(TAG, "stop chained fling after #$flingIndex: boundary reached (toBottom=$toBottom)")
                    cancelPendingSwipes()
                    return@Runnable
                }
                dispatchFling(flingIndex + 1)
            }
            pendingChainRunnable = chainTask
            handler.postDelayed(chainTask, CHAIN_FLING_GAP_MS)
        }
    }

    private fun swipeScroll(service: AccessibilityService, toBottom: Boolean, initialPackage: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (!isOverlayIdle()) return

        cancelPendingSwipes()
        val session = FlingSession(service, toBottom, initialPackage)
        activeFlingSession = session
        session.start()
    }

    /**
     * Checks strictly directional scroll actions ONLY (never generic [AccessibilityNodeInfo.isScrollable]
     * or `ACTION_SCROLL_TO_POSITION`, which stay true in Chromium even at scrollY == 0).
     */
    private fun hasDirectionalScrollAction(node: AccessibilityNodeInfo, toBottom: Boolean): Boolean {
        val collectionInfo = node.collectionInfo
        if (collectionInfo != null && collectionInfo.columnCount > 1 && collectionInfo.rowCount <= 1) {
            return false
        }
        val actions = node.actionList
        val hasVertical = actions.any { action ->
            if (toBottom) {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id)
            } else {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id)
            }
        }
        if (hasVertical) return true

        val hasGeneric = actions.any { action ->
            if (toBottom) action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        return hasGeneric && !hasHorizontalScrollAction(node)
    }

    private fun canStillScrollInDirection(
        service: AccessibilityService,
        targetPkg: String?,
        toBottom: Boolean
    ): Boolean {
        val root = try {
            service.rootInActiveWindow
        } catch (_: Exception) {
            null
        } ?: return false

        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
        val bounds = Rect()
        val rootPkg = root.packageName?.toString()
        if (!targetPkg.isNullOrBlank() && !rootPkg.isNullOrBlank() && rootPkg != targetPkg) {
            @Suppress("DEPRECATION")
            root.recycle()
            return false
        }
        queue.addLast(root to true)
        var visited = 0

        try {
            while (queue.isNotEmpty() && visited < MAX_BOUNDARY_BFS_NODES) {
                val (current, shouldRecycle) = queue.removeFirst()
                visited++
                var foundMatch = false
                var isBrowserHost = false

                if (hasDirectionalScrollAction(current, toBottom)) {
                    current.getBoundsInScreen(bounds)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        foundMatch = true
                    }
                }
                if (!foundMatch) {
                    isBrowserHost = isBrowserWebViewNode(current)
                    if (!isBrowserHost && visited < MAX_BOUNDARY_BFS_NODES) {
                        val childCount = current.childCount
                        for (i in 0 until childCount) {
                            if (visited + queue.size >= MAX_BOUNDARY_BFS_NODES) break
                            val child = current.getChild(i) ?: continue
                            queue.addLast(child to true)
                        }
                    }
                }

                if (shouldRecycle) {
                    @Suppress("DEPRECATION")
                    current.recycle()
                }
                if (foundMatch) return true
            }
            return false
        } finally {
            while (queue.isNotEmpty()) {
                val (remaining, shouldRecycle) = queue.removeFirst()
                if (shouldRecycle) {
                    @Suppress("DEPRECATION")
                    remaining.recycle()
                }
            }
        }
    }

    private fun hasHorizontalScrollAction(node: AccessibilityNodeInfo): Boolean {
        // RangeInfo / CollectionInfo checks
        val rangeInfo = node.rangeInfo
        if (rangeInfo != null && rangeInfo.type == android.view.accessibility.AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT) {
            // Check if collection info indicates horizontal
        }
        val collectionInfo = node.collectionInfo
        if (collectionInfo != null && collectionInfo.columnCount > 1 && collectionInfo.rowCount <= 1) {
            return true
        }
        return node.actionList.any { action ->
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id))
        }
    }

    private fun canScrollVertically(node: AccessibilityNodeInfo, toBottom: Boolean): Boolean {
        val actions = node.actionList
        val hasVerticalAction = actions.any { action ->
            if (toBottom) {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
            } else {
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id) ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id)
            }
        }

        val hasHorizontalAction = actions.any { action ->
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT.id)) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_LEFT.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_RIGHT.id))
        }

        val collectionInfo = node.collectionInfo
        if (collectionInfo != null && collectionInfo.columnCount > 1 && collectionInfo.rowCount <= 1) {
            // Horizontal collection/pager
            return false
        }

        // If only supported scroll directions are horizontal, skip
        if (hasHorizontalAction && !hasVerticalAction) {
            return false
        }

        if (hasVerticalAction) {
            return true
        }

        // Generic scrollable node with ACTION_SCROLL_FORWARD / BACKWARD
        val hasGenericScroll = actions.any { action ->
            if (toBottom) {
                action.id == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            } else {
                action.id == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            }
        }

        // Only allow generic scroll forward/backward if node is scrollable and has NO horizontal actions
        return (node.isScrollable || hasGenericScroll) && !hasHorizontalAction
    }

    internal fun collectScrollableNodes(
        node: AccessibilityNodeInfo,
        list: MutableList<AccessibilityNodeInfo>,
        toBottom: Boolean = false
    ) {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>()
        queue.addLast(node to false)
        var visited = 0

        try {
            while (queue.isNotEmpty() && visited < MAX_BFS_NODES) {
                val (current, shouldRecycle) = queue.removeFirst()
                visited++
                var isScrollableBrowserHost = false

                if (canScrollVertically(current, toBottom)) {
                    val copy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        AccessibilityNodeInfo(current)
                    } else {
                        @Suppress("DEPRECATION")
                        AccessibilityNodeInfo.obtain(current)
                    }
                    list.add(copy)
                    val cls = current.className?.toString()
                    isScrollableBrowserHost = cls != null && (
                        cls.contains("webkit.", ignoreCase = true) ||
                        cls.contains("chromium", ignoreCase = true) ||
                        (cls.contains("web", ignoreCase = true) && cls.contains("view", ignoreCase = true))
                    )
                }

                // Skip descending into browser DOM host children (hundreds/thousands of inner HTML nodes)
                if (!isScrollableBrowserHost && visited < MAX_BFS_NODES) {
                    val childCount = current.childCount
                    for (i in 0 until childCount) {
                        if (visited + queue.size >= MAX_BFS_NODES) break
                        val child = current.getChild(i) ?: continue
                        queue.addLast(child to true)
                    }
                }

                if (shouldRecycle) {
                    @Suppress("DEPRECATION")
                    current.recycle()
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                val (remaining, shouldRecycle) = queue.removeFirst()
                if (shouldRecycle) {
                    @Suppress("DEPRECATION")
                    remaining.recycle()
                }
            }
        }
    }
}
