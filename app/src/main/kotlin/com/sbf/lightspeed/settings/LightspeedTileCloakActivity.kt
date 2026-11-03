package com.sbf.lightspeed.settings

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.DeckTheme
import com.sbf.lightspeed.ui.theme.LightspeedStyle
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import com.sbf.lightspeed.ui.theme.LightspeedTokens

/**
 * Tactical Configuration Modal for Quick Settings Tile Cloaking.
 *
 * Triggered by long-pressing the Lightspeed Master Quick Settings tile
 * (via android.service.quicksettings.action.QS_TILE_PREFERENCES).
 * Allows the user to select which subsystems and installed Accessibility Services
 * to automatically cloak (disable) alongside Lightspeed.
 */
class LightspeedTileCloakActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LightspeedTheme(forceDark = true) {
                DeckTheme {
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
                                .fillMaxHeight(0.86f)
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
}

@Composable
private fun CloakChecklistItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val lsColors = LightspeedStyle.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = lsColors.textPrimary
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = lsColors.textSecondary,
                lineHeight = 12.sp
            )
        }
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = lsColors.accentWarn,
                uncheckedColor = lsColors.textSecondary
            )
        )
    }
}

@Composable
private fun CloakMatrixContent(
    context: Context,
    onDismiss: () -> Unit
) {
    val lsColors = LightspeedStyle.colors
    val pm = context.packageManager
    val prefs = remember { context.defaultPrefs() }
    val a11yManager = remember { context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager }
    val isShizukuActive = ElevatedTaskCloser.isShizukuActive

    var disableExtraDim by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EXTRA_DIM, true)) }
    var disableEyeCare by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EYE_CARE, false)) }
    var disableSmartPixels by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_SMART_PIXELS, true)) }
    var suspendSonicDeck by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_SUSPEND_SONIC_DECK, true)) }
    var hideDevOptions by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_DEV_OPTIONS, false)) }
    var hideUsbDebugging by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_USB_DEBUGGING, false)) }
    var stopShizuku by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_STOP_SHIZUKU, false)) }
    var shizukuFork by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_FORK, "auto") ?: "auto") }
    var toggleWatchdog by remember { mutableStateOf(prefs.getBoolean(LightspeedPreferences.KEY_CLOAK_SHIZUKU_WATCHDOG_TOGGLE, true)) }
    var shizukuToken by remember { mutableStateOf(prefs.getString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_TOGGLE_TOKEN, "") ?: "") }
    var isInterlocksExpanded by rememberSaveable { mutableStateOf(true) }

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
        // 1. Header (Fixed at top)
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
                        text = "Single-Tap Cloak Checklist & Service Guard",
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

        // Single Unified Scrollable LazyColumn for all content
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 2. Telemetry Status Chips
            item {
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
            }

            // 3. Explanatory Subtitle
            item {
                Text(
                    text = "Choose exactly what happens when you tap the Master QS Tile. Checked items are turned off during Cloak and automatically restored when you tap the tile again.",
                    fontSize = 10.5.sp,
                    color = lsColors.textSecondary.copy(alpha = 0.85f),
                    lineHeight = 14.sp
                )
            }

            // 4. System Cloak Interlocks Accordion (Full Checklist)
            item {
                DeckCard(
                    modifier = Modifier.fillMaxWidth(),
                    tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    isInterlocksExpanded = !isInterlocksExpanded
                                    LightspeedHapticEngine.tick(context)
                                },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "SYSTEM CLOAK INTERLOCKS (CHECKLIST)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = lsColors.accentInfo
                            )
                            Icon(
                                imageVector = if (isInterlocksExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isInterlocksExpanded) "Collapse" else "Expand",
                                tint = lsColors.accentInfo,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (isInterlocksExpanded) {
                            // 1. Extra Dim Overlay (Lightspeed feature -> ON by default)
                            CloakChecklistItem(
                                title = "Turn Off Extra Dim Overlay while Cloaked",
                                subtitle = "Lightspeed overlay window (ON by default; restores level on uncloak)",
                                checked = disableExtraDim,
                                onCheckedChange = {
                                    disableExtraDim = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EXTRA_DIM, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 2. Eye Care / Blue Light Filter (Native hardware matrix -> OFF by default)
                            CloakChecklistItem(
                                title = "Turn Off Eye Care (Blue Light Filter) while Cloaked",
                                subtitle = "Native hardware warmth doesn't trigger banking apps (OFF by default)",
                                checked = disableEyeCare,
                                onCheckedChange = {
                                    disableEyeCare = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_EYE_CARE, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 3. Smart Pixels & Video Override (ON by default)
                            CloakChecklistItem(
                                title = "Turn Off Smart Pixels & Video Override",
                                subtitle = "Removes OLED pixel mesh & brightness lock overlays while cloaked",
                                checked = disableSmartPixels,
                                onCheckedChange = {
                                    disableSmartPixels = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_DISABLE_SMART_PIXELS, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 4. Suspend Sonic Deck & Audio Loopback (ON by default)
                            CloakChecklistItem(
                                title = "Suspend Sonic Deck & Audio Loopback",
                                subtitle = "Releases AudioPolicy for side-by-side Nightly/Stable testing; restores on uncloak",
                                checked = suspendSonicDeck,
                                onCheckedChange = {
                                    suspendSonicDeck = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_SUSPEND_SONIC_DECK, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 5. Hide Developer Options
                            CloakChecklistItem(
                                title = "Hide Developer Options while Cloaked",
                                subtitle = "Sets development_settings_enabled = 0 via WRITE_SECURE_SETTINGS; restores on uncloak",
                                checked = hideDevOptions,
                                onCheckedChange = {
                                    hideDevOptions = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_DEV_OPTIONS, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 6. Hide USB / Wireless Debugging
                            CloakChecklistItem(
                                title = "Hide USB / Wireless Debugging while Cloaked",
                                subtitle = "Sets adb_enabled = 0 via WRITE_SECURE_SETTINGS; restores on uncloak",
                                checked = hideUsbDebugging,
                                onCheckedChange = {
                                    hideUsbDebugging = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_HIDE_USB_DEBUGGING, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            HorizontalDivider(color = lsColors.textPrimary.copy(alpha = 0.06f))

                            // 7. Stop Shizuku / Fork Server on Cloak
                            CloakChecklistItem(
                                title = "Stop Shizuku / Fork Server while Cloaked",
                                subtitle = "Dispatches STOP / START intents to Shizuku-Next or Shevery",
                                checked = stopShizuku,
                                onCheckedChange = {
                                    stopShizuku = it
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_STOP_SHIZUKU, it).apply()
                                    LightspeedHapticEngine.tick(context)
                                }
                            )

                            if (stopShizuku) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "TARGET SHIZUKU FORK",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = lsColors.accentInfo
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val forks = listOf(
                                            "auto" to "Auto / Both",
                                            "shizuku_next" to "Shizuku-Next",
                                            "shevery" to "Shevery"
                                        )
                                        forks.forEach { (id, label) ->
                                            TrackpadPillButton(
                                                text = label,
                                                isSelected = shizukuFork == id,
                                                onClick = {
                                                    shizukuFork = id
                                                    prefs.edit().putString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_FORK, id).apply()
                                                    LightspeedHapticEngine.tick(context)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    if (shizukuFork == "auto" || shizukuFork == "shizuku_next") {
                                        CloakChecklistItem(
                                            title = "Pause Shizuku-Next Watchdog while Cloaked",
                                            subtitle = "Sends WATCHDOG_OFF on cloak and WATCHDOG_ON on uncloak",
                                            checked = toggleWatchdog,
                                            onCheckedChange = {
                                                toggleWatchdog = it
                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_CLOAK_SHIZUKU_WATCHDOG_TOGGLE, it).apply()
                                                LightspeedHapticEngine.tick(context)
                                            }
                                        )
                                    }

                                    OutlinedTextField(
                                        value = shizukuToken,
                                        onValueChange = {
                                            shizukuToken = it.trim()
                                            prefs.edit().putString(LightspeedPreferences.KEY_CLOAK_SHIZUKU_TOGGLE_TOKEN, it.trim()).apply()
                                        },
                                        placeholder = {
                                            Text(
                                                "Auth Token (optional if 'Require auth token' is off)",
                                                fontSize = 10.sp,
                                                color = lsColors.textDisabled
                                            )
                                        },
                                        label = {
                                            Text("Broadcast Auth Token (--es auth)", fontSize = 10.sp)
                                        },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = lsColors.textPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Search Bar
            if (thirdPartyServices.size > 3) {
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter accessibility services...", fontSize = 11.sp, color = lsColors.textDisabled) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp),
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
                }
            }

            // 6. Services Items
            if (filteredServices.isEmpty()) {
                item {
                    DeckCard(
                        modifier = Modifier.fillMaxWidth(),
                        tint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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

                    DeckCard(
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
                        tint = if (isCloaked) lsColors.accentWarn.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
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

                            DeckSwitch(
                                checked = isCloaked,
                                onCheckedChange = {
                                    val nowCloaked = LightspeedPreferences.toggleCloakedAccessibilityService(context, componentId)
                                    cloakedServicesSet = LightspeedPreferences.getCloakedAccessibilityServices(context)
                                    LightspeedHapticEngine.tick(context)
                                    Toast.makeText(
                                        context,
                                        if (nowCloaked) "Will cloak $sLabel with Lightspeed" else "Uncloaked $sLabel",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 7. Action Button (Fixed at bottom)
        DeckButton(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("DONE", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}
