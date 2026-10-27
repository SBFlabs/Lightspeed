@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AttitudeTargetRow(
    item: AttitudeTargetItem,
    isAssigned: Boolean,
    context: Context,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    bucket: LightspeedOrientationEngine.AttitudeBucket? = null,
    onShowDetailInfo: ((title: String, message: String) -> Unit)? = null,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isAssigned) dynamicPrimary.copy(alpha = 0.28f)
                    else if (item.isPinned) LightspeedTokens.StatusGoldPinSubtle
                    else LightspeedStyle.colors.textPrimary.copy(alpha = 0.06f)
                )
                .border(
                    width = 1.dp,
                    color = if (isAssigned) dynamicPrimary.copy(alpha = 0.65f)
                    else if (item.isPinned) LightspeedTokens.StatusGoldPin
                    else LightspeedStyle.colors.textPrimary.copy(alpha = 0.07f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            if (item.id == "keyguard:lockscreen") {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(LightspeedStyle.colors.accentGold.copy(alpha = 0.18f), RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = LightspeedStyle.colors.accentGold,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                val iconBmp by produceState<ImageBitmap?>(initialValue = null, key1 = item.id) {
                    value = withContext(Dispatchers.IO) {
                        LightspeedIconManager.getIconBitmap(context, item.id, useCache = true)?.asImageBitmap()
                    }
                }
                if (iconBmp != null) {
                    Image(
                        bitmap = iconBmp!!,
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    val iconVector = when (item.badge) {
                        "LAUNCHER" -> Icons.Default.Home
                        "PHONE" -> Icons.Default.Phone
                        "SMS" -> Icons.AutoMirrored.Filled.Chat
                        "SETTINGS" -> Icons.Default.Settings
                        else -> Icons.Default.Apps
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = null,
                            tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isPinned && item.id != "keyguard:lockscreen") {
                        Icon(
                            imageVector = Icons.Outlined.PushPin,
                            contentDescription = "Pinned",
                            tint = LightspeedStyle.colors.accentGold,
                            modifier = Modifier.size(13.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = item.label,
                        color = if (isAssigned) dynamicPrimary
                        else if (item.isPinned) LightspeedTokens.StatusGoldBright
                        else LightspeedStyle.colors.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = if (isAssigned || item.isPinned) FontWeight.Bold else FontWeight.SemiBold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            lineHeight = 16.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.badge != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (item.badge == "SYSTEM") LightspeedStyle.colors.accentGold.copy(alpha = 0.2f) else dynamicPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(
                                0.5.dp,
                                if (item.badge == "SYSTEM") LightspeedStyle.colors.accentGold.copy(alpha = 0.6f) else dynamicPrimary.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text(
                                text = item.badge,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (item.badge == "SYSTEM") LightspeedStyle.colors.accentGold else dynamicPrimary,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp),
                                softWrap = false,
                                maxLines = 1
                            )
                        }
                    }
                }
                Text(
                    text = item.subtitle,
                    color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        lineHeight = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (bucket != null && item.id == "keyguard:lockscreen" && bucket != LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(LightspeedStyle.colors.accentWarn.copy(alpha = 0.15f))
                        .border(1.dp, LightspeedStyle.colors.accentWarn.copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "LOCK SCREEN INTERLOCK",
                                "Under-display optical or ultrasonic fingerprint sensors (UDFPS) lock the Keyguard to 0° portrait at the Android OS level to keep the physical optical lens aligned with the OLED display.\n\nDevices with side power-button sensors, rear sensors, or face unlock will rotate normally."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Lockscreen Interlock Info",
                        tint = LightspeedStyle.colors.accentWarn,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else if (item.badge == "LAUNCHER" && bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(dynamicPrimary.copy(alpha = 0.15f))
                        .border(1.dp, dynamicPrimary.copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "ACTIVE GYRO DRIVER (PHONES VS TABLETS)",
                                "• Phone vs Tablet/Fold Discrepancy:\nTablets and large foldables (sw600dp+) feature adaptive multi-column grids that rotate cleanly into landscape. Standard phone launchers are designed solely for vertical portrait grids and glitch/squish when forced into landscape.\n\n• Lightspeed Sensor Portrait Solution:\nThe active 5 Hz gyro driver dynamically flips your phone launcher between 0° upright and 180° inverted portrait based on tilt, bypassing launcher NOSENSOR locks while strictly locking out 90° and 270° landscape.\n\n• Zero Glitches:\nGives you full upside-down usability (charging from top or reading in bed) with zero home screen overlapping."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Active Gyro Info",
                        tint = dynamicPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else if (item.badge == "LAUNCHER" && (bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_360 || bucket == LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE)) {
                Box(
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(LightspeedStyle.colors.accentWarn.copy(alpha = 0.15f))
                        .border(1.dp, LightspeedStyle.colors.accentWarn.copy(alpha = 0.45f), CircleShape)
                        .clickable {
                            onShowDetailInfo?.invoke(
                                "LAUNCHER LANDSCAPE ADVISORY",
                                "• Form Factor Discrepancy:\nTablets and foldables (sw600dp+) handle landscape home screens natively with wide multi-column layouts. Standard phone launchers lack adaptive landscape grids.\n\n• Phone Launcher Glitching:\nForcing landscape on a phone launcher compresses widgets, overlaps search bars, and misaligns icon grids.\n\n• Recommended Setting:\nFor phones, assign the Launcher to 'Sensor Portrait' instead to enjoy 180° inverted portrait freedom without landscape glitches."
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PriorityHigh,
                        contentDescription = "Launcher Landscape Advisory",
                        tint = LightspeedStyle.colors.accentWarn,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            if (isAssigned) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .background(dynamicPrimary, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "ASSIGNED",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(18.dp)
                        .border(1.2.dp, LightspeedStyle.colors.textPrimary.copy(alpha = 0.22f), CircleShape)
                )
            }
        }
    }
}

