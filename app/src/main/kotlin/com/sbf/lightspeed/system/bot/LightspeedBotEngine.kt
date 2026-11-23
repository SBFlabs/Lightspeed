package com.sbf.lightspeed.system.bot

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedVolumeKeyEngine
import com.sbf.lightspeed.system.ShizukuExecHelper
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.lsString
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

    data class ResolvedMacro(val steps: List<BotStep>, val scopeType: String)

    @Volatile
    var isRecording = false
        private set
    var activeRecordingBotId: Int = 1
        private set

    @Volatile
    var isReplaying = false
        private set
    @Volatile
    private var activePlayingBotId: Int = -1
    private var playJob: Job? = null

    @Volatile
    var isLooping = false
        private set
    @Volatile
    private var activeLoopingBotId: Int = -1
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
    @Volatile
    var recordingScopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
        internal set

    private fun winMacroKey(botId: Int, pkg: String, win: String) = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_win_${pkg}_${win}"
    private fun winScopeKey(botId: Int, pkg: String, win: String) = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_win_${pkg}_${win}"
    private fun appMacroKey(botId: Int, pkg: String) = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}${botId}_app_${pkg}"
    private fun appScopeKey(botId: Int, pkg: String) = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_app_${pkg}"
    private fun globalMacroKey(botId: Int) = "${LightspeedBotContract.PREF_BOT_MACRO_PREFIX}$botId"
    private fun globalScopeKey(botId: Int) = "${LightspeedBotContract.KEY_BOT_MACRO_SCOPE_PREFIX}${botId}_global"

    private fun sanitizeToken(raw: String?): String {
        return LightspeedBotScreenScanner.sanitizeToken(raw)
    }

    private fun isOwnOrSystemPackage(context: Context, pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return true
        if (pkg == context.packageName ||
            pkg == "com.sbf.lightspeed" ||
            pkg == "com.sbf.lightspeed.nightly" ||
            pkg == "com.android.systemui" ||
            pkg.contains("systemui")
        ) return true
        return com.sbf.lightspeed.system.LightspeedForegroundTracker.isInputMethodPackage(context, pkg)
    }

    private fun isStepPackageCompatible(stepPkg: String?, livePkg: String, scopeType: String): Boolean {
        if (scopeType == LightspeedBotContract.SCOPE_GLOBAL) return true
        if (stepPkg.isNullOrBlank() || stepPkg == "global") return true
        if (livePkg.isBlank() || livePkg == "global") return true
        return stepPkg == livePkg
    }

    /**
     * Resolves the currently active foreground (packageName, windowSignature).
     * Ignores Lightspeed's own overlays/dialogs so per-app and per-window scoping
     * stays locked to the user's target application and window.
     */
    fun resolveActiveScope(context: Context): Pair<String, String> {
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
        var pkg: String? = null
        if (service != null) {
            try {
                val appWin = LightspeedBotScreenScanner.resolveUnderlyingAppWindow(service)
                val underlyingPkg = appWin?.root?.packageName?.toString()
                if (!isOwnOrSystemPackage(service, underlyingPkg)) {
                    pkg = underlyingPkg
                }
                if (pkg == null) {
                    val activePkg = service.rootInActiveWindow?.packageName?.toString()
                    if (!isOwnOrSystemPackage(service, activePkg)) {
                        pkg = activePkg
                    }
                }
                if (pkg == null) {
                    val windows = service.windows
                    if (!windows.isNullOrEmpty()) {
                        val sorted = try { windows.sortedByDescending { it.layer } } catch (_: Exception) { windows.reversed() }
                        for (win in sorted) {
                            if (win.type != android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) continue
                            val wPkg = try { win.root?.packageName?.toString() } catch (_: Exception) { null }
                            if (!isOwnOrSystemPackage(service, wPkg)) {
                                pkg = wPkg
                                break
                            }
                        }
                        if (pkg == null) {
                            for (win in sorted) {
                                val wPkg = try { win.root?.packageName?.toString() } catch (_: Exception) { null }
                                if (!isOwnOrSystemPackage(service, wPkg)) {
                                    pkg = wPkg
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        if (pkg == null) {
            pkg = LightspeedAccessibilityService.lastAppPackage?.takeIf { !isOwnOrSystemPackage(context, it) } ?: "global"
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

    fun getScopeDisplayLabel(
        context: Context,
        pkg: String,
        win: String,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
    ): String {
        if (scopeType == LightspeedBotContract.SCOPE_GLOBAL || pkg == "global") return "Global Workspace"
        val appLabel = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
        return if (scopeType == LightspeedBotContract.SCOPE_THIS_WINDOW &&
            win.isNotBlank() && win != "Main" && !win.equals(appLabel, ignoreCase = true)
        ) {
            "$appLabel • $win"
        } else {
            appLabel
        }
    }

    fun getActiveScopeDisplayLabel(
        context: Context,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
    ): String {
        val (pkg, win) = resolveActiveScope(context)
        return getScopeDisplayLabel(context, pkg, win, scopeType)
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
     * Resolves the effective scope for [botId], defaulting new/unset/cleared macros to Per-App
     * while preserving any existing user-saved Per-Window or Global macros.
     */
    fun resolveEffectiveScope(
        context: Context,
        botId: Int,
        scopeOverride: Pair<String, String>? = null
    ): String {
        val prefs = context.defaultPrefs()
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        if (pkg == "global") {
            return if (parseStepsJson(prefs, globalMacroKey(botId)).isNotEmpty() ||
                prefs.getString(globalScopeKey(botId), null) == LightspeedBotContract.SCOPE_GLOBAL
            ) LightspeedBotContract.SCOPE_GLOBAL else LightspeedBotContract.DEFAULT_MACRO_SCOPE
        }
        if (parseStepsJson(prefs, winMacroKey(botId, pkg, win)).isNotEmpty()) {
            return LightspeedBotContract.SCOPE_THIS_WINDOW
        }
        if (parseStepsJson(prefs, appMacroKey(botId, pkg)).isNotEmpty()) {
            return LightspeedBotContract.SCOPE_THIS_APP
        }
        if (parseStepsJson(prefs, globalMacroKey(botId)).isNotEmpty() &&
            prefs.getString(globalScopeKey(botId), null) == LightspeedBotContract.SCOPE_GLOBAL
        ) {
            return LightspeedBotContract.SCOPE_GLOBAL
        }
        val hasLegacyEmptyWin = prefs.contains(winMacroKey(botId, pkg, win))
        val savedWinScope = prefs.getString(winScopeKey(botId, pkg, win), null)
        if (!hasLegacyEmptyWin && savedWinScope == LightspeedBotContract.SCOPE_THIS_WINDOW) {
            return LightspeedBotContract.SCOPE_THIS_WINDOW
        }
        if (savedWinScope == LightspeedBotContract.SCOPE_GLOBAL ||
            prefs.getString(globalScopeKey(botId), null) == LightspeedBotContract.SCOPE_GLOBAL
        ) {
            return LightspeedBotContract.SCOPE_GLOBAL
        }
        return LightspeedBotContract.DEFAULT_MACRO_SCOPE
    }

    private fun macroAndScopeKeys(botId: Int, pkg: String, win: String, scopeType: String): Pair<String, String> =
        when (scopeType) {
            LightspeedBotContract.SCOPE_THIS_WINDOW -> winMacroKey(botId, pkg, win) to winScopeKey(botId, pkg, win)
            LightspeedBotContract.SCOPE_GLOBAL -> globalMacroKey(botId) to globalScopeKey(botId)
            else -> appMacroKey(botId, pkg) to appScopeKey(botId, pkg)
        }

    /**
     * Resolves macro steps for [botId] in a designated scope for editor loading.
     */
    fun getMacroSteps(
        context: Context,
        botId: Int,
        scopeOverride: Pair<String, String>? = null,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
    ): MutableList<BotStep> {
        val prefs = context.defaultPrefs()
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        return parseStepsJson(prefs, macroAndScopeKeys(botId, pkg, win, scopeType).first)
    }

    /**
     * Resolves active playback steps and scope for [botId]:
     * 1. Explicit Per-Window macro for (currentPkg, currentWin) if present.
     * 2. Per-App macro for currentPkg (default scope).
     * 3. Explicit Global macro if configured.
     */
    fun getPlaybackMacro(context: Context, botId: Int): ResolvedMacro? {
        val prefs = context.defaultPrefs()
        val (currentPkg, currentWin) = resolveActiveScope(context)
        if (currentPkg != "global") {
            val winSteps = parseStepsJson(prefs, winMacroKey(botId, currentPkg, currentWin))
            if (winSteps.isNotEmpty()) {
                val firstStep = winSteps.firstOrNull()
                if (isStepPackageCompatible(firstStep?.packageName, currentPkg, LightspeedBotContract.SCOPE_THIS_WINDOW)) {
                    Log.i(TAG, "Resolved Bot $botId: ${winSteps.size} steps (scope=window, pkg=$currentPkg, win=$currentWin)")
                    return ResolvedMacro(winSteps, LightspeedBotContract.SCOPE_THIS_WINDOW)
                }
                Log.w(TAG, "Window macro for $botId targets package ${firstStep?.packageName} but active app is $currentPkg")
            }
            val appSteps = parseStepsJson(prefs, appMacroKey(botId, currentPkg))
            if (appSteps.isNotEmpty()) {
                val hasMismatch = appSteps.any { !isStepPackageCompatible(it.packageName, currentPkg, LightspeedBotContract.SCOPE_THIS_APP) }
                if (!hasMismatch) {
                    Log.i(TAG, "Resolved Bot $botId: ${appSteps.size} steps (scope=app, pkg=$currentPkg)")
                    return ResolvedMacro(appSteps, LightspeedBotContract.SCOPE_THIS_APP)
                }
                Log.w(TAG, "App macro for $botId has package mismatch for $currentPkg")
            }
        }
        if (prefs.getString(globalScopeKey(botId), null) == LightspeedBotContract.SCOPE_GLOBAL) {
            val globalSteps = parseStepsJson(prefs, globalMacroKey(botId))
            if (globalSteps.isNotEmpty()) {
                Log.i(TAG, "Resolved Bot $botId: ${globalSteps.size} steps (scope=global)")
                return ResolvedMacro(globalSteps, LightspeedBotContract.SCOPE_GLOBAL)
            }
        }
        return null
    }

    fun getPlaybackMacroSteps(context: Context, botId: Int): List<BotStep> =
        getPlaybackMacro(context, botId)?.steps ?: emptyList()

    private fun normalizeStepsForScope(steps: List<BotStep>, pkg: String, win: String, scopeType: String): List<BotStep> {
        val effectivePkg = pkg.takeIf { it.isNotBlank() && it != "global" }
        return when (scopeType) {
            LightspeedBotContract.SCOPE_GLOBAL -> steps.map { it.copy(packageName = null) }
            LightspeedBotContract.SCOPE_THIS_WINDOW -> steps.map { it.copy(packageName = effectivePkg, windowId = win) }
            else -> steps.map { it.copy(packageName = effectivePkg) }
        }
    }

    private fun writeMacroToEditor(
        editor: android.content.SharedPreferences.Editor,
        botId: Int,
        pkg: String,
        win: String,
        scopeType: String,
        json: String
    ) {
        val (macroKey, scopeKey) = macroAndScopeKeys(botId, pkg, win, scopeType)
        val scopeValue = when (scopeType) {
            LightspeedBotContract.SCOPE_THIS_WINDOW -> LightspeedBotContract.SCOPE_THIS_WINDOW
            LightspeedBotContract.SCOPE_GLOBAL -> LightspeedBotContract.SCOPE_GLOBAL
            else -> LightspeedBotContract.SCOPE_THIS_APP
        }
        editor.putString(macroKey, json).putString(scopeKey, scopeValue)
    }

    fun saveMacroSteps(
        context: Context,
        botId: Int,
        steps: List<BotStep>,
        scopeOverride: Pair<String, String>? = null,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
    ) {
        if (steps.isEmpty()) {
            clearMacro(context, botId, scopeOverride, scopeType)
            return
        }
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        val normalized = normalizeStepsForScope(steps.take(40), pkg, win, scopeType)
        val arr = JSONArray()
        normalized.forEach { arr.put(it.toJson()) }
        val editor = context.defaultPrefs().edit()
        writeMacroToEditor(editor, botId, pkg, win, scopeType, arr.toString())
        if (!editor.commit()) editor.apply()
        LightspeedBotTrackpadOverlay.onMacroStateChanged(botId)
    }

    fun moveMacroScope(
        context: Context,
        botId: Int,
        steps: List<BotStep>,
        scopeOverride: Pair<String, String>?,
        oldScope: String,
        newScope: String
    ) {
        if (oldScope == newScope) return
        val (pkg, win) = scopeOverride ?: resolveActiveScope(context)
        val (oldMacroKey, oldScopeKey) = macroAndScopeKeys(botId, pkg, win, oldScope)
        val editor = context.defaultPrefs().edit().remove(oldMacroKey).remove(oldScopeKey)
        if (steps.isNotEmpty()) {
            val normalized = normalizeStepsForScope(steps.take(40), pkg, win, newScope)
            val arr = JSONArray()
            normalized.forEach { arr.put(it.toJson()) }
            writeMacroToEditor(editor, botId, pkg, win, newScope, arr.toString())
        }
        if (!editor.commit()) editor.apply()
        LightspeedBotTrackpadOverlay.onMacroStateChanged(botId)
    }

    fun clearMacro(
        context: Context,
        botId: Int,
        scopeOverride: Pair<String, String>? = null,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE
    ) {
        if (isReplaying && (activePlayingBotId == botId || activePlayingBotId == -1)) stopReplay()
        if (isLooping && (activeLoopingBotId == botId || activeLoopingBotId == -1)) stopContinuousLoop()
        if (isRecording && activeRecordingBotId == botId) stopRecording(context, savedSteps = null)

        val prevPkg = lastResolvedPkg
        val prevWin = lastResolvedWindow
        val (livePkg, liveWin) = resolveActiveScope(context)
        val target = scopeOverride ?: (livePkg to liveWin)
        val editor = context.defaultPrefs().edit()
        val scopeCandidates = linkedSetOf(target, livePkg to liveWin, prevPkg to prevWin)
        val hasTargetPkg = target.first.isNotBlank() && target.first != "global"
        for ((p, w) in scopeCandidates) {
            if (p.isBlank() || p == "global") continue
            if (hasTargetPkg && p != target.first) continue
            if (w.isNotBlank()) {
                editor.remove(winMacroKey(botId, p, w)).remove(winScopeKey(botId, p, w))
            }
            editor.remove(appMacroKey(botId, p)).remove(appScopeKey(botId, p))
        }
        editor.remove(globalMacroKey(botId)).remove(globalScopeKey(botId))
        if (!editor.commit()) editor.apply()
        LightspeedBotTrackpadOverlay.onMacroCleared(botId)
        Log.i(TAG, "Cleared macro for Bot $botId (pkg=${target.first}, win=${target.second}, scope=$scopeType)")
    }

    fun startRecording(context: Context, botId: Int, scopeOverride: Pair<String, String>? = null, scopeTypeOverride: String? = null) {
        stopReplay()
        stopContinuousLoop()
        if (isRecording) stopRecording(context)
        val (livePkg, liveWin) = resolveActiveScope(context)
        val (pkg, win) = scopeOverride?.takeIf { it.first.isNotBlank() && it.first != "global" } ?: (livePkg to liveWin)
        recordingScopePkg = pkg
        recordingScopeWindow = win
        activeRecordingBotId = botId.coerceIn(1, LightspeedBotContract.MAX_BOT_MACROS)
        val resolvedScope = scopeTypeOverride ?: resolveEffectiveScope(context, activeRecordingBotId, pkg to win)
        recordingScopeType = if (pkg == "global") LightspeedBotContract.SCOPE_GLOBAL else resolvedScope
        isRecording = true
        Log.i(TAG, "Starting Bot recording for Bot $activeRecordingBotId in scope $pkg / $win ($recordingScopeType)")
        LightspeedBotRecorderOverlay.show(context, activeRecordingBotId)
        if (!LightspeedBotRecorderOverlay.isShowing) {
            LightspeedBotTrackpadOverlay.setPendingRecordingLaunch(false)
        }
    }

    fun stopRecording(context: Context, savedSteps: List<BotStep>? = null) {
        if (!isRecording) return
        isRecording = false
        if (!savedSteps.isNullOrEmpty()) {
            saveMacroSteps(context, activeRecordingBotId, savedSteps, recordingScopePkg to recordingScopeWindow, recordingScopeType)
            Log.i(TAG, "Saved ${savedSteps.size} steps for Bot $activeRecordingBotId in scope $recordingScopePkg / $recordingScopeWindow ($recordingScopeType)")
        }
        LightspeedBotRecorderOverlay.hide()
    }

    fun stopReplay() {
        isReplaying = false
        activePlayingBotId = -1
        playJob?.cancel()
        playJob = null
        LightspeedBotTrackpadOverlay.endPlaybackPassThrough()
    }

    /**
     * Emergency Hardware Key Interception:
     * Immediately stops active loop, macro replay, and cancels recording mode without saving.
     * Triggered on Volume Up, Volume Down, or Power key presses, or screen off events.
     * Returns true if any active automation or recording was cancelled.
     */
    fun cancelAllAutomationAndRecording(context: Context): Boolean {
        var didCancel = false
        if (isLooping) { stopContinuousLoop(); didCancel = true }
        if (isReplaying) { stopReplay(); didCancel = true }
        if (isRecording || LightspeedBotRecorderOverlay.isShowing) { stopRecording(context, savedSteps = null); didCancel = true }
        try { LightspeedVolumeKeyEngine.stopAutoRepeat() } catch (_: Throwable) {}
        if (didCancel) {
            try {
                LightspeedHapticEngine.triggerConfirmation(context)
                mainHandler.post {
                    android.widget.Toast.makeText(
                        context.applicationContext, "⚡ Macro / Recording cancelled by hardware key", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Throwable) {}
        }
        return didCancel
    }

    fun playBot(
        context: Context, botId: Int, stepsOverride: List<BotStep>? = null, scopeTypeOverride: String? = null,
        expectedScopeOverride: Pair<String, String>? = null, onComplete: () -> Unit = {}
    ) {
        if (isReplaying) { stopReplay(); onComplete(); return }
        val resolved = if (stepsOverride != null) {
            ResolvedMacro(stepsOverride, scopeTypeOverride ?: resolveEffectiveScope(context, botId, expectedScopeOverride))
        } else getPlaybackMacro(context, botId)
        if (resolved == null || resolved.steps.isEmpty()) {
            Log.w(TAG, "Bot $botId has no macro for active app/window")
            mainHandler.post {
                val msg = lsString(R.string.bot_no_macro_for_app).ifBlank { "No macro recorded for this app" }
                android.widget.Toast.makeText(context.applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
            onComplete()
            return
        }
        val repeatPref = context.defaultPrefs().getInt("${LightspeedBotContract.KEY_BOT_MACRO_REPEAT_PREFIX}$botId", 1)
        val repeatCount = if (repeatPref <= 0) 1 else repeatPref
        playJob = scope.launch {
            isReplaying = true
            activePlayingBotId = botId
            try {
                for (rep in 0 until repeatCount) {
                    if (!isActive || !isReplaying) break
                    executeStepsSequence(context, resolved.steps, botId, resolved.scopeType, expectedScopeOverride)
                    if (rep < repeatCount - 1) delay(200L)
                }
            } finally {
                isReplaying = false
                activePlayingBotId = -1
                withContext(Dispatchers.Main) { onComplete() }
            }
        }
    }

    fun startContinuousLoop(
        context: Context, botId: Int, stepsOverride: List<BotStep>? = null, scopeTypeOverride: String? = null,
        expectedScopeOverride: Pair<String, String>? = null, onIteration: (Int) -> Unit = {}
    ) {
        stopContinuousLoop()
        isLooping = true
        activeLoopingBotId = botId
        loopIterationCount = 0
        val resolved = if (stepsOverride != null) {
            ResolvedMacro(stepsOverride, scopeTypeOverride ?: resolveEffectiveScope(context, botId, expectedScopeOverride))
        } else getPlaybackMacro(context, botId)
        val steps = resolved?.steps ?: emptyList()
        val scopeType = resolved?.scopeType ?: LightspeedBotContract.DEFAULT_MACRO_SCOPE
        val cadence = context.defaultPrefs().getLong(LightspeedBotContract.KEY_BOT_ACTIVE_LOOP_CADENCE_MS, 400L).coerceAtLeast(100L)

        loopJob = scope.launch {
            while (isActive && isLooping) {
                loopIterationCount++
                withContext(Dispatchers.Main) { onIteration(loopIterationCount) }
                if (steps.isNotEmpty()) {
                    executeStepsSequence(context, steps, botId, scopeType, expectedScopeOverride)
                } else {
                    val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
                    val cx = LightspeedBotTrackpadOverlay.cursorX.takeIf { it > 0f } ?: (screenW / 2f)
                    val cy = LightspeedBotTrackpadOverlay.cursorY.takeIf { it > 0f } ?: (screenH / 2f)
                    injectTapOrDispatch(context, cx, cy)
                }
                delay(cadence)
            }
        }
    }

    fun stopContinuousLoop() {
        isLooping = false
        activeLoopingBotId = -1
        loopJob?.cancel()
        loopJob = null
        loopIterationCount = 0
        LightspeedBotTrackpadOverlay.endPlaybackPassThrough()
    }

    private suspend fun executeStepsSequence(
        context: Context, steps: List<BotStep>, botId: Int = 1,
        scopeType: String = LightspeedBotContract.DEFAULT_MACRO_SCOPE, expectedScopeOverride: Pair<String, String>? = null
    ) {
        val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "Macro playback aborted: LightspeedAccessibilityService is unavailable")
            return
        }
        val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
        val prefs = context.defaultPrefs()
        val useRecordedDelay = prefs.getBoolean("${LightspeedBotContract.KEY_BOT_USE_RECORDED_DELAY_PREFIX}$botId", false)
        val customStepDelay = prefs.getInt("${LightspeedBotContract.KEY_BOT_STEP_DELAY_MS_PREFIX}$botId", 60).coerceIn(0, 2000).toLong()

        val (liveInitialPkg, liveInitialWin) = resolveActiveScope(context)
        val initialPkg = if (liveInitialPkg == "global" && expectedScopeOverride != null) expectedScopeOverride.first else liveInitialPkg
        val initialWin = if (liveInitialPkg == "global" && expectedScopeOverride != null) expectedScopeOverride.second else liveInitialWin
        val firstStep = steps.firstOrNull()
        if (firstStep != null && scopeType != LightspeedBotContract.SCOPE_GLOBAL) {
            val pkgMismatch = !isStepPackageCompatible(firstStep.packageName, initialPkg, scopeType)
            val winMismatch = scopeType == LightspeedBotContract.SCOPE_THIS_WINDOW &&
                firstStep.windowId != null && firstStep.windowId != initialWin && initialWin != "Main"
            if (pkgMismatch || winMismatch) {
                Log.w(TAG, "Macro aborted: scope mismatch ($scopeType) before Step 1 (live: $initialPkg/$initialWin vs step 1: ${firstStep.packageName}/${firstStep.windowId})")
                return
            }
        }

        withContext(Dispatchers.Main) {
            LightspeedBotTrackpadOverlay.beginPlaybackPassThrough()
        }
        try {
            // Allow SurfaceFlinger/InputFlinger to commit retracted + FLAG_NOT_TOUCHABLE before Step 1
            delay(70L)

            for ((stepIndex, step) in steps.withIndex()) {
                if (!currentCoroutineContext().isActive || (!isReplaying && !isLooping)) break

                // Step 0 fires immediately; delay before step 0 was pre-recording idle time
                val effectiveDelay = when {
                    stepIndex == 0 -> 0L
                    useRecordedDelay -> step.delayMs.coerceIn(0L, 5000L)
                    else -> customStepDelay.coerceAtLeast(15L)
                }
                if (effectiveDelay > 0L) delay(effectiveDelay)
                if (!currentCoroutineContext().isActive || (!isReplaying && !isLooping)) break

                if (step.stepType == BotStep.TYPE_DELAY) {
                    delay(step.durationMs.coerceIn(0L, 10_000L))
                    continue
                }

                if (step.useElementTarget) {
                    var nodeClicked = false
                    val root = LightspeedBotScreenScanner.resolveUnderlyingAppWindow(service)?.root ?: service.rootInActiveWindow
                    val matchedNode = root?.let { LightspeedBotScreenScanner.findSmartMatchingNode(it, step, screenW, screenH) }
                    if (matchedNode != null) {
                        if (matchedNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                            nodeClicked = true
                        } else {
                            val rect = Rect()
                            matchedNode.getBoundsInScreen(rect)
                            if (!rect.isEmpty) {
                                nodeClicked = LightspeedBotGestureDispatcher.dispatchTapAwait(
                                    service, rect.centerX().toFloat(), rect.centerY().toFloat(),
                                    step.durationMs, screenW, screenH, "Bot$botId#Step${stepIndex + 1}[elem-tap]"
                                )
                            }
                        }
                    }
                    if (!nodeClicked) {
                        // NEVER fall back to raw coordinates if the element is not found on screen!
                        Log.w(TAG, "Step ${stepIndex + 1}: Element-bound target not found (${step.viewIdResourceName ?: step.contentDescription ?: step.text}). Aborting step cleanly.")
                    }
                } else {
                    val (livePkg, _) = resolveActiveScope(context)
                    if (!isStepPackageCompatible(step.packageName, livePkg, scopeType)) {
                        Log.w(TAG, "Step ${stepIndex + 1}: Coordinate-bound step aborted due to app package mismatch (live: $livePkg vs step: ${step.packageName})")
                    } else {
                        val pxX = step.xRatio * screenW
                        val pxY = step.yRatio * screenH
                        if (step.stepType == BotStep.TYPE_SWIPE) {
                            val endX = step.endXRatio * screenW
                            val endY = step.endYRatio * screenH
                            Log.i(TAG, "Bot$botId Step ${stepIndex + 1}/${steps.size}: SWIPE ($pxX,$pxY)->($endX,$endY) dur=${step.durationMs}ms")
                            LightspeedBotGestureDispatcher.dispatchSwipeAwait(
                                service, pxX, pxY, endX, endY, step.durationMs, screenW, screenH, "Bot$botId#Step${stepIndex + 1}[swipe]"
                            )
                        } else {
                            Log.i(TAG, "Bot$botId Step ${stepIndex + 1}/${steps.size}: TAP ($pxX,$pxY) dur=${step.durationMs}ms")
                            LightspeedBotGestureDispatcher.dispatchTapAwait(
                                service, pxX, pxY, step.durationMs, screenW, screenH, "Bot$botId#Step${stepIndex + 1}[tap]"
                            )
                        }
                    }
                }
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { LightspeedBotTrackpadOverlay.endPlaybackPassThrough() }
        }
    }

    /** Traverses accessibility tree to find node matching screen conditions. */
    fun findMatchingNode(root: AccessibilityNodeInfo, step: BotStep): AccessibilityNodeInfo? {
        step.viewIdResourceName?.takeIf { it.isNotBlank() }?.let { id ->
            root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }
        step.text?.takeIf { it.isNotBlank() }?.let { txt ->
            root.findAccessibilityNodeInfosByText(txt)?.firstOrNull()?.let { return it }
        }
        step.contentDescription?.takeIf { it.isNotBlank() }?.let { desc ->
            root.findAccessibilityNodeInfosByText(desc)?.firstOrNull()?.let { return it }
        }
        return null
    }

    /** Finds the deepest/smallest AccessibilityNodeInfo containing point (x, y) for recording metadata. */
    fun findSmallestNodeAt(root: AccessibilityNodeInfo?, x: Float, y: Float): AccessibilityNodeInfo? =
        LightspeedBotScreenScanner.findDeepestNodeAt(root, x, y)

    private fun dispatchTapOnce(context: Context, x: Float, y: Float) {
        if (!ShizukuExecHelper.injectTapEvent(x, y)) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return
            val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
            LightspeedBotGestureDispatcher.dispatchTapAsync(service, x, y, 45L, screenW, screenH, "CursorTap")
        }
    }

    fun injectTapOrDispatch(context: Context, x: Float, y: Float) {
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = 95L) { dispatchTapOnce(context, x, y) }
    }

    fun injectDoubleTapOrDispatch(context: Context, x: Float, y: Float) {
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = 220L) {
            dispatchTapOnce(context, x, y)
            mainHandler.postDelayed({ dispatchTapOnce(context, x, y) }, 110L)
        }
    }

    fun injectLongPressOrDispatch(context: Context, x: Float, y: Float, duration: Long = 600L) {
        val safeDur = LightspeedBotGestureDispatcher.clampTapDuration(duration)
        LightspeedBotTrackpadOverlay.performWithPassThrough(x, y, holdMs = safeDur + 90L) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance
            if (service != null) {
                val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
                LightspeedBotGestureDispatcher.dispatchTapAsync(service, x, y, safeDur, screenW, screenH, "CursorLongPress")
            } else {
                ShizukuExecHelper.injectTapEvent(x, y)
            }
        }
    }

    fun injectSwipeOrDispatch(context: Context, startX: Float, startY: Float, endX: Float, endY: Float, duration: Long) {
        val clampedDuration = LightspeedBotGestureDispatcher.clampSwipeDuration(duration)
        LightspeedBotTrackpadOverlay.performWithPassThrough(startX, startY, endX, endY, holdMs = clampedDuration + 90L) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return@performWithPassThrough
            val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
            LightspeedBotGestureDispatcher.dispatchSwipeAsync(
                service, startX, startY, endX, endY, clampedDuration, screenW, screenH, "CursorSwipe"
            )
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
        LightspeedBotTrackpadOverlay.performWithPassThrough(startX, startY, endX, endY, holdMs = holdBeforeDragMs + dragDuration + 90L) {
            val service = (context as? AccessibilityService) ?: LightspeedAccessibilityService.instance ?: return@performWithPassThrough
            val (screenW, screenH) = LightspeedBotGestureDispatcher.resolveDisplayBounds(context)
            LightspeedBotGestureDispatcher.dispatchHoldAndDragAsync(
                service, startX, startY, endX, endY, dragDuration, holdBeforeDragMs, screenW, screenH
            )
        }
    }
}
