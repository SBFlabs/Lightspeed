package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedDeepShortcutHelper
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun DeepShortcutsPinDialogContent(
    context: Context,
    packageName: String,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val shortcuts = remember(packageName) {
        LightspeedDeepShortcutHelper.queryAppDeepShortcuts(context, packageName)
    }

    DeckCard(
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .padding(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SHORTCUTS & TACTICAL PIN",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = lsColors.textPrimary
                    )
                    Text(
                        packageName.substringAfterLast('.'),
                        fontSize = 11.sp,
                        color = lsColors.accentInfo
                    )
                }
                DeckButton(text = "Close", onClick = onDismiss)
            }

            // Quick Pin Row
            Text("TACTICAL PIN ACTIONS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.textSecondary)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DeckButton(
                    text = "Cockpit Gear",
                    icon = Icons.Default.PushPin,
                    onClick = {
                        LightspeedDeepShortcutHelper.pinToCockpitGear(context, "app:$packageName")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                DeckButton(
                    text = "Space Pod 1",
                    icon = Icons.Default.Adjust,
                    onClick = {
                        LightspeedDeepShortcutHelper.pinToSpacePod(context, 1, "app:$packageName")
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Live Deep Shortcuts List
            Text("LIVE DEEP SHORTCUTS (${shortcuts.size})", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = lsColors.textSecondary)
            if (shortcuts.isEmpty()) {
                Text(
                    "No dynamic or manifest shortcuts reported by system.",
                    fontSize = 10.5.sp,
                    color = lsColors.textSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(shortcuts) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    LightspeedDeepShortcutHelper.launchDeepShortcut(context, entry.packageName, entry.id)
                                    onDismiss()
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.shortLabel, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = lsColors.textPrimary)
                                if (entry.longLabel != entry.shortLabel) {
                                    Text(entry.longLabel, fontSize = 9.5.sp, color = lsColors.textSecondary)
                                }
                            }
                            Icon(Icons.AutoMirrored.Filled.Launch, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
