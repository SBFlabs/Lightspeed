package com.sbf.lightspeed.system

import android.content.Context
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
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SyncAlt
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
import com.sbf.lightspeed.settings.DeckSlider
import com.sbf.lightspeed.settings.DeckThemeSnapshot
import com.sbf.lightspeed.system.sonic.SonicAppVolumeEngine
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

    val sortedApps = remember(displayApps, pinnedApps, ignoredApps) {
        val pinned = displayApps.filter { pinnedApps.contains(it) && !ignoredApps.contains(it) }
        val normal = displayApps.filter { !pinnedApps.contains(it) && !ignoredApps.contains(it) }
        val ignored = displayApps.filter { ignoredApps.contains(it) }
        pinned + normal + ignored
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.Layers, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text("PER-APP MIXER, ROUTING & SOVEREIGNTY", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        if (showBypassWarning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF261805).copy(alpha = 0.85f))
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.7f), RoundedCornerShape(8.dp))
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
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "AUDIO ROUTED OUTSIDE ISOLATION",
                            color = Color(0xFFFFB300),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "This app is routed directly to a dedicated hardware output. Voice isolation and background muffling will be bypassed for this stream.",
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
                                    checkedColor = Color(0xFFFFB300),
                                    uncheckedColor = Color.White.copy(alpha = 0.5f),
                                    checkmarkColor = Color.Black
                                )
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "Don't show again",
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
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300))
                        ) {
                            Text("OK", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        if (sortedApps.isEmpty()) {
            Text("No active or pinned audio applications.", color = Color.Gray, fontSize = 10.sp)
        } else {
            for (pkg in sortedApps) {
                key(pkg) {
                    SonicAppRow(
                        context = context,
                        pkg = pkg,
                        tint = tint,
                        isPinned = pinnedApps.contains(pkg),
                        isIgnored = ignoredApps.contains(pkg),
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
    }
}

@Composable
private fun SonicAppRow(
    context: Context,
    pkg: String,
    tint: Color,
    isPinned: Boolean,
    isIgnored: Boolean,
    onTogglePin: () -> Unit,
    onToggleIgnore: () -> Unit,
    onRouteChanged: (String) -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    var volume by remember(pkg, isIgnored) { mutableFloatStateOf(if (isIgnored) 1.0f else SonicAppVolumeEngine.getAppVolume(context, pkg)) }
    var route by remember(pkg, isIgnored) { mutableStateOf(if (isIgnored) SonicChannelRouter.ROUTE_DEFAULT else SonicChannelRouter.getAppRoute(context, pkg)) }
    var isAlwaysOn by remember(pkg, isIgnored) { mutableStateOf(if (isIgnored) false else SonicChannelRouter.isAlwaysOnAudioFocus(context, pkg)) }

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
            .alpha(if (isIgnored) 0.38f else 1.0f)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isPinned -> tint.copy(alpha = 0.14f)
                    isIgnored -> Color.White.copy(alpha = 0.03f)
                    else -> Color.White.copy(alpha = 0.08f)
                }
            )
            .border(
                0.8.dp,
                when {
                    isPinned -> tint.copy(alpha = 0.55f)
                    isIgnored -> Color.White.copy(alpha = 0.06f)
                    else -> Color.White.copy(alpha = 0.15f)
                },
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
                color = if (isIgnored) Color.Gray else Color.White,
                fontSize = 11.5.sp,
                fontWeight = if (isPinned) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = if (isIgnored) "IGN" else "${(volume.coerceIn(0.0f, 1.0f) * 100).toInt()}%",
                color = if (isIgnored) Color.Gray else lsColors.textPrimary,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // SECOND LINE: Full-width DeckSlider + Route/Lock/Pin/Mute icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            DeckSlider(
                value = volume.coerceIn(0.0f, 1.0f),
                valueRange = 0.0f..1.0f,
                enabled = !isIgnored,
                onValueChange = {
                    if (!isIgnored) {
                        volume = it
                        SonicAppVolumeEngine.setAppVolume(context, pkg, it)
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp)
            )

            val routeIcon = when (route) {
                SonicChannelRouter.ROUTE_SPEAKER -> Icons.Default.Speaker
                SonicChannelRouter.ROUTE_BLUETOOTH, SonicChannelRouter.ROUTE_WIRED -> Icons.Default.Headphones
                else -> Icons.Default.SyncAlt
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(0.8.dp, if (isIgnored) Color.Gray.copy(alpha = 0.25f) else tint.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .clickable(enabled = !isIgnored) {
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
                    contentDescription = when (route) {
                        SonicChannelRouter.ROUTE_SPEAKER -> "Speaker"
                        SonicChannelRouter.ROUTE_BLUETOOTH, SonicChannelRouter.ROUTE_WIRED -> "Headphones"
                        else -> "Default Audio Route"
                    },
                    tint = if (isIgnored) Color.Gray else tint,
                    modifier = Modifier.size(14.dp)
                )
            }

            // No-Ducking Audio Sovereignty Lock
            IconButton(
                onClick = {
                    if (!isIgnored) {
                        isAlwaysOn = !isAlwaysOn
                        SonicChannelRouter.setAlwaysOnAudioFocus(context, pkg, isAlwaysOn)
                    }
                },
                enabled = !isIgnored,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isAlwaysOn) Icons.Default.Lock else Icons.Default.LockOpen,
                    contentDescription = if (isAlwaysOn) "Always-On Focus" else "Duckable",
                    tint = when {
                        isIgnored -> Color.Gray.copy(alpha = 0.3f)
                        isAlwaysOn -> lsColors.accentOk
                        else -> Color.White.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.size(14.dp)
                )
            }

            // Pin to top button
            IconButton(
                onClick = onTogglePin,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PushPin,
                    contentDescription = if (isPinned) "Unpin App" else "Pin to Top",
                    tint = if (isPinned) tint else Color.White.copy(alpha = 0.32f),
                    modifier = Modifier.size(13.dp)
                )
            }

            // Ignore App button (moves to bottom, grays out, disables controls)
            IconButton(
                onClick = onToggleIgnore,
                modifier = Modifier.size(22.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = if (isIgnored) "Unignore App" else "Ignore App",
                    tint = if (isIgnored) lsColors.accentDanger else Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
