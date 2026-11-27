package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

@Composable
fun DeflectorBlueprintSection(
    tabTitle: String,
    currentOrder: List<String>,
    pinnedSection: String,
    sectionTitles: Map<String, String>,
    topSecId: String,
    bottomSecId: String,
    unifiedSecId: String,
    trackpadSecId: String? = null,
    currentSubTop: List<String>,
    currentSubBottom: List<String>,
    currentSubUnified: List<String>,
    currentSubTrackpad: List<String> = listOf("geo", "physics"),
    sectionOrderPrefKey: String,
    pinnedAccordionPrefKey: String,
    prefs: SharedPreferences,
    onOrderChange: (String) -> Unit,
    onPinnedChange: (String) -> Unit,
    onExitBlueprint: () -> Unit,
    onSubTopChange: (List<String>) -> Unit,
    onSubBottomChange: (List<String>) -> Unit,
    onSubUnifiedChange: (List<String>) -> Unit,
    onSubTrackpadChange: (List<String>) -> Unit = {}
) {
    BlueprintWireframeView(
        tabTitle = tabTitle,
        sectionIds = currentOrder,
        pinnedSectionId = pinnedSection,
        sectionTitles = sectionTitles,
        onMoveUp = { idx: Int ->
            if (idx > 0) {
                val mutable = currentOrder.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx - 1, item)
                val newStr = mutable.joinToString(",")
                onOrderChange(newStr)
                prefs.edit().putString(sectionOrderPrefKey, newStr).apply()
            }
        },
        onMoveDown = { idx: Int ->
            if (idx < currentOrder.size - 1) {
                val mutable = currentOrder.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx + 1, item)
                val newStr = mutable.joinToString(",")
                onOrderChange(newStr)
                prefs.edit().putString(sectionOrderPrefKey, newStr).apply()
            }
        },
        onPinSection = { secId: String ->
            onPinnedChange(secId)
            prefs.edit().putString(pinnedAccordionPrefKey, secId).apply()
        },
        onExitBlueprint = onExitBlueprint,
        fillViewport = true,
        subSections = buildMap {
            put(topSecId, currentSubTop)
            put(bottomSecId, currentSubBottom)
            put(unifiedSecId, currentSubUnified)
            if (trackpadSecId != null) {
                put(trackpadSecId, currentSubTrackpad)
            }
        },
        subSectionTitles = buildMap {
            put("geo", "Sensor Geometry")
            put("scrub", "Inward Scrubbing Control")
            put("gestures", "Gesture Actions & Macro Mappings")
            put("physics", lsString(R.string.sec_cursor_physics_timing))
            if (trackpadSecId != null) {
                put("${trackpadSecId}_geo", lsString(R.string.sec_geometry_calibration))
            }
        },
        onMoveSubUp = { secId, idx ->
            if (idx > 0) {
                val (list, key) = when (secId) {
                    topSecId -> currentSubTop to "pref_sub_order_${topSecId}"
                    bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}"
                    unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}"
                    trackpadSecId -> currentSubTrackpad to "pref_sub_order_${trackpadSecId}"
                    else -> return@BlueprintWireframeView
                }
                val mutable = list.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx - 1, item)
                when (secId) {
                    topSecId -> onSubTopChange(mutable)
                    bottomSecId -> onSubBottomChange(mutable)
                    unifiedSecId -> onSubUnifiedChange(mutable)
                    trackpadSecId -> onSubTrackpadChange(mutable)
                }
                prefs.edit().putString(key, mutable.joinToString(",")).apply()
            }
        },
        onMoveSubDown = { secId, idx ->
            val (list, key) = when (secId) {
                topSecId -> currentSubTop to "pref_sub_order_${topSecId}"
                bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}"
                unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}"
                trackpadSecId -> currentSubTrackpad to "pref_sub_order_${trackpadSecId}"
                else -> return@BlueprintWireframeView
            }
            if (idx < list.size - 1) {
                val mutable = list.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx + 1, item)
                when (secId) {
                    topSecId -> onSubTopChange(mutable)
                    bottomSecId -> onSubBottomChange(mutable)
                    unifiedSecId -> onSubUnifiedChange(mutable)
                    trackpadSecId -> onSubTrackpadChange(mutable)
                }
                prefs.edit().putString(key, mutable.joinToString(",")).apply()
            }
        }
    )
}
