package com.sbf.lightspeed.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

private data class CosmicPioneer(
    val emoji: String,
    val name: String,
    val role: String,
    val license: String? = null,
    val url: String? = null
)

private val HALL_OF_FAME_PIONEERS = listOf(
    // Row 1: MiXplorer | VLC & mpv
    CosmicPioneer(
        emoji = "🗂️",
        name = "MiXplorer",
        role = "Precision Android file architect & media manager",
        license = null,
        url = "https://mixplorer.com"
    ),
    CosmicPioneer(
        emoji = "🎬",
        name = "VLC & mpv",
        role = "Universal open-source multimedia & video engines",
        license = "GPLv2+",
        url = "https://github.com/mpv-android/mpv-android"
    ),
    // Row 2: Essentials | Key Mapper
    CosmicPioneer(
        emoji = "✨",
        name = "Essentials",
        role = "System tweak deck & OLED Smart Pixels pioneer",
        license = "MIT",
        url = "https://github.com/sameerasw/essentials"
    ),
    CosmicPioneer(
        emoji = "⌨️",
        name = "Key Mapper",
        role = "Physical hardware key event remapper powerhouse",
        license = "GPLv3+",
        url = "https://github.com/keymapperorg/KeyMapper"
    ),
    // Row 3: Shizuku | Obtainium
    CosmicPioneer(
        emoji = "⚡",
        name = "Shizuku",
        role = "Elevated ADB privilege bridge without root",
        license = "MIT",
        url = "https://shizuku.rikka.app"
    ),
    CosmicPioneer(
        emoji = "📦",
        name = "Obtainium",
        role = "Direct-from-source GitHub & GitLab release updater",
        license = "GPLv3",
        url = "https://github.com/ImranR98/Obtainium"
    ),
    // Row 4: Orion | SplitAudio
    CosmicPioneer(
        emoji = "🛡️",
        name = "Orion",
        role = "Next-gen offline Android backup & restoration",
        license = "GPLv3",
        url = "https://github.com/XayahSuSuSu/Android-DataBackup"
    ),
    CosmicPioneer(
        emoji = "🔊",
        name = "SplitAudio",
        role = "Per-app audio routing & media stream separation",
        license = "MIT",
        url = "https://github.com/splitaudio"
    ),
    // Row 5: Voice Isolation AI | Volume++
    CosmicPioneer(
        emoji = "🎙️",
        name = "Voice Isolation",
        role = "Realtime ONNX neural dialogue & GTCRN model",
        license = "MIT",
        url = "https://github.com/andren96/android-realtime-voice-isolation"
    ),
    CosmicPioneer(
        emoji = "🎚️",
        name = "Volume++",
        role = "Multi-channel per-app audio mixer & volume boost",
        license = "MIT",
        url = "https://github.com/noel-digital-fan/volume_plus_plus"
    ),
    // Row 6: Atmos Wallpaper | Shizuku Stores
    CosmicPioneer(
        emoji = "🌌",
        name = "Atmos Wallpaper",
        role = "Dynamic depth & kinetic parallax live wallpaper",
        license = null,
        url = null
    ),
    CosmicPioneer(
        emoji = "🛸",
        name = "Shizuku Stores",
        role = "Elevated package installation ecosystems",
        license = null,
        url = null
    )
)

/**
 * Nebula Credits Hub ("Hall of the Universe").
 * Uncategorized 2-Column Supergiant Grid with full LightspeedStyle theme compliance.
 */
@Composable
fun NebulaHallOfFameDialog(
    context: Context,
    onDismissRequest: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val rows = HALL_OF_FAME_PIONEERS.chunked(2)

    DeckDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "HALL OF THE UNIVERSE",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.sp,
                    letterSpacing = 1.sp,
                    color = lsColors.textPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Best Apps in the Universe • Uncategorized 2-Column Grid",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        },
        content = {
            Spacer(modifier = Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rows) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (pioneer in rowItems) {
                            PioneerGridTile(
                                context = context,
                                pioneer = pioneer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            DeckButton(
                text = "Close Deck",
                onClick = onDismissRequest,
                isActive = true
            )
        }
    )
}

@Composable
private fun PioneerGridTile(
    context: Context,
    pioneer: CosmicPioneer,
    modifier: Modifier = Modifier
) {
    val lsColors = LightspeedStyle.colors
    val hasUrl = !pioneer.url.isNullOrBlank()

    DeckCard(
        modifier = modifier
            .height(94.dp)
            .then(
                if (hasUrl) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(pioneer.url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                } else Modifier
            ),
        contentPadding = PaddingValues(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = pioneer.emoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = pioneer.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = lsColors.textPrimary
                    )
                }
                if (hasUrl) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Launch,
                        contentDescription = "Open link",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Text(
                text = pioneer.role,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
            )

            if (pioneer.license != null) {
                Text(
                    text = "[${pioneer.license}]",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.secondary
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}
