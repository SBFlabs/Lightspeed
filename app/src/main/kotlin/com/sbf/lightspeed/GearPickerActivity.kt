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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.settings.resolveDynamicTokenLabel
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme
import kotlinx.coroutines.launch

private val ScrewdriverVector: ImageVector = ImageVector.Builder(
    name = "Screwdriver",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = PathParser().parsePathString(
        "M21.71,3.71L20.29,2.29C19.9,1.9 19.27,1.9 18.88,2.29L13.88,7.29L16.71,10.12L21.71,5.12C22.1,4.73 22.1,4.1 21.71,3.71M15.29,11.54L12.46,8.71L4.88,16.29L3.46,14.88L2.05,16.29L4.88,19.12L2.05,21.95L3.46,23.36L6.29,20.54L9.12,23.36L10.54,21.95L9.12,20.54L16.71,12.95L15.29,11.54Z"
    ).toNodes(),
    fill = SolidColor(Color.White)
).build()

sealed class PickerRowItem {
    abstract val key: String

    data class SystemHeader(val count: Int, val isExpanded: Boolean) : PickerRowItem() {
        override val key = "header_sys"
    }

    data class SystemCategoryHeader(
        val categoryKey: String,
        val title: String,
        val count: Int,
        val isExpanded: Boolean
    ) : PickerRowItem() {
        override val key = "sys_cat_$categoryKey"
    }

    data class SystemAction(
        val token: String,
        val label: String,
        val isCustomizable: Boolean = false,
        val isExpanded: Boolean = false
    ) : PickerRowItem() {
        override val key = "action_$token"
    }

    data class SystemCustomizationOption(
        val parentToken: String,
        val optionKey: String,
        val title: String,
        val subtitle: String,
        val isSelected: Boolean
    ) : PickerRowItem() {
        override val key = "sys_opt_${parentToken}_$optionKey"
    }

    data class AppHeader(
        val appName: String,
        val packageName: String,
        val appToken: String,
        val isExpanded: Boolean,
        val totalShortcuts: Int
    ) : PickerRowItem() {
        override val key = "app_header_$packageName"
    }

    data class SubHeader(
        val packageName: String,
        val subKey: String,
        val label: String,
        val isExpanded: Boolean
    ) : PickerRowItem() {
        override val key = "sub_header_$subKey"
    }

    data class ShortcutAction(
        val token: String,
        val label: String,
        val packageName: String,
        val isPlugin: Boolean
    ) : PickerRowItem() {
        override val key = "shortcut_$token"
    }
}

class GearPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val singleSelectPrefKey = intent.getStringExtra("SINGLE_SELECT_PREF_KEY")
        val singleSelectTitle = intent.getStringExtra("SINGLE_SELECT_TITLE") ?: "Select Action"
        val isSingleSelect = !singleSelectPrefKey.isNullOrBlank()

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
        val initialItems = if (isSingleSelect) {
            val currentToken = prefs.getString(singleSelectPrefKey, "none") ?: "none"
            if (currentToken != "none") listOf(currentToken) else emptyList()
        } else {
            val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", "") ?: ""
            csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }

        setContent {
            LightspeedTheme(forceDark = true) {
            var searchQuery by remember { mutableStateOf("") }
            val selectedTokens = remember { mutableStateListOf<String>().apply { addAll(initialItems) } }
            var expandedSubsections by remember { mutableStateOf(setOf<String>()) }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            var sideBarHeight by remember { mutableStateOf(1f) }
            var isDragging by remember { mutableStateOf(false) }
            var hudLetter by remember { mutableStateOf("") }

            fun handleTokenSelection(token: String) {
                if (isSingleSelect) {
                    prefs.edit().putString(singleSelectPrefKey, token).apply()
                    try {
                        LightspeedAccessibilityService.instance?.reloadPreferences()
                    } catch (_: Exception) {}
                    setResult(Activity.RESULT_OK, Intent().putExtra("SELECTED_TOKEN", token))
                    finish()
                } else {
                    if (selectedTokens.contains(token)) {
                        selectedTokens.remove(token)
                    } else {
                        selectedTokens.add(token)
                    }
                }
            }

            LaunchedEffect(Unit) {
                LightspeedActionRegistry.ensureIndexed(this@GearPickerActivity)
            }

            val allTokens = LightspeedActionRegistry.allTokens
            val labelCache = LightspeedActionRegistry.labelCache

            val shortcutConfigLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val data = result.data
                if (data != null && result.resultCode == Activity.RESULT_OK) {
                    var generatedToken = ""

                    if (data.hasExtra("android.content.pm.extra.PIN_ITEM_REQUEST")) {
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
                                generatedToken = com.sbf.lightspeed.system.LightspeedShortcutManager.createPinnedShortcutToken(info.`package`, info.id, label)
                            }
                        }
                    }

                    if (generatedToken.isBlank()) {
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

                        val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: "Shortcut"
                        if (shortcutIntent != null) {
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
                                                val canvas = Canvas(bmp)
                                                d.setBounds(0, 0, w, h)
                                                d.draw(canvas)
                                                bmp
                                            } else null
                                        } else null
                                    } catch (_: Exception) { null }
                                } else null
                            }

                            val pkg = shortcutIntent.`package` ?: shortcutIntent.component?.packageName ?: ""
                            generatedToken = com.sbf.lightspeed.system.LightspeedShortcutManager.createCustomShortcutToken(
                                context = this@GearPickerActivity,
                                pkg = pkg,
                                label = shortcutName,
                                intent = shortcutIntent,
                                bitmap = shortcutBmp
                            )
                        }
                    }

                    if (generatedToken.isNotBlank()) {
                        handleTokenSelection(generatedToken)
                    }
                }
            }

            var hudStyleVersion by remember { mutableStateOf(0) }

            // Build hierarchical items with stable keys
            val flatItemsList by remember(allTokens.size, labelCache.size, searchQuery, expandedSubsections, hudStyleVersion) {
                derivedStateOf {
                    val list = mutableListOf<PickerRowItem>()
                    val validTokens = allTokens.filter { it != "none" }
                    val filtered = if (searchQuery.isBlank()) {
                        validTokens
                    } else {
                        validTokens.filter { token ->
                            val label = labelCache[token] ?: token
                            label.contains(searchQuery, ignoreCase = true) || token.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    // 1. System Actions (Organized by Subcategories)
                    val systemActions = filtered.filter { it.startsWith("system:") || it == "action_enter_gearset_nav" }
                    if (systemActions.isNotEmpty()) {
                        val sysKey = "category:system"
                        val isExpanded = expandedSubsections.contains(sysKey) || searchQuery.isNotBlank()
                        list.add(PickerRowItem.SystemHeader(systemActions.size, isExpanded))
                        if (isExpanded) {
                            val categories = listOf(
                                Triple(
                                    "sys_nav",
                                    "Navigation & Multitasking",
                                    listOf("action_enter_gearset_nav", "system:previous_app", "system:close_app", "system:recents", "system:home", "system:back", "system:split_screen", "system:popup_window")
                                ),
                                Triple(
                                    "sys_hw",
                                    "Hardware & System Controls",
                                    listOf("system:flashlight", "system:screenshot", "system:lock_screen", "system:notifications", "system:quick_settings", "system:scroll_to_top")
                                ),
                                Triple(
                                    "sys_media",
                                    "Media Actions",
                                    listOf(
                                        "system:media_play_pause",
                                        "system:media_next",
                                        "system:media_prev",
                                        "system:media_skip_forward",
                                        "system:media_skip_backward",
                                        "system:media_scrubber",
                                        "system:media_stop"
                                    )
                                ),
                                Triple(
                                    "sys_tactical",
                                    "Tactical Quick Action & AI Tools",
                                    listOf(
                                        "system:tactical_flyout",
                                        "system:tactical_audio",
                                        "system:lens",
                                        "system:qr_scanner",
                                        "system:chatgpt",
                                        "system:claude",
                                        "system:gemini",
                                        "system:refueling_bay"
                                    )
                                ),
                                Triple(
                                    "sys_orient",
                                    "System Attitude & Orientation",
                                    listOf(
                                        "system:orientation_toggle",
                                        "system:orientation_portrait",
                                        "system:orientation_sensor_360",
                                        "system:orientation_sensor_portrait"
                                    )
                                ),
                                Triple(
                                    "sys_scrub",
                                    "Gesture Scrubbers & Sliders",
                                    listOf("system:screen_timeout", "system:volume", "system:brightness")
                                )
                            )

                            categories.forEach { (catKey, catTitle, tokenList) ->
                                val matchingTokens = systemActions.filter { tokenList.contains(it) }
                                if (matchingTokens.isNotEmpty()) {
                                    val collapseKey = "collapsed:$catKey"
                                    val catExpanded = if (searchQuery.isNotBlank()) true else !expandedSubsections.contains(collapseKey)

                                    list.add(PickerRowItem.SystemCategoryHeader(catKey, catTitle, matchingTokens.size, catExpanded))

                                    if (catExpanded) {
                                        matchingTokens.forEach { token ->
                                            val isCustomizable = token == "system:screen_timeout" ||
                                                    token == "system:media_skip_forward" ||
                                                    token == "system:media_skip_backward"
                                            val customKey = "customization:$token"
                                            val isCustomExpanded = expandedSubsections.contains(customKey)
                                            list.add(PickerRowItem.SystemAction(token, labelCache[token] ?: token, isCustomizable, isCustomExpanded))

                                            if (isCustomizable && isCustomExpanded) {
                                                if (token == "system:media_skip_forward" || token == "system:media_skip_backward") {
                                                    val currentSkip = prefs.getInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, 10)
                                                    val durations = listOf(
                                                        Pair("5", "5 Seconds" to "Ultra-short quick leap"),
                                                        Pair("10", "10 Seconds (Default)" to "Standard music & podcast skip"),
                                                        Pair("15", "15 Seconds" to "Standard audiobook interval"),
                                                        Pair("30", "30 Seconds" to "Fast commercial & sponsor leap"),
                                                        Pair("60", "60 Seconds (1 Min)" to "Extended segment leap")
                                                    )
                                                    durations.forEach { (secStr, desc) ->
                                                        val (title, subtitle) = desc
                                                        list.add(
                                                            PickerRowItem.SystemCustomizationOption(
                                                                parentToken = token,
                                                                optionKey = secStr,
                                                                title = title,
                                                                subtitle = subtitle,
                                                                isSelected = currentSkip == secStr.toInt()
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    val hudPrefKey = if (!singleSelectPrefKey.isNullOrBlank()) {
                                                        singleSelectPrefKey.replace("pref_macro_action_", "pref_macro_hud_style_")
                                                    } else "pref_macro_hud_style_default"
                                                    val currentStyle = prefs.getString(hudPrefKey, "canopy_droppod") ?: "canopy_droppod"

                                                    list.add(
                                                        PickerRowItem.SystemCustomizationOption(
                                                            parentToken = token,
                                                            optionKey = "canopy_droppod",
                                                            title = "Tactical Canopy Drop-Pod",
                                                            subtitle = "Chamfered visor below status bar with 7-segment quantum gauge",
                                                            isSelected = currentStyle == "canopy_droppod"
                                                        )
                                                    )
                                                    list.add(
                                                        PickerRowItem.SystemCustomizationOption(
                                                            parentToken = token,
                                                            optionKey = "cockpit_reticle",
                                                            title = "Holographic Cockpit Reticle",
                                                            subtitle = "Upper-third focal circular tachyon arc with orbital lock pips",
                                                            isSelected = currentStyle == "cockpit_reticle"
                                                        )
                                                    )
                                                    list.add(
                                                        PickerRowItem.SystemCustomizationOption(
                                                            parentToken = token,
                                                            optionKey = "edge_blade",
                                                            title = "Dynamic Edge Blade",
                                                            subtitle = "Lateral energy ladder aligned to active swipe edge",
                                                            isSelected = currentStyle == "edge_blade"
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Apps and Shortcuts grouped by package
                    val nonSystem = filtered.filter { !it.startsWith("system:") && it != "action_enter_gearset_nav" }
                    val appMap = mutableMapOf<String, MutableList<String>>()
                    nonSystem.forEach { token ->
                        val pkg = when {
                            token.startsWith("app:") -> token.removePrefix("app:")
                            token.startsWith("shortcut:") && token.contains(";pkg=") -> token.substringAfter(";pkg=").substringBefore(";")
                            token.startsWith("shortcut:") && token.contains("package=") -> token.substringAfter("package=").substringBefore(";")
                            else -> "other"
                        }
                        appMap.getOrPut(pkg) { mutableListOf() }.add(token)
                    }

                    val sortedApps = appMap.keys.map { pkg ->
                        val appLabel = labelCache["app:$pkg"] ?: pkg
                        Triple(pkg, appLabel, appMap[pkg] ?: emptyList())
                    }.sortedBy { it.second.lowercase() }

                    sortedApps.forEach { (pkg, appName, tokens) ->
                        val appToken = tokens.firstOrNull { it.startsWith("app:") } ?: "app:$pkg"
                        val appShortcuts = tokens.filter { it.contains(";type=app_shortcut;") }
                        val homeShortcuts = tokens.filter { it.contains(";type=home_shortcut;") }
                        val deepActivities = tokens.filter { it.contains(";type=activity;") }

                        val appKey = "app:$pkg"
                        val isExpanded = expandedSubsections.contains(appKey) || searchQuery.isNotBlank()
                        val totalShortcuts = appShortcuts.size + homeShortcuts.size + deepActivities.size

                        list.add(PickerRowItem.AppHeader(appName, pkg, appToken, isExpanded, totalShortcuts))

                        if (isExpanded) {
                            if (appShortcuts.isNotEmpty()) {
                                val subKey = "$pkg|AppShortcuts"
                                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                                list.add(PickerRowItem.SubHeader(pkg, subKey, "📱 App Shortcuts (${appShortcuts.size})", isSubExpanded))
                                if (isSubExpanded) {
                                    appShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = true))
                                    }
                                }
                            }

                            if (homeShortcuts.isNotEmpty()) {
                                val subKey = "$pkg|HomeShortcuts"
                                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                                list.add(PickerRowItem.SubHeader(pkg, subKey, "🏠 Home Shortcuts (${homeShortcuts.size})", isSubExpanded))
                                if (isSubExpanded) {
                                    homeShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = false))
                                    }
                                }
                            }

                            if (deepActivities.isNotEmpty()) {
                                val subKey = "$pkg|DeepActivities"
                                val isSubExpanded = expandedSubsections.contains(subKey) || searchQuery.isNotBlank()
                                list.add(PickerRowItem.SubHeader(pkg, subKey, "⚡ Deep Activities (${deepActivities.size})", isSubExpanded))
                                if (isSubExpanded) {
                                    deepActivities.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                        list.add(PickerRowItem.ShortcutAction(it, labelCache[it] ?: it, pkg, isPlugin = false))
                                    }
                                }
                            }
                        }
                    }
                    list
                }
            }

            val letterIndices = remember(flatItemsList) {
                val map = mutableMapOf<Char, Int>()
                flatItemsList.forEachIndexed { index, item ->
                    if (item is PickerRowItem.AppHeader) {
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
                        val rawName = getSharedPreferences("default", Context.MODE_PRIVATE).getString("gear_set_${setId}_name", "") ?: ""
                        val sName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
                            when(setId) {
                                "0" -> "POWER USER ANDROID"
                                "1" -> "MY APP STORES"
                                "2" -> "UTILITIES SECTOR"
                                "3" -> "ENTERTAINMENT DECK"
                                else -> "CUSTOM SET"
                            }
                        } else rawName

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

                        if (isSingleSelect && searchQuery.isBlank()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedTokens.isEmpty() || selectedTokens.contains("none")) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.04f))
                                    .border(1.dp, if (selectedTokens.isEmpty() || selectedTokens.contains("none")) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                                    .clickable {
                                        handleTokenSelection("none")
                                    }
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
                                        modifier = Modifier
                                            .background(dynamicPrimary, CircleShape)
                                            .padding(horizontal = 8.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("ACTIVE", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                            }
                        }

                        // Selected Ring Payloads Reorder Tray
                        if (!isSingleSelect && selectedTokens.isNotEmpty()) {
                            Text(
                                text = "RING COG ORDER (${selectedTokens.size} PAYLOADS) — TAP ◀ ▶ TO SHIFT:",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = dynamicSecondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            androidx.compose.foundation.lazy.LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(selectedTokens.size, key = { idx -> "selected_${idx}_${selectedTokens[idx]}" }) { idx ->
                                    val token = selectedTokens[idx]
                                    val label = com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(this@GearPickerActivity, token)
                                    val iconBmp = remember(token) {
                                        com.sbf.lightspeed.system.LightspeedShortcutManager.resolveIconBitmap(this@GearPickerActivity, token)
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
                                            Image(
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

                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.weight(1f).fillMaxHeight()
                                ) {
                                    items(
                                        items = flatItemsList,
                                        key = { it.key }
                                    ) { item ->
                                        when (item) {
                                            is PickerRowItem.SystemHeader -> {
                                                val sysKey = "category:system"
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp)
                                                        .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            expandedSubsections = if (item.isExpanded) {
                                                                expandedSubsections - sysKey
                                                            } else {
                                                                expandedSubsections + sysKey
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "⚡ System Actions (${item.count})",
                                                        color = Color.White,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = dynamicSecondary,
                                                        modifier = Modifier
                                                            .size(20.dp)
                                                            .rotate(if (item.isExpanded) 180f else 0f)
                                                    )
                                                }
                                            }
                                            is PickerRowItem.SystemCategoryHeader -> {
                                                val collapseKey = "collapsed:${item.categoryKey}"
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 6.dp, end = 2.dp, top = 6.dp, bottom = 2.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(dynamicPrimary.copy(alpha = 0.08f))
                                                        .border(1.dp, dynamicPrimary.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            expandedSubsections = if (item.isExpanded) {
                                                                expandedSubsections + collapseKey
                                                            } else {
                                                                expandedSubsections - collapseKey
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "${item.title} (${item.count})",
                                                        color = dynamicPrimary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = dynamicPrimary,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .rotate(if (item.isExpanded) 180f else 0f)
                                                    )
                                                }
                                            }
                                            is PickerRowItem.SystemAction -> {
                                                val isChecked = selectedTokens.contains(item.token)
                                                val customKey = "customization:${item.token}"

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .padding(start = 14.dp, top = 2.dp, bottom = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Primary Action Board (Left)
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                            .clip(
                                                                RoundedCornerShape(
                                                                    topStart = 10.dp,
                                                                    bottomStart = 10.dp,
                                                                    topEnd = if (item.isCustomizable) 4.dp else 10.dp,
                                                                    bottomEnd = if (item.isCustomizable) 4.dp else 10.dp
                                                                )
                                                            )
                                                            .background(
                                                                color = if (isChecked) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.03f)
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isChecked) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f),
                                                                shape = RoundedCornerShape(
                                                                    topStart = 10.dp,
                                                                    bottomStart = 10.dp,
                                                                    topEnd = if (item.isCustomizable) 4.dp else 10.dp,
                                                                    bottomEnd = if (item.isCustomizable) 4.dp else 10.dp
                                                                )
                                                            )
                                                            .clickable {
                                                                handleTokenSelection(item.token)
                                                            }
                                                            .padding(horizontal = 12.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                                         ) {
                                                            Text(
                                                                text = item.label,
                                                                color = if (isChecked) dynamicPrimary else Color.White,
                                                                fontSize = 13.sp,
                                                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.SemiBold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                style = TextStyle(
                                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                    lineHeight = 16.sp
                                                                )
                                                            )
                                                            Text(
                                                                text = item.token,
                                                                color = Color.LightGray.copy(alpha = 0.40f),
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                style = TextStyle(
                                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                    lineHeight = 12.sp
                                                                )
                                                            )
                                                        }
                                                        if (isChecked) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .background(dynamicPrimary, CircleShape)
                                                                    .padding(horizontal = 8.dp, vertical = 2.5.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "SELECTED",
                                                                    color = Color.Black,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.ExtraBold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    // 2. Customization Screwdriver Button (Right)
                                                    if (item.isCustomizable) {
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .width(42.dp)
                                                                .fillMaxHeight()
                                                                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp))
                                                                .background(
                                                                    if (item.isExpanded) dynamicSecondary.copy(alpha = 0.35f)
                                                                    else dynamicSecondary.copy(alpha = 0.12f)
                                                                )
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = if (item.isExpanded) dynamicSecondary.copy(alpha = 0.65f) else dynamicSecondary.copy(alpha = 0.20f),
                                                                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 10.dp, bottomEnd = 10.dp)
                                                                )
                                                                .clickable {
                                                                    expandedSubsections = if (item.isExpanded) {
                                                                        expandedSubsections - customKey
                                                                    } else {
                                                                        expandedSubsections + customKey
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = ScrewdriverVector,
                                                                contentDescription = "Customize",
                                                                tint = if (item.isExpanded) dynamicSecondary else dynamicSecondary.copy(alpha = 0.9f),
                                                                modifier = Modifier.size(17.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            is PickerRowItem.SystemCustomizationOption -> {
                                                val hudPrefKey = if (!singleSelectPrefKey.isNullOrBlank()) {
                                                    singleSelectPrefKey.replace("pref_macro_action_", "pref_macro_hud_style_")
                                                } else "pref_macro_hud_style_default"

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 24.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (item.isSelected) dynamicSecondary.copy(alpha = 0.22f)
                                                            else Color.White.copy(alpha = 0.04f)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (item.isSelected) dynamicSecondary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.06f),
                                                            shape = RoundedCornerShape(8.dp)
                                                        )
                                                        .clickable {
                                                            if (item.parentToken == "system:media_skip_forward" || item.parentToken == "system:media_skip_backward") {
                                                                val sec = item.optionKey.toIntOrNull() ?: 10
                                                                prefs.edit()
                                                                    .putInt(com.sbf.lightspeed.system.LightspeedPreferences.KEY_MEDIA_SKIP_SECONDS, sec)
                                                                    .apply()
                                                                LightspeedActionRegistry.labelCache["system:media_skip_forward"] = resolveDynamicTokenLabel(this@GearPickerActivity, "system:media_skip_forward")
                                                                LightspeedActionRegistry.labelCache["system:media_skip_backward"] = resolveDynamicTokenLabel(this@GearPickerActivity, "system:media_skip_backward")
                                                                try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            } else {
                                                                prefs.edit()
                                                                    .putString(hudPrefKey, item.optionKey)
                                                                    .putString("pref_macro_hud_style_default", item.optionKey)
                                                                    .apply()
                                                                try { com.sbf.lightspeed.LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                                                                hudStyleVersion++
                                                            }
                                                        }
                                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(
                                                        modifier = Modifier.weight(1f),
                                                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                                    ) {
                                                        Text(
                                                            text = item.title,
                                                            color = if (item.isSelected) dynamicSecondary else Color.White,
                                                            fontSize = 12.5.sp,
                                                            fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                lineHeight = 15.sp
                                                            )
                                                        )
                                                        Text(
                                                            text = item.subtitle,
                                                            color = Color.LightGray.copy(alpha = 0.55f),
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
                                            is PickerRowItem.AppHeader -> {
                                                val appKey = "app:${item.packageName}"
                                                val isAppSelected = selectedTokens.contains(item.appToken)
                                                val hasShortcuts = item.totalShortcuts > 0

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(50.dp)
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    // 1. Main Board (Left): App Icon + Title + Subtitle + Selection Badge
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                            .clip(
                                                                RoundedCornerShape(
                                                                    topStart = 12.dp,
                                                                    bottomStart = 12.dp,
                                                                    topEnd = if (hasShortcuts) 4.dp else 12.dp,
                                                                    bottomEnd = if (hasShortcuts) 4.dp else 12.dp
                                                                )
                                                            )
                                                            .background(
                                                                if (isAppSelected) dynamicPrimary.copy(alpha = 0.28f)
                                                                else Color.White.copy(alpha = 0.06f)
                                                            )
                                                            .border(
                                                                width = 1.dp,
                                                                color = if (isAppSelected) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.07f),
                                                                shape = RoundedCornerShape(
                                                                    topStart = 12.dp,
                                                                    bottomStart = 12.dp,
                                                                    topEnd = if (hasShortcuts) 4.dp else 12.dp,
                                                                    bottomEnd = if (hasShortcuts) 4.dp else 12.dp
                                                                )
                                                            )
                                                            .clickable {
                                                                handleTokenSelection(item.appToken)
                                                            }
                                                            .padding(horizontal = 10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        val iconBmp = LightspeedActionRegistry.getIconBitmap(this@GearPickerActivity, item.packageName)
                                                        if (iconBmp != null) {
                                                            Image(
                                                                bitmap = iconBmp.asImageBitmap(),
                                                                contentDescription = null,
                                                                modifier = Modifier.size(26.dp).padding(end = 8.dp)
                                                            )
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(26.dp)
                                                                    .padding(end = 8.dp)
                                                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(5.dp))
                                                            )
                                                        }

                                                        Column(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxHeight(),
                                                            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                                        ) {
                                                            Text(
                                                                text = item.appName,
                                                                color = if (isAppSelected) dynamicPrimary else Color.White,
                                                                fontSize = 13.5.sp,
                                                                fontWeight = if (isAppSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                                style = TextStyle(
                                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                    lineHeight = 16.sp
                                                                ),
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            if (hasShortcuts) {
                                                                Text(
                                                                    text = "${item.totalShortcuts} deep action${if (item.totalShortcuts > 1) "s" else ""}",
                                                                    color = dynamicSecondary.copy(alpha = 0.85f),
                                                                    fontSize = 10.sp,
                                                                    style = TextStyle(
                                                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                        lineHeight = 12.sp
                                                                    ),
                                                                    maxLines = 1
                                                                )
                                                            }
                                                        }

                                                        // Selection Indicator Badge
                                                        if (isAppSelected) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(start = 6.dp)
                                                                    .background(dynamicPrimary, CircleShape)
                                                                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Text(
                                                                    text = "SELECTED",
                                                                    color = Color.Black,
                                                                    fontSize = 8.sp,
                                                                    fontWeight = FontWeight.ExtraBold
                                                                )
                                                            }
                                                        }
                                                    }

                                                    if (hasShortcuts) {
                                                        Spacer(modifier = Modifier.width(3.dp))

                                                        // 2. Expand Chevron Zone (Right): Distinct Material 3 Color-Coded Container with exact same compact height
                                                        Box(
                                                            modifier = Modifier
                                                                .width(42.dp)
                                                                .fillMaxHeight()
                                                                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp))
                                                                .background(
                                                                    if (item.isExpanded) dynamicSecondary.copy(alpha = 0.35f)
                                                                    else dynamicSecondary.copy(alpha = 0.12f)
                                                                )
                                                                .border(
                                                                    width = 1.dp,
                                                                    color = if (item.isExpanded) dynamicSecondary.copy(alpha = 0.65f) else dynamicSecondary.copy(alpha = 0.20f),
                                                                    shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                                                                )
                                                                .clickable {
                                                                    expandedSubsections = if (item.isExpanded) {
                                                                        expandedSubsections - appKey
                                                                    } else {
                                                                        expandedSubsections + appKey
                                                                    }
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                Icons.Default.ArrowDropDown,
                                                                contentDescription = if (item.isExpanded) "Collapse" else "Expand",
                                                                tint = if (item.isExpanded) dynamicSecondary else dynamicSecondary.copy(alpha = 0.9f),
                                                                modifier = Modifier
                                                                    .size(22.dp)
                                                                    .rotate(if (item.isExpanded) 180f else 0f)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            is PickerRowItem.SubHeader -> {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                                                        .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                                        .clickable {
                                                            expandedSubsections = if (item.isExpanded) {
                                                                expandedSubsections - item.subKey
                                                            } else {
                                                                expandedSubsections + item.subKey
                                                            }
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = item.label,
                                                        color = dynamicSecondary,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Icon(
                                                        Icons.Default.ArrowDropDown,
                                                        contentDescription = null,
                                                        tint = dynamicSecondary,
                                                        modifier = Modifier
                                                            .size(18.dp)
                                                            .rotate(if (item.isExpanded) 180f else 0f)
                                                    )
                                                }
                                            }
                                            is PickerRowItem.ShortcutAction -> {
                                                val isChecked = selectedTokens.contains(item.token)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(48.dp)
                                                        .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            color = if (isChecked) dynamicPrimary.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.03f)
                                                        )
                                                        .border(
                                                            width = 1.dp,
                                                            color = if (isChecked) dynamicPrimary.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.05f),
                                                            shape = RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable {
                                                            if (item.isPlugin && !isChecked) {
                                                                val pkg = item.token.substringAfter(";pkg=").substringBefore(";")
                                                                val act = item.token.substringAfter(";activity=").substringBefore(";")
                                                                val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                                    setClassName(pkg, act)
                                                                }
                                                                shortcutConfigLauncher.launch(intent)
                                                            } else {
                                                                handleTokenSelection(item.token)
                                                            }
                                                        }
                                                        .padding(horizontal = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val shortcutBmp = remember(item.token) {
                                                        com.sbf.lightspeed.system.LightspeedShortcutManager.resolveIconBitmap(this@GearPickerActivity, item.token)
                                                    }
                                                    if (shortcutBmp != null) {
                                                        Image(
                                                            bitmap = shortcutBmp.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(22.dp).padding(end = 8.dp)
                                                        )
                                                    }
                                                    val displayLabel = remember(item.token) {
                                                        com.sbf.lightspeed.system.LightspeedShortcutManager.resolveLabel(this@GearPickerActivity, item.token)
                                                    }
                                                    Column(
                                                        modifier = Modifier.weight(1f).fillMaxHeight(),
                                                        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically)
                                                    ) {
                                                        Text(
                                                            text = displayLabel,
                                                            color = if (isChecked) dynamicPrimary else Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                lineHeight = 16.sp
                                                            )
                                                        )
                                                        Text(
                                                            text = if (item.isPlugin) "Shortcut Creator Wizard" else item.token,
                                                            color = Color.LightGray.copy(alpha = 0.40f),
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = TextStyle(
                                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                                lineHeight = 12.sp
                                                            )
                                                        )
                                                    }

                                                    if (isChecked) {
                                                        Box(
                                                            modifier = Modifier
                                                                .background(dynamicPrimary, CircleShape)
                                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text(
                                                                text = "SELECTED",
                                                                color = Color.Black,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.ExtraBold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // High Sensitivity Continuous Drag A-Z Index Scroller
                                Column(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(32.dp)
                                        .padding(start = 6.dp)
                                        .onGloballyPositioned { sideBarHeight = it.size.height.toFloat().coerceAtLeast(1f) }
                                        .pointerInput(flatItemsList) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val down = awaitFirstDown(requireUnconsumed = false)
                                                    isDragging = true
                                                    val currentY = down.position.y.coerceIn(0f, sideBarHeight)

                                                    if (flatItemsList.isNotEmpty()) {
                                                        val ratio = (currentY / sideBarHeight).coerceIn(0f, 1f)
                                                        val idx = (ratio * (flatItemsList.size - 1)).toInt().coerceIn(0, flatItemsList.size - 1)
                                                        coroutineScope.launch { listState.scrollToItem(idx) }
                                                        
                                                        val item = flatItemsList[idx]
                                                        val label = when (item) {
                                                            is PickerRowItem.SystemHeader -> "System Actions"
                                                            is PickerRowItem.SystemCategoryHeader -> item.title
                                                            is PickerRowItem.SystemAction -> item.label
                                                            is PickerRowItem.SystemCustomizationOption -> item.title
                                                            is PickerRowItem.AppHeader -> item.appName
                                                            is PickerRowItem.SubHeader -> item.label.substringBefore(" (")
                                                            is PickerRowItem.ShortcutAction -> item.label
                                                        }

                                                        hudLetter = if (label.length >= 2) {
                                                            label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                        } else {
                                                            label.uppercase()
                                                        }
                                                    }
                                                    down.consume()

                                                    while (true) {
                                                        val event = awaitPointerEvent()
                                                        val dragChange = event.changes.firstOrNull()
                                                        if (dragChange != null && dragChange.pressed) {
                                                            val dragY = dragChange.position.y.coerceIn(0f, sideBarHeight)
                                                            if (flatItemsList.isNotEmpty()) {
                                                                val ratio = (dragY / sideBarHeight).coerceIn(0f, 1f)
                                                                val idx = (ratio * (flatItemsList.size - 1)).toInt().coerceIn(0, flatItemsList.size - 1)
                                                                coroutineScope.launch { listState.scrollToItem(idx) }
                                                                
                                                                val item = flatItemsList[idx]
                                                                val label = when (item) {
                                                                    is PickerRowItem.SystemHeader -> "System Actions"
                                                                    is PickerRowItem.SystemCategoryHeader -> item.title
                                                                    is PickerRowItem.SystemAction -> item.label
                                                                    is PickerRowItem.SystemCustomizationOption -> item.title
                                                                    is PickerRowItem.AppHeader -> item.appName
                                                                    is PickerRowItem.SubHeader -> item.label.substringBefore(" (")
                                                                    is PickerRowItem.ShortcutAction -> item.label
                                                                }

                                                                hudLetter = if (label.length >= 2) {
                                                                    label.take(1).uppercase() + label.substring(1, 2).lowercase()
                                                                } else {
                                                                    label.uppercase()
                                                                }
                                                            }
                                                            dragChange.consume()
                                                        } else {
                                                            isDragging = false
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                        val hasApps = letterIndices.containsKey(letter)
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
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

                            if (isDragging && hudLetter.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(80.dp)
                                        .background(Color(0xFF1E1E28).copy(alpha = 0.94f), shape = RoundedCornerShape(16.dp))
                                        .border(2.2.dp, dynamicPrimary, shape = RoundedCornerShape(16.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = hudLetter,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

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
                                        val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
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
            }
        }
    }
}
}
