package com.sbf.lightspeed.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PerimeterShizukuBanner(
    isShizukuActive: Boolean,
    successGreen: Color,
    cautionAmber: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isShizukuActive) successGreen.copy(alpha = 0.12f) else cautionAmber.copy(alpha = 0.12f)
        ),
        border = BorderStroke(
            1.dp,
            if (isShizukuActive) successGreen.copy(alpha = 0.4f) else cautionAmber.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isShizukuActive) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                tint = if (isShizukuActive) successGreen else cautionAmber,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = if (isShizukuActive) "SHIZUKU PRIVILEGED BRIDGE: ACTIVE" else "SHIZUKU PRIVILEGED BRIDGE: STANDBY",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = if (isShizukuActive) successGreen else cautionAmber,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isShizukuActive) "Sub-100ms instant toggles armed without settings page" else "Standby. Toggles will launch system settings page",
                    fontSize = 9.5.sp,
                    color = Color.LightGray.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PerimeterTelemetrySummaryCard(
    context: Context,
    totalCount: Int,
    activeCount: Int,
    shieldedCount: Int,
    pinnedCount: Int,
    successGreen: Color,
    coroutineScope: CoroutineScope,
    onRefreshTrigger: () -> Unit,
    onRefreshNeeded: () -> Unit,
    onOpenExemptionDeck: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("$totalCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = successGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, successGreen.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ACTIVE", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("$activeCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = successGreen, maxLines = 1)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("SHIELDED", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("$shieldedCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFD54F).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFFFFD54F).copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("PINNED", fontSize = 8.5.sp, color = Color.LightGray, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text("$pinnedCount", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F), maxLines = 1)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            val count = LightspeedWatchdogEngine.pulsePerimeterServices(context)
                            withContext(Dispatchers.Main) {
                                LightspeedHapticEngine.heavyClick(context)
                                Toast.makeText(context, "Perimeter pulse: $count sentinels verified & revivified", Toast.LENGTH_SHORT).show()
                                onRefreshTrigger()
                                onRefreshNeeded()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pulse Sentinels", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                OutlinedButton(
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        onOpenExemptionDeck()
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Exempt More Apps", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = Color(0xFF00E5FF), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
