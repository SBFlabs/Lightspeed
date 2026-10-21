package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.ShizukuGate
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Master System Override Deck coordinator connecting Shizuku elevated operations,
 * edge gesture conflict neutralization, animation timings, display metrics, and lockscreen policies.
 */
@Composable
fun SystemOverrideDeckContents(
    context: Context,
    prefs: SharedPreferences
) {
    val lsColors = LightspeedStyle.colors
    var isShizukuActive by remember { mutableStateOf(ElevatedTaskCloser.isShizukuActive) }
    var accOnly by remember { mutableStateOf(ShizukuGate.isAccessibilityOnly()) }
    var permissionRemoved by remember { mutableStateOf(checkShizukuPermissionRemoved(accOnly)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isShizukuActive = ElevatedTaskCloser.isShizukuActive
                permissionRemoved = checkShizukuPermissionRemoved(accOnly)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        val listener = rikka.shizuku.Shizuku.OnBinderReceivedListener {
            isShizukuActive = ElevatedTaskCloser.isShizukuActive
        }
        val deadListener = rikka.shizuku.Shizuku.OnBinderDeadListener {
            isShizukuActive = false
        }
        try {
            rikka.shizuku.Shizuku.addBinderReceivedListenerSticky(listener)
            rikka.shizuku.Shizuku.addBinderDeadListener(deadListener)
        } catch (e: Exception) { logSwallowed("SystemOverrideComponents", "BinderListenerAdd", e) }

        onDispose {
            try {
                rikka.shizuku.Shizuku.removeBinderReceivedListener(listener)
                rikka.shizuku.Shizuku.removeBinderDeadListener(deadListener)
            } catch (e: Exception) { logSwallowed("SystemOverrideComponents", "BinderListenerRemove", e) }
        }
    }
    val scope = rememberCoroutineScope()

    // State for Edge Gesture Sovereignty & System Overrides
    var edgeGestureScale by remember { mutableFloatStateOf(1.0f) }
    var animationScale by remember { mutableFloatStateOf(1.0f) }
    var longPressDelay by remember { mutableFloatStateOf(400f) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val left = Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                edgeGestureScale = left
                val anim = Settings.Global.getFloat(context.contentResolver, "window_animation_scale", 1.0f)
                animationScale = anim
                val lpDelay = Settings.Secure.getInt(context.contentResolver, "long_press_timeout", 400).toFloat()
                longPressDelay = lpDelay
            } catch (e: Exception) {
                logSwallowed("SystemOverrideComponents", "ReadSystemSettings", e)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isShizukuActive) lsColors.accentOk.copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (isShizukuActive) lsColors.accentOk else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "ELEVATED EXECUTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = lsColors.textPrimary,
                    letterSpacing = 0.6.sp
                )
                Text(
                    if (isShizukuActive) "SHIZUKU ACTIVE" else "SHIZUKU OFFLINE",
                    fontSize = 10.sp,
                    color = if (isShizukuActive) lsColors.accentOk else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Accessibility Service only switch
        DisposableEffect(Unit) {
            val listener = rikka.shizuku.Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
                if (requestCode == ElevatedTaskCloser.SHIZUKU_REQ_CODE) {
                    if (grantResult != PackageManager.PERMISSION_GRANTED) {
                        accOnly = true
                        ShizukuGate.chooseAccessibilityOnly()
                    }
                    isShizukuActive = ElevatedTaskCloser.isShizukuActive
                    permissionRemoved = checkShizukuPermissionRemoved(accOnly)
                }
            }
            try {
                rikka.shizuku.Shizuku.addRequestPermissionResultListener(listener)
            } catch (_: Exception) {}
            onDispose {
                try {
                    rikka.shizuku.Shizuku.removeRequestPermissionResultListener(listener)
                } catch (_: Exception) {}
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable {
                    val next = !accOnly
                    accOnly = next
                    if (next) ShizukuGate.chooseAccessibilityOnly() else ShizukuGate.chooseFull()
                    permissionRemoved = checkShizukuPermissionRemoved(next)
                    isShizukuActive = ElevatedTaskCloser.isShizukuActive
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Accessibility Service only (no Shizuku)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = lsColors.textPrimary
                )
                Text(
                    "Lightspeed will never ask for Shizuku. Features that need it will say so.",
                    fontSize = 10.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.7f)
                )
                if (permissionRemoved) {
                    Text(
                        "Shizuku access was removed. Falling back to Accessibility Service only until you allow it again in your Shizuku app or fork.",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            Switch(
                checked = accOnly,
                onCheckedChange = { checked ->
                    accOnly = checked
                    if (checked) ShizukuGate.chooseAccessibilityOnly() else ShizukuGate.chooseFull()
                    permissionRemoved = checkShizukuPermissionRemoved(checked)
                    isShizukuActive = ElevatedTaskCloser.isShizukuActive
                },
                modifier = Modifier.padding(start = 8.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = lsColors.textPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = lsColors.textPrimary.copy(alpha = 0.75f),
                    uncheckedTrackColor = lsColors.textPrimary.copy(alpha = 0.12f)
                )
            )
        }

        // Tactical Native Back Conflict Neutralization Card
        SystemOverrideBackConflictCard(
            context = context,
            prefs = prefs,
            edgeGestureScale = edgeGestureScale,
            isShizukuActive = isShizukuActive,
            scope = scope,
            onScaleChanged = { edgeGestureScale = it }
        )

        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_edge_gesture",
            defaultValue = 1.0f,
            icon = Icons.Default.VerticalDistribute,
            title = "Native Edge Gesture Sovereignty",
            subtitle = "Force back-gesture insets to 0 to allow Lightspeed full edge control",
            value = edgeGestureScale,
            valueRange = 0.0f..2.0f,
            steps = 19,
            isEnabled = isShizukuActive,
            onValueChange = { edgeGestureScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                prefs.edit().putFloat("sys_override_edge_gesture", edgeGestureScale).apply()
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left ${edgeGestureScale}")
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right ${edgeGestureScale}")
                }
            }
        )

        // Lockscreen Overlay Policies
        SystemOverrideLockscreenSection(prefs = prefs)

        HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Animation Speeds
        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_animation_speed",
            defaultValue = 1.0f,
            icon = Icons.Default.Speed,
            title = "Animation Speeds",
            subtitle = "Global master scale & Window/Transition/Animator subdomains",
            value = animationScale,
            valueRange = 0.0f..2.0f,
            steps = 19,
            isEnabled = isShizukuActive,
            onValueChange = { animationScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put global window_animation_scale ${animationScale}")
                    ElevatedTaskCloser.execShizuku("settings put global transition_animation_scale ${animationScale}")
                    ElevatedTaskCloser.execShizuku("settings put global animator_duration_scale ${animationScale}")
                }
            }
        )

        HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Display Metrics & Font Scale (with 15-second countdown guard)
        SystemOverrideDisplayMetrics(
            context = context,
            prefs = prefs,
            isShizukuActive = isShizukuActive,
            scope = scope
        )

        HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Custom Long-Press Delay
        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_long_press_delay",
            defaultValue = 400f,
            icon = Icons.Default.TouchApp,
            title = "Long-Press Delay",
            subtitle = "Override system-wide touch latency (Settings.Secure.LONG_PRESS_TIMEOUT)",
            value = longPressDelay,
            valueRange = 150f..1000f,
            steps = 84, // 10ms increments
            valueFormatter = { "${it.toInt()} ms" },
            isEnabled = isShizukuActive,
            onValueChange = { longPressDelay = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put secure long_press_timeout ${longPressDelay.toInt()}")
                }
            }
        )
    }
}

private fun checkShizukuPermissionRemoved(accOnly: Boolean): Boolean {
    if (accOnly) return false
    return try {
        rikka.shizuku.Shizuku.pingBinder() &&
            rikka.shizuku.Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}
