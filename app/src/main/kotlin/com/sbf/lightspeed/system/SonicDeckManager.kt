package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import java.lang.ref.WeakReference

/**
 * 12-Theme Optical Glass Acoustic Command Deck Modal.
 *
 * Provides a unified cockpit for:
 * 1. Real-time Neural Voice Isolation & Film Dialogue Enhancement.
 * 2. Master System Audio Streams & One-Tap Silence / DND controls.
 * 3. Per-App Hardware Volume (0%..200%), Multi-Channel Device Routing & Always-On Audio Focus.
 */
object SonicDeckManager {

    const val KEY_SONIC_VOICE_ISOLATION_STASHED = "pref_sonic_voice_isolation_stashed"

    private var composeView: ComposeView? = null
    private var windowManager: WindowManager? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    fun isShowing(): Boolean = composeView != null

    /**
     * Master switch toggle handler.
     * When disabled: stops Cinema AI voice isolation, resets per-app volume overrides
     * to native unity (1.0f), and clears privileged audio routes.
     * When re-enabled: restores voice isolation if previously active, re-enables volume
     * monitoring, reapplies custom app volumes, and restores channel routing.
     */
    fun onMasterToggle(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        if (!enabled) {
            dismiss()

            // 1. Stash and stop Cinema AI Voice Isolation
            val wasVoiceActive = SonicVoiceIsolationEngine.isEnabled ||
                prefs.getBoolean(LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED, false)
            prefs.edit().putBoolean(KEY_SONIC_VOICE_ISOLATION_STASHED, wasVoiceActive).apply()
            SonicVoiceIsolationEngine.stop(appCtx)

            // 2. Reset per-app volume overrides to native unity (1.0f) and stop active player interception
            SonicAppVolumeEngine.stopMonitoring()

            // 3. Reset privileged audio routing
            SonicChannelRouter.teardown(appCtx)

            // 4. Shut down the privileged service process itself so it stops holding RAM
            SonicVoiceIsolationEngine.teardown(appCtx)
        } else {
            SonicVoiceIsolationEngine.ensureBound(appCtx)
            // Re-enabling restores all back:
            // 1. Restore Cinema AI Voice Isolation if previously stashed active
            val shouldRestoreVoice = prefs.getBoolean(KEY_SONIC_VOICE_ISOLATION_STASHED, false)
            if (shouldRestoreVoice) {
                SonicVoiceIsolationEngine.start(appCtx)
            }

            // 2. Restore per-app volume monitoring and reapply custom volumes
            SonicAppVolumeEngine.startMonitoring(appCtx)
            SonicAppVolumeEngine.reapplyAllVolumesToUserService(appCtx)
            SonicAppVolumeEngine.refreshActiveAudioPlayers(appCtx)

            // 3. Restore privileged audio routing
            SonicChannelRouter.reapplyPrivilegedRoutes(appCtx)
        }
    }

    fun show(service: AccessibilityService) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            if (composeView != null) return@post

            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
            windowManager = wm

            val view = ComposeView(service)
            val owner = OverlayLifecycleOwner()
            owner.attachToView(view)
            owner.onCreate()
            owner.onStart()
            owner.onResume()
            lifecycleOwner = owner

            view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            view.setContent {
                LightspeedTheme(forceDark = true) {
                    DeckTheme {
                        SonicDeckDialog(
                            service = service,
                            isVisible = true,
                            onDismiss = { dismiss() }
                        )
                    }
                }
            }
            composeView = view

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                dimAmount = 0f
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
            } catch (e: Exception) {
                logSwallowed("SonicDeckManager", "show.addView", e)
                dismiss()
            }
        }
    }

    fun dismiss(context: Context? = null) {
        dismiss()
    }

    fun dismiss() {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            composeView?.let { view ->
                try {
                    lifecycleOwner?.onPause()
                    lifecycleOwner?.onStop()
                    lifecycleOwner?.onDestroy()
                    lifecycleOwner = null
                    view.setContent {}
                    windowManager?.removeView(view)
                } catch (e: Exception) {
                    logSwallowed("SonicDeckManager", "dismiss.removeView", e)
                }
                composeView = null
            }
            windowManager = null
        }
    }

    @Composable
    private fun SonicDeckDialog(
        service: AccessibilityService,
        isVisible: Boolean,
        onDismiss: () -> Unit
    ) {
        val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
        val audioManager = remember { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(tween(200)) + scaleIn(tween(250), initialScale = 0.94f),
                exit = fadeOut(tween(150)) + scaleOut(tween(200), targetScale = 0.94f)
            ) {
                DeckGlassCardSurface(
                    modifier = Modifier
                        .widthIn(max = 380.dp)
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .heightIn(max = 540.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    snapshotOverride = snapshot,
                    isFlyout = false
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "◈ SONIC COMMAND DECK ◈",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        HorizontalDivider(color = Color(0x25FFFFFF))

                        // Section 1: Dialogue & Neural Voice Isolation
                        SonicDialogueSection(context = service, snapshot = snapshot)

                        HorizontalDivider(color = Color(0x25FFFFFF))

                        // Section 2: Master & System Streams
                        SonicMasterStreamsSection(context = service, audioManager = audioManager, snapshot = snapshot)

                        HorizontalDivider(color = Color(0x25FFFFFF))

                        // Section 3: Per-App Mixer & Output Channel Sovereignty
                        SonicAppMixerSection(context = service, snapshot = snapshot)
                    }
                }
            }
        }
    }
}
