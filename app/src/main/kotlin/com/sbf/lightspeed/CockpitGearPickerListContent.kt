package com.sbf.lightspeed

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.settings.resolveDynamicTokenLabel
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.ActionMethods
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.PopupDiagnostics
import com.sbf.lightspeed.system.safeReloadPreferences
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * LazyColumn List showing apps, shortcuts, and tactical action rows.
 */
@Composable
internal fun CockpitGearPickerItemList(
    flatItemsList: List<PickerRowItem>,
    listState: LazyListState,
    selectedTokens: List<String>,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    context: Context,
    prefs: SharedPreferences,
    allSysCategories: List<String>,
    expandedSubsections: Set<String>,
    onExpandedSubsectionsChanged: (Set<String>) -> Unit,
    onTokenSelected: (String) -> Unit,
    onShowSystemAccordionPrefs: () -> Unit,
    onPendingPinApp: (Pair<String, String>) -> Unit,
    onPendingDeepActivityRequest: (String) -> Unit,
    onLaunchWizard: (String) -> Unit,
    onSelectAttitudeBucket: (LightspeedOrientationEngine.AttitudeBucket) -> Unit,
    onSelectActionMethod: (Pair<String, String>) -> Unit,
    onStateMutated: () -> Unit,
    singleSelectPrefKey: String?,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxHeight()
    ) {
        items(items = flatItemsList, key = { it.key }) { item ->
            when (item) {
                is PickerRowItem.SystemHeader -> PickerSystemHeaderRow(
                    item = item,
                    dynamicSecondary = dynamicSecondary,
                    onClick = {
                        val sysKey = "category:system"
                        onExpandedSubsectionsChanged(
                            if (item.isExpanded) expandedSubsections - sysKey else expandedSubsections + sysKey
                        )
                    },
                    onToggleAll = {
                        val areAllCollapsed = allSysCategories.all { expandedSubsections.contains(it) }
                        onExpandedSubsectionsChanged(
                            if (areAllCollapsed) expandedSubsections - allSysCategories.toSet()
                            else expandedSubsections + allSysCategories.toSet()
                        )
                    },
                    onToggleAllLongClick = onShowSystemAccordionPrefs
                )
                is PickerRowItem.SystemCategoryHeader -> PickerSystemCategoryHeaderRow(
                    item = item,
                    dynamicPrimary = dynamicPrimary,
                    onClick = {
                        val collapseKey = "collapsed:${item.categoryKey}"
                        onExpandedSubsectionsChanged(
                            if (item.isExpanded) expandedSubsections + collapseKey else expandedSubsections - collapseKey
                        )
                    }
                )
                is PickerRowItem.SystemAction -> PickerSystemActionRow(
                    item = item,
                    isChecked = selectedTokens.contains(item.token),
                    dynamicPrimary = dynamicPrimary,
                    dynamicSecondary = dynamicSecondary,
                    onSelect = { onTokenSelected(item.token) },
                    onCustomizeToggle = {
                        val customKey = "customization:${item.token}"
                        onExpandedSubsectionsChanged(
                            if (item.isExpanded) expandedSubsections - customKey else expandedSubsections + customKey
                        )
                    }
                )
                is PickerRowItem.SystemCustomizationSlider -> PickerCustomizationSliderRow(
                    item = item,
                    dynamicSecondary = dynamicSecondary,
                    onValueChange = { newVal ->
                        when (item.prefKey) {
                            LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION -> {
                                val resVal = round(newVal).toInt().coerceIn(10, 254)
                                val stepVal = (255f / resVal).toInt().coerceIn(1, 32)
                                prefs.edit()
                                    .putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, resVal)
                                    .putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, stepVal)
                                    .apply()
                                safeReloadPreferences()
                                onStateMutated()
                            }
                            LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION -> {
                                val resVal = round(newVal).toInt().coerceIn(5, 100)
                                val stepVal = (100f / resVal).roundToInt().coerceIn(1, 20)
                                prefs.edit()
                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, resVal)
                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, stepVal)
                                    .apply()
                                safeReloadPreferences()
                                onStateMutated()
                            }
                            LightspeedPreferences.KEY_VOLUME_SCRUB_STEP -> {
                                val volVal = round(newVal).toInt().coerceIn(1, 25)
                                prefs.edit()
                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, volVal)
                                    .apply()
                                safeReloadPreferences()
                                onStateMutated()
                            }
                            else -> {
                                prefs.edit().putFloat(item.prefKey, newVal).apply()
                                safeReloadPreferences()
                                onStateMutated()
                            }
                        }
                    }
                )
                is PickerRowItem.SystemCustomizationOption -> {
                    if (item.optionKey.startsWith("popup_style:")) {
                        PickerPopupStyleOptionRow(
                            item = item,
                            dynamicSecondary = dynamicSecondary,
                            onClick = {
                                val style = item.optionKey.removePrefix("popup_style:")
                                prefs.edit().putString(LightspeedPreferences.KEY_POPUP_STYLE, style).apply()
                                safeReloadPreferences()
                                onStateMutated()
                            },
                            onLongClick = {
                                PopupDiagnostics.copyToClipboard(context)
                            }
                        )
                    } else {
                        PickerCustomizationOptionRow(
                            item = item,
                            dynamicSecondary = dynamicSecondary,
                            onClick = {
                                when {
                                    item.optionKey.startsWith("action_method:") -> {
                                        val parts = item.optionKey.split(":")
                                        if (parts.size >= 4) {
                                            val parentToken = "${parts[1]}:${parts[2]}"
                                            val methodId = parts[3]
                                            if (ActionMethods.isOptionEnabled(methodId)) {
                                                ActionMethods.toggle(context, parentToken, methodId)
                                                LightspeedActionRegistry.labelCache[parentToken] = resolveDynamicTokenLabel(context, parentToken)
                                                safeReloadPreferences()
                                                onStateMutated()
                                            }
                                        }
                                    }
                                    item.optionKey.startsWith("action_behavior:") -> {
                                        val parts = item.optionKey.split(":")
                                        if (parts.size >= 4) {
                                            val parentToken = "${parts[1]}:${parts[2]}"
                                            val behavior = parts[3]
                                            LightspeedPreferences.setActionBehavior(context, parentToken, behavior)
                                            LightspeedActionRegistry.labelCache[parentToken] = resolveDynamicTokenLabel(context, parentToken)
                                            safeReloadPreferences()
                                            onStateMutated()
                                        }
                                    }
                                    item.optionKey.startsWith("gravity_bucket_assign:") -> {
                                        val bucketName = item.optionKey.removePrefix("gravity_bucket_assign:")
                                        val isGravityEnabled = prefs.getBoolean(LightspeedPreferences.KEY_SYNTHETIC_GRAVITY_ENABLED, false)
                                        if (!isGravityEnabled) {
                                            Toast.makeText(context, "Synthetic Gravity Engine is disabled", Toast.LENGTH_SHORT).show()
                                        } else {
                                            val bucket = runCatching { LightspeedOrientationEngine.AttitudeBucket.valueOf(bucketName) }.getOrNull()
                                            if (bucket != null) {
                                                onSelectAttitudeBucket(bucket)
                                            }
                                        }
                                    }
                                    item.parentToken == "system:media_skip_forward" || item.parentToken == "system:media_skip_backward" -> {
                                        val sec = item.optionKey.toIntOrNull() ?: 10
                                        LightspeedPreferences.setMediaSkipSeconds(prefs, forward = (item.parentToken == "system:media_skip_forward"), sec = sec)
                                        LightspeedActionRegistry.labelCache["system:media_skip_forward"] = resolveDynamicTokenLabel(context, "system:media_skip_forward")
                                        LightspeedActionRegistry.labelCache["system:media_skip_backward"] = resolveDynamicTokenLabel(context, "system:media_skip_backward")
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("brightness_hud_toggle:") -> {
                                        val enabled = item.optionKey.endsWith(":on")
                                        prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, enabled).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("volume_hud_toggle:") -> {
                                        val enabled = item.optionKey.endsWith(":on")
                                        prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, enabled).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("volume_native_slider:") -> {
                                        val enabled = item.optionKey.endsWith(":on")
                                        prefs.edit().putBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, enabled).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("popup_style:") -> {
                                        val style = item.optionKey.removePrefix("popup_style:")
                                        prefs.edit().putString(LightspeedPreferences.KEY_POPUP_STYLE, style).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("brightness_step:") -> {
                                        val step = item.optionKey.removePrefix("brightness_step:").toIntOrNull() ?: 8
                                        prefs.edit().putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, step).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("volume_step:") -> {
                                        val step = item.optionKey.removePrefix("volume_step:").toIntOrNull() ?: 1
                                        prefs.edit().putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, step).apply()
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    item.optionKey.startsWith("hud_style:") -> {
                                        val style = item.optionKey.removePrefix("hud_style:")
                                        LightspeedPreferences.saveHudStyle(prefs, singleSelectPrefKey, item.parentToken, style)
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                    else -> {
                                        val style = item.optionKey
                                        LightspeedPreferences.saveHudStyle(prefs, singleSelectPrefKey, item.parentToken, style)
                                        safeReloadPreferences()
                                        onStateMutated()
                                    }
                                }
                            },
                            onLongClick = {
                                if (item.optionKey.startsWith("action_method:")) {
                                    val parts = item.optionKey.split(":")
                                    if (parts.size >= 4) {
                                        val parentToken = "${parts[1]}:${parts[2]}"
                                        val methodId = parts[3]
                                        onSelectActionMethod(Pair(parentToken, methodId))
                                    }
                                }
                            }
                        )
                    }
                }
                is PickerRowItem.AppHeader -> PickerAppHeaderRow(
                    item = item,
                    isAppSelected = selectedTokens.contains(item.appToken),
                    context = context,
                    dynamicPrimary = dynamicPrimary,
                    dynamicSecondary = dynamicSecondary,
                    onSelect = { onTokenSelected(item.appToken) },
                    onLongClick = { onPendingPinApp(Pair(item.packageName, item.appName)) },
                    onExpandToggle = {
                        val appKey = "app:${item.packageName}"
                        onExpandedSubsectionsChanged(
                            if (item.isExpanded) expandedSubsections - appKey else expandedSubsections + appKey
                        )
                    }
                )
                is PickerRowItem.SubHeader -> PickerSubHeaderRow(
                    item = item,
                    dynamicSecondary = dynamicSecondary,
                    onClick = {
                        val isDeepActivitiesHeader = item.subKey.endsWith("|DeepActivities")
                        if (isDeepActivitiesHeader && !item.isExpanded) {
                            val isSuppressed = prefs.getBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, false)
                            if (!isSuppressed) {
                                onPendingDeepActivityRequest(item.subKey)
                                return@PickerSubHeaderRow
                            }
                        }
                        onExpandedSubsectionsChanged(
                            if (item.isExpanded) expandedSubsections - item.subKey else expandedSubsections + item.subKey
                        )
                    }
                )
                is PickerRowItem.ShortcutAction -> PickerShortcutActionRow(
                    item = item,
                    isChecked = selectedTokens.contains(item.token),
                    context = context,
                    dynamicPrimary = dynamicPrimary,
                    onSelect = { onTokenSelected(item.token) },
                    onLaunchWizard = { onLaunchWizard(item.token) },
                    onLongClick = {
                        if (!item.isPlugin) {
                            LightspeedHapticEngine.click(context)
                            ActionDispatcher.execute(context.applicationContext, item.token)
                        }
                    }
                )
            }
        }
    }
}
