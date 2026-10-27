package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Granular lockscreen overlay behavior selectors and phone call deflector suppressor.
 */
@Composable
fun SystemOverrideLockscreenSection(
    prefs: SharedPreferences
) {
    val lsColors = LightspeedStyle.colors
    var currentLockscreenMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_DEFLECTOR_MODE, "hide_deflectors") ?: "hide_deflectors")
    }
    var currentSensorMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_SENSOR_DECK_MODE, "hide") ?: "hide")
    }
    var currentRailMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_HORIZON_RAIL_MODE, "hide") ?: "hide")
    }
    var currentCallDeflectorMode by remember {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_CALL_DEFLECTOR_MODE, "keep_active") ?: "keep_active")
    }

    var isLockscreenDropdownOpen by remember { mutableStateOf(false) }
    var isSensorDropdownOpen by remember { mutableStateOf(false) }
    var isRailDropdownOpen by remember { mutableStateOf(false) }
    var isCallDeflectorDropdownOpen by remember { mutableStateOf(false) }

    val lockscreenOptions = listOf(
        "hide_deflectors" to "🙈 Hide Deflectors Only (Astrogation Core Active)",
        "keep_active" to "🛡️ Keep Deflectors Active on Lock Screen",
        "full_lockdown" to "🔒 Full Lockdown (Hide All Overlays)"
    )
    val binaryOptions = listOf(
        "hide" to "🙈 Hide on Lock Screen (Recommended)",
        "keep_active" to "🛡️ Keep Active on Lock Screen"
    )

    DeckCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "LOCK SCREEN OVERLAY BEHAVIOR",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Granularly configure which overlay workspace components remain active while on the lock screen.",
                fontSize = 11.sp,
                color = lsColors.textSecondary.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "ℹ️ Note: Astrogation Core pill remains active in Hide Deflectors mode so you can launch tools or gears from the lock screen.",
                fontSize = 10.5.sp,
                color = LightspeedTokens.StatusSky
            )

            // 1. Kinetic Deflectors Dropdown
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Kinetic Deflectors", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isLockscreenDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = lockscreenOptions.firstOrNull { it.first == currentLockscreenMode }?.second ?: "🙈 Hide Deflectors Only (Astrogation Core Active)",
                            color = lsColors.textPrimary,
                            fontSize = 11.5.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isLockscreenDropdownOpen,
                    onDismissRequest = { isLockscreenDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    lockscreenOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 44.dp),
                            text = { Text(label, fontSize = 12.5.sp) },
                            onClick = {
                                isLockscreenDropdownOpen = false
                                currentLockscreenMode = key
                                prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_DEFLECTOR_MODE, key).apply()
                                safeReloadPreferences()
                            }
                        )
                    }
                }
            }

            // 2. Sensor Touch Area Dropdown
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "HUD Strip Sensor Area", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isSensorDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = binaryOptions.firstOrNull { it.first == currentSensorMode }?.second ?: "🙈 Hide on Lock Screen (Recommended)",
                            color = lsColors.textPrimary,
                            fontSize = 11.5.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isSensorDropdownOpen,
                    onDismissRequest = { isSensorDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    binaryOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 44.dp),
                            text = { Text(label, fontSize = 12.5.sp) },
                            onClick = {
                                isSensorDropdownOpen = false
                                currentSensorMode = key
                                prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_SENSOR_DECK_MODE, key).apply()
                                safeReloadPreferences()
                            }
                        )
                    }
                }
            }

            // 3. Horizon Rail Dropdown
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Horizon Status Rail", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isRailDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = binaryOptions.firstOrNull { it.first == currentRailMode }?.second ?: "🙈 Hide on Lock Screen (Recommended)",
                            color = lsColors.textPrimary,
                            fontSize = 11.5.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isRailDropdownOpen,
                    onDismissRequest = { isRailDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    binaryOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 44.dp),
                            text = { Text(label, fontSize = 12.5.sp) },
                            onClick = {
                                isRailDropdownOpen = false
                                currentRailMode = key
                                prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_HORIZON_RAIL_MODE, key).apply()
                                safeReloadPreferences()
                            }
                        )
                    }
                }
            }

            // 4. Orbital Capsule Dropdown (Disabled / Grayed Out)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Orbital Capsule (Camera Cutout Widget)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textDisabled)
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🚫 Disabled in Experimental Labs",
                        color = lsColors.textDisabled,
                        fontSize = 11.5.sp
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = lsColors.textDisabled)
                }
            }
        }
    }

    val callDeflectorOptions = listOf(
        "keep_active" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE),
        "hide_deflectors" to LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CALL_DEFLECTOR_MODE_HIDE)
    )

    DeckCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "DURING PHONE CALLS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CALL_DEFLECTOR_MODE_TITLE),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isCallDeflectorDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = callDeflectorOptions.firstOrNull { it.first == currentCallDeflectorMode }?.second ?: LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE),
                            color = lsColors.textPrimary,
                            fontSize = 11.5.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isCallDeflectorDropdownOpen,
                    onDismissRequest = { isCallDeflectorDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    callDeflectorOptions.forEach { (key, label) ->
                        DropdownMenuItem(
                            modifier = Modifier.heightIn(min = 44.dp),
                            text = { Text(label, fontSize = 12.5.sp) },
                            onClick = {
                                isCallDeflectorDropdownOpen = false
                                currentCallDeflectorMode = key
                                prefs.edit().putString(LightspeedPreferences.KEY_CALL_DEFLECTOR_MODE, key).apply()
                                safeReloadPreferences()
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Status bar and sensor area are not affected.",
                fontSize = 10.5.sp,
                color = lsColors.textSecondary.copy(alpha = 0.75f)
            )
        }
    }
}
