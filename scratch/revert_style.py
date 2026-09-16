import re

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'r') as f:
    content = f.read()

# Replace the box and card styling back to the original
old_box = """            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        isVisible = false
                        window.decorView.postDelayed({ finish() }, 200)
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(250)),
                    exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(250))
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}, // Absorb touches
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {"""

new_box = """            val scale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isVisible) 1f else 0.85f, animationSpec = tween(250))
            val alpha by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isVisible) 1f else 0f, animationSpec = tween(200))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha * 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        isVisible = false
                        window.decorView.postDelayed({ finish() }, 250)
                    },
                contentAlignment = Alignment.CenterEnd
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxHeight(0.85f)
                        .fillMaxWidth(0.88f)
                        .padding(end = 16.dp)
                        .scale(scale)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}, // Absorb touches
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF141419).copy(alpha = alpha * 0.98f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {"""

# Replace exit animation blocks
old_exit = """                }
            }"""

new_exit = """            }"""

# Update colors for texts back to original
content = content.replace(old_box, new_box)

# Need to replace the closing brackets correctly
content = content.replace("                    }\n                }\n            }\n        }\n    }\n}\n", "                    }\n                }\n            }\n        }\n    }\n}\n")
content = content.replace("                            Row(verticalAlignment = Alignment.CenterVertically) {\n                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = dynamicPrimary, modifier = Modifier.size(28.dp))\n                                Spacer(modifier = Modifier.width(12.dp))\n                                Text(\"AUDIO COMMAND\", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace, letterSpacing = 1.sp)\n                            }", "                            Row(verticalAlignment = Alignment.CenterVertically) {\n                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = dynamicPrimary, modifier = Modifier.size(28.dp))\n                                Spacer(modifier = Modifier.width(12.dp))\n                                Text(\"AUDIO COMMAND\", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)\n                            }")

# Make sure we remove the AnimatedVisibility closing bracket.
import textwrap
def remove_animated_visibility(s):
    # This is a bit tricky, let's just do a clean regex or string split
    return s.replace("                AnimatedVisibility(", "                if(true){ //")

with open('app/src/main/kotlin/com/sbf/lightspeed/system/OmniscientAudioDockActivity.kt', 'w') as f:
    f.write(content)
