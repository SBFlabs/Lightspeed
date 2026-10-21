package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.logSwallowed
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Suppress("DEPRECATION")
internal fun Context.extractShortcutToken(data: Intent): String {
    var generatedToken = ""

    // 1. Prioritize authentic Intent extraction (MacroDroid style)
    val shortcutIntent = try {
        IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
    } catch (_: Exception) { null }

    if (shortcutIntent != null) {
        val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
        val rawBmp: Bitmap? = try {
            IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
        } catch (_: Exception) { null }

        val shortcutBmp = rawBmp ?: run {
            val iconRes = try {
                IntentCompat.getParcelableExtra(data, Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
            } catch (_: Exception) { null }
            if (iconRes != null) {
                try {
                    val foreignRes = packageManager.getResourcesForApplication(iconRes.packageName)
                    val id = foreignRes.getIdentifier(iconRes.resourceName, null, null)
                    if (id != 0) {
                        val d = foreignRes.getDrawable(id, null)
                        if (d != null) {
                            val w = d.intrinsicWidth.coerceIn(48, 256)
                            val h = d.intrinsicHeight.coerceIn(48, 256)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            d.setBounds(0, 0, w, h)
                            d.draw(canvas)
                            bmp
                        } else null
                    } else null
                } catch (_: Exception) { null }
            } else null
        }

        val pkg = shortcutIntent.`package` ?: shortcutIntent.component?.packageName ?: ""
        generatedToken = LightspeedShortcutManager.createCustomShortcutToken(
            context = this,
            pkg = pkg,
            label = shortcutName,
            intent = shortcutIntent,
            bitmap = shortcutBmp
        )
    }

    // 2. Fallback to PIN_ITEM_REQUEST if EXTRA_SHORTCUT_INTENT was missing
    if (generatedToken.isBlank() && data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
        val pinRequest = try {
            IntentCompat.getParcelableExtra(data, "android.content.pm.extra.PIN_ITEM_REQUEST", LauncherApps.PinItemRequest::class.java)
        } catch (_: Exception) { null }

        if (pinRequest != null && pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
            try { pinRequest.accept() } catch (e: Exception) { logSwallowed("CockpitGearPickerActivity", "extractShortcutToken:1007", e) }
            val info = pinRequest.shortcutInfo
            if (info != null) {
                val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: "Shortcut"
                generatedToken = LightspeedShortcutManager.createPinnedShortcutToken(info.`package`, info.id, label)
            }
        }
    }

    return generatedToken
}

internal fun filterForSplitMode(list: List<PickerRowItem>): List<PickerRowItem> {
    val allowedSystemTokens = setOf(
        "system:refueling_bay",
        "system:perimeter_watchdog",
        "system:core_watchdog",
        "system:battery_exemption",
        "system:central_command"
    )

    val allowedActions = list.filterIsInstance<PickerRowItem.SystemAction>()
        .filter { allowedSystemTokens.contains(it.token) }

    if (allowedActions.isEmpty()) {
        return list.filter { item ->
            item !is PickerRowItem.SystemHeader &&
            item !is PickerRowItem.SystemCategoryHeader &&
            item !is PickerRowItem.SystemAction &&
            item !is PickerRowItem.SystemCustomizationOption &&
            item !is PickerRowItem.SystemCustomizationSlider
        }
    }

    val result = mutableListOf<PickerRowItem>()
    var currentCatHeader: PickerRowItem.SystemCategoryHeader? = null
    val currentCatActions = mutableListOf<PickerRowItem>()

    fun flushCategory() {
        val catHeader = currentCatHeader ?: return
        val validInCat = currentCatActions.filter {
            (it is PickerRowItem.SystemAction && allowedSystemTokens.contains(it.token)) ||
            (it is PickerRowItem.SystemCustomizationOption && allowedSystemTokens.contains(it.parentToken)) ||
            (it is PickerRowItem.SystemCustomizationSlider && allowedSystemTokens.contains(it.parentToken))
        }
        val actionCount = validInCat.count { it is PickerRowItem.SystemAction }
        if (actionCount > 0) {
            result.add(catHeader.copy(count = actionCount))
            result.addAll(validInCat)
        }
        currentCatHeader = null
        currentCatActions.clear()
    }

    for (item in list) {
        when (item) {
            is PickerRowItem.SystemHeader -> {
                result.add(item.copy(count = allowedActions.size))
            }
            is PickerRowItem.SystemCategoryHeader -> {
                flushCategory()
                currentCatHeader = item
            }
            is PickerRowItem.SystemAction,
            is PickerRowItem.SystemCustomizationOption,
            is PickerRowItem.SystemCustomizationSlider -> {
                if (currentCatHeader != null) {
                    currentCatActions.add(item)
                } else {
                    if (item is PickerRowItem.SystemAction && allowedSystemTokens.contains(item.token)) {
                        result.add(item)
                    }
                }
            }
            else -> {
                flushCategory()
                result.add(item)
            }
        }
    }
    flushCategory()

    return result
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PickerPopupStyleOptionRow(
    item: PickerRowItem.SystemCustomizationOption,
    dynamicSecondary: Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (item.isSelected) dynamicSecondary.copy(alpha = 0.22f)
                else lsColors.textPrimary.copy(alpha = 0.04f)
            )
            .border(
                width = 1.dp,
                color = if (item.isSelected) dynamicSecondary.copy(alpha = 0.65f) else lsColors.textPrimary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = item.title,
                color = if (item.isSelected) dynamicSecondary else lsColors.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 15.sp
                )
            )
            Text(
                text = item.subtitle,
                color = lsColors.textSecondary.copy(alpha = 0.55f),
                fontSize = 10.sp,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = 12.sp
                )
            )
        }
        if (item.isSelected) {
            Box(
                modifier = Modifier
                    .background(dynamicSecondary, CircleShape)
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ACTIVE",
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
