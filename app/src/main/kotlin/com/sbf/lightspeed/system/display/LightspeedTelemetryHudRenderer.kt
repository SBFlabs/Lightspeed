package com.sbf.lightspeed.system.display

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.CanvasThemePalette
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.resolveCanvasPalette
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlin.math.roundToInt

enum class TelemetryMetricKind {
    FPS, CPU, TEMP, RAM, BAT, WATT
}

enum class TelemetrySeverity {
    ACCENT, OK, NORMAL, WARN, DANGER
}

data class TelemetryMetricItem(
    val kind: TelemetryMetricKind,
    val label: String,
    val value: String,
    val fraction: Float,
    val severity: TelemetrySeverity
)

enum class HandlePressState {
    NONE, LEFT, RIGHT
}

data class HudMeasureSpec(
    val totalWidthPx: Int,
    val totalHeightPx: Int,
    val coreWidthPx: Int,
    val coreHeightPx: Int,
    val plumeMarginXPx: Int,
    val plumeMarginYPx: Int,
    val handleWidthPx: Int,
    val itemWidths: FloatArray = FloatArray(0),
    val totalItemsWidth: Float = 0f
)

/**
 * Renders the 4 modern Telemetry Micro-HUD styles (`alpha_shade`, `glass_capsule`,
 * `modular_pods`, `horizon_gauge`) with scaled DeviceFontScanner typography, dual
 * tactile maneuvering handles / morphing rocket thrusters, and transparent exhaust margins.
 */
object LightspeedTelemetryHudRenderer {

    const val STYLE_ALPHA_SHADE = "alpha_shade"
    const val STYLE_GLASS_CAPSULE = "glass_capsule"
    const val STYLE_MODULAR_PODS = "modular_pods"
    const val STYLE_HORIZON_GAUGE = "horizon_gauge"

    private const val PLUME_MARGIN_DP = 28f
    private const val V_PLUME_MARGIN_DP = 4f

    val TELEMETRY_HUD_STYLES: List<Pair<String, String>> = listOf(
        STYLE_ALPHA_SHADE to "Alpha Shade",
        STYLE_GLASS_CAPSULE to "Glass Capsule",
        STYLE_MODULAR_PODS to "Modular Pods",
        STYLE_HORIZON_GAUGE to "Micro Gauges"
    )

    fun localizedStyleLabel(styleKey: String): String = when (normalizeStyleKey(styleKey)) {
        STYLE_ALPHA_SHADE -> lsString(R.string.thud_style_alpha_shade)
        STYLE_MODULAR_PODS -> lsString(R.string.thud_style_modular_pods)
        STYLE_HORIZON_GAUGE -> lsString(R.string.thud_style_horizon_gauge)
        else -> lsString(R.string.thud_style_glass_capsule)
    }

    fun normalizeStyleKey(raw: String?): String {
        return TELEMETRY_HUD_STYLES.firstOrNull { it.first == raw }?.first ?: STYLE_ALPHA_SHADE
    }

