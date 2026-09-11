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

    internal fun persistActiveGearSetIndex() {
        com.sbf.lightspeed.system.CockpitGearRepository.persistActiveGearSetIndex(context, activeGearSetIndex, isOpenedFromLeftFlank)
    }

    internal var activeHoldScrubAction: String? = null
    internal var activeHoldScrubActionKey: String? = null
    internal var activeScrubVolumePct: Int = -1
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
    internal inline fun prefs() = cachedPrefs ?: context.defaultPrefs().also { cachedPrefs = it }

    // ── Render-path pref cache (updated by prefChangeListener via updateRenderCache) ─
    internal var renderCacheRightFlankUnified = false
    internal var renderCacheRightUnifiedExpanded = false
    internal var renderCacheTopExpanded = false
    internal var renderCacheCenterExpanded = false
    internal var renderCacheBottomExpanded = false
    internal var renderCacheSidebarPreview = false
    internal var renderCacheCenterTransparency = 0
    internal var renderCacheTopTransparency = 0
    internal var renderCacheBottomTransparency = 0
    internal var renderCacheGlowStyle = "progressive_frost"
    internal var renderCacheReticleStyle = "tactical"
    internal var renderCacheLinkEdges = false
    internal var renderCacheGlowEnabled = true
    internal var renderCacheUseM3Color = true

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

    internal fun updateRenderCache() {
        val p = prefs()
        renderCacheRightFlankUnified    = p.getBoolean("pref_sidebar_right_link_flank_actions", false)
        renderCacheRightUnifiedExpanded = p.getBoolean("pref_section_right_unified_expanded", false)
        renderCacheTopExpanded          = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_top_expanded", false)
        renderCacheCenterExpanded       = p.getBoolean("pref_section_center_expanded", false)
        renderCacheBottomExpanded       = if (renderCacheRightFlankUnified) renderCacheRightUnifiedExpanded else p.getBoolean("pref_section_bottom_expanded", false)
        renderCacheSidebarPreview       = p.getBoolean("pref_sidebar_preview", false)
        renderCacheLinkEdges            = p.getBoolean("pref_sidebar_link_edges", false)
        renderCacheCenterTransparency   = p.getInt("pref_sidebar_center_transparency", 0)
        renderCacheTopTransparency      = p.getInt("pref_sidebar_top_transparency", 0)
        renderCacheBottomTransparency   = if (renderCacheLinkEdges) renderCacheTopTransparency else p.getInt("pref_sidebar_bottom_transparency", 0)
        renderCacheGlowStyle            = p.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"
        renderCacheReticleStyle         = p.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
        renderCacheGlowEnabled          = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true)
        renderCacheUseM3Color           = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_USE_M3_COLOR, true)
    }

    fun updateMetricsDimensions() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = layoutParams as? WindowManager.LayoutParams ?: return

        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()
        val density = displayMetrics.density

        val prefs = prefs()
        
        centerHeightPx = prefs.getInt("pref_sidebar_center_height", 280).toFloat() * density
        centerYOffsetPx = prefs.getInt("pref_sidebar_center_y_offset", 0).toFloat() * density
        centerVisualWidthPx = prefs.getInt("pref_sidebar_center_visual_width", 4).toFloat() * density
        centerTouchWidthPx = prefs.getInt("pref_sidebar_center_touch_width", 24).toFloat() * density

        linkEdges = prefs.getBoolean("pref_sidebar_link_edges", false)

        topHeightPx = prefs.getInt("pref_sidebar_top_height", 300).toFloat() * density
        topVisualWidthPx = prefs.getInt("pref_sidebar_top_visual_width", 2).toFloat() * density
        topTouchWidthPx = prefs.getInt("pref_sidebar_top_touch_width", 32).toFloat() * density

        bottomHeightPx = if (linkEdges) topHeightPx else prefs.getInt("pref_sidebar_bottom_height", 300).toFloat() * density
        bottomVisualWidthPx = if (linkEdges) topVisualWidthPx else prefs.getInt("pref_sidebar_bottom_visual_width", 2).toFloat() * density
        bottomTouchWidthPx = if (linkEdges) topTouchWidthPx else prefs.getInt("pref_sidebar_bottom_touch_width", 32).toFloat() * density

        if (currentLayer == CruiseLayer.HIDDEN) {
            val centerY = (screenH / 2f) + centerYOffsetPx
            val centerTop = centerY - (centerHeightPx / 2f)
            val centerBottom = centerY + (centerHeightPx / 2f)

            val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
            val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(screenH)

            val maxTouchW = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx)
            val winHeight = (bottomLimit - topLimit).toInt().coerceAtLeast(100)
            val winWidth = maxTouchW.toInt().coerceAtLeast((10f * density).toInt())
            val winY = topLimit.toInt()
            val w = winWidth.toFloat()

            topTouchBounds.set(w - topTouchWidthPx, 0f, w, topHeightPx)
            centerTouchBounds.set(w - centerTouchWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomTouchBounds.set(w - bottomTouchWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            topVisualBounds.set(w - topVisualWidthPx, 0f, w, topHeightPx)
            centerVisualBounds.set(w - centerVisualWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomVisualBounds.set(w - bottomVisualWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != winY || lp.gravity != (Gravity.TOP or Gravity.END)) {
                lp.gravity = Gravity.TOP or Gravity.END
                lp.x = 0; lp.y = winY
                lp.width = winWidth; lp.height = winHeight
                wm.updateViewLayout(this, lp)
            }
        } else {
            val winWidth = WindowManager.LayoutParams.MATCH_PARENT
            val winHeight = WindowManager.LayoutParams.MATCH_PARENT
            val w = screenW; val h = screenH

            val centerY = (h / 2f) + centerYOffsetPx
            val centerTop = centerY - (centerHeightPx / 2f)
            val centerBottom = centerY + (centerHeightPx / 2f)

            val topLimit = (centerTop - topHeightPx).coerceAtLeast(0f)
            val bottomLimit = (centerBottom + bottomHeightPx).coerceAtMost(h)

            topTouchBounds.set(w - topTouchWidthPx, topLimit, w, centerTop)
            centerTouchBounds.set(w - centerTouchWidthPx, centerTop, w, centerBottom)
            bottomTouchBounds.set(w - bottomTouchWidthPx, centerBottom, w, bottomLimit)

            topVisualBounds.set(w - topVisualWidthPx, topLimit, w, centerTop)
            centerVisualBounds.set(w - centerVisualWidthPx, centerTop, w, centerBottom)
            bottomVisualBounds.set(w - bottomVisualWidthPx, centerBottom, w, bottomLimit)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != 0) {
                lp.gravity = Gravity.FILL
                lp.x = 0; lp.y = 0
                lp.width = winWidth; lp.height = winHeight
                wm.updateViewLayout(this, lp)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                progressiveShader.apply {
                    setFloatUniform("viewSize", screenW, screenH)
                    setFloatUniform("topBlurHeight", 130.0f * density)
                    setFloatUniform("bottomBlurHeight", 160.0f * density)
                }
                cachedRenderEffect = RenderEffect.createRuntimeShaderEffect(progressiveShader, "inputTexture")
            } catch (e: Exception) {
                Log.e("LightspeedBlur", "AGSL fault", e)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    cachedRenderEffect = RenderEffect.createBlurEffect(25f, 25f, android.graphics.Shader.TileMode.CLAMP)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            cachedRenderEffect = RenderEffect.createBlurEffect(25f, 25f, android.graphics.Shader.TileMode.CLAMP)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val systemExclusions = mutableListOf<android.graphics.Rect>()
            systemExclusions.add(android.graphics.Rect(topTouchBounds.left.toInt(), topTouchBounds.top.toInt(), topTouchBounds.right.toInt(), topTouchBounds.bottom.toInt()))
            systemExclusions.add(android.graphics.Rect(centerTouchBounds.left.toInt(), centerTouchBounds.top.toInt(), centerTouchBounds.right.toInt(), centerTouchBounds.bottom.toInt()))
            systemExclusions.add(android.graphics.Rect(bottomTouchBounds.left.toInt(), bottomTouchBounds.top.toInt(), bottomTouchBounds.right.toInt(), bottomTouchBounds.bottom.toInt()))
            this.systemGestureExclusionRects = systemExclusions
        }

        refreshActiveRenderEffect()
        invalidate()
    }

    internal fun refreshActiveRenderEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val shouldApplyBlur = (currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)
            setRenderEffect(if (shouldApplyBlur) cachedRenderEffect else null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return handleTouchEvent(event) { super.onTouchEvent(event) }
    }

    internal fun resetHoldTimer() {
        uiHandler.removeCallbacks(holdTimerRunnable)
        uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    internal fun dispatchScrubHud(title: String, value: String, stepIndex: Int, totalSteps: Int) {
        val prefs = prefs()
        val isBrightness = activeHoldScrubAction == "scrub:brightness" || activeHoldScrubAction == "system:brightness" || title == "BRIGHTNESS"
        val isVolume = activeHoldScrubAction == "scrub:volume" || activeHoldScrubAction == "system:volume" || title == "MEDIA VOLUME"
        val showHud = when {
            isBrightness -> prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, true)
            isVolume -> prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, true)
            else -> true
        }
        if (showHud) {
            val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
            val dynamicZone = if (isFlankUnified) "UNIFIED" else (if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM")
            val fallbackKey = activeHoldScrubActionKey ?: "pref_macro_action_${dynamicZone}_SCRUBBING"
            val hudStyle = com.sbf.lightspeed.system.LightspeedPreferences.resolveHudStyle(prefs, fallbackKey, activeHoldScrubAction)

            LightspeedStatusBarOverlay.showActionHud(
                title = title,
                value = value,
                stepIndex = stepIndex,
                totalSteps = totalSteps,
                durationMs = 0L,
                style = hudStyle
            )
        }
    }

    internal fun executeLinearScrubTrack(zone: TouchZone, pixelDelta: Float) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = prefs()
        val dynamicZone = if (prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
        val assignedScrub = activeHoldScrubAction ?: (prefs.getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none") ?: "none")

        if (assignedScrub == "none") return

        aggregateScrubAccumulator += pixelDelta
        val sensitivityThreshold = 28f

        if (abs(aggregateScrubAccumulator) >= sensitivityThreshold) {
            val steps = (aggregateScrubAccumulator / sensitivityThreshold).toInt()
            aggregateScrubAccumulator %= sensitivityThreshold
            if (steps != 0) {
                triggerHardwareHaptic(18, 110)
            }

            if (assignedScrub == "scrub:volume" || assignedScrub == "system:volume") {
                val volResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, 100).coerceIn(5, 100)
                val volStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, (100f / volResolution).roundToInt().coerceIn(1, 20))
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (activeScrubVolumePct < 0) {
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    activeScrubVolumePct = kotlin.math.round(curVol * 100f / maxVol.coerceAtLeast(1)).toInt().coerceIn(0, 100)
                }
                val targetPct = (activeScrubVolumePct + (steps * volStep)).coerceIn(0, 100)
                activeScrubVolumePct = targetPct
                val targetStreamVol = kotlin.math.round(targetPct * maxVol / 100f).toInt().coerceIn(0, maxVol)
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val showNativeUi = prefs.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, false)
                val flags = if (showNativeUi) AudioManager.FLAG_SHOW_UI else 0
                if (targetStreamVol != currentVol) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, flags)
                }
                scrubHudTitle = "MEDIA VOLUME"
                scrubHudValue = "$targetPct%"
                dispatchScrubHud(scrubHudTitle, scrubHudValue, (targetPct * volResolution / 100).coerceIn(0, volResolution), volResolution)
                invalidate()
            } else if (assignedScrub == "scrub:brightness" || assignedScrub == "system:brightness") {
                if (Settings.System.canWrite(context)) {
                    val brightResolution = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, 32).coerceIn(10, 254)
                    val brightStep = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, (255f / brightResolution).roundToInt().coerceIn(1, 32))
                    val currentBrightness = try {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { 128 }
                    val targetBrightness = (currentBrightness + (steps * brightStep)).coerceIn(0, 255)
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                        scrubHudTitle = "BRIGHTNESS"
                        scrubHudValue = "${(targetBrightness * 100 / 255)}%"
                        dispatchScrubHud(scrubHudTitle, scrubHudValue, (targetBrightness * brightResolution / 255), brightResolution)
                        invalidate()
                    } catch (e: Exception) {
                        Log.e("GestureEngine", "System write failure", e)
                    }
                } else {
                    LightspeedTimeoutEngine.requestWriteSettingsPermission(context)
                }
            } else if (assignedScrub == "system:screen_timeout") {
                val curIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                val targetIndex = (curIdx + steps).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                val stepResult = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                val label = stepResult.second
                if (scrubHudValue != label) {
                    scrubHudTitle = "SHIP GOES DARK IN"
                    scrubHudValue = label
                    dispatchScrubHud(scrubHudTitle, scrubHudValue, targetIndex, LightspeedTimeoutEngine.TIMEOUT_STEPS.size)
                    triggerHardwareHaptic(22, 140)
                    invalidate()
                }
            }
        }
    }

    internal fun executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = prefs()
        val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
        val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
        val isMirroringLeft = gestMode == "left"

        val actionValue = if (isMirroringLeft) {
            val isLeftUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)
            val leftZone = if (isLeftUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
            val leftGesture = when (gesture.name) {
                "SWIPE_LEFT" -> "SWIPE_RIGHT"
                "SWIPE_LEFT_UP" -> "SWIPE_RIGHT_UP"
                "SWIPE_LEFT_DOWN" -> "SWIPE_RIGHT_DOWN"
                "SWIPE_LEFT_BACK" -> "SWIPE_RIGHT_BACK"
                "SWIPE_UP_LEFT" -> "SWIPE_UP_RIGHT"
                "SWIPE_DOWN_LEFT" -> "SWIPE_DOWN_RIGHT"
                else -> gesture.name
            }
            prefs.getString("pref_macro_action_${leftZone}_$leftGesture", "none") ?: "none"
        } else {
            val dynamicZone = if (isFlankUnified) "UNIFIED" else zoneName
            val actionKey = "pref_macro_action_${dynamicZone}_${gesture.name}"
            prefs.getString(actionKey, "none") ?: "none"
        }

        Log.d("GestureEngine", "Target Vector: [$zoneName] -> Action Value: $actionValue")
        triggerHardwareHaptic(35, 160)

        // Disengage overlay window so incoming system window (Recents/Home/App) gets immediate focus
        dismissOverlay()

        ActionDispatcher.execute(service ?: context, actionValue)
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

    internal fun evaluateSpatialMetrics(rawX: Float, rawY: Float, localX: Float, localY: Float) {
        val wF = width.toFloat(); val hF = height.toFloat()
        if (wF <= 0f || hF <= 0f) return
        val density = resources.displayMetrics.density
        depthPercentage = if (isOpenedFromLeftFlank) (localX / wF).coerceIn(0f, 1f) else ((wF - localX) / wF).coerceIn(0f, 1f)
        val verticalComfortScope = hF * 0.40f
        val verticalStartAnchor = hF * 0.30f
        val normalizedVerticalProgress = ((localY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

        if (currentLayer == CruiseLayer.CATEGORY) {
            activeItem = null; viewportScrollOffset = 0f
            val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
            if (categoryScrubbingEngaged && horizontalPull > (28f * density)) {
                currentLayer = CruiseLayer.GRID; loadActiveCategoryGrid(); invalidate(); return
            }
            if (cachedCategories.isNotEmpty()) {
                val startYArea = hF * 0.22f; val endYArea = hF * 0.78f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                val isAtBottom = (activeCatIndex == cachedCategories.size - 1)
                val isDraggingDown = (rawY > lastTouchRawY)

                if (isAtBottom && isDraggingDown) {
                    val stepDistance = abs(rawY - lastTouchRawY)
                    if (stepDistance > 2f) {
                        overScrollBoundaryAccumulator += stepDistance * 0.45f
                    }
                    if (overScrollBoundaryAccumulator > 120f && !settingsCategoryAppended) {
                        settingsCategoryAppended = true
                        triggerHardwareHaptic(35, 160)
                        val mutableCats = cachedCategories.toMutableList()
                        val hasSettings = mutableCats.any { it.id == "launcher_settings_virtual_id" }
                        if (!hasSettings) {
                            mutableCats.add(LightspeedDataBridge.CategoryNode("launcher_settings_virtual_id", "⚙ Settings"))
                            cachedCategories = mutableCats
                        }
                    }
                } else if (normalizedVerticalProgress < 1.0f) {
                    overScrollBoundaryAccumulator = (overScrollBoundaryAccumulator - 12f).coerceAtLeast(0f)
                }

                val resistanceFactor = if (overScrollBoundaryAccumulator > 0f) (1.0f / (1.0f + (overScrollBoundaryAccumulator * 0.008f))) else 1.0f
                val adjustedProgress = if (isAtBottom && isDraggingDown) {
                    normalizedVerticalProgress + (overScrollBoundaryAccumulator * 0.0005f * resistanceFactor)
                } else {
                    normalizedVerticalProgress
                }

                categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (adjustedProgress * ((cachedCategories.size - 1) * catLineH)))

                var closestIndex = activeCatIndex; var minDistance = Float.MAX_VALUE
                for (index in cachedCategories.indices) {
                    val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                    if (d < minDistance) { minDistance = d; closestIndex = index }
                }
                if (closestIndex != activeCatIndex) activeCatIndex = closestIndex
            }
        } else {
            val horizontalPull = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
            // In GRID mode, allow navigating all the way to the rightmost column without prematurely kicking back to CATEGORY.
            // Only exit back to CATEGORY if the thumb slides all the way back to the physical screen edge (< 6dp pull).
            val nextLayerState = when {
                horizontalPull <= (6f * density) -> CruiseLayer.CATEGORY
                (if (isOpenedFromLeftFlank) localX else (wF - localX)) <= (wF * 0.42f) -> CruiseLayer.GRID
                else -> CruiseLayer.STICKY_PIN
            }
            if (currentLayer == CruiseLayer.GRID && nextLayerState == CruiseLayer.CATEGORY) {
                currentLayer = CruiseLayer.CATEGORY
                touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
                placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
                if (cachedCategories.isNotEmpty()) {
                    val startYArea = hF * 0.22f; val endYArea = hF * 0.78f
                    val catLineH = (endYArea - startYArea) / cachedCategories.size

                    val maxScroll = totalGridContentHeight - (hF * 0.79f)
                    val ratio = if (maxScroll > 0f) (viewportScrollOffset / maxScroll).coerceIn(0f, 1f) else normalizedVerticalProgress
                    categoryVisualOffset = (hF / 2f) - (startYArea + (catLineH / 2f) + (ratio * ((cachedCategories.size - 1) * catLineH)))
                    var closestIndex = 0; var minDistance = Float.MAX_VALUE
                    for (index in cachedCategories.indices) {
                        val d = abs((startYArea + (index * catLineH) + (catLineH / 2f) + categoryVisualOffset) - (hF / 2f))
                        if (d < minDistance) { minDistance = d; closestIndex = index }
                    }
                    activeCatIndex = closestIndex
                }
                invalidate(); return
            }
            currentLayer = nextLayerState
            if (currentLayer == CruiseLayer.CATEGORY) {
                touchDownRawX = rawX; touchDownRawY = rawY; lastTouchRawY = rawY
                placedAppsList.clear(); cachedApps = emptyList(); lastLoadedCategoryId = null
                invalidate(); return
            }

            // Map cursor progress so that the outermost column is reached cleanly with zero edge collision
            val pullDistance = if (isOpenedFromLeftFlank) localX.coerceAtLeast(0f) else (wF - localX).coerceAtLeast(0f)
            val cursorProgress = ((pullDistance - (10f * density)) / (wF * 0.28f)).coerceIn(0f, 1f)
            virtualCursorX = if (isOpenedFromLeftFlank) ((wF * 0.06f) + (cursorProgress * (wF * 0.88f))) else ((wF * 0.94f) - (cursorProgress * (wF * 0.88f)))

            val gTop = hF * 0.16f; val gBot = hF * 0.94f; val gScope = gBot - gTop
            virtualCursorY = gTop + (normalizedVerticalProgress * gScope)
            if (totalGridContentHeight <= gScope) { viewportScrollOffset = 0f } 
            else { viewportScrollOffset = normalizedVerticalProgress * (totalGridContentHeight - gScope) }

            var closestMatchItem: LightspeedDataBridge.LaunchTarget? = null; var minD = Float.MAX_VALUE
            for (item in placedAppsList) {
                val d = hypot((virtualCursorX - item.bounds.centerX()).toDouble(), ((virtualCursorY + viewportScrollOffset) - item.bounds.centerY()).toDouble()).toFloat()
                if (d < minD) { minD = d; closestMatchItem = item.app }
            }
            activeItem = if (closestMatchItem != null && placedAppsList.isNotEmpty() && currentLayer != CruiseLayer.STICKY_PIN) closestMatchItem else null
        }
        invalidate()
    }

    internal fun buildPackedGridLayout() {
        val wF = width.toFloat(); val hF = height.toFloat()
        if (wF <= 0f) return
        val columns = querySystemColumnPreference(resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
        val cellW = (wF * 0.92f) / columns; val cellH = cellW * 1.1f
        placedAppsList.clear()
        val occupied = HashSet<Pair<Int, Int>>()
        for (app in cachedApps.distinctBy { "${it.packageName}_${it.activityName}_${it.instanceId}" }) {
            var sW = 1; var sH = 1
            if (app.isWidget) { sW = if (app.spanX > 0) app.spanX.coerceIn(1, columns) else columns / 2; sH = if (app.spanY > 0) app.spanY.coerceIn(1, 12) else 2 }
            var row = 0; var found = false
            while (!found) {
                for (col in 0..columns - sW) {
                    var clear = true
                    for (dr in 0 until sH) { for (dc in 0 until sW) { if (occupied.contains(Pair(row + dr, col + dc))) { clear = false; break } }; if (!clear) break }
                    if (clear) {
                        placedAppsList.add(PlacedItem(app, RectF((wF * 0.04f) + (col * cellW), (hF * 0.16f) + (row * cellH), (wF * 0.04f) + ((col + sW) * cellW), (hF * 0.16f) + ((row + sH) * cellH))))
                        for (dr in 0 until sH) { for (dc in 0 until sW) { occupied.add(Pair(row + dr, col + dc)) } }
                        found = true; break
                    }
                }
                if (!found) row++
            }
        }
        totalGridContentHeight = if (occupied.isEmpty()) 0f else ((occupied.maxOf { it.first }) + 1) * cellH
    }

    internal fun querySystemColumnPreference(isLandscape: Boolean): Int {
        val prefs = prefs()
        return try { val v = prefs.all[if (isLandscape) "pref_numcolsland" else "pref_numcolspor"]; if (v is Int) v else v?.toString()?.toInt() ?: 4 } catch (e: Exception) { 4 }
    }

    internal fun executeLaunch(target: LightspeedDataBridge.LaunchTarget) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launchIntent) } catch (e: Exception) {}
        }
    }

    internal fun resolveCleanAppLabel(itemToken: String): String {
        return com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, itemToken)
    }

    internal fun launchLauncherSettings() {
        dismissOverlay()
        try {
            val settingsIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(settingsIntent)
        } catch (_: Exception) {
            val alternativeIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            alternativeIntent?.let { context.startActivity(it) }
        }
    }

    internal fun dismissOverlay() {
        longPressHangarBayRunnable?.let { removeCallbacks(it) }
        longPressCogRunnable?.let { removeCallbacks(it) }
        longPressHangarBayRunnable = null
        longPressCogRunnable = null
        hasLongPressFired = false

        snapAnimator?.cancel()
        glowAnimator?.cancel()
        glowFraction = 0f
        isCurrentlyTouched = false
        currentActiveZone = TouchZone.NONE
        macroTrackingActive = false
        isDraggingHangarBays = false
        isSpinningHangarRing = false
        isTouchingFocusedCog = false
        isStickyPinned = false; isCruising = false; currentLayer = CruiseLayer.HIDDEN
        activeItem = null; activeCatIndex = -1; viewportScrollOffset = 0f; categoryVisualOffset = 0f
        placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
        val cPrefs = prefs()
        if (cPrefs.getString("cockpit_launch_behavior", "default") != "last") {
            activeGearSetIndex = 0
        }
        service?.updateWindowLayout(false)
        updateMetricsDimensions()
        invalidate()
    }

    fun getGearSetsOrder(isLeft: Boolean): MutableList<String> {
        return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetsOrder(context, isLeft)
    }

    fun saveGearSetsOrder(isLeft: Boolean, list: List<String>) {
        com.sbf.lightspeed.system.CockpitGearRepository.saveGearSetsOrder(context, isLeft, list)
    }

    fun getFlankLaunchBehavior(isLeft: Boolean): String {
        return com.sbf.lightspeed.system.CockpitGearRepository.getFlankLaunchBehavior(context, isLeft)
    }

    fun setFlankLaunchBehavior(isLeft: Boolean, behavior: String) {
        com.sbf.lightspeed.system.CockpitGearRepository.setFlankLaunchBehavior(context, isLeft, behavior)
    }

    fun startCruiseFromFlank(isLeft: Boolean, startRawX: Float, startRawY: Float) {
        isOpenedFromLeftFlank = isLeft
        touchDownRawX = startRawX
        touchDownRawY = startRawY
        lastTouchRawX = startRawX
        lastTouchRawY = startRawY
        touchDownTime = System.currentTimeMillis()
        gestureStartX = startRawX
        gestureStartY = startRawY

        trackingStateLocked = false
        initialLeftSweepDistance = 0f
        lowestXReached = startRawX
        highestYReached = startRawY
        lowestYReached = startRawY
        aggregateScrubAccumulator = 0f
        isScrubEntranceHapticFired = false
        currentDetectedGesture = MacroGesture.NONE
        overScrollBoundaryAccumulator = 0f
        settingsCategoryAppended = false

        currentActiveZone = TouchZone.CENTER_CRUISE
        isCruising = true
        isCurrentlyTouched = true
        categoryScrubbingEngaged = false
        maxVerticalDisplacement = 0f
        categoryVisualOffset = 0f
        entranceStartTime = System.currentTimeMillis()
        lastLoadedCategoryId = null
        snapAnimator?.cancel()
        categoryAppsCache.clear(); categoryGridCache.clear()

        val cPrefs = prefs()
        val setsList = getGearSetsOrder(isLeft)
        val launchBehavior = getFlankLaunchBehavior(isLeft)
        val lastActiveKey = if (isLeft) "last_active_set_index_left" else "last_active_set_index_right"
        if (launchBehavior == "last") {
            val lastIndex = cPrefs.getInt(lastActiveKey, cPrefs.getInt("last_active_set_index", 0))
            activeGearSetIndex = if (lastIndex in setsList.indices) lastIndex else 0
        } else {
            activeGearSetIndex = 0
        }

        currentLayer = CruiseLayer.NEUTRAL
        updateMetricsDimensions()
        placedAppsList.clear(); cachedApps = emptyList()
        service?.updateWindowLayout(true)

        cachedCategories = dataBridge.getLiveCategories()
        if (cachedCategories.isNotEmpty()) {
            val hF = if (height > 0) height.toFloat() else resources.displayMetrics.heightPixels.toFloat()
            val startYArea = hF * 0.25f
            val endYArea = hF * 0.75f
            val usableHeight = endYArea - startYArea
            val normY = ((startRawY - startYArea) / usableHeight).coerceIn(0f, 1f)
            initialCatIndex = kotlin.math.floor(normY * cachedCategories.size).toInt().coerceIn(0, cachedCategories.size - 1)
            activeCatIndex = initialCatIndex
            val catLineH = usableHeight / cachedCategories.size
            categoryVisualOffset = (hF / 2f) - (startYArea + (activeCatIndex * catLineH) + (catLineH / 2f))
        }
        uiHandler.removeCallbacks(neutralToCategoryRunnable)
        uiHandler.postDelayed(neutralToCategoryRunnable, 140L)
        invalidate()
    }

    fun handleFlankTouchEvent(isLeft: Boolean, event: MotionEvent): Boolean {
        val rawX = event.rawX
        val rawY = event.rawY
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startCruiseFromFlank(isLeft, rawX, rawY)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density = resources.displayMetrics.density
                if (isCruising) {
                    val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
                    val deltaY = kotlin.math.abs(rawY - touchDownRawY)
                    if (deltaY > maxVerticalDisplacement) {
                        maxVerticalDisplacement = deltaY
                    }

                    // 1. Direct Lateral Inward Swipe -> Open Gears
                    if ((currentLayer == CruiseLayer.NEUTRAL || currentLayer == CruiseLayer.CATEGORY) &&
                        !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX > (deltaY * 1.1f)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        val cPrefs = prefs()
                        val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                        val launchBehavior = getFlankLaunchBehavior(isOpenedFromLeftFlank)
                        val lastActiveKey = if (isOpenedFromLeftFlank) "last_active_set_index_left" else "last_active_set_index_right"
                        if (launchBehavior == "last") {
                            val lastIndex = cPrefs.getInt(lastActiveKey, cPrefs.getInt("last_active_set_index", 0))
                            activeGearSetIndex = if (lastIndex in setsList.indices) lastIndex else 0
                        } else {
                            activeGearSetIndex = 0
                        }
                        currentLayer = CruiseLayer.FAVORITES_GEARS
                        triggerHardwareHaptic(30, 180)
                    }

                    // 2. Deliberate vertical movement -> Engage Category 3D Cylinder
                    if (currentLayer == CruiseLayer.NEUTRAL && maxVerticalDisplacement > (14f * density)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        categoryScrubbingEngaged = true
                        currentLayer = CruiseLayer.CATEGORY
                        entranceStartTime = System.currentTimeMillis()
                    }

                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        processGyroscopeTouchPhysics(rawX, rawY)
                    } else if (currentLayer == CruiseLayer.CATEGORY || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) {
                        evaluateSpatialMetrics(rawX, rawY, rawX, rawY)
                    }
                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isCurrentlyTouched = false
                currentActiveZone = TouchZone.NONE
                isScrubEntranceHapticFired = false
                uiHandler.removeCallbacks(holdTimerRunnable)
                if (isCruising) {
                    if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
                        val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
                        if (packages.isNotEmpty() && activeGearRing in 0..1) {
                            val itemCount = packages.size
                            val currentRotation = gearRingRotations[activeGearRing]
                            var targetedPackage: String? = null
                            var minAngleDiff = Float.MAX_VALUE
                            
                            for (i in packages.indices) {
                                val itemAngle = (currentRotation + i * (360f / itemCount)) % 360f
                                val normalizedAngle = if (itemAngle < 0) itemAngle + 360f else itemAngle
                                val diff = abs(normalizedAngle - 180f)
                                if (diff < minAngleDiff) {
                                    minAngleDiff = diff
                                    targetedPackage = packages[i]
                                }
                            }
                            
                            if (targetedPackage != null) {
                                val density = resources.displayMetrics.density
                                val cx = width / 2f
                                val cy = height / 2f
                                val rad0 = 310f * (density / 2.6f).coerceAtLeast(0.9f)
                                val rad1 = 190f * (density / 2.6f).coerceAtLeast(0.9f)
                                val currentTrackRadius = if (activeGearRing == 0) rad0 else rad1
                                val targetedX = cx - currentTrackRadius
                                val targetedY = cy

                                triggerHyperdriveWarpLaunch(targetedX, targetedY) {
                                    com.sbf.lightspeed.system.ActionDispatcher.execute(service ?: context, targetedPackage)
                                }
                                return true
                            }
                        } else if (activeGearRing == 2) {
                            triggerHardwareHaptic(50, 220)
                            persistActiveGearSetIndex()
                            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
                            val d = resources.displayMetrics.density
                            val deckW = width * 0.88f
                            val bayCount = setsList.size + 1
                            val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
                            val totalBayRailW = bayCount * slotW
                            val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
                            if (totalBayRailW > deckW) {
                                val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                                hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                            } else {
                                hangarBayScrollOffset = 0f
                            }
                            currentLayer = CruiseLayer.COCKPIT_HANGAR
                            invalidate()
                            return true
                        }
                        currentLayer = CruiseLayer.HIDDEN
                        isCruising = false
                        service?.updateWindowLayout(false)
                        updateMetricsDimensions()
                        invalidate()
                        return true
                    }

                    uiHandler.removeCallbacks(neutralToCategoryRunnable)
                    if (currentLayer == CruiseLayer.STICKY_PIN) {
                        isStickyPinned = true; currentLayer = CruiseLayer.GRID
                        updateMetricsDimensions(); invalidate()
                    } else if (currentLayer == CruiseLayer.CATEGORY && activeCatIndex in cachedCategories.indices && cachedCategories[activeCatIndex].id == "launcher_settings_virtual_id") {
                        launchLauncherSettings()
                    } else if (currentLayer == CruiseLayer.NEUTRAL) {
                        val duration = System.currentTimeMillis() - touchDownTime
                        val dist = hypot((rawX - touchDownRawX).toDouble(), (rawY - touchDownRawY).toDouble()).toFloat()
                        dismissOverlay()
                        if (duration < 350 && dist < (20f * resources.displayMetrics.density)) {
                            triggerHardwareHaptic(25, 120)
                            service?.triggerDeflectorsGlow() ?: triggerGlow()
                        }
                    } else {
                        val launchTarget = activeItem
                        if (launchTarget != null) {
                            val placed = placedAppsList.find { it.app == launchTarget }
                            val focalX = placed?.bounds?.centerX() ?: (width / 2f)
                            val focalY = placed?.bounds?.centerY() ?: (height / 2f)
                            triggerHyperdriveWarpLaunch(focalX, focalY) {
                                executeLaunch(launchTarget)
                            }
                        } else {
                            dismissOverlay()
                        }
                    }
                    isCruising = false
                }
                invalidate()
                return true
            }
        }
        return false
    }

    fun openHangarDirectly(setIndex: Int = -1) {
        val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
        if (setIndex in setsList.indices) {
            activeGearSetIndex = setIndex
        }
        currentLayer = CruiseLayer.COCKPIT_HANGAR
        isCruising = false
        isStickyPinned = false
        service?.updateWindowLayout(true)
        updateMetricsDimensions()
        invalidate()
    }

    fun openHangarFromFlank(isLeftFlank: Boolean) {
        isOpenedFromLeftFlank = isLeftFlank
        val prefs = prefs()
        val setsList = getGearSetsOrder(isLeftFlank)
        val launchBehavior = getFlankLaunchBehavior(isLeftFlank)
        val lastActiveKey = if (isLeftFlank) "last_active_set_index_left" else "last_active_set_index_right"

        val targetIndex = if (launchBehavior == "last") {
            prefs.getInt(lastActiveKey, 0)
        } else {
            0
        }

        if (targetIndex in setsList.indices) {
            activeGearSetIndex = targetIndex
        } else {
            activeGearSetIndex = 0
        }

        currentLayer = CruiseLayer.COCKPIT_HANGAR
        isCruising = false
        isStickyPinned = false
        service?.updateWindowLayout(true)
        updateMetricsDimensions()
        invalidate()
    }

    internal fun getAppsForActiveGear(setIndex: Int, ringIndex: Int): List<String> {
        return com.sbf.lightspeed.system.CockpitGearRepository.getAppsForActiveGear(context, isOpenedFromLeftFlank, setIndex, ringIndex)
    }

    internal fun getGearSetNameById(setId: String): String {
        return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameById(context, setId)
    }

    internal fun getGearSetNameByIndex(index: Int): String {
        return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameByIndex(context, isOpenedFromLeftFlank, index)
    }


    internal fun drawSpaceshipGimbalRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        trackRadius: Float,
        trackWidth: Float,
        teethCount: Int,
        toothDepth: Float,
        rotationDeg: Float,
        isActive: Boolean,
        m3Primary: Int
    ) = deepSpaceRenderer.drawSpaceshipGimbalRing(canvas, cx, cy, trackRadius, trackWidth, teethCount, toothDepth, rotationDeg, isActive, m3Primary)

    internal fun drawFlightLockReticle(
        canvas: Canvas,
        targetCX: Float,
        targetCY: Float,
        bracketSize: Float,
        m3Primary: Int,
        m3Secondary: Int,
        appName: String,
        density: Float,
        reticleStyle: String = "tactical"
    ) = deepSpaceRenderer.drawFlightLockReticle(canvas, targetCX, targetCY, bracketSize, m3Primary, m3Secondary, appName, density, reticleStyle)

    internal fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) = deepSpaceRenderer.drawHolographicReactorCore(canvas, cx, cy, coreRadius, isActive, m3Primary, density)

    internal fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) =
        deepSpaceRenderer.drawCosmicStarfield(canvas, w, h, density, alphaFactor)

    internal fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) = deepSpaceRenderer.drawGalacticNebula(canvas, cx, cy, radius, m3Primary, alphaFactor)

    internal fun triggerHyperdriveWarpLaunch(focalX: Float, focalY: Float, onLaunch: () -> Unit) {
        isCurrentlyTouched = false
        currentActiveZone = TouchZone.NONE
        glowAnimator?.cancel()
        glowFraction = 0f
        deepSpaceRenderer.isWarpLaunching = true
        deepSpaceRenderer.warpStartTime = System.currentTimeMillis()
        deepSpaceRenderer.warpFocalPointX = focalX
        deepSpaceRenderer.warpFocalPointY = focalY
        triggerHardwareHaptic(50, 255)
        invalidate()

        uiHandler.postDelayed({
            deepSpaceRenderer.isWarpLaunching = false
            dismissOverlay()
            onLaunch()
        }, 130L)
    }

    internal fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) =
        deepSpaceRenderer.drawHyperdriveWarpSurge(canvas, m3Primary, density)


    internal fun processGyroscopeTouchPhysics(rawX: Float, rawY: Float) {
        val density = resources.displayMetrics.density
        val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
        val deltaY = rawY - lastTouchRawY

        // Abort Track: Closing down gear layer if thumb slides back to bezel origin
        if (deltaX < (10f * density)) {
            currentLayer = CruiseLayer.HIDDEN
            isCruising = false
            service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
            updateMetricsDimensions()
            triggerHardwareHaptic(15, 80)
            invalidate()
            return
        }

        // Depth Axis Calibration (X-Axis Ring Selection)
        val outerThreshold = 50f * density
        val innerThreshold = 120f * density
        val cubeFlipThreshold = 220f * density

        val previousRing = activeGearRing
        activeGearRing = when {
            deltaX < outerThreshold -> 0
            deltaX < innerThreshold -> 1
            else -> 2
        }

        if (previousRing != activeGearRing && activeGearRing != 2) {
            triggerHardwareHaptic(20, 120) // Micro haptic tick on ring crossing
        }

        // Modular 3D Cube Rotation Gate (Over-Swipe Carousel)
        if (deltaX > cubeFlipThreshold) {
            if (!isCubeRotationFired) {
                isCubeRotationFired = true
                activeGearSetIndex = (activeGearSetIndex + 1) % totalGearSetsCount
                persistActiveGearSetIndex()
                triggerHardwareHaptic(55, 255) // Solid mechanical locking thud
            }
        } else if (deltaX < innerThreshold + (25f * density)) {
            isCubeRotationFired = false
        }

        // Rotational Axis Crank Math: Scale angular velocity by true kinematic arc-length and flight physics profile
        val prefs = prefs()
        val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
        val physicsMultiplier = when (physicsProfile) {
            "fluid" -> 1.45f
            "heavy" -> 0.72f
            else -> 1.0f
        }

        val baseDegreesPerDp = 1.35f * physicsMultiplier
        val dyInDp = deltaY / density

        if (activeGearRing in 0..1) {
            val angularDeltaDeg = dyInDp * baseDegreesPerDp
            gearRingRotations[activeGearRing] += angularDeltaDeg

            // Physical Mechanical Cog Notch Haptics as icons cross the 180° focus reticle
            val packages = getAppsForActiveGear(activeGearSetIndex, activeGearRing)
            if (packages.isNotEmpty()) {
                val itemCount = packages.size
                val currentRot = gearRingRotations[activeGearRing]
                var closestIdx = 0
                var minDiff = Float.MAX_VALUE
                for (i in packages.indices) {
                    val itemAngle = (currentRot + i * (360f / itemCount)) % 360f
                    val norm = if (itemAngle < 0) itemAngle + 360f else itemAngle
                    val diff = kotlin.math.abs(norm - 180f)
                    if (diff < minDiff) {
                        minDiff = diff
                        closestIdx = i
                    }
                }
                if (closestIdx != lastTargetedIndex[activeGearRing]) {
                    lastTargetedIndex[activeGearRing] = closestIdx
                    triggerGearCogHaptic()
                }
            }
        }

        lastTouchRawX = rawX
        lastTouchRawY = rawY
        invalidate()
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

            half4 main(float2 fragCoord) {
                float blurAlpha = 0.0;
                if (fragCoord.y < topBlurHeight) {
                    blurAlpha = 1.0 - (fragCoord.y / topBlurHeight);
                } else if (fragCoord.y > viewSize.y - bottomBlurHeight) {
                    blurAlpha = (fragCoord.y - (viewSize.y - bottomBlurHeight)) / bottomBlurHeight;
                }
                blurAlpha = clamp(blurAlpha, 0.0, 1.0);
                float fadeProgress = pow(blurAlpha, 1.3);
                float radius = blurAlpha * 24.0;

                half4 color = half4(0.0);
                if (radius > 0.4) {
                    // Multi-tap Poisson sampling for velvet-smooth liquid glass
                    color += inputTexture.eval(fragCoord) * 0.22;
                    color += inputTexture.eval(fragCoord + float2(0.0, radius * 0.55)) * 0.13;
                    color += inputTexture.eval(fragCoord - float2(0.0, radius * 0.55)) * 0.13;
                    color += inputTexture.eval(fragCoord + float2(radius * 0.55, 0.0)) * 0.13;
                    color += inputTexture.eval(fragCoord - float2(radius * 0.55, 0.0)) * 0.13;
                    color += inputTexture.eval(fragCoord + float2(radius * 0.38, radius * 0.38)) * 0.065;
                    color += inputTexture.eval(fragCoord - float2(radius * 0.38, radius * 0.38)) * 0.065;
                    color += inputTexture.eval(fragCoord + float2(-radius * 0.38, radius * 0.38)) * 0.065;
                    color += inputTexture.eval(fragCoord + float2(radius * 0.38, -radius * 0.38)) * 0.065;

                    // Subtle chromatic edge refraction (frosted prism effect)
                    float chroma = radius * 0.08;
                    half4 rSample = inputTexture.eval(fragCoord + float2(chroma, 0.0));
                    half4 bSample = inputTexture.eval(fragCoord - float2(chroma, 0.0));
                    color.r = mix(color.r, rSample.r, 0.25);
                    color.b = mix(color.b, bSample.b, 0.25);
                } else {
                    color = inputTexture.eval(fragCoord);
                }

                // Deep Space Void tint with subtle cosmic purple glow
                half4 deepSpaceVoid = half4(0.022, 0.018, 0.035, 1.0);
                return mix(color, deepSpaceVoid, fadeProgress * 0.88);
            }
        """.trimIndent()

        
        val progressiveShader by lazy { android.graphics.RuntimeShader(agslSource) }
    }
}
