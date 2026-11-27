package com.sbf.lightspeed.settings

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedInfinityManager
import com.sbf.lightspeed.system.lsString
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Lightspeed Infinity Activation & Founder's License Dialog.
 * Provides tappable deep-links to showcase perks, a prominent 1-tap Buy Me a Coffee CTA,
 * and a collapsible offline RSA key input that auto-expands on return from checkout.
 */
@Composable
fun LightspeedInfinityDialog(
    context: Context,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit = {},
    featureContext: String? = null,
    initialExpandKeyInput: Boolean = false
) {
    val lsColors = LightspeedStyle.colors
    val lifecycleOwner = LocalLifecycleOwner.current
    val localOpenDeck = LocalOpenFlightControlDeck.current
    val localNavTrackpad = LocalNavigateToTrackpadStyles.current

    var codeInput by rememberSaveable { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var hasClickedShop by rememberSaveable { mutableStateOf(false) }
    var isKeySectionExpanded by rememberSaveable(initialExpandKeyInput) {
        mutableStateOf(initialExpandKeyInput)
    }

    val unlockEpoch by LightspeedInfinityManager.unlockEpoch.collectAsState()
    var isUnlocked by remember(unlockEpoch) { mutableStateOf(LightspeedInfinityManager.isUnlocked(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hasClickedShop) {
                isKeySectionExpanded = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DeckDialog(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("★", fontSize = 18.sp, color = lsColors.accentGold)
                Text(
                    "LIGHTSPEED INFINITY",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.accentGold,
                    letterSpacing = 1.2.sp
                )
                Text("★", fontSize = 18.sp, color = lsColors.accentGold)
            }

            if (!isUnlocked && !featureContext.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lsString(R.string.inf_context_pill, featureContext),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = lsColors.accentGold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(lsColors.accentGold.copy(alpha = 0.14f))
                        .border(0.7.dp, lsColors.accentGold.copy(alpha = 0.42f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isUnlocked) lsString(R.string.inf_subtitle_unlocked) else lsString(R.string.inf_subtitle_locked),
                fontSize = 11.sp,
                color = lsColors.textPrimary.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 4 Tappable Perks Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(lsColors.textPrimary.copy(alpha = 0.04f))
                    .border(0.8.dp, lsColors.textPrimary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PerkRow(
                    icon = "◈",
                    title = lsString(R.string.inf_perk_themes_title),
                    subtitle = lsString(R.string.inf_perk_themes_sub),
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        onDismiss()
                        openFlightControlDeck(context, localOpenDeck)
                    }
                )
                PerkRow(
                    icon = "∞",
                    title = lsString(R.string.inf_perk_gears_title),
                    subtitle = lsString(R.string.inf_perk_gears_sub),
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        onDismiss()
                        openCockpitHangarForShowcase(context)
                    }
                )
                PerkRow(
                    icon = "⌖",
                    title = lsString(R.string.inf_perk_trackpad_title),
                    subtitle = lsString(R.string.inf_perk_trackpad_sub),
                    onClick = {
                        LightspeedHapticEngine.tick(context)
                        onDismiss()
                        openTrackpadStylesShowcase(context, localNavTrackpad)
                    }
                )
                PerkRow(
                    icon = "★",
                    title = lsString(R.string.inf_perk_craft_title),
                    subtitle = lsString(R.string.inf_perk_craft_sub),
                    onClick = null
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isUnlocked) {
                // Already Unlocked State — mask active key by default to prevent accidental screen/camera leaks
                val activeKey = LightspeedInfinityManager.getActiveCode(context) ?: "ACTIVE"
                var revealKey by remember { mutableStateOf(false) }
                val displayKey = if (revealKey) {
                    if (activeKey.length > 26) "${activeKey.take(16)}...${activeKey.takeLast(6)}" else activeKey
                } else {
                    if (activeKey.startsWith("LSINF-")) "LSINF-••••-••••" else "••••••••••••"
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(lsColors.accentGold.copy(alpha = 0.12f))
                        .border(1.dp, lsColors.accentGold.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            revealKey = !revealKey
                        }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lsString(R.string.inf_key_active, displayKey),
                        color = lsColors.accentGold,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (revealKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (revealKey) lsString(R.string.inf_cd_hide_key) else lsString(R.string.inf_cd_reveal_key),
                        tint = lsColors.accentGold.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LightspeedTokens.StatusGoldAlt.copy(alpha = 0.18f))
                            .border(0.8.dp, LightspeedTokens.StatusGoldAlt.copy(alpha = 0.48f), RoundedCornerShape(10.dp))
                            .clickable {
                                openExternalUrl(context, INFINITY_CREATOR_URL)
                            }
                            .padding(vertical = 10.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_buymeacoffee),
                            contentDescription = "Buy Me a Coffee",
                            tint = LightspeedTokens.StatusGoldAlt,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Buy Me a Coffee",
                            color = LightspeedTokens.StatusGoldAlt,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = lsColors.textPrimary.copy(alpha = 0.1f))
                    ) {
                        Text(lsString(R.string.inf_close_deck), color = lsColors.textPrimary, fontSize = 12.sp)
                    }
                }
            } else {
                // Locked State: Prominent 1-Tap Founder's License CTA + Collapsible Smart-Resume Key Drawer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightspeedTokens.StatusGoldAlt)
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            hasClickedShop = true
                            isKeySectionExpanded = true
                            openExternalUrl(context, INFINITY_SHOP_URL)
                        }
                        .padding(vertical = 11.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    RocketFuelCanIcon(
                        modifier = Modifier.size(18.dp),
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = lsString(R.string.inf_get_btn),
                            color = Color.Black,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = lsString(R.string.inf_get_btn_sub),
                            color = Color.Black.copy(alpha = 0.80f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = lsString(R.string.inf_google_play_note),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lsString(R.string.inf_pricing_note),
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Collapsible "Already have a key?" toggle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(lsColors.textPrimary.copy(alpha = 0.05f))
                        .border(0.7.dp, lsColors.textPrimary.copy(alpha = 0.12f), RoundedCornerShape(9.dp))
                        .clickable {
                            LightspeedHapticEngine.tick(context)
                            isKeySectionExpanded = !isKeySectionExpanded
                        }
                        .padding(vertical = 7.dp, horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = lsString(
                            if (isKeySectionExpanded) R.string.inf_have_key_toggle_open
                            else R.string.inf_have_key_toggle_closed
                        ),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = lsColors.accentGold.copy(alpha = 0.90f),
                        textAlign = TextAlign.Center
                    )
                }

                AnimatedVisibility(visible = isKeySectionExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = codeInput,
                                onValueChange = {
                                    codeInput = it
                                    errorMessage = null
                                },
                                placeholder = { Text("LSINF-XXXX-...", fontSize = 11.sp, color = lsColors.textDisabled) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = lsColors.textPrimary
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = lsColors.accentGold,
                                    unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.2f),
                                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                                )
                            )

                            Button(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = cb?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        codeInput = clip.trim()
                                        errorMessage = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = lsColors.textPrimary.copy(alpha = 0.08f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(lsString(R.string.inf_paste), fontSize = 11.sp, color = lsColors.textPrimary)
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(errorMessage!!, color = lsColors.accentDanger, fontSize = 10.5.sp, textAlign = TextAlign.Center)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                if (LightspeedInfinityManager.isLockedOut(context)) {
                                    val secs = LightspeedInfinityManager.getRemainingLockoutSeconds(context)
                                    errorMessage = lsString(R.string.inf_err_lockout, secs)
                                    return@Button
                                }
                                if (LightspeedInfinityManager.validateAndUnlock(context, codeInput)) {
                                    isUnlocked = true
                                    onUnlocked()
                                    Toast.makeText(context, lsString(R.string.inf_toast_unlocked), Toast.LENGTH_SHORT).show()
                                } else {
                                    LightspeedHapticEngine.triggerWarning(context)
                                    errorMessage = lsString(R.string.inf_err_invalid)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = lsColors.accentGold.copy(alpha = 0.22f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, lsColors.accentGold.copy(alpha = 0.6f))
                        ) {
                            Text(lsString(R.string.inf_authorize_key), color = lsColors.accentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerkRow(
    icon: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null
) {
    val lsColors = LightspeedStyle.colors
    val rowMod = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 3.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
    }
    Row(
        modifier = rowMod,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 14.sp, color = lsColors.accentGold, fontWeight = FontWeight.Bold)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = lsColors.textPrimary)
            Text(subtitle, fontSize = 9.5.sp, color = lsColors.textSecondary.copy(alpha = 0.74f), lineHeight = 12.5.sp)
        }
        if (onClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = lsColors.accentGold.copy(alpha = 0.75f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
