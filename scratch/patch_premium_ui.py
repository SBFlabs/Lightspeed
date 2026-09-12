import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'r') as f:
    content = f.read()

# 1. Remove FLAG_BLUR_BEHIND from the layout params!
old_flags = """            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                blurBehindRadius = 120
            }"""
new_flags = """            var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            
            // Removed FLAG_BLUR_BEHIND so the backdrop is 100% transparent!"""
content = content.replace(old_flags, new_flags)

# 2. Remove the LaunchedEffect that updates blur radius dynamically
old_launched = """        LaunchedEffect(glassStyleIndex) {
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
        }"""
content = content.replace(old_launched, "")

# 3. Replace the Box modifiers and styles with a clean, premium layout
# And remove the AGSL shader
agsl_pattern = re.compile(r'private const val NOISE_SHADER.*?\"\"\"', re.DOTALL)
content = agsl_pattern.sub('', content)

old_vars = """        // Glass Style Configuration
        val glassStyleNames = listOf("CLEAR GLASS", "HEAVY ACRYLIC", "AGSL FROSTED", "MATERIAL ADAPTIVE")
        val bgAlpha = when (glassStyleIndex) {
            0 -> 0.04f
            1 -> 0.25f
            2 -> 0.15f
            3 -> 0.25f // Later we can use system colors, for now tinted black
            else -> 0.04f
        }
        val bgColor = if (glassStyleIndex == 3) dynamicPrimary.copy(alpha = 0.2f) else Color.White.copy(alpha = bgAlpha)"""
new_vars = """        val bgColor = Color(0xB3121212) // 70% opacity deep dark grey for the dock itself"""
content = content.replace(old_vars, new_vars)


old_box_modifier = """                        .clip(RoundedCornerShape(36.dp))
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
                        .background(bgColor)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(36.dp)
                        )"""
new_box_modifier = """                        .clip(RoundedCornerShape(36.dp))
                        .background(bgColor)
                        .border(
                            1.dp,
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(36.dp)
                        )"""
content = content.replace(old_box_modifier, new_box_modifier)

# Remove the gear icon and the style text
old_header = """                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            }"""
content = content.replace(old_header, "")

# Make the AppVolumeRow and SystemVolumeRow more distinct like pills!
# Wait, SystemVolumeRow:
old_sys_row = """    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=0.02f), RoundedCornerShape(20.dp)).border(1.dp, Color.White.copy(alpha=0.04f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {"""
new_sys_row = """    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.White.copy(alpha=0.08f), RoundedCornerShape(20.dp)).padding(horizontal = 16.dp)) {"""
content = content.replace(old_sys_row, new_sys_row)

old_app_row = """    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.03f), RoundedCornerShape(24.dp)).border(1.dp, Color.White.copy(alpha=0.05f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {"""
new_app_row = """    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(64.dp).background(Color.White.copy(alpha=0.08f), RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)) {"""
content = content.replace(old_app_row, new_app_row)

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockManager.kt', 'w') as f:
    f.write(content)