    private val defaultLabelTf = Typeface.create("sans-serif-medium", Typeface.BOLD)
    private val defaultValueTf = Typeface.create("sans-serif-black", Typeface.BOLD)

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = defaultLabelTf }
    private val labelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = defaultLabelTf
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(235, 0, 0, 0)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = defaultValueTf }
    private val valueStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = defaultValueTf
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(242, 0, 0, 0)
    }
    private val tempRect = RectF()
    private var cachedGlassShader: LinearGradient? = null
    private var cachedGlassTop = Float.NaN
    private var cachedGlassBottom = Float.NaN
    private var cachedGlassInner = 0
    private var cachedGlassOuter = 0

    private fun resolveHudScale(context: Context): Float {
        val pct = context.defaultPrefs().getInt(
            LightspeedPreferences.KEY_TELEMETRY_HUD_SCALE,
            LightspeedPreferences.DEFAULT_TELEMETRY_HUD_SCALE
        ).coerceIn(75, 165)
        return pct / 100f
    }

    private fun applyTypeface(context: Context) {
        val fontKey = context.defaultPrefs().getString(
            LightspeedPreferences.KEY_TELEMETRY_HUD_FONT,
            LightspeedPreferences.DEFAULT_TELEMETRY_HUD_FONT
        ) ?: LightspeedPreferences.DEFAULT_TELEMETRY_HUD_FONT

        val lblTf: Typeface
        val valTf: Typeface
        if (fontKey.isNotBlank() && fontKey != "system_default") {
            lblTf = DeviceFontScanner.resolveAndroidTypeface(fontKey, isBold = false) ?: defaultLabelTf
            valTf = DeviceFontScanner.resolveAndroidTypeface(fontKey, isBold = true) ?: defaultValueTf
        } else {
            lblTf = DeviceFontScanner.resolveGlobalOverlayTypeface(context, isBold = false, fallback = defaultLabelTf)
            valTf = DeviceFontScanner.resolveGlobalOverlayTypeface(context, isBold = true, fallback = defaultValueTf)
        }
        labelPaint.typeface = lblTf
        labelStrokePaint.typeface = lblTf
        valuePaint.typeface = valTf
        valueStrokePaint.typeface = valTf
    }

    private fun resolveToneColor(severity: TelemetrySeverity, palette: CanvasThemePalette, defaultPrimary: Int): Int {
        return when (severity) {
            TelemetrySeverity.ACCENT -> palette.primaryAccentArgb or Color.BLACK
            TelemetrySeverity.OK -> LightspeedTokens.StatusOk.toArgb()
            TelemetrySeverity.WARN -> LightspeedTokens.StatusWarning.toArgb()
            TelemetrySeverity.DANGER -> LightspeedTokens.StatusDanger.toArgb()
            TelemetrySeverity.NORMAL -> defaultPrimary
        }
    }

    private fun isLightPanelColor(argb: Int): Boolean {
        val lum = (Color.red(argb) * 299 + Color.green(argb) * 587 + Color.blue(argb) * 114) / 1000
        return lum >= 150
    }

    private fun configureTypography(context: Context, styleKey: String, d: Float, scale: Float) {
        applyTypeface(context)
        when (styleKey) {
            STYLE_ALPHA_SHADE -> {
                labelPaint.textSize = 8.0f * d * scale
                labelStrokePaint.textSize = 8.0f * d * scale
                labelStrokePaint.strokeWidth = 2.6f * d * scale.coerceAtMost(1.35f)
                valuePaint.textSize = 11.2f * d * scale
                valueStrokePaint.textSize = 11.2f * d * scale
                valueStrokePaint.strokeWidth = 3.2f * d * scale.coerceAtMost(1.35f)
            }
            STYLE_MODULAR_PODS -> {
                labelPaint.textSize = 8.0f * d * scale
                valuePaint.textSize = 10.6f * d * scale
            }
            STYLE_HORIZON_GAUGE -> {
                labelPaint.textSize = 7.8f * d * scale
                valuePaint.textSize = 10.6f * d * scale
            }
            else -> {
                labelPaint.textSize = 8.0f * d * scale
                valuePaint.textSize = 10.8f * d * scale
            }
        }
    }

    private fun measureItemWidth(styleKey: String, item: TelemetryMetricItem, d: Float, scale: Float): Float {
        val lblW = labelPaint.measureText(item.label)
        val valW = valuePaint.measureText(item.value)
        return when (styleKey) {
            STYLE_ALPHA_SHADE -> lblW + (3.2f * d * scale) + valW + (8f * d * scale)
            STYLE_MODULAR_PODS -> lblW + (3.5f * d * scale) + valW + (12f * d * scale)
            STYLE_HORIZON_GAUGE -> lblW + (3.0f * d * scale) + valW + (13.5f * d * scale)
            else -> lblW + (3.5f * d * scale) + valW + (11f * d * scale)
        }
    }

    fun measureHud(
        context: Context,
        styleKey: String,
        items: List<TelemetryMetricItem>,
        density: Float
    ): HudMeasureSpec {
        val style = normalizeStyleKey(styleKey)
        val scale = resolveHudScale(context)
        configureTypography(context, style, density, scale)

        val handleW = (20f * density * scale.coerceIn(0.88f, 1.32f)).roundToInt()
        val bodyPad = 8f * density * scale
        val gap = when (style) {
            STYLE_MODULAR_PODS -> 4.5f * density * scale
            STYLE_ALPHA_SHADE -> 6.0f * density * scale
            STYLE_HORIZON_GAUGE -> 5.0f * density * scale
            else -> 4.0f * density * scale
        }
        val widths = FloatArray(items.size)
        var itemsW = 0f
        items.forEachIndexed { idx, item ->
            val w = measureItemWidth(style, item, density, scale)
            widths[idx] = w
            itemsW += w
            if (idx < items.lastIndex) itemsW += gap
        }
        val minW = (84f * density * scale.coerceAtMost(1.25f)).roundToInt()
        val coreW = (handleW * 2f + bodyPad + itemsW).roundToInt().coerceAtLeast(minW)
        val baseCoreH = if (style == STYLE_ALPHA_SHADE) 34f else 28f
        val coreH = (baseCoreH * density * scale.coerceIn(0.85f, 1.55f)).roundToInt()
        val plumeX = (PLUME_MARGIN_DP * density * scale.coerceIn(0.9f, 1.25f)).roundToInt()
        val plumeY = (V_PLUME_MARGIN_DP * density).roundToInt()

        return HudMeasureSpec(
            totalWidthPx = coreW + plumeX * 2,
            totalHeightPx = coreH + plumeY * 2,
            coreWidthPx = coreW,
            coreHeightPx = coreH,
            plumeMarginXPx = plumeX,
            plumeMarginYPx = plumeY,
            handleWidthPx = minOf(handleW, (coreW / 3).coerceAtLeast(1)),
            itemWidths = widths,
            totalItemsWidth = itemsW
        )
    }

    fun drawHud(
        canvas: Canvas,
        context: Context,
        styleKey: String,
        items: List<TelemetryMetricItem>,
        spec: HudMeasureSpec,
        pressState: HandlePressState,
        thruster: ThrusterRenderState,
        d: Float
    ) {
        val style = normalizeStyleKey(styleKey)
        val scale = resolveHudScale(context)
        val snapshot = DeckThemeManager.getSnapshot(context)
        val palette = snapshot.resolveCanvasPalette(context, LightspeedPreferences.COCKPIT_COLOR_MODE_THEME_12)
        val accent = palette.primaryAccentArgb or Color.BLACK
        val secondaryAccent = palette.secondaryAccentArgb or Color.BLACK
        val isLightSurface = isLightPanelColor(palette.panelOuterArgb)

        configureTypography(context, style, d, scale)

        val coreLeft = spec.plumeMarginXPx.toFloat()
        val coreRight = coreLeft + spec.coreWidthPx.toFloat()
        val coreTop = spec.plumeMarginYPx.toFloat()
        val coreBottom = coreTop + spec.coreHeightPx.toFloat()
        val handleW = spec.handleWidthPx.toFloat()

        when (style) {
            STYLE_ALPHA_SHADE -> drawAlphaShadeHud(
                canvas, items, spec, coreLeft, coreRight, coreTop, coreBottom, handleW, pressState, thruster, palette, accent, d, scale
            )
            STYLE_MODULAR_PODS -> drawModularPodsHud(
                canvas, items, spec, coreLeft, coreRight, coreTop, coreBottom, handleW, pressState, thruster, palette, accent, secondaryAccent, isLightSurface, d, scale
            )
            STYLE_HORIZON_GAUGE -> drawHorizonGaugeHud(
                canvas, items, spec, coreLeft, coreRight, coreTop, coreBottom, handleW, pressState, thruster, palette, accent, isLightSurface, d, scale
            )
            else -> drawGlassCapsuleHud(
                canvas, items, spec, coreLeft, coreRight, coreTop, coreBottom, handleW, pressState, thruster, palette, accent, secondaryAccent, isLightSurface, d, scale
            )
        }
    }

    private fun resolveItemWidths(
        spec: HudMeasureSpec,
        style: String,
        items: List<TelemetryMetricItem>,
        d: Float,
        scale: Float,
        gap: Float
    ): Pair<FloatArray, Float> {
        if (spec.itemWidths.size == items.size) {
            return spec.itemWidths to spec.totalItemsWidth
        }
        val widths = FloatArray(items.size)
        var totalW = 0f
        for (i in items.indices) {
            val w = measureItemWidth(style, items[i], d, scale)
            widths[i] = w
            totalW += w
            if (i < items.lastIndex) totalW += gap
        }
        return widths to totalW
    }

    private fun drawAlphaShadeHud(
        canvas: Canvas,
        items: List<TelemetryMetricItem>,
        spec: HudMeasureSpec,
        coreLeft: Float,
        coreRight: Float,
        coreTop: Float,
        coreBottom: Float,
        handleW: Float,
        pressState: HandlePressState,
        thruster: ThrusterRenderState,
        palette: CanvasThemePalette,
        accent: Int,
        d: Float,
        scale: Float
    ) {
        val coreH = coreBottom - coreTop
        val cy = (coreTop + coreBottom) * 0.5f
        LightspeedTelemetryHudHandles.drawFramelessSideHandle(
            canvas, true, coreLeft, coreRight, handleW, cy, accent, pressState == HandlePressState.LEFT, thruster, d, scale
        )
        LightspeedTelemetryHudHandles.drawFramelessSideHandle(
            canvas, false, coreLeft, coreRight, handleW, cy, accent, pressState == HandlePressState.RIGHT, thruster, d, scale
        )

        val bodyLeft = coreLeft + handleW + 4f * d
        val bodyRight = (coreRight - handleW - 4f * d).coerceAtLeast(bodyLeft + 1f)
        val gap = 6.0f * d * scale
        val (widths, totalContentW) = resolveItemWidths(spec, STYLE_ALPHA_SHADE, items, d, scale, gap)
        var curX = bodyLeft + ((bodyRight - bodyLeft - totalContentW).coerceAtLeast(0f) * 0.5f)
        val fm = valuePaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) * 0.5f - 1.2f * d * scale

        items.forEachIndexed { idx, item ->
            val iw = widths[idx]
            LightspeedTelemetryHudHandles.drawShadeCloud(canvas, curX + iw * 0.5f, cy, iw * 0.5f + 10f * d * scale, coreH * 0.46f)

            val lblX = curX + 4f * d * scale
            val valX = lblX + labelPaint.measureText(item.label) + 3.2f * d * scale
            val tone = resolveToneColor(item.severity, palette, Color.WHITE)

            canvas.drawText(item.label, lblX, baseline, labelStrokePaint)
            labelPaint.color = Color.argb(215, 224, 232, 244)
            canvas.drawText(item.label, lblX, baseline, labelPaint)

            canvas.drawText(item.value, valX, baseline, valueStrokePaint)
            valuePaint.color = tone
            canvas.drawText(item.value, valX, baseline, valuePaint)

            val barLeft = lblX
            val barRight = curX + iw - 4f * d * scale
            val barTop = baseline + 3.5f * d * scale
            val barH = 1.8f * d * scale.coerceAtMost(1.3f)
            if (barRight > barLeft + 4f * d) {
                barPaint.color = Color.argb(210, 0, 0, 0)
                tempRect.set(barLeft - 0.8f * d, barTop - 0.6f * d, barRight + 0.8f * d, barTop + barH + 0.6f * d)
                canvas.drawRoundRect(tempRect, barH, barH, barPaint)

                barPaint.color = Color.argb(72, 255, 255, 255)
                tempRect.set(barLeft, barTop, barRight, barTop + barH)
                canvas.drawRoundRect(tempRect, barH, barH, barPaint)

                val fillW = (barRight - barLeft) * item.fraction.coerceIn(0.08f, 1f)
                barPaint.color = tone
                tempRect.set(barLeft, barTop, barLeft + fillW, barTop + barH)
                canvas.drawRoundRect(tempRect, barH, barH, barPaint)
            }
            curX += iw + gap
        }
    }

    private fun drawGlassCapsuleHud(
        canvas: Canvas,
        items: List<TelemetryMetricItem>,
        spec: HudMeasureSpec,
        coreLeft: Float,
        coreRight: Float,
        coreTop: Float,
        coreBottom: Float,
        handleW: Float,
        pressState: HandlePressState,
        thruster: ThrusterRenderState,
        palette: CanvasThemePalette,
        accent: Int,
        secondaryAccent: Int,
        isLightSurface: Boolean,
        d: Float,
        scale: Float
    ) {
        val inset = 0.8f * d
        val coreH = coreBottom - coreTop
        val radius = coreH * 0.5f
        tempRect.set(coreLeft + inset, coreTop + inset, coreRight - inset, coreBottom - inset)

        val shader = cachedGlassShader
        val needsNewShader = shader == null ||
            cachedGlassTop != coreTop ||
            cachedGlassBottom != coreBottom ||
            cachedGlassInner != palette.panelInnerArgb ||
            cachedGlassOuter != palette.panelOuterArgb
        fillPaint.shader = if (needsNewShader) {
            LinearGradient(
                0f, coreTop, 0f, coreBottom,
                intArrayOf(palette.panelInnerArgb, palette.panelOuterArgb),
                null,
                Shader.TileMode.CLAMP
            ).also {
                cachedGlassShader = it
                cachedGlassTop = coreTop
                cachedGlassBottom = coreBottom
                cachedGlassInner = palette.panelInnerArgb
                cachedGlassOuter = palette.panelOuterArgb
            }
        } else {
            shader
        }
        canvas.drawRoundRect(tempRect, radius, radius, fillPaint)
        fillPaint.shader = null

        strokePaint.strokeWidth = 0.9f * d
        strokePaint.color = (palette.borderArgb and 0x00FFFFFF) or (0x88 shl 24)
        canvas.drawRoundRect(tempRect, radius, radius, strokePaint)

        val textPrimary = if (isLightSurface) Color.argb(242, 20, 24, 30) else Color.argb(246, 244, 248, 255)
        val textMuted = if (isLightSurface) Color.argb(175, 42, 48, 58) else Color.argb(180, 208, 218, 232)
        val dividerColor = if (isLightSurface) Color.argb(42, 0, 0, 0) else Color.argb(48, 255, 255, 255)

        LightspeedTelemetryHudHandles.drawRecessedSideHandle(
            canvas, true, coreLeft, coreRight, handleW, coreTop, coreBottom, accent, pressState == HandlePressState.LEFT, isLightSurface, thruster, d, scale
        )
        LightspeedTelemetryHudHandles.drawRecessedSideHandle(
            canvas, false, coreLeft, coreRight, handleW, coreTop, coreBottom, secondaryAccent, pressState == HandlePressState.RIGHT, isLightSurface, thruster, d, scale
        )

        val bodyLeft = coreLeft + handleW + 4f * d
        val bodyRight = (coreRight - handleW - 4f * d).coerceAtLeast(bodyLeft + 1f)
        val gap = 4.0f * d * scale
        val (widths, totalContentW) = resolveItemWidths(spec, STYLE_GLASS_CAPSULE, items, d, scale, gap)
        var curX = bodyLeft + ((bodyRight - bodyLeft - totalContentW).coerceAtLeast(0f) * 0.5f)
        val cy = (coreTop + coreBottom) * 0.5f
        val fm = valuePaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) * 0.5f - 1.0f * d * scale

        items.forEachIndexed { idx, item ->
            val iw = widths[idx]
            val lblX = curX + 5f * d * scale
            val valX = lblX + labelPaint.measureText(item.label) + 3.5f * d * scale
            val tone = resolveToneColor(item.severity, palette, textPrimary)

            labelPaint.color = textMuted
            canvas.drawText(item.label, lblX, baseline, labelPaint)
            valuePaint.color = tone
            canvas.drawText(item.value, valX, baseline, valuePaint)

            val barL = lblX
            val barR = curX + iw - 5f * d * scale
            val barH = 1.5f * d * scale.coerceAtMost(1.3f)
            val barTop = coreBottom - 4.2f * d * scale.coerceAtMost(1.25f)
            if (barR > barL + 4f * d) {
                barPaint.color = (tone and 0x00FFFFFF) or (0x45 shl 24)
                tempRect.set(barL, barTop, barR, barTop + barH)
                canvas.drawRoundRect(tempRect, d, d, barPaint)

                val fillW = (barR - barL) * item.fraction.coerceIn(0.08f, 1f)
                barPaint.color = tone
                tempRect.set(barL, barTop, barL + fillW, barTop + barH)
                canvas.drawRoundRect(tempRect, d, d, barPaint)
            }

            curX += iw
            if (idx < items.lastIndex) {
                val divX = curX + gap * 0.5f
                strokePaint.strokeWidth = 0.8f * d
                strokePaint.color = dividerColor
                canvas.drawLine(divX, coreTop + 6.5f * d, divX, coreBottom - 6.5f * d, strokePaint)
                curX += gap
            }
        }
    }

    private fun drawModularPodsHud(
        canvas: Canvas,
        items: List<TelemetryMetricItem>,
        spec: HudMeasureSpec,
        coreLeft: Float,
        coreRight: Float,
        coreTop: Float,
        coreBottom: Float,
        handleW: Float,
        pressState: HandlePressState,
        thruster: ThrusterRenderState,
        palette: CanvasThemePalette,
        accent: Int,
        secondaryAccent: Int,
        isLightSurface: Boolean,
        d: Float,
        scale: Float
    ) {
        val textPrimary = if (isLightSurface) Color.argb(242, 20, 24, 30) else Color.argb(246, 244, 248, 255)
        val textMuted = if (isLightSurface) Color.argb(175, 42, 48, 58) else Color.argb(180, 208, 218, 232)
        val podR = 9f * d * scale.coerceIn(0.85f, 1.3f)
        val vInset = 1.5f * d

        LightspeedTelemetryHudHandles.drawHandlePod(
            canvas, true, coreLeft, coreRight, handleW, coreTop, coreBottom, vInset, podR, palette, accent, pressState == HandlePressState.LEFT, thruster, d, scale
        )
        LightspeedTelemetryHudHandles.drawHandlePod(
            canvas, false, coreLeft, coreRight, handleW, coreTop, coreBottom, vInset, podR, palette, secondaryAccent, pressState == HandlePressState.RIGHT, thruster, d, scale
        )

        val bodyLeft = coreLeft + handleW + 4f * d
        val bodyRight = (coreRight - handleW - 4f * d).coerceAtLeast(bodyLeft + 1f)
        val gap = 4.5f * d * scale
        val (widths, totalContentW) = resolveItemWidths(spec, STYLE_MODULAR_PODS, items, d, scale, gap)
        var curX = bodyLeft + ((bodyRight - bodyLeft - totalContentW).coerceAtLeast(0f) * 0.5f)
        val cy = (coreTop + coreBottom) * 0.5f
        val fm = valuePaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) * 0.5f - 1.0f * d * scale

        items.forEachIndexed { idx, item ->
            val iw = widths[idx]
            val tone = resolveToneColor(item.severity, palette, textPrimary)
            tempRect.set(curX, coreTop + vInset, curX + iw, coreBottom - vInset)

            fillPaint.color = palette.panelOuterArgb
            canvas.drawRoundRect(tempRect, podR, podR, fillPaint)
            strokePaint.strokeWidth = 0.8f * d
            strokePaint.color = (palette.borderArgb and 0x00FFFFFF) or (0x66 shl 24)
            canvas.drawRoundRect(tempRect, podR, podR, strokePaint)

            val lblX = curX + 6f * d * scale
            val valX = lblX + labelPaint.measureText(item.label) + 3.5f * d * scale
            labelPaint.color = textMuted
            canvas.drawText(item.label, lblX, baseline, labelPaint)
            valuePaint.color = tone
            canvas.drawText(item.value, valX, baseline, valuePaint)

            val barL = curX + 6f * d * scale
            val barR = curX + iw - 6f * d * scale
            val barY = coreBottom - vInset - 2.6f * d * scale.coerceAtMost(1.25f)
            val barH = 1.5f * d * scale.coerceAtMost(1.3f)
            if (barR > barL + 4f * d) {
                val fillW = (barR - barL) * item.fraction.coerceIn(0.08f, 1f)
                barPaint.color = tone
                tempRect.set(barL, barY, barL + fillW, barY + barH)
                canvas.drawRoundRect(tempRect, d, d, barPaint)
            }
            curX += iw + gap
        }
    }

    private fun drawHorizonGaugeHud(
        canvas: Canvas,
        items: List<TelemetryMetricItem>,
        spec: HudMeasureSpec,
        coreLeft: Float,
        coreRight: Float,
        coreTop: Float,
        coreBottom: Float,
        handleW: Float,
        pressState: HandlePressState,
        thruster: ThrusterRenderState,
        palette: CanvasThemePalette,
        accent: Int,
        isLightSurface: Boolean,
        d: Float,
        scale: Float
    ) {
        val inset = 0.8f * d
        val r = 10f * d * scale.coerceIn(0.85f, 1.3f)
        tempRect.set(coreLeft + inset, coreTop + inset, coreRight - inset, coreBottom - inset)
        fillPaint.color = palette.panelOuterArgb
        canvas.drawRoundRect(tempRect, r, r, fillPaint)
        strokePaint.strokeWidth = 0.9f * d
        strokePaint.color = (accent and 0x00FFFFFF) or (0x85 shl 24)
        canvas.drawRoundRect(tempRect, r, r, strokePaint)

        val textPrimary = if (isLightSurface) Color.argb(242, 20, 24, 30) else Color.argb(246, 244, 248, 255)
        val textMuted = if (isLightSurface) Color.argb(175, 42, 48, 58) else Color.argb(180, 208, 218, 232)

        val cy = (coreTop + coreBottom) * 0.5f
        LightspeedTelemetryHudHandles.drawBracketHandle(
            canvas, true, coreLeft, coreRight, handleW, cy, accent, pressState == HandlePressState.LEFT, thruster, d, scale
        )
        LightspeedTelemetryHudHandles.drawBracketHandle(
            canvas, false, coreLeft, coreRight, handleW, cy, accent, pressState == HandlePressState.RIGHT, thruster, d, scale
        )

        val bodyLeft = coreLeft + handleW + 4f * d
        val bodyRight = (coreRight - handleW - 4f * d).coerceAtLeast(bodyLeft + 1f)
        val gap = 5.0f * d * scale
        val (widths, totalContentW) = resolveItemWidths(spec, STYLE_HORIZON_GAUGE, items, d, scale, gap)
        var curX = bodyLeft + ((bodyRight - bodyLeft - totalContentW).coerceAtLeast(0f) * 0.5f)
        val fm = valuePaint.fontMetrics
        val baseline = cy - (fm.ascent + fm.descent) * 0.5f

        items.forEachIndexed { idx, item ->
            val iw = widths[idx]
            val tone = resolveToneColor(item.severity, palette, textPrimary)
            val lblX = curX + 4f * d * scale
            val valX = lblX + labelPaint.measureText(item.label) + 3.0f * d * scale

            labelPaint.color = textMuted
            canvas.drawText(item.label, lblX, baseline, labelPaint)
            valuePaint.color = tone
            canvas.drawText(item.value, valX, baseline, valuePaint)

            val gaugeX = curX + iw - 7.5f * d * scale
            val bars = 4
            val activeBars = (item.fraction * bars).roundToInt().coerceIn(1, bars)
            val baseBottom = cy + 5.5f * d * scale.coerceAtMost(1.3f)
            for (b in 0 until bars) {
                val bx = gaugeX + b * 2.0f * d * scale.coerceAtMost(1.25f)
                val bh = (3.2f + b * 2.2f) * d * scale.coerceAtMost(1.35f)
                barPaint.color = if (b < activeBars) tone else (textMuted and 0x00FFFFFF) or (0x45 shl 24)
                tempRect.set(bx, baseBottom - bh, bx + 1.3f * d * scale.coerceAtMost(1.25f), baseBottom)
                canvas.drawRoundRect(tempRect, d, d, barPaint)
            }
            curX += iw + gap
        }
    }
}
