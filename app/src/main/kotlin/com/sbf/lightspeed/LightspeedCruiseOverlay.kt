package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import android.accessibilityservice.AccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedHudRenderer
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
import com.sbf.lightspeed.system.OverlayGlowDelegate
import com.sbf.lightspeed.system.defaultPrefs
import java.net.URISyntaxException
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class LightspeedCruiseOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    internal var cruiseAppWidgetHost: android.appwidget.AppWidgetHost? = null
    internal var cruiseAppWidgetHostView: android.appwidget.AppWidgetHostView? = null
    internal var currentBoundWidgetId: Int = -1
    internal var cruiseAppWidgetHostView2: android.appwidget.AppWidgetHostView? = null
    internal var currentBoundWidgetId2: Int = -1
    internal val cruiseAppWidgetHostViews = mutableMapOf<Int, android.appwidget.AppWidgetHostView>()
    internal val cruiseAppWidgetHostContainers = mutableMapOf<Int, ScrollableAppWidgetContainer>()
    internal var widgetSettlePending: Boolean = false
    internal var lastWidgetCategorySwitchMs: Long = 0L
    internal val widgetSettleRunnable = Runnable {
        widgetSettlePending = false
        updateCategoryAppWidgetView()
        invalidate()
    }
    internal var isHudEditMode: Boolean = false
    internal var isHudAddWidgetMenuOpen: Boolean = false
    internal var isHudTuneModalOpen: Boolean = false
    internal var hudTuneTargetItem: CategoryHudItem? = null
    internal var pendingDeleteHudItem: CategoryHudItem? = null
    internal var activeDraggingSliderId: String? = null
    internal val activeCategoryHudItems = mutableListOf<CategoryHudItem>()
    internal var isCountdownModalOpen: Boolean = false
    internal var countdownModalTargetMs: Long = 0L
    internal var countdownModalTitle: String = "TARGET"
    internal var countdownModalCatId: String = "comm"
    internal var isScreenTimeInfoBubbleOpen: Boolean = false
    internal var screenTimeInfoBubbleRect: android.graphics.RectF = android.graphics.RectF()
    internal var isPrivacyScoreInfoBubbleOpen: Boolean = false
    internal var privacyScoreInfoRect: android.graphics.RectF = android.graphics.RectF()
    internal val vitalsTempGaugeRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val vitalsRamGaugeRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val vitalsSwapGaugeRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val vitalsIntStorageBarRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val vitalsExtStorageBarRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val hudChipInfoRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val hudChipPrimaryRects: MutableMap<Int, android.graphics.RectF> = HashMap()
    internal val hudChipSecondaryRects: MutableMap<Int, android.graphics.RectF> = HashMap()

    internal fun vitalsRectForSlot(
        map: MutableMap<Int, android.graphics.RectF>,
        slotIndex: Int
    ): android.graphics.RectF = map.getOrPut(slotIndex) { android.graphics.RectF() }
    internal var hudAlertsPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var hudFirewallPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var pinnedMenuNetPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var pinnedMenuAlertsPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var pinnedMenuPrivacyInfoRect: android.graphics.RectF = android.graphics.RectF()
    internal var isAlertsHistoryModalOpen: Boolean = false
    internal var isFirewallModalOpen: Boolean = false
    internal var firewallBlockedAccordionOpen: Boolean = true
    internal var firewallAllowedAccordionOpen: Boolean = true
    internal var firewallOfflineAccordionOpen: Boolean = false
    internal var firewallModalScrollOffset: Float = 0f
    internal var firewallModalMaxScroll: Float = 0f
    internal var firewallModalDraggingRail: Boolean = false
    internal var firewallModalRect: android.graphics.RectF = android.graphics.RectF()
    internal var firewallCloseRect: android.graphics.RectF = android.graphics.RectF()
    internal var firewallFastRailRect: android.graphics.RectF = android.graphics.RectF()
    internal val firewallAccordionHeaderRects = mutableMapOf<String, android.graphics.RectF>()
    internal var firewallAppRowRects = mutableMapOf<String, android.graphics.RectF>()
    internal var firewallCachedApps: List<FirewallAppEntry> = emptyList()
    internal var isVitalsModalOpen: Boolean = false
    internal var vitalsModalSpanKey: String = "3H"
    internal var vitalsModalScrollOffset: Float = 0f
    internal var vitalsModalMaxScroll: Float = 0f
    internal var vitalsModalDraggingRail: Boolean = false
    internal var vitalsModalLoadingApps: Boolean = false
    internal var vitalsModalRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsCloseRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsTempUnitToggleRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsFastRailRect: android.graphics.RectF = android.graphics.RectF()
    internal val vitalsSpanPillRects = mutableMapOf<String, android.graphics.RectF>()
    internal val vitalsForceStopRects = mutableMapOf<String, android.graphics.RectF>()
    internal var vitalsCachedMemApps: List<AppMemoryEntry> = emptyList()
    internal var vitalsStorageVolumeFilter: String = "ALL"
    internal var vitalsStorageSortKey: String = "total"
    internal var vitalsStorageSortReversed: Boolean = false
    internal var vitalsStorageShowSystemApps: Boolean = false
    internal var vitalsAllStorageApps: List<AppStorageEntry> = emptyList()
    internal var vitalsFilteredStorageApps: List<AppStorageEntry> = emptyList()
    internal var vitalsHasUsageAccessForStorage: Boolean = true
    internal var vitalsStorageHasExternalApps: Boolean = false
    internal val vitalsStorageVolumePillRects = mutableMapOf<String, android.graphics.RectF>()
    internal val vitalsStorageSortPillRects = mutableMapOf<String, android.graphics.RectF>()
    internal var vitalsStorageReversePillRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsStorageRefreshPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsStorageSystemPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsStoragePermBannerRect: android.graphics.RectF = android.graphics.RectF()
    internal var vitalsStorageChangeBannerRect: android.graphics.RectF = android.graphics.RectF()
    internal val vitalsStorageAppInfoRects = mutableMapOf<String, android.graphics.RectF>()
    internal var vitalsStorageScanDone: Int = 0
    internal var vitalsStorageScanTotal: Int = 0
    internal var vitalsStorageScanning: Boolean = false
    internal val vitalsStorageScanToken = java.util.concurrent.atomic.AtomicInteger(0)
    internal val vitalsStorageNewlyResolvedMs = mutableMapOf<String, Long>()
    internal val vitalsStorageJumpMarkers = mutableListOf<StorageJumpMarker>()
    internal var vitalsStorageRowH: Float = 0f
    internal var vitalsStorageDragY: Float = -1f
    internal var vitalsStorageTickerActive: Boolean = false
    internal var vitalsStoragePrevSummarySum: Long = -1L
    internal var vitalsStorageSumFlashUntilMs: Long = 0L
    internal val hudLiveRefreshRunnable = Runnable {
        if (isAttachedToWindow &&
            (currentLayer == CruiseLayer.CATEGORY ||
                currentLayer == CruiseLayer.GRID ||
                currentLayer == CruiseLayer.STICKY_PIN)
        ) {
            invalidate()
        }
    }
    internal var alertsHistoryWindowKey: String = "24H"
    internal var alertsHistorySelectedPkg: String? = null
    internal var alertsHistoryScrollOffset: Float = 0f
    internal var alertsHistoryMaxScroll: Float = 0f
    internal var alertsHistoryDraggingRail: Boolean = false
    internal var alertsHistoryModalRect: android.graphics.RectF = android.graphics.RectF()
    internal var alertsHistoryCloseRect: android.graphics.RectF = android.graphics.RectF()
    internal var alertsHistoryFastRailRect: android.graphics.RectF = android.graphics.RectF()
    internal val alertsHistoryWindowPillRects = mutableMapOf<String, android.graphics.RectF>()
    internal val alertsHistoryAppPillRects = mutableMapOf<String, android.graphics.RectF>()
    internal val tuneFontPillRects = mutableMapOf<Int, android.graphics.RectF>()
    internal var tuneFontDropdownRect: android.graphics.RectF = android.graphics.RectF()
    internal var isTuneFontDropdownOpen: Boolean = false
    internal var tuneFontDropdownScrollOffset: Float = 0f
    internal var tuneFontDropdownMaxScroll: Float = 0f
    internal var tuneFontDropdownListRect: android.graphics.RectF = android.graphics.RectF()
    internal val tuneFontDropdownItemRects = mutableMapOf<String, android.graphics.RectF>()
    internal var tuneFontDropdownDragStartY: Float = 0f
    internal var tuneFontDropdownLastY: Float = 0f
    internal var tuneFontDropdownDragged: Boolean = false
    internal var tuneFontDropdownTracking: Boolean = false
    internal var tuneTempUnitPillRect: android.graphics.RectF = android.graphics.RectF()
    internal var pinnedEdgeSwitcherRect: android.graphics.RectF = android.graphics.RectF()
    internal var isDraggingEdgeCategorySwitcher: Boolean = false

    init {
        setWillNotDraw(false)
        initAppWidgetHost()
    }

    internal val totalGearSetsCount: Int
        get() {
            val count = getGearSetsOrder(isOpenedFromLeftFlank).size
            val isUnlocked = com.sbf.lightspeed.system.LightspeedInfinityManager.isUnlocked(context)
            val effective = if (isUnlocked) count else count.coerceAtMost(2)
            return if (effective > 0) effective else 1
        }



    internal val service = context as? LightspeedAccessibilityService
    internal val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    internal var isCruising = false
    internal var isStickyPinned = false
    internal var currentLayer: CruiseLayer = CruiseLayer.HIDDEN
        set(value) {
            field = value
            refreshActiveRenderEffect()
        }
    internal var currentActiveZone = TouchZone.NONE
    internal var isLockscreenPillOnly = false

    fun setLockscreenPillOnlyMode(enabled: Boolean) {
        if (isLockscreenPillOnly != enabled) {
            isLockscreenPillOnly = enabled
            post {
                updateMetricsDimensions()
                invalidate()
            }
        }
    }

    internal var centerHeightPx = 400f
    internal var centerVisualWidthPx = 12f
    internal var centerTouchWidthPx = 45f
    internal var centerYOffsetPx = 0f

    internal var topHeightPx = 300f
    internal var topVisualWidthPx = 2f
    internal var topTouchWidthPx = 32f

    internal var bottomHeightPx = 300f
    internal var bottomVisualWidthPx = 2f
    internal var bottomTouchWidthPx = 32f

    internal var linkEdges = false

    internal val topTouchBounds = RectF()
    internal val centerTouchBounds = RectF()
    internal val bottomTouchBounds = RectF()

    internal val topVisualBounds = RectF()
    internal val centerVisualBounds = RectF()
    internal val bottomVisualBounds = RectF()

    // Cached backdrop RadialGradient for FAVORITES_GEARS layer — rebuilt only on size change
    internal var spaceGradShader: android.graphics.RadialGradient? = null
    internal var spaceGradKey: Long = Long.MIN_VALUE

    internal val uiHandler = Handler(Looper.getMainLooper())
    internal var touchDownTime = 0L
    internal var gestureStartX = 0f
    internal var gestureStartY = 0f
    internal var macroTrackingActive = false
    internal var currentDetectedGesture = MacroGesture.NONE

    internal val glowDelegate = OverlayGlowDelegate(this)
    internal val glowFraction: Float get() = glowDelegate.glowFraction

    fun triggerGlow(durationMs: Long = -1L) {
        glowDelegate.triggerGlow(context, durationMs)
    }

    internal var trackingStateLocked = false
    internal var isCurrentlyTouched = false
    internal var initialLeftSweepDistance = 0f
    internal var lowestXReached = 0f
    internal var highestYReached = 0f
    internal var lowestYReached = 0f
    
    internal var aggregateScrubAccumulator = 0f
    internal var isScrubEntranceHapticFired = false

    internal fun triggerHardwareHaptic(durationMs: Long, amplitude: Int) {
        LightspeedHapticEngine.vibrate(context, durationMs, amplitude)
    }

    internal fun triggerDeflectorHaptic(isClick: Boolean = false) {
        if (isClick) {
            LightspeedHapticEngine.deflectorClick(context)
        } else {
            LightspeedHapticEngine.deflectorTick(context)
        }
    }
    internal var lastPermissionToastTime = 0L
    internal var overScrollBoundaryAccumulator = 0f
    internal var settingsCategoryAppended = false
    internal var hangarBayScrollOffset = 0f
    internal var hangarBayTouchDownX = 0f
    internal var hangarBayTouchDownY = 0f
    internal var isDraggingHangarBays = false
    internal var isSpinningHangarRing = false
    internal var hangarSpinTouchY = 0f
    internal var activeHangarRing = 0
    internal var isHangarEjectArmed = false
    internal var hangarEjectTargetIndex = -1
    internal var hangarEjectRing = -1
    internal var longPressHangarBayRunnable: Runnable? = null
    internal var longPressCogRunnable: Runnable? = null
    internal var hasLongPressFired = false
    internal var isTouchingFocusedCog = false
    internal var isOpenedFromLeftFlank = false
    @Volatile var isDispatchingFromLeftFlankView: Boolean = false
    internal val gearRingPath = android.graphics.Path()

    internal var activeHoldScrubAction: String? = null
    internal var activeHoldScrubActionKey: String? = null
    internal var activeScrubVolumePct: Int = -1
    internal var activeScrubBrightness: Int = -1
    internal var scrubHudTitle = ""
    internal var scrubHudValue = ""
    internal var isHoldFired = false

    internal val holdTimerRunnable = Runnable {
        resolveAndDispatchHoldGesture(currentActiveZone)
    }

    internal val neutralToCategoryRunnable = Runnable {
        if (isCruising && currentLayer == CruiseLayer.NEUTRAL) {
            currentLayer = CruiseLayer.CATEGORY
            categoryScrubbingEngaged = true
            entranceStartTime = System.currentTimeMillis()
            invalidate()
        }
    }

    internal val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_") || key.startsWith("pref_deflector_") || key.startsWith("pref_gear_") || key.startsWith("pref_cockpit_"))) {
            post {
                updateRenderCache()
                updateMetricsDimensions()
                invalidate()
            }
        }
    }

    internal var cachedRenderEffect: RenderEffect? = null

    internal var cachedCategories = listOf<LightspeedDataBridge.CategoryNode>()
    internal var activeCatIndex = -1
    internal var initialCatIndex = 0
    internal var categoryVisualOffset = 0f
    internal var entranceStartTime = 0L
    internal var snapAnimator: ValueAnimator? = null
    internal var isCategoryVectorLocked = false
    internal var gridEntryRawX = 0f
    internal var gridEntryRawY = 0f
    internal var gridDeadZoneLatched = false

    internal var cachedApps = listOf<LightspeedDataBridge.LaunchTarget>()
    internal var placedAppsList = mutableListOf<PlacedItem>()
    internal var totalGridContentHeight = 0f
    internal var viewportScrollOffset = 0f
    internal var hudScrollOffset = 0f
    internal var screenTimeAppsScrollOffset = 0f
    internal var screenTimeAppsMaxScroll = 0f
    internal val batteryAppsScrollOffsets: MutableMap<Int, Float> = HashMap()
    internal val batteryAppsMaxScrolls: MutableMap<Int, Float> = HashMap()

    internal fun getBatteryAppsScrollOffset(slotIndex: Int): Float =
        batteryAppsScrollOffsets[slotIndex] ?: 0f

    internal fun setBatteryAppsScrollOffset(slotIndex: Int, offset: Float) {
        batteryAppsScrollOffsets[slotIndex] = offset
    }

    internal fun getBatteryAppsMaxScroll(slotIndex: Int): Float =
        batteryAppsMaxScrolls[slotIndex] ?: 0f

    internal fun setBatteryAppsMaxScroll(slotIndex: Int, maxScroll: Float) {
        batteryAppsMaxScrolls[slotIndex] = maxScroll
    }
    internal var activeItem: LightspeedDataBridge.LaunchTarget? = null
    internal var pinnedMenuApp: LightspeedDataBridge.LaunchTarget? = null
    internal var pinnedMenuX = 0f
    internal var pinnedMenuY = 0f
    internal var pinnedMenuCategoryMode = false
    internal var isEditModeOpen = false
    internal var editModeTab = EditModeTab.PAGE
    internal var editModeCategories = mutableListOf<LightspeedDataBridge.CategoryNode>()
    internal var stickyPinLongPressRunnable: Runnable? = null
    internal var stickyPinLongPressFired = false
    internal var editModeHoldFired = false
    internal var editModeSavedCategories = listOf<LightspeedDataBridge.CategoryNode>()
    internal var isEditModePromptOpen = false
    internal var isCategoryIconPickerOpen = false
    internal var categoryIconPickerTarget: LightspeedDataBridge.CategoryNode? = null
    internal var editModeHasDragged = false
    internal var editModeDragSourceIndex = -1
    internal var editModeDragTargetIndex = -1
    internal var editModeDragY = 0f
    internal var editModeDragRunnable: Runnable? = null
    internal var hudVisibilityLongPressRunnable: Runnable? = null

    internal val categoryAppsCache = mutableMapOf<String, List<LightspeedDataBridge.LaunchTarget>>()
    internal val categoryGridCache = mutableMapOf<String, List<PlacedItem>>()
    internal val categoryHeightCache = mutableMapOf<String, Float>()
    internal val applicationIconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
    internal val vitalsRowIconLruCache = android.util.LruCache<String, Drawable>(64)
    private val overlayIoScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    internal var categoryIconJob: Job? = null
    internal var lastLoadedCategoryId: String? = null

    // --- GYROSCOPE CORE WORKSPACE STATES ---
    internal var activeGearRing = 0          // 0 = Outer Ring, 1 = Inner Ring, 2 = Center Control Hub
    internal var activeGearSetIndex = 0      // Active profile face (Set A, B, C, D)
    internal var gearRingRotations = FloatArray(3) { 0f }
    internal var rawHorizontalXAccumulator = 0f
    internal var isCubeRotationFired = false
    internal var lastTargetedIndex = intArrayOf(-1, -1)

    internal val gearAutoRepeatRunnable = object : Runnable {
        override fun run() {
            activeGearSetIndex = (activeGearSetIndex + 1) % totalGearSetsCount
            triggerHardwareHaptic(40, 200)
            invalidate()
            uiHandler.postDelayed(this, 350L)
        }
    }

    internal fun stopGearAutoRepeat() {
        uiHandler.removeCallbacks(gearAutoRepeatRunnable)
        persistActiveGearSetIndex()
    }

    internal fun triggerGearCogHaptic() {
        val prefs = prefs()
        val strength = prefs.getString(LightspeedPreferences.KEY_COCKPIT_HAPTICS, "")?.takeIf { it.isNotEmpty() }
            ?: (prefs.getString(LightspeedPreferences.KEY_GEAR_HAPTIC_STRENGTH, "tactical") ?: "tactical")
        when (strength) {
            "subtle", "soft", "soft_low_freq" -> triggerHardwareHaptic(10, 50)
            "heavy" -> triggerHardwareHaptic(30, 220)
            "off" -> {}
            else -> triggerHardwareHaptic(18, 130)
        }
    }

    internal fun triggerCruiseHaptic(isJump: Boolean, durationMs: Long = 20L, amplitude: Int = 100) {
        val mode = prefs().getString(LightspeedPreferences.KEY_CRUISE_HAPTICS, "full") ?: "full"
        when (mode) {
            "off" -> {}
            "jumps_only" -> {
                if (isJump) triggerHardwareHaptic(durationMs, amplitude)
            }
            "soft", "soft_low_freq" -> {
                triggerHardwareHaptic((durationMs * 0.7f).toLong().coerceAtLeast(10L), (amplitude * 0.6f).toInt().coerceAtLeast(50))
            }
            "heavy" -> {
                triggerHardwareHaptic((durationMs * 1.3f).toLong().coerceAtMost(60L), (amplitude * 1.3f).toInt().coerceIn(1, 255))
            }
            "full", "tactical" -> {
                triggerHardwareHaptic(durationMs, amplitude)
            }
            else -> triggerHardwareHaptic(durationMs, amplitude)
        }
    }

    internal fun triggerCruiseCategoryHaptic() {
        LightspeedHapticEngine.cruiseCategoryTick(context)
    }

    internal fun triggerCruiseLayerJumpHaptic() {
        LightspeedHapticEngine.cruiseLayerJump(context)
    }

    internal fun triggerCruiseAppFocusHaptic() {
        LightspeedHapticEngine.cruiseAppFocusTick(context)
    }

    internal var touchDownRawX = 0f
    internal var touchDownRawY = 0f
    internal var lastTouchRawX = 0f
    internal var lastTouchRawY = 0f
    internal var scrubStartX = 0f
    internal var categoryScrubbingEngaged = false
    internal var maxVerticalDisplacement = 0f
    internal var depthPercentage = 0f
    internal var virtualCursorX = 0f
    internal var virtualCursorY = 0f

    internal var touchDownX = 0f
    internal var touchDownY = 0f
    internal var lastTouchY = 0f

    internal val launchpadPillBounds = RectF() 
    internal val dataBridge = LightspeedDataBridge(context)

    // ── Cached SharedPreferences reference (set once in onAttachedToWindow) ──────
    // Eliminates per-frame file I/O from onDraw() and per-touch I/O from onTouchEvent().
    internal var cachedPrefs: android.content.SharedPreferences? = null
    internal fun prefs() = cachedPrefs ?: context.defaultPrefs().also { cachedPrefs = it }

    // ── Render-path pref cache (updated by prefChangeListener via updateRenderCache) ─
    internal var renderCacheRightFlankUnified = false
    internal var renderCacheRightUnifiedExpanded = false
    internal var renderCacheTopExpanded = false
    internal var renderCacheCenterExpanded = false
    internal var renderCacheBottomExpanded = false
    internal var renderCacheSidebarPreview = false
    internal var renderCacheCenterTransparency = 0
    internal var renderCacheLeftCenterTransparency = 0
    internal var renderCacheTopTransparency = 0
    internal var renderCacheBottomTransparency = 0
    internal var renderCacheGlowStyle = "progressive_frost"
    internal var renderCacheReticleStyle = "tactical"
    internal var renderCacheReticleOrientation = com.sbf.lightspeed.system.LightspeedPreferences.RETICLE_ORIENTATION_CLASSIC_180
    internal var renderCacheLinkEdges = false
    internal var renderCacheGlowEnabled = true
    internal var renderCacheLeftGlowEnabled = true
    internal var renderCacheUseM3Color = true
    internal var currentTouchY = -1f

    internal val projectionCamera3D = Camera()
    internal val transformMatrixPipeline = Matrix()
    internal val cylinderRadius = 500f

    internal val textPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 24f
        isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    internal val catTextPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 52f
        isAntiAlias = true; textAlign = Paint.Align.RIGHT; typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    internal val elementPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    internal val highlightPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    /** Extracted renderer for the COCKPIT_HANGAR layer and all shared draw helpers. */
    internal val deepSpaceRenderer = DeepSpaceRenderer(textPaint, elementPaint, highlightPaint)

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureAppWidgetHostListening()
        instance = this
        cachedPrefs = prefs()
        cachedPrefs!!.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateRenderCache()
        updateMetricsDimensions()
    }

    override fun onDetachedFromWindow() {
        uiHandler.removeCallbacks(hudLiveRefreshRunnable)
        closePinnedAppMenu()
        closeEditMode()
        stopGearAutoRepeat()
        categoryIconJob?.cancel()
        categoryIconJob = null
        applicationIconCache.clear()
        vitalsRowIconLruCache.evictAll()
        categoryAppsCache.clear()
        categoryGridCache.clear()
        categoryHeightCache.clear()
        lastLoadedCategoryId = null
        glowDelegate.cancel()
        cachedPrefs?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        cachedPrefs = null
        destroyAppWidgetHost()
        if (instance === this) {
            instance = null
        }
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post {
            updateMetricsDimensions()
            invalidate()
        }
    }

    /** Widget container that owns the current touch sequence (pinned state), or null. */
    private var widgetTouchTarget: ScrollableAppWidgetContainer? = null

    private var hudInterceptDownX = 0f
    private var hudInterceptDownY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isHudEditMode || isHudTuneModalOpen || isHudAddWidgetMenuOpen || isCategoryIconPickerOpen || isEditModeOpen || isAlertsHistoryModalOpen || isFirewallModalOpen || isVitalsModalOpen || isPrivacyScoreInfoBubbleOpen) {
            return true
        }
        val slop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                hudInterceptDownX = ev.x
                hudInterceptDownY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                // Widget with its own scrolling (Calendar list etc.) keeps the whole drag.
                if (widgetTouchTarget?.hasInnerVerticalScroll() == true) {
                    return false
                }
                val dx = kotlin.math.abs(ev.x - hudInterceptDownX)
                val dy = kotlin.math.abs(ev.y - hudInterceptDownY)
                if (dy > dx && dy > slop) {
                    val hudBounds = getTopHudStripBounds()
                    val contentBounds = getHudContentBounds()
                    val isFarSplit = isFarHalfSplitMode()
                    val maxHudScroll = if (isFarSplit) {
                        val hudBottomLimit = height.toFloat() * 0.96f
                        maxOf(0f, contentBounds.bottom - hudBottomLimit + 24f * resources.displayMetrics.density)
                    } else {
                        maxOf(0f, contentBounds.bottom - hudBounds.bottom)
                    }
                    if (maxHudScroll > 0f) {
                        touchDownX = hudInterceptDownX; touchDownY = hudInterceptDownY; lastTouchY = ev.y
                        return true
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val pinned = isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN
        val blocked = isHudEditMode || isHudTuneModalOpen || isHudAddWidgetMenuOpen ||
            isCategoryIconPickerOpen || isEditModeOpen || isCountdownModalOpen ||
            isAlertsHistoryModalOpen || isFirewallModalOpen || isVitalsModalOpen ||
            isPrivacyScoreInfoBubbleOpen || isScreenTimeInfoBubbleOpen || isPinnedAppMenuOpen() ||
            currentLayer == CruiseLayer.COCKPIT_HANGAR
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            widgetTouchTarget = if (pinned && !blocked && !isTouchOnPinnedEdgeSwitcher(ev.x, ev.y)) {
                findWidgetContainerAt(ev.x, ev.y)
            } else {
                null
            }
        }
        if (widgetTouchTarget != null) {
            // Entire sequence goes through normal child dispatch: the widget container owns it,
            // StickyPinTouchHandler only sees it if the child declines or we intercept a HUD scroll.
            val handled = super.dispatchTouchEvent(ev)
            if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                widgetTouchTarget = null
            }
            return handled
        }
        if (blocked) {
            return onTouchEvent(ev)
        }
        if (isHudEditMode || isHudTuneModalOpen || isHudAddWidgetMenuOpen || currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            return onTouchEvent(ev)
        }
        if (isCruising || pinned) {
            return onTouchEvent(ev)
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event) { super.onTouchEvent(event) }
    }

    internal fun resetHoldTimer() {
        uiHandler.removeCallbacks(holdTimerRunnable)
        val holdDuration = LightspeedPreferences.getGestureHoldDurationMs(context)
        uiHandler.postDelayed(holdTimerRunnable, holdDuration)
    }


    internal fun loadActiveCategoryGrid() {
        closePinnedAppMenu()
        if (activeCatIndex !in cachedCategories.indices) return
        val targetId = cachedCategories[activeCatIndex].id
        if (targetId == "launcher_settings_virtual_id") {
            launchLauncherSettings()
            return
        }
        if (targetId != lastLoadedCategoryId) {
            categoryIconJob?.cancel()
            categoryIconJob = null
            applicationIconCache.clear()
            vitalsRowIconLruCache.evictAll()
            lastLoadedCategoryId = targetId
        }
        if (categoryGridCache.containsKey(targetId)) {
            val cached = categoryGridCache[targetId]!!
            val density = resources.displayMetrics.density
            val isSquashed = cached.isNotEmpty() && cached.any { it.bounds.width() < 30f * density }
            if (!isSquashed) {
                placedAppsList = cached.toMutableList()
                totalGridContentHeight = categoryHeightCache[targetId] ?: 0f
                cachedApps = categoryAppsCache[targetId] ?: emptyList()
                preloadActiveCategoryIconsAsync()
                return
            }
        }
        val appsList = dataBridge.getAppsForCategory(targetId)
        categoryAppsCache[targetId] = appsList
        cachedApps = appsList
        buildPackedGridLayout()
        categoryGridCache[targetId] = ArrayList(placedAppsList)
        categoryHeightCache[targetId] = totalGridContentHeight
        preloadActiveCategoryIconsAsync()
    }

    internal fun preloadActiveCategoryIconsAsync() {
        categoryIconJob?.cancel()
        val appsToLoad = cachedApps.toList()
        val catId = if (activeCatIndex in cachedCategories.indices) cachedCategories[activeCatIndex].id else null
        categoryIconJob = overlayIoScope.launch {
            try {
                var loadedCount = 0
                for (target in appsToLoad) {
                    if (!isActive) break
                    if (!target.isWidget && !applicationIconCache.containsKey(target.packageName)) {
                        val drawable = dataBridge.getIcon(target.packageName, catId)
                        if (drawable != null) {
                            applicationIconCache[target.packageName] = drawable
                            loadedCount++
                            if (loadedCount % 4 == 0) {
                                postInvalidate()
                            }
                        }
                    }
                }
                if (loadedCount > 0) {
                    postInvalidate()
                }
            } catch (e: Exception) { logSwallowed("LightspeedCruiseOverlay", "preloadActiveCategoryIconsAsync:411", e) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (currentLayer == CruiseLayer.GRID || isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) {
            drawPinnedEdgeSwitcher(canvas)
            drawCruiseModalsOverlay(canvas)
        }
        if (isHudEditMode) {
            val snapshot = com.sbf.lightspeed.settings.DeckThemeManager.getSnapshot(context)
            val tintColor = categoryAccentOverride() ?: snapshot.tintArgb.toInt()
            val accent = ensureHighContrastColor(tintColor)
            val effectiveDensity = resources.displayMetrics.density * getHudScale()
            drawHudEditModeOverlay(canvas, activeCategoryHudItems, effectiveDensity, accent)
        }
    }

    companion object {
        @Volatile
        var instance: LightspeedCruiseOverlay? = null
            internal set

        fun loadAgslSource(context: Context): String {
            return try {
                context.assets.open("shaders/deflector_pill.agsl").bufferedReader().use { it.readText() }
            } catch (_: Exception) {
                agslFallbackSource
            }
        }

        private const val agslFallbackSource = """
            uniform shader inputTexture;
            uniform float2 viewSize;
            uniform float topBlurHeight;
            uniform float bottomBlurHeight;
            uniform float headerBlurActive;

            half4 main(float2 fragCoord) {
                if (headerBlurActive > 0.5) {
                    float blurAlpha = 0.0;
                    float safeTop = max(topBlurHeight, 1.0);
                    float safeBottom = max(bottomBlurHeight, 1.0);
                    float safeViewH = max(viewSize.y, 1.0);
                    if (fragCoord.y < safeTop) {
                        blurAlpha = 1.0 - (fragCoord.y / safeTop);
                    } else if (fragCoord.y > safeViewH - safeBottom) {
                        blurAlpha = (fragCoord.y - (safeViewH - safeBottom)) / safeBottom;
                    }
                    if (blurAlpha > 0.0) {
                        blurAlpha = clamp(blurAlpha, 0.0, 1.0);
                        float fadeProgress = pow(max(blurAlpha, 0.0001), 1.3);
                        float radius = blurAlpha * 24.0;
                        half4 color = half4(0.0, 0.0, 0.0, 0.0);
                        color += inputTexture.eval(fragCoord) * half(0.22);
                        color += inputTexture.eval(fragCoord + float2(0.0, radius * 0.55)) * half(0.13);
                        color += inputTexture.eval(fragCoord - float2(0.0, radius * 0.55)) * half(0.13);
                        color += inputTexture.eval(fragCoord + float2(radius * 0.55, 0.0)) * half(0.13);
                        color += inputTexture.eval(fragCoord - float2(radius * 0.55, 0.0)) * half(0.13);
                        color += inputTexture.eval(fragCoord + float2(radius * 0.38, radius * 0.38)) * half(0.065);
                        color += inputTexture.eval(fragCoord - float2(radius * 0.38, radius * 0.38)) * half(0.065);
                        color += inputTexture.eval(fragCoord + float2(-radius * 0.38, radius * 0.38)) * half(0.065);
                        color += inputTexture.eval(fragCoord + float2(radius * 0.38, -radius * 0.38)) * half(0.065);
                        half4 deepSpaceVoid = half4(0.022, 0.018, 0.035, 1.0);
                        return mix(color, deepSpaceVoid, half(fadeProgress * 0.88));
                    }
                }

                return inputTexture.eval(fragCoord);
            }
        """

        internal var agslSource: String = agslFallbackSource

        @Volatile
        private var shaderFailed = false

        @Volatile
        private var shaderInstance: android.graphics.RuntimeShader? = null

        @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
        fun getProgressiveShader(context: Context): android.graphics.RuntimeShader {
            if (shaderFailed) {
                throw IllegalStateException("AGSL RuntimeShader creation previously failed")
            }
            return shaderInstance ?: synchronized(this) {
                if (shaderFailed) {
                    throw IllegalStateException("AGSL RuntimeShader creation previously failed")
                }
                shaderInstance ?: run {
                    agslSource = loadAgslSource(context)
                    try {
                        android.graphics.RuntimeShader(agslSource).also { shaderInstance = it }
                    } catch (e: Exception) {
                        shaderFailed = true
                        android.util.Log.e("LightspeedBlur", "AGSL RuntimeShader creation failed", e)
                        throw e
                    }
                }
            }
        }

        val progressiveShader: android.graphics.RuntimeShader
            @androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.TIRAMISU)
            get() = shaderInstance ?: synchronized(this) {
                shaderInstance ?: android.graphics.RuntimeShader(agslSource).also { shaderInstance = it }
            }
    }
}
