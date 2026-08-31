package com.sbf.lightspeed.settings

import android.app.Activity
import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.UnfoldLess
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.defaultPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level floating settings coordinator for Lightspeed.
 * Manages presentation animations, expand/collapse toggles, and matrix config views.
 */
@Composable
fun MainSettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        isVisible = true
    }

    var showGuidebook by remember { mutableStateOf(false) }
    var targetJumpTab by remember { mutableIntStateOf(-1) }
    var targetJumpSection by remember { mutableStateOf<String?>(null) }
    var isAllExpanded by remember { mutableStateOf(false) }

    val dismissAction = {
        isVisible = false
        prefs.edit()
            .putBoolean("pref_statusbar_preview", false)
            .putBoolean("pref_sidebar_preview", false)
            .putBoolean("pref_sidebar_left_preview", false)
            .putBoolean("pref_section_statusbar_expanded", false)
            .putBoolean("pref_section_center_expanded", false)
            .putBoolean("pref_section_top_expanded", false)
            .putBoolean("pref_section_bottom_expanded", false)
            .putBoolean("pref_section_left_center_expanded", false)
            .putBoolean("pref_section_left_top_expanded", false)
            .putBoolean("pref_section_left_bottom_expanded", false)
            .apply()
        scope.launch {
            delay(300)
            (context as? Activity)?.finishAndRemoveTask()
        }
    }

    BackHandler {
        dismissAction()
    }

    val animatedScrimAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.25f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "scrim_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = animatedScrimAlpha))
            .clickable { dismissAction() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutLinearInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 250))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.96f)
                        .fillMaxHeight(0.94f)
                        .clickable(enabled = false) {}
                ) {
                    var refreshKey by remember { mutableStateOf(0) }
                    var toggleAllTrigger by remember { mutableStateOf(0) }

                    FloatingOverlayContainer(
                        title = "Central Command",
                        onDismiss = { dismissAction() },
                        headerControl = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showGuidebook = true },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.MenuBook,
                                        contentDescription = "The Stranded in Space Guidebook",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        isAllExpanded = !isAllExpanded
                                        toggleAllTrigger++
                                    },
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isAllExpanded) Icons.Outlined.UnfoldLess else Icons.Outlined.UnfoldMore,
                                        contentDescription = "Expand or Collapse All",
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    ) {
                        SidebarMatrixConfigurationFields(
                            context = context,
                            prefs = prefs,
                            toggleAllTrigger = toggleAllTrigger,
                            jumpTargetTab = targetJumpTab,
                            jumpTargetSection = targetJumpSection,
                            onRefreshNeeded = { /* Local state reacts immediately without recreating hierarchy */ }
                        )
                    }

                    if (showGuidebook) {
                        GuidebookBottomSheet(
                            onDismiss = { showGuidebook = false },
                            onNavigateToSection = { tabIdx, secKey ->
                                targetJumpTab = tabIdx
                                targetJumpSection = secKey
                                showGuidebook = false
                            }
                        )
                    }
                }
            }
        }
    }
}
