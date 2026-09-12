package com.sbf.lightspeed.system

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.media.session.MediaController
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.roundToInt

class OmniscientAudioDockActivity : ComponentActivity() {
    private fun getRawActiveAudioPackages(context: Context): List<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = context.packageManager
        val configs = audioManager.activePlaybackConfigurations
        val pkgs = mutableListOf<String>()
        
        for (config in configs) {
            val state = try {
                config.javaClass.getMethod("getPlayerState").invoke(config) as Int
            } catch (e: Exception) { -1 }
            
            if (state == 2) {
                try {
                    val getClientUidMethod = config.javaClass.getMethod("getClientUid")
                    val uid = getClientUidMethod.invoke(config) as Int
                    val packages = pm.getPackagesForUid(uid)
                    if (packages != null) {
                        pkgs.addAll(packages)
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
        return pkgs.distinct()
    }


    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes.blurBehindRadius = 120 // MASSIVE frosted glass blur for the entire background
            window.attributes = window.attributes
        }

        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val pm = packageManager
        
        // Fetch active media controllers
        val activeControllers = LightspeedMediaManager.getActiveControllers(this)
        val sessionPackages = activeControllers.map { it.packageName }
        val rawPackages = getRawActiveAudioPackages(this)
        
        val mergedPackages = (sessionPackages + rawPackages).distinct()
        
        val activeAppsList = mergedPackages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(appInfo).toString()
                val icon = pm.getApplicationIcon(appInfo)
                ActiveAppInfo(pkg, label, icon)
            } catch (e: Exception) {
                null
            }
        }

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
            
            var isVisible by remember { mutableStateOf(false) }
            val scale by animateFloatAsState(targetValue = if (isVisible) 1f else 0.85f, animationSpec = tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = tween(350))

            LaunchedEffect(Unit) {
                LightspeedHapticEngine.tick(this@OmniscientAudioDockActivity)
                isVisible = true
            }

            DisposableEffect(Unit) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        if (intent?.action == "android.media.VOLUME_CHANGED_ACTION") {
                            val type = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                            if (type == AudioManager.STREAM_MUSIC) {
                                masterVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                            }
                        }
                    }
                }
                registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
                onDispose { unregisterReceiver(receiver) }
            }

            // Glass gradient border
            val glassBorderBrush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.White.copy(alpha = 0.05f),
                    Color.White.copy(alpha = 0.01f),
                    Color.White.copy(alpha = 0.15f)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha * 0.25f)) // Just a subtle darkening since the Window is blurred heavily
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        isVisible = false
                        window.decorView.postDelayed({ finish() }, 250)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .scale(scale)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color.White.copy(alpha = 0.04f)) // Extremely translucent frost base
                        .border(1.dp, glassBorderBrush, RoundedCornerShape(32.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}, // Absorb touches
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp)
                    ) {
                        // Header
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(36.dp).background(dynamicPrimary.copy(alpha = 0.2f), CircleShape).border(1.dp, dynamicPrimary.copy(alpha=0.4f), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = dynamicPrimary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("AUDIO COMMAND", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        }
                        
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        // Master Volume
                        Text("MASTER MEDIA", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.03f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha=0.8f), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Slider(
                                value = masterVolume,
                                onValueChange = { 
                                    masterVolume = it 
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                                },
                                valueRange = 0f..maxVolume,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = dynamicPrimary, inactiveTrackColor = Color.White.copy(alpha=0.15f))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${((masterVolume / maxVolume) * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        // App Sovereignty Area
                        Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (activeAppsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                Text("No Active Sessions", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.name, app.icon, 0.8f, isPinned = false, dynamicPrimary)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))

                        // Bottom Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DockActionButton(Modifier.weight(1f), "MUTE ALL", Icons.AutoMirrored.Filled.VolumeOff, Color(0xFFEF4444)) {}
                            DockActionButton(Modifier.weight(1f), "DND", Icons.Default.DoNotDisturbOn, dynamicSecondary) {}
                            DockActionButton(Modifier.weight(1f), "AutoEQ", Icons.Default.Tune, Color(0xFF10B981)) {}
                        }
                    }
                }
            }
        }
    }
}

data class ActiveAppInfo(val pkg: String, val name: String, val icon: Drawable?)

@Composable
fun AppVolumeRow(name: String, iconDrawable: Drawable?, initialVol: Float, isPinned: Boolean, tint: Color) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.03f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
        Box(modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (iconDrawable != null) {
                val bitmap = remember(iconDrawable) {
                    if (iconDrawable is android.graphics.drawable.BitmapDrawable && iconDrawable.bitmap != null) {
                        iconDrawable.bitmap
                    } else {
                        val bmp = android.graphics.Bitmap.createBitmap(
                            if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 1,
                            if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 1,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                        iconDrawable.draw(canvas)
                        bmp
                    }
                }
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(Icons.Default.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines=1)
            Slider(
                value = vol,
                onValueChange = { vol = it },
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.8f), inactiveTrackColor = Color.White.copy(alpha=0.15f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = { pinned = !pinned },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DockActionButton(modifier: Modifier, title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}
