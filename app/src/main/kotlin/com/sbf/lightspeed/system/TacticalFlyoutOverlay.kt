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

object TacticalFlyoutLauncher {

    fun launch(context: Context) {
        val intent = Intent(context, TacticalFlyoutActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        context.startActivity(intent)
    }

    fun launchChatGPT(context: Context) {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage("com.openai.chatgpt")
        if (directIntent != null) {
            try {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "ChatGPT could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchClaude(context: Context) {
        val pm = context.packageManager
        val directIntent = pm.getLaunchIntentForPackage("com.anthropic.claude")
        if (directIntent != null) {
            try {
                directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(directIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Claude could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchGemini(context: Context) {
        val pm = context.packageManager
        val geminiPkgIntent = pm.getLaunchIntentForPackage("com.google.android.apps.bard")
        if (geminiPkgIntent != null) {
            try {
                geminiPkgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(geminiPkgIntent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        val assistIntents = listOf(
            Intent(Intent.ACTION_VOICE_COMMAND).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Intent.ACTION_ASSIST).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
        for (intent in assistIntents) {
            try {
                context.startActivity(intent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
            LightspeedHapticEngine.click(context)
        } catch (_: Exception) {
            Toast.makeText(context, "Gemini could not be opened", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchLens(context: Context) {
        val lensIntents = listOf(
            context.packageManager.getLaunchIntentForPackage("com.google.ar.lens"),
            Intent().apply {
                component = ComponentName("com.google.android.googlequicksearchbox", "com.google.android.apps.lens.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            Intent("android.intent.action.VIEW").apply {
                data = Uri.parse("googleapp://lens")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in lensIntents) {
            if (intent != null) {
                try {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LightspeedHapticEngine.click(context)
                    return
                } catch (_: Exception) {}
            }
        }

        Toast.makeText(context, "Google Lens not found", Toast.LENGTH_SHORT).show()
    }

    fun launchQrScanner(context: Context) {
        val qrIntents = listOf(
            Intent("com.google.android.gms.vision.barcode.SCAN").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent("com.google.zxing.client.android.SCAN").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("SCAN_MODE", "QR_CODE_MODE")
            },
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
            Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("googleapp://lens")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )

        for (intent in qrIntents) {
            try {
                context.startActivity(intent)
                LightspeedHapticEngine.click(context)
                return
            } catch (_: Exception) {}
        }

        Toast.makeText(context, "QR Scanner not found", Toast.LENGTH_SHORT).show()
    }
}

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
                        overridePendingTransition(0, 0)
                    }
                )
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

@Composable
fun TacticalFlyoutContent(
    context: Context,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var isRecordingActive by remember { mutableStateOf(TacticalAudioEngine.isRecordingActive()) }

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

                    // Section 3: Tactical Audio
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "TACTICAL AUDIO & VOICE RECORDER",
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
                                modifier = Modifier.weight(1.2f),
                                title = if (isRecordingActive) "STOP RECORDING" else "TACTICAL RECORDER",
                                subtitle = if (isRecordingActive) "Live Recording Active" else "Instant Audio Capture",
                                icon = if (isRecordingActive) Icons.Default.StopCircle else Icons.Default.Mic,
                                accentColor = if (isRecordingActive) Color(0xFFEF4444) else MaterialTheme.colorScheme.primary,
                                isPulse = isRecordingActive,
                                onClick = {
                                    TacticalAudioEngine.toggle(context)
                                    isRecordingActive = TacticalAudioEngine.isRecordingActive()
                                    if (!isRecordingActive) {
                                        onDismiss()
                                    }
                                }
                            )

                            TacticalFlyoutTile(
                                modifier = Modifier.weight(0.8f),
                                title = "VOICE APP",
                                subtitle = "Launch App",
                                icon = Icons.Default.GraphicEq,
                                accentColor = MaterialTheme.colorScheme.secondary,
                                onClick = {
                                    onDismiss()
                                    TacticalAudioEngine.launchSystemVoiceRecorder(context)
                                }
                            )
                        }
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
