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
    isAstrogationAirlockExpanded: Boolean,
    onToggleAstrogationAirlock: (Boolean) -> Unit,
    isSonicDeckExpanded: Boolean,
    onToggleSonicDeck: (Boolean) -> Unit,
    isVistaDeckExpanded: Boolean,
    onToggleVistaDeck: (Boolean) -> Unit,
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
    tokenLabelCache: Map<String, String>,
    languageMode: com.sbf.lightspeed.system.LightspeedLanguageEngine.LanguageMode = com.sbf.lightspeed.system.LightspeedLanguageEngine.modeFlow.value
) {
    when (sectionId) {
        "synthetic_gravity" -> {
            item(key = "synthetic_gravity", contentType = "accordion") {
                HudSyntheticGravitySection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isSyntheticGravityExpanded,
                    onToggle = {
                        toggleSection(1, "synthetic_gravity", isSyntheticGravityExpanded) { onToggleSyntheticGravity(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_SYNTHETIC_GRAVITY_EXPANDED, !isSyntheticGravityExpanded).apply()
                        safeReloadPreferences()
                    },
                    onRefreshNeeded = onRefreshNeeded,
                    languageMode = languageMode
                )
            }
        }
        "astrogation_airlock" -> {
            item(key = "astrogation_airlock", contentType = "accordion") {
                HudAstrogationAirlockSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isAstrogationAirlockExpanded,
                    onToggle = {
                        toggleSection(1, "astrogation_airlock", isAstrogationAirlockExpanded) { onToggleAstrogationAirlock(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_ASTROGATION_AIRLOCK_EXPANDED, !isAstrogationAirlockExpanded).apply()
                        safeReloadPreferences()
                    },
                    languageMode = languageMode
                )
            }
        }
        "sonic_deck" -> {
            item(key = "sonic_deck", contentType = "accordion") {
                HudSonicDeckSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isSonicDeckExpanded,
                    onToggle = {
                        toggleSection(1, "sonic_deck", isSonicDeckExpanded) { onToggleSonicDeck(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_SONIC_DECK_EXPANDED, !isSonicDeckExpanded).apply()
                        safeReloadPreferences()
                    },
                    onRefreshNeeded = onRefreshNeeded,
                    languageMode = languageMode
                )
            }
        }
        "vista_deck" -> {
            item(key = "vista_deck", contentType = "accordion") {
                HudVistaDeckSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isVistaDeckExpanded,
                    onToggle = {
                        toggleSection(1, "vista_deck", isVistaDeckExpanded) { onToggleVistaDeck(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_VISTA_DECK_EXPANDED, !isVistaDeckExpanded).apply()
                        safeReloadPreferences()
                    },
                    onRefreshNeeded = onRefreshNeeded,
                    languageMode = languageMode
                )
            }
        }
        "refueling_bay" -> {
            item(key = "refueling_bay", contentType = "accordion") {
                HudRefuelingBaySection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isRefuelingExpanded,
                    onToggle = {
                        toggleSection(1, "refueling_bay", isRefuelingExpanded) { onToggleRefueling(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_REFUELING_EXPANDED, !isRefuelingExpanded).apply()
                    },
                    onShowAmoledWarning = onShowAmoledWarning,
                    onRefreshNeeded = onRefreshNeeded,
                    languageMode = languageMode
                )
            }
        }
        "config_vault" -> {
            item(key = "config_vault", contentType = "accordion") {
                HudConfigVaultSection(
                    isExpanded = isConfigVaultExpanded,
                    onToggle = {
                        toggleSection(1, "config_vault", isConfigVaultExpanded) { onToggleConfigVault(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_BACKUP_EXPANDED, !isConfigVaultExpanded).apply()
                    },
                    onExportClicked = { exportLauncher.launch("lightspeed-backup.json") },
                    onShowImportOptions = onShowImportOptions,
                    onShowResetConfirm = onShowResetConfirm,
                    languageMode = languageMode
                )
            }
        }
        "system_overrides" -> {
            item(key = "system_overrides", contentType = "accordion") {
                HudSystemOverridesSection(
                    context = context,
                    prefs = prefs,
                    isExpanded = isSystemOverridesExpanded,
                    onToggle = {
                        toggleSection(1, "system_overrides", isSystemOverridesExpanded) { onToggleSystemOverrides(it) }
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_SYSTEM_OVERRIDES_EXPANDED, !isSystemOverridesExpanded).apply()
                        safeReloadPreferences()
                    },
                    languageMode = languageMode
                )
            }
        }
        "experimental_labs" -> {
            item(key = "experimental_labs", contentType = "hazard_accordion") {
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
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SECTION_REFUELING_EXPANDED, next).apply()
                    },
                    onShowAmoledWarning = onShowAmoledWarning,
                    onRefreshNeeded = onRefreshNeeded,
                    languageMode = languageMode
                )
            }
        }
    }
}
