package com.sbf.lightspeed.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Dvr
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.FlightLog
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

@Composable
fun WatchdogBlackboxCards(
    lastCrashTimestamp: Long,
    lastCrashMessage: String?,
    cautionAmber: Color,
    flightLogEntries: List<FlightLog.Entry>,
    blackboxTitle: String,
    onCopyReport: () -> Unit,
    onViewLog: () -> Unit,
    onClearCrash: () -> Unit,
    onClearActionLog: () -> Unit
) {
    val lsColors = LightspeedStyle.colors

    // Flight Recorder Telemetry Anomaly Notice (if recorded crash exists)
    if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
        val timeStr = remember(lastCrashTimestamp) {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCrashTimestamp))
        }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onViewLog() },
            shape = RoundedCornerShape(10.dp),
            color = cautionAmber.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = cautionAmber, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BLACKBOX ANOMALY",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = cautionAmber,
                            letterSpacing = 0.6.sp
                        )
                    }
                    Text(
                        text = timeStr,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = cautionAmber.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = lastCrashMessage ?: "Core exception recorded",
                    fontSize = 11.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Copy button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lsColors.textPrimary.copy(alpha = 0.08f),
                        border = BorderStroke(0.8.dp, lsColors.textPrimary.copy(alpha = 0.2f)),
                        modifier = Modifier.clickable { onCopyReport() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = lsColors.textPrimary.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("COPY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary.copy(alpha = 0.9f))
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // View Log button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = cautionAmber.copy(alpha = 0.18f),
                        border = BorderStroke(0.8.dp, cautionAmber.copy(alpha = 0.45f)),
                        modifier = Modifier.clickable { onViewLog() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, contentDescription = "View Log", tint = cautionAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("VIEW LOG", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = cautionAmber)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Clear button
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = lsColors.accentDanger.copy(alpha = 0.15f),
                        border = BorderStroke(0.8.dp, lsColors.accentDanger.copy(alpha = 0.4f)),
                        modifier = Modifier.clickable { onClearCrash() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = lsColors.accentDanger, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CLEAR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accentDanger)
                        }
                    }
                }
            }
        }
    }

    // Always-Visible Flight Blackbox Action Log Card
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onViewLog() },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Dvr, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = blackboxTitle.uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.6.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "${flightLogEntries.size} ENTRIES",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val newestEntry = flightLogEntries.firstOrNull()
            if (newestEntry != null) {
                val statusColor = if (newestEntry.ok) lsColors.accentOk else lsColors.accentDanger
                val statusText = if (newestEntry.ok) "OK" else "FAIL"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "[${newestEntry.formatTimestamp()}] [${newestEntry.tag}] ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.textSecondary
                    )
                    Text(
                        text = "[$statusText] ",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = statusColor
                    )
                    Text(
                        text = newestEntry.text,
                        fontSize = 10.sp,
                        color = lsColors.textPrimary.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    text = "No action entries logged",
                    fontSize = 10.5.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // COPY button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = lsColors.textPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(0.8.dp, lsColors.textPrimary.copy(alpha = 0.2f)),
                    modifier = Modifier.clickable { onCopyReport() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = lsColors.textPrimary.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPY", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary.copy(alpha = 0.9f))
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // VIEW LOG button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier.clickable { onViewLog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Code, contentDescription = "View Log", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("VIEW LOG", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // CLEAR button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = lsColors.accentDanger.copy(alpha = 0.15f),
                    border = BorderStroke(0.8.dp, lsColors.accentDanger.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onClearActionLog() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = lsColors.accentDanger, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CLEAR", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accentDanger)
                    }
                }
            }
        }
    }
}

@Composable
fun WatchdogBlackboxDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    lastCrashTimestamp: Long,
    lastCrashMessage: String?,
    lastCrashStack: String?,
    flightLogEntries: List<FlightLog.Entry>,
    blackboxTitle: String,
    cautionAmber: Color,
    onCopyReport: () -> Unit,
    onClearActionLog: () -> Unit
) {
    if (!showDialog) return
    val lsColors = LightspeedStyle.colors

    DeckDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) Icons.Default.Warning else Icons.AutoMirrored.Filled.Dvr,
                contentDescription = null,
                tint = if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) cautionAmber else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = blackboxTitle.uppercase(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (lastCrashTimestamp > 0L && !lastCrashMessage.isNullOrBlank()) {
                    val crashTimeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(lastCrashTimestamp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        color = cautionAmber.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "LAST CRASH ANOMALY ($crashTimeStr)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = cautionAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastCrashMessage ?: "Unknown Anomaly",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lsColors.textPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 140.dp)
                                    .background(LightspeedTokens.TacticalSurfaceTerminal, shape = RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = lastCrashStack ?: "No stacktrace recorded",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = LightspeedTokens.StatusSky,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "ACTION LOG (${flightLogEntries.size}/50)",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.6.sp
                )

                if (flightLogEntries.isEmpty()) {
                    Text(
                        text = "No action entries recorded.",
                        fontSize = 11.sp,
                        color = lsColors.textDisabled,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    flightLogEntries.forEach { entry ->
                        val statusColor = if (entry.ok) lsColors.accentOk else lsColors.accentDanger
                        val statusText = if (entry.ok) "OK" else "FAIL"
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = lsColors.textPrimary.copy(alpha = 0.04f),
                            border = BorderStroke(0.8.dp, lsColors.textPrimary.copy(alpha = 0.08f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${entry.formatFullTimestamp()} [${entry.tag}]",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = lsColors.textSecondary
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = statusColor.copy(alpha = 0.15f),
                                        border = BorderStroke(0.6.dp, statusColor.copy(alpha = 0.4f))
                                    ) {
                                        Text(
                                            text = statusText,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.text,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = lsColors.textPrimary.copy(alpha = 0.9f),
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCopyReport) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("COPY REPORT", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClearActionLog) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = LightspeedTokens.StatusErrorSoft, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CLEAR LOG", color = LightspeedTokens.StatusErrorSoft, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text("CLOSE", color = lsColors.textPrimary.copy(alpha = 0.7f), fontSize = 11.5.sp)
                }
            }
        }
    )
}
