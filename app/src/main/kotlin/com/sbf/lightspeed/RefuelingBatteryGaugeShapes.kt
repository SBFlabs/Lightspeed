package com.sbf.lightspeed

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Canonical battery gauge styles for Refueling Bay — layered sculpted instruments
 * with live orbital, kinetic, and charge-reactive motion.
 */
internal val REFUELING_GAUGE_STYLE_OPTIONS: List<Pair<String, String>> = listOf(
    "halo" to "Orbital Halo",
    "reactor_ticks" to "Reactor Turbine",
    "dual_wings" to "Twin Wings",
    "tachometer" to "Warp Tachometer",
    "hex_core" to "Quantum Hex Core",
    "warp_rail" to "Warp Plasma Rails",
    "cell_matrix" to "Fusion Cell Towers",
    "stealth_diamond" to "Aegis Prism",
    "oscilloscope" to "Harmonic Wave Core"
)

internal fun resolveGaugeStyleLabel(styleKey: String): String =
    REFUELING_GAUGE_STYLE_OPTIONS.firstOrNull { it.first == styleKey }?.second ?: "Orbital Halo"

@Composable
fun RefuelingGaugeMiniPreview(
    styleKey: String,
    accentColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(22.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * 0.40f
        val sw = 2.dp.toPx()
        when (styleKey) {
            "reactor_ticks" -> {
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    drawLine(
                        color = if (i < 6) accentColor else trackColor.copy(alpha = 0.25f),
                        start = Offset(c.x + r * 0.56f * cos(a).toFloat(), c.y + r * 0.56f * sin(a).toFloat()),
                        end = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            "dual_wings" -> {
                drawArc(accentColor, 110f, 140f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(accentColor, -70f, 140f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
            "tachometer" -> {
                drawArc(trackColor.copy(alpha = 0.25f), 145f, 250f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
                drawArc(accentColor, 145f, 185f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
            "hex_core" -> {
                val p = Path()
                for (i in 0 until 6) {
                    val a = Math.toRadians(-90.0 + i * 60.0)
                    val x = c.x + r * cos(a).toFloat()
                    val y = c.y + r * sin(a).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
                drawPath(p, accentColor, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
            }
            "warp_rail" -> {
                drawLine(accentColor, Offset(c.x - r, c.y - r * 0.55f), Offset(c.x + r, c.y - r * 0.55f), 2.5.dp.toPx(), StrokeCap.Round)
                drawLine(accentColor.copy(alpha = 0.7f), Offset(c.x - r * 0.55f, c.y + r * 0.55f), Offset(c.x + r, c.y + r * 0.55f), 2.5.dp.toPx(), StrokeCap.Round)
            }
            "cell_matrix" -> {
                for (row in 0 until 3) {
                    val y = c.y - r * 0.75f + row * (r * 0.6f)
                    val col = if (row > 0) accentColor else trackColor.copy(alpha = 0.3f)
                    drawRoundRect(col, Offset(c.x - r * 0.9f, y), Size(r * 0.5f, r * 0.35f), CornerRadius(1.dp.toPx()))
                    drawRoundRect(col, Offset(c.x + r * 0.4f, y), Size(r * 0.5f, r * 0.35f), CornerRadius(1.dp.toPx()))
                }
            }
            "stealth_diamond" -> {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r, c.y)
                    lineTo(c.x, c.y + r)
                    lineTo(c.x - r, c.y)
                    close()
                }
                drawPath(p, accentColor, style = Stroke(1.8.dp.toPx(), join = StrokeJoin.Round))
            }
            "oscilloscope" -> {
                val p = Path()
                for (i in 0..8) {
                    val x = c.x - r + (2f * r * i / 8f)
                    val y = c.y + sin(i * 1.1f) * (r * 0.42f)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                drawPath(p, accentColor, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
            }
            else -> {
                drawCircle(trackColor.copy(alpha = 0.22f), r, c, style = Stroke(sw))
                drawArc(accentColor, -90f, 260f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(sw, cap = StrokeCap.Round))
            }
        }
    }
}

internal fun DrawScope.drawOrbitalHaloGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val activeColor = if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor
    val sweepDeg = (batteryPct / 100f).coerceIn(0f, 1f) * 360f
    val arcRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

    // Subtle soft ambient outer halo
    drawCircle(
        color = activeColor.copy(alpha = if (isCharging) 0.10f else 0.05f),
        radius = radius + strokeWidthPx * 0.55f,
        center = center,
        style = Stroke(width = strokeWidthPx * 1.65f)
    )
    drawCircle(
        color = trackColor.copy(alpha = 0.08f),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
    drawArc(
        brush = Brush.sweepGradient(
            listOf(activeColor.copy(alpha = 0.50f), activeColor, activeColor.copy(alpha = 0.92f))
        ),
        startAngle = -90f,
        sweepAngle = sweepDeg,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )

    // Outer orbital scanner arc revolving smoothly while docked
    val outerOrbitR = radius + strokeWidthPx * 0.72f
    val orbitRect = Rect(center.x - outerOrbitR, center.y - outerOrbitR, center.x + outerOrbitR, center.y + outerOrbitR)
    val orbitStart = sweepPhase01 * 360f - 90f
    drawArc(
        brush = Brush.sweepGradient(listOf(Color.Transparent, activeColor.copy(alpha = 0.65f))),
        startAngle = orbitStart,
        sweepAngle = if (isCharging) 95f else 55f,
        useCenter = false,
        topLeft = orbitRect.topLeft,
        size = orbitRect.size,
        style = Stroke(width = (strokeWidthPx * 0.28f).coerceAtLeast(1.5f), cap = StrokeCap.Round)
    )

    // Glowing leading-edge plasma bead
    if (batteryPct > 0) {
        val tipRad = Math.toRadians((-90f + sweepDeg).toDouble())
        val tip = Offset(center.x + radius * cos(tipRad).toFloat(), center.y + radius * sin(tipRad).toFloat())
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = strokeWidthPx * 0.42f, center = tip)
    }
}

internal fun DrawScope.drawReactorTurbineGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val statorR = radius - strokeWidthPx * 1.45f
    drawCircle(
        color = trackColor.copy(alpha = 0.12f),
        radius = statorR,
        center = center,
        style = Stroke(width = 1.4.dp.toPx())
    )

    val tickCount = 24
    val activeTicks = (batteryPct / 100f * tickCount).roundToInt().coerceIn(0, tickCount)
    val tickLength = strokeWidthPx * 1.32f
    val chaseIdx = (sweepPhase01 * tickCount) % tickCount

    for (i in 0 until tickCount) {
        val angleDeg = i * (360f / tickCount) - 90f
        val angleRad = Math.toRadians(angleDeg.toDouble())
        val cosV = cos(angleRad).toFloat()
        val sinV = sin(angleRad).toFloat()

        val outerX = center.x + radius * cosV
        val outerY = center.y + radius * sinV
        val innerX = center.x + (radius - tickLength) * cosV
        val innerY = center.y + (radius - tickLength) * sinV

        val isActive = i < activeTicks
        val distToChase = minOf(abs(i - chaseIdx), tickCount - abs(i - chaseIdx))
        val chaseBoost = if (isCharging && isActive && distToChase < 3f) (1f - distToChase / 3f) * 0.45f else 0f

        val tickColor = if (isActive) {
            val baseAlpha = if (isPulseActive) (pulseAlpha * 0.8f + 0.2f) else 0.72f
            dynamicArcColor.copy(alpha = (baseAlpha + chaseBoost).coerceIn(0f, 1f))
        } else {
            trackColor.copy(alpha = 0.12f)
        }
        val tickWidth = if (isActive) (strokeWidthPx * 0.44f + chaseBoost * 2.dp.toPx()) else (strokeWidthPx * 0.24f)

        drawLine(
            color = tickColor,
            start = Offset(innerX, innerY),
            end = Offset(outerX, outerY),
            strokeWidth = tickWidth,
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawTwinWingsGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    pulseAlpha: Float
) {
    val activeColor = if (isCharging) dynamicArcColor.copy(alpha = (0.72f + 0.28f * pulseAlpha)) else dynamicArcColor
    val halfSweep = (batteryPct / 100f).coerceIn(0f, 1f) * 165f
    val arcRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)

    drawCircle(
        color = trackColor.copy(alpha = 0.08f),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
    // Left thruster wing
    drawArc(
        color = activeColor,
        startAngle = 90f,
        sweepAngle = -halfSweep,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )
    // Right thruster wing
    drawArc(
        color = activeColor,
        startAngle = 90f,
        sweepAngle = halfSweep,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )

    // Outer delicate kinetic shroud arcs
    val outerR = radius + strokeWidthPx * 0.7f
    val outerRect = Rect(center.x - outerR, center.y - outerR, center.x + outerR, center.y + outerR)
    val drift = sin(sweepPhase01 * 2f * PI).toFloat() * 8f
    drawArc(
        color = activeColor.copy(alpha = 0.32f),
        startAngle = 125f + drift,
        sweepAngle = 110f,
        useCenter = false,
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    )
    drawArc(
        color = activeColor.copy(alpha = 0.32f),
        startAngle = 55f - drift,
        sweepAngle = -110f,
        useCenter = false,
        topLeft = outerRect.topLeft,
        size = outerRect.size,
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
    )
}

internal fun DrawScope.drawWarpTachometerGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color
) {
    val arcRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawArc(
        color = trackColor.copy(alpha = 0.09f),
        startAngle = 150f,
        sweepAngle = 240f,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )

    // Zenith boost zone (last 20%)
    drawArc(
        color = dynamicArcColor.copy(alpha = 0.22f),
        startAngle = 342f,
        sweepAngle = 48f,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx * 0.45f, cap = StrokeCap.Round)
    )

    val subTickLength = strokeWidthPx * 0.62f
    val majorTickLength = strokeWidthPx * 1.15f
    val innerBase = radius - strokeWidthPx * 0.68f
    for (step in 0..24) {
        val angleRad = Math.toRadians((150f + step * 10f).toDouble())
        val cosV = cos(angleRad).toFloat()
        val sinV = sin(angleRad).toFloat()
        val isMajor = step % 6 == 0
        val tLen = if (isMajor) majorTickLength else subTickLength

        drawLine(
            color = trackColor.copy(alpha = if (isMajor) 0.42f else 0.16f),
            start = Offset(center.x + (innerBase - tLen) * cosV, center.y + (innerBase - tLen) * sinV),
            end = Offset(center.x + innerBase * cosV, center.y + innerBase * sinV),
            strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    val activeSweep = (batteryPct / 100f).coerceIn(0f, 1f) * 240f
    drawArc(
        brush = Brush.sweepGradient(listOf(dynamicArcColor.copy(alpha = 0.55f), dynamicArcColor)),
        startAngle = 150f,
        sweepAngle = activeSweep,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
    )

    // Animated comet needle pin along active progress
    val wobble = if (isCharging) sin(sweepPhase01 * 2f * PI).toFloat() * 1.6f else 0f
    val needleRad = Math.toRadians((150f + activeSweep + wobble).toDouble())
    val marker = Offset(center.x + radius * cos(needleRad).toFloat(), center.y + radius * sin(needleRad).toFloat())
    drawCircle(color = dynamicArcColor.copy(alpha = 0.4f), radius = strokeWidthPx * 0.85f, center = marker)
    drawCircle(color = Color.White.copy(alpha = 0.9f), radius = strokeWidthPx * 0.36f, center = marker)
}

internal fun DrawScope.drawHexCoreGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val hexRadius = radius * 1.02f
    val vertices = List(6) { i ->
        val angleRad = Math.toRadians((-90.0 + i * 60.0))
        Offset(center.x + hexRadius * cos(angleRad).toFloat(), center.y + hexRadius * sin(angleRad).toFloat())
    }

    val innerHex = Path()
    val innerR = hexRadius * 0.84f
    for (i in 0 until 6) {
        val angleRad = Math.toRadians((-90.0 + i * 60.0))
        val pt = Offset(center.x + innerR * cos(angleRad).toFloat(), center.y + innerR * sin(angleRad).toFloat())
        if (i == 0) innerHex.moveTo(pt.x, pt.y) else innerHex.lineTo(pt.x, pt.y)
    }
    innerHex.close()
    drawPath(path = innerHex, color = dynamicArcColor.copy(alpha = if (isCharging) 0.05f else 0.02f))
    drawPath(
        path = innerHex,
        color = trackColor.copy(alpha = 0.14f),
        style = Stroke(width = 1.4.dp.toPx(), join = StrokeJoin.Round)
    )

    drawPolygonPerimeterGauge(
        vertices = vertices,
        progress = batteryPct / 100f,
        strokeWidthPx = strokeWidthPx,
        trackColor = trackColor.copy(alpha = 0.12f),
        activeColor = if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor
    )

    val litCorners = ((batteryPct / 100f) * 6f).roundToInt().coerceIn(0, 6)
    val activeNode = (sweepPhase01 * 6f).toInt() % 6
    vertices.forEachIndexed { index, pt ->
        val isLit = index < litCorners
        val isPulseNode = isCharging && isLit && index == activeNode
        drawCircle(
            color = if (isLit) dynamicArcColor.copy(alpha = if (isPulseNode) 1f else 0.78f) else trackColor.copy(alpha = 0.22f),
            radius = if (isPulseNode) strokeWidthPx * 0.62f else if (isLit) strokeWidthPx * 0.44f else strokeWidthPx * 0.28f,
            center = pt
        )
    }
}

internal fun DrawScope.drawWarpBladeGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val barWidth = radius * 1.76f
    val barHeight = (strokeWidthPx * 1.25f).coerceAtLeast(8.dp.toPx())
    val left = center.x - barWidth / 2f
    val topRailY = center.y - radius * 0.80f
    val bottomRailY = center.y + radius * 0.80f - barHeight
    val corner = CornerRadius(barHeight / 2f, barHeight / 2f)
    val fillFrac = (batteryPct / 100f).coerceIn(0f, 1f)
    val activeFillColor = if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor

    listOf(topRailY to true, bottomRailY to false).forEach { (y, leftToRight) ->
        drawRoundRect(
            color = trackColor.copy(alpha = 0.11f),
            topLeft = Offset(left, y),
            size = Size(barWidth, barHeight),
            cornerRadius = corner
        )
        val fillW = (barWidth * fillFrac).coerceAtLeast(barHeight * 0.7f)
        val fillLeft = if (leftToRight) left else (left + barWidth - fillW)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(activeFillColor.copy(alpha = 0.62f), activeFillColor)
            ),
            topLeft = Offset(fillLeft, y),
            size = Size(fillW, barHeight),
            cornerRadius = corner
        )

        // Scrolling energy shimmer pulse traveling inside the rail while charging
        if (isCharging && fillW > barHeight * 1.5f) {
            val travel = if (leftToRight) sweepPhase01 else (1f - sweepPhase01)
            val pulseX = fillLeft + travel * (fillW - barHeight)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.48f),
                topLeft = Offset(pulseX, y + barHeight * 0.2f),
                size = Size(barHeight * 1.1f, barHeight * 0.6f),
                cornerRadius = corner
            )
        }
    }

    val sideOffset = radius * 0.96f
    val wingHalfH = radius * 0.48f
    listOf(-sideOffset, sideOffset).forEach { dx ->
        drawLine(
            color = activeFillColor.copy(alpha = 0.62f),
            start = Offset(center.x + dx, center.y - wingHalfH),
            end = Offset(center.x + dx, center.y + wingHalfH),
            strokeWidth = 2.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawPowerCellsGauge(
    center: Offset,
    radius: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val cellsPerSide = 10
    val stackHeight = radius * 1.58f
    val gap = (radius * 0.036f).coerceAtLeast(2.5.dp.toPx())
    val cellH = (stackHeight - (cellsPerSide - 1) * gap) / cellsPerSide
    val cellW = (radius * 0.14f).coerceAtLeast(10.dp.toPx())
    val startY = center.y + stackHeight / 2f
    val leftColumnX = center.x - radius * 0.94f
    val rightColumnX = center.x + radius * 0.94f - cellW

    val activeLevels = ((batteryPct / 100f) * cellsPerSide).roundToInt().coerceIn(0, cellsPerSide)
    val cellRadius = CornerRadius(2.5.dp.toPx(), 2.5.dp.toPx())
    val scanLevel = (sweepPhase01 * cellsPerSide) % cellsPerSide

    for (level in 0 until cellsPerSide) {
        val top = startY - (level + 1) * cellH - level * gap
        val isLit = level < activeLevels
        val archPush = (1f - abs((level - 4.5f) / 4.5f)) * (radius * 0.055f)
        val scanGlow = if (isCharging && isLit) (1f - abs(level - scanLevel).coerceAtMost(2f) / 2f) * 0.35f else 0f
        val cColor = if (isLit) {
            if (isPulseActive && level == activeLevels - 1) dynamicArcColor.copy(alpha = pulseAlpha)
            else dynamicArcColor.copy(alpha = (0.64f + 0.36f * (level / 9f) + scanGlow).coerceIn(0.6f, 1f))
        } else {
            trackColor.copy(alpha = 0.12f)
        }

        drawRoundRect(cColor, Offset(leftColumnX - archPush, top), Size(cellW, cellH), cellRadius)
        drawRoundRect(cColor, Offset(rightColumnX + archPush, top), Size(cellW, cellH), cellRadius)
    }

    val bracketW = radius * 0.44f
    val topY = center.y - radius * 0.88f
    val bottomY = center.y + radius * 0.88f
    listOf(topY, bottomY).forEach { y ->
        drawLine(
            color = dynamicArcColor.copy(alpha = 0.28f),
            start = Offset(center.x - bracketW, y),
            end = Offset(center.x + bracketW, y),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

internal fun DrawScope.drawStealthDiamondGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    sweepPhase01: Float,
    isCharging: Boolean,
    dynamicArcColor: Color,
    trackColor: Color,
    isPulseActive: Boolean,
    pulseAlpha: Float
) {
    val r = radius * 1.04f
    val chamfer = r * 0.14f
    val vertices = listOf(
        Offset(center.x - chamfer, center.y - r + chamfer),
        Offset(center.x + chamfer, center.y - r + chamfer),
        Offset(center.x + r - chamfer, center.y - chamfer),
        Offset(center.x + r - chamfer, center.y + chamfer),
        Offset(center.x + chamfer, center.y + r - chamfer),
        Offset(center.x - chamfer, center.y + r - chamfer),
        Offset(center.x - r + chamfer, center.y + chamfer),
        Offset(center.x - r + chamfer, center.y - chamfer)
    )

    val innerR = r * 0.84f
    val innerPath = Path().apply {
        moveTo(center.x, center.y - innerR)
        lineTo(center.x + innerR, center.y)
        lineTo(center.x, center.y + innerR)
        lineTo(center.x - innerR, center.y)
        close()
    }
    drawPath(innerPath, color = dynamicArcColor.copy(alpha = if (isCharging) 0.05f else 0.02f))
    drawPath(innerPath, color = trackColor.copy(alpha = 0.14f), style = Stroke(width = 1.2.dp.toPx(), join = StrokeJoin.Round))

    drawPolygonPerimeterGauge(
        vertices = vertices,
        progress = batteryPct / 100f,
        strokeWidthPx = strokeWidthPx * 0.92f,
        trackColor = trackColor.copy(alpha = 0.10f),
        activeColor = if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor
    )

    val cardinalPins = listOf(
        Offset(center.x, center.y - r + chamfer * 0.4f),
        Offset(center.x + r - chamfer * 0.4f, center.y),
        Offset(center.x, center.y + r - chamfer * 0.4f),
        Offset(center.x - r + chamfer * 0.4f, center.y)
    )
    val litPin = (sweepPhase01 * 4f).toInt() % 4
    cardinalPins.forEachIndexed { idx, pt ->
        drawCircle(
            color = if (isCharging && idx == litPin) dynamicArcColor else dynamicArcColor.copy(alpha = 0.35f),
            radius = if (isCharging && idx == litPin) 3.4.dp.toPx() else 2.dp.toPx(),
            center = pt
        )
    }
}

internal fun DrawScope.drawOscilloscopeGauge(
    center: Offset,
    radius: Float,
    strokeWidthPx: Float,
    batteryPct: Int,
    wattage: Float,
    wavePhaseRad: Float,
    dynamicArcColor: Color,
    trackColor: Color
) {
    val arcRect = Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius)
    drawArc(
        color = trackColor.copy(alpha = 0.10f),
        startAngle = 210f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx * 0.85f, cap = StrokeCap.Round)
    )
    drawArc(
        color = trackColor.copy(alpha = 0.10f),
        startAngle = 30f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx * 0.85f, cap = StrokeCap.Round)
    )

    val pctFraction = (batteryPct / 100f).coerceIn(0f, 1f)
    drawArc(
        color = dynamicArcColor,
        startAngle = 210f,
        sweepAngle = 120f * pctFraction,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx * 0.85f, cap = StrokeCap.Round)
    )
    drawArc(
        color = dynamicArcColor,
        startAngle = 150f,
        sweepAngle = -120f * pctFraction,
        useCenter = false,
        topLeft = arcRect.topLeft,
        size = arcRect.size,
        style = Stroke(width = strokeWidthPx * 0.85f, cap = StrokeCap.Round)
    )

    val waveWidth = radius * 1.48f
    val amplitude = (3.5.dp.toPx() + (wattage.coerceIn(0f, 45f) / 45f) * 6.5.dp.toPx())
    val waveTopY = center.y - radius * 0.62f
    val waveBottomY = center.y + radius * 0.62f
    val startX = center.x - waveWidth / 2f

    val upperWave = Path()
    val lowerWave = Path()
    val steps = 36
    for (s in 0..steps) {
        val u = s / steps.toFloat()
        val x = startX + u * waveWidth
        val envelope = sin(u * PI).toFloat()
        val yOffset = sin(u * 4f * PI.toFloat() + wavePhaseRad) * amplitude * envelope
        val subHarmonic = cos(u * 6f * PI.toFloat() - wavePhaseRad * 1.5f) * (amplitude * 0.35f) * envelope
        if (s == 0) {
            upperWave.moveTo(x, waveTopY + yOffset + subHarmonic)
            lowerWave.moveTo(x, waveBottomY - yOffset - subHarmonic)
        } else {
            upperWave.lineTo(x, waveTopY + yOffset + subHarmonic)
            lowerWave.lineTo(x, waveBottomY - yOffset - subHarmonic)
        }
    }
    // Luminous aura + crisp carrier line
    listOf(upperWave, lowerWave).forEach { wave ->
        drawPath(wave, color = dynamicArcColor.copy(alpha = 0.22f), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
        drawPath(wave, color = dynamicArcColor.copy(alpha = 0.72f), style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

private fun DrawScope.drawPolygonPerimeterGauge(
    vertices: List<Offset>,
    progress: Float,
    strokeWidthPx: Float,
    trackColor: Color,
    activeColor: Color
) {
    if (vertices.size < 3) return
    val fullPath = Path().apply {
        moveTo(vertices[0].x, vertices[0].y)
        for (i in 1 until vertices.size) lineTo(vertices[i].x, vertices[i].y)
        close()
    }
    drawPath(fullPath, color = trackColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val clampedProgress = progress.coerceIn(0f, 1f)
    if (clampedProgress <= 0f) return

    val segLengths = FloatArray(vertices.size)
    var totalLen = 0f
    for (i in vertices.indices) {
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        val len = hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble()).toFloat()
        segLengths[i] = len
        totalLen += len
    }
    if (totalLen <= 0.1f) return

    var remaining = totalLen * clampedProgress
    val activePath = Path().apply { moveTo(vertices[0].x, vertices[0].y) }
    for (i in vertices.indices) {
        if (remaining <= 0f) break
        val a = vertices[i]
        val b = vertices[(i + 1) % vertices.size]
        val len = segLengths[i].coerceAtLeast(0.01f)
        if (remaining >= len) {
            activePath.lineTo(b.x, b.y)
            remaining -= len
        } else {
            val t = (remaining / len).coerceIn(0f, 1f)
            activePath.lineTo(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            remaining = 0f
        }
    }
    drawPath(activePath, color = activeColor, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
