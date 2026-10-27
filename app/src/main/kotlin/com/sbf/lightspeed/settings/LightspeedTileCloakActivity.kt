package com.sbf.lightspeed.settings

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.sbf.lightspeed.system.ElevatedTaskCloser
import com.sbf.lightspeed.system.LightspeedHapticEngine
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedWatchdogEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Tactical Configuration Modal for Quick Settings Tile Cloaking.
 *
 * Triggered by long-pressing the Lightspeed Master Quick Settings tile
 * (via android.service.quicksettings.action.QS_TILE_PREFERENCES).
 * Allows the user to select which installed Accessibility Services to automatically
 * cloak (disable) alongside Lightspeed to bypass banking app security blocks or
 * facilitate seamless Nightly/Stable switching.
 */
class LightspeedTileCloakActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LightspeedTheme(forceDark = true) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { finish() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.94f)
                            .fillMaxHeight(0.85f)
                            .widthIn(max = 440.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {}
                            )
                    ) {
                        DeckGlassCardSurface(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            CloakMatrixContent(
                                context = this@LightspeedTileCloakActivity,
                                onDismiss = { finish() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CloakMatrixContent(
    context: Context,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val pm = context.packageManager
    val a11yManager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager }
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive

    var cloakedServicesSet by remember {
        mutableStateOf(LightspeedPreferences.getCloakedAccessibilityServices(context))
    }

    val installedA11y = remember {
        try {
            a11yManager?.installedAccessibilityServiceList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val enabledComponents = remember {
        LightspeedWatchdogEngine.getEnabledAccessibilityServices(context)
    }

    val thirdPartyServices = remember(installedA11y) {
        installedA11y.filter { sInfo ->
            val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
            sPkg != context.packageName
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredServices = remember(thirdPartyServices, searchQuery) {
        if (searchQuery.isBlank()) thirdPartyServices
        else {
            val query = searchQuery.trim().lowercase()
            thirdPartyServices.filter { sInfo ->
                val sPkg = (sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")).lowercase()
                val sLabel = try {
                    sInfo.resolveInfo?.loadLabel(pm)?.toString()?.lowercase() ?: ""
                } catch (_: Exception) { "" }
                sPkg.contains(query) || sLabel.contains(query)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = lsColors.accentWarn.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, lsColors.accentWarn.copy(alpha = 0.4f)),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = lsColors.accentWarn,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column {
                    Text(
                        text = "QS TILE CLOAK MATRIX",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.textPrimary
                    )
                    Text(
                        text = "Stealth Accessibility Coordination",
                        fontSize = 10.sp,
                        color = lsColors.textSecondary
                    )
                }
            }

            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = lsColors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Telemetry Status Chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = (if (isShizukuActive) lsColors.accentOk else lsColors.accentWarn).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, (if (isShizukuActive) lsColors.accentOk else lsColors.accentWarn).copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isShizukuActive) lsColors.accentOk else lsColors.accentWarn)
                    )
                    Text(
                        text = if (isShizukuActive) "SHIZUKU ACTIVE" else "SHIZUKU INACTIVE",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (isShizukuActive) lsColors.accentOk else lsColors.accentWarn
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = lsColors.accentInfo.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, lsColors.accentInfo.copy(alpha = 0.35f)),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(lsColors.accentInfo)
                    )
                    Text(
                        text = "EDGE BACK RECOVERY: 1.0",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = lsColors.accentInfo
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Explanatory Subtitle
        Text(
            text = "Services selected below will be silently disabled together with Lightspeed when tapping the Master QS Tile, keeping banking and security apps completely clear of accessibility flags. Native back gestures restore to 1.0 immediately.",
            fontSize = 10.5.sp,
            color = lsColors.textSecondary.copy(alpha = 0.85f),
            lineHeight = 14.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Search Bar
        if (thirdPartyServices.size > 3) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter accessibility services...", fontSize = 11.sp, color = lsColors.textDisabled) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = lsColors.accentInfo, modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = lsColors.textDisabled, modifier = Modifier.size(14.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 11.5.sp, color = lsColors.textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = lsColors.accentInfo,
                    unfocusedBorderColor = lsColors.textPrimary.copy(alpha = 0.15f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 5. Services List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredServices.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, lsColors.textPrimary.copy(alpha = 0.08f))
                    ) {
                        Text(
                            text = if (searchQuery.isBlank()) "No external Accessibility Services detected.\nLightspeed will be cloaked alone." else "No matching services found.",
                            fontSize = 11.sp,
                            color = lsColors.textSecondary,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredServices,
                    key = { sInfo ->
                        val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                        val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                        ComponentName(sPkg, sCls).flattenToString()
                    }
                ) { sInfo ->
                    val sPkg = sInfo.resolveInfo?.serviceInfo?.packageName ?: sInfo.id.substringBefore("/")
                    val sCls = sInfo.resolveInfo?.serviceInfo?.name ?: sInfo.id.substringAfter("/")
                    val component = ComponentName(sPkg, sCls)
                    val componentId = component.flattenToString()
                    val isCloaked = cloakedServicesSet.contains(componentId)
                    val isServiceEnabled = enabledComponents.contains(component)

                    val sLabel = remember(componentId) {
                        try {
                            sInfo.resolveInfo?.loadLabel(pm)?.toString() ?: sPkg
                        } catch (_: Exception) { sPkg }
                    }

                    val iconBitmap = remember(sPkg) {
                        try {
                            val d = sInfo.resolveInfo?.loadIcon(pm) ?: pm.getApplicationIcon(sPkg)
                            d.toBitmap(width = 56, height = 56).asImageBitmap()
                        } catch (_: Throwable) {
                            null
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val nowCloaked = LightspeedPreferences.toggleCloakedAccessibilityService(context, componentId)
                                cloakedServicesSet = LightspeedPreferences.getCloakedAccessibilityServices(context)
                                LightspeedHapticEngine.tick(context)
                                Toast.makeText(
                                    context,
                                    if (nowCloaked) "Will cloak $sLabel with Lightspeed" else "Uncloaked $sLabel",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCloaked) lsColors.accentWarn.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(
                            1.dp,
                            if (isCloaked) lsColors.accentWarn.copy(alpha = 0.45f) else lsColors.textPrimary.copy(alpha = 0.08f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (iconBitmap != null) {
                                    Image(
                                        bitmap = iconBitmap,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(lsColors.textPrimary.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Security,
                                            contentDescription = null,
                                            tint = lsColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = sLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isCloaked) lsColors.accentWarn else lsColors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isServiceEnabled) lsColors.accentOk else lsColors.textDisabled)
                                        )
                                        Text(
                                            text = if (isServiceEnabled) "RUNNING" else "STOPPED",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = if (isServiceEnabled) lsColors.accentOk else lsColors.textDisabled
                                        )
                                        Text(
                                            text = "• $sPkg",
                                            fontSize = 9.sp,
                                            color = lsColors.textDisabled,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isCloaked,
                                onCheckedChange = { checked ->
                                    val nowCloaked = LightspeedPreferences.toggleCloakedAccessibilityService(context, componentId)
                                    cloakedServicesSet = LightspeedPreferences.getCloakedAccessibilityServices(context)
                                    LightspeedHapticEngine.tick(context)
                                    Toast.makeText(
                                        context,
                                        if (nowCloaked) "Will cloak $sLabel with Lightspeed" else "Uncloaked $sLabel",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = lsColors.accentWarn,
                                    checkedTrackColor = lsColors.accentWarn.copy(alpha = 0.35f),
                                    uncheckedThumbColor = lsColors.textDisabled,
                                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 6. Action Button
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("DONE", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
