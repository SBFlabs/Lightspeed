package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry

class CockpitSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        LightspeedActionRegistry.initializeSync(this)

        setContent {
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                LightspeedActionRegistry.ensureIndexed(context)
            }

            val dynamicColorScheme = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    androidx.compose.material3.dynamicDarkColorScheme(context)
                } else {
                    darkColorScheme(
                        primary = Color(0xFF6750A4),
                        secondary = Color(0xFFD0BCFF),
                        background = Color(0xFF121214)
                    )
                }
            }

            val prefs = remember { getSharedPreferences("default", Context.MODE_PRIVATE) }
            var launchBehavior by remember { mutableStateOf(prefs.getString("cockpit_launch_behavior", "default") ?: "default") }
            val setsOrder = remember { mutableStateListOf<String>() }
            val setNames = remember { mutableStateMapOf<String, String>() }

            val ring0Data = remember { mutableStateMapOf<String, List<String>>() }
            val ring1Data = remember { mutableStateMapOf<String, List<String>>() }
            var selectedSetId by remember { mutableStateOf<String?>(null) }
            var activeRingTab by remember { mutableStateOf(0) }

            fun reloadSetData() {
                val orderStr = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
                val list = orderStr.split(",").filter { it.isNotEmpty() }
                setsOrder.clear()
                setsOrder.addAll(list)
                list.forEach { id ->
                    val defaultName = when(id) {
                        "0" -> "POWER USER ANDROID"
                        "1" -> "MY APP STORES"
                        "2" -> "UTILITIES SECTOR"
                        "3" -> "ENTERTAINMENT DECK"
                        else -> "CUSTOM SET"
                    }
                    val saved = prefs.getString("gear_set_${id}_name", defaultName) ?: defaultName
                    setNames[id] = if (saved in listOf("SET A", "SET B", "SET C", "SET D", "SET", "")) defaultName else saved

                    val r0Str = prefs.getString("gear_set_${id}_ring_0_packages", "") ?: ""
                    ring0Data[id] = r0Str.split(",").filter { it.isNotEmpty() }

                    val r1Str = prefs.getString("gear_set_${id}_ring_1_packages", "") ?: ""
                    ring1Data[id] = r1Str.split(",").filter { it.isNotEmpty() }
                }
            }

            LaunchedEffect(Unit) {
                reloadSetData()
            }

            MaterialTheme(colorScheme = dynamicColorScheme) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .weight(1f)
                                .padding(bottom = 24.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14).copy(alpha = 0.95f))
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = if (selectedSetId == null) "Cockpit & Gear Sets" else "Edit Profile: ${setNames[selectedSetId] ?: ""}",
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = if (selectedSetId == null) "Customize circular dual-ring shortcuts" else "Configure Outer & Inner rings",
                                            fontSize = 12.sp,
                                            color = dynamicColorScheme.secondary
                                        )
                                    }
                                    IconButton(
                                        onClick = { finish() },
                                        modifier = Modifier.size(32.dp).background(Color.White.copy(alpha = 0.08f), CircleShape)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }

                                if (selectedSetId == null) {
                                    // Launch Behavior Card
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Launch Mode", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            Text(
                                                if (launchBehavior == "default") "Always starts on first profile" else "Resumes on last used profile",
                                                fontSize = 10.sp,
                                                color = Color.LightGray.copy(alpha = 0.6f)
                                            )
                                        }
                                        
                                        FilterChip(
                                            selected = launchBehavior == "default",
                                            onClick = { launchBehavior = "default" },
                                            label = { Text("Always Default", fontSize = 11.sp, fontWeight = if (launchBehavior == "default") FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = dynamicColorScheme.primary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.padding(end = 6.dp)
                                        )
                                        FilterChip(
                                            selected = launchBehavior == "last",
                                            onClick = { launchBehavior = "last" },
                                            label = { Text("Remember Last", fontSize = 11.sp, fontWeight = if (launchBehavior == "last") FontWeight.Bold else FontWeight.Normal) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = dynamicColorScheme.primary,
                                                selectedLabelColor = Color.White
                                            )
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Icon Pack Selector Card
                                    val availableIconPacks = remember { com.sbf.lightspeed.system.LightspeedIconManager.getAvailableIconPacks(context) }
                                    var activeIconPack by remember { mutableStateOf(com.sbf.lightspeed.system.LightspeedIconManager.getActiveIconPack(context)) }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                            .padding(12.dp)
                                    ) {
                                        Text("Icon Pack & Visuals", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                        Text("Applied across Gimbal Gears, Category Horizon & Star System Grid", fontSize = 10.sp, color = Color.LightGray.copy(alpha = 0.6f))

                                        Spacer(modifier = Modifier.height(8.dp))

                                        androidx.compose.foundation.lazy.LazyRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            items(availableIconPacks.size) { idx ->
                                                val pack = availableIconPacks[idx]
                                                val isSelected = (activeIconPack == pack.packageName)

                                                Box(
                                                    modifier = Modifier
                                                        .background(
                                                            if (isSelected) dynamicColorScheme.primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .border(
                                                            if (isSelected) 1.5.dp else 1.dp,
                                                            if (isSelected) dynamicColorScheme.primary else Color.White.copy(alpha = 0.1f),
                                                            RoundedCornerShape(10.dp)
                                                        )
                                                        .clickable {
                                                            activeIconPack = pack.packageName
                                                            com.sbf.lightspeed.system.LightspeedIconManager.setActiveIconPack(context, pack.packageName)
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 7.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = if (pack.isSystem) "🎨 System Dynamic" else "📦 ${pack.label}",
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) Color.White else Color.LightGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text("Profiles & Gear Sets (${setsOrder.size}):", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))

                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
                                        itemsIndexed(setsOrder, key = { _, id -> id }) { index, id ->
                                            var currentName by remember(id) { mutableStateOf(setNames[id] ?: "") }
                                            val r0Count = (ring0Data[id] ?: emptyList()).size
                                            val r1Count = (ring1Data[id] ?: emptyList()).size

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp)
                                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    TextField(
                                                        value = currentName,
                                                        onValueChange = { currentName = it; setNames[id] = it },
                                                        placeholder = { Text("Profile Name...", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color.White
                                                        ),
                                                        colors = TextFieldDefaults.colors(
                                                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                                                            unfocusedContainerColor = Color.White.copy(alpha = 0.04f),
                                                            focusedTextColor = Color.White,
                                                            unfocusedTextColor = Color.White,
                                                            focusedIndicatorColor = Color.Transparent,
                                                            unfocusedIndicatorColor = Color.Transparent,
                                                            disabledIndicatorColor = Color.Transparent
                                                        ),
                                                        shape = RoundedCornerShape(12.dp),
                                                        modifier = Modifier.weight(1f).height(52.dp),
                                                        singleLine = true
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Button(
                                                        onClick = { selectedSetId = id; activeRingTab = 0 },
                                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicColorScheme.primary),
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 14.dp),
                                                        modifier = Modifier.height(52.dp)
                                                    ) {
                                                        Text("GEARS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "Outer Ring: $r0Count items  •  Inner Ring: $r1Count items",
                                                        fontSize = 11.sp,
                                                        color = Color.LightGray.copy(alpha = 0.6f)
                                                    )
                                                    Row {
                                                        IconButton(
                                                            onClick = {
                                                                if (index > 0) {
                                                                    val temp = setsOrder[index]; setsOrder[index] = setsOrder[index - 1]; setsOrder[index - 1] = temp
                                                                }
                                                            },
                                                            enabled = index > 0,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (index > 0) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (index < setsOrder.size - 1) {
                                                                    val temp = setsOrder[index]; setsOrder[index] = setsOrder[index + 1]; setsOrder[index + 1] = temp
                                                                }
                                                            },
                                                            enabled = index < setsOrder.size - 1,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (index < setsOrder.size - 1) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                        IconButton(
                                                            onClick = { setsOrder.removeAt(index) },
                                                            enabled = setsOrder.size > 1,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = if (setsOrder.size > 1) Color(0xFFFF6B6B) else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            val newId = System.currentTimeMillis().toString()
                                            setsOrder.add(newId)
                                            setNames[newId] = "CUSTOM SET"
                                            ring0Data[newId] = emptyList()
                                            ring1Data[newId] = emptyList()
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ ADD NEW SET", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                } else {
                                    val targetId = selectedSetId!!
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                        TextButton(onClick = { selectedSetId = null }) {
                                            Text("◀ BACK TO SETS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dynamicColorScheme.secondary)
                                        }
                                        Spacer(modifier = Modifier.weight(1f))
                                    }

                                    // Segmented Ring Tabs
                                    val r0List = ring0Data[targetId] ?: emptyList()
                                    val r1List = ring1Data[targetId] ?: emptyList()

                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = activeRingTab == 0,
                                            onClick = { activeRingTab = 0 },
                                            label = { Text("Gear 1 (Outer): ${r0List.size} apps", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = activeRingTab == 1,
                                            onClick = { activeRingTab = 1 },
                                            label = { Text("Gear 2 (Inner): ${r1List.size} apps", fontSize = 12.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    val currentRingList = if (activeRingTab == 0) r0List else r1List

                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        if (currentRingList.isEmpty()) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 24.dp)
                                                        .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("No items assigned to this ring yet.\nTap '+ ADD SHORTCUTS' below.", color = Color.Gray, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                                }
                                            }
                                        } else {
                                            itemsIndexed(currentRingList, key = { idx, pkg -> "ring_${activeRingTab}_${idx}_${pkg}" }) { idx, pkg ->
                                                AppRow(
                                                    pkg = pkg,
                                                    index = idx,
                                                    totalSize = currentRingList.size,
                                                    onMoveUp = {
                                                        val m = currentRingList.toMutableList(); val t = m[idx]; m[idx] = m[idx - 1]; m[idx - 1] = t
                                                        if (activeRingTab == 0) ring0Data[targetId] = m else ring1Data[targetId] = m
                                                    },
                                                    onMoveDown = {
                                                        val m = currentRingList.toMutableList(); val t = m[idx]; m[idx] = m[idx + 1]; m[idx + 1] = t
                                                        if (activeRingTab == 0) ring0Data[targetId] = m else ring1Data[targetId] = m
                                                    },
                                                    onRemove = {
                                                        val m = currentRingList.toMutableList(); m.removeAt(idx)
                                                        if (activeRingTab == 0) ring0Data[targetId] = m else ring1Data[targetId] = m
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val intent = Intent(context, GearPickerActivity::class.java).apply {
                                                putExtra("SET_ID", targetId)
                                                putExtra("RING_INDEX", activeRingTab)
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicColorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(44.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("+ ADD SHORTCUTS TO GEAR ${activeRingTab + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { finish() },
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("CANCEL", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    }
                                    Button(
                                        onClick = {
                                            val editor = prefs.edit()
                                            editor.putString("cockpit_launch_behavior", launchBehavior)
                                            editor.putString("gear_sets_order", setsOrder.joinToString(","))
                                            setNames.forEach { (id, name) -> editor.putString("gear_set_${id}_name", name) }
                                            ring0Data.forEach { (id, apps) -> editor.putString("gear_set_${id}_ring_0_packages", apps.joinToString(",")) }
                                            ring1Data.forEach { (id, apps) -> editor.putString("gear_set_${id}_ring_1_packages", apps.joinToString(",")) }
                                            editor.apply()
                                            finish()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicColorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.weight(1f).height(46.dp)
                                    ) {
                                        Text("SAVE CHANGES", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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

@Composable
fun AppRow(pkg: String, index: Int, totalSize: Int, onMoveUp: () -> Unit, onMoveDown: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current

    val appData = remember(pkg) {
        val extractedPkg = when {
            pkg.startsWith("app:") -> pkg.removePrefix("app:")
            pkg.startsWith("shortcut:") -> {
                if (pkg.contains(";pkg=")) pkg.substringAfter(";pkg=").substringBefore(";")
                else if (pkg.contains("package=")) pkg.substringAfter("package=").substringBefore(";")
                else ""
            }
            pkg.startsWith("system:") -> ""
            else -> pkg
        }

        val cachedLabel = LightspeedActionRegistry.labelCache[pkg]
        val label = cachedLabel ?: when {
            extractedPkg.isNotEmpty() -> try {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(extractedPkg, 0)).toString()
            } catch (_: Exception) { pkg.substringAfterLast(".") }
            else -> pkg
        }

        val bmp = if (extractedPkg.isNotEmpty()) {
            LightspeedActionRegistry.getIconBitmap(context, extractedPkg)
        } else null

        Pair(label, bmp?.asImageBitmap())
    }

    val appLabel = appData.first
    val appIcon = appData.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 10.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .padding(end = 10.dp)
                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(appLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pkg, fontSize = 10.sp, color = Color.LightGray.copy(alpha = 0.45f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = if (index > 0) Color.White.copy(alpha = 0.8f) else Color.Gray, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onMoveDown, enabled = index < totalSize - 1, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = if (index < totalSize - 1) Color.White.copy(alpha = 0.8f) else Color.Gray, modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
        }
    }
}

