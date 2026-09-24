package com.sbf.lightspeed.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sbf.lightspeed.R
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedInfinityManager

/**
 * Lightspeed Infinity Activation & Supporter Dialog.
 * Allows entering offline cryptographic license keys and provides
 * a 1-tap pathway to Buy Me a Coffee.
 */
@Composable
fun LightspeedInfinityDialog(
    context: Context,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit = {}
) {
    var codeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isUnlocked by remember { mutableStateOf(LightspeedInfinityManager.isUnlocked(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF14161C),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("★", fontSize = 18.sp, color = Color(0xFFFFD700))
                    Text(
                        "LIGHTSPEED INFINITY",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 1.2.sp
                    )
                    Text("★", fontSize = 18.sp, color = Color(0xFFFFD700))
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isUnlocked) "Flight Deck Unlocked • Lifetime Supporter" else "Support independent development to unlock unlimited decks & tactical themes.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Perks Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PerkRow("♾️", "Unlimited Cockpit Gear Sets", "Expand beyond the 2 default flight decks")
                    PerkRow("🌌", "Obsidian Stealth Theme", "Exclusive tactical deep black AMOLED glass")
                    PerkRow("🚀", "Independent Development", "100% offline, zero trackers, built for longevity")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isUnlocked) {
                    // Already Unlocked State
                    val activeKey = LightspeedInfinityManager.getActiveCode(context) ?: "ACTIVE"
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✓ KEY ACTIVE: $activeKey",
                            color = Color(0xFFFFD700),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f))
                    ) {
                        Text("Close Deck", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    // Locked State: 1-Tap Buy Me a Coffee + Code Entry
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFDD00))
                            .clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://buymeacoffee.com/sbflabs/e/579114")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Unable to open browser", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_buymeacoffee),
                            contentDescription = "Buy Me a Coffee",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Get Key on Buy Me a Coffee",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        "OR ENTER ACTIVATION KEY",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Code Input Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.uppercase()
                                errorMessage = null
                            },
                            placeholder = { Text("LSINF-XXXX-YYYY", fontSize = 11.sp, color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFD700),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f)
                            )
                        )

                        // Paste Button
                        Button(
                            onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = cb?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    codeInput = clip.trim().uppercase()
                                    errorMessage = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text("Paste", fontSize = 11.sp, color = Color.White)
                        }
                    }

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(errorMessage!!, color = Color(0xFFFF5252), fontSize = 10.5.sp, textAlign = TextAlign.Center)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (LightspeedInfinityManager.isLockedOut()) {
                                val secs = LightspeedInfinityManager.getRemainingLockoutSeconds()
                                errorMessage = "Too many attempts. Locked for ${secs}s."
                                return@Button
                            }
                            if (LightspeedInfinityManager.validateAndUnlock(context, codeInput)) {
                                isUnlocked = true
                                onUnlocked()
                                Toast.makeText(context, "★ Lightspeed Infinity Unlocked!", Toast.LENGTH_SHORT).show()
                            } else {
                                LightspeedHapticEngine.triggerWarning(context)
                                errorMessage = "Invalid Infinity Key. Please check the code."
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.22f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.6f))
                    ) {
                        Text("Authorize Key", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PerkRow(icon: String, title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(icon, fontSize = 14.sp)
        Column {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(subtitle, fontSize = 9.5.sp, color = Color.LightGray.copy(alpha = 0.7f))
        }
    }
}
