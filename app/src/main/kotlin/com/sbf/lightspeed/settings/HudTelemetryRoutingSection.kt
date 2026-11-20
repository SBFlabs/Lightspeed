package com.sbf.lightspeed.settings

import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.sbf.lightspeed.ui.theme.LightspeedStyle
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
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(lsString(R.string.tel_dual_hdr), fontWeight = FontWeight.Bold, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.primary, letterSpacing = 0.8.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("📏 Horizon Rail", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LightspeedStyle.colors.textPrimary)
                    Text(lsString(R.string.tel_hr_sub), fontSize = 10.5.sp, color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.85f))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("💊 Orbital Capsule", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = LightspeedStyle.colors.textPrimary)
                    Text(lsString(R.string.tel_cap_sub), fontSize = 10.5.sp, color = LightspeedStyle.colors.textSecondary.copy(alpha = 0.85f))
                }
            }
        }
    }



    if (!isNotifAccessGranted) {
        DeckCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
            onClick = { onShowNotificationAccessDialog() }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(lsString(R.string.cc_dlg_notif_req), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = LightspeedStyle.colors.textPrimary)
                    Text(lsString(R.string.tel_notif_grant), fontSize = 11.sp, color = LightspeedStyle.colors.textPrimary.copy(alpha = 0.85f))
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = LightspeedStyle.colors.textPrimary.copy(alpha = 0.6f))
            }
        }
    }

    val routingOptions = listOf(
        "none" to lsString(R.string.tel_none),
        "top_line" to lsString(R.string.tel_hr_opt),
        "notch_pill" to lsString(R.string.tel_cap_opt),
        "both" to lsString(R.string.tel_both_opt)
    )

    // Downloads Telemetry Selector
    var selectedDl by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(prefs.getString(LightspeedPreferences.KEY_RAIL_DOWNLOADS_ROUTING, "top_line") ?: "top_line")
    }
    PrefDropdownSelector(
        title = lsString(R.string.tel_dl_hdr),
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
        title = lsString(R.string.tel_med_hdr),
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
