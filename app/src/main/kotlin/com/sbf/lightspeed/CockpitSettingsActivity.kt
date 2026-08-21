package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class CockpitSettingsActivity : ComponentActivity() {
    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            view.setPadding(0, 0, 0, 0)
            WindowInsetsCompat.CONSUMED
        }

        setContent {
            val context = LocalContext.current

            val dynamicColorScheme = remember {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
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

            LaunchedEffect(Unit) {
                val orderStr = prefs.getString("gear_sets_order", "0,1,2,3") ?: "0,1,2,3"
                val list = orderStr.split(",").filter { it.isNotEmpty() }
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
                    setNames[id] = if (saved == "SET A" || saved == "SET B" || saved == "SET C" || saved == "SET D" || saved == "SET" || saved.isEmpty()) defaultName else saved
                    
                    val r0Str = prefs.getString("gear_set_${id}_ring_0_packages", "") ?: ""
                    ring0Data[id] = r0Str.split(",").filter { it.isNotEmpty() }
                    
                    val r1Str = prefs.getString("gear_set_${id}_ring_1_packages", "") ?: ""
                    ring1Data[id] = r1Str.split(",").filter { it.isNotEmpty() }
                }
            }

            MaterialTheme(colorScheme = dynamicColorScheme) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(52.dp))

                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.94f)
                                .weight(1f)
                                .padding(bottom = 24.dp)
                                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(24.dp)),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f))
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                                Text("Cockpit & Gear Sets Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(modifier = Modifier.height(14.dp))

                                if (selectedSetId == null) {
                                    Text("Launch Layer Behavior:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                        RadioButton(selected = launchBehavior == "default", onClick = { launchBehavior = "default" })
                                        Text("Always Default", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.clickable { launchBehavior = "default" })
                                        Spacer(modifier = Modifier.width(16.dp))
                                        RadioButton(selected = launchBehavior == "last", onClick = { launchBehavior = "last" })
                                        Text("Remember Last", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, modifier = Modifier.clickable { launchBehavior = "last" })
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("Manage Active Sets:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp)) {
                                        itemsIndexed(setsOrder) { index, id ->
                                            var currentName by remember(id) { mutableStateOf(setNames[id] ?: "") }
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = currentName,
                                                    onValueChange = { currentName = it; setNames[id] = it },
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                                    ),
                                                    modifier = Modifier.weight(1f).height(52.dp),
                                                    singleLine = true
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Button(
                                                    onClick = { selectedSetId = id },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                                    modifier = Modifier.height(48.dp)
                                                ) {
                                                    Text("GEARS", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                                }
                                                Spacer(modifier = Modifier.width(4.dp))
                                                IconButton(onClick = {
                                                    if (index > 0) {
                                                        val temp = setsOrder[index]; setsOrder[index] = setsOrder[index - 1]; setsOrder[index - 1] = temp
                                                    }
                                                }, enabled = index > 0) { Text("▲", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                                                IconButton(onClick = {
                                                    if (index < setsOrder.size - 1) {
                                                        val temp = setsOrder[index]; setsOrder[index] = setsOrder[index + 1]; setsOrder[index + 1] = temp
                                                    }
                                                }, enabled = index < setsOrder.size - 1) { Text("▼", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp) }
                                                IconButton(onClick = { setsOrder.removeAt(index) }, enabled = setsOrder.size > 1) {
                                                    Text("❌", color = Color.Red, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val newId = System.currentTimeMillis().toString()
                                            setsOrder.add(newId)
                                            setNames[newId] = "CUSTOM SET"
                                            ring0Data[newId] = emptyList()
                                            ring1Data[newId] = emptyList()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    ) {
                                        Text("+ ADD NEW SET", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    val targetId = selectedSetId!!
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text("Rearrange Wheel: ${setNames[targetId] ?: ""}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(onClick = { selectedSetId = null }) {
                                            Text("◀ BACK TO SETS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))

                                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                        item {
                                            Text("Gear 1 (Outer Ring)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        val r0List = ring0Data[targetId] ?: emptyList()
                                        if (r0List.isEmpty()) {
                                            item { Text("No apps inside outer ring.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) }
                                        } else {
                                            itemsIndexed(r0List) { idx, pkg ->
                                                AppRow(pkg = pkg, index = idx, totalSize = r0List.size, onMoveUp = {
                                                    val m = r0List.toMutableList(); val t = m[idx]; m[idx] = m[idx - 1]; m[idx - 1] = t
                                                    ring0Data[targetId] = m
                                                }, onMoveDown = {
                                                    val m = r0List.toMutableList(); val t = m[idx]; m[idx] = m[idx + 1]; m[idx + 1] = t
                                                    ring0Data[targetId] = m
                                                }, onRemove = {
                                                    val m = r0List.toMutableList(); m.removeAt(idx)
                                                    ring0Data[targetId] = m
                                                })
                                            }
                                        }

                                        item {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text("Gear 2 (Inner Ring)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                        val r1List = ring1Data[targetId] ?: emptyList()
                                        if (r1List.isEmpty()) {
                                            item { Text("No apps inside inner ring.", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) }
                                        } else {
                                            itemsIndexed(r1List) { idx, pkg ->
                                                AppRow(pkg = pkg, index = idx, totalSize = r1List.size, onMoveUp = {
                                                    val m = r1List.toMutableList(); val t = m[idx]; m[idx] = m[idx - 1]; m[idx - 1] = t
                                                    ring1Data[targetId] = m
                                                }, onMoveDown = {
                                                    val m = r1List.toMutableList(); val t = m[idx]; m[idx] = m[idx + 1]; m[idx + 1] = t
                                                    ring1Data[targetId] = m
                                                }, onRemove = {
                                                    val m = r1List.toMutableList(); m.removeAt(idx)
                                                    ring1Data[targetId] = m
                                                })
                                            }
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Button(
                                        onClick = { finish() },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                        modifier = Modifier.weight(1f).padding(end = 6.dp)
                                    ) {
                                        Text("CANCEL", color = MaterialTheme.colorScheme.onErrorContainer)
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
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                        modifier = Modifier.weight(1f).padding(start = 6.dp)
                                    ) {
                                        Text("SAVE", color = MaterialTheme.colorScheme.onPrimary)
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
    
    // Asynchronously resolve app metadata along with graphical icon assets natively
    val appData = remember(pkg) {
        try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val label = pm.getApplicationLabel(info).toString()
            val drawable = pm.getApplicationIcon(info)
            
            val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
                drawable.bitmap
            } else {
                val bmp = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bmp)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bmp
            }
            Pair(label, bitmap.asImageBitmap())
        } catch (e: Exception) {
            Pair(pkg.substringAfterLast("."), null)
        }
    }

    val appLabel = appData.first
    val appIcon = appData.second

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (appIcon != null) {
            Image(
                bitmap = appIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .padding(end = 10.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(appLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
            Text(pkg, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        
        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(32.dp)) {
            Text("▲", fontSize = 12.sp, color = if (index > 0) MaterialTheme.colorScheme.primary else Color.Gray)
        }
        IconButton(onClick = onMoveDown, enabled = index < totalSize - 1, modifier = Modifier.size(32.dp)) {
            Text("▼", fontSize = 12.sp, color = if (index < totalSize - 1) MaterialTheme.colorScheme.primary else Color.Gray)
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Text("❌", fontSize = 11.sp, color = Color.Red)
        }
    }
}
