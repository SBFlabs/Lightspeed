package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.CentralCommandBackdropBlur
import com.sbf.lightspeed.settings.DeckGlassCardSurface
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Top-level Screen Scaffold for Cockpit Gear Picker Activity.
 */
@Composable
internal fun CockpitGearPickerScreenContent(
    activity: CockpitGearPickerActivity,
    prefs: SharedPreferences,
    setId: String,
    ringIndex: Int,
    singleSelectPrefKey: String?,
    singleSelectTitle: String,
    isSingleSelect: Boolean,
    isSplitMode: Boolean,
    isHoldGesture: Boolean,
    initialItems: List<String>,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    shortcutConfigLauncher: ActivityResultLauncher<Intent>,
    onApplyWindowBlur: () -> Unit,
    onFinish: () -> Unit
) {
    val activeSnapshot by DeckThemeManager.snapshotFlow.collectAsState()
    LaunchedEffect(activeSnapshot) {
        onApplyWindowBlur()
    }

    var searchQuery by remember { mutableStateOf("") }
    val selectedTokens = remember { mutableStateListOf<String>().apply { addAll(initialItems) } }
    val allSysCategories = listOf(
        "collapsed:sys_nav",
        "collapsed:sys_hw",
        "collapsed:sys_media",
        "collapsed:sys_tactical",
        "collapsed:sys_orient",
        "collapsed:sys_scrub"
    )
    var systemAccordionMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_SYSTEM_ACCORDION_MODE, "expanded") ?: "expanded")
    }
    var expandedSubsections by remember {
        mutableStateOf(
            if (systemAccordionMode == "remember") {
                prefs.getStringSet(LightspeedPreferences.KEY_EXPANDED_SUBSECTIONS, emptySet()) ?: emptySet()
            } else if (systemAccordionMode == "collapsed") {
                allSysCategories.toSet()
            } else {
                emptySet()
            }
        )
    }

    LaunchedEffect(expandedSubsections, systemAccordionMode) {
        if (systemAccordionMode == "remember") {
            prefs.edit().putStringSet(LightspeedPreferences.KEY_EXPANDED_SUBSECTIONS, expandedSubsections).apply()
        }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var pinnedApps by remember {
        mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, emptySet()) ?: emptySet())
    }
    var pendingPinApp by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDeepActivitySubKey by remember { mutableStateOf<String?>(null) }
    var showDeepActivityWarning by remember { mutableStateOf(false) }
    var hudStyleVersion by remember { mutableIntStateOf(0) }
    var showSystemAccordionPrefsDialog by remember { mutableStateOf(false) }
    var selectedAttitudeBucketForAppPicker by remember { mutableStateOf<LightspeedOrientationEngine.AttitudeBucket?>(null) }
    var selectedActionMethodForAppPicker by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun handleTokenSelection(token: String) {
        if (isSingleSelect && !singleSelectPrefKey.isNullOrBlank()) {
            prefs.edit().putString(singleSelectPrefKey, token).apply()
            safeReloadPreferences()
            activity.setResult(Activity.RESULT_OK, Intent().putExtra("SELECTED_TOKEN", token))
            onFinish()
        } else {
            if (selectedTokens.contains(token)) selectedTokens.remove(token)
            else selectedTokens.add(token)
        }
    }

    val wizardResultLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val generatedToken = activity.extractShortcutToken(result.data!!)
            if (generatedToken.isNotBlank()) {
                LightspeedActionRegistry.labelCache[generatedToken] =
                    com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(activity, generatedToken)
                if (!LightspeedActionRegistry.allTokens.contains(generatedToken)) {
                    LightspeedActionRegistry.allTokens.add(generatedToken)
                }
                handleTokenSelection(generatedToken)
            }
        }
    }

    LaunchedEffect(Unit) {
        LightspeedActionRegistry.ensureIndexed(activity)
    }

    val allTokens = LightspeedActionRegistry.allTokens
    val labelCache = LightspeedActionRegistry.labelCache

    val flatItemsList by remember(allTokens.size, labelCache.size, searchQuery, expandedSubsections, hudStyleVersion, isSplitMode) {
        derivedStateOf {
            val rawList = buildFlatItemsList(
                allTokens = allTokens,
                labelCache = labelCache,
                searchQuery = searchQuery,
                expandedSubsections = expandedSubsections,
                pinnedApps = pinnedApps,
                prefs = prefs,
                singleSelectPrefKey = singleSelectPrefKey,
                isHoldOrScrubGesture = isHoldGesture,
                context = activity
            )
            if (isSplitMode) filterForSplitMode(rawList) else rawList
        }
    }

    val letterIndices = remember(flatItemsList) {
        val map = mutableMapOf<Char, Int>()
        flatItemsList.forEachIndexed { index, item ->
            if (item is PickerRowItem.AppHeader && !item.isPinned) {
                val firstChar = item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                if (!map.containsKey(targetKey)) map[targetKey] = index
            }
        }
        map
    }

    val hasFallbackBackdrop = CentralCommandBackdropBlur.backdropBitmap != null
    val scrimAlpha = if (hasFallbackBackdrop) (activeSnapshot.dimAmount * 0.45f) else activeSnapshot.dimAmount

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable { onFinish() },
        contentAlignment = Alignment.Center
    ) {
        DeckGlassCardSurface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .clickable(enabled = false) {}
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                val rawName = prefs.getString("gear_set_${setId}_name", "") ?: ""
                val sName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
                    when(setId) {
                        "0" -> "POWER USER ANDROID"
                        "1" -> "MY APP STORES"
                        "2" -> "UTILITIES SECTOR"
                        "3" -> "ENTERTAINMENT DECK"
                        else -> "CUSTOM SET"
                    }
                } else rawName

                // 1. Header Row
                CockpitGearPickerHeaderRow(
                    isSingleSelect = isSingleSelect,
                    singleSelectTitle = singleSelectTitle,
                    ringIndex = ringIndex,
                    isSplitMode = isSplitMode,
                    profileName = sName,
                    selectedCount = selectedTokens.size,
                    dynamicSecondary = dynamicSecondary,
                    onCloseClick = onFinish
                )

                // 2. Search Field
                CockpitGearPickerSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it }
                )

                // 3. None / Clear action row (single-select only)
                if (isSingleSelect && searchQuery.isBlank() && !isSplitMode) {
                    CockpitGearPickerNoneRow(
                        isSelected = selectedTokens.isEmpty() || selectedTokens.contains("none"),
                        dynamicPrimary = dynamicPrimary,
                        onSelectNone = { handleTokenSelection("none") }
                    )
                }

                // 4. Sticky Advisory Warning Banner
                CockpitGearPickerWarningBanner()

                // 5. Selected Tokens Reorder Tray (multi-select only)
                if (!isSingleSelect && selectedTokens.isNotEmpty()) {
                    Text(
                        text = "RING COG ORDER (${selectedTokens.size} PAYLOADS) — TAP ◀ ▶ TO SHIFT:",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = dynamicSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    CockpitGearPickerSelectedTokensTray(
                        selectedTokens = selectedTokens,
                        dynamicPrimary = dynamicPrimary,
                        dynamicSecondary = dynamicSecondary,
                        context = activity
                    )
                }

                // 6. Main Items List & Fast Scroller
                var pickerHudVisible by remember { mutableStateOf(false) }
                var pickerHudLetter by remember { mutableStateOf("") }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        CockpitGearPickerItemList(
                            flatItemsList = flatItemsList,
                            listState = listState,
                            selectedTokens = selectedTokens,
                            dynamicPrimary = dynamicPrimary,
                            dynamicSecondary = dynamicSecondary,
                            context = activity,
                            prefs = prefs,
                            allSysCategories = allSysCategories,
                            expandedSubsections = expandedSubsections,
                            onExpandedSubsectionsChanged = { expandedSubsections = it },
                            onTokenSelected = { handleTokenSelection(it) },
                            onShowSystemAccordionPrefs = { showSystemAccordionPrefsDialog = true },
                            onPendingPinApp = { pendingPinApp = it },
                            onPendingDeepActivityRequest = {
                                pendingDeepActivitySubKey = it
                                showDeepActivityWarning = true
                            },
                            onLaunchWizard = { token ->
                                val pkg = token.substringAfter(";pkg=").substringBefore(";")
                                val act = token.substringAfter(";activity=").substringBefore(";")
                                val wizardIntent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                    setClassName(pkg, act)
                                }
                                wizardResultLauncher.launch(wizardIntent)
                            },
                            onSelectAttitudeBucket = { selectedAttitudeBucketForAppPicker = it },
                            onSelectActionMethod = { selectedActionMethodForAppPicker = it },
                            onStateMutated = { hudStyleVersion++ },
                            singleSelectPrefKey = singleSelectPrefKey,
                            modifier = Modifier.weight(1f)
                        )

                        CockpitGearPickerAlphabetScroller(
                            flatItemsList = flatItemsList,
                            letterIndices = letterIndices,
                            listState = listState,
                            coroutineScope = coroutineScope,
                            dynamicPrimary = dynamicPrimary,
                            dynamicSecondary = dynamicSecondary,
                            onHudStateChange = { show, letter ->
                                pickerHudVisible = show
                                pickerHudLetter = letter
                            }
                        )
                    }

                    // Dragging HUD Letter Overlay (positioned near top at eye level)
                    if (pickerHudVisible && pickerHudLetter.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 48.dp)
                                .size(80.dp)
                                .background(Color(0xFF1E1E28).copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                                .border(2.2.dp, dynamicPrimary, shape = RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = pickerHudLetter,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = LightspeedStyle.colors.textPrimary
                            )
                        }
                    }
                }

                // 7. Bottom Save/Cancel Bar (multi-select mode)
                if (!isSingleSelect) {
                    CockpitGearPickerBottomBar(
                        selectedCount = selectedTokens.size,
                        dynamicPrimary = dynamicPrimary,
                        onCancel = onFinish,
                        onSave = {
                            prefs.edit().putString("gear_set_${setId}_ring_${ringIndex}_packages", selectedTokens.joinToString(",")).apply()
                            onFinish()
                        }
                    )
                }
            }
        }

        // 8. Dialog Overlays Host
        CockpitGearPickerSubDialogs(
            context = activity,
            prefs = prefs,
            dynamicPrimary = dynamicPrimary,
            allSysCategories = allSysCategories,
            pinnedApps = pinnedApps,
            onPinnedAppsChanged = { pinnedApps = it },
            pendingPinApp = pendingPinApp,
            onDismissPinApp = { pendingPinApp = null },
            showDeepActivityWarning = showDeepActivityWarning,
            pendingDeepActivitySubKey = pendingDeepActivitySubKey,
            onDeepActivityWarningDismiss = {
                showDeepActivityWarning = false
                pendingDeepActivitySubKey = null
            },
            onDeepActivityWarningProceed = { doNotShowAgain ->
                if (doNotShowAgain) {
                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, true).apply()
                }
                val subKeyToExpand = pendingDeepActivitySubKey
                showDeepActivityWarning = false
                pendingDeepActivitySubKey = null
                if (subKeyToExpand != null) {
                    expandedSubsections = expandedSubsections + subKeyToExpand
                }
            },
            showSystemAccordionPrefsDialog = showSystemAccordionPrefsDialog,
            onDismissSystemAccordionPrefs = { showSystemAccordionPrefsDialog = false },
            systemAccordionMode = systemAccordionMode,
            onSystemAccordionModeChanged = { mode ->
                systemAccordionMode = mode
                prefs.edit().putString(LightspeedPreferences.KEY_SYSTEM_ACCORDION_MODE, mode).apply()
                if (mode == "expanded") {
                    expandedSubsections = expandedSubsections - allSysCategories.toSet()
                } else if (mode == "collapsed") {
                    expandedSubsections = expandedSubsections + allSysCategories.toSet()
                }
                showSystemAccordionPrefsDialog = false
            },
            selectedAttitudeBucketForAppPicker = selectedAttitudeBucketForAppPicker,
            onDismissAttitudeBucketPicker = { selectedAttitudeBucketForAppPicker = null },
            selectedActionMethodForAppPicker = selectedActionMethodForAppPicker,
            onDismissActionMethodPicker = { selectedActionMethodForAppPicker = null },
            labelCache = labelCache,
            onPreferenceMutated = {
                hudStyleVersion++
                safeReloadPreferences()
            }
        )
    }
}
