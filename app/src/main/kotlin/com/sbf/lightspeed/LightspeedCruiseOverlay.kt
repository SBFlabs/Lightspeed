package com.sbf.lightspeed

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
) : View(context, attrs, defStyleAttr) {
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

    internal var cachedApps = listOf<LightspeedDataBridge.LaunchTarget>()
    internal var placedAppsList = mutableListOf<PlacedItem>()
    internal var totalGridContentHeight = 0f
    internal var viewportScrollOffset = 0f
    internal var activeItem: LightspeedDataBridge.LaunchTarget? = null

    internal val categoryAppsCache = mutableMapOf<String, List<LightspeedDataBridge.LaunchTarget>>()
    internal val categoryGridCache = mutableMapOf<String, List<PlacedItem>>()
    internal val categoryHeightCache = mutableMapOf<String, Float>()
    internal val applicationIconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
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
            com.sbf.lightspeed.system.CockpitGearRepository.persistActiveGearSetIndex(context, activeGearSetIndex, isOpenedFromLeftFlank)
            triggerHardwareHaptic(40, 200)
            invalidate()
            uiHandler.postDelayed(this, 350L)
        }
    }

    internal fun stopGearAutoRepeat() {
        uiHandler.removeCallbacks(gearAutoRepeatRunnable)
    }

    internal fun triggerGearCogHaptic() {
        val prefs = prefs()
        val strength = prefs.getString("pref_gear_haptic_strength", "tactical") ?: "tactical"
        when (strength) {
            "subtle" -> triggerHardwareHaptic(10, 50)
            "tactical" -> triggerHardwareHaptic(18, 130)
            "heavy" -> triggerHardwareHaptic(30, 220)
            "off" -> {}
            else -> triggerHardwareHaptic(18, 130)
        }
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
        cachedPrefs = prefs()
        cachedPrefs!!.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateRenderCache()
        updateMetricsDimensions()
    }

    override fun onDetachedFromWindow() {
        stopGearAutoRepeat()
        categoryIconJob?.cancel()
        categoryIconJob = null
        applicationIconCache.clear()
        categoryAppsCache.clear()
        categoryGridCache.clear()
        categoryHeightCache.clear()
        lastLoadedCategoryId = null
        glowDelegate.cancel()
        cachedPrefs?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        cachedPrefs = null
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        post {
            updateMetricsDimensions()
            invalidate()
        }
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
            lastLoadedCategoryId = targetId
        }
        if (categoryGridCache.containsKey(targetId)) {
            placedAppsList = categoryGridCache[targetId]!!.toMutableList()
            totalGridContentHeight = categoryHeightCache[targetId] ?: 0f
            cachedApps = categoryAppsCache[targetId] ?: emptyList()
            preloadActiveCategoryIconsAsync()
        } else {
            val appsList = dataBridge.getAppsForCategory(targetId)
            categoryAppsCache[targetId] = appsList
            cachedApps = appsList
            buildPackedGridLayout()
            categoryGridCache[targetId] = ArrayList(placedAppsList)
            categoryHeightCache[targetId] = totalGridContentHeight
            preloadActiveCategoryIconsAsync()
        }
    }

    internal fun preloadActiveCategoryIconsAsync() {
        categoryIconJob?.cancel()
        val appsToLoad = cachedApps.toList()
        categoryIconJob = overlayIoScope.launch {
            try {
                var loadedCount = 0
                for (target in appsToLoad) {
                    if (!isActive) break
                    if (!target.isWidget && !applicationIconCache.containsKey(target.packageName)) {
                        val drawable = dataBridge.getIcon(target.packageName)
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
            } catch (_: Exception) {}
        }
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }

    companion object {
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
                    if (fragCoord.y < topBlurHeight) {
                        blurAlpha = 1.0 - (fragCoord.y / topBlurHeight);
                    } else if (fragCoord.y > viewSize.y - bottomBlurHeight) {
                        blurAlpha = (fragCoord.y - (viewSize.y - bottomBlurHeight)) / bottomBlurHeight;
                    }
                    if (blurAlpha > 0.0) {
                        blurAlpha = clamp(blurAlpha, 0.0, 1.0);
                        float fadeProgress = pow(blurAlpha, 1.3);
                        float radius = blurAlpha * 24.0;
                        half4 color = half4(0.0);
                        color += inputTexture.eval(fragCoord) * 0.22;
                        color += inputTexture.eval(fragCoord + float2(0.0, radius * 0.55)) * 0.13;
                        color += inputTexture.eval(fragCoord - float2(0.0, radius * 0.55)) * 0.13;
                        color += inputTexture.eval(fragCoord + float2(radius * 0.55, 0.0)) * 0.13;
                        color += inputTexture.eval(fragCoord - float2(radius * 0.55, 0.0)) * 0.13;
                        color += inputTexture.eval(fragCoord + float2(radius * 0.38, radius * 0.38)) * 0.065;
                        color += inputTexture.eval(fragCoord - float2(radius * 0.38, radius * 0.38)) * 0.065;
                        color += inputTexture.eval(fragCoord + float2(-radius * 0.38, radius * 0.38)) * 0.065;
                        color += inputTexture.eval(fragCoord + float2(radius * 0.38, -radius * 0.38)) * 0.065;
                        half4 deepSpaceVoid = half4(0.022, 0.018, 0.035, 1.0);
                        return mix(color, deepSpaceVoid, fadeProgress * 0.88);
                    }
                }

                return inputTexture.eval(fragCoord);
            }
        """

        internal var agslSource: String = agslFallbackSource

        fun getProgressiveShader(context: Context): android.graphics.RuntimeShader {
            return shaderInstance ?: synchronized(this) {
                shaderInstance ?: run {
                    agslSource = loadAgslSource(context)
                    android.graphics.RuntimeShader(agslSource).also { shaderInstance = it }
                }
            }
        }

        @Volatile
        private var shaderInstance: android.graphics.RuntimeShader? = null

        val progressiveShader: android.graphics.RuntimeShader
            get() = shaderInstance ?: synchronized(this) {
                shaderInstance ?: android.graphics.RuntimeShader(agslSource).also { shaderInstance = it }
            }
    }
}
