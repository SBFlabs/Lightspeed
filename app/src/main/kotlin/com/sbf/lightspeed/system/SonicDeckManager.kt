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
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.sbf.lightspeed.settings.BlueprintWireframeView
import com.sbf.lightspeed.settings.CollapsibleSubSection
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

    private var windowParams: WindowManager.LayoutParams? = null

    fun isShowing(): Boolean = composeView != null

    fun updateWindowFocusable(focusable: Boolean) {
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post {
            val view = composeView ?: return@post
            val wm = windowManager ?: return@post
            val params = windowParams ?: return@post
            if (focusable) {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            } else {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                logSwallowed("SonicDeckManager", "updateWindowFocusable", e)
            }
        }
    }

    /**
     * Master switch toggle handler.
     * When disabled: stops voice isolation, resets per-app volume overrides
     * to native unity (1.0f), and clears privileged audio routes.
     * When re-enabled: restores voice isolation if previously active, re-enables volume
     * monitoring, reapplies custom app volumes, and restores channel routing.
     */
    fun onMasterToggle(context: Context, enabled: Boolean) {
        val appCtx = context.applicationContext ?: context
        val prefs = appCtx.defaultPrefs()
        if (!enabled) {
            dismiss()

            // 1. Stash and stop Voice Isolation
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
            // 1. Restore Voice Isolation if previously stashed active
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

            windowParams = params

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
                windowParams = null
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
        val prefs = service.defaultPrefs()
        val audioManager = remember { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        var isBlueprintMode by remember { mutableStateOf(false) }
        val tint = Color(snapshot.effectiveTintArgb.toInt())

        val defaultSections = listOf("mixer", "dialogue", "streams")
        val sectionTitles = mapOf(
            "mixer" to "Per-App Audio Mixer & Routing",
            "dialogue" to "Voice Isolation & Background Muffler",
            "streams" to "Master & System Streams"
        )
        val savedOrderStr = prefs.getString("pref_sonic_deck_section_order", "mixer,dialogue,streams") ?: "mixer,dialogue,streams"
        var currentSectionOrder by remember(savedOrderStr) {
            mutableStateOf(
                savedOrderStr.split(",").filter { it in defaultSections }.let { list ->
                    list + (defaultSections - list.toSet())
                }
            )
        }
        var pinnedSection by remember {
            mutableStateOf(prefs.getString("pref_sonic_deck_pinned_section", "mixer") ?: "mixer")
        }

        var isMixerExpanded by remember { mutableStateOf(pinnedSection == "mixer" || prefs.getBoolean("pref_sonic_sub_mixer_expanded", true)) }
        var isDialogueExpanded by remember { mutableStateOf(pinnedSection == "dialogue" || prefs.getBoolean("pref_sonic_sub_dialogue_expanded", true)) }
        var isStreamsExpanded by remember { mutableStateOf(pinnedSection == "streams" || prefs.getBoolean("pref_sonic_sub_streams_expanded", true)) }

        LaunchedEffect(isBlueprintMode) {
            updateWindowFocusable(isBlueprintMode)
            if (isBlueprintMode) {
                composeView?.isFocusableInTouchMode = true
                composeView?.requestFocus()
                composeView?.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && isBlueprintMode) {
                        isBlueprintMode = false
                        true
                    } else false
                }
            } else {
                composeView?.setOnKeyListener(null)
            }
        }

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
                        .widthIn(min = 320.dp, max = 480.dp)
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .heightIn(max = 680.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                    snapshotOverride = snapshot,
                    isFlyout = false
                ) {
                    if (isBlueprintMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            BlueprintWireframeView(
                                tabTitle = "SONIC DECK",
                                sectionIds = currentSectionOrder,
                                pinnedSectionId = pinnedSection,
                                sectionTitles = sectionTitles,
                                onMoveUp = { idx ->
                                    if (idx > 0) {
                                        val m = currentSectionOrder.toMutableList()
                                        val item = m.removeAt(idx)
                                        m.add(idx - 1, item)
                                        currentSectionOrder = m
                                        prefs.edit().putString("pref_sonic_deck_section_order", m.joinToString(",")).apply()
                                    }
                                },
                                onMoveDown = { idx ->
                                    if (idx < currentSectionOrder.size - 1) {
                                        val m = currentSectionOrder.toMutableList()
                                        val item = m.removeAt(idx)
                                        m.add(idx + 1, item)
                                        currentSectionOrder = m
                                        prefs.edit().putString("pref_sonic_deck_section_order", m.joinToString(",")).apply()
                                    }
                                },
                                onPinSection = { id ->
                                    pinnedSection = id
                                    prefs.edit().putString("pref_sonic_deck_pinned_section", id).apply()
                                    when (id) {
                                        "mixer" -> { isMixerExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_mixer_expanded", true).apply() }
                                        "dialogue" -> { isDialogueExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_dialogue_expanded", true).apply() }
                                        "streams" -> { isStreamsExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_streams_expanded", true).apply() }
                                    }
                                },
                                onExitBlueprint = { isBlueprintMode = false }
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Header
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { isBlueprintMode = true }
                                        .padding(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VolumeUp,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "◈ SONIC DECK ◈",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Edit Blueprint",
                                        tint = tint.copy(alpha = 0.85f),
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.8f))
                                }
                            }

                            HorizontalDivider(color = Color(0x25FFFFFF))

                            currentSectionOrder.forEach { secId ->
                                when (secId) {
                                    "mixer" -> {
                                        CollapsibleSubSection(
                                            title = "Per-App Audio Mixer & Routing",
                                            subtitle = "Hardware gain (0..200%) & multi-channel routing",
                                            isExpanded = isMixerExpanded,
                                            onToggle = {
                                                isMixerExpanded = !isMixerExpanded
                                                prefs.edit().putBoolean("pref_sonic_sub_mixer_expanded", isMixerExpanded).apply()
                                            }
                                        ) {
                                            SonicAppMixerSection(context = service, snapshot = snapshot)
                                        }
                                    }
                                    "dialogue" -> {
                                        CollapsibleSubSection(
                                            title = "Voice Isolation & Background Muffler",
                                            subtitle = "Real-time speech clarity & background sound muffle",
                                            isExpanded = isDialogueExpanded,
                                            onToggle = {
                                                isDialogueExpanded = !isDialogueExpanded
                                                prefs.edit().putBoolean("pref_sonic_sub_dialogue_expanded", isDialogueExpanded).apply()
                                            }
                                        ) {
                                            SonicDialogueSection(context = service, snapshot = snapshot)
                                        }
                                    }
                                    "streams" -> {
                                        CollapsibleSubSection(
                                            title = "Master & System Streams",
                                            subtitle = "Media, Ring, Notification, Alarm, Voice Call & System",
                                            isExpanded = isStreamsExpanded,
                                            onToggle = {
                                                isStreamsExpanded = !isStreamsExpanded
                                                prefs.edit().putBoolean("pref_sonic_sub_streams_expanded", isStreamsExpanded).apply()
                                            }
                                        ) {
                                            SonicMasterStreamsSection(context = service, audioManager = audioManager, snapshot = snapshot)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
