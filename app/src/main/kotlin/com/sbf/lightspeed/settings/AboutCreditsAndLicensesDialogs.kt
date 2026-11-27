package com.sbf.lightspeed.settings

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle

private const val SBF_SUPPORT_EMAIL = "sbflabs@gmail.com"

/** Opens the user's email app pre-addressed to SBF Labs; falls back to copying the address. */
internal fun openSbfSupportEmail(context: Context, versionLine: String) {
    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.parse("mailto:$SBF_SUPPORT_EMAIL")
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SBF_SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, "Lightspeed Support ($versionLine)")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("SBF Labs Email", SBF_SUPPORT_EMAIL))
        Toast.makeText(context, lsString(R.string.about_no_email_copied), Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("SBF Labs Email", SBF_SUPPORT_EMAIL))
        Toast.makeText(context, lsString(R.string.about_no_email_copied), Toast.LENGTH_SHORT).show()
    }
}

/** Credits & Acknowledgments dialog matching the calm 10.dp tonal style of About & Feedback. */
@Composable
fun LightspeedCreditsDialog(onDismiss: () -> Unit) {
    val lsColors = LightspeedStyle.colors
    val primary = MaterialTheme.colorScheme.primary
    DeckDialog(
        onDismissRequest = onDismiss,
        scrollable = false,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.WorkspacePremium,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(19.dp)
                )
                Text(
                    text = lsString(R.string.about_credits_title),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScrollWithEdgeFade(topFeatherDp = 20.dp, bottomFeatherDp = 22.dp)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = lsString(R.string.about_credits_intro),
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                    color = lsColors.textSecondary
                )
                CreditsTonalCard(
                    title = lsString(R.string.about_credits_sec_genesis),
                    body = lsString(R.string.about_credits_body_genesis),
                    accentTint = primary,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                CreditsTonalCard(
                    title = lsString(R.string.about_credits_sec_mechanical),
                    body = lsString(R.string.about_credits_body_mechanical),
                    accentTint = primary,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                CreditsTonalCard(
                    title = lsString(R.string.about_credits_sec_ethos),
                    body = lsString(R.string.about_credits_body_ethos),
                    accentTint = primary,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                CreditsTonalCard(
                    title = lsString(R.string.about_credits_sbf_title),
                    body = lsString(R.string.about_credits_sbf_body),
                    accentTint = primary,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
                CreditsTonalCard(
                    title = lsString(R.string.about_credits_founders_title),
                    body = lsString(R.string.about_credits_founders_body),
                    accentTint = lsColors.accentGold,
                    leadingIcon = {
                        RocketFuelCanIcon(
                            modifier = Modifier.size(15.dp),
                            tint = lsColors.accentGold
                        )
                    }
                )
                Text(
                    text = lsString(R.string.about_credits_disclaimer),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.78f)
                )
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.ls_close),
                onClick = onDismiss,
                isActive = true
            )
        }
    )
}

@Composable
private fun CreditsTonalCard(
    title: String,
    body: String,
    accentTint: Color,
    leadingIcon: @Composable () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(lsColors.textPrimary.copy(alpha = 0.05f))
            .border(0.8.dp, accentTint.copy(alpha = 0.28f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon()
            Text(
                text = title,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                color = accentTint
            )
        }
        Text(
            text = body,
            fontSize = 11.5.sp,
            lineHeight = 16.sp,
            color = lsColors.textPrimary.copy(alpha = 0.88f)
        )
    }
}

/** Open-Source Licenses dialog with smooth top/bottom alpha-fade scroll feathering. */
@Composable
fun OpenSourceLicensesDialog(onDismiss: () -> Unit) {
    val lsColors = LightspeedStyle.colors
    DeckDialog(
        onDismissRequest = onDismiss,
        scrollable = false,
        title = {
            Text(
                text = lsString(R.string.about_oss_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScrollWithEdgeFade(topFeatherDp = 20.dp, bottomFeatherDp = 22.dp)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Shizuku API & Provider\nCopyright (c) 2021 RikkaW\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "AndroidX & Jetpack Compose\nCopyright (c) The Android Open Source Project\nLicensed under the Apache License, Version 2.0\nhttps://www.apache.org/licenses/LICENSE-2.0",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "SplitAudio\nCopyright (c) 2026 splitaudio contributors\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "android-realtime-voice-isolation & GTCRN\nCopyright (c) 2026 André Naumann / Copyright (c) 2024 Rong Xiaobin\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "Microsoft ONNX Runtime\nCopyright (c) Microsoft Corporation\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "Volume++\nCopyright (c) 2026 noel-digital-fan\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "Essentials\nCopyright (c) 2025 Sameera Sandakelum\nLicensed under the MIT License.",
                    fontSize = 11.5.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "MIT License Notice:\nPermission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the \"Software\"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:\n\nThe above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.\n\nTHE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.",
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary
                )
                Spacer(modifier = Modifier.size(2.dp))
            }
        },
        confirmButton = {
            DeckButton(
                text = lsString(R.string.ls_close),
                onClick = onDismiss,
                isActive = true
            )
        }
    )
}
