package com.sbf.lightspeed.settings

import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Global Backdrop Blur Mode Selector component for theme customization.
 * Persists the user's choice across all 12 Flight Control Deck styles.
 */
@Composable
internal fun BackdropBlurModeSelector(
    selectedMode: BackdropBlurMode,
    onModeSelected: (BackdropBlurMode) -> Unit
) {
    val context = LocalContext.current
    val wm = context.getSystemService(WindowManager::class.java)
    val isNativeAvailable = BackdropBlurModeManager.isNativeBlurSupported(wm)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "BACKDROP BLUR ENGINE",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isNativeAvailable) "NATIVE HARDWARE READY" else "NATIVE BLUR UNAVAILABLE",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (isNativeAvailable) LightspeedStyle.colors.accentOk else LightspeedStyle.colors.accentWarn
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            BackdropBlurMode.values().forEach { mode ->
                val isSelected = mode == selectedMode
                val shape = RoundedCornerShape(10.dp)
                val isUnavailableNative = (mode == BackdropBlurMode.NATIVE && !isNativeAvailable)

                val containerColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else -> LightspeedStyle.colors.textPrimary.copy(alpha = 0.05f)
                }
                val borderColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f)
                }
                val textColor = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isUnavailableNative -> LightspeedStyle.colors.textPrimary.copy(alpha = 0.45f)
                    else -> LightspeedStyle.colors.textPrimary.copy(alpha = 0.85f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(shape)
                        .background(containerColor)
                        .border(BorderStroke(1.2.dp, borderColor), shape)
                        .clickable { onModeSelected(mode) }
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }

        // Distinct compact capability status indicator below pills
        if (!isNativeAvailable && selectedMode == BackdropBlurMode.NATIVE) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = LightspeedStyle.colors.accentDanger.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, LightspeedStyle.colors.accentDanger.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "HARDWARE BLUR UNAVAILABLE // Device firmware disabled SurfaceFlinger blur",
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = LightspeedStyle.colors.accentDanger,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Informational caption explaining the current mode and availability
        val statusExplanation = when (selectedMode) {
            BackdropBlurMode.SCREENSHOT -> "Clipped screenshot-based backdrop blur active. Bypasses OEM firmware limits."
            BackdropBlurMode.NATIVE -> {
                if (isNativeAvailable) "Native OS SurfaceFlinger window blur active."
                else "Native OS blur is disabled by device firmware. Choose Screenshot or Automatic to obtain backdrop blur."
            }
            BackdropBlurMode.AUTOMATIC -> {
                if (isNativeAvailable) "Automatic: Genuine native window blur detected and active."
                else "Automatic: Native blur unavailable on this firmware. Screenshot workaround active."
            }
        }

        Text(
            text = statusExplanation,
            fontSize = 10.5.sp,
            lineHeight = 14.sp,
            color = if (selectedMode == BackdropBlurMode.NATIVE && !isNativeAvailable) {
                LightspeedStyle.colors.accentDanger
            } else {
                LightspeedStyle.colors.textPrimary.copy(alpha = 0.65f)
            }
        )
    }
}

/**
 * Reusable Optical Slider Row with header label and readout value.
 */
@Composable
internal fun ThemeSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LightspeedStyle.colors.textPrimary
            )
            Text(
                text = valueLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = LightspeedStyle.colors.textPrimary.copy(alpha = 0.15f)
            ),
            modifier = Modifier.height(28.dp)
        )
    }
}

/**
 * Optical Parameters Sliders Section for real-time glass tuning.
 */
@Composable
internal fun ThemeOpticalSlidersSection(
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    blurRadius: Int,
    onBlurRadiusChange: (Int) -> Unit,
    cornerRadius: Float,
    onCornerRadiusChange: (Float) -> Unit,
    borderWidth: Float,
    onBorderWidthChange: (Float) -> Unit,
    glowStrength: Float,
    onGlowStrengthChange: (Float) -> Unit,
    highlightStrength: Float,
    onHighlightStrengthChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "OPTICAL PARAMETERS",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        // Opacity Slider
        ThemeSliderRow(
            title = "Surface Opacity",
            valueLabel = "${(opacity * 100).roundToInt()}%",
            value = opacity,
            range = 0.05f..1.0f,
            onValueChange = onOpacityChange
        )

        // Blur Radius Slider
        ThemeSliderRow(
            title = "Blur Radius",
            valueLabel = "${blurRadius}px",
            value = blurRadius.toFloat(),
            range = 0f..80f,
            onValueChange = { onBlurRadiusChange(it.roundToInt()) }
        )

        // Corner Radius Slider
        ThemeSliderRow(
            title = "Corner Radius",
            valueLabel = "${cornerRadius.roundToInt()}dp",
            value = cornerRadius,
            range = 0f..48f,
            onValueChange = onCornerRadiusChange
        )

        // Border Width Slider
        ThemeSliderRow(
            title = "Rim Border Stroke",
            valueLabel = String.format(Locale.US, "%.1fdp", borderWidth),
            value = borderWidth,
            range = 0f..4f,
            onValueChange = onBorderWidthChange
        )

        // Bloom / Glow Strength Slider
        ThemeSliderRow(
            title = "Bloom & Ambient Halo",
            valueLabel = "${(glowStrength * 100).roundToInt()}%",
            value = glowStrength,
            range = 0f..1f,
            onValueChange = onGlowStrengthChange
        )

        // Specular Gleam / Highlight Slider
        ThemeSliderRow(
            title = "Specular Gloss & Gleam",
            valueLabel = "${(highlightStrength * 100).roundToInt()}%",
            value = highlightStrength,
            range = 0f..1f,
            onValueChange = onHighlightStrengthChange
        )
    }
}
