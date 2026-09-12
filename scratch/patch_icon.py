import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'r') as f:
    content = f.read()

content = content.replace('import com.google.accompanist.drawablepainter.rememberDrawablePainter\n', 'import androidx.compose.ui.graphics.asImageBitmap\n')

draw_block = """            if (iconDrawable != null) {
                androidx.compose.foundation.Image(
                    painter = rememberDrawablePainter(iconDrawable),
                    contentDescription = name,
                    modifier = Modifier.size(24.dp)
                )
            } else {"""

new_draw_block = """            if (iconDrawable != null) {
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
            } else {"""

content = content.replace(draw_block, new_draw_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'w') as f:
    f.write(content)
