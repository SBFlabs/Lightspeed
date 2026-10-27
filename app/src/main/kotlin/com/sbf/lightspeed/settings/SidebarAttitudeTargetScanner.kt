package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens
import java.util.Locale

@Composable
fun rememberAttitudeTargets(context: Context): Pair<List<AttitudeTargetItem>, List<AttitudeTargetItem>> {
    return remember(context) {
        val pm = context.packageManager
        val pinned = mutableListOf<AttitudeTargetItem>()
        val regular = mutableListOf<AttitudeTargetItem>()
        val seenPackages = mutableSetOf<String>()

        // 1. Virtual Core: Lock Screen
        pinned.add(
            AttitudeTargetItem(
                id = "keyguard:lockscreen",
                label = "Lock Screen (Keyguard)",
                subtitle = "System Lock Screen & Ambient Display",
                isPinned = true,
                badge = "SYSTEM"
            )
        )
        seenPackages.add("keyguard:lockscreen")

        // 2. All Launcher Apps (CATEGORY_HOME + Known OEM Transsion Launchers)
        val homeIntent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val homeResolves = pm.queryIntentActivities(homeIntent, 0)
        homeResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Home Launcher ($pkg)",
                        isPinned = true,
                        badge = "LAUNCHER"
                    )
                )
            }
        }
        LightspeedOrientationEngine.KNOWN_TRANSSION_LAUNCHERS.forEach { oemPkg ->
            if (oemPkg !in seenPackages) {
                try {
                    val appInfo = pm.getApplicationInfo(oemPkg, 0)
                    seenPackages.add(oemPkg)
                    val label = pm.getApplicationLabel(appInfo).toString().ifBlank { "Infinix Home" }
                    pinned.add(
                        AttitudeTargetItem(
                            id = oemPkg,
                            label = label,
                            subtitle = "Home Launcher ($oemPkg)",
                            isPinned = true,
                            badge = "LAUNCHER"
                        )
                    )
                } catch (e: Exception) { logSwallowed("SidebarAttitudeTargetScanner", "rememberAttitudeTargets:oem", e) }
            }
        }

        // 3. Phone / Dialer
        val dialerIntent = Intent(Intent.ACTION_DIAL)
        val dialerResolves = pm.queryIntentActivities(dialerIntent, 0)
        dialerResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Phone & Dialer ($pkg)",
                        isPinned = true,
                        badge = "PHONE"
                    )
                )
            }
        }

        // 4. SMS / Messaging
        val smsIntent = Intent(Intent.ACTION_SENDTO).apply { data = android.net.Uri.parse("smsto:") }
        val smsResolves = pm.queryIntentActivities(smsIntent, 0)
        smsResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages && pkg != "android") {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                pinned.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = "Text & SMS ($pkg)",
                        isPinned = true,
                        badge = "SMS"
                    )
                )
            }
        }

        // 5. Popular Messengers & Derivatives (WhatsApp, Telegram, Messenger)
        val popularMessengers = listOf(
            Triple(listOf("com.whatsapp", "com.whatsapp.w4b", "com.gbwhatsapp", "com.yowhatsapp"), "WhatsApp", "WHATSAPP"),
            Triple(listOf("org.telegram.messenger", "org.thunderdog.challegram", "org.telegram.plus", "org.telegram.messenger.web"), "Telegram", "TELEGRAM"),
            Triple(listOf("com.facebook.orca", "com.facebook.mlite"), "Messenger", "MESSENGER")
        )
        popularMessengers.forEach { (pkgList, fallbackName, badgeName) ->
            pkgList.forEach { candidatePkg ->
                try {
                    val appInfo = pm.getApplicationInfo(candidatePkg, 0)
                    if (candidatePkg !in seenPackages) {
                        seenPackages.add(candidatePkg)
                        val label = pm.getApplicationLabel(appInfo).toString().ifBlank { fallbackName }
                        pinned.add(
                            AttitudeTargetItem(
                                id = candidatePkg,
                                label = label,
                                subtitle = "$fallbackName ($candidatePkg)",
                                isPinned = true,
                                badge = badgeName
                            )
                        )
                    }
                } catch (e: Exception) { logSwallowed("SidebarAttitudeTargetScanner", "rememberAttitudeTargets:messengers", e) }
            }
        }

        // 6. Settings App
        val settingsPkg = "com.android.settings"
        try {
            val appInfo = pm.getApplicationInfo(settingsPkg, 0)
            if (settingsPkg !in seenPackages) {
                seenPackages.add(settingsPkg)
                val label = pm.getApplicationLabel(appInfo).toString().ifBlank { "Settings" }
                pinned.add(
                    AttitudeTargetItem(
                        id = settingsPkg,
                        label = label,
                        subtitle = "System Settings ($settingsPkg)",
                        isPinned = true,
                        badge = "SETTINGS"
                    )
                )
            }
        } catch (e: Exception) { logSwallowed("SidebarAttitudeTargetScanner", "rememberAttitudeTargets:settings", e) }

        // 7. All other installed launcher apps (standard user apps)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val launcherResolves = pm.queryIntentActivities(launcherIntent, 0)
        launcherResolves.forEach { ri ->
            val pkg = ri.activityInfo.packageName
            if (pkg !in seenPackages) {
                seenPackages.add(pkg)
                val label = ri.loadLabel(pm).toString().ifBlank { pkg }
                regular.add(
                    AttitudeTargetItem(
                        id = pkg,
                        label = label,
                        subtitle = pkg,
                        isPinned = false,
                        badge = null
                    )
                )
            }
        }

        regular.sortBy { it.label.lowercase(Locale.ROOT) }
        Pair(pinned, regular)
    }
}

@Composable
fun SidebarAttitudeAssignedTray(
    context: Context,
    assignedPackages: MutableList<String>,
    allTargets: List<AttitudeTargetItem>,
    dynamicPrimary: Color,
    dynamicSecondary: Color,
    onSave: (Set<String>) -> Unit,
    onUpdated: () -> Unit
) {
    if (assignedPackages.isEmpty()) return

    Text(
        text = "ASSIGNED TARGETS (${assignedPackages.size}) — TAP ✕ TO REMOVE:",
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        color = dynamicSecondary,
        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(assignedPackages.size, key = { idx -> "assigned_${assignedPackages[idx]}" }) { idx ->
            val pkg = assignedPackages[idx]
            val targetItem = allTargets.firstOrNull { it.id == pkg }
            val label = targetItem?.label ?: pkg
            val iconBmp = remember(pkg) {
                if (pkg == "keyguard:lockscreen") null
                else LightspeedIconManager.getIconBitmap(context, pkg, useCache = false)
            }

            Row(
                modifier = Modifier
                    .background(LightspeedStyle.colors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                    .border(1.dp, dynamicPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBmp != null) {
                    Image(
                        bitmap = iconBmp.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).padding(end = 2.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (pkg == "keyguard:lockscreen") Icons.Default.Lock else Icons.Default.Apps,
                        contentDescription = null,
                        tint = dynamicPrimary,
                        modifier = Modifier.size(16.dp).padding(end = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label.take(15),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LightspeedStyle.colors.textPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        assignedPackages.remove(pkg)
                        onSave(assignedPackages.toSet())
                        onUpdated()
                    },
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = LightspeedTokens.StatusErrorSoft,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
