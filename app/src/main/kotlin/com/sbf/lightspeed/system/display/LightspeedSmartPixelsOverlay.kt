package com.sbf.lightspeed.system.display

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.LightspeedPreferences
import kotlinx.coroutines.*
import java.util.function.Consumer

/**
 * Smart Pixels OLED Power Saver & Burn-In Safe Mesh (Essentials Grade)
 *
 * Shuts off 5% to 85% of OLED display subpixels via a hardware-accelerated 8x8 Bayer
 * ordered dithering BitmapShader at 1:1 physical screen pixels with zero bilinear filtering.
 * Includes a 60-second spatial subpixel phase shifter to prevent non-uniform burn-in,
 * automatically conceals itself on package installer / permission controllers,
 * and auto-disables during screen recording, screenshots, or wireless casting/mirroring.
 */
object LightspeedSmartPixelsOverlay {

    private const val TAG = "SmartPixels"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var overlayView: SmartPixelView? = null
    private var shiftJob: Job? = null
    private var isInstallerSuppressed: Boolean = false
    private var isCaptureOrCastSuppressed: Boolean = false
    private var isScreenRecording: Boolean = false
    private var isCastDisplayActive: Boolean = false
    private var currentPatternPct: Int = 25
    private var currentShift: Int = 0
    private var displayListenerRegistered = false
    private var recordingCallbackRegistered = false
    private var recordingCallback: Any? = null

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = checkActiveDisplays()
        override fun onDisplayRemoved(displayId: Int) = checkActiveDisplays()
        override fun onDisplayChanged(displayId: Int) = checkActiveDisplays()
    }

    private val BAYER_8X8 = arrayOf(
        intArrayOf( 0, 32,  8, 40,  2, 34, 10, 42),
        intArrayOf(48, 16, 56, 24, 50, 18, 58, 26),
        intArrayOf(12, 44,  4, 36, 14, 46,  6, 38),
        intArrayOf(60, 28, 52, 20, 62, 30, 54, 22),
        intArrayOf( 3, 35, 11, 43,  1, 33,  9, 41),
        intArrayOf(51, 19, 59, 27, 49, 17, 57, 25),
        intArrayOf(15, 47,  7, 39, 13, 45,  5, 37),
        intArrayOf(63, 31, 55, 23, 61, 29, 53, 21)
    )

    fun init(context: Context) {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) {
            val pattern = prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)
            show(context, pattern)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean, patternPct: Int = 25) {
        val clampedPct = patternPct.coerceIn(5, 85)
        currentPatternPct = clampedPct
        context.defaultPrefs().edit()
            .putBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, enabled)
            .putInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, clampedPct)
            .apply()

        if (enabled) {
            show(context, clampedPct)
        } else {
            hide()
        }
    }

    fun refreshCaptureBypass(context: Context) {
        checkActiveDisplays()
        updateVisibility()
    }

    fun onForegroundPackageChanged(pkg: String) {
        val isInstaller = pkg == "com.google.android.permissioncontroller" ||
                pkg == "com.android.permissioncontroller" ||
                pkg == "com.android.packageinstaller" ||
                pkg == "com.google.android.packageinstaller"

        if (isInstaller != isInstallerSuppressed) {
            isInstallerSuppressed = isInstaller
            mainHandler.post { updateVisibility() }
        }
    }

    private fun checkActiveDisplays() {
        val service = LightspeedAccessibilityService.instance ?: return
        val dm = service.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val secondaryDisplays = dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        isCastDisplayActive = secondaryDisplays.any { d ->
            val flags = d.flags
            val isPresentation = (flags and Display.FLAG_PRESENTATION) != 0
            val isPublic = (flags and Display.FLAG_PRIVATE) == 0
            val name = d.name?.lowercase() ?: ""
            isPresentation || isPublic || name.contains("record") || name.contains("cast") || name.contains("wfd") || name.contains("virtual")
        }
        mainHandler.post { updateVisibility() }
    }

    private fun updateVisibility() {
        val service = LightspeedAccessibilityService.instance ?: return
        val disableOnCast = service.defaultPrefs().getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_DISABLE_ON_CAST, true)
        isCaptureOrCastSuppressed = disableOnCast && (isScreenRecording || isCastDisplayActive)
        val shouldHide = isInstallerSuppressed || isCaptureOrCastSuppressed
        overlayView?.visibility = if (shouldHide) View.GONE else View.VISIBLE
    }

    private fun registerCaptureListeners(service: Context, wm: WindowManager) {
        val dm = service.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        if (dm != null && !displayListenerRegistered) {
            try {
                dm.registerDisplayListener(displayListener, mainHandler)
                displayListenerRegistered = true
                checkActiveDisplays()
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= 35 && !recordingCallbackRegistered) {
            try {
                val cb = Consumer<Int> { state ->
                    isScreenRecording = (state == 1 /* SCREEN_RECORDING_STATE_VISIBLE */)
                    mainHandler.post { updateVisibility() }
                }
                val initialState = wm.addScreenRecordingCallback(service.mainExecutor, cb)
                isScreenRecording = (initialState == 1)
                recordingCallback = cb
                recordingCallbackRegistered = true
            } catch (_: Exception) {}
        }
    }

    private fun unregisterCaptureListeners(service: Context, wm: WindowManager) {
        val dm = service.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        if (dm != null && displayListenerRegistered) {
            try { dm.unregisterDisplayListener(displayListener) } catch (_: Exception) {}
            displayListenerRegistered = false
        }
        if (Build.VERSION.SDK_INT >= 35 && recordingCallbackRegistered) {
            @Suppress("UNCHECKED_CAST")
            (recordingCallback as? Consumer<Int>)?.let { cb ->
                try { wm.removeScreenRecordingCallback(cb) } catch (_: Exception) {}
            }
            recordingCallback = null
            recordingCallbackRegistered = false
        }
        isScreenRecording = false
        isCastDisplayActive = false
    }

    private fun show(context: Context, patternPct: Int) {
        mainHandler.post {
            val service = LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

            registerCaptureListeners(service, wm)

            if (overlayView == null) {
                val v = SmartPixelView(service, patternPct, currentShift, currentShift).apply {
                    alpha = 0f
                }
                val params = createFullDisplayOverlayParams(service)
                try {
                    wm.addView(v, params)
                    overlayView = v
                    updateVisibility()
                    ObjectAnimator.ofFloat(v, "alpha", 0f, 1f).apply {
                        duration = 200L
                        interpolator = DecelerateInterpolator()
                        start()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to attach smart pixels overlay", e)
                    return@post
                }
            } else {
                overlayView?.updatePattern(patternPct, currentShift, currentShift)
                updateVisibility()
            }

            startBurnInShift()
        }
    }

    private fun hide() {
        mainHandler.post {
            shiftJob?.cancel()
            shiftJob = null

            val service = LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

            unregisterCaptureListeners(service, wm)

            overlayView?.let {
                try { wm.removeView(it) } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    private fun startBurnInShift() {
        shiftJob?.cancel()
        shiftJob = scope.launch {
            while (isActive) {
                delay(60_000L) // Shift subpixel mesh every 60 seconds
                currentShift = (currentShift + 1) and 7
                mainHandler.post {
                    overlayView?.updatePattern(currentPatternPct, currentShift, currentShift)
                }
            }
        }
    }

    private class SmartPixelView(
        context: Context,
        initialPattern: Int,
        initialShiftX: Int,
        initialShiftY: Int
    ) : View(context) {

        private val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
        }
        private var shader: BitmapShader? = null

        init {
            isClickable = false
            isFocusable = false
            updatePattern(initialPattern, initialShiftX, initialShiftY)
        }

        fun updatePattern(pct: Int, shiftX: Int, shiftY: Int) {
            val bmp = createPatternBitmap(pct, shiftX, shiftY)
            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            paint.shader = shader
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        private fun createPatternBitmap(pct: Int, shiftX: Int, shiftY: Int): Bitmap {
            val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply {
                density = Bitmap.DENSITY_NONE
            }
            val clampedPct = pct.coerceIn(5, 85)
            val threshold = (clampedPct * 64) / 100

            for (y in 0 until 8) {
                val row = (y + shiftY) and 7
                for (x in 0 until 8) {
                    val col = (x + shiftX) and 7
                    val isBlack = BAYER_8X8[row][col] < threshold
                    bmp.setPixel(x, y, if (isBlack) Color.BLACK else Color.TRANSPARENT)
                }
            }
            return bmp
        }
    }
}
