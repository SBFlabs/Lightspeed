package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedHapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun PerimeterDialogHeader(
    context: Context,
    haptic: HapticFeedback,
    coroutineScope: CoroutineScope,
    dragOffsetY: Animatable<Float, AnimationVector1D>,
    thresholdPx: Float,
    onDismiss: () -> Unit
) {
    var hasCrossedThreshold by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { hasCrossedThreshold = false },
                    onDragEnd = {
                        if (dragOffsetY.value >= thresholdPx) {
                            onDismiss()
                        } else {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(
                                    0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffsetY.animateTo(0f)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val current = dragOffsetY.value + dragAmount
                        val newOffset = if (current <= 0f) 0f
                        else if (current <= thresholdPx) current
                        else thresholdPx + (current - thresholdPx) * 0.35f

                        if (!hasCrossedThreshold && newOffset >= thresholdPx) {
                            hasCrossedThreshold = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            LightspeedHapticEngine.tick(context)
                        } else if (hasCrossedThreshold && newOffset < thresholdPx) {
                            hasCrossedThreshold = false
                        }

                        coroutineScope.launch {
                            dragOffsetY.snapTo(newOffset)
                        }
                    }
                )
            }
    ) {
        // Sleek Drag Handle Pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(width = 38.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.35f))
            )
        }

        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "PERIMETER SENTINELS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        "Universal accessibility manager & privileged controls",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(32.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.08f))
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}
