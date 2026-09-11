@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings



import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sbf.lightspeed.system.LightspeedIconManager
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
fun BlueprintWireframeView(
    tabTitle: String,
    sectionIds: List<String>,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onPinSection: (String) -> Unit,
    onExitBlueprint: () -> Unit,
    subSections: Map<String, List<String>> = emptyMap(),
    subSectionTitles: Map<String, String> = emptyMap(),
    onMoveSubUp: (String, Int) -> Unit = { _, _ -> },
    onMoveSubDown: (String, Int) -> Unit = { _, _ -> }
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ViewAgenda, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("$tabTitle — Blueprint Reorder Mode", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                    Text("Use ▲ / ▼ to reorder sections. Tap 📌 to designate the anchor open section.", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.85f))
                }
            }
        }

        sectionIds.forEachIndexed { index, secId ->
            val isPinned = (secId == pinnedSectionId)
            val title = sectionTitles[secId] ?: secId

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color(0xFF141724).copy(alpha = 0.8f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.2.dp,
                    if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag Handle",
                        tint = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        if (isPinned) {
                            Text(
                                "📌 Default Pinned Section",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    // Pin Anchor Button
                    IconButton(
                        onClick = { onPinSection(secId) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Up
                    IconButton(
                        onClick = { onMoveUp(index) },
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            contentDescription = "Up",
                            tint = if (index > 0) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Move Down
                    IconButton(
                        onClick = { onMoveDown(index) },
                        enabled = index < sectionIds.size - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = "Down",
                            tint = if (index < sectionIds.size - 1) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Render sub-sections if they exist
            val subs = subSections[secId] ?: emptyList()
            if (subs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    subs.forEachIndexed { subIndex, subId ->
                        val isSubPinned = ("${secId}_${subId}" == pinnedSectionId)
                        val subTitle = subSectionTitles[subId] ?: subId
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSubPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0xFF1B1E2B).copy(alpha = 0.8f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSubPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "↳ $subTitle",
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp,
                                        color = Color.LightGray
                                    )
                                }

                                // Pin Sub-Section
                                IconButton(
                                    onClick = { onPinSection("${secId}_${subId}") },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PushPin,
                                        contentDescription = "Pin Sub",
                                        tint = if (isSubPinned) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Move Sub Up
                                IconButton(
                                    onClick = { onMoveSubUp(secId, subIndex) },
                                    enabled = subIndex > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = "Up",
                                        tint = if (subIndex > 0) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }

                                // Move Sub Down
                                IconButton(
                                    onClick = { onMoveSubDown(secId, subIndex) },
                                    enabled = subIndex < subs.size - 1,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Down",
                                        tint = if (subIndex < subs.size - 1) MaterialTheme.colorScheme.secondary else Color.DarkGray,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

        }

        Button(
            onClick = onExitBlueprint,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("Done (Exit Blueprint)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
