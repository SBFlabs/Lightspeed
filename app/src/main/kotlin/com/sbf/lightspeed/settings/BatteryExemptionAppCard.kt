package com.sbf.lightspeed.settings

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.PushPin
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
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BatteryExemptAppItem(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val appInfo: ApplicationInfo
)

@Composable
fun BatteryExemptionAppCard(
    context: Context,
    pm: PackageManager,
    appItem: BatteryExemptAppItem,
    isPinned: Boolean,
    isWhitelisted: Boolean,
    successGreen: Color,
    cautionAmber: Color,
    coroutineScope: CoroutineScope,
    onTogglePin: () -> Unit,
    onWhitelistChanged: (Boolean) -> Unit
) {
    val iconBitmap = remember(appItem.packageName) {
        try {
            pm.getApplicationIcon(appItem.appInfo).toBitmap(width = 64, height = 64).asImageBitmap()
        } catch (_: Throwable) {
            null
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = BorderStroke(
            1.dp,
            if (isPinned) Color(0xFFFFD54F).copy(alpha = 0.45f)
            else if (isWhitelisted) successGreen.copy(alpha = 0.25f)
            else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(appItem.label.take(1).uppercase(), fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appItem.label,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = appItem.packageName,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if (isPinned) Color(0xFFFFD54F) else Color.LightGray.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val ok = if (isWhitelisted) {
                            LightspeedWatchdogEngine.removeBatteryWhitelist(context, appItem.packageName)
                        } else {
                            LightspeedWatchdogEngine.whitelistBattery(context, appItem.packageName)
                        }
                        withContext(Dispatchers.Main) {
                            if (ok) {
                                LightspeedHapticEngine.tick(context)
                                val newStatus = !isWhitelisted
                                onWhitelistChanged(newStatus)
                                val msg = if (newStatus) "Battery whitelist granted for ${appItem.label}" else "Battery exemption removed for ${appItem.label}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Shizuku required for 1-tap battery whitelist", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                color = if (isWhitelisted) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isWhitelisted) successGreen.copy(alpha = 0.45f) else cautionAmber.copy(alpha = 0.45f)
                ),
                modifier = Modifier.height(28.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isWhitelisted) Icons.Default.BatteryChargingFull else Icons.Outlined.BatteryAlert,
                        contentDescription = null,
                        tint = if (isWhitelisted) successGreen else cautionAmber,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isWhitelisted) "BATTERY SAFE" else "WHITELIST BATTERY",
                        fontSize = 9.sp,
                        fontWeight = if (isWhitelisted) FontWeight.Bold else FontWeight.Medium,
                        color = if (isWhitelisted) successGreen else cautionAmber,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
