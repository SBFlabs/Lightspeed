package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.ui.draw.scale

import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemOverrideDeckContents(
    context: Context,
    prefs: SharedPreferences
) {
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive
    val scope = rememberCoroutineScope()

    // State for Edge Gesture Sovereignty
    // State for Edge Gesture Sovereignty
    var edgeGestureScale by remember { mutableFloatStateOf(1.0f) }
    var animationScale by remember { mutableFloatStateOf(1.0f) }
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Read from system settings directly
                val left = Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                edgeGestureScale = left
                val anim = Settings.Global.getFloat(context.contentResolver, "window_animation_scale", 1.0f)
                animationScale = anim
                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)
                fontScale = fScale
            } catch (e: Exception) {
                // Ignore exception, defaults are set
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (isShizukuActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "ELEVATED EXECUTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.6.sp
                )
                Text(
                    if (isShizukuActive) "SHIZUKU ACTIVE" else "SHIZUKU OFFLINE",
                    fontSize = 10.sp,
                    color = if (isShizukuActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        OverrideSliderRow(
            icon = Icons.Default.VerticalDistribute,
            title = "Native Edge Gesture Sovereignty",
            subtitle = "Force back-gesture insets to 0 to allow Lightspeed full edge control",
            value = edgeGestureScale,
            valueRange = 0.0f..2.0f,
            steps = 19,
            isEnabled = isShizukuActive,
            onValueChange = { edgeGestureScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left ${edgeGestureScale}")
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right ${edgeGestureScale}")
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 2. Animation Speeds
        OverrideSliderRow(
            icon = Icons.Default.Speed,
            title = "Animation Speeds",
            subtitle = "Global master scale & Window/Transition/Animator subdomains",
            value = animationScale,
            valueRange = 0.0f..2.0f,
            steps = 19,
            isEnabled = isShizukuActive,
            onValueChange = { animationScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put global window_animation_scale ${animationScale}")
                    ElevatedTaskCloser.execShizuku("settings put global transition_animation_scale ${animationScale}")
                    ElevatedTaskCloser.execShizuku("settings put global animator_duration_scale ${animationScale}")
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 3. Display Metrics
        OverrideSliderRow(
            icon = Icons.Default.ScreenshotMonitor,
            title = "Display Metrics",
            subtitle = "On-the-fly PPI / DPI adjustments (Default: ${context.resources.configuration.densityDpi})",
            value = displayDpi,
            valueRange = 200.0f..800.0f,
            steps = 59,
            valueFormatter = { "${it.toInt()} dpi" },
            isEnabled = isShizukuActive,
            onValueChange = { displayDpi = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("wm density ${displayDpi.toInt()}")
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 4. Font Scale
        OverrideSliderRow(
            icon = Icons.Default.FontDownload,
            title = "Font Scale",
            subtitle = "Tactile slider for system FONT_SCALE override",
            value = fontScale,
            valueRange = 0.5f..2.0f,
            steps = 14,
            isEnabled = isShizukuActive,
            onValueChange = { fontScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put system font_scale ${fontScale}")
                }
            }
        )
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 5. Custom Lock-Screen Shortcuts Engine
        OverrideSettingRow(
            icon = Icons.Default.LockOpen,
            title = "Lock-Screen Shortcuts",
            subtitle = "Remapping left/right lockscreen shortcuts directly via secure settings",
            isEnabled = isShizukuActive,
            onClick = { /* TODO */ }
        )
    }
}

@Composable
fun OverrideToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    isEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled) { onCheckedChange(!isChecked) }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f)
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = isEnabled,
            modifier = Modifier.scale(0.85f),
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.error)
        )
    }
}

@Composable
fun OverrideSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = isEnabled) { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f)
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = if (isEnabled) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun OverrideSliderRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    isEnabled: Boolean,
    valueFormatter: (Float) -> String = { String.format("%.2fx", it) },
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f)
                )
            }
            Text(
                text = valueFormatter(value),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = isEnabled,
            modifier = Modifier.fillMaxWidth().height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                activeTrackColor = if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.1f),
                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
