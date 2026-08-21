package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import kotlinx.coroutines.launch

class GearPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val setId = intent.getStringExtra("SET_ID") ?: "0"
        val ringIndex = intent.getIntExtra("RING_INDEX", 0)
        val pm = packageManager

        val primaryAccent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_600)
        } else {
            0xFF6750A4.toInt()
        }
        val secondaryAccent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            getColor(android.R.color.system_accent1_300)
        } else {
            0xFFD0BCFF.toInt()
        }

        val dynamicPrimary = Color(primaryAccent)
        val dynamicSecondary = Color(secondaryAccent)

        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val rawApps = pm.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .sortedBy { pm.getApplicationLabel(pm.getApplicationInfo(it, PackageManager.GET_META_DATA)).toString().uppercase() }

        setContent {
            var searchQuery by remember { mutableStateOf("") }
            val selectedApps = remember { mutableStateListOf<String>() }
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()

            LaunchedEffect(Unit) {
                val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
                val csvString = prefs.getString("gear_set_${setId}_ring_${ringIndex}_packages", "") ?: ""
                val currentApps = csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                selectedApps.addAll(currentApps)
            }

            val filteredApps = remember(searchQuery) {
                rawApps.filter { pkg ->
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    label.contains(searchQuery, ignoreCase = true) || pkg.contains(searchQuery, ignoreCase = true)
                }
            }

            val letterIndices = remember(filteredApps) {
                val map = mutableMapOf<Char, Int>()
                filteredApps.forEachIndexed { index, pkg ->
                    val label = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    val firstChar = label.firstOrNull()?.uppercaseChar() ?: '#'
                    val targetKey = if (firstChar in 'A'..'Z') firstChar else '#'
                    if (!map.containsKey(targetKey)) { map[targetKey] = index }
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
                        val sName = if (rawName.isEmpty() || rawName == "SET A" || rawName == "SET B" || rawName == "SET C" || rawName == "SET D" || rawName == "SET") {
                            when(setId) {
                                "0" -> "POWER USER ANDROID"
                                "1" -> "MY APP STORES"
                                "2" -> "UTILITIES SECTOR"
                                "3" -> "ENTERTAINMENT DECK"
                                else -> "CUSTOM SET"
                            }
                        } else {
                            rawName
                        }

                        Text(
                            text = "Assign Target Folders to Gear ${ringIndex + 1} ($sName)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search apps...", color = Color.White.copy(alpha = 0.45f), fontSize = 14.sp) },
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
                                itemsIndexed(filteredApps) { _, pkg ->
                                    val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                                    val isChecked = selectedApps.contains(pkg)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                color = if (isChecked) dynamicPrimary.copy(alpha = 0.24f) else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                if (selectedApps.contains(pkg)) {
                                                    selectedApps.remove(pkg)
                                                } else {
                                                    selectedApps.add(pkg)
                                                }
                                            }
                                            .padding(vertical = 10.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AndroidView(
                                            factory = { ctx -> ImageView(ctx) },
                                            modifier = Modifier.size(36.dp).padding(end = 10.dp),
                                            update = { view ->
                                                try { view.setImageDrawable(pm.getApplicationIcon(pkg)) } catch (e: Exception) { view.setImageDrawable(null) }
                                            }
                                        )

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = appLabel,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = pkg,
                                                color = Color.LightGray.copy(alpha = 0.5f),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = {
                                                if (isChecked) selectedApps.remove(pkg) else selectedApps.add(pkg)
                                            },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = dynamicPrimary,
                                                checkmarkColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }

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
                                    prefs.edit().putString("gear_set_${setId}_ring_${ringIndex}_packages", selectedApps.joinToString(",")).apply()
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
