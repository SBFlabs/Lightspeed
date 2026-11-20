package com.sbf.lightspeed.settings

import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.annotation.StringRes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.BuildConfig
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.InstalledTacticalToolsScanner
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private data class WarmupStageSpec(
    @StringRes val titleRes: Int,
    @StringRes val detailRes: Int,
    val targetProgress: Float
)

private val WARMUP_STAGES = listOf(
    WarmupStageSpec(
        titleRes = R.string.warmup_stage_1_title,
        detailRes = R.string.warmup_stage_1_detail,
        targetProgress = 0.28f
    ),
    WarmupStageSpec(
        titleRes = R.string.warmup_stage_2_title,
        detailRes = R.string.warmup_stage_2_detail,
        targetProgress = 0.56f
    ),
    WarmupStageSpec(
        titleRes = R.string.warmup_stage_3_title,
        detailRes = R.string.warmup_stage_3_detail,
        targetProgress = 0.84f
    ),
    WarmupStageSpec(
        titleRes = R.string.warmup_stage_4_title,
        detailRes = R.string.warmup_stage_4_detail,
        targetProgress = 1.0f
    )
)

/**
 * Full-window post-install / post-update engine ignition overlay inside Central Command.
 * Executes lightweight real initialization steps while staging settings tabs underneath,
 * and gives the user an immediate bypass button at any moment.
 */
