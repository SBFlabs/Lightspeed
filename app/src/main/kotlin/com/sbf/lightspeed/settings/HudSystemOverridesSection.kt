package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.LightspeedLanguageEngine
import com.sbf.lightspeed.system.LightspeedVocabulary

@Composable
fun HudSystemOverridesSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val currentLanguageMode by LightspeedLanguageEngine.modeFlow.collectAsState()
    val (overrideTitle, overrideSub) = LightspeedLanguageEngine.resolvePair(LightspeedVocabulary.Key.SYSTEM_OVERRIDES, currentLanguageMode)

    CompactAccordionSection(
        title = overrideTitle,
        subtitle = overrideSub,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        },
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        SystemOverrideDeckContents(
            context = context,
            prefs = prefs
        )
    }
}
