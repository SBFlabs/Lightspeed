package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ControlCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.bot.LightspeedBotContract
import com.sbf.lightspeed.system.bot.LightspeedBotTrackpadOverlay
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Cybernetic Trackpad Main Accordion for Deflector Tabs (< 100 lines).
 * Houses master enable toggle and 2 modular sub-accordions:
 * 1. Geometry & Calibration (with automatic live trigger zone preview)
 * 2. Cursor Physics & Timing
 */
@Composable
fun CyberneticTrackpadAccordion(
    isLeft: Boolean,
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    subBlueprintTarget: String? = null,
    onSubBlueprintToggle: (() -> Unit)? = null,
    isGeoExpanded: Boolean? = null,
    onGeoToggle: (() -> Unit)? = null,
    isPhysicsExpanded: Boolean? = null,
    onPhysicsToggle: (() -> Unit)? = null,
    onRefreshNeeded: () -> Unit,
    externalSubOrder: List<String>? = null,
    externalPinnedSection: String? = null,
    onSubOrderChange: ((List<String>) -> Unit)? = null,
    onPinSubSection: ((String) -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val flankKey = if (isLeft) "left" else "right"
    val secId = if (isLeft) "left_trackpad" else "trackpad"
    val subOrderKey = "pref_sub_order_$secId"
    val subPinnedKey = "pref_sub_pinned_$secId"
    val enabledKey = if (isLeft) LightspeedBotContract.KEY_BOT_TRACKPAD_LEFT_ENABLED else LightspeedBotContract.KEY_BOT_TRACKPAD_RIGHT_ENABLED

    var isEnabled by remember(prefs.getBoolean(enabledKey, false), enabledKey) {
        mutableStateOf(prefs.getBoolean(enabledKey, false))
    }
    var subOrderStr by remember(secId) {
        mutableStateOf(prefs.getString(subOrderKey, "geo,physics") ?: "geo,physics")
    }
    var localPinnedSection by remember(secId) {
        mutableStateOf(prefs.getString(subPinnedKey, "geo"))
    }
    val defaultSubOrder = remember { listOf("geo", "physics") }
    val localSubOrder = remember(subOrderStr) {
        subOrderStr.split(",").map { it.trim() }.filter { it in defaultSubOrder }.distinct().let { list ->
            list + (defaultSubOrder - list.toSet())
        }
    }
    val currentSubOrder = externalSubOrder ?: localSubOrder
    val pinnedSection = externalPinnedSection
        ?.takeIf { it.startsWith("${secId}_") }
        ?.removePrefix("${secId}_")
        ?: localPinnedSection

    CompactAccordionSection(
        title = lsString(R.string.sec_cybernetic_trackpad),
        isExpanded = isExpanded,
        onToggle = onToggle,
        onLongToggle = onSubBlueprintToggle,
        icon = {
            Icon(
                imageVector = Icons.Default.ControlCamera,
                contentDescription = null,
                tint = lsColors.accentInfo,
                modifier = Modifier.size(20.dp)
            )
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (subBlueprintTarget == secId && onSubBlueprintToggle != null) {
                BlueprintWireframeView(
                    tabTitle = lsString(R.string.sec_cybernetic_trackpad),
                    sectionIds = currentSubOrder,
                    pinnedSectionId = pinnedSection,
                    sectionTitles = mapOf(
                        "geo" to lsString(R.string.sec_geometry_calibration),
                        "physics" to lsString(R.string.sec_cursor_physics_timing)
                    ),
                    onMoveUp = { idx ->
                        if (idx > 0) {
                            val mutable = currentSubOrder.toMutableList()
                            val item = mutable.removeAt(idx)
                            mutable.add(idx - 1, item)
                            val newStr = mutable.joinToString(",")
                            subOrderStr = newStr
                            onSubOrderChange?.invoke(mutable)
                            prefs.edit().putString(subOrderKey, newStr).apply()
                        }
                    },
                    onMoveDown = { idx ->
                        if (idx < currentSubOrder.size - 1) {
                            val mutable = currentSubOrder.toMutableList()
                            val item = mutable.removeAt(idx)
                            mutable.add(idx + 1, item)
                            val newStr = mutable.joinToString(",")
                            subOrderStr = newStr
                            onSubOrderChange?.invoke(mutable)
                            prefs.edit().putString(subOrderKey, newStr).apply()
                        }
                    },
                    onPinSection = { id ->
                        localPinnedSection = id
                        prefs.edit().putString(subPinnedKey, id).apply()
                        onPinSubSection?.invoke("${secId}_$id")
                    },
                    onExitBlueprint = onSubBlueprintToggle
                )
            } else {
                // Master Flank Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(lsString(R.string.lab_trackpad_title), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                        Text(lsString(R.string.tp_desc), fontSize = 10.sp, color = lsColors.textSecondary)
                    }
                    DeckSwitch(
                        checked = isEnabled,
                        onCheckedChange = {
                            isEnabled = it
                            prefs.edit().putBoolean(enabledKey, it).apply()
                            LightspeedBotTrackpadOverlay.reload(context)
                            onRefreshNeeded()
                        }
                    )
                }

                if (isEnabled) {
                    HorizontalDivider(color = lsColors.borderSubtle.copy(alpha = 0.4f), thickness = 0.8.dp)

                    TrackpadRotatingTipBanner()

                    currentSubOrder.forEach { subKey ->
                        when (subKey) {
                            "geo" -> TrackpadGeometrySubSection(
                                isLeft = isLeft,
                                context = context,
                                prefs = prefs,
                                isParentExpanded = isExpanded,
                                isSubExpanded = isGeoExpanded,
                                onToggle = onGeoToggle
                            )
                            "physics" -> TrackpadPhysicsSubSection(
                                isLeft = isLeft,
                                prefs = prefs,
                                isSubExpanded = isPhysicsExpanded,
                                onToggle = onPhysicsToggle
                            )
                        }
                    }
                }
            }
        }
    }
}
