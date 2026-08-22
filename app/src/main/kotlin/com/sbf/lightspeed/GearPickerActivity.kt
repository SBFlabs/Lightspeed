package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import kotlinx.coroutines.launch

class GearPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val setId = intent.getStringExtra("SET_ID") ?: "0"
        val ringIndex = intent.getIntExtra("RING_INDEX", 0)
        val pm = packageManager

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
            val flatItemsList = remember(allTokens, searchQuery, expandedSubsections) {
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
                        .fillMaxWidth(0.94f)
                        .fillMaxHeight(0.88f)
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x1AFFFFFF))
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

                        Text(
                            text = "Assign Shortcuts to Gear ${ringIndex + 1} ($sName)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search apps, shortcuts & actions...", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp) },
                            leadingIcon = { Text("🔍", fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0x26FFFFFF),
                                unfocusedContainerColor = Color(0x14FFFFFF),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(28.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
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
                                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                                    .clickable {
                                                        expandedSubsections = if (expandedSubsections.contains(headerKey)) {
                                                            expandedSubsections - headerKey
                                                        } else {
                                                            expandedSubsections + headerKey
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (pkg.isNotEmpty()) {
                                                    AndroidView(
                                                        factory = { ctx -> ImageView(ctx) },
                                                        modifier = Modifier.size(28.dp).padding(end = 8.dp),
                                                        update = { view ->
                                                            try {
                                                                view.setImageDrawable(pm.getApplicationIcon(pkg))
                                                            } catch (_: Exception) {
                                                                view.setImageDrawable(null)
                                                            }
                                                        }
                                                    )
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
                                                Text(
                                                    text = if (isExpanded) "▲" else "▼",
                                                    color = dynamicSecondary,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(horizontal = 4.dp)
                                                )
                                            }
                                        }
                                        1 -> {
                                            val isExpanded = expandedSubsections.contains(key) || searchQuery.isNotBlank()
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 12.dp, top = 2.dp, bottom = 2.dp)
                                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        expandedSubsections = if (expandedSubsections.contains(key)) {
                                                            expandedSubsections - key
                                                        } else {
                                                            expandedSubsections + key
                                                        }
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
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
                                                Text(
                                                    text = if (isExpanded) "▲" else "▼",
                                                    color = dynamicSecondary,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        2 -> {
                                            val isChecked = selectedTokens.contains(key)
                                            val isConfiguredAction = key.contains(";type=app_shortcut;")

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(start = 20.dp, top = 1.dp, bottom = 1.dp)
                                                    .background(
                                                        color = if (isChecked) dynamicPrimary.copy(alpha = 0.22f) else Color.Transparent,
                                                        shape = RoundedCornerShape(6.dp)
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
                                                    .padding(horizontal = 8.dp, vertical = 6.dp),
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
                                                        color = Color.LightGray.copy(alpha = 0.45f),
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
                                    .width(24.dp)
                                    .fillMaxHeight()
                                    .padding(start = 4.dp),
                                verticalArrangement = Arrangement.SpaceEvenly,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                "#ABCDEFGHIJKLMNOPQRSTUVWXYZ".forEach { letter ->
                                    val targetIdx = letterIndices[letter]
                                    Text(
                                        text = letter.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (targetIdx != null) dynamicSecondary else Color.White.copy(alpha = 0.15f),
                                        modifier = Modifier.clickable(enabled = targetIdx != null) {
                                            targetIdx?.let { coroutineScope.launch { listState.scrollToItem(it) } }
                                        }.padding(vertical = 1.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Button(
                                onClick = { finish() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE61C1C)),
                                modifier = Modifier.weight(1f).padding(end = 8.dp)
                            ) {
                                Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
                                    prefs.edit().putString("gear_set_${setId}_ring_${ringIndex}_packages", selectedTokens.joinToString(",")).apply()
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = dynamicPrimary),
                                modifier = Modifier.weight(1f).padding(start = 8.dp)
                            ) {
                                Text("SAVE", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
