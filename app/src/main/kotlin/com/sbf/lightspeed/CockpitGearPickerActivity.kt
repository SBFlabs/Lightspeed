package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.settings.resolveDynamicTokenLabel
import com.sbf.lightspeed.system.LightspeedPreferences
import com.sbf.lightspeed.system.LightspeedShortcutManager
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

class CockpitGearPickerActivity : ComponentActivity() {
    override fun finish() {
        finishAndRemoveTask()
        super.finish()
        overrideZeroTransition()
    }

    override fun onDestroy() {
        super.onDestroy()
        val setId = intent.getStringExtra("SET_ID")
        val isSingleSelect = !intent.getStringExtra("SINGLE_SELECT_PREF_KEY").isNullOrBlank()
        if (!isSingleSelect && !setId.isNullOrBlank()) {
            val setIndex = setId.toIntOrNull() ?: -1
            if (setIndex >= 0) {
                try {
                    LightspeedAccessibilityService.instance?.reopenCockpitHangar(setIndex)
                } catch (_: Exception) {}
            }
        }
    }

    @OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val singleSelectPrefKey = intent.getStringExtra("SINGLE_SELECT_PREF_KEY")
        val singleSelectTitle = intent.getStringExtra("SINGLE_SELECT_TITLE") ?: "Select Action"
        val isSingleSelect = !singleSelectPrefKey.isNullOrBlank()
        val isHoldGesture = intent.getBooleanExtra("IS_HOLD_GESTURE", false) ||
                singleSelectPrefKey?.contains("_HOLD", ignoreCase = true) == true ||
                singleSelectPrefKey?.contains("SCRUB", ignoreCase = true) == true

        val setId = intent.getStringExtra("SET_ID") ?: "0"
        val ringIndex = intent.getIntExtra("RING_INDEX", 0)

