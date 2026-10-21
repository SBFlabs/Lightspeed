package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.ScreenshotMonitor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.system.readTextOrKill
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Tactical Display Metrics (DPI) and Font Scale override sliders with
 * automatic 15-second revert countdown guard to prevent device soft-locks.
 */
@Composable
fun SystemOverrideDisplayMetrics(
    context: Context,
    prefs: SharedPreferences,
    isShizukuActive: Boolean,
    scope: CoroutineScope
) {
    val lsColors = LightspeedStyle.colors
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }

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
                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)
                fontScale = fScale
            } catch (e: Exception) {
                logSwallowed("SystemOverrideDisplayMetrics", "ReadFontScale", e)
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Display Metrics
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

        HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.08f), thickness = 0.8.dp)

        // Font Scale
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
    }

    if (showDpiDialog) {
        DeckDialog(
            onDismissRequest = revertDpi,
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
                TextButton(onClick = keepDpi) {
                    Text("Keep")
                }
            },
            dismissButton = {
                TextButton(onClick = revertDpi) {
                    Text("Revert")
                }
            }
        )
    }

    if (showFontDialog) {
        DeckDialog(
            onDismissRequest = revertFontScale,
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
                TextButton(onClick = keepFontScale) {
                    Text("Keep")
                }
            },
            dismissButton = {
                TextButton(onClick = revertFontScale) {
                    Text("Revert")
                }
            }
        )
    }
}
