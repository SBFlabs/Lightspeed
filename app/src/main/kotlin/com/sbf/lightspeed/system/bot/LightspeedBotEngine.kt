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
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedVolumeKeyEngine
import com.sbf.lightspeed.system.ShizukuExecHelper
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
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
    var lastResolvedWindow: String = "Main"
        private set

    @Volatile
    var recordingScopePkg: String = "global"
        internal set
    @Volatile
    var recordingScopeWindow: String = "Main"
        internal set

    private fun sanitizeToken(raw: String?): String {
        return LightspeedBotScreenScanner.sanitizeToken(raw)
    }

    /**
     * Resolves the currently active foreground (packageName, windowSignature).
     * Ignores Lightspeed's own overlays/dialogs so per-app and per-window scoping
     * stays locked to the user's target application and window.
     */
    fun resolveActiveScope(context: Context): Pair<String, String> {
        val ownPkg = context.packageName
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
        var pkg: String? = null
        if (service != null) {
            try {
                val appWin = LightspeedBotScreenScanner.resolveUnderlyingAppWindow(service)
                val activeRoot = appWin?.root ?: service.rootInActiveWindow
                val rPkg = activeRoot?.packageName?.toString()
                if (!rPkg.isNullOrBlank() && rPkg != ownPkg && rPkg != "com.android.systemui") {
                    pkg = rPkg
                }
                if (pkg == null) {
                    val windows = service.windows
                    if (!windows.isNullOrEmpty()) {
                        for (win in windows) {
                            val root = try { win.root } catch (_: Exception) { null } ?: continue
                            val wPkg = root.packageName?.toString() ?: continue
                            if (wPkg != ownPkg && wPkg != "com.android.systemui") {
                                pkg = wPkg
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (pkg == null) {
            pkg = LightspeedAccessibilityService.lastAppPackage ?: "global"
        }
        val windowId = if (pkg != "global" && service != null) {
            LightspeedBotScreenScanner.resolveActiveWindowId(service)
        } else {
            "Main"
        }
        lastResolvedPkg = pkg
        lastResolvedWindow = windowId
        return pkg to windowId
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
        return if (win.isNotBlank() && win != "Main" && !win.equals(appLabel, ignoreCase = true)) {
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
     * Resolves macro steps for [botId] in a designated scope for editor loading.
     */
    fun getMacroSteps(
        context: Context,
        botId: Int,
        scopeOverride: Pair<String, String>? = null,
        scopeType: String = LightspeedBotContract.SCOPE_THIS_WINDOW
    ): MutableList<BotStep> {
        val prefs = context.defaultPrefs()
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        return when (scopeType) {
            LightspeedBotContract.SCOPE_THIS_APP -> {
                val appKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${pkg}"
                parseStepsJson(prefs, appKey)
            }
            LightspeedBotContract.SCOPE_GLOBAL -> {
                val globalKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId"
                parseStepsJson(prefs, globalKey)
            }
            else -> {
                val winKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${pkg}_${win}"
                parseStepsJson(prefs, winKey)
            }
        }
    }

    /**
     * Strict Per-Window Playback Resolution (Zero Fallback Leakage Across Windows or Apps).
     * 1. Looks up (currentPkg, currentWindowId) strictly.
     * 2. Only checks App-Wide or Global if user explicitly configured that macro's scope chip.
     * 3. Verifies step windowId / packageName under This Window scope.
     * 4. Aborts cleanly if no match.
     */
    fun getPlaybackMacroSteps(context: Context, botId: Int): List<BotStep> {
        val prefs = context.defaultPrefs()
        val (currentPkg, currentWin) = resolveActiveScope(context)
        if (currentPkg == "global") return emptyList()

        // 1. Strict Window-scoped lookup
        val winKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${currentPkg}_${currentWin}"
        val winSteps = parseStepsJson(prefs, winKey)
        if (winSteps.isNotEmpty()) {
            val scopeMode = prefs.getString(
                "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_win_${currentPkg}_${currentWin}",
                LightspeedBotContract.SCOPE_THIS_WINDOW
            )
            if (scopeMode == LightspeedBotContract.SCOPE_THIS_WINDOW) {
                // Verify initial step package matches currentPkg
                val firstStep = winSteps.firstOrNull()
                if (firstStep?.packageName != null && firstStep.packageName != currentPkg) {
                    Log.w(TAG, "Window macro for $botId targets package ${firstStep.packageName} but active app is $currentPkg")
                    return emptyList()
                }
            }
            return winSteps
        }

        // 2. Strict App-Wide lookup ONLY if user explicitly configured App scope
        val appScopeConfigured = prefs.getString(
            "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_app_${currentPkg}",
            null
        )
        if (appScopeConfigured == LightspeedBotContract.SCOPE_THIS_APP) {
            val appKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${currentPkg}"
            val appSteps = parseStepsJson(prefs, appKey)
            if (appSteps.isNotEmpty()) {
                val hasMismatch = appSteps.any { step ->
                    step.packageName != null && step.packageName != currentPkg
                }
                if (!hasMismatch) return appSteps
            }
        }

        // 3. Strict Global lookup ONLY if user explicitly configured Global scope
        val globalScopeConfigured = prefs.getString(
            "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_global",
            null
        )
        if (globalScopeConfigured == LightspeedBotContract.SCOPE_GLOBAL) {
            val globalKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId"
            val globalSteps = parseStepsJson(prefs, globalKey)
            if (globalSteps.isNotEmpty()) return globalSteps
        }

        return emptyList()
    }

    fun saveMacroSteps(
        context: Context,
        botId: Int,
        steps: List<BotStep>,
        scopeOverride: Pair<String, String>? = null,
        scopeType: String = LightspeedBotContract.SCOPE_THIS_WINDOW
    ) {
        val arr = JSONArray()
        steps.take(40).forEach { arr.put(it.toJson()) }
        val json = arr.toString()
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        val editor = context.defaultPrefs().edit()
        when (scopeType) {
            LightspeedBotContract.SCOPE_THIS_APP -> {
                val appKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${pkg}"
                val scopeModeKey = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_app_${pkg}"
                editor.putString(appKey, json)
                editor.putString(scopeModeKey, LightspeedBotContract.SCOPE_THIS_APP)
            }
            LightspeedBotContract.SCOPE_GLOBAL -> {
                val globalKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId"
                val scopeModeKey = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_global"
                editor.putString(globalKey, json)
                editor.putString(scopeModeKey, LightspeedBotContract.SCOPE_GLOBAL)
            }
            else -> {
                // Strict This Window scope: save only to winKey without polluting global or app keys
                val winKey = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${pkg}_${win}"
                val scopeModeKey = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_win_${pkg}_${win}"
                editor.putString(winKey, json)
                editor.putString(scopeModeKey, LightspeedBotContract.SCOPE_THIS_WINDOW)
            }
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
                scopeOverride = recordingScopePkg to recordingScopeWindow,
                scopeType = LightspeedBotContract.SCOPE_THIS_WINDOW
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

    /**
     * Emergency Hardware Key Interception:
     * Immediately stops active loop, macro replay, and cancels recording mode without saving.
     * Triggered on Volume Up, Volume Down, or Power key presses, or screen off events.
     * Returns true if any active automation or recording was cancelled.
     */
    fun cancelAllAutomationAndRecording(context: Context): Boolean {
        var didCancel = false
        if (isLooping) {
            stopContinuousLoop()
            didCancel = true
        }
        if (isReplaying) {
            stopReplay()
            didCancel = true
        }
        if (isRecording || LightspeedBotRecorderOverlay.isShowing) {
            stopRecording(context, savedSteps = null)
            didCancel = true
        }
        try {
            LightspeedVolumeKeyEngine.stopAutoRepeat()
        } catch (_: Throwable) {}
        if (didCancel) {
            try {
                LightspeedHapticEngine.triggerConfirmation(context)
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                mainHandler.post {
                    android.widget.Toast.makeText(
                        context.applicationContext,
                        "⚡ Macro / Recording cancelled by hardware key",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Throwable) {}
        }
        return didCancel
    }

    fun playBot(context: Context, botId: Int, stepsOverride: List<BotStep>? = null, onComplete: () -> Unit = {}) {
        if (isReplaying) {
            stopReplay()
            onComplete()
            return
        }
        val steps = stepsOverride ?: getPlaybackMacroSteps(context, botId)
        if (steps.isEmpty()) {
            Log.w(TAG, "Bot $botId has no macro for current window")
            mainHandler.post {
                android.widget.Toast.makeText(
                    context.applicationContext,
                    "No macro for this window",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            onComplete()
            return
        }
        val repeatPref = context.defaultPrefs().getInt("${LightspeedBotContract.KEY_BOT_MACRO_REPEAT_PREFIX}$botId", 1)
        val repeatCount = if (repeatPref <= 0) 1 else repeatPref
        playJob = scope.launch {
            isReplaying = true
            try {
                for (rep in 0 until repeatCount) {
                    if (!isActive || !isReplaying) break
                    executeStepsSequence(context, steps, botId)
                    if (rep < repeatCount - 1) {
                        delay(200L)
                    }
                }
            } finally {
                isReplaying = false
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun startContinuousLoop(context: Context, botId: Int, stepsOverride: List<BotStep>? = null, onIteration: (Int) -> Unit = {}) {
        stopContinuousLoop()
        isLooping = true
        loopIterationCount = 0
        val steps = stepsOverride ?: getPlaybackMacroSteps(context, botId)
        val cadence = context.defaultPrefs().getLong(LightspeedBotContract.KEY_BOT_ACTIVE_LOOP_CADENCE_MS, 400L).coerceAtLeast(100L)

        loopJob = scope.launch {
            while (isActive && isLooping) {
                loopIterationCount++
                withContext(Dispatchers.Main) { onIteration(loopIterationCount) }
                if (steps.isNotEmpty()) {
                    executeStepsSequence(context, steps, botId)
                } else {
                    // Rapid auto-click at active trackpad cursor position (or screen center fallback)
                    val metrics = context.resources.displayMetrics
                    val cx = LightspeedBotTrackpadOverlay.cursorX.takeIf { it > 0f } ?: (metrics.widthPixels / 2f)
                    val cy = LightspeedBotTrackpadOverlay.cursorY.takeIf { it > 0f } ?: (metrics.heightPixels / 2f)
                    injectTapOrDispatch(context, cx, cy)
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

    private suspend fun executeStepsSequence(context: Context, steps: List<BotStep>, botId: Int = 1) {
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val prefs = context.defaultPrefs()
        val useRecordedDelay = prefs.getBoolean("${LightspeedBotContract.KEY_BOT_USE_RECORDED_DELAY_PREFIX}$botId", false)
        val customStepDelay = prefs.getInt("${LightspeedBotContract.KEY_BOT_STEP_DELAY_MS_PREFIX}$botId", 60).coerceIn(0, 2000).toLong()

        // Scope gate verification ONCE before Step 1 begins
        val (initialPkg, initialWin) = resolveActiveScope(context)
        val firstStep = steps.firstOrNull()
        if (firstStep != null) {
            val pkgMismatch = firstStep.packageName != null && firstStep.packageName != initialPkg && initialPkg != "global"
            val winMismatch = firstStep.windowId != null && firstStep.windowId != initialWin && initialWin != "Main"
            if (pkgMismatch || winMismatch) {
                Log.w(TAG, "Macro aborted: initial scope mismatch before Step 1 (live: $initialPkg/$initialWin vs step 1: ${firstStep.packageName}/${firstStep.windowId})")
                return
            }
        }

        for ((stepIndex, step) in steps.withIndex()) {
            // Step 0 always fires immediately — the delay was the user's pre-recording idle time
            val effectiveDelay = if (stepIndex == 0) {
                0L
            } else if (useRecordedDelay) {
                step.delayMs.coerceIn(0L, 5000L)
            } else {
                customStepDelay
            }
            if (effectiveDelay > 0L) {
                delay(effectiveDelay)
            }

            if (step.useElementTarget) {
                // Element-Bound: Match accessibility node on live underlying window and perform action
                var nodeClicked = false
                val service = LightspeedAccessibilityService.instance
                val root = service?.let { LightspeedBotScreenScanner.resolveUnderlyingAppWindow(it)?.root ?: it.rootInActiveWindow }

                if (root != null) {
                    val matchedNode = LightspeedBotScreenScanner.findSmartMatchingNode(root, step, screenW, screenH)
                    if (matchedNode != null) {
                        if (matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            nodeClicked = true
                        } else {
                            val rect = Rect()
                            matchedNode.getBoundsInScreen(rect)
                            if (!rect.isEmpty) {
                                val clickX = rect.centerX().toFloat()
                                val clickY = rect.centerY().toFloat()
                                injectTapOrDispatch(context, clickX, clickY)
                                nodeClicked = true
                            }
                        }
                    }
                }

                if (!nodeClicked) {
                    // NEVER fall back to raw coordinates if the element is not found on screen!
                    Log.w(TAG, "Step ${stepIndex + 1}: Element-bound target not found (${step.viewIdResourceName ?: step.contentDescription ?: step.text}). Aborting step cleanly.")
                }
            } else {
                // Coordinate-Bound step: verify package still matches app, but do NOT abort Step 2+ on windowId changes
                val (livePkg, _) = resolveActiveScope(context)
                val matchesPkg = step.packageName.isNullOrBlank() || step.packageName == livePkg || livePkg == "global"
                if (!matchesPkg) {
                    Log.w(TAG, "Step ${stepIndex + 1}: Coordinate-bound step aborted due to app package mismatch (live: $livePkg vs step: ${step.packageName})")
                } else {
                    val pxX = step.xRatio * screenW
                    val pxY = step.yRatio * screenH
                    if (step.stepType == BotStep.TYPE_SWIPE) {
                        val endX = step.endXRatio * screenW
                        val endY = step.endYRatio * screenH
                        injectSwipeOrDispatch(context, pxX, pxY, endX, endY, step.durationMs)
                    } else {
                        injectTapOrDispatch(context, pxX, pxY)
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
     * Finds the deepest/smallest AccessibilityNodeInfo containing point (x, y) for recording metadata,
     * prioritizing clickable leaf widgets over layout containers.
     */
    fun findSmallestNodeAt(root: AccessibilityNodeInfo?, x: Float, y: Float): AccessibilityNodeInfo? {
        return LightspeedBotScreenScanner.findDeepestNodeAt(root, x, y)
    }

    fun injectTapOrDispatch(context: Context, x: Float, y: Float) {
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = 85L) {
            if (!ShizukuExecHelper.injectTapEvent(x, y)) {
                val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
                if (service != null) dispatchGestureTap(service, x, y)
            }
        }
    }

    fun injectDoubleTapOrDispatch(context: Context, x: Float, y: Float) {
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = 210L) {
            if (!ShizukuExecHelper.injectTapEvent(x, y)) {
                val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
                if (service != null) dispatchGestureTap(service, x, y)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!ShizukuExecHelper.injectTapEvent(x, y)) {
                    val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
                    if (service != null) dispatchGestureTap(service, x, y)
                }
            }, 110L)
        }
    }

    fun injectLongPressOrDispatch(context: Context, x: Float, y: Float, duration: Long = 600L) {
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = duration + 80L) {
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
    }

    fun injectSwipeOrDispatch(context: Context, startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val clampedDuration = duration.coerceIn(50L, 1000L)
        LightspeedBotTrackpadOverlay.performWithPassThrough(startX, startY, endX, endY, holdMs = clampedDuration + 80L) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            dispatchGestureSwipe(service, startX, startY, endX, endY, clampedDuration)
        }
    }

    fun injectHoldAndDragOrDispatch(
        context: Context,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        dragDuration: Long = 400L,
        holdBeforeDragMs: Long = 450L
    ) {
        val totalMs = holdBeforeDragMs + dragDuration
        LightspeedBotTrackpadOverlay.performWithPassThrough(startX, startY, endX, endY, holdMs = totalMs + 80L) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            if (service != null) {
                dispatchGestureHoldAndDrag(service, startX, startY, endX, endY, dragDuration, holdBeforeDragMs)
            } else {
                dispatchGestureSwipe(service, startX, startY, endX, endY, dragDuration)
            }
        }
    }

    private fun dispatchGestureHoldAndDrag(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        dragDuration: Long,
        holdBeforeDragMs: Long
    ) {
        val pathHold = Path().apply { moveTo(startX, startY) }
        val pathDrag = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val clampedDragDuration = dragDuration.coerceIn(150L, 1000L)
        val clampedHoldDuration = holdBeforeDragMs.coerceIn(300L, 800L)

        val holdStroke = GestureDescription.StrokeDescription(pathHold, 0L, clampedHoldDuration, true)
        val gestureHold = GestureDescription.Builder().addStroke(holdStroke).build()

        service.dispatchGesture(gestureHold, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                try {
                    val dragStroke = holdStroke.continueStroke(pathDrag, 0L, clampedDragDuration, false)
                    val gestureDrag = GestureDescription.Builder().addStroke(dragStroke).build()
                    service.dispatchGesture(gestureDrag, null, null)
                } catch (e: Exception) {
                    logSwallowed(TAG, "continueStroke holdAndDrag failed", e)
                    dispatchGestureSwipe(service, startX, startY, endX, endY, clampedDragDuration)
                }
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "HoldAndDrag hold stroke cancelled")
            }
        }, null)
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
