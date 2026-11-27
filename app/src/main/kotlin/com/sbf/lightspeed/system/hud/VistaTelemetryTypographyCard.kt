package com.sbf.lightspeed.system.hud

import android.accessibilityservice.AccessibilityService
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckStylePresets
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.FontWritingLoadingAnimation
import com.sbf.lightspeed.system.DeviceFontScanner
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.display.LightspeedTelemetryHudOverlay
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Animated Font Family selector (`FontWritingLoadingAnimation` + on-demand font scan) and
 * Scale Slider (`75%..165%`) for the Per-App Telemetry Micro-HUD inside Vista Deck.
 */
@Composable
fun VistaTelemetryTypographyControls(
    service: AccessibilityService,
    snapshot: DeckThemeSnapshot
) {
    val lsColors = LightspeedStyle.colors
    val colorScheme = MaterialTheme.colorScheme
    val isM3 = snapshot.styleId == DeckStylePresets.MATERIAL_3.styleId
    val prefs = service.defaultPrefs()

    var scalePct by remember {
        mutableIntStateOf(
            prefs.getInt(LightspeedPreferences.KEY_TELEMETRY_HUD_SCALE, LightspeedPreferences.DEFAULT_TELEMETRY_HUD_SCALE)
                .coerceIn(75, 165)
        )
    }
    var fontKey by remember {
        mutableStateOf(
            prefs.getString(LightspeedPreferences.KEY_TELEMETRY_HUD_FONT, LightspeedPreferences.DEFAULT_TELEMETRY_HUD_FONT)
                ?: LightspeedPreferences.DEFAULT_TELEMETRY_HUD_FONT
        )
    }
    var isFontListExpanded by remember { mutableStateOf(false) }
    var loadedFonts by remember { mutableStateOf<List<Triple<String, String, FontFamily?>>?>(null) }

    val selectedFontLabel = remember(fontKey) { DeviceFontScanner.getFontLabel(fontKey) }
    val selectedFontFamily = remember(fontKey) { DeviceFontScanner.resolveComposeFontFamily(fontKey) }

    LaunchedEffect(isFontListExpanded) {
        if (isFontListExpanded) {
            loadedFonts = null
            val startMs = android.os.SystemClock.uptimeMillis()
            val scanned = DeviceFontScanner.loadCleanResolvedFontsOnDemand()
            val elapsed = android.os.SystemClock.uptimeMillis() - startMs
            if (elapsed < 380L) {
                delay(380L - elapsed)
            }
            loadedFonts = scanned
        } else {
            loadedFonts = null
        }
    }

    val boxShape = RoundedCornerShape(12.dp)
    val boxBg = if (isM3) colorScheme.surfaceContainerHigh else lsColors.textPrimary.copy(alpha = 0.05f)
    val boxMod = if (isM3) {
        Modifier.fillMaxWidth().clip(boxShape).background(boxBg)
    } else {
        Modifier.fillMaxWidth().clip(boxShape).background(boxBg)
            .border(0.75.dp, lsColors.textPrimary.copy(alpha = 0.10f), boxShape)
    }
    val badgeBg = if (isM3) colorScheme.secondaryContainer else lsColors.accent.copy(alpha = 0.18f)
    val badgeFg = if (isM3) colorScheme.onSecondaryContainer else lsColors.accent

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // --- Telemetry Size Slider (75% - 165%) ---
        Column(modifier = boxMod.padding(horizontal = 11.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FormatSize,
                        contentDescription = null,
                        tint = if (isM3) colorScheme.primary else lsColors.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = lsString(R.string.thud_size_title),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = lsColors.textPrimary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .clickable {
                            LightspeedHapticEngine.tick(service)
                            scalePct = 100
                            prefs.edit().putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_SCALE, 100).apply()
                            LightspeedTelemetryHudOverlay.triggerLivePreview(service)
                        }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$scalePct%",
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            DeckSlider(
                value = scalePct.toFloat(),
                valueRange = 75f..165f,
                onValueChange = { raw ->
                    val snapped = ((raw / 5f).roundToInt() * 5).coerceIn(75, 165)
                    if (snapped != scalePct) {
                        scalePct = snapped
                        prefs.edit().putInt(LightspeedPreferences.KEY_TELEMETRY_HUD_SCALE, snapped).apply()
                        LightspeedTelemetryHudOverlay.triggerLivePreview(service)
                    }
                },
                modifier = Modifier.height(24.dp)
            )
        }

        // --- Animated On-Demand Device Font Family Selector ---
        Column(modifier = boxMod) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        LightspeedHapticEngine.tick(service)
                        isFontListExpanded = !isFontListExpanded
                    }
                    .padding(horizontal = 11.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = lsString(R.string.typo_font_family),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = if (isM3) colorScheme.primary else lsColors.accent
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = selectedFontLabel,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = selectedFontFamily,
                        color = lsColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (isFontListExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = null,
                    tint = if (isM3) colorScheme.primary else lsColors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            AnimatedVisibility(
                visible = isFontListExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val currentFonts = loadedFonts
                if (currentFonts == null) {
                    FontWritingLoadingAnimation(
                        accentColor = if (isM3) colorScheme.primary else lsColors.accent,
                        textColor = lsColors.textPrimary,
                        subColor = lsColors.textSecondary
                    )
                } else {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 210.dp)
                            .verticalScroll(scrollState)
                            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        currentFonts.forEach { (key, label, itemFont) ->
                            val isSelected = key == fontKey
                            val selBg = if (isM3) colorScheme.secondaryContainer else lsColors.accent.copy(alpha = 0.18f)
                            val selFg = if (isM3) colorScheme.onSecondaryContainer else lsColors.accent
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) selBg else Color.Transparent)
                                    .clickable {
                                        LightspeedHapticEngine.tick(service)
                                        fontKey = key
                                        prefs.edit().putString(LightspeedPreferences.KEY_TELEMETRY_HUD_FONT, key).apply()
                                        LightspeedTelemetryHudOverlay.triggerLivePreview(service)
                                        isFontListExpanded = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontFamily = itemFont,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) selFg else lsColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = selFg,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
