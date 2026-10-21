package com.sbf.lightspeed.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.sbf.lightspeed.settings.DeckStylePresets
import com.sbf.lightspeed.settings.DeckThemeManager
import com.sbf.lightspeed.settings.DeckThemeSnapshot

val LocalDeckTheme = staticCompositionLocalOf<DeckThemeSnapshot> { DeckThemeManager.snapshotFlow.value }

@Composable
fun DeckTheme(content: @Composable () -> Unit) {
    val snapshot by DeckThemeManager.snapshotFlow.collectAsState()
    CompositionLocalProvider(LocalDeckTheme provides snapshot) {
        content()
    }
}
