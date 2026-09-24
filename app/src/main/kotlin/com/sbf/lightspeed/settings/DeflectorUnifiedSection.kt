package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DeflectorUnifiedSection(
    isLeft: Boolean,
    context: Context,
    prefs: SharedPreferences,
    dynamicActionTokens: List<String>,
    tokenLabelCache: Map<String, String>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subBlueprintTarget: String?,
    onSubBlueprintToggle: () -> Unit,
    isGeoExpanded: Boolean,
    onGeoToggle: () -> Unit,
    isScrubExpanded: Boolean,
    onScrubToggle: () -> Unit,
    isGesturesExpanded: Boolean,
    onGesturesToggle: () -> Unit,
    customVectors: List<Pair<String, Pair<String, ArrowDirection>>>,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onMooringConfirm: (MooringConfirmData) -> Unit
) {
    val macroPrefix = if (isLeft) "LEFT_" else ""
    val sidebarPrefix = if (isLeft) "pref_sidebar_left_" else "pref_sidebar_"
    val secUnifiedKey = if (isLeft) "left_unified" else "unified"
    val subOrderKey = if (isLeft) LightspeedPreferences.KEY_SUB_ORDER_LEFT_UNIFIED else LightspeedPreferences.KEY_SUB_ORDER_UNIFIED
    val subPinnedKey = if (isLeft) LightspeedPreferences.KEY_SUB_PINNED_LEFT_UNIFIED else LightspeedPreferences.KEY_SUB_PINNED_UNIFIED

    var isScrubLinked by remember {
        mutableStateOf(LightspeedPreferences.isScrubRegionsLinked(context, isLeft = isLeft))
    }
    var gestureTiedState by remember {
        mutableStateOf(
            customVectors.flatMap {
                listOf(
                    it.first to LightspeedPreferences.isGestureUnified(context, isLeft = isLeft, it.first),
                    "${it.first}_HOLD" to LightspeedPreferences.isGestureUnified(context, isLeft = isLeft, "${it.first}_HOLD")
                )
            }.toMap()
        )
    }

    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.UNIFIED_DEFLECTORS),
        icon = {
            Icon(
                imageVector = Icons.Outlined.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle,
        onLongToggle = onSubBlueprintToggle
    ) {
        var unifiedSubOrderStr by remember { mutableStateOf(prefs.getString(subOrderKey, "geo,scrub,gestures")!!) }
        var unifiedPinnedSection by remember { mutableStateOf(prefs.getString(subPinnedKey, "gestures")) }
        val defaultUnifiedSubOrder = listOf("geo", "scrub", "gestures")
        val currentUnifiedSubOrder = unifiedSubOrderStr.split(",").map { it.trim() }.filter { it in defaultUnifiedSubOrder }.distinct().let { list ->
            list + (defaultUnifiedSubOrder - list.toSet())
        }

        if (subBlueprintTarget == secUnifiedKey) {
            BlueprintWireframeView(
                tabTitle = if (isLeft) "Left Flank Vector Zones" else "Right Flank Vector Zones",
                sectionIds = currentUnifiedSubOrder,
                pinnedSectionId = unifiedPinnedSection,
                sectionTitles = mapOf(
                    "geo" to "Sensor Geometry",
                    "scrub" to "Separate Deflector Controls",
                    "gestures" to "Unified Gesture Matrix"
                ),
                onMoveUp = { idx ->
                    if (idx > 0) {
                        val mutable = currentUnifiedSubOrder.toMutableList()
                        val item = mutable.removeAt(idx)
                        mutable.add(idx - 1, item)
                        val newStr = mutable.joinToString(",")
                        unifiedSubOrderStr = newStr
                        prefs.edit().putString(subOrderKey, newStr).apply()
                    }
                },
                onMoveDown = { idx ->
                    if (idx < currentUnifiedSubOrder.size - 1) {
                        val mutable = currentUnifiedSubOrder.toMutableList()
                        val item = mutable.removeAt(idx)
                        mutable.add(idx + 1, item)
                        val newStr = mutable.joinToString(",")
                        unifiedSubOrderStr = newStr
                        prefs.edit().putString(subOrderKey, newStr).apply()
                    }
                },
                onPinSection = { id ->
                    unifiedPinnedSection = id
                    prefs.edit().putString(subPinnedKey, id).apply()
                },
                onExitBlueprint = onSubBlueprintToggle
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                currentUnifiedSubOrder.forEach { subKey ->
                    when (subKey) {
                        "geo" -> {
                            CollapsibleSubSection(
                                title = "Sensor Geometry",
                                subtitle = "Independent height, touch reach & stealth glow",
                                isExpanded = isGeoExpanded,
                                onToggle = onGeoToggle
                            ) {
                                val configuration = androidx.compose.ui.platform.LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                val landscapeMode = prefs.getString(LightspeedPreferences.KEY_DEFLECTOR_LANDSCAPE_MODE, LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM) ?: LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM
                                val isCustomLandscape = landscapeMode == LightspeedPreferences.DEFLECTOR_LANDSCAPE_MODE_CUSTOM

                                Text("UPPER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                val defaultTopHeight = 220
                                val defaultBottomHeight = 250

                                if (isLandscape && isCustomLandscape) {
                                    val defaultLandscapeTopH = (prefs.getInt("${sidebarPrefix}top_height", defaultTopHeight) * 0.45f).toInt().coerceAtLeast(30)
                                    val defaultLandscapeBottomH = (prefs.getInt("${sidebarPrefix}bottom_height", defaultBottomHeight) * 0.45f).toInt().coerceAtLeast(30)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_height_landscape", "", "Upper Deflector Span (Height) [Landscape]", 30, 600, 10, defaultLandscapeTopH)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_touch_width", "", "Upper Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_height_landscape", "", "Lower Deflector Span (Height) [Landscape]", 30, 600, 10, defaultLandscapeBottomH)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_touch_width", "", "Lower Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                } else {
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_height", "", "Upper Deflector Span (Height)", 50, 600, 10, defaultTopHeight)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_touch_width", "", "Upper Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}top_transparency", "", "Upper Stealth Idle Glow", 0, 100, 5, 0)

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("LOWER VECTOR GEOMETRY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_height", "", "Lower Deflector Span (Height)", 50, 600, 10, defaultBottomHeight)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_touch_width", "", "Lower Touch Vector Reach", 0, 100, 1, 10)
                                    PrefDottedSliderRow(context, prefs, "${sidebarPrefix}bottom_transparency", "", "Lower Stealth Idle Glow", 0, 100, 5, 0)
                                }
                            }
                        }
                        "scrub" -> {
                            val sepCount = (if (!isScrubLinked) 1 else 0) + gestureTiedState.values.count { !it }
                            CollapsibleSubSection(
                                title = "Separate Deflector Controls",
                                subtitle = if (sepCount == 0) "All gestures tied in Unified Matrix" else "$sepCount unlinked dual control vector(s)",
                                isExpanded = isScrubExpanded,
                                onToggle = onScrubToggle
                            ) {
                                if (sepCount == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "All deflector gestures are currently linked across the full deflector. Tap the rope on any gesture to split it into independent Upper & Lower controls.",
                                            fontSize = 12.sp,
                                            color = Color.LightGray.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    if (!isScrubLinked) {
                                        val onTieScrubber = {
                                            onMooringConfirm(
                                                MooringConfirmData(
                                                    title = "Link Inward Sweep?",
                                                    message = "Unify Inward Sweep across the full flank in lockstep?",
                                                    confirmLabel = "Link",
                                                    isSever = false,
                                                    onConfirm = {
                                                        isScrubLinked = true
                                                        LightspeedPreferences.setScrubRegionsLinked(context, isLeft = isLeft, true)
                                                        if (!prefs.contains("pref_macro_action_${macroPrefix}TOP_SCRUBBING")) {
                                                            val topVal = prefs.getString("pref_macro_action_${macroPrefix}TOP_SCRUBBING", "system:brightness") ?: "system:brightness"
                                                            prefs.edit().putString("pref_macro_action_${macroPrefix}BOTTOM_SCRUBBING", topVal).apply()
                                                        }
                                                        if (!isGesturesExpanded) {
                                                            onGesturesToggle()
                                                            prefs.edit().putBoolean("pref_sub_gestures_${secUnifiedKey}", true).apply()
                                                        }
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                        coroutineScope.launch {
                                                            delay(60)
                                                            try { listState.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                        }
                                                    }
                                                )
                                            )
                                        }
                                        SeveredMooringPairCard(
                                            context = context,
                                            onTie = { onTieScrubber() },
                                            onMirror = {
                                                mirrorGestureToOppositeFlank(context, prefs, isLeft, "SCRUBBING", isHold = false, isCurrentlyTied = false)
                                            }
                                        ) {
                                            GestureMappingRow(
                                                context = context,
                                                prefs = prefs,
                                                direction = ArrowDirection.SCRUB,
                                                isHold = false,
                                                keyResName = "pref_macro_action_${macroPrefix}TOP_SCRUBBING",
                                                defaultTitle = "Upper · Inward Sweep",
                                                options = listOf("none", "system:brightness", "system:volume", "system:screen_timeout"),
                                                labelCache = tokenLabelCache
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            GestureMappingRow(
                                                context = context,
                                                prefs = prefs,
                                                direction = ArrowDirection.SCRUB,
                                                isHold = false,
                                                keyResName = "pref_macro_action_${macroPrefix}BOTTOM_SCRUBBING",
                                                defaultTitle = "Lower · Inward Sweep",
                                                options = listOf("none", "system:volume", "system:brightness", "system:screen_timeout"),
                                                labelCache = tokenLabelCache
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, "", "Long Swipe Distance Cutoff [Portrait]", 40, 240, 5, 90)
                                            Spacer(modifier = Modifier.height(4.dp))
                                            PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, "", "Long Swipe Distance Cutoff [Landscape]", 40, 320, 10, 130)
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    customVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        val isStandardTied = gestureTiedState[vectorKey] ?: true
                                        val isHoldTied = gestureTiedState["${vectorKey}_HOLD"] ?: true

                                        if (!isStandardTied) {
                                            val onTieVector = {
                                                onMooringConfirm(
                                                    MooringConfirmData(
                                                        title = "Link Upper & Lower?",
                                                        message = "Unify \"$vectorTitle\" across the full deflector in lockstep?",
                                                        confirmLabel = "Link",
                                                        isSever = false,
                                                        onConfirm = {
                                                            gestureTiedState = gestureTiedState + (vectorKey to true)
                                                            LightspeedPreferences.setGestureUnified(context, isLeft = isLeft, vectorKey, true)
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}UNIFIED_${vectorKey}")) {
                                                                val topVal = prefs.getString("pref_macro_action_${macroPrefix}TOP_${vectorKey}", "none") ?: "none"
                                                                prefs.edit().putString("pref_macro_action_${macroPrefix}UNIFIED_${vectorKey}", topVal).apply()
                                                            }
                                                            if (!isGesturesExpanded) {
                                                                onGesturesToggle()
                                                                prefs.edit().putBoolean("pref_sub_gestures_${secUnifiedKey}", true).apply()
                                                            }
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            coroutineScope.launch {
                                                                delay(60)
                                                                try { listState.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            SeveredMooringPairCard(
                                                context = context,
                                                onTie = { onTieVector() },
                                                onMirror = {
                                                    mirrorGestureToOppositeFlank(context, prefs, isLeft, vectorKey, isHold = false, isCurrentlyTied = false)
                                                }
                                            ) {
                                                GestureMappingRow(
                                                    context = context,
                                                    prefs = prefs,
                                                    direction = arrowEnum,
                                                    isHold = false,
                                                    keyResName = "pref_macro_action_${macroPrefix}TOP_${vectorKey}",
                                                    defaultTitle = "Upper · $vectorTitle",
                                                    options = dynamicActionTokens,
                                                    labelCache = tokenLabelCache
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                GestureMappingRow(
                                                    context = context,
                                                    prefs = prefs,
                                                    direction = arrowEnum,
                                                    isHold = false,
                                                    keyResName = "pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}",
                                                    defaultTitle = "Lower · $vectorTitle",
                                                    options = dynamicActionTokens,
                                                    labelCache = tokenLabelCache
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        if (!isHoldTied) {
                                            val holdKey = "${vectorKey}_HOLD"
                                            val onTieHoldVector = {
                                                onMooringConfirm(
                                                    MooringConfirmData(
                                                        title = "Link Upper & Lower?",
                                                        message = "Unify \"$vectorTitle + Hold\" across the full deflector in lockstep?",
                                                        confirmLabel = "Link",
                                                        isSever = false,
                                                        onConfirm = {
                                                            gestureTiedState = gestureTiedState + (holdKey to true)
                                                            LightspeedPreferences.setGestureUnified(context, isLeft = isLeft, holdKey, true)
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}UNIFIED_${holdKey}")) {
                                                                val topVal = prefs.getString("pref_macro_action_${macroPrefix}TOP_${vectorKey}_HOLD", "none") ?: "none"
                                                                prefs.edit().putString("pref_macro_action_${macroPrefix}UNIFIED_${holdKey}", topVal).apply()
                                                            }
                                                            if (!isGesturesExpanded) {
                                                                onGesturesToggle()
                                                                prefs.edit().putBoolean("pref_sub_gestures_${secUnifiedKey}", true).apply()
                                                            }
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            coroutineScope.launch {
                                                                delay(60)
                                                                try { listState.animateScrollToItem(index = 2, scrollOffset = 0) } catch (_: Exception) {}
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            SeveredMooringPairCard(
                                                context = context,
                                                onTie = { onTieHoldVector() },
                                                onMirror = {
                                                    mirrorGestureToOppositeFlank(context, prefs, isLeft, vectorKey, isHold = true, isCurrentlyTied = false)
                                                }
                                            ) {
                                                GestureMappingRow(
                                                    context = context,
                                                    prefs = prefs,
                                                    direction = arrowEnum,
                                                    isHold = true,
                                                    keyResName = "pref_macro_action_${macroPrefix}TOP_${vectorKey}_HOLD",
                                                    defaultTitle = "Upper · $vectorTitle + Hold",
                                                    options = dynamicActionTokens,
                                                    labelCache = tokenLabelCache
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                GestureMappingRow(
                                                    context = context,
                                                    prefs = prefs,
                                                    direction = arrowEnum,
                                                    isHold = true,
                                                    keyResName = "pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}_HOLD",
                                                    defaultTitle = "Lower · $vectorTitle + Hold",
                                                    options = dynamicActionTokens,
                                                    labelCache = tokenLabelCache
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                        "gestures" -> {
                            val unifiedCount = (if (isScrubLinked) 1 else 0) + gestureTiedState.values.count { it }
                            CollapsibleSubSection(
                                title = "Unified Gesture Matrix",
                                subtitle = if (unifiedCount == 0) "All gestures unlinked into Separate Controls" else "$unifiedCount unified full-flank vector(s)",
                                isExpanded = isGesturesExpanded,
                                onToggle = onGesturesToggle
                            ) {
                                if (unifiedCount == 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "All deflector gestures have been split into Separate Controls. Tap the rope on any gesture to link Upper & Lower back together.",
                                            fontSize = 12.sp,
                                            color = Color.LightGray.copy(alpha = 0.8f),
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                } else {
                                    if (isScrubLinked) {
                                        val onSeverScrubber = {
                                            onMooringConfirm(
                                                MooringConfirmData(
                                                    title = "Split Inward Sweep?",
                                                    message = "Split Inward Sweep into independent Upper and Lower sector controls?",
                                                    confirmLabel = "Split",
                                                    isSever = true,
                                                    onConfirm = {
                                                        isScrubLinked = false
                                                        LightspeedPreferences.setScrubRegionsLinked(context, isLeft = isLeft, false)
                                                        val topVal = prefs.getString("pref_macro_action_${macroPrefix}TOP_SCRUBBING", "system:brightness") ?: "system:brightness"
                                                        if (!prefs.contains("pref_macro_action_${macroPrefix}BOTTOM_SCRUBBING")) {
                                                            prefs.edit().putString("pref_macro_action_${macroPrefix}BOTTOM_SCRUBBING", topVal).apply()
                                                        }
                                                        if (!isScrubExpanded) {
                                                            onScrubToggle()
                                                            prefs.edit().putBoolean("pref_sub_scrub_${secUnifiedKey}", true).apply()
                                                        }
                                                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                        coroutineScope.launch {
                                                            delay(60)
                                                            try { listState.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                        }
                                                    }
                                                )
                                            )
                                        }
                                        GestureMappingRow(
                                            context = context,
                                            prefs = prefs,
                                            direction = ArrowDirection.SCRUB,
                                            isHold = false,
                                            keyResName = "pref_macro_action_${macroPrefix}TOP_SCRUBBING",
                                            defaultTitle = "Linked Inward Sweep (Scrubbing)",
                                            options = listOf("none", "system:brightness", "system:volume", "system:screen_timeout"),
                                            labelCache = tokenLabelCache,
                                            showMooringRope = true,
                                            isMooringTied = true,
                                            onToggleMooring = { onSeverScrubber() },
                                            onMirrorMooring = {
                                                mirrorGestureToOppositeFlank(context, prefs, isLeft, "SCRUBBING", isHold = false, isCurrentlyTied = true)
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_PORTRAIT, "", "Long Swipe Distance Cutoff [Portrait]", 40, 240, 5, 90)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        PrefDottedSliderRow(context, prefs, LightspeedPreferences.KEY_DEFLECTOR_LONG_SWIPE_THRESHOLD_LANDSCAPE, "", "Long Swipe Distance Cutoff [Landscape]", 40, 320, 10, 130)
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    customVectors.forEach { (vectorKey, pairInfo) ->
                                        val (vectorTitle, arrowEnum) = pairInfo
                                        val isStandardTied = gestureTiedState[vectorKey] ?: true
                                        val isHoldTied = gestureTiedState["${vectorKey}_HOLD"] ?: true

                                        if (isStandardTied) {
                                            val onSeverVector = {
                                                onMooringConfirm(
                                                    MooringConfirmData(
                                                        title = "Split into Upper & Lower?",
                                                        message = "Split \"$vectorTitle\" into independent Upper and Lower sector controls?",
                                                        confirmLabel = "Split",
                                                        isSever = true,
                                                        onConfirm = {
                                                            gestureTiedState = gestureTiedState + (vectorKey to false)
                                                            LightspeedPreferences.setGestureUnified(context, isLeft = isLeft, vectorKey, false)
                                                            val currentVal = prefs.getString("pref_macro_action_${macroPrefix}UNIFIED_${vectorKey}", "none") ?: "none"
                                                            val editor = prefs.edit()
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}TOP_${vectorKey}")) {
                                                                editor.putString("pref_macro_action_${macroPrefix}TOP_${vectorKey}", currentVal)
                                                            }
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}")) {
                                                                editor.putString("pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}", currentVal)
                                                            }
                                                            editor.apply()
                                                            if (!isScrubExpanded) {
                                                                onScrubToggle()
                                                                prefs.edit().putBoolean("pref_sub_scrub_${secUnifiedKey}", true).apply()
                                                            }
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            coroutineScope.launch {
                                                                delay(60)
                                                                try { listState.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            GestureMappingRow(
                                                context = context,
                                                prefs = prefs,
                                                direction = arrowEnum,
                                                isHold = false,
                                                keyResName = "pref_macro_action_${macroPrefix}UNIFIED_${vectorKey}",
                                                defaultTitle = vectorTitle,
                                                options = dynamicActionTokens,
                                                labelCache = tokenLabelCache,
                                                showMooringRope = true,
                                                isMooringTied = true,
                                                onToggleMooring = { onSeverVector() },
                                                onMirrorMooring = {
                                                    mirrorGestureToOppositeFlank(context, prefs, isLeft, vectorKey, isHold = false, isCurrentlyTied = true)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        if (isHoldTied) {
                                            val holdKey = "${vectorKey}_HOLD"
                                            val onSeverHoldVector = {
                                                onMooringConfirm(
                                                    MooringConfirmData(
                                                        title = "Split into Upper & Lower?",
                                                        message = "Split \"$vectorTitle + Hold\" into independent Upper and Lower sector controls?",
                                                        confirmLabel = "Split",
                                                        isSever = true,
                                                        onConfirm = {
                                                            gestureTiedState = gestureTiedState + (holdKey to false)
                                                            LightspeedPreferences.setGestureUnified(context, isLeft = isLeft, holdKey, false)
                                                            val currentVal = prefs.getString("pref_macro_action_${macroPrefix}UNIFIED_${holdKey}", "none") ?: "none"
                                                            val editor = prefs.edit()
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}TOP_${vectorKey}_HOLD")) {
                                                                editor.putString("pref_macro_action_${macroPrefix}TOP_${vectorKey}_HOLD", currentVal)
                                                            }
                                                            if (!prefs.contains("pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}_HOLD")) {
                                                                editor.putString("pref_macro_action_${macroPrefix}BOTTOM_${vectorKey}_HOLD", currentVal)
                                                            }
                                                            editor.apply()
                                                            if (!isScrubExpanded) {
                                                                onScrubToggle()
                                                                prefs.edit().putBoolean("pref_sub_scrub_${secUnifiedKey}", true).apply()
                                                            }
                                                            try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                            coroutineScope.launch {
                                                                delay(60)
                                                                try { listState.animateScrollToItem(index = 2, scrollOffset = 250) } catch (_: Exception) {}
                                                            }
                                                        }
                                                    )
                                                )
                                            }
                                            GestureMappingRow(
                                                context = context,
                                                prefs = prefs,
                                                direction = arrowEnum,
                                                isHold = true,
                                                keyResName = "pref_macro_action_${macroPrefix}UNIFIED_${vectorKey}_HOLD",
                                                defaultTitle = "$vectorTitle + Hold Modifier",
                                                options = dynamicActionTokens,
                                                labelCache = tokenLabelCache,
                                                showMooringRope = true,
                                                isMooringTied = true,
                                                onToggleMooring = { onSeverHoldVector() },
                                                onMirrorMooring = {
                                                    mirrorGestureToOppositeFlank(context, prefs, isLeft, vectorKey, isHold = true, isCurrentlyTied = true)
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
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

private fun mapVectorToOppositeFlank(vectorKey: String, fromLeft: Boolean): String {
    return if (fromLeft) {
        when (vectorKey) {
            "SWIPE_RIGHT" -> "SWIPE_LEFT"
            "SWIPE_RIGHT_BACK" -> "SWIPE_LEFT_BACK"
            "SWIPE_UP_RIGHT" -> "SWIPE_UP_LEFT"
            "SWIPE_DOWN_RIGHT" -> "SWIPE_DOWN_LEFT"
            "SWIPE_RIGHT_UP" -> "SWIPE_LEFT_UP"
            else -> vectorKey
        }
    } else {
        when (vectorKey) {
            "SWIPE_LEFT" -> "SWIPE_RIGHT"
            "SWIPE_LEFT_BACK" -> "SWIPE_RIGHT_BACK"
            "SWIPE_UP_LEFT" -> "SWIPE_UP_RIGHT"
            "SWIPE_DOWN_LEFT" -> "SWIPE_DOWN_RIGHT"
            "SWIPE_LEFT_UP" -> "SWIPE_RIGHT_UP"
            else -> vectorKey
        }
    }
}

private fun mirrorGestureToOppositeFlank(
    context: Context,
    prefs: SharedPreferences,
    isLeft: Boolean,
    vectorKey: String,
    isHold: Boolean,
    isCurrentlyTied: Boolean
) {
    val sourcePrefix = if (isLeft) "LEFT_" else ""
    val targetPrefix = if (isLeft) "" else "LEFT_"
    val targetIsLeft = !isLeft
    val editor = prefs.edit()

    if (vectorKey == "SCRUBBING") {
        LightspeedPreferences.setScrubRegionsLinked(context, isLeft = targetIsLeft, isCurrentlyTied)
        if (isCurrentlyTied) {
            val act = prefs.getString("pref_macro_action_${sourcePrefix}TOP_SCRUBBING", "system:brightness") ?: "system:brightness"
            editor.putString("pref_macro_action_${targetPrefix}TOP_SCRUBBING", act)
            editor.putString("pref_macro_action_${targetPrefix}BOTTOM_SCRUBBING", act)
        } else {
            val topAct = prefs.getString("pref_macro_action_${sourcePrefix}TOP_SCRUBBING", "system:brightness") ?: "system:brightness"
            val btmAct = prefs.getString("pref_macro_action_${sourcePrefix}BOTTOM_SCRUBBING", "system:volume") ?: "system:volume"
            editor.putString("pref_macro_action_${targetPrefix}TOP_SCRUBBING", topAct)
            editor.putString("pref_macro_action_${targetPrefix}BOTTOM_SCRUBBING", btmAct)
        }
    } else {
        val targetVectorKey = mapVectorToOppositeFlank(vectorKey, isLeft)
        val fullSourceKey = if (isHold) "${vectorKey}_HOLD" else vectorKey
        val fullTargetKey = if (isHold) "${targetVectorKey}_HOLD" else targetVectorKey

        LightspeedPreferences.setGestureUnified(context, isLeft = targetIsLeft, fullTargetKey, isCurrentlyTied)

        if (isCurrentlyTied) {
            val act = prefs.getString("pref_macro_action_${sourcePrefix}UNIFIED_${fullSourceKey}", "none") ?: "none"
            editor.putString("pref_macro_action_${targetPrefix}UNIFIED_${fullTargetKey}", act)
        } else {
            val topAct = prefs.getString("pref_macro_action_${sourcePrefix}TOP_${fullSourceKey}", "none") ?: "none"
            val btmAct = prefs.getString("pref_macro_action_${sourcePrefix}BOTTOM_${fullSourceKey}", "none") ?: "none"
            editor.putString("pref_macro_action_${targetPrefix}TOP_${fullTargetKey}", topAct)
            editor.putString("pref_macro_action_${targetPrefix}BOTTOM_${fullTargetKey}", btmAct)
        }
    }
    editor.apply()
    try {
        LightspeedAccessibilityService.instance?.reloadPreferences()
    } catch (_: Exception) {}

    val targetFlankName = if (targetIsLeft) "Left Deflector" else "Right Deflector"
    val stateText = if (isCurrentlyTied) "Unified" else "Split"
    Toast.makeText(context, "★ Golden Rope: Mirrored $stateText to $targetFlankName", Toast.LENGTH_SHORT).show()
}
