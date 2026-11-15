package com.sbf.lightspeed.settings

import android.content.Context
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.InstalledTacticalToolsScanner
import com.sbf.lightspeed.system.OemNotchDetector
import com.sbf.lightspeed.system.logSwallowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates pre-warming of heavy Central Command I/O caches (action icons, PackageManager
 * tactical tools, active Deck theme snapshot, and typography scanner) off the main thread.
 *
 * Ensures cold opens after device boot, deep sleep, force stop, or low-memory trims animate
 * cleanly on budget hardware without stalling the slide-up entrance.
 */
object CentralCommandWarmupCoordinator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val warmupMutex = Mutex()
    private var scheduledJob: Job? = null

    @Volatile
    var isWarm: Boolean = false
        private set

    @Volatile
    var isUiWarmed: Boolean = false
        private set

    private val _warmFlow = MutableStateFlow(false)
    val warmFlow: StateFlow<Boolean> = _warmFlow.asStateFlow()

    /** Marks the Compose UI hierarchy and GPU blur shaders as warmed for this process session. */
    fun markUiWarmed() {
        isUiWarmed = true
    }

    /** Marks only the UI warm state cold (e.g., on memory trim) so the next open re-stages smoothly. */
    fun markUiCold() {
        isUiWarmed = false
    }

    /**
     * Marks cached Central Command state as cold after process memory pressure or trim events
     * so the next open or idle window refreshes cleanly.
     */
    fun markCold() {
        isWarm = false
        isUiWarmed = false
        _warmFlow.value = false
    }

    /**
     * Schedules a single-flight background warm-up shortly after the accessibility service binds
     * (after reboot, service toggle, or crash recovery) so opening Central Command feels instant.
     */
    fun scheduleServiceWarmup(context: Context, delayMs: Long = 2200L) {
        if (isWarm) return
        val appContext = context.applicationContext
        scheduledJob?.cancel()
        scheduledJob = scope.launch {
            delay(delayMs)
            warmImmediatelyAsync(appContext)
        }
    }

    /**
     * Immediately warms Central Command registries and scanners on [Dispatchers.IO].
     * Coalesces concurrent callers so PackageManager and action token scans execute at most once.
     */
    suspend fun warmImmediatelyAsync(context: Context) {
        val appContext = context.applicationContext
        warmupMutex.withLock {
            if (isWarm) return
            withContext(Dispatchers.IO) {
                try {
                    DeckThemeManager.getSnapshot(appContext)
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "DeckThemeManager", e)
                }
                try {
                    BackdropBlurModeManager.getMode(appContext)
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "BackdropBlurModeManager", e)
                }
                try {
                    InstalledTacticalToolsScanner.scan(appContext)
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "InstalledTacticalToolsScanner", e)
                }
                try {
                    LightspeedActionRegistry.initializeAsync(appContext)
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "LightspeedActionRegistry", e)
                }
                try {
                    DeviceFontScanner.loadCleanResolvedFontsOnDemand()
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "DeviceFontScanner", e)
                }
                try {
                    OemNotchDetector.getDetectedFeatureName()
                } catch (e: Exception) {
                    logSwallowed("CentralCommandWarmup", "OemNotchDetector", e)
                }
                isWarm = true
                _warmFlow.value = true
            }
        }
    }
}
