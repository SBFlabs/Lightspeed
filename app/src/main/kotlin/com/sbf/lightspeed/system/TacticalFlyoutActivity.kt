package com.sbf.lightspeed.system

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class TacticalFlyoutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setDimAmount(0.65f)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setContent {
            LightspeedTheme(forceDark = true) {
                TacticalFlyoutContent(
                    context = this,
                    onDismiss = {
                        finish()
                    }
                )
            }
        }
    }

    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }
}

@Composable
fun TacticalFlyoutContent(
    context: Context,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }



    BackHandler {
        onDismiss()
    }

    LaunchedEffect(Unit) {
        isVisible = true
        LightspeedHapticEngine.heavyClick(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isVisible = false
                onDismiss()
            },
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent click through */ }
                    .border(
                        1.dp,
                        Color(0x33FFFFFF),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xF0101216))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TACTICAL QUICK FLYOUT",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.5.sp
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Section 1: AI Assistants
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AI ASSISTANTS & VOICE AGENTS",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TacticalFlyoutTile(
                                modifier = Modifier.weight(1f),
                                title = "ChatGPT",
                                subtitle = "Voice / Chat",
                                icon = Icons.Default.AutoAwesome,
                                accentColor = Color(0xFF10A37F),
                                onClick = {
                                    onDismiss()
                                    TacticalFlyoutLauncher.launchChatGPT(context)
                                }
                            )

                            TacticalFlyoutTile(
                                modifier = Modifier.weight(1f),
                                title = "Claude",
                                subtitle = "Anthropic AI",
                                icon = Icons.Default.Psychology,
                                accentColor = Color(0xFFD97706),
                                onClick = {
                                    onDismiss()
                                    TacticalFlyoutLauncher.launchClaude(context)
                                }
                            )

                            TacticalFlyoutTile(
                                modifier = Modifier.weight(1f),
                                title = "Gemini",
                                subtitle = "Google AI",
                                icon = Icons.Default.Stars,
                                accentColor = Color(0xFF3B82F6),
                                onClick = {
                                    onDismiss()
                                    TacticalFlyoutLauncher.launchGemini(context)
                                }
                            )
                        }
                    }

                    // Section 2: Optical Tools
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "OPTICAL SENSORS & VISION",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TacticalFlyoutTile(
                                modifier = Modifier.weight(1f),
                                title = "Google Lens",
                                subtitle = "Visual OCR Search",
                                icon = Icons.Default.CameraAlt,
                                accentColor = Color(0xFFEA4335),
                                onClick = {
                                    onDismiss()
                                    TacticalFlyoutLauncher.launchLens(context)
                                }
                            )

                            TacticalFlyoutTile(
                                modifier = Modifier.weight(1f),
                                title = "QR Scanner",
                                subtitle = "Optical Code Scan",
                                icon = Icons.Default.QrCodeScanner,
                                accentColor = Color(0xFF06B6D4),
                                onClick = {
                                    onDismiss()
                                    TacticalFlyoutLauncher.launchQrScanner(context)
                                }
                            )
                        }
                    }

                    // Section 3: Core Thermal / System Reboot
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "CORE THERMAL / SYSTEM REBOOT",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )

                        com.sbf.lightspeed.settings.CoreCoolingTripleLockButton(
                            onExecuteReboot = {
                                val ok = com.sbf.lightspeed.system.LightspeedWatchdogEngine.executeCoreCoolingReboot(context)
                                if (!ok) {
                                    Toast.makeText(context, "Shizuku or Root required for reboot", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TacticalFlyoutTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isPulse: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .border(
                1.dp,
                if (isPulse) Color(0xFFEF4444) else accentColor.copy(alpha = 0.35f),
                RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPulse) Color(0xFFEF4444).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 9.5.sp,
                color = Color.White.copy(alpha = 0.65f),
                maxLines = 1
            )
        }
    }
}
