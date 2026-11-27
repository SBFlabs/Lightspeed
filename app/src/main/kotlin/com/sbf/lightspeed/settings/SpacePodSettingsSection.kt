package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.lsString

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sbf.lightspeed.CockpitGearPickerActivity
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.ActionDispatcher
import com.sbf.lightspeed.system.LightspeedLocaleEngine
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.SpacePodGestureDispatcher
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun SpacePodSettingsSection(
    context: Context,
    prefs: SharedPreferences,
    onStateChanged: () -> Unit = {}
) {
    val lsColors = LightspeedStyle.colors
    var selectedPod by remember { mutableIntStateOf(1) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DeckCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Adjust, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(lsString(R.string.pod_hdr), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
                    Text(lsString(R.string.pod_sub), fontSize = 10.sp, color = lsColors.textSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Pod Selector Pills (1 to 4)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..4).forEach { index ->
                    DeckButton(
                        text = lsString(R.string.pod_btn_upper_fmt, index),
                        onClick = { selectedPod = index },
                        isActive = selectedPod == index,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Mapping Rows for Selected Pod
            SpacePodActionRow(context, prefs, selectedPod, SpacePodGestureDispatcher.GESTURE_CLICK, lsString(R.string.pod_g_click))
            SpacePodActionRow(context, prefs, selectedPod, SpacePodGestureDispatcher.GESTURE_DOUBLE_CLICK, lsString(R.string.pod_g_dclick))
            SpacePodActionRow(context, prefs, selectedPod, SpacePodGestureDispatcher.GESTURE_HOLD, lsString(R.string.pod_g_hold))
            SpacePodActionRow(context, prefs, selectedPod, SpacePodGestureDispatcher.GESTURE_CLICK_THEN_HOLD, lsString(R.string.pod_g_click_hold))

            Spacer(Modifier.height(10.dp))

            // Pin to Home Screen Action
            DeckButton(
                text = lsString(R.string.pod_pin_btn_fmt, selectedPod),
                icon = Icons.Default.Launch,
                onClick = { pinPodToHomeScreen(context, selectedPod) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SpacePodActionRow(
    context: Context,
    prefs: SharedPreferences,
    podIndex: Int,
    gesture: String,
    title: String
) {
    val lsColors = LightspeedStyle.colors
    val prefKey = SpacePodGestureDispatcher.getActionPrefKey(podIndex, gesture)
    var currentToken by remember(podIndex, gesture) {
        mutableStateOf(SpacePodGestureDispatcher.getActionToken(context, podIndex, gesture))
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            currentToken = SpacePodGestureDispatcher.getActionToken(context, podIndex, gesture)
        }
    }

    val displayAction = remember(currentToken, LightspeedLocaleEngine.state) {
        val tok = currentToken
        if (tok.isNullOrBlank()) lsString(R.string.pod_tap_to_cfg)
        else LightspeedShortcutManager.resolveLabel(context, tok)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable {
                val intent = Intent(context, CockpitGearPickerActivity::class.java).apply {
                    putExtra("SINGLE_SELECT_PREF_KEY", prefKey)
                    putExtra("SINGLE_SELECT_TITLE", lsString(R.string.pod_picker_title_fmt, podIndex, title))
                    putExtra("IS_HOLD_GESTURE", gesture == SpacePodGestureDispatcher.GESTURE_HOLD || gesture == SpacePodGestureDispatcher.GESTURE_CLICK_THEN_HOLD)
                }
                launcher.launch(intent)
            },
        color = lsColors.borderSubtle.copy(alpha = 0.08f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
            Text(displayAction.take(24), fontSize = 10.sp, color = lsColors.accentInfo)
        }
    }
}

private fun pinPodToHomeScreen(context: Context, podIndex: Int) {
    if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        val intent = Intent(context, SpacePodShortcutActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(SpacePodShortcutActivity.EXTRA_POD_INDEX, podIndex)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val shortcutInfo = ShortcutInfoCompat.Builder(context, "space_pod_$podIndex")
            .setShortLabel(lsString(R.string.pod_num_fmt, podIndex))
            .setLongLabel(lsString(R.string.pod_shortcut_long_fmt, podIndex))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_qs_flight_armed))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)
        Toast.makeText(context, lsString(R.string.pod_req_home_pin_fmt, podIndex), Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, lsString(R.string.pod_pin_unsup), Toast.LENGTH_SHORT).show()
    }
}
