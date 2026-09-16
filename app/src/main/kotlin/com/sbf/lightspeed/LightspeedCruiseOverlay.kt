package com.sbf.lightspeed

import android.accessibilityservice.AccessibilityService
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
import com.sbf.lightspeed.system.LightspeedTimeoutEngine
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
            return if (count > 0) count else 1
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

    internal val uiHandler = Handler(Looper.getMainLooper())
    internal var touchDownTime = 0L
    internal var gestureStartX = 0f
    internal var gestureStartY = 0f
    internal var macroTrackingActive = false
    internal var currentDetectedGesture = MacroGesture.NONE

    internal var glowFraction: Float = 0f
    internal var glowAnimator: ValueAnimator? = null

    fun triggerGlow(durationMs: Long = -1L) {
        post {
            val effectiveDuration = if (durationMs > 0L) durationMs else com.sbf.lightspeed.system.LightspeedPreferences.getDeflectorGlowDurationMs(context)
            glowAnimator?.cancel()
            glowAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = effectiveDuration
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    glowFraction = anim.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }
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

    internal val holdTimerRunnable = Runnable {
        if (macroTrackingActive) {
            val hostGesture = currentDetectedGesture
            val holdEquivalent = when(hostGesture) {
                MacroGesture.NONE -> MacroGesture.NONE
                MacroGesture.SWIPE_UP -> MacroGesture.SWIPE_UP_HOLD
                MacroGesture.SWIPE_DOWN -> MacroGesture.SWIPE_DOWN_HOLD
                MacroGesture.SWIPE_LEFT -> MacroGesture.SWIPE_LEFT_HOLD
                MacroGesture.SWIPE_UP_DOWN -> MacroGesture.SWIPE_UP_DOWN_HOLD
                MacroGesture.SWIPE_DOWN_UP -> MacroGesture.SWIPE_DOWN_UP_HOLD
                MacroGesture.SWIPE_UP_LEFT -> MacroGesture.SWIPE_UP_LEFT_HOLD
                MacroGesture.SWIPE_DOWN_LEFT -> MacroGesture.SWIPE_DOWN_LEFT_HOLD
                MacroGesture.SWIPE_LEFT_BACK -> MacroGesture.SWIPE_LEFT_BACK_HOLD
                MacroGesture.SWIPE_LEFT_UP -> MacroGesture.SWIPE_LEFT_UP_HOLD
                MacroGesture.SWIPE_LEFT_DOWN -> MacroGesture.SWIPE_LEFT_DOWN_HOLD
                else -> hostGesture
            }

            val gestureKey = if (hostGesture == MacroGesture.NONE) "TAP_HOLD" else holdEquivalent.name
            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
            val prefs = prefs()
            val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
            val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
            val isMirroringLeft = gestMode == "left"

            val actionKey = if (isMirroringLeft) {
                val isLeftUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)
                val leftZone = if (isLeftUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
                val leftGesture = when (gestureKey) {
                    "SWIPE_LEFT_HOLD" -> "SWIPE_RIGHT_HOLD"
                    "SWIPE_LEFT_UP_HOLD" -> "SWIPE_RIGHT_UP_HOLD"
                    "SWIPE_LEFT_DOWN_HOLD" -> "SWIPE_RIGHT_DOWN_HOLD"
                    "SWIPE_LEFT_BACK_HOLD" -> "SWIPE_RIGHT_BACK_HOLD"
                    "SWIPE_UP_LEFT_HOLD" -> "SWIPE_RIGHT_UP_HOLD"
                    "SWIPE_DOWN_LEFT_HOLD" -> "SWIPE_DOWN_RIGHT_HOLD"
                    else -> gestureKey
                }
                "pref_macro_action_${leftZone}_$leftGesture"
            } else {
                val dynamicZone = if (isFlankUnified) "UNIFIED" else zoneName
                "pref_macro_action_${dynamicZone}_$gestureKey"
            }
            val actionValue = prefs.getString(actionKey, "none") ?: "none"

            if (actionValue == "system:volume" || actionValue == "system:brightness" || actionValue == "system:screen_timeout" || actionValue == "scrub:volume" || actionValue == "scrub:brightness") {
                currentDetectedGesture = MacroGesture.SCRUBBING
                activeHoldScrubAction = actionValue
                activeHoldScrubActionKey = actionKey
                aggregateScrubAccumulator = 0f
                scrubStartX = lastTouchRawX
                isScrubEntranceHapticFired = true
                triggerHardwareHaptic(35, 180)
                when (actionValue) {
                    "system:volume", "scrub:volume" -> {
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val volResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                        val pct = kotlin.math.round(currentVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                        activeScrubVolumePct = pct
                        scrubHudTitle = "MEDIA VOLUME"
                        scrubHudValue = "$pct%"
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (pct * volResolution / 100).coerceIn(0, volResolution), volResolution)
                    }
                    "system:brightness", "scrub:brightness" -> {
                        val currentBrightness = try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        } catch (_: Exception) { 128 }
                        val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                        scrubHudTitle = "BRIGHTNESS"
                        scrubHudValue = "${(currentBrightness * 100 / 255)}%"
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (currentBrightness * brightResolution / 255), brightResolution)
                    }
                    "system:screen_timeout" -> {
                        scrubHudTitle = "SHIP GOES DARK IN"
                        val idx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                        scrubHudValue = LightspeedTimeoutEngine.TIMEOUT_STEPS[idx].second
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, idx, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                    }
                }
                invalidate()
            } else if (actionValue != "none") {
                if (holdEquivalent != hostGesture) {
                    currentDetectedGesture = holdEquivalent
                }
                executeMacroAction(currentActiveZone, holdEquivalent)
                triggerHardwareHaptic(35, 160)
                macroTrackingActive = false
            }
        }
    }

    internal val neutralToCategoryRunnable = Runnable {
        if (isCruising && currentLayer == CruiseLayer.NEUTRAL) {
            currentLayer = CruiseLayer.CATEGORY
            entranceStartTime = System.currentTimeMillis()
            invalidate()
        }
    }

    internal val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_") || key.startsWith("pref_deflector_") || key.startsWith("pref_gear_"))) {
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
    internal val applicationIconCache = mutableMapOf<String, Drawable>()
    internal var lastLoadedCategoryId: String? = null

    // --- GYROSCOPE CORE WORKSPACE STATES ---
    internal var activeGearRing = 0          // 0 = Outer Ring, 1 = Inner Ring, 2 = Center Control Hub
    internal var activeGearSetIndex = 0      // Active profile face (Set A, B, C, D)
    internal var gearRingRotations = FloatArray(3) { 0f }
    internal var rawHorizontalXAccumulator = 0f
    internal var isCubeRotationFired = false
    internal var lastTargetedIndex = intArrayOf(-1, -1)

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
        glowAnimator?.cancel()
        cachedPrefs?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        cachedPrefs = null
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event) { super.onTouchEvent(event) }
    }

    internal fun resetHoldTimer() {
        uiHandler.removeCallbacks(holdTimerRunnable)
        uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }


    internal fun loadActiveCategoryGrid() {
        if (activeCatIndex !in cachedCategories.indices) return
        val targetId = cachedCategories[activeCatIndex].id
        if (targetId == "launcher_settings_virtual_id") {
            launchLauncherSettings()
            return
        }
        if (categoryGridCache.containsKey(targetId)) {
            placedAppsList = categoryGridCache[targetId]!!.toMutableList()
            totalGridContentHeight = categoryHeightCache[targetId] ?: 0f
            cachedApps = categoryAppsCache[targetId] ?: emptyList()
            preloadActiveCategoryIcons()
        } else {
            val appsList = dataBridge.getAppsForCategory(targetId)
            categoryAppsCache[targetId] = appsList
            cachedApps = appsList
            buildPackedGridLayout()
            categoryGridCache[targetId] = ArrayList(placedAppsList)
            categoryHeightCache[targetId] = totalGridContentHeight
            preloadActiveCategoryIcons()
        }
    }

    internal fun preloadActiveCategoryIcons() {
        try {
            applicationIconCache.clear()
            for (target in cachedApps) {
                if (!target.isWidget) {
                    if (applicationIconCache.size >= 100) break // Cap: prevent unbounded RAM from large categories
                    val drawable = dataBridge.getIcon(target.packageName)
                    if (drawable != null) {
                        applicationIconCache[target.packageName] = drawable
                    }
                }
            }
        } catch (_: Exception) {}
    }

    override fun onDraw(canvas: Canvas) {
        handleDraw(canvas) { super.onDraw(canvas) }
    }

    companion object {
        internal val agslSource = """
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
        """.trimIndent()

        
        val progressiveShader by lazy { android.graphics.RuntimeShader(agslSource) }
    }
}