@Composable
fun CentralCommandWarmupPanel(
    context: Context,
    canStageDeck: Boolean = true,
    onStageDeck: () -> Unit,
    onWarmupComplete: () -> Unit,
    onProceedAnyway: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    var currentStageIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0.08f) }
    var statusSublineRes by remember { mutableIntStateOf(WARMUP_STAGES[0].detailRes) }
    val currentCanStageDeck by rememberUpdatedState(canStageDeck)

    LaunchedEffect(Unit) {
        // Stage 0: Optical snapshot & theme readiness (keeps light card animating cleanly)
        currentStageIndex = 0
        progress = WARMUP_STAGES[0].targetProgress
        DeckThemeManager.getSnapshot(context)
        delay(150L)

        // Stage 1: Watchdogs & Shizuku status verification off Main thread
        currentStageIndex = 1
        statusSublineRes = WARMUP_STAGES[1].detailRes
        val shizukuNoteRes = withContext(Dispatchers.IO) {
            val a11yReady = LightspeedWatchdogEngine.isAccessibilityServiceEnabled(context)
            val shizukuReady = try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
            when {
                a11yReady && shizukuReady -> R.string.warmup_note_both
                a11yReady -> R.string.warmup_note_a11y
                else -> R.string.warmup_note_dormant
            }
        }
        statusSublineRes = shizukuNoteRes
        progress = WARMUP_STAGES[1].targetProgress
        delay(140L)

        // Stage 2: Resolve only active configured gesture tokens + installed launcher labels + tools
        currentStageIndex = 2
        statusSublineRes = WARMUP_STAGES[2].detailRes
        val activeRawTokens = withContext(Dispatchers.IO) {
            val prefs = context.defaultPrefs()
            prefs.all
                .filterKeys { it.startsWith("pref_macro_action_") || it.startsWith("pref_key_") }
                .values
                .mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotBlank() } }
        }
        LightspeedActionRegistry.initializeAsync(context, activeRawTokens)
        withContext(Dispatchers.IO) {
            InstalledTacticalToolsScanner.scan(context)
        }
        progress = WARMUP_STAGES[2].targetProgress

        // Ensure slide-up entrance completes before inflating the heavy Pager tree underneath
        while (!currentCanStageDeck) {
            delay(16L)
        }

        // Stage 3: Stage hidden Central Command pager behind this curtain so JIT & Compose warm up
        currentStageIndex = 3
        statusSublineRes = WARMUP_STAGES[3].detailRes
        onStageDeck()
        repeat(5) {
            withFrameNanos { }
        }
        CentralCommandWarmupCoordinator.markUiWarmed()
        progress = 1.0f
        delay(120L)
        onWarmupComplete()
    }

    DeckGlassCardSurface(
        modifier = Modifier.fillMaxSize(),
        isFlyout = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(lsColors.accent.copy(alpha = 0.14f))
                    .border(0.8.dp, lsColors.accent.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = lsString(R.string.warmup_badge_version, BuildConfig.VERSION_NAME),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = lsColors.accent,
                    letterSpacing = 0.9.sp
                )
            }

            // Center turbine animation + checklist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WarmupThrusterRing(progress = progress)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = lsString(WARMUP_STAGES[currentStageIndex].titleRes),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = lsColors.textPrimary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = lsString(statusSublineRes),
                        fontSize = 11.sp,
                        color = lsColors.accentInfo,
                        textAlign = TextAlign.Center
                    )
                }

                // Compact live stage checklist
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(lsColors.textPrimary.copy(alpha = 0.04f))
                        .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    WARMUP_STAGES.forEachIndexed { idx, stage ->
                        val isDone = idx < currentStageIndex || progress >= 1f
                        val isActive = idx == currentStageIndex && progress < 1f
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val dotColor = when {
                                isDone -> lsColors.accent
                                isActive -> lsColors.accentInfo
                                else -> lsColors.textPrimary.copy(alpha = 0.25f)
                            }
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(dotColor)
                            )
                            Text(
                                text = lsString(stage.titleRes),
                                fontSize = 11.sp,
                                fontWeight = if (isActive || isDone) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isDone -> lsColors.textPrimary.copy(alpha = 0.90f)
                                    isActive -> lsColors.accentInfo
                                    else -> lsColors.textPrimary.copy(alpha = 0.45f)
                                },
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when {
                                    isDone -> lsString(R.string.warmup_status_ready)
                                    isActive -> "..."
                                    else -> lsString(R.string.warmup_status_queued)
                                },
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isDone -> lsColors.accent
                                    isActive -> lsColors.accentInfo
                                    else -> lsColors.textPrimary.copy(alpha = 0.35f)
                                }
                            )
                        }
                    }
                }
            }

            // Bottom bypass button + explicit caution caption
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onStageDeck()
                        onProceedAnyway()
                        Toast.makeText(
                            context,
                            lsString(R.string.warmup_toast_bypass),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.9.dp, lsColors.textPrimary.copy(alpha = 0.26f))
                ) {
                    Text(
                        text = lsString(R.string.warmup_btn_proceed),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.textPrimary
                    )
                }
                Text(
                    text = lsString(R.string.warmup_caution),
                    fontSize = 10.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.58f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun WarmupThrusterRing(progress: Float) {
    val lsColors = LightspeedStyle.colors
    val accent = lsColors.accent
    val accentInfo = lsColors.accentInfo
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "warmup_progress"
    )
    val transition = rememberInfiniteTransition(label = "warmup_spin")
    val spinDeg by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "warmup_ring_spin"
    )

    Box(
        modifier = Modifier.size(104.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = size.minDimension * 0.44f
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f

            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = r,
                style = Stroke(width = 4.dp.toPx())
            )
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
            rotate(degrees = spinDeg, pivot = center) {
                drawArc(
                    color = accentInfo.copy(alpha = 0.62f),
                    startAngle = 0f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.72f, cy - r * 0.72f),
                    size = Size(r * 1.44f, r * 1.44f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accentInfo.copy(alpha = 0.62f),
                    startAngle = 180f,
                    sweepAngle = 70f,
                    useCenter = false,
                    topLeft = Offset(cx - r * 0.72f, cy - r * 0.72f),
                    size = Size(r * 1.44f, r * 1.44f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Text(
            text = "${(animatedProgress * 100f).toInt()}%",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            color = lsColors.textPrimary
        )
    }
}
