package com.sbf.lightspeed.system
import androidx.compose.foundation.verticalScroll

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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.AccessAlarm
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.RuntimeShader
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

    

    private fun triggerHaptic(context: Context) {
        LightspeedHapticEngine.vibrate(context, 18, 120)
    }

    private fun getPinnedApps(context: Context): Set<String> {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        return prefs.getStringSet("pinned_audio_apps", emptySet()) ?: emptySet()
    }

    private fun addPinnedApp(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pinned_audio_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(pkg)
        prefs.edit().putStringSet("pinned_audio_apps", current).apply()
    }
    
    private fun removePinnedApp(context: Context, pkg: String) {
        val prefs = context.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        val current = prefs.getStringSet("pinned_audio_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
        current.remove(pkg)
        prefs.edit().putStringSet("pinned_audio_apps", current).apply()
    }

    private fun getRawActiveAudioPackages(context: Context): List<String> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val pm = context.packageManager
        val configs = audioManager.activePlaybackConfigurations
        val pkgs = mutableListOf<String>()
        
        for (config in configs) {
            try {
                val getClientUidMethod = config.javaClass.getMethod("getClientUid")
                val uid = getClientUidMethod.invoke(config) as Int
                if (uid > 10000) { // Ignore system UIDs
                    val packages = pm.getPackagesForUid(uid)
                    if (packages != null) {
                        pkgs.addAll(packages)
                    }
                }
            } catch (e: Exception) {
                // Ignore
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
        var showAppSelector by remember { mutableStateOf(false) }

        var refreshTrigger by remember { mutableIntStateOf(0) }
        
        LaunchedEffect(refreshTrigger) {
            val activeControllers = LightspeedMediaManager.getActiveControllers(service)
            val sessionPackages = activeControllers.map { it.packageName }
            val rawPackages = getRawActiveAudioPackages(service)
            val pinnedPackages = getPinnedApps(service)
            
            val mergedPackages = (sessionPackages + rawPackages + pinnedPackages).distinct()
            
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
            // Sort so pinned apps appear first
            activeAppsList = apps.sortedByDescending { it.pkg in pinnedPackages }
        }

        val prefs = service.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        var glassStyleIndex by remember { mutableIntStateOf(prefs.getInt("pref_glass_style", 0)) }
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        
        val dynamicPrimary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(service).primary else Color(0xFF6366F1)
        val dynamicSecondary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) androidx.compose.material3.dynamicDarkColorScheme(service).secondary else Color(0xFFEAB308)

        val bgColor = Color(0xB3121212) // 70% opacity deep dark grey for the dock itself
        

        
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
                        .fillMaxWidth(0.9f).heightIn(max = 750.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                        .clip(RoundedCornerShape(36.dp))
                        
                        .background(bgColor)
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
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(24.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OMNISCIENT AUDIO",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )

                        }
                        
                        Spacer(modifier = Modifier.height(36.dp))
                        
                        Text("MASTER MEDIA", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.08f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
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

                        Text("SYSTEM STREAMS", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SystemVolumeRow("RINGER & NOTIFICATIONS", Icons.Default.Notifications, AudioManager.STREAM_RING, audioManager, Color(0xFF3B82F6))
                            SystemVolumeRow("ALARMS", Icons.Default.AccessAlarm, AudioManager.STREAM_ALARM, audioManager, Color(0xFFF59E0B))
                            SystemVolumeRow("VOICE CALLS", Icons.Default.Phone, AudioManager.STREAM_VOICE_CALL, audioManager, Color(0xFF10B981))
                            SystemVolumeRow("ASSISTANT (GEMINI)", Icons.Default.Android, 11, audioManager, Color(0xFF8B5CF6))
                            SystemVolumeRow("ACCESSIBILITY", Icons.Default.Accessibility, 10, audioManager, Color(0xFFEC4899))
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("APP SOVEREIGNTY", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            Text("+ ADD APP", color = dynamicPrimary, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, modifier = Modifier.clickable {
                                showAppSelector = true
                            }.padding(4.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (activeAppsList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                                Text("No Active Sessions", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                val pinnedSet = remember(refreshTrigger) { getPinnedApps(service) }
                                activeAppsList.forEach { app ->
                                    AppVolumeRow(app.pkg, app.name, app.icon, 0.8f, app.pkg in pinnedSet, dynamicPrimary) { isPinned ->
                                        if (isPinned) addPinnedApp(service, app.pkg) else removePinnedApp(service, app.pkg)
                                        refreshTrigger++
                                    }
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
            
            AnimatedVisibility(visible = showAppSelector, enter = fadeIn(), exit = fadeOut()) {
                AppSelectorOverlay(pm = pm, onAppSelected = { pkg ->
                    addPinnedApp(service, pkg)
                    refreshTrigger++
                    showAppSelector = false
                }, onDismiss = { showAppSelector = false })
            }
        }
    }
}

@Composable
fun SystemVolumeRow(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, streamType: Int, audioManager: AudioManager, tint: Color) {
    val maxVol = remember { audioManager.getStreamMaxVolume(streamType).toFloat().coerceAtLeast(1f) }
    var vol by remember { mutableFloatStateOf(audioManager.getStreamVolume(streamType).toFloat()) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=0.08f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Slider(
                value = vol,
                onValueChange = { 
                    vol = it 
                    audioManager.setStreamVolume(streamType, it.roundToInt(), 0)
                },
                valueRange = 0f..maxVol,
                modifier = Modifier.height(20.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = tint, inactiveTrackColor = Color.White.copy(alpha=0.1f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("${((vol / maxVol) * 100).toInt()}%", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}


    @Composable
    private fun AppSelectorOverlay(pm: PackageManager, onAppSelected: (String) -> Unit, onDismiss: () -> Unit) {
        var searchQuery by remember { mutableStateOf("") }
        var installedApps by remember { mutableStateOf<List<ActiveAppInfo>>(emptyList()) }

        LaunchedEffect(Unit) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val apps = packages.filter { pm.getLaunchIntentForPackage(it.packageName) != null }.map {
                    ActiveAppInfo(it.packageName, pm.getApplicationLabel(it).toString(), pm.getApplicationIcon(it))
                }.sortedBy { it.name.lowercase() }
                installedApps = apps
            }
        }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.6f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.7f).clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E1E1E)).padding(16.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null){}) {
                Text("ADD PINNED APP", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.foundation.lazy.LazyColumn {
                    items(installedApps.size) { i ->
                        val app = installedApps[i]
                        Row(modifier = Modifier.fillMaxWidth().clickable { onAppSelected(app.pkg) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val bmp = remember(app.icon) {
                                val d = app.icon!!
                                if (d is android.graphics.drawable.BitmapDrawable && d.bitmap != null) d.bitmap else {
                                    val b = android.graphics.Bitmap.createBitmap(if (d.intrinsicWidth>0) d.intrinsicWidth else 1, if (d.intrinsicHeight>0) d.intrinsicHeight else 1, android.graphics.Bitmap.Config.ARGB_8888)
                                    val c = android.graphics.Canvas(b)
                                    d.setBounds(0,0,c.width,c.height)
                                    d.draw(c)
                                    b
                                }
                            }
                            androidx.compose.foundation.Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(app.name, color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

@Composable
fun AppVolumeRow(pkg: String, name: String, iconDrawable: android.graphics.drawable.Drawable?, initialVol: Float, isPinned: Boolean, tint: Color, onPinToggled: (Boolean) -> Unit) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.08f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
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
                Icon(androidx.compose.material.icons.Icons.Default.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
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
            onClick = { 
                pinned = !pinned 
                onPinToggled(pinned)
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DockActionButton(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
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

data class ActiveAppInfo(val pkg: String, val name: String, val icon: android.graphics.drawable.Drawable?)
