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
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class LightspeedCruiseOverlay @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val totalGearSetsCount: Int
        get() {
            val count = getGearSetsOrder(isOpenedFromLeftFlank).size
            return if (count > 0) count else 1
        }


    enum class CruiseLayer { HIDDEN, NEUTRAL, CATEGORY, GRID, STICKY_PIN, FAVORITES_GEARS, COCKPIT_HANGAR }
    enum class TouchZone { NONE, TOP_EDGE, CENTER_CRUISE, BOTTOM_EDGE }
    
    enum class MacroGesture {
        NONE,
        SWIPE_UP, SWIPE_UP_HOLD,
        SWIPE_DOWN, SWIPE_DOWN_HOLD,
        SWIPE_LEFT, SWIPE_LEFT_HOLD,
        SWIPE_UP_DOWN, SWIPE_UP_DOWN_HOLD,
        SWIPE_DOWN_UP, SWIPE_DOWN_UP_HOLD,
        SWIPE_UP_LEFT, SWIPE_UP_LEFT_HOLD,
        SWIPE_DOWN_LEFT, SWIPE_DOWN_LEFT_HOLD,
        SWIPE_LEFT_BACK, SWIPE_LEFT_BACK_HOLD,
        SWIPE_LEFT_UP, SWIPE_LEFT_UP_HOLD,
        SWIPE_LEFT_DOWN, SWIPE_LEFT_DOWN_HOLD,
        SCRUBBING
    }

    data class PlacedItem(val app: LightspeedDataBridge.LaunchTarget, val bounds: RectF)

    private val service = context as? LightspeedAccessibilityService
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var isCruising = false
    private var isStickyPinned = false
    private var currentLayer: CruiseLayer = CruiseLayer.HIDDEN
        set(value) {
            field = value
            refreshActiveRenderEffect()
        }
    private var currentActiveZone = TouchZone.NONE

    private var centerHeightPx = 400f
    private var centerVisualWidthPx = 12f
    private var centerTouchWidthPx = 45f
    private var centerYOffsetPx = 0f

    private var topHeightPx = 300f
    private var topVisualWidthPx = 2f
    private var topTouchWidthPx = 32f

    private var bottomHeightPx = 300f
    private var bottomVisualWidthPx = 2f
    private var bottomTouchWidthPx = 32f

    private var linkEdges = false

    private val topTouchBounds = RectF()
    private val centerTouchBounds = RectF()
    private val bottomTouchBounds = RectF()

    private val topVisualBounds = RectF()
    private val centerVisualBounds = RectF()
    private val bottomVisualBounds = RectF()

    private val uiHandler = Handler(Looper.getMainLooper())
    private var touchDownTime = 0L
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var macroTrackingActive = false
    private var currentDetectedGesture = MacroGesture.NONE

    private var trackingStateLocked = false
    private var isCurrentlyTouched = false
    private var initialLeftSweepDistance = 0f
    private var lowestXReached = 0f
    private var highestYReached = 0f
    private var lowestYReached = 0f
    
    private var aggregateScrubAccumulator = 0f
    private var isScrubEntranceHapticFired = false

    private fun triggerHardwareHaptic(durationMs: Long, amplitude: Int) {
        LightspeedHapticEngine.vibrate(context, durationMs, amplitude)
    }
    private var lastPermissionToastTime = 0L
    private var overScrollBoundaryAccumulator = 0f
    private var settingsCategoryAppended = false
    private var hangarBayScrollOffset = 0f
    private var hangarBayTouchDownX = 0f
    private var hangarBayTouchDownY = 0f
    private var isDraggingHangarBays = false
    private var isSpinningHangarRing = false
    private var hangarSpinTouchY = 0f
    private var activeHangarRing = 0
    private var isHangarEjectArmed = false
    private var hangarEjectTargetIndex = -1
    private var hangarEjectRing = -1
    private var longPressHangarBayRunnable: Runnable? = null
    private var longPressCogRunnable: Runnable? = null
    private var hasLongPressFired = false
    private var isTouchingFocusedCog = false
    private var isOpenedFromLeftFlank = false

    private fun persistActiveGearSetIndex() {
        com.sbf.lightspeed.system.CockpitGearRepository.persistActiveGearSetIndex(context, activeGearSetIndex, isOpenedFromLeftFlank)
    }

    private var activeHoldScrubAction: String? = null
    private var scrubHudTitle = ""
    private var scrubHudValue = ""

    private val holdTimerRunnable = Runnable {
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
            val prefs = context.defaultPrefs()
            val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
            val gestMode = prefs.getString("pref_symmetry_gesture_mode", "independent") ?: "independent"
            val isMirroringLeft = gestMode == "left"

            val actionValue = if (isMirroringLeft) {
                val isLeftUnified = prefs.getBoolean("pref_sidebar_left_link_flank_actions", false)
                val leftZone = if (isLeftUnified) "LEFT_UNIFIED" else "LEFT_$zoneName"
                val leftGesture = when (gestureKey) {
                    "SWIPE_LEFT_HOLD" -> "SWIPE_RIGHT_HOLD"
                    "SWIPE_LEFT_UP_HOLD" -> "SWIPE_RIGHT_UP_HOLD"
                    "SWIPE_LEFT_DOWN_HOLD" -> "SWIPE_RIGHT_DOWN_HOLD"
                    "SWIPE_LEFT_BACK_HOLD" -> "SWIPE_RIGHT_BACK_HOLD"
                    "SWIPE_UP_LEFT_HOLD" -> "SWIPE_UP_RIGHT_HOLD"
                    "SWIPE_DOWN_LEFT_HOLD" -> "SWIPE_DOWN_RIGHT_HOLD"
                    else -> gestureKey
                }
                prefs.getString("pref_macro_action_${leftZone}_$leftGesture", "none") ?: "none"
            } else {
                val dynamicZone = if (isFlankUnified) "UNIFIED" else zoneName
                val actionKey = "pref_macro_action_${dynamicZone}_$gestureKey"
                prefs.getString(actionKey, "none") ?: "none"
            }

            if (actionValue == "system:volume" || actionValue == "system:brightness" || actionValue == "system:screen_timeout" || actionValue == "scrub:volume" || actionValue == "scrub:brightness") {
                currentDetectedGesture = MacroGesture.SCRUBBING
                activeHoldScrubAction = actionValue
                aggregateScrubAccumulator = 0f
                isScrubEntranceHapticFired = true
                triggerHardwareHaptic(35, 180)
                when (actionValue) {
                    "system:volume", "scrub:volume" -> {
                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        scrubHudTitle = "MEDIA VOLUME"
                        scrubHudValue = "$currentVol / $maxVol"
                    }
                    "system:brightness", "scrub:brightness" -> {
                        val currentBrightness = try {
                            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                        } catch (_: Exception) { 128 }
                        scrubHudTitle = "BRIGHTNESS"
                        scrubHudValue = "${(currentBrightness * 100 / 255)}%"
                    }
                    "system:screen_timeout" -> {
                        scrubHudTitle = "SHIP GOES DARK IN"
                        scrubHudValue = LightspeedTimeoutEngine.getCurrentFormatted(context)
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

    private val neutralToCategoryRunnable = Runnable {
        if (isCruising && currentLayer == CruiseLayer.NEUTRAL) {
            currentLayer = CruiseLayer.CATEGORY
            entranceStartTime = System.currentTimeMillis()
            invalidate()
        }
    }

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key != null && (key.startsWith("pref_sidebar_") || key.startsWith("pref_section_"))) {
            post {
                updateMetricsDimensions()
                invalidate()
            }
        }
    }

    private var cachedRenderEffect: RenderEffect? = null

    private var cachedCategories = listOf<LightspeedDataBridge.CategoryNode>()
    private var activeCatIndex = -1
    private var initialCatIndex = 0
    private var categoryVisualOffset = 0f
    private var entranceStartTime = 0L
    private var snapAnimator: ValueAnimator? = null

    private var cachedApps = listOf<LightspeedDataBridge.LaunchTarget>()
    private var placedAppsList = mutableListOf<PlacedItem>()
    private var totalGridContentHeight = 0f
    private var viewportScrollOffset = 0f
    private var activeItem: LightspeedDataBridge.LaunchTarget? = null

    private val categoryAppsCache = mutableMapOf<String, List<LightspeedDataBridge.LaunchTarget>>()
    private val categoryGridCache = mutableMapOf<String, List<PlacedItem>>()
    private val categoryHeightCache = mutableMapOf<String, Float>()
    private val applicationIconCache = mutableMapOf<String, Drawable>()
    private var lastLoadedCategoryId: String? = null

    // --- GYROSCOPE CORE WORKSPACE STATES ---
    private var activeGearRing = 0          // 0 = Outer Ring, 1 = Inner Ring, 2 = Center Control Hub
    private var activeGearSetIndex = 0      // Active profile face (Set A, B, C, D)
    private var gearRingRotations = FloatArray(3) { 0f }
    private var rawHorizontalXAccumulator = 0f
    private var isCubeRotationFired = false
    private var lastTargetedIndex = intArrayOf(-1, -1)

    private fun triggerGearCogHaptic() {
        val prefs = context.defaultPrefs()
        val strength = prefs.getString("pref_gear_haptic_strength", "tactical") ?: "tactical"
        when (strength) {
            "subtle" -> triggerHardwareHaptic(10, 50)
            "tactical" -> triggerHardwareHaptic(18, 130)
            "heavy" -> triggerHardwareHaptic(30, 220)
            "off" -> {}
            else -> triggerHardwareHaptic(18, 130)
        }
    }

    private var touchDownRawX = 0f
    private var touchDownRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var categoryScrubbingEngaged = false
    private var maxVerticalDisplacement = 0f
    private var depthPercentage = 0f
    private var virtualCursorX = 0f
    private var virtualCursorY = 0f

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var lastTouchY = 0f

    private val launchpadPillBounds = RectF() 
    private val dataBridge = LightspeedDataBridge(context)

    private val projectionCamera3D = Camera()
    private val transformMatrixPipeline = Matrix()
    private val cylinderRadius = 500f

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 24f
        isAntiAlias = true; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val catTextPaint = Paint().apply {
        style = Paint.Style.FILL; color = Color.WHITE; textSize = 52f
        isAntiAlias = true; textAlign = Paint.Align.RIGHT; typeface = android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
    }
    private val elementPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    private val highlightPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

    /** Extracted renderer for the COCKPIT_HANGAR layer and all shared draw helpers. */
    private val deepSpaceRenderer by lazy { DeepSpaceRenderer(textPaint, elementPaint, highlightPaint) }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val prefs = context.defaultPrefs()
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateMetricsDimensions()
    }

    override fun onDetachedFromWindow() {
        val prefs = context.defaultPrefs()
        prefs.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        super.onDetachedFromWindow()
    }

    fun updateMetricsDimensions() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val lp = layoutParams as? WindowManager.LayoutParams ?: return

        val displayMetrics = resources.displayMetrics
        val screenW = displayMetrics.widthPixels.toFloat()
        val screenH = displayMetrics.heightPixels.toFloat()
        val density = displayMetrics.density

        val prefs = context.defaultPrefs()
        
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

    private fun refreshActiveRenderEffect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val shouldApplyBlur = (currentLayer == CruiseLayer.GRID || currentLayer == CruiseLayer.STICKY_PIN)
            setRenderEffect(if (shouldApplyBlur) cachedRenderEffect else null)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        val rawX = event.rawX; val rawY = event.rawY
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            val d = resources.displayMetrics.density
            val screenW = width.toFloat()
            val screenH = height.toFloat()
            val cx = screenW / 2f
            val cy = screenH / 2f
            val deckW = (screenW * 0.92f).coerceAtMost(480f * d)
            val leftX = cx - (deckW / 2f)
            val rightX = cx + (deckW / 2f)
            val gap = 8f * d

            val topHangarY = (screenH * 0.07f).coerceAtLeast(54f * d)
            val prefs = context.defaultPrefs()
            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
            if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
            val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"

            val r0Y = topHangarY + 40f * d
            val r1Y = r0Y + 38f * d
            val r2Y = r1Y + 44f * d
            val r3Y = r2Y + 44f * d
            val r4Y = r3Y + 38f * d
            val r5Y = r4Y + 38f * d
            val r6Y = r5Y + 38f * d
            val r7Y = r6Y + 35f * d

            val cyGimbal = (r7Y + 160f * d).coerceAtLeast(screenH * 0.62f)
            val radOuter = 135f * d
            val radInner = 84f * d
            val radHub = 32f * d
            val shiftBarY = cyGimbal + radOuter + 26f * d
            val transferBarY = cyGimbal + radOuter + 58f * d

            val bayCount = setsList.size + 1
            val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
            val totalBayRailW = bayCount * slotW
            val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)

            if (totalBayRailW <= deckW) {
                hangarBayScrollOffset = 0f
            } else {
                hangarBayScrollOffset = hangarBayScrollOffset.coerceIn(-maxScroll, 0f)
            }
            val railStartX = if (totalBayRailW <= deckW) (leftX + (deckW - totalBayRailW) / 2f) else (leftX + hangarBayScrollOffset)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hasLongPressFired = false
                    longPressHangarBayRunnable?.let { removeCallbacks(it) }
                    longPressCogRunnable?.let { removeCallbacks(it) }
                    longPressHangarBayRunnable = null
                    longPressCogRunnable = null

                    hangarBayTouchDownX = x
                    hangarBayTouchDownY = y
                    isDraggingHangarBays = (y in (r2Y - 24f * d)..(r2Y + 24f * d)) && (x in (leftX - 10f * d)..(rightX + 10f * d))

                    if (isDraggingHangarBays) {
                        for (gIndex in setsList.indices) {
                            val btnX = railStartX + (gIndex + 0.5f) * slotW
                            val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)
                            if (bayRect.contains(x, y)) {
                                val targetSetId = setsList[gIndex]
                                val currentName = prefs.getString("gear_set_${targetSetId}_name", "SET ${gIndex + 1}") ?: "SET ${gIndex + 1}"
                                val runnable = Runnable {
                                    hasLongPressFired = true
                                    triggerHardwareHaptic(50, 255)
                                    val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                                        action = CockpitDialogActivity.ACTION_RENAME_GEAR
                                        putExtra(CockpitDialogActivity.EXTRA_SET_ID, targetSetId)
                                        putExtra(CockpitDialogActivity.EXTRA_SET_INDEX, gIndex)
                                        putExtra(CockpitDialogActivity.EXTRA_CURRENT_NAME, currentName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                    }
                                    dismissOverlay()
                                    context.startActivity(intent)
                                }
                                longPressHangarBayRunnable = runnable
                                postDelayed(runnable, 400)
                                break
                            }
                        }
                    } else {
                        // 0. Flank Switcher: Port (Left) vs Starboard (Right)
                        val halfBtnW = (deckW - gap) / 2f
                        val portRect = RectF(leftX, r0Y - 16f * d, leftX + halfBtnW, r0Y + 16f * d)
                        val starboardRect = RectF(rightX - halfBtnW, r0Y - 16f * d, rightX, r0Y + 16f * d)
                        if (portRect.contains(x, y)) {
                            isOpenedFromLeftFlank = true
                            val flankSets = getGearSetsOrder(true)
                            if (activeGearSetIndex >= flankSets.size) activeGearSetIndex = 0
                            hangarBayScrollOffset = 0f
                            triggerHardwareHaptic(25, 140)
                            invalidate()
                            return true
                        }
                        if (starboardRect.contains(x, y)) {
                            isOpenedFromLeftFlank = false
                            val flankSets = getGearSetsOrder(false)
                            if (activeGearSetIndex >= flankSets.size) activeGearSetIndex = 0
                            hangarBayScrollOffset = 0f
                            triggerHardwareHaptic(25, 140)
                            invalidate()
                            return true
                        }

                        // 1. Startup Default Mode: Always First vs Resume Last (Per-Flank)
                        val btn1Rect = RectF(leftX, r1Y - 16f * d, leftX + halfBtnW, r1Y + 16f * d)
                        val btn2Rect = RectF(rightX - halfBtnW, r1Y - 16f * d, rightX, r1Y + 16f * d)
                        if (btn1Rect.contains(x, y)) {
                            setFlankLaunchBehavior(isOpenedFromLeftFlank, "default")
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (btn2Rect.contains(x, y)) {
                            setFlankLaunchBehavior(isOpenedFromLeftFlank, "last")
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 3. Reorder & Delete Active Profile in Rotation (Per-Flank)
                        val shiftW = deckW * 0.35f
                        val deleteW = deckW * 0.26f
                        val shiftLeftRect = RectF(leftX, r3Y - 16f * d, leftX + shiftW, r3Y + 16f * d)
                        val deleteBayRect = RectF(cx - deleteW / 2f, r3Y - 16f * d, cx + deleteW / 2f, r3Y + 16f * d)
                        val shiftRightRect = RectF(rightX - shiftW, r3Y - 16f * d, rightX, r3Y + 16f * d)

                        if (shiftLeftRect.contains(x, y) && activeGearSetIndex > 0) {
                            val temp = setsList[activeGearSetIndex]
                            setsList[activeGearSetIndex] = setsList[activeGearSetIndex - 1]
                            setsList[activeGearSetIndex - 1] = temp
                            activeGearSetIndex--
                            saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                            val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                            hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                            triggerHardwareHaptic(30, 160)
                            invalidate()
                            return true
                        }
                        if (shiftRightRect.contains(x, y) && activeGearSetIndex < setsList.size - 1) {
                            val temp = setsList[activeGearSetIndex]
                            setsList[activeGearSetIndex] = setsList[activeGearSetIndex + 1]
                            setsList[activeGearSetIndex + 1] = temp
                            activeGearSetIndex++
                            saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                            val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                            hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                            triggerHardwareHaptic(30, 160)
                            invalidate()
                            return true
                        }
                        if (deleteBayRect.contains(x, y)) {
                            if (setsList.size > 1) {
                                val removedId = setsList.removeAt(activeGearSetIndex)
                                if (activeGearSetIndex >= setsList.size) {
                                    activeGearSetIndex = setsList.size - 1
                                }
                                saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                                val targetOffset = (deckW / 2f) - ((activeGearSetIndex + 0.5f) * slotW)
                                hangarBayScrollOffset = targetOffset.coerceIn(-maxScroll, 0f)
                                triggerHardwareHaptic(45, 230)
                                invalidate()
                                return true
                            } else {
                                android.widget.Toast.makeText(context, "Cannot delete the last remaining gear set", android.widget.Toast.LENGTH_SHORT).show()
                                return true
                            }
                        }

                        // 4. Icon Theme Carousel
                        val iconThemeRect = RectF(leftX, r4Y - 18f * d, rightX, r4Y + 18f * d)
                        if (iconThemeRect.contains(x, y)) {
                            val availablePacks = com.sbf.lightspeed.system.LightspeedIconManager.getAvailableIconPacks(context)
                            if (availablePacks.isNotEmpty()) {
                                val activePack = com.sbf.lightspeed.system.LightspeedIconManager.getActiveIconPack(context)
                                val currentIndex = availablePacks.indexOfFirst { it.packageName == activePack }
                                val nextIndex = (currentIndex + 1) % availablePacks.size
                                com.sbf.lightspeed.system.LightspeedIconManager.setActiveIconPack(context, availablePacks[nextIndex].packageName)
                                triggerHardwareHaptic(35, 180)
                                invalidate()
                                return true
                            }
                        }

                        // 5. Flight Momentum
                        val physW = (deckW - (gap * 2)) / 3f
                        val phys1Rect = RectF(leftX, r5Y - 16f * d, leftX + physW, r5Y + 16f * d)
                        val phys2Rect = RectF(leftX + physW + gap, r5Y - 16f * d, leftX + physW * 2 + gap, r5Y + 16f * d)
                        val phys3Rect = RectF(rightX - physW, r5Y - 16f * d, rightX, r5Y + 16f * d)
                        if (phys1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "magnetic").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (phys2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "fluid").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (phys3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_physics_profile", "heavy").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 6. Tactile Ratchet Haptics
                        val hapW = (deckW - (gap * 3)) / 4f
                        val hap1Rect = RectF(leftX, r6Y - 15f * d, leftX + hapW, r6Y + 15f * d)
                        val hap2Rect = RectF(leftX + (hapW + gap), r6Y - 15f * d, leftX + (hapW + gap) + hapW, r6Y + 15f * d)
                        val hap3Rect = RectF(leftX + (hapW + gap) * 2, r6Y - 15f * d, leftX + (hapW + gap) * 2 + hapW, r6Y + 15f * d)
                        val hap4Rect = RectF(rightX - hapW, r6Y - 15f * d, rightX, r6Y + 15f * d)
                        if (hap1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "subtle").apply()
                            triggerHardwareHaptic(10, 60)
                            invalidate()
                            return true
                        }
                        if (hap2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "tactical").apply()
                            triggerHardwareHaptic(20, 140)
                            invalidate()
                            return true
                        }
                        if (hap3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "heavy").apply()
                            triggerHardwareHaptic(35, 240)
                            invalidate()
                            return true
                        }
                        if (hap4Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_haptic_strength", "off").apply()
                            invalidate()
                            return true
                        }

                        // 7. Reticle Crosshair Style
                        val retW = (deckW - (gap * 3)) / 4f
                        val ret1Rect = RectF(leftX, r7Y - 14f * d, leftX + retW, r7Y + 14f * d)
                        val ret2Rect = RectF(leftX + (retW + gap), r7Y - 14f * d, leftX + (retW + gap) + retW, r7Y + 14f * d)
                        val ret3Rect = RectF(leftX + (retW + gap) * 2, r7Y - 14f * d, leftX + (retW + gap) * 2 + retW, r7Y + 14f * d)
                        val ret4Rect = RectF(rightX - retW, r7Y - 14f * d, rightX, r7Y + 14f * d)
                        if (ret1Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "tactical").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret2Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "cyber").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret3Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "cross").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (ret4Rect.contains(x, y)) {
                            prefs.edit().putString("pref_gear_reticle_style", "diamond").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 7. Live Gimbal Touch & Interaction
                        val shiftCogLeftRect = RectF(cx - 150f * d, shiftBarY - 14f * d, cx - 60f * d, shiftBarY + 14f * d)
                        val shiftCogRightRect = RectF(cx + 60f * d, shiftBarY - 14f * d, cx + 150f * d, shiftBarY + 14f * d)
                        val transferRect = RectF(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
                        val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing).toMutableList()

                        // Check Inter-Ring Transfer Tap (Move App between Ring 0 and Ring 1)
                        if (transferRect.contains(x, y) && ringApps.isNotEmpty()) {
                            val count = ringApps.size
                            val baseRotation = gearRingRotations[activeHangarRing]
                            var targetedIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in ringApps.indices) {
                                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                                val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                                val diff = kotlin.math.abs(norm - 180f)
                                if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                            }
                            val destRing = if (activeHangarRing == 0) 1 else 0
                            val r0 = getAppsForActiveGear(activeGearSetIndex, 0).toMutableList()
                            val r1 = getAppsForActiveGear(activeGearSetIndex, 1).toMutableList()
                            val movedItem = if (activeHangarRing == 0) r0.removeAt(targetedIdx) else r1.removeAt(targetedIdx)
                            if (destRing == 0) r0.add(movedItem) else r1.add(movedItem)

                            prefs.edit()
                                .putString("gear_set_${currentSetId}_ring_0_packages", r0.joinToString(","))
                                .putString("gear_set_${currentSetId}_ring_1_packages", r1.joinToString(","))
                                .apply()

                            activeHangarRing = destRing
                            triggerHardwareHaptic(50, 255)
                            invalidate()
                            return true
                        }

                        // Check Live Orbital Shift Left
                        if (shiftCogLeftRect.contains(x, y) && ringApps.size > 1) {
                            val count = ringApps.size
                            val baseRotation = gearRingRotations[activeHangarRing]
                            var targetedIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in ringApps.indices) {
                                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                                val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                                val diff = kotlin.math.abs(norm - 180f)
                                if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                            }
                            val prevIdx = (targetedIdx - 1 + ringApps.size) % ringApps.size
                            val temp = ringApps[targetedIdx]
                            ringApps[targetedIdx] = ringApps[prevIdx]
                            ringApps[prevIdx] = temp
                            prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                            gearRingRotations[activeHangarRing] = (gearRingRotations[activeHangarRing] + (360f / count)) % 360f
                            triggerHardwareHaptic(30, 160)
                            invalidate()
                            return true
                        }

                        // Check Live Orbital Shift Right
                        if (shiftCogRightRect.contains(x, y) && ringApps.size > 1) {
                            val count = ringApps.size
                            val baseRotation = gearRingRotations[activeHangarRing]
                            var targetedIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in ringApps.indices) {
                                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                                val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                                val diff = kotlin.math.abs(norm - 180f)
                                if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                            }
                            val nextIdx = (targetedIdx + 1) % ringApps.size
                            val temp = ringApps[targetedIdx]
                            ringApps[targetedIdx] = ringApps[nextIdx]
                            ringApps[nextIdx] = temp
                            prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                            gearRingRotations[activeHangarRing] = (gearRingRotations[activeHangarRing] - (360f / count)) % 360f
                            triggerHardwareHaptic(30, 160)
                            invalidate()
                            return true
                        }

                        val distFromCore = kotlin.math.hypot(x - cx, y - cyGimbal)
                        val reticleX = cx - (if (activeHangarRing == 0) radOuter else radInner)
                        val reticleY = cyGimbal
                        val distFromReticle = kotlin.math.hypot(x - reticleX, y - reticleY)
                        val targetBadgeRect = RectF(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)

                        // 1. Center Command Core Tap -> Open Gear Picker for Active Ring
                        if (distFromCore <= radHub) {
                            isHangarEjectArmed = false
                            val intent = android.content.Intent(context, CockpitGearPickerActivity::class.java).apply {
                                putExtra("SET_ID", currentSetId)
                                putExtra("RING_INDEX", activeHangarRing)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                            }
                            context.startActivity(intent)
                            uiHandler.postDelayed({ dismissOverlay() }, 150)
                            return true
                        }

                        // 2. Focused Cog or Target Badge -> Long Press to Edit, Tap to Eject
                        val isTouchingReticleOrBadge = (distFromReticle <= 48f * d) || targetBadgeRect.contains(x, y)

                        if (isTouchingReticleOrBadge && ringApps.isNotEmpty()) {
                            isTouchingFocusedCog = true
                            val count = ringApps.size
                            val baseRotation = gearRingRotations[activeHangarRing]
                            var targetedIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in ringApps.indices) {
                                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                                val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                                val diff = kotlin.math.abs(norm - 180f)
                                if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                            }

                            val targetedToken = if (targetedIdx in ringApps.indices) ringApps[targetedIdx] else null
                            if (targetedToken != null) {
                                val runnable = Runnable {
                                    hasLongPressFired = true
                                    triggerHardwareHaptic(50, 255)
                                    val intent = Intent(context, CockpitDialogActivity::class.java).apply {
                                        action = CockpitDialogActivity.ACTION_EDIT_ITEM
                                        putExtra(CockpitDialogActivity.EXTRA_TOKEN, targetedToken)
                                        putExtra(CockpitDialogActivity.EXTRA_SET_INDEX, activeGearSetIndex)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                                    }
                                    context.startActivity(intent)
                                    uiHandler.postDelayed({ dismissOverlay() }, 150)
                                }
                                longPressCogRunnable = runnable
                                postDelayed(runnable, 400)
                            }
                            return true
                        }

                        if (distFromCore in (radInner + 16f * d)..(radOuter + 32f * d)) {
                            isHangarEjectArmed = false
                            activeHangarRing = 0
                            isSpinningHangarRing = true
                            hangarSpinTouchY = y
                            triggerHardwareHaptic(15, 80)
                            invalidate()
                            return true
                        } else if (distFromCore in (radHub + 4f * d)..(radInner + 16f * d)) {
                            isHangarEjectArmed = false
                            activeHangarRing = 1
                            isSpinningHangarRing = true
                            hangarSpinTouchY = y
                            triggerHardwareHaptic(15, 80)
                            invalidate()
                            return true
                        }

                        // 8. Bottom Exit Capsule
                        val exitBtnRect = RectF(cx - (deckW * 0.42f), screenH - 58f * d, cx + (deckW * 0.42f), screenH - 18f * d)
                        if (exitBtnRect.contains(x, y) || y < (20f * d) || y > (height - 15f * d)) {
                            dismissOverlay()
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    val distMoved = kotlin.math.hypot(x - hangarBayTouchDownX, y - hangarBayTouchDownY)
                    if (distMoved > 10f * d) {
                        longPressHangarBayRunnable?.let { removeCallbacks(it) }
                        longPressCogRunnable?.let { removeCallbacks(it) }
                        longPressHangarBayRunnable = null
                        longPressCogRunnable = null
                        isTouchingFocusedCog = false
                    }

                    if (isDraggingHangarBays) {
                        val dx = x - hangarBayTouchDownX
                        hangarBayTouchDownX = x
                        if (maxScroll > 0f) {
                            hangarBayScrollOffset = (hangarBayScrollOffset + dx).coerceIn(-maxScroll, 0f)
                            invalidate()
                        }
                        return true
                    } else if (isSpinningHangarRing) {
                        val dy = y - hangarSpinTouchY
                        hangarSpinTouchY = y
                        val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
                        val multiplier = when (physicsProfile) {
                            "magnetic" -> 1.0f
                            "fluid" -> 1.45f
                            "heavy" -> 0.72f
                            else -> 1.0f
                        }
                        val baseDegreesPerDp = 1.35f * multiplier
                        val dyInDp = dy / d
                        val rotDelta = dyInDp * baseDegreesPerDp
                        val oldRot = gearRingRotations[activeHangarRing]
                        val newRot = (oldRot + rotDelta) % 360f
                        gearRingRotations[activeHangarRing] = newRot

                        val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing)
                        if (ringApps.isNotEmpty()) {
                            val step = 360f / ringApps.size
                            val oldNotch = (((oldRot % 360f) + 360f) % 360f / step).toInt()
                            val newNotch = (((newRot % 360f) + 360f) % 360f / step).toInt()
                            if (oldNotch != newNotch) {
                                triggerGearCogHaptic()
                            }
                        }
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHangarBayRunnable?.let { removeCallbacks(it) }
                    longPressCogRunnable?.let { removeCallbacks(it) }
                    longPressHangarBayRunnable = null
                    longPressCogRunnable = null

                    if (hasLongPressFired) {
                        hasLongPressFired = false
                        isTouchingFocusedCog = false
                        isSpinningHangarRing = false
                        isDraggingHangarBays = false
                        return true
                    }

                    if (isTouchingFocusedCog) {
                        isTouchingFocusedCog = false
                        isSpinningHangarRing = false
                        val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing).toMutableList()
                        if (ringApps.isNotEmpty()) {
                            val count = ringApps.size
                            val baseRotation = gearRingRotations[activeHangarRing]
                            var targetedIdx = 0
                            var minDiff = Float.MAX_VALUE
                            for (i in ringApps.indices) {
                                val angleDeg = (baseRotation + i * (360f / count)) % 360f
                                val norm = if (angleDeg < 0) angleDeg + 360f else angleDeg
                                val diff = kotlin.math.abs(norm - 180f)
                                if (diff < minDiff) { minDiff = diff; targetedIdx = i }
                            }

                            if (isHangarEjectArmed && hangarEjectTargetIndex == targetedIdx && hangarEjectRing == activeHangarRing) {
                                // CONFIRM EJECT: Purge targeted cog from ring
                                ringApps.removeAt(targetedIdx)
                                prefs.edit().putString("gear_set_${currentSetId}_ring_${activeHangarRing}_packages", ringApps.joinToString(",")).apply()
                                isHangarEjectArmed = false
                                hangarEjectTargetIndex = -1
                                triggerHardwareHaptic(60, 255)
                                invalidate()
                                return true
                            } else {
                                // ARM EJECT: Show red containment and white X on the tapped cog, and show badge alert with eject shake
                                isHangarEjectArmed = true
                                hangarEjectTargetIndex = targetedIdx
                                hangarEjectRing = activeHangarRing
                                triggerHardwareHaptic(40, 220)
                                invalidate()
                                return true
                            }
                        }
                    }

                    isSpinningHangarRing = false
                    val distMoved = kotlin.math.hypot(x - hangarBayTouchDownX, y - hangarBayTouchDownY)
                    if (isDraggingHangarBays) {
                        isDraggingHangarBays = false
                        if (distMoved < 14f * d) {
                            for (gIndex in setsList.indices) {
                                val btnX = railStartX + (gIndex + 0.5f) * slotW
                                val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)
                                if (bayRect.contains(x, y)) {
                                    activeGearSetIndex = gIndex
                                    persistActiveGearSetIndex()
                                    triggerHardwareHaptic(25, 140)
                                    invalidate()
                                    return true
                                }
                            }
                            val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
                            val plusRect = RectF(plusBtnX - slotW * 0.42f, r2Y - 18f * d, plusBtnX + slotW * 0.42f, r2Y + 18f * d)
                            if (plusRect.contains(x, y)) {
                                val newId = System.currentTimeMillis().toString()
                                setsList.add(newId)
                                saveGearSetsOrder(isOpenedFromLeftFlank, setsList)
                                prefs.edit().putString("gear_set_${newId}_name", "SET ${setsList.size}").apply()
                                activeGearSetIndex = setsList.size - 1
                                val newTotalRailW = (setsList.size + 1) * slotW
                                val newMaxScroll = (newTotalRailW - deckW).coerceAtLeast(0f)
                                hangarBayScrollOffset = -newMaxScroll
                                triggerHardwareHaptic(40, 200)
                                invalidate()
                                return true
                            }
                        }
                        invalidate()
                        return true
                    }
                }
            }
            return true
        }

        if (isStickyPinned) {
            val hF = height.toFloat()
            val gridTopLimit = hF * 0.15f
            val gridBottomLimit = hF * 0.94f
            val gridHeightScope = gridBottomLimit - gridTopLimit
            val maxScroll = max(0f, totalGridContentHeight - gridHeightScope)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = x; touchDownY = y; lastTouchY = y
                    activeItem = null
                    if (y in gridTopLimit..gridBottomLimit) {
                        val absoluteY = y + viewportScrollOffset
                        for (placedItem in placedAppsList) {
                            if (placedItem.bounds.contains(x, absoluteY)) { activeItem = placedItem.app; break }
                        }
                    }
                    invalidate(); return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = y - lastTouchY
                    lastTouchY = y
                    if (abs(y - touchDownY) > 16f || abs(x - touchDownX) > 16f) activeItem = null
                    if (maxScroll > 0f) {
                        viewportScrollOffset = (viewportScrollOffset - deltaY).coerceIn(0f, maxScroll)
                        invalidate()
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (hypot((x - touchDownX).toDouble(), (y - touchDownY).toDouble()) < 16f) {
                        if (y in gridTopLimit..gridBottomLimit) activeItem?.let { executeLaunch(it); dismissOverlay() } ?: dismissOverlay()
                        else dismissOverlay()
                    }
                    activeItem = null; invalidate(); return true
                }
                MotionEvent.ACTION_CANCEL -> { isCurrentlyTouched = false; activeItem = null; invalidate(); return true }
            }
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchDownRawX = rawX; touchDownRawY = rawY
                lastTouchRawX = rawX; lastTouchRawY = rawY
                touchDownTime = System.currentTimeMillis()
                gestureStartX = rawX; gestureStartY = rawY

                trackingStateLocked = false
                isCurrentlyTouched = true; invalidate()
                initialLeftSweepDistance = 0f
                lowestXReached = rawX
                highestYReached = rawY
                lowestYReached = rawY
                aggregateScrubAccumulator = 0f
                isScrubEntranceHapticFired = false
                currentDetectedGesture = MacroGesture.NONE
                overScrollBoundaryAccumulator = 0f
                settingsCategoryAppended = false

                currentActiveZone = when {
                    centerTouchBounds.contains(x, y) -> TouchZone.CENTER_CRUISE
                    topTouchBounds.contains(x, y) -> TouchZone.TOP_EDGE
                    bottomTouchBounds.contains(x, y) -> TouchZone.BOTTOM_EDGE
                    else -> TouchZone.NONE
                }

                if (currentActiveZone == TouchZone.CENTER_CRUISE) {
                    startCruiseFromFlank(isLeft = false, startRawX = rawX, startRawY = rawY)
                } else if (currentActiveZone != TouchZone.NONE) {
                    macroTrackingActive = true
                    uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
                if (isCruising) {
                    val deltaX = if (isOpenedFromLeftFlank) (rawX - touchDownRawX) else (touchDownRawX - rawX)
                    val deltaY = abs(rawY - touchDownRawY)
                    if (deltaY > maxVerticalDisplacement) {
                        maxVerticalDisplacement = deltaY
                    }

                    // 1. Direct Lateral Swipe from rest -> Open Gears / Cockpit immediately
                    // Zero category flicker because NEUTRAL never painted categories on screen!
                    if ((currentLayer == CruiseLayer.NEUTRAL || currentLayer == CruiseLayer.CATEGORY) &&
                        !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX > (deltaY * 1.1f)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        val cPrefs = context.defaultPrefs()
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
                        evaluateSpatialMetrics(rawX, rawY, x, y)
                    }
                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                } else if (macroTrackingActive) {
                    val previousGesture = currentDetectedGesture
                    val deltaX = rawX - gestureStartX
                    val deltaY = rawY - gestureStartY

                    if (rawX < lowestXReached) lowestXReached = rawX
                    if (rawY > highestYReached) highestYReached = rawY
                    if (rawY < lowestYReached) lowestYReached = rawY

                    val screenW = resources.displayMetrics.widthPixels.toFloat()
                    val thresholdX_Scrub = screenW * 0.333f 
                    val thresholdY_Compound = 60f

                    if (currentDetectedGesture == MacroGesture.NONE) {
                        if (abs(deltaX) > (22f * density) && abs(deltaX) > abs(deltaY)) {
                            currentDetectedGesture = MacroGesture.SWIPE_LEFT
                            initialLeftSweepDistance = abs(deltaX)
                        } else if (deltaY < (-25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_UP
                        } else if (deltaY > (25f * density) && abs(deltaY) > abs(deltaX)) {
                            currentDetectedGesture = MacroGesture.SWIPE_DOWN
                        }
                    }

                    when (currentDetectedGesture) {
                        MacroGesture.SWIPE_LEFT -> {
                            val zoneName = if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
                            val dynamicZone = if (context.defaultPrefs().getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = context.defaultPrefs().getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                uiHandler.removeCallbacks(holdTimerRunnable)
                            } else if (deltaY < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_UP
                            } else if (deltaY > (25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_DOWN
                            } else {
                                val currentReturnRightDistance = rawX - lowestXReached
                                if (currentReturnRightDistance > (18f * density)) {
                                    currentDetectedGesture = MacroGesture.SWIPE_LEFT_BACK
                                }
                            }
                        }
                        MacroGesture.SWIPE_UP -> {
                            if ((rawY - lowestYReached) > (22f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_DOWN
                            } else if (deltaX < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_LEFT
                            }
                        }
                        MacroGesture.SWIPE_DOWN -> {
                            if ((highestYReached - rawY) > (22f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_UP
                            } else if (deltaX < (-25f * density)) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_LEFT
                            }
                        }
                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            val pixelDelta = if (currentActiveZone == TouchZone.TOP_EDGE) {
                                val dx = rawX - lastTouchRawX
                                val dy = rawY - lastTouchRawY
                                val dominant = if (abs(dx) >= abs(dy)) dx else dy
                                -dominant
                            } else {
                                rawY - lastTouchRawY
                            }
                            executeLinearScrubTrack(currentActiveZone, pixelDelta)
                        }
                        else -> {}
                    }

                    if (currentDetectedGesture != MacroGesture.SCRUBBING && !currentDetectedGesture.name.endsWith("_HOLD")) {
                        val moveDelta = hypot(rawX - lastTouchRawX, rawY - lastTouchRawY)
                        if (currentDetectedGesture != previousGesture || moveDelta > (3f * density)) {
                            resetHoldTimer()
                        }
                    }

                    lastTouchRawX = rawX
                    lastTouchRawY = rawY
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
                            
                            // Align hangar bay profile rail to center the active gear set
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
                        service?.updateWindowLayout(false) // CRITICAL DIRECTIVE: Disengage overlay window touch-trap immediately
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
                        dismissOverlay()
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
                } else if (macroTrackingActive) {
                    macroTrackingActive = false
                    if (currentDetectedGesture != MacroGesture.NONE && currentDetectedGesture != MacroGesture.SCRUBBING) {
                        executeMacroAction(currentActiveZone, currentDetectedGesture)
                    }
                }
                activeHoldScrubAction = null
                scrubHudTitle = ""
                scrubHudValue = ""
                invalidate()
                currentActiveZone = TouchZone.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun resetHoldTimer() {
        uiHandler.removeCallbacks(holdTimerRunnable)
        uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    private fun executeLinearScrubTrack(zone: TouchZone, pixelDelta: Float) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = context.defaultPrefs()
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
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (currentVol - steps).coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                scrubHudTitle = "MEDIA VOLUME"
                scrubHudValue = "$targetVol / $maxVol"
                invalidate()
            } else if (assignedScrub == "scrub:brightness" || assignedScrub == "system:brightness") {
                if (Settings.System.canWrite(context)) {
                    val currentBrightness = try {
                        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    } catch (_: Exception) { 128 }
                    val targetBrightness = (currentBrightness - (steps * 8)).coerceIn(10, 255)
                    try {
                        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                        scrubHudTitle = "BRIGHTNESS"
                        scrubHudValue = "${(targetBrightness * 100 / 255)}%"
                        invalidate()
                    } catch (e: Exception) {
                        Log.e("GestureEngine", "System write failure", e)
                    }
                }
            } else if (assignedScrub == "system:screen_timeout") {
                val curIdx = LightspeedTimeoutEngine.getCurrentTimeoutIndex(context)
                val targetIndex = (curIdx - steps).coerceIn(0, LightspeedTimeoutEngine.TIMEOUT_STEPS.lastIndex)
                val stepResult = LightspeedTimeoutEngine.setStepIndex(context, targetIndex)
                val label = stepResult.second
                if (scrubHudValue != label) {
                    scrubHudTitle = "SHIP GOES DARK IN"
                    scrubHudValue = label
                    triggerHardwareHaptic(22, 140)
                    invalidate()
                }
            }
        }
    }

    private fun executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = context.defaultPrefs()
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

    private fun loadActiveCategoryGrid() {
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

    private fun preloadActiveCategoryIcons() {
        try {
            applicationIconCache.clear()
            for (target in cachedApps) {
                if (!target.isWidget) {
                    val drawable = dataBridge.getIcon(target.packageName)
                    if (drawable != null) {
                        applicationIconCache[target.packageName] = drawable
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun evaluateSpatialMetrics(rawX: Float, rawY: Float, localX: Float, localY: Float) {
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

    private fun buildPackedGridLayout() {
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

    private fun querySystemColumnPreference(isLandscape: Boolean): Int {
        val prefs = context.defaultPrefs()
        return try { val v = prefs.all[if (isLandscape) "pref_numcolsland" else "pref_numcolspor"]; if (v is Int) v else v?.toString()?.toInt() ?: 4 } catch (e: Exception) { 4 }
    }

    private fun executeLaunch(target: LightspeedDataBridge.LaunchTarget) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(target.packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(launchIntent) } catch (e: Exception) {}
        }
    }

    private fun resolveCleanAppLabel(itemToken: String): String {
        return com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(context, itemToken)
    }

    private fun launchLauncherSettings() {
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

    private fun dismissOverlay() {
        longPressHangarBayRunnable?.let { removeCallbacks(it) }
        longPressCogRunnable?.let { removeCallbacks(it) }
        longPressHangarBayRunnable = null
        longPressCogRunnable = null
        hasLongPressFired = false

        snapAnimator?.cancel()
        isStickyPinned = false; isCruising = false; currentLayer = CruiseLayer.HIDDEN
        activeItem = null; activeCatIndex = -1; viewportScrollOffset = 0f; categoryVisualOffset = 0f
        placedAppsList.clear(); cachedApps = emptyList(); cachedCategories = emptyList()
        val cPrefs = context.defaultPrefs()
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
        categoryScrubbingEngaged = false
        maxVerticalDisplacement = 0f
        categoryVisualOffset = 0f
        entranceStartTime = System.currentTimeMillis()
        lastLoadedCategoryId = null
        snapAnimator?.cancel()
        categoryAppsCache.clear(); categoryGridCache.clear()

        val cPrefs = context.defaultPrefs()
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
                        val cPrefs = context.defaultPrefs()
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
                        dismissOverlay()
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
        val prefs = context.defaultPrefs()
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

    private fun getAppsForActiveGear(setIndex: Int, ringIndex: Int): List<String> {
        return com.sbf.lightspeed.system.CockpitGearRepository.getAppsForActiveGear(context, isOpenedFromLeftFlank, setIndex, ringIndex)
    }

    private fun getGearSetNameById(setId: String): String {
        return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameById(context, setId)
    }

    private fun getGearSetNameByIndex(index: Int): String {
        return com.sbf.lightspeed.system.CockpitGearRepository.getGearSetNameByIndex(context, isOpenedFromLeftFlank, index)
    }


    private fun drawSpaceshipGimbalRing(
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

    private fun drawFlightLockReticle(
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

    private fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) = deepSpaceRenderer.drawHolographicReactorCore(canvas, cx, cy, coreRadius, isActive, m3Primary, density)

    private fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) =
        deepSpaceRenderer.drawCosmicStarfield(canvas, w, h, density, alphaFactor)

    private fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) = deepSpaceRenderer.drawGalacticNebula(canvas, cx, cy, radius, m3Primary, alphaFactor)

    private fun triggerHyperdriveWarpLaunch(focalX: Float, focalY: Float, onLaunch: () -> Unit) {
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

    private fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) =
        deepSpaceRenderer.drawHyperdriveWarpSurge(canvas, m3Primary, density)


    private fun processGyroscopeTouchPhysics(rawX: Float, rawY: Float) {
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
        val prefs = context.defaultPrefs()
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
        if (activeGearSetIndex >= totalGearSetsCount) { activeGearSetIndex = 0 }
        val prefs = context.defaultPrefs()

        val m3Primary = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_600, context.theme)
        } else {
            Color.parseColor("#6750A4")
        }
        val m3Secondary = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            context.resources.getColor(android.R.color.system_accent1_300, context.theme)
        } else {
            Color.parseColor("#D0BCFF")
        }

        super.onDraw(canvas)
        if (currentLayer == CruiseLayer.COCKPIT_HANGAR) {
            val setsList = getGearSetsOrder(isOpenedFromLeftFlank)
            deepSpaceRenderer.drawDeepSpace(
                canvas, context, width.toFloat(), height.toFloat(), resources.displayMetrics.density,
                m3Primary, m3Secondary, isOpenedFromLeftFlank, activeGearSetIndex, activeHangarRing,
                isHangarEjectArmed, gearRingRotations, hangarBayScrollOffset, setsList,
                ::resolveCleanAppLabel, ::getAppsForActiveGear, ::getFlankLaunchBehavior
            )
            return
        }
        val w = width.toFloat(); val h = height.toFloat()
        if (currentLayer == CruiseLayer.HIDDEN) {
            val d = resources.displayMetrics.density

            val isRightFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
            val isRightUnifiedExpanded = prefs.getBoolean("pref_section_right_unified_expanded", false)
            val isTopExpanded = if (isRightFlankUnified) isRightUnifiedExpanded else prefs.getBoolean("pref_section_top_expanded", false)
            val isCenterExpanded = prefs.getBoolean("pref_section_center_expanded", false)
            val isBottomExpanded = if (isRightFlankUnified) isRightUnifiedExpanded else prefs.getBoolean("pref_section_bottom_expanded", false)
            val isSidebarPreview = prefs.getBoolean("pref_sidebar_preview", false)

            val centerTransparency = prefs.getInt("pref_sidebar_center_transparency", 0)
            val topTransparency = prefs.getInt("pref_sidebar_top_transparency", 0)
            val bottomTransparency = if (prefs.getBoolean("pref_sidebar_link_edges", false)) topTransparency else prefs.getInt("pref_sidebar_bottom_transparency", 0)

            fun drawWingBlade(bounds: RectF, color: Int, transparencyPct: Int, isExpanded: Boolean, targetZone: TouchZone) {
                val isReview = isExpanded && isSidebarPreview
                val effectivePct = if (isReview) 100 else transparencyPct
                if (effectivePct <= 0 && !isReview) return

                val alpha = (effectivePct * 2.55f).toInt().coerceIn(40, 255)

                if (isReview) {
                    // Active Review Mode: Full Zone Highlight + Crisp White Outline
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(120, Color.red(color), Color.green(color), Color.blue(color))
                    canvas.drawRoundRect(bounds, 6f * d, 6f * d, highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 2f * d
                    highlightPaint.color = Color.WHITE
                    canvas.drawRoundRect(bounds, 6f * d, 6f * d, highlightPaint)
                } else {
                    // Resting Mode & Touch Glow
                    val isTouched = isCurrentlyTouched && currentActiveZone == targetZone
                    val isCenter = targetZone == TouchZone.CENTER_CRUISE
                    val baseW = minOf(bounds.width(), 6f * d)
                    val bladeW = if (isTouched) (if (isCenter) 14f * d else 10f * d) else baseW
                    val finalAlpha = if (isTouched) 255 else alpha

                    val bladeRect = RectF(bounds.right - bladeW, bounds.top + 2f * d, bounds.right, bounds.bottom - 2f * d)

                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(finalAlpha, Color.red(color), Color.green(color), Color.blue(color))
                    canvas.drawRoundRect(bladeRect, 3f * d, 3f * d, highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1.2f * d
                    highlightPaint.color = Color.argb((finalAlpha * 0.9f).toInt(), 255, 255, 255)
                    canvas.drawLine(bladeRect.left, bladeRect.top + 4f * d, bladeRect.left, bladeRect.bottom - 4f * d, highlightPaint)
                }
            }

            val upperColor = Color.rgb(68, 138, 255)
            val coreColor = m3Primary
            val lowerColor = Color.rgb(255, 171, 0)

            drawWingBlade(topTouchBounds, upperColor, topTransparency, isTopExpanded, TouchZone.TOP_EDGE)
            drawWingBlade(centerTouchBounds, coreColor, centerTransparency, isCenterExpanded, TouchZone.CENTER_CRUISE)
            drawWingBlade(bottomTouchBounds, lowerColor, bottomTransparency, isBottomExpanded, TouchZone.BOTTOM_EDGE)
            return
        }

        if (currentLayer == CruiseLayer.NEUTRAL) {
            // Keep background neutral during intent decision gate (eliminates category ghost frames before Gears)
            return
        }

        if (currentLayer == CruiseLayer.FAVORITES_GEARS) {
            val density = resources.displayMetrics.density
            val cx = width / 2f
            val cy = height / 2f
            
            // 1. Deep Space Astrogation Backdrop with subtle radial vignette
            val spaceGrad = android.graphics.RadialGradient(
                cx, cy, (width.toFloat().coerceAtLeast(height.toFloat()) * 0.75f),
                intArrayOf(Color.argb(210, 10, 14, 22), Color.argb(245, 4, 6, 10)),
                floatArrayOf(0.0f, 1.0f),
                android.graphics.Shader.TileMode.CLAMP
            )
            elementPaint.style = Paint.Style.FILL
            elementPaint.shader = spaceGrad
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), elementPaint)
            elementPaint.shader = null

            // 2. Flight Telemetry Grid & Attitude Axis Crosshairs
            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = 1f * density
            elementPaint.color = Color.argb(24, 200, 220, 255)
            canvas.drawLine(cx - (380f * density / 2.6f), cy, cx + (380f * density / 2.6f), cy, elementPaint)
            canvas.drawLine(cx, cy - (380f * density / 2.6f), cx, cy + (380f * density / 2.6f), elementPaint)
            
            // Scaled Radii for Outer, Inner, and Core Hub
            val rad0 = 310f * (density / 2.6f).coerceAtLeast(0.9f)
            val rad1 = 190f * (density / 2.6f).coerceAtLeast(0.9f)
            val radHub = 64f * (density / 2.6f).coerceAtLeast(0.9f)
            
            // Draw Outer Orbital Gimbal Ring (Ring 0, 16 Magnetic Cogs)
            drawSpaceshipGimbalRing(
                canvas = canvas,
                cx = cx,
                cy = cy,
                trackRadius = rad0,
                trackWidth = 48f * density / 2.6f,
                teethCount = 16,
                toothDepth = 16f * density / 2.6f,
                rotationDeg = gearRingRotations[0],
                isActive = (activeGearRing == 0),
                m3Primary = m3Primary
            )

            // Draw Inner Orbital Gimbal Ring (Ring 1, 12 Magnetic Cogs)
            drawSpaceshipGimbalRing(
                canvas = canvas,
                cx = cx,
                cy = cy,
                trackRadius = rad1,
                trackWidth = 42f * density / 2.6f,
                teethCount = 12,
                toothDepth = 14f * density / 2.6f,
                rotationDeg = gearRingRotations[1],
                isActive = (activeGearRing == 1),
                m3Primary = m3Primary
            )

            // Draw Central Holographic Warp Core / Cockpit Command Node (Ring 2)
            drawHolographicReactorCore(
                canvas = canvas,
                cx = cx,
                cy = cy,
                coreRadius = radHub,
                isActive = (activeGearRing == 2),
                m3Primary = m3Primary,
                density = density
            )

            // --- ORBITAL APP FLIGHT PODS & ICONS PASS ---
            val pm = context.packageManager
            val sizeRaw = (42f * density).toInt()

            var activeHighlightedCX = 0f
            var activeHighlightedCY = 0f
            var activeHighlightedBracketSize = 0f
            var activeHighlightedLabel: String? = null

            for (r in 0..1) {
                val radius = if (r == 0) rad0 else rad1
                val ringApps = getAppsForActiveGear(activeGearSetIndex, r)
                if (ringApps.isEmpty()) continue
                
                val count = ringApps.size
                val baseRotation = gearRingRotations[r]
                val isRingFocused = (activeGearRing == r)
                
                for (i in ringApps.indices) {
                    val angleDeg = (baseRotation + i * (360f / count)) % 360f
                    val angleRad = Math.toRadians(angleDeg.toDouble())
                    
                    // Core Polar-to-Cartesian projection mapping coordinates
                    val iconCX = cx + radius * Math.cos(angleRad).toFloat()
                    val iconCY = cy + radius * Math.sin(angleRad).toFloat()
                    
                    // Dynamic Focus Interpolation: Target angle is 180 deg (straight left, pointing to center)
                    val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
                    val isHighlighted = isRingFocused && abs(normalizedDeg - 180f) < (180f / count)
                    val currentScale = if (isHighlighted) 1.28f else 1.0f
                    val currentSize = (sizeRaw * currentScale).toInt()
                    
                    val itemToken = ringApps[i]
                    val appLabel = resolveCleanAppLabel(itemToken)

                    if (isHighlighted) {
                        activeHighlightedCX = iconCX
                        activeHighlightedCY = iconCY
                        activeHighlightedBracketSize = currentSize + 22f * density
                        activeHighlightedLabel = appLabel
                    }

                    // 1. Orbital Flight Pod Socket Behind Icon
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = if (isHighlighted) Color.argb(200, 22, 28, 44) else Color.argb(90, 16, 20, 30)
                    canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = if (isHighlighted) 2.4f * density else 1.2f * density
                    highlightPaint.color = if (isHighlighted) m3Primary else Color.argb(55, 200, 220, 255)
                    canvas.drawCircle(iconCX, iconCY, (currentSize / 2f) + (6f * density), highlightPaint)

                    // 2. Draw Icon Drawable or Action Glyph
                    try {
                        val iconDrawable = com.sbf.lightspeed.system.LightspeedIconManager.getIconDrawable(context, itemToken)
                        if (iconDrawable != null) {
                            iconDrawable.alpha = if (isRingFocused) (if (isHighlighted) 255 else 210) else 110
                            iconDrawable.setBounds(
                                (iconCX - currentSize / 2).toInt(),
                                (iconCY - currentSize / 2).toInt(),
                                (iconCX + currentSize / 2).toInt(),
                                (iconCY + currentSize / 2).toInt()
                            )
                            iconDrawable.draw(canvas)
                        } else {
                            elementPaint.style = Paint.Style.FILL
                            elementPaint.color = if (isHighlighted) Color.WHITE else Color.GRAY
                            canvas.drawCircle(iconCX, iconCY, currentSize / 3f, elementPaint)
                        }
                    } catch (e: Exception) {
                        elementPaint.style = Paint.Style.FILL
                        elementPaint.color = if (isHighlighted) Color.WHITE else Color.GRAY
                        canvas.drawCircle(iconCX, iconCY, currentSize / 3f, elementPaint)
                    }
                }
            }

            // 3. Draw Flight Lock Targeting Reticle & Holographic Badge AFTER all rings & pods are drawn (Top of Z-Stack)
            if (activeHighlightedLabel != null) {
                drawFlightLockReticle(
                    canvas = canvas,
                    targetCX = activeHighlightedCX,
                    targetCY = activeHighlightedCY,
                    bracketSize = activeHighlightedBracketSize,
                    m3Primary = m3Primary,
                    m3Secondary = m3Secondary,
                    appName = activeHighlightedLabel,
                    density = density,
                    reticleStyle = prefs.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
                )
            }
            
            // --- TOP FLIGHT TELEMETRY HUD HEADER (PROFILE HUD) ---
            val topBadgeY = 54f * density
            val profileName = when(activeGearSetIndex) {
                0 -> getGearSetNameByIndex(0)
                1 -> getGearSetNameByIndex(1)
                2 -> getGearSetNameByIndex(2)
                else -> getGearSetNameByIndex(3)
            }
            
            val headerText = "◈ ASTROGATION // PROFILE $activeGearSetIndex: $profileName ◈"
            textPaint.textSize = 12f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            val headerWidth = textPaint.measureText(headerText)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(175, 12, 16, 28)
            val headerRect = RectF(cx - headerWidth / 2f - 16f * density, topBadgeY - 15f * density, cx + headerWidth / 2f + 16f * density, topBadgeY + 15f * density)
            canvas.drawRoundRect(headerRect, 10f * density, 10f * density, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1.2f * density
            highlightPaint.color = Color.argb(80, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(headerRect, 10f * density, 10f * density, highlightPaint)

            textPaint.color = Color.WHITE
            canvas.drawText(headerText, cx, topBadgeY + 4f * density, textPaint)

            // Subtitle Guidance Hint (cleanly separated with zero gear collision)
            textPaint.textSize = 8.5f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.color = Color.argb(160, 180, 210, 245)
            canvas.drawText(if (isOpenedFromLeftFlank) "PULL RIGHT >> SWITCH PROFILE  •  SLIDE LEFT << CANCEL" else "PULL LEFT >> SWITCH PROFILE  •  SLIDE RIGHT << CANCEL", cx, topBadgeY + 28f * density, textPaint)
            return
        }

        if (currentLayer == CruiseLayer.CATEGORY) {
            val density = resources.displayMetrics.density
            val decelerationFactor = sin(((System.currentTimeMillis() - entranceStartTime) / 220f).coerceIn(0f, 1f) * PI / 2.0).toFloat()
            
            // 1. Deep Space Astrogation Void Backdrop
            canvas.drawColor(Color.argb((205 * decelerationFactor).toInt(), 4, 6, 12))
            
            // 2. Cosmic Stardust Particle Field
            drawCosmicStarfield(canvas, w, h, density, decelerationFactor)

            // 3. Top Flight Telemetry Header (Sector Cruise HUD with Auto-Fit)
            val topBadgeY = 54f * density
            val currentSector = (activeCatIndex + 1).toString().padStart(2, '0')
            val totalSectors = cachedCategories.size.toString().padStart(2, '0')
            val activeCatName = cachedCategories.getOrNull(activeCatIndex)?.label?.uppercase() ?: "CRUISE"
            val hudHeader = "◈ SECTOR $currentSector / $totalSectors  ✦  $activeCatName ◈"

            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            var hudTextSize = 10.5f * density
            textPaint.textSize = hudTextSize
            val maxHudW = w * 0.85f
            while (textPaint.measureText(hudHeader) > maxHudW && hudTextSize > 7.5f * density) {
                hudTextSize -= 0.5f * density
                textPaint.textSize = hudTextSize
            }
            val hudWidth = textPaint.measureText(hudHeader)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb((150 * decelerationFactor).toInt(), 10, 14, 26)
            val headerRect = RectF(w / 2f - hudWidth / 2f - 14f * density, topBadgeY - 13f * density, w / 2f + hudWidth / 2f + 14f * density, topBadgeY + 13f * density)
            canvas.drawRoundRect(headerRect, 9f * density, 9f * density, highlightPaint)

            textPaint.color = Color.WHITE
            textPaint.alpha = (255 * decelerationFactor).toInt()
            canvas.drawText(hudHeader, w / 2f, topBadgeY + 3.5f * density, textPaint)

            // Subtitle Guidance Hint for Category Cruise
            textPaint.textSize = 8.5f * density
            textPaint.typeface = android.graphics.Typeface.DEFAULT
            textPaint.color = Color.argb((160 * decelerationFactor).toInt(), 180, 210, 245)
            canvas.drawText(if (isOpenedFromLeftFlank) "PULL RIGHT >> ENTER STAR SYSTEM  •  SLIDE LEFT << CANCEL" else "PULL LEFT >> ENTER STAR SYSTEM  •  SLIDE RIGHT << CANCEL", w / 2f, topBadgeY + 26f * density, textPaint)

            // 4. Galactic Horizon: Cruising Through Nebulae & Star Systems
            if (cachedCategories.isNotEmpty()) {
                val startYArea = h * 0.22f; val endYArea = h * 0.78f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    // Symmetrical anchor based on flank origin
                    val targetTextX = if (isOpenedFromLeftFlank) {
                        (28f * density) + (cos(sweepAngleRad).toFloat() * 160f * density)
                    } else {
                        w - (28f * density) - (cos(sweepAngleRad).toFloat() * 160f * density)
                    }
                    val distanceRatio = (abs(relativeDistanceFromCenter) / ((endYArea - startYArea) / 2f)).coerceIn(0f, 1f)
                    val zoom = 0.70f + Math.pow(1.0 - distanceRatio, 2.5).toFloat() * 1.35f

                    // If active category, draw its luminous chromatic Nebula Cloud behind it
                    if (idx == activeCatIndex) {
                        drawGalacticNebula(
                            canvas = canvas,
                            cx = if (isOpenedFromLeftFlank) targetTextX + (100f * density) else targetTextX - (100f * density),
                            cy = centerY,
                            radius = 185f * density,
                            m3Primary = m3Primary,
                            alphaFactor = decelerationFactor
                        )
                    }

                    canvas.save()
                    projectionCamera3D.save()
                    projectionCamera3D.setLocation(0f, 0f, -8f); projectionCamera3D.translate(0f, 0f, cylinderRadius)
                    projectionCamera3D.rotateX(-Math.toDegrees(sweepAngleRad.toDouble()).toFloat()); projectionCamera3D.translate(0f, 0f, -cylinderRadius)
                    projectionCamera3D.getMatrix(transformMatrixPipeline); projectionCamera3D.restore()
                    transformMatrixPipeline.preTranslate(-targetTextX, -centerY)
                    transformMatrixPipeline.postScale(zoom, zoom); transformMatrixPipeline.postTranslate(targetTextX, centerY)
                    canvas.concat(transformMatrixPipeline)

                    val labelText = cat.label.uppercase()
                    val focusFactor = Math.pow((1.0 - distanceRatio).coerceIn(0.0, 1.0), 3.0).toFloat() * decelerationFactor
                    
                    // 1. Stable, constant category font size based on text length (Zero per-frame shivering)
                    val baseTargetSize = when {
                        labelText.length <= 8 -> 18.5f * density
                        labelText.length <= 13 -> 14.5f * density
                        labelText.length <= 18 -> 12f * density
                        else -> 10.5f * density
                    }

                    // 2. Smoothly interpolate font size, alpha, and color with distance from center
                    val currentTextSize = 13f * density + (baseTargetSize - 13f * density) * focusFactor
                    catTextPaint.textSize = currentTextSize
                    catTextPaint.textAlign = if (isOpenedFromLeftFlank) Paint.Align.LEFT else Paint.Align.RIGHT
                    catTextPaint.typeface = if (focusFactor > 0.6f) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT

                    // Color interpolation: Dim starlight (#9EA7B8) to blazing white
                    val rCol = (158 + (255 - 158) * focusFactor).toInt()
                    val gCol = (167 + (255 - 167) * focusFactor).toInt()
                    val bCol = (184 + (255 - 184) * focusFactor).toInt()
                    val alpha = ((35 + 220 * focusFactor) * decelerationFactor).toInt().coerceIn(0, 255)
                    catTextPaint.color = Color.argb(alpha, rCol, gCol, bCol)

                    // Draw smoothly gliding Category Star System Typography
                    canvas.drawText(labelText, targetTextX, centerY + (5f + 1f * focusFactor) * density, catTextPaint)

                    // 3. Glowing Celestial Star Beacon (Fades in smoothly as category enters focus)
                    if (focusFactor > 0.25f) {
                        val measuredLabelW = catTextPaint.measureText(labelText)
                        val beaconAlpha = ((focusFactor - 0.25f) / 0.75f).coerceIn(0f, 1f)
                        textPaint.textAlign = if (isOpenedFromLeftFlank) Paint.Align.LEFT else Paint.Align.RIGHT
                        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textPaint.textSize = (currentTextSize * 0.75f).coerceAtLeast(8.5f * density)
                        textPaint.color = m3Primary
                        textPaint.alpha = (255 * beaconAlpha * decelerationFactor).toInt()
                        val beaconX = if (isOpenedFromLeftFlank) targetTextX + measuredLabelW + 6f * density else targetTextX - measuredLabelW - 6f * density
                        canvas.drawText("✦", beaconX, centerY + 5.5f * density, textPaint)
                    }
                    canvas.restore()
                }
            }
            if (decelerationFactor < 1f) postInvalidateOnAnimation()
        } else if (currentLayer == CruiseLayer.GRID || isStickyPinned || currentLayer == CruiseLayer.STICKY_PIN) {
            val density = resources.displayMetrics.density
            canvas.drawColor(Color.argb(195, 4, 6, 12))
            drawCosmicStarfield(canvas, w, h, density, 0.7f)

            textPaint.textAlign = Paint.Align.CENTER
            canvas.save(); canvas.clipRect(0f, 0f, w, h)
            canvas.translate(0f, -(if (totalGridContentHeight <= (h * 0.79f)) 0f else viewportScrollOffset))
            for (item in placedAppsList) {
                val b = item.bounds; val isSel = activeItem == item.app
                if (isSel) {
                    // Starlight Pod Active Docking Ring
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 24, 32, 52)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)
                    
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 2.2f * density
                    highlightPaint.color = m3Primary
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 18f, 18f, highlightPaint)
                    
                    textPaint.color = Color.WHITE
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                } else {
                    elementPaint.style = Paint.Style.FILL
                    elementPaint.color = Color.argb(if (item.app.isWidget) 30 else 18, 200, 220, 255)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)
                    
                    elementPaint.style = Paint.Style.STROKE
                    elementPaint.strokeWidth = 1f * density
                    elementPaint.color = Color.argb(35, 200, 220, 255)
                    canvas.drawRoundRect(b.left + 4f, b.top + 4f, b.right - 4f, b.bottom - 4f, 16f, 16f, elementPaint)
                    
                    textPaint.color = Color.parseColor("#E0E5F0")
                    textPaint.typeface = android.graphics.Typeface.DEFAULT
                }
                if (item.app.isWidget) {
                    canvas.drawText(if (item.app.label.length > 16) item.app.label.take(14) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
                } else {
                    val icon = applicationIconCache[item.app.packageName]
                    if (icon != null) {
                        val sz = minOf(b.width() * 0.48f, b.height() * 0.48f)
                        icon.setBounds((b.centerX() - sz / 2f).toInt(), (b.centerY() - sz * 0.62f).toInt(), (b.centerX() + sz / 2f).toInt(), (b.centerY() - sz * 0.62f + sz).toInt())
                        icon.draw(canvas)
                        canvas.drawText(if (item.app.label.length > 12) item.app.label.take(10) + ".." else item.app.label, b.centerX(), b.centerY() + (sz * 0.55f) + 14f, textPaint)
                    } else {
                        canvas.drawText(if (item.app.label.length > 14) item.app.label.take(12) + ".." else item.app.label, b.centerX(), b.centerY() + 8f, textPaint)
                    }
                }
            }
            canvas.restore()
        }

        drawHyperdriveWarpSurge(canvas, m3Primary, resources.displayMetrics.density)

        if (currentDetectedGesture == MacroGesture.SCRUBBING && scrubHudTitle.isNotEmpty()) {
            val d = resources.displayMetrics.density
            val screenW = resources.displayMetrics.widthPixels.toFloat()
            val cx = screenW / 2f
            val cy = h / 2f

            val prefs = context.defaultPrefs()
            val isFlankUnified = prefs.getBoolean("pref_sidebar_right_link_flank_actions", false)
            val dynamicZone = if (isFlankUnified) "UNIFIED" else (if (currentActiveZone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM")
            val hudStyle = prefs.getString("pref_macro_hud_style_${dynamicZone}_SCRUBBING", null)
                ?: prefs.getString("pref_macro_hud_style_default", "cockpit_reticle") ?: "cockpit_reticle"

            val totalSteps = if (activeHoldScrubAction == "system:screen_timeout" || scrubHudTitle == "SHIP GOES DARK IN") LightspeedTimeoutEngine.TIMEOUT_STEPS.size else 0
            val stepIdx = if (totalSteps > 0) LightspeedTimeoutEngine.getCurrentTimeoutIndex(context) else -1

            val primaryAccent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.resources.getColor(android.R.color.system_accent1_300, context.theme)
            } else m3Primary

            LightspeedHudRenderer.renderHud(
                canvas = canvas,
                style = hudStyle,
                title = scrubHudTitle,
                value = scrubHudValue,
                stepIndex = stepIdx,
                totalSteps = totalSteps,
                centerX = cx,
                centerY = cy,
                topY = 70f * d,
                primaryColor = primaryAccent,
                density = d,
                isLeftFlank = false
            )
        }
    }

    companion object {
        private val agslSource = """
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
