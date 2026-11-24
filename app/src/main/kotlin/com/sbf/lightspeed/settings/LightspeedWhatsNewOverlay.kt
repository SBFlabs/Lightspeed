package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.BuildConfig
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import kotlinx.coroutines.delay

/** Provides a callback into nested Settings sections so manual "What's New" morphs the full Central Command window. */
val LocalOpenWhatsNew = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Nightly-only callback to preview the full Post-Update What's New -> Engine Warm-Up Gate flow on demand. */
val LocalTestWarmupGate = staticCompositionLocalOf<(() -> Unit)?> { null }

/** Nightly-only callback to replay the Arm Lightspeed Core setup gate in a simulated, side-effect-free preview. */
val LocalTestCoreSetupGate = staticCompositionLocalOf<(() -> Unit)?> { null }

internal fun openExternalUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, lsString(R.string.wn_unable_browser), Toast.LENGTH_SHORT).show()
    }
}

enum class CentralCommandLaunchGate {
    NONE,
    FRESH_INSTALL,
    VERSION_UPDATE,
    BUILD_REFRESH
}

data class LaunchGateDecision(
    val gate: CentralCommandLaunchGate,
    val showWelcome: Boolean
)

/**
 * Evaluates initial launch gates (fresh install / post-update What's New + Engine Warm-Up)
 * and periodic 7d/30d supporter milestones inside Central Command.
 */
object SupporterReminderController {
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private fun readPackageLastUpdateTime(context: Context): Long = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Exception) {
        0L
    }

    /**
     * Checks whether this open of Central Command is a fresh install, version upgrade, or
     * same-version APK reinstall/update, immediately marking the current build (and one-time
     * v1.4.0 Welcome Aboard gate) as seen.
     */
    fun consumeInitialLaunchGate(context: Context): LaunchGateDecision {
        val prefs = context.defaultPrefs()
        val now = System.currentTimeMillis()
        val currentVersion = BuildConfig.VERSION_CODE
        val currentInstallTime = readPackageLastUpdateTime(context)
        val firstUseMs = prefs.getLong(LightspeedPreferences.KEY_SUPPORTER_FIRST_USE_MS, 0L)
        val lastSeenVersion = prefs.getInt(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_VERSION, 0)
        val lastSeenInstallTime = prefs.getLong(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_INSTALL_TIME, 0L)
        val welcomeSeen = prefs.getBoolean(LightspeedPreferences.KEY_WELCOME_V140_SEEN, false)

        if (firstUseMs <= 0L || lastSeenVersion <= 0) {
            prefs.edit()
                .putLong(LightspeedPreferences.KEY_SUPPORTER_FIRST_USE_MS, if (firstUseMs > 0L) firstUseMs else now)
                .putInt(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_VERSION, currentVersion)
                .putLong(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_INSTALL_TIME, currentInstallTime)
                .putBoolean(LightspeedPreferences.KEY_WELCOME_V140_SEEN, true)
                .apply()
            CentralCommandWarmupCoordinator.markCold()
            return LaunchGateDecision(CentralCommandLaunchGate.FRESH_INSTALL, showWelcome = true)
        }

        if (lastSeenVersion < currentVersion) {
            prefs.edit()
                .putInt(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_VERSION, currentVersion)
                .putLong(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_INSTALL_TIME, currentInstallTime)
                .putBoolean(LightspeedPreferences.KEY_WELCOME_V140_SEEN, true)
                .apply()
            CentralCommandWarmupCoordinator.markCold()
            return LaunchGateDecision(
                gate = CentralCommandLaunchGate.VERSION_UPDATE,
                showWelcome = !welcomeSeen
            )
        }

        if (currentInstallTime > 0L && lastSeenInstallTime != currentInstallTime) {
            prefs.edit()
                .putLong(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_INSTALL_TIME, currentInstallTime)
                .apply()
            CentralCommandWarmupCoordinator.markCold()
            return LaunchGateDecision(CentralCommandLaunchGate.BUILD_REFRESH, showWelcome = false)
        }

        return LaunchGateDecision(CentralCommandLaunchGate.NONE, showWelcome = false)
    }

    fun checkAndConsumeReminderReason(context: Context): String? {
        val prefs = context.defaultPrefs()
        if (prefs.getBoolean(LightspeedPreferences.KEY_SUPPORTER_REMINDER_OPT_OUT, false)) {
            return null
        }
        val now = System.currentTimeMillis()
        val currentVersion = BuildConfig.VERSION_CODE
        val firstUseMs = prefs.getLong(LightspeedPreferences.KEY_SUPPORTER_FIRST_USE_MS, 0L)
        val lastSeenVersion = prefs.getInt(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_VERSION, 0)

        if (firstUseMs <= 0L || lastSeenVersion <= 0) {
            prefs.edit()
                .putLong(LightspeedPreferences.KEY_SUPPORTER_FIRST_USE_MS, if (firstUseMs > 0L) firstUseMs else now)
                .putInt(LightspeedPreferences.KEY_SUPPORTER_LAST_SEEN_VERSION, currentVersion)
                .apply()
            return null
        }

        if (LightspeedInfinityManager.isUnlocked(context)) return null

        val elapsedDays = (now - firstUseMs) / DAY_MS
        val shown30d = prefs.getBoolean(LightspeedPreferences.KEY_SUPPORTER_SHOWN_30D, false)
        val shown7d = prefs.getBoolean(LightspeedPreferences.KEY_SUPPORTER_SHOWN_7D, false)

        if (elapsedDays >= 30L && !shown30d) {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_SUPPORTER_SHOWN_30D, true)
                .putBoolean(LightspeedPreferences.KEY_SUPPORTER_SHOWN_7D, true)
                .apply()
            return lsString(R.string.wn_milestone_30d)
        }
        if (elapsedDays >= 7L && !shown7d) {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_SUPPORTER_SHOWN_7D, true)
                .apply()
            return lsString(R.string.wn_milestone_7d)
        }
        return null
    }
}

