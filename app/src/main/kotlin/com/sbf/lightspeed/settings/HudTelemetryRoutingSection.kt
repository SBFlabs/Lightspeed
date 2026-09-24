package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedNotificationListener
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences

/**
 * Sub-Component: Dual-Channel Telemetry Routing & Permissions.
 * Manages notification access requirements, lock screen suppression,
 * and routing channels for Downloads and Media playback.
 */
@Composable
fun HudTelemetryRoutingSection(
    context: Context,
    prefs: SharedPreferences,
    isExpanded: Boolean,
    onShowNotificationAccessDialog: () -> Unit,
    onRefreshNeeded: () -> Unit
) {
    fun checkNotificationAccess(): Boolean {
        val pkgName = context.packageName
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat?.contains(pkgName) == true || LightspeedNotificationListener.instance != null
    }

    var isNotifAccessGranted by remember { mutableStateOf(checkNotificationAccess()) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotifAccessGranted = checkNotificationAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Visual Dual-Channel HUD Guide Badge Card
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("DUAL-CHANNEL TELEMETRY HUD", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📏 Horizon Rail", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    Text("Ultra-thin progress line on display top edge", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("💊 Orbital Capsule", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                    Text("Dynamic liquid-glass island on camera cutout", fontSize = 10.5.sp, color = Color.LightGray.copy(alpha = 0.85f))
                }
            }
        }
    }

    PrefToggleRow(
        prefs = prefs,
        prefKey = LightspeedPreferences.KEY_HIDE_ON_LOCKSCREEN_AND_DOCK,
        defaultVal = true,
        title = "Suppress on Lock Screen & OEM Screensavers",
        subtitle = "Automatically hides Orbital Capsule and HUD Strip when device is locked or running OEM ambient dock.",
        onChanged = { onRefreshNeeded() }
    )

    if (!isNotifAccessGranted) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onShowNotificationAccessDialog() }
                .padding(vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification Access Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Tap here to grant permission in Android Settings so Lightspeed can read download progress and media metadata.", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.6f))
            }
        }
    }

    val routingOptions = listOf(
        "none" to "None (Disabled)",
        "top_line" to "Horizon Rail (Top-Edge Line)",
        "notch_pill" to "Orbital Capsule [Experimental Labs]",
        "both" to "Both (Horizon Rail & Orbital Capsule [Labs])"
    )

    // Downloads Telemetry Selector
    var selectedDl by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line") ?: "top_line")
    }
    PrefDropdownSelector(
        title = "DOWNLOADS TELEMETRY ROUTING",
        currentKey = selectedDl,
        options = routingOptions,
        onSelected = { key ->
            selectedDl = key
            prefs.edit().putString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, key).apply()
            if (key != "none" && !isNotifAccessGranted) {
                onShowNotificationAccessDialog()
            }
            safeReloadPreferences()
            onRefreshNeeded()
        }
    )

    // Media Telemetry Selector
    var selectedMedia by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, "none") ?: "none")
    }
    PrefDropdownSelector(
        title = "MEDIA PLAYBACK TELEMETRY ROUTING",
        currentKey = selectedMedia,
        options = routingOptions,
        onSelected = { key ->
            selectedMedia = key
            prefs.edit().putString(LightspeedPreferences.KEY_RAIL_MEDIA_ROUTING, key).apply()
            if (key != "none" && !isNotifAccessGranted) {
                onShowNotificationAccessDialog()
            }
            safeReloadPreferences()
            onRefreshNeeded()
        }
    )
}
