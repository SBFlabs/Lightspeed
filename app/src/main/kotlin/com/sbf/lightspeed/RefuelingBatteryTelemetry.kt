package com.sbf.lightspeed

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import java.util.Locale
import kotlin.math.*

@Composable
fun BatteryTelemetryCircle(
    batteryPct: Int,
    wattage: Float,
    batteryTempC: Float,
    chargeTypeLabel: String,
    timeRemainingMinutes: Long,
    isCharging: Boolean,
    isFull: Boolean,
    batteryStyle: String,
    dynamicArcColor: Color,
    thermalBadgeColor: Color,
    sizeDp: Dp
) {
    // Fast-charge (>20W) pulsating glow transition
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_glow")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val isPulseActive = isCharging && wattage >= 20.0f

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(sizeDp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2f
            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)

            when (batteryStyle) {
                "reactor_ticks" -> {
                    // Style 2: 20 discrete laser-cut radial tick marks
                    val tickCount = 20
                    val activeTicks = (batteryPct / 100f * tickCount).roundToInt()
                    val tickLength = 10.dp.toPx()

                    for (i in 0 until tickCount) {
                        val angleDeg = i * (360f / tickCount) - 90f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(angleRad).toFloat()
                        val sinVal = sin(angleRad).toFloat()

                        val outerX = center.x + radius * cosVal
                        val outerY = center.y + radius * sinVal
                        val innerX = center.x + (radius - tickLength) * cosVal
                        val innerY = center.y + (radius - tickLength) * sinVal

                        val isActive = i < activeTicks
                        val tickColor = if (isActive) {
                            if (isPulseActive) dynamicArcColor.copy(alpha = pulseAlpha) else dynamicArcColor
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        }
                        val tickWidth = if (isActive) 3.5.dp.toPx() else 2.dp.toPx()

                        drawLine(
                            color = tickColor,
                            start = androidx.compose.ui.geometry.Offset(innerX, innerY),
                            end = androidx.compose.ui.geometry.Offset(outerX, outerY),
                            strokeWidth = tickWidth,
                            cap = StrokeCap.Round
                        )
                    }
                }

                "dual_wings" -> {
                    // Style 3: Symmetrical brackets sweeping upward from bottom (90°) to top (270° / -90°)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    val halfSweep = (batteryPct / 100f) * 180f
                    // Left wing (counter-clockwise from bottom 90°)
                    drawArc(
                        color = dynamicArcColor,
                        startAngle = 90f,
                        sweepAngle = -halfSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    // Right wing (clockwise from bottom 90°)
                    drawArc(
                        color = dynamicArcColor,
                        startAngle = 90f,
                        sweepAngle = halfSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                "tachometer" -> {
                    // Style 4: 240° cockpit sweep (7 to 5 o'clock, 150° to 390°) with micro-graduations
                    // Background track (150° with sweep 240°)
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
                        startAngle = 150f,
                        sweepAngle = 240f,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )

                    // Micro-graduations along the 240° sweep
                    val subTickLength = 5.dp.toPx()
                    val majorTickLength = 9.dp.toPx()
                    for (step in 0..24) {
                        val angleDeg = 150f + step * 10f
                        val angleRad = Math.toRadians(angleDeg.toDouble())
                        val cosVal = cos(angleRad).toFloat()
                        val sinVal = sin(angleRad).toFloat()
                        val isMajor = step % 6 == 0
                        val tLen = if (isMajor) majorTickLength else subTickLength

                        val outerX = center.x + (radius - strokeWidthPx / 2f - 2.dp.toPx()) * cosVal
                        val outerY = center.y + (radius - strokeWidthPx / 2f - 2.dp.toPx()) * sinVal
                        val innerX = center.x + (radius - strokeWidthPx / 2f - 2.dp.toPx() - tLen) * cosVal
                        val innerY = center.y + (radius - strokeWidthPx / 2f - 2.dp.toPx() - tLen) * sinVal

                        drawLine(
                            color = Color.White.copy(alpha = if (isMajor) 0.35f else 0.15f),
                            start = androidx.compose.ui.geometry.Offset(innerX, innerY),
                            end = androidx.compose.ui.geometry.Offset(outerX, outerY),
                            strokeWidth = if (isMajor) 2.dp.toPx() else 1.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Active sweep arc
                    val activeSweep = (batteryPct / 100f) * 240f
                    drawArc(
                        brush = Brush.sweepGradient(listOf(dynamicArcColor.copy(alpha = 0.7f), dynamicArcColor)),
                        startAngle = 150f,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }

                else -> {
                    // Style 1: "halo" (Default smooth continuous neon arc)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                    val sweep = (batteryPct / 100f) * 360f
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(dynamicArcColor.copy(alpha = 0.6f), dynamicArcColor, dynamicArcColor.copy(alpha = 0.9f))
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$batteryPct",
                    fontSize = if (sizeDp > 160.dp) 44.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "%",
                    fontSize = if (sizeDp > 160.dp) 18.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dynamicArcColor,
                    modifier = Modifier.padding(bottom = 6.dp, start = 2.dp)
                )
            }

            // Wattage & Thermal Telemetry (°C) Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (wattage > 0f) {
                    Text(
                        text = String.format(Locale.US, "%.1f W", wattage),
                        fontSize = if (sizeDp > 160.dp) 13.5.sp else 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicArcColor
                    )
                }

                // Real-time SoC Thermal Telemetry
                Text(
                    text = String.format(Locale.US, "%.1f°C", batteryTempC),
                    fontSize = if (sizeDp > 160.dp) 12.5.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = thermalBadgeColor
                )
            }

            Text(
                text = if (isFull) "FULL TANK" else chargeTypeLabel,
                fontSize = if (sizeDp > 160.dp) 10.5.sp else 9.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.8.sp,
                color = Color.White.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (timeRemainingMinutes > 0 && isCharging && !isFull) {
                Text(
                    text = if (timeRemainingMinutes >= 60) {
                        "Full in ${timeRemainingMinutes / 60}h ${timeRemainingMinutes % 60}m"
                    } else {
                        "Full in ${timeRemainingMinutes}m"
                    },
                    fontSize = 9.5.sp,
                    color = Color.LightGray.copy(alpha = 0.8f)
                )
            }
        }
    }
}
