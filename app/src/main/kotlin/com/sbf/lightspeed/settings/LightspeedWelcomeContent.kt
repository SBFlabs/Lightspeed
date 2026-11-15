package com.sbf.lightspeed.settings

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * Pure first-time onboarding cards shown on fresh installs immediately before morphing into
 * "What's New". Explains what Lightspeed is based on the project README introduction and points
 * new users to the indexed README guidebook, offline in-app guidebook, and issue/discussion channels
 * without any premature monetization or support prompts.
 */
@Composable
internal fun WelcomeIntroCardsContent(
    context: Context,
    revealedSteps: Int
) {
    val lsColors = LightspeedStyle.colors

    WelcomeRevealStep(visible = revealedSteps >= 1) {
        WelcomeSectionCard(title = "What Is Lightspeed?") {
            Text(
                text = "Lightspeed is a meta-launcher & Android control layer built on Linux's open foundation — keep your current home screen launcher and OEM features while commanding apps, gestures, and system controls from anywhere.",
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = lsColors.textPrimary.copy(alpha = 0.90f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            WelcomeLaunchPadBanner(
                description = "The Astrogation Core — or the edge pill on the right (or left) side of your screen — is the first thing you see when Lightspeed is active. From here, you reach all three workstations without leaving any app or video:"
            )
            Spacer(modifier = Modifier.height(7.dp))
            WelcomeStationRow(
                stepLabel = "1",
                name = "Category Cruise",
                description = "Rest your finger or slide up and down on the Astrogation Core pill to open Category Cruise — and experience one-thumb cruising through your categorized app drawers over any screen or video."
            )
            Spacer(modifier = Modifier.height(5.dp))
            WelcomeStationRow(
                stepLabel = "2",
                name = "Cockpit",
                description = "Slide your finger horizontally from the pill to open Cockpit — your favorite apps arranged in Cockpit Gear Sets inside Cockpit Hangar's spinning dial customizer."
            )
            Spacer(modifier = Modifier.height(5.dp))
            WelcomeStationRow(
                stepLabel = "3",
                name = "Central Command",
                description = "Your Android control layer & main settings. Open from the Lightspeed icon, or keep sliding slightly past the bottom Category Cruise category until you feel haptic feedback."
            )
        }
    }

    WelcomeRevealStep(visible = revealedSteps >= 2) {
        WelcomeSectionCard(title = "See It in Action (Videos & Screenshots)") {
            Text(
                text = "No wall of text — the GitHub README opens with short demo videos and step-by-step screenshots, plus an expandable Table of Contents index with back links so you jump straight to what you need.",
                fontSize = 13.5.sp,
                lineHeight = 19.sp,
                color = lsColors.textPrimary.copy(alpha = 0.90f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WelcomeMediaPill(
                    icon = Icons.Default.PlayArrow,
                    label = "7 Videos",
                    modifier = Modifier.weight(0.88f)
                )
                WelcomeMediaPill(
                    icon = Icons.Default.Image,
                    label = "Screenshots",
                    modifier = Modifier.weight(1.16f)
                )
                WelcomeMediaPill(
                    icon = Icons.Default.Bookmarks,
                    label = "Jump Index",
                    modifier = Modifier.weight(0.96f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            WelcomeExternalLinkRow(
                icon = Icons.AutoMirrored.Outlined.MenuBook,
                title = "Watch Demos & Open Guidebook",
                subtitle = "Showcase videos, screenshots & expandable index",
                highlighted = true,
                onClick = { openExternalUrl(context, "https://github.com/SBFlabs/Lightspeed#readme") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(lsColors.accentInfo.copy(alpha = 0.08f))
                    .border(0.7.dp, lsColors.accentInfo.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Prefer starting on a lighter note inside the app first? Tap the book icon in the top-right corner of Central Command anytime for a quick offline guide.",
                    fontSize = 12.sp,
                    lineHeight = 16.5.sp,
                    color = lsColors.textPrimary.copy(alpha = 0.84f)
                )
            }
        }
    }

    WelcomeRevealStep(visible = revealedSteps >= 3) {
        WelcomeSectionCard(title = "Community, Issues & Q&A") {
            Text(
                text = "Pick the right spot below — or follow along on YouTube and Reddit for convenience:",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = lsColors.textPrimary.copy(alpha = 0.88f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            WelcomeExternalLinkRow(
                icon = Icons.Rounded.BugReport,
                title = "GitHub Issues",
                subtitle = "Bug tracking, language requests, feature feedback & Q&A",
                highlighted = false,
                onClick = { openExternalUrl(context, "https://github.com/SBFlabs/Lightspeed/issues") }
            )
            Spacer(modifier = Modifier.height(6.dp))
            WelcomeExternalLinkRow(
                icon = Icons.Rounded.Forum,
                title = "XDA Forums Thread",
                subtitle = "Discussions, setup tips & sharing with users",
                highlighted = false,
                onClick = { openExternalUrl(context, "https://xdaforums.com/t/4804419/") }
            )
            Spacer(modifier = Modifier.height(6.dp))
            WelcomeExternalLinkRow(
                icon = Icons.Default.PlayArrow,
                title = "YouTube (@SBFlabs) & Reddit",
                subtitle = "Video clips & updates for convenience",
                highlighted = false,
                onClick = { openExternalUrl(context, "https://www.youtube.com/@SBFlabs") }
            )
        }
    }
}

@Composable
internal fun WelcomeActionRow(
    onSkip: () -> Unit,
    onNext: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onSkip,
            modifier = Modifier.weight(0.75f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 9.dp),
            colors = ButtonDefaults.buttonColors(containerColor = lsColors.textPrimary.copy(alpha = 0.08f))
        ) {
            Text(
                text = "Skip",
                color = lsColors.textPrimary,
                fontSize = 12.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        Button(
            onClick = onNext,
            modifier = Modifier.weight(1.35f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp),
            colors = ButtonDefaults.buttonColors(containerColor = lsColors.accent.copy(alpha = 0.22f)),
            border = BorderStroke(1.dp, lsColors.accent.copy(alpha = 0.65f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Next: What's New",
                    color = lsColors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = lsColors.accent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

@Composable
internal fun WhatsNewActionRow(
    context: Context,
    isUnlocked: Boolean,
    isUpdateBanner: Boolean,
    onDismiss: () -> Unit,
    onBeforeAction: () -> Unit,
    onOpenInfinityUnlock: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = onDismiss,
            modifier = Modifier.weight(if (isUnlocked) 1f else 0.78f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = lsColors.textPrimary.copy(alpha = 0.08f))
        ) {
            Text(
                text = if (isUpdateBanner) "Got it" else "Later",
                color = lsColors.textPrimary,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (!isUnlocked) {
            Button(
                onClick = {
                    onBeforeAction()
                    onOpenInfinityUnlock()
                },
                modifier = Modifier.weight(0.95f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = lsColors.textPrimary.copy(alpha = 0.10f))
            ) {
                Text(
                    text = "Enter Key",
                    color = lsColors.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Button(
            onClick = {
                onBeforeAction()
                val targetUrl = if (isUnlocked) {
                    "https://buymeacoffee.com/sbflabs"
                } else {
                    "https://buymeacoffee.com/sbflabs/e/579114"
                }
                openExternalUrl(context, targetUrl)
            },
            modifier = Modifier.weight(1.38f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = lsColors.accentGold.copy(alpha = 0.22f)),
            border = BorderStroke(1.dp, lsColors.accentGold.copy(alpha = 0.65f))
        ) {
            Text(
                text = if (isUnlocked) "☕ Support" else "☕ Unlock ∞ ($1.99)",
                color = lsColors.accentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun CommunityLinksRow(context: Context) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunityLinkTile(
            title = "📖 Main Guidebook (GitHub README)",
            subtitle = "Indexed Table of Contents, screenshots & showcase videos",
            modifier = Modifier.fillMaxWidth(),
            onClick = { openExternalUrl(context, "https://github.com/SBFlabs/Lightspeed#readme") }
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CommunityLinkTile(
                title = "🐛 GitHub Issues",
                subtitle = "Bugs, features & Q&A",
                modifier = Modifier.weight(1f),
                onClick = { openExternalUrl(context, "https://github.com/SBFlabs/Lightspeed/issues") }
            )
            CommunityLinkTile(
                title = "💬 XDA Thread",
                subtitle = "Discussions & setup tips",
                modifier = Modifier.weight(1f),
                onClick = { openExternalUrl(context, "https://xdaforums.com/t/4804419/") }
            )
        }
    }
}

@Composable
private fun CommunityLinkTile(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(lsColors.accentInfo.copy(alpha = 0.12f))
            .border(0.8.dp, lsColors.accentInfo.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = lsColors.accentInfo
        )
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = lsColors.textPrimary.copy(alpha = 0.76f)
        )
    }
}

@Composable
private fun WelcomeSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.045f))
            .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = lsColors.textPrimary
        )
        Spacer(modifier = Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun WelcomeLaunchPadBanner(
    description: String
) {
    val lsColors = LightspeedStyle.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(lsColors.accent.copy(alpha = 0.11f))
            .border(0.8.dp, lsColors.accent.copy(alpha = 0.36f), RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.TouchApp,
                contentDescription = null,
                tint = lsColors.accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = "Your Launch Pad",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.accent
            )
        }
        Text(
            text = description,
            fontSize = 12.sp,
            lineHeight = 16.5.sp,
            color = lsColors.textPrimary.copy(alpha = 0.88f)
        )
    }
}

@Composable
private fun WelcomeMediaPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(lsColors.accent.copy(alpha = 0.10f))
            .border(0.7.dp, lsColors.accent.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = lsColors.accent,
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = " $label",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = lsColors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WelcomeStationRow(
    stepLabel: String,
    name: String,
    description: String
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(lsColors.textPrimary.copy(alpha = 0.04f))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$stepLabel.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = lsColors.accent,
            modifier = Modifier.padding(top = 1.dp)
        )
        Column {
            Text(
                text = name,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary
            )
            Text(
                text = description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = lsColors.textSecondary
            )
        }
    }
}

@Composable
private fun WelcomeExternalLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val tint = if (highlighted) lsColors.accent else lsColors.accentInfo
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(tint.copy(alpha = if (highlighted) 0.12f else 0.07f))
            .border(0.8.dp, tint.copy(alpha = if (highlighted) 0.42f else 0.24f), RoundedCornerShape(11.dp))
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text = title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.textPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    color = lsColors.textSecondary
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Launch,
            contentDescription = "Open link",
            tint = tint,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun WelcomeRevealStep(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(220)) +
            slideInVertically(animationSpec = tween(240), initialOffsetY = { it / 6 })
    ) {
        content()
    }
}
