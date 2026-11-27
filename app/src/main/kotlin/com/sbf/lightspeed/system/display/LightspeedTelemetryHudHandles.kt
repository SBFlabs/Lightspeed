package com.sbf.lightspeed.system.display

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.toArgb
import com.sbf.lightspeed.settings.CanvasThemePalette
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlin.math.roundToInt
import kotlin.math.sin

data class ThrusterRenderState(
    val morph: Float = 0f,
    val leftThrust: Float = 0f,
    val rightThrust: Float = 0f,
    val verticalGimbal: Float = 0f,
    val flamePhase: Float = 0f
)

/**
 * Shared radial alpha-shade cloud, idle side-handles, morphing rocket thruster nozzles,
 * and multi-layered Newtonian exhaust fire plumes for [LightspeedTelemetryHudRenderer].
 */
internal object LightspeedTelemetryHudHandles {

    private val shadeShader = RadialGradient(
        0f, 0f, 1f,
        intArrayOf(
            Color.argb(240, 0, 0, 0),
            Color.argb(198, 0, 0, 0),
            Color.argb(108, 0, 0, 0),
            Color.argb(26, 0, 0, 0),
            Color.argb(0, 0, 0, 0)
        ),
        floatArrayOf(0f, 0.28f, 0.56f, 0.80f, 1f),
        Shader.TileMode.CLAMP
    )
    private val shadeMatrix = Matrix()
    private val shadePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = shadeShader
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val nozzlePath = Path()
    private val plumePath = Path()
    private val tempRect = RectF()
    private val gripRect = RectF()

    fun drawShadeCloud(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        val safeRx = rx.coerceAtLeast(2f)
        val safeRy = ry.coerceAtLeast(2f)
        shadeMatrix.reset()
        shadeMatrix.setScale(safeRx, safeRy)
        shadeMatrix.postTranslate(cx, cy)
        shadeShader.setLocalMatrix(shadeMatrix)
        canvas.drawOval(cx - safeRx, cy - safeRy, cx + safeRx, cy + safeRy, shadePaint)
    }

