package com.sbf.lightspeed.settings

import android.content.Context
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
        cornerRadius,
        borderWidth,
        glowStrength,
        highlightStrength,
        customTintArgb
    ) {
        basePreset.copy(
            windowBlurRadiusPx = blurRadius,
            panelAlpha = opacity,
            cornerRadiusDp = cornerRadius,
            borderWidthDp = borderWidth,
            glowStrength = glowStrength,
            highlightStrength = highlightStrength,
            tintArgb = if (basePreset.styleId == DeckStylePresets.ADAPTIVE.styleId) basePreset.tintArgb else (customTintArgb ?: basePreset.tintArgb)
        )
    }

    // Track whether any parameters were modified from the saved state
    val hasUnsavedChanges = remember(
        blurRadius,
        opacity,
        cornerRadius,
        borderWidth,
        glowStrength,
        highlightStrength,
        customTintArgb,
        initialCustomization
    ) {
        blurRadius != initialCustomization.blurRadius ||
            opacity != initialCustomization.opacity ||
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
        cornerRadius = basePreset.cornerRadiusDp
        borderWidth = basePreset.borderWidthDp
        glowStrength = basePreset.glowStrength
        highlightStrength = basePreset.highlightStrength
        customTintArgb = null
    }

    // Modal Profile Naming Dialog
    if (isAddingNewProfile) {
        ThemeProfileNamingDialog(
            title = "Save New Profile",
            initialName = "",
            onConfirm = { name ->
                val currentMap = mapOf(
                    "blur" to blurRadius.toFloat(),
                    "opacity" to opacity,
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
            title = "Rename Profile",
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
            presetDisplayName = basePreset.displayName,
            onConfirmDiscard = {
                showDiscardPrompt = false
                onDismiss()
            },
            onDismissRequest = { showDiscardPrompt = false }
        )
    }

    // Main Dialog Host
    Dialog(
        onDismissRequest = { handleDismissAttempt() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // The dialog card ITSELF renders directly in the live theme optics
        DeckGlassCardSurface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f),
            snapshotOverride = liveSnapshot
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // 1. Header Bar
                ThemeCustomizationHeader(
                    displayName = basePreset.displayName,
                    onCloseClick = { handleDismissAttempt() }
                )

                // 2. Scrollable Body: Profiles, Sliders & Color Tint Infusion
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
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

/**
 * Backward compatibility alias for legacy callers expecting [LiquidGlassCustomizationDialog].
 */
@Composable
fun LiquidGlassCustomizationDialog(
    context: Context,
    onDismiss: () -> Unit
) = DeckThemeCustomizationDialog(context, DeckStylePresets.PRISM.styleId, onDismiss)
