package com.sbf.lightspeed.system

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OmniscientAudioDockActivity : ComponentActivity() {

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        
        setContent {
            val dynamicPrimary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(resources.getColor(android.R.color.system_accent1_400, theme))
            } else {
                Color(0xFF80D8FF)
            }
            val dynamicSecondary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Color(resources.getColor(android.R.color.system_accent2_300, theme))
            } else {
                Color(0xFFB39DDB)
            }

            var masterVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
            
            var isEntering by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(targetValue = if (isEntering) 1f else 0.85f, animationSpec = tween(250))
            val alpha by animateFloatAsState(targetValue = if (isEntering) 1f else 0f, animationSpec = tween(200))

            LaunchedEffect(Unit) {
                LightspeedHapticEngine.tick(this@OmniscientAudioDockActivity)
                isEntering = true
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha * 0.4f))
                    .clickable { 
                        isEntering = false
                        window.decorView.postDelayed({ finish() }, 250)
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .fillMaxWidth(0.88f)
                        .padding(end = 16.dp)
                        .scale(scale)
                        .clickable(enabled = false) {}, // Absorb touches
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141419).copy(alpha = alpha * 0.98f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = dynamicPrimary, modifier = Modifier.size(28.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("AUDIO COMMAND", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Master Volume
                        Text("MASTER MEDIA", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Slider(
                                value = masterVolume,
                                onValueChange = { 
                                    masterVolume = it 
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                                },
                                valueRange = 0f..maxVolume,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = dynamicPrimary, activeTrackColor = dynamicPrimary)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${((masterVolume / maxVolume) * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // App Sovereignty Area (Stub for now)
                        Text("APP SOVEREIGNTY (ROOT)", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Spotify Stub
                        AppVolumeRow("Spotify", Icons.Default.MusicNote, 0.8f, isPinned = true, dynamicPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        // YouTube Stub
                        AppVolumeRow("YouTube", Icons.Default.PlayCircle, 0.4f, isPinned = false, dynamicPrimary)

                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DockActionButton(Modifier.weight(1f), "MUTE ALL", Icons.Default.VolumeOff, Color(0xFFEF4444)) {}
                            DockActionButton(Modifier.weight(1f), "DND", Icons.Default.DoNotDisturbOn, dynamicSecondary) {}
                            DockActionButton(Modifier.weight(1f), "AutoEQ", Icons.Default.Tune, Color(0xFF10B981)) {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppVolumeRow(name: String, icon: ImageVector, initialVol: Float, isPinned: Boolean, tint: Color) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = vol,
                onValueChange = { vol = it },
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.8f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = { pinned = !pinned },
            modifier = Modifier.size(40.dp).background(if (pinned) tint.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f))
        }
    }
}

@Composable
fun DockActionButton(modifier: Modifier, title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(64.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}
