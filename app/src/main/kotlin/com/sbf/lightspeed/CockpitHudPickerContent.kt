package com.sbf.lightspeed

import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.safeReloadPreferences

import com.sbf.lightspeed.system.defaultPrefs
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.IconPackInfo
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sbf.lightspeed.settings.LightspeedInfinityDialog
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme

/** Cockpit dialog content: HUD widget picker and timeline countdown composables (split from CockpitDialogActivity.kt). */
internal data class InstalledWidgetCandidate(
    val providerComponent: String,
    val packageName: String,
    val widgetLabel: String,
    val appLabel: String
)

@Composable
fun CategoryHudWidgetPickerContent(
    categoryId: String,
    categoryLabel: String,
    slotIndex: Int = 1,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }

    TacticalWidgetAccordionPicker(
        title = "$categoryLabel HUD",
        subtitle = "SELECT ANDROID WIDGET TO MOUNT INTO HUD",
        onDismiss = onDismiss,
        onSelectWidget = { providerInfo, widgetLabel, appLabel ->
            val candidate = InstalledWidgetCandidate(
                providerComponent = providerInfo.provider.flattenToShortString(),
                packageName = providerInfo.provider.packageName,
                widgetLabel = widgetLabel,
                appLabel = appLabel
            )
            val activity = context as? CockpitDialogActivity
            if (activity != null) {
                activity.allocateAndBindHudWidget(categoryId, categoryLabel, candidate, slotIndex)
            } else {
                val isSlot2 = (slotIndex == 2)
                val modeKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_MODE_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_MODE_PREFIX + categoryId
                val idKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_ID_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_ID_PREFIX + categoryId
                val labelKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_LABEL_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_LABEL_PREFIX + categoryId
                val pkgKey = if (isSlot2) com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_SLOT2_WIDGET_PKG_PREFIX + categoryId else com.sbf.lightspeed.system.LightspeedPreferences.KEY_CATEGORY_HUD_WIDGET_PKG_PREFIX + categoryId

                prefs.edit()
                    .putString(modeKey, LightspeedCruiseHudPods.MODE_ANDROID_WIDGET)
                    .putString(labelKey, "$appLabel · $widgetLabel")
                    .putString(pkgKey, providerInfo.provider.packageName)
                    .apply()
                safeReloadPreferences()
                val sTag = if (isSlot2) "Slot 2" else "HUD"
                Toast.makeText(context, "Bound $widgetLabel to $categoryLabel $sTag", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    )
}

@Composable
fun TimelineCountdownContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.defaultPrefs() }
    val initialTitle = remember { prefs.getString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, "") ?: "" }
    val initialTargetMs = remember {
        val saved = prefs.getLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, 0L)
        if (saved > System.currentTimeMillis()) saved else (System.currentTimeMillis() + 86400000L * 7)
    }

    var titleText by remember { mutableStateOf(initialTitle) }
    var targetMs by remember { mutableStateOf(initialTargetMs) }

    val dateFormat = remember { java.text.SimpleDateFormat("EEE, MMM dd, yyyy", java.util.Locale.getDefault()) }
    val formattedDate = remember(targetMs) { dateFormat.format(java.util.Date(targetMs)) }
    val daysRemaining = remember(targetMs) {
        val diff = targetMs - System.currentTimeMillis()
        (diff / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Timeline Countdown Target",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "Custom target date & remaining countdown for Screen Time HUD",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        OutlinedTextField(
            value = titleText,
            onValueChange = { titleText = it },
            label = { Text("Target Event / Goal Name") },
            placeholder = { Text("e.g. Vacation, Project Launch, New Year") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "TARGET DATE: $formattedDate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$daysRemaining days remaining",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "QUICK PRESETS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf(
                        "+1 Day" to 1,
                        "+7 Days" to 7,
                        "+30 Days" to 30,
                        "+90 Days" to 90
                    )
                    presets.forEach { (label, days) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    val cal = java.util.Calendar.getInstance().apply {
                                        add(java.util.Calendar.DAY_OF_YEAR, days)
                                        set(java.util.Calendar.HOUR_OF_DAY, 23)
                                        set(java.util.Calendar.MINUTE, 59)
                                    }
                                    targetMs = cal.timeInMillis
                                }
                        ) {
                            Text(
                                text = label,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Adjust Date:",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = {
                                val cal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = targetMs
                                    add(java.util.Calendar.DAY_OF_YEAR, -1)
                                }
                                if (cal.timeInMillis > System.currentTimeMillis()) {
                                    targetMs = cal.timeInMillis
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("-1d", fontSize = 10.5.sp)
                        }
                        Button(
                            onClick = {
                                val cal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = targetMs
                                    add(java.util.Calendar.DAY_OF_YEAR, 1)
                                }
                                targetMs = cal.timeInMillis
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+1d", fontSize = 10.5.sp)
                        }
                        Button(
                            onClick = {
                                val cal = java.util.Calendar.getInstance().apply {
                                    timeInMillis = targetMs
                                    add(java.util.Calendar.MONTH, 1)
                                }
                                targetMs = cal.timeInMillis
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("+1m", fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = {
                    prefs.edit()
                        .putBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, false)
                        .apply()
                    Toast.makeText(context, "Reset to standard timeline", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text(
                    "Reset to Year/Day",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = {
                        val name = titleText.trim().ifBlank { "TARGET" }
                        prefs.edit()
                            .putBoolean(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_ENABLED, true)
                            .putString(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TITLE, name)
                            .putLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_START_MS, System.currentTimeMillis())
                            .putLong(LightspeedPreferences.KEY_TIMELINE_COUNTDOWN_TARGET_MS, targetMs)
                            .apply()
                        Toast.makeText(context, "Countdown saved for $name", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Set Target", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
