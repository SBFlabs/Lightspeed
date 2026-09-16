package com.sbf.lightspeed.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun PerimeterServiceCard(
    context: Context,
    pm: PackageManager,
    sInfo: AccessibilityServiceInfo,
    componentId: String,
    isPinned: Boolean,
    isServiceEnabled: Boolean,
    isProtected: Boolean,
    isBatteryWhitelisted: Boolean,
    successGreen: Color,
    cautionAmber: Color,
    onTogglePin: () -> Unit,
    onToggleService: (Boolean) -> Unit,
    onToggleProtected: () -> Unit,
    onWhitelistBattery: () -> Unit
) {
    val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")

    val sLabel = remember(componentId) {
        try {
            sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
        } catch (_: Exception) { sPkg }
    }

    val iconBitmap = remember(sPkg) {
        try {
            val d = sInfo.resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(sPkg)
            d.toBitmap(width = 64, height = 64).asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
        border = BorderStroke(
            1.dp,
            if (isPinned) Color(0xFFFFD54F).copy(alpha = 0.45f)
            else if (isServiceEnabled) successGreen.copy(alpha = 0.25f)
            else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: App Icon, Name, Package, Pin, Master Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(sLabel.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sLabel,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = sPkg,
                        fontSize = 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onTogglePin, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (isPinned) Color(0xFFFFD54F) else Color.LightGray.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Switch(
                    checked = isServiceEnabled,
                    onCheckedChange = onToggleService,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = successGreen,
                        uncheckedThumbColor = Color.LightGray,
                        uncheckedTrackColor = Color.DarkGray
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }

            // Row 2: Defense Controls & Telemetry Tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Defense Sentinel Shield Toggle Button
                Surface(
                    onClick = onToggleProtected,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isProtected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(
                        1.dp,
                        if (isProtected) MaterialTheme.colorScheme.primary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isProtected) Icons.Default.Shield else Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = if (isProtected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isProtected) "SHIELDED" else "ARM SHIELD",
                            fontSize = 9.5.sp,
                            fontWeight = if (isProtected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isProtected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Battery Unrestricted Whitelist Toggle Button
                Surface(
                    onClick = onWhitelistBattery,
                    shape = RoundedCornerShape(8.dp),
                    color = if (isBatteryWhitelisted) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.12f),
                    border = BorderStroke(
                        1.dp,
                        if (isBatteryWhitelisted) successGreen.copy(alpha = 0.45f) else cautionAmber.copy(alpha = 0.45f)
                    ),
                    modifier = Modifier.weight(1f).height(30.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isBatteryWhitelisted) Icons.Default.BatteryChargingFull else Icons.Outlined.BatteryAlert,
                            contentDescription = null,
                            tint = if (isBatteryWhitelisted) successGreen else cautionAmber,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isBatteryWhitelisted) "BATTERY SAFE" else "WHITELIST BATTERY",
                            fontSize = 9.5.sp,
                            fontWeight = if (isBatteryWhitelisted) FontWeight.Bold else FontWeight.Medium,
                            color = if (isBatteryWhitelisted) successGreen else cautionAmber,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
