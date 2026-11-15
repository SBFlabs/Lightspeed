package com.sbf.lightspeed.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.BuildVerificationState
import com.sbf.lightspeed.system.DistributionVerifier
import com.sbf.lightspeed.system.SignatureStatus
import com.sbf.lightspeed.ui.theme.LightspeedStyle

/**
 * 12-Theme DeckDialog showing offline APK signing status, install origin, and
 * links to Lightspeed's approved distribution channels (GitHub, Obtainium, ShizuStore, Orion Store).
 */
@Composable
fun BuildVerificationDialog(
    context: Context,
    verification: BuildVerificationState,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors

    DeckDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Build Verification & Official Sources",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = lsColors.textPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SignatureStatusBanner(
                    verification = verification,
                    onCopyFingerprint = {
                        DistributionVerifier.copyToClipboard(
                            context = context,
                            label = "Lightspeed Signing SHA-256",
                            text = verification.actualCertSha256
                        )
                        Toast.makeText(context, "Signing SHA-256 copied", Toast.LENGTH_SHORT).show()
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lsColors.textPrimary.copy(alpha = 0.05f))
                        .border(0.7.dp, lsColors.borderSubtle, RoundedCornerShape(10.dp))
                        .padding(horizontal = 11.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Install Origin",
                        fontSize = 12.sp,
                        color = lsColors.textSecondary
                    )
                    Text(
                        text = verification.installOrigin.displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (verification.installOrigin.isOfficialClient) {
                            lsColors.accentOk
                        } else {
                            lsColors.textPrimary
                        }
                    )
                }

                if (verification.signatureStatus == SignatureStatus.OFFICIAL_RELEASE &&
                    !verification.installOrigin.isOfficialClient
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(lsColors.accentInfo.copy(alpha = 0.10f))
                            .border(0.7.dp, lsColors.accentInfo.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Your APK carries the genuine SBF Labs signature, though it was installed outside an official updater. Use one of the channels below for direct verified updates.",
                            fontSize = 11.5.sp,
                            lineHeight = 15.5.sp,
                            color = lsColors.textPrimary.copy(alpha = 0.88f)
                        )
                    }
                }

                Text(
                    text = "Official Distribution Channels",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                OfficialSourceRow(
                    icon = Icons.Default.Code,
                    title = "GitHub Releases",
                    subtitle = "Official signed 32-bit & 64-bit APKs and SHA-256 checksums",
                    onClick = {
                        DistributionVerifier.openUriWithFallback(
                            context = context,
                            primaryUri = DistributionVerifier.GITHUB_RELEASES_URL
                        )
                    }
                )

                OfficialSourceRow(
                    icon = Icons.Default.CloudDownload,
                    title = "Obtainium",
                    subtitle = "Direct release updates from GitHub (pre-releases off)",
                    onClick = {
                        DistributionVerifier.openUriWithFallback(
                            context = context,
                            primaryUri = DistributionVerifier.OBTAINIUM_DEEP_LINK,
                            fallbackUri = DistributionVerifier.OBTAINIUM_WEB_URL,
                            clipboardFallbackText = DistributionVerifier.GITHUB_REPO_URL
                        )
                    }
                )

                OfficialSourceRow(
                    icon = Icons.Default.Shield,
                    title = "ShizuStore",
                    subtitle = "Shizuku-native client pulling directly from GitHub",
                    onClick = {
                        DistributionVerifier.openUriWithFallback(
                            context = context,
                            primaryUri = DistributionVerifier.SHIZUSTORE_DEEP_LINK,
                            fallbackUri = DistributionVerifier.SHIZUSTORE_WEB_URL
                        )
                    }
                )

                OfficialSourceRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Orion Store",
                    subtitle = "Opens Lightspeed inside Orion Store (when installed)",
                    onClick = {
                        DistributionVerifier.openUriWithFallback(
                            context = context,
                            primaryUri = DistributionVerifier.ORION_STORE_URL
                        )
                    }
                )
            }
        },
        confirmButton = {
            DeckButton(
                text = "Close",
                onClick = onDismiss,
                isActive = true
            )
        }
    )
}

@Composable
private fun SignatureStatusBanner(
    verification: BuildVerificationState,
    onCopyFingerprint: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val statusTone: Color
    val statusIcon: ImageVector
    val statusTitle: String
    val statusDetail: String

    when (verification.signatureStatus) {
        SignatureStatus.OFFICIAL_RELEASE -> {
            statusTone = lsColors.accentOk
            statusIcon = Icons.Default.CheckCircle
            statusTitle = "Official SBF Labs Signature"
            statusDetail = "Verified release signing key matches SBFlabs/Lightspeed."
        }
        SignatureStatus.NIGHTLY_DEV_BUILD -> {
            statusTone = lsColors.accent
            statusIcon = Icons.Default.Code
            statusTitle = "Nightly Development Build"
            statusDetail = "Signed with local debug key. Stable releases verify against the SBF Labs key."
        }
        SignatureStatus.CUSTOM_OR_UNVERIFIED -> {
            statusTone = lsColors.accentWarn
            statusIcon = Icons.Default.Warning
            statusTitle = "Custom or Third-Party Signature"
            statusDetail = "Does not match SBF Labs' key. Only trust this APK if you built it yourself from source."
        }
    }

    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(statusTone.copy(alpha = 0.13f))
            .border(0.9.dp, statusTone.copy(alpha = 0.42f), shape)
            .clickable { onCopyFingerprint() }
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = statusIcon,
            contentDescription = null,
            tint = statusTone,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(19.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = statusTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = lsColors.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = statusDetail,
                fontSize = 11.5.sp,
                lineHeight = 15.sp,
                color = lsColors.textSecondary
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = "SHA-256: ${verification.actualCertSha256}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = statusTone
            )
        }
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy SHA-256 fingerprint",
            tint = statusTone,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
    }
}

@Composable
private fun OfficialSourceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lsColors.textPrimary.copy(alpha = 0.05f))
            .border(0.7.dp, lsColors.borderSubtle, shape)
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                lineHeight = 14.5.sp,
                color = lsColors.textSecondary
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Launch,
            contentDescription = "Open $title",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(15.dp)
        )
    }
}
