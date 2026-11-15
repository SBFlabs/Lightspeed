package com.sbf.lightspeed.settings

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Identifies which feature-matched loading illustration to render in shared app pickers. */
enum class AppPickerLoaderStyle {
    AIRLOCK_SEAL,
    BATTERY_CELL,
    SOLAR_BRIGHTNESS_TRIGGER,
    TELEMETRY_WAVEFORM,
    RADAR_SCAN
}

/**
 * Dispatches the feature-coherent 12-Theme loading animation matching the active dialog's purpose.
 */
@Composable
fun TailoredAppPickerLoader(
    style: AppPickerLoaderStyle,
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    when (style) {
        AppPickerLoaderStyle.BATTERY_CELL -> BatteryPowerCellLoader(title, subtitle, modifier)
        AppPickerLoaderStyle.AIRLOCK_SEAL -> AirlockSealLoader(title, subtitle, modifier)
        AppPickerLoaderStyle.SOLAR_BRIGHTNESS_TRIGGER -> SolarBrightnessLockLoader(title, subtitle, modifier)
        AppPickerLoaderStyle.TELEMETRY_WAVEFORM -> TelemetryWaveformLoader(title, subtitle, modifier)
        AppPickerLoaderStyle.RADAR_SCAN -> RadarAppScanLoader(title, subtitle, modifier)
    }
}

/**
 * 12-Theme segmented battery cell & shield-ring loader for Battery Exemption Control.
 */
