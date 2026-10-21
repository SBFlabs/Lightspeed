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
    private const val SWIPE_DURATION_MS = 220L
    private const val SWIPE_GAP_MS = 200L

    private val handler = Handler(Looper.getMainLooper())
    private var activeStepRunnable: StepRunnable? = null

    private fun getFrontPackage(service: AccessibilityService): String? {
        return LightspeedAccessibilityService.lastAppPackage
            ?: service.rootInActiveWindow?.let { root ->
                val pkg = root.packageName?.toString()
                @Suppress("DEPRECATION") root.recycle()
                pkg
            }
            ?: service.windows?.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isActive }?.root?.let { root ->
                val pkg = root.packageName?.toString()
                @Suppress("DEPRECATION") root.recycle()
                pkg
            }
    }

    fun cancelPendingSwipes() {
        activeStepRunnable?.let { runnable ->
            runnable.isCancelled = true
            handler.removeCallbacks(runnable)
            activeStepRunnable = null
        }
    }

    fun scroll(context: Context, toBottom: Boolean) {
        cancelPendingSwipes()
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return
        val token = if (toBottom) "system:scroll_to_bottom" else "system:scroll_to_top"
        val frontPackage = getFrontPackage(service)

        val assignedMethods = if (!frontPackage.isNullOrBlank()) {
            ActionMethods.getAssignedMethodsForApp(context, token, frontPackage)
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

        var anyNodeMoved = false

        if (usePosition || usePage || useStep) {
            val scrollableNodes = mutableListOf<AccessibilityNodeInfo>()
            val windows = service.windows

            if (!windows.isNullOrEmpty()) {
                for (window in windows) {
                    if (window.type == AccessibilityWindowInfo.TYPE_APPLICATION || window.isActive) {
                        val root = window.root ?: continue
                        collectScrollableNodes(root, scrollableNodes, toBottom)
                    }
                }
            }

            if (scrollableNodes.isEmpty()) {
                service.rootInActiveWindow?.let { root ->
                    collectScrollableNodes(root, scrollableNodes, toBottom)
                }
            }

            if (scrollableNodes.isNotEmpty()) {
                // Order candidates by visible on-screen area, biggest first
                val targetNodes = scrollableNodes.sortedByDescending { node ->
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    bounds.width().toLong() * bounds.height().toLong()
                }

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

                        if (!nodeMoved && (usePage || useStep)) {
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
        }

        if ((isAuto || useSwipe) && !anyNodeMoved) {
            swipeScroll(service, toBottom, frontPackage)
        }
    }

    private fun isVerticalScrollableCandidate(node: AccessibilityNodeInfo): Boolean {
        if (hasHorizontalScrollAction(node)) return false
        val collectionInfo = node.collectionInfo
        if (collectionInfo != null && collectionInfo.columnCount > 1 && collectionInfo.rowCount <= 1) {
            return false
        }
        val actions = node.actionList
        val hasVerticalAction = actions.any { action ->
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                (action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_UP.id ||
                 action.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_PAGE_DOWN.id))
        }
        return node.isScrollable || hasVerticalAction
    }

    private fun hasVerticalScrollActionCandidate(node: AccessibilityNodeInfo): Boolean {
        if (isVerticalScrollableCandidate(node)) {
            val actions = node.actionList
            val hasVerticalQAction = actions.any {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id ||
                     it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id)
            }
            if (hasVerticalQAction) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = hasVerticalScrollActionCandidate(child)
            @Suppress("DEPRECATION")
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun isBoundaryCheckPossible(service: AccessibilityService): Boolean {
        var root: AccessibilityNodeInfo? = null
        return try {
            root = service.rootInActiveWindow ?: service.windows?.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isActive
            }?.root ?: return false
            hasVerticalScrollActionCandidate(root)
        } catch (e: Exception) {
            logSwallowed(TAG, "isBoundaryCheckPossible", e)
            false
        } finally {
            @Suppress("DEPRECATION")
            root?.recycle()
        }
    }

    private fun findTargetScrollableNode(
        node: AccessibilityNodeInfo,
        bestSoFar: Pair<AccessibilityNodeInfo, Long>?
    ): Pair<AccessibilityNodeInfo, Long>? {
        var currentBest = bestSoFar
        if (isVerticalScrollableCandidate(node)) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val area = bounds.width().toLong() * bounds.height().toLong()
            if (currentBest == null || area > currentBest.second) {
                @Suppress("DEPRECATION")
                currentBest?.first?.recycle()
                val copy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    AccessibilityNodeInfo(node)
                } else {
                    @Suppress("DEPRECATION")
                    AccessibilityNodeInfo.obtain(node)
                }
                currentBest = Pair(copy, area)
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            currentBest = findTargetScrollableNode(child, currentBest)
            @Suppress("DEPRECATION")
            child.recycle()
        }
        return currentBest
    }

    private fun hasReachedBoundary(service: AccessibilityService, toBottom: Boolean): Boolean {
        var root: AccessibilityNodeInfo? = null
        var targetNode: AccessibilityNodeInfo? = null
        try {
            root = service.rootInActiveWindow ?: service.windows?.firstOrNull {
                it.type == AccessibilityWindowInfo.TYPE_APPLICATION || it.isActive
            }?.root ?: return false

            val best = findTargetScrollableNode(root, null)
            targetNode = best?.first ?: return false

            val actions = targetNode.actionList
            val hasScrollUp = actions.any {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id
            }
            val hasScrollDown = actions.any {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id
            }

            if (!hasScrollUp && !hasScrollDown) {
                return false
            }

            return if (toBottom) {
                hasScrollUp && !hasScrollDown
            } else {
                hasScrollDown && !hasScrollUp
            }
        } catch (e: Exception) {
            logSwallowed(TAG, "hasReachedBoundary", e)
            return false
        } finally {
            @Suppress("DEPRECATION")
            targetNode?.recycle()
            @Suppress("DEPRECATION")
            root?.recycle()
        }
    }

    private class StepRunnable(
        private val service: AccessibilityService,
        private val gesture: GestureDescription,
        private val toBottom: Boolean,
        private val canCheckBoundary: Boolean,
        private val initialPackage: String?
    ) : Runnable {
        private val maxSwipes = 3
        var swipeCount = 0
        @Volatile
        var isCancelled = false

        override fun run() {
            if (isCancelled) return
            if (swipeCount >= maxSwipes) {
                cancelPendingSwipes()
                return
            }
            if (swipeCount > 0) {
                val currentPkg = getFrontPackage(service)
                if (initialPackage != null && currentPkg != null && currentPkg != initialPackage) {
                    cancelPendingSwipes()
                    return
                }
            }
            swipeCount++
            try {
                val dispatched = service.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            super.onCompleted(gestureDescription)
                            if (isCancelled) return
                            if (canCheckBoundary && hasReachedBoundary(service, toBottom)) {
                                cancelPendingSwipes()
                                return
                            }
                            if (swipeCount < maxSwipes) {
                                val currentPkg = getFrontPackage(service)
                                if (initialPackage != null && currentPkg != null && currentPkg != initialPackage) {
                                    cancelPendingSwipes()
                                    return
                                }
                                handler.postDelayed(this@StepRunnable, SWIPE_GAP_MS)
                            } else {
                                cancelPendingSwipes()
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
                    Log.w(TAG, "swipeScroll: dispatchGesture returned false on swipe $swipeCount")
                    cancelPendingSwipes()
                }
            } catch (e: Exception) {
                logSwallowed(TAG, "swipeScroll", e)
                cancelPendingSwipes()
            }
        }
    }

    private fun swipeScroll(service: AccessibilityService, toBottom: Boolean, initialPackage: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val a11y = LightspeedAccessibilityService.instance
        if (a11y?.overlayView?.let { it.currentLayer != CruiseLayer.NEUTRAL && it.currentLayer != CruiseLayer.HIDDEN } == true ||
            a11y?.overlayView?.isEditModeOpen == true) {
            return
        }

        val displayMetrics = service.resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()
        if (width <= 0f || height <= 0f) return

        val centerX = width * 0.5f
        val startY = if (toBottom) height * 0.85f else height * 0.35f
        val endY = if (toBottom) height * 0.35f else height * 0.85f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        cancelPendingSwipes()
        val canCheckBoundary = isBoundaryCheckPossible(service)
        val stepRunnable = StepRunnable(service, gesture, toBottom, canCheckBoundary, initialPackage)
        activeStepRunnable = stepRunnable
        handler.post(stepRunnable)
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
        if (canScrollVertically(node, toBottom)) {
            val copy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                AccessibilityNodeInfo(node)
            } else {
                @Suppress("DEPRECATION")
                AccessibilityNodeInfo.obtain(node)
            }
            list.add(copy)
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectScrollableNodes(child, list, toBottom)
        }
    }
}
