package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.lazy.LazyListScope
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.TacticalToolItem
import com.sbf.lightspeed.system.safeReloadPreferences

fun LazyListScope.renderHudStripAdvancedSection(
    sectionId: String,
    context: Context,
    prefs: SharedPreferences,
    toggleSection: (Int, String, Boolean, (Boolean) -> Unit) -> Unit,
    onRefreshNeeded: () -> Unit,
    isSyntheticGravityExpanded: Boolean,
    onToggleSyntheticGravity: (Boolean) -> Unit,
    isRefuelingExpanded: Boolean,
    onToggleRefueling: (Boolean) -> Unit,
    onShowAmoledWarning: () -> Unit,
    isConfigVaultExpanded: Boolean,
    onToggleConfigVault: (Boolean) -> Unit,
    exportLauncher: ActivityResultLauncher<String>,
    onShowImportOptions: () -> Unit,
    onShowResetConfirm: () -> Unit,
    isSystemOverridesExpanded: Boolean,
    onToggleSystemOverrides: (Boolean) -> Unit,
    isExperimentalLabsExpanded: Boolean,
    onToggleExperimentalLabs: (Boolean) -> Unit,
    isNotchCalibExpanded: Boolean,
    onToggleNotchCalib: () -> Unit,
    isOemNoticeDemoted: Boolean,
    onOemNoticeDemotedChange: (Boolean) -> Unit,
    oemFeatureName: String,
    isMarqueeSubSectionExpanded: Boolean,
    onToggleMarqueeSubSection: () -> Unit,
    isSubPowerExpanded: Boolean,
    onToggleSubPower: () -> Unit,
    isSinglePressUnlocked: Boolean,
    onSinglePressUnlockedChange: (Boolean) -> Unit,
    singlePressTapCount: Int,
    onSinglePressTapCountChange: (Int) -> Unit,
    installedTacticalTools: List<TacticalToolItem>,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>
) {
    when (sectionId) {
        "synthetic_gravity" -> {
            item(key = "synthetic_gravity") {
                HudSyntheticGravitySection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isSyntheticGravityExpanded,
                    onToggle = {
                        toggleSection(1, "synthetic_gravity", isSyntheticGravityExpanded) { onToggleSyntheticGravity(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, !isSyntheticGravityExpanded).apply()
                        safeReloadPreferences()
                    },
                    onRefreshNeeded = onRefreshNeeded
                )
            }
        }
        "refueling_bay" -> {
            item(key = "refueling_bay") {
                HudRefuelingBaySection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isRefuelingExpanded,
                    onToggle = {
                        toggleSection(1, "refueling_bay", isRefuelingExpanded) { onToggleRefueling(it) }
                        prefs.edit().putBoolean("pref_section_refueling_expanded", !isRefuelingExpanded).apply()
                    },
                    onShowAmoledWarning = onShowAmoledWarning,
                    onRefreshNeeded = onRefreshNeeded
                )
            }
        }
        "config_vault" -> {
            item(key = "config_vault") {
                HudConfigVaultSection(
                    isExpanded = isConfigVaultExpanded,
                    onToggle = {
                        toggleSection(1, "config_vault", isConfigVaultExpanded) { onToggleConfigVault(it) }
                        prefs.edit().putBoolean("pref_section_backup_expanded", !isConfigVaultExpanded).apply()
                    },
                    onExportClicked = { exportLauncher.launch("lightspeed-backup.json") },
                    onShowImportOptions = onShowImportOptions,
                    onShowResetConfirm = onShowResetConfirm
                )
            }
        }
        "system_overrides" -> {
            item(key = "system_overrides") {
                HudSystemOverridesSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isSystemOverridesExpanded,
                    onToggle = {
                        toggleSection(1, "system_overrides", isSystemOverridesExpanded) { onToggleSystemOverrides(it) }
                        prefs.edit().putBoolean("pref_section_system_overrides_expanded", !isSystemOverridesExpanded).apply()
                        safeReloadPreferences()
                    }
                )
            }
        }
        "experimental_labs" -> {
            item(key = "experimental_labs") {
                HudExperimentalLabsSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isExperimentalLabsExpanded,
                    onToggle = {
                        toggleSection(1, "experimental_labs", isExperimentalLabsExpanded) { onToggleExperimentalLabs(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_EXPERIMENTAL_LABS_EXPANDED, !isExperimentalLabsExpanded).apply()
                        safeReloadPreferences()
                    },
                    isNotchCalibExpanded = isNotchCalibExpanded,
                    onToggleNotchCalib = onToggleNotchCalib,
                    isOemNoticeDemoted = isOemNoticeDemoted,
                    onOemNoticeDemotedChange = onOemNoticeDemotedChange,
                    oemFeatureName = oemFeatureName,
                    isMarqueeSubSectionExpanded = isMarqueeSubSectionExpanded,
                    onToggleMarqueeSubSection = onToggleMarqueeSubSection,
                    isSubPowerExpanded = isSubPowerExpanded,
                    onToggleSubPower = onToggleSubPower,
                    isSinglePressUnlocked = isSinglePressUnlocked,
                    onSinglePressUnlockedChange = onSinglePressUnlockedChange,
                    singlePressTapCount = singlePressTapCount,
                    onSinglePressTapCountChange = onSinglePressTapCountChange,
                    installedTacticalTools = installedTacticalTools,
                    dynamicActionTokens = dynamicActionTokens,
                    tokenLabelCache = tokenLabelCache,
                    isRefuelingExpanded = isRefuelingExpanded,
                    onToggleRefueling = {
                        val next = !isRefuelingExpanded
                        onToggleRefueling(next)
                        prefs.edit().putBoolean("pref_section_refueling_expanded", next).apply()
                    },
                    onShowAmoledWarning = onShowAmoledWarning,
                    onRefreshNeeded = onRefreshNeeded
                )
            }
        }
    }
}
