@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.sbf.lightspeed.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.ui.theme.LightspeedStyle

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
    onMoveSubDown: (String, Int) -> Unit = { _, _ -> },
    fillViewport: Boolean = false
) {
    if (fillViewport) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 4.dp, bottom = 8.dp)
        ) {
            BlueprintHeaderBanner(tabTitle = tabTitle)
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScrollWithEdgeFade(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BlueprintSectionCardsList(
                    sectionIds = sectionIds,
                    pinnedSectionId = pinnedSectionId,
                    sectionTitles = sectionTitles,
                    onMoveUp = onMoveUp,
                    onMoveDown = onMoveDown,
                    onPinSection = onPinSection,
                    subSections = subSections,
                    subSectionTitles = subSectionTitles,
                    onMoveSubUp = onMoveSubUp,
                    onMoveSubDown = onMoveSubDown
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            BlueprintExitButton(onExitBlueprint = onExitBlueprint)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BlueprintHeaderBanner(tabTitle = tabTitle)
            BlueprintSectionCardsList(
                sectionIds = sectionIds,
                pinnedSectionId = pinnedSectionId,
                sectionTitles = sectionTitles,
                onMoveUp = onMoveUp,
                onMoveDown = onMoveDown,
                onPinSection = onPinSection,
                subSections = subSections,
                subSectionTitles = subSectionTitles,
                onMoveSubUp = onMoveSubUp,
                onMoveSubDown = onMoveSubDown
            )
            Spacer(modifier = Modifier.height(2.dp))
            BlueprintExitButton(onExitBlueprint = onExitBlueprint)
        }
    }
}

@Composable
private fun BlueprintHeaderBanner(tabTitle: String) {
    val lsColors = LightspeedStyle.colors
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ViewAgenda,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$tabTitle — Blueprint Reorder Mode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = lsColors.textPrimary
                )
                Text(
                    text = "Use the arrows to reorder sections. Tap the pin to set the Anchored Solo section.",
                    fontSize = 11.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.BlueprintSectionCardsList(
    sectionIds: List<String>,
    pinnedSectionId: String?,
    sectionTitles: Map<String, String>,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onPinSection: (String) -> Unit,
    subSections: Map<String, List<String>>,
    subSectionTitles: Map<String, String>,
    onMoveSubUp: (String, Int) -> Unit,
    onMoveSubDown: (String, Int) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val disabledArrowColor = lsColors.textPrimary.copy(alpha = 0.18f)

    sectionIds.forEachIndexed { index, secId ->
        val isPinned = (secId == pinnedSectionId)
        val hasPinnedChild = pinnedSectionId != null && pinnedSectionId.startsWith("${secId}_")
        val title = sectionTitles[secId] ?: secId

        DeckCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.2.dp,
                    color = when {
                        isPinned -> MaterialTheme.colorScheme.primary
                        hasPinnedChild -> MaterialTheme.colorScheme.primary.copy(alpha = 0.48f)
                        else -> lsColors.textPrimary.copy(alpha = 0.12f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ),
            shape = RoundedCornerShape(12.dp),
            tint = when {
                isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                hasPinnedChild -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else -> Color.Unspecified
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag Handle",
                    tint = lsColors.textPrimary.copy(alpha = 0.45f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = lsColors.textPrimary
                    )
                    if (isPinned || hasPinnedChild) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = if (isPinned) "Anchored Open" else "Contains Anchored Sub-Section",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { onPinSection(secId) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "Pin",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.35f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onMoveUp(index) },
                    enabled = index > 0,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Up",
                        tint = if (index > 0) MaterialTheme.colorScheme.secondary else disabledArrowColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = { onMoveDown(index) },
                    enabled = index < sectionIds.size - 1,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Down",
                        tint = if (index < sectionIds.size - 1) MaterialTheme.colorScheme.secondary else disabledArrowColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        val subs = subSections[secId] ?: emptyList()
        if (subs.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                subs.forEachIndexed { subIndex, subId ->
                    val isSubPinned = ("${secId}_${subId}" == pinnedSectionId)
                    val subTitle = subSectionTitles["${secId}_${subId}"] ?: subSectionTitles[subId] ?: subId
                    DeckCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSubPinned) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.08f),
                                RoundedCornerShape(8.dp)
                            ),
                        shape = RoundedCornerShape(8.dp),
                        tint = if (isSubPinned) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Unspecified
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = if (isSubPinned) MaterialTheme.colorScheme.primary else lsColors.textSecondary.copy(alpha = 0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subTitle,
                                    fontWeight = if (isSubPinned) FontWeight.SemiBold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSubPinned) lsColors.textPrimary else lsColors.textSecondary
                                )
                            }

                            IconButton(
                                onClick = { onPinSection("${secId}_${subId}") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PushPin,
                                    contentDescription = "Pin Sub",
                                    tint = if (isSubPinned) MaterialTheme.colorScheme.primary else lsColors.textPrimary.copy(alpha = 0.25f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { onMoveSubUp(secId, subIndex) },
                                enabled = subIndex > 0,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Up",
                                    tint = if (subIndex > 0) MaterialTheme.colorScheme.secondary else disabledArrowColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            IconButton(
                                onClick = { onMoveSubDown(secId, subIndex) },
                                enabled = subIndex < subs.size - 1,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Down",
                                    tint = if (subIndex < subs.size - 1) MaterialTheme.colorScheme.secondary else disabledArrowColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlueprintExitButton(onExitBlueprint: () -> Unit) {
    Button(
        onClick = onExitBlueprint,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Done (Exit Blueprint)",
            fontWeight = FontWeight.Bold,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}
