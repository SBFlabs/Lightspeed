package com.sbf.lightspeed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PinAppDialog(
    packageName: String,
    appName: String,
    isCurrentlyPinned: Boolean,
    dynamicPrimary: Color,
    onConfirm: (nowPinned: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isCurrentlyPinned) "Unpin App?" else "Pin to Quick Deck?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Text(
                text = if (isCurrentlyPinned)
                    "Remove $appName from the Quick Deck top list?"
                else
                    "Pin $appName to the Quick Deck top list for immediate access?",
                color = Color.LightGray,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(!isCurrentlyPinned) },
                colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary)
            ) {
                Text(if (isCurrentlyPinned) "UNPIN" else "PIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF181B24),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun DeepActivityWarningDialog(
    dynamicPrimary: Color,
    onProceed: (doNotShowAgain: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var doNotShowAgain by remember { mutableStateOf(false) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF161822).copy(alpha = 0.95f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161822).copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Deep Activity Warning",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Launching internal activities directly may cause instability or unexpected behavior.",
                    fontSize = 13.sp,
                    color = Color.LightGray.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { doNotShowAgain = !doNotShowAgain }
                        .padding(vertical = 4.dp)
                ) {
                    Checkbox(
                        checked = doNotShowAgain,
                        onCheckedChange = { doNotShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = dynamicPrimary,
                            uncheckedColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Do not show again",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = Color.LightGray, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onProceed(doNotShowAgain) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dynamicPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Understood", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
