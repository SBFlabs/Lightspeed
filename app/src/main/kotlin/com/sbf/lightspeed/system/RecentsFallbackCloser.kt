package com.sbf.lightspeed.system

import android.content.Context
import android.util.Log
import android.os.Handler
import android.os.Looper
import android.widget.Toast

internal object RecentsFallbackCloser {
    private val TAG = ElevatedTaskCloser.TAG

    private val isRecentsFallbackRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    private data class CardMatchInfo(
        val cardNode: android.view.accessibility.AccessibilityNodeInfo,
        val action: android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
    )

    private data class WindowDumpInfo(
        val windowId: Int,
        val type: Int,
        val layer: Int,
        val title: String?,
        val isActive: Boolean,
        val isFocused: Boolean,
        val rootPackageName: String?,
        val nodes: List<android.view.accessibility.AccessibilityNodeInfo>
    )

    internal fun closeViaRecentsFallback(
        context: Context,
        service: com.sbf.lightspeed.LightspeedAccessibilityService
    ) {
        if (!isRecentsFallbackRunning.compareAndSet(false, true)) {
            Log.w(TAG, "closeViaRecentsFallback already running, ignoring trigger")
            return
        }

        val recordedPkg = com.sbf.lightspeed.LightspeedAccessibilityService.lastAppPackage
        val label: String? = if (!recordedPkg.isNullOrBlank()) {
            try {
                val appInfo = context.packageManager.getApplicationInfo(recordedPkg, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        if (recordedPkg.isNullOrBlank() || label.isNullOrBlank() || TaskInspector.isLauncherOrSystem(context, recordedPkg)) {
            isRecentsFallbackRunning.set(false)
            val reason = if (recordedPkg.isNullOrBlank()) "No recorded app package" else if (TaskInspector.isLauncherOrSystem(context, recordedPkg)) "Target package is launcher/system ($recordedPkg)" else "Could not resolve label for package $recordedPkg"
            FlightLog.log("recents_close", false, reason)
            PopupDiagnostics.recordStep(
                action = "close_app",
                step = "recents_fallback_aborted",
                ran = false,
                returned = reason,
                verified = false
            )
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Nothing to close here", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val recentsTriggered = try {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
        } catch (e: Exception) {
            false
        }

        if (!recentsTriggered) {
            isRecentsFallbackRunning.set(false)
            val reason = "performGlobalAction(GLOBAL_ACTION_RECENTS) returned false"
            FlightLog.log("recents_close", false, reason)
            PopupDiagnostics.recordStep(
                action = "close_app",
                step = "recents_perform_global_failed",
                ran = true,
                returned = reason,
                verified = false
            )
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Swipe \"$label\" away", Toast.LENGTH_SHORT).show()
            }
            return
        }

        ElevatedTaskCloser.executor.execute {
            val lastSeenWindows = mutableListOf<WindowDumpInfo>()
            try {
                try { Thread.sleep(60L) } catch (_: InterruptedException) { return@execute }

                val startTime = android.os.SystemClock.uptimeMillis()
                val deadline = startTime + 2000L
                var succeeded = false
                var failureReason = "No matching card found in Recents"
                val myPkg = context.packageName

                while (android.os.SystemClock.uptimeMillis() < deadline) {
                    val currentWindows = mutableListOf<WindowDumpInfo>()
                    val windows = try { service.windows } catch (_: Exception) { null }
                    if (!windows.isNullOrEmpty()) {
                        for (w in windows) {
                            val r = try { w.root } catch (_: Exception) { null }
                            if (r != null) {
                                val wNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                                collectAccessibilityNodes(r, myPkg, wNodes)
                                val wId = try { w.id } catch (_: Exception) { -1 }
                                val wType = try { w.type } catch (_: Exception) { -1 }
                                val wLayer = try { w.layer } catch (_: Exception) { -1 }
                                val wTitle = try { w.title?.toString() } catch (_: Exception) { null }
                                val wActive = try { w.isActive } catch (_: Exception) { false }
                                val wFocused = try { w.isFocused } catch (_: Exception) { false }
                                val rootPkg = try { r.packageName?.toString() } catch (_: Exception) { null }
                                currentWindows.add(
                                    WindowDumpInfo(
                                        windowId = wId,
                                        type = wType,
                                        layer = wLayer,
                                        title = wTitle,
                                        isActive = wActive,
                                        isFocused = wFocused,
                                        rootPackageName = rootPkg,
                                        nodes = wNodes
                                    )
                                )
                            }
                        }
                    } else {
                        val r = try { service.rootInActiveWindow } catch (_: Exception) { null }
                        if (r != null) {
                            val wNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                            collectAccessibilityNodes(r, myPkg, wNodes)
                            val wId = try { r.windowId } catch (_: Exception) { -1 }
                            val rootPkg = try { r.packageName?.toString() } catch (_: Exception) { null }
                            currentWindows.add(
                                WindowDumpInfo(
                                    windowId = wId,
                                    type = -1,
                                    layer = -1,
                                    title = null,
                                    isActive = true,
                                    isFocused = true,
                                    rootPackageName = rootPkg,
                                    nodes = wNodes
                                )
                            )
                        }
                    }

                    if (currentWindows.isNotEmpty()) {
                        lastSeenWindows.clear()
                        lastSeenWindows.addAll(currentWindows)
                    }

                    val allNodes = currentWindows.flatMap { it.nodes }

                    if (allNodes.isNotEmpty()) {
                        val matchingNodes = mutableListOf<android.view.accessibility.AccessibilityNodeInfo>()
                        val targetLabelTrimmed = label.trim()

                        for (node in allNodes) {
                            val t = try { node.text?.toString()?.trim() } catch (_: Exception) { null }
                            val c = try { node.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
                            val isTextMatch = t != null && t.equals(targetLabelTrimmed, ignoreCase = true)
                            val isDescMatch = c != null && c.equals(targetLabelTrimmed, ignoreCase = true)
                            if (isTextMatch || isDescMatch) {
                                matchingNodes.add(node)
                            }
                        }

                        if (matchingNodes.isNotEmpty()) {
                            var matchWithoutDismissableAncestor = false
                            val cardMatches = mutableListOf<CardMatchInfo>()

                            for (node in matchingNodes) {
                                var curr: android.view.accessibility.AccessibilityNodeInfo? = node
                                var foundCard: CardMatchInfo? = null
                                for (depth in 0 until 8) {
                                    if (curr == null) break
                                    val action = findDismissAction(curr)
                                    if (action != null) {
                                        foundCard = CardMatchInfo(curr, action)
                                        break
                                    }
                                    curr = try { curr.parent } catch (_: Exception) { null }
                                }

                                if (foundCard != null) {
                                    if (cardMatches.none { it.cardNode == foundCard.cardNode }) {
                                        cardMatches.add(foundCard)
                                    }
                                } else {
                                    matchWithoutDismissableAncestor = true
                                }
                            }

                            if (matchWithoutDismissableAncestor) {
                                failureReason = "Matching node for $label found, but ancestor exposes no dismiss action"
                            } else if (cardMatches.size > 1) {
                                failureReason = "Multiple distinct cards (${cardMatches.size}) found matching $label"
                            } else if (cardMatches.size == 1) {
                                val targetCard = cardMatches.first()
                                val actionAccepted = try {
                                    targetCard.cardNode.performAction(targetCard.action.id)
                                } catch (e: Exception) {
                                    Log.w(TAG, "performAction on card failed", e)
                                    false
                                }

                                if (actionAccepted) {
                                    succeeded = true
                                    val successMsg = "Action accepted for $label ($recordedPkg)"
                                    FlightLog.log("recents_close", true, successMsg)
                                    PopupDiagnostics.recordStep(
                                        action = "close_app",
                                        step = "recents_fallback_dismiss",
                                        ran = true,
                                        returned = successMsg,
                                        verified = false
                                    )
                                    break
                                } else {
                                    failureReason = "performAction returned false for $label"
                                }
                            }
                        }
                    }

                    try { Thread.sleep(80L) } catch (_: InterruptedException) { break }
                }

                if (!succeeded) {
                    FlightLog.log("recents_close", false, failureReason)
                    logRecentsFallbackDump(recordedPkg, label, lastSeenWindows)
                    PopupDiagnostics.recordStep(
                        action = "close_app",
                        step = "recents_fallback_failed",
                        ran = true,
                        returned = failureReason,
                        verified = false
                    )
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Swipe \"$label\" away", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "closeViaRecentsFallback background execution error", e)
                val errMsg = e.message ?: e.javaClass.simpleName
                FlightLog.log("recents_close", false, errMsg)
                logRecentsFallbackDump(recordedPkg, label, lastSeenWindows)
                PopupDiagnostics.recordStep(
                    action = "close_app",
                    step = "recents_fallback_exception",
                    ran = true,
                    returned = errMsg,
                    verified = false
                )
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "Swipe \"$label\" away", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isRecentsFallbackRunning.set(false)
            }
        }
    }

    private fun logRecentsFallbackDump(
        recordedPkg: String,
        label: String,
        windows: List<WindowDumpInfo>
    ) {
        try {
            val totalNodeCount = windows.sumOf { it.nodes.size }
            val labelTrimmed = label.trim()

            val nearMatchList = mutableListOf<String>()
            for (w in windows) {
                for (node in w.nodes) {
                    if (nearMatchList.size >= 5) break
                    val t = try { node.text?.toString()?.trim() } catch (_: Exception) { null }
                    val c = try { node.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
                    val matchingText = if (t != null && t.contains(labelTrimmed, ignoreCase = true)) t
                    else if (c != null && c.contains(labelTrimmed, ignoreCase = true)) c
                    else null

                    if (matchingText != null) {
                        val textStr = matchingText.take(60).replace("\n", " ")
                        val winId = w.windowId
                        val dismiss = hasDismissActionOnSelfOrAncestors(node)
                        nearMatchList.add("text=\"$textStr\" winId=$winId dismiss=$dismiss")
                    }
                }
                if (nearMatchList.size >= 5) break
            }

            val summarySb = StringBuilder()
            summarySb.append("lastAppPackage=").append(recordedPkg)
                .append(", label=").append(label)
                .append(", windows=").append(windows.size)
                .append(", total_nodes=").append(totalNodeCount).append("\n")

            if (nearMatchList.isEmpty()) {
                summarySb.append("near_matches: no near match")
            } else {
                summarySb.append("near_matches:\n")
                nearMatchList.forEachIndexed { idx, match ->
                    summarySb.append("${idx + 1}. $match\n")
                }
            }

            FlightLog.log("recents_fallback_dump", false, summarySb.toString().trimEnd(), maxChars = 1500)

            val windowDumps = windows.take(8)
            for (w in windowDumps) {
                val winSb = StringBuilder()
                winSb.append("winId=").append(w.windowId)
                    .append(", type=").append(w.type)
                    .append(", layer=").append(w.layer)
                    .append(", title=").append(w.title ?: "<none>")
                    .append(", isActive=").append(w.isActive)
                    .append(", isFocused=").append(w.isFocused)
                    .append(", rootPkg=").append(w.rootPackageName ?: "<none>")
                    .append(", nodes=").append(w.nodes.size).append("\n")

                val preferred = w.nodes.filter { n ->
                    val t = try { n.text?.toString() } catch (_: Exception) { null }
                    val c = try { n.contentDescription?.toString() } catch (_: Exception) { null }
                    (!t.isNullOrBlank()) || (!c.isNullOrBlank()) || hasDismissActionOnSelfOrAncestors(n)
                }
                val preferredSet = preferred.toSet()
                val fallback = w.nodes.filter { n -> n !in preferredSet }
                val selectedNodes = (preferred + fallback).take(15)

                selectedNodes.forEachIndexed { idx, node ->
                    val cls = try { node.className?.toString() ?: "unknown" } catch (_: Exception) { "unknown" }
                    val rawText = try {
                        val t = node.text?.toString()?.trim()
                        val c = node.contentDescription?.toString()?.trim()
                        if (!t.isNullOrEmpty()) t else if (!c.isNullOrEmpty()) c else null
                    } catch (_: Exception) { null }
                    val textStr = rawText?.take(60)?.replace("\n", " ") ?: "<empty>"
                    val viewId = try { node.viewIdResourceName ?: "null" } catch (_: Exception) { "null" }
                    val clickable = try { node.isClickable } catch (_: Exception) { false }
                    val dismiss = hasDismissActionOnSelfOrAncestors(node)

                    winSb.append("${idx + 1}. class=$cls text=\"$textStr\" id=$viewId clickable=$clickable dismiss=$dismiss\n")
                }

                FlightLog.log("recents_fallback_window", false, winSb.toString().trimEnd(), maxChars = 1500)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to log recents fallback dump", e)
        }
    }

    private fun hasDismissActionOnSelfOrAncestors(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): Boolean {
        var curr: android.view.accessibility.AccessibilityNodeInfo? = node
        for (depth in 0 until 8) {
            if (curr == null) break
            if (findDismissAction(curr) != null) return true
            curr = try { curr.parent } catch (_: Exception) { null }
        }
        return false
    }

    internal fun collectAccessibilityNodes(
        root: android.view.accessibility.AccessibilityNodeInfo,
        myPkg: String,
        outList: MutableList<android.view.accessibility.AccessibilityNodeInfo>
    ) {
        val pkg = try { root.packageName?.toString() } catch (_: Exception) { null }
        if (pkg == myPkg) return
        outList.add(root)
        val count = try { root.childCount } catch (_: Exception) { 0 }
        for (i in 0 until count) {
            val child = try { root.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                collectAccessibilityNodes(child, myPkg, outList)
            }
        }
    }

    private fun findDismissAction(
        node: android.view.accessibility.AccessibilityNodeInfo
    ): android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction? {
        val actions = try { node.actionList } catch (_: Exception) { null } ?: return null
        val dismissAction = actions.firstOrNull { it.id == android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.id }
        if (dismissAction != null) return dismissAction

        return actions.firstOrNull { act ->
            val l = act.label?.toString()?.lowercase()
            l != null && (l.contains("close") || l.contains("dismiss") || l.contains("remove"))
        }
    }

}
