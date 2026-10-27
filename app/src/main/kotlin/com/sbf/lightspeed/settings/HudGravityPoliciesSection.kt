package com.sbf.lightspeed.settings

import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.safeReloadPreferences
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun HudGravityPoliciesSection(
    prefs: SharedPreferences,
    onRefreshNeeded: () -> Unit
) {
    val lsColors = LightspeedStyle.colors

    // 1. Action Override Lifetime (Manual Gesture vs App Bucket)
    val currentOverrideExpiration = prefs.getString(
        LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION,
        LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
    ) ?: LightspeedPreferences.DEFAULT_ORIENTATION_OVERRIDE_EXPIRATION
    var isOverrideExpirationDropdownOpen by remember { mutableStateOf(false) }
    val overrideExpirationOptions = listOf(
        LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_APP_SWITCH to "Until App Switch (Temporary)",
        LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_UNTIL_SCREEN_OFF to "Until Screen Off / Lock",
        LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_PERSISTENT to "Persistent (Won't Reset / Manual Only)",
        LightspeedPreferences.ORIENTATION_OVERRIDE_EXPIRATION_DISABLED to "Disabled (Action Won't Work / Buckets Only)"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("MANUAL ACTION OVERRIDE DURATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Text("When you trigger an on-the-fly orientation action via gestures or deflector, choose how long it stays active before returning to bucket/system defaults:", fontSize = 10.sp, color = lsColors.textSecondary.copy(alpha = 0.75f), lineHeight = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { isOverrideExpirationDropdownOpen = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = overrideExpirationOptions.firstOrNull { it.first == currentOverrideExpiration }?.second ?: "Persistent (Won't Reset / Manual Only)",
                        color = lsColors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            DeckThemedDropdownMenu(
                expanded = isOverrideExpirationDropdownOpen,
                onDismissRequest = { isOverrideExpirationDropdownOpen = false }
            ) {
                overrideExpirationOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(label) },
                        onClick = {
                            isOverrideExpirationDropdownOpen = false
                            prefs.edit().putString(LightspeedPreferences.KEY_ORIENTATION_OVERRIDE_EXPIRATION, key).apply()
                            onRefreshNeeded()
                        }
                    )
                }
            }
        }
    }

    // 3. Manual Native Rotation Policy
    val currentNativePolicy = prefs.getString(
        LightspeedPreferences.KEY_MANUAL_NATIVE_ROTATION_POLICY,
        LightspeedPreferences.MANUAL_NATIVE_POLICY_BUCKET_WINS
    ) ?: LightspeedPreferences.MANUAL_NATIVE_POLICY_BUCKET_WINS
    var isNativePolicyDropdownOpen by remember { mutableStateOf(false) }
    val nativePolicyOptions = listOf(
        LightspeedPreferences.MANUAL_NATIVE_POLICY_BUCKET_WINS to "Bucket wins (default)",
        LightspeedPreferences.MANUAL_NATIVE_POLICY_UNTIL_APP_SWITCH to "Manual wins until app switch",
        LightspeedPreferences.MANUAL_NATIVE_POLICY_UNTIL_SCREEN_LOCK to "Manual wins until screen lock"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("MANUAL NATIVE ROTATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Text("If you flip the system auto-rotate toggle while an app bucket is active, choose who wins:", fontSize = 10.sp, color = lsColors.textSecondary.copy(alpha = 0.75f), lineHeight = 13.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Box {
            OutlinedButton(
                onClick = { isNativePolicyDropdownOpen = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = nativePolicyOptions.firstOrNull { it.first == currentNativePolicy }?.second ?: "Bucket wins (default)",
                        color = lsColors.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            DeckThemedDropdownMenu(
                expanded = isNativePolicyDropdownOpen,
                onDismissRequest = { isNativePolicyDropdownOpen = false }
            ) {
                nativePolicyOptions.forEach { (key, label) ->
                    DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        text = { Text(label) },
                        onClick = {
                            isNativePolicyDropdownOpen = false
                            prefs.edit().putString(LightspeedPreferences.KEY_MANUAL_NATIVE_ROTATION_POLICY, key).apply()
                            onRefreshNeeded()
                        }
                    )
                }
            }
        }
    }
}
