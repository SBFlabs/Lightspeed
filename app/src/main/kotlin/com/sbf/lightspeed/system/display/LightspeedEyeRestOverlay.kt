package com.sbf.lightspeed.system.display

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.OverlayLifecycleOwner
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme

object LightspeedEyeRestOverlay {

    private const val CHANNEL_ID = "lightspeed_wellness_channel"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun showFullscreen(context: Context, onDismiss: () -> Unit, onSnooze: () -> Unit) {
        mainHandler.post {
            dismiss()
            val service = (context as? LightspeedAccessibilityService)
                ?: LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val lifecycle = OverlayLifecycleOwner().apply { onCreate() }
            lifecycleOwner = lifecycle

            val view = ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LightspeedTheme {
                        EyeRestFullscreenContent(
                            context = service,
                            onDismiss = { dismiss(); onDismiss() },
                            onSnooze = { dismiss(); onSnooze() }
                        )
                    }
                }
            }

            lifecycle.attachToView(view)
            lifecycle.onStart()
            lifecycle.onResume()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setFitInsetsTypes(0)
                    setFitInsetsSides(0)
                    setFitInsetsIgnoringVisibility(true)
                }
            }

            try {
                wm.addView(view, params)
                composeView = view
            } catch (_: Exception) {}
        }
    }

    fun showFloating(context: Context, onDismiss: () -> Unit) {
        mainHandler.post {
            dismiss()
            val service = (context as? LightspeedAccessibilityService)
                ?: LightspeedAccessibilityService.instance ?: return@post
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val lifecycle = OverlayLifecycleOwner().apply { onCreate() }
            lifecycleOwner = lifecycle

            val view = ComposeView(service).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LightspeedTheme {
                        EyeRestFloatingContent(
                            context = service,
                            onDismiss = { dismiss(); onDismiss() }
                        )
                    }
                }
            }

            lifecycle.attachToView(view)
            lifecycle.onStart()
            lifecycle.onResume()

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (48 * service.resources.displayMetrics.density).toInt()
            }

            try {
                wm.addView(view, params)
                composeView = view
            } catch (_: Exception) {}
        }
    }

    fun dispatchNotification(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Eye Rest & Display Wellness", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "20-20-20 Eye Care Wellness Reminders"
                }
                nm.createNotificationChannel(channel)
            }

            val title = context.defaultPrefs().getString(LightspeedPreferences.KEY_EYE_REST_TITLE, "20-20-20 EYE CARE") ?: "20-20-20 EYE CARE"
            val sub = context.defaultPrefs().getString(LightspeedPreferences.KEY_EYE_REST_SUBTITLE, "Look 20 feet away for 20 seconds") ?: "Look 20 feet away for 20 seconds"

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(sub)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            nm.notify(202020, notification)
        } catch (_: Exception) {}
    }

    fun dismiss() {
        mainHandler.post {
            val v = composeView ?: return@post
            try { windowManager?.removeView(v) } catch (_: Exception) {}
            composeView = null
            lifecycleOwner?.onDestroy()
            lifecycleOwner = null
        }
    }

    @Composable
    private fun EyeRestFullscreenContent(context: Context, onDismiss: () -> Unit, onSnooze: () -> Unit) {
        val lsColors = LightspeedStyle.colors
        val prefs = remember { context.defaultPrefs() }
        val title = remember { prefs.getString(LightspeedPreferences.KEY_EYE_REST_TITLE, "20-20-20 EYE CARE") ?: "20-20-20 EYE CARE" }
        val subtitle = remember { prefs.getString(LightspeedPreferences.KEY_EYE_REST_SUBTITLE, "Look 20 feet away for 20 seconds") ?: "Look 20 feet away for 20 seconds" }
        var secondsLeft by remember { mutableIntStateOf(20) }

        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                secondsLeft -= 1
            }
        }

        val themeSnapshot = remember { DeckThemeManager.getSnapshot(context) }
        val tint = Color(themeSnapshot.tintArgb.toInt())

        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xF00A0D14)).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xEE141824))
                    .border(1.5.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, tint = tint, modifier = Modifier.size(36.dp))
                Text(title, color = lsColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 2.sp)
                Text(subtitle, color = lsColors.textSecondary, fontSize = 11.5.sp)

                Box(
                    modifier = Modifier.size(110.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)).border(2.dp, tint.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (secondsLeft > 0) "$secondsLeft" else "✓", color = tint, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onSnooze, colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FFA726)), modifier = Modifier.weight(1f)) {
                        Text("Snooze 5m", color = Color(0xFFFFA726), fontSize = 11.5.sp)
                    }
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = tint), modifier = Modifier.weight(1f)) {
                        Text("Dismiss", color = Color.Black, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    @Composable
    private fun EyeRestFloatingContent(context: Context, onDismiss: () -> Unit) {
        val lsColors = LightspeedStyle.colors
        val prefs = remember { context.defaultPrefs() }
        val title = remember { prefs.getString(LightspeedPreferences.KEY_EYE_REST_TITLE, "20-20-20 EYE CARE") ?: "20-20-20 EYE CARE" }
        var secondsLeft by remember { mutableIntStateOf(20) }

        LaunchedEffect(Unit) {
            while (secondsLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                secondsLeft -= 1
            }
            onDismiss()
        }

        val themeSnapshot = remember { DeckThemeManager.getSnapshot(context) }
        val tint = Color(themeSnapshot.tintArgb.toInt())

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF0141824))
                    .border(1.5.dp, tint.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Visibility, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, color = lsColors.textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Text("Look 20ft away ($secondsLeft s)", color = lsColors.textSecondary, fontSize = 9.5.sp)
                    }
                }
                Text("Dismiss", color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
