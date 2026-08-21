package com.sbf.lightspeed

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class FrostedPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val setIndex = intent.getIntExtra("SET_INDEX", 0)
        val ringIndex = intent.getIntExtra("RING_INDEX", 0)
        val pm = packageManager

        val launchIntent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val rawApps = pm.queryIntentActivities(launchIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .sortedBy { pm.getApplicationLabel(pm.getApplicationInfo(it, PackageManager.GET_META_DATA)).toString().uppercase() }

        setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(0.9f).fillMaxHeight(0.85f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x3D211F26))
                ) {
                    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                        Text(
                            text = "Assign to Gear ${ringIndex + 1} (Profile Set ${'A' + setIndex})",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) {
                            itemsIndexed(rawApps) { _, pkg ->
                                val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val prefs = getSharedPreferences("default", Context.MODE_PRIVATE)
                                            val csvString = prefs.getString("gear_set_${setIndex}_ring_${ringIndex}_packages", "") ?: ""
                                            val currentApps = csvString.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
                                            
                                            if (!currentApps.contains(pkg)) {
                                                currentApps.add(pkg)
                                                prefs.edit().putString("gear_set_${setIndex}_ring_${ringIndex}_packages", currentApps.joinToString(",")).apply()
                                            }
                                            finish()
                                        }
                                        .padding(vertical = 14.dp, horizontal = 10.dp)
                                ) {
                                    Text(appLabel, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Text(pkg.substringAfterLast("."), color = Color.LightGray.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                            }
                        }

                        Button(
                            onClick = { finish() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE61C1C)),
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                        ) {
                            Text("CANCEL", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
