package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
        "hide_deflectors" to lsString(R.string.sys_ls_hide_def),
        "keep_active" to lsString(R.string.sys_ls_keep_def),
        "full_lockdown" to lsString(R.string.sys_ls_lockdown)
    )
    val binaryOptions = listOf(
        "hide" to lsString(R.string.sys_ls_hide_rec),
        "keep_active" to lsString(R.string.sys_ls_keep_on)
    )

    DeckCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = lsString(R.string.sys_ls_hdr),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = lsString(R.string.sys_ls_desc),
                fontSize = 11.sp,
                color = lsColors.textSecondary.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = LightspeedTokens.StatusSky,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = lsString(R.string.sys_ls_core_note),
                    fontSize = 10.5.sp,
                    color = LightspeedTokens.StatusSky
                )
            }

            // 1. Kinetic Deflectors Dropdown
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = lsString(R.string.sys_ls_deflectors), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isLockscreenDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val icon = when (currentLockscreenMode) {
                                "keep_active" -> Icons.Default.Visibility
                                "full_lockdown" -> Icons.Default.Lock
                                else -> Icons.Default.VisibilityOff
                            }
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = lockscreenOptions.firstOrNull { it.first == currentLockscreenMode }?.second ?: lsString(R.string.sys_ls_hide_def),
                                color = lsColors.textPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isLockscreenDropdownOpen,
                    onDismissRequest = { isLockscreenDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    lockscreenOptions.forEach { (key, label) ->
                        val menuIcon = when (key) {
                            "keep_active" -> Icons.Default.Visibility
                            "full_lockdown" -> Icons.Default.Lock
                            else -> Icons.Default.VisibilityOff
                        }
                        DropdownMenuItem(
                            modifier = Modifier.height(42.dp),
                            leadingIcon = { Icon(menuIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                            text = { Text(label, fontSize = 12.sp, maxLines = 1) },
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
            Text(text = lsString(R.string.sys_ls_sensor), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isSensorDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val icon = if (currentSensorMode == "keep_active") Icons.Default.Visibility else Icons.Default.VisibilityOff
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = binaryOptions.firstOrNull { it.first == currentSensorMode }?.second ?: lsString(R.string.sys_ls_hide_rec),
                                color = lsColors.textPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isSensorDropdownOpen,
                    onDismissRequest = { isSensorDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    binaryOptions.forEach { (key, label) ->
                        val menuIcon = if (key == "keep_active") Icons.Default.Visibility else Icons.Default.VisibilityOff
                        DropdownMenuItem(
                            modifier = Modifier.height(42.dp),
                            leadingIcon = { Icon(menuIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                            text = { Text(label, fontSize = 12.sp, maxLines = 1) },
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
            Text(text = lsString(R.string.sys_ls_rail), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Spacer(modifier = Modifier.height(2.dp))
            DeckDropdownAnchorBox { anchorWidth ->
                OutlinedButton(
                    onClick = { isRailDropdownOpen = true },
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val icon = if (currentRailMode == "keep_active") Icons.Default.Visibility else Icons.Default.VisibilityOff
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = binaryOptions.firstOrNull { it.first == currentRailMode }?.second ?: lsString(R.string.sys_ls_hide_rec),
                                color = lsColors.textPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isRailDropdownOpen,
                    onDismissRequest = { isRailDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    binaryOptions.forEach { (key, label) ->
                        val menuIcon = if (key == "keep_active") Icons.Default.Visibility else Icons.Default.VisibilityOff
                        DropdownMenuItem(
                            modifier = Modifier.height(42.dp),
                            leadingIcon = { Icon(menuIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                            text = { Text(label, fontSize = 12.sp, maxLines = 1) },
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
            Text(text = lsString(R.string.sys_ls_capsule), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textDisabled)
            Spacer(modifier = Modifier.height(2.dp))
            OutlinedButton(
                onClick = { },
                enabled = false,
                modifier = Modifier.fillMaxWidth().height(42.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = lsColors.textDisabled, modifier = Modifier.size(15.dp))
                        Text(
                            text = lsString(R.string.sys_ls_disabled_labs),
                            color = lsColors.textDisabled,
                            fontSize = 11.5.sp,
                            maxLines = 1
                        )
                    }
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
                text = lsString(R.string.sys_calls_hdr),
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
                    modifier = Modifier.fillMaxWidth().height(42.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).padding(end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val icon = if (currentCallDeflectorMode == "keep_active") Icons.Default.Shield else Icons.Default.VisibilityOff
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                            Text(
                                text = callDeflectorOptions.firstOrNull { it.first == currentCallDeflectorMode }?.second ?: LightspeedLanguageEngine.resolve(LightspeedVocabulary.Key.CALL_DEFLECTOR_MODE_KEEP_ACTIVE),
                                color = lsColors.textPrimary,
                                fontSize = 11.5.sp,
                                maxLines = 1
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                DeckThemedDropdownMenu(
                    expanded = isCallDeflectorDropdownOpen,
                    onDismissRequest = { isCallDeflectorDropdownOpen = false },
                    anchorWidth = anchorWidth
                ) {
                    callDeflectorOptions.forEach { (key, label) ->
                        val menuIcon = if (key == "keep_active") Icons.Default.Shield else Icons.Default.VisibilityOff
                        DropdownMenuItem(
                            modifier = Modifier.height(42.dp),
                            leadingIcon = { Icon(menuIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp)) },
                            text = { Text(label, fontSize = 12.sp, maxLines = 1) },
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
                text = lsString(R.string.sys_calls_note),
                fontSize = 10.5.sp,
                color = lsColors.textSecondary.copy(alpha = 0.75f)
            )
        }
    }
}
