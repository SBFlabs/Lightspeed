package com.sbf.lightspeed

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale
import kotlin.math.PI

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
    sizeDp: Dp,
    showInsideGraph: Boolean = false,
    graphSpan: String = "charge",
    graphUnpluggedMode: String = "live_15m"
) {
    val lsColors = LightspeedStyle.colors
    val infiniteTransition = rememberInfiniteTransition(label = "refueling_gauge_anim")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.52f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val sweepPhase01 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isCharging && wattage >= 20f) 2400 else 4200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_phase"
    )
    val oscPhaseRad = sweepPhase01 * (2f * PI.toFloat())
    val isPulseActive = isCharging && wattage >= 20.0f

    val context = LocalContext.current

    val textScale = (sizeDp.value / 184f).coerceIn(0.75f, 1.48f)

    Box(
        modifier = Modifier
            .size(sizeDp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (showInsideGraph) {
            RefuelingTelemetryGraphSurface(
                wattage = wattage,
                batteryTempC = batteryTempC,
                isCharging = isCharging,
                graphSpan = graphSpan,
                graphUnpluggedMode = graphUnpluggedMode,
                showLabels = false,
                dynamicArcColor = dynamicArcColor,
                thermalBadgeColor = thermalBadgeColor,
                onCycleSpan = {},
                insideGaugeOnly = true,
                modifier = Modifier
                    .fillMaxSize(0.76f)
                    .clip(CircleShape)
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = (8f * textScale).dp.toPx()
            val radius = (size.minDimension - strokeWidthPx * 1.45f) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            when (batteryStyle) {
                "reactor_ticks" -> drawReactorTurbineGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )

                "dual_wings" -> drawTwinWingsGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    pulseAlpha = pulseAlpha
                )

                "tachometer" -> drawWarpTachometerGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary
                )

                "hex_core" -> drawHexCoreGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )

                "warp_rail" -> drawWarpBladeGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )

                "cell_matrix" -> drawPowerCellsGauge(
                    center = center,
                    radius = radius,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )

                "stealth_diamond" -> drawStealthDiamondGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )

                "oscilloscope" -> drawOscilloscopeGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    wattage = wattage,
                    wavePhaseRad = oscPhaseRad,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary
                )

                else -> drawOrbitalHaloGauge(
                    center = center,
                    radius = radius,
                    strokeWidthPx = strokeWidthPx,
                    batteryPct = batteryPct,
                    sweepPhase01 = sweepPhase01,
                    isCharging = isCharging,
                    dynamicArcColor = dynamicArcColor,
                    trackColor = lsColors.textPrimary,
                    isPulseActive = isPulseActive,
                    pulseAlpha = pulseAlpha
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$batteryPct",
                    fontSize = (42f * textScale).sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "%",
                    fontSize = (17f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = dynamicArcColor,
                    modifier = Modifier.padding(bottom = (6f * textScale).dp, start = 2.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (wattage > 0f) {
                    Text(
                        text = String.format(Locale.US, "%.1f W", wattage),
                        fontSize = (13f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicArcColor
                    )
                }

                val tempText = if (LightspeedCruiseHudPods.isTempFahrenheit(context)) {
                    String.format(Locale.US, "%.1f°F", batteryTempC * 9f / 5f + 32f)
                } else {
                    String.format(Locale.US, "%.1f°C", batteryTempC)
                }
                Text(
                    text = tempText,
                    fontSize = (12f * textScale).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = thermalBadgeColor
                )
            }

            val isArabic = LightspeedLocaleEngine.state.primary == LightspeedLocaleEngine.AppLanguage.AR
            Text(
                text = if (isFull) lsString(R.string.ref_bay_full_tank) else chargeTypeLabel,
                fontSize = (10f * textScale).coerceIn(8.5f, 13f).sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = if (isArabic) 0.sp else 0.8.sp,
                color = lsColors.textPrimary.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            if (timeRemainingMinutes > 0 && !isFull) {
                val hours = timeRemainingMinutes / 60
                val mins = timeRemainingMinutes % 60
                val etaText = if (isCharging) {
                    if (hours > 0) lsString(R.string.ref_bay_eta_full_hm, hours, mins)
                    else lsString(R.string.ref_bay_eta_full_m, mins)
                } else {
                    if (hours > 0) lsString(R.string.ref_bay_eta_left_hm, hours, mins)
                    else lsString(R.string.ref_bay_eta_left_m, mins)
                }
                Text(
                    text = etaText,
                    fontSize = (9.5f * textScale).coerceIn(8.5f, 12f).sp,
                    color = lsColors.textSecondary.copy(alpha = 0.8f)
                )
            }
        }
    }
}
