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
    var edgeGestureEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Read from system settings directly
                val left = Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                edgeGestureEnabled = (left == 0.0f)
            } catch (e: Settings.SettingNotFoundException) {
                edgeGestureEnabled = false
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

        // 1. Native Edge Gesture Sovereignty
        OverrideToggleRow(
            icon = Icons.Default.VerticalDistribute,
            title = "Native Edge Gesture Sovereignty",
            subtitle = "Force back-gesture insets to 0 to allow Lightspeed full edge control",
            isChecked = edgeGestureEnabled,
            isEnabled = isShizukuActive,
            onCheckedChange = { checked ->
                if (!isShizukuActive) return@OverrideToggleRow
                edgeGestureEnabled = checked
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    val scale = if (checked) "0" else "1"
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left $scale")
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right $scale")
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 2. Animation Speeds
        OverrideSettingRow(
            icon = Icons.Default.Speed,
            title = "Animation Speeds",
            subtitle = "Global master scale & Window/Transition/Animator subdomains",
            isEnabled = isShizukuActive,
            onClick = { /* TODO */ }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 3. Display Metrics (PPI/DPI)
        OverrideSettingRow(
            icon = Icons.Default.ScreenshotMonitor,
            title = "Display Metrics",
            subtitle = "On-the-fly PPI / DPI / Smallest Width adjustments",
            isEnabled = isShizukuActive,
            onClick = { /* TODO */ }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 4. Font Scale
        OverrideSettingRow(
            icon = Icons.Default.FontDownload,
            title = "Font Scale",
            subtitle = "Tactile slider for system FONT_SCALE override",
            isEnabled = isShizukuActive,
            onClick = { /* TODO */ }
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
