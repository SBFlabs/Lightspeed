package com.sbf.lightspeed.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Tactical visual alert card and one-tap zero-out button for Android native back gesture conflicts.
 */
@Composable
fun SystemOverrideBackConflictCard(
    context: Context,
    prefs: SharedPreferences,
    edgeGestureScale: Float,
    isShizukuActive: Boolean,
    scope: CoroutineScope,
    onScaleChanged: (Float) -> Unit
) {
    val lsColors = LightspeedStyle.colors

    if (edgeGestureScale > 0.01f) {
        DeckCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NATIVE BACK CONFLICT (${(edgeGestureScale * 100).toInt()}% SENSITIVITY)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Android's native back gesture sensitivity is currently active along the screen edges. This causes ghost system back triggers when swiping Deflectors. Zero-out native insets for 100% clean Lightspeed edge sovereignty.",
                    fontSize = 12.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                DeckButton(
                    onClick = {
                        if (isShizukuActive) {
                            LightspeedHapticEngine.heavyClick(context)
                            onScaleChanged(0.0f)
                            prefs.edit().putFloat("sys_override_edge_gesture", 0.0f).apply()
                            scope.launch(Dispatchers.IO) {
                                ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left 0")
                                ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right 0")
                            }
                            Toast.makeText(context, "⚡ Native back insets zeroed! Deflectors have full edge sovereignty.", Toast.LENGTH_SHORT).show()
                        } else {
                            val adbCmd = "adb shell settings put secure back_gesture_inset_scale_left 0 && adb shell settings put secure back_gesture_inset_scale_right 0"
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", adbCmd))
                            Toast.makeText(context, "ADB command copied to clipboard! Run in terminal to zero insets.", Toast.LENGTH_LONG).show()
                        }
                    },
                    isDestructive = isShizukuActive,
                    modifier = Modifier.fillMaxWidth().height(38.dp)
                ) {
                    Text(
                        text = if (isShizukuActive) "⚡ ZERO-OUT NATIVE BACK (RECOMMENDED)" else "📋 COPY ADB ZERO-OUT COMMAND",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                }
            }
        }
    } else {
        DeckCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, lsColors.accentOk.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = lsColors.accentOk,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "FULL EDGE SOVEREIGNTY ACTIVE (0.0x INSETS)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = lsColors.accentOk,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        text = "Android native edge back gesture is fully neutralized. Zero touch-trapping or accidental back collisions.",
                        fontSize = 11.sp,
                        color = lsColors.textSecondary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}
