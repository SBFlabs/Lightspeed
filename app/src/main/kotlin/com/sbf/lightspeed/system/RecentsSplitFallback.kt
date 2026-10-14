package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.sbf.lightspeed.LightspeedAccessibilityService
import java.util.concurrent.atomic.AtomicBoolean

internal object RecentsSplitFallback {
    private val TAG = ElevatedTaskCloser.TAG
    private val isRunning = AtomicBoolean(false)

    internal fun splitViaRecents(
        context: Context,
        service: LightspeedAccessibilityService,
        pkg: String
    ) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "splitViaRecents already running, ignoring trigger")
            return
        }

        ElevatedTaskCloser.executor.execute {
            try {
                // a) Resolve app label
                val label: String? = try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    null
                }

                if (label.isNullOrBlank()) {
                    val reason = "Failed to resolve app label for package $pkg"
                    FlightLog.log("recents_split", false, reason)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_resolve_label",
                        ran = true,
                        returned = reason,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                // b) service.performGlobalAction(GLOBAL_ACTION_RECENTS)
                val recentsTriggered = try {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                } catch (e: Exception) {
                    false
                }

                if (!recentsTriggered) {
                    val reason = "performGlobalAction(GLOBAL_ACTION_RECENTS) returned false"
                    FlightLog.log("recents_split", false, reason)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_trigger_recents",
                        ran = true,
                        returned = reason,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                // c) Poll up to 2000 ms
                val myPkg = context.packageName
                val trimmedLabel = label.trim()
                val recentsTriggerTime = SystemClock.uptimeMillis()
                val deadline = recentsTriggerTime + 2000L

                var targetClickableAncestor: AccessibilityNodeInfo? = null
                var totalNodesCollected = 0
                var matchedViewIds = emptySet<String>()
                var matchFailureReason: String? = null

                while (SystemClock.uptimeMillis() < deadline) {
                    val nodes = mutableListOf<AccessibilityNodeInfo>()
                    val windows = try { service.windows } catch (_: Exception) { null }
                    if (!windows.isNullOrEmpty()) {
                        for (w in windows) {
                            val r = try { w.root } catch (_: Exception) { null }
                            if (r != null) {
                                val rootPkg = try { r.packageName?.toString() } catch (_: Exception) { null }
                                if (rootPkg != null && (rootPkg == pkg || rootPkg == myPkg)) {
                                    continue
                                }
                                RecentsFallbackCloser.collectAccessibilityNodes(r, myPkg, nodes)
                            }
                        }
                    }
                    if (nodes.isEmpty()) {
                        val root = try { service.rootInActiveWindow } catch (_: Exception) { null }
                        if (root != null) {
                            val rootPkg = try { root.packageName?.toString() } catch (_: Exception) { null }
                            if (rootPkg == null || (rootPkg != pkg && rootPkg != myPkg)) {
                                RecentsFallbackCloser.collectAccessibilityNodes(root, myPkg, nodes)
                            }
                        }
                    }

                    totalNodesCollected = nodes.size

                    val matches = nodes.filter { node ->
                        val t = try { node.text?.toString()?.trim() } catch (_: Exception) { null }
                        val c = try { node.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
                        (t != null && t.equals(trimmedLabel, ignoreCase = true)) ||
                        (c != null && c.equals(trimmedLabel, ignoreCase = true))
                    }

                    if (matches.isNotEmpty()) {
                        matchedViewIds = matches.mapNotNull { node ->
                            try { node.viewIdResourceName } catch (_: Exception) { null }
                        }.toSet()

                        val clickableAncestors = mutableListOf<AccessibilityNodeInfo>()
                        for (m in matches) {
                            var curr: AccessibilityNodeInfo? = m
                            for (depth in 0 until 8) {
                                if (curr == null) break
                                val isClickable = try { curr.isClickable } catch (_: Exception) { false }
                                if (isClickable) {
                                    if (clickableAncestors.none { it == curr }) {
                                        clickableAncestors.add(curr)
                                    }
                                    break
                                }
                                curr = try { curr.parent } catch (_: Exception) { null }
                            }
                        }

                        if (clickableAncestors.size > 1) {
                            matchFailureReason = "Multiple cards match $label"
                            break
                        } else if (clickableAncestors.size == 1) {
                            targetClickableAncestor = clickableAncestors.first()
                            break
                        }
                    }

                    try { Thread.sleep(60L) } catch (_: InterruptedException) { return@execute }
                }

                if (targetClickableAncestor == null || matchFailureReason != null) {
                    val reason = matchFailureReason ?: "No matching card found for $label"
                    val textLog = "$reason (nodes=$totalNodesCollected, ids=$matchedViewIds)"
                    FlightLog.log("recents_split", false, textLog)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_find_card",
                        ran = true,
                        returned = textLog,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                val elapsedCard = SystemClock.uptimeMillis() - recentsTriggerTime
                val findCardText = "Found card ancestor for label=$label (t=${elapsedCard}ms, nodes=$totalNodesCollected, ids=$matchedViewIds)"
                FlightLog.log("recents_split", true, findCardText)
                PopupDiagnostics.recordStep(
                    action = "split",
                    step = "recents_split_find_card",
                    ran = true,
                    returned = findCardText,
                    verified = false
                )

                // d) performAction(ACTION_CLICK) on that ancestor, then poll up to 1500 ms for "Split screen"
                val clickSuccess = try {
                    targetClickableAncestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }
                val clickText = "ACTION_CLICK on card ancestor returned $clickSuccess (nodes=$totalNodesCollected, ids=$matchedViewIds)"
                FlightLog.log("recents_split", clickSuccess, clickText)
                PopupDiagnostics.recordStep(
                    action = "split",
                    step = "recents_split_click_card",
                    ran = true,
                    returned = clickText,
                    verified = false
                )

                var splitOptionInfo = pollForSplitScreenOption(service, pkg, myPkg, 1500L)

                if (splitOptionInfo.node == null) {
                    // If it does not appear, performAction(ACTION_LONG_CLICK) on the same ancestor and poll again up to 1500 ms
                    val longClickSuccess = try {
                        targetClickableAncestor.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                    } catch (_: Exception) {
                        false
                    }
                    val longClickText = "ACTION_LONG_CLICK on card ancestor returned $longClickSuccess (nodes=$totalNodesCollected, ids=$matchedViewIds)"
                    FlightLog.log("recents_split", longClickSuccess, longClickText)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_long_click_card",
                        ran = true,
                        returned = longClickText,
                        verified = false
                    )

                    splitOptionInfo = pollForSplitScreenOption(service, pkg, myPkg, 1500L)
                }

                if (splitOptionInfo.node == null) {
                    val hasMenuOptionLayout = splitOptionInfo.ids.any { it.endsWith("id/menu_option_layout") }
                    val failText = if (hasMenuOptionLayout) {
                        "Menu opened but no Split screen entry: app not splittable (pkg=$pkg, ids=${splitOptionInfo.ids})"
                    } else {
                        "Split screen option not found (nodes=${splitOptionInfo.totalNodes}, ids=${splitOptionInfo.ids})"
                    }
                    FlightLog.log("recents_split", false, failText)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_find_split_option",
                        ran = true,
                        returned = failText,
                        verified = false
                    )
                    if (hasMenuOptionLayout) {
                        showAppNotSplittableToast(context)
                    } else {
                        showFailureToast(context)
                    }
                    return@execute
                }

                val elapsedSplit = SystemClock.uptimeMillis() - recentsTriggerTime
                val findSplitText = "Found 'Split screen' option (t=${elapsedSplit}ms, nodes=${splitOptionInfo.totalNodes}, ids=${splitOptionInfo.ids})"
                FlightLog.log("recents_split", true, findSplitText)
                PopupDiagnostics.recordStep(
                    action = "split",
                    step = "recents_split_find_split_option",
                    ran = true,
                    returned = findSplitText,
                    verified = false
                )

                // e) When the "Split screen" node is found, climb up to 5 ancestors to the first clickable node and performAction(ACTION_CLICK)
                var splitClickableAncestor: AccessibilityNodeInfo? = null
                var currSplitNode: AccessibilityNodeInfo? = splitOptionInfo.node

                for (depth in 0 until 5) {
                    if (currSplitNode == null) break
                    val isClickable = try { currSplitNode.isClickable } catch (_: Exception) { false }
                    if (isClickable) {
                        splitClickableAncestor = currSplitNode
                        break
                    }
                    currSplitNode = try { currSplitNode.parent } catch (_: Exception) { null }
                }

                if (splitClickableAncestor == null) {
                    val reason = "Split screen entry not clickable"
                    val textFail = "$reason (nodes=${splitOptionInfo.totalNodes}, ids=${splitOptionInfo.ids})"
                    FlightLog.log("recents_split", false, textFail)
                    PopupDiagnostics.recordStep(
                        action = "split",
                        step = "recents_split_click_split_entry",
                        ran = true,
                        returned = textFail,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                val finalClickSuccess = try {
                    splitClickableAncestor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }

                val textFinal = "ACTION_CLICK on Split screen entry returned $finalClickSuccess (nodes=${splitOptionInfo.totalNodes}, ids=${splitOptionInfo.ids})"
                FlightLog.log("recents_split", finalClickSuccess, textFinal)
                PopupDiagnostics.recordStep(
                    action = "split",
                    step = "recents_split_click_split_entry",
                    ran = true,
                    returned = textFinal,
                    verified = false
                )

                if (!finalClickSuccess) {
                    showFailureToast(context)
                }

            } catch (t: Throwable) {
                Log.e(TAG, "splitViaRecents error", t)
                val errMsg = t.message ?: t.javaClass.simpleName
                FlightLog.log("recents_split", false, "Exception: $errMsg")
                PopupDiagnostics.recordStep(
                    action = "split",
                    step = "recents_split_exception",
                    ran = true,
                    returned = errMsg,
                    verified = false
                )
                showFailureToast(context)
            } finally {
                isRunning.set(false)
            }
        }
    }

    private data class SplitOptionResult(
        val node: AccessibilityNodeInfo?,
        val totalNodes: Int,
        val ids: Set<String>
    )

    private fun pollForSplitScreenOption(
        service: LightspeedAccessibilityService,
        targetPkg: String,
        myPkg: String,
        maxWaitMs: Long
    ): SplitOptionResult {
        val start = SystemClock.uptimeMillis()
        val deadline = start + maxWaitMs
        var lastTotalNodes = 0
        var lastIds = emptySet<String>()

        while (SystemClock.uptimeMillis() < deadline) {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            val windows = try { service.windows } catch (_: Exception) { null }
            if (!windows.isNullOrEmpty()) {
                for (w in windows) {
                    val r = try { w.root } catch (_: Exception) { null }
                    if (r != null) {
                        val rootPkg = try { r.packageName?.toString() } catch (_: Exception) { null }
                        if (rootPkg != null && (rootPkg == targetPkg || rootPkg == myPkg)) {
                            continue
                        }
                        RecentsFallbackCloser.collectAccessibilityNodes(r, myPkg, nodes)
                    }
                }
            }
            if (nodes.isEmpty()) {
                val root = try { service.rootInActiveWindow } catch (_: Exception) { null }
                if (root != null) {
                    val rootPkg = try { root.packageName?.toString() } catch (_: Exception) { null }
                    if (rootPkg == null || (rootPkg != targetPkg && rootPkg != myPkg)) {
                        RecentsFallbackCloser.collectAccessibilityNodes(root, myPkg, nodes)
                    }
                }
            }

            lastTotalNodes = nodes.size
            lastIds = nodes.mapNotNull { n -> try { n.viewIdResourceName } catch (_: Exception) { null } }.toSet()

            val foundNode = nodes.firstOrNull { node ->
                val t = try { node.text?.toString()?.trim() } catch (_: Exception) { null }
                val c = try { node.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
                (t != null && t.equals("Split screen", ignoreCase = true)) ||
                (c != null && c.equals("Split screen", ignoreCase = true))
            }

            if (foundNode != null) {
                return SplitOptionResult(foundNode, lastTotalNodes, lastIds)
            }

            try { Thread.sleep(60L) } catch (_: InterruptedException) { break }
        }

        return SplitOptionResult(null, lastTotalNodes, lastIds)
    }

    private fun showFailureToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Split isn't supported on this phone without Shizuku",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showAppNotSplittableToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Split screen isn't available for this app",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
