import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# I will append the Composables to the end of OmniscientAudioDockManager.kt
composables = """
@Composable
fun AppVolumeRow(pkg: String, name: String, iconDrawable: android.graphics.drawable.Drawable?, initialVol: Float, isPinned: Boolean, tint: Color, onPinToggled: (Boolean) -> Unit) {
    var vol by remember { mutableFloatStateOf(initialVol) }
    var pinned by remember { mutableStateOf(isPinned) }
    
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.03f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {
        Box(modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
            if (iconDrawable != null) {
                val bitmap = remember(iconDrawable) {
                    if (iconDrawable is android.graphics.drawable.BitmapDrawable && iconDrawable.bitmap != null) {
                        iconDrawable.bitmap
                    } else {
                        val bmp = android.graphics.Bitmap.createBitmap(
                            if (iconDrawable.intrinsicWidth > 0) iconDrawable.intrinsicWidth else 1,
                            if (iconDrawable.intrinsicHeight > 0) iconDrawable.intrinsicHeight else 1,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        val canvas = android.graphics.Canvas(bmp)
                        iconDrawable.setBounds(0, 0, canvas.width, canvas.height)
                        iconDrawable.draw(canvas)
                        bmp
                    }
                }
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = name,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(androidx.compose.material.icons.Icons.Default.Android, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines=1)
            Slider(
                value = vol,
                onValueChange = { vol = it },
                valueRange = 0f..1f,
                modifier = Modifier.height(24.dp),
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White.copy(alpha = 0.8f), inactiveTrackColor = Color.White.copy(alpha=0.15f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(
            onClick = { 
                pinned = !pinned 
                onPinToggled(pinned)
            },
            modifier = Modifier
                .size(36.dp)
                .background(if (pinned) tint.copy(alpha = 0.25f) else Color.Transparent, CircleShape)
        ) {
            Icon(androidx.compose.material.icons.Icons.Default.PushPin, contentDescription = "Pin Focus", tint = if (pinned) tint else Color.White.copy(alpha = 0.3f), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun DockActionButton(modifier: Modifier, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(24.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(6.dp))
            Text(title, color = color, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}
"""

if 'fun AppVolumeRow' not in content:
    content += composables
    
with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)

