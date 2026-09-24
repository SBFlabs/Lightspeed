package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary

/**
 * Reusable dropdown menu selector with tactical Material 3 styling
 * and tactile 48dp touch targets.
 */
@Composable
fun PrefDropdownSelector(
    title: String,
    currentKey: String,
    options: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { isExpanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = options.firstOrNull { it.first == currentKey }?.second ?: currentKey,
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            DropdownMenu(
                expanded = isExpanded,
                onDismissRequest = { isExpanded = false },
                modifier = Modifier
                    .background(Color(0xF012141A))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                containerColor = Color(0xF012141A)
            ) {
                options.forEach { (key, label) ->
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(label) },
                        onClick = {
                            isExpanded = false
                            onSelected(key)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Master Accordion: Telemetry & Indicators Section.
 * Coordinates dual-channel routing, horizon rail geometry, colors,
 * micro-text typography ticker, and camera cutout calibration.
 */
@Composable
fun HudTelemetryIndicatorsSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isHorizonRailGeomExpanded: Boolean,
    onToggleHorizonRailGeom: () -> Unit,
    isHorizonRailColorExpanded: Boolean,
    onToggleHorizonRailColor: () -> Unit,
    isHorizonRailTextExpanded: Boolean,
    onToggleHorizonRailText: () -> Unit,
    onShowNotificationAccessDialog: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    CompactAccordionSection(
        title = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.TELEMETRY_AND_INDICATORS),
        icon = {
            Icon(
                imageVector = Icons.Outlined.Speed,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Dual-Channel Routing & Permissions
            HudTelemetryRoutingSection(
                context = context,
                prefs = prefs,
                isExpanded = isExpanded,
                onShowNotificationAccessDialog = onShowNotificationAccessDialog,
                onRefreshNeeded = onRefreshNeeded
            )

            // 2. Horizon Rail Geometry & Multi-Rail Stacking
            HorizonRailGeometrySection(
                context = context,
                prefs = prefs,
                isExpanded = isHorizonRailGeomExpanded,
                onToggle = onToggleHorizonRailGeom,
                onRefreshNeeded = onRefreshNeeded
            )

            // 3. Horizon Rail Dynamic Colors & Contrast
            HorizonRailStyleSection(
                prefs = prefs,
                isExpanded = isHorizonRailColorExpanded,
                onToggle = onToggleHorizonRailColor,
                onRefreshNeeded = onRefreshNeeded
            )

            // 4. Horizon Rail Micro-Text Ticker & Typography
            HorizonRailTickerSection(
                context = context,
                prefs = prefs,
                isExpanded = isHorizonRailTextExpanded,
                onToggle = onToggleHorizonRailText,
                onRefreshNeeded = onRefreshNeeded
            )

            // 5. Shared Hardware Cutout & Punch-Hole Calibration
            HardwareCutoutCalibrationSection(
                context = context,
                prefs = prefs,
                onRefreshNeeded = onRefreshNeeded
            )
        }
    }
}
