package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@SuppressLint("StaticFieldLeak")
object OmniscientAudioDockManager {
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var volumeReceiver: BroadcastReceiver? = null

    // State flows
    private val masterVolumeState = mutableFloatStateOf(0f)

    fun show(service: AccessibilityService) {
        if (composeView != null) return
        
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        composeView = ComposeView(service).apply {
            setContent {
                var isVisible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    isVisible = true
                }
                
                OmniscientAudioDockContent(
                    service = service,
                    isVisible = isVisible,
                    onDismiss = {
                        isVisible = false
                        postDelayed({ dismiss(service) }, 250)
                    }
                )
            }
        }

        lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner?.attachToView(composeView!!)
        lifecycleOwner?.onCreate()
        lifecycleOwner?.onStart()
        lifecycleOwner?.onResume()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 120
            }
        }

        windowManager?.addView(composeView, params)

        // Register Volume Receiver
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        masterVolumeState.floatValue = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        
        volumeReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == "android.media.VOLUME_CHANGED_ACTION") {
                    val streamType = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        val currentVol = intent.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
                        if (currentVol != -1) {
                            masterVolumeState.floatValue = currentVol.toFloat()
                        }
                    }
                }
            }
        }
        service.registerReceiver(volumeReceiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
    }

    fun dismiss(service: Context) {
        volumeReceiver?.let {
            try { service.unregisterReceiver(it) } catch (e: Exception) {}
            volumeReceiver = null
        }
        composeView?.let {
            lifecycleOwner?.onPause()
            lifecycleOwner?.onStop()
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
            windowManager?.removeView(it)
            composeView = null
        }
        windowManager = null
    }

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

    @Composable
    private fun OmniscientAudioDockContent(service: Context, isVisible: Boolean, onDismiss: () -> Unit) {
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = service.packageManager
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat() }

        var activeAppsList by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }

        LaunchedEffect(Unit) {
            val activeControllers = LightspeedMediaManager.getActiveControllers(service)
            val sessionPackages = activeControllers.map { it.packageName }
            val rawPackages = getRawActiveAudioPackages(service)
            
            val mergedPackages = (sessionPackages + rawPackages).distinct()
            
            val apps = mergedPackages.mapNotNull { pkg ->
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)
                    ActiveAppInfo(pkg, label, icon)
                } catch (e: Exception) {
                    null
                }
            }
            activeAppsList = apps
        }

        val dynamicPrimary = Color(0xFF6366F1)
        val dynamicSecondary = Color(0xFFEAB308)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(250)) + scaleIn(tween(250, delayMillis = 50), initialScale = 0.9f),
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.9f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .clip(RoundedCornerShape(36.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                0.0f to Color.White.copy(alpha = 0.4f),
                                0.2f to Color.White.copy(alpha = 0.05f),
                                0.8f to Color.White.copy(alpha = 0.05f),
                                1.0f to Color.White.copy(alpha = 0.2f)
                            ),
                            RoundedCornerShape(36.dp)
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            text = "OMNISCIENT AUDIO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )
                        
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        Text("MASTER MEDIA", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.03f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
                            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White.copy(alpha=0.8f), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Slider(
                                value = masterVolumeState.floatValue,
                                onValueChange = { 
                                    masterVolumeState.floatValue = it 
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it.roundToInt(), 0)
                                },
                                valueRange = 0f..maxVolume,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = dynamicPrimary, inactiveTrackColor = Color.White.copy(alpha=0.15f))
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${((masterVolumeState.floatValue / maxVolume) * 100).toInt()}%", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(32.dp))

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
