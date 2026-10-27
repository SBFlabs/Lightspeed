package com.sbf.lightspeed.system.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.ShizukuExecHelper
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.*
import org.json.JSONArray

/**
 * Clean-Room Gesture Bot Record, Screen-Reading & Replay Engine.
 * 100% clean-room implementation using Android Accessibility APIs and Shizuku IInputManager.
 */
object LightspeedBotEngine {

    private const val TAG = "LightspeedBotEngine"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var isRecording = false
        private set
    var activeRecordingBotId: Int = 1
        private set

    @Volatile
    var isReplaying = false
        private set
    private var playJob: Job? = null

    @Volatile
    var isLooping = false
        private set
    private var loopJob: Job? = null
    var loopIterationCount = 0
        private set

    @Volatile
    var lastResolvedPkg: String = "global"
        private set
    @Volatile
    var lastResolvedWindow: String = "main"
        private set

    @Volatile
    private var recordingScopePkg: String = "global"
    @Volatile
    private var recordingScopeWindow: String = "main"

    private fun sanitizeToken(raw: String?): String {
        if (raw.isNullOrBlank()) return "main"
        return raw.trim()
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(48)
            .ifBlank { "main" }
    }

    /**
     * Resolves the currently active foreground (packageName, windowSignature).
     * Ignores Lightspeed's own overlays/dialogs so per-app and per-window scoping
     * stays locked to the user's target application and window.
     */
    fun resolveActiveScope(context: Context): Pair<String, String> {
        val ownPkg = context.packageName
        val service = LightspeedAccessibilityService.instance
        if (service != null) {
            try {
                val windows = service.windows
                if (!windows.isNullOrEmpty()) {
                    for (win in windows) {
                        val root = try { win.root } catch (_: Exception) { null } ?: continue
                        val pkg = root.packageName?.toString() ?: continue
                        if (pkg == ownPkg || pkg == "com.android.systemui") continue
                        val winTitle = try { win.title?.toString() } catch (_: Exception) { null }
                        val rootCls = root.className?.toString()?.substringAfterLast('.')
                        val sig = sanitizeToken(
                            when {
                                !winTitle.isNullOrBlank() -> winTitle
                                !rootCls.isNullOrBlank() -> rootCls
                                else -> "win_${win.id}"
                            }
                        )
                        lastResolvedPkg = pkg
                        lastResolvedWindow = sig
                        return pkg to sig
                    }
                }
                val activeRoot = service.rootInActiveWindow
                val pkg = activeRoot?.packageName?.toString()
                if (!pkg.isNullOrBlank() && pkg != ownPkg && pkg != "com.android.systemui") {
                    val cls = activeRoot.className?.toString()?.substringAfterLast('.')
                    val sig = sanitizeToken(cls ?: "main")
                    lastResolvedPkg = pkg
                    lastResolvedWindow = sig
                    return pkg to sig
                }
            } catch (_: Exception) {}
        }
        return lastResolvedPkg to lastResolvedWindow
    }

    fun getActiveScopeDisplayLabel(context: Context): String {
        val (pkg, win) = resolveActiveScope(context)
        if (pkg == "global") return "Global Workspace"
        val appLabel = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
        return if (win.isNotBlank() && win != "main" && !win.equals(appLabel, ignoreCase = true)) {
            "$appLabel • $win"
        } else {
            appLabel
        }
    }

