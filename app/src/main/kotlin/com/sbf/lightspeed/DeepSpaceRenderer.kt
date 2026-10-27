package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.LightspeedPreferences
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Encapsulates rendering state and callbacks for the Cockpit Hangar view.
 */
data class HangarRenderState(
    val canvas: Canvas,
    val context: Context,
    val screenW: Float,
    val screenH: Float,
    val density: Float,
    val m3Primary: Int,
    val m3Secondary: Int,
    val isOpenedFromLeftFlank: Boolean,
    val activeGearSetIndex: Int,
    val activeHangarRing: Int,
    val isHangarEjectArmed: Boolean,
    val gearRingRotations: FloatArray,
    val hangarBayScrollOffset: Float,
    val setsList: List<String>,
    val resolveCleanAppLabel: (String) -> String,
    val getAppsForActiveGear: (setIndex: Int, ringIndex: Int) -> List<String>,
    val getFlankLaunchBehavior: (Boolean) -> String,
    val onIconResolved: (() -> Unit)? = null
)

/**
 * DeepSpaceRenderer
 *
 * Extracted renderer for the COCKPIT_HANGAR layer of LightspeedCruiseOverlay.
 *
 * Accepts the overlay's shared Paint objects and live state references so no
 * data is duplicated. LightspeedCruiseOverlay delegates the COCKPIT_HANGAR
 * onDraw block, drawSpaceshipGimbalRing, drawFlightLockReticle,
 * drawHolographicReactorCore, drawCosmicStarfield, drawGalacticNebula, and
 * drawHyperdriveWarpSurge to this class.
 *
 * All drawing methods are internal to this class. LightspeedCruiseOverlay calls
 * only drawDeepSpace(canvas, ...) and the two warp methods.
 */
