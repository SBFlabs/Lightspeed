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

internal object RecentsPopupFallback {
    private val TAG = ElevatedTaskCloser.TAG
    private val isRunning = AtomicBoolean(false)

    internal fun popupViaRecents(
        context: Context,
        service: LightspeedAccessibilityService,
        pkg: String
    ) {
        if (!isRunning.compareAndSet(false, true)) {
            Log.w(TAG, "popupViaRecents already running, ignoring trigger")
            return
        }

        ElevatedTaskCloser.executor.execute {
            try {
                // a) Resolve app label; failure -> failure path
                val label: String? = try {
                    val appInfo = context.packageManager.getApplicationInfo(pkg, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    null
                }

                if (label.isNullOrBlank()) {
                    val reason = "Failed to resolve app label for package $pkg"
                    FlightLog.log("recents_popup", false, reason)
                    PopupDiagnostics.recordStep(
                        action = "popup",
                        step = "recents_popup_resolve_label",
                        ran = true,
                        returned = reason,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                // b) service.performGlobalAction(GLOBAL_ACTION_RECENTS); false -> failure path
                val recentsTriggered = try {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                } catch (e: Exception) {
                    false
                }

                if (!recentsTriggered) {
                    val reason = "performGlobalAction(GLOBAL_ACTION_RECENTS) returned false"
                    FlightLog.log("recents_popup", false, reason)
                    PopupDiagnostics.recordStep(
                        action = "popup",
                        step = "recents_popup_trigger_recents",
                        ran = true,
                        returned = reason,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                // c) Poll up to 2000 ms for nodes whose text or contentDescription equals the label (trim, ignoreCase).
                val myPkg = context.packageName
                val trimmedLabel = label.trim()
                val recentsTriggerTime = SystemClock.uptimeMillis()
                val deadline = recentsTriggerTime + 2000L

                var targetButton: AccessibilityNodeInfo? = null
                var totalNodesCollected = 0
                var nodesWithViewIdCount = 0
                var collectedViewIds = emptySet<String>()
                var candidateCards = 0
                var labelFoundWithoutButton = false
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
                    val nonNullViewIds = nodes.mapNotNull { n ->
                        try { n.viewIdResourceName } catch (_: Exception) { null }
                    }
                    nodesWithViewIdCount = nonNullViewIds.size
                    collectedViewIds = nonNullViewIds.toSet()

                    val matches = nodes.filter { node ->
                        val t = try { node.text?.toString()?.trim() } catch (_: Exception) { null }
                        val c = try { node.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
                        (t != null && t.equals(trimmedLabel, ignoreCase = true)) ||
                        (c != null && c.equals(trimmedLabel, ignoreCase = true))
                    }

                    if (matches.isNotEmpty()) {
                        // For each match climb up to 8 ancestors; at each ancestor search its descendants (depth-limited to 6)
                        // for a node whose viewIdResourceName ends with "id/smallWindowButton". Use the first ancestor that contains one.
                        val cardButtons = mutableListOf<Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>>()
                        var foundAnyButton = false

                        for (m in matches) {
                            var curr: AccessibilityNodeInfo? = m
                            var foundForThisMatch: Pair<AccessibilityNodeInfo, AccessibilityNodeInfo>? = null
                            for (depth in 0 until 8) {
                                if (curr == null) break
                                val viewId = try { curr.viewIdResourceName } catch (_: Exception) { null }
                                if (viewId != null && viewId.endsWith("id/overview_panel")) {
                                    break
                                }
                                if (countLabelMatches(curr, trimmedLabel) > 1) {
                                    break
                                }
                                val btn = findSmallWindowButton(curr, 6)
                                if (btn != null) {
                                    foundForThisMatch = Pair(curr, btn)
                                    break
                                }
                                if (viewId != null && viewId.endsWith("id/task")) {
                                    break
                                }
                                curr = try { curr.parent } catch (_: Exception) { null }
                            }
                            if (foundForThisMatch != null) {
                                foundAnyButton = true
                                if (cardButtons.none { it.first == foundForThisMatch.first }) {
                                    cardButtons.add(foundForThisMatch)
                                }
                            }
                        }

                        if (cardButtons.size > 1) {
                            matchFailureReason = "Multiple cards match $label"
                            candidateCards = cardButtons.size
                            break
                        } else if (cardButtons.size == 1) {
                            targetButton = cardButtons.first().second
                            candidateCards = 1
                            break
                        } else if (!foundAnyButton) {
                            labelFoundWithoutButton = true
                            break
                        }
                    }

                    try { Thread.sleep(60L) } catch (_: InterruptedException) { return@execute }
                }

                val elapsed = SystemClock.uptimeMillis() - recentsTriggerTime

                if (matchFailureReason != null) {
                    val textLog = "$matchFailureReason (t=${elapsed}ms, nodes=$totalNodesCollected, ids=$collectedViewIds)"
                    FlightLog.log("recents_popup", false, textLog)
                    PopupDiagnostics.recordStep(
                        action = "popup",
                        step = "recents_popup_find_button",
                        ran = true,
                        returned = textLog,
                        verified = false
                    )
                    showFailureToast(context)
                    return@execute
                }

                // d) If the label card is found but no smallWindowButton exists under any ancestor
                if (targetButton == null) {
                    if (labelFoundWithoutButton) {
                        val textLog = "Card found but no pop-up button: app not poppable (pkg=$pkg, idsWithResName=$nodesWithViewIdCount, ids=$collectedViewIds)"
                        FlightLog.log("recents_popup", false, "t=${elapsed}ms, $textLog")
                        PopupDiagnostics.recordStep(
                            action = "popup",
                            step = "recents_popup_find_button",
                            ran = true,
                            returned = textLog,
                            verified = false
                        )
                        showAppNotPoppableToast(context)
                    } else {
                        val reason = "No matching card found for $label"
                        val textLog = "$reason (t=${elapsed}ms, nodes=$totalNodesCollected, ids=$collectedViewIds)"
                        FlightLog.log("recents_popup", false, textLog)
                        PopupDiagnostics.recordStep(
                            action = "popup",
                            step = "recents_popup_find_button",
                            ran = true,
                            returned = textLog,
                            verified = false
                        )
                        showFailureToast(context)
                    }
                    return@execute
                }

                val findButtonText = "Found smallWindowButton for label=$label (t=${elapsed}ms, nodes=$totalNodesCollected, ids=$collectedViewIds)"
                FlightLog.log("recents_popup", true, findButtonText)
                PopupDiagnostics.recordStep(
                    action = "popup",
                    step = "recents_popup_find_button",
                    ran = true,
                    returned = findButtonText,
                    verified = false
                )

                // e) performAction(ACTION_CLICK) on the button; if it returns false, climb up to 3 ancestors to the first clickable node and click that.
                var clicked = try {
                    targetButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }

                var clickTargetDesc = "button"
                if (!clicked) {
                    var curr: AccessibilityNodeInfo? = targetButton.parent
                    for (depth in 0 until 3) {
                        if (curr == null) break
                        val isClickable = try { curr.isClickable } catch (_: Exception) { false }
                        if (isClickable) {
                            clicked = try {
                                curr.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            } catch (_: Exception) {
                                false
                            }
                            clickTargetDesc = "clickable ancestor at depth ${depth + 1}"
                            break
                        }
                        curr = try { curr.parent } catch (_: Exception) { null }
                    }
                }

                val elapsedTotal = SystemClock.uptimeMillis() - recentsTriggerTime
                val clickText = "ACTION_CLICK on $clickTargetDesc returned $clicked (t=${elapsedTotal}ms, nodes=$totalNodesCollected, ids=$collectedViewIds)"
                FlightLog.log("recents_popup", clicked, clickText)
                PopupDiagnostics.recordStep(
                    action = "popup",
                    step = "recents_popup_click_button",
                    ran = true,
                    returned = clickText,
                    verified = clicked
                )

                if (!clicked) {
                    showFailureToast(context)
                }
                // If clicked == true, no success toast (Android's own pop-up follows)

            } catch (t: Throwable) {
                Log.e(TAG, "popupViaRecents error", t)
                val errMsg = t.message ?: t.javaClass.simpleName
                FlightLog.log("recents_popup", false, "Exception: $errMsg")
                PopupDiagnostics.recordStep(
                    action = "popup",
                    step = "recents_popup_exception",
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

    private fun findSmallWindowButton(root: AccessibilityNodeInfo, maxDepth: Int): AccessibilityNodeInfo? {
        if (maxDepth < 0) return null
        val resName = try { root.viewIdResourceName } catch (_: Exception) { null }
        if (resName != null && resName.endsWith("id/smallWindowButton")) {
            return root
        }
        if (maxDepth == 0) return null
        val childCount = try { root.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            val child = try { root.getChild(i) } catch (_: Exception) { null } ?: continue
            val found = findSmallWindowButton(child, maxDepth - 1)
            if (found != null) return found
        }
        return null
    }

    private fun countLabelMatches(root: AccessibilityNodeInfo, label: String, maxDepth: Int = 8): Int {
        var count = 0
        val t = try { root.text?.toString()?.trim() } catch (_: Exception) { null }
        val c = try { root.contentDescription?.toString()?.trim() } catch (_: Exception) { null }
        if ((t != null && t.equals(label, ignoreCase = true)) ||
            (c != null && c.equals(label, ignoreCase = true))) {
            count++
        }
        if (maxDepth <= 0) return count
        val childCount = try { root.childCount } catch (_: Exception) { 0 }
        for (i in 0 until childCount) {
            val child = try { root.getChild(i) } catch (_: Exception) { null } ?: continue
            count += countLabelMatches(child, label, maxDepth - 1)
            if (count > 1) return count
        }
        return count
    }

    private fun showFailureToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Pop-up isn't supported on this phone without Shizuku",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showAppNotPoppableToast(context: Context) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(
                context,
                "Pop-up isn't available for this app",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