/**
 * Classy full-window Central Command morph panel shown when the user manually opens
 * "What's New >" in About or taps the footer version badge.
 */
@Composable
fun CentralCommandWhatsNewMorphPanel(
    context: Context,
    startOnWelcome: Boolean = false,
    onDismiss: () -> Unit,
    onOpenInfinityUnlock: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val unlockEpoch by LightspeedInfinityManager.unlockEpoch.collectAsState()
    val isUnlocked = remember(unlockEpoch) { LightspeedInfinityManager.isUnlocked(context) }
    var showingWelcomePage by remember(startOnWelcome) { mutableStateOf(startOnWelcome) }
    var lastWelcomeBackPressMs by remember { mutableLongStateOf(0L) }

    BackHandler {
        when {
            !showingWelcomePage && startOnWelcome -> showingWelcomePage = true
            showingWelcomePage -> {
                val now = System.currentTimeMillis()
                if (now - lastWelcomeBackPressMs <= 3000L) {
                    onDismiss()
                } else {
                    lastWelcomeBackPressMs = now
                    Toast.makeText(
                        context,
                        lsString(R.string.wn_welcome_back_toast),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            else -> onDismiss()
        }
    }

    AnimatedContent(
        targetState = showingWelcomePage,
        transitionSpec = {
            (fadeIn(animationSpec = tween(durationMillis = 240)) +
                scaleIn(
                    initialScale = 0.93f,
                    animationSpec = spring(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )) togetherWith
                (fadeOut(animationSpec = tween(durationMillis = 150)) +
                    scaleOut(
                        targetScale = 0.95f,
                        animationSpec = tween(durationMillis = 150)
                    ))
        },
        label = "welcome_whats_new_morph"
    ) { isWelcomeStep ->
        val stepScrollState = rememberScrollState()
        var revealedSteps by remember(isWelcomeStep) { mutableIntStateOf(0) }
        LaunchedEffect(isWelcomeStep) {
            revealedSteps = 0
            for (step in 1..6) {
                revealedSteps = step
                delay(48L)
            }
        }

        DeckGlassCardSurface(
            modifier = Modifier.fillMaxSize(),
            isFlyout = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                // Header: spacious layout without cramped version badge boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (!isWelcomeStep && startOnWelcome) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(lsColors.textPrimary.copy(alpha = 0.08f))
                                    .clickable { showingWelcomePage = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = lsString(R.string.wn_cd_back_welcome),
                                    tint = lsColors.textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        Column {
                            BilingualTitleText(
                                title = if (isWelcomeStep) {
                                    lsString(R.string.wn_welcome_title)
                                } else {
                                    lsString(R.string.wn_whats_new_version, BuildConfig.VERSION_NAME)
                                },
                                fontSize = if (isWelcomeStep) 18.sp else 16.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = lsColors.textPrimary
                            )
                            Text(
                                text = if (isWelcomeStep) {
                                    lsString(R.string.wn_welcome_subtitle)
                                } else {
                                    lsString(R.string.wn_release_tagline)
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = lsColors.accentInfo
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(lsColors.textPrimary.copy(alpha = 0.08f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = if (isWelcomeStep) lsString(R.string.wn_cd_close_welcome) else lsString(R.string.wn_cd_close_whats_new),
                            tint = lsColors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(
                    color = lsColors.textPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                if (!isWelcomeStep) {
                    InfinitySupportMissionLine(
                        isUnlocked = isUnlocked,
                        onOpenInfinity = onOpenInfinityUnlock
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalEdgeFade(stepScrollState)
                        .verticalScroll(stepScrollState),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (isWelcomeStep) {
                        WelcomeIntroCardsContent(
                            context = context,
                            revealedSteps = revealedSteps
                        )
                    } else {
                        ReleaseHighlightCardsContent(revealedSteps = revealedSteps)
                        StaggeredRevealCard(visible = revealedSteps >= 5) {
                            SupporterCommunityCard(isUnlocked = isUnlocked)
                        }
                        StaggeredRevealCard(visible = revealedSteps >= 6) {
                            CommunityLinksRow(context = context)
                        }
                    }
                }

                HorizontalDivider(
                    color = lsColors.textPrimary.copy(alpha = 0.08f),
                    modifier = Modifier.padding(top = 10.dp, bottom = 10.dp)
                )

                if (isWelcomeStep) {
                    WelcomeActionRow(
                        onSkip = onDismiss,
                        onNext = { showingWelcomePage = false }
                    )
                } else {
                    WhatsNewActionRow(
                        context = context,
                        isUnlocked = isUnlocked,
                        isUpdateBanner = true,
                        onDismiss = onDismiss,
                        onBeforeAction = {},
                        onOpenInfinityUnlock = onOpenInfinityUnlock
                    )
                }
            }
        }
    }
}

/**
 * Automatic milestone/post-update modal reminder shown inside Central Command when
 * [SupporterReminderController] detects an unread update or 7d/30d milestone.
 */
@Composable
fun LightspeedSupporterReminderDialog(
    context: Context,
    milestoneBadge: String,
    onDismiss: () -> Unit,
    onOpenInfinityUnlock: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val prefs = remember { context.defaultPrefs() }
    val unlockEpoch by LightspeedInfinityManager.unlockEpoch.collectAsState()
    val isUnlocked = remember(unlockEpoch) { LightspeedInfinityManager.isUnlocked(context) }
    val isUpdateBanner = milestoneBadge.contains("UPDATE", ignoreCase = true) ||
        milestoneBadge.contains("v${BuildConfig.VERSION_NAME}", ignoreCase = true)
    var doNotShowAgain by remember {
        mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_SUPPORTER_REMINDER_OPT_OUT, false))
    }

    val saveOptOutIfChecked = {
        if (doNotShowAgain) {
            prefs.edit()
                .putBoolean(LightspeedPreferences.KEY_SUPPORTER_REMINDER_OPT_OUT, true)
                .apply()
        }
    }

    val handleDismiss = {
        saveOptOutIfChecked()
        onDismiss()
    }

    DeckDialog(
        onDismissRequest = handleDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(lsColors.accentGold.copy(alpha = 0.14f))
                    .border(0.8.dp, lsColors.accentGold.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "✦ $milestoneBadge",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = lsColors.accentGold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            InfinitySupportMissionLine(
                isUnlocked = isUnlocked,
                onBeforeAction = saveOptOutIfChecked,
                onOpenInfinity = onOpenInfinityUnlock
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isUpdateBanner) lsString(R.string.wn_title_update) else lsString(R.string.wn_title_sovereignty),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = lsColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isUpdateBanner) {
                    lsString(R.string.wn_release_tagline)
                } else {
                    lsString(R.string.wn_offline_free_tagline)
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.accentInfo,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScrollWithEdgeFade(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isUpdateBanner) {
                    ReleaseHighlightCardsContent(revealedSteps = Int.MAX_VALUE)
                }
                SupporterCommunityCard(isUnlocked = isUnlocked)
                CommunityLinksRow(context = context)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(lsColors.textPrimary.copy(alpha = 0.04f))
                    .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .clickable { doNotShowAgain = !doNotShowAgain }
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lsString(R.string.wn_dont_show_again),
                    fontSize = 12.5.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f)
                )
                Checkbox(
                    checked = doNotShowAgain,
                    onCheckedChange = { doNotShowAgain = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = lsColors.accentGold,
                        uncheckedColor = lsColors.textPrimary.copy(alpha = 0.45f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            WhatsNewActionRow(
                context = context,
                isUnlocked = isUnlocked,
                isUpdateBanner = isUpdateBanner,
                onDismiss = handleDismiss,
                onBeforeAction = saveOptOutIfChecked,
                onOpenInfinityUnlock = onOpenInfinityUnlock
            )
        }
    }
}

@Composable
private fun StaggeredRevealCard(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(durationMillis = 240)) +
            slideInVertically(
                initialOffsetY = { it / 6 },
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
    ) {
        content()
    }
}

@Composable
private fun InfinitySupportMissionLine(
    isUnlocked: Boolean,
    onBeforeAction: () -> Unit = {},
    onOpenInfinity: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val bannerText = if (isUnlocked) {
        lsString(R.string.wn_mission_unlocked)
    } else {
        lsString(R.string.wn_mission_locked)
    }
    Text(
        text = buildGooglePayAnnotatedText(bannerText),
        fontSize = 11.8.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = lsColors.accentGold.copy(alpha = 0.94f),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(lsColors.accentGold.copy(alpha = 0.08f))
            .border(0.6.dp, lsColors.accentGold.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable {
                onBeforeAction()
                onOpenInfinity()
            }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    )
}

@Composable
private fun ReleaseHighlightCardsContent(revealedSteps: Int) {
    StaggeredRevealCard(visible = revealedSteps >= 1) {
        UpdateHighlightCard(
            title = lsString(R.string.wn_hl_cruise_title),
            bullets = listOf(
                lsString(R.string.wn_hl_cruise_b1),
                lsString(R.string.wn_hl_cruise_b2),
                lsString(R.string.wn_hl_cruise_b3),
                lsString(R.string.wn_hl_cruise_b4)
            )
        )
    }
    StaggeredRevealCard(visible = revealedSteps >= 2) {
        UpdateHighlightCard(
            title = lsString(R.string.wn_hl_refuel_title),
            bullets = listOf(
                lsString(R.string.wn_hl_refuel_b1),
                lsString(R.string.wn_hl_refuel_b2),
                lsString(R.string.wn_hl_refuel_b3)
            )
        )
    }
    StaggeredRevealCard(visible = revealedSteps >= 3) {
        UpdateHighlightCard(
            title = lsString(R.string.wn_hl_display_title),
            bullets = listOf(
                lsString(R.string.wn_hl_display_b1),
                lsString(R.string.wn_hl_display_b2)
            )
        )
    }
    StaggeredRevealCard(visible = revealedSteps >= 4) {
        UpdateHighlightCard(
            title = lsString(R.string.wn_hl_styles_title),
            bullets = listOf(
                lsString(R.string.wn_hl_styles_b1),
                lsString(R.string.wn_hl_styles_b2),
                lsString(R.string.wn_hl_styles_b3)
            )
        )
    }
}

@Composable
private fun SupporterCommunityCard(isUnlocked: Boolean) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.05f))
            .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = if (isUnlocked) lsString(R.string.wn_supporter_title_unlocked) else lsString(R.string.wn_supporter_title_locked),
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = lsColors.accentGold
        )
        Text(
            text = if (isUnlocked) {
                lsString(R.string.wn_supporter_body_unlocked)
            } else {
                lsString(R.string.wn_supporter_body_locked)
            },
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            color = lsColors.textPrimary.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun UpdateHighlightCard(
    title: String,
    bullets: List<String>
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.04f))
            .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = lsColors.textPrimary
        )
        bullets.forEach { bullet ->
            Text(
                text = "• $bullet",
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = lsColors.textPrimary.copy(alpha = 0.86f)
            )
        }
    }
}
