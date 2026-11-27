package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.LightspeedAccessibilityService
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedVocabulary
import com.sbf.lightspeed.system.SonicDeckManager
import com.sbf.lightspeed.system.VistaDeckManager
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun HudSonicDeckSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val lsColors = LightspeedStyle.colors
    var showSonicPowerWarning by remember { mutableStateOf(false) }
    val (sonicDeckTitle, sonicDeckSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.SONIC_DECK, languageMode)
    val subtitle = if (sonicDeckSub != null) "$sonicDeckSub • ${lsString(R.string.lab_sonic_sub)}" else lsString(R.string.lab_sonic_sub)

    CompactAccordionSection(
        title = sonicDeckTitle,
        subtitle = subtitle,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_SONIC_DECK_ENABLED,
                defaultVal = false,
                title = lsString(R.string.sdeck_enable_fmt, sonicDeckTitle),
                subtitle = lsString(R.string.lab_sonic_pull),
                onChanged = { enabled ->
                    SonicDeckManager.onMasterToggle(context, enabled)
                    if (enabled && !prefs.getBoolean(LightspeedPreferences.KEY_SONIC_POWER_WARNING_DISMISSED, false)) {
                        showSonicPowerWarning = true
                    }
                    onRefreshNeeded()
                }
            )
            Text(
                text = lsString(R.string.lab_sonic_ram_warn),
                fontSize = 11.sp,
                color = lsColors.accentDanger,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            if (showSonicPowerWarning) {
                var dontRemind by remember { mutableStateOf(false) }
                val dismissWarning = {
                    if (dontRemind) {
                        prefs.edit().putBoolean(LightspeedPreferences.KEY_SONIC_POWER_WARNING_DISMISSED, true).apply()
                    }
                    showSonicPowerWarning = false
                }
                DeckDialog(
                    onDismissRequest = dismissWarning,
                    title = {
                        Text(
                            text = lsString(R.string.lab_sonic_dlg_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = lsColors.accentDanger
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = lsString(R.string.lab_sonic_dlg_body),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { dontRemind = !dontRemind }
                            ) {
                                Checkbox(checked = dontRemind, onCheckedChange = { dontRemind = it })
                                Text(lsString(R.string.lab_dont_remind), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = dismissWarning) {
                            Text(lsString(R.string.cc_dlg_got_it_caps), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
            if (prefs.getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false)) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_SONIC_DECK_LEFT_ENABLED,
                    defaultVal = true,
                    title = lsString(R.string.lab_left_launch),
                    subtitle = lsString(R.string.lab_sonic_left_sub),
                    onChanged = { onRefreshNeeded() }
                )
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_SONIC_DECK_RIGHT_ENABLED,
                    defaultVal = true,
                    title = lsString(R.string.lab_right_launch),
                    subtitle = lsString(R.string.lab_sonic_right_sub),
                    onChanged = { onRefreshNeeded() }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            DeckButton(
                text = lsString(R.string.lab_launch_sonic),
                isActive = prefs.getBoolean(LightspeedPreferences.KEY_SONIC_DECK_ENABLED, false),
                onClick = {
                    val svc = LightspeedAccessibilityService.instance
                    if (svc != null) {
                        SonicDeckManager.show(svc)
                    } else {
                        Toast.makeText(context, lsString(R.string.lab_a11y_inactive), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
        }
    }
}

@Composable
fun HudVistaDeckSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onRefreshNeeded: () -> Unit,
    languageMode: LightspeedLanguageEngine.LanguageMode = LightspeedLanguageEngine.modeFlow.value
) {
    val (vistaDeckTitle, vistaDeckSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.VISTA_DECK, languageMode)
    val subtitle = if (vistaDeckSub != null) "$vistaDeckSub • ${lsString(R.string.lab_vista_sub)}" else lsString(R.string.lab_vista_sub)

    CompactAccordionSection(
        title = vistaDeckTitle,
        subtitle = subtitle,
        icon = {
            Icon(
                imageVector = Icons.Default.Brightness4,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PrefToggleRow(
                prefs = prefs,
                prefKey = LightspeedPreferences.KEY_VISTA_DECK_ENABLED,
                defaultVal = true,
                title = lsString(R.string.sdeck_enable_fmt, vistaDeckTitle),
                subtitle = lsString(R.string.lab_vista_pull),
                onChanged = { onRefreshNeeded() }
            )
            if (prefs.getBoolean(LightspeedPreferences.KEY_VISTA_DECK_ENABLED, true)) {
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_VISTA_DECK_LEFT_ENABLED,
                    defaultVal = true,
                    title = lsString(R.string.lab_left_launch),
                    subtitle = lsString(R.string.lab_vista_left_sub),
                    onChanged = { onRefreshNeeded() }
                )
                PrefToggleRow(
                    prefs = prefs,
                    prefKey = LightspeedPreferences.KEY_VISTA_DECK_RIGHT_ENABLED,
                    defaultVal = true,
                    title = lsString(R.string.lab_right_launch),
                    subtitle = lsString(R.string.lab_vista_right_sub),
                    onChanged = { onRefreshNeeded() }
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            DeckButton(
                text = lsString(R.string.lab_launch_vista),
                isActive = prefs.getBoolean(LightspeedPreferences.KEY_VISTA_DECK_ENABLED, true),
                onClick = {
                    val svc = LightspeedAccessibilityService.instance
                    if (svc != null) {
                        VistaDeckManager.show(svc)
                    } else {
                        Toast.makeText(context, lsString(R.string.lab_a11y_inactive), Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
        }
    }
}
