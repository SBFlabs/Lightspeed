package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Universal In-Place Optical Customization Dialog for all 12 Deck styles.
 *
 * Requirements:
 * 1. In-place live rendering: The dialog surface ITSELF renders in the theme being tuned
 *    and responds in real-time to slider changes. No separate fake preview box.
 * 2. Multi-profile preset management per theme (save, select, rename, delete custom profiles).
 * 3. Save & Restore Defaults action buttons.
 * 4. Discard warning confirmation prompt on exit if unsaved changes exist.
 */
@Composable
fun DeckThemeCustomizationDialog(
    context: Context,
    styleId: String = DeckStylePresets.PRISM.styleId,
    onDismiss: () -> Unit
) {
    val prefs = remember { context.defaultPrefs() }
    val basePreset = remember(styleId) { DeckStylePresets.resolve(styleId) }
    val initialCustomization = remember(styleId) {
        ThemeCustomizationManager.getCustomization(context, styleId)
    }

    // Editable optical tuning states
    var blurRadius by remember { mutableIntStateOf(initialCustomization.blurRadius) }
    var opacity by remember { mutableFloatStateOf(initialCustomization.opacity) }
    var backdropOpacity by remember { mutableFloatStateOf(initialCustomization.backdropOpacity) }

    // Stream live backdropOpacity to Central Command's scrim and window dim while Live Lab is open
    LaunchedEffect(backdropOpacity) {
        DeckThemeManager.setLiveBackdropOpacityOverride(backdropOpacity)
    }
    DisposableEffect(Unit) {
        onDispose {
            DeckThemeManager.setLiveBackdropOpacityOverride(null)
        }
    }
    var cornerRadius by remember { mutableFloatStateOf(initialCustomization.cornerRadius) }
    var borderWidth by remember { mutableFloatStateOf(initialCustomization.borderWidth) }
    var glowStrength by remember { mutableFloatStateOf(initialCustomization.glowStrength) }
    var highlightStrength by remember { mutableFloatStateOf(initialCustomization.highlightStrength) }
    var customTintArgb by remember { mutableStateOf(initialCustomization.customTintArgb) }
    var blurMode by remember { mutableStateOf(BackdropBlurModeManager.getMode(context)) }

    // Multi-profile state
    var profiles by remember(styleId) {
        mutableStateOf(ThemeProfileManager.getProfiles(prefs, styleId))
    }
    var profileToRename by remember { mutableStateOf<ThemeProfileManager.ThemeProfile?>(null) }
    var isAddingNewProfile by remember { mutableStateOf(false) }

    // Discard warning state
    var showDiscardPrompt by remember { mutableStateOf(false) }

    // Real-time dynamic snapshot for the dialog container surface itself
    val liveSnapshot = remember(
        basePreset,
        blurRadius,
        opacity,
        backdropOpacity,
        cornerRadius,
        borderWidth,
        glowStrength,
        highlightStrength,
        customTintArgb
    ) {
        val tintVal = customTintArgb
        val effectiveTint = if (basePreset.styleId == DeckStylePresets.ADAPTIVE.styleId) basePreset.tintArgb else (tintVal ?: basePreset.tintArgb)
        val effectiveGlow = if (tintVal != null && basePreset.styleId in setOf("jelly", "depth", "neon", "prism")) {
            tintVal
        } else basePreset.glowColorArgb

        val effectiveBorder = if (tintVal != null && basePreset.styleId in setOf("jelly", "depth")) {
            val r = (tintVal shr 16 and 0xFF).toInt()
            val g = (tintVal shr 8 and 0xFF).toInt()
            val b = (tintVal and 0xFF).toInt()
            val brightR = (r * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
            val brightG = (g * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
            val brightB = (b * 0.65f + 255 * 0.35f).toInt().coerceIn(0, 255)
            (0xB3L shl 24) or (brightR.toLong() shl 16) or (brightG.toLong() shl 8) or brightB.toLong()
        } else basePreset.borderColorArgb

        val effectivePanel = if (tintVal != null && basePreset.styleId in setOf("jelly", "depth")) {
            val r = (tintVal shr 16 and 0xFF).toInt()
            val g = (tintVal shr 8 and 0xFF).toInt()
            val b = (tintVal and 0xFF).toInt()
            val baseR = (14 * 0.85f + r * 0.15f).toInt().coerceIn(0, 255)
            val baseG = (16 * 0.85f + g * 0.15f).toInt().coerceIn(0, 255)
            val baseB = (24 * 0.85f + b * 0.15f).toInt().coerceIn(0, 255)
            (0xFFL shl 24) or (baseR.toLong() shl 16) or (baseG.toLong() shl 8) or baseB.toLong()
        } else basePreset.panelColorArgb

        basePreset.copy(
            windowBlurRadiusPx = blurRadius,
            panelAlpha = opacity,
            dimAmount = backdropOpacity,
            cornerRadiusDp = cornerRadius,
            borderWidthDp = borderWidth,
            glowStrength = glowStrength,
            highlightStrength = highlightStrength,
            tintArgb = effectiveTint,
            glowColorArgb = effectiveGlow,
            borderColorArgb = effectiveBorder,
            panelColorArgb = effectivePanel
        )
    }

    // Track whether any parameters were modified from the saved state
    val hasUnsavedChanges = remember(
        blurRadius,
        opacity,
        backdropOpacity,
        cornerRadius,
        borderWidth,
        glowStrength,
        highlightStrength,
        customTintArgb,
        initialCustomization
    ) {
        blurRadius != initialCustomization.blurRadius ||
            opacity != initialCustomization.opacity ||
            backdropOpacity != initialCustomization.backdropOpacity ||
            cornerRadius != initialCustomization.cornerRadius ||
            borderWidth != initialCustomization.borderWidth ||
            glowStrength != initialCustomization.glowStrength ||
            highlightStrength != initialCustomization.highlightStrength ||
            customTintArgb != initialCustomization.customTintArgb
    }

    fun handleDismissAttempt() {
        if (hasUnsavedChanges) {
            LightspeedHapticEngine.triggerWarning(context)
            showDiscardPrompt = true
        } else {
            LightspeedHapticEngine.tick(context)
            onDismiss()
        }
    }

    fun applyProfile(profile: ThemeProfileManager.ThemeProfile) {
        LightspeedHapticEngine.tick(context)
        val p = profile.params
        p["blur"]?.let { blurRadius = it.toInt() }
        p["opacity"]?.let { opacity = it }
        p["backdrop_opacity"]?.let { backdropOpacity = it }
        p["corner"]?.let { cornerRadius = it }
        p["border"]?.let { borderWidth = it }
        p["glow"]?.let { glowStrength = it }
        p["highlight"]?.let { highlightStrength = it }
        val tint = p["tint"]?.toLong()
        customTintArgb = if (tint != null && tint > 0) tint else null
    }

    fun saveCurrentChanges() {
        LightspeedHapticEngine.tick(context)
        val updated = ThemeCustomizationManager.ThemeCustomization(
            styleId = styleId,
            blurRadius = blurRadius,
            opacity = opacity,
            backdropOpacity = backdropOpacity,
            cornerRadius = cornerRadius,
            borderWidth = borderWidth,
            glowStrength = glowStrength,
            highlightStrength = highlightStrength,
            customTintArgb = customTintArgb
        )
        ThemeCustomizationManager.saveCustomization(context, updated)
        onDismiss()
    }

    fun restoreDefaults() {
        LightspeedHapticEngine.tick(context)
        ThemeCustomizationManager.resetToDefaults(context, styleId)
        blurRadius = basePreset.windowBlurRadiusPx
        opacity = basePreset.panelAlpha
        backdropOpacity = basePreset.dimAmount
        cornerRadius = basePreset.cornerRadiusDp
        borderWidth = basePreset.borderWidthDp
        glowStrength = basePreset.glowStrength
        highlightStrength = basePreset.highlightStrength
        customTintArgb = null
    }

    // Modal Profile Naming Dialog
    if (isAddingNewProfile) {
        ThemeProfileNamingDialog(
            title = lsString(R.string.lg_save_new),
            initialName = "",
            onConfirm = { name ->
                val currentMap = mapOf(
                    "blur" to blurRadius.toFloat(),
                    "opacity" to opacity,
                    "backdrop_opacity" to backdropOpacity,
                    "corner" to cornerRadius,
                    "border" to borderWidth,
                    "glow" to glowStrength,
                    "highlight" to highlightStrength,
                    "tint" to (customTintArgb?.toFloat() ?: -1f)
                )
                ThemeProfileManager.saveProfile(prefs, styleId, name, currentMap)
                profiles = ThemeProfileManager.getProfiles(prefs, styleId)
                isAddingNewProfile = false
            },
            onDismiss = { isAddingNewProfile = false }
        )
    }

    // Modal Profile Rename Dialog
    profileToRename?.let { profile ->
        ThemeProfileNamingDialog(
            title = lsString(R.string.lg_rename),
            initialName = profile.label,
            onConfirm = { newName ->
                ThemeProfileManager.renameProfile(prefs, styleId, profile, newName)
                profiles = ThemeProfileManager.getProfiles(prefs, styleId)
                profileToRename = null
            },
            onDismiss = { profileToRename = null }
        )
    }

    // Discard Unsaved Changes Warning Dialog
    if (showDiscardPrompt) {
        ThemeDiscardChangesDialog(
            presetDisplayName = basePreset.localizedName(),
            onConfirmDiscard = {
                showDiscardPrompt = false
                onDismiss()
            },
            onDismissRequest = { showDiscardPrompt = false }
        )
    }

    // Main Dialog Host
    val scrimContext = androidx.compose.ui.platform.LocalContext.current
    val activeDensity = LocalDensity.current
    Dialog(
        onDismissRequest = { handleDismissAttempt() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalDensity provides activeDensity) {
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        androidx.compose.runtime.SideEffect {
            dialogWindow?.setLayout(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT
            )
            dialogWindow?.setDimAmount(liveSnapshot.dimAmount)
        }
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .overlayScrimDismiss(
                        context = scrimContext,
                        onTapOutside = { handleDismissAttempt() },
                        onLongPressOutside = {
                            com.sbf.lightspeed.system.LightspeedOverlayDismissCoordinator.dismissAll(scrimContext)
                        }
                    )
            )
        // The dialog card ITSELF renders directly in the live theme optics
        DeckGlassCardSurface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .blockScrimPassthrough(),
            snapshotOverride = liveSnapshot
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 1. Header Bar
                ThemeCustomizationHeader(
                    displayName = basePreset.localizedName(),
                    onCloseClick = { handleDismissAttempt() }
                )

                // 2. Scrollable Body: Profiles, Sliders & Color Tint Infusion
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScrollWithEdgeFade(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Multi-Profile Preset Manager Section
                    ThemeProfileSection(
                        profiles = profiles,
                        onSelectProfile = { applyProfile(it) },
                        onRenameProfile = { profileToRename = it },
                        onDeleteProfile = {
                            LightspeedHapticEngine.tick(context)
                            ThemeProfileManager.deleteProfile(prefs, styleId, it)
                            profiles = ThemeProfileManager.getProfiles(prefs, styleId)
                        },
                        onAddProfile = { isAddingNewProfile = true }
                    )

                    HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

                    // Optical Sliders Section
                    ThemeOpticalSlidersSection(
                        opacity = opacity,
                        onOpacityChange = { opacity = it },
                        backdropOpacity = backdropOpacity,
                        onBackdropOpacityChange = { backdropOpacity = it },
                        blurRadius = blurRadius,
                        onBlurRadiusChange = { blurRadius = it },
                        cornerRadius = cornerRadius,
                        onCornerRadiusChange = { cornerRadius = it },
                        borderWidth = borderWidth,
                        onBorderWidthChange = { borderWidth = it },
                        glowStrength = glowStrength,
                        onGlowStrengthChange = { glowStrength = it },
                        highlightStrength = highlightStrength,
                        onHighlightStrengthChange = { highlightStrength = it }
                    )

                    HorizontalDivider(color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

                    // Global Backdrop Blur Engine Selector (Shared Across All 12 Themes)
                    BackdropBlurModeSelector(
                        selectedMode = blurMode,
                        onModeSelected = { newMode ->
                            blurMode = newMode
                            BackdropBlurModeManager.setMode(context, newMode)
                            LightspeedHapticEngine.tick(context)
                        }
                    )

                    // Color Tint Override Palette (Adaptive dynamically follows system wallpaper)
                    if (basePreset.styleId != DeckStylePresets.ADAPTIVE.styleId) {
                        ThemeColorTintSection(
                            context = context,
                            baseTintArgb = basePreset.tintArgb,
                            customTintArgb = customTintArgb,
                            onTintChanged = { customTintArgb = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 3. Bottom Action Bar: Restore Defaults & Save
                ThemeCustomizationBottomBar(
                    hasUnsavedChanges = hasUnsavedChanges,
                    onRestoreDefaults = { restoreDefaults() },
                    onSaveAndApply = { saveCurrentChanges() }
                )
            }
        }
        }
        }
    }
}

/**
 * Backward compatibility alias for legacy callers expecting [LiquidGlassCustomizationDialog].
 */
@Composable
fun LiquidGlassCustomizationDialog(
    context: Context,
    onDismiss: () -> Unit
) = DeckThemeCustomizationDialog(context, DeckStylePresets.PRISM.styleId, onDismiss)