@Composable
fun BatteryPowerCellLoader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    LoaderScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        durationMs = 1500,
        label = "battery_cell_loader"
    ) { phase, accent, accentInfo ->
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val cellW = size.minDimension * 0.36f
        val cellH = size.minDimension * 0.54f
        val left = cx - cellW * 0.5f
        val top = cy - cellH * 0.5f + 2.dp.toPx()

        // Outer protective exemption shield arc rotating slowly
        rotate(degrees = phase * 360f, pivot = center) {
            drawArc(
                color = accent.copy(alpha = 0.42f),
                startAngle = -55f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - size.minDimension * 0.44f, cy - size.minDimension * 0.44f),
                size = Size(size.minDimension * 0.88f, size.minDimension * 0.88f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = accentInfo.copy(alpha = 0.32f),
                startAngle = 125f,
                sweepAngle = 110f,
                useCenter = false,
                topLeft = Offset(cx - size.minDimension * 0.44f, cy - size.minDimension * 0.44f),
                size = Size(size.minDimension * 0.88f, size.minDimension * 0.88f),
                style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Terminal nub & rounded battery silhouette
        val capW = cellW * 0.44f
        val capH = 3.5.dp.toPx()
        drawRoundRect(
            color = accent.copy(alpha = 0.72f),
            topLeft = Offset(cx - capW * 0.5f, top - capH - 1.dp.toPx()),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.75f),
            topLeft = Offset(left, top),
            size = Size(cellW, cellH),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx())
        )

        // 4 rising segmented energy bars inside battery cell
        val segments = 4
        val pad = 3.5.dp.toPx()
        val gap = 2.5.dp.toPx()
        val usableH = cellH - pad * 2f - gap * (segments - 1)
        val segH = (usableH / segments).coerceAtLeast(2f)
        val fillStage = phase * (segments + 0.8f)

        for (i in 0 until segments) {
            val idxFromBottom = segments - 1 - i
            val segAlpha = ((fillStage - i).coerceIn(0f, 1f) * 0.78f + 0.14f).coerceIn(0f, 0.92f)
            val sy = top + pad + idxFromBottom * (segH + gap)
            drawRoundRect(
                color = accent.copy(alpha = segAlpha),
                topLeft = Offset(left + pad, sy),
                size = Size(cellW - pad * 2f, segH),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

/**
 * 12-Theme sliding vault bulkhead & padlock loader for Astrogation Airlock app selection.
 */
@Composable
fun AirlockSealLoader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    LoaderScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        durationMs = 2000,
        label = "airlock_seal_loader"
    ) { phase, accent, accentInfo ->
        val r = size.minDimension * 0.44f
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val breathe = 0.5f - 0.5f * cos(phase * Math.PI * 2.0).toFloat()

        // Outer rotating containment shield arcs
        rotate(degrees = phase * 360f, pivot = center) {
            drawArc(
                color = accentInfo.copy(alpha = 0.65f),
                startAngle = -30f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = accentInfo.copy(alpha = 0.65f),
                startAngle = 150f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // Vault bulkhead frame & animated sliding containment blast doors
        val boxHalf = r * 0.72f
        val boxL = cx - boxHalf
        val boxT = cy - boxHalf
        val boxW = boxHalf * 2f
        val boxCorner = 10.dp.toPx()

        clipRect(left = boxL, top = boxT, right = boxL + boxW, bottom = boxT + boxW) {
            val doorGap = 4.dp.toPx() * (1f - breathe)
            val panelW = boxHalf - doorGap
            drawRect(
                color = accent.copy(alpha = 0.16f + 0.12f * breathe),
                topLeft = Offset(boxL, boxT),
                size = Size(panelW.coerceAtLeast(0f), boxW)
            )
            drawRect(
                color = accent.copy(alpha = 0.16f + 0.12f * breathe),
                topLeft = Offset(cx + doorGap, boxT),
                size = Size(panelW.coerceAtLeast(0f), boxW)
            )
            drawLine(
                color = accentInfo.copy(alpha = 0.55f),
                start = Offset(cx - doorGap, boxT),
                end = Offset(cx - doorGap, boxT + boxW),
                strokeWidth = 1.2.dp.toPx()
            )
            drawLine(
                color = accentInfo.copy(alpha = 0.55f),
                start = Offset(cx + doorGap, boxT),
                end = Offset(cx + doorGap, boxT + boxW),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        drawRoundRect(
            color = accent.copy(alpha = 0.72f),
            topLeft = Offset(boxL, boxT),
            size = Size(boxW, boxW),
            cornerRadius = CornerRadius(boxCorner, boxCorner),
            style = Stroke(width = 1.8.dp.toPx())
        )

        // Centered geometric padlock core
        val lockW = 14.dp.toPx()
        val lockH = 11.dp.toPx()
        val lockTop = cy - 1.dp.toPx()
        val shackleW = 9.dp.toPx()
        val shackleLift = 2.dp.toPx() * (1f - breathe)
        drawArc(
            color = accent.copy(alpha = 0.92f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - shackleW * 0.5f, lockTop - 6.dp.toPx() - shackleLift),
            size = Size(shackleW, 10.dp.toPx()),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = accent.copy(alpha = 0.92f),
            topLeft = Offset(cx - lockW * 0.5f, lockTop),
            size = Size(lockW, lockH),
            cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        )
    }
}

/**
 * 12-Theme solar brightness core & auto-release halo loader for Vista Media Brightness Triggers.
 */
@Composable
fun SolarBrightnessLockLoader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    LoaderScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        durationMs = 1900,
        label = "solar_brightness_loader"
    ) { phase, accent, accentInfo ->
        val r = size.minDimension * 0.44f
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val pulse = 0.5f - 0.5f * cos(phase * Math.PI * 2.0).toFloat()

        // Outer auto-release adaptive ring alternating open & locked arc gaps
        val sweep = 110f + 130f * pulse
        rotate(degrees = phase * 360f, pivot = center) {
            drawArc(
                color = accentInfo.copy(alpha = 0.72f),
                startAngle = 0f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // 8 orbiting rounded solar photons (clean geometric orbs)
        val orbDist = r * (0.64f + 0.08f * pulse)
        val orbRadius = 2.3.dp.toPx()
        for (i in 0 until 8) {
            val rad = Math.toRadians(i * 45.0 - phase * 45.0)
            drawCircle(
                color = accent.copy(alpha = 0.65f + 0.30f * pulse),
                radius = orbRadius,
                center = Offset(cx + (cos(rad) * orbDist).toFloat(), cy + (sin(rad) * orbDist).toFloat())
            )
        }

        // Glowing solar core + miniature lock shackle indicating auto-release brightness override
        drawCircle(
            color = accent.copy(alpha = 0.20f + 0.18f * pulse),
            radius = r * 0.44f,
            center = center
        )
        drawCircle(
            color = accent.copy(alpha = 0.90f),
            radius = r * 0.34f,
            center = center,
            style = Stroke(width = 1.8.dp.toPx())
        )
        val lockW = 10.dp.toPx()
        val lockH = 8.dp.toPx()
        val lockTop = cy - 1.dp.toPx()
        drawArc(
            color = accent,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - 3.5.dp.toPx(), lockTop - 4.5.dp.toPx()),
            size = Size(7.dp.toPx(), 7.dp.toPx()),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(cx - lockW * 0.5f, lockTop),
            size = Size(lockW, lockH),
            cornerRadius = CornerRadius(2.4.dp.toPx(), 2.4.dp.toPx())
        )
    }
}

/**
 * 12-Theme scrolling Hz / FPS waveform inside corner reticle brackets for Vista Telemetry Target Apps.
 */
@Composable
fun TelemetryWaveformLoader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    LoaderScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        durationMs = 1350,
        label = "telemetry_wave_loader"
    ) { phase, accent, accentInfo ->
        val pad = size.minDimension * 0.12f
        val boxL = pad
        val boxR = size.width - pad
        val boxT = pad + 4.dp.toPx()
        val boxB = size.height - pad - 4.dp.toPx()
        val cornerLen = 9.dp.toPx()
        val strokeW = 1.6.dp.toPx()

        // Tactical corner reticle brackets
        val corners = listOf(
            Offset(boxL, boxT) to Pair(Offset(boxL + cornerLen, boxT), Offset(boxL, boxT + cornerLen)),
            Offset(boxR, boxT) to Pair(Offset(boxR - cornerLen, boxT), Offset(boxR, boxT + cornerLen)),
            Offset(boxL, boxB) to Pair(Offset(boxL + cornerLen, boxB), Offset(boxL, boxB - cornerLen)),
            Offset(boxR, boxB) to Pair(Offset(boxR - cornerLen, boxB), Offset(boxR, boxB - cornerLen))
        )
        for ((p, arms) in corners) {
            drawLine(accentInfo.copy(alpha = 0.72f), p, arms.first, strokeWidth = strokeW, cap = StrokeCap.Round)
            drawLine(accentInfo.copy(alpha = 0.72f), p, arms.second, strokeWidth = strokeW, cap = StrokeCap.Round)
        }

        // Baseline trace grid line
        val midY = (boxT + boxB) * 0.5f
        drawLine(
            color = accent.copy(alpha = 0.20f),
            start = Offset(boxL + 4.dp.toPx(), midY),
            end = Offset(boxR - 4.dp.toPx(), midY),
            strokeWidth = 0.9.dp.toPx()
        )

        // Scrolling Hz telemetry waveform path
        val waveL = boxL + 5.dp.toPx()
        val waveR = boxR - 5.dp.toPx()
        val waveW = (waveR - waveL).coerceAtLeast(1f)
        val amp = (boxB - boxT) * 0.34f
        val path = Path()
        val steps = 28
        for (i in 0..steps) {
            val frac = i / steps.toFloat()
            val x = waveL + frac * waveW
            val shifted = (frac + phase) % 1f
            val spike = when {
                abs(shifted - 0.35f) < 0.06f -> -amp
                abs(shifted - 0.45f) < 0.05f -> amp * 0.62f
                abs(shifted - 0.78f) < 0.05f -> -amp * 0.55f
                else -> sin((frac * 4f + phase * 2f) * Math.PI).toFloat() * amp * 0.14f
            }
            val y = midY + spike
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = accent.copy(alpha = 0.90f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        // Moving leading beam cursor dot
        val beadX = waveL + phase * waveW
        drawCircle(
            color = accent,
            radius = 3.0.dp.toPx(),
            center = Offset(beadX, midY)
        )
    }
}

/**
 * 12-Theme adaptive tactical radar sweep loader for process, wakelock, and threat scans.
 */
@Composable
fun RadarAppScanLoader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    LoaderScaffold(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        durationMs = 1650,
        label = "radar_app_scan"
    ) { phase, accent, _ ->
        val sweepAngle = phase * 360f
        val r = size.minDimension * 0.46f
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f

        drawCircle(color = accent.copy(alpha = 0.38f), radius = r, style = Stroke(width = 1.4.dp.toPx()))
        drawCircle(color = accent.copy(alpha = 0.22f), radius = r * 0.66f, style = Stroke(width = 1.dp.toPx()))
        drawCircle(color = accent.copy(alpha = 0.16f), radius = r * 0.33f, style = Stroke(width = 1.dp.toPx()))
        drawLine(accent.copy(alpha = 0.16f), Offset(cx - r, cy), Offset(cx + r, cy), strokeWidth = 0.9.dp.toPx())
        drawLine(accent.copy(alpha = 0.16f), Offset(cx, cy - r), Offset(cx, cy + r), strokeWidth = 0.9.dp.toPx())

        rotate(degrees = sweepAngle, pivot = center) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.76f to Color.Transparent,
                    1.0f to accent.copy(alpha = 0.36f),
                    center = center
                ),
                radius = r
            )
            drawLine(
                color = accent.copy(alpha = 0.92f),
                start = center,
                end = Offset(cx + r, cy),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        val blips = listOf(
            Triple(42f, 0.56f, 3.0f),
            Triple(168f, 0.78f, 2.6f),
            Triple(284f, 0.44f, 2.8f)
        )
        for ((angleDeg, distFrac, blipDp) in blips) {
            val rad = Math.toRadians(angleDeg.toDouble())
            val bx = cx + (cos(rad) * r * distFrac).toFloat()
            val by = cy + (sin(rad) * r * distFrac).toFloat()
            val diff = ((sweepAngle - angleDeg) % 360f + 360f) % 360f
            val pulse = if (diff < 110f) (1f - diff / 110f) else 0.12f
            drawCircle(
                color = accent.copy(alpha = (0.22f + pulse * 0.78f).coerceIn(0f, 1f)),
                radius = (blipDp * (0.85f + pulse * 0.4f)).dp.toPx(),
                center = Offset(bx, by)
            )
        }
        drawCircle(color = accent, radius = 2.6.dp.toPx(), center = center)
    }
}

@Composable
private fun LoaderScaffold(
    title: String,
    subtitle: String,
    modifier: Modifier,
    durationMs: Int,
    label: String,
    onDraw: DrawScope.(phase: Float, accent: Color, accentInfo: Color) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val accent = lsColors.accent
    val accentInfo = lsColors.accentInfo
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "${label}_phase"
    )

    Column(
        modifier = modifier.padding(vertical = 18.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(82.dp)) {
            onDraw(phase, accent, accentInfo)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = accent,
            letterSpacing = 0.9.sp,
            textAlign = TextAlign.Center
        )

        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = lsColors.textPrimary.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )
        }
    }
}
