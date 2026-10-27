package com.sbf.lightspeed.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ScreenRotationAlt
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sbf.lightspeed.system.LightspeedOrientationEngine
import com.sbf.lightspeed.ui.theme.LightspeedStyle

@Composable
fun HudGravityBucketsSection(
    context: Context,
    prefs: SharedPreferences,
    onBucketSelected: (LightspeedOrientationEngine.AttitudeBucket) -> Unit
) {
    val lsColors = LightspeedStyle.colors

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("ATTITUDE MODE BUCKETS (PER-APP RULES)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp)
        Text("All apps default to native Auto-Rotate (on/off) above, unless assigned to a bucket below:", fontSize = 11.sp, color = lsColors.textSecondary.copy(alpha = 0.8f))

        val buckets = listOf(
            LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT,
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_PORTRAIT,
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_LANDSCAPE,
            LightspeedOrientationEngine.AttitudeBucket.SENSOR_360
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(buckets[0], buckets[1]).forEach { bucket ->
                val assignedCount = remember(bucket, prefs.getStringSet(bucket.prefKey, null)) {
                    LightspeedOrientationEngine.getAssignedPackages(context, bucket).size
                }
                DeckCard(
                    modifier = Modifier.weight(1f),
                    onClick = { onBucketSelected(bucket) }
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (bucket) {
                                    LightspeedOrientationEngine.AttitudeBucket.STRICT_PORTRAIT -> Icons.Default.StayCurrentPortrait
                                    else -> Icons.Default.ScreenRotationAlt
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "$assignedCount apps",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = lsColors.textPrimary)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(bucket.subtitle, fontSize = 9.5.sp, color = lsColors.textSecondary.copy(alpha = 0.7f), lineHeight = 12.sp)
                    }
                }
            }
        }

        val landscapeBucket = buckets[2]
        val assignedLandscapeCount = remember(landscapeBucket, prefs.getStringSet(landscapeBucket.prefKey, null)) {
            LightspeedOrientationEngine.getAssignedPackages(context, landscapeBucket).size
        }
        DeckCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onBucketSelected(landscapeBucket) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.StayCurrentLandscape,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(landscapeBucket.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = lsColors.textPrimary)
                        Text(landscapeBucket.subtitle, fontSize = 10.sp, color = lsColors.textSecondary.copy(alpha = 0.75f))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$assignedLandscapeCount apps",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }

        val sensor360Bucket = buckets[3]
        val assigned360Count = remember(sensor360Bucket, prefs.getStringSet(sensor360Bucket.prefKey, null)) {
            LightspeedOrientationEngine.getAssignedPackages(context, sensor360Bucket).size
        }
        DeckCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { onBucketSelected(sensor360Bucket) }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(sensor360Bucket.title, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = lsColors.textPrimary)
                        Text(sensor360Bucket.subtitle, fontSize = 10.sp, color = lsColors.textSecondary.copy(alpha = 0.75f))
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$assigned360Count apps",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}
