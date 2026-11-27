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
    private var currentBlockSize: Int = 1
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

    private fun normalizeBlockSize(raw: Int): Int = when {
        raw >= 4 -> 4
        raw >= 2 -> 2
        else -> 1
    }

    fun init(context: Context) {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) {
            val pattern = prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, 25)
            val blockSize = normalizeBlockSize(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, 1))
            show(context, pattern, blockSize)
        }
    }

    fun setEnabled(context: Context, enabled: Boolean, patternPct: Int = 25) {
        val clampedPct = patternPct.coerceIn(5, 85)
        val prefs = context.defaultPrefs()
        val blockSize = normalizeBlockSize(prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, currentBlockSize))
        currentPatternPct = clampedPct
        currentBlockSize = blockSize
        prefs.edit()
            .putBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, enabled)
            .putInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, clampedPct)
            .apply()

        if (enabled) {
            show(context, clampedPct, blockSize)
        } else {
            hide()
        }
    }

    fun setBlockSize(context: Context, blockSize: Int) {
        val normalized = normalizeBlockSize(blockSize)
        currentBlockSize = normalized
        val prefs = context.defaultPrefs()
        prefs.edit().putInt(LightspeedPreferences.KEY_SMART_PIXELS_BLOCK_SIZE, normalized).apply()
        if (prefs.getBoolean(LightspeedPreferences.KEY_SMART_PIXELS_ENABLED, false)) {
            val pattern = prefs.getInt(LightspeedPreferences.KEY_SMART_PIXELS_PATTERN, currentPatternPct)
            show(context, pattern, normalized)
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

    @android.annotation.SuppressLint("MissingPermission") // Guarded by SDK_INT >= 35 and try/catch
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

    @android.annotation.SuppressLint("MissingPermission") // Guarded by SDK_INT >= 35 and try/catch
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

    private fun show(context: Context, patternPct: Int, blockSize: Int = currentBlockSize) {
        val normalizedBlock = normalizeBlockSize(blockSize)
        currentPatternPct = patternPct.coerceIn(5, 85)
        currentBlockSize = normalizedBlock
        mainHandler.post {
            val service = LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post

            registerCaptureListeners(service, wm)

            overlayView?.let { existing ->
                if (existing.context !== service) {
                    val oldWm = existing.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                    try { oldWm?.removeView(existing) } catch (_: Exception) {}
                    overlayView = null
                }
            }

            if (overlayView == null) {
                val v = SmartPixelView(service, currentPatternPct, normalizedBlock, currentShift, currentShift).apply {
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
                overlayView?.updatePattern(currentPatternPct, normalizedBlock, currentShift, currentShift)
                updateVisibility()
            }

            startBurnInShift()
        }
    }

    private fun hide() {
        mainHandler.post {
            shiftJob?.cancel()
            shiftJob = null

            val v = overlayView
            val ctx = LightspeedAccessibilityService.instance ?: v?.context
            val wm = try { ctx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager } catch (_: Exception) { null }
            if (ctx != null && wm != null) {
                unregisterCaptureListeners(ctx, wm)
            }
            if (v != null) {
                val viewWm = (v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: wm
                try { viewWm?.removeView(v) } catch (_: Exception) {}
                overlayView = null
            }
        }
    }

    fun onServiceDestroyed(service: Context?) {
        shiftJob?.cancel()
        shiftJob = null
        val ctx = service ?: overlayView?.context
        val wm = try { ctx?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager } catch (_: Exception) { null }
        if (ctx != null && wm != null) {
            unregisterCaptureListeners(ctx, wm)
        }
        overlayView?.let { v ->
            val viewWm = (v.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: wm
            try { viewWm?.removeView(v) } catch (_: Exception) {}
        }
        overlayView = null
    }

    private fun startBurnInShift() {
        shiftJob?.cancel()
        shiftJob = scope.launch {
            while (isActive) {
                delay(60_000L) // Shift subpixel mesh every 60 seconds
                currentShift = (currentShift + 1) and 7
                mainHandler.post {
                    overlayView?.updatePattern(currentPatternPct, currentBlockSize, currentShift, currentShift)
                }
            }
        }
    }

    private class SmartPixelView(
        context: Context,
        initialPattern: Int,
        initialBlockSize: Int,
        initialShiftX: Int,
        initialShiftY: Int
    ) : View(context) {

        private val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            isDither = false
        }
        private var shader: BitmapShader? = null
        private var cachedPatternBitmap: Bitmap? = null

        init {
            isClickable = false
            isFocusable = false
            updatePattern(initialPattern, initialBlockSize, initialShiftX, initialShiftY)
        }

        fun updatePattern(pct: Int, blockSize: Int, shiftX: Int, shiftY: Int) {
            val oldBmp = cachedPatternBitmap
            val bmp = createPatternBitmap(pct, blockSize, shiftX, shiftY)
            cachedPatternBitmap = bmp
            shader = BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            paint.shader = shader
            oldBmp?.recycle()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        }

        private fun createPatternBitmap(pct: Int, blockSize: Int, shiftX: Int, shiftY: Int): Bitmap {
            val bs = normalizeBlockSize(blockSize)
            val dim = 8 * bs
            val bmp = Bitmap.createBitmap(dim, dim, Bitmap.Config.ARGB_8888).apply {
                density = Bitmap.DENSITY_NONE
            }
            val clampedPct = pct.coerceIn(5, 85)
            val threshold = (clampedPct * 64) / 100
            val pixels = IntArray(dim * dim)

            for (by in 0 until 8) {
                val row = (by + shiftY) and 7
                for (bx in 0 until 8) {
                    val col = (bx + shiftX) and 7
                    val color = if (BAYER_8X8[row][col] < threshold) Color.BLACK else Color.TRANSPARENT
                    val startY = by * bs
                    val startX = bx * bs
                    for (dy in 0 until bs) {
                        val rowOff = (startY + dy) * dim + startX
                        for (dx in 0 until bs) {
                            pixels[rowOff + dx] = color
                        }
                    }
                }
            }
            bmp.setPixels(pixels, 0, dim, 0, 0, dim, dim)
            return bmp
        }
    }
}