        val primaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_600)
        } else {
            0xFF6750A4.toInt()
        }
        val secondaryAccent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_300)
        } else {
            0xFFD0BCFF.toInt()
        }

        val dynamicPrimary = Color(primaryAccent)
        val dynamicSecondary = Color(secondaryAccent)

        com.sbf.lightspeed.system.LightspeedIconManager.clearCache()
        LightspeedActionRegistry.iconBitmapCache.clear()
        LightspeedActionRegistry.initializeSync(this)

        val prefs = defaultPrefs()
        if (!prefs.contains(LightspeedPreferences.KEY_PICKER_PINNED_APPS)) {
            val defaultTools = listOf("com.arlosoft.macrodroid", "net.dinglisch.android.taskerm", "io.github.sds100.keymapper", "rk.android.app.shortcutmaker")
            val pm = packageManager
            val installedDefaults = defaultTools.filter { pkg ->
                try { pm.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
            }.toSet()
            prefs.edit().putStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, installedDefaults).apply()
        }

        val initialItems = if (isSingleSelect) {
            val currentToken = prefs.getString(singleSelectPrefKey, "none") ?: "none"
            if (currentToken != "none") listOf(currentToken) else emptyList()
        } else {
            val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", "") ?: ""
            csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        setContent {
            LightspeedTheme(forceDark = true) {
                val context: Context = this@CockpitGearPickerActivity
                var searchQuery by remember { mutableStateOf("") }
                val selectedTokens = remember { mutableStateListOf<String>().apply { addAll(initialItems) } }
                val allSysCategories = listOf("collapsed:sys_nav", "collapsed:sys_hw", "collapsed:sys_media", "collapsed:sys_tactical", "collapsed:sys_orient", "collapsed:sys_scrub")
                var systemAccordionMode by remember { mutableStateOf(prefs.getString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SYSTEM_ACCORDION_MODE, "expanded") ?: "expanded") }
                var expandedSubsections by remember {
                    mutableStateOf(
                        if (systemAccordionMode == "remember") {
                            prefs.getStringSet(com.sbf.lightspeed.system.LightspeedPreferences.KEY_EXPANDED_SUBSECTIONS, emptySet()) ?: emptySet()
                        } else if (systemAccordionMode == "collapsed") {
                            allSysCategories.toSet()
                        } else {
                            emptySet()
                        }
                    )
                }

                LaunchedEffect(expandedSubsections, systemAccordionMode) {
                    if (systemAccordionMode == "remember") {
                        prefs.edit().putStringSet(com.sbf.lightspeed.system.LightspeedPreferences.KEY_EXPANDED_SUBSECTIONS, expandedSubsections).apply()
                    }
                }
                val listState = rememberLazyListState()
                val coroutineScope = rememberCoroutineScope()
                var sideBarHeight by remember { mutableStateOf(1f) }
                var isDragging by remember { mutableStateOf(false) }
var showHud by remember { mutableStateOf(false) }
                var hudLetter by remember { mutableStateOf("") }
                var pinnedApps by remember {
                    mutableStateOf(prefs.getStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, emptySet()) ?: emptySet())
                }
                var pendingPinApp by remember { mutableStateOf<Pair<String, String>?>(null) }
                var pendingDeepActivitySubKey by remember { mutableStateOf<String?>(null) }
                var showDeepActivityWarning by remember { mutableStateOf(false) }
                var hudStyleVersion by remember { mutableStateOf(0) }
                var showSystemAccordionPrefsDialog by remember { mutableStateOf(false) }

                fun handleTokenSelection(token: String) {
                    if (isSingleSelect) {
                        prefs.edit().putString(singleSelectPrefKey, token).apply()
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                        setResult(Activity.RESULT_OK, Intent().putExtra("SELECTED_TOKEN", token))
                        finish()
                    } else {
                        if (selectedTokens.contains(token)) selectedTokens.remove(token)
                        else selectedTokens.add(token)
                    }
                }

                LaunchedEffect(Unit) {
                    LightspeedActionRegistry.ensureIndexed(this@CockpitGearPickerActivity)
                }

                val allTokens = LightspeedActionRegistry.allTokens
                val labelCache = LightspeedActionRegistry.labelCache

                val shortcutConfigLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val data = result.data
                    if (data != null && result.resultCode == Activity.RESULT_OK) {
                        var generatedToken = ""

                        // 1. Prioritize authentic Intent extraction (MacroDroid style)
                        val shortcutIntent = try {
                            if (Build.VERSION.SDK_INT >= 33) {
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT, Intent::class.java)
                            } else {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                            }
                        } catch (_: Exception) {
                            @Suppress("DEPRECATION")
                            data.getParcelableExtra(Intent.EXTRA_SHORTCUT_INTENT) as? Intent
                        }

                        if (shortcutIntent != null) {
                            val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                            val rawBmp: Bitmap? = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON, Bitmap::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON) as? Bitmap
                                }
                            } catch (_: Exception) { null }

                            val shortcutBmp = rawBmp ?: run {
                                val iconRes = try {
                                    if (Build.VERSION.SDK_INT >= 33) {
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource::class.java)
                                    } else {
                                        @Suppress("DEPRECATION")
                                        data.getParcelableExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE) as? Intent.ShortcutIconResource
                                    }
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
                                context = this@CockpitGearPickerActivity,
                                pkg = pkg,
                                label = shortcutName,
                                intent = shortcutIntent,
                                bitmap = shortcutBmp
                            )
                        }

                        // 2. Fallback to PIN_ITEM_REQUEST if EXTRA_SHORTCUT_INTENT was missing
                        if (generatedToken.isBlank() && data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
                            val pinRequest = try {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST", LauncherApps.PinItemRequest::class.java)
                                } else {
                                    @Suppress("DEPRECATION")
                                    data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                                }
                            } catch (_: Exception) {
                                @Suppress("DEPRECATION")
                                data.getParcelableExtra("android.content.pm.extra.PIN_ITEM_REQUEST") as? LauncherApps.PinItemRequest
                            }

                            if (pinRequest != null && pinRequest.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT) {
                                try { pinRequest.accept() } catch (_: Exception) {}
                                val info = pinRequest.shortcutInfo
                                if (info != null) {
                                    val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: "Shortcut"
                                    generatedToken = LightspeedShortcutManager.createPinnedShortcutToken(info.`package`, info.id, label)
                                }
                            }
                        }


                        if (generatedToken.isNotBlank()) {
                            handleTokenSelection(generatedToken)
                        }
                    }
                }

                // Build hierarchical items list
                val flatItemsList by remember(allTokens.size, labelCache.size, searchQuery, expandedSubsections, hudStyleVersion) {
                    derivedStateOf {
                        buildFlatItemsList(
                            allTokens = allTokens,
                            labelCache = labelCache,
                            searchQuery = searchQuery,
                            expandedSubsections = expandedSubsections,
                            pinnedApps = pinnedApps,
                            prefs = prefs,
                            singleSelectPrefKey = singleSelectPrefKey,
                            isHoldOrScrubGesture = isHoldGesture
                        )
                    }
                }

                val letterIndices = remember(flatItemsList) {
                    val map = mutableMapOf<Char, Int>()
                    flatItemsList.forEachIndexed { index, item ->
                        if (item is PickerRowItem.AppHeader && !item.isPinned) {
                            val firstChar = item.appName.firstOrNull()?.uppercaseChar() ?: '#'
                            val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                            if (!map.containsKey(targetKey)) map[targetKey] = index
                        }
                    }
                    map
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .fillMaxHeight(0.90f)
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14).copy(alpha = 0.95f))
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            val rawName = defaultPrefs().getString("gear_set_${setId}_name", "") ?: ""
                            val sName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
                                when(setId) {
                                    "0" -> "POWER USER ANDROID"
                                    "1" -> "MY APP STORES"
                                    "2" -> "UTILITIES SECTOR"
                                    "3" -> "ENTERTAINMENT DECK"
                                    else -> "CUSTOM SET"
                                }
                            } else rawName

                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isSingleSelect) singleSelectTitle else "Assign to Gear ${ringIndex + 1} (${if (ringIndex == 0) "Outer" else "Inner"})",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = if (isSingleSelect) "Tap any app, deep shortcut or system action to assign" else "Profile: $sName (${selectedTokens.size} selected)",
                                        fontSize = 12.sp,
                                        color = dynamicSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { finish() },
                                    modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }

                            // Search Field
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search apps, shortcuts & actions...", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                singleLine = true
                            )

                            // None / Clear action row (single-select only)
                            if (isSingleSelect && searchQuery.isBlank()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selectedTokens.isEmpty() || selectedTokens.contains("none")) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.04f))
                                        .border(1.dp, if (selectedTokens.isEmpty() || selectedTokens.contains("none")) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                        .clickable { handleTokenSelection("none") }
                                        .padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("None (No Action)", color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Clear and disable action for this gesture", color = Color.LightGray.copy(alpha = 0.5f), fontSize = 10.5.sp)
                                    }
                                    if (selectedTokens.isEmpty() || selectedTokens.contains("none")) {
                                        Box(
                                            modifier = Modifier.background(dynamicPrimary, CircleShape).padding(horizontal = 8.dp, vertical = 3.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }

                            // Sticky Advisory Warning Banner
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFB300).copy(alpha = 0.12f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = "Deep activities bypass standard app entry points and may produce unpredictable results.", fontSize = 11.sp, color = Color(0xFFFFE082), lineHeight = 14.sp)
                                }
                            }

                            // Selected Tokens Reorder Tray
                            if (!isSingleSelect && selectedTokens.isNotEmpty()) {
                                Text(
                                    text = "RING COG ORDER (${selectedTokens.size} PAYLOADS) — TAP ◀ ▶ TO SHIFT:",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = dynamicSecondary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(selectedTokens.size, key = { idx -> "selected_${idx}_${selectedTokens[idx]}" }) { idx ->
                                        val token = selectedTokens[idx]
                                        val label = LightspeedShortcutManager.resolveLabel(this@CockpitGearPickerActivity, token)
                                        val iconBmp = remember(token) {
                                            LightspeedShortcutManager.resolveIconBitmap(this@CockpitGearPickerActivity, token)
                                        }
                                        val isFirst = (idx == 0)
                                        val isLast = (idx == selectedTokens.size - 1)

                                        Row(
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                                                .border(1.dp, dynamicPrimary.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                                .padding(horizontal = 6.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (selectedTokens.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        if (!isFirst) {
                                                            val temp = selectedTokens[idx]
                                                            selectedTokens[idx] = selectedTokens[idx - 1]
                                                            selectedTokens[idx - 1] = temp
                                                        }
                                                    },
                                                    enabled = !isFirst,
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Text("◀", fontSize = 10.sp, color = if (!isFirst) dynamicSecondary else Color.Gray.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (iconBmp != null) {
                                                androidx.compose.foundation.Image(
                                                    bitmap = iconBmp.asImageBitmap(),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp).padding(end = 4.dp)
                                                )
                                            }

                                            Text(
                                                text = "${idx + 1}. ${label.take(13)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )

                                            if (selectedTokens.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        if (!isLast) {
                                                            val temp = selectedTokens[idx]
                                                            selectedTokens[idx] = selectedTokens[idx + 1]
                                                            selectedTokens[idx + 1] = temp
                                                        }
                                                    },
                                                    enabled = !isLast,
                                                    modifier = Modifier.size(22.dp)
                                                ) {
                                                    Text("▶", fontSize = 10.sp, color = if (!isLast) dynamicSecondary else Color.Gray.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            IconButton(
                                                onClick = { selectedTokens.removeAt(idx) },
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF6B6B), modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Main List + A-Z Scrubber
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    ) {
                                        items(items = flatItemsList, key = { it.key }) { item ->
                                            when (item) {
                                                is PickerRowItem.SystemHeader -> PickerSystemHeaderRow(
                                                    item = item,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onClick = {
                                                        val sysKey = "category:system"
                                                        expandedSubsections = if (item.isExpanded) expandedSubsections - sysKey else expandedSubsections + sysKey
                                                    },
                                                    onToggleAll = {
                                                        val areAllCollapsed = allSysCategories.all { expandedSubsections.contains(it) }
                                                        expandedSubsections = if (areAllCollapsed) {
                                                            expandedSubsections - allSysCategories.toSet()
                                                        } else {
                                                            expandedSubsections + allSysCategories.toSet()
                                                        }
                                                    },
                                                    onToggleAllLongClick = {
                                                        showSystemAccordionPrefsDialog = true
                                                    }
                                                )
                                                is PickerRowItem.SystemCategoryHeader -> PickerSystemCategoryHeaderRow(
                                                    item = item,
                                                    dynamicPrimary = dynamicPrimary,
                                                    onClick = {
                                                        val collapseKey = "collapsed:${item.categoryKey}"
                                                        expandedSubsections = if (item.isExpanded) expandedSubsections + collapseKey else expandedSubsections - collapseKey
                                                    }
                                                )
                                                is PickerRowItem.SystemAction -> PickerSystemActionRow(
                                                    item = item,
                                                    isChecked = selectedTokens.contains(item.token),
                                                    dynamicPrimary = dynamicPrimary,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onSelect = { handleTokenSelection(item.token) },
                                                    onCustomizeToggle = {
                                                        val customKey = "customization:${item.token}"
                                                        expandedSubsections = if (item.isExpanded) expandedSubsections - customKey else expandedSubsections + customKey
                                                    }
                                                )
                                                is PickerRowItem.SystemCustomizationSlider -> PickerCustomizationSliderRow(
                                                    item = item,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onValueChange = { newVal ->
                                                        when (item.prefKey) {
                                                            LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION -> {
                                                                val resVal = kotlin.math.round(newVal).toInt().coerceIn(10, 254)
                                                                val stepVal = (255f / resVal).toInt().coerceIn(1, 32)
                                                                prefs.edit()
                                                                    .putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_RESOLUTION, resVal)
                                                                    .putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, stepVal)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION -> {
                                                                val resVal = kotlin.math.round(newVal).toInt().coerceIn(5, 100)
                                                                val stepVal = (100f / resVal).roundToInt().coerceIn(1, 20)
                                                                prefs.edit()
                                                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_RESOLUTION, resVal)
                                                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, stepVal)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            LightspeedPreferences.KEY_VOLUME_SCRUB_STEP -> {
                                                                val volVal = kotlin.math.round(newVal).toInt().coerceIn(1, 25)
                                                                prefs.edit()
                                                                    .putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, volVal)
                                                                    .apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            else -> {
                                                                prefs.edit().putFloat(item.prefKey, newVal).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                        }
                                                    }
                                                )
                                                is PickerRowItem.SystemCustomizationOption -> PickerCustomizationOptionRow(
                                                    item = item,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onClick = {
                                                        when {
                                                            item.parentToken == "system:media_skip_forward" || item.parentToken == "system:media_skip_backward" -> {
                                                                val sec = item.optionKey.toIntOrNull() ?: 10
                                                                prefs.edit().putInt(LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, sec).apply()
                                                                LightspeedActionRegistry.labelCache["system:media_skip_forward"] = resolveDynamicTokenLabel(this@CockpitGearPickerActivity, "system:media_skip_forward")
                                                                LightspeedActionRegistry.labelCache["system:media_skip_backward"] = resolveDynamicTokenLabel(this@CockpitGearPickerActivity, "system:media_skip_backward")
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("brightness_hud_toggle:") -> {
                                                                val enabled = item.optionKey.endsWith(":on")
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_BRIGHTNESS_ENABLED, enabled).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("volume_hud_toggle:") -> {
                                                                val enabled = item.optionKey.endsWith(":on")
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_HUD_VOLUME_ENABLED, enabled).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("volume_native_slider:") -> {
                                                                val enabled = item.optionKey.endsWith(":on")
                                                                prefs.edit().putBoolean(LightspeedPreferences.KEY_VOLUME_SHOW_NATIVE_SLIDER, enabled).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("brightness_step:") -> {
                                                                val step = item.optionKey.removePrefix("brightness_step:").toIntOrNull() ?: 8
                                                                prefs.edit().putInt(LightspeedPreferences.KEY_BRIGHTNESS_SCRUB_STEP, step).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("volume_step:") -> {
                                                                val step = item.optionKey.removePrefix("volume_step:").toIntOrNull() ?: 1
                                                                prefs.edit().putInt(LightspeedPreferences.KEY_VOLUME_SCRUB_STEP, step).apply()
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            item.optionKey.startsWith("hud_style:") -> {
                                                                val style = item.optionKey.removePrefix("hud_style:")
                                                                LightspeedPreferences.saveHudStyle(prefs, singleSelectPrefKey, item.parentToken, style)
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                            else -> {
                                                                val style = item.optionKey
                                                                LightspeedPreferences.saveHudStyle(prefs, singleSelectPrefKey, item.parentToken, style)
                                                                try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                        }
                                                    }
                                                )
                                                is PickerRowItem.AppHeader -> PickerAppHeaderRow(
                                                    item = item,
                                                    isAppSelected = selectedTokens.contains(item.appToken),
                                                    context = context,
                                                    dynamicPrimary = dynamicPrimary,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onSelect = { handleTokenSelection(item.appToken) },
                                                    onLongClick = { pendingPinApp = Pair(item.packageName, item.appName) },
                                                    onExpandToggle = {
                                                        val appKey = "app:${item.packageName}"
                                                        expandedSubsections = if (item.isExpanded) expandedSubsections - appKey else expandedSubsections + appKey
                                                    }
                                                )
                                                is PickerRowItem.SubHeader -> PickerSubHeaderRow(
                                                    item = item,
                                                    dynamicSecondary = dynamicSecondary,
                                                    onClick = {
                                                        val isDeepActivitiesHeader = item.subKey.endsWith("|DeepActivities")
                                                        if (isDeepActivitiesHeader && !item.isExpanded) {
                                                            val isSuppressed = prefs.getBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, false)
                                                            if (!isSuppressed) {
                                                                pendingDeepActivitySubKey = item.subKey
                                                                showDeepActivityWarning = true
                                                                return@PickerSubHeaderRow
                                                            }
                                                        }
                                                        expandedSubsections = if (item.isExpanded) expandedSubsections - item.subKey else expandedSubsections + item.subKey
                                                    }
                                                )
                                                is PickerRowItem.ShortcutAction -> PickerShortcutActionRow(
                                                    item = item,
                                                    isChecked = selectedTokens.contains(item.token),
                                                    context = context,
                                                    dynamicPrimary = dynamicPrimary,
                                                    onSelect = { handleTokenSelection(item.token) },
                                                    onLaunchWizard = {
                                                        val pkg = item.token.substringAfter(";pkg=").substringBefore(";")
                                                        val act = item.token.substringAfter(";activity=").substringBefore(";")
                                                        val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                            setClassName(pkg, act)
                                                        }
                                                        shortcutConfigLauncher.launch(intent)
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // A-Z Scrubber Sidebar
                                    var currentDragY by remember { mutableStateOf(0f) }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(32.dp)
                                            .padding(start = 6.dp)
                                            .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                            .pointerInput(flatItemsList) {
                                                awaitPointerEventScope {
                                                    while (true) {
                                                        val down = awaitFirstDown(requireUnconsumed = false)
                                                        var hasDragged = false
                                                        var initialY = down.position.y
                                                        showHud = true

                                                        fun calculateInterpolatedIndex(yPos: Float) {
                                                            if (flatItemsList.isEmpty()) return
                                                            val letterArray = "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray()
                                                            val letterRatio = (yPos / sideBarHeight).coerceIn(0f, 0.999f)
                                                            val fractionalIndex = letterRatio * letterArray.size
                                                            val letterIndex = fractionalIndex.toInt()
                                                            val fractionWithinLetter = fractionalIndex - letterIndex

                                                            var startListIdx = -1
                                                            var searchStart = letterIndex
                                                            while (searchStart >= 0) {
                                                                val l = letterArray[searchStart]
                                                                if (letterIndices.containsKey(l)) {
                                                                    startListIdx = letterIndices[l]!!
                                                                    break
                                                                }
                                                                searchStart--
                                                            }
                                                            if (startListIdx == -1) startListIdx = 0

                                                            var endListIdx = -1
                                                            var searchEnd = letterIndex + 1
                                                            while (searchEnd < letterArray.size) {
                                                                val l = letterArray[searchEnd]
                                                                if (letterIndices.containsKey(l)) {
                                                                    endListIdx = letterIndices[l]!!
                                                                    break
                                                                }
                                                                searchEnd++
                                                            }
                                                            if (endListIdx == -1) endListIdx = flatItemsList.size - 1

                                                            val finalFloatIdx = startListIdx + (fractionWithinLetter * (endListIdx - startListIdx))
                                                            val finalListIdx = finalFloatIdx.toInt().coerceIn(0, flatItemsList.size - 1)

                                                            coroutineScope.launch { listState.scrollToItem(finalListIdx) }

                                                            val item = flatItemsList[finalListIdx]
                                                            val label = when (item) {
                                                                is PickerRowItem.SystemHeader -> "System Actions"
                                                                is PickerRowItem.SystemCategoryHeader -> item.title
                                                                is PickerRowItem.SystemAction -> item.label
                                                                is PickerRowItem.SystemCustomizationOption -> item.title
                                                                is PickerRowItem.SystemCustomizationSlider -> item.title
                                                                is PickerRowItem.AppHeader -> item.appName
                                                                is PickerRowItem.SubHeader -> item.label.substringBefore(" (")
                                                                is PickerRowItem.ShortcutAction -> item.label
                                                            }
                                                            hudLetter = if (label.length >= 2) label.take(1).uppercase() + label.substring(1, 2).lowercase() else label.uppercase()
                                                        }

                                                        // Handle immediate tap
                                                        calculateInterpolatedIndex(initialY.coerceIn(0f, sideBarHeight))

                                                        while (true) {
                                                            val event = awaitPointerEvent()
                                                            val dragChange = event.changes.firstOrNull()
                                                            if (dragChange != null && dragChange.pressed) {
                                                                val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                                if (!hasDragged && kotlin.math.abs(dragY - initialY) > 8f) {
                                                                    hasDragged = true
                                                                    isDragging = true
                                                                }
                                                                
                                                                if (hasDragged) {
                                                                    currentDragY = dragY
                                                                    calculateInterpolatedIndex(dragY)
                                                                }
                                                                dragChange.consume()
                                                            } else {
                                                                isDragging = false
                                                                showHud = false
                                                                break
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                    ) {
                                        if (isDragging) {
                                            // Morph into a physical scrollbar track and thumb
                                            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(Color.White.copy(alpha=0.1f), RoundedCornerShape(2.dp)).align(Alignment.Center))
                                            Box(
                                                modifier = Modifier
                                                    .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) { (currentDragY - 16.dp.toPx()).toDp() })
                                                    .height(32.dp).width(6.dp)
                                                    .background(dynamicPrimary, RoundedCornerShape(3.dp))
                                                    .align(Alignment.TopCenter)
                                            )
                                        } else {
                                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                                                "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                                    val hasApps = letterIndices.containsKey(letter)
                                                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = letter.toString(),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (hasApps) dynamicSecondary else Color.White.copy(alpha = 0.2f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Dragging HUD Letter Overlay
                                if (showHud && hudLetter.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(80.dp)
                                            .background(Color(0xFF1E1E28).copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                                            .border(2.2.dp, dynamicPrimary, shape = RoundedCornerShape(16.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = hudLetter, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    }
                                }
                            }

                            // Bottom Save/Cancel Bar (multi-select mode)
                            if (!isSingleSelect) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { finish() },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f))
                                    ) {
                                        Text("CANCEL", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            prefs.edit().putString("gear_set_${setId}_ring_${ringIndex}_packages", selectedTokens.joinToString(",")).apply()
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("SAVE (${selectedTokens.size})", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Dialogs
                    if (pendingPinApp != null) {
                        val (pinPkg, pinName) = pendingPinApp!!
                        PinAppDialog(
                            packageName = pinPkg,
                            appName = pinName,
                            isCurrentlyPinned = pinnedApps.contains(pinPkg),
                            dynamicPrimary = dynamicPrimary,
                            onConfirm = { nowPinned ->
                                val newSet = if (nowPinned) pinnedApps + pinPkg else pinnedApps - pinPkg
                                pinnedApps = newSet
                                prefs.edit().putStringSet(LightspeedPreferences.KEY_PICKER_PINNED_APPS, newSet).apply()
                                pendingPinApp = null
                            },
                            onDismiss = { pendingPinApp = null }
                        )
                    }

                    if (showDeepActivityWarning && pendingDeepActivitySubKey != null) {
                        DeepActivityWarningDialog(
                            dynamicPrimary = dynamicPrimary,
                            onProceed = { doNotShowAgain ->
                                if (doNotShowAgain) {
                                    prefs.edit().putBoolean(LightspeedPreferences.KEY_SUPPRESS_DEEP_ACTIVITY_WARNING, true).apply()
                                }
                                val subKeyToExpand = pendingDeepActivitySubKey
                                showDeepActivityWarning = false
                                pendingDeepActivitySubKey = null
                                if (subKeyToExpand != null) {
                                    expandedSubsections = expandedSubsections + subKeyToExpand
                                }
                            },
                            onDismiss = {
                                showDeepActivityWarning = false
                                pendingDeepActivitySubKey = null
                            }
                        )
                    }

                    if (showSystemAccordionPrefsDialog) {
                        SystemAccordionPrefsDialog(
                            dynamicPrimary = dynamicPrimary,
                            currentPreference = systemAccordionMode,
                            onSelectPreference = { mode ->
                                systemAccordionMode = mode
                                prefs.edit().putString(com.sbf.lightspeed.system.LightspeedPreferences.KEY_SYSTEM_ACCORDION_MODE, mode).apply()
                                if (mode == "expanded") {
                                    expandedSubsections = expandedSubsections - allSysCategories.toSet()
                                } else if (mode == "collapsed") {
                                    expandedSubsections = expandedSubsections + allSysCategories.toSet()
                                }
                                showSystemAccordionPrefsDialog = false
                            },
                            onDismiss = { showSystemAccordionPrefsDialog = false }
                        )
                    }
                }
            }
        }
    }
}
