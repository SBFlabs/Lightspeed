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
import com.sbf.lightspeed.defaultPrefs
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ElevatedTaskCloser
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
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
            val count = setsString.split(",").filter { it.isNotEmpty() }.size
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
    private var initialLeftSweepDistance = 0f
    private var lowestXReached = 0f
    private var highestYReached = 0f
    private var lowestYReached = 0f
    
    private var aggregateScrubAccumulator = 0f
    private var isScrubEntranceHapticFired = false

    private fun triggerHardwareHaptic(durationMs: Long, amplitude: Int) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createOneShot(durationMs, amplitude))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(durationMs)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("GestureEngine", "Hardware vibration failed", e)
        }
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

    private val holdTimerRunnable = Runnable {
        if (macroTrackingActive) {
            val hostGesture = currentDetectedGesture
            val holdEquivalent = when(hostGesture) {
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
            if (holdEquivalent != hostGesture) {
                currentDetectedGesture = holdEquivalent
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
        if (key != null && key.startsWith("pref_sidebar_")) {
            post { updateMetricsDimensions() }
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
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
        updateMetricsDimensions()
    }

    override fun onDetachedFromWindow() {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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

        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        
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

            val winHeight = (bottomLimit - topLimit).toInt()
            val winWidth = maxOf(centerTouchWidthPx, topTouchWidthPx, bottomTouchWidthPx).toInt()
            val w = winWidth.toFloat()
            
            topTouchBounds.set(w - topTouchWidthPx, 0f, w, topHeightPx)
            centerTouchBounds.set(w - centerTouchWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomTouchBounds.set(w - bottomTouchWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            topVisualBounds.set(w - topVisualWidthPx, 0f, w, topHeightPx)
            centerVisualBounds.set(w - centerVisualWidthPx, topHeightPx, w, topHeightPx + centerHeightPx)
            bottomVisualBounds.set(w - bottomVisualWidthPx, topHeightPx + centerHeightPx, w, topHeightPx + centerHeightPx + bottomHeightPx)

            launchpadPillBounds.set(centerVisualBounds)

            if (lp.height != winHeight || lp.width != winWidth || lp.y != topLimit.toInt() || lp.gravity != (Gravity.TOP or Gravity.END)) {
                lp.gravity = Gravity.TOP or Gravity.END
                lp.x = 0; lp.y = topLimit.toInt()
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
                val agslSource = """
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
                val progressiveShader = RuntimeShader(agslSource).apply {
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

            val topHangarY = (screenH * 0.08f).coerceAtLeast(64f * d)
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
            val setsList = setsString.split(",").filter { it.isNotEmpty() }.toMutableList()
            if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
            val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"

            val r1Y = topHangarY + 46f * d
            val r2Y = r1Y + 46f * d
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
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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
                        // 1. Startup Default Mode: Always First vs Resume Last
                        val halfBtnW = (deckW - gap) / 2f
                        val btn1Rect = RectF(leftX, r1Y - 18f * d, leftX + halfBtnW, r1Y + 18f * d)
                        val btn2Rect = RectF(rightX - halfBtnW, r1Y - 18f * d, rightX, r1Y + 18f * d)
                        if (btn1Rect.contains(x, y)) {
                            prefs.edit().putString("cockpit_launch_behavior", "default").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }
                        if (btn2Rect.contains(x, y)) {
                            prefs.edit().putString("cockpit_launch_behavior", "last").apply()
                            triggerHardwareHaptic(20, 120)
                            invalidate()
                            return true
                        }

                        // 3. Reorder & Delete Active Profile in Rotation
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
                            prefs.edit().putString("gear_sets_order", setsList.joinToString(",")).apply()
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
                            prefs.edit().putString("gear_sets_order", setsList.joinToString(",")).apply()
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
                                prefs.edit().putString("gear_sets_order", setsList.joinToString(","))
                                    .remove("gear_set_${removedId}_ring0")
                                    .remove("gear_set_${removedId}_ring1")
                                    .remove("gear_set_${removedId}_name")
                                    .apply()
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
                            val intent = android.content.Intent(context, GearPickerActivity::class.java).apply {
                                putExtra("SET_ID", currentSetId)
                                putExtra("RING_INDEX", activeHangarRing)
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                            dismissOverlay()
                            return true
                        }

                        // 2. Focused Cog or Target Badge / Tag -> Long Press to Edit, Tap to Eject
                        // 2. Focused Cog or Target Badge -> Long Press to Edit
                        val isTouchingReticleOrBadge = (distFromReticle <= 30f * d) || targetBadgeRect.contains(x, y)

                        if (isTouchingReticleOrBadge && ringApps.isNotEmpty()) {
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
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    dismissOverlay()
                                    context.startActivity(intent)
                                }
                                longPressCogRunnable = runnable
                                postDelayed(runnable, 400)
                            }
                            invalidate()
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
                    if (distMoved > 8f * d) {
                        longPressHangarBayRunnable?.let { removeCallbacks(it) }
                        longPressCogRunnable?.let { removeCallbacks(it) }
                        longPressHangarBayRunnable = null
                        longPressCogRunnable = null
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
                        isSpinningHangarRing = false
                        isDraggingHangarBays = false
                        return true
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
                                    if (prefs.getString("cockpit_launch_behavior", "default") == "last") {
                                        prefs.edit().putInt("last_active_set_index", gIndex).apply()
                                    }
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
                                prefs.edit().putString("gear_sets_order", setsList.joinToString(",")).putString("gear_set_${newId}_name", "SET ${setsList.size}").apply()
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
                    } else if (distMoved < 14f * d) {
                        val reticleX = cx - (if (activeHangarRing == 0) radOuter else radInner)
                        val reticleY = cyGimbal
                        val distFromReticle = kotlin.math.hypot(x - reticleX, y - reticleY)
                        val targetBadgeRect = RectF(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)
                        val isTouchingReticleOrBadge = (distFromReticle <= 30f * d) || targetBadgeRect.contains(x, y)

                        val ringApps = getAppsForActiveGear(activeGearSetIndex, activeHangarRing).toMutableList()
                        if (isTouchingReticleOrBadge && ringApps.isNotEmpty()) {
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
                                // ARM EJECT: Show red containment and white X on the tapped cog
                                isHangarEjectArmed = true
                                hangarEjectTargetIndex = targetedIdx
                                hangarEjectRing = activeHangarRing
                                triggerHardwareHaptic(30, 180)
                                invalidate()
                                return true
                            }
                        }
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
                MotionEvent.ACTION_CANCEL -> { activeItem = null; invalidate(); return true }
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
                    isCruising = true
                    categoryScrubbingEngaged = false
                    maxVerticalDisplacement = 0f
                    categoryVisualOffset = 0f
                    entranceStartTime = System.currentTimeMillis()
                    lastLoadedCategoryId = null
                    snapAnimator?.cancel()
                    categoryAppsCache.clear(); categoryGridCache.clear()
                    val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                    if (cPrefs.getString("cockpit_launch_behavior", "default") == "last") {
                        activeGearSetIndex = cPrefs.getInt("last_active_set_index", 0)
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
                        val normY = ((y - startYArea) / usableHeight).coerceIn(0f, 1f)
                        initialCatIndex = floor(normY * cachedCategories.size).toInt().coerceIn(0, cachedCategories.size - 1)
                        activeCatIndex = initialCatIndex
                        val catLineH = usableHeight / cachedCategories.size
                        categoryVisualOffset = (hF / 2f) - (startYArea + (activeCatIndex * catLineH) + (catLineH / 2f))
                    }
                    uiHandler.removeCallbacks(neutralToCategoryRunnable)
                    uiHandler.postDelayed(neutralToCategoryRunnable, 140L)
                    invalidate()
                } else if (currentActiveZone != TouchZone.NONE) {
                    macroTrackingActive = true
                    uiHandler.postDelayed(holdTimerRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val density = resources.displayMetrics.density // Convert hardcoded pixels to device-agnostic DP
                if (isCruising) {
                    val deltaX = touchDownRawX - rawX
                    val deltaY = abs(rawY - touchDownRawY)
                    if (deltaY > maxVerticalDisplacement) {
                        maxVerticalDisplacement = deltaY
                    }

                    // 1. Direct Lateral Swipe from rest -> Open Gears / Cockpit immediately
                    // Zero category flicker because NEUTRAL never painted categories on screen!
                    if ((currentLayer == CruiseLayer.NEUTRAL || currentLayer == CruiseLayer.CATEGORY) &&
                        !categoryScrubbingEngaged && deltaX > (14f * density) && deltaX > (deltaY * 1.1f)) {
                        uiHandler.removeCallbacks(neutralToCategoryRunnable)
                        val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                        if (cPrefs.getString("cockpit_launch_behavior", "default") == "last") {
                            activeGearSetIndex = cPrefs.getInt("last_active_set_index", 0)
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
                            val dynamicZone = if (context.getSharedPreferences("default", Context.MODE_PRIVATE).getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
                            val assignedScrub = context.getSharedPreferences("default", Context.MODE_PRIVATE).getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none")

                            if (assignedScrub != "none" && assignedScrub != null && abs(deltaX) > thresholdX_Scrub) {
                                currentDetectedGesture = MacroGesture.SCRUBBING
                                uiHandler.removeCallbacks(holdTimerRunnable)
                            } else if (deltaY < -thresholdY_Compound) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_UP
                            } else if (deltaY > thresholdY_Compound) {
                                currentDetectedGesture = MacroGesture.SWIPE_LEFT_DOWN
                            } else {
                                val currentReturnRightDistance = rawX - lowestXReached
                                if (currentReturnRightDistance > (30f * density)) {
                                    currentDetectedGesture = MacroGesture.SWIPE_LEFT_BACK
                                }
                            }
                        }
                        MacroGesture.SWIPE_UP -> {
                            if ((rawY - lowestYReached) > 60f) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_DOWN
                            } else if (deltaX < -50f) {
                                currentDetectedGesture = MacroGesture.SWIPE_UP_LEFT
                            }
                        }
                        MacroGesture.SWIPE_DOWN -> {
                            if ((highestYReached - rawY) > 60f) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_UP
                            } else if (deltaX < -50f) {
                                currentDetectedGesture = MacroGesture.SWIPE_DOWN_LEFT
                            }
                        }
                        MacroGesture.SCRUBBING -> {
                            uiHandler.removeCallbacks(holdTimerRunnable) // PORTAL LINE LOCK: Suppress any hold actions immediately
                            if (!isScrubEntranceHapticFired) {
                                triggerHardwareHaptic(65, 255) // Chunky high-inertia hardware pop (50ms)
                                isScrubEntranceHapticFired = true
                            }
                            val pixelYDelta = rawY - lastTouchRawY
                            executeLinearScrubTrack(currentActiveZone, pixelYDelta)
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
                            
                            // Find which app icon rotated closest to the selection zone (180 degrees / straight left)
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
                            val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                            cPrefs.edit().putInt("last_active_set_index", activeGearSetIndex).apply()
                            
                            // Align hangar bay profile rail to center the active gear set
                            val setsString = cPrefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
                            val setsList = setsString.split(",").filter { it.isNotEmpty() }
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
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val dynamicZone = if (prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
        val assignedScrub = prefs.getString("pref_macro_action_${dynamicZone}_SCRUBBING", "none") ?: "none"

        if (assignedScrub == "none") return

        aggregateScrubAccumulator += pixelDelta
        val sensitivityThreshold = 35f

        if (abs(aggregateScrubAccumulator) >= sensitivityThreshold) {
            val steps = (aggregateScrubAccumulator / sensitivityThreshold).toInt()
            aggregateScrubAccumulator %= sensitivityThreshold
            if (steps != 0) {
                triggerHardwareHaptic(20, 120) // Noticeable micro-tick (25ms) that vibrates even during slow pulls
            }

            if (assignedScrub == "scrub:volume" || assignedScrub == "system:volume") {
                val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (currentVol - steps).coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
            } else if (assignedScrub == "scrub:brightness" || assignedScrub == "system:brightness") {
                if (!Settings.System.canWrite(context)) {
                    val currentTimestamp = System.currentTimeMillis()
                    if (currentTimestamp - lastPermissionToastTime > 5000) {
                        lastPermissionToastTime = currentTimestamp
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "LaunchTime requires Write Settings permission for brightness control", Toast.LENGTH_LONG).show()
                            val grantIntent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                                data = Uri.parse("package:" + context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(grantIntent)
                        }
                    }
                    return
                }
                try {
                    val currentBrightness = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                    val targetBrightness = (currentBrightness - (steps * 4)).coerceIn(0, 255)
                    Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, targetBrightness)
                } catch (e: Exception) {
                    Log.e("GestureEngine", "System write failure", e)
                }
            }
        }
    }

    private fun executeMacroAction(zone: TouchZone, gesture: MacroGesture) {
        val zoneName = if (zone == TouchZone.TOP_EDGE) "TOP" else "BOTTOM"
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val dynamicZone = if (prefs.getBoolean("pref_sidebar_link_gestures", false)) "TOP" else zoneName
        val actionKey = "pref_macro_action_${dynamicZone}_${gesture.name}"
        val actionValue = prefs.getString(actionKey, "none") ?: "none"

        Log.d("GestureEngine", "Target Vector: [$dynamicZone] -> Action Value: $actionValue")
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
        depthPercentage = ((wF - localX) / wF).coerceIn(0f, 1f)
        val verticalComfortScope = hF * 0.40f
        val verticalStartAnchor = hF * 0.30f
        val normalizedVerticalProgress = ((localY - verticalStartAnchor) / verticalComfortScope).coerceIn(0f, 1f)

        if (currentLayer == CruiseLayer.CATEGORY) {
            activeItem = null; viewportScrollOffset = 0f
            val horizontalPull = touchDownRawX - rawX
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
            val horizontalPull = touchDownRawX - rawX
            // In GRID mode, allow navigating all the way to the rightmost column without prematurely kicking back to CATEGORY.
            // Only exit back to CATEGORY if the thumb slides all the way back to the physical screen edge (< 6dp pull).
            val nextLayerState = when {
                horizontalPull <= (6f * density) -> CruiseLayer.CATEGORY
                (wF - localX) <= (wF * 0.42f) -> CruiseLayer.GRID
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

            // Map cursor progress so that the rightmost column is reached cleanly with zero edge collision
            val pullDistance = (wF - localX).coerceAtLeast(0f)
            val cursorProgress = ((pullDistance - (10f * density)) / (wF * 0.28f)).coerceIn(0f, 1f)
            virtualCursorX = (wF * 0.94f) - (cursorProgress * (wF * 0.88f))

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
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(settingsIntent)
        } catch (_: Exception) {
            val alternativeIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
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
        val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        if (cPrefs.getString("cockpit_launch_behavior", "default") != "last") {
            activeGearSetIndex = 0
        }
        service?.updateWindowLayout(false)
        updateMetricsDimensions()
        invalidate()
    }

    fun openHangarDirectly(setIndex: Int = -1) {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setsList = setsString.split(",").filter { it.isNotEmpty() }
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

    private fun getAppsForActiveGear(setIndex: Int, ringIndex: Int): List<String> {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setsList = setsString.split(",").filter { it.isNotEmpty() }
        val setId = if (setIndex in setsList.indices) setsList[setIndex] else setIndex.toString()
        val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", null)
        if (!csvString.isNullOrEmpty()) {
            return csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return when (setId) {
            "0" -> if (ringIndex == 0) listOf("com.android.vending", "org.fdroid.fdroid") else listOf("com.termux", "com.android.settings")
            else -> emptyList()
        }
    }

    private fun getGearSetNameById(setId: String): String {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val defaultName = when(setId) {
            "0" -> "POWER USER ANDROID"
            "1" -> "MY APP STORES"
            "2" -> "UTILITIES SECTOR"
            "3" -> "ENTERTAINMENT DECK"
            else -> "CUSTOM SET"
        }
        val saved = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
        return if (saved == "SET A" || saved == "SET B" || saved == "SET C" || saved == "SET D" || saved == "SET" || saved.isEmpty()) defaultName else saved
    }

    private fun getGearSetNameByIndex(index: Int): String {
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
        val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
        val setsList = setsString.split(",").filter { it.isNotEmpty() }
        if (index in setsList.indices) {
            return getGearSetNameById(setsList[index])
        }
        val alphabetLabel = if (index in 0..25) "${'A' + index}" else index.toString()
        return "SET " + alphabetLabel
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
    ) {
        val innerR = trackRadius - trackWidth / 2f
        val outerR = trackRadius + trackWidth / 2f
        
        // 1. Frosted Translucent Orbital Gimbal Track Band
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = trackWidth
        elementPaint.color = if (isActive) Color.argb(45, 255, 255, 255) else Color.argb(15, 255, 255, 255)
        canvas.drawCircle(cx, cy, trackRadius, elementPaint)

        // 2. Inner and Outer Precision Glowing Boundary Rails
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 2.2f else 1.2f
        elementPaint.color = if (isActive) m3Primary else Color.argb(45, 200, 210, 230)
        canvas.drawCircle(cx, cy, innerR, elementPaint)
        canvas.drawCircle(cx, cy, outerR, elementPaint)

        // 3. Precision Laser-Etched Azimuth Ticks
        val totalTicks = teethCount * 2
        val tickAngleStep = 360.0 / totalTicks
        val rotRad = Math.toRadians(rotationDeg.toDouble())
        elementPaint.style = Paint.Style.STROKE
        
        for (i in 0 until totalTicks) {
            val angleRad = Math.toRadians(i * tickAngleStep) + rotRad
            val isMajor = (i % 2 == 0)
            val tickLen = if (isMajor) (trackWidth * 0.35f) else (trackWidth * 0.18f)
            val tickR1 = outerR - tickLen
            val tickR2 = outerR
            
            val x1 = cx + tickR1 * Math.cos(angleRad).toFloat()
            val y1 = cy + tickR1 * Math.sin(angleRad).toFloat()
            val x2 = cx + tickR2 * Math.cos(angleRad).toFloat()
            val y2 = cy + tickR2 * Math.sin(angleRad).toFloat()
            
            elementPaint.strokeWidth = if (isMajor) 1.8f else 1.0f
            elementPaint.color = if (isActive) (if (isMajor) Color.argb(180, 255, 255, 255) else Color.argb(100, 255, 255, 255))
                                 else (if (isMajor) Color.argb(70, 200, 210, 230) else Color.argb(30, 200, 210, 230))
            canvas.drawLine(x1, y1, x2, y2, elementPaint)
        }

        // 4. Solid Chamfered Magnetic Hyperdrive Teeth (Energy Cogs)
        val gearPath = android.graphics.Path()
        val toothAngleStep = (2.0 * Math.PI) / teethCount
        val toothInnerR = outerR - (toothDepth * 0.25f)
        val toothOuterR = outerR + toothDepth
        
        for (i in 0 until teethCount) {
            val angle = i * toothAngleStep + rotRad
            val p1X = cx + toothInnerR * Math.cos(angle).toFloat()
            val p1Y = cy + toothInnerR * Math.sin(angle).toFloat()
            if (i == 0) gearPath.moveTo(p1X, p1Y) else gearPath.lineTo(p1X, p1Y)

            val p2X = cx + toothOuterR * Math.cos(angle + toothAngleStep * 0.15).toFloat()
            val p2Y = cy + toothOuterR * Math.sin(angle + toothAngleStep * 0.15).toFloat()
            gearPath.lineTo(p2X, p2Y)

            val p3X = cx + toothOuterR * Math.cos(angle + toothAngleStep * 0.35).toFloat()
            val p3Y = cy + toothOuterR * Math.sin(angle + toothAngleStep * 0.35).toFloat()
            gearPath.lineTo(p3X, p3Y)

            val p4X = cx + toothInnerR * Math.cos(angle + toothAngleStep * 0.50).toFloat()
            val p4Y = cy + toothInnerR * Math.sin(angle + toothAngleStep * 0.50).toFloat()
            gearPath.lineTo(p4X, p4Y)

            val p5X = cx + toothInnerR * Math.cos(angle + toothAngleStep).toFloat()
            val p5Y = cy + toothInnerR * Math.sin(angle + toothAngleStep).toFloat()
            gearPath.lineTo(p5X, p5Y)
        }
        gearPath.close()

        // Draw Teeth Surface Fill
        elementPaint.style = Paint.Style.FILL
        elementPaint.color = if (isActive) Color.argb(55, 255, 255, 255) else Color.argb(18, 255, 255, 255)
        canvas.drawPath(gearPath, elementPaint)

        // Draw Teeth Glowing Edge Contour
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 2.2f else 1.2f
        elementPaint.color = if (isActive) Color.argb(220, 240, 245, 255) else Color.argb(60, 150, 160, 180)
        canvas.drawPath(gearPath, elementPaint)
    }

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
    ) {
        val half = bracketSize / 2f
        val armLen = bracketSize * 0.28f
        
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = 2.4f * density
        elementPaint.color = m3Primary
        
        when (reticleStyle) {
            "cyber" -> {
                // Circular segmented targeting arc with collimator crosshairs
                val arcRect = RectF(targetCX - half, targetCY - half, targetCX + half, targetCY + half)
                canvas.drawArc(arcRect, 35f, 110f, false, elementPaint)
                canvas.drawArc(arcRect, 215f, 110f, false, elementPaint)
                
                elementPaint.strokeWidth = 1.6f * density
                canvas.drawLine(targetCX, targetCY - half - 4f * density, targetCX, targetCY - half + 4f * density, elementPaint)
                canvas.drawLine(targetCX, targetCY + half - 4f * density, targetCX, targetCY + half + 4f * density, elementPaint)
                canvas.drawLine(targetCX - half - 4f * density, targetCY, targetCX - half + 4f * density, targetCY, elementPaint)
                canvas.drawLine(targetCX + half - 4f * density, targetCY, targetCX + half + 4f * density, targetCY, elementPaint)
            }
            "cross" -> {
                // Precision fine crosshairs with center clearance & micro dot
                elementPaint.strokeWidth = 1.8f * density
                val gap = half * 0.52f
                canvas.drawLine(targetCX - half, targetCY, targetCX - gap, targetCY, elementPaint)
                canvas.drawLine(targetCX + gap, targetCY, targetCX + half, targetCY, elementPaint)
                canvas.drawLine(targetCX, targetCY - half, targetCX, targetCY - gap, elementPaint)
                canvas.drawLine(targetCX, targetCY + gap, targetCX, targetCY + half, elementPaint)
                
                elementPaint.style = Paint.Style.FILL
                canvas.drawCircle(targetCX, targetCY, 2.2f * density, elementPaint)
                elementPaint.style = Paint.Style.STROKE
            }
            "diamond" -> {
                // Sci-Fi Angular Diamond Targeting Matrix
                val dOffset = half * 1.05f
                val path = android.graphics.Path().apply {
                    moveTo(targetCX, targetCY - dOffset)
                    lineTo(targetCX + dOffset, targetCY)
                    lineTo(targetCX, targetCY + dOffset)
                    lineTo(targetCX - dOffset, targetCY)
                    close()
                }
                elementPaint.strokeWidth = 2f * density
                canvas.drawPath(path, elementPaint)
                
                elementPaint.style = Paint.Style.FILL
                canvas.drawCircle(targetCX, targetCY - dOffset, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX, targetCY + dOffset, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX - dOffset, targetCY, 2.2f * density, elementPaint)
                canvas.drawCircle(targetCX + dOffset, targetCY, 2.2f * density, elementPaint)
                elementPaint.style = Paint.Style.STROKE
            }
            else -> {
                // Default Tactical Corner Brackets [ ]
                canvas.drawLine(targetCX - half, targetCY - half + armLen, targetCX - half, targetCY - half, elementPaint)
                canvas.drawLine(targetCX - half, targetCY - half, targetCX - half + armLen, targetCY - half, elementPaint)
                canvas.drawLine(targetCX + half - armLen, targetCY - half, targetCX + half, targetCY - half, elementPaint)
                canvas.drawLine(targetCX + half, targetCY - half, targetCX + half, targetCY - half + armLen, elementPaint)
                canvas.drawLine(targetCX - half, targetCY + half - armLen, targetCX - half, targetCY + half, elementPaint)
                canvas.drawLine(targetCX - half, targetCY + half, targetCX - half + armLen, targetCY + half, elementPaint)
                canvas.drawLine(targetCX + half - armLen, targetCY + half, targetCX + half, targetCY + half, elementPaint)
                canvas.drawLine(targetCX + half, targetCY + half, targetCX + half, targetCY + half - armLen, elementPaint)
            }
        }
        
        // Horizontal Laser Alignment Lead Line extending to left bezel
        elementPaint.strokeWidth = 1.2f * density
        elementPaint.color = Color.argb(120, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawLine(0f, targetCY, targetCX - half - 10f, targetCY, elementPaint)

        // Holographic Telemetry Label above/adjacent target
        textPaint.textSize = 12f * density
        textPaint.color = Color.WHITE
        textPaint.textAlign = Paint.Align.LEFT
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        val maxBadgeWidth = 160f * density
        var cleanAppName = appName
        if (textPaint.measureText(cleanAppName) > maxBadgeWidth) {
            while (cleanAppName.length > 3 && textPaint.measureText("$cleanAppName…") > maxBadgeWidth) {
                cleanAppName = cleanAppName.dropLast(1)
            }
            cleanAppName = "$cleanAppName…"
        }
        
        val subText = "TARGET LOCKED"
        textPaint.textSize = 8.5f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        val subWidth = textPaint.measureText(subText)

        textPaint.textSize = 12f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        val titleWidth = textPaint.measureText(cleanAppName)

        val finalBadgeWidth = maxOf(titleWidth, subWidth).coerceAtLeast(54f * density)
        
        val badgeX = targetCX + half + 14f * density
        val badgeY = targetCY - 6f * density
        
        // Target Lock Badge Frame
        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(190, 16, 20, 32)
        val badgeRect = RectF(badgeX - 8f * density, badgeY - 14f * density, badgeX + finalBadgeWidth + 12f * density, badgeY + 18f * density)
        canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, highlightPaint)
        
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * density
        highlightPaint.color = m3Primary
        canvas.drawRoundRect(badgeRect, 8f * density, 8f * density, highlightPaint)
        
        // App Name
        textPaint.textSize = 12f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText(cleanAppName, badgeX, badgeY + 1f * density, textPaint)
        
        // Telemetry Subtext (Material 3 Dynamic Secondary Tonal Accent)
        textPaint.textSize = 8f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT
        textPaint.color = Color.argb(220, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
        canvas.drawText(subText, badgeX, badgeY + 12.5f * density, textPaint)
    }

    private fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) {
        // 1. Outer Containment Bezel
        elementPaint.style = Paint.Style.FILL
        val coreGrad = android.graphics.RadialGradient(
            cx, cy, coreRadius,
            intArrayOf(
                if (isActive) m3Primary else Color.argb(180, 35, 40, 55),
                if (isActive) Color.argb(220, 20, 25, 38) else Color.argb(240, 12, 14, 20)
            ),
            floatArrayOf(0.0f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        elementPaint.shader = coreGrad
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)
        elementPaint.shader = null

        // 2. Outer Containment Border
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = if (isActive) 3f * density else 1.8f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(80, 200, 220, 255)
        canvas.drawCircle(cx, cy, coreRadius, elementPaint)

        // 3. Rotating Inner Containment Ring with 4 Aperture Notches
        val rotAngle = (System.currentTimeMillis() % 10000L) / 10000f * 360f
        val innerR = coreRadius * 0.68f
        elementPaint.strokeWidth = 1.5f * density
        elementPaint.color = if (isActive) Color.WHITE else Color.argb(120, 200, 220, 255)
        canvas.drawCircle(cx, cy, innerR, elementPaint)

        for (i in 0..3) {
            val a = Math.toRadians((rotAngle + i * 90.0))
            val nx1 = cx + (innerR - 6f * density) * Math.cos(a).toFloat()
            val ny1 = cy + (innerR - 6f * density) * Math.sin(a).toFloat()
            val nx2 = cx + (innerR + 6f * density) * Math.cos(a).toFloat()
            val ny2 = cy + (innerR + 6f * density) * Math.sin(a).toFloat()
            canvas.drawLine(nx1, ny1, nx2, ny2, elementPaint)
        }

        // 4. Central Astrogation Aperture Glyph & "COCKPIT" text
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 10f * density
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE
        canvas.drawText("COCKPIT", cx, cy - 2f * density, textPaint)

        textPaint.textSize = 7.5f * density
        textPaint.color = if (isActive) Color.WHITE else Color.argb(160, 200, 220, 255)
        canvas.drawText("HANGAR", cx, cy + 10f * density, textPaint)
    }

    private fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) {
        val starPaint = elementPaint
        starPaint.style = Paint.Style.FILL
        
        val time = System.currentTimeMillis()
        val starCount = 42
        for (i in 0 until starCount) {
            val seedX = ((i * 137.5f) % w)
            val seedY = ((i * 269.3f) % h)
            val pulse = sin((time / 450.0) + (i * 0.75)).toFloat() * 0.35f + 0.65f
            val starSize = ((i % 3) + 1.2f) * density * (0.8f + 0.2f * pulse)
            val starAlpha = ((70 + (i * 17) % 130) * pulse * alphaFactor).toInt().coerceIn(0, 255)
            
            starPaint.color = when (i % 4) {
                0 -> Color.argb(starAlpha, 255, 255, 255)
                1 -> Color.argb(starAlpha, 180, 220, 255) // Cyan Starlight
                2 -> Color.argb(starAlpha, 225, 190, 255) // Violet Nebula Dust
                else -> Color.argb(starAlpha, 255, 235, 180) // Stellar Gold
            }
            canvas.drawCircle(seedX, seedY, starSize / 2f, starPaint)
        }
    }

    private fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) {
        val t = (System.currentTimeMillis() % 100000) / 1000.0
        // 1. Primary Core Plasma Cloud
        val nebulaShader1 = android.graphics.RadialGradient(
            cx, cy, radius * 1.3f,
            intArrayOf(
                Color.argb((140 * alphaFactor).toInt(), Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)),
                Color.argb((90 * alphaFactor).toInt(), 138, 43, 226), // Cosmic Violet
                Color.argb((45 * alphaFactor).toInt(), 0, 229, 255),  // Interstellar Cyan
                Color.argb(0, 4, 6, 12)
            ),
            floatArrayOf(0.0f, 0.35f, 0.70f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        elementPaint.style = Paint.Style.FILL
        elementPaint.shader = nebulaShader1
        canvas.drawCircle(cx, cy, radius * 1.3f, elementPaint)

        // 2. Secondary Diffuse Gaseous Filament
        val offX = (sin(t * 0.8) * 20.0).toFloat()
        val offY = (cos(t * 0.6) * 15.0).toFloat()
        val nebulaShader2 = android.graphics.RadialGradient(
            cx + offX, cy + offY, radius * 0.9f,
            intArrayOf(
                Color.argb((85 * alphaFactor).toInt(), 255, 64, 129), // Stellar Magenta
                Color.argb((40 * alphaFactor).toInt(), 64, 196, 255), // Cyan Aurora
                Color.argb(0, 0, 0, 0)
            ),
            floatArrayOf(0.0f, 0.5f, 1.0f),
            android.graphics.Shader.TileMode.CLAMP
        )
        elementPaint.shader = nebulaShader2
        canvas.drawCircle(cx + offX, cy + offY, radius * 0.9f, elementPaint)
        elementPaint.shader = null
    }

    private var isWarpLaunching = false
    private var warpStartTime = 0L
    private var warpFocalPointX = 0f
    private var warpFocalPointY = 0f

    private fun triggerHyperdriveWarpLaunch(focalX: Float, focalY: Float, onLaunch: () -> Unit) {
        isWarpLaunching = true
        warpStartTime = System.currentTimeMillis()
        warpFocalPointX = focalX
        warpFocalPointY = focalY
        triggerHardwareHaptic(50, 255)
        invalidate()

        uiHandler.postDelayed({
            isWarpLaunching = false
            dismissOverlay()
            onLaunch()
        }, 130L)
    }

    private fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) {
        if (!isWarpLaunching) return
        val elapsed = (System.currentTimeMillis() - warpStartTime).toFloat()
        val progress = (elapsed / 130f).coerceIn(0f, 1f)
        val easeProgress = progress * progress

        val cx = warpFocalPointX
        val cy = warpFocalPointY

        // 1. High-Energy Chromatic Shockwave Ring
        val shockwaveR = easeProgress * 340f * density
        val shockAlpha = ((1f - progress) * 255).toInt().coerceIn(0, 255)
        
        elementPaint.style = Paint.Style.STROKE
        elementPaint.strokeWidth = (4.0f * (1f - progress) + 1.0f) * density
        elementPaint.color = Color.argb(shockAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawCircle(cx, cy, shockwaveR, elementPaint)

        elementPaint.strokeWidth = 1.4f * density
        elementPaint.color = Color.argb((shockAlpha * 0.75f).toInt(), 0, 229, 255) // Cyan edge glow
        canvas.drawCircle(cx, cy, shockwaveR * 0.84f, elementPaint)

        // 2. 36 Radiating Warp Vector Streaks
        val lineCount = 36
        val angleStep = (2 * Math.PI) / lineCount
        elementPaint.style = Paint.Style.STROKE

        for (i in 0 until lineCount) {
            val angle = i * angleStep
            val rStart = (easeProgress * 35f * density) + (i % 4) * 8f * density
            val streakLength = (easeProgress * 230f * density) + (i % 3) * 35f * density
            val rEnd = rStart + streakLength

            val x1 = cx + (rStart * Math.cos(angle)).toFloat()
            val y1 = cy + (rStart * Math.sin(angle)).toFloat()
            val x2 = cx + (rEnd * Math.cos(angle)).toFloat()
            val y2 = cy + (rEnd * Math.sin(angle)).toFloat()

            val lineAlpha = ((1f - progress) * (180 + (i * 13) % 75)).toInt().coerceIn(0, 255)
            elementPaint.strokeWidth = if (i % 3 == 0) 2.4f * density else 1.2f * density
            elementPaint.color = when (i % 3) {
                0 -> Color.argb(lineAlpha, 255, 255, 255)
                1 -> Color.argb(lineAlpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                else -> Color.argb(lineAlpha, 0, 229, 255)
            }
            canvas.drawLine(x1, y1, x2, y2, elementPaint)
        }

        // 3. Central Hyperdrive Flash Core
        val flashRadius = (1f - progress) * 50f * density
        val flashAlpha = ((1f - progress) * 230).toInt().coerceIn(0, 255)
        elementPaint.style = Paint.Style.FILL
        elementPaint.color = Color.argb(flashAlpha, 255, 255, 255)
        canvas.drawCircle(cx, cy, flashRadius, elementPaint)

        postInvalidateOnAnimation()
    }

    private fun processGyroscopeTouchPhysics(rawX: Float, rawY: Float) {
        val density = resources.displayMetrics.density
        val deltaX = touchDownRawX - rawX
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
                val cPrefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
                if (cPrefs.getString("cockpit_launch_behavior", "default") == "last") {
                    cPrefs.edit().putInt("last_active_set_index", activeGearSetIndex).apply()
                }
                triggerHardwareHaptic(55, 255) // Solid mechanical locking thud
            }
        } else if (deltaX < innerThreshold + (25f * density)) {
            isCubeRotationFired = false
        }

        // Rotational Axis Crank Math: Scale angular velocity by true kinematic arc-length and flight physics profile
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
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
        val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)

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
            val d = resources.displayMetrics.density
            val screenW = width.toFloat()
            val screenH = height.toFloat()
            val cx = screenW / 2f
            val cy = screenH / 2f
            val deckW = (screenW * 0.92f).coerceAtMost(480f * d)
            val leftX = cx - (deckW / 2f)
            val rightX = cx + (deckW / 2f)
            val gap = 8f * d

            val topHangarY = (screenH * 0.08f).coerceAtLeast(64f * d)

            // 1. Deep Space Astrogation Hangar Void with Starfield
            canvas.drawColor(Color.argb(245, 6, 8, 14))
            drawCosmicStarfield(canvas, screenW, screenH, d, 0.75f)

            // 2. Flight Telemetry Header
            val hangarHeader = "◈ ASTROGATION COCKPIT // HANGAR DECK ◈"
            textPaint.textSize = 13f * d
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.textAlign = Paint.Align.CENTER
            val headerWidth = textPaint.measureText(hangarHeader)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(175, 12, 16, 28)
            val headerRect = RectF(cx - headerWidth / 2f - 20f * d, topHangarY - 18f * d, cx + headerWidth / 2f + 20f * d, topHangarY + 18f * d)
            canvas.drawRoundRect(headerRect, 12f * d, 12f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1.2f * d
            highlightPaint.color = Color.argb(90, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
            canvas.drawRoundRect(headerRect, 12f * d, 12f * d, highlightPaint)
            textPaint.color = Color.WHITE
            canvas.drawText(hangarHeader, cx, topHangarY + 5.5f * d, textPaint)

            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val launchBehavior = prefs.getString("cockpit_launch_behavior", "default") ?: "default"
            val physicsProfile = prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic"
            val hapticStrength = prefs.getString("pref_gear_haptic_strength", "tactical") ?: "tactical"

            // 3. ROW 1: STARTUP DEFAULT MODE [ ALWAYS FIRST PROFILE ] | [ RESUME LAST PROFILE ]
            val r1Y = topHangarY + 46f * d
            val halfBtnW = (deckW - gap) / 2f
            val btn1Rect = RectF(leftX, r1Y - 18f * d, leftX + halfBtnW, r1Y + 18f * d)
            val btn2Rect = RectF(rightX - halfBtnW, r1Y - 18f * d, rightX, r1Y + 18f * d)

            val isDef = (launchBehavior == "default")
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isDef) Color.argb(190, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(60, 20, 26, 40)
            canvas.drawRoundRect(btn1Rect, 10f * d, 10f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isDef) 1.5f * d else 0.8f * d
            highlightPaint.color = if (isDef) m3Primary else Color.argb(50, 200, 220, 255)
            canvas.drawRoundRect(btn1Rect, 10f * d, 10f * d, highlightPaint)
            textPaint.textSize = 10.5f * d
            textPaint.color = if (isDef) Color.WHITE else Color.argb(160, 200, 220, 255)
            canvas.drawText("STARTUP: ALWAYS FIRST", btn1Rect.centerX(), btn1Rect.centerY() + 4f * d, textPaint)

            val isLast = (launchBehavior == "last")
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (isLast) Color.argb(190, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(60, 20, 26, 40)
            canvas.drawRoundRect(btn2Rect, 10f * d, 10f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = if (isLast) 1.5f * d else 0.8f * d
            highlightPaint.color = if (isLast) m3Primary else Color.argb(50, 200, 220, 255)
            canvas.drawRoundRect(btn2Rect, 10f * d, 10f * d, highlightPaint)
            textPaint.color = if (isLast) Color.WHITE else Color.argb(160, 200, 220, 255)
            canvas.drawText("STARTUP: RESUME LAST", btn2Rect.centerX(), btn2Rect.centerY() + 4f * d, textPaint)

            // 4. ROW 2: DOCKING BAYS (HORIZONTALLY SCROLLABLE RAIL)
            val r2Y = r1Y + 46f * d
            val setsString = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
            val setsList = setsString.split(",").filter { it.isNotEmpty() }
            if (activeGearSetIndex >= setsList.size) { activeGearSetIndex = 0 }
            val currentSetId = if (activeGearSetIndex in setsList.indices) setsList[activeGearSetIndex] else "0"

            val bayCount = setsList.size + 1
            val slotW = (88f * d).coerceAtLeast(deckW / bayCount.coerceAtMost(4))
            val totalBayRailW = bayCount * slotW
            val maxScroll = (totalBayRailW - deckW).coerceAtLeast(0f)
            val railStartX = if (totalBayRailW <= deckW) (leftX + (deckW - totalBayRailW) / 2f) else (leftX + hangarBayScrollOffset.coerceIn(-maxScroll, 0f))

            canvas.save()
            canvas.clipRect(leftX - 4f * d, r2Y - 24f * d, rightX + 4f * d, r2Y + 24f * d)

            for (g in setsList.indices) {
                val setId = setsList[g]
                val defaultName = when (setId) { "0" -> "POWER"; "1" -> "STORES"; "2" -> "UTILITY"; "3" -> "MEDIA"; else -> "SET" }
                val setName = prefs.getString("gear_set_${setId}_name", defaultName) ?: defaultName
                val btnX = railStartX + (g + 0.5f) * slotW
                val isCurrentBay = (activeGearSetIndex == g)

                val bayRect = RectF(btnX - slotW * 0.46f, r2Y - 18f * d, btnX + slotW * 0.46f, r2Y + 18f * d)

                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (isCurrentBay) Color.argb(190, 24, 32, 54) else Color.argb(70, 14, 18, 28)
                canvas.drawRoundRect(bayRect, 10f * d, 10f * d, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = if (isCurrentBay) 1.8f * d else 0.8f * d
                highlightPaint.color = if (isCurrentBay) m3Primary else Color.argb(40, 200, 220, 255)
                canvas.drawRoundRect(bayRect, 10f * d, 10f * d, highlightPaint)

                // Bay Number & Short Name
                val shortLabel = "${g + 1}: ${setName.take(6)}"
                textPaint.textSize = 10.5f * d
                textPaint.typeface = if (isCurrentBay) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                textPaint.color = if (isCurrentBay) Color.WHITE else Color.argb(170, 200, 220, 255)
                canvas.drawText(shortLabel, btnX, r2Y + 4f * d, textPaint)
            }

            // Plus button to add profile set
            val plusBtnX = railStartX + (setsList.size + 0.5f) * slotW
            val plusRect = RectF(plusBtnX - slotW * 0.42f, r2Y - 18f * d, plusBtnX + slotW * 0.42f, r2Y + 18f * d)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(70, 20, 26, 40)
            canvas.drawRoundRect(plusRect, 10f * d, 10f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.8f * d
            highlightPaint.color = Color.argb(60, 200, 220, 255)
            canvas.drawRoundRect(plusRect, 10f * d, 10f * d, highlightPaint)
            textPaint.textSize = 15f * d
            textPaint.color = Color.WHITE
            canvas.drawText("+", plusBtnX, r2Y + 5.5f * d, textPaint)

            canvas.restore()

            // Draw subtle indicator arrows if the rail is scrollable
            if (maxScroll > 0f) {
                if (hangarBayScrollOffset < -4f * d) {
                    textPaint.textSize = 9f * d
                    textPaint.color = Color.argb(130, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
                    canvas.drawText("◀", leftX - 1f * d, r2Y + 3.5f * d, textPaint)
                }
                if (hangarBayScrollOffset > -maxScroll + 4f * d) {
                    textPaint.textSize = 9f * d
                    textPaint.color = Color.argb(130, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
                    canvas.drawText("▶", rightX + 1f * d, r2Y + 3.5f * d, textPaint)
                }
            }

            // 5. ROW 3: REORDER & DELETE ACTIVE PROFILE IN ROTATION
            val r3Y = r2Y + 44f * d
            val shiftW = deckW * 0.35f
            val deleteW = deckW * 0.26f
            val shiftLeftRect = RectF(leftX, r3Y - 16f * d, leftX + shiftW, r3Y + 16f * d)
            val deleteBayRect = RectF(cx - deleteW / 2f, r3Y - 16f * d, cx + deleteW / 2f, r3Y + 16f * d)
            val shiftRightRect = RectF(rightX - shiftW, r3Y - 16f * d, rightX, r3Y + 16f * d)

            // Shift Left Button
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(80, 20, 26, 40)
            canvas.drawRoundRect(shiftLeftRect, 8f * d, 8f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.8f * d
            highlightPaint.color = Color.argb(60, 200, 220, 255)
            canvas.drawRoundRect(shiftLeftRect, 8f * d, 8f * d, highlightPaint)
            textPaint.textSize = 10f * d
            textPaint.color = if (activeGearSetIndex > 0) m3Secondary else Color.argb(70, 150, 150, 150)
            canvas.drawText("◀ SHIFT LEFT", shiftLeftRect.centerX(), shiftLeftRect.centerY() + 3.5f * d, textPaint)

            // Delete Bay Button
            val canDelete = setsList.size > 1
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = if (canDelete) Color.argb(60, 239, 83, 80) else Color.argb(20, 60, 60, 60)
            canvas.drawRoundRect(deleteBayRect, 8f * d, 8f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.8f * d
            highlightPaint.color = if (canDelete) Color.argb(160, 239, 83, 80) else Color.argb(30, 100, 100, 100)
            canvas.drawRoundRect(deleteBayRect, 8f * d, 8f * d, highlightPaint)
            textPaint.textSize = 9.5f * d
            textPaint.color = if (canDelete) Color.parseColor("#FFCDD2") else Color.argb(60, 150, 150, 150)
            canvas.drawText("🗑 DELETE", deleteBayRect.centerX(), deleteBayRect.centerY() + 3.5f * d, textPaint)

            // Shift Right Button
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(80, 20, 26, 40)
            canvas.drawRoundRect(shiftRightRect, 8f * d, 8f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 0.8f * d
            highlightPaint.color = Color.argb(60, 200, 220, 255)
            canvas.drawRoundRect(shiftRightRect, 8f * d, 8f * d, highlightPaint)
            textPaint.textSize = 10f * d
            textPaint.color = if (activeGearSetIndex < setsList.size - 1) m3Secondary else Color.argb(70, 150, 150, 150)
            canvas.drawText("SHIFT RIGHT ▶", shiftRightRect.centerX(), shiftRightRect.centerY() + 3.5f * d, textPaint)

            // 6. ROW 4: ICON THEME CAROUSEL
            val r4Y = r3Y + 42f * d
            val activePack = com.sbf.lightspeed.system.LightspeedIconManager.getActiveIconPack(context)
            val availablePacks = com.sbf.lightspeed.system.LightspeedIconManager.getAvailableIconPacks(context)
            val currentPackLabel = availablePacks.firstOrNull { it.packageName == activePack }?.label ?: "System Default"
            val iconThemeRect = RectF(leftX, r4Y - 18f * d, rightX, r4Y + 18f * d)

            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(90, 16, 22, 36)
            canvas.drawRoundRect(iconThemeRect, 10f * d, 10f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1f * d
            highlightPaint.color = Color.argb(70, Color.red(m3Secondary), Color.green(m3Secondary), Color.blue(m3Secondary))
            canvas.drawRoundRect(iconThemeRect, 10f * d, 10f * d, highlightPaint)
            textPaint.textSize = 11f * d
            textPaint.color = Color.WHITE
            canvas.drawText("THEME: $currentPackLabel  [ ➔ CYCLE ]", iconThemeRect.centerX(), iconThemeRect.centerY() + 4f * d, textPaint)

            // 7. ROW 5: FLIGHT MOMENTUM & HAPTICS
            val r5Y = r4Y + 42f * d
            val physW = (deckW - (gap * 2)) / 3f
            val phys1Rect = RectF(leftX, r5Y - 16f * d, leftX + physW, r5Y + 16f * d)
            val phys2Rect = RectF(leftX + physW + gap, r5Y - 16f * d, leftX + physW * 2 + gap, r5Y + 16f * d)
            val phys3Rect = RectF(rightX - physW, r5Y - 16f * d, rightX, r5Y + 16f * d)

            fun drawPill(rect: RectF, label: String, isSelected: Boolean) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = if (isSelected) Color.argb(170, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary)) else Color.argb(50, 18, 22, 34)
                canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = if (isSelected) 1.3f * d else 0.7f * d
                highlightPaint.color = if (isSelected) m3Primary else Color.argb(40, 200, 220, 255)
                canvas.drawRoundRect(rect, 8f * d, 8f * d, highlightPaint)
                textPaint.textSize = 10f * d
                textPaint.color = if (isSelected) Color.WHITE else Color.argb(150, 200, 220, 255)
                canvas.drawText(label, rect.centerX(), rect.centerY() + 3.5f * d, textPaint)
            }

            drawPill(phys1Rect, "⚡ SNAPPY", physicsProfile == "magnetic")
            drawPill(phys2Rect, "🌊 FLUID", physicsProfile == "fluid")
            drawPill(phys3Rect, "🚀 HEAVY", physicsProfile == "heavy")

            val r6Y = r5Y + 38f * d
            val hapW = (deckW - (gap * 3)) / 4f
            val hap1Rect = RectF(leftX, r6Y - 15f * d, leftX + hapW, r6Y + 15f * d)
            val hap2Rect = RectF(leftX + (hapW + gap), r6Y - 15f * d, leftX + (hapW + gap) + hapW, r6Y + 15f * d)
            val hap3Rect = RectF(leftX + (hapW + gap) * 2, r6Y - 15f * d, leftX + (hapW + gap) * 2 + hapW, r6Y + 15f * d)
            val hap4Rect = RectF(rightX - hapW, r6Y - 15f * d, rightX, r6Y + 15f * d)

            drawPill(hap1Rect, "SUBTLE", hapticStrength == "subtle")
            drawPill(hap2Rect, "TACTICAL", hapticStrength == "tactical")
            drawPill(hap3Rect, "HEAVY", hapticStrength == "heavy")
            drawPill(hap4Rect, "OFF", hapticStrength == "off")

            // 7. ROW 7: RETICLE CROSSHAIR STYLE
            val r7Y = r6Y + 35f * d
            val retW = (deckW - (gap * 3)) / 4f
            val ret1Rect = RectF(leftX, r7Y - 14f * d, leftX + retW, r7Y + 14f * d)
            val ret2Rect = RectF(leftX + (retW + gap), r7Y - 14f * d, leftX + (retW + gap) + retW, r7Y + 14f * d)
            val ret3Rect = RectF(leftX + (retW + gap) * 2, r7Y - 14f * d, leftX + (retW + gap) * 2 + retW, r7Y + 14f * d)
            val ret4Rect = RectF(rightX - retW, r7Y - 14f * d, rightX, r7Y + 14f * d)

            val reticleStyle = prefs.getString("pref_gear_reticle_style", "tactical") ?: "tactical"
            drawPill(ret1Rect, "[ ] TACTICAL", reticleStyle == "tactical")
            drawPill(ret2Rect, "( ) CYBER", reticleStyle == "cyber")
            drawPill(ret3Rect, "+ CROSS", reticleStyle == "cross")
            drawPill(ret4Rect, "◇ DIAMOND", reticleStyle == "diamond")

            // 8. Live Dual Gimbal Assembly Preview & Active Ring Command Core
            val cyGimbal = (r7Y + 160f * d).coerceAtLeast(screenH * 0.62f)
            val radOuter = 135f * d
            val radInner = 84f * d
            val radHub = 32f * d

            // Draw Gimbal Rings with Active Highlight based on activeHangarRing
            drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radOuter, 26f * d, 16, 7.5f * d, gearRingRotations[0], (activeHangarRing == 0), m3Primary)
            drawSpaceshipGimbalRing(canvas, cx, cyGimbal, radInner, 20f * d, 10, 5.5f * d, gearRingRotations[1], (activeHangarRing == 1), m3Primary)

            val r0 = getAppsForActiveGear(activeGearSetIndex, 0)
            val r1 = getAppsForActiveGear(activeGearSetIndex, 1)
            val activeAppsList = if (activeHangarRing == 0) r0 else r1
            val pm = context.packageManager
            val sizeRaw = (38f * d).toInt()

            var focusedAppLabel: String? = null

            // Draw Real App Icons on both rings
            for (r in 0..1) {
                val radius = if (r == 0) radOuter else radInner
                val ringApps = if (r == 0) r0 else r1
                if (ringApps.isEmpty()) continue

                val count = ringApps.size
                val baseRotation = gearRingRotations[r]
                val isRingFocused = (activeHangarRing == r)

                for (i in ringApps.indices) {
                    val angleDeg = (baseRotation + i * (360f / count)) % 360f
                    val angleRad = Math.toRadians(angleDeg.toDouble())

                    val iconCX = cx + radius * Math.cos(angleRad).toFloat()
                    val iconCY = cyGimbal + radius * Math.sin(angleRad).toFloat()

                    val normalizedDeg = if (angleDeg < 0) angleDeg + 360f else angleDeg
                    val isHighlighted = isRingFocused && abs(normalizedDeg - 180f) < (180f / count)
                    val currentScale = if (isHighlighted) 1.25f else 1.0f
                    val currentSize = (sizeRaw * currentScale).toInt()

                    val itemToken = ringApps[i]
                    val appLabel = resolveCleanAppLabel(itemToken)

                    if (isHighlighted) {
                        focusedAppLabel = appLabel
                    }

                    val isEjectArmedHere = isHangarEjectArmed && isHighlighted && (activeHangarRing == r)

                    // Draw Pod Glowing Backdrop
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = if (isEjectArmedHere) Color.argb(230, 220, 38, 38)
                                          else if (isHighlighted) Color.argb(220, 24, 32, 54)
                                          else Color.argb(140, 12, 16, 26)
                    canvas.drawCircle(iconCX, iconCY, currentSize / 1.7f, highlightPaint)

                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = if (isEjectArmedHere) 2.6f * d else if (isHighlighted) 1.8f * d else 0.8f * d
                    highlightPaint.color = if (isEjectArmedHere) Color.argb(255, 255, 100, 100)
                                          else if (isHighlighted) m3Primary
                                          else Color.argb(50, 200, 220, 255)
                    canvas.drawCircle(iconCX, iconCY, currentSize / 1.7f, highlightPaint)

                    // Draw App Icon
                    try {
                        val dIcon = com.sbf.lightspeed.system.LightspeedIconManager.getIconDrawable(context, itemToken)
                        if (dIcon != null) {
                            dIcon.alpha = if (isRingFocused) (if (isHighlighted) (if (isEjectArmedHere) 140 else 255) else 220) else 140
                            val iconLeft = (iconCX - currentSize / 2f).toInt()
                            val iconTop = (iconCY - currentSize / 2f).toInt()
                            dIcon.setBounds(iconLeft, iconTop, iconLeft + currentSize, iconTop + currentSize)
                            dIcon.draw(canvas)
                        }
                    } catch (_: Exception) {}

                    // Draw Red Armed Eject Overlay Cross if Armed
                    if (isEjectArmedHere) {
                        elementPaint.style = Paint.Style.STROKE
                        elementPaint.strokeWidth = 3.2f * d
                        elementPaint.color = Color.WHITE
                        val cr = 8.5f * d
                        canvas.drawLine(iconCX - cr, iconCY - cr, iconCX + cr, iconCY + cr, elementPaint)
                        canvas.drawLine(iconCX + cr, iconCY - cr, iconCX - cr, iconCY + cr, elementPaint)
                    }
                }
            }

            val activeRingColor = if (activeHangarRing == 0) m3Primary else m3Secondary
            val otherRingColor = if (activeHangarRing == 0) m3Secondary else m3Primary

            // Draw Targeting Reticle Collimator at 180 deg (straight left) matching selected reticleStyle
            val reticleX = cx - (if (activeHangarRing == 0) radOuter else radInner)
            val reticleY = cyGimbal
            val reticleColor = if (isHangarEjectArmed) Color.argb(255, 255, 60, 60)
                               else if (activeHangarRing == 0) m3Primary
                               else m3Secondary

            val bracketSize = 54f * d
            val half = bracketSize / 2f
            val armLen = bracketSize * 0.28f

            elementPaint.style = Paint.Style.STROKE
            elementPaint.strokeWidth = if (isHangarEjectArmed) 2.4f * d else 1.8f * d
            elementPaint.color = reticleColor

            when (reticleStyle) {
                "cyber" -> {
                    val arcRect = RectF(reticleX - half, reticleY - half, reticleX + half, reticleY + half)
                    canvas.drawArc(arcRect, 35f, 110f, false, elementPaint)
                    canvas.drawArc(arcRect, 215f, 110f, false, elementPaint)
                    elementPaint.strokeWidth = 1.4f * d
                    canvas.drawLine(reticleX, reticleY - half - 4f * d, reticleX, reticleY - half + 4f * d, elementPaint)
                    canvas.drawLine(reticleX, reticleY + half - 4f * d, reticleX, reticleY + half + 4f * d, elementPaint)
                    canvas.drawLine(reticleX - half - 4f * d, reticleY, reticleX - half + 4f * d, reticleY, elementPaint)
                    canvas.drawLine(reticleX + half - 4f * d, reticleY, reticleX + half + 4f * d, reticleY, elementPaint)
                }
                "cross" -> {
                    elementPaint.strokeWidth = 1.6f * d
                    val gap = half * 0.52f
                    canvas.drawLine(reticleX - half, reticleY, reticleX - gap, reticleY, elementPaint)
                    canvas.drawLine(reticleX + gap, reticleY, reticleX + half, reticleY, elementPaint)
                    canvas.drawLine(reticleX, reticleY - half, reticleX, reticleY - gap, elementPaint)
                    canvas.drawLine(reticleX, reticleY + gap, reticleX, reticleY + half, elementPaint)
                    elementPaint.style = Paint.Style.FILL
                    canvas.drawCircle(reticleX, reticleY, 2.2f * d, elementPaint)
                    elementPaint.style = Paint.Style.STROKE
                }
                "diamond" -> {
                    val dOffset = half * 1.05f
                    val path = android.graphics.Path().apply {
                        moveTo(reticleX, reticleY - dOffset)
                        lineTo(reticleX + dOffset, reticleY)
                        lineTo(reticleX, reticleY + dOffset)
                        lineTo(reticleX - dOffset, reticleY)
                        close()
                    }
                    elementPaint.strokeWidth = 1.8f * d
                    canvas.drawPath(path, elementPaint)
                    elementPaint.style = Paint.Style.FILL
                    canvas.drawCircle(reticleX, reticleY - dOffset, 2.2f * d, elementPaint)
                    canvas.drawCircle(reticleX, reticleY + dOffset, 2.2f * d, elementPaint)
                    canvas.drawCircle(reticleX - dOffset, reticleY, 2.2f * d, elementPaint)
                    canvas.drawCircle(reticleX + dOffset, reticleY, 2.2f * d, elementPaint)
                    elementPaint.style = Paint.Style.STROKE
                }
                else -> {
                    // Tactical Corner Brackets [ ]
                    canvas.drawLine(reticleX - half, reticleY - half + armLen, reticleX - half, reticleY - half, elementPaint)
                    canvas.drawLine(reticleX - half, reticleY - half, reticleX - half + armLen, reticleY - half, elementPaint)
                    canvas.drawLine(reticleX + half - armLen, reticleY - half, reticleX + half, reticleY - half, elementPaint)
                    canvas.drawLine(reticleX + half, reticleY - half, reticleX + half, reticleY - half + armLen, elementPaint)
                    canvas.drawLine(reticleX - half, reticleY + half - armLen, reticleX - half, reticleY + half, elementPaint)
                    canvas.drawLine(reticleX - half, reticleY + half, reticleX - half + armLen, reticleY + half, elementPaint)
                    canvas.drawLine(reticleX + half - armLen, reticleY + half, reticleX + half, reticleY + half, elementPaint)
                    canvas.drawLine(reticleX + half, reticleY + half, reticleX + half, reticleY + half - armLen, elementPaint)
                }
            }

            // Central Command Reactor Core (Tap to Add/Edit Shortcuts on Active Ring)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(210, 16, 22, 38)
            canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)

            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1.6f * d
            highlightPaint.color = activeRingColor
            canvas.drawCircle(cx, cyGimbal, radHub, highlightPaint)

            textPaint.textSize = 8.5f * d
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.color = Color.WHITE
            canvas.drawText("RING 0${activeHangarRing + 1}", cx, cyGimbal - 7f * d, textPaint)

            textPaint.textSize = 8.5f * d
            textPaint.color = activeRingColor
            canvas.drawText("[ ⊕ EDIT ]", cx, cyGimbal + 4f * d, textPaint)

            textPaint.textSize = 7f * d
            textPaint.color = Color.argb(160, 200, 220, 255)
            canvas.drawText("${activeAppsList.size} APPS", cx, cyGimbal + 13f * d, textPaint)

            // Target Indicator & Live Orbital Control Deck (Positioned BELOW the Gears)
            val shiftBarY = cyGimbal + radOuter + 26f * d
            val transferBarY = cyGimbal + radOuter + 58f * d

            if (focusedAppLabel != null) {
                if (activeAppsList.size > 1) {
                    val shiftLeftRect = RectF(cx - 150f * d, shiftBarY - 14f * d, cx - 60f * d, shiftBarY + 14f * d)
                    val shiftRightRect = RectF(cx + 60f * d, shiftBarY - 14f * d, cx + 150f * d, shiftBarY + 14f * d)

                    // Left Shift Cog Button
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 18, 24, 40)
                    canvas.drawRoundRect(shiftLeftRect, 9f * d, 9f * d, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1f * d
                    highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
                    canvas.drawRoundRect(shiftLeftRect, 9f * d, 9f * d, highlightPaint)
                    textPaint.textSize = 10f * d
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textPaint.color = activeRingColor
                    canvas.drawText("◀ SHIFT COG", shiftLeftRect.centerX(), shiftLeftRect.centerY() + 3.5f * d, textPaint)

                    // Center Target Badge
                    val targetBadgeRect = RectF(cx - 56f * d, shiftBarY - 14f * d, cx + 56f * d, shiftBarY + 14f * d)
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 18, 24, 40)
                    canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1f * d
                    highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
                    canvas.drawRoundRect(targetBadgeRect, 9f * d, 9f * d, highlightPaint)

                    textPaint.textSize = 11f * d
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textPaint.color = Color.WHITE
                    canvas.drawText(focusedAppLabel.take(13), cx, shiftBarY + 3.5f * d, textPaint)

                    // Right Shift Cog Button
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 18, 24, 40)
                    canvas.drawRoundRect(shiftRightRect, 9f * d, 9f * d, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1f * d
                    highlightPaint.color = Color.argb(100, Color.red(activeRingColor), Color.green(activeRingColor), Color.blue(activeRingColor))
                    canvas.drawRoundRect(shiftRightRect, 9f * d, 9f * d, highlightPaint)
                    textPaint.color = activeRingColor
                    canvas.drawText("SHIFT COG ▶", shiftRightRect.centerX(), shiftRightRect.centerY() + 3.5f * d, textPaint)
                } else {
                    val singleTargetRect = RectF(cx - 75f * d, shiftBarY - 14f * d, cx + 75f * d, shiftBarY + 14f * d)
                    highlightPaint.style = Paint.Style.FILL
                    highlightPaint.color = Color.argb(160, 18, 24, 40)
                    canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)
                    highlightPaint.style = Paint.Style.STROKE
                    highlightPaint.strokeWidth = 1.2f * d
                    highlightPaint.color = activeRingColor
                    canvas.drawRoundRect(singleTargetRect, 9f * d, 9f * d, highlightPaint)

                    textPaint.textSize = 11f * d
                    textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    textPaint.color = Color.WHITE
                    canvas.drawText(focusedAppLabel.take(16), cx, shiftBarY + 3.5f * d, textPaint)
                }

                // Inter-Ring Transfer Button
                val destRingName = if (activeHangarRing == 0) "INNER RING 02" else "OUTER RING 01"
                val transferRect = RectF(cx - 115f * d, transferBarY - 14f * d, cx + 115f * d, transferBarY + 14f * d)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(160, 20, 26, 46)
                canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 1.2f * d
                highlightPaint.color = otherRingColor
                canvas.drawRoundRect(transferRect, 9f * d, 9f * d, highlightPaint)
                textPaint.textSize = 10f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                textPaint.color = Color.WHITE
                canvas.drawText("⇄ TRANSFER TO $destRingName", transferRect.centerX(), transferRect.centerY() + 3.5f * d, textPaint)

                // Subtitle Hint for Long-Press Customization
                textPaint.textSize = 8.5f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT
                textPaint.color = Color.argb(150, 180, 210, 245)
                canvas.drawText("HOLD COG / SET TO CUSTOMIZE", cx, transferBarY + 22f * d, textPaint)
            } else {
                textPaint.textSize = 10f * d
                textPaint.typeface = android.graphics.Typeface.DEFAULT
                textPaint.color = Color.argb(140, 200, 220, 255)
                canvas.drawText("✦ TOUCH & DRAG TO SPIN // TAP CENTER TO EDIT ✦", cx, shiftBarY + 3.5f * d, textPaint)
            }

            // Bottom Exit Capsule
            val exitBtnRect = RectF(cx - (deckW * 0.42f), screenH - 58f * d, cx + (deckW * 0.42f), screenH - 18f * d)
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(140, 24, 28, 40)
            canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
            highlightPaint.style = Paint.Style.STROKE
            highlightPaint.strokeWidth = 1.2f * d
            highlightPaint.color = Color.argb(80, 200, 220, 255)
            canvas.drawRoundRect(exitBtnRect, 14f * d, 14f * d, highlightPaint)
            textPaint.textSize = 11.5f * d
            textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            textPaint.color = Color.WHITE
            canvas.drawText("✕ CLOSE HANGAR / RETURN", cx, exitBtnRect.centerY() + 4f * d, textPaint)
            return
        }
        val w = width.toFloat(); val h = height.toFloat()
        if (currentLayer == CruiseLayer.HIDDEN) {
            highlightPaint.style = Paint.Style.FILL
            highlightPaint.color = Color.argb(40, 255, 255, 255)
            if (topVisualWidthPx > 0.5f && topVisualBounds.width() > 0.5f) {
                canvas.drawRect(topVisualBounds, highlightPaint)
            }
            if (centerVisualWidthPx > 0.5f && centerVisualBounds.width() > 0.5f) {
                canvas.drawRoundRect(centerVisualBounds, 6f, 6f, highlightPaint)
            }
            if (bottomVisualWidthPx > 0.5f && bottomVisualBounds.width() > 0.5f) {
                canvas.drawRect(bottomVisualBounds, highlightPaint)
            }
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
                    
                    // Dynamic Focus Interpolation: Target angle is 180 deg (straight left)
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

            // 3. Draw 180° Flight Lock Targeting Reticle & Holographic Badge AFTER all rings & pods are drawn (Top of Z-Stack)
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
            canvas.drawText("PULL LEFT >> SWITCH PROFILE  •  SLIDE RIGHT << CANCEL", cx, topBadgeY + 28f * density, textPaint)
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
            canvas.drawText("PULL LEFT >> ENTER STAR SYSTEM  •  SLIDE RIGHT << CANCEL", w / 2f, topBadgeY + 26f * density, textPaint)

            // 4. Galactic Horizon: Cruising Through Nebulae & Star Systems
            if (cachedCategories.isNotEmpty()) {
                val startYArea = h * 0.22f; val endYArea = h * 0.78f
                val catLineH = (endYArea - startYArea) / cachedCategories.size

                cachedCategories.forEachIndexed { idx, cat ->
                    val centerY = startYArea + (idx * catLineH) + (catLineH / 2f) + categoryVisualOffset
                    val relativeDistanceFromCenter = centerY - (h / 2f)
                    val sweepAngleRad = (relativeDistanceFromCenter / ((endYArea - startYArea) / 2f)).coerceIn(-1.2f, 1.2f) * (PI / 2.6f)
                    
                    // Natural right anchor with safe 28dp margin from screen edge
                    val targetTextX = w - (28f * density) - (cos(sweepAngleRad).toFloat() * 160f * density)
                    val distanceRatio = (abs(relativeDistanceFromCenter) / ((endYArea - startYArea) / 2f)).coerceIn(0f, 1f)
                    val zoom = 0.70f + Math.pow(1.0 - distanceRatio, 2.5).toFloat() * 1.35f

                    // If active category, draw its luminous chromatic Nebula Cloud behind it
                    if (idx == activeCatIndex) {
                        drawGalacticNebula(
                            canvas = canvas,
                            cx = targetTextX - (100f * density),
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
                    catTextPaint.textAlign = Paint.Align.RIGHT
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
                        textPaint.textAlign = Paint.Align.RIGHT
                        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textPaint.textSize = (currentTextSize * 0.75f).coerceAtLeast(8.5f * density)
                        textPaint.color = m3Primary
                        textPaint.alpha = (255 * beaconAlpha * decelerationFactor).toInt()
                        canvas.drawText("✦", targetTextX - measuredLabelW - 6f * density, centerY + 5.5f * density, textPaint)
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

        if (currentLayer == CruiseLayer.HIDDEN) {
            val isSidebarPreview = prefs.getBoolean("pref_sidebar_preview", false)
            val isTopExpanded = prefs.getBoolean("pref_section_top_expanded", false)
            val isCenterExpanded = prefs.getBoolean("pref_section_center_expanded", false)
            val isBottomExpanded = prefs.getBoolean("pref_section_bottom_expanded", false)

            val d = resources.displayMetrics.density
            val screenW = width.toFloat()

            val centerTransparency = prefs.getInt("pref_sidebar_center_transparency", 0)
            val topTransparency = prefs.getInt("pref_sidebar_top_transparency", 0)
            val bottomTransparency = if (prefs.getBoolean("pref_sidebar_link_edges", false)) topTransparency else prefs.getInt("pref_sidebar_bottom_transparency", 0)

            // 1. Center Zone (Gimbal / Cogs)
            if (isCenterExpanded || isSidebarPreview) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(130, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                canvas.drawRect(centerTouchBounds, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 2f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRect(centerTouchBounds, highlightPaint)
            } else if (centerTransparency > 0) {
                val alpha = (centerTransparency * 2.55f).toInt().coerceIn(0, 255)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(alpha, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
                canvas.drawRect(screenW - (3f * d), centerTouchBounds.top, screenW, centerTouchBounds.bottom, highlightPaint)
            }

            // 2. Upper Zone
            if (isTopExpanded || isSidebarPreview) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(130, 68, 138, 255)
                canvas.drawRect(topTouchBounds, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 2f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRect(topTouchBounds, highlightPaint)
            } else if (topTransparency > 0) {
                val alpha = (topTransparency * 2.55f).toInt().coerceIn(0, 255)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(alpha, 68, 138, 255)
                canvas.drawRect(screenW - (3f * d), topTouchBounds.top, screenW, topTouchBounds.bottom, highlightPaint)
            }

            // 3. Lower Zone
            if (isBottomExpanded || isSidebarPreview) {
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(130, 255, 171, 0)
                canvas.drawRect(bottomTouchBounds, highlightPaint)

                highlightPaint.style = Paint.Style.STROKE
                highlightPaint.strokeWidth = 2f * d
                highlightPaint.color = Color.WHITE
                canvas.drawRect(bottomTouchBounds, highlightPaint)
            } else if (bottomTransparency > 0) {
                val alpha = (bottomTransparency * 2.55f).toInt().coerceIn(0, 255)
                highlightPaint.style = Paint.Style.FILL
                highlightPaint.color = Color.argb(alpha, 255, 171, 0)
                canvas.drawRect(screenW - (3f * d), bottomTouchBounds.top, screenW, bottomTouchBounds.bottom, highlightPaint)
            }
        }

        drawHyperdriveWarpSurge(canvas, m3Primary, resources.displayMetrics.density)
    }
}

fun Context.defaultPrefs(): android.content.SharedPreferences = getSharedPreferences("default", Context.MODE_PRIVATE)