    fun drawFramelessSideHandle(
        canvas: Canvas,
        isLeft: Boolean,
        coreLeft: Float,
        coreRight: Float,
        handleW: Float,
        cy: Float,
        accent: Int,
        active: Boolean,
        thruster: ThrusterRenderState,
        d: Float,
        scale: Float
    ) {
        val hCx = if (isLeft) (coreLeft + handleW * 0.52f) else (coreRight - handleW * 0.52f)
        drawShadeCloud(canvas, hCx, cy, handleW * 0.72f + 5f * d, 14f * d * scale.coerceAtMost(1.25f))

        val m = thruster.morph.coerceIn(0f, 1f)
        if (m < 0.85f) {
            val pillW = (if (active) 4.2f * d else 3.2f * d) * (1f - m * 0.5f)
            val pillH = 12.5f * d * scale.coerceIn(0.85f, 1.3f) * (1f - m * 0.25f)
            val aFactor = (1f - m).coerceIn(0f, 1f)
            fillPaint.color = Color.argb((235 * aFactor).toInt(), 0, 0, 0)
            gripRect.set(
                hCx - pillW * 0.5f - 1.2f * d,
                cy - pillH * 0.5f - 1.2f * d,
                hCx + pillW * 0.5f + 1.2f * d,
                cy + pillH * 0.5f + 1.2f * d
            )
            canvas.drawRoundRect(gripRect, pillW, pillW, fillPaint)

            val tint = if (active) Color.WHITE else accent
            fillPaint.color = Color.argb((255 * aFactor).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
            gripRect.set(hCx - pillW * 0.5f, cy - pillH * 0.5f, hCx + pillW * 0.5f, cy + pillH * 0.5f)
            canvas.drawRoundRect(gripRect, pillW, pillW, fillPaint)
        }

        if (m > 0.04f) {
            val sideThrust = if (isLeft) thruster.leftThrust else thruster.rightThrust
            val nozzleExitX = if (isLeft) coreLeft + 1.5f * d else coreRight - 1.5f * d
            drawRocketThrusterAndPlume(
                canvas = canvas,
                isLeft = isLeft,
                mountCenterX = hCx,
                exitX = nozzleExitX,
                cy = cy,
                handleW = handleW,
                accent = accent,
                morph = m,
                thrust = sideThrust,
                gimbal = thruster.verticalGimbal,
                phase = thruster.flamePhase,
                drawDarkOutline = true,
                d = d,
                scale = scale
            )
        }
    }

    fun drawRecessedSideHandle(
        canvas: Canvas,
        isLeft: Boolean,
        coreLeft: Float,
        coreRight: Float,
        handleW: Float,
        coreTop: Float,
        coreBottom: Float,
        accent: Int,
        active: Boolean,
        isLightSurface: Boolean,
        thruster: ThrusterRenderState,
        d: Float,
        scale: Float
    ) {
        val h = coreBottom - coreTop
        val cx = if (isLeft) (coreLeft + handleW * 0.54f) else (coreRight - handleW * 0.54f)
        val cy = (coreTop + coreBottom) * 0.5f
        val capR = (h - 6f * d) * 0.5f
        fillPaint.color = if (active) {
            (accent and 0x00FFFFFF) or (0x55 shl 24)
        } else if (isLightSurface) {
            Color.argb(26, 0, 0, 0)
        } else {
            Color.argb(38, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, capR, fillPaint)

        val m = thruster.morph.coerceIn(0f, 1f)
        if (m < 0.85f) {
            val aInt = ((1f - m) * 255f).toInt().coerceIn(0, 255)
            val tint = if (active) Color.WHITE else accent
            fillPaint.color = Color.argb(aInt, Color.red(tint), Color.green(tint), Color.blue(tint))
            val barH = 8.5f * d * scale.coerceIn(0.85f, 1.3f)
            val barW = 1.6f * d
            gripRect.set(cx - 2.3f * d, cy - barH * 0.5f, cx - 2.3f * d + barW, cy + barH * 0.5f)
            canvas.drawRoundRect(gripRect, barW, barW, fillPaint)
            gripRect.set(cx + 0.7f * d, cy - barH * 0.5f, cx + 0.7f * d + barW, cy + barH * 0.5f)
            canvas.drawRoundRect(gripRect, barW, barW, fillPaint)
        }

        if (m > 0.04f) {
            val sideThrust = if (isLeft) thruster.leftThrust else thruster.rightThrust
            val nozzleExitX = if (isLeft) coreLeft + 1.5f * d else coreRight - 1.5f * d
            drawRocketThrusterAndPlume(
                canvas, isLeft, cx, nozzleExitX, cy, handleW, accent, m, sideThrust, thruster.verticalGimbal, thruster.flamePhase, false, d, scale
            )
        }
    }

    fun drawHandlePod(
        canvas: Canvas,
        isLeft: Boolean,
        coreLeft: Float,
        coreRight: Float,
        handleW: Float,
        coreTop: Float,
        coreBottom: Float,
        vInset: Float,
        podR: Float,
        palette: CanvasThemePalette,
        accent: Int,
        active: Boolean,
        thruster: ThrusterRenderState,
        d: Float,
        scale: Float
    ) {
        val left = if (isLeft) coreLeft + 1f * d else (coreRight - handleW + 1f * d)
        val right = if (isLeft) (coreLeft + handleW - 1f * d) else (coreRight - 1f * d)
        tempRect.set(left, coreTop + vInset, right, coreBottom - vInset)
        fillPaint.color = if (active) (accent and 0x00FFFFFF) or (0xCC shl 24) else palette.panelInnerArgb
        canvas.drawRoundRect(tempRect, podR, podR, fillPaint)
        strokePaint.strokeWidth = 0.9f * d
        strokePaint.color = (accent and 0x00FFFFFF) or (0x99 shl 24)
        canvas.drawRoundRect(tempRect, podR, podR, strokePaint)

        val cx = (left + right) * 0.5f
        val cy = (coreTop + coreBottom) * 0.5f
        val m = thruster.morph.coerceIn(0f, 1f)
        if (m < 0.85f) {
            val tint = if (active) Color.WHITE else accent
            fillPaint.color = Color.argb(((1f - m) * 255f).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
            val pillW = 2.6f * d
            val pillH = 10f * d * scale.coerceIn(0.85f, 1.3f)
            gripRect.set(cx - pillW * 0.5f, cy - pillH * 0.5f, cx + pillW * 0.5f, cy + pillH * 0.5f)
            canvas.drawRoundRect(gripRect, pillW, pillW, fillPaint)
        }

        if (m > 0.04f) {
            val sideThrust = if (isLeft) thruster.leftThrust else thruster.rightThrust
            val nozzleExitX = if (isLeft) coreLeft + 1.5f * d else coreRight - 1.5f * d
            drawRocketThrusterAndPlume(
                canvas, isLeft, cx, nozzleExitX, cy, handleW, accent, m, sideThrust, thruster.verticalGimbal, thruster.flamePhase, false, d, scale
            )
        }
    }

    fun drawBracketHandle(
        canvas: Canvas,
        isLeft: Boolean,
        coreLeft: Float,
        coreRight: Float,
        handleW: Float,
        cy: Float,
        accent: Int,
        active: Boolean,
        thruster: ThrusterRenderState,
        d: Float,
        scale: Float
    ) {
        val cx = if (isLeft) (coreLeft + handleW * 0.5f) else (coreRight - handleW * 0.5f)
        val m = thruster.morph.coerceIn(0f, 1f)
        if (m < 0.85f) {
            strokePaint.strokeWidth = if (active) 2.2f * d else 1.7f * d
            val tint = if (active) Color.WHITE else accent
            strokePaint.color = Color.argb(((1f - m) * 255f).toInt(), Color.red(tint), Color.green(tint), Color.blue(tint))
            val dir = if (isLeft) 1f else -1f
            val tipX = cx - dir * 2.5f * d
            val armX = cx + dir * 2.5f * d
            val bh = 5.5f * d * scale.coerceIn(0.85f, 1.25f)
            canvas.drawLine(armX, cy - bh, tipX, cy - bh * 0.6f, strokePaint)
            canvas.drawLine(tipX, cy - bh * 0.6f, tipX, cy + bh * 0.6f, strokePaint)
            canvas.drawLine(tipX, cy + bh * 0.6f, armX, cy + bh, strokePaint)
        }

        if (m > 0.04f) {
            val sideThrust = if (isLeft) thruster.leftThrust else thruster.rightThrust
            val nozzleExitX = if (isLeft) coreLeft + 1.5f * d else coreRight - 1.5f * d
            drawRocketThrusterAndPlume(
                canvas, isLeft, cx, nozzleExitX, cy, handleW, accent, m, sideThrust, thruster.verticalGimbal, thruster.flamePhase, false, d, scale
            )
        }
    }

    private fun drawRocketThrusterAndPlume(
        canvas: Canvas,
        isLeft: Boolean,
        mountCenterX: Float,
        exitX: Float,
        cy: Float,
        handleW: Float,
        accent: Int,
        morph: Float,
        thrust: Float,
        gimbal: Float,
        phase: Float,
        drawDarkOutline: Boolean,
        d: Float,
        scale: Float
    ) {
        // Exhaust dir: Left thruster shoots LEFT (-1); Right thruster shoots RIGHT (+1)
        val dir = if (isLeft) -1f else 1f
        val sc = scale.coerceIn(0.85f, 1.28f)
        val throatX = mountCenterX - dir * (2.5f * d * sc * morph)
        val bellExitX = exitX + dir * (1.2f * d * morph)
        val throatHalfH = 2.4f * d * sc * morph
        val bellHalfH = (5.4f * d * sc * morph).coerceAtMost(handleW * 0.36f)

        // 1. Multi-layer Newtonian fire plume behind & shooting out of the nozzle bell
        if (thrust > 0.02f && morph > 0.2f) {
            drawRocketExhaustPlume(
                canvas = canvas,
                originX = bellExitX - dir * 1.2f * d,
                cy = cy,
                dir = dir,
                throatHalfH = bellHalfH * 0.86f,
                thrust = thrust.coerceIn(0f, 1f) * morph,
                gimbal = gimbal,
                phase = phase,
                d = d,
                scale = sc
            )
        }

        // 2. Sculpted thruster bell nozzle polygon
        nozzlePath.reset()
        nozzlePath.moveTo(throatX, cy - throatHalfH)
        nozzlePath.lineTo(bellExitX, cy - bellHalfH)
        nozzlePath.lineTo(bellExitX, cy + bellHalfH)
        nozzlePath.lineTo(throatX, cy + throatHalfH)
        nozzlePath.close()

        val nozzleAlpha = (240f * morph).toInt().coerceIn(0, 255)
        if (drawDarkOutline) {
            strokePaint.strokeWidth = 2.4f * d
            strokePaint.color = Color.argb((220f * morph).toInt(), 0, 0, 0)
            canvas.drawPath(nozzlePath, strokePaint)
        }
        fillPaint.color = Color.argb(nozzleAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
        canvas.drawPath(nozzlePath, fillPaint)
        strokePaint.strokeWidth = 1.0f * d
        strokePaint.color = Color.argb((210f * morph).toInt(), 255, 255, 255)
        canvas.drawPath(nozzlePath, strokePaint)

        // Glowing nozzle lip throat rim
        val danger = LightspeedTokens.StatusDanger.toArgb()
        val glowCol = if (thrust > 0.08f) LightspeedTokens.StatusWarning.toArgb() else danger
        strokePaint.strokeWidth = 1.8f * d
        strokePaint.color = Color.argb((190f * morph).toInt(), Color.red(glowCol), Color.green(glowCol), Color.blue(glowCol))
        canvas.drawLine(bellExitX, cy - bellHalfH * 0.78f, bellExitX, cy + bellHalfH * 0.78f, strokePaint)
    }

    private fun drawRocketExhaustPlume(
        canvas: Canvas,
        originX: Float,
        cy: Float,
        dir: Float,
        throatHalfH: Float,
        thrust: Float,
        gimbal: Float,
        phase: Float,
        d: Float,
        scale: Float
    ) {
        val flicker = 0.84f + 0.16f * sin(phase * 19f) + 0.08f * sin(phase * 31f + dir)
        val maxLen = (8f * d + 19f * d * thrust) * scale * flicker
        val tiltY = gimbal.coerceIn(-0.45f, 0.45f) * maxLen * 0.55f

        val warnArgb = LightspeedTokens.StatusWarning.toArgb()
        val dangerArgb = LightspeedTokens.StatusDanger.toArgb()
        val goldArgb = LightspeedTokens.StatusGold.toArgb()

        // Thermal glow halo at nozzle exit
        fillPaint.color = Color.argb((82f * thrust).roundToInt().coerceIn(0, 110), Color.red(warnArgb), Color.green(warnArgb), Color.blue(warnArgb))
        tempRect.set(
            originX + dir * (maxLen * 0.35f) - maxLen * 0.45f,
            cy + tiltY * 0.25f - throatHalfH * 1.45f,
            originX + dir * (maxLen * 0.35f) + maxLen * 0.45f,
            cy + tiltY * 0.25f + throatHalfH * 1.45f
        )
        canvas.drawOval(tempRect, fillPaint)

        // Outer crimson-orange afterburner cone
        drawFlameCone(
            canvas = canvas,
            originX = originX,
            cy = cy,
            dir = dir,
            halfBaseH = throatHalfH * (1.02f + 0.25f * thrust),
            tipX = originX + dir * maxLen,
            tipY = cy + tiltY,
            color = Color.argb((232f * thrust.coerceAtLeast(0.45f)).roundToInt().coerceIn(0, 240), Color.red(dangerArgb), Color.green(dangerArgb), Color.blue(dangerArgb))
        )

        // Mid golden-amber supersonic jet
        drawFlameCone(
            canvas = canvas,
            originX = originX,
            cy = cy,
            dir = dir,
            halfBaseH = throatHalfH * 0.72f,
            tipX = originX + dir * (maxLen * 0.68f),
            tipY = cy + tiltY * 0.65f,
            color = Color.argb((240f * thrust.coerceAtLeast(0.55f)).roundToInt().coerceIn(0, 245), Color.red(goldArgb), Color.green(goldArgb), Color.blue(goldArgb))
        )

        // Inner white-hot plasma spike
        drawFlameCone(
            canvas = canvas,
            originX = originX,
            cy = cy,
            dir = dir,
            halfBaseH = throatHalfH * 0.38f,
            tipX = originX + dir * (maxLen * 0.38f),
            tipY = cy + tiltY * 0.32f,
            color = Color.argb((245f * thrust.coerceAtLeast(0.6f)).roundToInt().coerceIn(0, 250), 255, 255, 255)
        )

        // Two trailing ember sparks during strong thrust
        if (thrust > 0.38f) {
            fillPaint.color = Color.argb((210f * thrust).roundToInt().coerceIn(0, 230), Color.red(goldArgb), Color.green(goldArgb), Color.blue(goldArgb))
            val s1X = originX + dir * (maxLen * (1.05f + 0.12f * sin(phase * 23f)))
            val s1Y = cy + tiltY + (1.8f * d * sin(phase * 17f))
            canvas.drawCircle(s1X, s1Y, 1.3f * d, fillPaint)
            val s2X = originX + dir * (maxLen * (0.88f + 0.14f * sin(phase * 29f + 1.2f)))
            val s2Y = cy + tiltY * 0.85f - (2.1f * d * sin(phase * 21f))
            canvas.drawCircle(s2X, s2Y, 1.0f * d, fillPaint)
        }
    }

    private fun drawFlameCone(
        canvas: Canvas,
        originX: Float,
        cy: Float,
        dir: Float,
        halfBaseH: Float,
        tipX: Float,
        tipY: Float,
        color: Int
    ) {
        val midX = originX + (tipX - originX) * 0.48f
        val ctrlSpread = halfBaseH * 0.58f
        plumePath.reset()
        plumePath.moveTo(originX, cy - halfBaseH)
        plumePath.quadTo(midX, cy + (tipY - cy) * 0.3f - ctrlSpread, tipX, tipY)
        plumePath.quadTo(midX, cy + (tipY - cy) * 0.3f + ctrlSpread, originX, cy + halfBaseH)
        plumePath.close()
        fillPaint.color = color
        canvas.drawPath(plumePath, fillPaint)
    }
}
