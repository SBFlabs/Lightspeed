import re

file_path = "app/src/main/kotlin/com/sbf/lightspeed/settings/DeflectorComponents.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    var glowStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorGlowStyle(context)) }"""
replacement = """    var glowStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorGlowStyle(context)) }
    var pillStyle by remember { mutableStateOf(LightspeedPreferences.getDeflectorPillStyle(context)) }"""

content = content.replace(target, replacement)

pill_options_block = """
        val pillOptions1 = listOf(
            "anchored_glow" to "Anchored",
            "floating_smart_pill" to "Smart Pill",
            "neon_core" to "Neon Core"
        )
        val pillOptions2 = listOf(
            "razor_edge" to "Razor Edge",
            "kinetic_elastic" to "Elastic",
            "hollow_ghost" to "Ghost Rim"
        )
        
        Text("Pill Geometry Style", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pillOptions1.forEach { (id, label) ->
                val isSelected = pillStyle == id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        pillStyle = id
                        LightspeedPreferences.setDeflectorPillStyle(context, id)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                        LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                    },
                    label = {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pillOptions2.forEach { (id, label) ->
                val isSelected = pillStyle == id
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        pillStyle = id
                        LightspeedPreferences.setDeflectorPillStyle(context, id)
                        try { LightspeedAccessibilityService.instance?.reloadPreferences() } catch (_: Exception) {}
                        LightspeedAccessibilityService.instance?.triggerDeflectorGlow(isLeft)
                    },
                    label = {
                        Text(
                            label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
"""

target_inject = """        Text("Aura Color Rendering", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)"""

content = content.replace(target_inject, pill_options_block + target_inject)

with open(file_path, "w") as f:
    f.write(content)
print("Updated DeflectorComponents.kt")
