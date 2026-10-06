package com.sbf.lightspeed.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.combinedClickable

import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.readTextOrKill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SystemOverrideDeckContents(
    context: Context,
    prefs: SharedPreferences
) {
    var isShizukuActive by remember { mutableStateOf(ElevatedTaskCloser.isShizukuActive) }
    
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
        } catch (e: Exception) { logSwallowed("SystemOverrideComponents", "SystemOverrideDeckContents:49", e) }
        
        onDispose {
            try {
                rikka.shizuku.Shizuku.removeBinderReceivedListener(listener)
                rikka.shizuku.Shizuku.removeBinderDeadListener(deadListener)
            } catch (e: Exception) { logSwallowed("SystemOverrideComponents", "SystemOverrideDeckContents:55", e) }
        }
    }
    val scope = rememberCoroutineScope()

    // State for Edge Gesture Sovereignty
    // State for Edge Gesture Sovereignty
    var edgeGestureScale by remember { mutableFloatStateOf(1.0f) }
    var animationScale by remember { mutableFloatStateOf(1.0f) }
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }
    var longPressDelay by remember { mutableFloatStateOf(400f) }

    // State for DPI confirm-or-revert dialog & countdown
    var showDpiDialog by rememberSaveable { mutableStateOf(false) }
    var dpiDeadlineMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var dpiRemainingSeconds by remember { mutableIntStateOf(15) }
    var originalDpiOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var savedPhysicalDpi by rememberSaveable { mutableStateOf<Float?>(null) }

    // State for Font Scale confirm-or-revert dialog & countdown
    var showFontDialog by rememberSaveable { mutableStateOf(false) }
    var fontDeadlineMillis by rememberSaveable { mutableStateOf<Long?>(null) }
    var fontRemainingSeconds by remember { mutableIntStateOf(15) }
    var originalFontScaleOverride by rememberSaveable { mutableStateOf<Float?>(null) }

    val revertDpi: () -> Unit = {
        val targetToRestore = originalDpiOverride
        if (targetToRestore != null) {
            originalDpiOverride = null
            showDpiDialog = false
            dpiDeadlineMillis = null

            val phys = savedPhysicalDpi ?: android.util.DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat()

            scope.launch(Dispatchers.IO) {
                ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_dpi_reve[r]t' || pkill -f 'ls_dpi_reve[r]t'")
                if (targetToRestore == "none") {
                    ElevatedTaskCloser.execShizuku("wm density reset")
                    withContext(Dispatchers.Main) {
                        displayDpi = phys
                    }
                } else {
                    ElevatedTaskCloser.execShizuku("wm density $targetToRestore")
                    withContext(Dispatchers.Main) {
                        displayDpi = targetToRestore.toFloatOrNull() ?: phys
                    }
                }
            }
        }
    }

    val keepDpi: () -> Unit = {
        originalDpiOverride = null
        showDpiDialog = false
        dpiDeadlineMillis = null
        scope.launch(Dispatchers.IO) {
            ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_dpi_reve[r]t' || pkill -f 'ls_dpi_reve[r]t'")
        }
    }

    val revertFontScale: () -> Unit = {
        val targetToRestore = originalFontScaleOverride
        if (targetToRestore != null) {
            originalFontScaleOverride = null
            showFontDialog = false
            fontDeadlineMillis = null

            scope.launch(Dispatchers.IO) {
                ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_font_reve[r]t' || pkill -f 'ls_font_reve[r]t'")
                ElevatedTaskCloser.execShizuku("settings put system font_scale $targetToRestore")
                withContext(Dispatchers.Main) {
                    fontScale = targetToRestore
                }
            }
        }
    }

    val keepFontScale: () -> Unit = {
        originalFontScaleOverride = null
        showFontDialog = false
        fontDeadlineMillis = null
        scope.launch(Dispatchers.IO) {
            ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_font_reve[r]t' || pkill -f 'ls_font_reve[r]t'")
        }
    }

    LaunchedEffect(dpiDeadlineMillis) {
        val deadline = dpiDeadlineMillis ?: return@LaunchedEffect
        while (true) {
            val remaining = (kotlin.math.max(0L, deadline - System.currentTimeMillis() + 999L) / 1000L).toInt()
            dpiRemainingSeconds = remaining
            if (remaining <= 0) {
                revertDpi()
                break
            }
            delay(200L)
        }
    }

    LaunchedEffect(fontDeadlineMillis) {
        val deadline = fontDeadlineMillis ?: return@LaunchedEffect
        while (true) {
            val remaining = (kotlin.math.max(0L, deadline - System.currentTimeMillis() + 999L) / 1000L).toInt()
            fontRemainingSeconds = remaining
            if (remaining <= 0) {
                revertFontScale()
                break
            }
            delay(200L)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                // Read from system settings directly
                val left = Settings.Secure.getFloat(context.contentResolver, "back_gesture_inset_scale_left", 1.0f)
                edgeGestureScale = left
                val anim = Settings.Global.getFloat(context.contentResolver, "window_animation_scale", 1.0f)
                animationScale = anim
                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)
                fontScale = fScale
                val lpDelay = Settings.Secure.getInt(context.contentResolver, "long_press_timeout", 400).toFloat()
                longPressDelay = lpDelay
            } catch (e: Exception) {
                logSwallowed("SystemOverrideComponents", "SystemOverrideDeckContents:80", e)
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
                .background(if (isShizukuActive) Color(0xFF00E676).copy(alpha = 0.1f) else MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (isShizukuActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    "ELEVATED EXECUTION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 0.6.sp
                )
                Text(
                    if (isShizukuActive) "SHIZUKU ACTIVE" else "SHIZUKU OFFLINE",
                    fontSize = 10.sp,
                    color = if (isShizukuActive) Color(0xFF00E676) else MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Tactical Native Back Conflict Neutralization Card
        if (edgeGestureScale > 0.01f) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFD32F2F).copy(alpha = 0.12f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "NATIVE BACK CONFLICT (${(edgeGestureScale * 100).toInt()}% SENSITIVITY)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFF5252),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Android's native back gesture sensitivity is currently active along the screen edges. This causes ghost system back triggers when swiping Deflectors. Zero-out native insets for 100% clean Lightspeed edge sovereignty.",
                        fontSize = 12.sp,
                        color = Color.LightGray.copy(alpha = 0.85f),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isShizukuActive) {
                                LightspeedHapticEngine.heavyClick(context)
                                edgeGestureScale = 0.0f
                                prefs.edit().putFloat("sys_override_edge_gesture", 0.0f).apply()
                                scope.launch(Dispatchers.IO) {
                                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left 0")
                                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right 0")
                                }
                                Toast.makeText(context, "⚡ Native back insets zeroed! Deflectors have full edge sovereignty.", Toast.LENGTH_SHORT).show()
                            } else {
                                val adbCmd = "adb shell settings put secure back_gesture_inset_scale_left 0 && adb shell settings put secure back_gesture_inset_scale_right 0"
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("ADB Command", adbCmd))
                                Toast.makeText(context, "ADB command copied to clipboard! Run in terminal to zero insets.", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isShizukuActive) Color(0xFFD32F2F) else Color(0xFF424242)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text(
                            text = if (isShizukuActive) "⚡ ZERO-OUT NATIVE BACK (RECOMMENDED)" else "📋 COPY ADB ZERO-OUT COMMAND",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF00E676).copy(alpha = 0.10f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.30f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00E676),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "FULL EDGE SOVEREIGNTY ACTIVE (0.0x INSETS)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF00E676),
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "Android native edge back gesture is fully neutralized. Zero touch-trapping or accidental back collisions.",
                            fontSize = 11.sp,
                            color = Color.LightGray.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

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
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_left ${edgeGestureScale}")
                    ElevatedTaskCloser.execShizuku("settings put secure back_gesture_inset_scale_right ${edgeGestureScale}")
                }
            }
        )

        // Lock Screen Behavior Selector in System Override
        // Granular Lock Screen Behavior Selectors in System Override
        var currentLockscreenMode by remember {
            mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_DEFLECTOR_MODE, "hide_deflectors") ?: "hide_deflectors")
        }
        var currentSensorMode by remember {
            mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_SENSOR_DECK_MODE, "hide") ?: "hide")
        }
        var currentRailMode by remember {
            mutableStateOf(prefs.getString(LightspeedPreferences.KEY_LOCKSCREEN_HORIZON_RAIL_MODE, "hide") ?: "hide")
        }

        var isLockscreenDropdownOpen by remember { mutableStateOf(false) }
        var isSensorDropdownOpen by remember { mutableStateOf(false) }
        var isRailDropdownOpen by remember { mutableStateOf(false) }

        val lockscreenOptions = listOf(
            "hide_deflectors" to "🙈 Hide Deflectors Only (Astrogation Core Active)",
            "keep_active" to "🛡️ Keep Deflectors Active on Lock Screen",
            "full_lockdown" to "🔒 Full Lockdown (Hide All Overlays)"
        )
        val binaryOptions = listOf(
            "hide" to "🙈 Hide on Lock Screen (Recommended)",
            "keep_active" to "🛡️ Keep Active on Lock Screen"
        )

        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = "LOCK SCREEN OVERLAY BEHAVIOR",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Granularly configure which overlay workspace components remain active while on the lock screen.",
                    fontSize = 11.sp,
                    color = Color.LightGray.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ℹ️ Note: Astrogation Core pill remains active in Hide Deflectors mode so you can launch tools or gears from the lock screen.",
                    fontSize = 10.5.sp,
                    color = Color(0xFF81D4FA)
                )

                // 1. Kinetic Deflectors Dropdown
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Kinetic Deflectors", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Box {
                    OutlinedButton(
                        onClick = { isLockscreenDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = lockscreenOptions.firstOrNull { it.first == currentLockscreenMode }?.second ?: "🙈 Hide Deflectors Only (Astrogation Core Active)",
                                color = Color.White,
                                fontSize = 11.5.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isLockscreenDropdownOpen,
                        onDismissRequest = { isLockscreenDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        lockscreenOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 44.dp),
                                text = { Text(label, fontSize = 12.5.sp) },
                                onClick = {
                                    isLockscreenDropdownOpen = false
                                    currentLockscreenMode = key
                                    prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_DEFLECTOR_MODE, key).apply()
                                    com.sbf.lightspeed.system.safeReloadPreferences()
                                }
                            )
                        }
                    }
                }

                // 2. Sensor Touch Area Dropdown
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "HUD Strip Sensor Area", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Box {
                    OutlinedButton(
                        onClick = { isSensorDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = binaryOptions.firstOrNull { it.first == currentSensorMode }?.second ?: "🙈 Hide on Lock Screen (Recommended)",
                                color = Color.White,
                                fontSize = 11.5.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isSensorDropdownOpen,
                        onDismissRequest = { isSensorDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        binaryOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 44.dp),
                                text = { Text(label, fontSize = 12.5.sp) },
                                onClick = {
                                    isSensorDropdownOpen = false
                                    currentSensorMode = key
                                    prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_SENSOR_DECK_MODE, key).apply()
                                    com.sbf.lightspeed.system.safeReloadPreferences()
                                }
                            )
                        }
                    }
                }

                // 3. Horizon Rail Dropdown
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Horizon Status Rail", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                Spacer(modifier = Modifier.height(2.dp))
                Box {
                    OutlinedButton(
                        onClick = { isRailDropdownOpen = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = binaryOptions.firstOrNull { it.first == currentRailMode }?.second ?: "🙈 Hide on Lock Screen (Recommended)",
                                color = Color.White,
                                fontSize = 11.5.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = isRailDropdownOpen,
                        onDismissRequest = { isRailDropdownOpen = false },
                        modifier = Modifier
                            .background(Color(0xF012141A))
                            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
                        shape = RoundedCornerShape(16.dp),
                        containerColor = Color(0xF012141A)
                    ) {
                        binaryOptions.forEach { (key, label) ->
                            DropdownMenuItem(
                                modifier = Modifier.heightIn(min = 44.dp),
                                text = { Text(label, fontSize = 12.5.sp) },
                                onClick = {
                                    isRailDropdownOpen = false
                                    currentRailMode = key
                                    prefs.edit().putString(LightspeedPreferences.KEY_LOCKSCREEN_HORIZON_RAIL_MODE, key).apply()
                                    com.sbf.lightspeed.system.safeReloadPreferences()
                                }
                            )
                        }
                    }
                }

                // 4. Orbital Capsule Dropdown (Disabled / Grayed Out)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Orbital Capsule (Camera Cutout Widget)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedButton(
                    onClick = { },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🚫 Disabled in Experimental Labs",
                            color = Color.Gray,
                            fontSize = 11.5.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 2. Animation Speeds
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

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 3. Display Metrics
        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_display_dpi",
            defaultValue = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat(),
            icon = Icons.Default.ScreenshotMonitor,
            title = "Display Metrics",
            subtitle = "On-the-fly PPI / DPI adjustments",
            value = displayDpi,
            valueRange = 200.0f..800.0f,
            steps = 59,
            valueFormatter = { "${it.toInt()} dpi" },
            isEnabled = isShizukuActive,
            onValueChange = { displayDpi = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                val targetDpiInt = displayDpi.toInt()
                scope.launch(Dispatchers.IO) {
                    var currentOriginal = originalDpiOverride
                    if (currentOriginal == null) {
                        val proc = ElevatedTaskCloser.execShizuku("wm density")
                        val output = proc.readTextOrKill(2000L)
                        val overrideMatch = Regex("""Override density:\s*(\d+)""", RegexOption.IGNORE_CASE).find(output ?: "")
                        val physicalMatch = Regex("""Physical density:\s*(\d+)""", RegexOption.IGNORE_CASE).find(output ?: "")

                        val rememberedOverride = overrideMatch?.groupValues?.get(1) ?: "none"
                        val physDpi = physicalMatch?.groupValues?.get(1)?.toFloatOrNull() ?: android.util.DisplayMetrics.DENSITY_DEVICE_STABLE.toFloat()

                        currentOriginal = rememberedOverride
                        withContext(Dispatchers.Main) {
                            originalDpiOverride = rememberedOverride
                            savedPhysicalDpi = physDpi
                        }
                    }

                    ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_dpi_reve[r]t' || pkill -f 'ls_dpi_reve[r]t'")
                    ElevatedTaskCloser.execShizuku("wm density $targetDpiInt")

                    val backupValue = when {
                        currentOriginal == "none" -> "reset"
                        currentOriginal?.toIntOrNull() != null -> currentOriginal?.toIntOrNull()?.toString()
                        else -> null
                    }
                    if (backupValue != null) {
                        ElevatedTaskCloser.execShizuku("nohup sh -c \"sleep 20; wm density $backupValue # ls_dpi_revert\" >/dev/null 2>&1 &")
                    }

                    withContext(Dispatchers.Main) {
                        dpiDeadlineMillis = System.currentTimeMillis() + 15000L
                        showDpiDialog = true
                    }
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        // 4. Font Scale
        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_font_scale",
            defaultValue = 1.0f,
            icon = Icons.Default.FontDownload,
            title = "Font Scale",
            subtitle = "Tactile slider for system FONT_SCALE override",
            value = fontScale,
            valueRange = 0.5f..2.0f,
            steps = 14,
            isEnabled = isShizukuActive,
            onValueChange = { fontScale = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    var currentOriginal = originalFontScaleOverride
                    if (currentOriginal == null) {
                        val proc = ElevatedTaskCloser.execShizuku("settings get system font_scale")
                        val output = proc.readTextOrKill(2000L)?.trim()
                        val remembered = output?.toFloatOrNull() ?: 1.0f
                        currentOriginal = remembered
                        withContext(Dispatchers.Main) {
                            originalFontScaleOverride = remembered
                        }
                    }

                    ElevatedTaskCloser.execShizuku("pkill -9 -f 'ls_font_reve[r]t' || pkill -f 'ls_font_reve[r]t'")
                    ElevatedTaskCloser.execShizuku("settings put system font_scale ${fontScale}")

                    val backupValFloat = currentOriginal.toFloat()
                    if (backupValFloat.isFinite() && backupValFloat in 0.5f..3.0f) {
                        val backupValueStr = String.format(java.util.Locale.US, "%.2f", backupValFloat)
                        ElevatedTaskCloser.execShizuku("nohup sh -c \"sleep 20; settings put system font_scale $backupValueStr # ls_font_revert\" >/dev/null 2>&1 &")
                    }

                    withContext(Dispatchers.Main) {
                        fontDeadlineMillis = System.currentTimeMillis() + 15000L
                        showFontDialog = true
                    }
                }
            }
        )
        
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)
        
        // 5. Custom Long-Press Delay
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

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

        if (showDpiDialog) {
            AlertDialog(
                onDismissRequest = {
                    revertDpi()
                },
                title = {
                    Text(
                        text = "Keep this setting?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Reverting in $dpiRemainingSeconds second${if (dpiRemainingSeconds == 1) "" else "s"}..."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            keepDpi()
                        }
                    ) {
                        Text("Keep")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            revertDpi()
                        }
                    ) {
                        Text("Revert")
                    }
                }
            )
        }

        if (showFontDialog) {
            AlertDialog(
                onDismissRequest = {
                    revertFontScale()
                },
                title = {
                    Text(
                        text = "Keep this setting?",
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Reverting in $fontRemainingSeconds second${if (fontRemainingSeconds == 1) "" else "s"}..."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            keepFontScale()
                        }
                    ) {
                        Text("Keep")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            revertFontScale()
                        }
                    ) {
                        Text("Revert")
                    }
                }
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OverrideSliderRow(
    context: Context,
    prefs: SharedPreferences,
    sliderKey: String,
    defaultValue: Float,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    isEnabled: Boolean,
    valueFormatter: (Float) -> String = { String.format("%.2fx", it) },
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    var isFlyoutOpen by remember { mutableStateOf(false) }
    var isTapToJumpEnabled by remember { mutableStateOf(prefs.getBoolean("pref_slider_tap_to_jump_$sliderKey", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.4f)
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.2f)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        enabled = isEnabled,
                        onClick = {
                            LightspeedHapticEngine.tick(context)
                            onValueChange(defaultValue)
                            onValueChangeFinished()
                        },
                        onLongClick = {
                            LightspeedHapticEngine.heavyClick(context)
                            isFlyoutOpen = true
                        }
                    )
            ) {
                Text(
                    text = valueFormatter(value),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        DragOnlySlider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = steps,
            enabled = isEnabled,
            tapToJump = isTapToJumpEnabled,
            modifier = Modifier.fillMaxWidth().height(36.dp)
        )
    }

    if (isFlyoutOpen) {
        SliderCalibrationFlyoutDialog(
            title = title,
            sliderKey = sliderKey,
            currentValueStr = value.toString(),
            defaultValueStr = defaultValue.toString(),
            onValueTyped = { typed ->
                typed.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(valueRange))
                }
            },
            onResetToDefault = {
                onValueChange(defaultValue)
                onValueChangeFinished()
            },
            onSelectProfile = { profileValueStr ->
                profileValueStr.toFloatOrNull()?.let { f ->
                    onValueChange(f.coerceIn(valueRange))
                    onValueChangeFinished()
                }
            },
            onDismiss = {
                isFlyoutOpen = false
                onValueChangeFinished()
            },
            prefs = prefs,
            context = context,
            isTapToJumpEnabled = isTapToJumpEnabled,
            onTapToJumpChanged = {
                isTapToJumpEnabled = it
                prefs.edit().putBoolean("pref_slider_tap_to_jump_$sliderKey", it).apply()
            }
        )
    }
}
