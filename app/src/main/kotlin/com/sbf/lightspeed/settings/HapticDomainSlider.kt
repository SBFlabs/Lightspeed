package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlin.math.roundToInt

/**
 * Signature Lightspeed tactile slider row with predetermined snap stops horizontally
 * and fine percentage stepping when drifting finger vertically (up or down).
 */
@Composable
fun HapticDomainSliderRow(
    context: Context,
    prefs: SharedPreferences,
    title: String,
    subtitle: String,
    currentKey: String? = null,
    currentValue: String? = null,
    options: List<Pair<String, String>>,
    defaultVal: String = "tactical",
    onSelect: (String) -> Unit
) {
    fun keyToSliderVal(key: String): Float {
        if (key.startsWith("custom_")) {
            val pct = key.removePrefix("custom_").toFloatOrNull() ?: 50f
            val maxIdx = (options.size - 1).coerceAtLeast(1).toFloat()
            return (pct / 100f) * maxIdx
        }
        val idx = options.indexOfFirst { it.first == key }
        return if (idx >= 0) idx.toFloat() else 0f
    }

    var selectedKey by remember(currentKey, currentValue) {
        val initial = currentValue ?: currentKey?.let { prefs.getString(it, defaultVal) } ?: defaultVal
        mutableStateOf(initial)
    }

    var sliderVal by remember(currentKey, currentValue) {
        mutableFloatStateOf(keyToSliderVal(selectedKey))
    }

    val currentIdx = options.indexOfFirst { it.first == selectedKey }.let { if (it >= 0) it else 0 }

    val activeLabel = if (selectedKey.startsWith("custom_")) {
        val pct = selectedKey.removePrefix("custom_").toIntOrNull() ?: 50
        "Custom ($pct%)"
    } else {
        options.getOrNull(currentIdx)?.second ?: options.firstOrNull()?.second ?: "Standard"
    }

    fun applyKey(newKey: String) {
        selectedKey = newKey
        sliderVal = keyToSliderVal(newKey)
        onSelect(newKey)
        LightspeedHapticEngine.pulseClick(context, newKey)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.35f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = LightspeedStyle.colors.textPrimary,
                    maxLines = 1
                )
                if (subtitle.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        lineHeight = 13.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                    val nextIdx = (currentIdx + 1) % options.size
                    applyKey(options[nextIdx].first)
                }
            ) {
                Text(
                    text = activeLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        val maxOptionIdx = (options.size - 1).coerceAtLeast(1)

        DragOnlySlider(
            value = sliderVal,
            onValueChange = { newVal ->
                sliderVal = newVal
                val snappedIdx = newVal.roundToInt().coerceIn(0, maxOptionIdx)
                val diffFromInt = kotlin.math.abs(newVal - snappedIdx)
                val newKey = if (diffFromInt < 0.08f) {
                    options[snappedIdx].first
                } else {
                    val pct = (newVal / maxOptionIdx.toFloat() * 100f).roundToInt().coerceIn(0, 100)
                    "custom_$pct"
                }
                if (newKey != selectedKey) {
                    selectedKey = newKey
                    LightspeedHapticEngine.pulseTick(context, newKey)
                }
            },
            onValueChangeFinished = {
                val snappedIdx = sliderVal.roundToInt().coerceIn(0, maxOptionIdx)
                val diffFromInt = kotlin.math.abs(sliderVal - snappedIdx)
                val finalKey = if (diffFromInt < 0.12f) {
                    sliderVal = snappedIdx.toFloat()
                    options[snappedIdx].first
                } else {
                    val pct = (sliderVal / maxOptionIdx.toFloat() * 100f).roundToInt().coerceIn(0, 100)
                    "custom_$pct"
                }
                selectedKey = finalKey
                applyKey(finalKey)
            },
            valueRange = 0f..maxOptionIdx.toFloat(),
            steps = (options.size - 2).coerceAtLeast(0),
            precisionStep = 0.05f, // Finger UP/DOWN allows 5% finer sub-step values
            tapToJump = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            options.forEachIndexed { idx, (key, _) ->
                val isSelected = (idx == currentIdx && !selectedKey.startsWith("custom_"))
                val shortLabel = when (key) {
                    "off" -> "Off"
                    "jumps_only" -> "Trans"
                    "soft", "soft_low_freq" -> "Soft"
                    "heavy" -> "Heavy"
                    else -> "Crisp"
                }
                Text(
                    text = shortLabel,
                    fontSize = 9.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else LightspeedStyle.colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.clickable {
                        applyKey(key)
                    }
                )
            }
        }
    }
}
