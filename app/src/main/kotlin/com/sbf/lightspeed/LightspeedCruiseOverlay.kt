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
        renderCacheLeftCenterTransparency = p.getInt("pref_sidebar_left_center_transparency", 0)
        renderCacheTopTransparency      = p.getInt("pref_sidebar_top_transparency", 0)
        renderCacheBottomTransparency   = if (renderCacheLinkEdges) renderCacheTopTransparency else p.getInt("pref_sidebar_bottom_transparency", 0)
        renderCacheGlowStyle            = p.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_STYLE, "progressive_frost") ?: "progressive_frost"
        renderCacheReticleStyle         = p.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
        renderCacheGlowEnabled          = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_RIGHT_GLOW_ENABLED, p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))
        renderCacheLeftGlowEnabled      = p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_LEFT_GLOW_ENABLED, p.getBoolean(com.sbf.lightspeed.system.LightspeedPreferences.KEY_DEFLECTOR_GLOW_ENABLED, true))
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

            if (isOpenedFromLeftFlank) {
                topTouchBounds.set(0f, topLimit, topTouchWidthPx, centerTop)
                centerTouchBounds.set(0f, centerTop, centerTouchWidthPx, centerBottom)
                bottomTouchBounds.set(0f, centerBottom, bottomTouchWidthPx, bottomLimit)

                topVisualBounds.set(0f, topLimit, topVisualWidthPx, centerTop)
                centerVisualBounds.set(0f, centerTop, centerVisualWidthPx, centerBottom)
                bottomVisualBounds.set(0f, centerBottom, bottomVisualWidthPx, bottomLimit)
            } else {
                topTouchBounds.set(w - topTouchWidthPx, topLimit, w, centerTop)
                centerTouchBounds.set(w - centerTouchWidthPx, centerTop, w, centerBottom)
                bottomTouchBounds.set(w - bottomTouchWidthPx, centerBottom, w, bottomLimit)

                topVisualBounds.set(w - topVisualWidthPx, topLimit, w, centerTop)
                centerVisualBounds.set(w - centerVisualWidthPx, centerTop, w, centerBottom)
                bottomVisualBounds.set(w - bottomVisualWidthPx, centerBottom, w, bottomLimit)
            }

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
                updatePillShaderUniforms(active = false)
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
            val isPillEnabled = if (isOpenedFromLeftFlank) renderCacheLeftGlowEnabled else renderCacheGlowEnabled
            val isExpanded = (currentLayer != CruiseLayer.HIDDEN && currentLayer != CruiseLayer.NEUTRAL)
            val shouldApplyBlur = isExpanded && (isPillEnabled || currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)
            setRenderEffect(if (shouldApplyBlur) cachedRenderEffect else null)
        }
    }

    internal fun updatePillShaderUniforms(
        left: Float = 0f,
        top: Float = 0f,
        right: Float = 0f,
        bottom: Float = 0f,
        cornerR: Float = 0f,
        isLeft: Boolean = false,
        active: Boolean = false
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val screenW = width.toFloat().coerceAtLeast(1f)
                val screenH = height.toFloat().coerceAtLeast(1f)
                val density = resources.displayMetrics.density
                progressiveShader.apply {
                    setFloatUniform("viewSize", screenW, screenH)
                    setFloatUniform("topBlurHeight", 130.0f * density)
                    setFloatUniform("bottomBlurHeight", 160.0f * density)
                    setFloatUniform("headerBlurActive", if (currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN) 1f else 0f)
                }
            } catch (e: Exception) {
                Log.e("LightspeedBlur", "Failed to update AGSL uniforms", e)
            }
        }
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
        currentTouchY = -1f
        currentActiveZone = TouchZone.NONE
        updatePillShaderUniforms(active = false)
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
        performStartCruiseFromFlank(isLeft, startRawX, startRawY)
    }

    fun handleFlankTouchEvent(isLeft: Boolean, event: MotionEvent): Boolean {
        return performFlankTouchEvent(isLeft, event)
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
