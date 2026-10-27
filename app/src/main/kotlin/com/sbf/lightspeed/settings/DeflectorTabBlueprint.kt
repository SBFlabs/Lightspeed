package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import androidx.compose.runtime.Composable

@Composable
fun DeflectorBlueprintSection(
    tabTitle: String,
    currentOrder: List<String>,
    pinnedSection: String,
    sectionTitles: Map<String, String>,
    topSecId: String,
    bottomSecId: String,
    unifiedSecId: String,
    currentSubTop: List<String>,
    currentSubBottom: List<String>,
    currentSubUnified: List<String>,
    sectionOrderPrefKey: String,
    pinnedAccordionPrefKey: String,
    prefs: SharedPreferences,
    onOrderChange: (String) -> Unit,
    onPinnedChange: (String) -> Unit,
    onExitBlueprint: () -> Unit,
    onSubTopChange: (List<String>) -> Unit,
    onSubBottomChange: (List<String>) -> Unit,
    onSubUnifiedChange: (List<String>) -> Unit
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
        subSections = mapOf(
            topSecId to currentSubTop,
            bottomSecId to currentSubBottom,
            unifiedSecId to currentSubUnified
        ),
        subSectionTitles = mapOf(
            "geo" to "Sensor Geometry",
            "scrub" to "Inward Scrubbing Control",
            "gestures" to "Gesture Actions & Macro Mappings"
        ),
        onMoveSubUp = { secId, idx ->
            if (idx > 0) {
                val (list, key) = when (secId) {
                    topSecId -> currentSubTop to "pref_sub_order_${topSecId}_2"
                    bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}_2"
                    unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}_2"
                    else -> return@BlueprintWireframeView
                }
                val mutable = list.toMutableList()
                val item = mutable.removeAt(idx)
                mutable.add(idx - 1, item)
                when (secId) {
                    topSecId -> onSubTopChange(mutable)
                    bottomSecId -> onSubBottomChange(mutable)
                    unifiedSecId -> onSubUnifiedChange(mutable)
                }
                prefs.edit().putString(key, mutable.joinToString(",")).apply()
            }
        },
        onMoveSubDown = { secId, idx ->
            val (list, key) = when (secId) {
                topSecId -> currentSubTop to "pref_sub_order_${topSecId}_2"
                bottomSecId -> currentSubBottom to "pref_sub_order_${bottomSecId}_2"
                unifiedSecId -> currentSubUnified to "pref_sub_order_${unifiedSecId}_2"
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
                }
                prefs.edit().putString(key, mutable.joinToString(",")).apply()
            }
        }
    )
}
