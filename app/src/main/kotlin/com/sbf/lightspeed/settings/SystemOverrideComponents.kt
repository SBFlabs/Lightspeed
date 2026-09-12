package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
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

@Composable
fun SystemOverrideDeckCard(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "SYSTEM OVERRIDE DECK",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.6.sp
                    )
                    Text(
                        if (isShizukuActive) "ELEVATED EXECUTION ACTIVE // SHIZUKU" else "ELEVATED EXECUTION OFFLINE",
                        fontSize = 10.sp,
                        color = if (isShizukuActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // 1. Native Edge Gesture Sovereignty
            OverrideSettingRow(
                icon = Icons.Default.VerticalDistribute,
                title = "Native Edge Gesture Sovereignty",
                subtitle = "Force back-gesture insets to 0 to allow Lightspeed deflectors full edge control",
                onClick = { /* TODO: Execute settings put secure back_gesture_inset_scale_left 0 */ }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // 2. Continuous Animation Speeds
            OverrideSettingRow(
                icon = Icons.Default.Speed,
                title = "Continuous Animation Speeds",
                subtitle = "Global master scale & Window/Transition/Animator subdomains",
                onClick = { /* TODO: Expand slider UI */ }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // 3. Display Metrics (PPI/DPI)
            OverrideSettingRow(
                icon = Icons.Default.ScreenshotMonitor,
                title = "Display Metrics Overwrite",
                subtitle = "On-the-fly PPI / DPI / Smallest Width adjustments",
                onClick = { /* TODO: Expand slider UI */ }
            )

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

            // 4. Dynamic Font Scale
            OverrideSettingRow(
                icon = Icons.Default.FontDownload,
                title = "Dynamic Font Scale",
                subtitle = "Tactile slider for system FONT_SCALE override",
                onClick = { /* TODO: Expand slider UI */ }
            )
        }
    }
}

@Composable
fun OverrideSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.3f),
            modifier = Modifier.size(20.dp)
        )
    }
}
