import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# Add GlassStyle enum and preference
imports_add = """import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.input.pointer.pointerInput
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.RuntimeShader
"""

if 'import androidx.compose.ui.input.pointer.pointerInput' not in content:
    content = content.replace('import androidx.compose.ui.platform.ComposeView', imports_add + 'import androidx.compose.ui.platform.ComposeView')


# Add the Shader for AGSL
agsl_shader = """
    private const val NOISE_SHADER = \"\"\"
        uniform float2 resolution;
        uniform float time;
        uniform shader content;
        
        float random(float2 st) {
            return fract(sin(dot(st.xy, float2(12.9898,78.233))) * 43758.5453123);
        }
        
        half4 main(float2 fragCoord) {
            half4 color = content.eval(fragCoord);
            float noise = random(fragCoord + time) * 0.15;
            return color + half4(noise, noise, noise, 0.0);
        }
    \"\"\"
"""
if 'private const val NOISE_SHADER' not in content:
    content = content.replace('object OmniscientAudioDockManager {', 'object OmniscientAudioDockManager {' + agsl_shader)


# Update the UI state variables
ui_vars_old = """        val dynamicPrimary = Color(0xFF6366F1)
        val dynamicSecondary = Color(0xFFEAB308)

        Box("""
ui_vars_new = """        val prefs = service.getSharedPreferences("lightspeed_audio_dock", Context.MODE_PRIVATE)
        var glassStyleIndex by remember { mutableIntStateOf(prefs.getInt("pref_glass_style", 0)) }
        var dragAccumulator by remember { mutableFloatStateOf(0f) }
        
        val dynamicPrimary = Color(0xFF6366F1)
        val dynamicSecondary = Color(0xFFEAB308)

        // Glass Style Configuration
        val glassStyleNames = listOf("CLEAR GLASS", "HEAVY ACRYLIC", "AGSL FROSTED", "MATERIAL ADAPTIVE")
        val bgAlpha = when (glassStyleIndex) {
            0 -> 0.04f
            1 -> 0.25f
            2 -> 0.15f
            3 -> 0.25f // Later we can use system colors, for now tinted black
            else -> 0.04f
        }
        val bgColor = if (glassStyleIndex == 3) dynamicPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = bgAlpha)
        
        LaunchedEffect(glassStyleIndex) {
            // Update WindowManager blur radius dynamically
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val p = composeView?.layoutParams as? WindowManager.LayoutParams
                    if (p != null) {
                        p.blurBehindRadius = if (glassStyleIndex == 1 || glassStyleIndex == 2) 250 else 120
                        windowManager?.updateViewLayout(composeView, p)
                    }
                }
            } catch (e: Exception) {}
        }
        
        Box("""
content = content.replace(ui_vars_old, ui_vars_new)

# Update the main Box background
box_bg_old = """                        .background(Color.White.copy(alpha = 0.04f))
                        .border("""
box_bg_new = """                        .background(bgColor)
                        .border("""
content = content.replace(box_bg_old, box_bg_new)

# Add RenderEffect to the main Box for AGSL
box_modifier_old = """                        .clip(RoundedCornerShape(36.dp))
                        .background(bgColor)"""
box_modifier_new = """                        .clip(RoundedCornerShape(36.dp))
                        .then(
                            if (glassStyleIndex == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Modifier.graphicsLayer {
                                    val runtimeShader = RuntimeShader(NOISE_SHADER)
                                    runtimeShader.setFloatUniform("resolution", size.width, size.height)
                                    runtimeShader.setFloatUniform("time", System.currentTimeMillis() % 100000 / 1000f)
                                    renderEffect = RenderEffect.createRuntimeShaderEffect(runtimeShader, "content").asComposeRenderEffect()
                                }
                            } else Modifier
                        )
                        .background(bgColor)"""
# We need to import graphicsLayer
if 'import androidx.compose.ui.graphics.graphicsLayer' not in content:
    content = content.replace('import androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.graphics.asComposeRenderEffect')

content = content.replace(box_modifier_old, box_modifier_new)

# Add the gear icon header
header_old = """                        Text(
                            text = "OMNISCIENT AUDIO",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp
                        )"""
header_new = """                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "OMNISCIENT AUDIO",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 4.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(glassStyleNames[glassStyleIndex], color = Color.White.copy(alpha = 0.5f), fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
                                Icon(
                                    androidx.compose.material.icons.Icons.Default.Settings,
                                    contentDescription = "Glass Style",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .pointerInput(Unit) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { triggerHaptic(service) },
                                                onDrag = { change, dragAmount -> 
                                                    change.consume()
                                                    dragAccumulator += dragAmount.x
                                                    if (kotlin.math.abs(dragAccumulator) > 40f) {
                                                        val steps = (dragAccumulator / 40f).toInt()
                                                        dragAccumulator %= 40f
                                                        if (steps != 0) {
                                                            var newIdx = glassStyleIndex + steps
                                                            while (newIdx < 0) newIdx += glassStyleNames.size
                                                            newIdx %= glassStyleNames.size
                                                            glassStyleIndex = newIdx
                                                            prefs.edit().putInt("pref_glass_style", newIdx).apply()
                                                            triggerHaptic(service)
                                                        }
                                                    }
                                                },
                                                onDragEnd = { dragAccumulator = 0f }
                                            )
                                        }
                                        .clickable { 
                                            var newIdx = glassStyleIndex + 1
                                            newIdx %= glassStyleNames.size
                                            glassStyleIndex = newIdx
                                            prefs.edit().putInt("pref_glass_style", newIdx).apply()
                                            triggerHaptic(service)
                                        }
                                )
                            }
                        }"""
content = content.replace(header_old, header_new)

# Add Settings icon import
if 'import androidx.compose.material.icons.filled.Settings' not in content:
    content = content.replace('import androidx.compose.material.icons.filled.Phone', 'import androidx.compose.material.icons.filled.Phone\nimport androidx.compose.material.icons.filled.Settings')

# Add triggerHaptic function inside OmniscientAudioDockManager
haptic_fn = """
    private fun triggerHaptic(context: Context) {
        LightspeedHapticEngine.vibrate(context, 18, 120)
    }
"""
if 'private fun triggerHaptic' not in content:
    content = content.replace('    private fun getPinnedApps', haptic_fn + '\n    private fun getPinnedApps')


with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)
