package com.sbf.lightspeed.system

import android.accessibilityservice.AccessibilityService
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.BlueprintWireframeView
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
import com.sbf.lightspeed.settings.CollapsibleSubSection
import com.sbf.lightspeed.settings.ConsciousAirlockAppPickerContent
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.settings.blockScrimPassthrough
import com.sbf.lightspeed.settings.overlayScrimDismiss
import com.sbf.lightspeed.settings.verticalScrollWithEdgeFade
import com.sbf.lightspeed.system.display.LightspeedTelemetryHudOverlay
import com.sbf.lightspeed.system.hud.VistaBrightnessSection
import com.sbf.lightspeed.system.hud.VistaOpticsSection
import com.sbf.lightspeed.system.hud.VistaTelemetryHudSection
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine

@Composable
internal fun UnifiedSystemDeckDialog(
    service: AccessibilityService,
    mode: SystemDeckMode,
    fromContinuum: Boolean,
    onSelectMode: (SystemDeckMode) -> Unit,
    onDismiss: () -> Unit
) {
    val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
    val prefs = service.defaultPrefs()
    val tint = Color(snapshot.effectiveTintArgb.toInt())
    val density = LocalDensity.current.density

    var showMediaFilterPicker by remember { mutableStateOf(false) }
    var showTelemetryAppPicker by remember { mutableStateOf(false) }
    var isSonicSaveDialogOpen by remember { mutableStateOf(false) }
    var isBlueprintMode by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        if (mode != SystemDeckMode.VISTA) {
            showMediaFilterPicker = false
            showTelemetryAppPicker = false
        }
        if (mode != SystemDeckMode.SONIC) {
            isSonicSaveDialogOpen = false
        }
    }

    LaunchedEffect(showMediaFilterPicker, showTelemetryAppPicker, isBlueprintMode, isSonicSaveDialogOpen) {
        val focusable = showMediaFilterPicker || showTelemetryAppPicker || isBlueprintMode || isSonicSaveDialogOpen
        UnifiedSystemDeckHost.setBackHandler {
            when {
                showMediaFilterPicker -> { showMediaFilterPicker = false; true }
                showTelemetryAppPicker -> { showTelemetryAppPicker = false; true }
                isSonicSaveDialogOpen -> { isSonicSaveDialogOpen = false; true }
                isBlueprintMode -> { isBlueprintMode = false; true }
                else -> false
            }
        }
        UnifiedSystemDeckHost.updateWindowFocusable(focusable)
    }

    val isCapturing = CentralCommandBackdropBlur.isCapturing
    var hasEntered by remember { mutableStateOf(!isCapturing) }
    LaunchedEffect(isCapturing) {
        if (!isCapturing) hasEntered = true
    }

    val showContent = hasEntered || !isCapturing
    val morphProgress = remember { Animatable(if (fromContinuum) 0f else 0f) }
    LaunchedEffect(showContent, fromContinuum) {
        if (showContent) {
            morphProgress.snapTo(if (fromContinuum) 0.15f else 0f)
            morphProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = if (fromContinuum) 0.78f else 0.85f,
                    stiffness = if (fromContinuum) 540f else 480f
                )
            )
        }
    }

    val t = morphProgress.value.coerceIn(0f, 1f)
    val scrimAlpha = if (!showContent) 0f else (snapshot.dimAmount * 0.85f * t).coerceIn(0.25f, 0.65f)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .overlayScrimDismiss(
                    context = service,
                    onTapOutside = onDismiss
                )
        )

        if (showContent) {
            DeckGlassCardSurface(
                modifier = Modifier
                    .widthIn(min = 320.dp, max = 480.dp)
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .heightIn(max = 680.dp)
                    .graphicsLayer {
                        val s = if (fromContinuum) (0.93f + 0.07f * t) else (0.95f + 0.05f * t)
                        scaleX = s
                        scaleY = s
                        alpha = (0.30f + 0.70f * t).coerceIn(0f, 1f)
                        translationY = if (fromContinuum) (-22f * density * (1f - t)) else (10f * density * (1f - t))
                    }
                    .blockScrimPassthrough(),
                snapshotOverride = snapshot,
                isFlyout = false,
                useLightweightHudBlur = true
            ) {
                when {
                    showMediaFilterPicker -> {
                        ConsciousAirlockAppPickerContent(
                            context = service,
                            prefs = prefs,
                            onDismissRequest = { showMediaFilterPicker = false },
                            prefKey = LightspeedPreferences.KEY_VIDEO_OVERRIDE_PACKAGES,
                            title = lsString(R.string.vdeck_media_picker_title),
                            subtitleTemplate = { count ->
                                if (count == 0) lsString(R.string.vdeck_media_picker_sub_all)
                                else lsString(R.string.vdeck_media_picker_sub_fmt, count)
                            },
                            loaderStyle = com.sbf.lightspeed.settings.AppPickerLoaderStyle.SOLAR_BRIGHTNESS_TRIGGER
                        )
                    }
                    showTelemetryAppPicker -> {
                        ConsciousAirlockAppPickerContent(
                            context = service,
                            prefs = prefs,
                            onDismissRequest = {
                                showTelemetryAppPicker = false
                                LightspeedTelemetryHudOverlay.refreshState(service)
                            },
                            prefKey = LightspeedPreferences.KEY_TELEMETRY_HUD_PACKAGES,
                            title = lsString(R.string.thud_picker_title),
                            subtitleTemplate = { count ->
                                if (count == 0) lsString(R.string.thud_picker_sub_empty)
                                else lsString(R.string.thud_picker_sub_fmt, count)
                            },
                            prioritizeGames = true,
                            loaderStyle = com.sbf.lightspeed.settings.AppPickerLoaderStyle.TELEMETRY_WAVEFORM,
                            onSelectionChanged = {
                                LightspeedTelemetryHudOverlay.refreshState(service)
                            }
                        )
                    }
                    else -> {
                        UnifiedSystemDeckBody(
                            service = service,
                            prefs = prefs,
                            snapshot = snapshot,
                            tint = tint,
                            mode = mode,
                            isBlueprintMode = isBlueprintMode,
                            onToggleBlueprint = { isBlueprintMode = !isBlueprintMode },
                            onExitBlueprint = { isBlueprintMode = false },
                            onSelectMode = onSelectMode,
                            isSonicSaveDialogOpen = isSonicSaveDialogOpen,
                            onSonicSaveDialogChange = { isSonicSaveDialogOpen = it },
                            onOpenMediaPicker = { showMediaFilterPicker = true },
                            onOpenTelemetryPicker = { showTelemetryAppPicker = true },
                            onDismiss = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UnifiedSystemDeckBody(
    service: AccessibilityService,
    prefs: SharedPreferences,
    snapshot: DeckThemeSnapshot,
    tint: Color,
    mode: SystemDeckMode,
    isBlueprintMode: Boolean,
    onToggleBlueprint: () -> Unit,
    onExitBlueprint: () -> Unit,
    onSelectMode: (SystemDeckMode) -> Unit,
    isSonicSaveDialogOpen: Boolean,
    onSonicSaveDialogChange: (Boolean) -> Unit,
    onOpenMediaPicker: () -> Unit,
    onOpenTelemetryPicker: () -> Unit,
    onDismiss: () -> Unit
) {
    val sonicSections = listOf("mixer", "dialogue", "streams")
    val sonicTitles = mapOf(
        "mixer" to lsString(R.string.sec_per_app_audio_mixer_routing),
        "dialogue" to lsString(R.string.sec_voice_isolation_background_muf),
        "streams" to lsString(R.string.sec_master_system_streams)
    )
    val savedSonicOrder = prefs.getString("pref_sonic_deck_section_order", "mixer,dialogue,streams") ?: "mixer,dialogue,streams"
    var sonicOrder by remember(savedSonicOrder) {
        mutableStateOf(savedSonicOrder.split(",").filter { it in sonicSections }.let { it + (sonicSections - it.toSet()) })
    }
    var sonicPinned by remember {
        mutableStateOf(prefs.getString("pref_sonic_deck_pinned_section", "mixer") ?: "mixer")
    }
    var isMixerExpanded by remember { mutableStateOf(sonicPinned == "mixer" || prefs.getBoolean("pref_sonic_sub_mixer_expanded", true)) }
    var isDialogueExpanded by remember { mutableStateOf(sonicPinned == "dialogue" || prefs.getBoolean("pref_sonic_sub_dialogue_expanded", true)) }
    var isStreamsExpanded by remember { mutableStateOf(sonicPinned == "streams" || prefs.getBoolean("pref_sonic_sub_streams_expanded", true)) }

    val vistaSections = listOf("brightness", "optics", "telemetry")
    val vistaTitles = mapOf(
        "brightness" to lsString(R.string.sec_brightness_and_beyond),
        "optics" to lsString(R.string.sec_optics_eye_care),
        "telemetry" to lsString(R.string.sec_overdrive_hz_telemetry_hud)
    )
    val savedVistaOrder = prefs.getString("pref_vista_deck_section_order", "brightness,optics,telemetry") ?: "brightness,optics,telemetry"
    var vistaOrder by remember(savedVistaOrder) {
        mutableStateOf(savedVistaOrder.split(",").filter { it in vistaSections }.let { it + (vistaSections - it.toSet()) })
    }
    var vistaPinned by remember {
        val raw = prefs.getString("pref_vista_deck_pinned_section", "brightness") ?: "brightness"
        mutableStateOf(if (raw in vistaSections) raw else "brightness")
    }
    var isBrightnessExpanded by remember { mutableStateOf(vistaPinned == "brightness" || prefs.getBoolean("pref_vista_sub_brightness_expanded", true)) }
    var isOpticsExpanded by remember { mutableStateOf(vistaPinned == "optics" || prefs.getBoolean("pref_vista_sub_optics_expanded", true)) }
    var isTelemetryExpanded by remember { mutableStateOf(vistaPinned == "telemetry" || prefs.getBoolean(LightspeedPreferences.KEY_VISTA_SUB_TELEMETRY_EXPANDED, true)) }

    if (isBlueprintMode) {
        val activeOrder = if (mode == SystemDeckMode.SONIC) sonicOrder else vistaOrder
        val activePinned = if (mode == SystemDeckMode.SONIC) sonicPinned else vistaPinned
        val activeTitles = if (mode == SystemDeckMode.SONIC) sonicTitles else vistaTitles
        val orderKey = if (mode == SystemDeckMode.SONIC) "pref_sonic_deck_section_order" else "pref_vista_deck_section_order"
        val pinKey = if (mode == SystemDeckMode.SONIC) "pref_sonic_deck_pinned_section" else "pref_vista_deck_pinned_section"

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScrollWithEdgeFade()
        ) {
            BlueprintWireframeView(
                tabTitle = if (mode == SystemDeckMode.SONIC) lsString(R.string.sdeck_bp_sonic) else lsString(R.string.sdeck_bp_vista),
                sectionIds = activeOrder,
                pinnedSectionId = activePinned,
                sectionTitles = activeTitles,
                onMoveUp = { idx ->
                    if (idx > 0) {
                        val m = activeOrder.toMutableList()
                        val item = m.removeAt(idx)
                        m.add(idx - 1, item)
                        if (mode == SystemDeckMode.SONIC) sonicOrder = m else vistaOrder = m
                        prefs.edit().putString(orderKey, m.joinToString(",")).apply()
                    }
                },
                onMoveDown = { idx ->
                    if (idx < activeOrder.size - 1) {
                        val m = activeOrder.toMutableList()
                        val item = m.removeAt(idx)
                        m.add(idx + 1, item)
                        if (mode == SystemDeckMode.SONIC) sonicOrder = m else vistaOrder = m
                        prefs.edit().putString(orderKey, m.joinToString(",")).apply()
                    }
                },
                onPinSection = { id ->
                    prefs.edit().putString(pinKey, id).apply()
                    if (mode == SystemDeckMode.SONIC) {
                        sonicPinned = id
                        when (id) {
                            "mixer" -> { isMixerExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_mixer_expanded", true).apply() }
                            "dialogue" -> { isDialogueExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_dialogue_expanded", true).apply() }
                            "streams" -> { isStreamsExpanded = true; prefs.edit().putBoolean("pref_sonic_sub_streams_expanded", true).apply() }
                        }
                    } else {
                        vistaPinned = id
                        when (id) {
                            "brightness" -> { isBrightnessExpanded = true; prefs.edit().putBoolean("pref_vista_sub_brightness_expanded", true).apply() }
                            "optics" -> { isOpticsExpanded = true; prefs.edit().putBoolean("pref_vista_sub_optics_expanded", true).apply() }
                            "telemetry" -> { isTelemetryExpanded = true; prefs.edit().putBoolean(LightspeedPreferences.KEY_VISTA_SUB_TELEMETRY_EXPANDED, true).apply() }
                        }
                    }
                },
                onExitBlueprint = onExitBlueprint
            )
        }
        return
    }

    val audioManager = remember { service.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var voiceOn by remember { mutableStateOf(SonicVoiceIsolationEngine.isEnabled) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LightspeedPreferences.KEY_SONIC_VOICE_ISOLATION_ENABLED) {
                voiceOn = prefs.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var mediaVol by remember {
        val max = try { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat().coerceAtLeast(1f) } catch (_: Exception) { 15f }
        val cur = try { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() } catch (_: Exception) { 10f }
        mutableFloatStateOf((cur / max).coerceIn(0f, 1f))
    }
    var lastUnmutedMediaVol by remember {
        mutableFloatStateOf(if (mediaVol > 0f) mediaVol else 0.45f)
    }
    var isSilent by remember {
        mutableStateOf(
            try { audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT } catch (_: Exception) { false }
        )
    }
    var appResetEpoch by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .padding(16.dp)
            .verticalScrollWithEdgeFade(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Unified Header + Quick Deck Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleBlueprint)
                    .padding(4.dp)
            ) {
                Icon(
                    imageVector = if (mode == SystemDeckMode.SONIC) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.Brightness4,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (mode == SystemDeckMode.SONIC) lsString(R.string.sdeck_hdr_sonic) else lsString(R.string.sdeck_hdr_vista),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = lsString(R.string.sdeck_cd_blueprint),
                    tint = tint.copy(alpha = 0.85f),
                    modifier = Modifier.size(15.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val otherMode = if (mode == SystemDeckMode.VISTA) SystemDeckMode.SONIC else SystemDeckMode.VISTA
                val otherLabel = if (otherMode == SystemDeckMode.SONIC) lsString(R.string.sdeck_btn_sonic) else lsString(R.string.sdeck_btn_vista)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tint.copy(alpha = 0.16f))
                        .border(0.7.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                        .clickable { onSelectMode(otherMode) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = otherLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = lsString(R.string.cc_common_close), tint = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        HorizontalDivider(color = tint.copy(alpha = 0.25f))

        if (mode == SystemDeckMode.VISTA) {
            VistaQuickOpticsStrip(
                service = service,
                prefs = prefs,
                snapshot = snapshot
            )
            vistaOrder.forEach { secId ->
                when (secId) {
                    "brightness" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_brightness_and_beyond),
                        subtitle = null,
                        isExpanded = isBrightnessExpanded,
                        onToggle = {
                            isBrightnessExpanded = !isBrightnessExpanded
                            prefs.edit().putBoolean("pref_vista_sub_brightness_expanded", isBrightnessExpanded).apply()
                        }
                    ) {
                        VistaBrightnessSection(service = service, onOpenMediaOverridePicker = onOpenMediaPicker)
                    }
                    "optics" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_optics_eye_care),
                        subtitle = lsString(R.string.secd_below_0_targets_extra_dim_oled),
                        isExpanded = isOpticsExpanded,
                        onToggle = {
                            isOpticsExpanded = !isOpticsExpanded
                            prefs.edit().putBoolean("pref_vista_sub_optics_expanded", isOpticsExpanded).apply()
                        }
                    ) {
                        VistaOpticsSection(service = service)
                    }
                    "telemetry" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_overdrive_hz_telemetry_hud),
                        subtitle = lsString(R.string.secd_hardware_refresh_rate_selector),
                        isExpanded = isTelemetryExpanded,
                        onToggle = {
                            isTelemetryExpanded = !isTelemetryExpanded
                            prefs.edit().putBoolean(LightspeedPreferences.KEY_VISTA_SUB_TELEMETRY_EXPANDED, isTelemetryExpanded).apply()
                        }
                    ) {
                        VistaTelemetryHudSection(service = service, onOpenTelemetryAppPicker = onOpenTelemetryPicker)
                    }
                }
            }
        } else {
            SonicQuickControlsStrip(
                context = service,
                snapshot = snapshot,
                voiceOn = voiceOn,
                isMuted = mediaVol <= 0.001f,
                isSilent = isSilent,
                onToggleVoice = {
                    val next = !voiceOn
                    voiceOn = next
                    SonicVoiceIsolationEngine.setEnabled(service, next)
                },
                onToggleMute = {
                    val mutedNow = mediaVol <= 0.001f
                    try {
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        if (mutedNow) {
                            val restore = lastUnmutedMediaVol.coerceIn(0.1f, 1f)
                            val targetStep = kotlin.math.round(restore * max).toInt().coerceAtLeast(1)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
                            mediaVol = targetStep.toFloat() / max
                        } else {
                            lastUnmutedMediaVol = mediaVol
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                            mediaVol = 0f
                        }
                    } catch (_: Exception) {}
                },
                onToggleSilent = {
                    try {
                        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        if (nm != null && nm.isNotificationPolicyAccessGranted) {
                            val nextSilent = !isSilent
                            audioManager.ringerMode = if (nextSilent) {
                                AudioManager.RINGER_MODE_SILENT
                            } else {
                                AudioManager.RINGER_MODE_NORMAL
                            }
                            isSilent = audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT
                        } else {
                            audioManager.ringerMode = if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                                audioManager.ringerMode == AudioManager.RINGER_MODE_VIBRATE
                            ) {
                                AudioManager.RINGER_MODE_NORMAL
                            } else {
                                AudioManager.RINGER_MODE_VIBRATE
                            }
                            isSilent = audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL
                        }
                    } catch (_: Exception) {}
                }
            )
            sonicOrder.forEach { secId ->
                when (secId) {
                    "mixer" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_per_app_audio_mixer_routing),
                        subtitle = lsString(R.string.secd_hardware_gain_0_200_multi_chan),
                        isExpanded = isMixerExpanded,
                        onToggle = {
                            isMixerExpanded = !isMixerExpanded
                            prefs.edit().putBoolean("pref_sonic_sub_mixer_expanded", isMixerExpanded).apply()
                        }
                    ) {
                        key(appResetEpoch) {
                            SonicAppMixerSection(context = service, snapshot = snapshot)
                        }
                    }
                    "dialogue" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_voice_isolation_background_muf),
                        subtitle = lsString(R.string.secd_real_time_speech_clarity_backg),
                        isExpanded = isDialogueExpanded,
                        onToggle = {
                            isDialogueExpanded = !isDialogueExpanded
                            prefs.edit().putBoolean("pref_sonic_sub_dialogue_expanded", isDialogueExpanded).apply()
                        }
                    ) {
                        SonicDialogueSection(
                            context = service,
                            snapshot = snapshot,
                            voiceOn = voiceOn,
                            onVoiceOnChanged = { voiceOn = it },
                            showSaveDialog = isSonicSaveDialogOpen,
                            onShowSaveDialogChange = onSonicSaveDialogChange
                        )
                    }
                    "streams" -> CollapsibleSubSection(
                        title = lsString(R.string.sec_master_system_streams),
                        subtitle = lsString(R.string.secd_media_ring_notification_alarm),
                        isExpanded = isStreamsExpanded,
                        onToggle = {
                            isStreamsExpanded = !isStreamsExpanded
                            prefs.edit().putBoolean("pref_sonic_sub_streams_expanded", isStreamsExpanded).apply()
                        }
                    ) {
                        SonicMasterStreamsSection(
                            context = service,
                            audioManager = audioManager,
                            snapshot = snapshot,
                            mediaVol = mediaVol,
                            onMediaVolChange = { next ->
                                if (next > 0.001f) lastUnmutedMediaVol = next
                                mediaVol = next
                            },
                            onAllMuted = {
                                if (mediaVol > 0.001f) lastUnmutedMediaVol = mediaVol
                                try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) } catch (_: Exception) {}
                                mediaVol = 0f
                            },
                            onAppsReset = { appResetEpoch += 1 }
                        )
                    }
                }
            }
        }
    }
}
