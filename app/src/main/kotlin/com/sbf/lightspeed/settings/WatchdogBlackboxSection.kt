package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

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
                            text = lsString(R.string.wd_bb_anom),
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
                    text = lastCrashMessage ?: lsString(R.string.wd_core_exc),
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
                            Icon(Icons.Default.ContentCopy, contentDescription = lsString(R.string.wd_copy), tint = lsColors.textPrimary.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lsString(R.string.wd_copy), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary.copy(alpha = 0.9f))
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
                            Icon(Icons.Default.Code, contentDescription = lsString(R.string.wd_view_log), tint = cautionAmber, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lsString(R.string.wd_view_log), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = cautionAmber)
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
                            Icon(Icons.Default.DeleteOutline, contentDescription = lsString(R.string.wd_clear), tint = lsColors.accentDanger, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(lsString(R.string.wd_clear), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accentDanger)
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
                        text = lsString(R.string.wd_n_entries, flightLogEntries.size),
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
                    text = lsString(R.string.wd_no_entries),
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
                        Icon(Icons.Default.ContentCopy, contentDescription = lsString(R.string.wd_copy), tint = lsColors.textPrimary.copy(alpha = 0.9f), modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lsString(R.string.wd_copy), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary.copy(alpha = 0.9f))
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
                        Icon(Icons.Default.Code, contentDescription = lsString(R.string.wd_view_log), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lsString(R.string.wd_view_log), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                        Icon(Icons.Default.DeleteOutline, contentDescription = lsString(R.string.wd_clear), tint = lsColors.accentDanger, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(lsString(R.string.wd_clear), fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = lsColors.accentDanger)
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
                                text = lsString(R.string.wd_last_crash_anom, crashTimeStr),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = cautionAmber
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = lastCrashMessage ?: lsString(R.string.wd_unknown_anom),
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
                                    text = lastCrashStack ?: lsString(R.string.wd_no_stack),
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
                    text = lsString(R.string.wd_action_log_count, flightLogEntries.size),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.6.sp
                )

                if (flightLogEntries.isEmpty()) {
                    Text(
                        text = lsString(R.string.wd_no_recorded),
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
                Text(lsString(R.string.wd_copy_rep), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onClearActionLog) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = LightspeedTokens.StatusErrorSoft, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(lsString(R.string.wd_clear_log), color = LightspeedTokens.StatusErrorSoft, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                }
                TextButton(onClick = onDismiss) {
                    Text(lsString(R.string.wd_close_caps), color = lsColors.textPrimary.copy(alpha = 0.7f), fontSize = 11.5.sp)
                }
            }
        }
    )
}
