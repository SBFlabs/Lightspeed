package com.sbf.lightspeed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.settings.LightspeedActionRegistry
import com.sbf.lightspeed.system.defaultPrefs
import com.sbf.lightspeed.ui.theme.LightspeedTheme

class CockpitSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LightspeedActionRegistry.initializeSync(this)

        setContent {
            val context = this
            val prefs = remember { context.defaultPrefs() }
            
            var selectedFlankTab by remember { mutableStateOf(0) } // 0 = Left Flank / Port, 1 = Right Flank / Starboard

            var launchBehaviorLeft by remember { 
                mutableStateOf(prefs.getString("cockpit_launch_behavior_left", prefs.getString("cockpit_launch_behavior", "default")) ?: "default") 
            }
            var launchBehaviorRight by remember { 
                mutableStateOf(prefs.getString("cockpit_launch_behavior_right", prefs.getString("cockpit_launch_behavior", "default")) ?: "default") 
            }

            var physicsProfile by remember {
                mutableStateOf(prefs.getString("pref_gear_physics_profile", "magnetic") ?: "magnetic")
            }
            var hapticStrength by remember {
                mutableStateOf(prefs.getString("pref_gear_haptic_strength", "tactical") ?: "tactical")
            }

            val setsOrderLeft = remember { mutableStateListOf<String>() }
            val setsOrderRight = remember { mutableStateListOf<String>() }
            val setNames = remember { mutableStateMapOf<String, String>() }
            val ring0Data = remember { mutableStateMapOf<String, List<String>>() }
            val ring1Data = remember { mutableStateMapOf<String, List<String>>() }

            var selectedSetId by remember { mutableStateOf<String?>(null) }
            var activeRingTab by remember { mutableStateOf(0) }

            fun reloadSetData() {
                setsOrderLeft.clear()
                setsOrderRight.clear()
                setNames.clear()
                ring0Data.clear()
                ring1Data.clear()

                val savedLeft = prefs.getString("gear_sets_order_left", null)
                val idsLeft = if (!savedLeft.isNullOrEmpty()) {
                    savedLeft.split(",").filter { it.isNotEmpty() }
                } else {
                    val global = prefs.getString("gear_sets_order", "") ?: ""
                    if (global.isNotEmpty()) global.split(",").filter { it.isNotEmpty() } else listOf("0", "1", "2", "3")
                }
                setsOrderLeft.addAll(idsLeft)

                val savedRight = prefs.getString("gear_sets_order_right", null)
                val idsRight = if (!savedRight.isNullOrEmpty()) {
                    savedRight.split(",").filter { it.isNotEmpty() }
                } else {
                    val global = prefs.getString("gear_sets_order", "") ?: ""
                    if (global.isNotEmpty()) global.split(",").filter { it.isNotEmpty() } else listOf("0", "1", "2", "3")
                }
                setsOrderRight.addAll(idsRight)

                val allIds = (idsLeft + idsRight + listOf("0", "1", "2", "3")).distinct()
                for (id in allIds) {
                    val rawName = prefs.getString("gear_set_${id}_name", "") ?: ""
                    val sName = if (rawName.isEmpty() || rawName in listOf("SET A", "SET B", "SET C", "SET D", "SET")) {
                        when (id) {
                            "0" -> "POWER USER ANDROID"
                            "1" -> "MY APP STORES"
                            "2" -> "UTILITIES SECTOR"
                            "3" -> "ENTERTAINMENT DECK"
                            else -> "CUSTOM SET"
                        }
                    } else rawName
                    setNames[id] = sName

                    val r0Str = prefs.getString("gear_set_${id}_ring0", "") ?: ""
                    val r1Str = prefs.getString("gear_set_${id}_ring1", "") ?: ""
                    ring0Data[id] = r0Str.split(",").filter { it.isNotEmpty() }
                    ring1Data[id] = r1Str.split(",").filter { it.isNotEmpty() }
                }
            }

            LaunchedEffect(Unit) {
                reloadSetData()
            }

            LightspeedTheme(forceDark = true) {
                val dynamicColorScheme = MaterialTheme.colorScheme
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(44.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .weight(1f)
                                .padding(bottom = 20.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E0E14).copy(alpha = 0.95f))
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                                // Top Header Bar
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
                                            text = if (selectedSetId == null) "Customize circular dual-ring shortcuts & physics" else "Configure Outer & Inner rings",
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
                                    // Flank Selector Switcher
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedFlankTab == 0,
                                            onClick = { selectedFlankTab = 0 },
                                            label = {
                                                Text(
                                                    "◀ Port (Left Astrogation)",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedFlankTab == 0) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = dynamicColorScheme.primary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = selectedFlankTab == 1,
                                            onClick = { selectedFlankTab = 1 },
                                            label = {
                                                Text(
                                                    "Starboard (Right Astrogation) ▶",
                                                    fontSize = 11.sp,
                                                    fontWeight = if (selectedFlankTab == 1) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = dynamicColorScheme.primary,
                                                selectedLabelColor = Color.White
                                            ),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Main Scrollable Cockpit Configuration View
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // 1. Launch Mode Card (Per-Flank)
                                        item {
                                            val isLeft = (selectedFlankTab == 0)
                                            val currentLaunchBehavior = if (isLeft) launchBehaviorLeft else launchBehaviorRight
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Text(
                                                    if (isLeft) "Port (Left) Startup Mode" else "Starboard (Right) Startup Mode",
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    if (currentLaunchBehavior == "default") "Always starts on first profile in this flank's sequence" else "Resumes on last used profile on this flank",
                                                    fontSize = 11.sp,
                                                    color = Color.LightGray.copy(alpha = 0.7f),
                                                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                                )

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    FilterChip(
                                                        selected = currentLaunchBehavior == "default",
                                                        onClick = {
                                                            if (isLeft) {
                                                                launchBehaviorLeft = "default"
                                                                prefs.edit().putString("cockpit_launch_behavior_left", "default").apply()
                                                            } else {
                                                                launchBehaviorRight = "default"
                                                                prefs.edit().putString("cockpit_launch_behavior_right", "default").apply()
                                                            }
                                                        },
                                                        label = { Text("Always Default", fontSize = 11.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                    )
                                                    FilterChip(
                                                        selected = currentLaunchBehavior == "last",
                                                        onClick = {
                                                            if (isLeft) {
                                                                launchBehaviorLeft = "last"
                                                                prefs.edit().putString("cockpit_launch_behavior_left", "last").apply()
                                                            } else {
                                                                launchBehaviorRight = "last"
                                                                prefs.edit().putString("cockpit_launch_behavior_right", "last").apply()
                                                            }
                                                        },
                                                        label = { Text("Remember Last", fontSize = 11.sp) },
                                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                    )
                                                }
                                            }
                                        }

                                        // 2. Gimbal Flight Physics & Haptics Tuning Card
                                        item {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                                                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                                                    .padding(12.dp)
                                            ) {
                                                Text("Gimbal Flight Physics & Haptics", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                                Text("Tune gear rotation angular momentum and mechanical ratchet ticks", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                                                Text("Physics Momentum:", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    item {
                                                        FilterChip(
                                                            selected = physicsProfile == "magnetic",
                                                            onClick = {
                                                                physicsProfile = "magnetic"
                                                                prefs.edit().putString("pref_gear_physics_profile", "magnetic").apply()
                                                            },
                                                            label = { Text("⚡ Snappy", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = physicsProfile == "fluid",
                                                            onClick = {
                                                                physicsProfile = "fluid"
                                                                prefs.edit().putString("pref_gear_physics_profile", "fluid").apply()
                                                            },
                                                            label = { Text("🌊 Fluid Glide", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = physicsProfile == "heavy",
                                                            onClick = {
                                                                physicsProfile = "heavy"
                                                                prefs.edit().putString("pref_gear_physics_profile", "heavy").apply()
                                                            },
                                                            label = { Text("🚀 Heavy Cargo", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                }

                                                Text("Tactile Ratchet Tick:", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                                                LazyRow(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    item {
                                                        FilterChip(
                                                            selected = hapticStrength == "subtle",
                                                            onClick = {
                                                                hapticStrength = "subtle"
                                                                prefs.edit().putString("pref_gear_haptic_strength", "subtle").apply()
                                                            },
                                                            label = { Text("Subtle", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = hapticStrength == "tactical",
                                                            onClick = {
                                                                hapticStrength = "tactical"
                                                                prefs.edit().putString("pref_gear_haptic_strength", "tactical").apply()
                                                            },
                                                            label = { Text("Tactical", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = hapticStrength == "heavy",
                                                            onClick = {
                                                                hapticStrength = "heavy"
                                                                prefs.edit().putString("pref_gear_haptic_strength", "heavy").apply()
                                                            },
                                                            label = { Text("Heavy Thud", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                    item {
                                                        FilterChip(
                                                            selected = hapticStrength == "off",
                                                            onClick = {
                                                                hapticStrength = "off"
                                                                prefs.edit().putString("pref_gear_haptic_strength", "off").apply()
                                                            },
                                                            label = { Text("Off", fontSize = 11.sp) },
                                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = dynamicColorScheme.primary, selectedLabelColor = Color.White)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        // 3. Icon Pack & Visuals Card
                                        item {
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
                                                Text("Applied across Gimbal Gears, Category Horizon & Star System Grid", fontSize = 11.sp, color = Color.LightGray.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))

                                                LazyRow(
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

                                        // 4. Section Title: Profiles & Gear Sets
                                        val activeOrder = if (selectedFlankTab == 0) setsOrderLeft else setsOrderRight

                                        item {
                                            Text(
                                                text = if (selectedFlankTab == 0) "Port (Left) Profiles Sequence (${activeOrder.size}):" else "Starboard (Right) Profiles Sequence (${activeOrder.size}):",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White.copy(alpha = 0.8f),
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }

                                        // 5. List of Profiles & Gear Sets
                                        itemsIndexed(activeOrder, key = { _, id -> "${selectedFlankTab}_$id" }) { index, id ->
                                            var currentName by remember(id) { mutableStateOf(setNames[id] ?: "") }
                                            val r0Count = (ring0Data[id] ?: emptyList()).size
                                            val r1Count = (ring1Data[id] ?: emptyList()).size

                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
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
                                                        modifier = Modifier.weight(1f).height(46.dp),
                                                        singleLine = true,
                                                        shape = RoundedCornerShape(10.dp)
                                                    )

                                                    Spacer(modifier = Modifier.width(8.dp))

                                                    Button(
                                                        onClick = { selectedSetId = id },
                                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicColorScheme.primary),
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(38.dp)
                                                    ) {
                                                        Text("EDIT GEARS ➔", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Outer: $r0Count apps  |  Inner: $r1Count apps",
                                                        fontSize = 11.sp,
                                                        color = Color.LightGray.copy(alpha = 0.7f)
                                                    )

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = {
                                                                if (index > 0) {
                                                                    val temp = activeOrder[index]
                                                                    activeOrder[index] = activeOrder[index - 1]
                                                                    activeOrder[index - 1] = temp
                                                                }
                                                            },
                                                            enabled = index > 0,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = if (index > 0) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                        IconButton(
                                                            onClick = {
                                                                if (index < activeOrder.size - 1) {
                                                                    val temp = activeOrder[index]
                                                                    activeOrder[index] = activeOrder[index + 1]
                                                                    activeOrder[index + 1] = temp
                                                                }
                                                            },
                                                            enabled = index < activeOrder.size - 1,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = if (index < activeOrder.size - 1) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                        IconButton(
                                                            onClick = { activeOrder.removeAt(index) },
                                                            enabled = activeOrder.size > 1,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = if (activeOrder.size > 1) Color(0xFFFF6B6B) else Color.Gray, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // 6. Add New Set Button
                                        item {
                                            OutlinedButton(
                                                onClick = {
                                                    val newId = System.currentTimeMillis().toString()
                                                    activeOrder.add(newId)
                                                    setNames[newId] = "CUSTOM SET"
                                                    ring0Data[newId] = emptyList()
                                                    ring1Data[newId] = emptyList()
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(44.dp)
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("+ ADD NEW SET", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else {
                                    // Individual Profile Gear Ring Editor View
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
                                                putStringArrayListExtra("CURRENT_SELECTION", ArrayList(currentRingList))
                                            }
                                            startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = dynamicColorScheme.primary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp)
                                    ) {
                                        Text("+ ADD / CHANGE SHORTCUTS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    val editor = prefs.edit()
                    editor.putString("cockpit_launch_behavior_left", launchBehaviorLeft)
                    editor.putString("cockpit_launch_behavior_right", launchBehaviorRight)
                    editor.putString("cockpit_launch_behavior", launchBehaviorRight)
                    editor.putString("pref_gear_physics_profile", physicsProfile)
                    editor.putString("pref_gear_haptic_strength", hapticStrength)
                    editor.putString("gear_sets_order_left", setsOrderLeft.joinToString(","))
                    editor.putString("gear_sets_order_right", setsOrderRight.joinToString(","))
                    editor.putString("gear_sets_order", setsOrderRight.joinToString(","))
                    val allSavedIds = (setsOrderLeft + setsOrderRight + setNames.keys).distinct()
                    for (id in allSavedIds) {
                        editor.putString("gear_set_${id}_name", setNames[id] ?: "")
                        editor.putString("gear_set_${id}_ring0", (ring0Data[id] ?: emptyList()).joinToString(","))
                        editor.putString("gear_set_${id}_ring1", (ring1Data[id] ?: emptyList()).joinToString(","))
                    }
                    editor.apply()
                }
            }
        }
    }

    @Composable
    private fun AppRow(
        pkg: String,
        index: Int,
        totalSize: Int,
        onMoveUp: () -> Unit,
        onMoveDown: () -> Unit,
        onRemove: () -> Unit
    ) {
        val context = this
        val dynamicColorScheme = MaterialTheme.colorScheme

        val label = remember(pkg) {
            when {
                pkg.startsWith("system:") -> LightspeedActionRegistry.labelCache[pkg] ?: pkg.substringAfter("system:").replace("_", " ").uppercase()
                pkg.startsWith("shortcut:") -> LightspeedActionRegistry.labelCache[pkg] ?: run {
                    if (pkg.contains(";custom_label=")) pkg.substringAfter(";custom_label=").substringBefore(";")
                    else if (pkg.contains(";label=")) pkg.substringAfter(";label=").substringBefore(";")
                    else "Shortcut"
                }
                pkg.startsWith("app:") -> {
                    val clean = pkg.removePrefix("app:")
                    try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(clean, 0)).toString()
                    } catch (_: Exception) { clean }
                }
                else -> {
                    try {
                        val pm = context.packageManager
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                    } catch (_: Exception) { pkg }
                }
            }
        }

        val iconBmp = remember(pkg) {
            com.sbf.lightspeed.system.LightspeedIconManager.getIconBitmap(context, pkg)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (iconBmp != null) {
                Image(
                    bitmap = iconBmp.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp))
                )
            } else {
                Box(
                    modifier = Modifier.size(24.dp).background(Color.White.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (pkg.startsWith("system:")) "⚡" else "⚙", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (pkg.startsWith("system:")) "System Action" else if (pkg.startsWith("shortcut:")) "Deep Shortcut" else pkg.removePrefix("app:"),
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onMoveUp,
                enabled = index > 0,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Up", tint = if (index > 0) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onMoveDown,
                enabled = index < totalSize - 1,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Down", tint = if (index < totalSize - 1) dynamicColorScheme.secondary else Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(26.dp)
            ) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(16.dp))
            }
        }
    }
}
