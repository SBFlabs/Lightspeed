package com.sbf.lightspeed.system

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.sbf.lightspeed.R
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import com.sbf.lightspeed.system.sonic.SonicChannelRouter
import com.sbf.lightspeed.system.sonic.SonicVoiceIsolationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sleek, single-row per-app audio controls with hardware gain (0..200%),
 * multi-channel routing, and audio focus lock.
 * Detects foreground, Picture-in-Picture, Pop-Up / Freeform, MediaSession, and UID 2000 players in 0ms.
 */
@Composable
internal fun SonicAppMixerSection(
    context: Context,
    snapshot: DeckThemeSnapshot
) {
    val tint = Color(snapshot.effectiveTintArgb.toInt())
    val customApps = remember { try { SonicAppVolumeEngine.getAllCustomAppVolumes(context) } catch (_: Exception) { emptyMap() } }
    var pinnedApps by remember { mutableStateOf(SonicChannelRouter.getPinnedApps(context)) }
    var ignoredApps by remember { mutableStateOf(SonicChannelRouter.getIgnoredApps(context)) }
    var showBypassWarning by remember { mutableStateOf(false) }
    var dontShowAgainChecked by remember { mutableStateOf(false) }

    var displayApps by remember {
        val pm = context.packageManager
        val list = linkedSetOf<String>()

        // 0. Pinned apps first
        for (pkg in SonicChannelRouter.getPinnedApps(context)) {
            list.add(pkg)
        }

        // 1. Immediate visible / PiP / Pop-Up / MediaSession / cached playing packages (0ms, non-blocking)
        for (pkg in SonicAppVolumeEngine.getImmediateVisibleAndMediaPackages(context)) {
            list.add(pkg)
        }

        // 2. User configured volume overrides
        for (pkg in customApps.keys) {
            list.add(pkg)
        }

        // 3. Common media apps installed on device
        val candidates = listOf(
            "org.jellyfin.mobile",
            "org.jellyfin.androidtv",
            "org.videolan.vlc",
            "is.xyz.mpv",
            "com.spotify.music",
            "com.google.android.youtube",
            "app.revanced.android.youtube"
        )
        for (pkg in candidates) {
            if (!list.contains(pkg)) {
                try {
                    pm.getApplicationInfo(pkg, 0)
                    list.add(pkg)
                } catch (_: Exception) {}
            }
        }
        // 4. Include ignored apps so they remain visible at the bottom and can be un-ignored
        for (pkg in SonicChannelRouter.getIgnoredApps(context)) {
            list.add(pkg)
        }
        mutableStateOf(list.toList())
    }

    LaunchedEffect(Unit) {
        val privApps = withContext(Dispatchers.IO) {
            SonicAppVolumeEngine.queryPrivilegedAudioPackages(context)
        }
        if (privApps.isNotEmpty()) {
            val merged = linkedSetOf<String>()
            merged.addAll(privApps)
            merged.addAll(displayApps)
            displayApps = merged.toList()
        }
    }

    var isHiddenAppsExpanded by remember {
        mutableStateOf(context.defaultPrefs().getBoolean("pref_sonic_sub_hidden_apps_expanded", false))
    }

    val activeApps = remember(displayApps, pinnedApps, ignoredApps) {
        val pinned = displayApps.filter { pinnedApps.contains(it) && !ignoredApps.contains(it) }
        val normal = displayApps.filter { !pinnedApps.contains(it) && !ignoredApps.contains(it) }
        pinned + normal
    }

    val hiddenApps = remember(displayApps, ignoredApps) {
        val list = mutableListOf<String>()
        for (pkg in displayApps) {
            if (ignoredApps.contains(pkg) && !list.contains(pkg)) {
                list.add(pkg)
            }
        }
        for (pkg in ignoredApps) {
            if (!list.contains(pkg)) {
                list.add(pkg)
            }
        }
        list
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Layers, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(lsString(R.string.sdeck_mixer_hdr), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (showBypassWarning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(LightspeedTokens.TacticalNoticeWarm.copy(alpha = 0.85f))
                    .border(1.dp, LightspeedTokens.StatusWarning.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = LightspeedTokens.StatusWarning,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = lsString(R.string.sdeck_bypass_title),
                            color = LightspeedTokens.StatusWarning,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = lsString(R.string.sdeck_bypass_body),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { dontShowAgainChecked = !dontShowAgainChecked }
                                .padding(vertical = 4.dp, horizontal = 2.dp)
                        ) {
                            Checkbox(
                                checked = dontShowAgainChecked,
                                onCheckedChange = { dontShowAgainChecked = it },
                                modifier = Modifier.size(18.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LightspeedTokens.StatusWarning,
                                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                lsString(R.string.sdeck_dont_show),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 8.5.sp
                            )
                        }

                        Button(
                            onClick = {
                                if (dontShowAgainChecked) {
                                    context.defaultPrefs().edit()
                                        .putBoolean(LightspeedPreferences.KEY_SONIC_ROUTING_BYPASS_WARNING_DISMISSED, true)
                                        .apply()
                                }
                                showBypassWarning = false
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LightspeedTokens.StatusWarning)
                        ) {
                            Text(lsString(R.string.cc_dlg_got_it_caps), color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (activeApps.isEmpty() && hiddenApps.isEmpty()) {
            Text(lsString(R.string.sdeck_no_apps), color = com.sbf.lightspeed.ui.theme.LightspeedStyle.colors.textSecondary, fontSize = 10.sp)
        } else {
            if (activeApps.isEmpty()) {
                Text(lsString(R.string.sdeck_all_hidden), color = com.sbf.lightspeed.ui.theme.LightspeedStyle.colors.textSecondary, fontSize = 10.sp)
            } else {
                for (pkg in activeApps) {
                    key(pkg) {
                        SonicAppRow(
                            context = context,
                            pkg = pkg,
                            tint = tint,
                            isPinned = pinnedApps.contains(pkg),
                            onTogglePin = {
                                SonicChannelRouter.togglePinApp(context, pkg)
                                pinnedApps = SonicChannelRouter.getPinnedApps(context)
                                ignoredApps = SonicChannelRouter.getIgnoredApps(context)
                            },
                            onToggleIgnore = {
                                SonicChannelRouter.toggleIgnoreApp(context, pkg)
                                pinnedApps = SonicChannelRouter.getPinnedApps(context)
                                ignoredApps = SonicChannelRouter.getIgnoredApps(context)
                            },
                            onRouteChanged = { newRoute ->
                                if (newRoute != SonicChannelRouter.ROUTE_DEFAULT &&
                                    SonicVoiceIsolationEngine.isEnabled &&
                                    !context.defaultPrefs().getBoolean(LightspeedPreferences.KEY_SONIC_ROUTING_BYPASS_WARNING_DISMISSED, false)
                                ) {
                                    showBypassWarning = true
                                }
                            }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = hiddenApps.isNotEmpty(),
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SonicHiddenAppsSubAccordion(
                    hiddenApps = hiddenApps,
                    context = context,
                    isExpanded = isHiddenAppsExpanded,
                    onToggle = {
                        isHiddenAppsExpanded = !isHiddenAppsExpanded
                        context.defaultPrefs().edit()
                            .putBoolean("pref_sonic_sub_hidden_apps_expanded", isHiddenAppsExpanded)
                            .apply()
                    },
                    onUnhideApp = { pkg ->
                        SonicChannelRouter.toggleIgnoreApp(context, pkg)
                        pinnedApps = SonicChannelRouter.getPinnedApps(context)
                        ignoredApps = SonicChannelRouter.getIgnoredApps(context)
                    }
                )
            }
        }
    }
}

@Composable
private fun SonicAppRow(
    context: Context,
    pkg: String,
    tint: Color,
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onToggleIgnore: () -> Unit,
    onRouteChanged: (String) -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    var volume by remember(pkg) { mutableFloatStateOf(SonicAppVolumeEngine.getAppVolume(context, pkg)) }
    var route by remember(pkg) { mutableStateOf(SonicChannelRouter.getAppRoute(context, pkg)) }
    var isAlwaysOn by remember(pkg) { mutableStateOf(SonicChannelRouter.isAlwaysOnAudioFocus(context, pkg)) }

    val pm = context.packageManager
    val label = remember(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }

    val appBitmap = remember(pkg) {
        try {
            pm.getApplicationIcon(pkg).toBitmap(width = 48, height = 48).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPinned) tint.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.08f)
            )
            .border(
                0.8.dp,
                if (isPinned) tint.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.15f),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // TOP LINE: App Icon + Full App Name + Percentage
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (appBitmap != null) {
                Image(
                    bitmap = appBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                    tint = lsColors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = label,
                color = Color.White,
                fontSize = 11.5.sp,
                fontWeight = if (isPinned) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${(volume.coerceIn(0.0f, 1.0f) * 100).toInt()}%",
                color = lsColors.textPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // SECOND LINE: Full-width DeckSlider + Route/Lock/Pin/Mute icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            DeckSlider(
                value = volume.coerceIn(0.0f, 1.0f),
                valueRange = 0.0f..1.0f,
                onValueChange = {
                    volume = it
                    SonicAppVolumeEngine.setAppVolume(context, pkg, it)
                },
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )

            // Audio Routing Selector
            val (routeIcon, routeColor, routeDesc) = when (route) {
                SonicChannelRouter.ROUTE_SPEAKER -> Triple(
                    Icons.Default.Speaker,
                    LightspeedTokens.StatusSky,
                    lsString(R.string.sdeck_cd_speaker)
                )
                SonicChannelRouter.ROUTE_BLUETOOTH, SonicChannelRouter.ROUTE_WIRED -> Triple(
                    Icons.Default.Headphones,
                    LightspeedTokens.StatusOkSoft,
                    lsString(R.string.sdeck_cd_headphones)
                )
                else -> Triple(
                    Icons.AutoMirrored.Filled.VolumeUp,
                    if (tint == Color.White) LightspeedTokens.StatusSlate else tint,
                    lsString(R.string.sdeck_cd_route_default)
                )
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (route != SonicChannelRouter.ROUTE_DEFAULT) routeColor.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .border(
                        0.9.dp,
                        if (route != SonicChannelRouter.ROUTE_DEFAULT) routeColor.copy(alpha = 0.75f)
                        else Color.White.copy(alpha = 0.35f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        val next = when (route) {
                            SonicChannelRouter.ROUTE_DEFAULT -> SonicChannelRouter.ROUTE_SPEAKER
                            SonicChannelRouter.ROUTE_SPEAKER -> SonicChannelRouter.ROUTE_BLUETOOTH
                            else -> SonicChannelRouter.ROUTE_DEFAULT
                        }
                        route = next
                        SonicChannelRouter.setAppRoute(context, pkg, next)
                        onRouteChanged(next)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = routeIcon,
                    contentDescription = routeDesc,
                    tint = if (route != SonicChannelRouter.ROUTE_DEFAULT) routeColor else Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }

            // No-Ducking Audio Sovereignty Lock
            val lockColor = if (isAlwaysOn) lsColors.accentOk else Color.White.copy(alpha = 0.65f)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isAlwaysOn) lsColors.accentOk.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        0.8.dp,
                        if (isAlwaysOn) lsColors.accentOk.copy(alpha = 0.70f) else Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable {
                        isAlwaysOn = !isAlwaysOn
                        SonicChannelRouter.setAlwaysOnAudioFocus(context, pkg, isAlwaysOn)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isAlwaysOn) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isAlwaysOn) lsString(R.string.sdeck_cd_focus_on) else lsString(R.string.sdeck_cd_focus_off),
                    tint = lockColor,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Pin to top button
            val pinAccent = LightspeedTokens.StatusWarning
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isPinned) pinAccent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f))
                    .border(
                        0.9.dp,
                        if (isPinned) pinAccent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onTogglePin),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (isPinned) lsString(R.string.sdeck_cd_unpin) else lsString(R.string.sdeck_cd_pin),
                    tint = if (isPinned) pinAccent else Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(15.dp)
                )
            }

            // Hide App button (moves to hidden sub-accordion, resets overrides)
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(
                        0.8.dp,
                        Color.White.copy(alpha = 0.20f),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable(onClick = onToggleIgnore),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = lsString(R.string.sdeck_cd_hide),
                    tint = Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun SonicHiddenAppsSubAccordion(
    hiddenApps: List<String>,
    context: Context,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onUnhideApp: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(0.6.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    LightspeedHapticEngine.centralCommandClick(context)
                    onToggle()
                }
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = lsString(R.string.sdeck_hidden_hdr),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 0.3.sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "${hiddenApps.size}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (pkg in hiddenApps) {
                    key(pkg) {
                        SonicHiddenAppRow(
                            context = context,
                            pkg = pkg,
                            onUnhide = { onUnhideApp(pkg) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SonicHiddenAppRow(
    context: Context,
    pkg: String,
    onUnhide: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val pm = context.packageManager
    val label = remember(pkg) {
        try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg.substringAfterLast('.')
        }
    }
    val appBitmap = remember(pkg) {
        try {
            pm.getApplicationIcon(pkg).toBitmap(width = 40, height = 40).asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
            .clickable(onClick = onUnhide)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (appBitmap != null) {
            Image(
                bitmap = appBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        } else {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
                tint = lsColors.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Restore / Unhide Action Button
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(lsColors.accentOk.copy(alpha = 0.16f))
                .border(0.6.dp, lsColors.accentOk.copy(alpha = 0.6f), RoundedCornerShape(5.dp))
                .clickable(onClick = onUnhide),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = lsString(R.string.sdeck_cd_unhide),
                tint = lsColors.accentOk,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
