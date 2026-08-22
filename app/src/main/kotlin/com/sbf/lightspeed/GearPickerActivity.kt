package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import kotlinx.coroutines.launch

class GearPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

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

        LightspeedActionRegistry.initializeSync(this)

        setContent {
            var searchQuery by remember { mutableStateOf("") }
            val selectedTokens = remember { mutableStateListOf<String>() }
            var expandedSubsections by remember { mutableStateOf(setOf<String>()) }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                LightspeedActionRegistry.ensureIndexed(this@GearPickerActivity)
                val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
                val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", "") ?: ""
                val currentItems = csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                selectedTokens.addAll(currentItems)
            }

            val allTokens = LightspeedActionRegistry.allTokens
            val labelCache = LightspeedActionRegistry.labelCache

            val shortcutConfigLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val data = result.data
                if (data != null && result.resultCode == Activity.RESULT_OK) {
                    var uriString = ""
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
                                val label = info.shortLabel?.toString() ?: info.longLabel?.toString() ?: ""
                                uriString = "shortcut:;id=${info.id};pkg=${info.`package`};label=$label;"
                            }
                        }
                    }

                    if (uriString.isBlank()) {
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

                        val shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME) ?: ""
                        if (shortcutIntent != null) {
                            val intentUri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                            uriString = if (shortcutName.isNotEmpty()) {
                                "shortcut:intent:$intentUri;custom_label=$shortcutName;"
                            } else {
                                "shortcut:$intentUri"
                            }
                        }
                    }

                    if (uriString.isNotBlank() && !selectedTokens.contains(uriString)) {
                        selectedTokens.add(uriString)
                    }
                }
            }

            // Build hierarchical items: Level 0 = App/Category, Level 1 = Subsection, Level 2 = Action Item
            val flatItemsList = remember(allTokens.size, searchQuery, expandedSubsections) {
                val list = mutableListOf<Triple<String, String, Int>>()

                val validTokens = allTokens.filter { it != "none" }
                val filtered = if (searchQuery.isBlank()) {
                    validTokens
                } else {
                    validTokens.filter { token ->
                        val label = labelCache[token] ?: token
                        label.contains(searchQuery, ignoreCase = true) || token.contains(searchQuery, ignoreCase = true)
                    }
                }

                // 1. System Actions Category
                val systemActions = filtered.filter { it.startsWith("system:") }
                if (systemActions.isNotEmpty()) {
                    list.add(Triple("category:system", "System Actions", 0))
                    val systemKey = "System Actions|Items"
                    val isExpanded = expandedSubsections.contains(systemKey) || searchQuery.isNotBlank()
                    if (isExpanded) {
                        systemActions.forEach { token ->
                            list.add(Triple(token, labelCache[token] ?: token, 2))
                        }
                    }
                }

                // 2. Apps and Nested App Shortcuts
                val nonSystem = filtered.filter { !it.startsWith("system:") }
                val groupedByApp = nonSystem.groupBy { token ->
                    when {
                        token.startsWith("app:") -> {
                            val pkg = token.removePrefix("app:")
                            labelCache["app:$pkg"] ?: pkg
                        }
                        token.startsWith("shortcut:") -> {
                            val raw = token.removePrefix("shortcut:")
                            val pkg = if (raw.contains(";pkg=")) raw.substringAfter(";pkg=").substringBefore(";") else ""
                            if (pkg.isNotEmpty()) labelCache["app:$pkg"] ?: pkg else "App Shortcuts"
                        }
                        else -> "Other"
                    }
                }.toSortedMap(compareBy { it.lowercase() })

                groupedByApp.forEach { (appName, tokens) ->
                    val appToken = tokens.firstOrNull { it.startsWith("app:") }
                    val appShortcuts = tokens.filter { it.contains(";type=app_shortcut;") }
                    val homeShortcuts = tokens.filter { it.contains(";type=home_shortcut;") }
                    val deepActivities = tokens.filter { it.contains(";type=activity;") }

                    list.add(Triple("app_header:$appName", appName, 0))

                    val appHeaderKey = "$appName|All"
                    val isAppExpanded = expandedSubsections.contains(appHeaderKey) || searchQuery.isNotBlank()

                    if (isAppExpanded) {
                        if (appToken != null) {
                            list.add(Triple(appToken, "Launch $appName", 2))
                        }

                        if (appShortcuts.isNotEmpty()) {
                            val subKey = "$appName|App Shortcuts"
                            list.add(Triple(subKey, "App Shortcuts (${appShortcuts.size})", 1))
                            if (expandedSubsections.contains(subKey) || searchQuery.isNotBlank()) {
                                appShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                    list.add(Triple(it, labelCache[it] ?: it, 2))
                                }
                            }
                        }

                        if (homeShortcuts.isNotEmpty()) {
                            val subKey = "$appName|Home Screen Shortcuts"
                            list.add(Triple(subKey, "Home Screen Shortcuts (${homeShortcuts.size})", 1))
                            if (expandedSubsections.contains(subKey) || searchQuery.isNotBlank()) {
                                homeShortcuts.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                    list.add(Triple(it, labelCache[it] ?: it, 2))
                                }
                            }
                        }

                        if (deepActivities.isNotEmpty()) {
                            val subKey = "$appName|Deep Activities"
                            list.add(Triple(subKey, "Deep Activities (${deepActivities.size})", 1))
                            if (expandedSubsections.contains(subKey) || searchQuery.isNotBlank()) {
                                deepActivities.sortedBy { (labelCache[it] ?: it).lowercase() }.forEach {
                                    list.add(Triple(it, labelCache[it] ?: it, 2))
                                }
                            }
                        }
                    }
                }
                list
            }

            val letterIndices = remember(flatItemsList) {
                val map = mutableMapOf<Char, Int>()
                flatItemsList.forEachIndexed { index, (_, label, level) ->
                    if (level == 0) {
                        val firstChar = label.firstOrNull()?.uppercaseChar() ?: '#'
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
                            Column {
                                Text(
                                    text = "Assign to Gear ${ringIndex + 1} (${if (ringIndex == 0) "Outer" else "Inner"})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Profile: $sName (${selectedTokens.size} selected)",
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
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true
                        )

                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            ) {
                                itemsIndexed(flatItemsList, key = { index, (key, _, level) -> "${level}_${key}_$index" }) { _, (key, label, level) ->
                                    when (level) {
                                        0 -> {
                                            val headerKey = if (key.startsWith("category:system")) "System Actions|Items" else "${label}|All"
                                            val isExpanded = expandedSubsections.contains(headerKey) || searchQuery.isNotBlank()
                                            val pkg = if (key.startsWith("app_header:")) {
                                                allTokens.firstOrNull { it.startsWith("app:") && labelCache[it] == label }?.removePrefix("app:") ?: ""
                                            } else ""

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp)
                                                    .background(Color.White.copy(alpha = 0.07f), RoundedCornerShape(10.dp))
                                                    .clickable {
                                                        expandedSubsections = if (expandedSubsections.contains(headerKey)) {
                                                            expandedSubsections - headerKey
                                                        } else {
                                                            expandedSubsections + headerKey
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (pkg.isNotEmpty()) {
                                                    val iconBmp = remember(pkg) {
                                                        LightspeedActionRegistry.getIconBitmap(this@GearPickerActivity, pkg)
                                                    }
                                                    if (iconBmp != null) {
                                                        Image(
                                                            bitmap = iconBmp.asImageBitmap(),
                                                            contentDescription = null,
                                                            modifier = Modifier.size(28.dp).padding(end = 8.dp)
                                                        )
                                                    } else {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(28.dp)
                                                                .padding(end = 8.dp)
                                                                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = label,
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.weight(1f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = dynamicSecondary,
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .rotate(if (isExpanded) 180f else 0f)
                                                )
                                            }
                                        }
                                        1 -> {
                                            val isExpanded = expandedSubsections.contains(key) || searchQuery.isNotBlank()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        expandedSubsections = if (expandedSubsections.contains(key)) {
                                                            expandedSubsections - key
                                                        } else {
                                                            expandedSubsections + key
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = label,
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
                                                        .rotate(if (isExpanded) 180f else 0f)
                                                )
                                            }
                                        }
                                        2 -> {
                                            val isChecked = selectedTokens.contains(key)
                                            val isConfiguredAction = key.contains(";type=app_shortcut;")

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 18.dp, top = 2.dp, bottom = 2.dp)
                                                    .background(
                                                        color = if (isChecked) dynamicPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.02f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isChecked) dynamicPrimary.copy(alpha = 0.5f) else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        if (isConfiguredAction && !isChecked) {
                                                            val pkg = key.substringAfter(";pkg=").substringBefore(";")
                                                            val act = key.substringAfter(";activity=").substringBefore(";")
                                                            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                                setClassName(pkg, act)
                                                            }
                                                            shortcutConfigLauncher.launch(intent)
                                                        } else {
                                                            if (isChecked) selectedTokens.remove(key) else selectedTokens.add(key)
                                                        }
                                                    }
                                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = label,
                                                        color = Color.White,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = key,
                                                        color = Color.LightGray.copy(alpha = 0.40f),
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = {
                                                        if (isConfiguredAction && !isChecked) {
                                                            val pkg = key.substringAfter(";pkg=").substringBefore(";")
                                                            val act = key.substringAfter(";activity=").substringBefore(";")
                                                            val intent = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
                                                                setClassName(pkg, act)
                                                            }
                                                            shortcutConfigLauncher.launch(intent)
                                                        } else {
                                                            if (isChecked) selectedTokens.remove(key) else selectedTokens.add(key)
                                                        }
                                                    },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = dynamicPrimary,
                                                        checkmarkColor = Color.White
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Fast A-Z Index Scroller
                            Column(
                                modifier = Modifier
                                    .width(22.dp)
                                    .fillMaxHeight()
                                    .padding(start = 4.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                    val targetIdx = letterIndices[letter]
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetIdx != null) dynamicSecondary else Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable(enabled = targetIdx != null) {
                                            targetIdx?.let { coroutineScope.launch { listState.scrollToItem(it) } }
                                        }.padding(vertical = 0.5.dp)
                                    )
                                }
                            }
                        }

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
