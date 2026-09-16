import re

with open('app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt', 'r') as f:
    content = f.read()

# Add states
old_states = """    var animationScale by remember { mutableFloatStateOf(1.0f) }
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }"""

new_states = """    var animationScale by remember { mutableFloatStateOf(1.0f) }
    var displayDpi by remember { mutableFloatStateOf(context.resources.configuration.densityDpi.toFloat()) }
    var fontScale by remember { mutableFloatStateOf(1.0f) }
    var longPressDelay by remember { mutableFloatStateOf(400f) }"""

content = content.replace(old_states, new_states)

# Add read logic
old_read = """                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)
                fontScale = fScale
            } catch (e: Exception) {"""

new_read = """                val fScale = Settings.System.getFloat(context.contentResolver, "font_scale", 1.0f)
                fontScale = fScale
                val lpDelay = Settings.Secure.getInt(context.contentResolver, "long_press_timeout", 400).toFloat()
                longPressDelay = lpDelay
            } catch (e: Exception) {"""
content = content.replace(old_read, new_read)

# Add Long Press Slider block
old_block = """        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)

    }
}"""

new_block = """        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)
        
        // 5. Custom Long-Press Delay
        OverrideSliderRow(
            context = context,
            prefs = prefs,
            sliderKey = "sys_override_long_press_delay",
            defaultValue = 400f,
            icon = Icons.Default.TouchApp,
            title = "Long-Press Delay",
            subtitle = "Override system-wide touch latency (Settings.Secure.LONG_PRESS_TIMEOUT)",
            value = longPressDelay,
            valueRange = 150f..1000f,
            steps = 84, // 10ms increments
            valueFormatter = { "${it.toInt()} ms" },
            isEnabled = isShizukuActive,
            onValueChange = { longPressDelay = it },
            onValueChangeFinished = {
                if (!isShizukuActive) return@OverrideSliderRow
                LightspeedHapticEngine.tick(context)
                scope.launch(Dispatchers.IO) {
                    ElevatedTaskCloser.execShizuku("settings put secure long_press_timeout ${longPressDelay.toInt()}")
                }
            }
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.8.dp)
    }
}"""
content = content.replace(old_block, new_block)

with open('app/src/main/kotlin/com/sbf/lightspeed/settings/SystemOverrideComponents.kt', 'w') as f:
    f.write(content)