    private fun parseStepsJson(prefs: android.content.SharedPreferences, key: String): MutableList<BotStep> {
        val raw = prefs.getString(key, null)
        val list = mutableListOf<BotStep>()
        if (raw.isNullOrBlank()) return list
        try {
            val arr = JSONArray(raw)
            if (arr.length() > 40) {
                prefs.edit().remove(key).apply()
                Log.w(TAG, "Auto-purged corrupted macro for key $key (${arr.length()} steps)")
                return list
            }
            for (i in 0 until arr.length()) {
                list.add(BotStep.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse macro key $key", e)
        }
        return list
    }

    /**
     * Resolves macro steps for [botId] in the current active Window -> current App -> Global fallback.
     */
    fun getMacroSteps(context: Context, botId: Int): MutableList<BotStep> {
        val prefs = context.defaultPrefs()
        val (pkg, win) = resolveActiveScope(context)
        if (pkg != "global") {
            val winKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${pkg}_${win}"
            val winSteps = parseStepsJson(prefs, winKey)
            if (winSteps.isNotEmpty()) return winSteps

            val appKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${pkg}"
            val appSteps = parseStepsJson(prefs, appKey)
            if (appSteps.isNotEmpty()) return appSteps
        }
        val globalKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId"
        return parseStepsJson(prefs, globalKey)
    }

    fun saveMacroSteps(
        context: Context,
        botId: Int,
        steps: List<BotStep>,
        scopeOverride: Pair<String, String>? = null
    ) {
        val arr = JSONArray()
        steps.take(40).forEach { arr.put(it.toJson()) }
        val json = arr.toString()
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        val editor = context.defaultPrefs().edit()
        if (pkg != "global") {
            val winKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${pkg}_${win}"
            val appKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${pkg}"
            editor.putString(winKey, json)
            editor.putString(appKey, json)
        } else {
            editor.putString("${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId", json)
        }
        editor.apply()
    }

    fun startRecording(context: Context, botId: Int) {
        stopReplay()
        stopContinuousLoop()
        if (isRecording) stopRecording(context)
        val (pkg, win) = resolveActiveScope(context)
        recordingScopePkg = pkg
        recordingScopeWindow = win
        isRecording = true
        activeRecordingBotId = botId.coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS)
        Log.i(TAG, "Starting Bot recording for Bot $activeRecordingBotId in scope $pkg / $win")
        LightspeedBotRecorderOverlay.show(context, activeRecordingBotId)
    }

    fun stopRecording(context: Context, savedSteps: List<BotStep>? = null) {
        if (!isRecording) return
        isRecording = false
        if (savedSteps != null) {
            saveMacroSteps(
                context = context,
                botId = activeRecordingBotId,
                steps = savedSteps,
                scopeOverride = recordingScopePkg to recordingScopeWindow
            )
            Log.i(TAG, "Saved ${savedSteps.size} steps for Bot $activeRecordingBotId in scope $recordingScopePkg / $recordingScopeWindow")
        }
        LightspeedBotRecorderOverlay.hide()
    }

    fun stopReplay() {
        isReplaying = false
        playJob?.cancel()
        playJob = null
    }

    fun playBot(context: Context, botId: Int, onComplete: () -> Unit = {}) {
        if (isReplaying) {
            stopReplay()
            onComplete()
            return
        }
        val steps = getMacroSteps(context, botId)
        if (steps.isEmpty()) {
            Log.w(TAG, "Bot $botId has no recorded steps")
            onComplete()
            return
        }
        playJob = scope.launch {
            isReplaying = true
            try {
                executeStepsSequence(context, steps)
            } finally {
                isReplaying = false
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun startContinuousLoop(context: Context, botId: Int, onIteration: (Int) -> Unit = {}) {
        stopContinuousLoop()
        isLooping = true
        loopIterationCount = 0
        val steps = getMacroSteps(context, botId)
        val cadence = context.defaultPrefs().getLong(LightspeedBotContract.KEY_BOT_ACTIVE_LOOP_CADENCE_MS, 400L).coerceAtLeast(100L)

        loopJob = scope.launch {
            while (isActive && isLooping) {
                loopIterationCount++
                withContext(Dispatchers.Main) { onIteration(loopIterationCount) }
                if (steps.isNotEmpty()) {
                    executeStepsSequence(context, steps)
                } else {
                    // Default rapid auto-click at current cursor or center
                    val metrics = context.resources.displayMetrics
                    ShizukuExecHelper.injectTapEvent(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
                }
                delay(cadence)
            }
        }
    }

    fun stopContinuousLoop() {
        isLooping = false
        loopJob?.cancel()
        loopJob = null
        loopIterationCount = 0
    }

    private suspend fun executeStepsSequence(context: Context, steps: List<BotStep>) {
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        for (step in steps) {
            delay(step.delayMs)

            // 1. Accessibility Screen Reading matching: check if target node exists on screen
            var nodeClicked = false
            val service = LightspeedAccessibilityService.instance
            val root = service?.rootInActiveWindow

            if (root != null) {
                val matchedNode = LightspeedBotScreenScanner.findSmartMatchingNode(root, step, screenW, screenH)
                if (matchedNode != null) {
                    val rect = Rect()
                    matchedNode.getBoundsInScreen(rect)
                    if (!rect.isEmpty) {
                        val clickX = rect.centerX().toFloat()
                        val clickY = rect.centerY().toFloat()
                        if (ShizukuExecHelper.injectTapEvent(clickX, clickY)) {
                            nodeClicked = true
                        } else if (matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            nodeClicked = true
                        } else if (service != null) {
                            dispatchGestureTap(service, clickX, clickY)
                            nodeClicked = true
                        }
                    }
                }
            }

            // 2. Fallback to physical coordinate dispatch if node was not matched
            if (!nodeClicked) {
                val pxX = step.xRatio * screenW
                val pxY = step.yRatio * screenH
                if (step.stepType == BotStep.TYPE_SWIPE) {
                    val endX = step.endXRatio * screenW
                    val endY = step.endYRatio * screenH
                    dispatchGestureSwipe(service, pxX, pxY, endX, endY, step.durationMs)
                } else {
                    if (!ShizukuExecHelper.injectTapEvent(pxX, pxY) && service != null) {
                        dispatchGestureTap(service, pxX, pxY)
                    }
                }
            }
        }
    }

    /**
     * Traverses accessibility tree to find node matching screen conditions.
     */
    fun findMatchingNode(root: AccessibilityNodeInfo, step: BotStep): AccessibilityNodeInfo? {
        // Match by viewId
        if (!step.viewIdResourceName.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(step.viewIdResourceName)
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        // Match by Text
        if (!step.text.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(step.text)
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        // Match by Content Description
        if (!step.contentDescription.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(step.contentDescription)
            if (!nodes.isNullOrEmpty()) return nodes[0]
        }
        return null
    }

    /**
     * Finds the smallest AccessibilityNodeInfo containing point (x, y) for recording metadata.
     */
    fun findSmallestNodeAt(root: AccessibilityNodeInfo?, x: Float, y: Float): AccessibilityNodeInfo? {
        if (root == null) return null
        var bestNode: AccessibilityNodeInfo? = null
        var minArea = Long.MAX_VALUE

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        val rect = Rect()
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            node.getBoundsInScreen(rect)
            if (rect.contains(x.toInt(), y.toInt())) {
                val area = rect.width().toLong() * rect.height().toLong()
                if (area < minArea && (!node.viewIdResourceName.isNullOrBlank() || !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank())) {
                    minArea = area
                    bestNode = node
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return bestNode
    }

    fun injectTapOrDispatch(context: Context, x: Float, y: Float) {
        if (!ShizukuExecHelper.injectTapEvent(x, y)) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            if (service != null) dispatchGestureTap(service, x, y)
        }
    }

    fun injectDoubleTapOrDispatch(context: Context, x: Float, y: Float) {
        injectTapOrDispatch(context, x, y)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            injectTapOrDispatch(context, x, y)
        }, 110L)
    }

    fun injectLongPressOrDispatch(context: Context, x: Float, y: Float, duration: Long = 600L) {
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
        if (service != null) {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
        } else {
            // Fallback tap if accessibility service unreachable
            ShizukuExecHelper.injectTapEvent(x, y)
        }
    }

    fun injectSwipeOrDispatch(context: Context, startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
        dispatchGestureSwipe(service, startX, startY, endX, endY, duration)
    }

    private fun dispatchGestureTap(service: AccessibilityService, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun dispatchGestureSwipe(service: AccessibilityService?, startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        if (service == null) return
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.coerceIn(50L, 1000L))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        service.dispatchGesture(gesture, null, null)
    }
}
