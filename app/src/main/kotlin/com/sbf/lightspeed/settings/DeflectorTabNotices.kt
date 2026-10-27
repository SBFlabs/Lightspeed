package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

@Composable
fun EdgeSovereigntyNoticeCard(
    exceedsEdgeThreshold: Boolean,
    deflectorCoverageRatio: Float,
    totalDeflectorSpanDp: Int,
    screenHeightDp: Int,
    onDismiss: () -> Unit,
    onOpenOverrides: () -> Unit
) {
    val cautionAmber = LightspeedStyle.colors.accentWarn
    val noticeContainerColor = if (exceedsEdgeThreshold) LightspeedTokens.TacticalNoticeWarm.copy(alpha = 0.70f) else LightspeedTokens.TacticalNoticeDark.copy(alpha = 0.55f)
    val noticeBorderColor = if (exceedsEdgeThreshold) cautionAmber.copy(alpha = 0.50f) else LightspeedTokens.StatusCyanSoft.copy(alpha = 0.35f)
    val noticeAccentColor = if (exceedsEdgeThreshold) cautionAmber else LightspeedTokens.StatusCyanSoft

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = noticeContainerColor,
        border = BorderStroke(1.dp, noticeBorderColor),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (exceedsEdgeThreshold) Icons.Default.Warning else Icons.Default.Info,
                        contentDescription = null,
                        tint = noticeAccentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (exceedsEdgeThreshold) {
                            "ANDROID EDGE GESTURE NOTICE · HIGH SPAN (${(deflectorCoverageRatio * 100).toInt()}%)"
                        } else {
                            "ANDROID EDGE GESTURE NOTICE"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = noticeAccentColor
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = LightspeedStyle.colors.textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (exceedsEdgeThreshold) {
                    "Total deflector span along the display edge (${totalDeflectorSpanDp}dp) exceeds 25% of the side (${(screenHeightDp * 0.25f).toInt()}dp recommended threshold). This may overlap or conflict with Android's native back swipe gestures. You can reduce vector heights or neutralize edge back insets in System Overrides."
                } else {
                    "Android's native back gestures are active along the screen edges and may conflict with deflector swipes. You can neutralize native back insets anytime under Central Command → HUD Strip → System Overrides."
                },
                fontSize = 11.5.sp,
                color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.85f),
                lineHeight = 16.sp
            )

            if (exceedsEdgeThreshold) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onOpenOverrides,
                    modifier = Modifier.fillMaxWidth().height(34.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, cautionAmber.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = cautionAmber.copy(alpha = 0.12f),
                        contentColor = cautionAmber
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                ) {
                    Text(
                        "⚡ OPEN SYSTEM OVERRIDES (EDGE NEUTRALIZATION)",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun DeflectorOverridesDialog(
    visible: Boolean,
    context: Context,
    prefs: SharedPreferences,
    onDismiss: () -> Unit
) {
    if (!visible) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        DeckGlassCardSurface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "SYSTEM OVERRIDES",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = LightspeedStyle.colors.textPrimary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = LightspeedStyle.colors.textPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    SystemOverrideDeckContents(context, prefs)
                }
            }
        }
    }
}