internal class DeepSpaceRenderer(
    internal val textPaint: Paint,
    internal val elementPaint: Paint,
    internal val highlightPaint: Paint
) {

    internal val diamondPath = Path()
    internal val gearPath = Path()
    internal val transferRect = RectF()
    internal val exitBtnRect = RectF()
    internal val reticleArcRect = RectF()
    internal val flightBadgeRect = RectF()
    internal val shiftLeftRect = RectF()
    internal val shiftRightRect = RectF()
    internal val targetBadgeRect = RectF()
    internal val singleTargetRect = RectF()
    internal val headerRect = RectF()
    internal val tempIconRect = RectF()
    internal val tempEjectRect = RectF()
    internal val tempShiftR0Rect = RectF()
    internal val tempShiftR1Rect = RectF()

    // Modular sub-renderers
    val cosmicRenderer = LightspeedCosmicBackdropRenderer()
    val reactorRenderer = LightspeedReactorRenderer()

    // -------------------------------------------------------------------------
    // Warp launch state — delegated to LightspeedReactorRenderer
    // -------------------------------------------------------------------------
    var isWarpLaunching: Boolean
        get() = reactorRenderer.isWarpLaunching
        set(value) { if (!value) reactorRenderer.cancelWarp() }

    var warpStartTime: Long
        get() = reactorRenderer.warpStartTime
        set(_) {}

    var warpFocalPointX: Float
        get() = reactorRenderer.warpFocalPointX
        set(_) {}

    var warpFocalPointY: Float
        get() = reactorRenderer.warpFocalPointY
        set(_) {}

    // -------------------------------------------------------------------------
    // Public entry point — called from LightspeedCruiseOverlay.onDraw
    // -------------------------------------------------------------------------

    /**
     * Renders the full COCKPIT_HANGAR screen onto canvas using [HangarRenderState].
     */
    fun drawDeepSpace(state: HangarRenderState) {
        val canvas = state.canvas
        val context = state.context
        val screenW = state.screenW
        val screenH = state.screenH
        val d = state.density
        val m3Primary = state.m3Primary
        val m3Secondary = state.m3Secondary
        val isOpenedFromLeftFlank = state.isOpenedFromLeftFlank
        val activeGearSetIndex = state.activeGearSetIndex
        val activeHangarRing = state.activeHangarRing
        val isHangarEjectArmed = state.isHangarEjectArmed
        val gearRingRotations = state.gearRingRotations
        val hangarBayScrollOffset = state.hangarBayScrollOffset
        val setsList = state.setsList
        val resolveCleanAppLabel = state.resolveCleanAppLabel
        val getAppsForActiveGear = state.getAppsForActiveGear
        val getFlankLaunchBehavior = state.getFlankLaunchBehavior
        val onIconResolved = state.onIconResolved

        val prefs = context.defaultPrefs()
        val openAccordion = prefs.getInt(KEY_HANGAR_OPEN_ACCORDION, -1)
        val layout = computeHangarLayoutMetrics(screenW, screenH, d, openAccordion)

        // 1. Deep Space Astrogation Hangar Void with Starfield
        canvas.drawColor(Color.argb(245, 6, 8, 14))
        drawCosmicStarfield(canvas, screenW, screenH, d, 0.75f)

        // 2. Flight Telemetry Header
        val hangarHeader = "◈ ASTROGATION COCKPIT // HANGAR DECK ◈"
        textPaint.textSize = (if (layout.isLandscape) 11f else 12.5f) * d
        textPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        textPaint.textAlign = Paint.Align.CENTER
        val headerWidth = textPaint.measureText(hangarHeader)

        highlightPaint.style = Paint.Style.FILL
        highlightPaint.color = Color.argb(175, 12, 16, 28)
        headerRect.set(layout.ctrlCx - headerWidth / 2f - 16f * d, layout.topHangarY - 14f * d, layout.ctrlCx + headerWidth / 2f + 16f * d, layout.topHangarY + 14f * d)
        canvas.drawRoundRect(headerRect, 10f * d, 10f * d, highlightPaint)
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = 1.2f * d
        highlightPaint.color = Color.argb(90, Color.red(m3Primary), Color.green(m3Primary), Color.blue(m3Primary))
        canvas.drawRoundRect(headerRect, 10f * d, 10f * d, highlightPaint)
        textPaint.color = Color.WHITE
        canvas.drawText(hangarHeader, layout.ctrlCx, layout.topHangarY + 4f * d, textPaint)

        drawHangarGimbalAssembly(state, layout)
        drawHangarControlRows(state, layout)
    }

    /**
     * Backward-compatible overload accepting individual parameters.
     */
    fun drawDeepSpace(
        canvas: Canvas,
        context: Context,
        screenW: Float,
        screenH: Float,
        d: Float,
        m3Primary: Int,
        m3Secondary: Int,
        isOpenedFromLeftFlank: Boolean,
        activeGearSetIndex: Int,
        activeHangarRing: Int,
        isHangarEjectArmed: Boolean,
        gearRingRotations: FloatArray,
        hangarBayScrollOffset: Float,
        setsList: List<String>,
        resolveCleanAppLabel: (String) -> String,
        getAppsForActiveGear: (setIndex: Int, ringIndex: Int) -> List<String>,
        getFlankLaunchBehavior: (Boolean) -> String,
        onIconResolved: (() -> Unit)? = null
    ) {
        drawDeepSpace(
            HangarRenderState(
                canvas, context, screenW, screenH, d, m3Primary, m3Secondary,
                isOpenedFromLeftFlank, activeGearSetIndex, activeHangarRing,
                isHangarEjectArmed, gearRingRotations, hangarBayScrollOffset,
                setsList, resolveCleanAppLabel, getAppsForActiveGear,
                getFlankLaunchBehavior, onIconResolved
            )
        )
    }

    // -------------------------------------------------------------------------
    // Warp surge animation — called from LightspeedCruiseOverlay.onDraw
    // -------------------------------------------------------------------------

    fun drawHyperdriveWarpSurge(canvas: Canvas, m3Primary: Int, density: Float) {
        reactorRenderer.drawHyperdriveWarpSurge(canvas, m3Primary, density)
    }

    // -------------------------------------------------------------------------
    // Shared drawing primitives (also used by LightspeedCruiseOverlay directly)
    // -------------------------------------------------------------------------

    fun drawSpaceshipGimbalRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        trackRadius: Float,
        trackWidth: Float,
        teethCount: Int,
        toothDepth: Float,
        rotationDeg: Float,
        isActive: Boolean,
        m3Primary: Int,
        gearShape: String = LightspeedPreferences.GEAR_SHAPE_CIRCLE,
        segmentCount: Int = (teethCount / 2).coerceAtLeast(6)
    ) = drawSpaceshipGimbalRingImpl(canvas, cx, cy, trackRadius, trackWidth, teethCount, toothDepth, rotationDeg, isActive, m3Primary, gearShape, segmentCount)

    fun drawFlightLockReticle(
        canvas: Canvas,
        targetCX: Float,
        targetCY: Float,
        bracketSize: Float,
        m3Primary: Int,
        m3Secondary: Int,
        appName: String,
        density: Float,
        reticleStyle: String = "tactical"
    ) = drawFlightLockReticleImpl(canvas, targetCX, targetCY, bracketSize, m3Primary, m3Secondary, appName, density, reticleStyle)

    fun drawHolographicReactorCore(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        coreRadius: Float,
        isActive: Boolean,
        m3Primary: Int,
        density: Float
    ) {
        reactorRenderer.drawHolographicReactorCore(canvas, cx, cy, coreRadius, isActive, m3Primary, density)
    }

    fun drawCosmicStarfield(canvas: Canvas, w: Float, h: Float, density: Float, alphaFactor: Float) {
        cosmicRenderer.drawCosmicStarfield(canvas, w, h, density, alphaFactor)
    }

    fun drawGalacticNebula(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        m3Primary: Int,
        alphaFactor: Float
    ) {
        cosmicRenderer.drawGalacticNebula(canvas, cx, cy, radius, m3Primary, alphaFactor)
    }
}
